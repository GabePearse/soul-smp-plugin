package me.Evil.soulSMP.fishing;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FishingConfig {

    private final Plugin plugin;
    private final File file;

    public boolean enabled;
    public boolean overrideVanilla;

    public Map<String, FishingRarity> rarities = new HashMap<>();
    public Map<String, FishType> fishTypes = new HashMap<>();

    public List<String> loreLines;

    public FishingConfig(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fishing.yml");
        reload(); // load immediately
    }

    /**
     * Reload fishing.yml from disk and repopulate rarities + fish types.
     * Also saves fishing.yml from jar if it doesn't exist yet.
     */
    public void reload() {
        // Ensure data folder exists
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        // Ensure fishing.yml exists (copied from resources in your plugin jar)
        if (!file.exists()) {
            try {
                plugin.saveResource("fishing.yml", false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Missing fishing.yml in plugin jar resources. Create plugins/"
                        + plugin.getName() + "/fishing.yml manually.");
            }
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        enabled = cfg.getBoolean("enabled", true);
        overrideVanilla = cfg.getBoolean("override-vanilla-fishing", true);

        // reset
        rarities.clear();
        fishTypes.clear();

        // ---- Load Rarities ----
        ConfigurationSection rSec = cfg.getConfigurationSection("rarities");
        if (rSec != null) {
            for (String id : rSec.getKeys(false)) {
                ConfigurationSection rs = rSec.getConfigurationSection(id);
                if (rs == null) continue;

                ConfigurationSection wr = rs.getConfigurationSection("weight-range");
                double minW = (wr != null) ? wr.getDouble("min", 1.0) : rs.getDouble("min-weight", 1.0);
                double maxW = (wr != null) ? wr.getDouble("max", 1.0) : rs.getDouble("max-weight", 1.0);

                rarities.put(id, new FishingRarity(
                        id,
                        rs.getString("display-name", id),
                        rs.getString("color", "&7"),
                        rs.getDouble("weight", 1.0),
                        rs.getDouble("score-multiplier", 1.0),
                        minW,
                        maxW
                ));
            }
        }

        // ---- Load Fish Types ----
        ConfigurationSection tSec = cfg.getConfigurationSection("fish-types");
        if (tSec != null) {
            for (String id : tSec.getKeys(false)) {
                ConfigurationSection fs = tSec.getConfigurationSection(id);
                if (fs == null) continue;

                String matName = fs.getString("material", "COD");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.COD;

                FishType type = new FishType(
                        id,
                        mat,
                        fs.getString("display-name-format", "{rarity_color}{rarity_name} {type_name} &7({weight}lb)"),
                        fs.getInt("base-score", 10)
                );

                // global chance (how often this fish is picked vs others)
                type.setChance(fs.getDouble("chance", 1.0));

                // optional per-fish rarity weights (if absent, generator uses global rarity weights)
                ConfigurationSection rw = fs.getConfigurationSection("rarity-weights");
                if (rw != null) {
                    for (String rarityId : rw.getKeys(false)) {
                        type.getRarityWeights().put(rarityId, rw.getDouble(rarityId));
                    }
                }

                fishTypes.put(id, type);
            }
        }

        loreLines = cfg.getStringList("display.lore");

        plugin.getLogger().info("FishingConfig: loaded rarities=" + rarities.size()
                + ", fishTypes=" + fishTypes.size()
                + " from " + file.getName());
    }
}
