package me.Evil.soulSMP.vouchers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class VoucherMailManager {

    private final Plugin plugin;

    private File file;
    private FileConfiguration cfg;

    public VoucherMailManager(Plugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        file = new File(plugin.getDataFolder(), "voucher_mail.yml");
        if (!file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create voucher_mail.yml: " + e.getMessage());
            }
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void queue(UUID uuid, ItemStack stack) {
        if (uuid == null || stack == null) return;
        if (cfg == null) init();

        String path = "pending." + uuid.toString();
        List<Map<String, Object>> list = (List<Map<String, Object>>) cfg.getList(path);
        if (list == null) list = new ArrayList<>();

        list.add(stack.serialize());
        cfg.set(path, list);

        save();
    }

    public List<ItemStack> claim(UUID uuid) {
        if (uuid == null) return Collections.emptyList();
        if (cfg == null) init();

        String path = "pending." + uuid.toString();
        List<?> raw = cfg.getList(path);
        if (raw == null || raw.isEmpty()) return Collections.emptyList();

        List<ItemStack> out = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> map)) continue;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) map;
                ItemStack item = ItemStack.deserialize(m);
                if (item != null) out.add(item);
            } catch (Exception ignored) {}
        }

        // clear mail
        cfg.set(path, null);
        cleanupPendingIfEmpty();
        save();

        return out;
    }

    private void cleanupPendingIfEmpty() {
        ConfigurationSection pending = cfg.getConfigurationSection("pending");
        if (pending == null) return;
        if (pending.getKeys(false).isEmpty()) {
            cfg.set("pending", null);
        }
    }

    public void save() {
        if (cfg == null) return;
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save voucher_mail.yml: " + e.getMessage());
        }
    }
}
