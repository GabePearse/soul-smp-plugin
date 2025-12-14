package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.fishing.FishingConfig;
import me.Evil.soulSMP.fishing.journal.FishingJournalGUI;
import me.Evil.soulSMP.fishing.journal.FishingJournalManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JournalCommand implements CommandExecutor {

    private final FishingJournalGUI gui;

    public JournalCommand(FishingJournalManager journalManager, FishingConfig fishingConfig) {
        this.gui = new FishingJournalGUI(journalManager, fishingConfig);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Usage: /journal [page]");
                return true;
            }
        }

        player.openInventory(gui.createFor(player, page));
        return true;
    }
}
