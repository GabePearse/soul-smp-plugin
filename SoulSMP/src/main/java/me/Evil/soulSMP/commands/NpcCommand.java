package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.SoulSMP;
import me.Evil.soulSMP.npc.MannequinNpcManager;
import me.Evil.soulSMP.npc.NpcType;
import me.Evil.soulSMP.store.StoreManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /npc <create|remove|info>
 *
 * Uses the same Mannequin entity type you use in the leaderboard.
 *
 * Create usage:
 *   /npc create <shop|bank> [skinUsername] [displayName...]
 */
public class NpcCommand implements CommandExecutor, TabCompleter {

    private final SoulSMP plugin;
    private final MannequinNpcManager npcManager;
    @SuppressWarnings("unused")
    private final StoreManager storeManager;

    public NpcCommand(SoulSMP plugin, MannequinNpcManager npcManager, StoreManager storeManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.storeManager = storeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (!p.hasPermission("soulsmp.admin")) {
            p.sendMessage(ChatColor.RED + "You do not have permission to use this.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(p, label);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /" + label + " create <shop|bank> [skinUsername] [name...]");
                    return true;
                }

                NpcType type;
                try {
                    type = NpcType.valueOf(args[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    p.sendMessage(ChatColor.RED + "Unknown type. Use: shop or bank");
                    return true;
                }

                // Optional: skin username (single token)
                // Optional: display name (rest)
                String skinUsername = null;
                String nameRaw = null;

                if (args.length >= 3) {
                    skinUsername = args[2].trim();
                }
                if (args.length >= 4) {
                    nameRaw = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
                }

                Component name = null;
                if (nameRaw != null && !nameRaw.isBlank()) {
                    String colored = ChatColor.translateAlternateColorCodes('&', nameRaw);
                    String stripped = ChatColor.stripColor(colored);
                    if (stripped == null) stripped = nameRaw;

                    name = Component.text(stripped)
                            .color(type == NpcType.SHOP ? NamedTextColor.AQUA : NamedTextColor.DARK_AQUA)
                            .decorate(TextDecoration.BOLD);
                }

                // Spawn directly where player is standing (centered on block)
                Location spawn = getSpawnLocation(p);
                Mannequin m = npcManager.spawn(spawn, type, name, null);

                // Apply skin-by-username if provided
                if (skinUsername != null && !skinUsername.isBlank()) {
                    npcManager.applySkinByUsername(m, skinUsername);
                }

                p.sendMessage(ChatColor.GREEN + "Created " + ChatColor.AQUA + type.name().toLowerCase(Locale.ROOT)
                        + ChatColor.GREEN + " mannequin NPC.");
                p.sendMessage(ChatColor.DARK_GRAY + "EntityId: " + m.getEntityId());

                if (skinUsername != null && !skinUsername.isBlank()) {
                    p.sendMessage(ChatColor.GRAY + "Skin: " + ChatColor.AQUA + skinUsername);
                } else {
                    p.sendMessage(ChatColor.DARK_GRAY + "(No skin username provided)");
                }
                return true;
            }

            case "remove" -> {
                Entity looked = getLookedAtNpc(p, 6.0);
                if (looked == null) {
                    p.sendMessage(ChatColor.RED + "Look directly at a mannequin NPC within 6 blocks, then run this again.");
                    return true;
                }
                NpcType type = npcManager.getType(looked);
                looked.remove();
                p.sendMessage(ChatColor.YELLOW + "Removed " + (type != null ? type.name().toLowerCase(Locale.ROOT) : "") + " mannequin NPC.");
                return true;
            }

            case "info" -> {
                Entity looked = getLookedAtNpc(p, 6.0);
                if (looked == null) {
                    p.sendMessage(ChatColor.RED + "Look directly at a mannequin NPC within 6 blocks, then run this again.");
                    return true;
                }

                NpcType type = npcManager.getType(looked);
                p.sendMessage(ChatColor.AQUA + "NPC Type: " + ChatColor.WHITE + (type != null ? type.name() : "UNKNOWN"));
                p.sendMessage(ChatColor.AQUA + "UUID: " + ChatColor.WHITE + looked.getUniqueId());
                p.sendMessage(ChatColor.AQUA + "World: " + ChatColor.WHITE + looked.getWorld().getName());
                p.sendMessage(ChatColor.AQUA + "Location: " + ChatColor.WHITE
                        + looked.getLocation().getBlockX() + ", "
                        + looked.getLocation().getBlockY() + ", "
                        + looked.getLocation().getBlockZ());
                return true;
            }

            default -> {
                sendUsage(p, label);
                return true;
            }
        }
    }

    private void sendUsage(Player p, String label) {
        p.sendMessage(ChatColor.YELLOW + "Usage:");
        p.sendMessage(ChatColor.YELLOW + "/" + label + " create <shop|bank> [skinUsername] [name...]");
        p.sendMessage(ChatColor.YELLOW + "/" + label + " remove");
        p.sendMessage(ChatColor.YELLOW + "/" + label + " info");
        p.sendMessage(ChatColor.DARK_GRAY + "Example: /" + label + " create bank BankerSteve &3&lSoul Banker");
    }

    private Location getSpawnLocation(Player p) {
        Location base = p.getLocation().clone();
        base.setX(base.getBlockX() + 0.5);
        base.setY(base.getBlockY());
        base.setZ(base.getBlockZ() + 0.5);
        base.setYaw(p.getLocation().getYaw());
        base.setPitch(0f);
        return base;
    }

    private Entity getLookedAtNpc(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceEntities(
                p.getEyeLocation(),
                p.getEyeLocation().getDirection(),
                range,
                (entity) -> entity.getType() == EntityType.MANNEQUIN && npcManager.isNpc(entity)
        );
        return (r != null) ? r.getHitEntity() : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("create", "remove", "info")) {
                if (s.startsWith(a0)) out.add(s);
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            String a1 = args[1].toLowerCase(Locale.ROOT);
            for (String s : List.of("shop", "bank")) {
                if (s.startsWith(a1)) out.add(s);
            }
        }

        return out;
    }
}
