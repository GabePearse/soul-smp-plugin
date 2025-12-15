package me.Evil.soulSMP.store.gui;

import me.Evil.soulSMP.store.StoreItem;
import me.Evil.soulSMP.store.StoreItemBuilder;
import me.Evil.soulSMP.store.StoreManager;
import me.Evil.soulSMP.store.gui.holder.StoreHolder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class StoreCategoryGui {

    public static void open(Player player, StoreManager manager, String categoryId, int page) {
        var s = manager.getSettings();

        String title = s.getCategoryTitle().replace("{category}", categoryId);
        Inventory inv = Bukkit.createInventory(
                new StoreHolder(StoreHolder.View.CATEGORY, manager, categoryId, page),
                s.getCategorySize(),
                color(title)
        );

        // filler
        ItemStack filler = new ItemStack(s.getCategoryFillerMat());
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(color(s.getCategoryFillerName()));
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // Build list of store items
        List<StoreItem> items = new ArrayList<>(manager.getSettings().getBuyItems(categoryId).values());

        // Pagination (only items with configured slots; but also allow > 1 page by slot ranges)
        // Simple: we place items by their configured slot and ignore page if slot already absolute.
        // If you want true paging by list order, we can switch to dynamic slot maps later.
        for (StoreItem it : items) {
            ItemStack display = StoreItemBuilder.build(it.give);

            // add price line to lore
            ItemMeta dm = display.getItemMeta();
            if (dm != null) {
                var lore = dm.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add(color("&7Cost: &b" + it.price + " &7Soul Tokens"));
                dm.setLore(lore);
                display.setItemMeta(dm);
            }

            if (it.slot >= 0 && it.slot < inv.getSize()) inv.setItem(it.slot, display);
        }

        // Back button
        inv.setItem(s.getBackSlot(), button("&cBack", org.bukkit.Material.BARRIER));

        player.openInventory(inv);
    }

    private static ItemStack button(String name, org.bukkit.Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
