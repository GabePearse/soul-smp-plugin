package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.store.StoreItem;
import me.Evil.soulSMP.store.StoreItemBuilder;
import me.Evil.soulSMP.store.gui.SellGui;
import me.Evil.soulSMP.store.gui.StoreCategoryGui;
import me.Evil.soulSMP.store.gui.StoreMainMenuGui;
import me.Evil.soulSMP.store.gui.StoreHolder;
import me.Evil.soulSMP.store.sell.SellHolder;
import me.Evil.soulSMP.util.GiveOrDrop;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class StoreListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        // ---------------------------------------------------------------------
        // SELL GUI (new selectable selling) - uses SellHolder
        // ---------------------------------------------------------------------
        if (e.getInventory().getHolder() instanceof SellHolder sellHolder) {
            // Prevent taking/moving items in the GUI
            e.setCancelled(true);

            // Only left click sells
            if (!e.isLeftClick()) return;

            int slot = e.getSlot();
            boolean shift = e.isShiftClick();

            var manager = sellHolder.getManager();
            var sell = manager.getSellEngine();

            // Back
            if (slot == sellHolder.getBackSlot()) {
                StoreMainMenuGui.open(player, manager);
                return;
            }

            // Soul Fish button
            if (slot == sellHolder.getFishSlot()) {
                int payout = shift ? sell.sellAllSoulFish(player) : sell.sellOneSoulFish(player);

                if (payout <= 0) {
                    player.sendMessage(ChatColor.GRAY + "No Soul Fish found to sell.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Sold Soul Fish for " + ChatColor.AQUA + payout + ChatColor.GREEN + " Soul Tokens.");
                }
                return;
            }

            // Material buttons
            Material mat = sellHolder.getMaterialBySlot().get(slot);
            if (mat != null) {
                int payout = shift ? sell.sellAllMaterialUnits(player, mat) : sell.sellOneMaterialUnit(player, mat);

                if (payout <= 0) {
                    var rule = sell.getMaterialRules().get(mat);
                    int need = (rule != null ? rule.unit : 1);
                    player.sendMessage(ChatColor.GRAY + "Not enough " + pretty(mat) + " to sell (need " + need + ").");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Sold " + ChatColor.WHITE + pretty(mat)
                            + ChatColor.GREEN + " for " + ChatColor.AQUA + payout + ChatColor.GREEN + " Soul Tokens.");
                }
            }
            return;
        }

        // ---------------------------------------------------------------------
        // MAIN STORE / CATEGORY STORE (uses StoreHolder)
        // ---------------------------------------------------------------------
        if (!(e.getInventory().getHolder() instanceof StoreHolder holder)) return;

        // Block shift-moving from player inventory into GUI as well
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        var manager = holder.getManager();

        switch (holder.getView()) {

            case MAIN -> {
                int slot = e.getSlot();

                for (var cat : manager.getSettings().getCategories().values()) {
                    if (cat.slot == slot) {
                        if (cat.id.equalsIgnoreCase("sell")) {
                            // Open new selectable Sell GUI
                            SellGui.open(player, manager);
                        } else {
                            StoreCategoryGui.open(player, manager, cat.id, 0);
                        }
                        return;
                    }
                }
            }

            case CATEGORY -> {
                int slot = e.getSlot();

                // Back
                if (slot == manager.getSettings().getBackSlot()) {
                    StoreMainMenuGui.open(player, manager);
                    return;
                }

                // Find item by matching slot
                String categoryId = holder.getCategoryId();
                for (StoreItem it : manager.getSettings().getBuyItems(categoryId).values()) {
                    if (it.slot != slot) continue;

                    int price = it.price;
                    int have = manager.getTokenManager().countTokensInInventory(player);

                    if (have < price) {
                        player.sendMessage(ChatColor.RED + "Not enough Soul Tokens. Need " + price + ", you have " + have + ".");
                        return;
                    }

                    boolean removed = manager.getTokenManager().removeTokensFromPlayer(player, price);
                    if (!removed) {
                        player.sendMessage(ChatColor.RED + "Could not remove tokens (inventory changed). Try again.");
                        return;
                    }

                    ItemStack give = StoreItemBuilder.buildForGive(it);

                    // Use your existing util if you want (kept import)
                    GiveOrDrop.give(player, give);

                    player.sendMessage(ChatColor.AQUA + "Purchased " + ChatColor.WHITE
                            + stripColor(give.getItemMeta() != null ? give.getItemMeta().getDisplayName() : give.getType().name())
                            + ChatColor.AQUA + " for " + price + " Soul Tokens.");
                    return;
                }
            }

            case SELL -> {
                // You no longer need this view if you're using SellHolder.
                // If you still have legacy code opening StoreHolder.View.SELL, keep a fallback.
                int slot = e.getSlot();

                // Back
                if (slot == manager.getSellEngine().getBackSlot()) {
                    StoreMainMenuGui.open(player, manager);
                    return;
                }

                // Legacy: sell all button at 22
                if (slot == 22) {
                    int payout = manager.getSellEngine().sellAll(player);
                    if (payout <= 0) {
                        player.sendMessage(ChatColor.GRAY + "Nothing sellable found in your inventory.");
                    } else {
                        player.sendMessage(ChatColor.GREEN + "Sold items for " + ChatColor.AQUA + payout + ChatColor.GREEN + " Soul Tokens.");
                    }
                }
            }
        }
    }

    private String stripColor(String s) {
        return s == null ? "" : ChatColor.stripColor(s);
    }

    private String pretty(Material m) {
        String raw = m.name().toLowerCase(Locale.US);
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
