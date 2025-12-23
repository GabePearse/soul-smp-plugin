package me.Evil.soulSMP.leaderboard;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final LeaderboardManager lb;

    public LeaderboardCommand(LeaderboardManager lb) {
        this.lb = lb;
    }

    // =====================
    // Command handling
    // =====================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!p.hasPermission("soulsmp.admin")) {
            p.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length < 1) {
            sendUsage(p);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "set" -> {
                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /lb set <rarest|journal|claim>");
                    return true;
                }

                String which = args[1].toLowerCase(Locale.ROOT);
                switch (which) {
                    case "rarest" -> lb.setDisplayLocation("rarestFishMannequin", p.getLocation());
                    case "journal" -> lb.setDisplayLocation("journalMannequin", p.getLocation());
                    case "claim" -> lb.setDisplayLocation("biggestClaimBanner", p.getLocation());
                    default -> {
                        p.sendMessage(ChatColor.RED + "Unknown: " + which);
                        return true;
                    }
                }

                p.sendMessage(ChatColor.GREEN + "Leaderboard display set: " + which);
                lb.scheduleRecompute();
                return true;
            }

            case "remove" -> {
                lb.removeAllDisplays();
                p.sendMessage(ChatColor.GREEN + "Removed all leaderboard displays and cleared data.");
                return true;
            }

            case "recompute", "update", "refresh" -> {
                lb.scheduleRecompute();
                p.sendMessage(ChatColor.GREEN + "Leaderboard recompute scheduled.");
                return true;
            }

            default -> {
                sendUsage(p);
                return true;
            }
        }
    }

    private void sendUsage(Player p) {
        p.sendMessage(ChatColor.RED + "Usage:");
        p.sendMessage(ChatColor.GRAY + "/lb set <rarest|journal|claim>");
        p.sendMessage(ChatColor.GRAY + "/lb remove");
        p.sendMessage(ChatColor.GRAY + "/lb recompute");
    }

    // =====================
    // Tab completion
    // =====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player p)) return completions;

        if (!p.hasPermission("soulsmp.admin")) return completions;

        // /lb <subcommand>
        if (args.length == 1) {
            List<String> subs = List.of("set", "remove", "recompute", "update", "refresh");
            String current = args[0].toLowerCase(Locale.ROOT);
            for (String s : subs) {
                if (s.startsWith(current)) {
                    completions.add(s);
                }
            }
            return completions;
        }

        // /lb set <...>
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            List<String> subs = List.of("rarest", "journal", "claim");
            String current = args[1].toLowerCase(Locale.ROOT);
            for (String s : subs) {
                if (s.startsWith(current)) {
                    completions.add(s);
                }
            }
            return completions;
        }

        return completions;
    }
}
