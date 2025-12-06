package me.Evil.soulSMP.upgrades;

import me.Evil.soulSMP.team.Team;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder for the Beacon Effects GUI.
 */
public class BeaconEffectsHolder implements InventoryHolder {

    private final Team team;
    private Inventory inventory;

    public BeaconEffectsHolder(Team team) {
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
