package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.Random;

public class CropGrowthBoostListener implements Listener {

    private final TeamManager teams;
    private final Random rng = new Random();

    /**
     * Must match BeaconEffectManager's radius multiplier upgrade id.
     * If you ever rename it in the shop, rename it here too.
     */
    private static final String RADIUS_UPGRADE_ID = "radius";

    /**
     * Base chance to apply an extra growth stage when inside effect radius.
     * 0.35 = 35% of growth ticks become "double growth".
     */
    private static final double BASE_DOUBLE_GROW_CHANCE = 0.35;

    public CropGrowthBoostListener(TeamManager teams) {
        this.teams = teams;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (block == null) return;

        BlockState newState = event.getNewState();
        if (newState == null) return;

        // Only ageable crops (wheat/carrots/potatoes/beetroot/nether wart/cocoa/etc.)
        if (!(newState.getBlockData() instanceof Ageable ageable)) return;

        Location loc = block.getLocation();
        Team team = getTeamWhoseEffectRadiusContains(loc);
        if (team == null) return;

        // Chance-based acceleration (keeps it feeling vanilla-ish, not instant farms)
        double chance = BASE_DOUBLE_GROW_CHANCE;

        if (chance > 0.90) chance = 0.90; // cap

        if (rng.nextDouble() > chance) return;

        int age = ageable.getAge();
        int max = ageable.getMaximumAge();
        if (age >= max) return;

        // Apply ONE extra stage of growth
        ageable.setAge(Math.min(max, age + 1));
        newState.setBlockData(ageable);
    }

    // -------------------------
    // Effect-radius check (dimension-aware)
    // -------------------------

    private Team getTeamWhoseEffectRadiusContains(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;

        for (Team team : teams.getAllTeams()) {
            if (team == null) continue;
            if (isInsideEffectRadius(loc, team)) return team;
        }
        return null;
    }

    private boolean isInsideEffectRadius(Location loc, Team team) {
        World w = loc.getWorld();
        if (w == null) return false;

        Location banner = getBannerForWorld(team, w);
        if (banner == null || banner.getWorld() == null) return false;

        if (!banner.getWorld().equals(w)) return false;

        int radiusTiles = Math.max(1, team.getClaimRadius());

        double mult = getEffectRadiusMultiplier(team);
        int halfSize = (int) Math.round(radiusTiles * 8.0 * mult);

        int dx = loc.getBlockX() - banner.getBlockX();
        int dz = loc.getBlockZ() - banner.getBlockZ();

        return Math.abs(dx) <= halfSize && Math.abs(dz) <= halfSize;
    }

    private Location getBannerForWorld(Team team, World w) {
        World.Environment env = w.getEnvironment();

        if (env == World.Environment.NORMAL) {
            return team.getBannerLocation();
        }
        if (env == World.Environment.NETHER) {
            return team.getDimensionalBanner("NETHER");
        }
        if (env == World.Environment.THE_END) {
            Location l = team.getDimensionalBanner("END");
            if (l == null) l = team.getDimensionalBanner("THE_END");
            return l;
        }

        // custom dimensions fallback
        return team.getBannerLocation();
    }

    private double getEffectRadiusMultiplier(Team team) {
        int lvl = team.getEffectLevel(RADIUS_UPGRADE_ID);
        if (lvl <= 0) return 1.0;
        if (lvl == 1) return 1.5;
        return 2.0;
    }
}
