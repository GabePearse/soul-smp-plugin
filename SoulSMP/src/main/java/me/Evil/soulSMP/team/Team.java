package me.Evil.soulSMP.team;

import me.Evil.soulSMP.upkeep.UpkeepStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.banner.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import java.util.*;

public class Team {

    public static final int MAX_MEMBERS = 3;

    // Identity & members
    private final String name;
    private final Set<UUID> members = new HashSet<>();
    private final Map<String, Integer> beaconEffects = new HashMap<>();
    private UUID owner;

    // Claim: banner location + radius
    private Location bannerLocation;
    private int claimRadius;

    // Claimed banner design
    private Material bannerMaterial;
    private List<Pattern> bannerPatterns = new ArrayList<>();

    // Extra dimensional banners & unlocks
    private final Map<String, Location> dimensionalBanners = new HashMap<>();
    private final Set<String> unlockedDimensionalBanners = new HashSet<>();
    private final Set<String> unlockedDimensionalTeleports = new HashSet<>();

    // Progression
    private int lives;
    private int vaultSize;

    // === Upkeep ===
    private long lastUpkeepPaymentMillis = 0L;
    private int unpaidWeeks = 0;
    private UpkeepStatus upkeepStatus = UpkeepStatus.PROTECTED;
    private int baseClaimRadiusForUpkeep = -1;

    public Team(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
        if (owner != null) {
            this.members.add(owner);
        }

        this.claimRadius = 1;
        this.lives = 10;
        this.vaultSize = 1;
    }

    // --- Identity ---

    public String getName() { return name; }

    public UUID getOwner() { return owner; }

    public void setOwner(UUID owner) { this.owner = owner; }

    // --- Members ---

    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }

    public boolean isMember(UUID uuid) { return members.contains(uuid); }

    public boolean addMember(UUID uuid) {
        if (members.size() >= MAX_MEMBERS) return false;
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) { return members.remove(uuid); }

    public boolean isFull() { return members.size() >= MAX_MEMBERS; }

    // --- Beacon effects ---

    public void setEffectLevel(String id, int level) { beaconEffects.put(id, level); }

    public int getEffectLevel(String id) { return beaconEffects.getOrDefault(id, 0); }

    public Map<String, Integer> getEffectMap() { return new HashMap<>(beaconEffects); }

    public void setEffectMap(Map<String, Integer> newEffects) {
        beaconEffects.clear();
        if (newEffects != null) beaconEffects.putAll(newEffects);
    }

    // --- Claim ---

    public Location getBannerLocation() { return bannerLocation; }

    public void setBannerLocation(Location bannerLocation) { this.bannerLocation = bannerLocation; }

    public boolean hasBannerLocation() { return bannerLocation != null; }

    public int getClaimRadius() { return claimRadius; }

    public void setClaimRadius(int claimRadius) { this.claimRadius = claimRadius; }

    // --- Banner design ---

    public boolean hasClaimedBannerDesign() { return bannerMaterial != null; }

    public Material getBannerMaterial() {
        return bannerMaterial;
    }


    public void setBannerDesign(ItemStack stack) {
        if (stack == null || !stack.getType().name().endsWith("_BANNER")) return;
        if (!(stack.getItemMeta() instanceof BannerMeta meta)) return;

        this.bannerMaterial = stack.getType();
        this.bannerPatterns = new ArrayList<>(meta.getPatterns());
    }

    public void clearBannerDesign() {
        this.bannerMaterial = null;
        this.bannerPatterns.clear();
    }

    public boolean matchesBannerDesign(ItemStack stack) {
        if (!hasClaimedBannerDesign()) return false;
        if (stack == null || stack.getType() != this.bannerMaterial) return false;
        if (!(stack.getItemMeta() instanceof BannerMeta meta)) return false;

        List<Pattern> other = meta.getPatterns();
        if (other.size() != bannerPatterns.size()) return false;
        for (int i = 0; i < other.size(); i++) {
            if (!other.get(i).equals(bannerPatterns.get(i))) return false;
        }
        return true;
    }

    public ItemStack createBannerItem() {
        if (!hasClaimedBannerDesign()) return null;
        ItemStack banner = new ItemStack(bannerMaterial);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        meta.setPatterns(new ArrayList<>(bannerPatterns));
        banner.setItemMeta(meta);
        return banner;
    }

    // --- Lives & Vault ---

    public int getLives() { return lives; }

    public void setLives(int lives) { this.lives = lives; }

    public void addLives(int amount) { this.lives += amount; }

    public void removeLife() { this.lives--; }

    public int getVaultSize() { return vaultSize; }

    public void setVaultSize(int vaultSize) { this.vaultSize = vaultSize; }

    // --- Dimensional banners & teleports ---

    public boolean hasDimensionalBannerLocation(String dimKey) {
        return getDimensionalBanner(dimKey) != null;
    }

    public Map<String, Location> getDimensionalBanners() {
        return Collections.unmodifiableMap(dimensionalBanners);
    }

    public void unlockDimensionalBanner(String dimKey) {
        dimKey = normalizeDimension(dimKey);
        unlockedDimensionalBanners.add(dimKey);
    }

    public boolean hasDimensionalBannerUnlocked(String dimKey) {
        return unlockedDimensionalBanners.contains(normalizeDimension(dimKey));
    }

    public void unlockDimensionalTeleport(String dimKey) {
        unlockedDimensionalTeleports.add(normalizeDimension(dimKey));
    }

    public boolean hasDimensionalTeleportUnlocked(String dimKey) {
        return unlockedDimensionalTeleports.contains(normalizeDimension(dimKey));
    }

    public void setDimensionalBanner(String dimKey, Location loc) {
        dimensionalBanners.put(normalizeDimension(dimKey), loc);
    }

    public Location getDimensionalBanner(String dimKey) {
        return dimensionalBanners.get(normalizeDimension(dimKey));
    }

    private static String normalizeDimension(String key) {
        return key == null ? null : key.toUpperCase(Locale.ROOT);
    }

    // --- Upkeep ---

    public long getLastUpkeepPaymentMillis() { return lastUpkeepPaymentMillis; }

    public void setLastUpkeepPaymentMillis(long millis) { this.lastUpkeepPaymentMillis = millis; }

    public int getUnpaidWeeks() { return unpaidWeeks; }

    public void setUnpaidWeeks(int unpaidWeeks) { this.unpaidWeeks = unpaidWeeks; }

    public UpkeepStatus getUpkeepStatus() { return upkeepStatus; }

    public void setUpkeepStatus(UpkeepStatus upkeepStatus) { this.upkeepStatus = upkeepStatus; }

    public int getBaseClaimRadiusForUpkeep() { return baseClaimRadiusForUpkeep; }

    public void setBaseClaimRadiusForUpkeep(int radius) { this.baseClaimRadiusForUpkeep = radius; }

    // --- Serialization ---

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("name", name);
        if (owner != null) map.put("owner", owner.toString());

        // Members
        List<String> memberStrings = new ArrayList<>();
        for (UUID uuid : members) memberStrings.add(uuid.toString());
        map.put("members", memberStrings);

        map.put("effects", beaconEffects);
        map.put("lives", lives);
        map.put("claimRadius", claimRadius);
        map.put("vaultSize", vaultSize);

        // Banner location
        if (bannerLocation != null && bannerLocation.getWorld() != null) {
            Map<String, Object> loc = new LinkedHashMap<>();
            loc.put("world", bannerLocation.getWorld().getName());
            loc.put("x", bannerLocation.getX());
            loc.put("y", bannerLocation.getY());
            loc.put("z", bannerLocation.getZ());
            map.put("bannerLocation", loc);
        }

        // Banner design
        if (hasClaimedBannerDesign()) {
            Map<String, Object> design = new LinkedHashMap<>();
            design.put("material", bannerMaterial.name());

            List<Map<String, Object>> patternsList = new ArrayList<>();
            for (Pattern p : bannerPatterns) {
                Map<String, Object> pMap = new LinkedHashMap<>();
                pMap.put("color", p.getColor().name());

                NamespacedKey key = Registry.BANNER_PATTERN.getKey(p.getPattern());
                if (key != null) pMap.put("type", key.toString());

                patternsList.add(pMap);
            }
            design.put("patterns", patternsList);
            map.put("bannerDesign", design);
        }

        // Dimensional banners
        if (!dimensionalBanners.isEmpty()) {
            Map<String, Object> dimMap = new LinkedHashMap<>();
            for (var entry : dimensionalBanners.entrySet()) {
                Location loc = entry.getValue();
                if (loc == null || loc.getWorld() == null) continue;

                Map<String, Object> locMap = new LinkedHashMap<>();
                locMap.put("world", loc.getWorld().getName());
                locMap.put("x", loc.getX());
                locMap.put("y", loc.getY());
                locMap.put("z", loc.getZ());
                dimMap.put(entry.getKey(), locMap);
            }
            map.put("dimensionalBanners", dimMap);
        }

        map.put("unlockedDimensionalBanners", new ArrayList<>(unlockedDimensionalBanners));
        map.put("unlockedDimensionalTeleports", new ArrayList<>(unlockedDimensionalTeleports));

        // === Upkeep Persistence ===
        map.put("lastUpkeepPayment", lastUpkeepPaymentMillis);
        map.put("unpaidWeeks", unpaidWeeks);
        map.put("upkeepStatus", upkeepStatus != null ? upkeepStatus.name() : "PROTECTED");
        map.put("baseClaimRadiusForUpkeep", baseClaimRadiusForUpkeep);

        return map;
    }

    // --- Deserialize ---

    @SuppressWarnings("unchecked")
    public static Team deserialize(ConfigurationSection sec) {
        String name = sec.getString("name", sec.getName());
        String ownerStr = sec.getString("owner");
        UUID owner = ownerStr != null ? UUID.fromString(ownerStr) : null;

        Team team = new Team(name, owner);

        // Members
        for (String s : sec.getStringList("members")) {
            if (s != null && !s.isEmpty()) team.members.add(UUID.fromString(s));
        }
        if (owner != null) team.members.add(owner);

        // Basic values
        team.lives = sec.getInt("lives", 5);
        team.claimRadius = sec.getInt("claimRadius", 1);
        team.vaultSize = sec.getInt("vaultSize", 9);

        // Effects
        ConfigurationSection effSec = sec.getConfigurationSection("effects");
        if (effSec != null) {
            for (String k : effSec.getKeys(false)) {
                team.beaconEffects.put(k, effSec.getInt(k, 0));
            }
        }

        // Banner location
        ConfigurationSection locSec = sec.getConfigurationSection("bannerLocation");
        if (locSec != null) {
            String worldN = locSec.getString("world");
            World world = Bukkit.getWorld(worldN);
            if (world != null) {
                team.bannerLocation = new Location(
                        world,
                        locSec.getDouble("x"),
                        locSec.getDouble("y"),
                        locSec.getDouble("z")
                );
            }
        }

        // Banner design
        ConfigurationSection designSec = sec.getConfigurationSection("bannerDesign");
        if (designSec != null) {
            team.bannerMaterial = Material.matchMaterial(designSec.getString("material"));
            List<Map<?, ?>> patternMaps = designSec.getMapList("patterns");

            List<Pattern> patterns = new ArrayList<>();
            for (var map : patternMaps) {
                String colorName = (String) map.get("color");
                String typeName = (String) map.get("type");
                try {
                    var color = org.bukkit.DyeColor.valueOf(colorName);
                    var key = NamespacedKey.fromString(typeName);
                    var pt = key != null ? Registry.BANNER_PATTERN.get(key) : null;
                    if (pt != null) patterns.add(new Pattern(color, pt));
                } catch (Exception ignored) {}
            }
            team.bannerPatterns = patterns;
        }

        // Dimensional banners
        ConfigurationSection dimSec = sec.getConfigurationSection("dimensionalBanners");
        if (dimSec != null) {
            for (String dimKey : dimSec.getKeys(false)) {
                ConfigurationSection ls = dimSec.getConfigurationSection(dimKey);
                if (ls == null) continue;

                World w = Bukkit.getWorld(ls.getString("world"));
                if (w != null) {
                    team.dimensionalBanners.put(
                            dimKey.toUpperCase(Locale.ROOT),
                            new Location(w, ls.getDouble("x"), ls.getDouble("y"), ls.getDouble("z"))
                    );
                }
            }
        }

        for (String s : sec.getStringList("unlockedDimensionalBanners"))
            team.unlockedDimensionalBanners.add(s.toUpperCase(Locale.ROOT));

        for (String s : sec.getStringList("unlockedDimensionalTeleports"))
            team.unlockedDimensionalTeleports.add(s.toUpperCase(Locale.ROOT));

        // === Upkeep ===
        team.lastUpkeepPaymentMillis = sec.getLong("lastUpkeepPayment", 0L);
        team.unpaidWeeks = sec.getInt("unpaidWeeks", 0);

        String statusName = sec.getString("upkeepStatus", "PROTECTED");
        try {
            team.upkeepStatus = UpkeepStatus.valueOf(statusName.toUpperCase());
        } catch (Exception ignored) {
            team.upkeepStatus = UpkeepStatus.PROTECTED;
        }

        team.baseClaimRadiusForUpkeep = sec.getInt("baseClaimRadiusForUpkeep", -1);

        return team;
    }
}
