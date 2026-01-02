package me.Evil.soulSMP.broadcast;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ServerTipBroadcaster {

    private final Plugin plugin;
    private final Random rng = new Random();

    private List<String> messages = new ArrayList<>();
    private boolean enabled = true;
    private boolean randomOrder = false;
    private int intervalSeconds = 300;

    private int index = 0;

    public ServerTipBroadcaster(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();

        enabled = plugin.getConfig().getBoolean("tips.enabled", true);
        randomOrder = plugin.getConfig().getBoolean("tips.random-order", false);
        intervalSeconds = Math.max(30, plugin.getConfig().getInt("tips.interval-seconds", 300));

        List<String> raw = plugin.getConfig().getStringList("tips.messages");
        messages = new ArrayList<>();

        for (String line : raw) {
            if (line == null) continue;
            String s = ChatColor.translateAlternateColorCodes('&', line.trim());
            if (!s.isEmpty()) messages.add(s);
        }

        index = 0;
    }

    public boolean isEnabled() {
        return enabled && !messages.isEmpty();
    }

    public int getIntervalTicks() {
        return intervalSeconds * 20;
    }

    public void broadcastNext() {
        if (!isEnabled()) return;

        String msg;
        if (randomOrder) {
            msg = messages.get(rng.nextInt(messages.size()));
        } else {
            if (index >= messages.size()) index = 0;
            msg = messages.get(index++);
        }

        Bukkit.broadcastMessage(msg);
    }
}
