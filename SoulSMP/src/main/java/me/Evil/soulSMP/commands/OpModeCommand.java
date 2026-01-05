package me.Evil.soulSMP.commands;

import me.Evil.soulSMP.opmode.OpModeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OpModeCommand implements CommandExecutor {

    private final OpModeManager opmode;

    public OpModeCommand(OpModeManager opmode) {
        this.opmode = opmode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // Optional safety: require OP
        if (!p.isOp() && !p.hasPermission("soulsmp.opmode")) {
            p.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (opmode.isInOpMode(p.getUniqueId())) {
            boolean restored = opmode.exit(p);
            if (restored) {
                p.sendMessage(ChatColor.GREEN + "OpMode disabled. Restored your inventory, XP, and location.");
            } else {
                p.sendMessage(ChatColor.RED + "Could not restore your OpMode snapshot.");
            }
        } else {
            opmode.enter(p);
            p.sendMessage(ChatColor.AQUA + "OpMode enabled.");
            p.sendMessage(ChatColor.GRAY + "Your inventory, XP, location, and gamemode were saved.");
        }

        return true;
    }
}
