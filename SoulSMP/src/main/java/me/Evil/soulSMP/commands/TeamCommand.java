package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.upkeep.UpkeepStatus;
import me.Evil.soulSMP.util.GiveOrDrop;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.regex.Pattern;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamManager teamManager;
    private final TeamChatManager teamChatManager; // ✅ NEW

    // ✅ Safe team name rules:
    // - 3 to 16 chars
    // - letters, numbers, underscore only
    private static final Pattern TEAM_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public TeamCommand(TeamManager teamManager, TeamChatManager teamChatManager) {
        this.teamManager = teamManager;
        this.teamChatManager = teamChatManager;
    }

    // =====================
    // Command handling
    // =====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "list" -> handleList(player, args);          // /team list [name]
            case "info" -> handleInfo(player, args);          // /team info [name]
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "transfer" -> handleTransfer(player, args);
            case "kick" -> handleKick(player, args);          // ✅ NEW: /team kick <player>
            case "banner" -> handleBanner(player, args);
            case "border" -> handleBorder(player);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            default -> sendHelp(player);
        }

        return true;
    }

    // =====================
    // Main /team help
    // =====================

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "==== Team Commands ====");
        player.sendMessage(ChatColor.YELLOW + "/team create <name> " + ChatColor.GRAY + "- Create a new team");
        player.sendMessage(ChatColor.YELLOW + "/team list " + ChatColor.GRAY + "- List all teams");
        player.sendMessage(ChatColor.YELLOW + "/team list <name> " + ChatColor.GRAY + "- Show info for a team (no banner location)");
        player.sendMessage(ChatColor.YELLOW + "/team info " + ChatColor.GRAY + "- Show your team info");
        player.sendMessage(ChatColor.YELLOW + "/team info <name> " + ChatColor.GRAY + "- Show info for a team (no banner location)");
        player.sendMessage(ChatColor.YELLOW + "/team leave " + ChatColor.GRAY + "- Leave your team");
        player.sendMessage(ChatColor.YELLOW + "/team disband " + ChatColor.GRAY + "- Disband your team (owner only)");
        player.sendMessage(ChatColor.YELLOW + "/team transfer <player> " + ChatColor.GRAY + "- Transfer team ownership");
        player.sendMessage(ChatColor.YELLOW + "/team kick <player> " + ChatColor.GRAY + "- Kick a member (owner only)");
        player.sendMessage(ChatColor.YELLOW + "/team invite <player> " + ChatColor.GRAY + "- Invite a player to your team");
        player.sendMessage(ChatColor.YELLOW + "/team accept " + ChatColor.GRAY + "- Accept a team invite");
        player.sendMessage(ChatColor.YELLOW + "/team border " + ChatColor.GRAY + "- Show your team's claim border");
        player.sendMessage(ChatColor.YELLOW + "/team banner " + ChatColor.GRAY + "- Banner & claim commands (/team banner help)");
    }

    // =====================
    // Helpers
    // =====================

    private String formatLoc(Location loc) {
        if (loc == null) return ChatColor.GRAY + "Not set";
        if (loc.getWorld() == null) return ChatColor.RED + "Invalid world";
        return ChatColor.WHITE + loc.getWorld().getName() + " [" +
                loc.getBlockX() + ", " +
                loc.getBlockY() + ", " +
                loc.getBlockZ() + "]";
    }

    private boolean isValidTeamName(String name) {
        if (name == null) return false;
        name = name.trim();
        if (!TEAM_NAME_PATTERN.matcher(name).matches()) return false;

        return !name.contains(".") && !name.contains("/") && !name.contains("\\") && !name.contains(":");
    }

    private void sendInvalidName(Player player) {
        player.sendMessage(ChatColor.RED + "Invalid team name.");
        player.sendMessage(ChatColor.GRAY + "Use 3-16 characters: " + ChatColor.WHITE + "A-Z, 0-9, _");
        player.sendMessage(ChatColor.DARK_GRAY + "Example: " + ChatColor.GRAY + "My_Team1");
    }

    private boolean isTeamInactive(Team t) {
        if (t == null) return true;
        if (t.getLives() <= 0) return true;

        UpkeepStatus st = t.getUpkeepStatus();
        return st == UpkeepStatus.UNSTABLE || st == UpkeepStatus.UNPROTECTED;
    }

    // =====================
    // /team list
    // =====================

    private void handleList(Player player, String[] args) {
        if (args.length >= 2) {
            String name = args[1];
            Team t = teamManager.getTeamByName(name);
            if (t == null) {
                player.sendMessage(ChatColor.RED + "No team found named '" + name + "'.");
                return;
            }
            sendPublicTeamInfo(player, t, false);
            return;
        }

        Collection<Team> all = teamManager.getAllTeams();
        if (all.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No teams exist yet.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "==== Teams (" + all.size() + ") ====");
        List<Team> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER));

        for (Team t : sorted) {
            player.sendMessage(ChatColor.AQUA + "- " + ChatColor.WHITE + t.getName()
                    + ChatColor.DARK_GRAY + " (" + ChatColor.GRAY + t.getMembers().size() + ChatColor.DARK_GRAY + ")");
        }

        player.sendMessage(ChatColor.DARK_GRAY + "Tip: /team info <name> for details.");
    }

    // =====================
    // /team info
    // =====================

    private void handleInfo(Player player, String[] args) {

        if (args.length >= 2) {
            String name = args[1];
            Team t = teamManager.getTeamByName(name);
            if (t == null) {
                player.sendMessage(ChatColor.RED + "No team found named '" + name + "'.");
                return;
            }
            sendPublicTeamInfo(player, t, true);
            return;
        }

        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "==== Team Info ====");
        player.sendMessage(ChatColor.AQUA + "Name: " + ChatColor.WHITE + team.getName());
        player.sendMessage(ChatColor.AQUA + "Members: " + ChatColor.WHITE + team.getMembers().size() + "/" + team.getMaxMembers());
        player.sendMessage(ChatColor.AQUA + "Lives: " + ChatColor.WHITE + team.getLives());
        player.sendMessage(ChatColor.AQUA + "Claim Radius: " + ChatColor.WHITE + team.getClaimRadius() + " chunks");
        player.sendMessage(ChatColor.AQUA + "Vault Size: " + ChatColor.WHITE + team.getVaultSize());
        player.sendMessage(ChatColor.AQUA + "Upkeep Status: " + ChatColor.WHITE + team.getUpkeepStatus().name());
        player.sendMessage(ChatColor.AQUA + "Unpaid Weeks: " + ChatColor.WHITE + team.getUnpaidWeeks());

        if (team.hasBannerLocation()) {
            var loc = team.getBannerLocation();
            player.sendMessage(ChatColor.AQUA + "Banner Location: " + ChatColor.WHITE +
                    loc.getWorld().getName() + " [" +
                    loc.getBlockX() + ", " +
                    loc.getBlockY() + ", " +
                    loc.getBlockZ() + "]");
        } else {
            player.sendMessage(ChatColor.AQUA + "Banner Location: " + ChatColor.GRAY + "Not placed");
        }

        player.sendMessage(ChatColor.AQUA + "Banner Design: " + ChatColor.WHITE + (team.hasClaimedBannerDesign() ? "Claimed" : "Not claimed"));

        List<String> names = new ArrayList<>();
        for (UUID id : team.getMembers()) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            if (n != null) names.add(n);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        player.sendMessage(ChatColor.AQUA + "Players: " + ChatColor.GRAY + String.join(ChatColor.DARK_GRAY + ", " + ChatColor.GRAY, names));
    }

    private void sendPublicTeamInfo(Player viewer, Team t, boolean allowBeaconReveal) {
        viewer.sendMessage(ChatColor.GOLD + "==== Team Info: " + ChatColor.AQUA + t.getName() + ChatColor.GOLD + " ====");

        String ownerName = Bukkit.getOfflinePlayer(t.getOwner()).getName();
        if (ownerName == null) ownerName = "Unknown";

        viewer.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + ownerName);
        viewer.sendMessage(ChatColor.YELLOW + "Members: " + ChatColor.WHITE + t.getMembers().size() + "/" + t.getMaxMembers());
        viewer.sendMessage(ChatColor.YELLOW + "Lives: " + ChatColor.WHITE + t.getLives());
        viewer.sendMessage(ChatColor.YELLOW + "Claim Radius: " + ChatColor.WHITE + t.getClaimRadius());
        viewer.sendMessage(ChatColor.YELLOW + "Vault Size: " + ChatColor.WHITE + t.getVaultSize());
        viewer.sendMessage(ChatColor.YELLOW + "Upkeep Status: " + ChatColor.WHITE + t.getUpkeepStatus().name());
        viewer.sendMessage(ChatColor.YELLOW + "Unpaid Weeks: " + ChatColor.WHITE + t.getUnpaidWeeks());

        viewer.sendMessage(ChatColor.YELLOW + "Banner Design: " + ChatColor.WHITE + (t.hasClaimedBannerDesign() ? "Claimed" : "Not claimed"));

        List<String> names = new ArrayList<>();
        for (UUID id : t.getMembers()) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            if (n != null) names.add(n);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        viewer.sendMessage(ChatColor.YELLOW + "Players: " + ChatColor.GRAY + String.join(ChatColor.DARK_GRAY + ", " + ChatColor.GRAY, names));

        viewer.sendMessage(ChatColor.DARK_GRAY + "(Banner locations are hidden)");

        if (allowBeaconReveal && isTeamInactive(t)) {
            Location beaconLoc = t.getBannerLocation();
            viewer.sendMessage(ChatColor.LIGHT_PURPLE + "Beacon Location: " + formatLoc(beaconLoc));
        }
    }

    private void sendBannerHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "==== Team Banner Commands ====");
        player.sendMessage(ChatColor.YELLOW + "/team banner claim " + ChatColor.GRAY + "- Claim the banner design in your hand");
        player.sendMessage(ChatColor.YELLOW + "/team banner preview " + ChatColor.GRAY + "- Get a copy of your team banner");
        player.sendMessage(ChatColor.YELLOW + "/team banner remove " + ChatColor.GRAY + "- Remove your team's placed banner block");
        player.sendMessage(ChatColor.YELLOW + "/team banner unclaim " + ChatColor.GRAY + "- Drop your banner design and ALL land claims");
    }

    // =====================
    // Core team commands
    // =====================

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team create <name>");
            return;
        }

        if (teamManager.getTeamByPlayer(player) != null) {
            player.sendMessage(ChatColor.RED + "You are already in a team.");
            return;
        }

        String name = args[1].trim();
        if (!isValidTeamName(name)) {
            sendInvalidName(player);
            return;
        }

        Team created = teamManager.createTeam(name, player);
        if (created == null) {
            player.sendMessage(ChatColor.RED + "A team with that name already exists (or name is invalid).");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Team '" + created.getName() + "' created!");
        player.sendMessage(ChatColor.YELLOW + "Use /team banner claim while holding your banner design.");
    }

    private void handleLeave(Player player) {
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        if (team.getOwner().equals(player.getUniqueId()) && team.getMembers().size() > 1) {
            player.sendMessage(ChatColor.RED + "You are the team owner. Use /team disband or transfer ownership first.");
            return;
        }

        teamManager.removePlayerFromTeam(player);
        player.sendMessage(ChatColor.YELLOW + "You have left the team '" + team.getName() + "'.");
    }

    private void handleDisband(Player player) {
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        if (!team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the team owner can disband the team.");
            return;
        }

        String name = team.getName();
        teamManager.disbandTeam(team);
        player.sendMessage(ChatColor.RED + "You have disbanded the team '" + name + "'.");
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team transfer <player>");
            return;
        }

        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        if (!team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the team owner can transfer ownership.");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Could not find online player '" + targetName + "'.");
            return;
        }

        Team targetTeam = teamManager.getTeamByPlayer(target);
        if (targetTeam == null || !targetTeam.equals(team)) {
            player.sendMessage(ChatColor.RED + target.getName() + " is not in your team.");
            return;
        }

        team.setOwner(target.getUniqueId());
        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.GREEN + "You transferred ownership of team "
                + ChatColor.AQUA + team.getName()
                + ChatColor.GREEN + " to " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + ".");

        target.sendMessage(ChatColor.GREEN + "You are now the owner of team "
                + ChatColor.AQUA + team.getName() + ChatColor.GREEN + ".");
    }

    // ✅ NEW: /team kick <player>
    // =====================
// /team kick <player>
// Owner-only
// =====================
    private void handleKick(Player leader, String[] args) {
        if (args.length < 2) {
            leader.sendMessage(ChatColor.RED + "Usage: /team kick <player>");
            return;
        }

        Team team = teamManager.getTeamByPlayer(leader);
        if (team == null) {
            leader.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        if (!team.getOwner().equals(leader.getUniqueId())) {
            leader.sendMessage(ChatColor.RED + "Only the team owner can kick members.");
            return;
        }

        String targetName = args[1];

        // Try online first
        UUID targetId = null;
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            targetId = onlineTarget.getUniqueId();
        } else {
            // Offline lookup (safe guard so random names don't create fake players)
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
            if (!off.hasPlayedBefore()) {
                leader.sendMessage(ChatColor.RED + "Player not found: " + targetName);
                return;
            }
            targetId = off.getUniqueId();
        }

        TeamManager.KickResult result = teamManager.kickPlayerFromTeam(leader, targetId);

        switch (result) {
            case SUCCESS -> {
                String kickedName = (onlineTarget != null)
                        ? onlineTarget.getName()
                        : Bukkit.getOfflinePlayer(targetId).getName();

                if (kickedName == null) kickedName = targetName;

                leader.sendMessage(ChatColor.GREEN + "You kicked " + ChatColor.YELLOW + kickedName
                        + ChatColor.GREEN + " from team " + ChatColor.AQUA + team.getName() + ChatColor.GREEN + ".");

                // Notify the kicked player if online
                Player kickedOnline = Bukkit.getPlayer(targetId);
                if (kickedOnline != null && kickedOnline.isOnline()) {
                    kickedOnline.sendMessage(ChatColor.RED + "You were kicked from team "
                            + ChatColor.AQUA + team.getName()
                            + ChatColor.RED + " by " + ChatColor.YELLOW + leader.getName() + ChatColor.RED + ".");
                }

                // Notify remaining members
                for (UUID uuid : team.getMembers()) {
                    Player member = Bukkit.getPlayer(uuid);
                    if (member != null && member.isOnline()) {
                        member.sendMessage(ChatColor.YELLOW + kickedName + ChatColor.RED + " was kicked by "
                                + ChatColor.YELLOW + leader.getName() + ChatColor.RED + ".");
                    }
                }
            }

            case NOT_IN_TEAM -> leader.sendMessage(ChatColor.RED + "You are not in a team.");
            case NOT_LEADER -> leader.sendMessage(ChatColor.RED + "Only the team owner can kick members.");
            case TARGET_NOT_IN_TEAM -> leader.sendMessage(ChatColor.RED + "That player is not in your team.");
            case CANNOT_KICK_SELF -> leader.sendMessage(ChatColor.RED + "You cannot kick yourself.");
            case CANNOT_KICK_LEADER -> leader.sendMessage(ChatColor.RED + "You cannot kick the team owner.");
            default -> leader.sendMessage(ChatColor.RED + "Could not kick that player.");
        }
    }


    // =====================
    // Banner subsection
    // =====================

    private void handleBanner(Player player, String[] args) {
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        if (args.length == 1 || (args.length >= 2 && args[1].equalsIgnoreCase("help"))) {
            sendBannerHelp(player);
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "claim" -> handleBannerClaim(player, team);
            case "preview" -> handleBannerPreview(player, team);
            case "remove" -> handleBannerRemove(player, team, args);
            case "unclaim" -> handleBannerUnclaim(player, team);
            default -> sendBannerHelp(player);
        }
    }

    private void handleBannerRemove(Player player, Team team, String[] args) {
        if (!team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the team owner can remove the team banner.");
            return;
        }

        String target = (args.length >= 3) ? args[2].toLowerCase(Locale.ROOT) : "overworld";

        switch (target) {
            case "overworld", "normal", "main" -> {
                if (!team.hasBannerLocation()) {
                    player.sendMessage(ChatColor.RED + "Your team does not have a claimed banner placed in the Overworld.");
                    return;
                }

                var loc = team.getBannerLocation();
                var block = loc.getBlock();

                block.setType(Material.AIR);
                team.setBannerLocation(null);
                teamManager.saveTeam(team);

                player.sendMessage(ChatColor.YELLOW + "Your team's Overworld banner has been removed.");
                player.sendMessage(ChatColor.GREEN + "You can place a new matching banner to set a new banner location.");
            }
            case "nether" -> {
                String dimKey = "NETHER";
                if (!team.hasDimensionalBannerLocation(dimKey)) {
                    player.sendMessage(ChatColor.RED + "Your team does not have a Nether banner placed.");
                    return;
                }

                var loc = team.getDimensionalBanner(dimKey);
                if (loc == null || loc.getWorld() == null) {
                    team.setDimensionalBanner(dimKey, null);
                    teamManager.saveTeam(team);
                    player.sendMessage(ChatColor.RED + "Your Nether banner location was invalid and has been cleared.");
                    return;
                }

                var block = loc.getBlock();
                if (block.getType().name().endsWith("_BANNER")) {
                    block.setType(Material.AIR);
                }

                team.setDimensionalBanner(dimKey, null);
                teamManager.saveTeam(team);

                player.sendMessage(ChatColor.YELLOW + "Your team's Nether banner has been removed.");
                player.sendMessage(ChatColor.GREEN + "You can place a new matching banner in the Nether to set a new banner location.");
            }
            case "end", "the_end" -> {
                String dimKey = "THE_END";
                if (!team.hasDimensionalBannerLocation(dimKey)) {
                    player.sendMessage(ChatColor.RED + "Your team does not have an End banner placed.");
                    return;
                }

                var loc = team.getDimensionalBanner(dimKey);
                if (loc == null || loc.getWorld() == null) {
                    team.setDimensionalBanner(dimKey, null);
                    teamManager.saveTeam(team);
                    player.sendMessage(ChatColor.RED + "Your End banner location was invalid and has been cleared.");
                    return;
                }

                var block = loc.getBlock();
                if (block.getType().name().endsWith("_BANNER")) {
                    block.setType(Material.AIR);
                }

                team.setDimensionalBanner(dimKey, null);
                teamManager.saveTeam(team);

                player.sendMessage(ChatColor.YELLOW + "Your team's End banner has been removed.");
                player.sendMessage(ChatColor.GREEN + "You can place a new matching banner in The End to set a new banner location.");
            }
            default -> player.sendMessage(ChatColor.RED + "Unknown world '" + target + "'. Use overworld, nether, or end.");
        }
    }

    private void handleBannerClaim(Player player, Team team) {
        if (team.hasClaimedBannerDesign()) {
            player.sendMessage(ChatColor.RED + "Your team has already claimed a banner design.");
            return;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You must hold a banner in your main hand.");
            return;
        }

        if (!inHand.getType().name().endsWith("_BANNER")) {
            player.sendMessage(ChatColor.RED + "You must hold a banner (any color).");
            return;
        }

        Team existingOwner = teamManager.getTeamByBannerItem(inHand);
        if (existingOwner != null && existingOwner != team) {
            player.sendMessage(ChatColor.RED + "That banner design is already claimed by team '" + existingOwner.getName() + "'.");
            return;
        }

        team.setBannerDesign(inHand);
        teamManager.saveTeam(team);
        player.sendMessage(ChatColor.GREEN + "Your team has claimed this banner design!");
        player.sendMessage(ChatColor.YELLOW + "Only banners with this exact design will count as your team banner.");
    }

    private void handleBannerUnclaim(Player player, Team team) {
        if (!team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the team owner can unclaim the banner and drop claims.");
            return;
        }

        if (!team.hasBannerLocation() && !team.hasClaimedBannerDesign()) {
            player.sendMessage(ChatColor.RED + "Your team has no banner claimed and no active claims.");
            return;
        }

        if (team.hasBannerLocation()) {
            var loc = team.getBannerLocation();
            var world = loc.getWorld();
            if (world != null) {
                var block = world.getBlockAt(loc);
                if (block.getType().name().endsWith("_BANNER") || block.getType().name().endsWith("_WALL_BANNER")) {
                    block.setType(Material.AIR);
                }
            }
            team.setBannerLocation(null);
        }

        if (team.hasClaimedBannerDesign()) {
            team.clearBannerDesign();
        }

        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.YELLOW + "Your team's banner and all land claims have been unclaimed.");
        player.sendMessage(ChatColor.GREEN + "You can claim a new design with "
                + ChatColor.AQUA + "/team banner claim" + ChatColor.GREEN +
                " and place a new banner to start claiming again.");
    }

    private void handleBannerPreview(Player player, Team team) {
        if (!team.hasClaimedBannerDesign()) {
            player.sendMessage(ChatColor.RED + "Your team has not claimed a banner design yet.");
            return;
        }

        ItemStack banner = team.createBannerItem();
        if (banner == null) {
            player.sendMessage(ChatColor.RED + "Could not create banner item.");
            return;
        }

        GiveOrDrop.give(player, banner);
        player.sendMessage(ChatColor.GREEN + "You received a copy of your team banner.");
    }

    // =====================
    // Other team commands
    // =====================

    private void handleBorder(Player player) {
        Team team = teamManager.getTeamByPlayer(player);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        if (!team.hasBannerLocation()) {
            player.sendMessage(ChatColor.RED + "Your team does not have a banner placed.");
            return;
        }

        teamManager.showTeamBorder(player);
    }

    private void handleInvite(Player player, String[] args) {
        Team team = teamManager.getTeamByPlayer(player);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        if (!team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the team owner can invite players.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team invite <player>");
            return;
        }

        Player target = player.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }

        if (teamManager.getTeamByPlayer(target) != null) {
            player.sendMessage(ChatColor.RED + "That player is already in a team.");
            return;
        }

        if (teamManager.sendInvite(player, target)) {
            player.sendMessage(ChatColor.GREEN + "Invite sent to " + target.getName() + "!");
            target.sendMessage(ChatColor.AQUA + player.getName() + " has invited you to join team "
                    + ChatColor.GOLD + team.getName());
            target.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.GREEN + "/team accept" + ChatColor.YELLOW + " to join.");
        }
    }

    private void handleAccept(Player player) {
        if (!teamManager.hasPendingInvite(player)) {
            player.sendMessage(ChatColor.RED + "You do not have any pending team invites.");
            return;
        }

        Team team = teamManager.acceptInvite(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "Your team invite has expired.");
            return;
        }

        Team actual = teamManager.getTeamByPlayer(player);
        if (actual == null || !actual.equals(team)) {
            player.sendMessage(ChatColor.RED + "Could not join the team (it may be full).");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "You joined the team " + team.getName() + "!");
        for (UUID uuid : team.getMembers()) {
            Player member = player.getServer().getPlayer(uuid);
            if (member != null) {
                member.sendMessage(ChatColor.AQUA + player.getName() + ChatColor.GREEN + " has joined the team!");
            }
        }
    }

    // =====================
    // Tab completion
    // =====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player player)) return completions;

        if (args.length == 1) {
            List<String> subs = List.of("create", "list", "info", "leave", "disband", "transfer", "kick", "banner", "border", "invite", "accept");
            String current = args[0].toLowerCase(Locale.ROOT);
            for (String s : subs) {
                if (s.startsWith(current)) {
                    completions.add(s);
                }
            }
            return completions;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("info"))) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            List<Team> teams = new ArrayList<>(teamManager.getAllTeams());
            teams.sort(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER));
            for (Team t : teams) {
                if (t.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    completions.add(t.getName());
                }
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("banner")) {
            List<String> subs = List.of("help", "claim", "preview", "remove", "unclaim");
            String current = args[1].toLowerCase(Locale.ROOT);
            for (String s : subs) {
                if (s.startsWith(current)) {
                    completions.add(s);
                }
            }
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("banner") && args[1].equalsIgnoreCase("remove")) {
            String current = args[2].toLowerCase(Locale.ROOT);
            for (String s : List.of("overworld", "nether", "end")) {
                if (s.startsWith(current)) completions.add(s);
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("transfer")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        // ✅ tab-complete teammates for /team kick <name>
        if (args.length == 2 && args[0].equalsIgnoreCase("kick")) {
            Team team = teamManager.getTeamByPlayer(player);
            if (team == null) return completions;

            String partial = args[1].toLowerCase(Locale.ROOT);

            for (UUID id : team.getMembers()) {
                if (id.equals(player.getUniqueId())) continue;
                String n = Bukkit.getOfflinePlayer(id).getName();
                if (n != null && n.toLowerCase(Locale.ROOT).startsWith(partial)) {
                    completions.add(n);
                }
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                String n = p.getName();
                if (n.toLowerCase(Locale.ROOT).startsWith(partial) && !completions.contains(n)) {
                    completions.add(n);
                }
            }

            completions.sort(String.CASE_INSENSITIVE_ORDER);
            return completions;
        }

        return completions;
    }
}
