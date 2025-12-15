package me.Evil.soulSMP.store.gui;

import me.Evil.soulSMP.store.StoreManager;
import me.Evil.soulSMP.store.sell.MaterialSellRule;
import me.Evil.soulSMP.store.sell.SellHolder;
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
        holder.setFishSlot(50);
        holder.setSellAllSlot(48);

        // ✅ Capitalize first visible letter of title (safe with color codes)
        String rawTitle = sell.getSellTitle();
        String title = capitalizeFirstVisible(rawTitle);

        Inventory inv = Bukkit.createInventory(
                holder,
                sell.getSellSize(),
                color(title)
        );
        holder.setInventory(inv);

        // ----------------------------
        // 1) Place buttons/items FIRST
        // ----------------------------

        // Sell All button
        inv.setItem(holder.getSellAllSlot(), make(
                Material.HOPPER,
                "&aSell All",
                List.of("&7Left-Click: sell &feverything sellable")
        ));

        // Soul Fish button
        inv.setItem(holder.getFishSlot(), make(
                Material.FISHING_ROD,
                "&bSell Soul Fish",
                List.of(
                        "&7Left-Click: Sell &f1 &7Soul Fish",
                        "&7Shift-Left-Click: Sell &fall &7Soul Fish"
                )
        ));

        // Back button
        inv.setItem(holder.getBackSlot(), make(
                Material.ARROW,
                "&cBack",
                List.of("&7Return to store menu.")
        ));

        // Material buttons (slot required)
        Set<Integer> reserved = Set.of(
                holder.getFishSlot(),
                holder.getSellAllSlot(),
                holder.getBackSlot()
        );

        Set<Integer> used = new HashSet<>(reserved);

        List<MaterialSellRule> rules = new ArrayList<>(sell.getMaterialRules().values());
        rules.sort(Comparator
                .comparingInt((MaterialSellRule r) -> r.slot)
                .thenComparing(r -> r.material.name())
        );

        for (MaterialSellRule rule : rules) {
            int slot = rule.slot;

            if (slot < 0) {
                Bukkit.getLogger().warning("[SellGui] Missing slot for material " + rule.material + " in store-sell.yml (materials." + rule.material.name() + ".slot)");
                continue;
            }

            if (slot >= inv.getSize()) {
                Bukkit.getLogger().warning("[SellGui] Slot " + slot + " out of bounds for material " + rule.material + " (inv size " + inv.getSize() + ")");
                continue;
            }

            if (reserved.contains(slot)) {
                Bukkit.getLogger().warning("[SellGui] Slot " + slot + " for material " + rule.material + " conflicts with reserved slot.");
                continue;
            }

            if (!used.add(slot)) {
                Bukkit.getLogger().warning("[SellGui] Duplicate slot " + slot + " already used; skipping material " + rule.material);
                continue;
            }

            inv.setItem(slot, make(
                    rule.material,
                    "&aSell " + pretty(rule.material),
                    List.of(
                            "&7Unit: &f" + formatUnit(rule.unit),
                            "&7Payout per unit: &b" + rule.payout + " &7Soul Tokens",
                            "",
                            "&7Left-Click: Sell &f1 unit",
                            "&7Shift-Left-Click: Sell &fall units"
                    )
            ));

            holder.bindMaterialSlot(slot, rule.material);
        }

        // ----------------------------
        // 2) Fill ONLY EMPTY slots LAST
        // ----------------------------
        ItemStack filler = new ItemStack(sell.getFillerMat());
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(color(sell.getFillerName()));
            filler.setItemMeta(fm);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }

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

    private static String formatUnit(int unit) {
        if (unit <= 0) return "0";

        if (unit % 64 == 0) {
            int stacks = unit / 64;
            return stacks + " stack" + (stacks == 1 ? "" : "s") + " &7(64×" + stacks + ")";
        }

        return unit + " items";
    }


    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    // ✅ Capitalizes first visible character (ignores & color codes)
    private static String capitalizeFirstVisible(String s) {
        if (s == null || s.isEmpty()) return s;

        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length) {
                i++;
                continue;
            }
            chars[i] = Character.toUpperCase(chars[i]);
            break;
        }
        return new String(chars);
    }

    private static String pretty(Material m) {
        String[] parts = m.name().toLowerCase(Locale.US).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty())
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
