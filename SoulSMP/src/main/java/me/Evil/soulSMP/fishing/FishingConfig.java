package me.Evil.soulSMP.fishing;

import me.Evil.soulSMP.fishing.FishType;
import me.Evil.soulSMP.fishing.FishingRarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public class FishingConfig {

    public boolean enabled;
    public boolean overrideVanilla;

    public Map<String, FishingRarity> rarities = new HashMap<>();
    public Map<String, FishType> fishTypes = new HashMap<>();

    public List<String> loreLines;

    public FishingConfig(Plugin plugin) {

        File file = new File(plugin.getDataFolder(), "fishing.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        enabled = cfg.getBoolean("enabled", true);
        overrideVanilla = cfg.getBoolean("override-vanilla-fishing", true);

        // ---- Load Rarities ----
        ConfigurationSection rSec = cfg.getConfigurationSection("rarities");
        for (String id : rSec.getKeys(false)) {
            ConfigurationSection rs = rSec.getConfigurationSection(id);
            rarities.put(id, new FishingRarity(
                    id,
                    rs.getString("display-name"),
                    rs.getString("color"),
                    rs.getDouble("weight"),
                    rs.getDouble("score-multiplier")
            ));
        }

        // ---- Load Fish Types ----
        ConfigurationSection tSec = cfg.getConfigurationSection("fish-types");
        for (String id : tSec.getKeys(false)) {
            ConfigurationSection fs = tSec.getConfigurationSection(id);

            FishType type = new FishType(
                    id,
                    Material.matchMaterial(fs.getString("material", "COD")),
                    fs.getString("display-name-format"),
                    fs.getInt("base-score")
            );

            // Allowed rarities
            for (String r : fs.getStringList("allowed-rarities")) {
                if (rarities.containsKey(r)) type.getAllowedRarities().add(r);
            }

            // Weight ranges
            ConfigurationSection wr = fs.getConfigurationSection("weight-ranges");
            for (String rarity : wr.getKeys(false)) {
                ConfigurationSection rs = wr.getConfigurationSection(rarity);
                type.getRanges().put(rarity,
                        new FishType.WeightRange(
                                rs.getDouble("min"),
                                rs.getDouble("max")
                        )
                );
            }

            fishTypes.put(id, type);
        }

        // ---- Selection Weights ----
        ConfigurationSection swSec = cfg.getConfigurationSection("selection-weights");
        if (swSec != null) {
            for (String typeId : swSec.getKeys(false)) {
                FishType type = fishTypes.get(typeId);
                if (type == null) continue;

                ConfigurationSection entry = swSec.getConfigurationSection(typeId);
                for (String rarityId : entry.getKeys(false)) {
                    type.getSelectionWeights().put(rarityId, entry.getDouble(rarityId));
                }
            }
        }

        loreLines = cfg.getStringList("display.lore");
    }
}
