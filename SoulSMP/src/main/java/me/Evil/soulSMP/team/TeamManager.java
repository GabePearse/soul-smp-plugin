package me.Evil.soulSMP.team;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages all teams: creation, membership, lookups and persistence.
 */
public class TeamManager {

    private final Plugin plugin;

    // name (lowercase) -> team
    private final Map<String, Team> teamsByName = new HashMap<>();
    // player UUID -> team
    private final Map<UUID, Team> teamsByPlayer = new HashMap<>();

    private File teamsFile;
    private FileConfiguration teamsConfig;

    public TeamManager(Plugin plugin) {
        this.plugin = plugin;

        File file = new File(plugin.getDataFolder(), "teams.yml");
        if (!file.exists()) {
            plugin.saveResource("teams.yml", false);
        }

        teamsFile = file;
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
    }


    // ==========================
    // Storage setup
    // ==========================

    private void initStorage() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        if (!teamsFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                teamsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create teams.yml: " + e.getMessage());
            }
        }

        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
    }

    // ==========================
    // Creation & removal
    // ==========================

    /**
     * Creates a new team with the given name and owner.
     *
     * @return the created Team, or null if a team with that name already exists
     */
    public Team createTeam(String name, Player owner) {
        String key = name.toLowerCase(Locale.ROOT);
        if (teamsByName.containsKey(key)) {
            return null;
        }

        Team team = new Team(name, owner.getUniqueId());
        teamsByName.put(key, team);
        teamsByPlayer.put(owner.getUniqueId(), team);
        return team;
    }

    /**
     * Disbands the given team, removing all references.
     */
    public void disbandTeam(Team team) {
        if (team == null) return;

        String key = team.getName().toLowerCase(Locale.ROOT);
        teamsByName.remove(key);

        for (UUID uuid : team.getMembers()) {
            teamsByPlayer.remove(uuid);
        }
    }

    // ==========================
    // Lookups
    // ==========================

    public Team getTeamByName(String name) {
        if (name == null) return null;
        return teamsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public Team getTeamByPlayer(Player player) {
        if (player == null) return null;
        return teamsByPlayer.get(player.getUniqueId());
    }

    public Team getTeamByPlayer(UUID uuid) {
        if (uuid == null) return null;
        return teamsByPlayer.get(uuid);
    }

    public boolean isInTeam(Player player) {
        return getTeamByPlayer(player) != null;
    }

    public Collection<Team> getAllTeams() {
        return Collections.unmodifiableCollection(teamsByName.values());
    }

    // ==========================
    // Membership
    // ==========================

    // playerUUID -> invite info
    private final Map<UUID, TeamInvite> pendingInvites = new HashMap<>();

    public static class TeamInvite {
        public final Team team;
        public final long expiresAt;

        public TeamInvite(Team team, long expiresAt) {
            this.team = team;
            this.expiresAt = expiresAt;
        }
    }

    public boolean sendInvite(Player inviter, Player target) {
        Team inviterTeam = getTeamByPlayer(inviter);
        if (inviterTeam == null) return false;

        // Only owner can invite OR allow all members — your choice:
        // if (!inviterTeam.getOwner().equals(inviter.getUniqueId())) return false;

        UUID targetId = target.getUniqueId();

        // Already in team
        if (getTeamByPlayer(target) != null) {
            inviter.sendMessage(ChatColor.RED + "That player is already in a team.");
            return false;
        }

        // Overwrite previous invite
        pendingInvites.put(targetId, new TeamInvite(
                inviterTeam,
                System.currentTimeMillis() + 60_000 // expires in 60 seconds
        ));

        return true;
    }

    public Team acceptInvite(Player player) {
        UUID id = player.getUniqueId();

        TeamInvite invite = pendingInvites.get(id);
        if (invite == null) return null;

        // Expired?
        if (System.currentTimeMillis() > invite.expiresAt) {
            pendingInvites.remove(id);
            return null;
        }

        Team team = invite.team;
        pendingInvites.remove(id);

        addPlayerToTeam(team, player);
        return team;
    }

    public boolean hasPendingInvite(Player player) {
        return pendingInvites.containsKey(player.getUniqueId());
    }

    public JoinResult addPlayerToTeam(Team team, Player player) {
        if (team == null || player == null) return JoinResult.ERROR;

        UUID uuid = player.getUniqueId();

        if (teamsByPlayer.containsKey(uuid)) {
            return JoinResult.ALREADY_IN_TEAM;
        }

        if (team.isFull()) {
            return JoinResult.TEAM_FULL;
        }

        boolean added = team.addMember(uuid);
        if (!added) {
            return JoinResult.ERROR;
        }

        teamsByPlayer.put(uuid, team);
        return JoinResult.SUCCESS;
    }

    public Team removePlayerFromTeam(Player player) {
        if (player == null) return null;

        UUID uuid = player.getUniqueId();
        Team team = teamsByPlayer.remove(uuid);
        if (team == null) return null;

        team.removeMember(uuid);

        // If no members left, disband
        if (team.getMembers().isEmpty()) {
            disbandTeam(team);
        }

        return team;
    }

    public enum JoinResult {
        SUCCESS,
        TEAM_FULL,
        ALREADY_IN_TEAM,
        ERROR
    }

    // ==========================
    // Banner design uniqueness
    // ==========================

    /**
     * Returns the team that owns the given banner design (by item), or null if none.
     */
    public Team getTeamByBannerItem(ItemStack stack) {
        if (stack == null) return null;

        for (Team team : teamsByName.values()) {
            if (team.hasClaimedBannerDesign() && team.matchesBannerDesign(stack)) {
                return team;
            }
        }

        return null;
    }

    // ==========================
    // Border Visualization
    // ==========================

    public void showTeamBorder(Player player) {
        Team team = getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        if (!team.hasBannerLocation()) {
            player.sendMessage(ChatColor.RED + "Your team does not have a banner placed.");
            return;
        }

        var center = team.getBannerLocation();
        var world = center.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + "World for your banner is not loaded.");
            return;
        }

        int radiusTiles = Math.max(1, team.getClaimRadius()); // 1 tile = 16x16
        int halfSize = radiusTiles * 8; // ±8, ±16, ±24, etc.

        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();

        int minX = centerX - halfSize;
        int maxX = centerX + halfSize;
        int minZ = centerZ - halfSize;
        int maxZ = centerZ + halfSize;

        player.sendMessage(ChatColor.AQUA + "Showing claim border for 10 seconds...");

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 200) { // 10 seconds @ 10 ticks
                    cancel();
                    return;
                }

                highlightClaimBorder(world, minX, maxX, minZ, maxZ, player);

                ticks += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /**
     * Draws a 3D rectangular border (walls) around the claimed square.
     */
    private void highlightClaimBorder(World world, int minX, int maxX, int minZ, int maxZ, Player player) {
        int baseY = player.getLocation().getBlockY();
        int minY = baseY - 10;
        int maxY = baseY + 20;

        minY = Math.max(minY, world.getMinHeight());
        maxY = Math.min(maxY, world.getMaxHeight());

        for (int y = minY; y <= maxY; y++) {
            // Top & bottom edges
            for (int x = minX; x <= maxX; x++) {
                world.spawnParticle(Particle.GLOW, x + 0.5, y, minZ + 0.5, 0, 0, 0, 0);
                world.spawnParticle(Particle.GLOW, x + 0.5, y, maxZ + 0.5, 0, 0, 0, 0);
            }
            // Left & right edges
            for (int z = minZ; z <= maxZ; z++) {
                world.spawnParticle(Particle.GLOW, minX + 0.5, y, z + 0.5, 0, 0, 0, 0);
                world.spawnParticle(Particle.GLOW, maxX + 0.5, y, z + 0.5, 0, 0, 0, 0);
            }
        }
    }


    // ==========================
    // Persistence
    // ==========================

    /**
     * Saves all teams to teams.yml using Team.serialize().
     */
    public void saveTeams() {
        if (teamsConfig == null) {
            initStorage();
        }

        teamsConfig.set("teams", null);

        Map<String, Object> allTeamsMap = new LinkedHashMap<>();

        for (Team team : teamsByName.values()) {
            allTeamsMap.put(team.getName().toLowerCase(Locale.ROOT), team.serialize());
        }

        teamsConfig.createSection("teams", allTeamsMap);

        try {
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml: " + e.getMessage());
        }
    }

    /**
     * Loads all teams from teams.yml using Team.deserialize().
     */
    @SuppressWarnings("unchecked")
    public void loadTeams() {
        teamsByName.clear();
        teamsByPlayer.clear();

        if (teamsConfig == null) {
            initStorage();
        }

        // "teams" is a ConfigurationSection, not a Map
        ConfigurationSection teamsSection = teamsConfig.getConfigurationSection("teams");
        if (teamsSection == null) {
            return; // no teams yet
        }

        for (String key : teamsSection.getKeys(false)) {
            ConfigurationSection teamSection = teamsSection.getConfigurationSection(key);
            if (teamSection == null) continue;

            Team team = Team.deserialize(teamSection);  // ← new overload

            if (team == null) continue;

            String nameKey = team.getName().toLowerCase(Locale.ROOT);

            teamsByName.put(nameKey, team);
            for (UUID uuid : team.getMembers()) {
                teamsByPlayer.put(uuid, team);
            }
        }

    }
}
