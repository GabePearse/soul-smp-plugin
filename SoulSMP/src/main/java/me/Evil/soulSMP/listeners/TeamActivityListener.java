package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.upkeep.TeamUpkeepManager;
import me.Evil.soulSMP.upkeep.UpkeepStatus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class TeamActivityListener implements Listener {

    private final TeamManager teamManager;
    private final TeamUpkeepManager upkeepManager;

    public TeamActivityListener(TeamManager teamManager,
                                TeamUpkeepManager upkeepManager) {
        this.teamManager = teamManager;
        this.upkeepManager = upkeepManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            return;
        }

        // 1) Update upkeep state so info is fresh
        upkeepManager.updateTeamUpkeep(team);

        // 2) Send team-status rundown to the joining player
        sendTeamStatusSummary(player, team);

        // 3) Notify other teammates that this player came online (existing behaviour)
        String joinMsg = ChatColor.AQUA + "[" + team.getName() + " Team] "
                + ChatColor.GREEN + player.getName() + " has come online.";

        for (UUID uuid : team.getMembers()) {
            if (uuid.equals(player.getUniqueId())) continue;

            Player teammate = Bukkit.getPlayer(uuid);
            if (teammate != null && teammate.isOnline()) {
                teammate.sendMessage(joinMsg);
            }
        }
    }

    private void sendTeamStatusSummary(Player player, Team team) {
        UpkeepStatus status = team.getUpkeepStatus();
        long daysSince = upkeepManager.calculateDaysSinceLastPayment(team);
        int unstableThreshold = upkeepManager.getUnstableThresholdDays();
        int unprotectedThreshold = upkeepManager.getUnprotectedThresholdDays();

        int daysLeftUntilUnstable = (int) Math.max(0, unstableThreshold - daysSince);
        int daysLeftUntilUnprotected = (int) Math.max(0, unprotectedThreshold - daysSince);
        player.sendMessage(ChatColor.AQUA + "==============================");
        player.sendMessage(ChatColor.AQUA + "Team: " + ChatColor.GOLD + team.getName());
        player.sendMessage("");


        // Lives
        player.sendMessage(ChatColor.GRAY + "Lives: "
                + ChatColor.GOLD + team.getLives());

        // Claim radius
        player.sendMessage(ChatColor.GRAY + "Claim radius: "
                + ChatColor.GOLD + team.getClaimRadius()
                + ChatColor.GRAY + " chunk(s)");

        // Upkeep status & days left
        switch (status) {
            case PROTECTED -> {
                player.sendMessage(ChatColor.GRAY + "Upkeep status: "
                        + ChatColor.GREEN + "Protected");
                player.sendMessage(ChatColor.GRAY + "Days until "
                        + ChatColor.YELLOW + "Unstable"
                        + ChatColor.GRAY + ": "
                        + ChatColor.AQUA + daysLeftUntilUnstable + " day(s)");
            }
            case UNSTABLE -> {
                player.sendMessage(ChatColor.GRAY + "Upkeep status: "
                        + ChatColor.YELLOW + "Unstable");
                player.sendMessage(ChatColor.GRAY + "Days until "
                        + ChatColor.RED + "Unprotected"
                        + ChatColor.GRAY + ": "
                        + ChatColor.AQUA + daysLeftUntilUnprotected + " day(s)");
            }
            case UNPROTECTED -> {
                player.sendMessage(ChatColor.GRAY + "Upkeep status: "
                        + ChatColor.DARK_RED + "Unprotected");
                player.sendMessage(ChatColor.GRAY + "Upkeep is overdue. "
                        + "Pay back up to "
                        + ChatColor.AQUA + team.getUnpaidWeeks()
                        + ChatColor.GRAY + " week(s) to restore protection.");
            }
        }
        player.sendMessage(ChatColor.AQUA + "==============================");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            return;
        }

        String quitMsg = ChatColor.AQUA + "[" + team.getName() + " Team] "
                + ChatColor.RED + player.getName() + " has gone offline.";

        for (UUID uuid : team.getMembers()) {
            if (uuid.equals(player.getUniqueId())) continue;

            Player teammate = Bukkit.getPlayer(uuid);
            if (teammate != null && teammate.isOnline()) {
                teammate.sendMessage(quitMsg);
            }
        }
    }
}
