package me.Evil.soulSMP.vault;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import me.Evil.soulSMP.util.InventoryUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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

public class TeamVaultManager {

    public static final int VAULT_INVENTORY_SIZE = 27;

    private final Plugin plugin;
    private final TeamManager teamManager;

    /** Cache vaults by stable key (NOT Team object instance). */
    private final Map<String, Inventory> vaults = new HashMap<>();

    private File vaultFile;
    private FileConfiguration vaultConfig;

    private final ItemStack lockedPaneTemplate;
    private final ItemStack shopIconTemplate;

    public TeamVaultManager(Plugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        initFile();
        this.lockedPaneTemplate = createLockedPaneTemplate();
        this.shopIconTemplate = createShopIconTemplate();
    }

    private String teamKey(Team team) {
        return team.getName().toLowerCase();
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
     * Tries to find the actual stored key for this team in vaults.yml.
     * Supports migrating old keys like "Flyhigh" -> "flyhigh".
     */
    private String findStoredVaultPathForTeam(Team team) {
        if (vaultConfig == null || team == null) return null;

        String norm = teamKey(team);
        String normPath = "vaults." + norm;
        if (vaultConfig.contains(normPath)) return normPath;

        // Try exact (legacy behavior)
        String exactPath = "vaults." + team.getName();
        if (vaultConfig.contains(exactPath)) return exactPath;

        // Try case-insensitive scan of vaults section
        ConfigurationSection sec = vaultConfig.getConfigurationSection("vaults");
        if (sec == null) return null;

        for (String k : sec.getKeys(false)) {
            if (k != null && k.equalsIgnoreCase(team.getName())) {
                return "vaults." + k;
            }
        }

        return null;
    }

    /**
     * Layout rules:
     * - Slots [0, allowed) are usable
     * - Slots [allowed, 26) are locked panes
     * - Slot 26 is always the shop icon
     */
    public void refreshVaultLayout(Team team) {
        if (team == null) return;

        Inventory inv = vaults.get(teamKey(team));
        if (inv == null) return;

        int allowed = Math.max(0, Math.min(team.getVaultSize(), VAULT_INVENTORY_SIZE));
        int lastIndex = VAULT_INVENTORY_SIZE - 1;

        for (int slot = 0; slot < inv.getSize(); slot++) {

            if (slot == lastIndex) {
                inv.setItem(slot, createShopIcon());
                continue;
            }

            if (slot < allowed) {
                ItemStack current = inv.getItem(slot);
                if (isLockedPane(current)) {
                    inv.setItem(slot, null);
                }
            } else {
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
        if (meta == null || !meta.hasDisplayName()) return false;

        return ChatColor.stripColor(meta.getDisplayName()).contains("Locked");
    }

    public boolean isSlotLocked(Team team, int slot) {
        if (team == null) return true;
        if (slot < 0 || slot >= VAULT_INVENTORY_SIZE) return true;

        int lastIndex = VAULT_INVENTORY_SIZE - 1;
        if (slot == lastIndex) return true;

        int allowed = Math.max(0, Math.min(team.getVaultSize(), VAULT_INVENTORY_SIZE));
        return slot >= allowed;
    }

    public Inventory getVault(Team team) {
        if (team == null) return null;

        String tKey = teamKey(team);

        Inventory inv = vaults.get(tKey);
        if (inv == null) {
            TeamVaultHolder holder = new TeamVaultHolder(team);

            inv = plugin.getServer().createInventory(
                    holder,
                    VAULT_INVENTORY_SIZE,
                    ChatColor.DARK_GREEN + "Team Vault - " + team.getName()
            );

            holder.setInventory(inv);
            vaults.put(tKey, inv);
        }

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
    // Persistence (Plain YAML)
    // ==========================

    /**
     * New format (plain YAML, NOT base64):
     *
     * vaults:
     *   teamname:
     *     items:
     *       "0": <ItemStack serialized>
     *       "5": <ItemStack serialized>
     *
     * Legacy format (base64 string) still loads ONCE and is migrated to the new format.
     */
    public void loadVaults() {
        initFile();
        if (vaultConfig == null) return;

        vaults.clear();

        boolean migratedAny = false;

        for (Team team : teamManager.getAllTeams()) {
            if (team == null) continue;

            String storedPath = findStoredVaultPathForTeam(team);
            if (storedPath == null) continue;

            Object raw = vaultConfig.get(storedPath);
            if (raw == null) continue;

            TeamVaultHolder holder = new TeamVaultHolder(team);
            Inventory inv = plugin.getServer().createInventory(
                    holder,
                    VAULT_INVENTORY_SIZE,
                    ChatColor.DARK_GREEN + "Team Vault - " + team.getName()
            );
            holder.setInventory(inv);

            boolean loaded = false;

            // ===== 1) Legacy base64 string =====
            if (raw instanceof String base64) {
                if (base64 != null && !base64.isEmpty()) {
                    try {
                        Inventory decoded = InventoryUtils.fromBase64(
                                base64,
                                ChatColor.DARK_GREEN + "Team Vault - " + team.getName(),
                                holder
                        );

                        // Copy decoded contents into our fixed-size inventory
                        ItemStack[] contents = decoded.getContents();
                        for (int i = 0; i < Math.min(contents.length, VAULT_INVENTORY_SIZE); i++) {
                            inv.setItem(i, contents[i]);
                        }

                        loaded = true;

                        // Migrate to new normalized key + plain format
                        String normPath = "vaults." + teamKey(team);
                        writeVaultToConfig(team, inv, normPath);

                        // Remove old legacy base64 entry (and old-case key)
                        vaultConfig.set(storedPath, null);

                        migratedAny = true;

                    } catch (IllegalStateException e) {
                        plugin.getLogger().warning("Failed to load legacy(base64) vault for team "
                                + team.getName() + ": " + e.getMessage());
                    }
                }
            }

            // ===== 2) New plain YAML section =====
            if (!loaded) {
                ConfigurationSection teamSec = vaultConfig.getConfigurationSection(storedPath);
                if (teamSec != null) {
                    readVaultFromConfig(inv, teamSec);
                    loaded = true;

                    // Migrate key to normalized lowercase if needed
                    String normPath = "vaults." + teamKey(team);
                    if (!storedPath.equals(normPath)) {
                        // Copy section over and remove old
                        writeVaultToConfig(team, inv, normPath);
                        vaultConfig.set(storedPath, null);
                        migratedAny = true;
                    }
                }
            }

            if (!loaded) {
                // Nothing usable found for this team
                continue;
            }

            vaults.put(teamKey(team), inv);

            // Always rebuild UI after load
            refreshVaultLayout(team);
        }

        if (migratedAny) {
            try {
                vaultConfig.save(vaultFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save migrated vaults.yml: " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + vaults.size() + " team vault(s).");
    }

    /**
     * Clean copy for persistence:
     * - Removes locked panes
     * - Removes shop icon
     * - Removes anything in the locked region [allowed, 26)
     */
    private Inventory makeSavableCopy(Team team, Inventory inv) {
        Inventory copy = plugin.getServer().createInventory(null, VAULT_INVENTORY_SIZE);
        copy.setContents(inv.getContents().clone());

        int lastIndex = VAULT_INVENTORY_SIZE - 1;

        // Remove shop icon
        copy.setItem(lastIndex, null);

        // Remove panes anywhere
        for (int i = 0; i < VAULT_INVENTORY_SIZE; i++) {
            if (isLockedPane(copy.getItem(i))) {
                copy.setItem(i, null);
            }
        }

        // Do not persist anything in locked slots
        int allowed = Math.max(0, Math.min(team.getVaultSize(), VAULT_INVENTORY_SIZE));
        for (int i = allowed; i < lastIndex; i++) {
            copy.setItem(i, null);
        }

        return copy;
    }

    /**
     * Writes the given inventory to config in the new plain YAML format at the given path:
     * vaults.<teamKey>:
     *   items:
     *     "slot": <ItemStack>
     */
    private void writeVaultToConfig(Team team, Inventory inv, String path) {
        if (vaultConfig == null) initFile();
        if (vaultConfig == null) return;
        if (team == null || inv == null || path == null || path.isEmpty()) return;

        // Ensure clean slate at that path (removes any leftover legacy string or old structure)
        vaultConfig.set(path, null);

        ConfigurationSection teamSec = vaultConfig.createSection(path);
        ConfigurationSection itemsSec = teamSec.createSection("items");

        ItemStack[] contents = inv.getContents();
        int max = Math.min(contents.length, VAULT_INVENTORY_SIZE);

        for (int slot = 0; slot < max; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) continue;

            // Slot keys as strings to be safe with YAML
            itemsSec.set(String.valueOf(slot), item);
        }
    }

    /**
     * Reads the new plain YAML format into an inventory:
     * teamSection.items.<slot> = ItemStack
     */
    private void readVaultFromConfig(Inventory inv, ConfigurationSection teamSection) {
        if (inv == null || teamSection == null) return;

        ConfigurationSection itemsSec = teamSection.getConfigurationSection("items");
        if (itemsSec == null) return;

        for (String key : itemsSec.getKeys(false)) {
            if (key == null) continue;

            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (slot < 0 || slot >= VAULT_INVENTORY_SIZE) continue;

            ItemStack item = itemsSec.getItemStack(key);
            if (item == null || item.getType().isAir()) continue;

            inv.setItem(slot, item);
        }
    }

    public void saveVault(Team team) {
        if (team == null) return;
        if (vaultConfig == null) initFile();
        if (vaultConfig == null) return;

        Inventory inv = vaults.get(teamKey(team));
        if (inv == null) return;

        Inventory savable = makeSavableCopy(team, inv);

        String key = "vaults." + teamKey(team);
        writeVaultToConfig(team, savable, key);

        try {
            vaultConfig.save(vaultFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save vaults.yml for team "
                    + team.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Saves ALL vaults in a cleaned format.
     * Iterates teams so cleaning is always correct (no lookup required).
     */
    public void saveVaults() {
        if (vaultConfig == null) initFile();
        if (vaultConfig == null) return;

        vaultConfig.set("vaults", null);

        for (Team team : teamManager.getAllTeams()) {
            if (team == null) continue;

            Inventory inv = vaults.get(teamKey(team));
            if (inv == null) continue;

            Inventory savable = makeSavableCopy(team, inv);
            String key = "vaults." + teamKey(team);

            writeVaultToConfig(team, savable, key);
        }

        try {
            vaultConfig.save(vaultFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save vaults.yml: " + e.getMessage());
        }

        plugin.getLogger().info("Saved " + vaults.size() + " team vault(s).");
    }
}
