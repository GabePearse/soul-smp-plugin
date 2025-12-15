package me.Evil.soulSMP.store.util;

import me.Evil.soulSMP.fishing.CustomFishGenerator;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * /fishsim [samples] [luck]
 *
 * Simulates fish generation and prints score stats + suggested score-multiplier for selling.
 *
 * Fixes "No scores collected" by:
 *  - Using passed-in keys first
 *  - Auto-detecting keys from PDC if the passed keys don't exist on generated fish
 */
public class FishSimCommand implements CommandExecutor {

    private final CustomFishGenerator fishGenerator;

    // Preferred keys (you pass these in)
    private NamespacedKey rarityKey;
    private NamespacedKey scoreKey;

    // After detection, we lock these in:
    private boolean keysResolved = false;

    public FishSimCommand(CustomFishGenerator fishGenerator,
                          NamespacedKey rarityKey,
                          NamespacedKey scoreKey) {
        this.fishGenerator = fishGenerator;
        this.rarityKey = rarityKey;
        this.scoreKey = scoreKey;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        int samples = parseInt(args, 0, 100000); // default 100k
        int luck = parseInt(args, 1, 0);         // default luck 0
        samples = Math.max(1, Math.min(samples, 1_000_000)); // sanity cap

        // Targets are "median payout" desired per rarity.
        // These keys MUST match EXACT rarity.getId() values stored in PDC.
        Map<String, Integer> targetMedianPayout = new LinkedHashMap<>();
        targetMedianPayout.put("COMMON", 1);
        targetMedianPayout.put("UNCOMMON", 2);
        targetMedianPayout.put("RARE", 5);
        targetMedianPayout.put("EPIC", 12);
        targetMedianPayout.put("LEGENDARY", 30);
        targetMedianPayout.put("MYTHIC", 60);
        targetMedianPayout.put("DIVINE", 200);

        sender.sendMessage(color("&7Running fish sim: &f" + samples + " &7samples, luck &f" + luck + "&7..."));

        List<Double> allScores = new ArrayList<>(Math.min(samples, 250000));
        Map<String, List<Double>> byRarity = new HashMap<>();

        int nullCount = 0;
        int missingMeta = 0;
        int missingScore = 0;
        int missingRarity = 0;

        // Reset resolution each run (in case you hot-reload / change keys)
        keysResolved = false;

        for (int i = 0; i < samples; i++) {
            ItemStack fish = fishGenerator.generateFish(luck);
            if (fish == null) {
                nullCount++;
                continue;
            }

            ItemMeta meta = fish.getItemMeta();
            if (meta == null) {
                missingMeta++;
                continue;
            }

            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            // Resolve keys once using the first valid fish we see
            if (!keysResolved) {
                keysResolved = true;
                resolveKeys(sender, fish, pdc);
            }

            Double score = readScore(pdc);
            if (score == null) {
                missingScore++;
                continue;
            }

            String rarity = readRarity(pdc);
            if (rarity == null) {
                rarity = "UNKNOWN";
                missingRarity++;
            }

            allScores.add(score);
            byRarity.computeIfAbsent(rarity, k -> new ArrayList<>()).add(score);
        }

        if (allScores.isEmpty()) {
            sender.sendMessage(color("&cNo scores collected."));
            sender.sendMessage(color("&7Counts: null=" + nullCount
                    + ", missingMeta=" + missingMeta
                    + ", missingScore=" + missingScore
                    + ", missingRarity=" + missingRarity));
            sender.sendMessage(color("&7Most likely: your scoreKey/rarityKey do not match what your generator writes."));
            return true;
        }

        allScores.sort(Double::compareTo);

        sender.sendMessage(color("&a=== Overall Score Stats ==="));
        printPercentiles(sender, allScores, "ALL (" + allScores.size() + ")");

        sender.sendMessage(color("&a=== Per-Rarity Score Stats ==="));
        List<String> rarities = new ArrayList<>(byRarity.keySet());
        rarities.sort(String::compareToIgnoreCase);

        for (String r : rarities) {
            List<Double> list = byRarity.get(r);
            if (list == null || list.isEmpty()) continue;

            list.sort(Double::compareTo);

            // Only print targeted tiers + UNKNOWN
            if (!targetMedianPayout.containsKey(r) && !"UNKNOWN".equalsIgnoreCase(r)) continue;

            printPercentiles(sender, list, r + " (" + list.size() + ")");
        }

        sender.sendMessage(color("&a=== Recommended score-multiplier candidates ==="));
        sender.sendMessage(color("&7Computed as: targetMedianPayout / rarityMedianScore"));

        List<Double> candidates = new ArrayList<>();

        for (var entry : targetMedianPayout.entrySet()) {
            String rarity = entry.getKey();
            int target = entry.getValue();

            List<Double> list = byRarity.get(rarity);
            if (list == null || list.isEmpty()) continue;

            list.sort(Double::compareTo);

            double medianScore = percentileSorted(list, 0.50);
            if (medianScore <= 0) continue;

            double m = target / medianScore;
            candidates.add(m);

            sender.sendMessage(color("&f" + rarity + "&7: medianScore=&b" + fmt(medianScore)
                    + " &7target=&b" + target
                    + " &7=> multiplier=&e" + fmt(m)));
        }

        if (!candidates.isEmpty()) {
            candidates.sort(Double::compareTo);

            double suggested = percentileSorted(candidates, 0.50);
            double p25 = percentileSorted(candidates, 0.25);
            double p75 = percentileSorted(candidates, 0.75);

            sender.sendMessage(color("&aSuggested default score-multiplier: &e" + fmt(suggested)));
            sender.sendMessage(color("&7Reasonable range: &e" + fmt(p25) + " &7to &e" + fmt(p75)));

            sender.sendMessage(color("&a=== Example sell formula ==="));
            sender.sendMessage(color("&7sellPrice = round(score * &e" + fmt(suggested) + "&7)"));
            sender.sendMessage(color("&7(you can clamp min/max per rarity if you want)"));

            sender.sendMessage(color("&7Put this in &fstore-sell.yml&7:"));
            sender.sendMessage(color("&fscore-multiplier: &e" + fmt(suggested)));
        } else {
            sender.sendMessage(color("&cNo multiplier candidates computed."));
            sender.sendMessage(color("&7This usually means your rarity IDs don't match the target keys."));
            sender.sendMessage(color("&7Update targetMedianPayout keys to match rarity.getId() values you store."));
        }

        sender.sendMessage(color("&7Counts: null=" + nullCount
                + ", missingMeta=" + missingMeta
                + ", missingScore=" + missingScore
                + ", missingRarity=" + missingRarity));
        sender.sendMessage(color("&aDone."));
        return true;
    }

    private void resolveKeys(CommandSender sender, ItemStack fish, PersistentDataContainer pdc) {
        sender.sendMessage(color("&eDEBUG: first fish type=&f" + fish.getType()));
        sender.sendMessage(color("&eDEBUG: PDC keys=&f" + pdc.getKeys()));
        sender.sendMessage(color("&eDEBUG: provided scoreKey=&f" + scoreKey));
        sender.sendMessage(color("&eDEBUG: provided rarityKey=&f" + rarityKey));

        boolean hasScore = pdc.has(scoreKey, PersistentDataType.DOUBLE);
        boolean hasRarity = pdc.has(rarityKey, PersistentDataType.STRING);

        // If provided keys work, keep them
        if (hasScore && hasRarity) {
            sender.sendMessage(color("&aDEBUG: provided keys are valid."));
            return;
        }

        // Try to auto-detect:
        NamespacedKey detectedScore = null;
        NamespacedKey detectedRarity = null;

        // 1) Look for obvious key names first
        for (NamespacedKey k : pdc.getKeys()) {
            String key = k.getKey().toLowerCase(Locale.ROOT);

            if (detectedScore == null && (key.contains("score"))) {
                if (pdc.has(k, PersistentDataType.DOUBLE)) detectedScore = k;
            }
            if (detectedRarity == null && (key.contains("rarity"))) {
                if (pdc.has(k, PersistentDataType.STRING)) detectedRarity = k;
            }
        }

        // 2) Fallback: first DOUBLE as score, first STRING as rarity (only if we must)
        if (detectedScore == null) {
            for (NamespacedKey k : pdc.getKeys()) {
                if (pdc.has(k, PersistentDataType.DOUBLE)) {
                    detectedScore = k;
                    break;
                }
            }
        }
        if (detectedRarity == null) {
            for (NamespacedKey k : pdc.getKeys()) {
                if (pdc.has(k, PersistentDataType.STRING)) {
                    detectedRarity = k;
                    break;
                }
            }
        }

        if (detectedScore != null) {
            sender.sendMessage(color("&aDEBUG: detected scoreKey=&f" + detectedScore));
            this.scoreKey = detectedScore;
        } else {
            sender.sendMessage(color("&cDEBUG: could not detect a DOUBLE score key from PDC."));
        }

        if (detectedRarity != null) {
            sender.sendMessage(color("&aDEBUG: detected rarityKey=&f" + detectedRarity));
            this.rarityKey = detectedRarity;
        } else {
            sender.sendMessage(color("&cDEBUG: could not detect a STRING rarity key from PDC."));
        }

        sender.sendMessage(color("&eDEBUG: has(scoreKey, DOUBLE)=&f" + pdc.has(this.scoreKey, PersistentDataType.DOUBLE)));
        sender.sendMessage(color("&eDEBUG: has(rarityKey, STRING)=&f" + pdc.has(this.rarityKey, PersistentDataType.STRING)));
    }

    private Double readScore(PersistentDataContainer pdc) {
        // Expected: DOUBLE
        Double score = pdc.get(scoreKey, PersistentDataType.DOUBLE);
        if (score != null) return score;

        // Diagnostic fallback: STRING
        String s = pdc.get(scoreKey, PersistentDataType.STRING);
        if (s != null) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }

        return null;
    }

    private String readRarity(PersistentDataContainer pdc) {
        return pdc.get(rarityKey, PersistentDataType.STRING);
    }

    private void printPercentiles(CommandSender sender, List<Double> sortedScores, String label) {
        double p50 = percentileSorted(sortedScores, 0.50);
        double p90 = percentileSorted(sortedScores, 0.90);
        double p95 = percentileSorted(sortedScores, 0.95);
        double p99 = percentileSorted(sortedScores, 0.99);
        double max = sortedScores.get(sortedScores.size() - 1);

        sender.sendMessage(color("&f" + label + "&7: p50=&b" + fmt(p50)
                + " &7p90=&b" + fmt(p90)
                + " &7p95=&b" + fmt(p95)
                + " &7p99=&b" + fmt(p99)
                + " &7max=&b" + fmt(max)));
    }

    /**
     * Percentile on a SORTED list.
     * p in [0,1]. Uses linear interpolation between closest ranks.
     */
    private double percentileSorted(List<Double> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) return 0.0;
        if (p <= 0) return sorted.get(0);
        if (p >= 1) return sorted.get(sorted.size() - 1);

        double idx = p * (sorted.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);

        if (lo == hi) return sorted.get(lo);

        double w = idx - lo;
        return sorted.get(lo) * (1.0 - w) + sorted.get(hi) * w;
    }

    private int parseInt(String[] args, int index, int def) {
        if (args == null || args.length <= index) return def;
        try { return Integer.parseInt(args[index]); }
        catch (Exception ignored) { return def; }
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
