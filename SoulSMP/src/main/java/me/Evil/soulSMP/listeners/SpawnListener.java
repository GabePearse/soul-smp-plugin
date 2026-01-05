package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.spawn.SpawnClaim;
import me.Evil.soulSMP.spawn.SpawnClaimConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpawnListener implements Listener {

    private final JavaPlugin plugin;

    // Spawn protection
    private final SpawnClaim spawnClaim;
    private final SpawnClaimConfig spawnCfg;

    // Optional "spawn saturation" (keeps hunger full in spawn)
    private final boolean saturationEnabled;
    private final int saturationFoodLevel;
    private final float saturationValue;

    // Track players inside spawn (for saturation logic)
    private final Set<UUID> inSpawn = new HashSet<>();

    public SpawnListener(JavaPlugin plugin) {
        this.plugin = plugin;

        this.spawnClaim = new SpawnClaim(plugin);
        this.spawnCfg = spawnClaim.config();

        this.saturationEnabled = plugin.getConfig().getBoolean("spawn.saturation.enabled", false);
        this.saturationFoodLevel = clampInt(plugin.getConfig().getInt("spawn.saturation.food-level", 20), 0, 20);
        this.saturationValue = clampFloat((float) plugin.getConfig().getDouble("spawn.saturation.saturation", 20.0), 0f, 20f);
    }

    // =========================================================
    // Spawn helpers (PER-WORLD)
    // =========================================================

    private SpawnClaimConfig.WorldSettings spawnSettings(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        if (!spawnCfg.enabled) return null;
        return spawnCfg.getSettings(loc.getWorld().getName());
    }

    private boolean isInSpawn(Location loc) {
        return spawnSettings(loc) != null && spawnClaim.isInSpawnClaim(loc);
    }

    private boolean isSpawnBypass(Player p, Location at) {
        if (p == null) return false;
        if (p.isOp()) return true;

        SpawnClaimConfig.WorldSettings s = spawnSettings(at);
        if (s == null) return false;

        return s.bypassPermission != null
                && !s.bypassPermission.isBlank()
                && p.hasPermission(s.bypassPermission);
    }

    private boolean isLiquid(Material type) {
        return type == Material.WATER
                || type == Material.LAVA
                || type == Material.KELP
                || type == Material.SEAGRASS
                || type == Material.BUBBLE_COLUMN;
    }

    // =========================================================
    // Spawn saturation (optional)
    // =========================================================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!saturationEnabled) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> updateSaturationState(e.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!saturationEnabled) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> updateSaturationState(e.getPlayer()));
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (!saturationEnabled) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> updateSaturationState(e.getPlayer()));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!saturationEnabled) return;

        if (e.getTo() == null) return;
        if (e.getFrom().getWorld() == e.getTo().getWorld()
                && e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        updateSaturationState(e.getPlayer());
    }

    private void updateSaturationState(Player p) {
        if (p == null) return;

        boolean inside = isInSpawn(p.getLocation());
        UUID id = p.getUniqueId();

        if (inside) {
            inSpawn.add(id);
            applyFull(p);
        } else {
            inSpawn.remove(id);
        }
    }

    private void applyFull(Player p) {
        if (p.getFoodLevel() != saturationFoodLevel) p.setFoodLevel(saturationFoodLevel);
        if (p.getSaturation() < saturationValue) p.setSaturation(saturationValue);
    }

    // =========================================================
    // BLOCK PLACE/BREAK – cancel ALL inside spawn (except bypass)
    // =========================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Location loc = event.getBlockPlaced().getLocation();
        SpawnClaimConfig.WorldSettings s = spawnSettings(loc);
        if (s == null) return;

        if (!s.fBlockPlace) return;
        if (!isInSpawn(loc)) return;
        if (isSpawnBypass(event.getPlayer(), loc)) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(s.msgBlockModify);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        SpawnClaimConfig.WorldSettings s = spawnSettings(loc);
        if (s == null) return;

        if (!s.fBlockBreak) return;
        if (!isInSpawn(loc)) return;
        if (isSpawnBypass(event.getPlayer(), loc)) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(s.msgBlockModify);
    }

    // =========================================================
    // EXPLOSIONS – spawn protection
    // =========================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        SpawnClaimConfig.WorldSettings s = spawnSettings(event.getBlock().getLocation());
        if (s != null && s.fExplosions) {
            event.blockList().removeIf(b -> isInSpawn(b.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        SpawnClaimConfig.WorldSettings s = spawnSettings(event.getEntity().getLocation());
        if (s != null && s.fExplosions) {
            event.blockList().removeIf(b -> isInSpawn(b.getLocation()));
        }
    }

    // =========================================================
    // LIQUID FLOW – spawn protection
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();

        Material type = from.getType();
        if (!isLiquid(type)) return;

        SpawnClaimConfig.WorldSettings sFrom = spawnSettings(from.getLocation());
        SpawnClaimConfig.WorldSettings sTo = spawnSettings(to.getLocation());

        // Spawn protection (liquid flow into/within spawn)
        if (sFrom != null || sTo != null) {
            boolean fromSpawn = isInSpawn(from.getLocation());
            boolean toSpawn = isInSpawn(to.getLocation());

            if (fromSpawn != toSpawn) {
                boolean liquidsOn = (sFrom != null && sFrom.fLiquids) || (sTo != null && sTo.fLiquids);
                if (liquidsOn) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // =========================================================
    // BUCKET USE – spawn protection
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();

        Block clicked = event.getBlockClicked();
        if (clicked == null) return;

        BlockFace face = event.getBlockFace();
        Block target = clicked.getRelative(face);

        SpawnClaimConfig.WorldSettings s = spawnSettings(target.getLocation());

        if (s != null && s.fBuckets
                && isInSpawn(target.getLocation())
                && !isSpawnBypass(player, target.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(s.msgBlockModify);
        }
    }

    // =========================================================
    // INTERACTIONS WITH BLOCKS – spawn protection
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();

        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();

        SpawnClaimConfig.WorldSettings s = spawnSettings(block.getLocation());

        if (s != null && s.fInteractBlocks
                && isInSpawn(block.getLocation())
                && !isSpawnBypass(player, block.getLocation())) {
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                player.sendMessage(s.msgInteract);
            }
        }
    }

    // =========================================================
    // INTERACTIONS WITH ENTITIES – spawn protection
    // Allow horse interaction in spawn (mount/open inventory) even if entity interaction is blocked
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        SpawnClaimConfig.WorldSettings s = spawnSettings(entity.getLocation());
        if (s == null) return;

        if (!s.fInteractEntities) return;
        if (!isInSpawn(entity.getLocation())) return;
        if (isSpawnBypass(player, entity.getLocation())) return;

        // ✅ Allow horses (and donkeys/mules/llamas via AbstractHorse)
        if (entity instanceof AbstractHorse) return;

        event.setCancelled(true);
        player.sendMessage(s.msgInteract);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        SpawnClaimConfig.WorldSettings s = spawnSettings(entity.getLocation());
        if (s == null) return;

        if (!s.fInteractEntities) return;
        if (!isInSpawn(entity.getLocation())) return;
        if (isSpawnBypass(player, entity.getLocation())) return;

        // ✅ Allow horses (and donkeys/mules/llamas via AbstractHorse)
        if (entity instanceof AbstractHorse) return;

        event.setCancelled(true);
        player.sendMessage(s.msgInteract);
    }

    // =========================================================
    // DAMAGE TO ENTITIES – spawn protection
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        Entity victim = event.getEntity();
        SpawnClaimConfig.WorldSettings s = spawnSettings(victim.getLocation());

        if (s != null && s.fDamageEntities
                && isInSpawn(victim.getLocation())
                && !isSpawnBypass(player, victim.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(s.msgDamage);
        }
    }

    // =========================================================
    // PISTONS – spawn protection
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        BlockFace direction = event.getDirection();

        SpawnClaimConfig.WorldSettings s = spawnSettings(piston.getLocation());
        if (s != null && s.fPistons && isInSpawn(piston.getLocation())) {
            event.setCancelled(true);
            return;
        }

        for (Block moved : event.getBlocks()) {
            Block from = moved;
            Block to = moved.getRelative(direction);

            SpawnClaimConfig.WorldSettings sFrom = spawnSettings(from.getLocation());
            SpawnClaimConfig.WorldSettings sTo = spawnSettings(to.getLocation());

            if (((sFrom != null && sFrom.fPistons) || (sTo != null && sTo.fPistons))
                    && (isInSpawn(from.getLocation()) || isInSpawn(to.getLocation()))) {
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

        SpawnClaimConfig.WorldSettings s = spawnSettings(piston.getLocation());
        if (s != null && s.fPistons && isInSpawn(piston.getLocation())) {
            event.setCancelled(true);
            return;
        }

        Block from = piston.getRelative(direction);
        if (from.getType().isAir()) return;

        Block to = piston;

        SpawnClaimConfig.WorldSettings sFrom = spawnSettings(from.getLocation());
        SpawnClaimConfig.WorldSettings sTo = spawnSettings(to.getLocation());

        if (((sFrom != null && sFrom.fPistons) || (sTo != null && sTo.fPistons))
                && (isInSpawn(from.getLocation()) || isInSpawn(to.getLocation()))) {
            event.setCancelled(true);
        }
    }

    // =========================================================
    // PROJECTILES – spawn protection
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        SpawnClaimConfig.WorldSettings s = spawnSettings(target.getLocation());
        if (s == null) return;

        if (!s.fProjectiles) return;
        if (!isInSpawn(target.getLocation())) return;

        Object shooterObj = projectile.getShooter();
        if (shooterObj instanceof Player shooter && isSpawnBypass(shooter, target.getLocation())) {
            return;
        }

        event.setCancelled(true);
    }

    // =========================================================
    // FISHING – spawn protection
    // =========================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFishInSpawn(PlayerFishEvent event) {
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

        SpawnClaimConfig.WorldSettings s = spawnSettings(hookLoc);
        if (s != null && s.fFishing && isInSpawn(hookLoc) && !isSpawnBypass(event.getPlayer(), hookLoc)) {
            event.setCancelled(true);
            event.getHook().remove();
            event.getPlayer().sendMessage(s.msgFish);
        }
    }

    // =========================================================
    // MOB SPAWN BLOCK IN SPAWN
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location loc = event.getLocation();
        SpawnClaimConfig.WorldSettings s = spawnSettings(loc);
        if (s == null) return;

        if (!s.fMobSpawn) return;
        if (!isInSpawn(loc)) return;

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        switch (reason) {
            case NATURAL, PATROL, RAID, REINFORCEMENTS, VILLAGE_DEFENSE, VILLAGE_INVASION, TRAP -> event.setCancelled(true);
            default -> {}
        }
    }

    // =========================================================
    // small utils
    // =========================================================

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
