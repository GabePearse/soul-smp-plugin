package me.Evil.soulSMP.upgrades;

import org.bukkit.Material;

import java.util.Map;

public class BeaconEffectDefinition {

    private final String id;
    private final String type;
    private final int slot;
    private final Material material;
    private final String displayName;
    private final boolean isBackButton;

    private final Map<Integer, BeaconEffectLevel> levels;

    /**
     * @param id          config id of the effect (e.g. "speed")
     * @param type        logical type from config (e.g. "SPEED", "BACK")
     * @param slot        GUI slot this effect goes in
     * @param material    icon material to display in the GUI
     * @param displayName display name for the GUI item
     * @param levels      map of level -> definition (cost + lore)
     */
    public BeaconEffectDefinition(String id,
                                  String type,
                                  int slot,
                                  Material material,
                                  String displayName,
                                  Map<Integer, BeaconEffectLevel> levels) {

        this.id = id;
        this.type = type;
        this.slot = slot;
        this.material = material;
        this.displayName = displayName;
        this.levels = levels;

        // Treat special entries as a configurable back button
        this.isBackButton =
                "BACK".equalsIgnoreCase(type) ||
                        "BACK_BUTTON".equalsIgnoreCase(type);
    }

    public boolean isBackButton() {
        return isBackButton;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Map<Integer, BeaconEffectLevel> getLevels() {
        return levels;
    }

    public int getMaxLevel() {
        return (levels != null) ? levels.size() : 0;
    }

    public BeaconEffectLevel getLevel(int level) {
        return (levels != null) ? levels.get(level) : null;
    }
}
