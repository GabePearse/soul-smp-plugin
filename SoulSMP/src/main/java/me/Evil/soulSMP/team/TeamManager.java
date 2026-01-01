package me.Evil.soulSMP.team;

import me.Evil.soulSMP.leaderboard.LeaderboardManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private final Plugin plugin;

    private final Map<String, Team> teamsByName = new HashMap<>();
    private final Map<UUID, Team> teamsByPlayer = new HashMap<>();

    private File teamsFile;
    private FileConfiguration teamsConfig;

    // ✅ Optional hook so TeamManager can trigger leaderboard recomputes when claim changes
    private LeaderboardManager leaderboard;

    public TeamManager(Plugin plugin) {
        this.plugin = plugin;
        initStorage(); // ✅ centralize file creation / resource copy
    }

    /** Reads max team members from plugin config.yml (NOT teams.yml). */
    public int getMaxTeamMembers() {
        // Works for Paper/Spigot: your plugin main class extends JavaPlugin, so Plugin should be a JavaPlugin.
        if (plugin instanceof JavaPlugin jp) {
            return Math.max(1, jp.getConfig().getInt("teams.max-members", 3));
        }
        return 3;
    }

    /** Call this once after LeaderboardManager is constructed (onEnable / reload). */
    public void setLeaderboard(LeaderboardManager leaderboard) {
        this.leaderboard = leaderboard;
    }

    /**
     * Preferred way to change claim radius so leaderboard auto-updates.
     * Use this instead of team.setClaimRadius(...) directly.
     */
    public void setClaimRadius(Team team, int newRadius) {
        if (team == null) return;

        int before = team.getClaimRadius();
        team.setClaimRadius(newRadius);
        saveTeam(team);

        if (leaderboard != null && newRadius != before) {
            leaderboard.scheduleRecompute();
        }
    }

    /**
     * Ensures plugins/<PluginName>/teams.yml exists.
     * If missing, tries to copy from jar resources (src/main/resources/teams.yml).
     * If resource copy fails, creates an empty file as a last resort.
     */
    private void initStorage() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        teamsFile = new File(plugin.getDataFolder(), "teams.yml");

        // If missing, prefer the packaged resource
        if (!teamsFile.exists()) {
            boolean copied = false;

            try {
                plugin.saveResource("teams.yml", false);
                copied = teamsFile.exists();
            } catch (IllegalArgumentException ex) {
                // teams.yml not present in jar resources
                plugin.getLogger().warning("teams.yml not found in plugin jar resources. Will create a blank teams.yml.");
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to copy teams.yml from resources: " + ex.getMessage());
            }

            // Last resort: create an empty file
            if (!copied) {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    teamsFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create teams.yml: " + e.getMessage());
                }
            }
        }

        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
    }

    // --- Team creation ---

    public Team createTeam(String name, Player owner) {
        String key = name.toLowerCase(Locale.ROOT);
        if (teamsByName.containsKey(key)) return null;

        Team team = new Team(name, owner.getUniqueId(), getMaxTeamMembers());
        teamsByName.put(key, team);
        teamsByPlayer.put(owner.getUniqueId(), team);

        saveTeam(team);
        return team;
    }

    public void disbandTeam(Team team) {
        if (team == null) return;

        String key = team.getName().toLowerCase(Locale.ROOT);
        teamsByName.remove(key);

        for (UUID uuid : team.getMembers()) teamsByPlayer.remove(uuid);

        if (teamsConfig == null) initStorage();

        teamsConfig.set("teams." + key, null);
        try { teamsConfig.save(teamsFile); }
        catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml while disbanding: " + e.getMessage());
        }

        // If team changes could impact biggest-claim leaderboard, recompute (debounced)
        if (leaderboard != null) leaderboard.scheduleRecompute();
    }

    // --- Lookups ---

    public Team getTeamByName(String name) {
        if (name == null) return null;
        return teamsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public Team getTeamByPlayer(Player p) {
        if (p == null) return null;
        return teamsByPlayer.get(p.getUniqueId());
    }

    public Team getTeamByPlayer(UUID uuid) { return teamsByPlayer.get(uuid); }

    public boolean isInTeam(Player p) { return getTeamByPlayer(p) != null; }

    public Collection<Team> getAllTeams() {
        return Collections.unmodifiableCollection(teamsByName.values());
    }

    // --- Membership / invites ---

    private final Map<UUID, TeamInvite> pendingInvites = new HashMap<>();

    public static class TeamInvite {
        public final Team team;
        public final long expiresAt;
        public TeamInvite(Team t, long exp) { this.team = t; this.expiresAt = exp; }
    }

    public boolean sendInvite(Player inviter, Player target) {
        Team inviterTeam = getTeamByPlayer(inviter);
        if (inviterTeam == null) return false;

        if (getTeamByPlayer(target) != null) return false;

        UUID id = target.getUniqueId();
        pendingInvites.put(id, new TeamInvite(inviterTeam,
                System.currentTimeMillis() + 60_000));

        return true;
    }

    public Team acceptInvite(Player p) {
        UUID id = p.getUniqueId();

        TeamInvite inv = pendingInvites.get(id);
        if (inv == null) return null;

        if (System.currentTimeMillis() > inv.expiresAt) {
            pendingInvites.remove(id);
            return null;
        }

        pendingInvites.remove(id);
        addPlayerToTeam(inv.team, p);

        return inv.team;
    }

    public boolean hasPendingInvite(Player p) {
        return pendingInvites.containsKey(p.getUniqueId());
    }

    public JoinResult addPlayerToTeam(Team team, Player p) {
        if (team == null || p == null) return JoinResult.ERROR;

        UUID id = p.getUniqueId();

        if (teamsByPlayer.containsKey(id)) return JoinResult.ALREADY_IN_TEAM;
        if (team.isFull()) return JoinResult.TEAM_FULL;

        if (!team.addMember(id)) return JoinResult.ERROR;

        teamsByPlayer.put(id, team);
        saveTeam(team);

        return JoinResult.SUCCESS;
    }

    public Team removePlayerFromTeam(Player p) {
        if (p == null) return null;

        UUID id = p.getUniqueId();
        Team team = teamsByPlayer.remove(id);
        if (team == null) return null;

        team.removeMember(id);

        if (team.getMembers().isEmpty())
            disbandTeam(team);
        else saveTeam(team);

        return team;
    }

    public void transferOwner(Team team, UUID newOwner) {
        if (team == null) return;
        team.setOwner(newOwner);
        saveTeam(team);
    }

    public enum JoinResult { SUCCESS, TEAM_FULL, ALREADY_IN_TEAM, ERROR }

    // --- Banner design uniqueness ---

    public Team getTeamByBannerItem(ItemStack stack) {
        if (stack == null) return null;

        for (Team t : teamsByName.values()) {
            if (t.hasClaimedBannerDesign() && t.matchesBannerDesign(stack))
                return t;
        }
        return null;
    }

    // --- Border visualization ---

    public void showTeamBorder(Player p) {
        Team t = getTeamByPlayer(p);
        if (t == null) { p.sendMessage(ChatColor.RED + "You are not in a team."); return; }
        if (!t.hasBannerLocation()) { p.sendMessage(ChatColor.RED + "Your team has no banner placed."); return; }

        Location c = t.getBannerLocation();
        World w = c.getWorld();
        if (w == null) return;

        // Recommended: don't try to show an overworld claim while player is in nether/end
        if (p.getWorld() != w) {
            p.sendMessage(ChatColor.RED + "You must be in " + w.getName() + " to view your claim border.");
            return;
        }

        int tiles = Math.max(1, t.getClaimRadius());
        int half = tiles * 8;

        int minX = c.getBlockX() - half;
        int maxX = c.getBlockX() + half;
        int minZ = c.getBlockZ() - half;
        int maxZ = c.getBlockZ() + half;

        new BukkitRunnable() {
            int ticks = 0;

            @Override public void run() {
                if (ticks >= 200) { cancel(); return; }

                int playerY = p.getLocation().getBlockY(); // player's Y, not banner's
                draw(w, minX, maxX, minZ, maxZ, playerY);

                ticks += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        p.sendMessage(ChatColor.AQUA + "Showing border for 10 seconds...");
    }

    private void draw(World w, int minX, int maxX, int minZ, int maxZ, int centerY) {
        int minY = Math.max(centerY - 10, w.getMinHeight());
        int maxY = Math.min(centerY + 20, w.getMaxHeight() - 1);

        for (int yy = minY; yy <= maxY; yy++) {
            for (int x = minX; x <= maxX; x++) {
                w.spawnParticle(Particle.GLOW, x + 0.5, yy + 0.5, minZ + 0.5, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.GLOW, x + 0.5, yy + 0.5, maxZ + 0.5, 1, 0, 0, 0, 0);
            }
            for (int z = minZ; z <= maxZ; z++) {
                w.spawnParticle(Particle.GLOW, minX + 0.5, yy + 0.5, z + 0.5, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.GLOW, maxX + 0.5, yy + 0.5, z + 0.5, 1, 0, 0, 0, 0);
            }
        }
    }


    // --- Persistence ---

    public void saveTeams() {
        if (teamsConfig == null) initStorage();

        Map<String, Object> out = new LinkedHashMap<>();
        for (Team t : teamsByName.values()) {
            out.put(t.getName().toLowerCase(Locale.ROOT), t.serialize());
        }

        teamsConfig.set("teams", out);

        try { teamsConfig.save(teamsFile); }
        catch (IOException e) { plugin.getLogger().severe("saveTeams: " + e.getMessage()); }
    }

    public void saveTeam(Team t) {
        if (t == null) return;
        if (teamsConfig == null) initStorage();

        String key = "teams." + t.getName().toLowerCase(Locale.ROOT);
        teamsConfig.set(key, t.serialize());

        try { teamsConfig.save(teamsFile); }
        catch (IOException e) {
            plugin.getLogger().severe("Could not save team " + t.getName() + ": " + e.getMessage());
        }
    }

    public void loadTeams() {
        teamsByName.clear();
        teamsByPlayer.clear();

        if (teamsConfig == null) initStorage();

        ConfigurationSection teamsSec = teamsConfig.getConfigurationSection("teams");
        if (teamsSec == null) return;

        int maxMembers = getMaxTeamMembers();

        for (String key : teamsSec.getKeys(false)) {
            Team t = Team.deserialize(teamsSec.getConfigurationSection(key), maxMembers);
            if (t == null) continue;

            String nameKey = t.getName().toLowerCase(Locale.ROOT);
            teamsByName.put(nameKey, t);
            for (UUID uuid : t.getMembers()) teamsByPlayer.put(uuid, t);
        }
    }

    public void reloadTeamsFromFile() {
        if (teamsFile == null || teamsConfig == null) initStorage();

        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);

        ConfigurationSection sec = teamsConfig.getConfigurationSection("teams");
        if (sec == null) {
            for (Team t : new ArrayList<>(teamsByName.values())) disbandTeam(t);
            return;
        }

        int maxMembers = getMaxTeamMembers();
        Set<String> remaining = new HashSet<>(teamsByName.keySet());

        for (String key : sec.getKeys(false)) {
            var teamSec = sec.getConfigurationSection(key);
            if (teamSec == null) continue;

            Team loaded = Team.deserialize(teamSec, maxMembers);
            if (loaded == null) continue;

            String nameKey = loaded.getName().toLowerCase(Locale.ROOT);
            Team existing = teamsByName.get(nameKey);

            if (existing != null) {
                remaining.remove(nameKey);

                existing.setOwner(loaded.getOwner());
                existing.setLives(loaded.getLives());
                existing.setClaimRadius(loaded.getClaimRadius());
                existing.setVaultSize(loaded.getVaultSize());
                existing.setEffectMap(loaded.getEffectMap());
                existing.setBannerLocation(loaded.getBannerLocation());
                if (loaded.getBannerMaterial() != null)
                    existing.setBannerDesign(loaded.createBannerItem());
                else existing.clearBannerDesign();

                existing.setLastUpkeepPaymentMillis(loaded.getLastUpkeepPaymentMillis());
                existing.setUnpaidWeeks(loaded.getUnpaidWeeks());
                existing.setUpkeepStatus(loaded.getUpkeepStatus());
                existing.setBaseClaimRadiusForUpkeep(loaded.getBaseClaimRadiusForUpkeep());

                // Member diff/sync:
                // NOTE: existing Team's maxMembers is whatever it was created with.
                // If you want it to update live when config changes, you should recreate Team objects
                // (or refactor Team to consult TeamManager each time). Keeping it stable is usually fine.

                Set<UUID> old = new HashSet<>(existing.getMembers());
                Set<UUID> nw = new HashSet<>(loaded.getMembers());

                for (UUID u : old) {
                    if (!nw.contains(u)) {
                        existing.removeMember(u);
                        teamsByPlayer.remove(u);
                    }
                }
                for (UUID u : nw) {
                    if (!old.contains(u)) {
                        // Raw add so reload doesn't silently fail because of cap
                        existing.addMemberRaw(u);
                        teamsByPlayer.put(u, existing);
                    }
                }

                saveTeam(existing);

            } else {
                teamsByName.put(nameKey, loaded);
                for (UUID uuid : loaded.getMembers())
                    teamsByPlayer.put(uuid, loaded);

                saveTeam(loaded);
            }
        }

        for (String k : remaining) {
            Team t = teamsByName.get(k);
            if (t != null) disbandTeam(t);
        }

        // If claim radii changed across reload, recompute (debounced)
        if (leaderboard != null) leaderboard.scheduleRecompute();
    }
}
