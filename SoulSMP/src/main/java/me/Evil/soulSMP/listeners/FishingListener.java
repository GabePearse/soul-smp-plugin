package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.fishing.CustomFishGenerator;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.FishingRarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class FishingListener implements Listener {

    private final FishingConfig cfg;
    private final CustomFishGenerator generator;

    private final NamespacedKey typeKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey weightKey;

    public FishingListener(FishingConfig cfg,
                           CustomFishGenerator generator,
                           NamespacedKey typeKey,
                           NamespacedKey rarityKey,
                           NamespacedKey weightKey) {
        this.cfg = cfg;
        this.generator = generator;
        this.typeKey = typeKey;
        this.rarityKey = rarityKey;
        this.weightKey = weightKey;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (!cfg.enabled) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();

        // Cancel vanilla loot if configured to do so
        if (cfg.overrideVanilla) {
            event.setCancelled(true);
        }

        ItemStack fish = generator.generateFish();
        if (fish == null) {
            return;
        }

        // Give fish to player
        player.getInventory().addItem(fish);

        // --- Broadcast if EPIC or above ---
        ItemMeta meta = fish.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String rarityId = pdc.get(rarityKey, PersistentDataType.STRING);
        String typeId   = pdc.get(typeKey, PersistentDataType.STRING);
        Double weight   = pdc.get(weightKey, PersistentDataType.DOUBLE);

        if (rarityId == null || typeId == null || weight == null) {
            return;
        }

        // Only announce EPIC, LEGENDARY, MYTHIC
        String upper = rarityId.toUpperCase();
        if (!(upper.equals("EPIC") || upper.equals("LEGENDARY") || upper.equals("MYTHIC"))) {
            return;
        }

        FishingRarity rarity = cfg.rarities.get(upper);
        String rarityColor = rarity != null ? rarity.getColor() : "&5";

        String message = ChatColor.translateAlternateColorCodes('&',
                "&7[&bFishing&7] &f" + player.getName()
                        + " &7caught a " + rarityColor + upper
                        + " &f" + String.format("%.1f", weight) + "lb "
                        + typeId + "&7!");

        Bukkit.broadcastMessage(message);
    }
}
