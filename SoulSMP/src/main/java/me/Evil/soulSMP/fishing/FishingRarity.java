package me.Evil.soulSMP.fishing;

public class FishingRarity {

    private final String id;
    private final String displayName;
    private final String color;
    private final double weight;
    private final double scoreMultiplier;

    public FishingRarity(String id, String displayName, String color, double weight, double scoreMultiplier) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.weight = weight;
        this.scoreMultiplier = scoreMultiplier;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
    public double getWeight() { return weight; }
    public double getScoreMultiplier() { return scoreMultiplier; }
}
