package me.Evil.soulSMP.fishing;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class FishingConfig {

    public boolean enabled;
    public boolean overrideVanilla;

    public Map<String, FishingRarity> rarities = new HashMap<>();
    public Map<String, FishType> fishTypes = new HashMap<>();

    public java.util.List<String> loreLines;

    public FishingConfig(Plugin plugin) {

        File file = new File(plugin.getDataFolder(), "fishing.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        enabled = cfg.getBoolean("enabled", true);
        overrideVanilla = cfg.getBoolean("override-vanilla-fishing", true);

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
                        rs.getString("display-name"),
                        rs.getString("color"),
                        rs.getDouble("weight"),
                        rs.getDouble("score-multiplier"),
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

                FishType type = new FishType(
                        id,
                        Material.matchMaterial(fs.getString("material", "COD")),
                        fs.getString("display-name-format"),
                        fs.getInt("base-score")
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
    }
}
