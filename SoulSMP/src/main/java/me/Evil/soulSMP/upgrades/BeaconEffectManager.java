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

    public BeaconEffectManager(TeamManager teams) {
        this.teams = teams;
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team team = teams.getTeamByPlayer(p);

            if (team == null || !isInsideClaim(p, team)) {
                clearAll(p);
                continue;
            }

            apply(p, team);
        }
    }

    /**
     * Matches your claim logic:
     *  halfSize = claimRadius * 8 on each axis (square, not circle).
     */
    private boolean isInsideClaim(Player p, Team team) {
        if (!team.hasBannerLocation()) return false;

        Location banner = team.getBannerLocation();
        if (banner.getWorld() == null || !banner.getWorld().equals(p.getWorld())) return false;

        int radiusTiles = Math.max(1, team.getClaimRadius());
        int halfSize = radiusTiles * 8; // ✅ same as BannerListener

        int dx = p.getLocation().getBlockX() - banner.getBlockX();
        int dz = p.getLocation().getBlockZ() - banner.getBlockZ();

        return Math.abs(dx) <= halfSize && Math.abs(dz) <= halfSize;
    }

    private void apply(Player p, Team team) {
        applyEffect(p, team, "speed",      PotionEffectType.SPEED);
        applyEffect(p, team, "haste",      PotionEffectType.HASTE);        // or FAST_DIGGING on older APIs
        applyEffect(p, team, "strength",   PotionEffectType.STRENGTH);     // or INCREASE_DAMAGE
        applyEffect(p, team, "regen",      PotionEffectType.REGENERATION);
        applyEffect(p, team, "resistance", PotionEffectType.RESISTANCE);   // or DAMAGE_RESISTANCE
        applyEffect(p, team, "jump",       PotionEffectType.JUMP_BOOST);   // or JUMP
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
                40,           // 2 seconds, refreshed every tick() call
                level - 1,
                true,         // ambient
                false,        // particles
                false         // icon
        ));
    }

    private void clearAll(Player p) {
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.HASTE);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }
}
