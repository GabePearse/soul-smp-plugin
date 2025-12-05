package me.Evil.soulSMP.vault;

import me.Evil.soulSMP.team.Team;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder for team vault inventories.
 * Lets us know which team the vault belongs to when handling clicks.
 */
public class TeamVaultHolder implements InventoryHolder {

    private final Team team;
    private Inventory inventory;

    public TeamVaultHolder(Team team) {
        this.team = team;
    }

    public Team getTeam() {
        return team;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
