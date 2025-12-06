package me.Evil.soulSMP.vault;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.util.InventoryUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages team vault inventories, slot-lock logic, and YAML persistence.
 */
public class TeamVaultManager {

    public static final int VAULT_INVENTORY_SIZE = 27;

    private final Plugin plugin;
    private final TeamManager teamManager;
    private final Map<Team, Inventory> vaults = new HashMap<>();

    private File vaultFile;
    private FileConfiguration vaultConfig;

    // Template for locked slots (cloned per slot)
    private final ItemStack lockedPaneTemplate;

    // Template for the vault shop icon (last slot)
    private final ItemStack shopIconTemplate;

    public TeamVaultManager(Plugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        initFile();
        this.lockedPaneTemplate = createLockedPaneTemplate();
        this.shopIconTemplate = createShopIconTemplate();
    }

    private void initFile() {
        vaultFile = new File(plugin.getDataFolder(), "vaults.yml");
        if (!vaultFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                vaultFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create vaults.yml: " + e.getMessage());
            }
        }
        vaultConfig = YamlConfiguration.loadConfiguration(vaultFile);
    }

    private ItemStack createLockedPaneTemplate() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_GRAY + "Locked Slot");
            meta.setLore(java.util.List.of(
                    ChatColor.GRAY + "Upgrade your team vault",
                    ChatColor.GRAY + "to unlock more space."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createLockedPane() {
        return lockedPaneTemplate.clone();
    }

    private ItemStack createShopIconTemplate() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Vault & Banner Shop");
            meta.setLore(java.util.List.of(
                    ChatColor.AQUA + "Click to open the upgrade shop.",
                    ChatColor.GRAY + "Spend Soul Tokens on:",
                    ChatColor.GRAY + "- Claim radius",
                    ChatColor.GRAY + "- Banner perks",
                    ChatColor.GRAY + "- Team progression"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShopIcon() {
        return shopIconTemplate.clone();
    }

    /**
     * Ensures that:
     * - Slots [0, vaultSize) are normal (no locked panes forced in them)
     * - Slots [vaultSize, VAULT_INVENTORY_SIZE-1) are filled with locked panes
     * - The very last slot (index 26) is always the shop icon
     */
    public void refreshVaultLayout(Team team) {
        Inventory inv = vaults.get(team);
        if (inv == null) return;

        int size = inv.getSize(); // should be 27
        int allowed = team.getVaultSize();
        if (allowed < 0) allowed = 0;
        if (allowed > VAULT_INVENTORY_SIZE) allowed = VAULT_INVENTORY_SIZE;

        int lastIndex = VAULT_INVENTORY_SIZE - 1; // 26

        for (int slot = 0; slot < size; slot++) {

            // Reserve the last slot for the shop icon
            if (slot == lastIndex) {
                inv.setItem(slot, createShopIcon());
                continue;
            }

            if (slot < allowed) {
                // Unlocked region: if we previously had a locked pane here, clear it
                ItemStack current = inv.getItem(slot);
                if (isLockedPane(current)) {
                    inv.setItem(slot, null);
                }
            } else {
                // Locked region: enforce locked pane
                ItemStack current = inv.getItem(slot);
                if (!isLockedPane(current)) {
                    inv.setItem(slot, createLockedPane());
                }
            }
        }
    }

    public boolean isLockedPane(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.BLACK_STAINED_GLASS_PANE) return false;

        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        if (!meta.hasDisplayName()) return false;

        return ChatColor.stripColor(meta.getDisplayName()).contains("Locked");
    }

    /**
     * Only the first team.getVaultSize() slots are usable for storage.
     * The last slot (index 26) is always treated as locked (shop icon).
     */
    public boolean isSlotLocked(Team team, int slot) {
        if (team == null) return true;
        if (slot < 0 || slot >= VAULT_INVENTORY_SIZE) return true;

        int lastIndex = VAULT_INVENTORY_SIZE - 1;
        if (slot == lastIndex) return true; // shop slot is never storage

        int allowed = team.getVaultSize();
        if (allowed < 0) allowed = 0;
        if (allowed > VAULT_INVENTORY_SIZE) allowed = VAULT_INVENTORY_SIZE;

        return slot >= allowed;
    }

    /**
     * Gets or creates the vault inventory for a team.
     */
    public Inventory getVault(Team team) {
        if (team == null) return null;

        Inventory inv = vaults.get(team);
        if (inv == null) {
            TeamVaultHolder holder = new TeamVaultHolder(team);

            inv = plugin.getServer().createInventory(
                    holder,
                    VAULT_INVENTORY_SIZE,
                    ChatColor.DARK_GREEN + "Team Vault - " + team.getName()
            );

            holder.setInventory(inv);
            vaults.put(team, inv);
        }

        // Ensure locked panes + shop icon are in correct slots
        refreshVaultLayout(team);

        return inv;
    }

    public void openVault(Player player, Team team) {
        Inventory inv = getVault(team);
        if (inv == null) {
            player.sendMessage(ChatColor.RED + "Could not open team vault.");
            return;
        }
        player.openInventory(inv);
    }

    // ==========================
    // Persistence
    // ==========================

    public void loadVaults() {
        initFile();

        if (vaultConfig == null) return;

        for (Team team : teamManager.getAllTeams()) {
            String key = "vaults." + team.getName();
            if (!vaultConfig.contains(key)) continue;

            String base64 = vaultConfig.getString(key);
            if (base64 == null || base64.isEmpty()) continue;

            try {
                TeamVaultHolder holder = new TeamVaultHolder(team);
                Inventory inv = InventoryUtils.fromBase64(
                        base64,
                        ChatColor.DARK_GREEN + "Team Vault - " + team.getName(),
                        holder
                );
                vaults.put(team, inv);

                // Ensure locked panes & shop icon in the right slots after loading
                refreshVaultLayout(team);

            } catch (IllegalStateException e) {
                plugin.getLogger().warning("Failed to load vault for team " + team.getName() + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + vaults.size() + " team vault(s).");
    }

    public void saveVault(Team team) {
        if (team == null) return;

        if (vaultConfig == null) {
            initFile();
        }

        Inventory inv = vaults.get(team);
        if (inv == null) return;

        String key = "vaults." + team.getName();
        String base64 = InventoryUtils.toBase64(inv);
        vaultConfig.set(key, base64);

        try {
            vaultConfig.save(vaultFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save vaults.yml for team " + team.getName() + ": " + e.getMessage());
        }
    }

    public void saveVaults() {
        if (vaultConfig == null) {
            initFile();
        }

        vaultConfig.set("vaults", null);

        for (Map.Entry<Team, Inventory> entry : vaults.entrySet()) {
            Team team = entry.getKey();
            Inventory inv = entry.getValue();
            if (team == null || inv == null) continue;

            String key = "vaults." + team.getName();
            String base64 = InventoryUtils.toBase64(inv);
            vaultConfig.set(key, base64);
        }

        try {
            vaultConfig.save(vaultFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save vaults.yml: " + e.getMessage());
        }

        plugin.getLogger().info("Saved " + vaults.size() + " team vault(s).");
    }
}
