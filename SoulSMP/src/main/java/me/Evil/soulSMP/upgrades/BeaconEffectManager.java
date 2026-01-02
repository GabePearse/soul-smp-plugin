package me.Evil.soulSMP.upgrades;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
     * ✅ Now supports an "effect radius multiplier" upgrade:
     * - level 0 => x1.0
     * - level 1 => x1.5
     * - level 2+ => x2.0
     */
    private boolean isInsideClaim(Player p, Team team) {
        if (!team.hasBannerLocation()) return false;

        Location banner = team.getBannerLocation();
        if (banner.getWorld() == null || !banner.getWorld().equals(p.getWorld())) return false;

        int radiusTiles = Math.max(1, team.getClaimRadius());

        double mult = getEffectRadiusMultiplier(team);
        int halfSize = (int) Math.round(radiusTiles * 8.0 * mult);

        int dx = p.getLocation().getBlockX() - banner.getBlockX();
        int dz = p.getLocation().getBlockZ() - banner.getBlockZ();

        return Math.abs(dx) <= halfSize && Math.abs(dz) <= halfSize;
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
        // ✅ FREE saturation inside claim (benefits from radius multiplier because it shares the same inside-claim check)
        applyFreeSaturation(p);

        applyEffect(p, team, "speed",      PotionEffectType.SPEED);
        applyEffect(p, team, "haste",      PotionEffectType.HASTE);
        applyEffect(p, team, "strength",   PotionEffectType.STRENGTH);
        applyEffect(p, team, "regen",      PotionEffectType.REGENERATION);
        applyEffect(p, team, "resistance", PotionEffectType.RESISTANCE);
        applyEffect(p, team, "jump",       PotionEffectType.JUMP_BOOST);
    }

    /**
     * Gives free Saturation while inside claim.
     *
     * We intentionally use a short duration so it naturally expires shortly after leaving the claim
     * (since tick() only applies effects while inside claim).
     */
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

        // amplifiers start at 0 (I = 0, II = 1, etc.)
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
