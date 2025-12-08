package me.Evil.soulSMP.shop;

import me.Evil.soulSMP.SoulSMP;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class DimensionBannerShopSettings {

    private final SoulSMP plugin;
    private final List<BannerShopItem> items = new ArrayList<>();
    private final Map<Integer, BannerShopItem> itemsBySlot = new HashMap<>();
    private Material fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
    private String fillerName = ChatColor.GRAY + " ";

    public DimensionBannerShopSettings(SoulSMP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        items.clear();
        itemsBySlot.clear();

        File file = new File(plugin.getDataFolder(), "dimension-shop.yml");
        if (!file.exists()) {
            plugin.saveResource("dimension-shop.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);


        ConfigurationSection root = cfg.getConfigurationSection("dimension-shop");
        if (root == null) {
            plugin.getLogger().warning("[DimensionShop] 'dimension-shop' section missing.");
            return;
        }

        ConfigurationSection fillerSec = root.getConfigurationSection("filler");
        if (fillerSec != null) {
            String matName = fillerSec.getString("material", "GRAY_STAINED_GLASS_PANE");
            fillerMaterial = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
            if (fillerMaterial == null) fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
            fillerName = ChatColor.translateAlternateColorCodes('&',
                    fillerSec.getString("name", " "));
        }

        ConfigurationSection itemsSec = root.getConfigurationSection("items");
        if (itemsSec == null) {
            plugin.getLogger().warning("[DimensionShop] No 'items' section.");
            return;
        }

        for (String id : itemsSec.getKeys(false)) {
            ConfigurationSection sec = itemsSec.getConfigurationSection(id);
            if (sec == null) continue;

            String typeStr = sec.getString("type", "UNKNOWN").toUpperCase(Locale.ROOT);
            BannerShopItem.ShopItemType type;
            try {
                type = BannerShopItem.ShopItemType.valueOf(typeStr);
            } catch (IllegalArgumentException ex) {
                type = BannerShopItem.ShopItemType.UNKNOWN;
            }

            int slot = sec.getInt("slot", -1);
            String matName = sec.getString("material", "STONE");
            Material mat = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
            if (mat == null) mat = Material.STONE;

            String displayName = ChatColor.translateAlternateColorCodes('&',
                    sec.getString("display-name", "&fItem"));
            List<String> loreRaw = sec.getStringList("lore");
            List<String> lore = new ArrayList<>();
            for (String line : loreRaw) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            int maxRadius = sec.getInt("max-radius", 0); // unused here but required by ctor
            int baseCost = sec.getInt("base-cost", 0);
            double multiplier = sec.getDouble("cost-multiplier", 1.0);
            String dimensionKey = sec.getString("dimension", null);

            BannerShopItem item = new BannerShopItem(
                    id,
                    type,
                    slot,
                    mat,
                    displayName,
                    lore,
                    maxRadius,
                    baseCost,
                    multiplier,
                    dimensionKey
            );

            items.add(item);
            if (slot >= 0) {
                itemsBySlot.put(slot, item);
            }
        }

        plugin.getLogger().info("[DimensionShop] Loaded " + items.size() + " item(s).");
    }

    public Material getFillerMaterial() {
        return fillerMaterial;
    }

    public String getFillerName() {
        return fillerName;
    }

    public List<BannerShopItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public BannerShopItem getItemBySlot(int slot) {
        return itemsBySlot.get(slot);
    }
}
