package me.Evil.soulSMP.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public class FishConsumeBlockerListener implements Listener {

    private final NamespacedKey fishTypeKey;

    public FishConsumeBlockerListener(Plugin plugin) {
        // Same key used by your fishing system
        this.fishTypeKey = new NamespacedKey(plugin, "fish_type");
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // If it has our fishing NBT → cancel eating
        if (pdc.has(fishTypeKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou can’t eat this fish. Sell it or trade it instead!");
        }
    }
}
