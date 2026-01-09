package me.Evil.soulSMP.bannershop;

import me.Evil.soulSMP.team.Team;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DimensionBannerShopHolder implements InventoryHolder {

    private final Team team;
    private Inventory inventory;

    public DimensionBannerShopHolder(Team team) {
        this.team = team;
    }

    @Nullable
    public Team getTeam() {
        return team;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
