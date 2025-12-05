package me.Evil.soulSMP.chat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players have team chat mode toggled on.
 */
public class TeamChatManager {

    private final Set<UUID> teamChatEnabled = new HashSet<>();

    public boolean isTeamChatEnabled(UUID uuid) {
        return teamChatEnabled.contains(uuid);
    }

    public void enableTeamChat(UUID uuid) {
        teamChatEnabled.add(uuid);
    }

    public void disableTeamChat(UUID uuid) {
        teamChatEnabled.remove(uuid);
    }

    public boolean toggleTeamChat(UUID uuid) {
        if (teamChatEnabled.contains(uuid)) {
            teamChatEnabled.remove(uuid);
            return false; // now disabled
        } else {
            teamChatEnabled.add(uuid);
            return true; // now enabled
        }
    }
}
