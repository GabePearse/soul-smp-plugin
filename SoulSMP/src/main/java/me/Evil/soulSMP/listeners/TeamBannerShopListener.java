package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.bannershop.*;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.beacon.BeaconEffectSettings;
import me.Evil.soulSMP.beacon.BeaconEffectsGui;
import me.Evil.soulSMP.vault.TeamVaultManager;
import me.Evil.soulSMP.upkeep.TeamUpkeepManager;
import me.Evil.soulSMP.upkeep.UpkeepStatus;
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
    private final TeamUpkeepManager upkeepManager;

    public TeamBannerShopListener(TeamManager teamManager,
                                  SoulTokenManager tokenManager,
                                  BannerShopSettings mainSettings,
                                  BeaconEffectSettings effSettings,
                                  DimensionBannerShopSettings dimensionSettings,
                                  TeamUpkeepManager upkeepManager) {
        this.teamManager = teamManager;
        this.tokenManager = tokenManager;
        this.settings = mainSettings;
        this.effSettings = effSettings;
        this.dimensionSettings = dimensionSettings;
        this.upkeepManager = upkeepManager;
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

        UpkeepStatus upkeep = currentTeam.getUpkeepStatus();

        switch (item.getType()) {

            case CLOSE -> handleClose(player);

            case BACK -> player.closeInventory();

            case UPKEEP -> {
                // ✅ Always allowed for any team member
                handleUpkeepPayment(player, currentTeam);
            }

            case RADIUS, BEACON_MENU, LIVES, STORAGE, DIMENSION_MENU -> {
                // ❌ Block these if Unprotected
                if (upkeep == UpkeepStatus.UNPROTECTED) {
                    player.sendMessage(ChatColor.DARK_RED + "Your banner is Unprotected due to unpaid upkeep.");
                    player.sendMessage(ChatColor.RED + "All banner perks are disabled until upkeep is paid.");
                    return;
                }

                // Normal behavior
                switch (item.getType()) {
                    case RADIUS -> handleRadiusUpgrade(player, currentTeam, item);
                    case BEACON_MENU -> handleBeaconMenu(player, currentTeam);
                    case LIVES -> handleLivesPurchase(player, currentTeam, item);
                    case STORAGE -> handleStorageUpgrade(player, currentTeam, item);
                    case DIMENSION_MENU -> handleDimensionMenuOpen(player, currentTeam);
                }
            }

            case DIMENSION_BANNER, DIMENSION_TELEPORT -> {
                // Those are handled in handleDimensionShopClick, not here
            }

            case UNKNOWN -> {
            }
        }
    }

    private void handleUpkeepPayment(Player player, Team team) {
        upkeepManager.payUpkeep(player, team);
        TeamBannerShopGui.open(player, team, settings, upkeepManager);
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
            case CLOSE -> TeamBannerShopGui.open(player, team, settings, upkeepManager);
            case DIMENSION_BANNER -> handleDimensionalBannerUnlock(player, currentTeam, item);
            case DIMENSION_TELEPORT -> handleDimensionalTeleport(player, currentTeam, item);
            case BACK -> TeamBannerShopGui.open(player, team, settings, upkeepManager);
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

        int newRadius = currentRadius + 1;
        teamManager.setClaimRadius(team, newRadius);

        player.sendMessage(ChatColor.GREEN + "Your team's claim radius has been increased to "
                + ChatColor.AQUA + newRadius + ChatColor.GREEN + " chunks!");
        player.sendMessage(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + cost + " Soul Tokens");

        TeamBannerShopGui.open(player, team, settings, upkeepManager);
    }

    /**
     * Lives cost uses "livesPurchased" (lifetime upgrades), NOT current lives.
     * This prevents the "stay low then buy when needed" exploit.
     *
     * Next life cost:
     *   cost = baseCost * multiplier^(livesPurchased)
     */
    private void handleLivesPurchase(Player player, Team team, BannerShopItem item) {

        int stepIndex = Math.max(0, team.getLivesPurchased());
        int cost = item.getScaledCost(stepIndex);

        int tokens = tokenManager.countTokensInInventory(player);
        if (tokens < cost) {
            player.sendMessage(ChatColor.RED + "You need at least "
                    + ChatColor.AQUA + cost + ChatColor.RED
                    + " Soul Tokens to buy an extra life.");
            return;
        }

        if (!tokenManager.removeTokensFromPlayer(player, cost)) {
            player.sendMessage(ChatColor.RED + "Could not remove Soul Tokens from your inventory.");
            return;
        }

        // Purchase succeeds: add life + record lifetime purchase
        team.addLives(1);
        team.addLivesPurchased(1);
        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.GREEN + "Your team has gained "
                + ChatColor.AQUA + "1" + ChatColor.GREEN + " extra life!");
        player.sendMessage(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + cost + " Soul Tokens");
        player.sendMessage(ChatColor.AQUA + "Total lives: " + ChatColor.YELLOW + team.getLives());

        TeamBannerShopGui.open(player, team, settings, upkeepManager);
    }

    private void handleStorageUpgrade(Player player, Team team, BannerShopItem item) {
        int currentSlots = team.getVaultSize();

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

        if (!tokenManager.removeTokensFromPlayer(player, cost)) {
            player.sendMessage(ChatColor.RED + "Could not remove Soul Tokens from your inventory.");
            return;
        }

        team.setVaultSize(currentSlots + 1);
        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.GREEN + "Your team vault size has increased to "
                + ChatColor.AQUA + team.getVaultSize() + ChatColor.GREEN + " slots!");
        player.sendMessage(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + cost + " Soul Tokens");

        TeamBannerShopGui.open(player, team, settings, upkeepManager);
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
        TeamBannerShopGui.open(player, team, settings, upkeepManager);
    }

    private void handleDimensionalTeleport(Player player, Team team, BannerShopItem item) {
        String dimKey = item.getDimensionKey();
        if (dimKey == null) {
            player.sendMessage(ChatColor.RED + "This shop item has no dimension configured.");
            return;
        }
        dimKey = dimKey.toUpperCase(Locale.ROOT);

        UpkeepStatus upkeep = team.getUpkeepStatus();
        if (upkeep == UpkeepStatus.UNSTABLE) {
            player.sendMessage(ChatColor.YELLOW + "Your team is Unstable.");
            player.sendMessage(ChatColor.RED + "Teleporting to banners is disabled until upkeep is paid.");
            return;
        }
        if (upkeep == UpkeepStatus.UNPROTECTED) {
            player.sendMessage(ChatColor.DARK_RED + "Your banner is Unprotected due to unpaid upkeep.");
            player.sendMessage(ChatColor.RED + "Teleporting to banners is disabled until upkeep is paid.");
            return;
        }

        if (!"OVERWORLD".equals(dimKey) && !team.hasDimensionalBannerUnlocked(dimKey)) {
            player.sendMessage(ChatColor.RED + "You must unlock the "
                    + niceDimensionName(dimKey) + " banner before buying teleportation.");
            return;
        }

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
            TeamBannerShopGui.open(player, team, settings, upkeepManager);
            return;
        }

        Location loc;
        if ("OVERWORLD".equals(dimKey)) {
            loc = team.getBannerLocation();
        } else {
            loc = team.getDimensionalBanner(dimKey);
        }

        if (loc == null || loc.getWorld() == null) {
            if ("OVERWORLD".equals(dimKey)) {
                player.sendMessage(ChatColor.RED + "Your team does not have a main banner placed in the Overworld.");
                player.sendMessage(ChatColor.GRAY + "Place one of your team banners in the Overworld to set it.");
            } else {
                player.sendMessage(ChatColor.RED + "Your team has not set a "
                        + niceDimensionName(dimKey) + " banner location yet.");
                player.sendMessage(ChatColor.GRAY + "Place one of your team banners in that dimension to set it.");
            }
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
