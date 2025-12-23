package me.Evil.soulSMP.leaderboard;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class LeaderboardCommand implements CommandExecutor {

    private final LeaderboardManager lb;

    public LeaderboardCommand(LeaderboardManager lb) {
        this.lb = lb;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // Match plugin.yml (soulsmp.admin)
        if (!p.hasPermission("soulsmp.admin")) {
            p.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length < 1) {
            sendUsage(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "set" -> {
                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /lb set <rarest|journal|claim>");
                    return true;
                }

                String which = args[1].toLowerCase();
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
                lb.scheduleRecompute(); // debounced
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
}
