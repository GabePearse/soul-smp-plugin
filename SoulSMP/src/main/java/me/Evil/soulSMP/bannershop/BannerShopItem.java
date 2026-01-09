package me.Evil.soulSMP.bannershop;

import org.bukkit.Material;

import java.util.List;


public class BannerShopItem {

    public enum ShopItemType {
        CLOSE,
        RADIUS,
        BEACON_MENU,
        LIVES,
        STORAGE,
        DIMENSION_MENU,
        DIMENSION_BANNER,
        DIMENSION_TELEPORT,
        UPKEEP,
        BACK,
        UNKNOWN
    }



    private final boolean isBackButton;
    private final ShopItemType type;
    private final int slot;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final String dimensionKey;

    // Configurable values
    private final int maxRadius;
    private final int baseCost;
    private final double costMultiplier;

    public BannerShopItem(ShopItemType type, int slot, Material material, String displayName,
                          List<String> lore, int maxRadius, int baseCost, double multiplier,
                          String dimensionKey, boolean isBackButton) {
        this.type = type;
        this.slot = slot;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.maxRadius = maxRadius;
        this.baseCost = baseCost;
        this.costMultiplier = multiplier;
        this.dimensionKey = dimensionKey;
        this.isBackButton = isBackButton;
    }

    public boolean isBackButton() { return isBackButton; }
    public ShopItemType getType() { return type; }
    public int getSlot() { return slot; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public String getDimensionKey() {
        return dimensionKey;
    }

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
