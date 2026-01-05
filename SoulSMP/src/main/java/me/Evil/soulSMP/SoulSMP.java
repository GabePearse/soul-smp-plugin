package me.Evil.soulSMP;

import me.Evil.soulSMP.bank.BankManager;
import me.Evil.soulSMP.broadcast.ServerTipBroadcaster;
import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.commands.*;
import me.Evil.soulSMP.fishing.CustomFishGenerator;
import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.journal.FishingJournalManager;
import me.Evil.soulSMP.fishing.journal.JournalAutoGeneratorPaged;
import me.Evil.soulSMP.koth.KothKitSettings;
import me.Evil.soulSMP.koth.KothManager;
import me.Evil.soulSMP.leaderboard.LeaderboardCommand;
import me.Evil.soulSMP.leaderboard.LeaderboardDisplay;
import me.Evil.soulSMP.leaderboard.LeaderboardManager;
import me.Evil.soulSMP.listeners.*;
import me.Evil.soulSMP.npc.MannequinNpcListener;
import me.Evil.soulSMP.npc.MannequinNpcManager;
import me.Evil.soulSMP.rewards.RewardProgressManager;
import me.Evil.soulSMP.shop.BannerShopSettings;
import me.Evil.soulSMP.shop.DimensionBannerShopSettings;
import me.Evil.soulSMP.store.StoreManager;
import me.Evil.soulSMP.store.sell.SellEngine;
import me.Evil.soulSMP.store.util.FishSimCommand;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.beacon.BeaconEffectManager;
import me.Evil.soulSMP.beacon.BeaconEffectSettings;
import me.Evil.soulSMP.upkeep.TeamUpkeepManager;
import me.Evil.soulSMP.vault.TeamVaultManager;
import me.Evil.soulSMP.vouchers.VoucherMailManager;
import me.Evil.soulSMP.opmode.OpModeManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class SoulSMP extends JavaPlugin {

    private TeamManager teamManager;
    private TeamChatManager teamChatManager;
    private TeamVaultManager vaultManager;
    private SoulTokenManager tokenManager;
    private TeamUpkeepManager upkeepManager;

    // Rewards
    private RewardProgressManager rewardProgressManager;

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

    // NPCs (Mannequin)
    private MannequinNpcManager npcManager;

    // Tasks
    private int auraTaskId = -1;
    private int upkeepTaskId = -1;

    // Tips
    private int tipsTaskId = -1;
    private ServerTipBroadcaster tipBroadcaster;

    // Fish keys
    private NamespacedKey fishTypeKey;
    private NamespacedKey fishRarityKey;
    private NamespacedKey fishWeightKey;
    private NamespacedKey fishScoreKey;
    private NamespacedKey fishChanceKey;

    // Leaderboard
    private LeaderboardManager leaderboardManager;
    private LeaderboardDisplay leaderboardDisplay;

    // ✅ KOTH
    private KothKitSettings kothKitSettings;
    private KothManager kothManager;

    // VOUCHER MAIL
    private VoucherMailManager voucherMail;

    // OP MODE
    private OpModeManager opModeManager;



    public MannequinNpcManager getNpcManager() {
        return npcManager;
    }

    public KothManager getKothManager() { return kothManager; }

    @Override
    public void onEnable() {

        saveDefaultConfig();

        // CORE
        teamManager = new TeamManager(this);
        teamChatManager = new TeamChatManager();
        vaultManager = new TeamVaultManager(this, teamManager);
        tokenManager = new SoulTokenManager(this);

        // OP MODE
        opModeManager = new OpModeManager(this);


        // VOUCHER MAIL MANAGER
        voucherMail = new VoucherMailManager(this);

        // ✅ KOTH settings + manager
        kothKitSettings = new KothKitSettings(this);
        kothKitSettings.reload();
        kothManager = new KothManager(this, teamManager, kothKitSettings);

        // REWARDS
        rewardProgressManager = new RewardProgressManager(this);
        rewardProgressManager.load();
        rewardProgressManager.save();

        // BANK
        bankManager = new BankManager(this);

        // FISHING CONFIG
        fishingConfig = new FishingConfig(this);
        fishingConfig.reload();

        // STORE
        storeManager = new StoreManager(this, tokenManager);
        SellEngine sellEngine = storeManager.getSellEngine();

        // NPC MANAGER
        npcManager = new MannequinNpcManager(this);



        // FISH NBT KEYS
        fishTypeKey   = new NamespacedKey(this, "fish_type");
        fishRarityKey = new NamespacedKey(this, "fish_rarity");
        fishWeightKey = new NamespacedKey(this, "fish_weight");
        fishScoreKey  = new NamespacedKey(this, "fish_score");
        fishChanceKey = new NamespacedKey(this, "fish_chance");

        // FISH GENERATOR
        fishGenerator = new CustomFishGenerator(
                fishingConfig,
                fishTypeKey,
                fishRarityKey,
                fishWeightKey,
                fishScoreKey,
                fishChanceKey,
                sellEngine
        );

        // JOURNAL
        JournalAutoGeneratorPaged.regenerate(this, fishingConfig);
        fishingJournalManager = new FishingJournalManager(this);

        // OTHER MANAGERS
        upkeepManager = new TeamUpkeepManager(this, teamManager, tokenManager);
        bannerShopSettings = new BannerShopSettings(this);
        effectSettings = new BeaconEffectSettings(this);
        dimensionBannerShopSettings = new DimensionBannerShopSettings(this);

        // LOAD DATA
        teamManager.loadTeams();
        vaultManager.loadVaults();

        // LEADERBOARD
        leaderboardDisplay = new LeaderboardDisplay(this);
        leaderboardManager = new LeaderboardManager(this, teamManager, fishingJournalManager, leaderboardDisplay);
        teamManager.setLeaderboard(leaderboardManager);

        // COMMANDS
        registerCommands();

        // LISTENERS
        registerListeners(sellEngine);

        // TASKS
        tipBroadcaster = new ServerTipBroadcaster(this);
        restartTasks();

        leaderboardManager.scheduleRecompute();

        getLogger().info("SoulSMP enabled.");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) teamManager.saveTeams();
        if (vaultManager != null) vaultManager.saveVaults();
        if (bankManager != null) bankManager.save();
        if (rewardProgressManager != null) rewardProgressManager.save();

        if (opModeManager != null) opModeManager.save();

        if (kothManager != null && kothManager.isActive()) {
            kothManager.stop(false);
        }

        if (tipsTaskId != -1) Bukkit.getScheduler().cancelTask(tipsTaskId);

        getLogger().info("SoulSMP disabled.");
    }

    public void reloadConfigs() {

        reloadConfig();

        // Reload shop/settings
        bannerShopSettings = new BannerShopSettings(this);
        effectSettings = new BeaconEffectSettings(this);
        dimensionBannerShopSettings = new DimensionBannerShopSettings(this);

        // Reload fishing
        fishingConfig = new FishingConfig(this);
        fishingConfig.reload();

        if (storeManager != null) storeManager.reload();
        SellEngine sellEngine = storeManager.getSellEngine();

        fishGenerator = new CustomFishGenerator(
                fishingConfig,
                fishTypeKey,
                fishRarityKey,
                fishWeightKey,
                fishScoreKey,
                fishChanceKey,
                sellEngine
        );

        // Journal regen + reload
        JournalAutoGeneratorPaged.regenerate(this, fishingConfig);
        if (fishingJournalManager != null) fishingJournalManager.reload();

        // Reload team/vault/bank
        if (teamManager != null) teamManager.reloadTeamsFromFile();
        if (vaultManager != null) vaultManager.loadVaults();
        if (bankManager != null) bankManager.reload();

        // Reload rewards
        if (rewardProgressManager == null) rewardProgressManager = new RewardProgressManager(this);
        rewardProgressManager.load();
        rewardProgressManager.save();

        // ✅ Reload KOTH kit config
        if (kothKitSettings == null) kothKitSettings = new KothKitSettings(this);
        kothKitSettings.reload();

        // Reload Op
        if (opModeManager != null) opModeManager.load();


        // Reset event registrations
        org.bukkit.event.HandlerList.unregisterAll(this);

        // Re-register listeners + commands
        registerListeners(sellEngine);
        registerCommands();

        // Restart tasks
        Bukkit.getScheduler().cancelTasks(this);
        restartTasks();

        if (leaderboardManager != null) leaderboardManager.scheduleRecompute();

        getLogger().info("SoulSMP configuration files reloaded (including fishing_journal_data.yml).");
    }

    private void registerCommands() {

        if (getCommand("ssmp") != null) {
            SoulSMPCommand cmd = new SoulSMPCommand(this);
            getCommand("ssmp").setExecutor(cmd);
            getCommand("ssmp").setTabCompleter(cmd);
        }

        if (getCommand("text") != null) {
            TextCommand cmd = new TextCommand(this);
            getCommand("text").setExecutor(cmd);
            getCommand("text").setTabCompleter(cmd);
        }

        if (getCommand("npc") != null) {
            NpcCommand cmd = new NpcCommand(this, npcManager, storeManager);
            getCommand("npc").setExecutor(cmd);
            getCommand("npc").setTabCompleter(cmd);
        }

        if (getCommand("team") != null) {
            TeamCommand cmd = new TeamCommand(teamManager, teamChatManager);
            getCommand("team").setExecutor(cmd);
            getCommand("team").setTabCompleter(cmd);
        }

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

        if (getCommand("fishsim") != null) {
            getCommand("fishsim").setExecutor(new FishSimCommand(fishGenerator, fishRarityKey, fishScoreKey));
        }

        if (getCommand("lb") != null) {
            LeaderboardCommand lbCmd = new LeaderboardCommand(leaderboardManager);
            getCommand("lb").setExecutor(lbCmd);
            getCommand("lb").setTabCompleter(lbCmd);
        }

        if (getCommand("stuck") != null) {
            StuckCommand stuckCommand = new StuckCommand(this);
            getCommand("stuck").setExecutor(stuckCommand);
        }

        // ✅ KOTH
        if (getCommand("koth") != null) {
            KothCommand cmd = new KothCommand(kothManager);
            getCommand("koth").setExecutor(cmd);
            getCommand("koth").setTabCompleter(cmd);
        }

        if (getCommand("voucher") != null) {
            VoucherCommand v = new VoucherCommand(this, voucherMail);
            getCommand("voucher").setExecutor(v);
            getCommand("voucher").setTabCompleter(v);
        }

        if (getCommand("opmode") != null) {
            getCommand("opmode").setExecutor(new OpModeCommand(opModeManager));
        }

    }

    private void registerListeners(SellEngine sellEngine) {

        Bukkit.getPluginManager().registerEvents(new LevelRewardListener(tokenManager, rewardProgressManager), this);
        Bukkit.getPluginManager().registerEvents(new AdvancementRewardListener(tokenManager, rewardProgressManager), this);
        Bukkit.getPluginManager().registerEvents(new FishingListener(fishingConfig, fishGenerator, sellEngine, fishTypeKey, fishRarityKey, fishWeightKey, fishChanceKey, leaderboardManager), this);
        Bukkit.getPluginManager().registerEvents(new FishingJournalListener(this, fishingJournalManager, fishingConfig, leaderboardManager), this);
        Bukkit.getPluginManager().registerEvents(new StoreListener(), this);
        Bukkit.getPluginManager().registerEvents(new MannequinNpcListener(npcManager, storeManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamActivityListener(teamManager, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(teamManager, teamChatManager), this);
        Bukkit.getPluginManager().registerEvents(new BannerListener(this, teamManager, vaultManager, opModeManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamVaultListener(vaultManager, bannerShopSettings, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new SoulTokenProtectionListener(tokenManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamBannerShopListener(teamManager, tokenManager, bannerShopSettings, effectSettings, dimensionBannerShopSettings, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new BeaconEffectsListener(effectSettings, tokenManager, teamManager, bannerShopSettings, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(teamManager, tokenManager, kothManager), this);
        Bukkit.getPluginManager().registerEvents(new EnderChestBlocker(), this);
        Bukkit.getPluginManager().registerEvents(new DisableEndCrystalExplosionsListener(), this);
        Bukkit.getPluginManager().registerEvents(new FirstJoinSpawnListener(this, kothManager), this);
        Bukkit.getPluginManager().registerEvents(new BankListener(bankManager, tokenManager, teamManager), this);
        Bukkit.getPluginManager().registerEvents(new CropGrowthBoostListener(teamManager), this);
        Bukkit.getPluginManager().registerEvents(new KothListener(this, kothManager), this);
        Bukkit.getPluginManager().registerEvents(new VoucherListener(this, teamManager, upkeepManager), this);
        Bukkit.getPluginManager().registerEvents(new VoucherDeliveryListener(voucherMail), this);
        getServer().getPluginManager().registerEvents(new AnvilBeyondVanillaListener(), this);
        getServer().getPluginManager().registerEvents(new SpawnListener(this), this);
    }

    private void restartTasks() {

        BeaconEffectManager aura = new BeaconEffectManager(teamManager);
        auraTaskId = Bukkit.getScheduler().runTaskTimer(this, aura::tick, 10L, 10L).getTaskId();

        long ticksPerDay = 20L * 60L * 60L * 24L;
        upkeepTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, upkeepManager::runDailyCheck, ticksPerDay, ticksPerDay)
                .getTaskId();

        if (tipBroadcaster == null) tipBroadcaster = new ServerTipBroadcaster(this);
        tipBroadcaster.reload();

        if (tipsTaskId != -1) Bukkit.getScheduler().cancelTask(tipsTaskId);
        tipsTaskId = -1;

        if (tipBroadcaster.isEnabled()) {
            int period = tipBroadcaster.getIntervalTicks();
            tipsTaskId = Bukkit.getScheduler().runTaskTimer(this, tipBroadcaster::broadcastNext, period, period).getTaskId();
            getLogger().info("[Tips] Enabled. Interval: " + (period / 20) + "s");
        } else {
            getLogger().info("[Tips] Disabled (or no messages configured).");
        }
    }
}
