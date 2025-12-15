package me.Evil.soulSMP.store;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public class StoreSettings {

    private final Plugin plugin;

    private String mainTitle;
    private int mainSize;
    private Material mainFillerMat;
    private String mainFillerName;

    private String categoryTitle;
    private int categorySize;
    private Material categoryFillerMat;
    private String categoryFillerName;
    private int nextSlot, prevSlot, backSlot;

    private final Map<String, StoreCategory> categories = new LinkedHashMap<>();
    private final Map<String, Map<String, StoreItem>> buyItemsByCategory = new HashMap<>();

    public StoreSettings(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "store.yml");
        if (!file.exists()) plugin.saveResource("store.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // main
        mainTitle = cfg.getString("main.title", "&bPlayer Store");
        mainSize = cfg.getInt("main.size", 54);
        mainFillerMat = Material.matchMaterial(cfg.getString("main.filler.material", "BLACK_STAINED_GLASS_PANE"));
        mainFillerName = cfg.getString("main.filler.name", " ");

        // categories
        categories.clear();
        ConfigurationSection catSec = cfg.getConfigurationSection("main.categories");
        if (catSec != null) {
            for (String id : catSec.getKeys(false)) {
                int slot = catSec.getInt(id + ".slot", 0);
                String matStr = catSec.getString(id + ".icon.material", "STONE");
                Material mat = Material.matchMaterial(matStr);
                String name = catSec.getString(id + ".icon.name", id);
                List<String> lore = catSec.getStringList(id + ".icon.lore");
                categories.put(id, new StoreCategory(id, slot, mat == null ? Material.STONE : mat, name, lore));
            }
        }

        // category gui
        categoryTitle = cfg.getString("category-gui.title", "&bStore: {category}");
        categorySize = cfg.getInt("category-gui.size", 54);
        categoryFillerMat = Material.matchMaterial(cfg.getString("category-gui.filler.material", "GRAY_STAINED_GLASS_PANE"));
        categoryFillerName = cfg.getString("category-gui.filler.name", " ");
        nextSlot = cfg.getInt("category-gui.next-page-slot", 53);
        prevSlot = cfg.getInt("category-gui.prev-page-slot", 45);
        backSlot = cfg.getInt("category-gui.back-slot", 49);

        // buy items
        buyItemsByCategory.clear();
        ConfigurationSection buySec = cfg.getConfigurationSection("buy");
        if (buySec != null) {
            for (String catId : buySec.getKeys(false)) {
                ConfigurationSection itemsSec = buySec.getConfigurationSection(catId + ".items");
                if (itemsSec == null) continue;

                Map<String, StoreItem> map = new LinkedHashMap<>();
                for (String itemId : itemsSec.getKeys(false)) {
                    ConfigurationSection s = itemsSec.getConfigurationSection(itemId);
                    if (s == null) continue;
                    StoreItem item = StoreItem.fromSection(itemId, s);
                    map.put(itemId, item);
                }
                buyItemsByCategory.put(catId, map);
            }
        }
    }

    public String getMainTitle() { return mainTitle; }
    public int getMainSize() { return mainSize; }
    public Material getMainFillerMat() { return mainFillerMat == null ? Material.BLACK_STAINED_GLASS_PANE : mainFillerMat; }
    public String getMainFillerName() { return mainFillerName; }

    public String getCategoryTitle() { return categoryTitle; }
    public int getCategorySize() { return categorySize; }
    public Material getCategoryFillerMat() { return categoryFillerMat == null ? Material.GRAY_STAINED_GLASS_PANE : categoryFillerMat; }
    public String getCategoryFillerName() { return categoryFillerName; }
    public int getNextSlot() { return nextSlot; }
    public int getPrevSlot() { return prevSlot; }
    public int getBackSlot() { return backSlot; }

    public Map<String, StoreCategory> getCategories() { return categories; }
    public Map<String, StoreItem> getBuyItems(String categoryId) {
        return buyItemsByCategory.getOrDefault(categoryId, Collections.emptyMap());
    }
}
