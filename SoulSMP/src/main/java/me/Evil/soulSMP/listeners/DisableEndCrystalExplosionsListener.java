package me.Evil.soulSMP.listeners;

import org.bukkit.entity.EnderCrystal;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class DisableEndCrystalExplosionsListener implements Listener {

    // Prevent the explosion itself (prevents block + entity damage)
    @EventHandler
    public void onCrystalExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal)) return;
        event.setCancelled(true);
    }

    // Extra safety: if any damage is being applied "by" an End Crystal, cancel it
    @EventHandler
    public void onDamageByCrystal(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof EnderCrystal) {
            event.setCancelled(true);
        }
    }
}
