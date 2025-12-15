package me.Evil.soulSMP.fishing.journal;

import me.Evil.soulSMP.fishing.FishType;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.FishingRarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class FishingJournalGUI {

    private final FishingJournalManager manager;
    private final FishingConfig fishingConfig;

    public FishingJournalGUI(FishingJournalManager manager, FishingConfig fishingConfig) {
        this.manager = manager;
        this.fishingConfig = fishingConfig;
    }

    public Inventory createFor(Player player, int page) {
        FileConfiguration cfg = manager.getJournalCfg();

        int size = cfg.getInt("journal.size", 54);

        int pageCount = Math.max(1, manager.getPageCount());
        if (page < 1) page = 1;
        if (page > pageCount) page = pageCount;

        String baseTitle = cfg.getString("journal.title", "&bFishing Journal");
        String suffix = cfg.getString(
                "journal.pages.page-" + page + ".title-suffix",
                " &7(" + page + "/" + pageCount + ")"
        );
        String title = color(baseTitle + suffix);

        Inventory inv = Bukkit.createInventory(
                new FishingJournalHolder(player.getUniqueId(), page),
                size,
                title
        );

        // filler
        Material fillerMat = Material.matchMaterial(
                cfg.getString("journal.filler.material", "GRAY_STAINED_GLASS_PANE")
        );
        if (fillerMat == null) fillerMat = Material.GRAY_STAINED_GLASS_PANE;
        String fillerName = color(cfg.getString("journal.filler.name", " "));

        ItemStack filler = namedItem(fillerMat, fillerName, Collections.emptyList());
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        // deposit slot
        int depositSlot = cfg.getInt("journal.deposit-slot", 49);
        inv.setItem(depositSlot, buildDepositItem(cfg));

        // navigation buttons
        setNavButtons(inv, cfg, page, pageCount);

        UUID uuid = player.getUniqueId();

        // page entries
        for (FishingJournalManager.EntryDef def : manager.getEntriesForPage(page).values()) {
            String entryKey = def.key;

            // Parse rarity:type from key so we can apply "row discovery" logic.
            String[] parts = entryKey.split(":");
            String rarityId = parts.length > 0 ? parts[0].toUpperCase(Locale.ROOT) : "UNKNOWN";
            String typeId = parts.length > 1 ? parts[1].toUpperCase(Locale.ROOT) : "UNKNOWN";

            double best = manager.getBestWeight(uuid, entryKey);

            if (best < 0) {
                // If they've discovered ANY rarity for this fish type, show the type name for ALL entries in that type row.
                boolean typeDiscovered = hasDiscoveredType(uuid, typeId);
                inv.setItem(def.slot, buildUndiscoveredForEntry(cfg, rarityId, typeId, def.chance, typeDiscovered));
            } else {
                inv.setItem(def.slot, buildDiscoveredFish(entryKey, best, def.chance));
            }
        }

        return inv;
    }

    /**
     * Returns true if the player has discovered at least one fish for this type across any rarity.
     * This is what makes "SARDINE" appear on all undiscovered entries in the sardine row once any sardine is discovered.
     */
    private boolean hasDiscoveredType(UUID uuid, String typeId) {
        if (typeId == null || typeId.isEmpty()) return false;

        for (String rarityId : fishingConfig.rarities.keySet()) {
            String key = rarityId.toUpperCase(Locale.ROOT) + ":" + typeId.toUpperCase(Locale.ROOT);
            if (manager.getBestWeight(uuid, key) >= 0) return true;
        }
        return false;
    }

    private void setNavButtons(Inventory inv, FileConfiguration cfg, int page, int pageCount) {
        int prevSlot = cfg.getInt("journal.navigation.prev.slot", 45);
        int nextSlot = cfg.getInt("journal.navigation.next.slot", 53);

        if (page > 1) {
            Material prevMat = Material.matchMaterial(cfg.getString("journal.navigation.prev.material", "ARROW"));
            if (prevMat == null) prevMat = Material.ARROW;
            String prevName = color(cfg.getString("journal.navigation.prev.name", "&cPrevious Page"));
            inv.setItem(prevSlot, namedItem(
                    prevMat,
                    prevName,
                    List.of(color("&7Go to page " + (page - 1)))
            ));
        } else {
            inv.setItem(prevSlot, namedItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    color("&8No previous"),
                    List.of()
            ));
        }

        if (page < pageCount) {
            Material nextMat = Material.matchMaterial(cfg.getString("journal.navigation.next.material", "ARROW"));
            if (nextMat == null) nextMat = Material.ARROW;
            String nextName = color(cfg.getString("journal.navigation.next.name", "&aNext Page"));
            inv.setItem(nextSlot, namedItem(
                    nextMat,
                    nextName,
                    List.of(color("&7Go to page " + (page + 1)))
            ));
        } else {
            inv.setItem(nextSlot, namedItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    color("&8No next"),
                    List.of()
            ));
        }
    }

    private ItemStack buildDepositItem(FileConfiguration cfg) {
        ConfigurationSection sec = cfg.getConfigurationSection("journal.deposit-item");
        if (sec == null) {
            return namedItem(
                    Material.HOPPER,
                    color("&eTurn In Fish"),
                    List.of(color("&7Place a Soul Fish here."))
            );
        }

        Material mat = Material.matchMaterial(sec.getString("material", "HOPPER"));
        if (mat == null) mat = Material.HOPPER;

        String name = color(sec.getString("name", "&eTurn In Fish"));
        List<String> lore = new ArrayList<>();
        for (String line : sec.getStringList("lore")) lore.add(color(line));

        return namedItem(mat, name, lore);
    }

    /**
     * Undiscovered entry:
     * - If typeDiscovered == false => show Type: ???
     * - If typeDiscovered == true  => show the real Type name (e.g., Sardine) for all entries in that row
     */
    private ItemStack buildUndiscoveredForEntry(FileConfiguration cfg,
                                                String rarityId,
                                                String typeId,
                                                double chance,
                                                boolean typeDiscovered) {

        Material mat = Material.matchMaterial(
                cfg.getString("journal.undiscovered.material", "RED_STAINED_GLASS_PANE")
        );
        if (mat == null) mat = Material.RED_STAINED_GLASS_PANE;

        String name = color(cfg.getString("journal.undiscovered.name", "&8???"));

        FishingRarity rarity = fishingConfig.rarities.get(rarityId);
        String rarityColor = rarity != null ? rarity.getColor() : "&7";
        String rarityPretty = rarity != null
                ? stripColor(color(rarity.getDisplayName()))
                : rarityId;

        String percent = String.format("%.4f", chance * 100.0);

        String typeLine = typeDiscovered
                ? color("&7Type: &f" + pretty(typeId))
                : color("&7Type: &8???");

        List<String> lore = new ArrayList<>();
        lore.add(typeLine);
        lore.add(color("&7Rarity: " + rarityColor + rarityPretty));
        lore.add(color("&7Chance: &f" + percent + "%"));
        lore.add("");
        lore.add(color("&8Turn in a fish to reveal."));

        return namedItem(mat, name, lore);
    }

    private ItemStack buildDiscoveredFish(String entryKey, double bestWeight, double chance) {
        String[] parts = entryKey.split(":");
        String rarityId = parts.length > 0 ? parts[0].toUpperCase(Locale.ROOT) : "COMMON";
        String typeId = parts.length > 1 ? parts[1].toUpperCase(Locale.ROOT) : "COD";

        FishType type = fishingConfig.fishTypes.get(typeId);
        FishingRarity rarity = fishingConfig.rarities.get(rarityId);

        Material mat = (type != null && type.getMaterial() != null)
                ? type.getMaterial()
                : Material.COD;

        String rarityColor = (rarity != null) ? rarity.getColor() : "&7";

        String displayFormat = (type != null && type.getDisplayFormat() != null)
                ? type.getDisplayFormat()
                : "{rarity_color}{weight}lb {type_name}";

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String weightStr = String.format("%.1f", bestWeight);

        String name = displayFormat
                .replace("{rarity_color}", rarityColor)
                .replace("{rarity_name}", rarityId)
                .replace("{type_name}", pretty(typeId))
                .replace("{weight}", weightStr);

        meta.setDisplayName(color(name));

        // Lore for journal:
        // - remove "Right-click to add to journal"
        // - remove ALL blank lines from base lore (we control spacing ourselves)
        List<String> lore = new ArrayList<>();

        for (String line : fishingConfig.loreLines) {
            String replaced = line
                    .replace("{rarity_color}", rarityColor)
                    .replace("{rarity_name}", pretty(rarityId))
                    .replace("{type_name}", pretty(typeId))
                    .replace("{weight}", weightStr);

            String normalized = ChatColor.stripColor(color(replaced)).trim().toLowerCase(Locale.ROOT);

            if (normalized.contains("right-click to add to journal")) continue;
            if (normalized.isEmpty()) continue;

            lore.add(color(replaced));
        }

        String percent = String.format("%.4f", chance * 100.0);

        lore.add("");
        lore.add(color("&aBest recorded: &f" + weightStr + "lb"));
        lore.add(color("&7Chance: &f" + percent + "%"));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack namedItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static String stripColor(String s) {
        return ChatColor.stripColor(s == null ? "" : s);
    }

    private static String pretty(String s) {
        if (s == null) return "Unknown";
        String x = s.toLowerCase(Locale.ROOT).replace("_", " ");
        String[] parts = x.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.substring(1))
                    .append(" ");
        }
        return sb.toString().trim();
    }
}
