package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.vouchers.VoucherMailManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class VoucherDeliveryListener implements Listener {

    private final VoucherMailManager mail;

    public VoucherDeliveryListener(VoucherMailManager mail) {
        this.mail = mail;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p == null) return;

        List<ItemStack> items = mail.claim(p.getUniqueId());
        if (items.isEmpty()) return;

        int delivered = 0;
        for (ItemStack it : items) {
            var leftover = p.getInventory().addItem(it);
            delivered += (leftover.isEmpty() ? 1 : 0);

            if (!leftover.isEmpty()) {
                leftover.values().forEach(stack -> p.getWorld().dropItemNaturally(p.getLocation(), stack));
            }
        }

        p.sendMessage(ChatColor.GOLD + "You received " + ChatColor.AQUA + items.size()
                + ChatColor.GOLD + " voucher item(s) while you were offline.");
    }
}
