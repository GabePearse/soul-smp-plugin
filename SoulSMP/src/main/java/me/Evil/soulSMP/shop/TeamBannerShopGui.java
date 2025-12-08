package me.Evil.soulSMP.shop;

import me.Evil.soulSMP.team.Team;
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

    private static final int SHOP_SIZE = 54;

    public static void open(Player player, Team team, BannerShopSettings settings) {
        if (player == null || team == null || settings == null) return;

        TeamBannerShopHolder holder = new TeamBannerShopHolder(team);
        String title = ChatColor.DARK_AQUA + "Team Banner Upgrades";

        Inventory inv = Bukkit.createInventory(holder, SHOP_SIZE, title);
        holder.setInventory(inv);

        // Filler background
        ItemStack filler = new ItemStack(settings.getFillerMaterial());
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(settings.getFillerName());
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        // Place each shop item from config
        for (BannerShopItem item : settings.getItems()) {
            int slot = item.getSlot();
            if (slot < 0 || slot >= inv.getSize()) continue;

            ItemStack stack = new ItemStack(item.getMaterial());
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(item.getDisplayName());

                List<String> lore = new ArrayList<>();
                for (String line : item.getLore()) {
                    lore.add(applyPlaceholders(line, item, team));
                }
                meta.setLore(lore);

                stack.setItemMeta(meta);
            }

            inv.setItem(slot, stack);
        }

        player.openInventory(inv);
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
                // lives are flat cost here; multiplier can still be used if you want scaling
                int cost = item.getScaledCost(0);

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
