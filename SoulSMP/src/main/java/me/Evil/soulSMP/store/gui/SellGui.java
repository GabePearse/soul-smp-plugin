package me.Evil.soulSMP.store.gui;

import me.Evil.soulSMP.store.StoreManager;
import me.Evil.soulSMP.store.gui.holder.StoreHolder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class SellGui {

    public static void open(Player player, StoreManager manager) {
        var sell = manager.getSellEngine();

        Inventory inv = Bukkit.createInventory(
                new StoreHolder(StoreHolder.View.SELL, manager, "sell", 0),
                sell.getSellSize(),
                color(sell.getSellTitle())
        );

        // filler
        ItemStack filler = new ItemStack(sell.getFillerMat());
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(color(sell.getFillerName()));
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // Sell All
        inv.setItem(22, make(Material.EMERALD, "&aSell All",
                List.of("&7Sells all configured materials",
                        "&7and any Soul Fish in your inventory.")));

        // Back
        inv.setItem(sell.getBackSlot(), make(Material.BARRIER, "&cBack", List.of("&7Return to store menu.")));

        player.openInventory(inv);
    }

    private static ItemStack make(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(lore.stream().map(SellGui::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
