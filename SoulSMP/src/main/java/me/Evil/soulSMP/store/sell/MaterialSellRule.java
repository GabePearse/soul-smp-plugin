package me.Evil.soulSMP.store.sell;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MaterialSellRule {
    public final org.bukkit.Material material;
    public final int unit;
    public final int payout;

    public MaterialSellRule(org.bukkit.Material material, int unit, int payout) {
        this.material = material;
        this.unit = Math.max(1, unit);
        this.payout = Math.max(0, payout);
    }

    public int sellFromStack(Player player, int slot, ItemStack stack) {
        if (payout <= 0) return 0;

        int amount = stack.getAmount();
        int bundles = amount / unit;
        if (bundles <= 0) return 0;

        int remove = bundles * unit;
        int remain = amount - remove;

        if (remain <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            stack.setAmount(remain);
            player.getInventory().setItem(slot, stack);
        }

        return bundles * payout;
    }
}
