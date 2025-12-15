package me.Evil.soulSMP.tokens;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class SoulTokenManager {

    private final Plugin plugin;
    private final NamespacedKey key; // NBT identifier for Soul Tokens

    public SoulTokenManager(Plugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "soul_token");
    }

    /**
     * Creates a Soul Token (NBT-tagged Nether Star).
     */
    public ItemStack createToken(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Soul Token");
            meta.setLore(java.util.List.of(
                    ChatColor.GRAY + "The essence of fallen souls.",
                    ChatColor.GRAY + "Used as the primary currency."
            ));

            // Mark with NBT
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 1);

            // Prevent enchant glint manipulation later
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }

        return item;
    }

    public void giveTokens(Player player, int amount) {
        if (amount <= 0) return;

        int maxStack = 64;
        int remaining = amount;

        while (remaining > 0) {
            int stack = Math.min(maxStack, remaining);
            ItemStack tokenStack = createToken(stack);

            var leftover = player.getInventory().addItem(tokenStack);
            if (!leftover.isEmpty()) {
                // drop overflow at player feet
                leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
            }

            remaining -= stack;
        }
    }


    /**
     * Checks if an ItemStack is a Soul Token.
     */
    public boolean isToken(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.NETHER_STAR) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Integer val = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return val != null && val == 1;
    }

    /**
     * Counts how many Soul Tokens a player has in their inventory.
     */
    public int countTokensInInventory(Player player) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (isToken(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /**
     * Tries to remove the given number of Soul Tokens from a player's inventory.
     * Returns true if exactly that many were removed, false otherwise (and does not "refund").
     */
    public boolean removeTokensFromPlayer(Player player, int amount) {
        int toRemove = amount;
        Inventory inv = player.getInventory();

        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (!isToken(item)) continue;

            int stackAmount = item.getAmount();

            if (stackAmount <= toRemove) {
                // Remove entire stack
                inv.setItem(slot, null);
                toRemove -= stackAmount;
            } else {
                // Reduce stack
                item.setAmount(stackAmount - toRemove);
                toRemove = 0;
            }

            if (toRemove <= 0) break;
        }

        return toRemove == 0;
    }
}
