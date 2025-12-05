package me.Evil.soulSMP.team;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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
        initStorage();
        loadTeams();
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

        Object sectionObj = teamsConfig.get("teams");
        if (!(sectionObj instanceof Map<?, ?> map)) {
            return; // no teams yet
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) continue;
            if (!(entry.getValue() instanceof Map<?, ?> teamMapRaw)) continue;

            Map<String, Object> teamMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> inner : teamMapRaw.entrySet()) {
                if (inner.getKey() instanceof String s) {
                    teamMap.put(s, inner.getValue());
                }
            }

            Team team = Team.deserialize(key, teamMap);
            String nameKey = team.getName().toLowerCase(Locale.ROOT);

            teamsByName.put(nameKey, team);
            for (UUID uuid : team.getMembers()) {
                teamsByPlayer.put(uuid, team);
            }
        }
    }
}
