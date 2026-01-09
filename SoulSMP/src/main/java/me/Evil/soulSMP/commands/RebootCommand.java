package me.Evil.soulSMP.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

public class RebootCommand implements CommandExecutor {

    private final Plugin plugin;

    private static boolean rebootInProgress = false;
    private static BukkitTask rebootTask = null;

    public RebootCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("soulsmp.reboot")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do this.");
            return true;
        }

        // /reboot cancel
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            if (!rebootInProgress || rebootTask == null) {
                sender.sendMessage(ChatColor.RED + "There is no reboot in progress.");
                return true;
            }

            rebootTask.cancel();
            rebootTask = null;
            rebootInProgress = false;

            Bukkit.broadcastMessage(ChatColor.GREEN + "✔ Server reboot has been cancelled.");
            return true;
        }

        if (rebootInProgress) {
            sender.sendMessage(ChatColor.RED + "A reboot is already in progress.");
            return true;
        }

        // Default is 60s
        int totalSeconds = 60;

        // /reboot <duration>
        if (args.length >= 1) {
            Integer parsed = parseDurationSeconds(args[0]);
            if (parsed == null) {
                sender.sendMessage(ChatColor.RED + "Invalid duration. Examples: "
                        + ChatColor.YELLOW + "/reboot 30s"
                        + ChatColor.RED + ", "
                        + ChatColor.YELLOW + "/reboot 5m"
                        + ChatColor.RED + ", "
                        + ChatColor.YELLOW + "/reboot 2m30s"
                        + ChatColor.RED + ", "
                        + ChatColor.YELLOW + "/reboot 45"
                        + ChatColor.RED + " (seconds)");
                return true;
            }
            totalSeconds = parsed;
        }

        // Safety bounds
        if (totalSeconds < 5) totalSeconds = 5;
        if (totalSeconds > 60 * 60) totalSeconds = 60 * 60; // cap at 1 hour

        rebootInProgress = true;

        Bukkit.broadcastMessage(ChatColor.RED + "⚠ Server will reboot in "
                + ChatColor.GOLD + formatTime(totalSeconds)
                + ChatColor.RED + "! "
                + ChatColor.RED + "⚠");


        final int startSeconds = totalSeconds;

        rebootTask = new BukkitRunnable() {
            int seconds = startSeconds;

            @Override
            public void run() {

                // Announce at common warning points if they make sense for this duration
                // (Only announce if the warning time is below the start time)
                if (seconds == 300 && startSeconds > 300) { // 5m
                    broadcastTime(seconds);
                }
                if (seconds == 60 && startSeconds > 60) { // 1m
                    Bukkit.broadcastMessage(ChatColor.RED + "⚠ Server rebooting in "
                            + ChatColor.GOLD + "60 seconds"
                            + ChatColor.RED + "!"
                            + ChatColor.RED + "⚠");
                }
                if (seconds == 30 && startSeconds > 30) { // 30s
                    Bukkit.broadcastMessage(ChatColor.RED + "⚠ Server rebooting in "
                            + ChatColor.GOLD + "30 seconds"
                            + ChatColor.RED + "!"
                            + ChatColor.RED + "⚠");
                }
                if (seconds == 10 && startSeconds > 10) { // 10s
                    Bukkit.broadcastMessage(ChatColor.RED + "⚠ Server rebooting in "
                            + ChatColor.GOLD + "10 seconds"
                            + ChatColor.RED + "!"
                            + ChatColor.RED + "⚠");
                }

                // Final 5 second countdown (always)
                if (seconds <= 5 && seconds > 0) {
                    Bukkit.broadcastMessage(ChatColor.RED + "⚠ Rebooting in "
                            + ChatColor.GOLD + seconds
                            + ChatColor.RED + "..."
                            + ChatColor.RED + "⚠");
                }

                // Shutdown
                if (seconds <= 0) {
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚠ Server is rebooting now! ⚠");

                    Bukkit.getOnlinePlayers().forEach(p ->
                            p.kickPlayer(ChatColor.RED + "Server rebooting. Please rejoin shortly.")
                    );

                    // Reset state before shutdown just in case
                    rebootTask = null;
                    rebootInProgress = false;

                    Bukkit.shutdown();
                    cancel();
                    return;
                }

                seconds--;
            }

            private void broadcastTime(int secondsLeft) {
                Bukkit.broadcastMessage(ChatColor.RED + "⚠ Server rebooting in "
                        + ChatColor.GOLD + formatTime(secondsLeft)
                        + ChatColor.RED + "!"
                        + ChatColor.RED + "⚠");
            }
        }.runTaskTimer(plugin, 20L, 20L);

        return true;
    }

    /**
     * Parses durations like:
     * - "30s", "5m", "2m30s"
     * - "45" (seconds)
     * - "1h", "1h10m"
     *
     * Returns total seconds or null if invalid.
     */
    private Integer parseDurationSeconds(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        // Plain number => seconds
        if (s.matches("\\d+")) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        // Must be groups like 1h, 5m, 30s in any order, no duplicates required but allowed (we'll sum)
        // Example: "2m30s" => 2m + 30s
        int total = 0;
        int idx = 0;
        boolean any = false;

        while (idx < s.length()) {
            int start = idx;
            while (idx < s.length() && Character.isDigit(s.charAt(idx))) idx++;
            if (start == idx) return null; // expected number

            int value;
            try {
                value = Integer.parseInt(s.substring(start, idx));
            } catch (NumberFormatException ex) {
                return null;
            }

            if (idx >= s.length()) return null; // expected unit

            char unit = s.charAt(idx);
            idx++;

            switch (unit) {
                case 'h' -> total += value * 3600;
                case 'm' -> total += value * 60;
                case 's' -> total += value;
                default -> { return null; }
            }

            any = true;
        }

        return any ? total : null;
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + " seconds";

        int hours = totalSeconds / 3600;
        int rem = totalSeconds % 3600;
        int mins = rem / 60;
        int secs = rem % 60;

        if (hours > 0) {
            if (secs == 0 && mins > 0) return hours + "h " + mins + "m";
            if (mins == 0 && secs == 0) return hours + "h";
            if (secs == 0) return hours + "h " + mins + "m";
            return hours + "h " + mins + "m " + secs + "s";
        }

        if (secs == 0) return mins + " minute" + (mins == 1 ? "" : "s");
        return mins + "m " + secs + "s";
    }
}
