package me.Evil.soulSMP.shop;

import org.bukkit.Material;

import java.util.List;

public class BannerShopItem {

    public enum ShopItemType {
        RADIUS,
        BEACON_MENU,
        LIVES,
        STORAGE,
        CLOSE,
        UNKNOWN
    }

    private final String id;
    private final ShopItemType type;
    private final int slot;
    private final Material material;
    private final String displayName;
    private final List<String> lore;

    // Configurable values
    private final int maxRadius;
    private final int baseCost;
    private final double costMultiplier;

    public BannerShopItem(
            String id,
            ShopItemType type,
            int slot,
            Material material,
            String displayName,
            List<String> lore,
            int maxRadius,
            int baseCost,
            double costMultiplier
    ) {
        this.id = id;
        this.type = type;
        this.slot = slot;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.maxRadius = maxRadius;
        this.baseCost = baseCost;
        this.costMultiplier = costMultiplier;
    }

    public String getId() { return id; }
    public ShopItemType getType() { return type; }
    public int getSlot() { return slot; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }

    public int getMaxRadius() { return maxRadius; }
    public int getBaseCost() { return baseCost; }
    public double getCostMultiplier() { return costMultiplier; }

    /**
     * Generic geometric scaling:
     * cost = baseCost * (multiplier ^ stepIndex)
     */
    public int getScaledCost(int stepIndex) {
        if (stepIndex < 0) stepIndex = 0;
        double value = baseCost * Math.pow(costMultiplier, stepIndex);
        return (int) Math.round(value);
    }

    /**
     * Convenience method for radius specifically.
     * radius 1 -> stepIndex 0, radius 2 -> stepIndex 1, etc.
     */
    public int getRadiusUpgradeCost(int currentRadius) {
        return getScaledCost(currentRadius - 1);
    }
}
