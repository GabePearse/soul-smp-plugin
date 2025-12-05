package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "info" -> handleInfo(player);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "banner" -> handleBanner(player, args);
            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "==== Team Commands ====");
        player.sendMessage(ChatColor.YELLOW + "/team create <name> " + ChatColor.GRAY + "- Create a new team");
        player.sendMessage(ChatColor.YELLOW + "/team info " + ChatColor.GRAY + "- Show your team info");
        player.sendMessage(ChatColor.YELLOW + "/team leave " + ChatColor.GRAY + "- Leave your team");
        player.sendMessage(ChatColor.YELLOW + "/team disband " + ChatColor.GRAY + "- Disband your team (owner only)");
        player.sendMessage(ChatColor.YELLOW + "/team banner claim " + ChatColor.GRAY + "- Claim the banner design in your hand");
        player.sendMessage(ChatColor.YELLOW + "/team banner preview " + ChatColor.GRAY + "- Get a copy of your team banner");
        player.sendMessage(ChatColor.YELLOW + "/team banner remove " + ChatColor.GRAY + "- Remove your team’s claimed banner block");
    }

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

    // /team banner ...
    private void handleBanner(Player player, String[] args) {
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team banner <claim|preview|remove>");
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "claim" -> handleBannerClaim(player, team);
            case "preview" -> handleBannerPreview(player, team);
            case "remove" -> handleBannerRemove(player, team);
            default -> player.sendMessage(ChatColor.RED + "Usage: /team banner <claim|preview|remove>");
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
    // Tab completion
    // =====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;

        // /team <subcommand>
        if (args.length == 1) {
            List<String> subs = List.of("create", "info", "leave", "disband", "banner");
            String current = args[0].toLowerCase();
            for (String s : subs) {
                if (s.startsWith(current)) {
                    completions.add(s);
                }
            }
            return completions;
        }

        // /team banner <claim|preview|remove>
        if (args.length == 2 && args[0].equalsIgnoreCase("banner")) {
            List<String> subs = List.of("claim", "preview", "remove");
            String current = args[1].toLowerCase();
            for (String s : subs) {
                if (s.startsWith(current)) {
                    completions.add(s);
                }
            }
            return completions;
        }

        return completions;
    }
}
