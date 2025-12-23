package me.Evil.soulSMP;

import me.Evil.soulSMP.bank.BankManager;
import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.commands.*;
import me.Evil.soulSMP.fishing.CustomFishGenerator;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.journal.FishingJournalManager;
import me.Evil.soulSMP.fishing.journal.JournalAutoGeneratorPaged;
import me.Evil.soulSMP.leaderboard.LeaderboardCommand;
import me.Evil.soulSMP.leaderboard.LeaderboardDisplay;
import me.Evil.soulSMP.leaderboard.LeaderboardManager;
import me.Evil.soulSMP.listeners.*;
import me.Evil.soulSMP.shop.BannerShopSettings;
import me.Evil.soulSMP.shop.DimensionBannerShopSettings;
import me.Evil.soulSMP.store.StoreManager;
import me.Evil.soulSMP.store.sell.SellEngine;
import me.Evil.soulSMP.store.util.FishSimCommand;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.upgrades.BeaconEffectManager;
import me.Evil.soulSMP.upgrades.BeaconEffectSettings;
import me.Evil.soulSMP.upkeep.TeamUpkeepManager;
import me.Evil.soulSMP.vault.TeamVaultManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class SoulSMP extends JavaPlugin {

    private TeamManager teamManager;
    private TeamChatManager teamChatManager;
    private TeamVaultManager vaultManager;
    private SoulTokenManager tokenManager;
    private TeamUpkeepManager upkeepManager;

    // Bank
    private BankManager bankManager;

    // Fishing
    private FishingConfig fishingConfig;
    private CustomFishGenerator fishGenerator;

    // Journal
    private FishingJournalManager fishingJournalManager;

    // Shops
    private BannerShopSettings bannerShopSettings;
    private BeaconEffectSettings effectSettings;
    private DimensionBannerShopSettings dimensionBannerShopSettings;

    // Store
    private StoreManager storeManager;

    // Tasks
    private int auraTaskId = -1;
    private int upkeepTaskId = -1;

    // Fish keys (keep so reload can reuse consistently)
    private NamespacedKey fishTypeKey;
    private NamespacedKey fishRarityKey;
    private NamespacedKey fishWeightKey;
    private NamespacedKey fishScoreKey;
    private NamespacedKey fishChanceKey;

    // Leaderboard
    private LeaderboardManager leaderboardManager;
    private LeaderboardDisplay leaderboardDisplay;

    @Override
    public void onEnable() {

        // ---------------------------
        // CORE
        // ---------------------------
        teamManager = new TeamManager(this);
        teamChatManager = new TeamChatManager();
        vaultManager = new TeamVaultManager(this, teamManager);
        tokenManager = new SoulTokenManager(this);

        // ---------------------------
        // BANK
        // ---------------------------
        bankManager = new BankManager(this);

        // ---------------------------
        // FISHING CONFIG (FIRST)
        // ---------------------------
        fishingConfig = new FishingConfig(this);
        fishingConfig.reload();

        // ---------------------------
        // STORE (SELL ENGINE MUST EXIST BEFORE GENERATOR)
        // ---------------------------
        storeManager = new StoreManager(this, tokenManager);
        SellEngine sellEngine = storeManager.getSellEngine();

        // ---------------------------
        // FISH NBT KEYS
        // ---------------------------
        fishTypeKey   = new NamespacedKey(this, "fish_type");
        fishRarityKey = new NamespacedKey(this, "fish_rarity");
        fishWeightKey = new NamespacedKey(this, "fish_weight");
        fishScoreKey  = new NamespacedKey(this, "fish_score");
        fishChanceKey = new NamespacedKey(this, "fish_chance");

        // ---------------------------
        // FISH GENERATOR
        // ---------------------------
        fishGenerator = new CustomFishGenerator(
                fishingConfig,
                fishTypeKey,
                fishRarityKey,
                fishWeightKey,
                fishScoreKey,
                fishChanceKey,
                sellEngine
        );

        // ---------------------------
        // JOURNAL
        // ---------------------------
        JournalAutoGeneratorPaged.regenerate(this, fishingConfig);
        fishingJournalManager = new FishingJournalManager(this);

        // ---------------------------
        // OTHER MANAGERS
        // ---------------------------
        upkeepManager = new TeamUpkeepManager(this, teamManager, tokenManager);
        bannerShopSettings = new BannerShopSettings(this);
        effectSettings = new BeaconEffectSettings(this);
        dimensionBannerShopSettings = new DimensionBannerShopSettings(this);

        // ---------------------------
        // LOAD DATA
        // ---------------------------
        vaultManager.loadVaults();
        teamManager.loadTeams();
        // bank.yml is loaded by BankManager constructor

        // ---------------------------
        // LEADERBOARD
        // ---------------------------
        leaderboardDisplay = new LeaderboardDisplay(this);
        leaderboardManager = new LeaderboardManager(this, teamManager, fishingJournalManager, leaderboardDisplay);
        teamManager.setLeaderboard(leaderboardManager);

        // ---------------------------
        // COMMANDS
        // ---------------------------
        registerCommands();

        // ---------------------------
        // LISTENERS
        // ---------------------------
        registerListeners(sellEngine);

        // ---------------------------
        // TASKS
        // ---------------------------
        restartTasks();

        // Optional: update displays on boot (debounced)
        leaderboardManager.scheduleRecompute();

        getLogger().info("SoulSMP enabled.");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) teamManager.saveTeams();
        if (vaultManager != null) vaultManager.saveVaults();
        if (bankManager != null) bankManager.save();
        getLogger().info("SoulSMP disabled.");
    }

    /**
     * Call this from /ssmp reload.
     * This will reload fishing_journal_data.yml because fishingJournalManager.reload() reloads it.
     */
    public void reloadConfigs() {

        // Reload shop settings
        bannerShopSettings = new BannerShopSettings(this);
        effectSettings = new BeaconEffectSettings(this);
        dimensionBannerShopSettings = new DimensionBannerShopSettings(this);

        // Reload fishing config
        fishingConfig = new FishingConfig(this);
        fishingConfig.reload();

        // Store reload (sell config etc.)
        if (storeManager != null) storeManager.reload();
        SellEngine sellEngine = storeManager.getSellEngine();

        // Recreate fish generator (needs updated config + sell engine)
        fishGenerator = new CustomFishGenerator(
                fishingConfig,
                fishTypeKey,
                fishRarityKey,
                fishWeightKey,
                fishScoreKey,
                fishChanceKey,
                sellEngine
        );

        // Regenerate journal layout (journal.yml) if you want this on reload
        JournalAutoGeneratorPaged.regenerate(this, fishingConfig);

        // RELOAD .YML FILES
        if (fishingJournalManager != null) fishingJournalManager.reload();
        if (teamManager != null) teamManager.reloadTeamsFromFile();
        if (vaultManager != null) vaultManager.loadVaults();
        if (bankManager != null) bankManager.reload();

        // Re-register listeners so they use the new generator/config refs
        org.bukkit.event.HandlerList.unregisterAll(this);

        registerListeners(sellEngine);
        registerCommands(); // ✅ include /lb as well

        // Restart tasks
        Bukkit.getScheduler().cancelTasks(this);
        restartTasks();

        // Refresh leaderboard displays after reload (debounced)
        if (leaderboardManager != null) leaderboardManager.scheduleRecompute();

        getLogger().info("SoulSMP configuration files reloaded (including fishing_journal_data.yml).");
    }

    // -------------------------------------------------
    // Helpers to keep enable/reload consistent
    // -------------------------------------------------

    private void registerCommands() {

        if (getCommand("soulsmp") != null) {
            SoulSMPCommand cmd = new SoulSMPCommand(this);
            getCommand("soulsmp").setExecutor(cmd);
            getCommand("soulsmp").setTabCompleter(cmd);
        }

        if (getCommand("team") != null) {
            TeamCommand cmd = new TeamCommand(teamManager);
            getCommand("team").setExecutor(cmd);
            getCommand("team").setTabCompleter(cmd);
        }

        // NOTE: your plugin.yml defines "tc", not "teamchat".
        // If you want /tc to work, you must register "tc".
        if (getCommand("tc") != null) {
            TeamChatToggleCommand cmd = new TeamChatToggleCommand(teamManager, teamChatManager);
            getCommand("tc").setExecutor(cmd);
        }

        if (getCommand("tw") != null) {
            TeamWhisperCommand cmd = new TeamWhisperCommand(teamManager);
            getCommand("tw").setExecutor(cmd);
        }

        if (getCommand("token") != null) {
            SoulTokenCommand cmd = new SoulTokenCommand(tokenManager);
            getCommand("token").setExecutor(cmd);
            getCommand("token").setTabCompleter(cmd);
        }

        if (getCommand("journal") != null) {
            getCommand("journal").setExecutor(new JournalCommand(fishingJournalManager, fishingConfig));
        }

        if (getCommand("store") != null) {
            getCommand("store").setExecutor(new StoreCommand(storeManager));
        }

        if (getCommand("fishsim") != null) {
            getCommand("fishsim").setExecutor(new FishSimCommand(fishGenerator, fishRarityKey, fishScoreKey));
        }

        if (getCommand("bank") != null) {
            getCommand("bank").setExecutor(new BankCommand());
        }

        if (getCommand("lb") != null) {
            getCommand("lb").setExecutor(new LeaderboardCommand(leaderboardManager));
        }
    }

    private void registerListeners(SellEngine sellEngine) {

        Bukkit.getPluginManager().registerEvents(new FishingListener(fishingConfig, fishGenerator, sellEngine, fishTypeKey, fishRarityKey, fishWeightKey, fishChanceKey, leaderboardManager), this);
        Bukkit.getPluginManager().registerEvents(new FishingJournalListener(this, fishingJournalManager, fishingConfig, leaderboardManager), this);
        Bukkit.getPluginManager().registerEvents(new StoreListener(), this);
        Bukkit.getPluginManager().registerEvents(new TeamActivityListener(teamManager, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamChatListener(teamManager, teamChatManager), this);
        Bukkit.getPluginManager().registerEvents(new BannerListener(this, teamManager, vaultManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamVaultListener(vaultManager, bannerShopSettings, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new SoulTokenProtectionListener(tokenManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamBannerShopListener(teamManager, tokenManager, bannerShopSettings, effectSettings, dimensionBannerShopSettings, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new BeaconEffectsListener(effectSettings, tokenManager, teamManager, bannerShopSettings, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(teamManager, tokenManager), this);
        Bukkit.getPluginManager().registerEvents(new EnderChestBlocker(), this);
        Bukkit.getPluginManager().registerEvents(new DisableEndCrystalExplosionsListener(), this);

        // Leaderboard protection (banner unbreakable)
        Bukkit.getPluginManager().registerEvents(new LeaderboardProtectionListener(leaderboardManager), this);

        // Bank GUI / clicks / withdraw chat
        Bukkit.getPluginManager().registerEvents(new BankListener(bankManager, tokenManager, teamManager), this);
    }

    private void restartTasks() {

        BeaconEffectManager aura = new BeaconEffectManager(teamManager);
        auraTaskId = Bukkit.getScheduler().runTaskTimer(this, aura::tick, 20L, 20L).getTaskId();

        long ticksPerDay = 20L * 60L * 60L * 24L;
        upkeepTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, upkeepManager::runDailyCheck, ticksPerDay, ticksPerDay)
                .getTaskId();
    }
}
