package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.tokens.SoulTokenManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SoulTokenCommand implements CommandExecutor, TabCompleter {

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        // First argument: suggest "give"
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("give".startsWith(partial)) {
                completions.add("give");
            }
            return completions;
        }

        // Second argument: suggest online player names when first arg is "give"
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String partialName = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        // Third argument: suggest a placeholder for the amount
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            completions.add("<amount>");
            return completions;
        }

        return completions;
    }
}
