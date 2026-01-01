package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerDeathListener implements Listener {

    private final TeamManager teamManager;
    private final SoulTokenManager tokenManager;

    public PlayerDeathListener(TeamManager teamManager, SoulTokenManager tokenManager) {
        this.teamManager = teamManager;
        this.tokenManager = tokenManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 2) Reduce team lives and handle wipe logic
        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            return;
        }

        // 1) Drop 10x Soul Token at the death location
        ItemStack token = tokenManager.createToken(10);
        if (token != null) {
            player.getWorld().dropItemNaturally(player.getLocation(), token);
        }

        int livesBefore = team.getLives();
        if (livesBefore <= 0) {
            // Team is already at 0; don't go negative, just exit.
            return;
        }

        team.removeLife(); // team.lives -= 1
        int livesAfter = team.getLives();

        if (livesAfter <= 0) {
            // Clamp to 0 just to be safe
            team.setLives(0);

            // Drop all claims by setting claim radius to 0
            team.setClaimRadius(0);

            // Broadcast banner coordinates if we have a banner location
            Location bannerLoc = team.getBannerLocation();
            if (bannerLoc != null && bannerLoc.getWorld() != null) {
                String msg = ChatColor.DARK_RED + "A team has shattered."
                        + ChatColor.GRAY + " The team " + ChatColor.DARK_RED + team.getName()
                        + ChatColor.GRAY + " has been turned to dust."
                        + ChatColor.RED + " Their banner now lies vulnerable at: "
                        + bannerLoc.getBlockX() + ", " + bannerLoc.getBlockY() + ", " + bannerLoc.getBlockZ()
                        + ChatColor.DARK_RED + ".";

                Bukkit.broadcastMessage(msg);
            } else {
                // Fallback if somehow no banner location is set
                String msg = ChatColor.DARK_RED + "A team has shattered." + ChatColor.RED + team.getName() + ChatColor.GRAY + " has run out of lives!";
                Bukkit.broadcastMessage(msg);
            }
        }

        // Persist changes to teams.yml
        teamManager.saveTeam(team);
    }
}
