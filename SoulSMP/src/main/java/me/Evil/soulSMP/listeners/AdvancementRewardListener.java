package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.rewards.AdvancementRewardSettings;
import me.Evil.soulSMP.rewards.RewardProgressManager;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.UUID;

public class AdvancementRewardListener implements Listener {

    private final SoulTokenManager tokens;
    private final RewardProgressManager progress;
    private final AdvancementRewardSettings settings;

    public AdvancementRewardListener(SoulTokenManager tokens, RewardProgressManager progress, AdvancementRewardSettings settings) {
        this.tokens = tokens;
        this.progress = progress;
        this.settings = settings;
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Advancement adv = event.getAdvancement();
        if (adv == null || adv.getKey() == null) return;

        String advKey = adv.getKey().toString();
        UUID uuid = player.getUniqueId();

        // Prevent duplicates
        if (progress.hasRewardedAdvancement(uuid, advKey)) return;

        // Compute tokens (0 if not eligible)
        int baseTokens = settings.getTokensForAdvancement(adv);
        if (baseTokens <= 0) return;

        // Mark first to make this idempotent even if something weird fires twice
        progress.markRewardedAdvancement(uuid, advKey);

        boolean isFirst = progress.trySetFirstCompleter(advKey, uuid);
        int payout = isFirst ? (baseTokens * 2) : baseTokens;

        if (payout > 0) {
            tokens.giveTokens(player, payout);
        }

        progress.save();
    }
}
