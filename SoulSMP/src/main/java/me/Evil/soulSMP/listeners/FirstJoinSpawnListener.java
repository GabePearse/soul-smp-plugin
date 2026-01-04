package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.koth.KothManager;
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

public class FirstJoinSpawnListener implements Listener {

    private final JavaPlugin plugin;
    private final KothManager koth;

    public FirstJoinSpawnListener(JavaPlugin plugin, KothManager koth) {
        this.plugin = plugin;
        this.koth = koth;
    }

    private ConfigurationSection spawnRoot() {
        return plugin.getConfig().getConfigurationSection("spawn-claim");
    }

    private ConfigurationSection firstJoinSection() {
        ConfigurationSection root = spawnRoot();
        return root == null ? null : root.getConfigurationSection("first-join");
    }

    private boolean isDefaultRespawnEnabled() {
        ConfigurationSection fj = firstJoinSection();
        if (fj == null) return false;
        if (!fj.getBoolean("enabled", true)) return false;
        return fj.getBoolean("set-bed-spawn", true);
    }

    private boolean debugForce() {
        ConfigurationSection fj = firstJoinSection();
        return fj != null && fj.getBoolean("debug-force", false);
    }

    private World getDefaultRespawnWorld() {
        ConfigurationSection fj = firstJoinSection();
        String worldName = (fj != null) ? fj.getString("world", "world") : "world";
        return Bukkit.getWorld(worldName);
    }

    private Location getConfiguredSpawn(World world) {
        ConfigurationSection root = spawnRoot();
        if (root == null || world == null) return null;

        ConfigurationSection perWorld = root.getConfigurationSection("per-world");
        ConfigurationSection worldSec = (perWorld != null) ? perWorld.getConfigurationSection(world.getName()) : null;

        if (worldSec == null) return null;
        if (!worldSec.getBoolean("enabled", true)) return null;

        if (worldSec.getBoolean("use-world-spawn", false)) {
            return world.getSpawnLocation();
        }

        ConfigurationSection center = worldSec.getConfigurationSection("center");
        double x = (center != null) ? center.getDouble("x", 0.5) : 0.5;
        double y = (center != null) ? center.getDouble("y", 64.0) : 64.0;
        double z = (center != null) ? center.getDouble("z", 0.5) : 0.5;

        float yaw = (float) root.getDouble("center-yaw", 0.0);
        float pitch = (float) root.getDouble("center-pitch", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private boolean kothOverridesFor(Player p) {
        return koth != null && koth.isActive() && koth.isParticipant(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {

        // ✅ Only override spawn if THIS joining player is a KOTH participant
        if (kothOverridesFor(e.getPlayer())) return;

        Player p = e.getPlayer();
        if (!isDefaultRespawnEnabled()) return;

        if (!debugForce() && p.hasPlayedBefore()) return;

        World defaultWorld = getDefaultRespawnWorld();
        Location spawn = getConfiguredSpawn(defaultWorld);
        if (spawn == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            p.setRespawnLocation(spawn, true);
            p.teleport(spawn);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {

        // ✅ Only override spawn if THIS respawning player is a KOTH participant
        if (kothOverridesFor(e.getPlayer())) return;

        if (!isDefaultRespawnEnabled()) return;

        if (e.isBedSpawn() || e.isAnchorSpawn()) return;

        World defaultWorld = getDefaultRespawnWorld();
        Location spawn = getConfiguredSpawn(defaultWorld);
        if (spawn == null) return;

        e.setRespawnLocation(spawn);

        Bukkit.getScheduler().runTask(plugin, () -> e.getPlayer().setRespawnLocation(spawn, true));
    }
}
