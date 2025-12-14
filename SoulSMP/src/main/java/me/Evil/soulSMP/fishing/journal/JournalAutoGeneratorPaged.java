package me.Evil.soulSMP.fishing.journal;

import me.Evil.soulSMP.fishing.FishType;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.FishingRarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class JournalAutoGeneratorPaged {

    private static final int GUI_SIZE = 54;
    private static final int COLS = 9;

    // Layout:
    // Row 0 = empty padding
    // Rows 1..4 = fish rows (4 types per page)
    // Row 5 = nav/deposit row
    private static final int TYPES_PER_PAGE = 4;  // rows 1..4
    private static final int FIRST_TYPE_ROW = 1;

    // Inner columns only (leave sides empty)
    private static final int FIRST_INNER_COL = 1;
    private static final int LAST_INNER_COL  = 7; // inclusive (7 columns total)

    private static final int DEPOSIT_SLOT = 49;

    public static void regenerate(Plugin plugin, FishingConfig fishingConfig) {
        if (fishingConfig == null) {
            plugin.getLogger().severe("[SoulSMP] FishingConfig is null — cannot regenerate journal.yml.");
            return;
        }

        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        File file = new File(plugin.getDataFolder(), "journal.yml");
        YamlConfiguration yml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();

        // ---- Base defaults (only set if missing) ----
        setIfMissing(yml, "journal.title", "&bFishing Journal");
        setIfMissing(yml, "journal.size", GUI_SIZE);

        setIfMissing(yml, "journal.filler.material", "GRAY_STAINED_GLASS_PANE");
        setIfMissing(yml, "journal.filler.name", " ");

        setIfMissing(yml, "journal.deposit-slot", DEPOSIT_SLOT);
        setIfMissing(yml, "journal.deposit-item.material", "HOPPER");
        setIfMissing(yml, "journal.deposit-item.name", "&eTurn In Fish");
        if (!yml.contains("journal.deposit-item.lore")) {
            yml.set("journal.deposit-item.lore", List.of(
                    "&7Place a Soul Fish here to",
                    "&7record it in your journal.",
                    "",
                    "&8(Consumes 1 fish)"
            ));
        }

        setIfMissing(yml, "journal.navigation.prev.slot", 45);
        setIfMissing(yml, "journal.navigation.prev.material", "ARROW");
        setIfMissing(yml, "journal.navigation.prev.name", "&cPrevious Page");

        setIfMissing(yml, "journal.navigation.next.slot", 53);
        setIfMissing(yml, "journal.navigation.next.material", "ARROW");
        setIfMissing(yml, "journal.navigation.next.name", "&aNext Page");

        // Undiscovered pane
        yml.set("journal.undiscovered.material", "GREEN_STAINED_GLASS_PANE");
        setIfMissing(yml, "journal.undiscovered.name", "&8???");

        // ---- Clear generated pages only (preserve custom pages) ----
        yml.set("journal.pages.generated", null);

        // ---- Precompute global rarity probabilities + order ----
        Map<String, Double> globalRarityProb = computeGlobalRarityProbabilities(fishingConfig);

        List<String> rarityOrder = new ArrayList<>(fishingConfig.rarities.keySet());
        rarityOrder.sort((a, b) -> Double.compare(
                globalRarityProb.getOrDefault(b, 0.0),
                globalRarityProb.getOrDefault(a, 0.0)
        ));

        // ---- Precompute type probabilities (based on fish chance) ----
        Map<String, Double> typeProb = computeTypeProbabilities(fishingConfig);

        // ---- Sort types by most common overall ----
        List<TypeChance> typesSorted = new ArrayList<>();
        for (FishType type : fishingConfig.fishTypes.values()) {
            if (type == null) continue;
            typesSorted.add(new TypeChance(type.getId(), typeProb.getOrDefault(type.getId(), 0.0)));
        }
        typesSorted.sort((a, b) -> Double.compare(b.chance, a.chance));

        // ---- Pages ----
        int generatedPageCount = (int) Math.ceil(typesSorted.size() / (double) TYPES_PER_PAGE);
        if (generatedPageCount < 1) generatedPageCount = 1;

        int customPageCount = getCustomPageCount(yml);
        int totalPages = generatedPageCount + customPageCount;

        yml.set("journal.pages.count", totalPages);

        // ---- Write generated pages ----
        int typeIndex = 0;
        for (int page = 1; page <= generatedPageCount; page++) {
            String pagePath = "journal.pages.generated.page-" + page;

            yml.set(pagePath + ".title-suffix", " &7(" + page + "/" + totalPages + ")");

            for (int rowOffset = 0; rowOffset < TYPES_PER_PAGE && typeIndex < typesSorted.size(); rowOffset++, typeIndex++) {
                int row = FIRST_TYPE_ROW + rowOffset; // rows 1..4
                String typeId = typesSorted.get(typeIndex).typeId;

                FishType type = fishingConfig.fishTypes.get(typeId);
                if (type == null) continue;

                // Per-type rarity probabilities: use per-fish rarity-weights if present,
                // otherwise fall back to global rarity probabilities.
                Map<String, Double> rarityGivenTypeProb = computeRarityGivenTypeProb(type, fishingConfig, globalRarityProb);

                // show rarities in global rarity order, but only if they exist in the computed map
                List<String> raritySortedForType = new ArrayList<>();
                for (String r : rarityOrder) {
                    if (rarityGivenTypeProb.containsKey(r)) raritySortedForType.add(r);
                }

                int col = FIRST_INNER_COL;
                for (String rarityId : raritySortedForType) {
                    if (col > LAST_INNER_COL) break;

                    String entryKey = rarityId.toUpperCase(Locale.ROOT) + ":" + typeId.toUpperCase(Locale.ROOT);
                    int slot = row * COLS + col;

                    double pType = typeProb.getOrDefault(typeId, 0.0);
                    double pRarityGivenType = rarityGivenTypeProb.getOrDefault(rarityId, 0.0);

                    double chance = pType * pRarityGivenType;

                    yml.set(pagePath + ".entries." + entryKey + ".slot", slot);
                    yml.set(pagePath + ".entries." + entryKey + ".chance", chance);

                    col++;
                }
            }
        }

        // Ensure custom page suffix exists (optional)
        for (int i = 1; i <= customPageCount; i++) {
            int pageNumber = generatedPageCount + i;
            String customPath = "journal.pages.custom.page-" + i;
            if (!yml.contains(customPath + ".title-suffix")) {
                yml.set(customPath + ".title-suffix", " &7(" + pageNumber + "/" + totalPages + ")");
            }
        }

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[SoulSMP] Could not save journal.yml: " + e.getMessage());
        }

        plugin.getLogger().info("[SoulSMP] Regenerated journal.yml (chance-based). Generated pages="
                + generatedPageCount + ", custom pages preserved=" + customPageCount + ".");
    }

    private static void setIfMissing(YamlConfiguration yml, String path, Object value) {
        if (!yml.contains(path)) yml.set(path, value);
    }

    private static int getCustomPageCount(YamlConfiguration yml) {
        ConfigurationSection sec = yml.getConfigurationSection("journal.pages.custom");
        if (sec == null) return 0;

        int count = 0;
        for (String key : sec.getKeys(false)) {
            if (key != null && key.toLowerCase(Locale.ROOT).startsWith("page-")) count++;
        }
        return count;
    }

    // Global rarity probabilities based on rarity.weight
    private static Map<String, Double> computeGlobalRarityProbabilities(FishingConfig fishingConfig) {
        Map<String, Double> out = new HashMap<>();
        double total = 0.0;

        for (FishingRarity r : fishingConfig.rarities.values()) {
            if (r == null) continue;
            total += Math.max(0.0, r.getWeight());
        }

        if (total <= 0.0) {
            int n = Math.max(1, fishingConfig.rarities.size());
            double p = 1.0 / n;
            for (String id : fishingConfig.rarities.keySet()) out.put(id, p);
            return out;
        }

        for (Map.Entry<String, FishingRarity> e : fishingConfig.rarities.entrySet()) {
            FishingRarity r = e.getValue();
            if (r == null) continue;
            out.put(e.getKey(), Math.max(0.0, r.getWeight()) / total);
        }
        return out;
    }

    // Type probabilities based on fish-types.<id>.chance
    private static Map<String, Double> computeTypeProbabilities(FishingConfig fishingConfig) {
        Map<String, Double> out = new HashMap<>();
        double total = 0.0;

        for (FishType t : fishingConfig.fishTypes.values()) {
            if (t == null) continue;
            total += Math.max(0.0, t.getChance());
        }

        if (total <= 0.0) {
            int n = Math.max(1, fishingConfig.fishTypes.size());
            double p = 1.0 / n;
            for (String id : fishingConfig.fishTypes.keySet()) out.put(id, p);
            return out;
        }

        for (Map.Entry<String, FishType> e : fishingConfig.fishTypes.entrySet()) {
            FishType t = e.getValue();
            if (t == null) continue;
            out.put(e.getKey(), Math.max(0.0, t.getChance()) / total);
        }
        return out;
    }

    // Rarity probabilities conditional on type:
    // - if type has rarity-weights, normalize those
    // - otherwise use global rarity probabilities
    private static Map<String, Double> computeRarityGivenTypeProb(
            FishType type,
            FishingConfig fishingConfig,
            Map<String, Double> globalRarityProb
    ) {
        Map<String, Double> perFish = type.getRarityWeights();
        if (perFish == null || perFish.isEmpty()) {
            return new HashMap<>(globalRarityProb);
        }

        double total = 0.0;
        for (Map.Entry<String, Double> e : perFish.entrySet()) {
            if (!fishingConfig.rarities.containsKey(e.getKey())) continue;
            total += Math.max(0.0, e.getValue());
        }

        Map<String, Double> out = new HashMap<>();
        if (total <= 0.0) {
            // fallback to global if config is bad
            out.putAll(globalRarityProb);
            return out;
        }

        for (Map.Entry<String, Double> e : perFish.entrySet()) {
            if (!fishingConfig.rarities.containsKey(e.getKey())) continue;
            out.put(e.getKey(), Math.max(0.0, e.getValue()) / total);
        }
        return out;
    }

    private static class TypeChance {
        final String typeId;
        final double chance;
        TypeChance(String typeId, double chance) {
            this.typeId = typeId;
            this.chance = chance;
        }
    }
}
