package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class TeamChatListener implements Listener {

    private final TeamManager teamManager;
    private final TeamChatManager teamChatManager;

    public TeamChatListener(TeamManager teamManager, TeamChatManager teamChatManager) {
        this.teamManager = teamManager;
        this.teamChatManager = teamChatManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // If they don't have team chat toggled on, let normal chat happen
        if (!teamChatManager.isTeamChatEnabled(player.getUniqueId())) {
            return;
        }

        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            // Not in a team anymore, turn off mode and let them chat normally
            teamChatManager.disableTeamChat(player.getUniqueId());
            return;
        }

        // Cancel normal public chat
        event.setCancelled(true);

        String message = event.getMessage();

        String formatted = ChatColor.AQUA + "[" + team.getName() + " Team] "
                + ChatColor.WHITE + player.getName() + ": "
                + ChatColor.GRAY + message;

        // Send only to online teammates
        for (UUID uuid : team.getMembers()) {
            Player teammate = Bukkit.getPlayer(uuid);
            if (teammate != null && teammate.isOnline()) {
                teammate.sendMessage(formatted);
            }
        }
    }
}
