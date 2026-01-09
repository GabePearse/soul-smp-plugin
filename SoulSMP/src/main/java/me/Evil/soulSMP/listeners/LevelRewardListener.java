package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.rewards.RewardProgressManager;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLevelChangeEvent;

public class LevelRewardListener implements Listener {

    private final SoulTokenManager tokens;
    private final RewardProgressManager progress;

    public LevelRewardListener(SoulTokenManager tokens, RewardProgressManager progress) {
        this.tokens = tokens;
        this.progress = progress;
    }

    @EventHandler
    public void onLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        int newLevel = event.getNewLevel();

        // Only reward on actual increases
        if (newLevel <= event.getOldLevel()) return;

        // Rewards start at level 30
        if (newLevel < 30) return;

        // Use persisted progress as source of truth (not Bukkit's old level)
        int lastRewarded = progress.getLastRewardedLevel(player.getUniqueId());
        if (newLevel <= lastRewarded) return;

        // Calculate + mark (only marks if tokens > 0)
        int totalTokens = progress.payAndMarkLevels(player.getUniqueId(), newLevel);
        if (totalTokens <= 0) return;

        // Save the "paid up to" level first (prevents double pay on crashes/reloads)
        progress.save();

        // Then pay out
        tokens.giveTokens(player, totalTokens);
    }
}
