package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.SoulSMP;
import org.bukkit.ChatColor;
import org.bukkit.command.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Main command handler for SoulSMP.
 * Currently supports /soulsmp reload to reload configuration files.
 */
public class SoulSMPCommand implements CommandExecutor, TabCompleter {

    private final SoulSMP plugin;

    public SoulSMPCommand(SoulSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            if (!sender.hasPermission("soulsmp.admin")) {
                sender.sendMessage(ChatColor.RED +
                        "You do not have permission to reload SoulSMP configuration files.");
                return true;
            }
            plugin.reloadConfigs();
            sender.sendMessage(ChatColor.GREEN +
                    "SoulSMP configuration files reloaded.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW +
                "Usage: /" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            completions.add("reload");
        }
        return completions;
    }
}
