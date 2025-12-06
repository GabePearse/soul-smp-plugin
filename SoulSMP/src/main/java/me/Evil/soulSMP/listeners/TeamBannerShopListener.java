package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.shop.TeamBannerShopGui;
import me.Evil.soulSMP.shop.TeamBannerShopHolder;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.shop.BannerShopItem;
import me.Evil.soulSMP.shop.BannerShopSettings;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.upgrades.BeaconEffectSettings;
import me.Evil.soulSMP.upgrades.BeaconEffectsGui;
import me.Evil.soulSMP.vault.TeamVaultManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class TeamBannerShopListener implements Listener {

    private final TeamManager teamManager;
    private final SoulTokenManager tokenManager;
    private final BannerShopSettings settings;
    private final BeaconEffectSettings effSettings;

    public TeamBannerShopListener(TeamManager teamManager,
                                  SoulTokenManager tokenManager,
                                  BannerShopSettings settings, BeaconEffectSettings effSettings) {
        this.teamManager = teamManager;
        this.tokenManager = tokenManager;
        this.settings = settings;
        this.effSettings = effSettings;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TeamBannerShopHolder holder)) return;

        event.setCancelled(true); // block item movement

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;

        Team team = holder.getTeam();
        if (team == null) {
            player.sendMessage(ChatColor.RED + "Error: shop has no team associated.");
            return;
        }

        Team currentTeam = teamManager.getTeamByPlayer(player);
        if (currentTeam == null || !currentTeam.equals(team)) {
            player.sendMessage(ChatColor.RED + "You are no longer in this team.");
            player.closeInventory();
            return;
        }

        BannerShopItem item = settings.getItemBySlot(rawSlot);
        if (item == null) {
            return; // filler / empty slot
        }

        switch (item.getType()) {
            case CLOSE -> handleClose(player);
            case RADIUS -> handleRadiusUpgrade(player, currentTeam, item);
            case BEACON_MENU -> handleBeaconMenu(player, currentTeam);
            case LIVES -> handleLivesPurchase(player, currentTeam, item);
            case STORAGE -> handleStorageUpgrade(player, currentTeam, item);
            case UNKNOWN -> {
                // ignore
            }
        }
    }

    private void handleClose(Player player) {
        player.closeInventory();
    }

    private void handleBeaconMenu(Player player, Team team) {
        player.closeInventory();
        BeaconEffectsGui.open(player, team, effSettings);
    }

    private void handleRadiusUpgrade(Player player, Team team, BannerShopItem item) {
        int currentRadius = team.getClaimRadius();
        int maxRadius = item.getMaxRadius();

        if (team.getLives() <= 0) {
            player.sendMessage(ChatColor.DARK_RED + "Your team has no lives left.");
            player.sendMessage(ChatColor.RED + "You cannot expand your claim while your banner is doomed.");
            player.closeInventory();
            return;
        }

        if (currentRadius >= maxRadius) {
            player.sendMessage(ChatColor.RED + "Your team's claim radius is already at the maximum.");
            return;
        }

        int cost = item.getRadiusUpgradeCost(currentRadius);
        int tokens = tokenManager.countTokensInInventory(player);

        if (tokens < cost) {
            player.sendMessage(ChatColor.RED + "You need at least "
                    + ChatColor.AQUA + cost + ChatColor.RED
                    + " Soul Tokens to upgrade your claim radius.");
            return;
        }

        boolean removed = tokenManager.removeTokensFromPlayer(player, cost);
        if (!removed) {
            player.sendMessage(ChatColor.RED + "Could not remove Soul Tokens from your inventory.");
            return;
        }

        team.setClaimRadius(currentRadius + 1);
        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.GREEN + "Your team's claim radius has been increased to "
                + ChatColor.AQUA + team.getClaimRadius() + ChatColor.GREEN + " chunks!");
        player.sendMessage(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + cost + " Soul Tokens");

        TeamBannerShopGui.open(player, team, settings);
    }

    private void handleLivesPurchase(Player player, Team team, BannerShopItem item) {
        int cost = item.getBaseCost(); // flat cost (100 from YAML by default)
        int tokens = tokenManager.countTokensInInventory(player);

        if (tokens < cost) {
            player.sendMessage(ChatColor.RED + "You need at least "
                    + ChatColor.AQUA + cost + ChatColor.RED
                    + " Soul Tokens to buy an extra life.");
            return;
        }

        boolean removed = tokenManager.removeTokensFromPlayer(player, cost);
        if (!removed) {
            player.sendMessage(ChatColor.RED + "Could not remove Soul Tokens from your inventory.");
            return;
        }

        // Give 1 life per purchase (tweak if you want more)
        team.addLives(1);
        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.GREEN + "Your team has gained "
                + ChatColor.AQUA + "1" + ChatColor.GREEN + " extra life!");
        player.sendMessage(ChatColor.AQUA + "Total lives: " + ChatColor.YELLOW + team.getLives());

        // Refresh the shop to update "current lives" display
        TeamBannerShopGui.open(player, team, settings);
    }

    private void handleStorageUpgrade(Player player, Team team, BannerShopItem item) {
        int currentSlots = team.getVaultSize();

        // Last slot in vault is reserved for shop icon, so max usable = size - 1
        int maxSlots = TeamVaultManager.VAULT_INVENTORY_SIZE - 1;
        if (currentSlots >= maxSlots) {
            player.sendMessage(ChatColor.RED + "Your team vault is already at maximum capacity.");
            return;
        }

        int cost = item.getScaledCost(currentSlots - 1);
        int tokens = tokenManager.countTokensInInventory(player);

        if (tokens < cost) {
            player.sendMessage(ChatColor.RED + "You need at least "
                    + ChatColor.AQUA + cost + ChatColor.RED
                    + " Soul Tokens to upgrade your vault slots.");
            return;
        }

        boolean removed = tokenManager.removeTokensFromPlayer(player, cost);
        if (!removed) {
            player.sendMessage(ChatColor.RED + "Could not remove Soul Tokens from your inventory.");
            return;
        }

        // Increase vault size by 1 slot
        team.setVaultSize(currentSlots + 1);
        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.GREEN + "Your team vault size has increased to "
                + ChatColor.AQUA + team.getVaultSize() + ChatColor.GREEN + " slots!");
        player.sendMessage(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + cost + " Soul Tokens");

        // Refresh shop GUI with new values
        TeamBannerShopGui.open(player, team, settings);
    }
}
