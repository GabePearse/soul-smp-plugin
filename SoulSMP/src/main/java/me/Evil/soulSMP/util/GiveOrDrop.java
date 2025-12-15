package me.Evil.soulSMP.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class GiveOrDrop {
    private GiveOrDrop() {}

    public static void give(Player player, ItemStack item) {
        if (player == null || item == null || item.getAmount() <= 0) return;

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (leftover.isEmpty()) return;

        Location loc = player.getLocation();

        for (ItemStack left : leftover.values()) {
            if (left == null || left.getAmount() <= 0) continue;

            var dropped = player.getWorld().dropItem(loc, left); // no scatter
            dropped.setPickupDelay(20);

            // Optional anti-snipe if supported
            try { dropped.setOwner(player.getUniqueId()); } catch (Throwable ignored) {}
        }
    }
}
