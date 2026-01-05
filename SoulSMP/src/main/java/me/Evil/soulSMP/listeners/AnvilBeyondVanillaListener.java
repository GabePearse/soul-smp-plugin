package me.Evil.soulSMP.listeners;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.HashMap;
import java.util.Map;

public class AnvilBeyondVanillaListener implements Listener {

    // Safety cap so someone can't create a 2-billion-level anvil cost and break UI
    private static final int MAX_DISPLAY_COST = 1_000_000;

    // Exponential pricing: each level step costs x the previous
    private static final double EXP_BASE = 1.25;

    // Global multiplier so costs aren’t tiny (tune this)
    private static final int BASE_STEP_COST = 4;

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView() instanceof AnvilView view)) return;

        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);
        if (left == null || right == null) return;

        boolean leftBookLike = isBookLike(left);
        boolean rightBookLike = isBookLike(right);

        // Allowed combinations:
        // - item + item (same type)
        // - book-like + book-like
        // - item + book-like (either side)
        boolean sameTypeItems = (!leftBookLike && !rightBookLike && left.getType() == right.getType());
        boolean bothBooks = (leftBookLike && rightBookLike);
        boolean itemAndBook = (leftBookLike ^ rightBookLike);

        if (!(sameTypeItems || bothBooks || itemAndBook)) return;

        // Use vanilla result if it exists (keeps rename/repair behavior), else clone base:
        // - If one side is item and the other is book, base is the item.
        // - If both are books, base is left.
        ItemStack vanilla = event.getResult();
        ItemStack base = (itemAndBook ? (leftBookLike ? right : left) : left);
        ItemStack result = (vanilla != null ? vanilla.clone() : base.clone());

        // IMPORTANT FIX:
        // If this is book+book, always force result to be ENCHANTED_BOOK so we never end up with
        // a plain BOOK holding unsafe enchants (which can later stack with stored enchants).
        if (bothBooks) {
            ensureEnchantedBook(result);
        }

        Map<Enchantment, Integer> leftEnchants = getAllEnchants(left);
        Map<Enchantment, Integer> rightEnchants = getAllEnchants(right);
        if (rightEnchants.isEmpty()) return;

        // Start from what result already has (vanilla may have applied some)
        Map<Enchantment, Integer> merged = getAllEnchants(result);

        boolean changed = false;
        int computedCost = 0;

        for (Map.Entry<Enchantment, Integer> e : rightEnchants.entrySet()) {
            Enchantment ench = e.getKey();
            int rLevel = e.getValue();

            // Respect conflicts (vanilla-ish)
            if (conflictsWithAny(ench, merged)) continue;

            boolean alreadyPresent = leftEnchants.containsKey(ench) || merged.containsKey(ench);

            // Applicability rules:
            // - If result is an enchanted book: allow any enchant (stored)
            // - Otherwise: only allow if it can go on the item OR already present
            boolean resultIsBook = isEnchantedBook(result);
            if (!resultIsBook && !alreadyPresent && !ench.canEnchantItem(result)) continue;

            int leftLevel = leftEnchants.getOrDefault(ench, 0);
            int baseLevel = merged.getOrDefault(ench, 0);
            int current = Math.max(leftLevel, baseLevel);

            int newLevel;
            if (current > 0 && current == rLevel) {
                // Beyond-vanilla combine: VIII + VIII => IX
                newLevel = current + 1;
            } else {
                // Otherwise take the max (book adds to unenchanted item, etc.)
                newLevel = Math.max(current, rLevel);
            }

            if (newLevel != baseLevel) {
                merged.put(ench, newLevel);
                changed = true;

                // ----- XP scaling (true exponential per level step) -----
                // If current=5 -> new=6 : charge levelStepCost(6)
                // If current=0 -> new=8 : charge sum(levelStepCost(1..8))
                computedCost += costForUpgrade(ench, current, newLevel);
            }
        }

        if (!changed) return;

        // Apply merged enchants cleanly (fixes Prot6+Prot5 double-line bug)
        applyMergedEnchantsClean(result, merged);

        event.setResult(result);

        // Remove "Too Expensive"
        view.setMaximumRepairCost(Integer.MAX_VALUE);

        int vanillaCost = Math.max(0, view.getRepairCost());
        int finalCost = vanillaCost + computedCost;

        if (finalCost > MAX_DISPLAY_COST) finalCost = MAX_DISPLAY_COST;
        if (finalCost < 1) finalCost = 1;

        view.setRepairCost(finalCost);
    }

    private boolean conflictsWithAny(Enchantment ench, Map<Enchantment, Integer> current) {
        for (Enchantment existing : current.keySet()) {
            if (existing.equals(ench)) continue;
            if (existing.conflictsWith(ench)) return true;
        }
        return false;
    }

    private boolean isBookLike(ItemStack item) {
        if (item == null) return false;
        Material t = item.getType();
        return t == Material.BOOK || t == Material.ENCHANTED_BOOK;
    }

    private boolean isEnchantedBook(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof EnchantmentStorageMeta;
    }

    /**
     * Force an ItemStack to be ENCHANTED_BOOK with EnchantmentStorageMeta.
     */
    private void ensureEnchantedBook(ItemStack item) {
        if (item == null) return;
        if (item.getType() != Material.ENCHANTED_BOOK) {
            item.setType(Material.ENCHANTED_BOOK);
        }
        // Touch meta to ensure it's the right type
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta)) {
            // In practice Paper should give EnchantmentStorageMeta for ENCHANTED_BOOK automatically.
            // But if something weird happens, resetting type then re-get meta usually fixes it.
            item.setItemMeta(item.getItemMeta());
        }
    }

    /**
     * Reads BOTH normal enchants and (if enchanted book) stored enchants into one map.
     */
    private Map<Enchantment, Integer> getAllEnchants(ItemStack item) {
        Map<Enchantment, Integer> out = new HashMap<>();
        if (item == null) return out;

        out.putAll(item.getEnchantments());

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta esm) {
            out.putAll(esm.getStoredEnchants());
        }

        return out;
    }

    /**
     * Cleanly clears BOTH direct enchants AND stored enchants, then re-applies merged:
     * - If result is ENCHANTED_BOOK => stored enchants
     * - else => direct unsafe enchants
     *
     * This prevents "Protection VI" + "Protection V" showing at once.
     */
    private void applyMergedEnchantsClean(ItemStack result, Map<Enchantment, Integer> merged) {
        if (result == null) return;

        // Clear direct enchants on the ItemStack
        for (Enchantment ench : new HashMap<>(result.getEnchantments()).keySet()) {
            result.removeEnchantment(ench);
        }

        ItemMeta meta = result.getItemMeta();

        // Clear stored enchants if present
        if (meta instanceof EnchantmentStorageMeta esm) {
            for (Enchantment ench : new HashMap<>(esm.getStoredEnchants()).keySet()) {
                esm.removeStoredEnchant(ench);
            }
            result.setItemMeta(esm);
            meta = result.getItemMeta(); // refresh
        }

        // Re-apply
        if (isEnchantedBook(result) && meta instanceof EnchantmentStorageMeta esm) {
            for (Map.Entry<Enchantment, Integer> me : merged.entrySet()) {
                esm.addStoredEnchant(me.getKey(), me.getValue(), true);
            }
            result.setItemMeta(esm);
        } else {
            for (Map.Entry<Enchantment, Integer> me : merged.entrySet()) {
                result.addUnsafeEnchantment(me.getKey(), me.getValue());
            }
        }
    }

    /**
     * Exponential per-level step cost:
     * stepCost(level) = weight * BASE_STEP_COST * (EXP_BASE ^ (level - 1))
     */
    private int levelStepCost(Enchantment ench, int level) {
        int weight = enchantWeight(ench);
        double exp = Math.pow(EXP_BASE, Math.max(0, level - 1));
        return (int) Math.ceil(weight * BASE_STEP_COST * exp);
    }

    /**
     * Sum of step costs for every level you gain.
     * current=5 new=6 => cost(step 6)
     * current=0 new=8 => sum(step 1..8)
     */
    private int costForUpgrade(Enchantment ench, int current, int newLevel) {
        if (newLevel <= current) return 0;

        int cost = 0;
        for (int lvl = current + 1; lvl <= newLevel; lvl++) {
            cost += levelStepCost(ench, lvl);
        }
        return cost;
    }

    /**
     * Rough weighting similar in spirit to vanilla "rarity" costing, but simple and predictable.
     */
    private int enchantWeight(Enchantment ench) {
        if (ench.isTreasure()) return 10;
        if (ench.isCursed()) return 1;

        String key = ench.getKey().getKey(); // e.g. "protection_environmental"
        if (key.contains("protection")) return 6;
        if (key.contains("sharpness") || key.contains("power")) return 6;
        if (key.contains("efficiency")) return 5;
        if (key.contains("unbreaking")) return 4;
        if (key.contains("fortune") || key.contains("looting")) return 8;
        if (key.contains("silk_touch")) return 8;

        return 4;
    }
}
