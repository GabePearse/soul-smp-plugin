package me.Evil.soulSMP.fishing;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class CustomFishGenerator {

    private final FishingConfig cfg;
    private final NamespacedKey typeKey, rarityKey, weightKey, scoreKey, chanceKey;

    private static final double LUCK_BIAS_PER_LEVEL = 0.10;

    // cached rank map (Common=0 ... Divine=highest)
    private final Map<String, Integer> rarityRank = new HashMap<>();

    public CustomFishGenerator(FishingConfig cfg,
                               NamespacedKey typeKey,
                               NamespacedKey rarityKey,
                               NamespacedKey weightKey,
                               NamespacedKey scoreKey,
                               NamespacedKey chanceKey) {
        this.cfg = cfg;
        this.typeKey = typeKey;
        this.rarityKey = rarityKey;
        this.weightKey = weightKey;
        this.scoreKey = scoreKey;
        this.chanceKey = chanceKey;

        buildRarityRank();
    }

    private void buildRarityRank() {
        // Rank by score-multiplier ascending (lowest = most common)
        List<FishingRarity> list = new ArrayList<>(cfg.rarities.values());
        list.sort(Comparator.comparingDouble(FishingRarity::getScoreMultiplier));

        for (int i = 0; i < list.size(); i++) {
            rarityRank.put(list.get(i).getId(), i);
        }
    }

    // --- Weighted fish-type selection (chance field) ---
    private FishType rollFishType() {
        if (cfg.fishTypes.isEmpty()) return null;

        double total = 0.0;
        for (FishType t : cfg.fishTypes.values()) total += Math.max(0.0, t.getChance());
        if (total <= 0.0) return cfg.fishTypes.values().iterator().next();

        double r = Math.random() * total;
        for (FishType t : cfg.fishTypes.values()) {
            r -= Math.max(0.0, t.getChance());
            if (r <= 0) return t;
        }
        return cfg.fishTypes.values().iterator().next();
    }

    // Apply Luck-of-the-Sea bias to a base weight based on rarity rank.
    private double applyLuckBias(double baseWeight, String rarityId, int luckLevel) {
        if (luckLevel <= 0) return Math.max(0.0, baseWeight);

        int rank = rarityRank.getOrDefault(rarityId, 0);
        double baseMult = 1.0 + (LUCK_BIAS_PER_LEVEL * luckLevel);

        // rarer => higher rank => larger multiplier
        double mult = Math.pow(baseMult, rank);
        return Math.max(0.0, baseWeight) * mult;
    }

    // --- Weighted rarity selection (with per-fish overrides + luck bias) ---
    private FishingRarity rollRarity(FishType type, int luckLevel) {
        if (cfg.rarities.isEmpty()) return null;

        Map<String, Double> perFish = type.getRarityWeights();
        boolean usePerFish = perFish != null && !perFish.isEmpty();

        // Build adjusted weights for selection
        Map<String, Double> adjusted = new HashMap<>();
        double total = 0.0;

        if (usePerFish) {
            for (Map.Entry<String, Double> e : perFish.entrySet()) {
                String rid = e.getKey();
                if (!cfg.rarities.containsKey(rid)) continue;

                double w = applyLuckBias(e.getValue(), rid, luckLevel);
                if (w <= 0) continue;

                adjusted.put(rid, w);
                total += w;
            }
        } else {
            for (FishingRarity r : cfg.rarities.values()) {
                double w = applyLuckBias(r.getWeight(), r.getId(), luckLevel);
                if (w <= 0) continue;

                adjusted.put(r.getId(), w);
                total += w;
            }
        }

        if (total <= 0.0) return cfg.rarities.values().iterator().next();

        double roll = Math.random() * total;
        for (Map.Entry<String, Double> e : adjusted.entrySet()) {
            roll -= e.getValue();
            if (roll <= 0) return cfg.rarities.get(e.getKey());
        }

        return cfg.rarities.values().iterator().next();
    }

    // NEW: compute exact chance after luck bias (P(type) * P(rarity|type))
    private double computeChance(FishType type, FishingRarity rarity, int luckLevel) {
        // P(type)
        double totalType = 0.0;
        for (FishType t : cfg.fishTypes.values()) totalType += Math.max(0.0, t.getChance());
        double pType = (totalType <= 0.0)
                ? (1.0 / Math.max(1, cfg.fishTypes.size()))
                : (Math.max(0.0, type.getChance()) / totalType);

        // P(rarity|type) using SAME biased weights as rollRarity
        Map<String, Double> perFish = type.getRarityWeights();
        boolean usePerFish = perFish != null && !perFish.isEmpty();

        double total = 0.0;
        double target = 0.0;

        if (usePerFish) {
            for (Map.Entry<String, Double> e : perFish.entrySet()) {
                String rid = e.getKey();
                if (!cfg.rarities.containsKey(rid)) continue;

                double w = applyLuckBias(e.getValue(), rid, luckLevel);
                total += w;
                if (rid.equalsIgnoreCase(rarity.getId())) target = w;
            }
        } else {
            for (FishingRarity r : cfg.rarities.values()) {
                double w = applyLuckBias(r.getWeight(), r.getId(), luckLevel);
                total += w;
                if (r.getId().equalsIgnoreCase(rarity.getId())) target = w;
            }
        }

        double pR = (total <= 0.0) ? 0.0 : (target / total);

        return pType * pR;
    }

    // --- Generates the final ItemStack ---
    public ItemStack generateFish(int luckLevel) {
        FishType type = rollFishType();
        if (type == null) return null;

        FishingRarity rarity = rollRarity(type, luckLevel);
        if (rarity == null) return null;

        // weight from rarity range
        double minW = rarity.getMinWeight();
        double maxW = rarity.getMaxWeight();
        if (maxW < minW) { double tmp = minW; minW = maxW; maxW = tmp; }
        double weight = minW + (Math.random() * (maxW - minW));

        // Score formula
        double score =
                type.getBaseScore() *
                        rarity.getScoreMultiplier() *
                        (weight / 10.0);

        double chance = computeChance(type, rarity, luckLevel);

        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = type.getDisplayFormat()
                .replace("{rarity_color}", rarity.getColor())
                .replace("{rarity_name}", rarity.getId())
                .replace("{type_name}", type.getId())
                .replace("{weight}", String.format("%.1f", weight));

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        List<String> lore = new ArrayList<>();
        for (String line : cfg.loreLines) {
            lore.add(ChatColor.translateAlternateColorCodes('&',
                    line.replace("{rarity_color}", rarity.getColor())
                            .replace("{rarity_name}", rarity.getId())
                            .replace("{type_name}", type.getId())
                            .replace("{weight}", String.format("%.1f", weight))));
        }
        meta.setLore(lore);

        // NBT/PDC
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.getId());
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity.getId());
        meta.getPersistentDataContainer().set(weightKey, PersistentDataType.DOUBLE, weight);
        meta.getPersistentDataContainer().set(scoreKey, PersistentDataType.DOUBLE, score);
        meta.getPersistentDataContainer().set(chanceKey, PersistentDataType.DOUBLE, chance);

        item.setItemMeta(meta);
        return item;
    }

    // Helper: use if you want to call generator without passing luck (defaults to 0)
    public ItemStack generateFish() {
        return generateFish(0);
    }
}
