package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.vouchers.VoucherItem;
import me.Evil.soulSMP.vouchers.VoucherMailManager;
import me.Evil.soulSMP.vouchers.VoucherType;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.Locale;

public class VoucherCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final VoucherMailManager mail;

    public VoucherCommand(Plugin plugin, VoucherMailManager mail) {
        this.plugin = plugin;
        this.mail = mail;
    }

    // /voucher give <player> <type> [amount] [extra] [count]
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("give")) {
            sendHelp(sender, label);
            return true;
        }

        if (!sender.hasPermission("soulsmp.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " give <player> <type> [amount] [extra] [count]");
            return true;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        // Safety: don't create fake UUIDs for unknown players on online-mode servers
        if (target.getName() == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            sender.sendMessage(ChatColor.RED + "That player has never joined before (or name is invalid): " + targetName);
            return true;
        }

        VoucherType type;
        try {
            type = VoucherType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Unknown voucher type: " + args[2]);
            sender.sendMessage(ChatColor.GRAY + "Valid types: " + Arrays.toString(VoucherType.values()));
            return true;
        }

        int amount = 1;
        String extra = null;
        int count = 1;

        // args[3] = amount (optional)
        if (args.length >= 4) {
            Integer parsed = tryParseInt(args[3]);
            if (parsed != null) amount = parsed;
        }

        // args[4] = extra (optional)
        if (args.length >= 5) {
            extra = args[4];
        }

        // args[5] = count / stack size (optional)
        if (args.length >= 6) {
            Integer parsed = tryParseInt(args[5]);
            if (parsed != null) count = parsed;
        }

        amount = clamp(amount, 0, 1_000_000);
        count = clamp(count, 1, 64);

        if (requiresExtra(type) && (extra == null || extra.isBlank())) {
            sender.sendMessage(ChatColor.RED + "That voucher type requires an extra value.");
            sender.sendMessage(ChatColor.GRAY + "Examples:");
            sender.sendMessage(ChatColor.YELLOW + "/voucher give " + target.getName() + " EFFECT_LEVEL_SET 3 speed");
            sender.sendMessage(ChatColor.YELLOW + "/voucher give " + target.getName() + " DIM_BANNER_UNLOCK 1 NETHER");
            return true;
        }

        ItemStack voucher = createVoucher(type, amount, extra);
        voucher.setAmount(count);

        // If online: deliver now
        if (target.isOnline() && target.getPlayer() != null) {
            Player p = target.getPlayer();
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(voucher);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(stack -> p.getWorld().dropItemNaturally(p.getLocation(), stack));
            }

            sender.sendMessage(ChatColor.GREEN + "Gave " + ChatColor.AQUA + p.getName()
                    + ChatColor.GREEN + " voucher: " + ChatColor.YELLOW + type.name()
                    + ChatColor.GRAY + " (amount=" + amount
                    + (extra != null ? ", extra=" + extra : "")
                    + ", count=" + count + ")");

            if (p.isOnline()) {
                p.sendMessage(ChatColor.GREEN + "You received a Team Voucher!");
                p.sendMessage(ChatColor.GRAY + "Use it by right-clicking while holding it.");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            }

            return true;
        }

        // Offline: queue it
        mail.queue(target.getUniqueId(), voucher);

        sender.sendMessage(ChatColor.GREEN + "Queued voucher for offline player "
                + ChatColor.AQUA + target.getName()
                + ChatColor.GREEN + ": " + ChatColor.YELLOW + type.name()
                + ChatColor.GRAY + " (amount=" + amount
                + (extra != null ? ", extra=" + extra : "")
                + ", count=" + count + ")");
        return true;
    }

    private ItemStack createVoucher(VoucherType type, int amount, String extra) {
        ItemStack stack = new ItemStack(Material.NAME_TAG, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.setDisplayName(ChatColor.GOLD + prettyName(type));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Right-click to redeem for your team.");
        lore.add(ChatColor.DARK_GRAY + "Type: " + ChatColor.YELLOW + type.name());

        if (typeUsesAmount(type)) {
            lore.add(ChatColor.DARK_GRAY + "Amount: " + ChatColor.AQUA + amount);
        }
        if (extra != null && !extra.isBlank()) {
            lore.add(ChatColor.DARK_GRAY + "Extra: " + ChatColor.AQUA + extra);
        }
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey kType = VoucherItem.KEY_TYPE(plugin);
        NamespacedKey kAmt = VoucherItem.KEY_AMOUNT(plugin);
        NamespacedKey kStr = VoucherItem.KEY_STRING(plugin);

        pdc.set(kType, PersistentDataType.STRING, type.name());
        pdc.set(kAmt, PersistentDataType.INTEGER, amount);

        if (extra != null && !extra.isBlank()) {
            pdc.set(kStr, PersistentDataType.STRING, extra);
        } else {
            pdc.remove(kStr);
        }

        stack.setItemMeta(meta);
        return stack;
    }

    private static boolean requiresExtra(VoucherType type) {
        return type == VoucherType.EFFECT_LEVEL_SET
                || type == VoucherType.DIM_BANNER_UNLOCK
                || type == VoucherType.DIM_TELEPORT_UNLOCK;
    }

    private static boolean typeUsesAmount(VoucherType type) {
        return switch (type) {
            case TEAM_LIFE_PLUS, CLAIM_RADIUS_PLUS, VAULT_SLOTS_PLUS, EFFECT_LEVEL_SET -> true;
            default -> false;
        };
    }

    private static String prettyName(VoucherType type) {
        return switch (type) {
            case TEAM_LIFE_PLUS -> "Team Life Voucher";
            case TEAM_LIFE_COST_RESET -> "Team Life Cost Reset Voucher";
            case CLAIM_RADIUS_PLUS -> "Claim Radius Voucher";
            case VAULT_SLOTS_PLUS -> "Vault Slots Voucher";
            case EFFECT_LEVEL_SET -> "Effect Level Voucher";
            case UPKEEP_WEEKS_CLEAR -> "Upkeep Reset Voucher";
            case UPKEEP_PAY_NOW -> "Upkeep Paid Voucher";
            case DIM_BANNER_UNLOCK -> "Dimensional Banner Unlock Voucher";
            case DIM_TELEPORT_UNLOCK -> "Dimensional Teleport Unlock Voucher";
        };
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return null; }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "Voucher command:");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " give <player> <type> [amount] [extra] [count]");
        sender.sendMessage(ChatColor.GRAY + "Examples:");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " give Gabe TEAM_LIFE_PLUS 1");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " give Gabe TEAM_LIFE_COST_RESET");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " give Gabe EFFECT_LEVEL_SET 3 speed");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " give Gabe DIM_BANNER_UNLOCK 1 NETHER");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return startsWith(List.of("give"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return startsWith(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> types = new ArrayList<>();
            for (VoucherType t : VoucherType.values()) types.add(t.name());
            return startsWith(types, args[2]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            VoucherType t = null;
            try { t = VoucherType.valueOf(args[2].toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
            if (t == VoucherType.DIM_BANNER_UNLOCK || t == VoucherType.DIM_TELEPORT_UNLOCK) {
                return startsWith(List.of("OVERWORLD", "NETHER", "THE_END"), args[4]);
            }
            if (t == VoucherType.EFFECT_LEVEL_SET) {
                return startsWith(List.of("speed","haste","strength","regen","resistance","jump","radius"), args[4]);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> startsWith(List<String> options, String token) {
        if (token == null || token.isEmpty()) return options;
        String t = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        }
        return out;
    }
}
