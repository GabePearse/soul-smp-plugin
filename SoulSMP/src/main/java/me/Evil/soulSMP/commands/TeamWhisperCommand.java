package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TeamWhisperCommand implements CommandExecutor {

    private final TeamManager teamManager;

    public TeamWhisperCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
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

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /tmsg <message>");
            return true;
        }

        // Build the message from all args (no color codes as requested)
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            sb.append(arg).append(" ");
        }
        String message = sb.toString().trim();
        if (message.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You must enter a message.");
            return true;
        }

        // Prefix with team name and player name
        String formatted = ChatColor.AQUA + "[" + team.getName() + " Team] "
                + ChatColor.WHITE + player.getName() + ": "
                + ChatColor.GRAY + message;

        // Send to all online team members (cross-world is allowed)
        for (UUID uuid : team.getMembers()) {
            Player teammate = Bukkit.getPlayer(uuid);
            if (teammate != null && teammate.isOnline()) {
                teammate.sendMessage(formatted);
            }
        }

        return true;
    }
}
