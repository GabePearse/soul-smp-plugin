package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.SoulSMP;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /ssmp reload
 */
public class SoulSMPCommand implements CommandExecutor, TabCompleter {

    private final SoulSMP plugin;

    public SoulSMPCommand(SoulSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("reload")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
            return true;
        }

        if (!sender.hasPermission("soulsmp.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reload SoulSMP.");
            return true;
        }

        plugin.reloadConfigs();
        sender.sendMessage(ChatColor.GREEN + "SoulSMP configuration files reloaded.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(a0)) out.add("reload");
        }
        return out;
    }
}
