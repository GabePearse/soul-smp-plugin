package me.Evil.soulSMP.store.sell;

import me.Evil.soulSMP.tokens.SoulTokenManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SellEngine {

    private final Plugin plugin;
    private final SoulTokenManager tokenManager;

    private final NamespacedKey fishTypeKey;
    private final NamespacedKey fishRarityKey;
    private final NamespacedKey fishWeightKey;
    private final NamespacedKey fishScoreKey;
    private final NamespacedKey fishChanceKey;

    // material rules: material -> (unit, payout)
    private final Map<Material, MaterialSellRule> materialRules = new HashMap<>();

    // soul fish config
    private boolean soulFishEnabled;
    private boolean useScore;
    private double scoreMultiplier;
    private int flatBonus;
    private int minPayout;
    private final Map<String, Double> rarityMult = new HashMap<>();

    private String sellTitle;
    private int sellSize;
    private Material fillerMat;
    private String fillerName;
    private int backSlot;

    public SellEngine(Plugin plugin,
                      SoulTokenManager tokenManager,
                      NamespacedKey fishTypeKey,
                      NamespacedKey fishRarityKey,
                      NamespacedKey fishWeightKey,
                      NamespacedKey fishScoreKey,
                      NamespacedKey fishChanceKey) {
        this.plugin = plugin;
        this.tokenManager = tokenManager;
        this.fishTypeKey = fishTypeKey;
        this.fishRarityKey = fishRarityKey;
        this.fishWeightKey = fishWeightKey;
        this.fishScoreKey = fishScoreKey;
        this.fishChanceKey = fishChanceKey;

        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "store-sell.yml");
        if (!file.exists()) plugin.saveResource("store-sell.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        sellTitle = cfg.getString("sell-gui.title", "&aSell Items");
        sellSize = cfg.getInt("sell-gui.size", 54);
        fillerMat = Material.matchMaterial(cfg.getString("sell-gui.filler.material", "LIME_STAINED_GLASS_PANE"));
        fillerName = cfg.getString("sell-gui.filler.name", " ");
        backSlot = cfg.getInt("sell-gui.back-slot", 49);

        materialRules.clear();
        var matSec = cfg.getConfigurationSection("materials");
        if (matSec != null) {
            for (String matName : matSec.getKeys(false)) {
                Material m = Material.matchMaterial(matName);
                if (m == null) continue;
                int unit = matSec.getInt(matName + ".unit", 1);
                int payout = matSec.getInt(matName + ".payout", 0);
                materialRules.put(m, new MaterialSellRule(m, unit, payout));
            }
        }

        soulFishEnabled = cfg.getBoolean("soulfish.enabled", true);
        useScore = cfg.getBoolean("soulfish.use-score", true);
        scoreMultiplier = cfg.getDouble("soulfish.score-multiplier", 0.35);
        flatBonus = cfg.getInt("soulfish.flat-bonus", 0);
        minPayout = cfg.getInt("soulfish.min-payout", 1);

        rarityMult.clear();
        var rSec = cfg.getConfigurationSection("soulfish.rarity-multipliers");
        if (rSec != null) {
            for (String rid : rSec.getKeys(false)) {
                rarityMult.put(rid, rSec.getDouble(rid, 1.0));
            }
        }
    }

    public int sellAll(Player player) {
        int totalPayout = 0;

        // 1) materials
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack it = player.getInventory().getItem(slot);
            if (it == null) continue;

            MaterialSellRule rule = materialRules.get(it.getType());
            if (rule != null) {
                int payout = rule.sellFromStack(player, slot, it);
                totalPayout += payout;
                continue;
            }

            // 2) soul fish
            if (soulFishEnabled && isSoulFish(it)) {
                int payout = computeFishPayout(it);
                if (payout > 0) {
                    player.getInventory().setItem(slot, null);
                    totalPayout += payout;
                }
            }
        }

        if (totalPayout > 0) tokenManager.giveTokens(player, totalPayout);
        return totalPayout;
    }

    private boolean isSoulFish(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        return pdc.has(fishTypeKey, PersistentDataType.STRING)
                && pdc.has(fishRarityKey, PersistentDataType.STRING)
                && pdc.has(fishScoreKey, PersistentDataType.DOUBLE);
    }

    private int computeFishPayout(ItemStack it) {
        var meta = it.getItemMeta();
        if (meta == null) return 0;

        var pdc = meta.getPersistentDataContainer();
        Double score = pdc.get(fishScoreKey, PersistentDataType.DOUBLE);
        String rarity = pdc.get(fishRarityKey, PersistentDataType.STRING);

        if (score == null) return 0;
        double base = useScore ? (score * scoreMultiplier) : 0.0;
        double mult = (rarity != null) ? rarityMult.getOrDefault(rarity, 1.0) : 1.0;

        int payout = (int) Math.floor((base * mult) + flatBonus);
        return Math.max(minPayout, payout);
    }

    public String getSellTitle() { return sellTitle; }
    public int getSellSize() { return sellSize; }
    public Material getFillerMat() { return fillerMat == null ? Material.LIME_STAINED_GLASS_PANE : fillerMat; }
    public String getFillerName() { return fillerName; }
    public int getBackSlot() { return backSlot; }
}
