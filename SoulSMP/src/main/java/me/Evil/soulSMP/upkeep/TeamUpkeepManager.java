package me.Evil.soulSMP.upkeep;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

public class TeamUpkeepManager {

    private final Plugin plugin;
    private final TeamManager teamManager;
    private final SoulTokenManager tokenManager;

    private final boolean enabled;
    private final int weeklyCostBase;
    private final int maxWeeksBackpay;
    private final int unstableThresholdDays;
    private final int unprotectedThresholdDays;
    private final String unprotectedBroadcast;

    public TeamUpkeepManager(Plugin plugin,
                             TeamManager teamManager,
                             SoulTokenManager tokenManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.tokenManager = tokenManager;

        var cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("upkeep.enabled", true);
        this.weeklyCostBase = cfg.getInt("upkeep.weekly-cost-base", 10);
        this.maxWeeksBackpay = cfg.getInt("upkeep.max-weeks-backpay", 4);
        this.unstableThresholdDays = cfg.getInt("upkeep.unstable-threshold-days", 14);
        this.unprotectedThresholdDays = cfg.getInt("upkeep.unprotected-threshold-days", 28);
        this.unprotectedBroadcast = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("upkeep.unprotected-broadcast",
                        "&4A team has lost Banner Protection due to unpaid upkeep."));
    }

    // Called e.g. once per day by a repeating task
    public void runDailyCheck() {
        if (!enabled) return;

        Collection<Team> teams = teamManager.getAllTeams();
        for (Team team : teams) {
            updateTeamUpkeep(team);
        }
    }

    /**
     * Recalculate status + penalties for a single team.
     */
    public void updateTeamUpkeep(Team team) {
        long last = team.getLastUpkeepPaymentMillis();
        long now = System.currentTimeMillis();

        // Brand-new / legacy teams: start their upkeep clock now
        if (last <= 0L) {
            team.setLastUpkeepPaymentMillis(now);
            team.setUnpaidWeeks(0);
            team.setUpkeepStatus(UpkeepStatus.PROTECTED);
            team.setBaseClaimRadiusForUpkeep(-1); // optional safety
            teamManager.saveTeam(team);
            return;
        }

        long diffMillis = now - last;
        long days = Duration.ofMillis(diffMillis).toDays();

        // Weeks owed, capped
        int weeksOwed;
        if (days < 7) {
            // No upkeep due yet
            weeksOwed = 0;
        } else {
            // At least 1 week due once they've passed 7 days
            weeksOwed = (int) Math.min(maxWeeksBackpay, (days / 7L));
            if (weeksOwed < 1) weeksOwed = 1;
        }
        team.setUnpaidWeeks(weeksOwed);

        UpkeepStatus oldStatus = team.getUpkeepStatus();
        UpkeepStatus newStatus = computeStatus(days);

        if (newStatus != oldStatus) {
            applyStatusTransition(team, oldStatus, newStatus);
            team.setUpkeepStatus(newStatus);
            teamManager.saveTeam(team);
        } else {
            // Still same status, just ensure penalties line up
            applyStatusPenalties(team, newStatus);
            teamManager.saveTeam(team);
        }
    }

    private UpkeepStatus computeStatus(long days) {
        if (days < unstableThresholdDays) {
            return UpkeepStatus.PROTECTED;
        }
        if (days < unprotectedThresholdDays) {
            return UpkeepStatus.UNSTABLE;
        }
        return UpkeepStatus.UNPROTECTED;
    }

    private void applyStatusTransition(Team team,
                                       UpkeepStatus oldStatus,
                                       UpkeepStatus newStatus) {

        // Handle leaving UNSTABLE/UNPROTECTED → restore everything
        if ((oldStatus == UpkeepStatus.UNSTABLE || oldStatus == UpkeepStatus.UNPROTECTED)
                && newStatus == UpkeepStatus.PROTECTED) {
            restoreClaimRadius(team);
            // Re-enable perks/teleports in other checks (GUI/listeners can gate on PROTECTED)
        }

        // Handle entering UNSTABLE
        if (newStatus == UpkeepStatus.UNSTABLE) {
            applyUnstablePenalties(team);
        }

        // Handle entering UNPROTECTED
        if (newStatus == UpkeepStatus.UNPROTECTED) {
            applyUnprotectedPenalties(team);
            // Broadcast once when they first hit UNPROTECTED
            Bukkit.broadcastMessage(unprotectedBroadcast);
        }
    }

    private void applyStatusPenalties(Team team, UpkeepStatus status) {
        switch (status) {
            case PROTECTED -> restoreClaimRadius(team);
            case UNSTABLE -> applyUnstablePenalties(team);
            case UNPROTECTED -> applyUnprotectedPenalties(team);
        }
    }

    /**
     * UNSTABLE:
     * - Reduce claim radius by 1 (but keep backup)
     * - Dimension teleports should be gated elsewhere using team.getUpkeepStatus()
     */
    private void applyUnstablePenalties(Team team) {
        int current = team.getClaimRadius();
        int backup = team.getBaseClaimRadiusForUpkeep();

        if (backup <= 0) {
            // First time entering UNSTABLE – remember the "true" radius
            team.setBaseClaimRadiusForUpkeep(current);
        } else {
            current = backup; // ensure we always base from true radius
        }

        int reduced = Math.max(0, current - 1);
        team.setClaimRadius(reduced);
    }

    /**
     * UNPROTECTED:
     * - Remove claim (radius 0), but we rely on banner-specific protection in BannerListener
     */
    private void applyUnprotectedPenalties(Team team) {
        int backup = team.getBaseClaimRadiusForUpkeep();
        if (backup <= 0) {
            // First time unprotected: capture the original radius
            team.setBaseClaimRadiusForUpkeep(team.getClaimRadius());
        }
        team.setClaimRadius(0);
    }

    /**
     * Return claim radius to original value if we have a backup.
     */
    private void restoreClaimRadius(Team team) {
        int backup = team.getBaseClaimRadiusForUpkeep();
        if (backup > 0) {
            team.setClaimRadius(backup);
        }
        team.setBaseClaimRadiusForUpkeep(-1);
    }

    // --- getters for thresholds ---

    public int getUnstableThresholdDays() {
        return unstableThresholdDays;
    }

    public int getUnprotectedThresholdDays() {
        return unprotectedThresholdDays;
    }

    // --- getters for cost ---

    public int getWeeklyCostBase() {
        return weeklyCostBase;
    }

    public int getMaxWeeksBackpay() {
        return maxWeeksBackpay;
    }


    /**
     * How many days since this team last paid upkeep.
     * If they've never paid, returns a large number.
     */
    public long calculateDaysSinceLastPayment(Team team) {
        long last = team.getLastUpkeepPaymentMillis();
        if (last <= 0L) {
            // Never paid – treat as very overdue for display purposes
            return 999L;
        }
        long diffMillis = System.currentTimeMillis() - last;
        return java.time.Duration.ofMillis(diffMillis).toDays();
    }


    // ===================================================================================
    // Payment logic – called from a command like /team upkeep pay
    // ===================================================================================

    public void payUpkeep(Player player, Team team) {
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "Upkeep is currently disabled on this server.");
            return;
        }

        int weeksOwed = team.getUnpaidWeeks();

        if (weeksOwed == 0) {
            player.sendMessage(ChatColor.RED + "You currently do not have an upkeep payment due.");
            return;
        }

        int totalCost = weeklyCostBase * weeksOwed;

        int tokens = tokenManager.countTokensInInventory(player);
        if (tokens < totalCost) {
            player.sendMessage(ChatColor.RED + "You need at least "
                    + ChatColor.AQUA + totalCost + ChatColor.RED
                    + " Soul Tokens to pay your team's upkeep (" + weeksOwed + " week(s)).");
            return;
        }

        boolean removed = tokenManager.removeTokensFromPlayer(player, totalCost);
        if (!removed) {
            player.sendMessage(ChatColor.RED + "Could not remove Soul Tokens from your inventory.");
            return;
        }

        // Reset upkeep state
        team.setLastUpkeepPaymentMillis(Instant.now().toEpochMilli());
        team.setUnpaidWeeks(0);
        team.setUpkeepStatus(UpkeepStatus.PROTECTED);
        restoreClaimRadius(team);

        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.GREEN + "You have paid " + weeksOwed
                + " week(s) of upkeep for your team.");
        player.sendMessage(ChatColor.GRAY + "Total cost: " + ChatColor.AQUA + totalCost + " Soul Tokens");
    }
}
