package me.Evil.soulSMP.team;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
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

    // Claimed banner design (only one per team)
    private Material bannerMaterial;            // e.g. RED_BANNER
    private List<Pattern> bannerPatterns = new ArrayList<>();

    // Extra dimensional banners & unlocks (by dimension key: "OVERWORLD", "NETHER", "THE_END", etc.)
    private final Map<String, Location> dimensionalBanners = new HashMap<>();
    private final Set<String> unlockedDimensionalBanners = new HashSet<>();
    private final Set<String> unlockedDimensionalTeleports = new HashSet<>();

    // Progression
    private int lives;
    private int vaultSize;

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

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    // --- Members ---

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean addMember(UUID uuid) {
        if (members.size() >= MAX_MEMBERS) return false;
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }

    public boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }

    // --- Beacon effects ---

    public void setEffectLevel(String id, int level) {
        beaconEffects.put(id, level);
    }

    public int getEffectLevel(String id) {
        return beaconEffects.getOrDefault(id, 0);
    }

    public Map<String, Integer> getEffectMap() {
        return new HashMap<>(beaconEffects);
    }

    public void setEffectMap(Map<String, Integer> newEffects) {
        beaconEffects.clear();
        if (newEffects != null) {
            beaconEffects.putAll(newEffects);
        }
    }

    // --- Claim location ---

    public Location getBannerLocation() {
        return bannerLocation;
    }

    public void setBannerLocation(Location bannerLocation) {
        this.bannerLocation = bannerLocation;
    }

    public boolean hasBannerLocation() {
        return bannerLocation != null;
    }

    public int getClaimRadius() {
        return claimRadius;
    }

    public void setClaimRadius(int claimRadius) {
        this.claimRadius = claimRadius;
    }

    // --- Claimed banner design ---

    public boolean hasClaimedBannerDesign() {
        return bannerMaterial != null;
    }

    /**
     * Claim banner design from an ItemStack the player is holding.
     */
    public void setBannerDesign(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return;
        if (!stack.getType().name().endsWith("_BANNER")) return;
        if (!(stack.getItemMeta() instanceof BannerMeta meta)) return;

        this.bannerMaterial = stack.getType();
        this.bannerPatterns = new ArrayList<>(meta.getPatterns());
    }

    public void clearBannerDesign() {
        this.bannerMaterial = null;
        this.bannerPatterns.clear();
    }

    /**
     * Check if the given banner item matches this team's claimed design.
     */
    public boolean matchesBannerDesign(ItemStack stack) {
        if (!hasClaimedBannerDesign()) return false;
        if (stack == null || stack.getType() == Material.AIR) return false;
        if (stack.getType() != this.bannerMaterial) return false;
        if (!(stack.getItemMeta() instanceof BannerMeta meta)) return false;

        List<Pattern> other = meta.getPatterns();
        if (other.size() != this.bannerPatterns.size()) return false;

        for (int i = 0; i < other.size(); i++) {
            if (!other.get(i).equals(this.bannerPatterns.get(i))) return false;
        }
        return true;
    }

    public Material getBannerMaterial() {
        return bannerMaterial;
    }

    public List<Pattern> getBannerPatterns() {
        return Collections.unmodifiableList(bannerPatterns);
    }

    /**
     * Create a banner ItemStack that matches this team's claimed design.
     */
    public ItemStack createBannerItem() {
        if (!hasClaimedBannerDesign()) return null;

        ItemStack banner = new ItemStack(bannerMaterial);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        meta.setPatterns(new ArrayList<>(bannerPatterns));
        banner.setItemMeta(meta);
        return banner;
    }

    // --- Lives & vault ---

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    public void addLives(int amount) {
        this.lives += amount;
    }

    public void removeLife() {
        this.lives -= 1;
    }

    public int getVaultSize() {
        return vaultSize;
    }

    public void setVaultSize(int vaultSize) {
        this.vaultSize = vaultSize;
    }

    // --- Dimensional banners / teleports ---

    public void unlockDimensionalBanner(String dimKey) {
        dimKey = normalizeDimensionKey(dimKey);
        if (dimKey != null) {
            unlockedDimensionalBanners.add(dimKey);
        }
    }

    public boolean hasDimensionalBannerUnlocked(String dimKey) {
        dimKey = normalizeDimensionKey(dimKey);
        return dimKey != null && unlockedDimensionalBanners.contains(dimKey);
    }

    public void unlockDimensionalTeleport(String dimKey) {
        dimKey = normalizeDimensionKey(dimKey);
        if (dimKey != null) {
            unlockedDimensionalTeleports.add(dimKey);
        }
    }

    public boolean hasDimensionalTeleportUnlocked(String dimKey) {
        dimKey = normalizeDimensionKey(dimKey);
        return dimKey != null && unlockedDimensionalTeleports.contains(dimKey);
    }

    public void setDimensionalBanner(String dimKey, Location loc) {
        dimKey = normalizeDimensionKey(dimKey);
        if (dimKey != null) {
            if (loc == null) {
                dimensionalBanners.remove(dimKey);
            } else {
                dimensionalBanners.put(dimKey, loc);
            }
        }
    }

    public Location getDimensionalBanner(String dimKey) {
        dimKey = normalizeDimensionKey(dimKey);
        if (dimKey == null) return null;
        return dimensionalBanners.get(dimKey);
    }

    public boolean hasDimensionalBannerLocation(String dimKey) {
        return getDimensionalBanner(dimKey) != null;
    }

    public Map<String, Location> getDimensionalBanners() {
        return Collections.unmodifiableMap(dimensionalBanners);
    }

    private static String normalizeDimensionKey(String key) {
        if (key == null) return null;
        return key.toUpperCase(Locale.ROOT);
    }

    // --- Serialization helpers for persistence ---

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        if (owner != null) {
            map.put("owner", owner.toString());
        }

        List<String> memberStrings = new ArrayList<>();
        for (UUID uuid : members) {
            memberStrings.add(uuid.toString());
        }
        map.put("members", memberStrings);
        map.put("effects", beaconEffects);
        map.put("lives", lives);
        map.put("claimRadius", claimRadius);
        map.put("vaultSize", vaultSize);

        if (bannerLocation != null && bannerLocation.getWorld() != null) {
            Map<String, Object> loc = new LinkedHashMap<>();
            loc.put("world", bannerLocation.getWorld().getName());
            loc.put("x", bannerLocation.getX());
            loc.put("y", bannerLocation.getY());
            loc.put("z", bannerLocation.getZ());
            map.put("bannerLocation", loc);
        }

        if (hasClaimedBannerDesign()) {
            Map<String, Object> design = new LinkedHashMap<>();
            design.put("material", bannerMaterial.name());

            List<Map<String, Object>> patternList = new ArrayList<>();
            for (Pattern p : bannerPatterns) {
                Map<String, Object> pMap = new LinkedHashMap<>();
                pMap.put("color", p.getColor().name());

                NamespacedKey key = Registry.BANNER_PATTERN.getKey(p.getPattern());
                if (key != null) {
                    pMap.put("type", key.toString());
                }

                patternList.add(pMap);
            }
            design.put("patterns", patternList);
            map.put("bannerDesign", design);
        }

        // Dimensional banners
        if (!dimensionalBanners.isEmpty()) {
            Map<String, Object> dimRoot = new LinkedHashMap<>();
            for (Map.Entry<String, Location> entry : dimensionalBanners.entrySet()) {
                String dimKey = entry.getKey();
                Location loc = entry.getValue();
                if (loc == null || loc.getWorld() == null) continue;

                Map<String, Object> locMap = new LinkedHashMap<>();
                locMap.put("world", loc.getWorld().getName());
                locMap.put("x", loc.getX());
                locMap.put("y", loc.getY());
                locMap.put("z", loc.getZ());
                dimRoot.put(dimKey, locMap);
            }
            map.put("dimensionalBanners", dimRoot);
        }

        if (!unlockedDimensionalBanners.isEmpty()) {
            map.put("unlockedDimensionalBanners", new ArrayList<>(unlockedDimensionalBanners));
        }
        if (!unlockedDimensionalTeleports.isEmpty()) {
            map.put("unlockedDimensionalTeleports", new ArrayList<>(unlockedDimensionalTeleports));
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    public static Team deserialize(ConfigurationSection sec) {
        String key = sec.getName();

        // --- Basic fields ---
        String name = sec.getString("name", key);

        String ownerStr = sec.getString("owner");
        UUID owner = null;
        if (ownerStr != null && !ownerStr.isEmpty()) {
            owner = UUID.fromString(ownerStr);
        }

        Team team = new Team(name, owner);

        // --- Members ---
        List<String> memberStrings = sec.getStringList("members");
        if (memberStrings != null) {
            for (String s : memberStrings) {
                if (s == null || s.isEmpty()) continue;
                team.members.add(UUID.fromString(s));
            }
        }
        if (owner != null) {
            team.members.add(owner);
        }

        // --- Numeric fields ---
        team.lives       = sec.getInt("lives", 5);
        team.claimRadius = sec.getInt("claimRadius", 1);
        team.vaultSize   = sec.getInt("vaultSize", 9);

        // --- Beacon effects ---
        ConfigurationSection effSec = sec.getConfigurationSection("effects");
        if (effSec != null) {
            for (String effKey : effSec.getKeys(false)) {
                int lvl = effSec.getInt(effKey, 0);
                team.beaconEffects.put(effKey, lvl);
            }
        }

        // --- Banner location ---
        ConfigurationSection locSec = sec.getConfigurationSection("bannerLocation");
        if (locSec != null) {
            String worldName = locSec.getString("world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;

            if (world != null) {
                double x = locSec.getDouble("x");
                double y = locSec.getDouble("y");
                double z = locSec.getDouble("z");

                team.bannerLocation = new Location(world, x, y, z);
            } else if (worldName != null) {
                Bukkit.getLogger().warning("[SoulSMP] World '" + worldName
                        + "' not found when loading bannerLocation for team " + team.name);
            }
        }

        // --- Banner design ---
        ConfigurationSection designSec = sec.getConfigurationSection("bannerDesign");
        if (designSec != null) {
            String matName = designSec.getString("material");
            if (matName != null) {
                team.bannerMaterial = Material.matchMaterial(matName);
            }

            List<Map<?, ?>> patternMaps = designSec.getMapList("patterns");
            List<Pattern> patterns = new ArrayList<>();

            for (Map<?, ?> pMap : patternMaps) {
                Object colorObj = pMap.get("color");
                Object typeObj  = pMap.get("type");

                if (!(colorObj instanceof String colorName)) continue;
                if (!(typeObj instanceof String typeName)) continue;

                try {
                    org.bukkit.DyeColor color = org.bukkit.DyeColor.valueOf(colorName);
                    NamespacedKey keyNs = NamespacedKey.fromString(typeName);
                    PatternType patternType = keyNs != null ? Registry.BANNER_PATTERN.get(keyNs) : null;

                    if (patternType != null) {
                        patterns.add(new Pattern(color, patternType));
                    }
                } catch (Exception ignored) {
                }
            }

            team.bannerPatterns = patterns;
        }

        // --- Dimensional banners & unlocks ---
        ConfigurationSection dimSec = sec.getConfigurationSection("dimensionalBanners");
        if (dimSec != null) {
            for (String dimKey : dimSec.getKeys(false)) {
                ConfigurationSection locSec2 = dimSec.getConfigurationSection(dimKey);
                if (locSec2 == null) continue;

                String worldName = locSec2.getString("world");
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                if (world == null) {
                    Bukkit.getLogger().warning("[SoulSMP] World '" + worldName
                            + "' not found when loading dimensional banner '" + dimKey
                            + "' for team " + team.name);
                    continue;
                }

                double x = locSec2.getDouble("x");
                double y = locSec2.getDouble("y");
                double z = locSec2.getDouble("z");

                team.dimensionalBanners.put(
                        dimKey.toUpperCase(Locale.ROOT),
                        new Location(world, x, y, z)
                );
            }
        }

        List<String> unlockedDims = sec.getStringList("unlockedDimensionalBanners");
        if (unlockedDims != null) {
            for (String k : unlockedDims) {
                if (k == null) continue;
                team.unlockedDimensionalBanners.add(k.toUpperCase(Locale.ROOT));
            }
        }

        List<String> unlockedTp = sec.getStringList("unlockedDimensionalTeleports");
        if (unlockedTp != null) {
            for (String k : unlockedTp) {
                if (k == null) continue;
                team.unlockedDimensionalTeleports.add(k.toUpperCase(Locale.ROOT));
            }
        }

        return team;
    }
}
