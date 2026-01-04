package me.Evil.soulSMP.vouchers;

public enum VoucherType {
    TEAM_LIFE_PLUS,          // +N lives
    TEAM_LIFE_COST_RESET,    // livesPurchased = 0
    CLAIM_RADIUS_PLUS,       // +N claim radius (respects your max, if you want)
    VAULT_SLOTS_PLUS,        // +N vault slots
    EFFECT_LEVEL_SET,        // set an effect id to a level
    UPKEEP_WEEKS_CLEAR,      // unpaidWeeks = 0
    UPKEEP_PAY_NOW,          // set lastUpkeepPaymentMillis = now
    DIM_BANNER_UNLOCK,       // unlockDimensionalBanners add DIM
    DIM_TELEPORT_UNLOCK      // unlockDimensionalTeleports add DIM
}
