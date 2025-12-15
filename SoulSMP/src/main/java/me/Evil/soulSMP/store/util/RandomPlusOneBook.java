package me.Evil.soulSMP.store.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomPlusOneBook {

    private RandomPlusOneBook() {}

    public static ItemStack roll() {
        Enchantment ench = pickRandomEnchant();
        int level = ench.getMaxLevel() + 1;

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta == null) return book;

        // true = allow unsafe level (max+1)
        meta.addStoredEnchant(ench, level, true);
        book.setItemMeta(meta);
        return book;
    }

    private static Enchantment pickRandomEnchant() {
        List<Enchantment> pool = new ArrayList<>();

        for (Enchantment e : Enchantment.values()) {
            if (e == null) continue;

            // Optional: if you want to avoid “meta-defining” rolls, uncomment:
            // if (e.equals(Enchantment.PROTECTION_ENVIRONMENTAL)) continue;
            // if (e.equals(Enchantment.DAMAGE_ALL)) continue;
            // if (e.equals(Enchantment.DIG_SPEED)) continue;
            // if (e.equals(Enchantment.ARROW_DAMAGE)) continue;

            pool.add(e);
        }

        // failsafe
        if (pool.isEmpty()) return Enchantment.UNBREAKING;

        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
