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
import java.util.regex.Pattern;

public class TeamManager {

    private final Plugin plugin;

    private final Map<String, Team> teamsByName = new HashMap<>();
    private final Map<UUID, Team> teamsByPlayer = new HashMap<>();

    private File teamsFile;
    private FileConfiguration teamsConfig;

    private LeaderboardManager leaderboard;

    // ✅ Same rules as TeamCommand to guarantee safety even if called elsewhere
    private static final Pattern TEAM_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public TeamManager(Plugin plugin) {
        this.plugin = plugin;
        initStorage();
    }

    public int getMaxTeamMembers() {
        if (plugin instanceof JavaPlugin jp) {
            return Math.max(1, jp.getConfig().getInt("teams.max-members", 3));
        }
        return 3;
    }

    public void setLeaderboard(LeaderboardManager leaderboard) {
        this.leaderboard = leaderboard;
    }

    public void setClaimRadius(Team team, int newRadius) {
        if (team == null) return;

        int before = team.getClaimRadius();
        team.setClaimRadius(newRadius);
        saveTeam(team);

        if (leaderboard != null && newRadius != before) {
            leaderboard.scheduleRecompute();
        }
    }

    private void initStorage() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        teamsFile = new File(plugin.getDataFolder(), "teams.yml");

        if (!teamsFile.exists()) {
            boolean copied = false;

            try {
                plugin.saveResource("teams.yml", false);
                copied = teamsFile.exists();
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("teams.yml not found in plugin jar resources. Will create a blank teams.yml.");
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to copy teams.yml from resources: " + ex.getMessage());
            }

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

    // -------------------------
    // Name safety
    // -------------------------

    private boolean isValidTeamName(String name) {
        if (name == null) return false;
        name = name.trim();
        if (!TEAM_NAME_PATTERN.matcher(name).matches()) return false;

        // extra safety; regex already blocks these
        return !name.contains(".") && !name.contains("/") && !name.contains("\\") && !name.contains(":");
    }

    private String teamKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String teamKey(Team team) {
        return teamKey(team.getName());
    }

    // --- Team creation ---

    public Team createTeam(String name, Player owner) {
        if (owner == null) return null;
        if (!isValidTeamName(name)) return null;

        String key = teamKey(name);
        if (teamsByName.containsKey(key)) return null;

        Team team = new Team(name, owner.getUniqueId(), getMaxTeamMembers());

        // ✅ NEW: ensure it starts at 0 lifetime purchases (if your Team ctor doesn’t already)
        // If you add a default in Team constructor you can delete this line.
        team.setLivesPurchased(0);

        teamsByName.put(key, team);
        teamsByPlayer.put(owner.getUniqueId(), team);

        saveTeam(team);
        return team;
    }

    public void disbandTeam(Team team) {
        if (team == null) return;

        String key = teamKey(team);
        teamsByName.remove(key);

        for (UUID uuid : team.getMembers()) teamsByPlayer.remove(uuid);

        if (teamsConfig == null) initStorage();

        teamsConfig.set("teams." + key, null);
        try { teamsConfig.save(teamsFile); }
        catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml while disbanding: " + e.getMessage());
        }

        if (leaderboard != null) leaderboard.scheduleRecompute();
    }

    // --- Lookups ---

    public Team getTeamByName(String name) {
        if (name == null) return null;
        return teamsByName.get(teamKey(name));
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

                int playerY = p.getLocation().getBlockY();
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
            out.put(teamKey(t), t.serialize());
        }

        teamsConfig.set("teams", out);

        try { teamsConfig.save(teamsFile); }
        catch (IOException e) { plugin.getLogger().severe("saveTeams: " + e.getMessage()); }
    }

    public void saveTeam(Team t) {
        if (t == null) return;
        if (teamsConfig == null) initStorage();

        String keyName = t.getName();
        if (!isValidTeamName(keyName)) {
            plugin.getLogger().warning("Refusing to save team with invalid name: '" + keyName + "'");
            return;
        }

        String key = "teams." + teamKey(t);
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

            if (!isValidTeamName(t.getName())) {
                plugin.getLogger().warning("Skipping team with invalid name from teams.yml: '" + t.getName() + "'");
                continue;
            }

            String nameKey = teamKey(t);
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

            if (!isValidTeamName(loaded.getName())) {
                plugin.getLogger().warning("Skipping invalid team during reload: '" + loaded.getName() + "'");
                continue;
            }

            String nameKey = teamKey(loaded.getName());
            Team existing = teamsByName.get(nameKey);

            if (existing != null) {
                remaining.remove(nameKey);

                existing.setOwner(loaded.getOwner());
                existing.setLives(loaded.getLives());

                // ✅ NEW: keep lifetime lives purchase counter in sync
                existing.setLivesPurchased(loaded.getLivesPurchased());

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

        if (leaderboard != null) leaderboard.scheduleRecompute();
    }
}
