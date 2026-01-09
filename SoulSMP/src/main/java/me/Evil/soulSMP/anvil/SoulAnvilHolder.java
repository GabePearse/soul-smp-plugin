package me.Evil.soulSMP.anvil;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class SoulAnvilHolder implements InventoryHolder {

    private final UUID owner;
    private final SoulAnvilSession session;
    private Inventory inventory;

    public SoulAnvilHolder(UUID owner, SoulAnvilSession session) {
        this.owner = owner;
        this.session = session;
    }

    public UUID getOwner() {
        return owner;
    }

    public SoulAnvilSession getSession() {
        return session;
    }

    public void setInventory(Inventory inv) {
        this.inventory = inv;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
