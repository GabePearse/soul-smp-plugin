package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.koth.KothManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KothCommand implements CommandExecutor, TabCompleter {

    private final KothManager koth;

    public KothCommand(KothManager koth) {
        this.koth = koth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        // =========================
        // NEW: /koth <seconds>
        // =========================
        if (args.length == 1 && isInt(args[0])) {
            if (!player.hasPermission("soul.koth.admin")) {
                player.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (koth.isActive()) {
                player.sendMessage(ChatColor.RED + "KOTH is already running.");
                return true;
            }

            int seconds = Integer.parseInt(args[0]);
            Location here = player.getLocation();

            // use player's current block (keep precise coords but your center uses getBlockX/Y/Z in messages)
            koth.prepareForSeconds(here, seconds);
            player.sendMessage(ChatColor.GREEN + "Prepared KOTH here for " + seconds + " seconds. Use /koth join then /koth start.");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "start" -> {
                if (!player.hasPermission("soul.koth.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (!koth.isPrepared()) {
                    player.sendMessage(ChatColor.RED + "KOTH is not prepared.");
                    return true;
                }
                boolean ok = koth.startPrepared(player);
                if (!ok) player.sendMessage(ChatColor.RED + "Could not start KOTH.");
                return true;
            }

            case "join" -> {
                koth.join(player);
                return true;
            }

            case "leave" -> {
                koth.leave(player);
                return true;
            }

            case "stop" -> {
                if (!player.hasPermission("soul.koth.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                koth.stop(true);
                return true;
            }

            default -> {
                // Keep your existing "/koth x y z" behavior if you have it:
                // If args are 3 ints, treat as prepare coords.
                if (args.length == 3 && isInt(args[0]) && isInt(args[1]) && isInt(args[2])) {
                    if (!player.hasPermission("soul.koth.admin")) {
                        player.sendMessage(ChatColor.RED + "No permission.");
                        return true;
                    }
                    if (koth.isActive()) {
                        player.sendMessage(ChatColor.RED + "KOTH is already running.");
                        return true;
                    }

                    int x = Integer.parseInt(args[0]);
                    int y = Integer.parseInt(args[1]);
                    int z = Integer.parseInt(args[2]);

                    koth.prepare(new Location(player.getWorld(), x + 0.5, y, z + 0.5));
                    player.sendMessage(ChatColor.GREEN + "Prepared KOTH at " + x + " " + y + " " + z + ". Use /koth join then /koth start.");
                    return true;
                }

                sendHelp(player);
                return true;
            }
        }
    }

    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "KOTH Commands:");
        sender.sendMessage(ChatColor.YELLOW + "/koth <x> <y> <z>" + ChatColor.GRAY + " - prepare at coords");
        sender.sendMessage(ChatColor.YELLOW + "/koth <seconds>" + ChatColor.GRAY + " - prepare at your location for custom goal time");
        sender.sendMessage(ChatColor.YELLOW + "/koth join" + ChatColor.GRAY + " - join");
        sender.sendMessage(ChatColor.YELLOW + "/koth leave" + ChatColor.GRAY + " - leave");
        sender.sendMessage(ChatColor.YELLOW + "/koth start" + ChatColor.GRAY + " - start (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/koth stop" + ChatColor.GRAY + " - stop (admin)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("join");
            out.add("leave");
            out.add("start");
            out.add("stop");
            out.add("60");
            out.add("120");
        }
        return out;
    }
}
