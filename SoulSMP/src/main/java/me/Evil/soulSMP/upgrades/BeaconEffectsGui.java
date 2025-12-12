package me.Evil.soulSMP.upgrades;

import me.Evil.soulSMP.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BeaconEffectsGui {

    private static final int SIZE = 27;
    private static final String TITLE = ChatColor.DARK_AQUA + "Beacon Effects";

    public static void open(Player player, Team team, BeaconEffectSettings settings) {
        if (player == null || team == null || settings == null) return;

        BeaconEffectsHolder holder = new BeaconEffectsHolder(team);
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inv);

        // Filler
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(" ");
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Effects (including BACK) from config
        for (BeaconEffectDefinition effect : settings.getAllEffects()) {
            int slot = effect.getSlot();
            if (slot < 0 || slot >= SIZE) continue;

            // SPECIAL CASE: BACK BUTTON
            if (effect.isBackButton()) {
                ItemStack back = new ItemStack(
                        effect.getMaterial() != null ? effect.getMaterial() : Material.ARROW
                );
                ItemMeta bm = back.getItemMeta();
                if (bm != null) {
                    String name = effect.getDisplayName() != null
                            ? effect.getDisplayName()
                            : ChatColor.YELLOW + "Back";
                    bm.setDisplayName(name);

                    // Use level 1 lore if defined, otherwise no lore
                    List<String> lore = new ArrayList<>();
                    BeaconEffectLevel lvl1 = effect.getLevel(1);
                    if (lvl1 != null && lvl1.getLore() != null) {
                        lore.addAll(lvl1.getLore());
                    }
                    if (!lore.isEmpty()) {
                        bm.setLore(lore);
                    }

                    back.setItemMeta(bm);
                }
                inv.setItem(slot, back);
                continue;
            }

            // NORMAL UPGRADE EFFECTS
            int current = team.getEffectLevel(effect.getId());
            int max = effect.getMaxLevel();

            ItemStack item = new ItemStack(
                    effect.getMaterial() != null ? effect.getMaterial() : Material.STONE
            );
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            meta.setDisplayName(effect.getDisplayName());

            List<String> lore = new ArrayList<>();

            if (max <= 0) {
                lore.add(ChatColor.RED + "No levels configured for this effect.");
            } else if (current >= max) {
                lore.add(ChatColor.GREEN + "MAXED");
            } else {
                BeaconEffectLevel next = effect.getLevel(current + 1);
                if (next != null) {
                    for (String line : next.getLore()) {
                        lore.add(line.replace("{cost}", String.valueOf(next.getCost())));
                    }
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Click to upgrade to level " + (current + 1));
                } else {
                    lore.add(ChatColor.RED + "Next level is not configured.");
                }
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }

        // ✅ Only open – no extra hard-coded back arrow anymore
        player.openInventory(inv);
    }
}
