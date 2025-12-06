package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.vault.TeamVaultManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.inventory.ItemStack;


public class BannerListener implements Listener {

    private static final int MIN_BANNER_CHUNK_SEPARATION = 2;

    private final TeamManager teamManager;
    private final TeamVaultManager vaultManager;

    public BannerListener(TeamManager teamManager, TeamVaultManager vaultManager) {
        this.teamManager = teamManager;
        this.vaultManager = vaultManager;
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

        // Before claiming, make sure this new claim would NOT be too close or overlap
        // another team's claim.
        if (wouldOverlapOrBeTooClose(block.getLocation(), playerTeam)) {
            player.sendMessage(ChatColor.RED + "You cannot place your team banner here. "
                    + "Your claim would be too close to another team's claim. Move farther away.");
            event.setCancelled(true);
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
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 1) Protect the banner block AND its support block absolutely
        // Banner block or its support is protected
        if (isProtectedBannerOrSupport(block)) {
            Team owningTeam = getTeamByBannerBlock(
                    block.getType().name().endsWith("_BANNER")
                            ? block
                            : block.getRelative(0, 1, 0) // if they broke the block below, banner is above
            );

            event.setCancelled(true);

            // Always send a feedback message when a banner (or its support) is broken.
            if (owningTeam != null) {
                if (owningTeam.isMember(player.getUniqueId())) {
                    if (owningTeam.getOwner().equals(player.getUniqueId())) {
                        player.sendMessage(ChatColor.YELLOW + "You cannot break your team banner or its base directly.");
                        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.AQUA + "/team banner remove" + ChatColor.YELLOW + " to remove it.");
                    } else {
                        player.sendMessage(ChatColor.RED + "You cannot break your team's banner or its base.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You cannot break another team's banner or its base.");
                }
            } else {
                // Fallback: we found a protected banner/support block but couldn't resolve a team.
                // Still inform the player that this block is protected.
                player.sendMessage(ChatColor.RED + "You cannot break this banner or its base.");
            }
            return;
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
        event.blockList().removeIf(block ->
                isProtectedBannerOrSupport(block) || isInAnyClaimArea(block)
        );
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
                if (isProtectedBannerOrSupport(block)) {
                    return true; // remove from list => cannot be destroyed
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
        event.blockList().removeIf(block ->
                isProtectedBannerOrSupport(block) || isInAnyClaimArea(block)
        );
    }

    // =========================================================
    // LIQUID FLOW – prevent water/lava griefing into claims
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to   = event.getToBlock();

        Material type = from.getType();

        // Only care about water/lava
        if (!isLiquid(type)) {
            return;
        }

        Team fromTeam = getClaimingTeam(from);
        Team toTeam   = getClaimingTeam(to);

        // Case 1: Liquid flows from unclaimed area into a claimed area
        if (fromTeam == null && toTeam != null) {
            event.setCancelled(true);
            return;
        }

        // Case 2: Liquid flows from one team's claim into another team's claim
        if (fromTeam != null && toTeam != null && !fromTeam.equals(toTeam)) {
            event.setCancelled(true);
            return;
        }
    }

    // =========================================================
    // BUCKET USE – prevent lava/water being placed in claims
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();

        // Block that was clicked
        Block clicked = event.getBlockClicked();
        if (clicked == null) {
            return;
        }

        BlockFace face = event.getBlockFace();
        // This is where the water/lava source block will actually go
        Block target = clicked.getRelative(face);

        // Reuse your existing claim protection logic
        if (isProtectedClaimArea(target, player)) {
            // isProtectedClaimArea already sends the "you cannot modify blocks..." message
            event.setCancelled(true);
        }
    }

    // =========================================================
    // INTERACTIONS WITH BLOCKS – chests, doors, buttons, etc.
    // =========================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();

        // Only care about clicks on blocks
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();

        // 1) Right-clicking a team banner opens the team vault
        if (action == Action.RIGHT_CLICK_BLOCK && isBannerBlock(block.getType())) {
            Team bannerTeam = getTeamByBannerBlock(block);
            if (bannerTeam != null) {
                Team playerTeam = teamManager.getTeamByPlayer(player);

                // Only that team's members can access the vault
                if (playerTeam == null || !playerTeam.equals(bannerTeam)) {
                    event.setCancelled(true);
                    return;
                }

                // Open the team vault
                vaultManager.openVault(player, bannerTeam);
                event.setCancelled(true);
                return;
            }
        }

        if (isEnemyInClaim(block.getLocation(), player)) {
            // Cancel right‑click interactions only; left‑clicks should reach
            // BlockBreakEvent where claim protection applies.
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot interact with blocks inside another team's claim.");
            }
        }
    }


    // =========================================================
    // INTERACTIONS WITH ENTITIES – villagers, armor stands, etc.
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (isEnemyInClaim(entity.getLocation(), player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot interact with entities inside another team's claim.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (isEnemyInClaim(entity.getLocation(), player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot interact with entities inside another team's claim.");
        }
    }

    // =========================================================
    // DAMAGE TO ENTITIES – protect mobs/frames in claims
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        Entity victim = event.getEntity();

        if (isEnemyInClaim(victim.getLocation(), player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot damage entities inside another team's claim.");
        }
    }

    // =========================================================
    // PISTONS – prevent pushing blocks in/out/across claims
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        BlockFace direction = event.getDirection();

        Team pistonTeam = getClaimingTeam(piston);

        for (Block moved : event.getBlocks()) {
            Block from = moved;
            Block to   = moved.getRelative(direction);

            if (isIllegalPistonMovement(pistonTeam, from, to)) {
                event.setCancelled(true);
                return;
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isSticky()) {
            return; // only sticky pistons pull blocks
        }

        Block piston = event.getBlock();
        BlockFace direction = event.getDirection();
        Team pistonTeam = getClaimingTeam(piston);

        // Block being pulled is directly in front of the piston
        Block from = piston.getRelative(direction);
        if (from.getType().isAir()) {
            return;
        }

        Block to = piston; // pulled into piston face

        if (isIllegalPistonMovement(pistonTeam, from, to)) {
            event.setCancelled(true);
        }
    }


    // =========================================================
    // Helpers
    // =========================================================

    private boolean isBannerBlock(Material type) {
        String name = type.name();
        return name.endsWith("_BANNER") || name.endsWith("_WALL_BANNER");
    }

    private boolean isLiquid(Material type) {
        // Simple check; you can expand if you want more variants
        return type == Material.WATER
                || type == Material.LAVA
                || type == Material.KELP
                || type == Material.SEAGRASS
                || type == Material.BUBBLE_COLUMN;
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

        int ax = block.getX();
        int az = block.getZ();

        for (Team team : teamManager.getAllTeams()) {
            if (!team.hasBannerLocation()) continue;

            int radiusTiles = team.getClaimRadius(); // 1 = 16x16 around banner, 2 = 32x32, etc.
            if (radiusTiles <= 0) continue;

            var bannerLoc = team.getBannerLocation();
            if (bannerLoc.getWorld() == null || !bannerLoc.getWorld().equals(block.getWorld())) continue;

            int bx = bannerLoc.getBlockX();
            int bz = bannerLoc.getBlockZ();

            int halfSize = radiusTiles * 8; // half of 16 * radius

            int dx = ax - bx;
            int dz = az - bz;

            if (Math.abs(dx) <= halfSize && Math.abs(dz) <= halfSize) {
                return team;
            }
        }

        return null;
    }


    private boolean isProtectedClaimArea(Block block, Player actor) {
        Team actorTeam = teamManager.getTeamByPlayer(actor);
        int bx = block.getX();
        int bz = block.getZ();

        for (Team team : teamManager.getAllTeams()) {
            if (!team.hasBannerLocation()) continue;

            int radiusTiles = team.getClaimRadius();
            if (radiusTiles <= 0) continue;

            var bannerLoc = team.getBannerLocation();
            if (bannerLoc.getWorld() == null || !bannerLoc.getWorld().equals(block.getWorld())) continue;

            int cx = bannerLoc.getBlockX();
            int cz = bannerLoc.getBlockZ();

            int halfSize = radiusTiles * 8;

            int dx = bx - cx;
            int dz = bz - cz;

            // Inside this team's square claim
            if (Math.abs(dx) <= halfSize && Math.abs(dz) <= halfSize) {
                // If actor has no team or is in another team => deny
                if (actorTeam == null || !actorTeam.equals(team)) {
                    actor.sendMessage(ChatColor.RED + "You cannot modify blocks inside team '" +
                            team.getName() + "' claim area.");
                    return true;
                }
                // Their own claim → allowed
            }
        }

        return false;
    }


    private boolean isProtectedBannerOrSupport(Block block) {
        for (Team team : teamManager.getAllTeams()) {
            if (!team.hasBannerLocation()) continue;

            Location loc = team.getBannerLocation();
            if (!block.getWorld().equals(loc.getWorld())) continue;

            int bx = loc.getBlockX();
            int by = loc.getBlockY();
            int bz = loc.getBlockZ();

            // Banner block itself
            if (block.getX() == bx && block.getY() == by && block.getZ() == bz) {
                return true;
            }

            // SUPPORT BLOCK FOR STANDING BANNER (below)
            if (block.getX() == bx && block.getY() == by - 1 && block.getZ() == bz) {
                return true;
            }

            // SUPPORT BLOCK FOR WALL BANNER
            Block bannerBlock = block.getWorld().getBlockAt(bx, by, bz);
            if (bannerBlock.getState().getBlockData() instanceof org.bukkit.block.data.Directional directional) {
                Block support = bannerBlock.getRelative(directional.getFacing().getOppositeFace());
                if (block.equals(support)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the team that owns this location's claim area, or null if none.
     */
    private Team getClaimingTeamAtLocation(Location loc) {
        if (loc == null) return null;
        Block block = loc.getBlock();
        return getClaimingTeam(block);
    }

    /**
     * Returns true if the actor is NOT on the claimingTeam that owns this location.
     */
    private boolean isEnemyInClaim(Location loc, Player actor) {
        Team claimingTeam = getClaimingTeamAtLocation(loc);
        if (claimingTeam == null) return false;

        Team actorTeam = teamManager.getTeamByPlayer(actor);
        // Enemy if no team or different team
        return actorTeam == null || !actorTeam.equals(claimingTeam);
    }

    /**
     * Returns true if moving a block from 'from' to 'to' would cross claim boundaries
     * in a way we want to forbid.
     *
     * Rules:
     * - Outside -> inside claim: illegal
     * - Inside claim -> outside: illegal
     * - One team's claim -> another team's claim: illegal
     */
    private boolean isIllegalClaimMovement(Block from, Block to) {
        Team fromTeam = getClaimingTeam(from);
        Team toTeam   = getClaimingTeam(to);

        // From unclaimed into claimed -> illegal
        if (fromTeam == null && toTeam != null) {
            return true;
        }

        // From claimed into unclaimed -> illegal
        if (fromTeam != null && toTeam == null) {
            return true;
        }

        // From one team's claim into another team's claim -> illegal
        if (fromTeam != null && toTeam != null && !fromTeam.equals(toTeam)) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if moving a block from 'from' to 'to' via a piston
     * should be forbidden, based on where the piston is (pistonTeam).
     *
     * Rules:
     * - If piston is OUTSIDE any claim:
     *      - Any interaction with claimed blocks (from or to) is illegal.
     * - If piston is INSIDE a team's claim:
     *      - It may only move blocks within:
     *          - wilderness (null) and/or
     *          - that same team's claim.
     *      - Any movement involving another team's claim is illegal.
     */
    private boolean isIllegalPistonMovement(Team pistonTeam, Block from, Block to) {
        Team fromTeam = getClaimingTeam(from);
        Team toTeam   = getClaimingTeam(to);

        // Piston in wilderness (no claim)
        if (pistonTeam == null) {
            // If either side touches ANY claim, that's illegal
            if (fromTeam != null || toTeam != null) {
                return true;
            }
            return false; // all wilderness -> allowed
        }

        // Piston inside a team's claim
        // It can only interact with wilderness and its own claim.
        // Any movement involving another team's claim is illegal.
        if (fromTeam != null && !fromTeam.equals(pistonTeam)) {
            return true;
        }
        if (toTeam != null && !toTeam.equals(pistonTeam)) {
            return true;
        }

        return false;
    }


    /**
     * Returns true if setting this location as `newTeam`'s banner
     * would either:
     *  - be within MIN_BANNER_CHUNK_SEPARATION chunks of another team's banner, OR
     *  - cause this team's claim square to overlap another team's claim square.
     */
    private boolean wouldOverlapOrBeTooClose(Location newBannerLoc, Team newTeam) {
        if (newBannerLoc == null || newBannerLoc.getWorld() == null) return false;

        int newRadiusTiles = newTeam.getClaimRadius();
        if (newRadiusTiles < 0) newRadiusTiles = 0;

        int nx = newBannerLoc.getBlockX();
        int nz = newBannerLoc.getBlockZ();
        int newHalf = newRadiusTiles * 8; // same as your other claim logic

        // Chunk coords for separation check
        int newChunkX = nx >> 4;
        int newChunkZ = nz >> 4;

        for (Team other : teamManager.getAllTeams()) {
            if (other == null) continue;
            if (!other.hasBannerLocation()) continue;
            if (other.equals(newTeam)) continue;

            Location otherLoc = other.getBannerLocation();
            if (otherLoc == null || otherLoc.getWorld() == null) continue;
            if (!otherLoc.getWorld().equals(newBannerLoc.getWorld())) continue;

            int ox = otherLoc.getBlockX();
            int oz = otherLoc.getBlockZ();

            // --- 1) Hard minimum chunk separation ---

            int otherChunkX = ox >> 4;
            int otherChunkZ = oz >> 4;

            int dxChunks = Math.abs(newChunkX - otherChunkX);
            int dzChunks = Math.abs(newChunkZ - otherChunkZ);

            // If both axes are closer than MIN_BANNER_CHUNK_SEPARATION,
            // the banners are considered "too close".
            if (dxChunks < MIN_BANNER_CHUNK_SEPARATION && dzChunks < MIN_BANNER_CHUNK_SEPARATION) {
                return true;
            }

            // --- 2) Claim-square overlap check (extra safety) ---

            int otherRadiusTiles = other.getClaimRadius();
            if (otherRadiusTiles < 0) otherRadiusTiles = 0;

            int otherHalf = otherRadiusTiles * 8;

            int dx = nx - ox;
            int dz = nz - oz;
            int totalHalf = newHalf + otherHalf;

            // Squares overlap if their centers are closer than sum of half-sizes on both axes
            if (Math.abs(dx) <= totalHalf && Math.abs(dz) <= totalHalf) {
                return true;
            }
        }

        return false;
    }

}