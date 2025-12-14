package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.fishing.CustomFishGenerator;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.FishingRarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public class FishingListener implements Listener {

    private final FishingConfig cfg;
    private final CustomFishGenerator generator;

    private final NamespacedKey typeKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey chanceKey;

    public FishingListener(FishingConfig cfg,
                           CustomFishGenerator generator,
                           NamespacedKey typeKey,
                           NamespacedKey rarityKey,
                           NamespacedKey weightKey,
                           NamespacedKey chanceKey) {
        this.cfg = cfg;
        this.generator = generator;
        this.typeKey = typeKey;
        this.rarityKey = rarityKey;
        this.weightKey = weightKey;
        this.chanceKey = chanceKey;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (!cfg.enabled) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();

        // If overriding vanilla: remove the vanilla caught item + exp,
        // but DO NOT cancel the event (cancelling can stop the reel-in / hook retract).
        if (cfg.overrideVanilla) {
            event.setExpToDrop(0);

            if (event.getCaught() instanceof Item caughtItem) {
                caughtItem.remove(); // prevents vanilla loot from being collected/dropped
            }
        }

        // Luck of the Sea
        int luck = 0;
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod != null) {
            luck = rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
        }

        ItemStack fish = generator.generateFish(luck);
        if (fish == null) return;

        // Give fish to player
        player.getInventory().addItem(fish);

        // Broadcast if chance < 1%
        announceIfUltraRare(player, fish);
    }

    private void announceIfUltraRare(Player player, ItemStack fish) {
        ItemMeta meta = fish.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Double chance = pdc.get(chanceKey, PersistentDataType.DOUBLE);
        if (chance == null || chance >= 0.01) return; // only < 1%

        String rarityId = pdc.get(rarityKey, PersistentDataType.STRING);
        String rarityColor = "&e"; // fallback

        if (rarityId != null) {
            FishingRarity rarity = cfg.rarities.get(rarityId.toUpperCase(Locale.ROOT));
            if (rarity != null) rarityColor = rarity.getColor();
        }

        String display = meta.hasDisplayName()
                ? ChatColor.stripColor(meta.getDisplayName())
                : fish.getType().name().toLowerCase(Locale.ROOT).replace("_", " ");

        String percent = String.format("%.4f", chance * 100.0);

        String message = ChatColor.translateAlternateColorCodes('&',
                "&b[Fish] &f" + player.getName()
                        + " &7just caught "
                        + rarityColor + display
                        + " &7(&f" + percent + "%&7)!"
        );

        Bukkit.broadcastMessage(message);
    }
}