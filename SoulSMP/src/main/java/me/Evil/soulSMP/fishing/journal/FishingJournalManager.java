package me.Evil.soulSMP.fishing.journal;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FishingJournalManager {

    private final Plugin plugin;

    // journal.yml
    private File journalFile;
    private FileConfiguration journalCfg;

    // fishing_journal_data.yml
    private File dataFile;
    private FileConfiguration dataCfg;

    // page -> (entryKey -> EntryDef)
    private final Map<Integer, Map<String, EntryDef>> pageEntries = new LinkedHashMap<>();

    public FishingJournalManager(Plugin plugin) {
        this.plugin = plugin;
        loadJournalConfig();
        loadDataFile();
    }

    public void reload() {
        pageEntries.clear();
        loadJournalConfig();
        loadDataFile();
    }

    private void loadJournalConfig() {
        journalFile = new File(plugin.getDataFolder(), "journal.yml");
        if (!journalFile.exists()) {
            try {
                plugin.saveResource("journal.yml", false);
            } catch (IllegalArgumentException ex) {
                try { //noinspection ResultOfMethodCallIgnored
                    journalFile.createNewFile();
                } catch (IOException ignored) {}
            }
        }

        journalCfg = YamlConfiguration.loadConfiguration(journalFile);

        int pageCount = journalCfg.getInt("journal.pages.count", 1);
        if (pageCount < 1) pageCount = 1;

        for (int page = 1; page <= pageCount; page++) {
            String path = "journal.pages.page-" + page + ".entries";
            ConfigurationSection entries = journalCfg.getConfigurationSection(path);
            if (entries == null) continue;

            Map<String, EntryDef> map = new LinkedHashMap<>();
            for (String rawKey : entries.getKeys(false)) {
                ConfigurationSection sec = entries.getConfigurationSection(rawKey);
                if (sec == null) continue;

                String key = normalizeKey(rawKey);
                int slot = sec.getInt("slot", -1);
                double chance = sec.getDouble("chance", 0.0);
                if (slot < 0) continue;

                map.put(key, new EntryDef(key, slot, chance));
            }
            pageEntries.put(page, map);
        }

        plugin.getLogger().info("[SoulSMP] Journal loaded: pages=" + pageEntries.size());
    }

    private void loadDataFile() {
        dataFile = new File(plugin.getDataFolder(), "fishing_journal_data.yml");
        if (!dataFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[SoulSMP] Could not create fishing_journal_data.yml: " + e.getMessage());
            }
        }
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);
    }

    public FileConfiguration getJournalCfg() {
        return journalCfg;
    }

    public int getPageCount() {
        return journalCfg.getInt("journal.pages.count", 1);
    }

    public Map<String, EntryDef> getEntriesForPage(int page) {
        return pageEntries.getOrDefault(page, Collections.emptyMap());
    }

    public EntryDef findEntry(String entryKey) {
        entryKey = normalizeKey(entryKey);
        for (Map<String, EntryDef> map : pageEntries.values()) {
            EntryDef def = map.get(entryKey);
            if (def != null) return def;
        }
        return null;
    }

    /** Returns best weight for this entry, or -1 if undiscovered. */
    public double getBestWeight(UUID playerId, String entryKey) {
        entryKey = normalizeKey(entryKey);
        String path = "players." + playerId + "." + entryKey;
        if (!dataCfg.contains(path)) return -1;
        return dataCfg.getDouble(path, -1);
    }

    /**
     * Updates best weight if newWeight is higher.
     * @return true if the entry is new or improved; false if not improved.
     */
    public boolean updateBestWeight(UUID playerId, String entryKey, double newWeight) {
        entryKey = normalizeKey(entryKey);
        double current = getBestWeight(playerId, entryKey);
        if (current < 0 || newWeight > current) {
            dataCfg.set("players." + playerId + "." + entryKey, newWeight);
            saveData();
            return true;
        }
        return false;
    }

    private void saveData() {
        try {
            dataCfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[SoulSMP] Could not save fishing_journal_data.yml: " + e.getMessage());
        }
    }

    private static String normalizeKey(String s) {
        if (s == null) return null;
        return s.trim().toUpperCase(Locale.ROOT);
    }

    public static class EntryDef {
        public final String key;   // RARITY:TYPE
        public final int slot;
        public final double chance;

        public EntryDef(String key, int slot, double chance) {
            this.key = key;
            this.slot = slot;
            this.chance = chance;
        }
    }
}
