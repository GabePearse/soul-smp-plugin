package me.Evil.soulSMP;

import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.commands.*;
import me.Evil.soulSMP.fishing.CustomFishGenerator;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.journal.FishingJournalManager;
import me.Evil.soulSMP.fishing.journal.JournalAutoGeneratorPaged;
import me.Evil.soulSMP.listeners.*;
import me.Evil.soulSMP.shop.BannerShopSettings;
import me.Evil.soulSMP.shop.DimensionBannerShopSettings;
import me.Evil.soulSMP.store.StoreManager;
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

    // Fishing system
    private FishingConfig fishingConfig;
    private CustomFishGenerator fishGenerator;

    // Journal system
    private FishingJournalManager fishingJournalManager;

    // Shop settings
    private BannerShopSettings bannerShopSettings;
    private BeaconEffectSettings effectSettings;
    private DimensionBannerShopSettings dimensionBannerShopSettings;

    // Player Store
    private StoreManager storeManager;

    // Task IDs
    private int auraTaskId = -1;
    private int upkeepTaskId = -1;

    @Override
    public void onEnable() {

        // ---------------------------
        // CORE MANAGERS
        // ---------------------------
        teamManager = new TeamManager(this);
        teamChatManager = new TeamChatManager();
        vaultManager = new TeamVaultManager(this, teamManager);
        tokenManager = new SoulTokenManager(this);

        // ---------------------------
        // FISHING CONFIG (LOAD FIRST)
        // ---------------------------
        fishingConfig = new FishingConfig(this);
        fishingConfig.reload(); // ✅ REQUIRED

        getLogger().info("FishingConfig loaded: fishTypes="
                + fishingConfig.fishTypes.size()
                + ", rarities="
                + fishingConfig.rarities.size());

        // ---------------------------
        // JOURNAL
        // ---------------------------
        JournalAutoGeneratorPaged.regenerate(this, fishingConfig);
        fishingJournalManager = new FishingJournalManager(this);

        // ---------------------------
        // OTHER MANAGERS
        // ---------------------------
        upkeepManager = new TeamUpkeepManager(this, teamManager, tokenManager);
        storeManager = new StoreManager(this, tokenManager);
        bannerShopSettings = new BannerShopSettings(this);
        effectSettings = new BeaconEffectSettings(this);
        dimensionBannerShopSettings = new DimensionBannerShopSettings(this);

        // ---------------------------
        // LOAD DATA
        // ---------------------------
        vaultManager.loadVaults();
        teamManager.loadTeams();

        // ---------------------------
        // FISH NBT KEYS
        // ---------------------------
        NamespacedKey fishTypeKey   = new NamespacedKey(this, "fish_type");
        NamespacedKey fishRarityKey = new NamespacedKey(this, "fish_rarity");
        NamespacedKey fishWeightKey = new NamespacedKey(this, "fish_weight");
        NamespacedKey fishScoreKey  = new NamespacedKey(this, "fish_score");
        NamespacedKey fishChanceKey = new NamespacedKey(this, "fish_chance");

        fishGenerator = new CustomFishGenerator(
                fishingConfig,
                fishTypeKey,
                fishRarityKey,
                fishWeightKey,
                fishScoreKey,
                fishChanceKey
        );

        // ---------------------------
        // COMMANDS
        // ---------------------------
        if (getCommand("soulsmp") != null) {
            SoulSMPCommand mainCmd = new SoulSMPCommand(this);
            getCommand("soulsmp").setExecutor(mainCmd);
            getCommand("soulsmp").setTabCompleter(mainCmd);
        }

        if (getCommand("team") != null) {
            TeamCommand teamCommand = new TeamCommand(teamManager);
            getCommand("team").setExecutor(teamCommand);
            getCommand("team").setTabCompleter(teamCommand);
        }

        if (getCommand("tw") != null) {
            getCommand("tw").setExecutor(new TeamWhisperCommand(teamManager));
        }

        if (getCommand("tc") != null) {
            getCommand("tc").setExecutor(new TeamChatToggleCommand(teamManager, teamChatManager));
        }

        if (getCommand("token") != null) {
            SoulTokenCommand tokenCmd = new SoulTokenCommand(tokenManager);
            getCommand("token").setExecutor(tokenCmd);
            getCommand("token").setTabCompleter(tokenCmd);
        }

        if (getCommand("journal") != null) {
            getCommand("journal").setExecutor(
                    new JournalCommand(fishingJournalManager, fishingConfig)
            );
        }

        if (getCommand("store") != null) {
            getCommand("store").setExecutor(new StoreCommand(storeManager));
        }

        if (getCommand("fishsim") != null) {
            getCommand("fishsim").setExecutor(
                    new FishSimCommand(
                            fishGenerator,
                            fishRarityKey,
                            fishScoreKey
                    )
            );
        }

        // ---------------------------
        // LISTENERS
        // ---------------------------
        Bukkit.getPluginManager().registerEvents(new TeamActivityListener(teamManager, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamChatListener(teamManager, teamChatManager), this);
        Bukkit.getPluginManager().registerEvents(new BannerListener(this, teamManager, vaultManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamVaultListener(vaultManager, bannerShopSettings, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new SoulTokenProtectionListener(tokenManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamBannerShopListener(
                teamManager, tokenManager, bannerShopSettings, effectSettings,
                dimensionBannerShopSettings, upkeepManager
        ), this);
        Bukkit.getPluginManager().registerEvents(new BeaconEffectsListener(
                effectSettings, tokenManager, teamManager, bannerShopSettings, upkeepManager
        ), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(teamManager, tokenManager), this);
        Bukkit.getPluginManager().registerEvents(new EnderChestBlocker(), this);

        Bukkit.getPluginManager().registerEvents(
                new FishingListener(
                        fishingConfig,
                        fishGenerator,
                        fishTypeKey,
                        fishRarityKey,
                        fishWeightKey,
                        fishChanceKey
                ),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new FishingJournalListener(this, fishingJournalManager, fishingConfig),
                this
        );
        Bukkit.getPluginManager().registerEvents(new StoreListener(), this);

        // ---------------------------
        // TASKS
        // ---------------------------
        BeaconEffectManager aura = new BeaconEffectManager(teamManager);
        auraTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, aura::tick, 20L, 20L)
                .getTaskId();

        long ticksPerDay = 20L * 60L * 60L * 24L;
        upkeepTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, upkeepManager::runDailyCheck, ticksPerDay, ticksPerDay)
                .getTaskId();

        getLogger().info("SoulSMP enabled.");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) teamManager.saveTeams();
        if (vaultManager != null) vaultManager.saveVaults();
        getLogger().info("SoulSMP disabled.");
    }

    public void reloadConfigs() {

        bannerShopSettings = new BannerShopSettings(this);
        effectSettings = new BeaconEffectSettings(this);
        dimensionBannerShopSettings = new DimensionBannerShopSettings(this);

        // ---------------------------
        // RELOAD FISHING CONFIG (FIX)
        // ---------------------------
        fishingConfig = new FishingConfig(this);
        fishingConfig.reload(); // ✅ REQUIRED

        getLogger().info("FishingConfig reloaded: fishTypes="
                + fishingConfig.fishTypes.size()
                + ", rarities="
                + fishingConfig.rarities.size());

        JournalAutoGeneratorPaged.regenerate(this, fishingConfig);

        if (teamManager != null) teamManager.reloadTeamsFromFile();
        if (vaultManager != null) vaultManager.loadVaults();
        if (fishingJournalManager != null) fishingJournalManager.reload();
        if (storeManager != null) storeManager.reload();

        NamespacedKey fishTypeKey   = new NamespacedKey(this, "fish_type");
        NamespacedKey fishRarityKey = new NamespacedKey(this, "fish_rarity");
        NamespacedKey fishWeightKey = new NamespacedKey(this, "fish_weight");
        NamespacedKey fishScoreKey  = new NamespacedKey(this, "fish_score");
        NamespacedKey fishChanceKey = new NamespacedKey(this, "fish_chance");

        fishGenerator = new CustomFishGenerator(
                fishingConfig,
                fishTypeKey,
                fishRarityKey,
                fishWeightKey,
                fishScoreKey,
                fishChanceKey
        );

        org.bukkit.event.HandlerList.unregisterAll(this);

        Bukkit.getPluginManager().registerEvents(
                new FishingListener(
                        fishingConfig,
                        fishGenerator,
                        fishTypeKey,
                        fishRarityKey,
                        fishWeightKey,
                        fishChanceKey
                ),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new FishingJournalListener(this, fishingJournalManager, fishingConfig),
                this
        );
        Bukkit.getPluginManager().registerEvents(new StoreListener(), this);

        if (getCommand("journal") != null) {
            getCommand("journal").setExecutor(
                    new JournalCommand(fishingJournalManager, fishingConfig)
            );
        }

        Bukkit.getScheduler().cancelTasks(this);

        BeaconEffectManager aura = new BeaconEffectManager(teamManager);
        auraTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, aura::tick, 20L, 20L)
                .getTaskId();

        long ticksPerDay = 20L * 60L * 60L * 24L;
        upkeepTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, upkeepManager::runDailyCheck, ticksPerDay, ticksPerDay)
                .getTaskId();

        getLogger().info("SoulSMP configuration files reloaded.");
    }
}
