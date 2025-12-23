package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.leaderboard.LeaderboardManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

public class LeaderboardProtectionListener implements Listener {

    private final LeaderboardManager lb;

    public LeaderboardProtectionListener(LeaderboardManager lb) {
        this.lb = lb;
    }

    private boolean isProtectedClaimBanner(Block b) {
        Location loc = lb.getDisplayLocation("biggestClaimBanner");
        if (loc == null) return false;

        World w = loc.getWorld();
        if (w == null) return false;

        // Compare block coords + world
        return b.getWorld().equals(w)
                && b.getX() == loc.getBlockX()
                && b.getY() == loc.getBlockY()
                && b.getZ() == loc.getBlockZ();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isProtectedClaimBanner(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isProtectedClaimBanner);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isProtectedClaimBanner);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (isProtectedClaimBanner(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (isProtectedClaimBanner(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
