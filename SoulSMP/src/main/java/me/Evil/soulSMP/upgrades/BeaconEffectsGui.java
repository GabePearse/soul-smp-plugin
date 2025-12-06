package me.Evil.soulSMP.upgrades;

import me.Evil.soulSMP.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class BeaconEffectsGui {

    public static void open(Player player, Team team, BeaconEffectSettings settings) {

        Inventory inv = Bukkit.createInventory(new BeaconEffectsHolder(team), 27,
                ChatColor.DARK_AQUA + "Beacon Effects");

        // Filler
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);

        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        for (BeaconEffectDefinition effect : settings.getAllEffects()) {

            int current = team.getEffectLevel(effect.getId());
            int max = effect.getMaxLevel();

            ItemStack item = new ItemStack(effect.getMaterial());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(effect.getDisplayName());

            List<String> lore = new ArrayList<>();

            if (current >= max) {
                lore.add(ChatColor.GREEN + "MAXED");
            } else {
                BeaconEffectLevel next = effect.getLevel(current + 1);
                for (String line : next.getLore()) {
                    lore.add(line.replace("{cost}", String.valueOf(next.getCost())));
                }
                lore.add("");
                lore.add(ChatColor.YELLOW + "Click to upgrade to level " + (current + 1));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(effect.getSlot(), item);
        }

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName(ChatColor.RED + "Close");
        close.setItemMeta(cm);

        inv.setItem(26, close);

        player.openInventory(inv);
    }
}
