package me.Evil.soulSMP.leaderboard;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardDisplay {

    private final Plugin plugin;

    /**
     * If mannequins consistently face backwards, set this to 180f.
     */
    private static final float MANNEQUIN_YAW_OFFSET = 0f;

    /**
     * Cache completed Paper PlayerProfiles (includes textures) so we don't refetch constantly.
     */
    private final Map<UUID, PlayerProfile> resolvedProfileCache = new ConcurrentHashMap<>();

    /**
     * Prevent spamming async lookups if updateAll() runs frequently.
     */
    private final Set<UUID> resolving = ConcurrentHashMap.newKeySet();

    public LeaderboardDisplay(Plugin plugin) {
        this.plugin = plugin;
    }

    public void updateAll(LeaderboardManager lb) {
        updatePlayerMannequin(lb, "rarest_fish", "rarestFishMannequin");
        updatePlayerMannequin(lb, "most_filled_journal", "journalMannequin");
        updateBiggestClaim(lb);
    }

    public void removeAll(LeaderboardManager lb) {
        // Remove mannequins
        removeEntityByStoredUuid(lb, "rarestFishMannequin");
        removeEntityByStoredUuid(lb, "journalMannequin");

        // Remove claim text displays
        removeTextEntityByStoredUuid(lb, "claim_title");
        removeTextEntityByStoredUuid(lb, "claim_sub");

        // Remove banner block
        Location loc = lb.getDisplayLocation("biggestClaimBanner");
        if (loc != null && loc.getWorld() != null) {
            Block b = loc.getWorld().getBlockAt(loc);
            b.setType(Material.AIR, false);
        }

        // Clear UUID refs
        lb.clearEntityUuid("rarestFishMannequin");
        lb.clearEntityUuid("journalMannequin");
        lb.clearTextEntityUuid("claim_title");
        lb.clearTextEntityUuid("claim_sub");
    }

    private void removeEntityByStoredUuid(LeaderboardManager lb, String displayKey) {
        Location loc = lb.getDisplayLocation(displayKey);
        UUID id = lb.getEntityUuid(displayKey);
        if (id == null) return;

        // First try global lookup (works if entity is loaded anywhere)
        Entity e = Bukkit.getEntity(id);

        // If not found, try loading the chunk where it SHOULD be and retry
        if (e == null && loc != null && loc.getWorld() != null) {
            var chunk = loc.getChunk();
            if (!chunk.isLoaded()) chunk.load();

            e = loc.getWorld().getEntity(id);
        }

        if (e != null) {
            e.remove();
        }
    }

    private void removeTextEntityByStoredUuid(LeaderboardManager lb, String key) {
        Location loc = lb.getDisplayLocation("biggestClaimBanner");
        UUID id = lb.getTextEntityUuid(key);
        if (id == null) return;

        Entity e = Bukkit.getEntity(id);

        if (e == null && loc != null && loc.getWorld() != null) {
            var chunk = loc.getChunk();
            if (!chunk.isLoaded()) chunk.load();

            e = loc.getWorld().getEntity(id);
        }

        if (e != null) {
            e.remove();
        }
    }


    private void updatePlayerMannequin(LeaderboardManager lb, String boardKey, String displayKey) {
        Location rawLoc = lb.getDisplayLocation(displayKey);
        if (rawLoc == null || rawLoc.getWorld() == null) return;

        // ✅ Center mannequin on the block
        Location loc = centerOnBlock(rawLoc);

        UUID winnerUuid = lb.getWinnerPlayerUuid(boardKey);
        String winnerName = lb.getWinnerPlayerName(boardKey);

        World w = loc.getWorld();

        UUID existingId = lb.getEntityUuid(displayKey);

        Mannequin mannequin = null;
        if (existingId != null) {
            Entity e = w.getEntity(existingId);
            if (e instanceof Mannequin m) mannequin = m;
        }

        if (mannequin == null) {
            mannequin = (Mannequin) w.spawnEntity(loc, EntityType.MANNEQUIN);
            lb.setEntityUuid(displayKey, mannequin.getUniqueId());
        } else {
            mannequin.teleport(loc);
        }

        mannequin.setInvulnerable(true);
        mannequin.setGlowing(true);
        mannequin.setAI(false);
        mannequin.setSilent(true);
        mannequin.setImmovable(true);
        mannequin.setPersistent(true);

        String shownName = (winnerName == null || winnerName.isBlank()) ? "None" : winnerName;
        mannequin.customName(Component.text(shownName, NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        mannequin.setCustomNameVisible(true);

        // ✅ Face EXACT yaw stored in the location
        float yaw = normalizeYaw(loc.getYaw() + MANNEQUIN_YAW_OFFSET);
        mannequin.setRotation(yaw, 0f);

        // ✅ Skin/Profile
        applyMannequinSkin(lb, boardKey, mannequin, winnerUuid, winnerName);
    }

    /**
     * Forces a texture-filled profile onto the mannequin.
     *
     * Key idea: Create a Paper PlayerProfile and COMPLETE it off-thread
     * (this fetches textures), then set ResolvableProfile built from that.
     *
     * This is more reliable than UUID/name-only profiles which often remain Steve/Alex.
     */
    private void applyMannequinSkin(LeaderboardManager lb, String boardKey, Mannequin mannequin, UUID winnerUuid, String winnerName) {
        if (winnerUuid == null) {
            mannequin.setProfile(null);
            return;
        }

        // If we already have a completed profile cached, apply it immediately.
        PlayerProfile cached = resolvedProfileCache.get(winnerUuid);
        if (cached != null) {
            setMannequinProfileSafe(lb, boardKey, mannequin, winnerUuid, cached);
            return;
        }

        // Avoid launching multiple resolves for the same UUID
        if (!resolving.add(winnerUuid)) {
            return;
        }

        // Resolve off-thread (blocking call)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Create & complete profile to pull textures (blocking)
                PlayerProfile pp = Bukkit.createProfile(winnerUuid, (winnerName == null || winnerName.isBlank()) ? null : winnerName);
                pp.complete(true); // fetch textures

                resolvedProfileCache.put(winnerUuid, pp);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    setMannequinProfileSafe(lb, boardKey, mannequin, winnerUuid, pp);
                });

            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to resolve skin for " + winnerUuid + ": " + ex.getMessage());
            } finally {
                resolving.remove(winnerUuid);
            }
        });
    }

    /**
     * Apply the profile only if the mannequin is still valid and the leaderboard winner hasn't changed.
     * Also applies 1 tick later to help clients consistently receive the updated profile.
     */
    private void setMannequinProfileSafe(LeaderboardManager lb, String boardKey, Mannequin mannequin, UUID expectedWinner, PlayerProfile resolved) {
        if (!mannequin.isValid()) return;

        UUID latestWinner = lb.getWinnerPlayerUuid(boardKey);
        if (latestWinner == null || !latestWinner.equals(expectedWinner)) return;

        // Apply now
        mannequin.setProfile(ResolvableProfile.resolvableProfile(resolved));

        // Apply again 1 tick later (helps “always default skin” issues on some clients/setups)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mannequin.isValid()) return;

            UUID latest = lb.getWinnerPlayerUuid(boardKey);
            if (latest == null || !latest.equals(expectedWinner)) return;

            mannequin.setProfile(ResolvableProfile.resolvableProfile(resolved));
        }, 1L);
    }

    private void updateBiggestClaim(LeaderboardManager lb) {
        Location rawLoc = lb.getDisplayLocation("biggestClaimBanner");
        if (rawLoc == null || rawLoc.getWorld() == null) return;

        World w = rawLoc.getWorld();
        Block b = w.getBlockAt(rawLoc);

        String teamName = lb.getWinnerTeamName("biggest_claim");
        int radius = (int) lb.getWinnerValue("biggest_claim");

        Material bannerMat = materialOrNull(lb.getClaimBannerMaterial());
        if (bannerMat == null || !bannerMat.name().endsWith("_BANNER")) {
            bannerMat = Material.WHITE_BANNER;
        }

        // Place banner
        b.setType(bannerMat, false);

        // Rotate banner to match the yaw stored on the location (player look direction)
        orientStandingBannerToYaw(b, rawLoc.getYaw());

        // Apply patterns
        applyBannerDesign(b, lb.getClaimBannerPatterns());

        Location base = rawLoc.clone().add(0.50, 1.2, 0.50);
        Location titleLoc = base.clone().add(0.0, 1.05, 0.0);
        Location subLoc   = base.clone().add(0.0, 0.78, 0.0);

        TextDisplay title = findOrSpawnTextDisplayPersistent(lb, "claim_title", w, titleLoc);
        TextDisplay sub   = findOrSpawnTextDisplayPersistent(lb, "claim_sub", w, subLoc);

        String shownTeam = (teamName == null || teamName.isBlank()) ? "None" : teamName;

        title.text(Component.text(shownTeam, NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        title.setBillboard(Display.Billboard.CENTER);
        title.setSeeThrough(true);

        sub.text(Component.text("Claim Radius: " + Math.max(0, radius), NamedTextColor.GRAY));
        sub.setBillboard(Display.Billboard.CENTER);
        sub.setSeeThrough(true);
    }

    private TextDisplay findOrSpawnTextDisplayPersistent(LeaderboardManager lb, String key, World w, Location loc) {
        UUID id = lb.getTextEntityUuid(key);
        if (id != null) {
            Entity e = w.getEntity(id);
            if (e instanceof TextDisplay td) {
                td.teleport(loc);
                return td;
            }
        }

        TextDisplay td = (TextDisplay) w.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        td.setInvulnerable(true);
        td.setPersistent(true);
        td.setGlowing(true);
        td.setBillboard(Display.Billboard.CENTER);
        td.setSeeThrough(true);

        lb.setTextEntityUuid(key, td.getUniqueId());
        return td;
    }

    private Material materialOrNull(String name) {
        if (name == null) return null;
        try { return Material.valueOf(name); }
        catch (Exception e) { return null; }
    }

    private void applyBannerDesign(Block block, List<Map<String, Object>> patterns) {
        try {
            BlockState st = block.getState();
            if (!(st instanceof Banner banner)) return;

            List<org.bukkit.block.banner.Pattern> out = new ArrayList<>();

            for (Map<String, Object> map : patterns) {
                if (map == null) continue;

                String colorName = map.get("color") != null ? String.valueOf(map.get("color")) : null;
                String typeName  = map.get("type")  != null ? String.valueOf(map.get("type"))  : null;

                if (colorName == null || typeName == null) continue;

                DyeColor dye;
                try { dye = DyeColor.valueOf(colorName); }
                catch (Exception e) { continue; }

                NamespacedKey key = NamespacedKey.fromString(typeName);
                if (key == null) continue;

                var pt = Registry.BANNER_PATTERN.get(key);
                if (pt == null) continue;

                out.add(new org.bukkit.block.banner.Pattern(dye, pt));
            }

            banner.setPatterns(out);
            banner.update(true, false);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply banner design: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private Location centerOnBlock(Location raw) {
        Location out = raw.getBlock().getLocation().add(0.5, 0.0, 0.5);
        out.setYaw(raw.getYaw());
        out.setPitch(raw.getPitch());
        return out;
    }

    private float normalizeYaw(float yaw) {
        return (yaw % 360f + 360f) % 360f;
    }

    private void orientStandingBannerToYaw(Block b, float yaw) {
        BlockData data = b.getBlockData();
        if (!(data instanceof Rotatable rot)) return;

        rot.setRotation(blockFaceFromYaw16(yaw));
        b.setBlockData(rot, false);
    }

    private BlockFace blockFaceFromYaw16(float yaw) {
        yaw = normalizeYaw(yaw);

        // 0=S, 45=SW, 90=W, 135=NW, 180=N, 225=NE, 270=E, 315=SE
        BlockFace[] faces = new BlockFace[] {
                BlockFace.SOUTH,
                BlockFace.SOUTH_SOUTH_WEST,
                BlockFace.SOUTH_WEST,
                BlockFace.WEST_SOUTH_WEST,
                BlockFace.WEST,
                BlockFace.WEST_NORTH_WEST,
                BlockFace.NORTH_WEST,
                BlockFace.NORTH_NORTH_WEST,
                BlockFace.NORTH,
                BlockFace.NORTH_NORTH_EAST,
                BlockFace.NORTH_EAST,
                BlockFace.EAST_NORTH_EAST,
                BlockFace.EAST,
                BlockFace.EAST_SOUTH_EAST,
                BlockFace.SOUTH_EAST,
                BlockFace.SOUTH_SOUTH_EAST
        };

        int idx = (int) Math.floor((yaw + 11.25) / 22.5) & 15;
        return faces[idx];
    }
}
