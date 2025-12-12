package me.Evil.soulSMP.fishing;

import org.bukkit.Material;

import java.util.*;

public class FishType {

    private final String id;
    private final Material material;
    private final String displayFormat;
    private final int baseScore;

    private final Set<String> allowedRarities = new HashSet<>();
    private final Map<String, WeightRange> ranges = new HashMap<>();
    private final Map<String, Double> selectionWeights = new HashMap<>();

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

    public Set<String> getAllowedRarities() { return allowedRarities; }
    public Map<String, WeightRange> getRanges() { return ranges; }
    public Map<String, Double> getSelectionWeights() { return selectionWeights; }

    public static class WeightRange {
        public final double min, max;
        public WeightRange(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }
}
