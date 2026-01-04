package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.koth.KothManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class KothCommand implements CommandExecutor, TabCompleter {

    private final KothManager koth;

    public KothCommand(KothManager koth) {
        this.koth = koth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this.");
            return true;
        }

        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /koth <x> <y> <z> | /koth join | /koth leave | /koth start | /koth stop | /koth status");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // ----------------
        // stop
        // ----------------
        if (sub.equals("stop")) {
            if (!koth.isActive() && !koth.isPrepared()) {
                p.sendMessage(ChatColor.RED + "KOTH is not active/prepared.");
                return true;
            }
            koth.stop(true);
            return true;
        }

        // ----------------
        // status
        // ----------------
        if (sub.equals("status")) {
            if (!koth.isActive()) {
                if (koth.isPrepared()) {
                    p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "KOTH is prepared (not started yet)");
                    p.sendMessage(ChatColor.GRAY + "Players joined: " + ChatColor.AQUA + koth.getParticipantsSnapshot().size());
                    return true;
                }
                p.sendMessage(ChatColor.RED + "KOTH is not active.");
                return true;
            }

            Map<String, Integer> snap = koth.getProgressSnapshot();
            int goal = koth.getWinSeconds();

            List<Map.Entry<String, Integer>> list = new ArrayList<>(snap.entrySet());
            list.removeIf(e -> e.getValue() == null || e.getValue() <= 0);
            list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "KOTH Status");
            p.sendMessage(ChatColor.GRAY + "Goal: " + ChatColor.AQUA + koth.formatTime(goal));

            if (list.isEmpty()) {
                p.sendMessage(ChatColor.DARK_GRAY + "No team has made progress yet.");
                return true;
            }

            for (Map.Entry<String, Integer> e : list) {
                String team = e.getKey();
                int secs = e.getValue();
                p.sendMessage(ChatColor.WHITE + team + ChatColor.GRAY + ": "
                        + ChatColor.YELLOW + koth.formatTime(secs)
                        + ChatColor.GRAY + " / "
                        + ChatColor.AQUA + koth.formatTime(goal));
            }
            return true;
        }

        // ----------------
        // join
        // ----------------
        if (sub.equals("join")) {
            koth.join(p);
            return true;
        }

        // ----------------
        // leave
        // ----------------
        if (sub.equals("leave")) {
            koth.leave(p);

            // If they left while a restore is pending and KOTH isn't active, restore now.
            if (!koth.isActive()) {
                koth.restoreIfPending(p);
            }
            return true;
        }

        // ----------------
        // start (actual start from prepared)
        // ----------------
        if (sub.equals("start")) {
            if (!p.isOp()) {
                p.sendMessage(ChatColor.RED + "Only operators can start KOTH.");
                return true;
            }
            boolean ok = koth.startPrepared(p);
            if (!ok) {
                if (!koth.isPrepared()) p.sendMessage(ChatColor.RED + "KOTH is not prepared. Use /koth <x> <y> <z> first.");
            }
            return true;
        }

        // ----------------
        // legacy: tp (only affects participants now)
        // ----------------
        if (sub.equals("tp")) {
            if (!p.isOp()) {
                p.sendMessage(ChatColor.RED + "Only operators can use /koth tp.");
                return true;
            }
            if (!koth.isActive()) {
                p.sendMessage(ChatColor.RED + "KOTH is not active.");
                return true;
            }

            int count = koth.teleportAllParticipantsToRandomSpawn();
            if (count <= 0) {
                p.sendMessage(ChatColor.RED + "No participants were teleported (no safe spots or nobody joined).");
                return true;
            }

            p.sendMessage(ChatColor.GREEN + "Teleported " + count + " KOTH participants to spawns and applied kits.");
            return true;
        }

        // ----------------
        // /koth x y z -> prepare (NOT start)
        // ----------------
        if (args.length < 3) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /koth <x> <y> <z>");
            return true;
        }

        double x, y, z;
        try {
            x = Double.parseDouble(args[0]);
            y = Double.parseDouble(args[1]);
            z = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            p.sendMessage(ChatColor.RED + "x y z must be numbers.");
            return true;
        }

        if (!p.isOp()) {
            p.sendMessage(ChatColor.RED + "Only operators can prepare KOTH.");
            return true;
        }

        Location center = new Location(p.getWorld(), x + 0.5, y, z + 0.5);
        koth.prepare(center);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "start", "stop", "status", "tp");
        }
        return List.of();
    }
}