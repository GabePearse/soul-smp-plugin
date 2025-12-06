package me.Evil.soulSMP.upgrades;

import org.bukkit.Material;

import java.util.Map;

public class BeaconEffectDefinition {

    private final String id;
    private final String type;
    private final int slot;
    private final Material material;
    private final String displayName;

    private final Map<Integer, BeaconEffectLevel> levels;

    public BeaconEffectDefinition(String id, String type, int slot, Material material, String displayName,
                                  Map<Integer, BeaconEffectLevel> levels) {
        this.id = id;
        this.type = type;
        this.slot = slot;
        this.material = material;
        this.displayName = displayName;
        this.levels = levels;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public int getSlot() { return slot; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public Map<Integer, BeaconEffectLevel> getLevels() { return levels; }

    public int getMaxLevel() { return levels.size(); }

    public BeaconEffectLevel getLevel(int level) {
        return levels.get(level);
    }
}
