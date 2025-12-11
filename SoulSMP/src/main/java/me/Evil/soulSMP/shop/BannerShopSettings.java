package me.Evil.soulSMP.shop;

import me.Evil.soulSMP.shop.BannerShopItem.ShopItemType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public class BannerShopSettings {

    private final Plugin plugin;

    // Filler background item
    private final Material fillerMaterial;
    private final String fillerName;

    // Items by slot
    private final Map<Integer, BannerShopItem> itemsBySlot = new HashMap<>();

    public BannerShopSettings(Plugin plugin) {
        this.plugin = plugin;

        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) {
            plugin.saveResource("shop.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Filler
        ConfigurationSection fillerSec = cfg.getConfigurationSection("banner-shop.filler");
        if (fillerSec == null) {
            this.fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
            this.fillerName = " ";
        } else {
            String matName = fillerSec.getString("material", "GRAY_STAINED_GLASS_PANE");
            Material mat = Material.matchMaterial(matName);
            if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;
            this.fillerMaterial = mat;
            this.fillerName = ChatColor.translateAlternateColorCodes('&',
                    fillerSec.getString("name", " "));
        }

        // Items
        ConfigurationSection itemsSec = cfg.getConfigurationSection("banner-shop.items");
        if (itemsSec == null) {
            plugin.getLogger().warning("[SoulSMP] banner-shop.items missing in shop.yml");
        } else {
            for (String id : itemsSec.getKeys(false)) {
                ConfigurationSection sec = itemsSec.getConfigurationSection(id);
                if (sec == null) continue;

                String typeStr = sec.getString("type", "UNKNOWN").toUpperCase(Locale.ROOT);
                ShopItemType type;
                try {
                    type = ShopItemType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    type = ShopItemType.UNKNOWN;
                }

                int slot = sec.getInt("slot", -1);

                String materialName = sec.getString("material", "STONE");
                Material mat = Material.matchMaterial(materialName);
                if (mat == null) mat = Material.STONE;

                String displayName = ChatColor.translateAlternateColorCodes('&',
                        sec.getString("display-name", id));

                List<String> loreRaw = sec.getStringList("lore");
                List<String> lore = new ArrayList<>();
                for (String line : loreRaw) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }

                int maxRadius = sec.getInt("max-radius", 5);
                int baseCost = sec.getInt("base-cost", 5);
                double costMultiplier = sec.getDouble("cost-multiplier", 2.0);
                String dimensionKey = sec.getString("dimension", null);
                boolean backButton = sec.getBoolean("back-button", false);



                BannerShopItem item = new BannerShopItem(
                        type,
                        slot,
                        mat,
                        displayName,
                        lore,
                        maxRadius,
                        baseCost,
                        costMultiplier,
                        dimensionKey,
                        backButton
                );


                itemsBySlot.put(slot, item);
            }
        }

        plugin.getLogger().info("[SoulSMP] Loaded " + itemsBySlot.size() + " banner shop item(s).");
    }

    public Material getFillerMaterial() {
        return fillerMaterial;
    }

    public String getFillerName() {
        return fillerName;
    }

    public Collection<BannerShopItem> getItems() {
        return Collections.unmodifiableCollection(itemsBySlot.values());
    }

    public BannerShopItem getItemBySlot(int slot) {
        return itemsBySlot.get(slot);
    }
}
