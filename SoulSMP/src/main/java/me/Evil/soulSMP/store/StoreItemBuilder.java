package me.Evil.soulSMP.store;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StoreItemBuilder {

    /** GUI display item: ALWAYS uses meta so it looks good in the shop. */
    public static ItemStack buildForDisplay(StoreItem item) {
        return buildInternal(item.give, item.price, true);
    }

    /** Item given to player: VANILLA unless give.meta == true. */
    public static ItemStack buildForGive(StoreItem item) {
        if (!item.give.meta) {
            // pure vanilla stack (material + amount only)
            return new ItemStack(item.give.material, Math.max(1, item.give.amount));
        }
        return buildInternal(item.give, item.price, true);
    }

    /** Internal builder that applies meta + placeholders. */
    private static ItemStack buildInternal(StoreItem.GiveItem give, int price, boolean applyMeta) {
        ItemStack item = new ItemStack(give.material, Math.max(1, give.amount));
        if (!applyMeta) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String materialPretty = pretty(give.material);
        String amountStr = String.valueOf(Math.max(1, give.amount));
        String priceStr = String.valueOf(price);

        if (give.name != null) {
            meta.setDisplayName(color(applyPlaceholders(give.name, materialPretty, amountStr, priceStr)));
        }

        if (give.lore != null && !give.lore.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String l : give.lore) {
                lore.add(color(applyPlaceholders(l, materialPretty, amountStr, priceStr)));
            }
            meta.setLore(lore);
        }

        // Apply other meta ONLY when meta: true exists (this whole method is only used then)
        meta.setUnbreakable(give.unbreakable);

        if (give.customModelData != null) {
            try { meta.setCustomModelData(give.customModelData); } catch (Throwable ignored) {}
        }

        // enchants format: "namespace:key:level"
        for (String e : give.enchants) {
            String[] parts = e.split(":");
            if (parts.length < 3) continue;

            int lvl;
            try { lvl = Integer.parseInt(parts[2]); }
            catch (Exception ex) { continue; }

            Enchantment ench = Enchantment.getByKey(new NamespacedKey(parts[0], parts[1]));
            if (ench != null) meta.addEnchant(ench, lvl, true);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static String applyPlaceholders(String s, String materialPretty, String amount, String price) {
        if (s == null) return "";
        return s
                .replace("{MATERIAL}", materialPretty)
                .replace("{amount}", amount)
                .replace("{price}", price);
    }

    private static String pretty(Material m) {
        if (m == null) return "Unknown";
        String raw = m.name().toLowerCase(Locale.US);
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
