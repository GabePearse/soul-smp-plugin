package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamChatToggleCommand implements CommandExecutor {

    private final TeamManager teamManager;
    private final TeamChatManager teamChatManager;

    public TeamChatToggleCommand(TeamManager teamManager, TeamChatManager teamChatManager) {
        this.teamManager = teamManager;
        this.teamChatManager = teamChatManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return true;
        }

        boolean enabled = teamChatManager.toggleTeamChat(player.getUniqueId());
        if (enabled) {
            player.sendMessage(ChatColor.AQUA + "[" + team.getName() + " Team] " +
                    ChatColor.GREEN + "Team chat enabled. Your messages will go to your team.");
        } else {
            player.sendMessage(ChatColor.AQUA + "[" + team.getName() + " Team] " +
                    ChatColor.YELLOW + "Team chat disabled. Your messages are global again.");
        }

        return true;
    }
}
