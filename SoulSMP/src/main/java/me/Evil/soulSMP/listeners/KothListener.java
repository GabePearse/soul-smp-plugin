// FILE: me/Evil/soulSMP/listeners/KothListener.java
package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.koth.KothManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class KothListener implements Listener {

    private final JavaPlugin plugin;
    private final KothManager koth;

    public KothListener(JavaPlugin plugin, KothManager koth) {
        this.plugin = plugin;
        this.koth = koth;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        if (koth == null || !koth.isActive()) return;
        if (!koth.isParticipant(e.getPlayer().getUniqueId())) return;
        if (koth.getCenter() == null) return;

        World w = koth.getCenter().getWorld();
        if (w == null) return;

        var loc = koth.randomSpawnLocation(w);
        if (loc != null) e.setRespawnLocation(koth.faceTowardCenterPublic(loc));

        Bukkit.getScheduler().runTask(plugin, () -> koth.applyKothKit(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {

        // ✅ If KOTH ended while they were offline, restore their inventory now
        if (koth != null && !koth.isActive()) {
            Bukkit.getScheduler().runTask(plugin, () -> koth.restoreIfPending(e.getPlayer()));
            return;
        }

        // If KOTH is active and they are a participant, teleport them back in + kit
        if (koth == null || !koth.isActive()) return;
        if (!koth.isParticipant(e.getPlayer().getUniqueId())) return;
        if (koth.getCenter() == null) return;

        World w = koth.getCenter().getWorld();
        if (w == null) return;

        var loc = koth.randomSpawnLocation(w);
        if (loc != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                e.getPlayer().teleport(koth.faceTowardCenterPublic(loc));
                koth.applyKothKit(e.getPlayer());
            });
        }
    }
}