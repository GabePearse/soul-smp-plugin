package me.Evil.soulSMP.spawn;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class SpawnClaim {

    private final SpawnClaimConfig cfg;

    public SpawnClaim(JavaPlugin plugin) {
        this.cfg = new SpawnClaimConfig(plugin);
    }

    public SpawnClaimConfig config() {
        return cfg;
    }

    public boolean isInSpawnClaim(Location loc) {
        if (!cfg.enabled) return false;
        if (loc == null || loc.getWorld() == null) return false;

        World w = loc.getWorld();
        if (!cfg.worlds.isEmpty() && !cfg.worlds.contains(w.getName())) return false;

        Location center = cfg.useWorldSpawn
                ? w.getSpawnLocation()
                : new Location(w, cfg.centerX + 0.5, cfg.centerY + 0.5, cfg.centerZ + 0.5);

        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();

        double r = cfg.radiusBlocks;
        return (dx * dx + dz * dz) <= (r * r);
    }
}
