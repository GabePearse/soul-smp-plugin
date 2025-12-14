package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.journal.FishingJournalGUI;
import me.Evil.soulSMP.fishing.journal.FishingJournalHolder;
import me.Evil.soulSMP.fishing.journal.FishingJournalManager;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;

public class FishingJournalListener implements Listener {

    private final FishingJournalManager journalManager;
    private final FishingJournalGUI gui;

    private final NamespacedKey fishTypeKey;
    private final NamespacedKey fishRarityKey;
    private final NamespacedKey fishWeightKey;

    private final int depositSlot;
    private final int prevSlot;
    private final int nextSlot;

    public FishingJournalListener(Plugin plugin, FishingJournalManager journalManager, FishingConfig fishingConfig) {
        this.journalManager = journalManager;
        this.gui = new FishingJournalGUI(journalManager, fishingConfig);

        this.fishTypeKey = new NamespacedKey(plugin, "fish_type");
        this.fishRarityKey = new NamespacedKey(plugin, "fish_rarity");
        this.fishWeightKey = new NamespacedKey(plugin, "fish_weight");

        this.depositSlot = journalManager.getJournalCfg().getInt("journal.deposit-slot", 49);
        this.prevSlot = journalManager.getJournalCfg().getInt("journal.navigation.prev.slot", 45);
        this.nextSlot = journalManager.getJournalCfg().getInt("journal.navigation.next.slot", 53);
    }

    @EventHandler
    public void onRightClickFish(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (!(a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK)) return;

        ItemStack item = event.getItem();
        if (!isSoulFish(item)) return;

        event.setCancelled(true);
        Player p = event.getPlayer();
        p.openInventory(gui.createFor(p, 1));
    }

    @EventHandler
    public void onJournalClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof FishingJournalHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = holder.getPlayerId();
        int page = holder.getPage();
        int pageCount = Math.max(1, journalManager.getPageCount());

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        boolean clickTop = clickedInv.equals(top);
        boolean clickBottom = clickedInv.equals(event.getView().getBottomInventory());

        int raw = event.getRawSlot();

        // --- TOP (journal) inventory: block normal movement ---
        if (clickTop) {
            event.setCancelled(true);

            // Navigation
            if (raw == prevSlot && page > 1) {
                player.openInventory(gui.createFor(player, page - 1));
                return;
            }
            if (raw == nextSlot && page < pageCount) {
                player.openInventory(gui.createFor(player, page + 1));
                return;
            }

            // Deposit slot: consume from CURSOR (placing onto deposit)
            if (raw == depositSlot) {
                ItemStack cursor = event.getCursor();
                handleDeposit(player, playerId, page, cursor, event);
                return;
            }

            return;
        }

        // --- BOTTOM (player inventory): allow normal clicks ---
        // Support SHIFT-CLICK depositing a Soul Fish directly.
        if (clickBottom) {
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (isSoulFish(current)) {
                    event.setCancelled(true);
                    handleShiftDeposit(player, playerId, page, current, event);
                }
            }
        }
    }

    private void handleDeposit(Player player, UUID playerId, int page, ItemStack cursor, InventoryClickEvent event) {
        if (!isSoulFish(cursor)) {
            player.sendMessage(ChatColor.RED + "Put a Soul Fish in the turn-in slot.");
            return;
        }

        String entryKey = getEntryKey(cursor);
        Double weight = getFishWeight(cursor);

        if (entryKey == null || weight == null) {
            player.sendMessage(ChatColor.RED + "That fish is missing journal data.");
            return;
        }

        FishingJournalManager.EntryDef def = journalManager.findEntry(entryKey);
        if (def == null) {
            player.sendMessage(ChatColor.RED + "That fish isn't registered in journal.yml yet: " + entryKey);
            return;
        }

        // NEW: only allow deposit if it improves (or is first discovery)
        double oldBest = journalManager.getBestWeight(playerId, entryKey);
        if (oldBest >= 0 && weight <= oldBest) {
            player.sendMessage(ChatColor.YELLOW + "Not big enough. Best is "
                    + String.format("%.1f", oldBest) + "lb. This one is "
                    + String.format("%.1f", weight) + "lb.");
            return; // DO NOT consume
        }

        // Consume ONE fish from cursor
        int amt = cursor.getAmount();
        if (amt <= 1) {
            event.setCursor(null);
        } else {
            cursor.setAmount(amt - 1);
            event.setCursor(cursor);
        }

        // Update journal (should now always improve)
        journalManager.updateBestWeight(playerId, entryKey, weight);

        if (oldBest < 0) {
            player.sendMessage(ChatColor.GREEN + "Journal discovered: " + entryKey + " (" + String.format("%.1f", weight) + "lb)");
        } else {
            player.sendMessage(ChatColor.AQUA + "Journal upgraded: " + entryKey
                    + " (" + String.format("%.1f", oldBest) + "lb -> " + String.format("%.1f", weight) + "lb)");
        }

        player.openInventory(gui.createFor(player, page));
    }

    private void handleShiftDeposit(Player player, UUID playerId, int page, ItemStack stack, InventoryClickEvent event) {

        String entryKey = getEntryKey(stack);
        Double weight = getFishWeight(stack);

        if (entryKey == null || weight == null) {
            player.sendMessage(ChatColor.RED + "That fish is missing journal data.");
            return;
        }

        FishingJournalManager.EntryDef def = journalManager.findEntry(entryKey);
        if (def == null) {
            player.sendMessage(ChatColor.RED + "That fish isn't registered in journal.yml yet: " + entryKey);
            return;
        }

        // NEW: only allow deposit if it improves (or is first discovery)
        double oldBest = journalManager.getBestWeight(playerId, entryKey);
        if (oldBest >= 0 && weight <= oldBest) {
            player.sendMessage(ChatColor.YELLOW + "Not big enough. Best is "
                    + String.format("%.1f", oldBest) + "lb. This one is "
                    + String.format("%.1f", weight) + "lb.");
            return; // DO NOT consume
        }

        // Consume ONE fish from the clicked stack
        int amt = stack.getAmount();
        if (amt <= 1) {
            event.setCurrentItem(null);
        } else {
            stack.setAmount(amt - 1);
            event.setCurrentItem(stack);
        }

        // Update journal
        journalManager.updateBestWeight(playerId, entryKey, weight);

        if (oldBest < 0) {
            player.sendMessage(ChatColor.GREEN + "Journal discovered: " + entryKey + " (" + String.format("%.1f", weight) + "lb)");
        } else {
            player.sendMessage(ChatColor.AQUA + "Journal upgraded: " + entryKey
                    + " (" + String.format("%.1f", oldBest) + "lb -> " + String.format("%.1f", weight) + "lb)");
        }

        player.openInventory(gui.createFor(player, page));
    }

    private boolean isSoulFish(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        var pdc = meta.getPersistentDataContainer();
        return pdc.has(fishTypeKey, PersistentDataType.STRING)
                && pdc.has(fishRarityKey, PersistentDataType.STRING)
                && pdc.has(fishWeightKey, PersistentDataType.DOUBLE);
    }

    private String getEntryKey(ItemStack fish) {
        ItemMeta meta = fish.getItemMeta();
        if (meta == null) return null;

        var pdc = meta.getPersistentDataContainer();
        String type = pdc.get(fishTypeKey, PersistentDataType.STRING);
        String rarity = pdc.get(fishRarityKey, PersistentDataType.STRING);

        if (type == null || rarity == null) return null;
        return rarity.trim().toUpperCase(Locale.ROOT) + ":" + type.trim().toUpperCase(Locale.ROOT);
    }

    private Double getFishWeight(ItemStack fish) {
        ItemMeta meta = fish.getItemMeta();
        if (meta == null) return null;

        var pdc = meta.getPersistentDataContainer();
        return pdc.get(fishWeightKey, PersistentDataType.DOUBLE);
    }
}
