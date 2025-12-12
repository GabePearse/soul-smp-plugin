package me.Evil.soulSMP.fishing.journal;

import me.Evil.soulSMP.fishing.FishType;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.FishingRarity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class JournalAutoGeneratorPaged {

    public static void generateIfMissing(Plugin plugin, FishingConfig fishingConfig) {
        File file = new File(plugin.getDataFolder(), "journal.yml");
        if (file.exists()) return;

        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration yml = new YamlConfiguration();

        // Base settings
        yml.set("journal.title", "&bFishing Journal");
        yml.set("journal.size", 54);

        yml.set("journal.filler.material", "GRAY_STAINED_GLASS_PANE");
        yml.set("journal.filler.name", " ");

        int depositSlot = 49;
        yml.set("journal.deposit-slot", depositSlot);

        yml.set("journal.deposit-item.material", "HOPPER");
        yml.set("journal.deposit-item.name", "&eTurn In Fish");
        yml.set("journal.deposit-item.lore", List.of(
                "&7Place a Soul Fish here to",
                "&7record it in your journal.",
                "",
                "&8(Consumes 1 fish)"
        ));

        yml.set("journal.navigation.prev.slot", 45);
        yml.set("journal.navigation.prev.material", "ARROW");
        yml.set("journal.navigation.prev.name", "&cPrevious Page");

        yml.set("journal.navigation.next.slot", 53);
        yml.set("journal.navigation.next.material", "ARROW");
        yml.set("journal.navigation.next.name", "&aNext Page");

        yml.set("journal.undiscovered.material", "BLACK_STAINED_GLASS_PANE");
        yml.set("journal.undiscovered.name", "&8???");

        // Slot pool per page
        List<Integer> usableSlots = buildUsableSlots(54, depositSlot);
        int perPage = usableSlots.size();

        // rarity total
        double rarityTotal = fishingConfig.rarities.values().stream()
                .mapToDouble(FishingRarity::getWeight).sum();

        // stable order
        List<String> rarityOrder = new ArrayList<>(fishingConfig.rarities.keySet());
        List<String> typeOrder = new ArrayList<>(fishingConfig.fishTypes.keySet());
        Collections.sort(typeOrder);

        List<EntryInfo> allEntries = new ArrayList<>();

        for (String rarityId : rarityOrder) {
            FishingRarity rarity = fishingConfig.rarities.get(rarityId);
            if (rarity == null) continue;
            double rarityChance = (rarityTotal <= 0) ? 0 : (rarity.getWeight() / rarityTotal);

            List<FishType> candidates = new ArrayList<>();
            for (String typeId : typeOrder) {
                FishType t = fishingConfig.fishTypes.get(typeId);
                if (t == null) continue;
                if (t.getAllowedRarities().contains(rarityId)) candidates.add(t);
            }
            if (candidates.isEmpty()) continue;

            boolean hasOverrides = candidates.stream().anyMatch(t -> t.getSelectionWeights().containsKey(rarityId));
            double typeTotal = 0.0;
            if (hasOverrides) {
                for (FishType t : candidates) {
                    typeTotal += t.getSelectionWeights().getOrDefault(rarityId, 1.0);
                }
            }

            for (FishType t : candidates) {
                double typeChance;
                if (hasOverrides) {
                    double w = t.getSelectionWeights().getOrDefault(rarityId, 1.0);
                    typeChance = (typeTotal <= 0) ? 0 : (w / typeTotal);
                } else {
                    typeChance = 1.0 / candidates.size();
                }

                double finalChance = rarityChance * typeChance;

                allEntries.add(new EntryInfo(rarityId, t.getId(), finalChance));
            }
        }

        int pageCount = (int) Math.ceil(allEntries.size() / (double) perPage);
        if (pageCount < 1) pageCount = 1;

        yml.set("journal.pages.count", pageCount);

        int index = 0;
        for (int page = 1; page <= pageCount; page++) {
            String pagePath = "journal.pages.page-" + page;
            yml.set(pagePath + ".title-suffix", " &7(" + page + "/" + pageCount + ")");

            for (int i = 0; i < perPage && index < allEntries.size(); i++, index++) {
                EntryInfo info = allEntries.get(index);
                int slot = usableSlots.get(i);

                String key = info.rarityId + ":" + info.typeId;

                yml.set(pagePath + ".entries." + key + ".slot", slot);
                yml.set(pagePath + ".entries." + key + ".chance", info.chance);
            }
        }

        plugin.getLogger().info("[SoulSMP] Auto-generated journal.yml with " + allEntries.size() + " entries across " + pageCount + " page(s).");

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[SoulSMP] Could not save auto-generated journal.yml: " + e.getMessage());
        }
    }

    private static class EntryInfo {
        final String rarityId;
        final String typeId;
        final double chance;
        EntryInfo(String rarityId, String typeId, double chance) {
            this.rarityId = rarityId;
            this.typeId = typeId;
            this.chance = chance;
        }
    }

    private static List<Integer> buildUsableSlots(int size, int depositSlot) {
        Set<Integer> forbidden = new HashSet<>();
        int rows = size / 9;

        // top row
        for (int i = 0; i < 9; i++) forbidden.add(i);

        // bottom row
        int bottomStart = (rows - 1) * 9;
        for (int i = bottomStart; i < bottomStart + 9; i++) forbidden.add(i);

        // left/right borders
        for (int r = 0; r < rows; r++) {
            forbidden.add(r * 9);
            forbidden.add(r * 9 + 8);
        }

        // deposit slot
        forbidden.add(depositSlot);

        List<Integer> usable = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            if (!forbidden.contains(slot)) usable.add(slot);
        }
        return usable;
    }
}
