package me.Evil.soulSMP.store.gui;

import me.Evil.soulSMP.store.StoreManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class StoreMainMenuGui {

    public static void open(Player player, StoreManager manager) {
        var s = manager.getSettings();

        Inventory inv = Bukkit.createInventory(
                new StoreHolder(StoreHolder.View.MAIN, manager, null, 0),
                s.getMainSize(),
                color(s.getMainTitle())
        );

        // filler
        ItemStack filler = new ItemStack(s.getMainFillerMat());
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(color(s.getMainFillerName()));
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // categories
        for (var cat : s.getCategories().values()) {
            ItemStack icon = new ItemStack(cat.iconMaterial == null ? Material.STONE : cat.iconMaterial);
            ItemMeta im = icon.getItemMeta();
            if (im != null) {
                im.setDisplayName(color(cat.iconName));
                if (cat.iconLore != null && !cat.iconLore.isEmpty()) {
                    im.setLore(cat.iconLore.stream().map(StoreMainMenuGui::color).toList());
                }
                icon.setItemMeta(im);
            }
            inv.setItem(cat.slot, icon);
        }

        player.openInventory(inv);
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
