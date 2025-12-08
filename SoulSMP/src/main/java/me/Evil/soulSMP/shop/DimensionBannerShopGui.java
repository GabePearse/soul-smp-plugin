package me.Evil.soulSMP.shop;

import me.Evil.soulSMP.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class DimensionBannerShopGui {

    private static final int SIZE = 54;

    public static void open(Player player, Team team,
                            DimensionBannerShopSettings settings) {
        if (player == null || team == null || settings == null) return;

        DimensionBannerShopHolder holder = new DimensionBannerShopHolder(team);
        String title = ChatColor.DARK_AQUA + "Dimensional Banners";

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        // Fill background
        ItemStack filler = new ItemStack(settings.getFillerMaterial());
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(settings.getFillerName());
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        // Dimension items from dimension-shop.yml
        for (BannerShopItem item : settings.getItems()) {
            int slot = item.getSlot();
            if (slot < 0 || slot >= inv.getSize()) continue;

            ItemStack stack = new ItemStack(item.getMaterial());
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(item.getDisplayName());

                List<String> lore = new ArrayList<>();
                for (String line : item.getLore()) {
                    lore.add(applyPlaceholders(line, item, team));
                }
                meta.setLore(lore);

                stack.setItemMeta(meta);
            }
            inv.setItem(slot, stack);
        }

        player.openInventory(inv);
    }

    private static String applyPlaceholders(String line, BannerShopItem item, Team team) {
        switch (item.getType()) {
            case DIMENSION_BANNER -> {
                int cost = item.getBaseCost();
                boolean unlocked = team.hasDimensionalBannerUnlocked(item.getDimensionKey());
                line = line.replace("{cost}", String.valueOf(cost))
                        .replace("{status}", unlocked ? ChatColor.GREEN + "Unlocked" : ChatColor.RED + "Locked");
            }
            case DIMENSION_TELEPORT -> {
                int cost = item.getBaseCost();
                boolean unlocked = team.hasDimensionalTeleportUnlocked(item.getDimensionKey());
                line = line.replace("{cost}", String.valueOf(cost))
                        .replace("{status}", unlocked ? ChatColor.GREEN + "Unlocked" : ChatColor.RED + "Locked");
            }
            default -> {
                // no placeholders
            }
        }
        return line;
    }
}
