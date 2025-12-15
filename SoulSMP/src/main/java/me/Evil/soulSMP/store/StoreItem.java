package me.Evil.soulSMP.store;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class StoreItem {
    public final String id;
    public final int slot;
    public final int price;
    public final GiveItem give;

    public StoreItem(String id, int slot, int price, GiveItem give) {
        this.id = id;
        this.slot = slot;
        this.price = price;
        this.give = give;
    }

    public static StoreItem fromSection(String id, ConfigurationSection s) {
        int slot = s.getInt("slot", 0);
        int price = s.getInt("price", 0);

        ConfigurationSection g = s.getConfigurationSection("give");
        if (g == null) g = s;

        Material mat = Material.matchMaterial(g.getString("material", "STONE"));
        int amt = g.getInt("amount", 1);

        // THIS is the flag you asked for:
        boolean metaEnabled = g.getBoolean("meta", false);

        String name = g.getString("name", null);
        List<String> lore = g.getStringList("lore");
        if (!lore.isEmpty()) {
            lore.add(0, " "); // whitespace line before lore
        }
        boolean unbreakable = g.getBoolean("unbreakable", false);
        Integer model = g.contains("custom-model-data") ? g.getInt("custom-model-data") : null;
        List<String> ench = g.getStringList("enchants");

        GiveItem give = new GiveItem(
                mat == null ? Material.STONE : mat,
                amt,
                metaEnabled,
                name,
                lore,
                unbreakable,
                model,
                ench
        );

        return new StoreItem(id, slot, price, give);
    }

    public static class GiveItem {
        public final Material material;
        public final int amount;

        // if false -> give vanilla item (no meta at all)
        public final boolean meta;

        public final String name;
        public final List<String> lore;
        public final boolean unbreakable;
        public final Integer customModelData;
        public final List<String> enchants;

        public GiveItem(Material material,
                        int amount,
                        boolean meta,
                        String name,
                        List<String> lore,
                        boolean unbreakable,
                        Integer customModelData,
                        List<String> enchants) {

            this.material = material;
            this.amount = Math.max(1, amount);
            this.meta = meta;

            this.name = name;
            this.lore = (lore != null ? lore : new ArrayList<>());
            this.unbreakable = unbreakable;
            this.customModelData = customModelData;
            this.enchants = (enchants != null ? enchants : new ArrayList<>());
        }
    }
}
