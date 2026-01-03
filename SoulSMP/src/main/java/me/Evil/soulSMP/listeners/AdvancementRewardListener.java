package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.rewards.RewardProgressManager;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AdvancementRewardListener implements Listener {

    private final SoulTokenManager tokens;
    private final RewardProgressManager progress;

    public AdvancementRewardListener(SoulTokenManager tokens, RewardProgressManager progress) {
        this.tokens = tokens;
        this.progress = progress;
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        var player = event.getPlayer();
        if (player == null) return;

        Advancement adv = event.getAdvancement();
        if (adv == null || adv.getKey() == null) return;

        String advKey = adv.getKey().toString();
        UUID uuid = player.getUniqueId();

        // Prevent duplicates
        if (progress.hasRewardedAdvancement(uuid, advKey)) return;

        // Tier = depth-from-root + 1
        int tier = computeTierFromParentChain(adv);
        if (tier <= 0) return;

        // Mark rewarded BEFORE paying (idempotent / double-fire safe)
        progress.markRewardedAdvancement(uuid, advKey);

        boolean isFirst = progress.trySetFirstCompleter(advKey, uuid);

        int payout = isFirst ? (tier * 2) : tier;
        if (payout > 0) {
            tokens.giveTokens(player, payout);
        }

        progress.save();
    }

    /**
     * Paper API provides Advancement#getParent().
     * Tier = depth-from-root + 1
     *  - root => tier 1
     *  - child => tier 2
     *  - etc.
     */
    private int computeTierFromParentChain(Advancement adv) {
        int depth = 0;
        Set<String> seen = new HashSet<>();

        Advancement cur = adv;

        while (true) {
            if (cur == null || cur.getKey() == null) break;

            String k = cur.getKey().toString();
            if (!seen.add(k)) {
                // Safety: prevent infinite loops if something is weird
                break;
            }

            Advancement parent = cur.getParent();
            if (parent == null) break;

            depth++;
            cur = parent;

            if (depth > 200) break; // hard safety cap
        }

        return depth + 1;
    }
}
