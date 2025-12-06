package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.tokens.SoulTokenManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class SoulTokenCommand implements CommandExecutor {

    private final SoulTokenManager tokenManager;

    public SoulTokenCommand(SoulTokenManager manager) {
        this.tokenManager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /token give <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Amount must be a number.");
            return true;
        }

        target.getInventory().addItem(tokenManager.createToken(amount));
        target.sendMessage(ChatColor.AQUA + "You received " + amount + " Soul Token(s)!");
        sender.sendMessage(ChatColor.GREEN + "Given " + amount + " Soul Token(s) to " + target.getName());

        return true;
    }
}
