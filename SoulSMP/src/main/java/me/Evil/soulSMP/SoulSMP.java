package me.Evil.soulSMP;

import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.commands.SoulTokenCommand;
import me.Evil.soulSMP.commands.TeamWhisperCommand;
import me.Evil.soulSMP.commands.TeamCommand;
import me.Evil.soulSMP.commands.TeamChatToggleCommand;
import me.Evil.soulSMP.listeners.*;
import me.Evil.soulSMP.shop.BannerShopSettings;
import me.Evil.soulSMP.shop.DimensionBannerShopSettings;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.upgrades.BeaconEffectManager;
import me.Evil.soulSMP.upgrades.BeaconEffectSettings;
import me.Evil.soulSMP.vault.TeamVaultManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SoulSMP extends JavaPlugin {

    private TeamManager teamManager;
    private TeamChatManager teamChatManager;
    private TeamVaultManager vaultManager;
    private SoulTokenManager tokenManager;

    private BannerShopSettings bannerShopSettings;
    private BeaconEffectSettings effectSettings;
    private DimensionBannerShopSettings dimensionBannerShopSettings;

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
        // SETUP
        // ---------------------------
        teamManager = new TeamManager(this);
        teamChatManager = new TeamChatManager();
        vaultManager = new TeamVaultManager(this, teamManager);
        tokenManager = new SoulTokenManager(this);

        // Shop / effects settings
        bannerShopSettings = new BannerShopSettings(this);
        effectSettings = new BeaconEffectSettings(this);
        dimensionBannerShopSettings = new DimensionBannerShopSettings(this);

        vaultManager.loadVaults();
        teamManager.loadTeams();   // IMPORTANT: load BEFORE listeners register

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
        getServer().getPluginManager().registerEvents(new TeamActivityListener(teamManager), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(teamManager, teamChatManager), this);
        getServer().getPluginManager().registerEvents(new BannerListener(this, teamManager, vaultManager), this);
        getServer().getPluginManager().registerEvents(new TeamVaultListener(vaultManager, bannerShopSettings), this);
        getServer().getPluginManager().registerEvents(new SoulTokenProtectionListener(tokenManager), this);
        getServer().getPluginManager().registerEvents(
                new TeamBannerShopListener(
                        teamManager,
                        tokenManager,
                        bannerShopSettings,
                        effectSettings,
                        dimensionBannerShopSettings
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new BeaconEffectsListener(
                        effectSettings,
                        tokenManager,
                        teamManager,
                        bannerShopSettings
                ),
                this
        );
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(teamManager, tokenManager), this);

        // Beacon aura ticking
        BeaconEffectManager aura = new BeaconEffectManager(teamManager);
        Bukkit.getScheduler().runTaskTimer(this, aura::tick, 20L, 20L);

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

        // Reload teams.yml and update existing team objects
        if (teamManager != null) {
            teamManager.reloadTeamsFromFile();
        }

        // Reload vaults.yml (clearing the old cache in TeamVaultManager)
        if (vaultManager != null) {
            vaultManager.loadVaults();
        }

        // Unregister listeners, re-register them, and restart the aura task as before
        org.bukkit.event.HandlerList.unregisterAll(this);

        getServer().getPluginManager().registerEvents(new TeamActivityListener(teamManager), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(teamManager, teamChatManager), this);
        getServer().getPluginManager().registerEvents(new BannerListener(this, teamManager, vaultManager), this);
        getServer().getPluginManager().registerEvents(new TeamVaultListener(vaultManager, bannerShopSettings), this);
        getServer().getPluginManager().registerEvents(new SoulTokenProtectionListener(tokenManager), this);
        getServer().getPluginManager().registerEvents(
                new TeamBannerShopListener(
                        teamManager,
                        tokenManager,
                        bannerShopSettings,
                        effectSettings,
                        dimensionBannerShopSettings
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new BeaconEffectsListener(
                        effectSettings,
                        tokenManager,
                        teamManager,
                        bannerShopSettings
                ),
                this
        );
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(teamManager, tokenManager), this);

        // Restart the beacon aura task
        getServer().getScheduler().cancelTasks(this);
        BeaconEffectManager aura = new BeaconEffectManager(teamManager);
        Bukkit.getScheduler().runTaskTimer(this, aura::tick, 20L, 20L);

        getLogger().info("SoulSMP configuration files reloaded.");
    }
}
