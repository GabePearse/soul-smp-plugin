package me.Evil.soulSMP.leaderboard;

import me.Evil.soulSMP.fishing.journal.FishingJournalManager;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LeaderboardManager {

    private final Plugin plugin;
    private final TeamManager teamManager;
    private final FishingJournalManager journalManager;
    private final LeaderboardDisplay display;

    private final File file;
    private FileConfiguration data;

    // Debounce recompute (collapse frequent triggers)
    private int recomputeTaskId = -1;

    public LeaderboardManager(
            Plugin plugin,
            TeamManager teamManager,
            FishingJournalManager journalManager,
            LeaderboardDisplay display
    ) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.journalManager = journalManager;
        this.display = display;

        this.file = new File(plugin.getDataFolder(), "leaderboard.yml");
        reload();
    }

    // -------------------------
    // Persistence
    // -------------------------

    public void reload() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { data.save(file); } catch (IOException ignored) {}
    }

    // -------------------------
    // Display Locations
    // -------------------------

    public void setDisplayLocation(String key, Location loc) {
        if (loc == null || loc.getWorld() == null) return;

        String p = "displays." + key;
        data.set(p + ".world", loc.getWorld().getName());
        data.set(p + ".x", loc.getX());
        data.set(p + ".y", loc.getY());
        data.set(p + ".z", loc.getZ());
        data.set(p + ".yaw", loc.getYaw());
        data.set(p + ".pitch", loc.getPitch());
        save();
    }

    public Location getDisplayLocation(String key) {
        String p = "displays." + key;
        String worldName = data.getString(p + ".world");
        if (worldName == null) return null;

        var w = Bukkit.getWorld(worldName);
        if (w == null) return null;

        double x = data.getDouble(p + ".x");
        double y = data.getDouble(p + ".y");
        double z = data.getDouble(p + ".z");
        float yaw = (float) data.getDouble(p + ".yaw");
        float pitch = (float) data.getDouble(p + ".pitch");

        return new Location(w, x, y, z, yaw, pitch);
    }

    // -------------------------
    // Entity UUID persistence (leaderboard.yml)
    // -------------------------

    public UUID getEntityUuid(String displayKey) {
        String s = data.getString("entities." + displayKey + ".uuid");
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public void setEntityUuid(String displayKey, UUID uuid) {
        data.set("entities." + displayKey + ".uuid", uuid != null ? uuid.toString() : null);
        save();
    }

    public void clearEntityUuid(String displayKey) {
        data.set("entities." + displayKey + ".uuid", null);
        save();
    }

    public UUID getTextEntityUuid(String key) {
        String s = data.getString("entities.text." + key);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public void setTextEntityUuid(String key, UUID uuid) {
        data.set("entities.text." + key, uuid != null ? uuid.toString() : null);
        save();
    }

    public void clearTextEntityUuid(String key) {
        data.set("entities.text." + key, null);
        save();
    }

    // -------------------------
    // Team lookup for mannequin subtitle
    // -------------------------

    /** Returns team name for a player UUID, or null if none. Works for offline players. */
    public String getTeamNameForPlayer(UUID playerId) {
        if (playerId == null) return null;
        Team t = teamManager.getTeamByPlayer(playerId);
        return (t == null || t.getName() == null || t.getName().isBlank()) ? null : t.getName();
    }

    // -------------------------
    // Winners storage
    // -------------------------

    private void setWinner(String boardKey, UUID playerId, String playerName, double value) {
        String p = "boards." + boardKey;
        data.set(p + ".playerUuid", playerId != null ? playerId.toString() : null);
        data.set(p + ".playerName", (playerName == null || playerName.isBlank()) ? null : playerName);
        data.set(p + ".value", value);
    }

    private void clearWinner(String boardKey) {
        String p = "boards." + boardKey;
        data.set(p, null);
    }

    private void setTeamWinner(String boardKey, String teamName, double value) {
        String p = "boards." + boardKey;
        data.set(p + ".teamName", (teamName == null || teamName.isBlank()) ? null : teamName);
        data.set(p + ".value", value);
    }

    private void clearTeamWinner(String boardKey) {
        String p = "boards." + boardKey;
        data.set(p, null);
    }

    public UUID getWinnerPlayerUuid(String boardKey) {
        String s = data.getString("boards." + boardKey + ".playerUuid");
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public String getWinnerPlayerName(String boardKey) {
        String n = data.getString("boards." + boardKey + ".playerName");
        return (n == null || n.isBlank()) ? null : n;
    }

    public String getWinnerTeamName(String boardKey) {
        String n = data.getString("boards." + boardKey + ".teamName");
        return (n == null || n.isBlank()) ? null : n;
    }

    public double getWinnerValue(String boardKey) {
        return data.getDouble("boards." + boardKey + ".value", 0D);
    }

    // Claim banner design snapshot (stored on recompute)
    public String getClaimBannerMaterial() {
        String s = data.getString("claimBanner.design.material");
        return (s == null || s.isBlank()) ? null : s;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getClaimBannerPatterns() {
        List<?> list = data.getList("claimBanner.design.patterns");
        if (list == null) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> nm = new LinkedHashMap<>();
                for (var e : m.entrySet()) {
                    if (e.getKey() != null) nm.put(String.valueOf(e.getKey()), e.getValue());
                }
                out.add(nm);
            }
        }
        return out;
    }

    private void setClaimBannerDesignFromTeam(Team t) {
        // Clear if no team or no design
        if (t == null || !t.hasClaimedBannerDesign() || t.getBannerMaterial() == null) {
            data.set("claimBanner.design", null);
            return;
        }

        data.set("claimBanner.design.material", t.getBannerMaterial().name());

        // store: [{color: "RED", type: "minecraft:creeper"}...]
        List<Map<String, Object>> patterns = new ArrayList<>();
        try {
            var item = t.createBannerItem();
            if (item != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.BannerMeta meta) {
                for (org.bukkit.block.banner.Pattern p : meta.getPatterns()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("color", p.getColor().name());
                    var key = org.bukkit.Registry.BANNER_PATTERN.getKey(p.getPattern());
                    if (key != null) m.put("type", key.toString());
                    patterns.add(m);
                }
            }
        } catch (Exception ignored) {}

        data.set("claimBanner.design.patterns", patterns);
    }

    // -------------------------
    // Recompute + update (debounced)
    // -------------------------

    public void scheduleRecompute() {
        if (recomputeTaskId != -1) return;

        recomputeTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            recomputeTaskId = -1;
            recomputeAndUpdateDisplays();
        }, 20L).getTaskId();
    }

    public void recomputeAndUpdateDisplays() {

        // 1) RAREST FISH CAUGHT (player)
        UUID bestRarestPlayer = null;
        double bestRarestValue = -1;
        String bestRarestName = null;

        ConfigurationSection playersSec = data.getConfigurationSection("stats.players");
        if (playersSec != null) {
            for (String uuidStr : playersSec.getKeys(false)) {
                double v = data.getDouble("stats.players." + uuidStr + ".rarestFishValue", -1);
                if (v > bestRarestValue) {
                    bestRarestValue = v;
                    try {
                        bestRarestPlayer = UUID.fromString(uuidStr);
                        OfflinePlayer op = Bukkit.getOfflinePlayer(bestRarestPlayer);
                        bestRarestName = (op.getName() != null) ? op.getName() : data.getString("stats.players." + uuidStr + ".name");
                    } catch (Exception ignored) {}
                }
            }
        }

        if (bestRarestPlayer != null) {
            setWinner("rarest_fish", bestRarestPlayer, bestRarestName, bestRarestValue);
        } else {
            clearWinner("rarest_fish");
        }

        // 2) MOST FILLED JOURNAL (player)
        UUID bestJournalPlayer = null;
        double bestJournalValue = -1;
        String bestJournalName = null;

        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getUniqueId() == null) continue;

            double completion = journalManager.getCompletionPercent(op.getUniqueId());
            if (completion > bestJournalValue) {
                bestJournalValue = completion;
                bestJournalPlayer = op.getUniqueId();
                bestJournalName = op.getName();
            }
        }

        if (bestJournalPlayer != null) {
            setWinner("most_filled_journal", bestJournalPlayer, bestJournalName, bestJournalValue);
        } else {
            clearWinner("most_filled_journal");
        }

        // 3) BIGGEST CLAIM RADIUS (team)
        String bestTeamName = null;
        double bestRadius = -1;
        Team bestTeam = null;

        for (Team t : teamManager.getAllTeams()) {
            if (t == null) continue;
            int radius = t.getClaimRadius();
            if (radius > bestRadius) {
                bestRadius = radius;
                bestTeamName = t.getName();
                bestTeam = t;
            }
        }

        if (bestTeamName != null) {
            setTeamWinner("biggest_claim", bestTeamName, bestRadius);
            setClaimBannerDesignFromTeam(bestTeam);
        } else {
            clearTeamWinner("biggest_claim");
            data.set("claimBanner.design", null);
        }

        save();

        // Update world displays
        display.updateAll(this);
    }

    // -------------------------
    // Hooks for recording stats
    // -------------------------

    public void recordRarestFish(UUID player, String name, double rarityValue) {
        if (player == null) return;

        String p = "stats.players." + player.toString();
        data.set(p + ".name", name);

        double current = data.getDouble(p + ".rarestFishValue", -1);
        if (rarityValue > current) {
            data.set(p + ".rarestFishValue", rarityValue);
            save();
        }
    }

    public double getPlayerRarestValue(UUID player) {
        if (player == null) return -1;
        return data.getDouble("stats.players." + player + ".rarestFishValue", -1);
    }

    // -------------------------
    // Remove everything
    // -------------------------

    public void removeAllDisplays() {
        display.removeAll(this);

        data.set("entities", null);
        data.set("displays", null);
        data.set("boards", null);
        data.set("claimBanner", null);

        // data.set("stats", null); // optional wipe

        save();
    }
}
