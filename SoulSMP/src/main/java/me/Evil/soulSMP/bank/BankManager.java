package me.Evil.soulSMP.bank;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class BankManager {

    private final Plugin plugin;
    private final File file;
    private FileConfiguration data;

    public BankManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bank.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { data.save(file); } catch (IOException ignored) {}
    }

    private String path(UUID uuid) {
        return "players." + uuid.toString();
    }

    public long getBalance(UUID uuid) {
        return data.getLong(path(uuid) + ".balance", 0L);
    }

    public void setBalance(UUID uuid, long amount) {
        data.set(path(uuid) + ".balance", Math.max(0L, amount));
    }

    public void addBalance(UUID uuid, long amount) {
        setBalance(uuid, getBalance(uuid) + Math.max(0L, amount));
    }

    public boolean subtractBalance(UUID uuid, long amount) {
        long bal = getBalance(uuid);
        if (amount <= 0) return true;
        if (bal < amount) return false;
        setBalance(uuid, bal - amount);
        return true;
    }

    public long getLastInterestDay(UUID uuid) {
        return data.getLong(path(uuid) + ".lastInterestDay", 0L);
    }

    public void setLastInterestDay(UUID uuid, long dayIndex) {
        data.set(path(uuid) + ".lastInterestDay", dayIndex);
    }

    /** Day index in UTC days since epoch. */
    public long currentDayIndex() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    /**
     * Apply 1% daily interest for any missed days.
     * Uses integer balance; interest rounds down each day.
     */
    public long applyDailyInterest(UUID uuid) {
        long today = currentDayIndex();
        long last = getLastInterestDay(uuid);

        // first time: set last and do nothing
        if (last == 0L) {
            setLastInterestDay(uuid, today);
            return 0L;
        }

        long days = today - last;
        if (days <= 0) return 0L;

        long bal = getBalance(uuid);
        long gainedTotal = 0L;

        // 1% per day, compounding; rounded down daily
        for (long i = 0; i < days; i++) {
            long gain = (long) Math.floor(bal * 0.01);
            if (gain <= 0) break; // nothing more to gain at this balance
            bal += gain;
            gainedTotal += gain;
        }

        setBalance(uuid, bal);
        setLastInterestDay(uuid, today);
        return gainedTotal;
    }

    /** If a team is inactive, advance the interest day so they can't "bank" missed days. */
    public void markInterestDayAsToday(UUID uuid) {
        setLastInterestDay(uuid, currentDayIndex());
    }
}
