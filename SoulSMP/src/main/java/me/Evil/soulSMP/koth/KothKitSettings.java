package me.Evil.soulSMP.koth;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KothKitSettings {

    private final JavaPlugin plugin;
    private File file;
    private YamlConfiguration cfg;

    // ✅ Spawn is now a band (annulus)
    private int spawnInnerRadius = 20;
    private int spawnOuterRadius = 35;

    private int hillRadius = 2;
    private int winSeconds = 300;

    private boolean clearInventory = true;

    private ItemStack helmet, chest, legs, boots;
    private List<ItemStack> items = new ArrayList<>();
    private List<PotionEffect> effects = new ArrayList<>();

    public KothKitSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        file = new File(plugin.getDataFolder(), "koth.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();

                // write defaults
                cfg = new YamlConfiguration();

                // ✅ New defaults: band spawn
                cfg.set("settings.spawn-inner-radius", 20);
                cfg.set("settings.spawn-outer-radius", 35);

                cfg.set("settings.hill-radius", 2);
                cfg.set("settings.win-seconds", 300);

                cfg.set("kit.clear-inventory", true);
                cfg.set("kit.armor.helmet", "IRON_HELMET");
                cfg.set("kit.armor.chestplate", "IRON_CHESTPLATE");
                cfg.set("kit.armor.leggings", "IRON_LEGGINGS");
                cfg.set("kit.armor.boots", "IRON_BOOTS");

                List<Map<String, Object>> defaultItems = new ArrayList<>();
                defaultItems.add(Map.of("material", "IRON_SWORD", "amount", 1));
                defaultItems.add(Map.of("material", "COOKED_BEEF", "amount", 16));
                cfg.set("kit.items", defaultItems);

                cfg.set("kit.effects", new ArrayList<>());

                cfg.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create koth.yml: " + e.getMessage());
            }
        }

        cfg = YamlConfiguration.loadConfiguration(file);

        // ✅ Backwards compat: if old key exists, treat spawn-radius as OUTER.
        boolean hasNewInner = cfg.contains("settings.spawn-inner-radius");
        boolean hasNewOuter = cfg.contains("settings.spawn-outer-radius");
        boolean hasOldRadius = cfg.contains("settings.spawn-radius");

        if ((!hasNewInner || !hasNewOuter) && hasOldRadius) {
            int oldOuter = Math.max(3, cfg.getInt("settings.spawn-radius", 25));
            int inferredInner = Math.max(3, oldOuter - 10);

            spawnOuterRadius = oldOuter;
            spawnInnerRadius = Math.min(inferredInner, oldOuter - 1);

            cfg.set("settings.spawn-inner-radius", spawnInnerRadius);
            cfg.set("settings.spawn-outer-radius", spawnOuterRadius);
            try { cfg.save(file); } catch (IOException ignored) {}
        } else {
            spawnInnerRadius = Math.max(3, cfg.getInt("settings.spawn-inner-radius", 20));
            spawnOuterRadius = cfg.getInt("settings.spawn-outer-radius", 35);
            spawnOuterRadius = Math.max(spawnInnerRadius + 1, spawnOuterRadius);
        }

        hillRadius = Math.max(1, cfg.getInt("settings.hill-radius", 2));
        winSeconds = Math.max(30, cfg.getInt("settings.win-seconds", 300));

        clearInventory = cfg.getBoolean("kit.clear-inventory", true);

        helmet = matItem(cfg.getString("kit.armor.helmet"));
        chest = matItem(cfg.getString("kit.armor.chestplate"));
        legs = matItem(cfg.getString("kit.armor.leggings"));
        boots = matItem(cfg.getString("kit.armor.boots"));

        items = new ArrayList<>();
        for (Map<?, ?> map : cfg.getMapList("kit.items")) {
            ItemStack it = parseItem(map);
            if (it != null) items.add(it);
        }

        effects = new ArrayList<>();
        for (Map<?, ?> map : cfg.getMapList("kit.effects")) {
            PotionEffect pe = parseEffect(map);
            if (pe != null) effects.add(pe);
        }
    }

    public int getSpawnInnerRadius() { return spawnInnerRadius; }
    public int getSpawnOuterRadius() { return spawnOuterRadius; }
    public int getSpawnRadius() { return spawnOuterRadius; } // compatibility

    public int getHillRadius() { return hillRadius; }
    public int getWinSeconds() { return winSeconds; }

    public boolean isClearInventory() { return clearInventory; }

    public ItemStack getHelmet() { return cloneOrNull(helmet); }
    public ItemStack getChest() { return cloneOrNull(chest); }
    public ItemStack getLegs() { return cloneOrNull(legs); }
    public ItemStack getBoots() { return cloneOrNull(boots); }

    public List<ItemStack> getItems() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack it : items) out.add(it.clone());
        return out;
    }

    public List<PotionEffect> getEffects() {
        return new ArrayList<>(effects);
    }

    private ItemStack matItem(String matName) {
        if (matName == null) return null;
        Material m = Material.matchMaterial(matName);
        return m == null ? null : new ItemStack(m, 1);
    }

    private ItemStack cloneOrNull(ItemStack s) {
        return s == null ? null : s.clone();
    }

    private ItemStack parseItem(Map<?, ?> map) {
        Object mObj = map.get("material");
        if (!(mObj instanceof String matName)) return null;

        Material mat = Material.matchMaterial(matName);
        if (mat == null) return null;

        int amount = 1;
        Object aObj = map.get("amount");
        if (aObj instanceof Number n) amount = Math.max(1, n.intValue());

        ItemStack stack = new ItemStack(mat, amount);

        Object enchObj = map.get("enchants");
        if (enchObj instanceof Map<?, ?> enchMap) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                for (Map.Entry<?, ?> e : enchMap.entrySet()) {
                    if (!(e.getKey() instanceof String enchName)) continue;

                    int lvl = 1;
                    if (e.getValue() instanceof Number n) lvl = Math.max(1, n.intValue());

                    Enchantment ench = Enchantment.getByName(enchName.toUpperCase(Locale.ROOT));
                    if (ench != null) meta.addEnchant(ench, lvl, true);
                }
                stack.setItemMeta(meta);
            }
        }

        return stack;
    }

    private PotionEffect parseEffect(Map<?, ?> map) {
        Object tObj = map.get("type");
        if (!(tObj instanceof String typeName)) return null;

        PotionEffectType type = PotionEffectType.getByName(typeName.toUpperCase(Locale.ROOT));
        if (type == null) return null;

        int seconds = 5;
        Object sObj = map.get("seconds");
        if (sObj instanceof Number n) seconds = Math.max(1, n.intValue());

        int amp = 0;
        Object aObj = map.get("amplifier");
        if (aObj instanceof Number n) amp = Math.max(0, n.intValue());

        return new PotionEffect(type, seconds * 20, amp, true, false, true);
    }
}
