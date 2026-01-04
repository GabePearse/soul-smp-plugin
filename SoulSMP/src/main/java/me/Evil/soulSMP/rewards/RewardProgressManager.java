package me.Evil.soulSMP.rewards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RewardProgressManager {

    private final Plugin plugin;

    private File file;
    private FileConfiguration cfg;

    // Cached data for speed
    private final Map<UUID, Integer> lastRewardedLevel = new HashMap<>();
    private final Map<UUID, Set<String>> rewardedAdvancementsByPlayer = new HashMap<>();
    private final Map<String, UUID> firstCompleterByAdvancement = new HashMap<>();

    public RewardProgressManager(Plugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        file = new File(plugin.getDataFolder(), "rewards.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create rewards.yml: " + e.getMessage());
            }
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void load() {
        init();

        lastRewardedLevel.clear();
        rewardedAdvancementsByPlayer.clear();
        firstCompleterByAdvancement.clear();

        // players.<uuid>.lastRewardedLevel
        ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players != null) {
            for (String uuidStr : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection pSec = players.getConfigurationSection(uuidStr);
                    if (pSec == null) continue;

                    int lvl = pSec.getInt("lastRewardedLevel", 0);
                    lastRewardedLevel.put(uuid, Math.max(0, lvl));

                    List<String> advs = pSec.getStringList("rewardedAdvancements");
                    if (advs != null && !advs.isEmpty()) {
                        rewardedAdvancementsByPlayer.put(uuid, new HashSet<>(advs));
                    }
                } catch (Exception ignored) {}
            }
        }

        // advancements.<key>.first
        ConfigurationSection advsSec = cfg.getConfigurationSection("advancements");
        if (advsSec != null) {
            for (String advKey : advsSec.getKeys(false)) {
                String firstStr = advsSec.getString(advKey + ".first", null);
                if (firstStr == null || firstStr.isEmpty()) continue;

                try {
                    UUID first = UUID.fromString(firstStr);
                    firstCompleterByAdvancement.put(advKey, first);
                } catch (Exception ignored) {}
            }
        }
    }

    public void save() {
        if (cfg == null) init();

        // Write players
        cfg.set("players", null);
        ConfigurationSection players = cfg.createSection("players");

        for (var entry : lastRewardedLevel.entrySet()) {
            UUID uuid = entry.getKey();
            int lvl = entry.getValue();

            ConfigurationSection pSec = players.createSection(uuid.toString());
            pSec.set("lastRewardedLevel", lvl);

            Set<String> advs = rewardedAdvancementsByPlayer.getOrDefault(uuid, Collections.emptySet());
            if (!advs.isEmpty()) {
                pSec.set("rewardedAdvancements", new ArrayList<>(advs));
            }
        }

        // Write advancements first-completer map
        cfg.set("advancements", null);
        ConfigurationSection advsSec = cfg.createSection("advancements");

        for (var entry : firstCompleterByAdvancement.entrySet()) {
            String advKey = entry.getKey();
            UUID first = entry.getValue();
            if (advKey == null || advKey.isEmpty() || first == null) continue;

            ConfigurationSection sec = advsSec.createSection(advKey);
            sec.set("first", first.toString());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save rewards.yml: " + e.getMessage());
        }
    }

    // -----------------------
    // Level rewards
    // -----------------------

    public int getLastRewardedLevel(UUID uuid) {
        if (uuid == null) return 0;
        return lastRewardedLevel.getOrDefault(uuid, 0);
    }

    public void setLastRewardedLevel(UUID uuid, int level) {
        if (uuid == null) return;
        lastRewardedLevel.put(uuid, Math.max(0, level));
    }

    /**
     * Calculates how many tokens to award for level-ups:
     * - Only levels >= 30 reward tokens
     * - Each level rewards ONLY ONCE (tracked by lastRewardedLevel)
     * - Tokens per level = 1
     *
     * Supports skipping levels (ex: 29 -> 33 will reward 30..33 = 4 tokens).
     *
     * IMPORTANT:
     * - This method does not mutate state.
     * - Call setLastRewardedLevel(uuid, newLevel) after paying out (or use payAndMarkLevels).
     */
    public int calculateLevelTokensToAward(UUID uuid, int newLevel) {
        if (uuid == null) return 0;

        int last = getLastRewardedLevel(uuid);

        // Only pay on new progress (never double-pay old levels)
        if (newLevel <= last) return 0;

        // Only start at level 30
        int start = Math.max(30, last + 1);
        if (newLevel < start) return 0;

        // 1 token per newly reached level (>=30)
        return (newLevel - start) + 1;
    }


    /**
     * Convenience helper:
     * Returns tokens to pay for reaching newLevel (if any),
     * and updates lastRewardedLevel to newLevel ONLY if tokens > 0.
     */
    public int payAndMarkLevels(UUID uuid, int newLevel) {
        int tokens = calculateLevelTokensToAward(uuid, newLevel);
        if (tokens > 0) {
            setLastRewardedLevel(uuid, newLevel);
        }
        return tokens;
    }

    // -----------------------
    // Advancement rewards
    // -----------------------

    public boolean hasRewardedAdvancement(UUID uuid, String advKey) {
        if (uuid == null || advKey == null) return false;
        return rewardedAdvancementsByPlayer.getOrDefault(uuid, Collections.emptySet()).contains(advKey);
    }

    public void markRewardedAdvancement(UUID uuid, String advKey) {
        if (uuid == null || advKey == null) return;
        rewardedAdvancementsByPlayer.computeIfAbsent(uuid, k -> new HashSet<>()).add(advKey);
    }

    public UUID getFirstCompleter(String advKey) {
        if (advKey == null) return null;
        return firstCompleterByAdvancement.get(advKey);
    }

    /**
     * Returns true if this call successfully set the first completer (meaning it was not set before).
     */
    public boolean trySetFirstCompleter(String advKey, UUID uuid) {
        if (advKey == null || uuid == null) return false;
        if (firstCompleterByAdvancement.containsKey(advKey)) return false;

        firstCompleterByAdvancement.put(advKey, uuid);
        return true;
    }
}
