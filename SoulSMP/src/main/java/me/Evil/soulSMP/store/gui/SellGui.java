package me.Evil.soulSMP.store.gui;

import me.Evil.soulSMP.store.StoreManager;
import me.Evil.soulSMP.store.sell.SellHolder;
import me.Evil.soulSMP.store.sell.MaterialSellRule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SellGui {

    public static void open(Player player, StoreManager manager) {
        var sell = manager.getSellEngine();

        SellHolder holder = new SellHolder(manager);
        holder.setBackSlot(sell.getBackSlot());
        holder.setFishSlot(22);

        Inventory inv = Bukkit.createInventory(
                holder,
                sell.getSellSize(),
                color(sell.getSellTitle())
        );
        holder.setInventory(inv);

        // filler
        ItemStack filler = new ItemStack(sell.getFillerMat());
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(color(sell.getFillerName()));
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // Place Soul Fish sell button
        inv.setItem(holder.getFishSlot(), make(
                Material.FISHING_ROD,
                "&bSell Soul Fish",
                List.of(
                        "&7Left click: sell &f1 &7Soul Fish",
                        "&7Shift-left click: sell &fall &7Soul Fish"
                )
        ));

        // Place material sell buttons
        // Layout slots: 10-16, 19-25, 28-34 (nice 3 rows)
        List<Integer> slots = new ArrayList<>();
        for (int s : new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34}) slots.add(s);

        // Avoid clobbering fish slot if it overlaps
        slots.remove((Integer) holder.getFishSlot());

        // Stable order by material name
        List<MaterialSellRule> rules = new ArrayList<>(sell.getMaterialRules().values());
        rules.sort(Comparator.comparing(r -> r.material.name()));

        int idx = 0;
        for (MaterialSellRule rule : rules) {
            if (idx >= slots.size()) break;
            int slot = slots.get(idx++);

            int unit = rule.unit;
            int payout = rule.payout;

            inv.setItem(slot, make(
                    rule.material,
                    "&aSell " + pretty(rule.material),
                    List.of(
                            "&7Unit: &f" + unit,
                            "&7Payout per unit: &b" + payout + " &7Soul Tokens",
                            "",
                            "&7Left click: sell &f1 unit",
                            "&7Shift-left click: sell &fall units"
                    )
            ));

            holder.bindMaterialSlot(slot, rule.material);
        }

        // Back button
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

    private static String pretty(Material m) {
        String[] parts = m.name().toLowerCase(Locale.US).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
