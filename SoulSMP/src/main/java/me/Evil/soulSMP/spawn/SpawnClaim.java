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
        SpawnClaimConfig.WorldSettings s = cfg.getSettings(w.getName());
        if (s == null) return false; // not enabled for this world

        Location center = s.useWorldSpawn
                ? w.getSpawnLocation()
                : new Location(w, s.centerX + 0.5, s.centerY + 0.5, s.centerZ + 0.5);

        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();

        double r = s.radiusBlocks;
        return (dx * dx + dz * dz) <= (r * r);
    }
}
