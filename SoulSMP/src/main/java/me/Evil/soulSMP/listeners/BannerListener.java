package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

public class BannerListener implements Listener {

    private final TeamManager teamManager;

    public BannerListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    // =========================================================
    // BLOCK PLACE – claim protection + banner claim placement
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();

        // 1) Generic chunk-claim protection
        if (isProtectedClaimArea(block, player)) {
            event.setCancelled(true);
            return;
        }

        // 2) Banner-specific logic
        if (!isBannerBlock(block.getType())) {
            return;
        }

        Team playerTeam = teamManager.getTeamByPlayer(player);
        if (playerTeam == null) {
            // Non-team players can still place banners as decoration (outside protected claims)
            return;
        }

        ItemStack inHand = event.getItemInHand();
        if (inHand == null || inHand.getType() == Material.AIR) {
            return;
        }

        // Team must have a claimed design first
        if (!playerTeam.hasClaimedBannerDesign()) {
            player.sendMessage(ChatColor.RED + "Your team has not claimed a banner design yet. Use /team banner claim while holding one.");
            return;
        }

        // Only banners matching the claimed design can be used as the team banner location
        if (!playerTeam.matchesBannerDesign(inHand)) {
            // Allow placement, but it's just decorative
            player.sendMessage(ChatColor.GRAY + "This banner does not match your claimed team banner design.");
            return;
        }

        // It DOES match their design.
        // If the team already has a banner location, don't override it.
        if (playerTeam.hasBannerLocation()) {
            player.sendMessage(ChatColor.YELLOW + "Your team already has a claimed banner location. Use /team banner remove first.");
            return;
        }

        // Set this placement as their official team banner location.
        playerTeam.setBannerLocation(block.getLocation());
        player.sendMessage(ChatColor.GREEN + "This banner is now your team's claimed banner location!");
        player.sendMessage(ChatColor.YELLOW + "It cannot be broken or destroyed. Use /team banner remove (owner only) to remove it.");
    }

    // =========================================================
    // BLOCK BREAK – indestructible banner + claim protection
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 1) Protect the banner block itself absolutely
        if (isBannerBlock(block.getType())) {
            Team owningTeam = getTeamByBannerBlock(block);
            if (owningTeam != null) {
                event.setCancelled(true);

                if (owningTeam.isMember(player.getUniqueId())) {
                    if (owningTeam.getOwner().equals(player.getUniqueId())) {
                        player.sendMessage(ChatColor.YELLOW + "You cannot break your team banner directly.");
                        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.AQUA + "/team banner remove" + ChatColor.YELLOW + " to remove it.");
                    } else {
                        player.sendMessage(ChatColor.RED + "You cannot break your team's banner.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You cannot break another team's banner.");
                }
                return;
            }
        }

        // 2) Generic claim protection in chunks for all other blocks
        if (isProtectedClaimArea(block, player)) {
            event.setCancelled(true);
        }
    }

    // =========================================================
    // EXPLOSIONS – claim protection + special TNT logic
    // =========================================================

    // Natural block explosions (beds, respawn anchors, etc.)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        // Beds, anchors, etc. have no clear player source → fully protect all claims
        event.blockList().removeIf(this::isInAnyClaimArea);
    }

    // Entity explosions (TNT, creepers, withers, crystals, etc.)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        // Special case: TNT with a player source
        if (entity instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            Player sourcePlayer = (source instanceof Player) ? (Player) source : null;
            Team sourceTeam = (sourcePlayer != null) ? teamManager.getTeamByPlayer(sourcePlayer) : null;

            // If TNT has no player source or source has no team -> treat like enemy TNT (full protection)
            if (sourcePlayer == null || sourceTeam == null) {
                event.blockList().removeIf(this::isInAnyClaimArea);
                return;
            }

            // TNT from a team member:
            // - Can break blocks INSIDE that team's claim
            // - Cannot break blocks in other teams' claims
            event.blockList().removeIf(block -> {
                // Banner block itself is always protected
                if (isBannerBlock(block.getType()) && getTeamByBannerBlock(block) != null) {
                    return true;
                }

                Team claimingTeam = getClaimingTeam(block);
                if (claimingTeam == null) {
                    return false; // not in any claim → TNT works normally
                }

                // Inside this team's claim
                if (claimingTeam.equals(sourceTeam)) {
                    return false; // allow TNT from that team to break their own stuff
                }

                // In another team's claim → protect it
                return true;
            });

            return;
        }

        // Non-TNT explosions (creepers, withers, crystals, etc.) – fully protect claims
        event.blockList().removeIf(this::isInAnyClaimArea);
    }

    // =========================================================
    // Helpers
    // =========================================================

    private boolean isBannerBlock(Material type) {
        String name = type.name();
        return name.endsWith("_BANNER") || name.endsWith("_WALL_BANNER");
    }

    /**
     * Returns the team whose bannerLocation is exactly at this block, or null if none.
     */
    private Team getTeamByBannerBlock(Block block) {
        for (Team team : teamManager.getAllTeams()) {
            if (!team.hasBannerLocation()) continue;

            var loc = team.getBannerLocation();
            if (loc.getWorld() == null) continue;

            if (!loc.getWorld().equals(block.getWorld())) continue;
            if (loc.getBlockX() != block.getX()) continue;
            if (loc.getBlockY() != block.getY()) continue;
            if (loc.getBlockZ() != block.getZ()) continue;

            return team;
        }
        return null;
    }

    /**
     * Returns true if this block is inside any team's claim area (by chunks).
     */
    private boolean isInAnyClaimArea(Block block) {
        return getClaimingTeam(block) != null;
    }

    /**
     * Returns the team whose claim area (chunk radius from banner) contains this block, or null if none.
     */
    private Team getClaimingTeam(Block block) {
        if (teamManager.getAllTeams().isEmpty()) return null;

        Chunk blockChunk = block.getChunk();

        for (Team team : teamManager.getAllTeams()) {
            if (!team.hasBannerLocation()) continue;
            int radiusChunks = team.getClaimRadius();
            if (radiusChunks <= 0) continue;

            var bannerLoc = team.getBannerLocation();
            if (bannerLoc.getWorld() == null || !bannerLoc.getWorld().equals(block.getWorld())) continue;

            Chunk bannerChunk = bannerLoc.getChunk();
            int dx = blockChunk.getX() - bannerChunk.getX();
            int dz = blockChunk.getZ() - bannerChunk.getZ();

            if (Math.abs(dx) <= radiusChunks && Math.abs(dz) <= radiusChunks) {
                return team;
            }
        }

        return null;
    }

    /**
     * Chunk-based claim protection for normal block editing:
     * - Each team has a bannerLocation and a claimRadius (in CHUNKS).
     * - If a player is not in that team, they cannot place/break blocks in that chunk area.
     */
    private boolean isProtectedClaimArea(Block block, Player actor) {
        Chunk blockChunk = block.getChunk();
        Team actorTeam = teamManager.getTeamByPlayer(actor);

        for (Team team : teamManager.getAllTeams()) {
            if (!team.hasBannerLocation()) continue;
            int radiusChunks = team.getClaimRadius();
            if (radiusChunks <= 0) continue;

            var bannerLoc = team.getBannerLocation();
            if (bannerLoc.getWorld() == null || !bannerLoc.getWorld().equals(block.getWorld())) continue;

            Chunk bannerChunk = bannerLoc.getChunk();
            int dx = blockChunk.getX() - bannerChunk.getX();
            int dz = blockChunk.getZ() - bannerChunk.getZ();

            // Square claim in chunk coordinates
            if (Math.abs(dx) <= radiusChunks && Math.abs(dz) <= radiusChunks) {
                // Inside this team's claim
                if (actorTeam == null || !actorTeam.equals(team)) {
                    // Not the owning team → deny modification
                    actor.sendMessage(ChatColor.RED + "You cannot modify blocks inside team '" +
                            team.getName() + "' claim area.");
                    return true;
                }
                // If it's their own claim, it's allowed.
            }
        }

        return false;
    }
}
