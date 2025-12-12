package me.Evil.soulSMP.fishing.journal;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class FishingJournalHolder implements InventoryHolder {

    private final UUID playerId;
    private final int page;

    public FishingJournalHolder(UUID playerId, int page) {
        this.playerId = playerId;
        this.page = page;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
