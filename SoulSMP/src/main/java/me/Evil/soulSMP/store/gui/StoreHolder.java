package me.Evil.soulSMP.store.gui;

import me.Evil.soulSMP.store.StoreManager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class StoreHolder implements InventoryHolder {

    public enum View { MAIN, CATEGORY, SELL }

    private final View view;
    private final StoreManager manager;
    private final String categoryId;
    private final int page;
    private Inventory inv;

    public StoreHolder(View view, StoreManager manager, String categoryId, int page) {
        this.view = view;
        this.manager = manager;
        this.categoryId = categoryId;
        this.page = page;
    }

    public View getView() { return view; }
    public StoreManager getManager() { return manager; }
    public String getCategoryId() { return categoryId; }
    public int getPage() { return page; }

    public void setInventory(Inventory inv) { this.inv = inv; }

    @Override
    public Inventory getInventory() { return inv; }
}
