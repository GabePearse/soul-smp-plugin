package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
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

    public TeamActivityListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            return;
        }

        String joinMsg = ChatColor.AQUA + "[" + team.getName() + " Team] "
                + ChatColor.GREEN + player.getName() + " has come online.";

        // Notify all other online teammates
        for (UUID uuid : team.getMembers()) {
            if (uuid.equals(player.getUniqueId())) continue;

            Player teammate = Bukkit.getPlayer(uuid);
            if (teammate != null && teammate.isOnline()) {
                teammate.sendMessage(joinMsg);
            }
        }
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

        // Notify all other online teammates
        for (UUID uuid : team.getMembers()) {
            if (uuid.equals(player.getUniqueId())) continue;

            Player teammate = Bukkit.getPlayer(uuid);
            if (teammate != null && teammate.isOnline()) {
                teammate.sendMessage(quitMsg);
            }
        }
    }
}
