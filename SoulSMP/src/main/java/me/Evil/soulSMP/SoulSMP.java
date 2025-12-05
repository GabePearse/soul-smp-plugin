package me.Evil.soulSMP;

import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.commands.TeamWhisperCommand;
import me.Evil.soulSMP.commands.TeamCommand;
import me.Evil.soulSMP.commands.TeamChatToggleCommand;
import me.Evil.soulSMP.listeners.TeamActivityListener;
import me.Evil.soulSMP.listeners.TeamChatListener;
import me.Evil.soulSMP.listeners.BannerListener;
import me.Evil.soulSMP.listeners.TeamVaultListener;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.vault.TeamVaultManager;
import org.bukkit.plugin.java.JavaPlugin;

public class SoulSMP extends JavaPlugin {

    private TeamManager teamManager;
    private TeamChatManager teamChatManager;
    private TeamVaultManager vaultManager;


    @Override
    public void onEnable() {

        // ---------------------------
        // TEAM MANAGER SETUP
        // ---------------------------
        teamManager = new TeamManager(this);
        teamChatManager = new TeamChatManager();
        vaultManager = new TeamVaultManager(this, teamManager);

        vaultManager.loadVaults();
        teamManager.loadTeams();   // IMPORTANT: load BEFORE listeners register


        // ---------------------------
        // REGISTER COMMANDS
        // ---------------------------

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

        // ---------------------------
        // REGISTER LISTENERS
        // ---------------------------
        getServer().getPluginManager().registerEvents(new TeamActivityListener(teamManager), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(teamManager, teamChatManager), this);
        getServer().getPluginManager().registerEvents(new BannerListener(teamManager, vaultManager), this);
        getServer().getPluginManager().registerEvents(new TeamVaultListener(vaultManager), this);

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

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public TeamChatManager getTeamChatManager() {
        return teamChatManager;
    }
}
