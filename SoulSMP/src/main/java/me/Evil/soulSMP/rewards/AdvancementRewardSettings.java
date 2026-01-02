package me.Evil.soulSMP.rewards;

import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Paper-only auto-tiering using the advancement requirement tree:
 * Tier = depth-from-root + 1
 *  - root => tier 1
 *  - child => tier 2
 *  - etc.
 *
 * Token payout is based on "tiers" config:
 * tiers:
 *   1: 2
 *   2: 4
 *   3: 8
 *
 * Options:
 * - include-hidden: if false, skip advancements with null display (recipes, etc.)
 * - clamp-to-max-tier: if true, tiers beyond the max tier defined pay the max tier tokens
 */
public class AdvancementRewardSettings {

    private final Plugin plugin;

    // tier -> tokens
    private final Map<Integer, Integer> tierTokens = new HashMap<>();

    private boolean includeHidden = false;
    private boolean clampToMaxTier = true;
    private int maxTierDefined = 0;

    public AdvancementRewardSettings(Plugin plugin) {
        this.plugin = plugin;

        File file = new File(plugin.getDataFolder(), "advancement_rewards.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("advancement_rewards.yml", false);
            } catch (Exception ignored) {
                try {
                    plugin.getDataFolder().mkdirs();
                    //noinspection ResultOfMethodCallIgnored
                    file.createNewFile();
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not create advancement_rewards.yml: " + e.getMessage());
                }
            }
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        load(cfg);
    }

    private void load(FileConfiguration cfg) {
        tierTokens.clear();
        maxTierDefined = 0;

        includeHidden = cfg.getBoolean("options.include-hidden", false);
        clampToMaxTier = cfg.getBoolean("options.clamp-to-max-tier", true);

        ConfigurationSection tiers = cfg.getConfigurationSection("tiers");
        if (tiers != null) {
            for (String k : tiers.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(k);
                    int tokens = tiers.getInt(k, 0);
                    if (tier > 0 && tokens > 0) {
                        tierTokens.put(tier, tokens);
                        if (tier > maxTierDefined) maxTierDefined = tier;
                    }
                } catch (Exception ignored) {}
            }
        }

        plugin.getLogger().info("[Rewards] Loaded " + tierTokens.size() + " tier token value(s).");
    }

    /**
     * Returns tokens for this advancement, auto-tiered from its parent chain.
     * Returns 0 if not eligible (hidden when includeHidden=false, or no tiers defined).
     */
    public int getTokensForAdvancement(Advancement adv) {
        if (adv == null || adv.getKey() == null) return 0;

        // Skip recipe/hidden advancements unless enabled.
        if (!includeHidden && adv.getDisplay() == null) {
            return 0;
        }

        if (tierTokens.isEmpty()) return 0;

        int tier = computeTierFromParentChain(adv);
        return tokensForTier(tier);
    }

    private int tokensForTier(int tier) {
        if (tier <= 0) return 0;

        Integer direct = tierTokens.get(tier);
        if (direct != null) return direct;

        if (clampToMaxTier && maxTierDefined > 0) {
            return tierTokens.getOrDefault(maxTierDefined, 0);
        }

        return 0;
    }

    /**
     * Paper API provides Advancement#getParent().
     * Tier = depth-from-root + 1
     */
    private int computeTierFromParentChain(Advancement adv) {
        int depth = 0;
        Set<String> seen = new HashSet<>();

        Advancement cur = adv;

        while (true) {
            if (cur == null || cur.getKey() == null) break;

            String k = cur.getKey().toString();
            if (!seen.add(k)) {
                // Safety: prevent infinite loops if something is weird
                break;
            }

            Advancement parent = cur.getParent();
            if (parent == null) break;

            depth++;
            cur = parent;

            if (depth > 200) break; // hard safety cap
        }

        return depth + 1;
    }
}
