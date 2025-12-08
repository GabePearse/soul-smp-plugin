package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.shop.*;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.upgrades.BeaconEffectSettings;
import me.Evil.soulSMP.upgrades.BeaconEffectsGui;
import me.Evil.soulSMP.vault.TeamVaultManager;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Locale;


public class TeamBannerShopListener implements Listener {

    private final TeamManager teamManager;
    private final SoulTokenManager tokenManager;
    private final BannerShopSettings settings;
    private final BeaconEffectSettings effSettings;
    private final DimensionBannerShopSettings dimensionSettings;

    public TeamBannerShopListener(TeamManager teamManager,
                                  SoulTokenManager tokenManager,
                                  BannerShopSettings mainSettings,
                                  BeaconEffectSettings effSettings,
                                  DimensionBannerShopSettings dimensionSettings) {
        this.teamManager = teamManager;
        this.tokenManager = tokenManager;
        this.settings = mainSettings;
        this.effSettings = effSettings;
        this.dimensionSettings = dimensionSettings;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Object holder = top.getHolder();

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Main banner shop
        if (holder instanceof TeamBannerShopHolder mainHolder) {
            event.setCancelled(true);
            handleMainShopClick(event, player, top, mainHolder);
            return;
        }

        // Dimension banner shop
        if (holder instanceof DimensionBannerShopHolder dimHolder) {
            event.setCancelled(true);
            handleDimensionShopClick(event, player, top, dimHolder);
        }
    }

    private void handleMainShopClick(InventoryClickEvent event, Player player,
                                     Inventory top, TeamBannerShopHolder holder) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;

        Team team = holder.getTeam();
        if (team == null) {
            player.sendMessage(ChatColor.RED + "Error: shop has no team associated.");
            player.closeInventory();
            return;
        }

        Team currentTeam = teamManager.getTeamByPlayer(player);
        if (currentTeam == null || !currentTeam.equals(team)) {
            player.sendMessage(ChatColor.RED + "You are no longer in this team.");
            player.closeInventory();
            return;
        }

        BannerShopItem item = settings.getItemBySlot(rawSlot);
        if (item == null) return;

        switch (item.getType()) {
            case CLOSE -> handleClose(player);
            case RADIUS -> handleRadiusUpgrade(player, currentTeam, item);
            case BEACON_MENU -> handleBeaconMenu(player, currentTeam);
            case LIVES -> handleLivesPurchase(player, currentTeam, item);
            case STORAGE -> handleStorageUpgrade(player, currentTeam, item);
            case DIMENSION_MENU -> handleDimensionMenuOpen(player, currentTeam); // 🔹 NEW
            case UNKNOWN, DIMENSION_BANNER, DIMENSION_TELEPORT -> {
                // DIMENSION_* should not be in main shop if you move them; ignore here
            }
        }
    }

    private void handleDimensionMenuOpen(Player player, Team team) {
        player.closeInventory();
        DimensionBannerShopGui.open(player, team, dimensionSettings);
    }

    private void handleDimensionShopClick(InventoryClickEvent event, Player player,
                                          Inventory top, DimensionBannerShopHolder holder) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;

        Team team = holder.getTeam();
        if (team == null) {
            player.closeInventory();
            return;
        }

        Team currentTeam = teamManager.getTeamByPlayer(player);
        if (currentTeam == null || !currentTeam.equals(team)) {
            player.sendMessage(ChatColor.RED + "You are no longer in this team.");
            player.closeInventory();
            return;
        }

        BannerShopItem item = dimensionSettings.getItemBySlot(rawSlot);
        if (item == null) return;

        switch (item.getType()) {
            case CLOSE -> {
                // Back to main banner shop
                TeamBannerShopGui.open(player, team, settings);
            }
            case DIMENSION_BANNER -> handleDimensionalBannerUnlock(player, currentTeam, item);
            case DIMENSION_TELEPORT -> handleDimensionalTeleport(player, currentTeam, item);
            default -> {
                // ignore other types in this menu
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

        // Can't expand if team is out of lives
        if (team.getLives() <= 0) {
            player.sendMessage(ChatColor.DARK_RED + "Your team has no lives left.");
            player.sendMessage(ChatColor.RED + "You cannot expand your claim while your banner is doomed.");
            player.closeInventory();
            return;
        }

        // Already at max radius
        if (currentRadius >= maxRadius) {
            player.sendMessage(ChatColor.RED + "Your team's claim radius is already at the maximum.");
            return;
        }

        // Cost is based on the *current* radius
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

        // ✅ Apply new radius, THEN save to teams.yml
        int newRadius = currentRadius + 1;
        team.setClaimRadius(newRadius);
        teamManager.saveTeam(team); // <--- make sure this line exists and stays here

        player.sendMessage(ChatColor.GREEN + "Your team's claim radius has been increased to "
                + ChatColor.AQUA + newRadius + ChatColor.GREEN + " chunks!");
        player.sendMessage(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + cost + " Soul Tokens");

        // Refresh the shop to show updated radius / cost
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

    private void handleDimensionalBannerUnlock(Player player, Team team, BannerShopItem item) {
        String dimKey = item.getDimensionKey();
        if (dimKey == null) {
            player.sendMessage(ChatColor.RED + "This shop item has no dimension configured.");
            return;
        }
        dimKey = dimKey.toUpperCase(Locale.ROOT);

        if (team.hasDimensionalBannerUnlocked(dimKey)) {
            player.sendMessage(ChatColor.RED + "Your team already unlocked the "
                    + niceDimensionName(dimKey) + " banner.");
            return;
        }

        int cost = item.getBaseCost();
        int tokens = tokenManager.countTokensInInventory(player);
        if (tokens < cost) {
            player.sendMessage(ChatColor.RED + "You need at least "
                    + ChatColor.AQUA + cost + ChatColor.RED
                    + " Soul Tokens to unlock the " + niceDimensionName(dimKey) + " banner.");
            return;
        }

        if (!tokenManager.removeTokensFromPlayer(player, cost)) {
            player.sendMessage(ChatColor.RED + "Could not remove Soul Tokens from your inventory.");
            return;
        }

        team.unlockDimensionalBanner(dimKey);
        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.GREEN + "Your team can now have a banner in the "
                + ChatColor.AQUA + niceDimensionName(dimKey) + ChatColor.GREEN + ".");
        TeamBannerShopGui.open(player, team, settings);
    }


    private void handleDimensionalTeleport(Player player, Team team, BannerShopItem item) {
        String dimKey = item.getDimensionKey();
        if (dimKey == null) {
            player.sendMessage(ChatColor.RED + "This shop item has no dimension configured.");
            return;
        }
        dimKey = dimKey.toUpperCase(Locale.ROOT);

        // Must have bought the banner itself first
        if (!team.hasDimensionalBannerUnlocked(dimKey)) {
            player.sendMessage(ChatColor.RED + "You must unlock the "
                    + niceDimensionName(dimKey) + " banner before buying teleportation.");
            return;
        }

        // If teleport not unlocked yet → this click is the purchase
        if (!team.hasDimensionalTeleportUnlocked(dimKey)) {
            int cost = item.getBaseCost();
            int tokens = tokenManager.countTokensInInventory(player);
            if (tokens < cost) {
                player.sendMessage(ChatColor.RED + "You need at least "
                        + ChatColor.AQUA + cost + ChatColor.RED
                        + " Soul Tokens to unlock teleportation to the "
                        + niceDimensionName(dimKey) + " banner.");
                return;
            }

            if (!tokenManager.removeTokensFromPlayer(player, cost)) {
                player.sendMessage(ChatColor.RED + "Could not remove Soul Tokens from your inventory.");
                return;
            }

            team.unlockDimensionalTeleport(dimKey);
            teamManager.saveTeam(team);

            player.sendMessage(ChatColor.GREEN + "Teleportation to your "
                    + ChatColor.AQUA + niceDimensionName(dimKey) + ChatColor.GREEN
                    + " banner is now unlocked!");
            TeamBannerShopGui.open(player, team, settings);
            return;
        }

        // Teleport is unlocked → now this click is the actual teleport
        Location loc = team.getDimensionalBanner(dimKey);
        if (loc == null || loc.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Your team has not set a "
                    + niceDimensionName(dimKey) + " banner location yet.");
            player.sendMessage(ChatColor.GRAY + "Place one of your team banners in that dimension to set it.");
            return;
        }

        player.teleport(loc);
        player.sendMessage(ChatColor.GREEN + "Teleported to your "
                + ChatColor.AQUA + niceDimensionName(dimKey) + ChatColor.GREEN + " banner.");
    }



    private String niceDimensionName(String dimKey) {
        if (dimKey == null) return "Unknown Dimension";
        dimKey = dimKey.toUpperCase(Locale.ROOT);
        return switch (dimKey) {
            case "NORMAL", "OVERWORLD" -> "Overworld";
            case "NETHER" -> "Nether";
            case "THE_END", "END" -> "The End";
            default -> dimKey;
        };
    }
}
