package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.upgrades.*;
import me.Evil.soulSMP.team.TeamManager;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

public class BeaconEffectsListener implements Listener {

    private final BeaconEffectSettings settings;
    private final SoulTokenManager tokens;
    private final TeamManager teams;

    public BeaconEffectsListener(BeaconEffectSettings s, SoulTokenManager t, TeamManager tm) {
        this.settings = s;
        this.tokens = t;
        this.teams = tm;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BeaconEffectsHolder holder)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == 26) {
            player.closeInventory();
            return;
        }

        Team team = holder.getTeam();
        if (team == null) return;

        // Find effect by slot
        BeaconEffectDefinition effect = settings.getAllEffects()
                .stream().filter(e -> e.getSlot() == slot)
                .findFirst().orElse(null);

        if (effect == null) return;

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
