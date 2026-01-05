package me.Evil.soulSMP.opmode;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OpModeManager {

    private final Plugin plugin;

    private File file;
    private FileConfiguration cfg;

    // Cache
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();

    public OpModeManager(Plugin plugin) {
        this.plugin = plugin;
        init();
        load();
    }

    private void init() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        file = new File(plugin.getDataFolder(), "opmode.yml");
        if (!file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create opmode.yml: " + e.getMessage());
            }
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isInOpMode(UUID uuid) {
        return uuid != null && snapshots.containsKey(uuid);
    }

    public boolean isInOpMode(Player p) {
        if (p == null) return false;
        return isInOpMode(p.getUniqueId());
    }


    public void enter(Player p) {
        if (p == null) return;

        UUID uuid = p.getUniqueId();
        if (snapshots.containsKey(uuid)) return; // already

        Snapshot snap = Snapshot.capture(p);
        snapshots.put(uuid, snap);

        // Switch mode
        p.getInventory().clear();
        p.setExp(0f);
        p.setLevel(0);
        p.setTotalExperience(0);

        p.setGameMode(GameMode.CREATIVE);

        save();
    }

    public boolean exit(Player p) {
        if (p == null) return false;

        UUID uuid = p.getUniqueId();
        Snapshot snap = snapshots.remove(uuid);
        if (snap == null) return false;

        snap.restore(p);
        save();
        return true;
    }

    public void load() {
        init();
        snapshots.clear();

        ConfigurationSection root = cfg.getConfigurationSection("players");
        if (root == null) return;

        for (String uuidStr : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection sec = root.getConfigurationSection(uuidStr);
                if (sec == null) continue;

                Snapshot snap = Snapshot.deserialize(sec);
                if (snap != null) snapshots.put(uuid, snap);

            } catch (Exception ignored) {}
        }
    }

    public void save() {
        if (cfg == null) init();

        cfg.set("players", null);
        ConfigurationSection root = cfg.createSection("players");

        for (Map.Entry<UUID, Snapshot> e : snapshots.entrySet()) {
            UUID uuid = e.getKey();
            Snapshot snap = e.getValue();
            if (uuid == null || snap == null) continue;

            ConfigurationSection sec = root.createSection(uuid.toString());
            snap.serialize(sec);
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save opmode.yml: " + e.getMessage());
        }
    }

    // -----------------------
    // Snapshot
    // -----------------------

    private static class Snapshot {
        private ItemStack[] contents;
        private ItemStack[] armor;
        private ItemStack offhand;

        private float exp;
        private int level;
        private int totalExp;

        private String world;
        private double x, y, z;
        private float yaw, pitch;

        private String gameMode;

        static Snapshot capture(Player p) {
            Snapshot s = new Snapshot();

            s.contents = p.getInventory().getContents();
            s.armor = p.getInventory().getArmorContents();
            s.offhand = p.getInventory().getItemInOffHand();

            s.exp = p.getExp();
            s.level = p.getLevel();
            s.totalExp = p.getTotalExperience();

            Location loc = p.getLocation();
            World w = loc.getWorld();
            s.world = (w != null) ? w.getName() : "world";
            s.x = loc.getX();
            s.y = loc.getY();
            s.z = loc.getZ();
            s.yaw = loc.getYaw();
            s.pitch = loc.getPitch();

            GameMode gm = p.getGameMode();
            s.gameMode = (gm != null) ? gm.name() : GameMode.SURVIVAL.name();

            return s;
        }

        void restore(Player p) {
            // Restore gamemode first (some servers restrict inventory in some modes)
            try {
                p.setGameMode(GameMode.valueOf(gameMode));
            } catch (Exception ignored) {
                p.setGameMode(GameMode.SURVIVAL);
            }

            p.getInventory().setContents(contents != null ? contents : new ItemStack[0]);
            p.getInventory().setArmorContents(armor != null ? armor : new ItemStack[0]);
            p.getInventory().setItemInOffHand(offhand);

            p.setExp(exp);
            p.setLevel(level);
            p.setTotalExperience(totalExp);

            World w = p.getServer().getWorld(world);
            if (w != null) {
                Location loc = new Location(w, x, y, z, yaw, pitch);
                p.teleport(loc);
            }
        }

        void serialize(ConfigurationSection sec) {
            // Inventory
            sec.set("inventory.contents", contents);
            sec.set("inventory.armor", armor);
            sec.set("inventory.offhand", offhand);

            // XP
            sec.set("xp.exp", exp);
            sec.set("xp.level", level);
            sec.set("xp.total", totalExp);

            // Location
            sec.set("loc.world", world);
            sec.set("loc.x", x);
            sec.set("loc.y", y);
            sec.set("loc.z", z);
            sec.set("loc.yaw", yaw);
            sec.set("loc.pitch", pitch);

            // Mode
            sec.set("gamemode", gameMode);
        }

        @SuppressWarnings("unchecked")
        static Snapshot deserialize(ConfigurationSection sec) {
            Snapshot s = new Snapshot();

            s.contents = ((List<ItemStack>) sec.getList("inventory.contents", new ArrayList<>())).toArray(new ItemStack[0]);
            s.armor = ((List<ItemStack>) sec.getList("inventory.armor", new ArrayList<>())).toArray(new ItemStack[0]);
            s.offhand = sec.getItemStack("inventory.offhand");

            s.exp = (float) sec.getDouble("xp.exp", 0.0);
            s.level = sec.getInt("xp.level", 0);
            s.totalExp = sec.getInt("xp.total", 0);

            s.world = sec.getString("loc.world", "world");
            s.x = sec.getDouble("loc.x", 0.5);
            s.y = sec.getDouble("loc.y", 64.0);
            s.z = sec.getDouble("loc.z", 0.5);
            s.yaw = (float) sec.getDouble("loc.yaw", 0.0);
            s.pitch = (float) sec.getDouble("loc.pitch", 0.0);

            s.gameMode = sec.getString("gamemode", GameMode.SURVIVAL.name());

            // Basic sanity: must have some loc + gamemode
            return s;
        }
    }
}
