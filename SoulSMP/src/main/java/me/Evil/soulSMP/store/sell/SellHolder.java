package me.Evil.soulSMP.store.sell;

import me.Evil.soulSMP.store.StoreManager;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class SellHolder implements InventoryHolder {

    private final StoreManager manager;
    private final Map<Integer, Material> materialBySlot = new HashMap<>();
    private int backSlot = 49;
    private int fishSlot = 22;

    private Inventory inv;

    public SellHolder(StoreManager manager) {
        this.manager = manager;
    }

    public StoreManager getManager() {
        return manager;
    }

    public Map<Integer, Material> getMaterialBySlot() {
        return materialBySlot;
    }

    public void bindMaterialSlot(int slot, Material material) {
        materialBySlot.put(slot, material);
    }

    public int getBackSlot() {
        return backSlot;
    }

    public void setBackSlot(int backSlot) {
        this.backSlot = backSlot;
    }

    public int getFishSlot() {
        return fishSlot;
    }

    public void setFishSlot(int fishSlot) {
        this.fishSlot = fishSlot;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void setInventory(Inventory inv) {
        this.inv = inv;
    }
}
