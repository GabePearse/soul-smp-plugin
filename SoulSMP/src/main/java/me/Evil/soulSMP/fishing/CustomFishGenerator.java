package me.Evil.soulSMP.fishing;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class CustomFishGenerator {

    private final FishingConfig cfg;
    private final NamespacedKey typeKey, rarityKey, weightKey, scoreKey;

    public CustomFishGenerator(FishingConfig cfg, NamespacedKey typeKey, NamespacedKey rarityKey,
                               NamespacedKey weightKey, NamespacedKey scoreKey) {
        this.cfg = cfg;
        this.typeKey = typeKey;
        this.rarityKey = rarityKey;
        this.weightKey = weightKey;
        this.scoreKey = scoreKey;
    }

    // --- Weighted rarity selection ---
    private FishingRarity rollRarity() {
        double total = cfg.rarities.values().stream().mapToDouble(FishingRarity::getWeight).sum();
        double r = Math.random() * total;

        for (FishingRarity rarity : cfg.rarities.values()) {
            r -= rarity.getWeight();
            if (r <= 0) return rarity;
        }
        return cfg.rarities.values().iterator().next(); // fallback
    }

    // --- Weighted fish-type selection ---
    private FishType rollFishType(FishingRarity rarity) {

        // Filter types usable at this rarity
        List<FishType> candidates = new ArrayList<>();
        for (FishType type : cfg.fishTypes.values()) {
            if (type.getAllowedRarities().contains(rarity.getId())) {
                candidates.add(type);
            }
        }

        if (candidates.isEmpty()) return null;

        // Weighted selection if overrides exist
        boolean hasWeights = candidates.stream()
                .anyMatch(f -> f.getSelectionWeights().containsKey(rarity.getId()));

        if (!hasWeights) {
            return candidates.get(new Random().nextInt(candidates.size()));
        }

        // Weighted pick
        double total = 0;
        for (FishType type : candidates) {
            total += type.getSelectionWeights().getOrDefault(rarity.getId(), 1.0);
        }

        double r = Math.random() * total;
        for (FishType type : candidates) {
            r -= type.getSelectionWeights().getOrDefault(rarity.getId(), 1.0);
            if (r <= 0) return type;
        }

        return candidates.get(0);
    }

    // --- Generates the final ItemStack ---
    public ItemStack generateFish() {

        FishingRarity rarity = rollRarity();
        FishType type = rollFishType(rarity);

        if (type == null) return null;

        FishType.WeightRange wr = type.getRanges().get(rarity.getId());
        double weight = wr.min + (Math.random() * (wr.max - wr.min));

        // Score formula
        double score =
                type.getBaseScore() *
                        rarity.getScoreMultiplier() *
                        (weight / 10.0);

        // Build item
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        String name = type.getDisplayFormat()
                .replace("{rarity_color}", rarity.getColor())
                .replace("{rarity_name}", rarity.getId())
                .replace("{type_name}", type.getId())
                .replace("{weight}", String.format("%.1f", weight));

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        // Lore
        List<String> lore = new ArrayList<>();
        for (String line : cfg.loreLines) {
            lore.add(ChatColor.translateAlternateColorCodes('&',
                    line.replace("{rarity_color}", rarity.getColor())
                            .replace("{rarity_name}", rarity.getId())
                            .replace("{type_name}", type.getId())
                            .replace("{weight}", String.format("%.1f", weight))));
        }
        meta.setLore(lore);

        // NBT
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.getId());
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity.getId());
        meta.getPersistentDataContainer().set(weightKey, PersistentDataType.DOUBLE, weight);
        meta.getPersistentDataContainer().set(scoreKey, PersistentDataType.DOUBLE, score);

        item.setItemMeta(meta);
        return item;
    }
}
