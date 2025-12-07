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

        saveTeam(team); // 💾 persist immediately

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

        // 💾 remove from config and save
        if (teamsConfig == null) {
            initStorage();
        }
        String path = "teams." + key;
        teamsConfig.set(path, null);
        try {
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml while disbanding team " + team.getName() + ": " + e.getMessage());
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

        saveTeam(team);

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
            disbandTeam(team); // disbandTeam already updates config
        } else {
            saveTeam(team); // 💾 member list changed
        }

        return team;
    }

    public void transferOwner(Team team, UUID newOwner) {
        if (team == null || newOwner == null) return;

        team.setOwner(newOwner);
        saveTeam(team);
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
     * Saves a single team to teams.yml using Team.serialize().
     */
    public void saveTeam(Team team) {
        if (team == null) return;

        if (teamsConfig == null) {
            initStorage();
        }

        String key = "teams." + team.getName().toLowerCase(Locale.ROOT);

        // Overwrite this team's section
        teamsConfig.set(key, null);
        teamsConfig.createSection(key, team.serialize());

        try {
            plugin.getLogger().info("Saved team " + team.getName() + " to teams.yml.");
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml for team " + team.getName() + ": " + e.getMessage());
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

            Team team = Team.deserialize(teamSection);

            if (team == null) continue;

            String nameKey = team.getName().toLowerCase(Locale.ROOT);

            teamsByName.put(nameKey, team);
            for (UUID uuid : team.getMembers()) {
                teamsByPlayer.put(uuid, team);
            }
        }

    }

    /**
     * Reloads the on‑disk teams.yml and synchronizes any changes into the existing
     * in‑memory {@link Team} instances.  This method attempts to update
     * existing team objects rather than discarding them, so that any code
     * holding references to {@link Team} objects (GUI holders, event
     * listeners, etc.) will continue to operate on updated data.  New
     * teams defined in the file will be created, while teams that no
     * longer exist in the file will be removed and disbanded.
     *
     * <p>Fields synchronized include:</p>
     *
     * <ul>
     *   <li>Owner UUID</li>
     *   <li>Member list</li>
     *   <li>Lives</li>
     *   <li>Claim radius</li>
     *   <li>Vault size</li>
     *   <li>Beacon effect levels</li>
     *   <li>Banner location</li>
     *   <li>Banner design (material and patterns)</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public void reloadTeamsFromFile() {
        // Ensure storage exists and reload the YAML configuration from disk.
        if (teamsFile == null || teamsConfig == null) {
            initStorage();
        }
        // Reload file to capture external modifications
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);

        ConfigurationSection teamsSection = teamsConfig.getConfigurationSection("teams");
        if (teamsSection == null) {
            // If the file has no teams, remove all existing teams
            for (Team existing : new java.util.ArrayList<>(teamsByName.values())) {
                disbandTeam(existing);
            }
            return;
        }

        // Track existing team keys to detect removals
        Set<String> remainingKeys = new HashSet<>(teamsByName.keySet());

        for (String key : teamsSection.getKeys(false)) {
            ConfigurationSection teamSection = teamsSection.getConfigurationSection(key);
            if (teamSection == null) {
                continue;
            }

            // Deserialize a fresh team from the YAML section to use as a template
            Team loadedTeam = Team.deserialize(teamSection);
            if (loadedTeam == null) {
                continue;
            }

            String nameKey = loadedTeam.getName().toLowerCase(java.util.Locale.ROOT);
            Team existing = teamsByName.get(nameKey);
            if (existing != null) {
                // Remove this key from remainingKeys since it's still present
                remainingKeys.remove(nameKey);

                // Update owner
                java.util.UUID newOwner = loadedTeam.getOwner();
                if (newOwner != null && !newOwner.equals(existing.getOwner())) {
                    existing.setOwner(newOwner);
                }

                // Synchronize member list: remove members not present, add new ones
                // Build sets for comparison
                java.util.Set<java.util.UUID> existingMembers = new java.util.HashSet<>(existing.getMembers());
                java.util.Set<java.util.UUID> loadedMembers   = new java.util.HashSet<>(loadedTeam.getMembers());

                // Remove players who are no longer in the team
                for (java.util.UUID uuid : existingMembers) {
                    if (!loadedMembers.contains(uuid)) {
                        existing.removeMember(uuid);
                        teamsByPlayer.remove(uuid);
                    }
                }
                // Add players who are new
                for (java.util.UUID uuid : loadedMembers) {
                    if (!existingMembers.contains(uuid)) {
                        existing.addMember(uuid);
                        teamsByPlayer.put(uuid, existing);
                    }
                }

                // Update numeric fields
                existing.setLives(loadedTeam.getLives());
                existing.setClaimRadius(loadedTeam.getClaimRadius());
                existing.setVaultSize(loadedTeam.getVaultSize());

                // Synchronize beacon effects
                existing.setEffectMap(loadedTeam.getEffectMap());

                // Update banner location
                existing.setBannerLocation(loadedTeam.getBannerLocation());

                // Update banner design (material and patterns)
                if (loadedTeam.getBannerMaterial() != null) {
                    // Clone the design via an ItemStack to avoid internal modifications
                    existing.setBannerDesign(loadedTeam.createBannerItem());
                } else {
                    existing.clearBannerDesign();
                }

            } else {
                // New team: add to in‑memory collections
                teamsByName.put(nameKey, loadedTeam);
                for (java.util.UUID uuid : loadedTeam.getMembers()) {
                    teamsByPlayer.put(uuid, loadedTeam);
                }
            }
        }

        // Any keys remaining in remainingKeys are teams that no longer exist in the file
        for (String removedKey : remainingKeys) {
            Team toRemove = teamsByName.get(removedKey);
            if (toRemove != null) {
                disbandTeam(toRemove);
            }
        }
    }
}
