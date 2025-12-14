package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.vault.TeamVaultManager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.*;
import org.bukkit.block.BlockFace;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.Locale;


public class BannerListener implements Listener {

    private static final int MIN_BANNER_BLOCK_SEPARATION = 50;

    private final TeamManager teamManager;
    private final TeamVaultManager vaultManager;
    private final NamespacedKey tntOwnerKey;

    public BannerListener(JavaPlugin plugin, TeamManager teamManager, TeamVaultManager vaultManager) {
        this.teamManager = teamManager;
        this.vaultManager = vaultManager;
        this.tntOwnerKey = new NamespacedKey(plugin, "tnt_owner_team");
    }

    // =========================================================
    // BLOCK PLACE – claim protection + banner claim placement
    // =========================================================
    @EventHandler
    public void onBannerPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!block.getType().name().endsWith("_BANNER")) return;

        Player player = event.getPlayer();
        ItemStack inHand = event.getItemInHand();

        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            // Not in a team – banner is just decoration
            return;
        }

        // 1) If team has NO claimed banner yet: lock this design on first banner
        if (!team.hasClaimedBannerDesign()) {
            team.setBannerDesign(inHand);
            teamManager.saveTeam(team);
            player.sendMessage(ChatColor.GREEN + "Your team's banner design has been locked to this banner.");
            // Now fall through to treat THIS placement as the official banner (claim / dimension, etc.)
        }

        // 2) If this banner does NOT match the team design, it is decorative → allow silently
        if (!team.matchesBannerDesign(inHand)) {
            // DO NOT cancel the event
            // Just let it be a normal decorative banner
            return;
        }

        // 3) From here down, it IS an official team banner (matches design)
        //    Now apply your existing claim or dimensional logic.

        World world = block.getWorld();
        World.Environment env = world.getEnvironment();
        Location loc = block.getLocation().add(0.5, 0, 0.5); // nice centered location

        String dimKey = switch (env) {
            case NORMAL -> "OVERWORLD";
            case NETHER -> "NETHER";
            case THE_END -> "THE_END";
            default -> null;
        };

        // Non-overworld dimensional banners
        if (dimKey != null && env != World.Environment.NORMAL) {
            // Must have purchased the dimension
            if (!team.hasDimensionalBannerUnlocked(dimKey)) {
                player.sendMessage(ChatColor.GRAY + "Your team hasn't bought the "
                        + niceDimensionName(dimKey) + " banner upgrade. This banner is decorative only.");
                // Don't cancel; it will just be decoration.
                return;
            }

            if (team.hasDimensionalBannerLocation(dimKey)) {
                player.sendMessage(ChatColor.YELLOW + "Your team already has a "
                        + niceDimensionName(dimKey) + " banner. Break the old one first.");
                event.setCancelled(true); // prevent accidentally placing a second official one
                return;
            }

            team.setDimensionalBanner(dimKey, loc);
            teamManager.saveTeam(team);
            player.sendMessage(ChatColor.GREEN + "Your team's "
                    + niceDimensionName(dimKey) + " banner has been set here.");
            return;
        }

        // Overworld (main claim banner) – keep your existing claim logic here
        // Example:
        if (team.hasBannerLocation()) {
            player.sendMessage(ChatColor.RED + "Your team already has a banner placed. Use `/team banner remove' to break it first.");
            event.setCancelled(true);
            return;
        }

        team.setBannerLocation(loc);
        teamManager.saveTeam(team);
        player.sendMessage(ChatColor.GREEN + "Your team's main banner has been placed.");
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTNTSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }

        Team ownerTeam = null;

        // 1) If there is a player source, use their team
        Entity source = tnt.getSource();
        if (source instanceof Player sourcePlayer) {
            ownerTeam = teamManager.getTeamByPlayer(sourcePlayer);
        }

        // 2) If no player source, infer from where the TNT was primed:
        //    if the TNT block was inside a claim, use that claim's team.
        if (ownerTeam == null) {
            Block spawnBlock = tnt.getLocation().getBlock();
            ownerTeam = getClaimingTeam(spawnBlock);
        }

        // 3) If we found an owning team, store it on the TNT entity
        if (ownerTeam != null) {
            PersistentDataContainer pdc = tnt.getPersistentDataContainer();
            pdc.set(tntOwnerKey, PersistentDataType.STRING, ownerTeam.getName());
        }
    }

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

        // Special case: TNT (primed by player, redstone, chain reactions, etc.)
        if (entity instanceof TNTPrimed tnt) {
            Team sourceTeam = null;

            // 1) Try to read tagged owner from the TNT entity
            PersistentDataContainer pdc = tnt.getPersistentDataContainer();
            String teamName = pdc.get(tntOwnerKey, PersistentDataType.STRING);
            if (teamName != null) {
                for (Team team : teamManager.getAllTeams()) {
                    if (team.getName().equalsIgnoreCase(teamName)) {
                        sourceTeam = team;
                        break;
                    }
                }
            }

            // 2) Fallback: for legacy / untagged TNT, use player source (if any)
            if (sourceTeam == null) {
                Entity source = tnt.getSource();
                if (source instanceof Player sourcePlayer) {
                    sourceTeam = teamManager.getTeamByPlayer(sourcePlayer);
                }
            }

            // 3) If we STILL have no team -> treat as enemy TNT:
            //    fully protect all claimed blocks and banners.
            if (sourceTeam == null) {
                event.blockList().removeIf(block ->
                        isProtectedBannerOrSupport(block) || isInAnyClaimArea(block)
                );
                return;
            }

            final Team finalSourceTeam = sourceTeam;

            // 4) TNT with a known team:
            //    - Can break blocks INSIDE that team's claim
            //    - Cannot break blocks in OTHER teams' claims
            //    - Banner blocks are always protected
            event.blockList().removeIf(block -> {
                if (isProtectedBannerOrSupport(block)) {
                    return true; // never destroy banner or its support
                }

                Team claimingTeam = getClaimingTeam(block);
                if (claimingTeam == null) {
                    // wilderness → TNT works normally
                    return false;
                }

                // Inside this TNT owner's claim → allowed
                if (claimingTeam.equals(finalSourceTeam)) {
                    return false;
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
    // PROJECTILES - Prevent projectiles from hitting entities in claims
    // =========================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Only care about projectiles such as arrows/tridents
        Entity damager = event.getDamager();
        if (!(damager instanceof Projectile projectile)) {
            return;
        }

        // Target must be a living entity (players, mobs)
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // Determine if the target is inside a team's claim
        Team claimTeam = getClaimingTeamAtLocation(target.getLocation());
        if (claimTeam == null) {
            return; // not inside a claim
        }

        // Figure out who fired the projectile (player, dispenser, etc.)
        Object shooterObj = projectile.getShooter();
        Player shooter = (shooterObj instanceof Player p) ? p : null;

        // If shooter is not a player (e.g. dispenser/railgun), ALWAYS block damage in claims
        if (shooter == null) {
            event.setCancelled(true);
            return;
        }

        // Shooter is a player: only allow if they are on the owning team
        Team shooterTeam = teamManager.getTeamByPlayer(shooter);
        if (shooterTeam == null || !shooterTeam.equals(claimTeam)) {
            shooter.sendMessage(ChatColor.RED + "You cannot damage entities inside team '"
                    + claimTeam.getName() + "' claim with projectiles.");
            event.setCancelled(true);
        }
    }

    // =========================================================
    // FISHING – block fishing inside ANY team claim
    // =========================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFishInClaim(PlayerFishEvent event) {

        // Block early states so the hook doesn't persist
        PlayerFishEvent.State state = event.getState();
        if (!(state == PlayerFishEvent.State.FISHING
                || state == PlayerFishEvent.State.BITE
                || state == PlayerFishEvent.State.CAUGHT_FISH
                || state == PlayerFishEvent.State.CAUGHT_ENTITY)) {
            return;
        }

        if (event.getHook() == null) return;

        Location hookLoc = event.getHook().getLocation();
        if (hookLoc == null) return;

        // Reuse your existing claim logic
        Team claimTeam = getClaimingTeamAtLocation(hookLoc);
        if (claimTeam == null) return;

        // Block fishing in ANY claim (including own team)
        event.setCancelled(true);

        // IMPORTANT: remove hook so the cast is consumed
        event.getHook().remove();

        Player player = event.getPlayer();
        player.sendMessage(ChatColor.RED + "You cannot fish inside a claimed area.");
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
    // File: src/main/java/me/Evil/soulSMP/listeners/BannerListener.java
// (only the updated helper methods are shown)

    /**
     * Returns the team whose banner block (main or dimensional) is exactly at this block, or null if none.
     */
    private Team getTeamByBannerBlock(Block block) {
        for (Team team : teamManager.getAllTeams()) {
            // Main overworld banner
            if (team.hasBannerLocation()) {
                Location loc = team.getBannerLocation();
                if (loc.getWorld() != null
                        && loc.getWorld().equals(block.getWorld())
                        && loc.getBlockX() == block.getX()
                        && loc.getBlockY() == block.getY()
                        && loc.getBlockZ() == block.getZ()) {
                    return team;
                }
            }

            // Dimensional banners (Nether, End, etc.)
            for (Location dimLoc : team.getDimensionalBanners().values()) {
                if (dimLoc == null || dimLoc.getWorld() == null) continue;
                if (!dimLoc.getWorld().equals(block.getWorld())) continue;

                if (dimLoc.getBlockX() == block.getX()
                        && dimLoc.getBlockY() == block.getY()
                        && dimLoc.getBlockZ() == block.getZ()) {
                    return team;
                }
            }
        }
        return null;
    }

    private boolean isProtectedBannerOrSupport(Block block) {
        for (Team team : teamManager.getAllTeams()) {
            // Main banner
            if (team.hasBannerLocation()) {
                if (isProtectedBannerOrSupportAt(team.getBannerLocation(), block)) {
                    return true;
                }
            }

            // Dimensional banners
            for (Location loc : team.getDimensionalBanners().values()) {
                if (loc == null) continue;
                if (isProtectedBannerOrSupportAt(loc, block)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check protection against a specific banner location (main or dimensional).
     */
    private boolean isProtectedBannerOrSupportAt(Location loc, Block block) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!block.getWorld().equals(loc.getWorld())) return false;

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        // The actual banner block at this location
        Block bannerBlock = block.getWorld().getBlockAt(bx, by, bz);
        Material bannerType = bannerBlock.getType();
        boolean isWallBanner = bannerType.name().endsWith("_WALL_BANNER");

        // 1) Banner block itself
        if (block.getX() == bx && block.getY() == by && block.getZ() == bz) {
            return true;
        }

        // 2) SUPPORT BLOCK FOR STANDING BANNER (below)
        if (!isWallBanner) {
            if (block.getX() == bx && block.getY() == by - 1 && block.getZ() == bz) {
                return true;
            }
        }

        // 3) SUPPORT BLOCK FOR WALL BANNER (block behind the banner)
        if (bannerBlock.getState().getBlockData() instanceof org.bukkit.block.data.Directional directional) {
            Block support = bannerBlock.getRelative(directional.getFacing().getOppositeFace());
            if (block.equals(support)) {
                return true;
            }
        }

        return false;
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
        int newHalf = newRadiusTiles * 8; // half-size of new claim square in blocks

        for (Team other : teamManager.getAllTeams()) {
            if (other == null) continue;
            if (!other.hasBannerLocation()) continue;
            if (other.equals(newTeam)) continue;

            Location otherLoc = other.getBannerLocation();
            if (otherLoc == null || otherLoc.getWorld() == null) continue;
            if (!otherLoc.getWorld().equals(newBannerLoc.getWorld())) continue;

            int ox = otherLoc.getBlockX();
            int oz = otherLoc.getBlockZ();

            int otherRadiusTiles = other.getClaimRadius();
            if (otherRadiusTiles < 0) otherRadiusTiles = 0;

            int otherHalf = otherRadiusTiles * 8; // half-size of other claim square

            int dx = nx - ox;
            int dz = nz - oz;

            // Maximum allowed center distance on each axis before claims are considered "too close":
            // half of new + half of other + required gap between edges
            int requiredCenterDistance = newHalf + otherHalf + MIN_BANNER_BLOCK_SEPARATION;

            // If centers are closer than this on BOTH axes, the squares + gap overlap → too close
            if (Math.abs(dx) <= requiredCenterDistance && Math.abs(dz) <= requiredCenterDistance) {
                return true;
            }
        }

        return false;
    }

    private String niceDimensionName(String dimKey) {
        if (dimKey == null) return "Unknown Dimension";
        dimKey = dimKey.toUpperCase(Locale.ROOT);
        return switch (dimKey) {
            case "OVERWORLD" -> "Overworld";
            case "NETHER" -> "Nether";
            case "THE_END" -> "The End";
            default -> dimKey;
        };
    }


}