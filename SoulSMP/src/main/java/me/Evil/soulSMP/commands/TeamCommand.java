package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamManager teamManager;

    public TeamCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
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
            case "info" -> handleInfo(player);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "transfer" -> handleTransfer(player, args);
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
        player.sendMessage(ChatColor.YELLOW + "/team info " + ChatColor.GRAY + "- Show your team info");
        player.sendMessage(ChatColor.YELLOW + "/team leave " + ChatColor.GRAY + "- Leave your team");
        player.sendMessage(ChatColor.YELLOW + "/team disband " + ChatColor.GRAY + "- Disband your team (owner only)");
        player.sendMessage(ChatColor.YELLOW + "/team transfer <player> " + ChatColor.GRAY + "- Transfer team ownership");
        player.sendMessage(ChatColor.YELLOW + "/team invite <player> " + ChatColor.GRAY + "- Invite a player to your team");
        player.sendMessage(ChatColor.YELLOW + "/team accept " + ChatColor.GRAY + "- Accept a team invite");
        player.sendMessage(ChatColor.YELLOW + "/team border " + ChatColor.GRAY + "- Show your team's claim border");
        player.sendMessage(ChatColor.YELLOW + "/team banner " + ChatColor.GRAY + "- Banner & claim commands (/team banner help)");
    }

    // Banner-only help
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

    // /team create <name>
    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team create <name>");
            return;
        }

        if (teamManager.getTeamByPlayer(player) != null) {
            player.sendMessage(ChatColor.RED + "You are already in a team.");
            return;
        }

        String name = args[1];
        Team created = teamManager.createTeam(name, player);
        if (created == null) {
            player.sendMessage(ChatColor.RED + "A team with that name already exists.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Team '" + created.getName() + "' created!");
        player.sendMessage(ChatColor.YELLOW + "Use /team banner claim while holding your banner design.");
    }

    // /team info
    private void handleInfo(Player player) {
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "==== Team Info ====");
        player.sendMessage(ChatColor.AQUA + "Name: " + ChatColor.WHITE + team.getName());
        player.sendMessage(ChatColor.AQUA + "Members: " + ChatColor.WHITE + team.getMembers().size() + "/" + Team.MAX_MEMBERS);
        player.sendMessage(ChatColor.AQUA + "Lives: " + ChatColor.WHITE + team.getLives());
        player.sendMessage(ChatColor.AQUA + "Claim Radius: " + ChatColor.WHITE + team.getClaimRadius() + " chunks");

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

        if (team.hasClaimedBannerDesign()) {
            player.sendMessage(ChatColor.AQUA + "Banner Design: " + ChatColor.WHITE + "Claimed");
        } else {
            player.sendMessage(ChatColor.AQUA + "Banner Design: " + ChatColor.GRAY + "Not claimed");
        }
    }

    // /team leave
    private void handleLeave(Player player) {
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        // Prevent owners from leaving without disbanding if there are other members
        if (team.getOwner().equals(player.getUniqueId()) && team.getMembers().size() > 1) {
            player.sendMessage(ChatColor.RED + "You are the team owner. Use /team disband or transfer ownership first.");
            return;
        }

        teamManager.removePlayerFromTeam(player);
        player.sendMessage(ChatColor.YELLOW + "You have left the team '" + team.getName() + "'.");
    }

    // /team disband
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

    // /team transfer <player>
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

        // Only owner can transfer
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

        // Target must be in the same team
        Team targetTeam = teamManager.getTeamByPlayer(target);
        if (targetTeam == null || !targetTeam.equals(team)) {
            player.sendMessage(ChatColor.RED + target.getName() + " is not in your team.");
            return;
        }

        // Transfer & save
        team.setOwner(target.getUniqueId());
        teamManager.saveTeam(team);

        player.sendMessage(ChatColor.GREEN + "You transferred ownership of team "
                + ChatColor.AQUA + team.getName()
                + ChatColor.GREEN + " to " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + ".");

        target.sendMessage(ChatColor.GREEN + "You are now the owner of team "
                + ChatColor.AQUA + team.getName() + ChatColor.GREEN + ".");
    }

    // =====================
    // Banner subsection
    // =====================

    // /team banner ...
    private void handleBanner(Player player, String[] args) {
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        // /team banner  OR  /team banner help
        if (args.length == 1 || (args.length >= 2 && args[1].equalsIgnoreCase("help"))) {
            sendBannerHelp(player);
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "claim"   -> handleBannerClaim(player, team);
            case "preview" -> handleBannerPreview(player, team);
            case "remove"  -> handleBannerRemove(player, team);
            case "unclaim" -> handleBannerUnclaim(player, team);
            default        -> sendBannerHelp(player);
        }
    }

    // /team banner claim
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

        // Check if another team already owns this design
        Team existingOwner = teamManager.getTeamByBannerItem(inHand);
        if (existingOwner != null && existingOwner != team) {
            player.sendMessage(ChatColor.RED + "That banner design is already claimed by team '" + existingOwner.getName() + "'.");
            return;
        }

        team.setBannerDesign(inHand);
        player.sendMessage(ChatColor.GREEN + "Your team has claimed this banner design!");
        player.sendMessage(ChatColor.YELLOW + "Only banners with this exact design will count as your team banner.");
    }

    // /team banner unclaim
    private void handleBannerUnclaim(Player player, Team team) {
        // Only owner can unclaim the banner + claims
        if (!team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the team owner can unclaim the banner and drop claims.");
            return;
        }

        if (!team.hasBannerLocation() && !team.hasClaimedBannerDesign()) {
            player.sendMessage(ChatColor.RED + "Your team has no banner claimed and no active claims.");
            return;
        }

        // 1) Remove the placed banner block if it exists
        if (team.hasBannerLocation()) {
            var loc = team.getBannerLocation();
            var world = loc.getWorld();
            if (world != null) {
                var block = world.getBlockAt(loc);
                // Only remove if it’s actually a banner, to avoid nuking random blocks
                if (block.getType().name().endsWith("_BANNER") || block.getType().name().endsWith("_WALL_BANNER")) {
                    block.setType(Material.AIR);
                }
            }
            // Drop all claims by clearing banner location
            team.setBannerLocation(null);
        }

        // 2) Remove the banner design so they can pick a new one later
        if (team.hasClaimedBannerDesign()) {
            team.clearBannerDesign();
        }

        player.sendMessage(ChatColor.YELLOW + "Your team's banner and all land claims have been unclaimed.");
        player.sendMessage(ChatColor.GREEN + "You can claim a new design with "
                + ChatColor.AQUA + "/team banner claim" + ChatColor.GREEN +
                " and place a new banner to start claiming again.");
    }

    // /team banner preview
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

        player.getInventory().addItem(banner);
        player.sendMessage(ChatColor.GREEN + "You received a copy of your team banner.");
    }

    // /team banner remove
    private void handleBannerRemove(Player player, Team team) {
        if (!team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the team owner can remove the team banner.");
            return;
        }

        if (!team.hasBannerLocation()) {
            player.sendMessage(ChatColor.RED + "Your team does not have a claimed banner placed.");
            return;
        }

        var loc = team.getBannerLocation();
        var block = loc.getBlock();

        // Just delete the block; protection is handled in listeners for normal breaks
        block.setType(Material.AIR);
        team.setBannerLocation(null);

        player.sendMessage(ChatColor.YELLOW + "Your team's claimed banner has been removed.");
        player.sendMessage(ChatColor.GREEN + "You can place a new matching banner to set a new banner location.");
    }

    // =====================
    // Other team commands
    // =====================

    // /team border
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

    // /team invite <player>
    private void handleInvite(Player player, String[] args) {
        Team team = teamManager.getTeamByPlayer(player);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        // Only owner can invite
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

    // /team accept
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
        if (!(sender instanceof Player)) return completions;

        // /team <subcommand>
        if (args.length == 1) {
            List<String> subs = List.of("create", "info", "leave", "disband", "banner", "border", "invite", "accept");
            String current = args[0].toLowerCase(Locale.ROOT);
            for (String s : subs) {
                if (s.startsWith(current)) {
                    completions.add(s);
                }
            }
            return completions;
        }

        // /team banner <...>
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

        // /team invite <player>
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        return completions;
    }
}
