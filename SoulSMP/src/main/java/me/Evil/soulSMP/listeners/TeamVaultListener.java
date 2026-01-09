package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.bannershop.BannerShopSettings;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.bannershop.TeamBannerShopGui;
import me.Evil.soulSMP.upkeep.TeamUpkeepManager;
import me.Evil.soulSMP.vault.TeamVaultHolder;
import me.Evil.soulSMP.vault.TeamVaultManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles clicks and drags inside team vault inventories,
 * enforcing locked slots, preventing locked panes from being moved,
 * and opening the shop from the last slot.
 */
public class TeamVaultListener implements Listener {

    private final TeamVaultManager vaultManager;
    private final BannerShopSettings bannerShopSettings;
    private final TeamUpkeepManager upkeepManager;

    public TeamVaultListener(TeamVaultManager vaultManager,
                             BannerShopSettings bannerShopSettings,
                             TeamUpkeepManager upKeepManager) {
        this.vaultManager = vaultManager;
        this.bannerShopSettings = bannerShopSettings;
        this.upkeepManager = upKeepManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TeamVaultHolder holder)) return;

        Team team = holder.getTeam();
        Player player = (Player) event.getWhoClicked();

        int topSize = top.getSize(); // should be 27
        int rawSlot = event.getRawSlot();

        // Only care about clicks in the top inventory (the vault)
        if (rawSlot < 0 || rawSlot >= topSize) return;

        int lastIndex = topSize - 1; // 26 = shop slot

        // Block number-key swapping into vault
        if (event.getClick().isKeyboardClick()) {
            event.setCancelled(true);
            return;
        }

        ItemStack current = event.getCurrentItem();

        // Prevent moving locked panes themselves
        if (vaultManager.isLockedPane(current)) {
            event.setCancelled(true);
            return;
        }

        // Shop icon clicked
        if (rawSlot == lastIndex) {
            event.setCancelled(true);
            player.closeInventory();
            TeamBannerShopGui.open(player, team, bannerShopSettings, upkeepManager);
            return;
        }

        // Locked slot logic
        if (vaultManager.isSlotLocked(team, rawSlot)) {
            event.setCancelled(true);
        }

        // Otherwise: normal click inside an unlocked vault slot is allowed
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TeamVaultHolder holder)) return;

        Team team = holder.getTeam();
        int topSize = top.getSize();

        // Check every raw slot involved in the drag
        for (int slot : event.getRawSlots()) {
            if (slot < topSize && vaultManager.isSlotLocked(team, slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TeamVaultHolder holder)) return;

        Team team = holder.getTeam();
        vaultManager.saveVault(team);
    }

}
