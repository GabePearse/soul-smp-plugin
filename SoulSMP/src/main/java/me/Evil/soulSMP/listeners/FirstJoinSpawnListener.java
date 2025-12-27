package me.Evil.soulSMP.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class FirstJoinSpawnListener implements Listener {

    private final JavaPlugin plugin;

    public FirstJoinSpawnListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection spawnRoot() {
        return plugin.getConfig().getConfigurationSection("spawn-claim");
    }

    private ConfigurationSection firstJoinSection() {
        ConfigurationSection root = spawnRoot();
        return root == null ? null : root.getConfigurationSection("first-join");
    }

    private boolean isFirstJoinEnabled() {
        ConfigurationSection fj = firstJoinSection();
        if (fj == null) return false;
        if (!fj.getBoolean("enabled", true)) return false;
        return fj.getBoolean("set-bed-spawn", true);
    }

    private boolean debugForce() {
        ConfigurationSection fj = firstJoinSection();
        return fj != null && fj.getBoolean("debug-force", false);
    }

    private Location getConfiguredSpawn() {
        ConfigurationSection root = spawnRoot();
        if (root == null) return null;

        ConfigurationSection fj = firstJoinSection();
        if (fj == null) return null;

        if (!fj.getBoolean("enabled", true)) return null;
        if (!fj.getBoolean("set-bed-spawn", true)) return null;

        String worldName = fj.getString("world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;

        ConfigurationSection c = root.getConfigurationSection("center");
        double x = (c != null) ? c.getDouble("x", 0.5) : 0.5;
        double y = (c != null) ? c.getDouble("y", 64.0) : 64.0;
        double z = (c != null) ? c.getDouble("z", 0.5) : 0.5;

        float yaw = (float) root.getDouble("center-yaw", 0.0);
        float pitch = (float) root.getDouble("center-pitch", 0.0);

        return new Location(w, x, y, z, yaw, pitch);
    }

    private String seenPath(UUID uuid) {
        return "first-join-seen." + uuid.toString();
    }

    private boolean wasHandledFirstJoin(UUID uuid) {
        return plugin.getConfig().getBoolean(seenPath(uuid), false);
    }

    private void markHandledFirstJoin(UUID uuid) {
        plugin.getConfig().set(seenPath(uuid), true);
        plugin.saveConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFirstJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (!isFirstJoinEnabled()) return;

        // Normal behavior: only run for true first join players.
        // Testing behavior: debug-force ignores hasPlayedBefore().
        if (!debugForce() && p.hasPlayedBefore()) return;

        Location spawn = getConfiguredSpawn();
        if (spawn == null) return;

        // Run 1 tick later so it happens after most join handlers
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.setRespawnLocation(spawn, true);
            markHandledFirstJoin(p.getUniqueId());

            Location now = p.getRespawnLocation();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();

        if (!isFirstJoinEnabled()) return;

        boolean allow = debugForce() || wasHandledFirstJoin(p.getUniqueId());
        if (!allow) return;

        // If the player has a bed/anchor respawn, DO NOT override it.
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;

        Location spawn = getConfiguredSpawn();
        if (spawn == null) return;

        e.setRespawnLocation(spawn);
    }

}
