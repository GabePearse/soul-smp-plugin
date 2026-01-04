package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.koth.KothManager;
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
    private final KothManager koth;

    public PlayerDeathListener(TeamManager teamManager, SoulTokenManager tokenManager, KothManager koth) {
        this.teamManager = teamManager;
        this.tokenManager = tokenManager;
        this.koth = koth;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();

        // ✅ KOTH mode: only participants are exempt from life loss/token drops
        if (koth != null && koth.isActive() && koth.isParticipant(player.getUniqueId())) {
            return;
        }

        Team team = teamManager.getTeamByPlayer(player);
        if (team == null) {
            return;
        }

        int livesBefore = team.getLives();
        if (livesBefore <= 0) {
            return;
        }

        Player killer = player.getKiller();
        if (killer != null) {
            Team killerTeam = teamManager.getTeamByPlayer(killer);

            boolean sameTeam = (killerTeam != null && killerTeam == team);
            if (!sameTeam) {
                ItemStack token = tokenManager.createToken(1);
                if (token != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), token);
                }
            }
        }

        team.removeLife();
        int livesAfter = team.getLives();

        if (livesAfter <= 0) {
            team.setLives(0);
            team.setClaimRadius(0);

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
                String msg = ChatColor.DARK_RED + "A team has shattered." + ChatColor.RED + team.getName() + ChatColor.GRAY + " has run out of lives!";
                Bukkit.broadcastMessage(msg);
            }
        }

        teamManager.saveTeam(team);
    }
}
