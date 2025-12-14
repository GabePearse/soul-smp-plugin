package me.Evil.soulSMP.fishing;

public class FishingRarity {

    private final String id;
    private final String displayName;
    private final String color;
    private final double weight;
    private final double scoreMultiplier;

    // NEW: weight range is now defined per-rarity
    private final double minWeight;
    private final double maxWeight;

    public FishingRarity(
            String id,
            String displayName,
            String color,
            double weight,
            double scoreMultiplier,
            double minWeight,
            double maxWeight
    ) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.weight = weight;
        this.scoreMultiplier = scoreMultiplier;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
    public double getWeight() { return weight; }
    public double getScoreMultiplier() { return scoreMultiplier; }

    public double getMinWeight() { return minWeight; }
    public double getMaxWeight() { return maxWeight; }
}
