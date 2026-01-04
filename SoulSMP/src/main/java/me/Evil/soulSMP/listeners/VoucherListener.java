package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.upkeep.TeamUpkeepManager;
import me.Evil.soulSMP.upkeep.UpkeepStatus;
import me.Evil.soulSMP.vouchers.VoucherItem;
import me.Evil.soulSMP.vouchers.VoucherType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public class VoucherListener implements Listener {

    private final Plugin plugin;
    private final TeamManager teams;
    private final TeamUpkeepManager upkeep; // ✅ add upkeep manager

    public VoucherListener(Plugin plugin, TeamManager teams, TeamUpkeepManager upkeep) {
        this.plugin = plugin;
        this.teams = teams;
        this.upkeep = upkeep;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        // only main hand to avoid double fire
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player p = event.getPlayer();
        if (p == null) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (!VoucherItem.isVoucher(plugin, item)) return;

        Team team = teams.getTeamByPlayer(p);
        if (team == null) {
            p.sendMessage(ChatColor.RED + "You must be in a team to use a voucher.");
            event.setCancelled(true);
            return;
        }

        VoucherType type = VoucherItem.getType(plugin, item);
        if (type == null) {
            p.sendMessage(ChatColor.RED + "This voucher is invalid.");
            event.setCancelled(true);
            return;
        }

        int amount = VoucherItem.getAmount(plugin, item, 1);
        String extra = VoucherItem.getString(plugin, item);

        boolean applied = applyVoucher(p, team, type, amount, extra);
        if (!applied) {
            event.setCancelled(true);
            return;
        }

        // consume 1
        int newAmt = item.getAmount() - 1;
        if (newAmt <= 0) {
            p.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(newAmt);
        }

        teams.saveTeam(team);
        event.setCancelled(true);
    }

    private boolean applyVoucher(Player p, Team team, VoucherType type, int amount, String extra) {
        switch (type) {

            case TEAM_LIFE_PLUS -> {
                int add = Math.max(1, amount);
                team.addLives(add);

                // If you want vouchers to still "count" toward scaling:
                team.addLivesPurchased(add); // remove if you want vouchers to bypass scaling

                p.sendMessage(ChatColor.GREEN + "Voucher redeemed: +" + add + " team life(s).");
                p.sendMessage(ChatColor.AQUA + "Total lives: " + ChatColor.YELLOW + team.getLives());
                return true;
            }

            case TEAM_LIFE_COST_RESET -> {
                team.setLivesPurchased(0);
                p.sendMessage(ChatColor.GREEN + "Voucher redeemed: team life cost scaling reset.");
                return true;
            }

            case CLAIM_RADIUS_PLUS -> {
                int add = Math.max(1, amount);
                team.setClaimRadius(team.getClaimRadius() + add);

                p.sendMessage(ChatColor.GREEN + "Voucher redeemed: +" + add + " claim radius.");
                p.sendMessage(ChatColor.AQUA + "New radius: " + ChatColor.YELLOW + team.getClaimRadius());
                return true;
            }

            case VAULT_SLOTS_PLUS -> {
                int add = Math.max(1, amount);
                team.setVaultSize(team.getVaultSize() + add);

                p.sendMessage(ChatColor.GREEN + "Voucher redeemed: +" + add + " vault slot(s).");
                p.sendMessage(ChatColor.AQUA + "New vault size: " + ChatColor.YELLOW + team.getVaultSize());
                return true;
            }

            case EFFECT_LEVEL_SET -> {
                if (extra == null || extra.isBlank()) {
                    p.sendMessage(ChatColor.RED + "This voucher is missing an effect id.");
                    return false;
                }
                String effectId = extra.toLowerCase(Locale.ROOT);
                int lvl = Math.max(0, amount);
                team.setEffectLevel(effectId, lvl);
                p.sendMessage(ChatColor.GREEN + "Voucher redeemed: set effect '" + effectId + "' to level " + lvl + ".");
                return true;
            }

            case UPKEEP_WEEKS_CLEAR -> {
                // ✅ FULL "fresh team" reset to match TeamUpkeepManager behavior:
                team.setLastUpkeepPaymentMillis(0L);
                team.setUnpaidWeeks(0);
                team.setUpkeepStatus(UpkeepStatus.PROTECTED);
                team.setBaseClaimRadiusForUpkeep(-1);

                // Optional: immediately recompute / normalize (safe either way)
                if (upkeep != null) {
                    upkeep.updateTeamUpkeep(team);
                }

                p.sendMessage(ChatColor.GREEN + "Voucher redeemed: upkeep fully reset.");
                p.sendMessage(ChatColor.GRAY + "Your team is treated like it was freshly created.");
                return true;
            }

            case UPKEEP_PAY_NOW -> {
                // "Pay now" means clear debt + set paid time to now
                team.setLastUpkeepPaymentMillis(System.currentTimeMillis());
                team.setUnpaidWeeks(0);
                team.setUpkeepStatus(UpkeepStatus.PROTECTED);
                team.setBaseClaimRadiusForUpkeep(-1);

                // Optional: normalize (will keep it PROTECTED)
                if (upkeep != null) {
                    upkeep.updateTeamUpkeep(team);
                }

                p.sendMessage(ChatColor.GREEN + "Voucher redeemed: upkeep marked as paid.");
                return true;
            }

            case DIM_BANNER_UNLOCK -> {
                if (extra == null || extra.isBlank()) {
                    p.sendMessage(ChatColor.RED + "This voucher is missing a dimension key.");
                    return false;
                }
                String dim = extra.toUpperCase(Locale.ROOT);
                team.unlockDimensionalBanner(dim);
                p.sendMessage(ChatColor.GREEN + "Voucher redeemed: unlocked banner for " + dim + ".");
                return true;
            }

            case DIM_TELEPORT_UNLOCK -> {
                if (extra == null || extra.isBlank()) {
                    p.sendMessage(ChatColor.RED + "This voucher is missing a dimension key.");
                    return false;
                }
                String dim = extra.toUpperCase(Locale.ROOT);
                team.unlockDimensionalTeleport(dim);
                p.sendMessage(ChatColor.GREEN + "Voucher redeemed: unlocked teleport for " + dim + ".");
                return true;
            }
        }

        return false;
    }
}
