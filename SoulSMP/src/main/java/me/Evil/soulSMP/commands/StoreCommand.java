package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.store.StoreManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StoreCommand implements CommandExecutor {
    private final StoreManager storeManager;

    public StoreCommand(StoreManager storeManager) {
        this.storeManager = storeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        storeManager.openMain(p);
        return true;
    }
}
