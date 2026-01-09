package me.Evil.soulSMP.bannershop;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.upkeep.TeamUpkeepManager;
import me.Evil.soulSMP.upkeep.UpkeepStatus;
import me.Evil.soulSMP.util.CostUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Banner Shop GUI from YAML config.
 */
public class TeamBannerShopGui {

    private static final int DEFAULT_SHOP_SIZE = 54;

    public static void open(Player player,
                            Team team,
                            BannerShopSettings settings,
                            TeamUpkeepManager upkeepManager) {

        if (player == null || team == null || settings == null) return;

        int size = settings.getSize() > 0 ? settings.getSize() : DEFAULT_SHOP_SIZE;
        String title = settings.getTitle();

        TeamBannerShopHolder holder = new TeamBannerShopHolder(team);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // Fill background with filler item
        ItemStack filler = new ItemStack(settings.getFillerMaterial());
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(settings.getFillerName());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        // Precompute upkeep info if manager is provided
        int weeksOwed = 1;
        int totalCost = 0;
        String statusText = "";
        UpkeepStatus status = null;

        if (upkeepManager != null) {
            // Make sure the team state is current
            upkeepManager.updateTeamUpkeep(team);

            weeksOwed = Math.max(0, team.getUnpaidWeeks());
            status = team.getUpkeepStatus();
            totalCost = upkeepManager.getWeeklyCostBase() * weeksOwed;
            statusText = formatUpkeepStatus(status);
        }

        // Place each configured shop item
        for (BannerShopItem item : settings.getItems()) {
            int slot = item.getSlot();
            if (slot < 0 || slot >= inv.getSize()) continue;

            ItemStack stack = new ItemStack(item.getMaterial());
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) continue;

            // Name
            if (item.getDisplayName() != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        item.getDisplayName()));
            }

            // Lore
            List<String> cfgLore = item.getLore();
            if (cfgLore != null && !cfgLore.isEmpty()) {
                List<String> lore = new ArrayList<>();

                for (String raw : cfgLore) {
                    String line = ChatColor.translateAlternateColorCodes('&', raw);

                    // Dynamic lore for the upkeep item
                    if (item.getType() == BannerShopItem.ShopItemType.UPKEEP && upkeepManager != null) {
                        line = line
                                .replace("{weeks}", String.valueOf(weeksOwed))
                                .replace("{cost}", String.valueOf(totalCost))
                                .replace("{status}", statusText);
                    }

                    // Apply generic placeholders (radius, lives, storage, dimension unlock, etc.)
                    line = applyPlaceholders(line, item, team);

                    lore.add(line);
                }

                meta.setLore(lore);
            }

            stack.setItemMeta(meta);
            inv.setItem(slot, stack);
        }

        player.openInventory(inv);
    }

    // Backwards-compatible overload (if anything old still calls it)
    public static void open(Player player,
                            Team team,
                            BannerShopSettings settings) {
        open(player, team, settings, null);
    }

    private static String formatUpkeepStatus(UpkeepStatus status) {
        if (status == null) return ChatColor.GRAY + "Unknown";

        return switch (status) {
            case PROTECTED -> ChatColor.GREEN + "Protected";
            case UNSTABLE -> ChatColor.YELLOW + "Unstable";
            case UNPROTECTED -> ChatColor.DARK_RED + "Unprotected";
        };
    }

    private static String applyPlaceholders(String line, BannerShopItem item, Team team) {
        switch (item.getType()) {

            case RADIUS -> {
                int currentRadius = team.getClaimRadius();
                int max = item.getMaxRadius();
                // radius 1 -> step 0, radius 2 -> step 1, etc.
                int step = currentRadius - 1;
                int cost = CostUtils.computeCost(item.getBaseCost(), item.getCostMultiplier(), step);

                line = line
                        .replace("{current_radius}", String.valueOf(currentRadius))
                        .replace("{max_radius}", String.valueOf(max))
                        .replace("{cost}", String.valueOf(cost));
            }

            case LIVES -> {
                int lives = team.getLives();

                // IMPORTANT: lives cost scales using lifetime purchases, not current lives.
                int stepIndex = Math.max(0, team.getLivesPurchased());
                int cost = item.getScaledCost(stepIndex);

                line = line
                        .replace("{current_lives}", String.valueOf(lives))
                        .replace("{cost}", String.valueOf(cost));
            }

            case STORAGE -> {
                int currentSlots = team.getVaultSize();
                int cost = item.getScaledCost(currentSlots - 1);

                line = line
                        .replace("{current_slots}", String.valueOf(currentSlots))
                        .replace("{cost}", String.valueOf(cost));
            }

            case DIMENSION_BANNER -> {
                int cost = item.getBaseCost();
                boolean unlocked = team.hasDimensionalBannerUnlocked(item.getDimensionKey());
                line = line
                        .replace("{cost}", String.valueOf(cost))
                        .replace("{status}", unlocked ? "Unlocked" : "Locked");
            }

            case DIMENSION_TELEPORT -> {
                int cost = item.getBaseCost();
                boolean unlocked = team.hasDimensionalTeleportUnlocked(item.getDimensionKey());
                line = line
                        .replace("{cost}", String.valueOf(cost))
                        .replace("{status}", unlocked ? "Unlocked" : "Locked");
            }

            default -> {
                // no placeholders
            }
        }

        return line;
    }
}
