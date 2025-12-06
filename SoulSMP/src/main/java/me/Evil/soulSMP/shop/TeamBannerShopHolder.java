package me.Evil.soulSMP.shop;

import me.Evil.soulSMP.team.Team;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder for the Team Banner Shop GUI.
 */
public class TeamBannerShopHolder implements InventoryHolder {

    private final Team team;
    private Inventory inventory;

    public TeamBannerShopHolder(Team team) {
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
