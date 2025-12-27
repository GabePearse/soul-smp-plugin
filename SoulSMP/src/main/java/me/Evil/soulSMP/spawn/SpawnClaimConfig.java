package me.Evil.soulSMP.spawn;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpawnClaimConfig {

    public final boolean enabled;

    public final Set<String> worlds;
    public final boolean useWorldSpawn;
    public final int centerX, centerY, centerZ;
    public final int radiusBlocks;

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

    public SpawnClaimConfig(JavaPlugin plugin) {
        plugin.saveDefaultConfig();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("spawn-claim");
        if (root == null) {
            // If missing, default OFF
            enabled = false;

            worlds = Set.of();
            useWorldSpawn = true;
            centerX = 0;
            centerY = 64;
            centerZ = 0;
            radiusBlocks = 0;

            bypassPermission = "soulsmp.spawn.bypass";

            fBlockBreak = false;
            fBlockPlace = false;
            fExplosions = false;
            fLiquids = false;
            fBuckets = false;
            fInteractBlocks = false;
            fInteractEntities = false;
            fDamageEntities = false;
            fPistons = false;
            fProjectiles = false;
            fFishing = false;

            msgGeneric = color("&cYou can't do that in Spawn.");
            msgBlockModify = color("&cYou cannot modify blocks in Spawn.");
            msgInteract = color("&cYou cannot interact here in Spawn.");
            msgDamage = color("&cYou cannot damage entities in Spawn.");
            msgFish = color("&cYou cannot fish in Spawn.");
            return;
        }

        enabled = root.getBoolean("enabled", true);

        List<String> w = root.getStringList("worlds");
        HashSet<String> ws = new HashSet<>();
        for (String s : w) {
            if (s != null && !s.isBlank()) ws.add(s.trim());
        }
        worlds = ws;

        useWorldSpawn = root.getBoolean("use-world-spawn", true);

        ConfigurationSection c = root.getConfigurationSection("center");
        centerX = (c != null) ? c.getInt("x", 0) : 0;
        centerY = (c != null) ? c.getInt("y", 64) : 64;
        centerZ = (c != null) ? c.getInt("z", 0) : 0;

        radiusBlocks = Math.max(0, root.getInt("radius-blocks", 150));

        bypassPermission = root.getString("bypass-permission", "soulsmp.spawn.bypass");

        ConfigurationSection f = root.getConfigurationSection("features");
        fBlockBreak       = (f == null) ? true : f.getBoolean("block-break", true);
        fBlockPlace       = (f == null) ? true : f.getBoolean("block-place", true);
        fExplosions       = (f == null) ? true : f.getBoolean("explosions", true);
        fLiquids          = (f == null) ? true : f.getBoolean("liquids", true);
        fBuckets          = (f == null) ? true : f.getBoolean("buckets", true);
        fInteractBlocks   = (f == null) ? true : f.getBoolean("interact-blocks", true);
        fInteractEntities = (f == null) ? true : f.getBoolean("interact-entities", true);
        fDamageEntities   = (f == null) ? true : f.getBoolean("damage-entities", true);
        fPistons          = (f == null) ? true : f.getBoolean("pistons", true);
        fProjectiles      = (f == null) ? true : f.getBoolean("projectiles", true);
        fFishing          = (f == null) ? true : f.getBoolean("fishing", true);

        ConfigurationSection m = root.getConfigurationSection("messages");
        msgGeneric     = color(m != null ? m.getString("generic", "&cYou can't do that in Spawn.") : "&cYou can't do that in Spawn.");
        msgBlockModify = color(m != null ? m.getString("block-modify", "&cYou cannot modify blocks in Spawn.") : "&cYou cannot modify blocks in Spawn.");
        msgInteract    = color(m != null ? m.getString("interact", "&cYou cannot interact here in Spawn.") : "&cYou cannot interact here in Spawn.");
        msgDamage      = color(m != null ? m.getString("damage", "&cYou cannot damage entities in Spawn.") : "&cYou cannot damage entities in Spawn.");
        msgFish        = color(m != null ? m.getString("fish", "&cYou cannot fish in Spawn.") : "&cYou cannot fish in Spawn.");
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
