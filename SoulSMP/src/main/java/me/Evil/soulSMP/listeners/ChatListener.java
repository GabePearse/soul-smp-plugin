package me.Evil.soulSMP.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.Evil.soulSMP.chat.TeamChatManager;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public class ChatListener implements Listener {

    private final TeamManager teamManager;
    private final TeamChatManager teamChatManager;

    public ChatListener(TeamManager teamManager, TeamChatManager teamChatManager) {
        this.teamManager = teamManager;
        this.teamChatManager = teamChatManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // =========================
        // TEAM CHAT (toggle enabled)
        // =========================
        if (teamChatManager.isTeamChatEnabled(playerId)) {
            Team team = teamManager.getTeamByPlayer(player);

            // If they aren't in a team anymore, disable and fall back to global
            if (team == null) {
                teamChatManager.disableTeamChat(playerId);
            } else {
                event.setCancelled(true);

                // [TEAM] Name: <green message>   (no team name)
                Component prefix = Component.text("[TEAM] ", NamedTextColor.GREEN);

                Component name = Component.text(player.getName(), NamedTextColor.WHITE);
                Component sep = Component.text(": ", NamedTextColor.DARK_GRAY);

                // Force message to green
                Component msg = event.message().color(NamedTextColor.GREEN);

                Component line = prefix.append(name).append(sep).append(msg);

                // Send only to online teammates
                for (UUID uuid : team.getMembers()) {
                    Player teammate = Bukkit.getPlayer(uuid);
                    if (teammate != null && teammate.isOnline()) {
                        teammate.sendMessage(line);
                    }
                }
                return;
            }
        }

        // =========================
        // GLOBAL CHAT (normal)
        // =========================
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Team t = teamManager.getTeamByPlayer(source);

            Component teamTag = Component.empty();
            if (t != null) {
                teamTag = Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text(t.getName(), NamedTextColor.AQUA))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY));
            }

            Component name = Component.text(source.getName(), NamedTextColor.WHITE);
            Component sep = Component.text(": ", NamedTextColor.DARK_GRAY);

            // message color -> white
            Component msg = message.color(NamedTextColor.WHITE);

            return teamTag.append(name).append(sep).append(msg);
        });
    }
}
