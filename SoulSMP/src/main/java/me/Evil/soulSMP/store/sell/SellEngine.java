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
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SellEngine {

    private final Plugin plugin;
    private final SoulTokenManager tokenManager;

    private final NamespacedKey fishTypeKey;
    private final NamespacedKey fishRarityKey;
    private final NamespacedKey fishWeightKey;
    private final NamespacedKey fishScoreKey;
    private final NamespacedKey fishChanceKey;

    private final Map<Material, MaterialSellRule> materialRules = new HashMap<>();

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

                // NEW: slot (optional). If missing, default -1.
                int slot = matSec.getInt(matName + ".slot", -1);

                materialRules.put(m, new MaterialSellRule(m, unit, payout, slot));
            }
        }

        soulFishEnabled = cfg.getBoolean("soulfish.enabled", true);
        useScore = cfg.getBoolean("soulfish.use-score", true);
        scoreMultiplier = cfg.getDouble("soulfish.score-multiplier", 0.35);
        flatBonus = cfg.getInt("soulfish.flat-bonus", 0);
        minPayout = cfg.getInt("soulfish.min-payout", 0);

        rarityMult.clear();
        var rSec = cfg.getConfigurationSection("soulfish.rarity-multipliers");
        if (rSec != null) {
            for (String rid : rSec.getKeys(false)) {
                // Normalize keys to avoid casing mismatches
                rarityMult.put(rid.toLowerCase(Locale.ROOT), rSec.getDouble(rid, 1.0));
            }
        }
    }

    public int sellAll(Player player) {
        int totalPayout = 0;
        boolean hadWorthlessFish = false;

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack it = player.getInventory().getItem(slot);
            if (it == null) continue;

            // 1) materials
            MaterialSellRule rule = materialRules.get(it.getType());
            if (rule != null) {
                int payout = rule.sellFromStack(player, slot, it);
                totalPayout += payout;
                continue;
            }

            // 2) soul fish (always delete; payout may be 0)
            if (soulFishEnabled && isSoulFish(it)) {
                int payout = computeFishPayout(it);

                // Always remove the fish
                player.getInventory().setItem(slot, null);

                if (payout > 0) {
                    totalPayout += payout;
                } else {
                    hadWorthlessFish = true;
                }
            }
        }

        if (totalPayout > 0) {
            tokenManager.giveTokens(player, totalPayout);
        }

        // Soft warning once
        if (hadWorthlessFish) {
            player.sendMessage("§7Some fish were too weak to yield any Soul Tokens.");
        }

        return totalPayout;
    }

    public Map<Material, MaterialSellRule> getMaterialRules() {
        return Collections.unmodifiableMap(materialRules);
    }

    public int sellOneMaterialUnit(Player player, Material material) {
        MaterialSellRule rule = materialRules.get(material);
        if (rule == null) return 0;

        int unit = rule.unit;
        int payoutPerUnit = rule.payout;
        if (payoutPerUnit <= 0) return 0;

        int available = countMaterial(player, material);
        if (available < unit) return 0;

        removeMaterial(player, material, unit);

        tokenManager.giveTokens(player, payoutPerUnit);
        return payoutPerUnit;
    }

    public int sellAllMaterialUnits(Player player, Material material) {
        MaterialSellRule rule = materialRules.get(material);
        if (rule == null) return 0;

        int unit = rule.unit;
        int payoutPerUnit = rule.payout;
        if (payoutPerUnit <= 0) return 0;

        int available = countMaterial(player, material);
        int bundles = available / unit;
        if (bundles <= 0) return 0;

        int remove = bundles * unit;
        removeMaterial(player, material, remove);

        int total = bundles * payoutPerUnit;
        tokenManager.giveTokens(player, total);
        return total;
    }

    public int sellOneSoulFish(Player player) {
        if (!soulFishEnabled) return 0;

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack it = player.getInventory().getItem(slot);
            if (it == null) continue;

            if (isSoulFish(it)) {
                int payout = computeFishPayout(it);

                // 🔥 Always remove the fish
                player.getInventory().setItem(slot, null);

                // 💰 Only pay if worth something
                if (payout > 0) {
                    tokenManager.giveTokens(player, payout);
                } else {
                    player.sendMessage("§7That fish was too weak to yield any Soul Tokens.");
                }

                return payout;
            }
        }
        return 0;
    }

    public int sellAllSoulFish(Player player) {
        if (!soulFishEnabled) return 0;

        int total = 0;
        boolean hadWorthless = false;

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack it = player.getInventory().getItem(slot);
            if (it == null) continue;

            if (isSoulFish(it)) {
                int payout = computeFishPayout(it);

                // Always remove fish
                player.getInventory().setItem(slot, null);

                if (payout > 0) {
                    total += payout;
                } else {
                    hadWorthless = true;
                }
            }
        }

        if (total > 0) {
            tokenManager.giveTokens(player, total);
        }

        // 🟡 Soft warning (once)
        if (hadWorthless) {
            player.sendMessage("§7Some fish were too weak to yield any Soul Tokens.");
        }

        return total;
    }

    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null) continue;
            if (it.getType() == material) total += it.getAmount();
        }
        return total;
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int toRemove = amount;

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack it = player.getInventory().getItem(slot);
            if (it == null) continue;
            if (it.getType() != material) continue;

            int stack = it.getAmount();
            if (stack <= toRemove) {
                player.getInventory().setItem(slot, null);
                toRemove -= stack;
            } else {
                it.setAmount(stack - toRemove);
                player.getInventory().setItem(slot, it);
                toRemove = 0;
            }

            if (toRemove <= 0) break;
        }
    }

    public boolean isSoulFish(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        return pdc.has(fishTypeKey, PersistentDataType.STRING)
                && pdc.has(fishRarityKey, PersistentDataType.STRING)
                && pdc.has(fishScoreKey, PersistentDataType.DOUBLE);
    }

    /**
     * Source-of-truth payout method.
     * Use this everywhere (sell + visuals) so worth always matches.
     */
    public int computeFishPayout(double score, String rarityId) {
        if (!soulFishEnabled) return 0;

        String key = (rarityId == null) ? null : rarityId.toLowerCase(Locale.ROOT);

        double base = useScore ? (score * scoreMultiplier) : 0.0;
        double mult = (key != null) ? rarityMult.getOrDefault(key, 1.0) : 1.0;

        int payout = (int) Math.floor((base * mult) + flatBonus);
        return Math.max(minPayout, payout);
    }

    public int computeFishPayout(ItemStack it) {
        var meta = it.getItemMeta();
        if (meta == null) return 0;

        var pdc = meta.getPersistentDataContainer();
        Double score = pdc.get(fishScoreKey, PersistentDataType.DOUBLE);
        String rarity = pdc.get(fishRarityKey, PersistentDataType.STRING);

        if (score == null) return 0;
        return computeFishPayout(score, rarity);
    }

    public String getSellTitle() { return sellTitle; }
    public int getSellSize() { return sellSize; }
    public Material getFillerMat() { return fillerMat == null ? Material.LIME_STAINED_GLASS_PANE : fillerMat; }
    public String getFillerName() { return fillerName; }
    public int getBackSlot() { return backSlot; }
}
