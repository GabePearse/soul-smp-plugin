package me.Evil.soulSMP.upgrades;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public class BeaconEffectSettings {

    private final Plugin plugin;
    private final Map<String, BeaconEffectDefinition> effects = new HashMap<>();

    public BeaconEffectSettings(Plugin plugin) {
        this.plugin = plugin;

        File file = new File(plugin.getDataFolder(), "effects.yml");
        if (!file.exists()) {
            plugin.saveResource("effects.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        load(cfg);
    }

    private void load(FileConfiguration cfg) {
        ConfigurationSection sec = cfg.getConfigurationSection("effects");
        if (sec == null) {
            plugin.getLogger().warning("No effects defined in effects.yml!");
            return;
        }

        for (String id : sec.getKeys(false)) {
            ConfigurationSection eSec = sec.getConfigurationSection(id);
            if (eSec == null) continue;

            String type = eSec.getString("type", id).toUpperCase();
            int slot = eSec.getInt("slot");
            Material material = Material.matchMaterial(eSec.getString("material", "STONE"));
            String displayName = ChatColor.translateAlternateColorCodes('&',
                    eSec.getString("display-name", id));

            Map<Integer, BeaconEffectLevel> levelMap = new LinkedHashMap<>();

            ConfigurationSection levelSec = eSec.getConfigurationSection("levels");
            if (levelSec != null) {
                for (String lvlStr : levelSec.getKeys(false)) {
                    int lvl = Integer.parseInt(lvlStr);
                    int cost = levelSec.getInt(lvlStr + ".cost");

                    List<String> loreRaw = levelSec.getStringList(lvlStr + ".lore");
                    List<String> lore = new ArrayList<>();
                    for (String line : loreRaw) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', line));
                    }

                    levelMap.put(lvl, new BeaconEffectLevel(lvl, cost, lore));
                }
            }

            BeaconEffectDefinition def = new BeaconEffectDefinition(
                    id, type, slot, material, displayName, levelMap);

            effects.put(id, def);
        }

        plugin.getLogger().info("[Effects] Loaded " + effects.size() + " beacon effect(s).");
    }

    public BeaconEffectDefinition getEffect(String id) {
        return effects.get(id);
    }

    public Collection<BeaconEffectDefinition> getAllEffects() {
        return effects.values();
    }
}
