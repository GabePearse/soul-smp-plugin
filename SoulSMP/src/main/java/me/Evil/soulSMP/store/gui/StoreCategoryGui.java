package me.Evil.soulSMP.store.gui;

import me.Evil.soulSMP.store.StoreItem;
import me.Evil.soulSMP.store.StoreItemBuilder;
import me.Evil.soulSMP.store.StoreManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StoreCategoryGui {

    public static void open(Player player, StoreManager manager, String categoryId, int page) {
        var s = manager.getSettings();

        // ✅ Capitalize FIRST LETTER of {category}, not the color code
        String prettyCategory = (categoryId == null || categoryId.isEmpty())
                ? ""
                : Character.toUpperCase(categoryId.charAt(0)) + categoryId.substring(1);

        String title = s.getCategoryTitle().replace("{category}", prettyCategory);

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

        for (StoreItem it : items) {
            ItemStack display = StoreItemBuilder.buildForDisplay(it);

            // add price line to lore
            ItemMeta dm = display.getItemMeta();
            if (dm != null) {
                List<String> lore = dm.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add("");
                lore.add(color("&7Cost: &b" + it.price + " &7Soul Tokens"));
                dm.setLore(lore);
                display.setItemMeta(dm);
            }

            if (it.slot >= 0 && it.slot < inv.getSize()) {
                inv.setItem(it.slot, display);
            }
        }

        // Back button
        inv.setItem(s.getBackSlot(), button("&cBack", Material.ARROW));

        player.openInventory(inv);
    }

    private static ItemStack button(String name, Material mat) {
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
