package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.vault.TeamVaultHolder;
import me.Evil.soulSMP.vault.TeamVaultManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles clicks and drags inside team vault inventories,
 * enforcing locked slots and preventing locked panes from being moved.
 */
public class TeamVaultListener implements Listener {

    private final TeamVaultManager vaultManager;

    public TeamVaultListener(TeamVaultManager vaultManager) {
        this.vaultManager = vaultManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TeamVaultHolder holder)) return;

        Team team = holder.getTeam();
        Player player = (Player) event.getWhoClicked();

        int topSize = top.getSize(); // 27
        int rawSlot = event.getRawSlot();

        // Are we clicking inside the vault (top) or in the player inventory (bottom)?
        boolean inTop = rawSlot >= 0 && rawSlot < topSize;

        // If click is not in the vault itself, we don't care (except maybe for drags)
        if (!inTop) {
            // You can still add any rules for bottom-inventory if you want
            return;
        }

        // Current item in that slot
        ItemStack current = event.getCurrentItem();

        // Prevent ANY movement of locked panes themselves
        if (vaultManager.isLockedPane(current)) {
            event.setCancelled(true);
            return;
        }

    }


    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TeamVaultHolder holder)) return;

        Team team = holder.getTeam();
        int topSize = top.getSize();

        // 🔒 Check every raw slot involved in the drag
        for (int slot : event.getRawSlots()) {
            if (slot < topSize && vaultManager.isSlotLocked(team, slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

}
