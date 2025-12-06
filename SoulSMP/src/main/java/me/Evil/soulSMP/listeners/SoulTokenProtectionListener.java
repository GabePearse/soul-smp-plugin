package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.tokens.SoulTokenManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

public class SoulTokenProtectionListener implements Listener {

    private final SoulTokenManager tokenManager;

    public SoulTokenProtectionListener(SoulTokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    /**
     * Prevent Soul Tokens from being used in ANY crafting recipe.
     */
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item == null) continue;

            if (tokenManager.isToken(item)) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage("§cYou cannot use Soul Tokens in crafting recipes.");
                return;
            }
        }
    }

    /**
     * Prevent Soul Tokens from being renamed or modified in anvils.
     */
    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null) return;

        if (tokenManager.isToken(result)) {
            event.setResult(null); // Remove output
        }
    }
}
