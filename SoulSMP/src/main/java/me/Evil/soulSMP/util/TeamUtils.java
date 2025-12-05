package me.Evil.soulSMP.util;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Utility methods for mutating Team data and immediately persisting it via TeamManager.
 *
 * Usage:
 *   TeamUtils.setVaultSize(teamManager, team, 9);
 *   TeamUtils.setClaimRadius(teamManager, team, 3);
 *   TeamUtils.setBannerDesign(teamManager, team, bannerItem);
 */
public final class TeamUtils {

    private TeamUtils() {
        // utility class, no instances
    }

    // ==========================
    // Core safety helper
    // ==========================

    private static boolean canMutate(TeamManager manager, Team team) {
        return manager != null && team != null;
    }

    // ==========================
    // Vault size
    // ==========================

    public static void setVaultSize(TeamManager manager, Team team, int size) {
        if (!canMutate(manager, team)) return;

        team.setVaultSize(size);
        manager.saveTeam(team);
    }

    // Optional helper: increase/decrease vault size
    public static void addVaultSlots(TeamManager manager, Team team, int amount, int maxSize) {
        if (!canMutate(manager, team)) return;

        int newSize = team.getVaultSize() + amount;
        if (newSize < 0) newSize = 0;
        if (newSize > maxSize) newSize = maxSize;

        team.setVaultSize(newSize);
        manager.saveTeam(team);
    }

    // ==========================
    // Claim radius
    // ==========================

    public static void setClaimRadius(TeamManager manager, Team team, int radius) {
        if (!canMutate(manager, team)) return;

        if (radius < 1) radius = 1;
        team.setClaimRadius(radius);
        manager.saveTeam(team);
    }

    // ==========================
    // Lives
    // ==========================

    public static void setLives(TeamManager manager, Team team, int lives) {
        if (!canMutate(manager, team)) return;

        team.setLives(lives);
        manager.saveTeam(team);
    }

    public static void addLives(TeamManager manager, Team team, int amount) {
        if (!canMutate(manager, team)) return;

        team.addLives(amount);
        manager.saveTeam(team);
    }

    public static void removeLife(TeamManager manager, Team team) {
        if (!canMutate(manager, team)) return;

        team.removeLife();
        manager.saveTeam(team);
    }

    // ==========================
    // Owner
    // ==========================

    public static void setOwner(TeamManager manager, Team team, UUID newOwner) {
        if (!canMutate(manager, team)) return;

        team.setOwner(newOwner);
        manager.saveTeam(team);
    }

    // ==========================
    // Banner location
    // ==========================

    public static void setBannerLocation(TeamManager manager, Team team, Location loc) {
        if (!canMutate(manager, team)) return;

        team.setBannerLocation(loc);
        manager.saveTeam(team);
    }

    // ==========================
    // Banner design
    // ==========================

    public static void setBannerDesign(TeamManager manager, Team team, ItemStack bannerItem) {
        if (!canMutate(manager, team)) return;

        team.setBannerDesign(bannerItem);
        manager.saveTeam(team);
    }

    public static void clearBannerDesign(TeamManager manager, Team team) {
        if (!canMutate(manager, team)) return;

        team.clearBannerDesign();
        manager.saveTeam(team);
    }
}
