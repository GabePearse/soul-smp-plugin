package me.Evil.soulSMP;

import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.commands.SoulTokenCommand;
import me.Evil.soulSMP.commands.TeamWhisperCommand;
import me.Evil.soulSMP.commands.TeamCommand;
import me.Evil.soulSMP.commands.TeamChatToggleCommand;
import me.Evil.soulSMP.fishing.CustomFishGenerator;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.journal.JournalAutoGeneratorPaged;
import me.Evil.soulSMP.listeners.*;
import me.Evil.soulSMP.shop.BannerShopSettings;
import me.Evil.soulSMP.shop.DimensionBannerShopSettings;
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
    private me.Evil.soulSMP.fishing.journal.FishingJournalManager fishingJournalManager;


    private BannerShopSettings bannerShopSettings;
    private BeaconEffectSettings effectSettings;
    private DimensionBannerShopSettings dimensionBannerShopSettings;

    // Aura + upkeep task IDs (so we can properly restart on reload)
    private int auraTaskId = -1;
    private int upkeepTaskId = -1;

    public BannerShopSettings getBannerShopSettings() {
        return bannerShopSettings;
    }

    public BeaconEffectSettings getEffectSettings() {
        return effectSettings;
    }

    public DimensionBannerShopSettings getDimensionBannerShopSettings() {
        return dimensionBannerShopSettings;
    }

    @Override
    public void onEnable() {

        // ---------------------------
        // CORE MANAGERS
        // ---------------------------
        teamManager = new TeamManager(this);
        teamChatManager = new TeamChatManager();
        vaultManager = new TeamVaultManager(this, teamManager);
        tokenManager = new SoulTokenManager(this);

        // Journal Manager
        JournalAutoGeneratorPaged.generateIfMissing(this, fishingConfig);
        fishingJournalManager = new me.Evil.soulSMP.fishing.journal.FishingJournalManager(this);

        // Daily upkeep manager
        upkeepManager = new TeamUpkeepManager(this, teamManager, tokenManager);

        // Shop / effects settings
        bannerShopSettings = new BannerShopSettings(this);
        effectSettings = new BeaconEffectSettings(this);
        dimensionBannerShopSettings = new DimensionBannerShopSettings(this);

        // Load data
        vaultManager.loadVaults();
        teamManager.loadTeams();   // IMPORTANT: load BEFORE listeners register

        // Load fishing configuration
        fishingConfig = new FishingConfig(this);


        // NBT keys for fish affix data (PersistentDataContainer)
        NamespacedKey fishTypeKey   = new NamespacedKey(this, "fish_type");
        NamespacedKey fishRarityKey = new NamespacedKey(this, "fish_rarity");
        NamespacedKey fishWeightKey = new NamespacedKey(this, "fish_weight");
        NamespacedKey fishScoreKey  = new NamespacedKey(this, "fish_score");

        // Generator that handles: rarity roll, type roll, weight, score, item building
        fishGenerator = new CustomFishGenerator(
                fishingConfig,
                fishTypeKey,
                fishRarityKey,
                fishWeightKey,
                fishScoreKey
        );

        // ---------------------------
        // REGISTER COMMANDS
        // ---------------------------

        // SOULSMP MAIN COMMAND (/soulsmp)
        if (getCommand("soulsmp") != null) {
            var mainCmd = new me.Evil.soulSMP.commands.SoulSMPCommand(this);
            getCommand("soulsmp").setExecutor(mainCmd);
            getCommand("soulsmp").setTabCompleter(mainCmd);
        } else {
            getLogger().severe("Command 'soulsmp' not found in plugin.yml!");
        }

        // TEAM COMMAND (/team, alias /t)
        if (getCommand("team") != null) {
            TeamCommand teamCommand = new TeamCommand(teamManager);
            getCommand("team").setExecutor(teamCommand);
            getCommand("team").setTabCompleter(teamCommand);
        } else {
            getLogger().severe("Command 'team' not found in plugin.yml!");
        }

        // TEAM WHISPER COMMAND (/tw, /tmsg, etc.)
        if (getCommand("tw") != null) {
            getCommand("tw").setExecutor(new TeamWhisperCommand(teamManager));
        } else {
            getLogger().severe("Command 'tw' not found in plugin.yml!");
        }

        // TEAM TOGGLE CHAT COMMAND (/tc)
        if (getCommand("tc") != null) {
            getCommand("tc").setExecutor(new TeamChatToggleCommand(teamManager, teamChatManager));
        } else {
            getLogger().severe("Command 'tc' not found in plugin.yml!");
        }

        // SOUL TOKEN COMMAND (/token)
        if (getCommand("token") != null) {
            SoulTokenCommand tokenCmd = new SoulTokenCommand(tokenManager);
            getCommand("token").setExecutor(tokenCmd);
            getCommand("token").setTabCompleter(tokenCmd);
        } else {
            getLogger().severe("Command 'token' not found in plugin.yml!");
        }

        // ---------------------------
        // REGISTER LISTENERS
        // ---------------------------
        getServer().getPluginManager().registerEvents(new TeamActivityListener(teamManager, upkeepManager), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(teamManager, teamChatManager), this);
        getServer().getPluginManager().registerEvents(new BannerListener(this, teamManager, vaultManager), this);
        getServer().getPluginManager().registerEvents(new TeamVaultListener(vaultManager, bannerShopSettings, upkeepManager), this);
        getServer().getPluginManager().registerEvents(new SoulTokenProtectionListener(tokenManager), this);
        getServer().getPluginManager().registerEvents(new TeamBannerShopListener(teamManager, tokenManager, bannerShopSettings, effectSettings, dimensionBannerShopSettings, upkeepManager), this);
        getServer().getPluginManager().registerEvents(new BeaconEffectsListener(effectSettings, tokenManager, teamManager, bannerShopSettings, upkeepManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(teamManager, tokenManager), this);
        getServer().getPluginManager().registerEvents(new EnderChestBlocker(), this);
        getServer().getPluginManager().registerEvents(new FishingListener(fishingConfig, fishGenerator, fishTypeKey, fishRarityKey, fishWeightKey), this);
        getServer().getPluginManager().registerEvents(new FishingJournalListener(this, fishingJournalManager, fishingConfig), this);
        // ---------------------------
        // SCHEDULED TASKS
        // ---------------------------

        // Beacon aura ticking (every second)
        BeaconEffectManager aura = new BeaconEffectManager(teamManager);
        auraTaskId = Bukkit.getScheduler().runTaskTimer(this, aura::tick, 20L, 20L).getTaskId();

        // Daily upkeep check (every 24h)
        long ticksPerDay = 20L * 60L * 60L * 24L;
        upkeepTaskId = Bukkit.getScheduler().runTaskTimer(
                this,
                upkeepManager::runDailyCheck,
                ticksPerDay,
                ticksPerDay
        ).getTaskId();

        getLogger().info("SoulSMP enabled.");
    }

    @Override
    public void onDisable() {
        // Save team data via TeamManager (it handles IO + exceptions)
        if (teamManager != null) {
            teamManager.saveTeams();
        }

        if (vaultManager != null) {
            vaultManager.saveVaults();
        }

        getLogger().info("SoulSMP disabled.");
    }

    public void reloadConfigs() {
        // Recreate settings objects for shop.yml, dimension-shop.yml, and effects.yml
        this.bannerShopSettings = new BannerShopSettings(this);
        this.effectSettings     = new BeaconEffectSettings(this);
        this.dimensionBannerShopSettings = new DimensionBannerShopSettings(this);
        this.fishingConfig = new FishingConfig(this);
        JournalAutoGeneratorPaged.generateIfMissing(this, fishingConfig);


        // Reload teams.yml and update existing team objects
        if (teamManager != null) {
            teamManager.reloadTeamsFromFile();
        }

        // Reload vaults.yml (clearing the old cache in TeamVaultManager)
        if (vaultManager != null) {
            vaultManager.loadVaults();
        }

        if (fishingJournalManager != null) {
            fishingJournalManager.reload();
        }

        NamespacedKey fishTypeKey   = new NamespacedKey(this, "fish_type");
        NamespacedKey fishRarityKey = new NamespacedKey(this, "fish_rarity");
        NamespacedKey fishWeightKey = new NamespacedKey(this, "fish_weight");
        NamespacedKey fishScoreKey  = new NamespacedKey(this, "fish_score");

        this.fishGenerator = new CustomFishGenerator(
                fishingConfig,
                fishTypeKey,
                fishRarityKey,
                fishWeightKey,
                fishScoreKey
        );

        // Unregister listeners, re-register them, and restart tasks
        org.bukkit.event.HandlerList.unregisterAll(this);

        getServer().getPluginManager().registerEvents(new TeamActivityListener(teamManager, upkeepManager), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(teamManager, teamChatManager), this);
        getServer().getPluginManager().registerEvents(new BannerListener(this, teamManager, vaultManager), this);
        getServer().getPluginManager().registerEvents(new TeamVaultListener(vaultManager, bannerShopSettings, upkeepManager), this);
        getServer().getPluginManager().registerEvents(new SoulTokenProtectionListener(tokenManager), this);
        getServer().getPluginManager().registerEvents(new TeamBannerShopListener(teamManager, tokenManager, bannerShopSettings, effectSettings, dimensionBannerShopSettings, upkeepManager), this);
        getServer().getPluginManager().registerEvents(new BeaconEffectsListener(effectSettings, tokenManager, teamManager, bannerShopSettings, upkeepManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(teamManager, tokenManager), this);
        getServer().getPluginManager().registerEvents(new EnderChestBlocker(), this);
        getServer().getPluginManager().registerEvents(new FishingListener(fishingConfig, fishGenerator, fishTypeKey, fishRarityKey, fishWeightKey), this);
        getServer().getPluginManager().registerEvents(new FishingJournalListener(this, fishingJournalManager, fishingConfig), this);


        // Restart scheduled tasks
        getServer().getScheduler().cancelTasks(this);

        BeaconEffectManager aura = new BeaconEffectManager(teamManager);
        auraTaskId = Bukkit.getScheduler().runTaskTimer(this, aura::tick, 20L, 20L).getTaskId();

        long ticksPerDay = 20L * 60L * 60L * 24L;
        upkeepTaskId = Bukkit.getScheduler().runTaskTimer(
                this,
                upkeepManager::runDailyCheck,
                ticksPerDay,
                ticksPerDay
        ).getTaskId();

        getLogger().info("SoulSMP configuration files reloaded.");
    }
}
