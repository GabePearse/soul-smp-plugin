package me.Evil.soulSMP.beacon;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BeaconEffectManager {

    private final TeamManager teams;

    /**
     * The upgrade id your effect shop sells for effect-radius.
     * This multiplies the radius used to determine whether players are "inside claim" for effects.
     */
    private static final String RADIUS_UPGRADE_ID = "radius";

    public BeaconEffectManager(TeamManager teams) {
        this.teams = teams;
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team team = teams.getTeamByPlayer(p);

            if (team == null || !isInsideClaim(p, team)) {
                // NOTE: We don't force-remove effects here because:
                // - paid effects are removed by applyEffect() when level <= 0 (only called inside claim)
                // - free saturation uses a short duration and will expire quickly outside the claim
                continue;
            }

            apply(p, team);
        }
    }

    /**
     * Matches your claim logic:
     *  halfSize = claimRadius * 8 on each axis (square, not circle).
     *
     * ✅ Dimension-aware:
     * - Overworld => main bannerLocation
     * - Nether    => dimensional banner "NETHER"
     * - End       => dimensional banner "END" (or "THE_END")
     *
     * ✅ Supports an "effect radius multiplier" upgrade:
     * - level 0 => x1.0
     * - level 1 => x1.5
     * - level 2+ => x2.0
     */
    private boolean isInsideClaim(Player p, Team team) {
        if (p == null || team == null) return false;

        Location banner = getBannerForPlayerDimension(p, team);
        if (banner == null || banner.getWorld() == null) return false;

        // Must be in the same world as the banner center for this dimension
        if (!banner.getWorld().equals(p.getWorld())) return false;

        int radiusTiles = Math.max(1, team.getClaimRadius());

        double mult = getEffectRadiusMultiplier(team);
        int halfSize = (int) Math.round(radiusTiles * 8.0 * mult);

        int dx = p.getLocation().getBlockX() - banner.getBlockX();
        int dz = p.getLocation().getBlockZ() - banner.getBlockZ();

        return Math.abs(dx) <= halfSize && Math.abs(dz) <= halfSize;
    }

    /**
     * Picks the correct claim center banner based on the player's current dimension.
     */
    private Location getBannerForPlayerDimension(Player p, Team team) {
        World w = p.getWorld();
        if (w == null) return null;

        World.Environment env = w.getEnvironment();

        // Overworld uses main banner location
        if (env == World.Environment.NORMAL) {
            return team.getBannerLocation();
        }

        // Nether uses dimensional banner
        if (env == World.Environment.NETHER) {
            return team.getDimensionalBanner("NETHER");
        }

        // End uses dimensional banner (support both keys)
        if (env == World.Environment.THE_END) {
            Location loc = team.getDimensionalBanner("END");
            if (loc == null) loc = team.getDimensionalBanner("THE_END");
            return loc;
        }

        // Fallback for custom dimensions
        return team.getBannerLocation();
    }

    private double getEffectRadiusMultiplier(Team team) {
        if (team == null) return 1.0;

        int lvl = team.getEffectLevel(RADIUS_UPGRADE_ID);

        if (lvl <= 0) return 1.0;
        if (lvl == 1) return 1.5;

        // lvl 2+ clamps to 2.0
        return 2.0;
    }

    private void apply(Player p, Team team) {
        // ✅ FREE saturation inside claim
        applyFreeSaturation(p);

        applyEffect(p, team, "speed",      PotionEffectType.SPEED);
        applyEffect(p, team, "haste",      PotionEffectType.HASTE);
        applyEffect(p, team, "strength",   PotionEffectType.STRENGTH);
        applyEffect(p, team, "regen",      PotionEffectType.REGENERATION);
        applyEffect(p, team, "resistance", PotionEffectType.RESISTANCE);
        applyEffect(p, team, "jump",       PotionEffectType.JUMP_BOOST);
    }

    private void applyFreeSaturation(Player p) {
        if (p == null) return;

        p.addPotionEffect(new PotionEffect(
                PotionEffectType.SATURATION,
                40,     // 2 seconds
                0,      // Saturation I
                true,   // ambient
                false,  // particles
                false   // icon
        ));
    }

    private void applyEffect(Player p, Team team, String id, PotionEffectType type) {
        int level = team.getEffectLevel(id);
        if (level <= 0) {
            p.removePotionEffect(type);
            return;
        }

        p.addPotionEffect(new PotionEffect(
                type,
                20,           // 1 second, refreshed every tick() call
                level - 1,
                true,         // ambient
                false,        // particles
                false         // icon
        ));
    }
}
