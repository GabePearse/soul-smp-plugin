package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.fishing.CustomFishGenerator;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.FishingRarity;
import me.Evil.soulSMP.leaderboard.LeaderboardManager;
import me.Evil.soulSMP.store.sell.SellEngine;
import me.Evil.soulSMP.util.GiveOrDrop;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class FishingListener implements Listener {

    private final FishingConfig cfg;
    private final CustomFishGenerator generator;
    private final SellEngine sellEngine;

    private final NamespacedKey typeKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey chanceKey;

    private final LeaderboardManager leaderboard;

    public FishingListener(FishingConfig cfg,
                           CustomFishGenerator generator,
                           SellEngine sellEngine,
                           NamespacedKey typeKey,
                           NamespacedKey rarityKey,
                           NamespacedKey weightKey,
                           NamespacedKey chanceKey,
                           LeaderboardManager leaderboard) {
        this.cfg = cfg;
        this.generator = generator;
        this.sellEngine = sellEngine;
        this.typeKey = typeKey;
        this.rarityKey = rarityKey;
        this.weightKey = weightKey;
        this.chanceKey = chanceKey;
        this.leaderboard = leaderboard;
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

        // Add worth lore that matches the exact sell payout
        applyWorthLore(fish);

        // Give fish to player
        GiveOrDrop.give(player, fish);

        // Broadcast if ultra rare
        announceIfUltraRare(player, fish);

        // Leaderboard hook
        recordRarestForLeaderboard(player, fish);
    }

    private void recordRarestForLeaderboard(Player player, ItemStack fish) {
        if (leaderboard == null) return;

        ItemMeta meta = fish.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        // We’ll compute a "rarityValue" where HIGHER = rarer.
        // Primary: use chance (lower chance => rarer). Convert to score: -log10(chance).
        // Fallback: use rarity rank derived from cfg.rarities order.
        Double chance = pdc.get(chanceKey, PersistentDataType.DOUBLE);
        String rarityId = pdc.get(rarityKey, PersistentDataType.STRING);

        double rarityValue = computeRarityValue(chance, rarityId);

        // If we couldn’t compute anything meaningful, don’t write.
        if (rarityValue <= 0) return;

        // Only schedule recompute if the player's best improved:
        // recordRarestFish only saves when higher than existing
        double before = getCurrentRecordedRarest(player.getUniqueId());

        leaderboard.recordRarestFish(player.getUniqueId(), player.getName(), rarityValue);

        double after = getCurrentRecordedRarest(player.getUniqueId());
        if (after > before) {
            leaderboard.scheduleRecompute();
        }
    }

    /**
     * Read current stored value so we can know if it improved (to avoid scheduling spam).
     */
    private double getCurrentRecordedRarest(UUID uuid) {
        return leaderboard.getPlayerRarestValue(uuid);
    }

    private double computeRarityValue(Double chance, String rarityId) {
        // Primary: chance-based (best)
        if (chance != null && chance > 0.0) {
            // example:
            // chance=0.01 => -log10(0.01)=2
            // chance=0.0001 => 4 (rarer)
            return -Math.log10(chance);
        }

        // Fallback: rarity id based
        if (rarityId != null && !rarityId.isBlank()) {
            FishingRarity rarity = cfg.rarities.get(rarityId);
            if (rarity == null) {
                for (FishingRarity r : cfg.rarities.values()) {
                    if (r.getId().equalsIgnoreCase(rarityId)) {
                        rarity = r;
                        break;
                    }
                }
            }
            if (rarity != null) {
                // Use index rank in config order (later = rarer if your config is ordered common->rare)
                // If you want the opposite, flip it.
                List<FishingRarity> ordered = new ArrayList<>(cfg.rarities.values());
                int idx = ordered.indexOf(rarity);
                if (idx >= 0) return 1.0 + idx;
            }
        }

        return 0;
    }

    private void applyWorthLore(ItemStack fish) {
        if (sellEngine == null) return;

        int worth = sellEngine.computeFishPayout(fish);
        if (worth <= 0) return;

        ItemMeta meta = fish.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        // remove existing Worth line to avoid duplicates
        lore.removeIf(line ->
                ChatColor.stripColor(line)
                        .toLowerCase(Locale.ROOT)
                        .startsWith("worth:")
        );

        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&',
                "&bWorth: &f" + worth + " &7Soul Tokens"
        ));

        meta.setLore(lore);
        fish.setItemMeta(meta);
    }

    private void announceIfUltraRare(Player player, ItemStack fish) {
        ItemMeta meta = fish.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        Double chance = pdc.get(chanceKey, PersistentDataType.DOUBLE);
        String rarityId = pdc.get(rarityKey, PersistentDataType.STRING);
        if (chance == null || rarityId == null) return;

        // Only announce these
        boolean ultraRare =
                rarityId.equalsIgnoreCase("MYTHIC") ||
                        rarityId.equalsIgnoreCase("DIVINE");

        if (!ultraRare) return;

        // Lookup rarity (try exact first, then case-insensitive fallback)
        FishingRarity rarity = cfg.rarities.get(rarityId);
        if (rarity == null) {
            for (FishingRarity r : cfg.rarities.values()) {
                if (r.getId().equalsIgnoreCase(rarityId)) {
                    rarity = r;
                    break;
                }
            }
        }
        if (rarity == null) return;

        String rarityColor = rarity.getColor();

        String display = meta.hasDisplayName()
                ? ChatColor.stripColor(meta.getDisplayName())
                : fish.getType().name().toLowerCase(Locale.ROOT).replace("_", " ");

        String percent = String.format(Locale.US, "%.4f", chance * 100.0);

        String message = ChatColor.translateAlternateColorCodes('&',
                "&b[Fish] &f" + player.getName()
                        + " &7just caught "
                        + rarityColor + display
                        + " &7(&f" + percent + "%&7)!"
        );

        Bukkit.broadcastMessage(message);
    }
}
