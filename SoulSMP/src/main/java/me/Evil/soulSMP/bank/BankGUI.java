package me.Evil.soulSMP.bank;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class BankGUI {

    public static final String TITLE = ChatColor.AQUA + "Soul Bank";

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(new BankHolder(), 9, TITLE);

        inv.setItem(2, button(Material.HOPPER, ChatColor.GREEN + "Deposit",
                List.of(ChatColor.GRAY + "Deposits all Soul Tokens",
                        ChatColor.GRAY + "from your inventory.")));

        inv.setItem(4, button(Material.BOOK, ChatColor.AQUA + "Info",
                List.of(ChatColor.GRAY + "View your bank balance",
                        ChatColor.GRAY + "and interest status.")));

        inv.setItem(6, button(Material.DISPENSER, ChatColor.GOLD + "Withdraw",
                List.of(ChatColor.GRAY + "Withdraw Soul Tokens.",
                        ChatColor.GRAY + "You will type the amount in chat.")));

        // fillers
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        player.openInventory(inv);
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Marker holder so we can detect "our" GUI safely. */
    public static class BankHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
