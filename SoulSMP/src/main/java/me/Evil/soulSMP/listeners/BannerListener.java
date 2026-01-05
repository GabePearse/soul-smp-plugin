package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.opmode.OpModeManager;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.vault.TeamVaultManager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class BannerListener implements Listener {

    private static final int MIN_BANNER_BLOCK_SEPARATION = 50;

    private final TeamManager teamManager;
    private final TeamVaultManager vaultManager;
    private final NamespacedKey tntOwnerKey;

    // OpMode (bypass claims)
    private final OpModeManager opMode;

    public BannerListener(JavaPlugin plugin,
                          TeamManager teamManager,
                          TeamVaultManager vaultManager,
                          OpModeManager opMode) {
        this.teamManager = teamManager;
        this.vaultManager = vaultManager;
        this.opMode = opMode;
        this.tntOwnerKey = new NamespacedKey(plugin, "tnt_owner_team");
    }

    // =========================================================
    // BLOCK PLACE – banner claim placement
    // =========================================================
    @EventHandler
    public void onBannerPlace(BlockPlaceEvent event) {

        // ✅ OpMode bypasses claims
        if (isOpMode(event.getPlayer())) return;

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
            // Now fall through to treat THIS placement as the official banner
        }

        // 2) If this banner does NOT match the team design, it is decorative → allow silently
        if (!team.matchesBannerDesign(inHand)) {
            return;
        }

        // 3) Official team banner
        World world = block.getWorld();
        World.Environment env = world.getEnvironment();
        Location loc = block.getLocation().add(0.5, 0, 0.5); // centered

        String dimKey = switch (env) {
            case NORMAL -> "OVERWORLD";
            case NETHER -> "NETHER";
            case THE_END -> "THE_END";
            default -> null;
        };

        // Non-overworld dimensional banners
        if (dimKey != null && env != World.Environment.NORMAL) {
            if (!team.hasDimensionalBannerUnlocked(dimKey)) {
                player.sendMessage(ChatColor.GRAY + "Your team hasn't bought the "
                        + niceDimensionName(dimKey) + " banner upgrade. This banner is decorative only.");
                return;
            }

            if (team.hasDimensionalBannerLocation(dimKey)) {
                player.sendMessage(ChatColor.YELLOW + "Your team already has a "
                        + niceDimensionName(dimKey) + " banner. Break the old one first.");
                event.setCancelled(true);
                return;
            }

            team.setDimensionalBanner(dimKey, loc);
            teamManager.saveTeam(team);
            player.sendMessage(ChatColor.GREEN + "Your team's "
                    + niceDimensionName(dimKey) + " banner has been set here.");
            return;
        }

        // Overworld (main claim banner)
        if (team.hasBannerLocation()) {
            player.sendMessage(ChatColor.RED + "Your team already has a banner placed. This will just be a decoration. If you want to replace your banner use: "
                    + ChatColor.AQUA + "/team banner remove" + ChatColor.RED + ".");
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

        // ✅ OpMode bypasses claims
        if (isOpMode(event.getPlayer())) return;

        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 1) Protect the banner block AND its support block absolutely
        if (isProtectedBannerOrSupport(block)) {
            Team owningTeam = getTeamByBannerBlock(
                    block.getType().name().endsWith("_BANNER")
                            ? block
                            : block.getRelative(0, 1, 0)
            );

            event.setCancelled(true);

            if (owningTeam != null) {
                if (owningTeam.isMember(player.getUniqueId())) {
                    if (owningTeam.getOwner().equals(player.getUniqueId())) {
                        player.sendMessage(ChatColor.YELLOW + "You cannot break your team banner or its base directly.");
                        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.AQUA + "/team banner remove"
                                + ChatColor.YELLOW + " to remove it.");
                    } else {
                        player.sendMessage(ChatColor.RED + "You cannot break your team's banner or its base.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You cannot break another team's banner or its base.");
                }
            } else {
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
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;

        Team ownerTeam = null;

        Entity source = tnt.getSource();
        if (source instanceof Player sourcePlayer) {
            ownerTeam = teamManager.getTeamByPlayer(sourcePlayer);
        }

        if (ownerTeam == null) {
            Block spawnBlock = tnt.getLocation().getBlock();
            ownerTeam = getClaimingTeam(spawnBlock);
        }

        if (ownerTeam != null) {
            PersistentDataContainer pdc = tnt.getPersistentDataContainer();
            pdc.set(tntOwnerKey, PersistentDataType.STRING, ownerTeam.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block ->
                isProtectedBannerOrSupport(block) || isInAnyClaimArea(block)
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {

        Entity entity = event.getEntity();

        // Special case: TNT
        if (entity instanceof TNTPrimed tnt) {
            Team sourceTeam = null;

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

            if (sourceTeam == null) {
                Entity source = tnt.getSource();
                if (source instanceof Player sourcePlayer) {
                    sourceTeam = teamManager.getTeamByPlayer(sourcePlayer);
                }
            }

            if (sourceTeam == null) {
                event.blockList().removeIf(block ->
                        isProtectedBannerOrSupport(block) || isInAnyClaimArea(block)
                );
                return;
            }

            final Team finalSourceTeam = sourceTeam;

            event.blockList().removeIf(block -> {
                if (isProtectedBannerOrSupport(block)) return true;

                Team claimingTeam = getClaimingTeam(block);
                if (claimingTeam == null) return false;

                // Allow TNT to break blocks only in own claim / wilderness
                return !claimingTeam.equals(finalSourceTeam);
            });

            return;
        }

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
        Block to = event.getToBlock();

        Material type = from.getType();
        if (!isLiquid(type)) return;

        Team fromTeam = getClaimingTeam(from);
        Team toTeam = getClaimingTeam(to);

        if (fromTeam == null && toTeam != null) {
            event.setCancelled(true);
            return;
        }

        if (fromTeam != null && toTeam != null && !fromTeam.equals(toTeam)) {
            event.setCancelled(true);
        }
    }

    // =========================================================
    // BUCKET USE – prevent lava/water being placed in claims
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();

        Block clicked = event.getBlockClicked();
        if (clicked == null) return;

        BlockFace face = event.getBlockFace();
        Block target = clicked.getRelative(face);

        // ✅ OpMode bypasses claims
        if (isOpMode(player)) return;

        if (isProtectedClaimArea(target, player)) {
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

        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();

        // ✅ OpMode bypasses claims
        if (isOpMode(player)) return;

        // 1) Right-clicking a team banner opens the team vault
        if (action == Action.RIGHT_CLICK_BLOCK && isBannerBlock(block.getType())) {
            Team bannerTeam = getTeamByBannerBlock(block);
            if (bannerTeam != null) {
                Team playerTeam = teamManager.getTeamByPlayer(player);

                if (playerTeam == null || !playerTeam.equals(bannerTeam)) {
                    event.setCancelled(true);
                    return;
                }

                vaultManager.openVault(player, bannerTeam);
                event.setCancelled(true);
                return;
            }
        }

        if (isEnemyInClaim(block.getLocation(), player)) {
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot interact with blocks inside another team's claim.");
            }
        }
    }

    // =========================================================
    // INTERACTIONS WITH ENTITIES
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // ✅ OpMode bypasses claims
        if (isOpMode(player)) return;

        if (isEnemyInClaim(entity.getLocation(), player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot interact with entities inside another team's claim.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // ✅ OpMode bypasses claims
        if (isOpMode(player)) return;

        if (isEnemyInClaim(entity.getLocation(), player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot interact with entities inside another team's claim.");
        }
    }

    // =========================================================
    // DAMAGE TO ENTITIES
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        Entity victim = event.getEntity();

        // ✅ OpMode bypasses claims
        if (isOpMode(player)) return;

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
            Block to = moved.getRelative(direction);

            if (isIllegalPistonMovement(pistonTeam, from, to)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isSticky()) return;

        Block piston = event.getBlock();
        BlockFace direction = event.getDirection();

        Team pistonTeam = getClaimingTeam(piston);

        Block from = piston.getRelative(direction);
        if (from.getType().isAir()) return;

        Block to = piston;

        if (isIllegalPistonMovement(pistonTeam, from, to)) {
            event.setCancelled(true);
        }
    }

    // =========================================================
    // PROJECTILES - Prevent projectiles from hitting entities in claims
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Projectile projectile)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        Object shooterObj = projectile.getShooter();
        Player shooter = (shooterObj instanceof Player p) ? p : null;

        // ✅ If shooter is in OpMode, ignore claim rules
        if (isOpMode(shooter)) return;

        Team claimTeam = getClaimingTeamAtLocation(target.getLocation());
        if (claimTeam == null) return;

        if (shooter == null) {
            event.setCancelled(true);
            return;
        }

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

        PlayerFishEvent.State state = event.getState();
        if (!(state == PlayerFishEvent.State.FISHING
                || state == PlayerFishEvent.State.BITE
                || state == PlayerFishEvent.State.CAUGHT_FISH
                || state == PlayerFishEvent.State.CAUGHT_ENTITY)) {
            return;
        }

        // ✅ OpMode bypasses claims
        if (isOpMode(event.getPlayer())) return;

        if (event.getHook() == null) return;

        Location hookLoc = event.getHook().getLocation();
        if (hookLoc == null) return;

        Team claimTeam = getClaimingTeamAtLocation(hookLoc);
        if (claimTeam == null) return;

        event.setCancelled(true);
        event.getHook().remove();

        Player player = event.getPlayer();
        player.sendMessage(ChatColor.RED + "You cannot fish inside a claimed area.");
    }

    // =========================================================
    // Helpers
    // =========================================================

    private boolean isOpMode(Player p) {
        return p != null && opMode != null && opMode.isInOpMode(p.getUniqueId());
    }

    private boolean isBannerBlock(Material type) {
        String name = type.name();
        return name.endsWith("_BANNER") || name.endsWith("_WALL_BANNER");
    }

    private boolean isLiquid(Material type) {
        return type == Material.WATER
                || type == Material.LAVA
                || type == Material.KELP
                || type == Material.SEAGRASS
                || type == Material.BUBBLE_COLUMN;
    }

    /**
     * Dimension-aware “claim center”.
     * Overworld uses main banner, Nether/End use dimensional banner if set.
     */
    private Location getClaimCenterForWorld(Team team, World world) {
        if (team == null || world == null) return null;

        return switch (world.getEnvironment()) {
            case NORMAL -> team.getBannerLocation();
            case NETHER -> team.getDimensionalBanner("NETHER");
            case THE_END -> {
                Location l = team.getDimensionalBanner("THE_END");
                if (l == null) l = team.getDimensionalBanner("END");
                yield l;
            }
            default -> team.getBannerLocation();
        };
    }

    private Team getTeamByBannerBlock(Block block) {
        for (Team team : teamManager.getAllTeams()) {
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
            if (team.hasBannerLocation()) {
                if (isProtectedBannerOrSupportAt(team.getBannerLocation(), block)) {
                    return true;
                }
            }

            for (Location loc : team.getDimensionalBanners().values()) {
                if (loc == null) continue;
                if (isProtectedBannerOrSupportAt(loc, block)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isProtectedBannerOrSupportAt(Location loc, Block block) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!block.getWorld().equals(loc.getWorld())) return false;

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        Block bannerBlock = block.getWorld().getBlockAt(bx, by, bz);
        Material bannerType = bannerBlock.getType();
        boolean isWallBanner = bannerType.name().endsWith("_WALL_BANNER");

        // banner itself
        if (block.getX() == bx && block.getY() == by && block.getZ() == bz) {
            return true;
        }

        // standing banner base block
        if (!isWallBanner) {
            if (block.getX() == bx && block.getY() == by - 1 && block.getZ() == bz) {
                return true;
            }
        }

        // wall banner support block
        if (bannerBlock.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
            Block support = bannerBlock.getRelative(directional.getFacing().getOppositeFace());
            return block.equals(support);
        }

        return false;
    }

    private boolean isInAnyClaimArea(Block block) {
        return getClaimingTeam(block) != null;
    }

    /**
     * ✅ FIXED: Claim checks now work in Nether/End too (uses dimension banner if present)
     */
    private Team getClaimingTeam(Block block) {
        if (block == null) return null;
        if (teamManager.getAllTeams().isEmpty()) return null;

        int ax = block.getX();
        int az = block.getZ();
        World world = block.getWorld();

        for (Team team : teamManager.getAllTeams()) {
            Location center = getClaimCenterForWorld(team, world);
            if (center == null || center.getWorld() == null) continue;
            if (!center.getWorld().equals(world)) continue;

            int radiusTiles = team.getClaimRadius();
            if (radiusTiles <= 0) continue;

            int bx = center.getBlockX();
            int bz = center.getBlockZ();

            int halfSize = radiusTiles * 8;

            int dx = ax - bx;
            int dz = az - bz;

            if (Math.abs(dx) <= halfSize && Math.abs(dz) <= halfSize) {
                return team;
            }
        }

        return null;
    }

    /**
     * ✅ FIXED: Claim protection now works in Nether/End too, and OpMode bypasses it.
     */
    private boolean isProtectedClaimArea(Block block, Player actor) {
        if (block == null) return false;

        // ✅ OpMode bypass
        if (isOpMode(actor)) return false;

        Team actorTeam = teamManager.getTeamByPlayer(actor);
        int bx = block.getX();
        int bz = block.getZ();
        World world = block.getWorld();

        for (Team team : teamManager.getAllTeams()) {
            Location center = getClaimCenterForWorld(team, world);
            if (center == null || center.getWorld() == null) continue;
            if (!center.getWorld().equals(world)) continue;

            int radiusTiles = team.getClaimRadius();
            if (radiusTiles <= 0) continue;

            int cx = center.getBlockX();
            int cz = center.getBlockZ();

            int halfSize = radiusTiles * 8;

            int dx = bx - cx;
            int dz = bz - cz;

            if (Math.abs(dx) <= halfSize && Math.abs(dz) <= halfSize) {
                if (actorTeam == null || !actorTeam.equals(team)) {
                    actor.sendMessage(ChatColor.RED + "You cannot modify blocks inside team '"
                            + team.getName() + "' claim area.");
                    return true;
                }
            }
        }

        return false;
    }

    private Team getClaimingTeamAtLocation(Location loc) {
        if (loc == null) return null;
        return getClaimingTeam(loc.getBlock());
    }

    private boolean isEnemyInClaim(Location loc, Player actor) {
        // ✅ OpMode bypass
        if (isOpMode(actor)) return false;

        Team claimingTeam = getClaimingTeamAtLocation(loc);
        if (claimingTeam == null) return false;

        Team actorTeam = teamManager.getTeamByPlayer(actor);
        return actorTeam == null || !actorTeam.equals(claimingTeam);
    }

    private boolean isIllegalPistonMovement(Team pistonTeam, Block from, Block to) {
        Team fromTeam = getClaimingTeam(from);
        Team toTeam = getClaimingTeam(to);

        if (pistonTeam == null) {
            return fromTeam != null || toTeam != null;
        }

        if (fromTeam != null && !fromTeam.equals(pistonTeam)) return true;
        if (toTeam != null && !toTeam.equals(pistonTeam)) return true;

        return false;
    }

    private String niceDimensionName(String dimKey) {
        if (dimKey == null) return "Unknown Dimension";
        dimKey = dimKey.toUpperCase(Locale.ROOT);
        return switch (dimKey) {
            case "OVERWORLD" -> "Overworld";
            case "NETHER" -> "Nether";
            case "THE_END", "END" -> "The End";
            default -> dimKey;
        };
    }
}
