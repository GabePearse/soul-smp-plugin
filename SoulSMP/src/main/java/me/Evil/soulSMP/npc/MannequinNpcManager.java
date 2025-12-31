package me.Evil.soulSMP.npc;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shop/Bank NPCs implemented as Paper's Mannequin entity (same as your leaderboard mannequins).
 * Tagged via PDC with npc_type.
 *
 * Also supports applying skin by Minecraft username (async profile completion).
 */
public class MannequinNpcManager {

    private final JavaPlugin plugin;
    private final NamespacedKey npcTypeKey;

    // Skin resolution cache (same pattern as LeaderboardDisplay)
    private final Map<String, PlayerProfile> resolvedProfileCache = new ConcurrentHashMap<>();
    private final Set<String> resolving = ConcurrentHashMap.newKeySet();

    public MannequinNpcManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.npcTypeKey = new NamespacedKey(plugin, "npc_type");
    }

    public boolean isNpc(Entity e) {
        return getType(e) != null;
    }

    public @Nullable NpcType getType(Entity e) {
        if (e == null) return null;
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        String raw = pdc.get(npcTypeKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return NpcType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void tag(Entity e, NpcType type) {
        e.getPersistentDataContainer().set(npcTypeKey, PersistentDataType.STRING, type.name());
    }

    public Mannequin spawn(Location loc, NpcType type, @Nullable Component nameComponent, @Nullable Component description) {
        if (loc == null || loc.getWorld() == null) throw new IllegalArgumentException("Location/world cannot be null");

        Mannequin m = (Mannequin) loc.getWorld().spawnEntity(loc, EntityType.MANNEQUIN);
        tag(m, type);

        m.setInvulnerable(true);
        m.setAI(false);
        m.setSilent(true);
        m.setImmovable(true);
        m.setPersistent(true);


        if (nameComponent != null) {
            m.customName(nameComponent);
            m.setCustomNameVisible(true);
        } else {
            Component defaultName = (type == NpcType.SHOP)
                    ? Component.text("SOUL SHOP", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                    : Component.text("SOUL BANK", NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
            m.customName(defaultName);
            m.setCustomNameVisible(true);
        }

        if (description != null) {
            m.setDescription(description);
        } else {
            Component defaultDesc = (type == NpcType.SHOP)
                    ? Component.text("Right-click to open the store", NamedTextColor.GRAY)
                    : Component.text("Right-click to open your bank", NamedTextColor.GRAY);
            m.setDescription(defaultDesc);
        }

        return m;
    }

    /**
     * Apply a skin to the mannequin by Minecraft username.
     * Uses Paper PlayerProfile completion asynchronously, then sets profile on main thread.
     */
    public void applySkinByUsername(Mannequin mannequin, String username) {
        if (mannequin == null) return;
        if (username == null || username.isBlank()) return;

        final String key = username.trim().toLowerCase(Locale.ROOT);

        PlayerProfile cached = resolvedProfileCache.get(key);
        if (cached != null) {
            mannequin.setProfile(ResolvableProfile.resolvableProfile(cached));
            return;
        }

        if (!resolving.add(key)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // uuid null is allowed; profile completion will fetch UUID + textures by name (online-mode)
                PlayerProfile pp = Bukkit.createProfile(null, username.trim());
                pp.complete(true);

                resolvedProfileCache.put(key, pp);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!mannequin.isValid()) return;
                    mannequin.setProfile(ResolvableProfile.resolvableProfile(pp));
                });
            } finally {
                resolving.remove(key);
            }
        });
    }

    // Optional helper if you ever want to locate a mannequin by UUID after restarts (same as LB)
    public @Nullable Mannequin findExistingMannequin(World world, Location loc, UUID uuid) {
        if (uuid == null || world == null || loc == null) return null;

        Entity direct = Bukkit.getEntity(uuid);
        if (direct instanceof Mannequin m) return m;

        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) chunk.load();

        for (Entity e : chunk.getEntities()) {
            if (e instanceof Mannequin m && m.getUniqueId().equals(uuid)) {
                return m;
            }
        }
        return null;
    }
}
