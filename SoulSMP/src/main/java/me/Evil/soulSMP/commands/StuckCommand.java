package me.Evil.soulSMP.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public class StuckCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final HashMap<UUID, Long> confirmations = new HashMap<>();
    private static final long CONFIRM_WINDOW_MS = 30_000; // 30 seconds

    public StuckCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // If already confirmed recently → kill
        if (confirmations.containsKey(uuid)) {
            long last = confirmations.get(uuid);

            if (now - last <= CONFIRM_WINDOW_MS) {
                confirmations.remove(uuid);

                player.sendMessage("§cYou have chosen to use /stuck.");
                player.setHealth(0.0);
                return true;
            }
        }

        // First confirmation
        confirmations.put(uuid, now);
        player.sendMessage("§e⚠ You are about to use §c/stuck§e.");
        player.sendMessage("§7Type §f/stuck §7again within §f30 seconds §7to confirm.");
        player.sendMessage("§8(This will kill your character.)");

        return true;
    }
}
