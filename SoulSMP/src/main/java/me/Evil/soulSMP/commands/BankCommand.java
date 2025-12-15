package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.bank.BankGUI;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class BankCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        BankGUI.open(player);
        return true;
    }
}
