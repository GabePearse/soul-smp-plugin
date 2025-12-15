package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.store.StoreItem;
import me.Evil.soulSMP.store.StoreItemBuilder;
import me.Evil.soulSMP.store.gui.SellGui;
import me.Evil.soulSMP.store.gui.StoreCategoryGui;
import me.Evil.soulSMP.store.gui.StoreMainMenuGui;
import me.Evil.soulSMP.store.gui.StoreHolder;
import me.Evil.soulSMP.store.sell.SellHolder;
import me.Evil.soulSMP.store.util.RandomPlusOneBook;
import me.Evil.soulSMP.util.GiveOrDrop;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class StoreListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        // ---------------------------------------------------------------------
        // SELL GUI (SellHolder)
        // ---------------------------------------------------------------------
        if (e.getInventory().getHolder() instanceof SellHolder sellHolder) {

            Inventory top = e.getView().getTopInventory();
            int topSize = top.getSize();
            int raw = e.getRawSlot();
            boolean inTop = raw >= 0 && raw < topSize;

            // Always block shift-moving items into GUI
            if (!inTop && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
                return;
            }

            var manager = sellHolder.getManager();
            var sell = manager.getSellEngine();

            // ============================================================
            // ✅ Right-click-to-sell from PLAYER inventory
            // ============================================================
            // Only when clicking in player inventory (bottom) and right-clicking
            if (!inTop && e.getClickedInventory() != null && e.getClickedInventory().equals(player.getInventory())) {

                // only right click selling (regular right click or shift-right click)
                if (!e.isRightClick()) return;

                ItemStack clickedItem = e.getCurrentItem();
                if (clickedItem == null || clickedItem.getType().isAir()) return;

                boolean shift = e.isShiftClick();

                // If Soul Fish
                if (sell.isSoulFish(clickedItem)) {
                    e.setCancelled(true);

                    if (shift) {
                        int payout = sell.sellAllSoulFish(player);
                        if (payout <= 0) {
                            player.sendMessage(ChatColor.GRAY + "No Soul Fish found to sell.");
                        } else {
                            player.sendMessage(ChatColor.GREEN + "Sold Soul Fish for "
                                    + ChatColor.AQUA + payout + ChatColor.GREEN + " Soul Tokens.");
                        }
                    } else {
                        int payout = sell.computeFishPayout(clickedItem);

                        // Remove ONLY the clicked stack safely using the view index -> player inv index
                        int playerIndex = raw - topSize;
                        if (playerIndex >= 0 && playerIndex < player.getInventory().getSize()) {
                            player.getInventory().setItem(playerIndex, null);
                        }

                        if (payout > 0) {
                            manager.getTokenManager().giveTokens(player, payout);
                            player.sendMessage(ChatColor.GREEN + "Sold Soul Fish for "
                                    + ChatColor.AQUA + payout + ChatColor.GREEN + " Soul Tokens.");
                        } else {
                            player.sendMessage(ChatColor.GRAY + "That fish was too weak to yield any Soul Tokens.");
                        }
                    }

                    player.updateInventory();
                    return;
                }

                // If sellable material (based on your materials rules)
                Material mat = clickedItem.getType();
                if (sell.getMaterialRules().containsKey(mat)) {
                    e.setCancelled(true);

                    int payout = shift
                            ? sell.sellAllMaterialUnits(player, mat)
                            : sell.sellOneMaterialUnit(player, mat);

                    if (payout <= 0) {
                        var rule = sell.getMaterialRules().get(mat);
                        int need = (rule != null ? rule.unit : 1);
                        player.sendMessage(ChatColor.GRAY + "Not enough " + pretty(mat) + " to sell (need " + need + ").");
                    } else {
                        player.sendMessage(ChatColor.GREEN + "Sold " + ChatColor.WHITE + pretty(mat)
                                + ChatColor.GREEN + " for " + ChatColor.AQUA + payout + ChatColor.GREEN + " Soul Tokens.");
                    }

                    player.updateInventory();
                    return;
                }

                // Not sellable: do nothing, let normal right-click behavior happen
                return;
            }

            // ============================================================
            // Existing Sell GUI button logic (TOP inventory)
            // ============================================================
            if (!inTop) return;

            e.setCancelled(true);

            // You can allow both left/right click for GUI buttons if you want.
            // Keeping it left-only for buttons is fine; change if desired.
            if (!e.isLeftClick()) return;

            int slot = e.getSlot();
            boolean shift = e.isShiftClick();

            // Back
            if (slot == sellHolder.getBackSlot()) {
                StoreMainMenuGui.open(player, manager);
                return;
            }

            // Sell All button
            if (slot == sellHolder.getSellAllSlot()) {
                int payout = sell.sellAll(player);

                if (payout <= 0) {
                    player.sendMessage(ChatColor.GRAY + "Nothing sellable found in your inventory.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Sold items for "
                            + ChatColor.AQUA + payout + ChatColor.GREEN + " Soul Tokens.");
                }
                return;
            }

            // Soul Fish button
            if (slot == sellHolder.getFishSlot()) {
                int payout = shift ? sell.sellAllSoulFish(player) : sell.sellOneSoulFish(player);

                if (payout <= 0) {
                    player.sendMessage(ChatColor.GRAY + "No Soul Fish found to sell.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Sold Soul Fish for "
                            + ChatColor.AQUA + payout + ChatColor.GREEN + " Soul Tokens.");
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
        // MAIN STORE / CATEGORY STORE (StoreHolder)
        // ---------------------------------------------------------------------
        if (!(e.getInventory().getHolder() instanceof StoreHolder holder)) return;

        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        // Block shift-moving items into GUI from player inventory
        if (!inTop && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
            return;
        }

        // Only run store logic when clicking inside the TOP inventory (GUI/chest)
        if (!inTop) return;

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

                    ItemStack give;

                    if (it.id.equalsIgnoreCase("random_plus_one_book")) {
                        give = RandomPlusOneBook.roll();
                    } else {
                        give = StoreItemBuilder.buildForGive(it);
                    }

                    GiveOrDrop.give(player, give);

                    String display = (give.hasItemMeta() && give.getItemMeta().hasDisplayName())
                            ? give.getItemMeta().getDisplayName()
                            : pretty(give.getType());

                    player.sendMessage(ChatColor.AQUA + "Purchased " + ChatColor.WHITE
                            + stripColor(display)
                            + ChatColor.AQUA + " for " + price + " Soul Tokens.");
                    return;
                }
            }

            case SELL -> {
                // Legacy fallback if still used anywhere
                int slot = e.getSlot();

                if (slot == manager.getSellEngine().getBackSlot()) {
                    StoreMainMenuGui.open(player, manager);
                    return;
                }

                if (slot == 22) {
                    int payout = manager.getSellEngine().sellAll(player);
                    if (payout <= 0) {
                        player.sendMessage(ChatColor.GRAY + "Nothing sellable found in your inventory.");
                    } else {
                        player.sendMessage(ChatColor.GREEN + "Sold items for "
                                + ChatColor.AQUA + payout + ChatColor.GREEN + " Soul Tokens.");
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