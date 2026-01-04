package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.beacon.*;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.shop.BannerShopSettings;
import me.Evil.soulSMP.shop.TeamBannerShopGui;
import me.Evil.soulSMP.upkeep.TeamUpkeepManager;
import me.Evil.soulSMP.upkeep.UpkeepStatus;


import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

public class BeaconEffectsListener implements Listener {

    private final BeaconEffectSettings settings;
    private final SoulTokenManager tokens;
    private final TeamManager teams;
    private final BannerShopSettings bannerShopSettings;
    private final TeamUpkeepManager upkeepManager;

    public BeaconEffectsListener(BeaconEffectSettings s,
                                 SoulTokenManager t,
                                 TeamManager tm,
                                 BannerShopSettings bannerShopSettings,
                                 TeamUpkeepManager um) {
        this.settings = s;
        this.tokens = t;
        this.teams = tm;
        this.bannerShopSettings = bannerShopSettings;
        this.upkeepManager = um;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BeaconEffectsHolder holder)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        Team team = holder.getTeam();
        if (team == null) {
            player.closeInventory();
            return;
        }

        // 👉 ADD THIS:
        UpkeepStatus upkeep = team.getUpkeepStatus();
        if (upkeep == UpkeepStatus.UNPROTECTED) {
            player.sendMessage("§4Your banner is Unprotected due to unpaid upkeep.");
            player.sendMessage("§cBeacon upgrades are disabled until your team pays upkeep.");
            // Send them back to the main shop for clarity
            TeamBannerShopGui.open(player, team, bannerShopSettings);
            return;
        }
        // (UNSTABLE is allowed here, so no block)

        // Find effect by slot once
        BeaconEffectDefinition effect = settings.getAllEffects()
                .stream().filter(e -> e.getSlot() == slot)
                .findFirst().orElse(null);

        if (effect == null) return;

        if (effect.isBackButton()) {
            TeamBannerShopGui.open(player, team, bannerShopSettings, upkeepManager);
            return;
        }

        int current = team.getEffectLevel(effect.getId());
        int max = effect.getMaxLevel();

        if (current >= max) {
            player.sendMessage("§cThis effect is already maxed!");
            return;
        }

        BeaconEffectLevel next = effect.getLevel(current + 1);
        int cost = next.getCost();

        if (tokens.countTokensInInventory(player) < cost) {
            player.sendMessage("§cYou need " + cost + " Soul Tokens.");
            return;
        }

        // Remove tokens
        tokens.removeTokensFromPlayer(player, cost);

        // Upgrade
        team.setEffectLevel(effect.getId(), current + 1);
        teams.saveTeam(team);

        player.sendMessage("§aUpgraded " + effect.getDisplayName()
                + "§a to level " + (current + 1) + ".");

        // Refresh GUI
        BeaconEffectsGui.open(player, team, settings);
    }
}
