package me.Evil.soulSMP.vouchers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VoucherItem {

    private VoucherItem() {}

    // PDC keys
    public static NamespacedKey KEY_TYPE(Plugin plugin) { return new NamespacedKey(plugin, "voucher_type"); }
    public static NamespacedKey KEY_AMOUNT(Plugin plugin) { return new NamespacedKey(plugin, "voucher_amount"); }
    public static NamespacedKey KEY_STRING(Plugin plugin) { return new NamespacedKey(plugin, "voucher_string"); } // effectId or dimension

    // -------------------------
    // Create
    // -------------------------

    /**
     * Creates an unspoofable voucher NAME_TAG using PDC.
     *
     * @param type   voucher type
     * @param amount integer amount (meaning depends on type)
     * @param extra  optional string (effect id / dimension key)
     * @param name   optional custom display name (supports & colors). If null/blank, a default is used.
     */
    public static ItemStack createVoucher(Plugin plugin, VoucherType type, int amount, String extra, String name) {
        if (plugin == null) throw new IllegalArgumentException("plugin cannot be null");
        if (type == null) throw new IllegalArgumentException("type cannot be null");

        ItemStack tag = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = tag.getItemMeta();
        if (meta == null) return tag;

        meta.getPersistentDataContainer().set(KEY_TYPE(plugin), PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(KEY_AMOUNT(plugin), PersistentDataType.INTEGER, amount);

        if (extra != null && !extra.isBlank()) {
            meta.getPersistentDataContainer().set(KEY_STRING(plugin), PersistentDataType.STRING, extra);
        } else {
            meta.getPersistentDataContainer().remove(KEY_STRING(plugin));
        }

        String display = (name != null && !name.isBlank())
                ? ChatColor.translateAlternateColorCodes('&', name)
                : defaultName(type, amount, extra);

        meta.setDisplayName(display);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Voucher");
        lore.add(ChatColor.GRAY + "Right-click to redeem.");
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        tag.setItemMeta(meta);
        return tag;
    }

    private static String defaultName(VoucherType type, int amount, String extra) {
        return switch (type) {
            case TEAM_LIFE_PLUS -> ChatColor.AQUA + "" + ChatColor.BOLD + "Team Life Voucher +" + Math.max(1, amount);
            case TEAM_LIFE_COST_RESET -> ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Team Life Cost Reset Voucher";
            case CLAIM_RADIUS_PLUS -> ChatColor.GREEN + "" + ChatColor.BOLD + "Claim Radius Voucher +" + Math.max(1, amount);
            case VAULT_SLOTS_PLUS -> ChatColor.GOLD + "" + ChatColor.BOLD + "Vault Slots Voucher +" + Math.max(1, amount);
            case EFFECT_LEVEL_SET -> ChatColor.BLUE + "" + ChatColor.BOLD + "Effect Set Voucher (" + (extra == null ? "?" : extra) + ") " + amount;
            case UPKEEP_WEEKS_CLEAR -> ChatColor.YELLOW + "" + ChatColor.BOLD + "Upkeep Weeks Clear Voucher";
            case UPKEEP_PAY_NOW -> ChatColor.YELLOW + "" + ChatColor.BOLD + "Upkeep Pay Now Voucher";
            case DIM_BANNER_UNLOCK -> ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Dim Banner Unlock (" + (extra == null ? "?" : extra) + ")";
            case DIM_TELEPORT_UNLOCK -> ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Dim Teleport Unlock (" + (extra == null ? "?" : extra) + ")";
        };
    }

    // -------------------------
    // Read
    // -------------------------

    public static boolean isVoucher(Plugin plugin, ItemStack stack) {
        if (plugin == null) return false;
        if (stack == null) return false;
        if (stack.getType() != Material.NAME_TAG) return false;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;

        return meta.getPersistentDataContainer().has(KEY_TYPE(plugin), PersistentDataType.STRING);
    }

    public static VoucherType getType(Plugin plugin, ItemStack stack) {
        if (!isVoucher(plugin, stack)) return null;

        ItemMeta meta = stack.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(KEY_TYPE(plugin), PersistentDataType.STRING);
        if (raw == null) return null;

        try {
            return VoucherType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static int getAmount(Plugin plugin, ItemStack stack, int def) {
        if (!isVoucher(plugin, stack)) return def;

        ItemMeta meta = stack.getItemMeta();
        Integer v = meta.getPersistentDataContainer().get(KEY_AMOUNT(plugin), PersistentDataType.INTEGER);
        return v == null ? def : v;
    }

    public static String getString(Plugin plugin, ItemStack stack) {
        if (!isVoucher(plugin, stack)) return null;

        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().get(KEY_STRING(plugin), PersistentDataType.STRING);
    }
}
