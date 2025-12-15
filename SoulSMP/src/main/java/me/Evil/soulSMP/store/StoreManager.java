package me.Evil.soulSMP.store;

import me.Evil.soulSMP.SoulSMP;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.store.sell.SellEngine;
import me.Evil.soulSMP.store.gui.StoreMainMenuGui;
import org.bukkit.NamespacedKey;

public class StoreManager {

    private final SoulSMP plugin;
    private final SoulTokenManager tokenManager;

    private StoreSettings settings;
    private SellEngine sellEngine;

    // Fish PDC keys (must match SoulSMP)
    private final NamespacedKey fishTypeKey;
    private final NamespacedKey fishRarityKey;
    private final NamespacedKey fishWeightKey;
    private final NamespacedKey fishScoreKey;
    private final NamespacedKey fishChanceKey;

    public StoreManager(SoulSMP plugin, SoulTokenManager tokenManager) {
        this.plugin = plugin;
        this.tokenManager = tokenManager;

        this.fishTypeKey   = new NamespacedKey(plugin, "fish_type");
        this.fishRarityKey = new NamespacedKey(plugin, "fish_rarity");
        this.fishWeightKey = new NamespacedKey(plugin, "fish_weight");
        this.fishScoreKey  = new NamespacedKey(plugin, "fish_score");
        this.fishChanceKey = new NamespacedKey(plugin, "fish_chance");

        reload();
    }

    public void reload() {
        this.settings = new StoreSettings(plugin);
        this.sellEngine = new SellEngine(plugin, tokenManager, fishTypeKey, fishRarityKey, fishWeightKey, fishScoreKey, fishChanceKey);
    }

    public StoreSettings getSettings() { return settings; }
    public SellEngine getSellEngine() { return sellEngine; }
    public SoulTokenManager getTokenManager() { return tokenManager; }

    public void openMain(org.bukkit.entity.Player player) {
        StoreMainMenuGui.open(player, this);
    }
}
