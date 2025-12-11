package me.Evil.soulSMP.upgrades;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public class BeaconEffectDefinition {

    private final String id;
    private final int slot;
    private final String displayName;
    private final boolean isBackButton;

    private final Map<Integer, BeaconEffectLevel> levels;

    public BeaconEffectDefinition(String id, String displayName, int slot, boolean isBackButton, List<BeaconEffectLevel> levels) {
        this.id = id;
        this.displayName = displayName;
        this.slot = slot;
        this.levels = levels;
        this.isBackButton = isBackButton;
    }

    public boolean isBackButton() { return isBackButton; }
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
