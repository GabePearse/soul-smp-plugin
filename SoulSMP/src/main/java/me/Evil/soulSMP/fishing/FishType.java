package me.Evil.soulSMP.fishing;

import org.bukkit.Material;
import java.util.HashMap;
import java.util.Map;

public class FishType {

    private final String id;
    private final Material material;
    private final String displayFormat;
    private final int baseScore;

    // NEW
    private double chance = 1.0;
    private final Map<String, Double> rarityWeights = new HashMap<>();

    public FishType(String id, Material material, String displayFormat, int baseScore) {
        this.id = id;
        this.material = material;
        this.displayFormat = displayFormat;
        this.baseScore = baseScore;
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public String getDisplayFormat() { return displayFormat; }
    public int getBaseScore() { return baseScore; }

    // --- Fish spawn chance ---
    public double getChance() { return chance; }
    public void setChance(double chance) { this.chance = chance; }

    // --- Optional per-fish rarity weights ---
    public Map<String, Double> getRarityWeights() {
        return rarityWeights;
    }
}
