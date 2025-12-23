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

    private File journalFile;
    private FileConfiguration journalCfg;

    private File dataFile;
    private FileConfiguration dataCfg;

    // GUI pageNumber (1..N) -> entries on that GUI page
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
                // If you ship journal.yml as a resource, this will work.
                plugin.saveResource("journal.yml", false);
            } catch (IllegalArgumentException ignored) {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    journalFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create journal.yml: " + e.getMessage());
                }
            }
        }

        journalCfg = YamlConfiguration.loadConfiguration(journalFile);

        // Determine page count.
        int explicitCount = journalCfg.getInt("journal.pages.count", -1);

        // We'll load pages until no entries section exists for both schemas.
        // If explicitCount is provided, use it; otherwise auto-detect.
        int pageCount;
        if (explicitCount > 0) {
            pageCount = explicitCount;
        } else {
            pageCount = detectPageCount();
        }

        if (pageCount < 1) pageCount = 1;

        // Load pages 1..pageCount
        for (int page = 1; page <= pageCount; page++) {
            ConfigurationSection entries = getEntriesSectionForPage(page);
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

            if (!map.isEmpty()) {
                pageEntries.put(page, map);
            }
        }

        plugin.getLogger().info("Journal loaded: pages=" + pageCount + ", pagesWithEntries=" + pageEntries.size());
    }
    /**
     * Completion percent based on ALL possible journal combinations (RARITY:TYPE),
     * as defined in journal.yml (pageEntries).
     *
     * Example: COD with 7 rarities = 7 separate combinations.
     * Returns 0..100.
     */
    public double getCompletionPercent(UUID playerId) {
        if (playerId == null) return 0.0;

        int totalCombos = getTotalCombinationCount();
        if (totalCombos <= 0) return 0.0;

        int discoveredCombos = getDiscoveredCombinationCount(playerId);

        if (discoveredCombos < 0) discoveredCombos = 0;
        if (discoveredCombos > totalCombos) discoveredCombos = totalCombos;

        return (discoveredCombos / (double) totalCombos) * 100.0;
    }

    /**
     * Total number of possible combinations (RARITY:TYPE) defined across all pages in journal.yml.
     */
    public int getTotalCombinationCount() {
        int total = 0;
        for (Map<String, EntryDef> map : pageEntries.values()) {
            total += map.size();
        }
        return total;
    }

    /**
     * Number of combinations (RARITY:TYPE) the player has discovered:
     * i.e., has an entry saved under players.<uuid>.<RARITY:TYPE> in fishing_journal_data.yml,
     * AND that key exists in journal.yml (prevents stray keys from counting).
     */
    public int getDiscoveredCombinationCount(UUID playerId) {
        if (playerId == null) return 0;

        // All valid RARITY:TYPE combos from the journal definition
        Set<String> validCombos = new HashSet<>();
        for (Map<String, EntryDef> map : pageEntries.values()) {
            validCombos.addAll(map.keySet());
        }

        ConfigurationSection playerSec = dataCfg.getConfigurationSection("players." + playerId);
        if (playerSec == null) return 0;

        int count = 0;
        for (String rawKey : playerSec.getKeys(false)) {
            String key = normalizeKey(rawKey);
            if (key == null) continue;

            if (!validCombos.contains(key)) continue;

            // Your updateBestWeight stores a double, so this is the normal case
            double weight = playerSec.getDouble(rawKey, -1);
            if (weight >= 0) count++;
        }

        return count;
    }


    /**
     * Supports BOTH schemas:
     *  - old: journal.pages.page-<n>.entries
     *  - new: journal.pages.generated.page-<n>.entries
     */
    private ConfigurationSection getEntriesSectionForPage(int page) {
        // Old schema
        String oldPath = "journal.pages.page-" + page + ".entries";
        ConfigurationSection oldSec = journalCfg.getConfigurationSection(oldPath);
        if (oldSec != null) return oldSec;

        // New schema (generated)
        String genPath = "journal.pages.generated.page-" + page + ".entries";
        return journalCfg.getConfigurationSection(genPath);
    }

    private int detectPageCount() {
        int maxPage = 0;

        // Old schema: journal.pages.page-#
        ConfigurationSection pages = journalCfg.getConfigurationSection("journal.pages");
        if (pages != null) {
            for (String k : pages.getKeys(false)) {
                if (k == null) continue;
                if (k.toLowerCase(Locale.ROOT).startsWith("page-")) {
                    int n = parsePageNumber(k);
                    if (n > maxPage) maxPage = n;
                }
            }
        }

        // New schema: journal.pages.generated.page-#
        ConfigurationSection gen = journalCfg.getConfigurationSection("journal.pages.generated");
        if (gen != null) {
            for (String k : gen.getKeys(false)) {
                if (k == null) continue;
                if (k.toLowerCase(Locale.ROOT).startsWith("page-")) {
                    int n = parsePageNumber(k);
                    if (n > maxPage) maxPage = n;
                }
            }
        }

        return Math.max(1, maxPage);
    }

    private int parsePageNumber(String key) {
        // "page-12" -> 12
        try {
            String[] parts = key.split("-");
            return Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void loadDataFile() {
        dataFile = new File(plugin.getDataFolder(), "fishing_journal_data.yml");
        if (!dataFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create fishing_journal_data.yml: " + e.getMessage());
            }
        }
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);
    }

    public FileConfiguration getJournalCfg() {
        return journalCfg;
    }

    public int getPageCount() {
        int explicit = journalCfg.getInt("journal.pages.count", -1);
        return explicit > 0 ? explicit : detectPageCount();
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

    public double getBestWeight(UUID playerId, String entryKey) {
        entryKey = normalizeKey(entryKey);
        String path = "players." + playerId + "." + entryKey;
        if (!dataCfg.contains(path)) return -1;
        return dataCfg.getDouble(path, -1);
    }

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
            plugin.getLogger().severe("Could not save fishing_journal_data.yml: " + e.getMessage());
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
