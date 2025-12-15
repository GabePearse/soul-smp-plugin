package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.bank.BankGUI;
import me.Evil.soulSMP.bank.BankManager;
import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.tokens.SoulTokenManager;
import me.Evil.soulSMP.upkeep.UpkeepStatus;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BankListener implements Listener {

    private final BankManager bank;
    private final SoulTokenManager tokens;
    private final TeamManager teamManager;

    private final Map<UUID, Boolean> awaitingWithdraw = new ConcurrentHashMap<>();

    public BankListener(BankManager bank, SoulTokenManager tokens, TeamManager teamManager) {
        this.bank = bank;
        this.tokens = tokens;
        this.teamManager = teamManager;
    }

    private boolean isTeamActive(Team team) {
        if (team == null) return false;

        // Choose your definition of "active":
        return team.getUpkeepStatus() == UpkeepStatus.PROTECTED;
        // or: return team.getUpkeepStatus() != UpkeepStatus.UNPROTECTED;
    }

    private void handleInterest(Player player) {
        UUID uuid = player.getUniqueId();
        Team team = teamManager.getTeamByPlayer(uuid); // rename if needed

        if (isTeamActive(team)) {
            long gained = bank.applyDailyInterest(uuid);
            if (gained > 0) {
                player.sendMessage(ChatColor.GOLD + "Bank Interest: +" + gained + " Soul Tokens");
            }
        } else {
            bank.markInterestDayAsToday(uuid);
        }
        bank.save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBankClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof BankGUI.BankHolder)) return;

        e.setCancelled(true);

        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
        String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());

        // Apply interest when interacting with bank
        handleInterest(player);

        if ("Deposit".equalsIgnoreCase(name)) {
            int invTokens = tokens.countTokensInInventory(player);
            if (invTokens <= 0) {
                player.sendMessage(ChatColor.RED + "You have no Soul Tokens to deposit.");
                return;
            }

            boolean removed = tokens.removeTokensFromPlayer(player, invTokens);
            if (!removed) {
                player.sendMessage(ChatColor.RED + "Could not remove tokens from inventory.");
                return;
            }

            bank.addBalance(player.getUniqueId(), invTokens);
            bank.save();

            player.sendMessage(ChatColor.GREEN + "Deposited " + invTokens + " Soul Tokens.");
            return;
        }

        if ("Info".equalsIgnoreCase(name)) {
            long bal = bank.getBalance(player.getUniqueId());
            player.sendMessage(ChatColor.AQUA + "Bank Balance: " + ChatColor.WHITE + bal + ChatColor.AQUA + " Soul Tokens");

            Team team = teamManager.getTeamByPlayer(player.getUniqueId());
            if (isTeamActive(team)) {
                player.sendMessage(ChatColor.GRAY + "Interest: " + ChatColor.GREEN + "Active (1% daily)");
            } else {
                player.sendMessage(ChatColor.GRAY + "Interest: " + ChatColor.RED + "Inactive (team not active)");
            }
            return;
        }

        if ("Withdraw".equalsIgnoreCase(name)) {
            player.closeInventory();
            awaitingWithdraw.put(player.getUniqueId(), true);
            player.sendMessage(ChatColor.GOLD + "Type the amount to withdraw in chat (or 'all').");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChatWithdraw(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!awaitingWithdraw.containsKey(uuid)) return;

        e.setCancelled(true);
        awaitingWithdraw.remove(uuid);

        // Interest check again (optional but safe)
        // (If you prefer, remove this line so interest only applies on GUI click)
        // handleInterest(player);

        String msg = e.getMessage().trim().toLowerCase();
        long bal = bank.getBalance(uuid);

        long amount;
        if (msg.equals("all")) {
            amount = bal;
        } else {
            try {
                amount = Long.parseLong(msg);
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "Invalid amount. Try /bank again.");
                return;
            }
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Amount must be positive.");
            return;
        }
        if (bal < amount) {
            player.sendMessage(ChatColor.RED + "Not enough in bank. Balance: " + bal);
            return;
        }
        if (amount > Integer.MAX_VALUE) {
            player.sendMessage(ChatColor.RED + "That amount is too large.");
            return;
        }

        // subtract, then give as items
        if (!bank.subtractBalance(uuid, amount)) {
            player.sendMessage(ChatColor.RED + "Withdraw failed.");
            return;
        }
        bank.save();

        tokens.giveTokens(player, (int) amount);
        player.sendMessage(ChatColor.GREEN + "Withdrew " + amount + " Soul Tokens.");
    }
}
