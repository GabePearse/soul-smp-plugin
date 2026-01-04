package me.Evil.soulSMP.beacon;

import java.util.List;

public class BeaconEffectLevel {

    private final int level;
    private final int cost;
    private final List<String> lore;

    public BeaconEffectLevel(int level, int cost, List<String> lore) {
        this.level = level;
        this.cost = cost;
        this.lore = lore;
    }

    public int getLevel() {
        return level;
    }

    public int getCost() {
        return cost;
    }

    public List<String> getLore() {
        return lore;
    }
}
