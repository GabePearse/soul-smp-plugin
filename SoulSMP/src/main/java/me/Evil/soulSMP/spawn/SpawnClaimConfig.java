package me.Evil.soulSMP.spawn;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SpawnClaimConfig {

    public final boolean enabled;

    /**
     * Global defaults (fallback when per-world does not override).
     */
    public final WorldSettings defaults;

    /**
     * Per-world settings. Key = Bukkit world name (e.g. "world", "world_nether", "world_the_end").
     */
    private final Map<String, WorldSettings> perWorld;

    public SpawnClaimConfig(JavaPlugin plugin) {
        plugin.saveDefaultConfig();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("spawn-claim");
        if (root == null) {
            enabled = false;
            defaults = WorldSettings.defaults();
            perWorld = Collections.emptyMap();
            return;
        }

        enabled = root.getBoolean("enabled", true);

        // Build global defaults from spawn-claim.* (legacy / fallback)
        defaults = WorldSettings.fromSection(root, WorldSettings.defaults());

        // Build per-world settings from spawn-claim.per-world.<worldName>.*
        Map<String, WorldSettings> map = new HashMap<>();
        ConfigurationSection pw = root.getConfigurationSection("per-world");
        if (pw != null) {
            for (String worldName : pw.getKeys(false)) {
                if (worldName == null || worldName.isBlank()) continue;

                ConfigurationSection wsSec = pw.getConfigurationSection(worldName);
                if (wsSec == null) continue;

                // Allow a per-world enabled toggle. Defaults to true if present.
                boolean worldEnabled = wsSec.getBoolean("enabled", true);
                if (!worldEnabled) continue;

                WorldSettings worldSettings = WorldSettings.fromSection(wsSec, defaults);
                map.put(worldName, worldSettings);
            }
        }

        perWorld = Collections.unmodifiableMap(map);
    }

    /**
     * Returns settings for a world, or null if this world is not configured/enabled.
     *
     * Behavior:
     * - If per-world is defined and contains the world -> returns it
     * - Else if per-world is defined but does NOT contain the world -> returns null (not protected)
     * - Else (no per-world section at all) -> returns defaults (legacy behavior)
     */
    public WorldSettings getSettings(String worldName) {
        if (worldName == null || worldName.isBlank()) return null;

        // If user defined per-world section, only worlds listed there are protected.
        if (hasPerWorldConfigured()) {
            return perWorld.get(worldName);
        }

        // Legacy behavior: global applies everywhere unless you intentionally restrict (old "worlds" list is gone).
        // If you want restriction, use per-world.
        return defaults;
    }

    public boolean hasPerWorldConfigured() {
        return !perWorld.isEmpty();
    }

    /**
     * All settings required for claim + protections.
     * Per-world blocks override defaults.
     */
    public static class WorldSettings {

        // Claim geometry
        public final boolean useWorldSpawn;
        public final int centerX, centerY, centerZ;
        public final int radiusBlocks;

        // Permissions
        public final String bypassPermission;

        // Feature toggles
        public final boolean fBlockBreak;
        public final boolean fBlockPlace;
        public final boolean fExplosions;
        public final boolean fLiquids;
        public final boolean fBuckets;
        public final boolean fInteractBlocks;
        public final boolean fInteractEntities;
        public final boolean fDamageEntities;
        public final boolean fPistons;
        public final boolean fProjectiles;
        public final boolean fFishing;

        // Messages
        public final String msgGeneric;
        public final String msgBlockModify;
        public final String msgInteract;
        public final String msgDamage;
        public final String msgFish;

        private WorldSettings(
                boolean useWorldSpawn,
                int centerX, int centerY, int centerZ,
                int radiusBlocks,
                String bypassPermission,
                boolean fBlockBreak,
                boolean fBlockPlace,
                boolean fExplosions,
                boolean fLiquids,
                boolean fBuckets,
                boolean fInteractBlocks,
                boolean fInteractEntities,
                boolean fDamageEntities,
                boolean fPistons,
                boolean fProjectiles,
                boolean fFishing,
                String msgGeneric,
                String msgBlockModify,
                String msgInteract,
                String msgDamage,
                String msgFish
        ) {
            this.useWorldSpawn = useWorldSpawn;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radiusBlocks = radiusBlocks;

            this.bypassPermission = bypassPermission;

            this.fBlockBreak = fBlockBreak;
            this.fBlockPlace = fBlockPlace;
            this.fExplosions = fExplosions;
            this.fLiquids = fLiquids;
            this.fBuckets = fBuckets;
            this.fInteractBlocks = fInteractBlocks;
            this.fInteractEntities = fInteractEntities;
            this.fDamageEntities = fDamageEntities;
            this.fPistons = fPistons;
            this.fProjectiles = fProjectiles;
            this.fFishing = fFishing;

            this.msgGeneric = msgGeneric;
            this.msgBlockModify = msgBlockModify;
            this.msgInteract = msgInteract;
            this.msgDamage = msgDamage;
            this.msgFish = msgFish;
        }

        public static WorldSettings defaults() {
            return new WorldSettings(
                    true,
                    0, 64, 0,
                    150,
                    "soulsmp.spawn.bypass",
                    true,  // block-break
                    true,  // block-place
                    true,  // explosions
                    true,  // liquids
                    true,  // buckets
                    true,  // interact-blocks
                    true,  // interact-entities
                    true,  // damage-entities
                    true,  // pistons
                    true,  // projectiles
                    true,  // fishing
                    color("&cYou can't do that in Spawn."),
                    color("&cYou cannot modify blocks in Spawn."),
                    color("&cYou cannot interact here in Spawn."),
                    color("&cYou cannot damage entities in Spawn."),
                    color("&cYou cannot fish in Spawn.")
            );
        }

        /**
         * Build settings from a section, with fallback = base.
         *
         * Supported keys inside the section:
         * - use-world-spawn: boolean
         * - center: {x,y,z}
         * - radius-blocks: int
         * - bypass-permission: string
         * - features: { block-break, block-place, explosions, liquids, buckets, interact-blocks, interact-entities, damage-entities, pistons, projectiles, fishing }
         * - messages: { generic, block-modify, interact, damage, fish }
         */
        public static WorldSettings fromSection(ConfigurationSection sec, WorldSettings base) {
            if (sec == null) return base;

            boolean useWorldSpawn = sec.getBoolean("use-world-spawn", base.useWorldSpawn);

            ConfigurationSection c = sec.getConfigurationSection("center");
            int centerX = (c != null) ? c.getInt("x", base.centerX) : base.centerX;
            int centerY = (c != null) ? c.getInt("y", base.centerY) : base.centerY;
            int centerZ = (c != null) ? c.getInt("z", base.centerZ) : base.centerZ;

            int radiusBlocks = Math.max(0, sec.getInt("radius-blocks", base.radiusBlocks));

            String bypassPermission = sec.getString("bypass-permission", base.bypassPermission);

            ConfigurationSection f = sec.getConfigurationSection("features");
            boolean fBlockBreak       = (f == null) ? base.fBlockBreak : f.getBoolean("block-break", base.fBlockBreak);
            boolean fBlockPlace       = (f == null) ? base.fBlockPlace : f.getBoolean("block-place", base.fBlockPlace);
            boolean fExplosions       = (f == null) ? base.fExplosions : f.getBoolean("explosions", base.fExplosions);
            boolean fLiquids          = (f == null) ? base.fLiquids : f.getBoolean("liquids", base.fLiquids);
            boolean fBuckets          = (f == null) ? base.fBuckets : f.getBoolean("buckets", base.fBuckets);
            boolean fInteractBlocks   = (f == null) ? base.fInteractBlocks : f.getBoolean("interact-blocks", base.fInteractBlocks);
            boolean fInteractEntities = (f == null) ? base.fInteractEntities : f.getBoolean("interact-entities", base.fInteractEntities);
            boolean fDamageEntities   = (f == null) ? base.fDamageEntities : f.getBoolean("damage-entities", base.fDamageEntities);
            boolean fPistons          = (f == null) ? base.fPistons : f.getBoolean("pistons", base.fPistons);
            boolean fProjectiles      = (f == null) ? base.fProjectiles : f.getBoolean("projectiles", base.fProjectiles);
            boolean fFishing          = (f == null) ? base.fFishing : f.getBoolean("fishing", base.fFishing);

            ConfigurationSection m = sec.getConfigurationSection("messages");
            String msgGeneric     = color(m != null ? m.getString("generic", base.msgGeneric) : base.msgGeneric);
            String msgBlockModify = color(m != null ? m.getString("block-modify", base.msgBlockModify) : base.msgBlockModify);
            String msgInteract    = color(m != null ? m.getString("interact", base.msgInteract) : base.msgInteract);
            String msgDamage      = color(m != null ? m.getString("damage", base.msgDamage) : base.msgDamage);
            String msgFish        = color(m != null ? m.getString("fish", base.msgFish) : base.msgFish);

            return new WorldSettings(
                    useWorldSpawn,
                    centerX, centerY, centerZ,
                    radiusBlocks,
                    bypassPermission,
                    fBlockBreak,
                    fBlockPlace,
                    fExplosions,
                    fLiquids,
                    fBuckets,
                    fInteractBlocks,
                    fInteractEntities,
                    fDamageEntities,
                    fPistons,
                    fProjectiles,
                    fFishing,
                    msgGeneric,
                    msgBlockModify,
                    msgInteract,
                    msgDamage,
                    msgFish
            );
        }

        private static String color(String s) {
            return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
        }
    }
}
