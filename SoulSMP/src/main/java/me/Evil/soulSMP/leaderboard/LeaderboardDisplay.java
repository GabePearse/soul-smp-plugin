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

    private static final float MANNEQUIN_YAW_OFFSET = 0f;

    private final Map<UUID, PlayerProfile> resolvedProfileCache = new ConcurrentHashMap<>();
    private final Set<UUID> resolving = ConcurrentHashMap.newKeySet();

    public LeaderboardDisplay(Plugin plugin) {
        this.plugin = plugin;
    }

    public void updateAll(LeaderboardManager lb) {
        updatePlayerMannequin(lb, "rarest_fish", "rarestFishMannequin");
        updatePlayerMannequin(lb, "most_filled_journal", "journalMannequin");
        updateBiggestClaim(lb);
    }

    // =========================================================
    // FIX: reliably find existing mannequin after restart
    // =========================================================
    private Mannequin findExistingMannequin(World world, Location loc, UUID uuid) {
        if (uuid == null) return null;

        // Fast path (works if chunk already loaded)
        Entity direct = Bukkit.getEntity(uuid);
        if (direct instanceof Mannequin m) {
            return m;
        }

        // Force-load chunk and scan
        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) chunk.load();

        for (Entity e : chunk.getEntities()) {
            if (e instanceof Mannequin m && m.getUniqueId().equals(uuid)) {
                return m;
            }
        }

        return null;
    }

    public void removeAll(LeaderboardManager lb) {
        removeEntityByStoredUuid(lb, "rarestFishMannequin");
        removeEntityByStoredUuid(lb, "journalMannequin");

        removeTextEntityByStoredUuid(lb, "rarest_title");
        removeTextEntityByStoredUuid(lb, "journal_title");

        removeTextEntityByStoredUuid(lb, "rarest_value");
        removeTextEntityByStoredUuid(lb, "journal_value");

        removeTextEntityByStoredUuid(lb, "claim_reason");
        removeTextEntityByStoredUuid(lb, "claim_title");
        removeTextEntityByStoredUuid(lb, "claim_sub");

        Location loc = lb.getDisplayLocation("biggestClaimBanner");
        if (loc != null && loc.getWorld() != null) {
            loc.getWorld().getBlockAt(loc).setType(Material.AIR, false);
        }

        lb.clearEntityUuid("rarestFishMannequin");
        lb.clearEntityUuid("journalMannequin");

        lb.clearTextEntityUuid("rarest_title");
        lb.clearTextEntityUuid("journal_title");
        lb.clearTextEntityUuid("rarest_value");
        lb.clearTextEntityUuid("journal_value");

        lb.clearTextEntityUuid("claim_reason");
        lb.clearTextEntityUuid("claim_title");
        lb.clearTextEntityUuid("claim_sub");
    }

    private void removeEntityByStoredUuid(LeaderboardManager lb, String displayKey) {
        Location loc = lb.getDisplayLocation(displayKey);
        UUID id = lb.getEntityUuid(displayKey);
        if (id == null || loc == null || loc.getWorld() == null) return;

        Mannequin m = findExistingMannequin(loc.getWorld(), loc, id);
        if (m != null) m.remove();
    }

    private void removeTextEntityByStoredUuid(LeaderboardManager lb, String key) {
        UUID id = lb.getTextEntityUuid(key);
        if (id == null) return;

        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    private void updatePlayerMannequin(LeaderboardManager lb, String boardKey, String displayKey) {
        Location rawLoc = lb.getDisplayLocation(displayKey);
        if (rawLoc == null || rawLoc.getWorld() == null) return;

        Location loc = centerOnBlock(rawLoc);
        World w = loc.getWorld();

        UUID winnerUuid = lb.getWinnerPlayerUuid(boardKey);
        String winnerName = lb.getWinnerPlayerName(boardKey);

        String teamName = (winnerUuid == null) ? null : lb.getTeamNameForPlayer(winnerUuid);
        String shownTeam = (teamName == null || teamName.isBlank())
                ? "NO TEAM"
                : teamName.toUpperCase(Locale.ROOT);

        String shownName = (winnerName == null || winnerName.isBlank()) ? "None" : winnerName;

        Component nameComponent = Component.text("[", NamedTextColor.WHITE)
                .append(Component.text(shownTeam, NamedTextColor.AQUA))
                .append(Component.text("] ", NamedTextColor.WHITE))
                .append(Component.text(shownName, NamedTextColor.DARK_RED))
                .decorate(TextDecoration.BOLD);

        UUID existingId = lb.getEntityUuid(displayKey);
        Mannequin mannequin = findExistingMannequin(w, loc, existingId);

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

        mannequin.customName(nameComponent);
        mannequin.setCustomNameVisible(true);

        Component valuePart = Component.empty();

        if (displayKey.equalsIgnoreCase("rarestFishMannequin")) {
            String odds = lb.formatRarityOdds(lb.getWinnerValue(boardKey));
            valuePart = Component.text("RARITY: ", NamedTextColor.GRAY)
                    .append(Component.text(odds, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));

            updateMannequinTitleOnly(
                    lb,
                    "rarest_title",
                    w,
                    loc,
                    rawLoc,
                    Component.text("RAREST FISH", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
            );

        } else if (displayKey.equalsIgnoreCase("journalMannequin")) {
            String pct = lb.formatNumber(lb.getWinnerValue(boardKey), 1) + "%";
            valuePart = Component.text("JOURNAL: ", NamedTextColor.GRAY)
                    .append(Component.text(pct, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));

            updateMannequinTitleOnly(
                    lb,
                    "journal_title",
                    w,
                    loc,
                    rawLoc,
                    Component.text("JOURNAL LEADER", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
            );
        }

        mannequin.setDescription(valuePart);

        float yaw = normalizeYaw(loc.getYaw() + MANNEQUIN_YAW_OFFSET);
        mannequin.setRotation(yaw, 0f);

        applyMannequinSkin(lb, boardKey, mannequin, winnerUuid, winnerName);
    }

    /* =========================
       Everything below unchanged
       ========================= */

    private void updateMannequinTitleOnly(
            LeaderboardManager lb,
            String textKey,
            World w,
            Location mannequinLoc,
            Location rawLocForChunk,
            Component titleText
    ) {
        Location titleLoc = mannequinLoc.clone().add(0.0, 3.0, 0.0);
        TextDisplay title = findOrSpawnTextDisplayPersistent(lb, textKey, w, titleLoc, rawLocForChunk);
        title.teleport(titleLoc);
        applyTextDisplayStyle(title);
        title.text(titleText);
    }

    private void applyTextDisplayStyle(TextDisplay td) {
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setPersistent(true);
        td.setInvulnerable(true);
        td.setGlowing(true);
        td.setSeeThrough(false);
    }

    private TextDisplay findOrSpawnTextDisplayPersistent(
            LeaderboardManager lb,
            String key,
            World w,
            Location loc,
            Location rawLocForChunk
    ) {
        UUID id = lb.getTextEntityUuid(key);
        if (id != null) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof TextDisplay td) return td;
        }

        TextDisplay td = (TextDisplay) w.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        applyTextDisplayStyle(td);
        lb.setTextEntityUuid(key, td.getUniqueId());
        return td;
    }

    private void applyMannequinSkin(
            LeaderboardManager lb,
            String boardKey,
            Mannequin mannequin,
            UUID winnerUuid,
            String winnerName
    ) {
        if (winnerUuid == null) {
            mannequin.setProfile(null);
            return;
        }

        PlayerProfile cached = resolvedProfileCache.get(winnerUuid);
        if (cached != null) {
            mannequin.setProfile(ResolvableProfile.resolvableProfile(cached));
            return;
        }

        if (!resolving.add(winnerUuid)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerProfile pp = Bukkit.createProfile(winnerUuid, winnerName);
                pp.complete(true);
                resolvedProfileCache.put(winnerUuid, pp);

                Bukkit.getScheduler().runTask(plugin, () ->
                        mannequin.setProfile(ResolvableProfile.resolvableProfile(pp))
                );
            } finally {
                resolving.remove(winnerUuid);
            }
        });
    }

    private void updateBiggestClaim(LeaderboardManager lb) {
        Location rawLoc = lb.getDisplayLocation("biggestClaimBanner");
        if (rawLoc == null || rawLoc.getWorld() == null) return;

        World w = rawLoc.getWorld();
        Block bannerBlock = rawLoc.getBlock();

        Material bannerMat = materialOrNull(lb.getClaimBannerMaterial());
        if (bannerMat == null || !bannerMat.name().endsWith("_BANNER")) {
            bannerMat = Material.WHITE_BANNER;
        }

        bannerBlock.setType(bannerMat, false);
        orientStandingBannerToYaw(bannerBlock, rawLoc.getYaw());
        applyBannerDesign(bannerBlock, lb.getClaimBannerPatterns());

        Location center = bannerBlock.getLocation().add(0.5, 0.25, 0.5);

        Location reasonLoc = center.clone().add(0.0, 3.0, 0.0);
        TextDisplay reason = findOrSpawnClaimText(lb, "claim_reason", w, reasonLoc, rawLoc);
        reason.teleport(reasonLoc);
        reason.text(Component.text("BIGGEST CLAIM", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        Location titleLoc = center.clone().add(0.0, 2.05, 0.0);
        Location subLoc = center.clone().add(0.0, 1.78, 0.0);

        TextDisplay title = findOrSpawnClaimText(lb, "claim_title", w, titleLoc, rawLoc);
        TextDisplay sub = findOrSpawnClaimText(lb, "claim_sub", w, subLoc, rawLoc);

        String team = Optional.ofNullable(lb.getWinnerTeamName("biggest_claim")).orElse("None");
        int radius = (int) lb.getWinnerValue("biggest_claim");

        title.teleport(titleLoc);
        title.text(Component.text("[", NamedTextColor.WHITE)
                .append(Component.text(team.toUpperCase(Locale.ROOT), NamedTextColor.AQUA))
                .append(Component.text("]", NamedTextColor.WHITE))
                .decorate(TextDecoration.BOLD));

        sub.teleport(subLoc);
        sub.text(Component.text("Claim Radius: " + Math.max(0, radius), NamedTextColor.GRAY));
    }

    private TextDisplay findOrSpawnClaimText(
            LeaderboardManager lb,
            String key,
            World w,
            Location loc,
            Location rawLocForChunk
    ) {
        UUID id = lb.getTextEntityUuid(key);
        if (id != null) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof TextDisplay td) return td;
        }

        TextDisplay td = (TextDisplay) w.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setInvulnerable(true);
        td.setPersistent(true);
        td.setGlowing(true);
        td.setSeeThrough(false);

        lb.setTextEntityUuid(key, td.getUniqueId());
        return td;
    }

    private Material materialOrNull(String name) {
        try { return name == null ? null : Material.valueOf(name); }
        catch (Exception e) { return null; }
    }

    private void applyBannerDesign(Block block, List<Map<String, Object>> patterns) {
        try {
            BlockState st = block.getState();
            if (!(st instanceof Banner banner)) return;

            List<org.bukkit.block.banner.Pattern> out = new ArrayList<>();

            for (Map<String, Object> map : patterns) {
                DyeColor dye = DyeColor.valueOf(String.valueOf(map.get("color")));
                NamespacedKey key = NamespacedKey.fromString(String.valueOf(map.get("type")));
                var pt = Registry.BANNER_PATTERN.get(key);
                if (pt != null) out.add(new org.bukkit.block.banner.Pattern(dye, pt));
            }

            banner.setPatterns(out);
            banner.update(true, false);
        } catch (Exception ignored) {}
    }

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
        if (data instanceof Rotatable rot) {
            rot.setRotation(blockFaceFromYaw16(yaw));
            b.setBlockData(rot, false);
        }
    }

    private BlockFace blockFaceFromYaw16(float yaw) {
        yaw = normalizeYaw(yaw);
        BlockFace[] faces = {
                BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
                BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
                BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
                BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
        };
        int idx = (int) Math.floor((yaw + 11.25) / 22.5) & 15;
        return faces[idx];
    }
}
