package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.SoulSMP;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class TextCommand implements CommandExecutor, TabCompleter {

    private final SoulSMP plugin;

    // In-memory edit sessions (not persisted). Intended for spawn building.
    private final Map<UUID, TextDisplayCommand> textEditSessions = new HashMap<>();

    // /plugins/SoulSMP/text_exports/
    private static final String EXPORT_DIR = "text_exports";
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

    public TextCommand(SoulSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

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
                TextDisplay td = p.getWorld().spawn(
                        p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8)),
                        TextDisplay.class
                );

                td.setBillboard(Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setSeeThrough(false);
                td.setLineWidth(220);
                td.setBackgroundColor(Color.fromARGB(140, 0, 0, 0));
                td.setText("");

                textEditSessions.put(p.getUniqueId(), new TextDisplayCommand(p.getUniqueId(), td));

                p.sendMessage(ChatColor.GREEN + "Created a TextDisplay and entered edit mode.");
                p.sendMessage(ChatColor.GRAY + "Use: " + ChatColor.AQUA + "/" + label + " line <#> <text>");
                p.sendMessage(ChatColor.GRAY + "Blank line: " + ChatColor.AQUA + "/" + label + " line <#> empty");
                p.sendMessage(ChatColor.GRAY + "Export to file: " + ChatColor.AQUA + "/" + label + " export <name>");
                p.sendMessage(ChatColor.GRAY + "Import from file: " + ChatColor.AQUA + "/" + label + " import <name>");
                p.sendMessage(ChatColor.GRAY + "Finish: " + ChatColor.AQUA + "/" + label + " done");
                return true;
            }

            case "edit" -> {
                TextDisplay td = getLookedAtTextDisplay(p, 10.0);
                if (td == null) {
                    p.sendMessage(ChatColor.RED + "Look directly at a TextDisplay within 10 blocks, then run this again.");
                    return true;
                }

                TextDisplayCommand session = new TextDisplayCommand(p.getUniqueId(), td);
                session.loadFromCurrentText();
                textEditSessions.put(p.getUniqueId(), session);

                p.sendMessage(ChatColor.GREEN + "Now editing that TextDisplay.");
                p.sendMessage(ChatColor.GRAY + "Use: " + ChatColor.AQUA + "/" + label + " info" + ChatColor.GRAY + " to see current lines.");
                p.sendMessage(ChatColor.GRAY + "Export to file: " + ChatColor.AQUA + "/" + label + " export <name>");
                return true;
            }

            case "line" -> {
                TextDisplayCommand session = textEditSessions.get(p.getUniqueId());
                if (session == null) {
                    p.sendMessage(ChatColor.RED + "You are not editing a TextDisplay. Use /" + label + " create or /" + label + " edit.");
                    return true;
                }

                if (args.length < 2) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " line <lineNumber> <text>");
                    p.sendMessage(ChatColor.GRAY + "Use '" + ChatColor.AQUA + "empty" + ChatColor.GRAY + "' to insert a blank line.");
                    return true;
                }

                int lineNumber;
                try {
                    lineNumber = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    p.sendMessage(ChatColor.RED + "Line number must be a number (1, 2, 3...).");
                    return true;
                }

                if (lineNumber < 1 || lineNumber > 50) {
                    p.sendMessage(ChatColor.RED + "Line number must be between 1 and 50.");
                    return true;
                }

                // Allow: /text line <n>   (no text) -> blank line
                String raw;
                if (args.length == 2) {
                    raw = "&r";
                } else {
                    raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                    if (raw.equalsIgnoreCase("empty") || raw.equalsIgnoreCase("<empty>")) {
                        raw = "&r";
                    }
                }

                String txt = ChatColor.translateAlternateColorCodes('&', raw);
                session.setLine(lineNumber - 1, txt);

                p.sendMessage(ChatColor.GREEN + "Updated line " + lineNumber + ".");
                return true;
            }

            case "clear" -> {
                TextDisplayCommand session = textEditSessions.get(p.getUniqueId());
                if (session == null) {
                    p.sendMessage(ChatColor.RED + "You are not editing a TextDisplay.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " clear <lineNumber>");
                    return true;
                }

                int lineNumber;
                try {
                    lineNumber = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    p.sendMessage(ChatColor.RED + "Line number must be a number (1, 2, 3...).");
                    return true;
                }

                if (lineNumber < 1 || lineNumber > 50) {
                    p.sendMessage(ChatColor.RED + "Line number must be between 1 and 50.");
                    return true;
                }

                session.clearLine(lineNumber - 1);
                p.sendMessage(ChatColor.YELLOW + "Cleared line " + lineNumber + ".");
                return true;
            }

            case "info" -> {
                TextDisplayCommand session = textEditSessions.get(p.getUniqueId());
                if (session == null) {
                    p.sendMessage(ChatColor.RED + "You are not editing a TextDisplay.");
                    return true;
                }

                p.sendMessage(ChatColor.AQUA + "--- TextDisplay Lines ---");
                List<String> lines = session.getLines();
                if (lines.isEmpty()) {
                    p.sendMessage(ChatColor.GRAY + "(no lines yet)");
                    return true;
                }

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line == null) line = "";
                    p.sendMessage(ChatColor.GRAY + "" + (i + 1) + ". " + ChatColor.WHITE + line);
                }
                return true;
            }

            case "export" -> {
                TextDisplayCommand session = textEditSessions.get(p.getUniqueId());
                if (session == null) {
                    p.sendMessage(ChatColor.RED + "You are not editing a TextDisplay.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " export <name>");
                    return true;
                }

                String name = args[1].trim();
                if (!SAFE_NAME.matcher(name).matches()) {
                    p.sendMessage(ChatColor.RED + "Invalid name. Use only letters, numbers, dot, underscore, dash (max 64).");
                    return true;
                }

                File dir = new File(plugin.getDataFolder(), EXPORT_DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    p.sendMessage(ChatColor.RED + "Could not create export folder: " + dir.getPath());
                    return true;
                }

                File out = new File(dir, name + ".txt");

                List<String> lines = session.getLines();
                try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
                    w.write("/" + label + " create");
                    w.newLine();
                    w.newLine();

                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        int lineNo = i + 1;

                        if (line == null || line.isEmpty() || isOnlyReset(line)) {
                            w.write("/" + label + " line " + lineNo + " empty");
                        } else {
                            w.write("/" + label + " line " + lineNo + " " + sectionToAmpersand(line));
                        }
                        w.newLine();
                    }

                    w.write("/" + label + " done");
                    w.newLine();
                } catch (IOException e) {
                    p.sendMessage(ChatColor.RED + "Failed to write file: " + e.getMessage());
                    return true;
                }

                p.sendMessage(ChatColor.GREEN + "Exported to file: " + ChatColor.AQUA + out.getPath());
                p.sendMessage(ChatColor.GRAY + "Edit it, then run: " + ChatColor.AQUA + "/" + label + " import " + name);
                return true;
            }

            case "import" -> {
                if (args.length < 2) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " import <name>");
                    return true;
                }

                String name = args[1].trim();
                if (!SAFE_NAME.matcher(name).matches()) {
                    p.sendMessage(ChatColor.RED + "Invalid name. Use only letters, numbers, dot, underscore, dash (max 64).");
                    return true;
                }

                File dir = new File(plugin.getDataFolder(), EXPORT_DIR);
                File in = new File(dir, name + ".txt");
                if (!in.exists()) {
                    p.sendMessage(ChatColor.RED + "File not found: " + in.getPath());
                    return true;
                }

                int ran = 0;
                int skipped = 0;

                try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(in), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String cmdLine = line.trim();
                        if (cmdLine.isEmpty()) continue;

                        // allow comments
                        if (cmdLine.startsWith("#") || cmdLine.startsWith("//")) continue;

                        // strip leading slash
                        if (cmdLine.startsWith("/")) cmdLine = cmdLine.substring(1).trim();
                        if (cmdLine.isEmpty()) continue;

                        // Safety: accept only "text ..." OR legacy "ssmp text ..."
                        boolean okPrefix =
                                startsWithIgnoreCase(cmdLine, label + " ") ||
                                        startsWithIgnoreCase(cmdLine, "text ") ||
                                        startsWithIgnoreCase(cmdLine, "ssmp text ");

                        if (!okPrefix) {
                            skipped++;
                            continue;
                        }

                        boolean ok = p.performCommand(cmdLine);
                        if (ok) ran++; else skipped++;
                    }
                } catch (IOException e) {
                    p.sendMessage(ChatColor.RED + "Failed to read file: " + e.getMessage());
                    return true;
                }

                p.sendMessage(ChatColor.GREEN + "Import complete. Ran " + ran + " command(s), skipped " + skipped + ".");
                return true;
            }

            case "done" -> {
                TextDisplayCommand session = textEditSessions.remove(p.getUniqueId());
                if (session == null) {
                    p.sendMessage(ChatColor.RED + "You are not editing a TextDisplay.");
                    return true;
                }
                p.sendMessage(ChatColor.GREEN + "TextDisplay saved (in-world). Edit session closed.");
                return true;
            }

            case "cancel" -> {
                TextDisplayCommand session = textEditSessions.remove(p.getUniqueId());
                if (session == null) {
                    p.sendMessage(ChatColor.RED + "You are not editing a TextDisplay.");
                    return true;
                }
                session.getDisplay().remove();
                p.sendMessage(ChatColor.RED + "TextDisplay discarded and edit session closed.");
                return true;
            }

            default -> {
                sendUsage(p, label);
                return true;
            }
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "Usage:");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " <create|edit|line|clear|info|export|import|done|cancel>");
        sender.sendMessage(ChatColor.GRAY + "Tip: /" + label + " line <n> empty  -> inserts a blank line");
        sender.sendMessage(ChatColor.GRAY + "Files: /" + label + " export <name>  |  /" + label + " import <name>");
        sender.sendMessage(ChatColor.DARK_GRAY + "Saved under: plugins/SoulSMP/" + EXPORT_DIR + "/<name>.txt");
    }

    /**
     * Reliable TextDisplay targeting via ray trace with a filter.
     */
    private TextDisplay getLookedAtTextDisplay(Player p, double maxDistance) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();

        RayTraceResult result = p.getWorld().rayTraceEntities(
                eye,
                dir,
                maxDistance,
                0.35, // hitbox expansion
                entity -> entity instanceof TextDisplay
        );

        if (result == null) return null;
        Entity hit = result.getHitEntity();
        if (hit instanceof TextDisplay td) return td;
        return null;
    }

    // ---------- Helpers (export / file) ----------

    private static boolean isOnlyReset(String s) {
        if (s == null) return true;
        String t = s.trim();
        // normalize '&' to '§' if someone somehow stored '&'
        t = t.replace('&', '§');
        // remove any number of reset codes
        while (t.contains("§r")) t = t.replace("§r", "");
        return t.isEmpty();
    }

    private static String sectionToAmpersand(String s) {
        if (s == null) return "";
        // Keep text exactly, just swap Minecraft section sign codes to ampersand codes.
        return s.replace('§', '&');
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        if (s == null || prefix == null) return false;
        if (s.length() < prefix.length()) return false;
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("create", "edit", "line", "clear", "info", "export", "import", "done", "cancel")) {
                if (s.startsWith(a0)) completions.add(s);
            }
            return completions;
        }

        // Suggest "empty" for /text line <n> ...
        if (args.length == 3 && args[0].equalsIgnoreCase("line")) {
            String a2 = args[2].toLowerCase(Locale.ROOT);
            if ("empty".startsWith(a2)) completions.add("empty");
            if ("<empty>".startsWith(a2)) completions.add("<empty>");
        }

        return completions;
    }
}
