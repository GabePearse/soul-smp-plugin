package me.Evil.soulSMP.anvil;

import me.Evil.soulSMP.SoulSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SoulAnvilGui {

    public static final int SIZE = 27;

    public static final int SLOT_LEFT   = 10;
    public static final int SLOT_OUT    = 13;
    public static final int SLOT_RIGHT  = 16;
    public static final int SLOT_RENAME = 22;

    // "Clear what is input/output" + validity status
    public static final int SLOT_LABEL_LEFT  = 9;
    public static final int SLOT_LABEL_RIGHT = 17;
    public static final int SLOT_ARROW_LEFT  = 12;
    public static final int SLOT_ARROW_RIGHT = 14;

    // Status panes are the arrows (they start black, turn green/red)
    public enum CraftStatus { NONE, VALID, INVALID }

    /** Only these slots are meant to be interacted with by the player. */
    public static boolean isInteractiveSlot(int slot) {
        return slot == SLOT_LEFT
                || slot == SLOT_RIGHT
                || slot == SLOT_OUT
                || slot == SLOT_RENAME;
    }

    /** Everything else is locked/filler/buttons that shouldn't move. */
    public static boolean isLockedSlot(int slot) {
        return !isInteractiveSlot(slot);
    }

    public static void open(Player player, SoulSMP plugin, SoulAnvilSession session) {
        var cfg = plugin.getConfig().getConfigurationSection("soul-anvil");

        String title = "&8Soul Anvil";
        Material fillerMat = Material.GRAY_STAINED_GLASS_PANE;
        String fillerName = "&8 ";
        if (cfg != null) {
            title = cfg.getString("title", title);
            fillerMat = Material.matchMaterial(cfg.getString("filler.material", fillerMat.name()));
            if (fillerMat == null) fillerMat = Material.GRAY_STAINED_GLASS_PANE;
            fillerName = cfg.getString("filler.name", fillerName);
        }

        SoulAnvilHolder holder = new SoulAnvilHolder(player.getUniqueId(), session);
        Inventory inv = Bukkit.createInventory(holder, SIZE, color(title));
        holder.setInventory(inv);

        // base filler
        ItemStack filler = makeItem(fillerMat, fillerName, null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // clear key slots
        inv.setItem(SLOT_LEFT, null);
        inv.setItem(SLOT_RIGHT, null);

        // output starts blocked
        setBlockedOutput(inv);

        // labels for clarity
        inv.setItem(SLOT_LABEL_LEFT, makeItem(Material.BLACK_STAINED_GLASS_PANE, "&7Input", List.of(
                "&8Shift-click items in.",
                "&8Or place manually."
        )));
        inv.setItem(SLOT_LABEL_RIGHT, makeItem(Material.BLACK_STAINED_GLASS_PANE, "&7Input", List.of(
                "&8Second item / book."
        )));

        // status panes (act like arrows + validity indicator)
        setStatus(inv, CraftStatus.NONE);

        // rename button
        setRenameButton(inv, session);

        // a tiny hint in the middle-top
        inv.setItem(4, makeItem(Material.ANVIL, "&bCombine Items", List.of(
                "&7Put items in the two inputs.",
                "&7Take result from output."
        )));

        player.openInventory(inv);
    }

    public static void setRenameButton(Inventory inv, SoulAnvilSession session) {
        String current = session.getRename();
        if (current == null) current = "";

        ItemStack rename = new ItemStack(Material.NAME_TAG);
        ItemMeta rm = rename.getItemMeta();
        if (rm != null) {
            rm.setDisplayName(color("&bRename"));
            rm.setLore(List.of(
                    color("&7Click to set the output name in chat."),
                    color("&7Type &ecancel &7to abort."),
                    color("&8Current: " + (current.isEmpty() ? "&7(none)" : current.replace("§", "&")))
            ));
            rename.setItemMeta(rm);
        }
        inv.setItem(SLOT_RENAME, rename);
    }

    public static void setBlockedOutput(Inventory inv) {
        inv.setItem(SLOT_OUT, makeItem(Material.BARRIER, "&cNo valid result", List.of(
                "&7Place valid items in both inputs.",
                "&8(If valid, this becomes the output.)"
        )));
    }

    public static void setStatus(Inventory inv, CraftStatus status) {
        Material mat;
        String name;
        List<String> lore;

        switch (status) {
            case VALID -> {
                mat = Material.LIME_STAINED_GLASS_PANE;
                name = "&aValid";
                lore = List.of("&7You can take the output.");
            }
            case INVALID -> {
                mat = Material.RED_STAINED_GLASS_PANE;
                name = "&cInvalid";
                lore = List.of("&7These items cannot combine.");
            }
            default -> {
                mat = Material.BLACK_STAINED_GLASS_PANE;
                name = "&7Waiting";
                lore = List.of("&8Add two inputs.");
            }
        }

        ItemStack pane = makeItem(mat, name, lore);
        inv.setItem(SLOT_ARROW_LEFT, pane);
        inv.setItem(SLOT_ARROW_RIGHT, pane);
    }

    private static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String s : lore) colored.add(color(s));
                meta.setLore(colored);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
