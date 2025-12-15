package me.Evil.soulSMP.store;

import org.bukkit.Material;

import java.util.List;

public class StoreCategory {
    public final String id;
    public final int slot;
    public final Material iconMaterial;
    public final String iconName;
    public final List<String> iconLore;

    public StoreCategory(String id, int slot, Material iconMaterial, String iconName, List<String> iconLore) {
        this.id = id;
        this.slot = slot;
        this.iconMaterial = iconMaterial;
        this.iconName = iconName;
        this.iconLore = iconLore;
    }
}
