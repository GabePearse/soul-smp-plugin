package me.Evil.soulSMP.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;


public class EnderChestBlocker implements Listener {
    @EventHandler
    public void onEnderChestOpen(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null &&
                event.getClickedBlock().getType() == Material.ENDER_CHEST &&
                event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Ender Chests are disabled.");
        }
    }
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.ENDER_CHEST) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place Ender Chests.");
        }
    }
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() != null &&
                event.getRecipe().getResult().getType() == Material.ENDER_CHEST) {
            event.getInventory().setResult(null);
        }
    }
}

