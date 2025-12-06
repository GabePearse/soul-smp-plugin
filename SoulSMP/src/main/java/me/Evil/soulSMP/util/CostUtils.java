package me.Evil.soulSMP.util;

public final class CostUtils {

    private CostUtils() {
    }

    /**
     * Generic geometric cost calculator.
     *
     * cost = baseCost * (multiplier ^ stepIndex)
     *
     * stepIndex is "how many upgrades deep" you are.
     * For example:
     *  - radius 1 -> stepIndex 0
     *  - radius 2 -> stepIndex 1
     */
    public static int computeCost(int baseCost, double multiplier, int stepIndex) {
        if (stepIndex < 0) stepIndex = 0;
        double value = baseCost * Math.pow(multiplier, stepIndex);
        return (int) Math.round(value);
    }
}
