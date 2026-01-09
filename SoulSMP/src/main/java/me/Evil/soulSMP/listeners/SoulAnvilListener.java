package me.Evil.soulSMP.listeners;

import me.Evil.soulSMP.SoulSMP;
import me.Evil.soulSMP.anvil.SoulAnvilGui;
import me.Evil.soulSMP.anvil.SoulAnvilHolder;
import me.Evil.soulSMP.anvil.SoulAnvilSession;
import me.Evil.soulSMP.util.GiveOrDrop;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class SoulAnvilListener implements Listener {

    private final SoulSMP plugin;

    // PDC cost marker on result items
    private final NamespacedKey COST_KEY;

    // PDC "prior work" marker for books/items (your rule: 2+2 -> 3)
    private final NamespacedKey BOOK_WORK_KEY;

    // PDC nonce on result items (dupe-proofing / stale-output prevention)
    private final NamespacedKey OUT_NONCE_KEY;

    // rename flow (session scoped)
    private final Set<UUID> awaitingRename = new HashSet<>();
    private final Map<UUID, PendingRestore> pendingRestore = new HashMap<>();

    // output anti-spam/dupe guard (blocks multiple takes in same tick)
    private final Set<UUID> takingOutput = new HashSet<>();

    // per-player current valid output nonce
    private final Map<UUID, UUID> currentOutNonce = new HashMap<>();

    // cached config values
    private boolean enabled;
    private int maxDisplayCost;
    private double expBase;
    private int baseStepCost;

    private int repairBaseCost;
    private double repairPercentMultiplier;

    private boolean renameAllowColors;
    private boolean renameRequirePerm;
    private String renameColorPerm;

    private int wTreasure;
    private int wCursed;
    private int wProtection;
    private int wSharpnessPower;
    private int wEfficiency;
    private int wUnbreaking;
    private int wFortuneLooting;
    private int wSilkTouch;
    private int wDefault;

    private static class PendingRestore {
        final SoulAnvilSession session;
        final ItemStack left;
        final ItemStack right;

        PendingRestore(SoulAnvilSession session, ItemStack left, ItemStack right) {
            this.session = session;
            this.left = left;
            this.right = right;
        }
    }

    public SoulAnvilListener(SoulSMP plugin) {
        this.plugin = plugin;
        this.COST_KEY = new NamespacedKey(plugin, "soul_anvil_cost");
        this.BOOK_WORK_KEY = new NamespacedKey(plugin, "soul_anvil_book_work");
        this.OUT_NONCE_KEY = new NamespacedKey(plugin, "soul_anvil_out_nonce");
        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("soul-anvil");

        enabled = true;
        maxDisplayCost = 1_000_000;

        expBase = 1.5;
        baseStepCost = 4;

        repairBaseCost = 2;
        repairPercentMultiplier = 10.0;

        renameAllowColors = true;
        renameRequirePerm = true;
        renameColorPerm = "soulsmp.anvil.rename.color";

        wTreasure = 10;
        wCursed = 1;
        wProtection = 6;
        wSharpnessPower = 6;
        wEfficiency = 5;
        wUnbreaking = 4;
        wFortuneLooting = 8;
        wSilkTouch = 8;
        wDefault = 4;

        if (root == null) return;

        enabled = root.getBoolean("enabled", true);
        maxDisplayCost = Math.max(1, root.getInt("max-display-cost", maxDisplayCost));

        expBase = Math.max(1.0, root.getDouble("exp-base", expBase));
        baseStepCost = Math.max(1, root.getInt("base-step-cost", baseStepCost));

        repairBaseCost = Math.max(0, root.getInt("repair-base-cost", repairBaseCost));
        repairPercentMultiplier = Math.max(0.0, root.getDouble("repair-percent-multiplier", repairPercentMultiplier));

        ConfigurationSection ren = root.getConfigurationSection("rename");
        if (ren != null) {
            renameAllowColors = ren.getBoolean("allow-colors", renameAllowColors);
            renameRequirePerm = ren.getBoolean("require-permission", renameRequirePerm);
            renameColorPerm = ren.getString("permission", renameColorPerm);
        }

        ConfigurationSection weights = root.getConfigurationSection("weights");
        if (weights != null) {
            wTreasure = weights.getInt("treasure", wTreasure);
            wCursed = weights.getInt("cursed", wCursed);
            wProtection = weights.getInt("protection", wProtection);
            wSharpnessPower = weights.getInt("sharpness_power", wSharpnessPower);
            wEfficiency = weights.getInt("efficiency", wEfficiency);
            wUnbreaking = weights.getInt("unbreaking", wUnbreaking);
            wFortuneLooting = weights.getInt("fortune_looting", wFortuneLooting);
            wSilkTouch = weights.getInt("silk_touch", wSilkTouch);
            wDefault = weights.getInt("default", wDefault);
        }
    }

    // ---------------------------------------------------------------------
    // Replace anvils
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilUse(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Material t = e.getClickedBlock().getType();
        if (t != Material.ANVIL && t != Material.CHIPPED_ANVIL && t != Material.DAMAGED_ANVIL) return;

        e.setCancelled(true);
        SoulAnvilGui.open(e.getPlayer(), plugin, new SoulAnvilSession());
    }

    // ---------------------------------------------------------------------
    // Click handling (DUPE-PROOF: fully control top inventory interactions)
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof SoulAnvilHolder holder)) return;
        if (!holder.getOwner().equals(player.getUniqueId())) return;

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();
        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < topSize;

        // Block common mod/packet actions that are used to desync/dupe
        if (e.getClick() == ClickType.DOUBLE_CLICK) { e.setCancelled(true); return; }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        if (e.getAction() == InventoryAction.HOTBAR_SWAP) { e.setCancelled(true); return; }

        // -------------------------------------------------
        // Bottom inventory (player inv)
        // -------------------------------------------------
        if (!inTop) {
            // SHIFT-CLICK from player inv into the GUI: handle ourselves
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                ItemStack moving = e.getCurrentItem();
                if (isEmpty(moving)) { e.setCancelled(true); return; }

                e.setCancelled(true);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack left = top.getItem(SoulAnvilGui.SLOT_LEFT);
                    ItemStack right = top.getItem(SoulAnvilGui.SLOT_RIGHT);

                    // Only ever take ONE item into inputs (prevents stack weirdness/dupes)
                    if (isEmpty(left)) {
                        ItemStack one = moving.clone();
                        one.setAmount(1);
                        top.setItem(SoulAnvilGui.SLOT_LEFT, one);
                        decrementClickedStack(e.getClickedInventory(), e.getSlot(), 1);
                    } else if (isEmpty(right)) {
                        ItemStack one = moving.clone();
                        one.setAmount(1);
                        top.setItem(SoulAnvilGui.SLOT_RIGHT, one);
                        decrementClickedStack(e.getClickedInventory(), e.getSlot(), 1);
                    }

                    recompute(player, top);
                    player.updateInventory();
                });
                return;
            }

            // Allow normal inventory interaction; just recompute after.
            Bukkit.getScheduler().runTask(plugin, () -> recompute(player, top));
            return;
        }

        // -------------------------------------------------
        // Top inventory (our GUI): cancel and fully control
        // -------------------------------------------------
        e.setCancelled(true);

        int slot = e.getSlot();

        // Rename button
        if (slot == SoulAnvilGui.SLOT_RENAME) {
            Bukkit.getScheduler().runTask(plugin, () -> beginRename(player, top, holder));
            return;
        }

        // Locked filler/buttons
        if (SoulAnvilGui.isLockedSlot(slot)) {
            return;
        }

        // Output slot
        if (slot == SoulAnvilGui.SLOT_OUT) {
            Bukkit.getScheduler().runTask(plugin, () -> handleTakeOutput(player, top));
            return;
        }

        // Input slots: server-controlled cursor <-> slot
        if (slot == SoulAnvilGui.SLOT_LEFT || slot == SoulAnvilGui.SLOT_RIGHT) {

            // Shift-click on input slot: move that item back to player inv
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack moving = top.getItem(slot);
                    if (isEmpty(moving)) return;

                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(moving.clone());
                    if (leftovers.isEmpty()) {
                        top.setItem(slot, null);
                    }
                    recompute(player, top);
                    player.updateInventory();
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack cursor = e.getView().getCursor();
                ItemStack inSlot = top.getItem(slot);

                // pick up from slot
                if (!isEmpty(inSlot) && isEmpty(cursor)) {
                    e.getView().setCursor(inSlot.clone());
                    top.setItem(slot, null);
                    recompute(player, top);
                    player.updateInventory();
                    return;
                }

                // place into slot (ONLY 1 item)
                if (isEmpty(inSlot) && !isEmpty(cursor)) {
                    ItemStack one = cursor.clone();
                    one.setAmount(1);
                    top.setItem(slot, one);

                    // decrement cursor by 1
                    int amt = cursor.getAmount();
                    if (amt <= 1) {
                        e.getView().setCursor(null);
                    } else {
                        cursor.setAmount(amt - 1);
                        e.getView().setCursor(cursor);
                    }

                    recompute(player, top);
                    player.updateInventory();
                    return;
                }

                // if both occupied, do nothing (prevents swap-based dupes)
                recompute(player, top);
                player.updateInventory();
            });
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof SoulAnvilHolder holder)) return;
        if (!holder.getOwner().equals(player.getUniqueId())) return;

        // Only allow dragging into the two input slots, and only if every raw slot is input.
        for (int raw : e.getRawSlots()) {
            if (raw < 0 || raw >= e.getInventory().getSize()) continue;
            if (raw != SoulAnvilGui.SLOT_LEFT && raw != SoulAnvilGui.SLOT_RIGHT) {
                e.setCancelled(true);
                return;
            }
        }

        // Prevent multi-slot drag that places stacks; we only want 1 item in each input.
        for (Map.Entry<Integer, ItemStack> entry : e.getNewItems().entrySet()) {
            int raw = entry.getKey();
            if (raw == SoulAnvilGui.SLOT_LEFT || raw == SoulAnvilGui.SLOT_RIGHT) {
                ItemStack it = entry.getValue();
                if (!isEmpty(it) && it.getAmount() > 1) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> recompute(player, e.getInventory()));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof SoulAnvilHolder holder)) return;
        if (!holder.getOwner().equals(player.getUniqueId())) return;

        // if we're in rename flow, don't dump items yet (we re-open after chat)
        if (awaitingRename.contains(player.getUniqueId())) return;

        // clear nonce tracking
        currentOutNonce.remove(player.getUniqueId());
        takingOutput.remove(player.getUniqueId());

        ItemStack left = e.getInventory().getItem(SoulAnvilGui.SLOT_LEFT);
        ItemStack right = e.getInventory().getItem(SoulAnvilGui.SLOT_RIGHT);

        if (!isEmpty(left)) GiveOrDrop.give(player, left);
        if (!isEmpty(right)) GiveOrDrop.give(player, right);
    }

    // ---------------------------------------------------------------------
    // Rename via chat
    // ---------------------------------------------------------------------

    private void beginRename(Player player, Inventory top, SoulAnvilHolder holder) {
        UUID id = player.getUniqueId();

        if (awaitingRename.contains(id)) return;

        // snapshot inputs and session
        ItemStack left = cloneOrNull(top.getItem(SoulAnvilGui.SLOT_LEFT));
        ItemStack right = cloneOrNull(top.getItem(SoulAnvilGui.SLOT_RIGHT));

        pendingRestore.put(id, new PendingRestore(holder.getSession(), left, right));
        awaitingRename.add(id);

        player.closeInventory();
        player.sendMessage(ChatColor.AQUA + "Type the new name in chat. " + ChatColor.GRAY + "(type "
                + ChatColor.YELLOW + "cancel" + ChatColor.GRAY + " to abort)");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatRename(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID id = player.getUniqueId();
        if (!awaitingRename.contains(id)) return;

        e.setCancelled(true);
        awaitingRename.remove(id);

        PendingRestore restore = pendingRestore.remove(id);
        if (restore == null) {
            player.sendMessage(ChatColor.RED + "Rename session lost. Re-open the anvil.");
            return;
        }

        String raw = e.getMessage();
        if (raw == null) raw = "";
        raw = raw.trim();

        if (raw.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "Rename cancelled.");
            Bukkit.getScheduler().runTask(plugin, () -> {
                SoulAnvilGui.open(player, plugin, restore.session);
                Inventory reopened = player.getOpenInventory().getTopInventory();
                reopened.setItem(SoulAnvilGui.SLOT_LEFT, restore.left);
                reopened.setItem(SoulAnvilGui.SLOT_RIGHT, restore.right);
                recompute(player, reopened);
            });
            return;
        }

        if (raw.length() > 50) raw = raw.substring(0, 50);

        String finalName;
        if (renameAllowColors && (!renameRequirePerm || player.hasPermission(renameColorPerm))) {
            finalName = ChatColor.translateAlternateColorCodes('&', raw);
        } else {
            finalName = ChatColor.stripColor(raw);
            if (finalName == null) finalName = "";
        }

        restore.session.setRename(finalName);

        Bukkit.getScheduler().runTask(plugin, () -> {
            SoulAnvilGui.open(player, plugin, restore.session);
            Inventory reopened = player.getOpenInventory().getTopInventory();
            reopened.setItem(SoulAnvilGui.SLOT_LEFT, restore.left);
            reopened.setItem(SoulAnvilGui.SLOT_RIGHT, restore.right);
            recompute(player, reopened);
        });
    }

    // ---------------------------------------------------------------------
    // Core: compute + take output
    // ---------------------------------------------------------------------

    private void recompute(Player player, Inventory top) {
        if (!(top.getHolder() instanceof SoulAnvilHolder holder)) return;

        ItemStack left = top.getItem(SoulAnvilGui.SLOT_LEFT);
        ItemStack right = top.getItem(SoulAnvilGui.SLOT_RIGHT);

        // refresh rename button lore (so it shows current selection)
        SoulAnvilGui.setRenameButton(top, holder.getSession());

        if (isEmpty(left) || isEmpty(right)) {
            SoulAnvilGui.setBlockedOutput(top);
            SoulAnvilGui.setStatus(top, SoulAnvilGui.CraftStatus.NONE);
            currentOutNonce.remove(player.getUniqueId());
            return;
        }

        boolean leftBookLike = isBookLike(left);
        boolean rightBookLike = isBookLike(right);

        boolean sameTypeItems = (!leftBookLike && !rightBookLike && left.getType() == right.getType());
        boolean bothBooks = (leftBookLike && rightBookLike);
        boolean itemAndBook = (leftBookLike ^ rightBookLike);

        if (!(sameTypeItems || bothBooks || itemAndBook)) {
            SoulAnvilGui.setBlockedOutput(top);
            SoulAnvilGui.setStatus(top, SoulAnvilGui.CraftStatus.INVALID);
            currentOutNonce.remove(player.getUniqueId());
            return;
        }

        ItemStack base = (itemAndBook ? (leftBookLike ? right : left) : left);
        ItemStack result = base.clone();

        if (bothBooks) result.setType(Material.ENCHANTED_BOOK);

        int computedCost = 0;
        boolean changed = false;

        if (sameTypeItems && isDamageable(left) && isDamageable(right)) {
            RepairOutcome ro = mergeRepair(left, right, result);
            result = ro.result;
            computedCost += ro.cost;
            if (ro.cost > 0) changed = true;
        }

        Map<Enchantment, Integer> leftEnchants = getAllEnchants(left);
        Map<Enchantment, Integer> rightEnchants = getAllEnchants(right);

        Map<Enchantment, Integer> merged = getAllEnchants(result);

        if (!rightEnchants.isEmpty()) {
            for (Map.Entry<Enchantment, Integer> ent : rightEnchants.entrySet()) {
                Enchantment ench = ent.getKey();
                int rLevel = ent.getValue();

                if (conflictsWithAny(ench, merged)) continue;

                boolean alreadyPresent = leftEnchants.containsKey(ench) || merged.containsKey(ench);

                boolean resultIsBook = isEnchantedBook(result);
                if (!resultIsBook && !alreadyPresent && !ench.canEnchantItem(result)) continue;

                int leftLevel = leftEnchants.getOrDefault(ench, 0);
                int baseLevel = merged.getOrDefault(ench, 0);
                int current = Math.max(leftLevel, baseLevel);

                int newLevel = (current > 0 && current == rLevel) ? current + 1 : Math.max(current, rLevel);

                if (newLevel != baseLevel) {
                    merged.put(ench, newLevel);
                    changed = true;

                    // Fresh add => base "level 1" cost, multiplied by (bookWork - 1)
                    if (current <= 0) {
                        int mult = 1;
                        if (rightBookLike) {
                            mult = bookWorkMultiplier(getBookWork(right)); // count 3 => mult 2
                        }
                        computedCost += levelStepCost(ench, 1) * mult;
                    } else {
                        computedCost += costForUpgrade(ench, current, newLevel);
                    }
                }
            }
        }

        if (!changed) {
            SoulAnvilGui.setBlockedOutput(top);
            SoulAnvilGui.setStatus(top, SoulAnvilGui.CraftStatus.INVALID);
            currentOutNonce.remove(player.getUniqueId());
            return;
        }

        applyMergedEnchantsClean(result, merged);

        String rename = holder.getSession().getRename();
        if (rename != null && !rename.isEmpty()) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(rename);
                result.setItemMeta(meta);
            }
        }

        // BOOK WORK RULE: resultWork = max(leftWork, rightWork) + 1
        if (bothBooks) {
            int lw = getBookWork(left);
            int rw = getBookWork(right);
            setBookWork(result, Math.max(lw, rw) + 1);
        }

        int trueCost = Math.max(1, computedCost);
        if (trueCost > maxDisplayCost) trueCost = maxDisplayCost;

        setTrueCost(result, trueCost);
        addOrReplaceCostLore(result, trueCost);

        // Dupe-proof nonce: output is only valid if nonce matches currentOutNonce[player]
        UUID nonce = UUID.randomUUID();
        currentOutNonce.put(player.getUniqueId(), nonce);
        setOutNonce(result, nonce);

        top.setItem(SoulAnvilGui.SLOT_OUT, result);
        SoulAnvilGui.setStatus(top, SoulAnvilGui.CraftStatus.VALID);
    }

    private void handleTakeOutput(Player player, Inventory top) {
        UUID pid = player.getUniqueId();

        // one-tick lock to prevent packet spam / client mods double-taking
        if (takingOutput.contains(pid)) return;
        takingOutput.add(pid);
        Bukkit.getScheduler().runTask(plugin, () -> takingOutput.remove(pid));

        ItemStack out = top.getItem(SoulAnvilGui.SLOT_OUT);
        if (isEmpty(out)) return;

        // must be a real computed output, not the barrier
        Integer cost = getTrueCost(out);
        if (cost == null) return;

        // validate nonce
        UUID expected = currentOutNonce.get(pid);
        UUID found = getOutNonce(out);
        if (expected == null || found == null || !expected.equals(found)) {
            SoulAnvilGui.setBlockedOutput(top);
            SoulAnvilGui.setStatus(top, SoulAnvilGui.CraftStatus.INVALID);
            return;
        }

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getLevel() < cost) {
            player.sendMessage(ChatColor.RED + "You need " + ChatColor.GOLD + cost + ChatColor.RED + " levels.");
            return;
        }

        // Re-validate inputs still exist BEFORE consuming
        ItemStack left = top.getItem(SoulAnvilGui.SLOT_LEFT);
        ItemStack right = top.getItem(SoulAnvilGui.SLOT_RIGHT);
        if (isEmpty(left) || isEmpty(right)) {
            SoulAnvilGui.setBlockedOutput(top);
            SoulAnvilGui.setStatus(top, SoulAnvilGui.CraftStatus.INVALID);
            currentOutNonce.remove(pid);
            return;
        }

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            player.giveExpLevels(-cost);
        }

        ItemStack give = out.clone();
        clearTrueCost(give);
        stripCostLore(give);
        clearOutNonce(give);

        // consume first
        top.setItem(SoulAnvilGui.SLOT_LEFT, null);
        top.setItem(SoulAnvilGui.SLOT_RIGHT, null);
        SoulAnvilGui.setBlockedOutput(top);
        SoulAnvilGui.setStatus(top, SoulAnvilGui.CraftStatus.NONE);
        currentOutNonce.remove(pid);

        GiveOrDrop.give(player, give);
        player.updateInventory();
    }

    // ---------------------------------------------------------------------
    // Repair merge
    // ---------------------------------------------------------------------

    private static class RepairOutcome {
        final ItemStack result;
        final int cost;
        RepairOutcome(ItemStack result, int cost) { this.result = result; this.cost = cost; }
    }

    private RepairOutcome mergeRepair(ItemStack left, ItemStack right, ItemStack baseResult) {
        ItemStack result = baseResult.clone();

        ItemMeta lm = left.getItemMeta();
        ItemMeta rm = right.getItemMeta();
        ItemMeta om = result.getItemMeta();

        if (!(lm instanceof Damageable ld) || !(rm instanceof Damageable rd) || !(om instanceof Damageable od)) {
            return new RepairOutcome(result, 0);
        }

        int max = left.getType().getMaxDurability();
        if (max <= 0) return new RepairOutcome(result, 0);

        int leftDamage = ld.getDamage();
        int rightDamage = rd.getDamage();

        int leftRemain = max - leftDamage;
        int rightRemain = max - rightDamage;

        int bonus = (int) Math.floor(max * 0.12);
        int newRemain = Math.min(max, leftRemain + rightRemain + bonus);
        int newDamage = Math.max(0, max - newRemain);

        od.setDamage(newDamage);
        result.setItemMeta((ItemMeta) od);

        int repaired = Math.max(0, Math.min(max, leftDamage) - newDamage);
        double pct = (max == 0) ? 0.0 : (repaired / (double) max);
        int cost = repairBaseCost + (int) Math.ceil(pct * repairPercentMultiplier);

        return new RepairOutcome(result, Math.max(0, cost));
    }

    private boolean isDamageable(ItemStack it) {
        if (it == null) return false;
        ItemMeta m = it.getItemMeta();
        return m instanceof Damageable;
    }

    // ---------------------------------------------------------------------
    // Enchant logic helpers
    // ---------------------------------------------------------------------

    private boolean conflictsWithAny(Enchantment ench, Map<Enchantment, Integer> current) {
        for (Enchantment existing : current.keySet()) {
            if (existing.equals(ench)) continue;
            if (existing.conflictsWith(ench)) return true;
        }
        return false;
    }

    private boolean isBookLike(ItemStack item) {
        if (item == null) return false;
        Material t = item.getType();
        return t == Material.BOOK || t == Material.ENCHANTED_BOOK;
    }

    private boolean isEnchantedBook(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof EnchantmentStorageMeta;
    }

    private Map<Enchantment, Integer> getAllEnchants(ItemStack item) {
        Map<Enchantment, Integer> out = new HashMap<>();
        if (item == null) return out;

        out.putAll(item.getEnchantments());

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta esm) {
            out.putAll(esm.getStoredEnchants());
        }
        return out;
    }

    private void applyMergedEnchantsClean(ItemStack result, Map<Enchantment, Integer> merged) {
        if (result == null) return;

        for (Enchantment ench : new HashMap<>(result.getEnchantments()).keySet()) {
            result.removeEnchantment(ench);
        }

        ItemMeta meta = result.getItemMeta();

        if (meta instanceof EnchantmentStorageMeta esm) {
            for (Enchantment ench : new HashMap<>(esm.getStoredEnchants()).keySet()) {
                esm.removeStoredEnchant(ench);
            }
            result.setItemMeta(esm);
            meta = result.getItemMeta();
        }

        if (isEnchantedBook(result) && meta instanceof EnchantmentStorageMeta esm) {
            for (Map.Entry<Enchantment, Integer> me : merged.entrySet()) {
                esm.addStoredEnchant(me.getKey(), me.getValue(), true);
            }
            result.setItemMeta(esm);
        } else {
            for (Map.Entry<Enchantment, Integer> me : merged.entrySet()) {
                result.addUnsafeEnchantment(me.getKey(), me.getValue());
            }
        }
    }

    private int levelStepCost(Enchantment ench, int level) {
        int weight = enchantWeight(ench);
        double exp = Math.pow(expBase, Math.max(0, level - 1));
        return (int) Math.ceil(weight * baseStepCost * exp);
    }

    /**
     * IMPORTANT CHANGE:
     * Upgrades only charge the target level's step cost (no summing 2..N).
     * This keeps Prot 1 + Prot 3 from exploding.
     */
    private int costForUpgrade(Enchantment ench, int current, int newLevel) {
        if (newLevel <= current) return 0;
        return levelStepCost(ench, newLevel);
    }

    private int enchantWeight(Enchantment ench) {
        if (ench.isTreasure()) return wTreasure;
        if (ench.isCursed()) return wCursed;

        String key = ench.getKey().getKey();
        if (key.contains("protection")) return wProtection;
        if (key.contains("sharpness") || key.contains("power")) return wSharpnessPower;
        if (key.contains("efficiency")) return wEfficiency;
        if (key.contains("unbreaking")) return wUnbreaking;
        if (key.contains("fortune") || key.contains("looting")) return wFortuneLooting;
        if (key.contains("silk_touch")) return wSilkTouch;

        return wDefault;
    }

    // ---------------------------------------------------------------------
    // Cost tagging + lore
    // ---------------------------------------------------------------------

    private void setTrueCost(ItemStack item, int cost) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(COST_KEY, PersistentDataType.INTEGER, cost);
        item.setItemMeta(meta);
    }

    private Integer getTrueCost(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(COST_KEY, PersistentDataType.INTEGER);
    }

    private void clearTrueCost(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(COST_KEY);
        item.setItemMeta(meta);
    }

    private void addOrReplaceCostLore(ItemStack item, int cost) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();
        lore.removeIf(s -> ChatColor.stripColor(s).toLowerCase(Locale.ROOT).startsWith("cost:"));

        lore.add(ChatColor.DARK_GRAY + "Cost: " + ChatColor.GOLD + cost + ChatColor.GRAY + " levels");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void stripCostLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<String> lore = new ArrayList<>(Objects.requireNonNull(meta.getLore()));
        lore.removeIf(s -> ChatColor.stripColor(s).toLowerCase(Locale.ROOT).startsWith("cost:"));
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    // ---------------------------------------------------------------------
    // Output nonce (dupe-proofing)
    // ---------------------------------------------------------------------

    private void setOutNonce(ItemStack item, UUID nonce) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(OUT_NONCE_KEY, PersistentDataType.STRING, nonce.toString());
        item.setItemMeta(meta);
    }

    private UUID getOutNonce(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String s = meta.getPersistentDataContainer().get(OUT_NONCE_KEY, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void clearOutNonce(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(OUT_NONCE_KEY);
        item.setItemMeta(meta);
    }

    // ---------------------------------------------------------------------
    // Book Work (2+2 -> 3)
    // ---------------------------------------------------------------------

    private int getBookWork(ItemStack item) {
        if (item == null) return 1;
        if (!isBookLike(item)) return 1;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 1;

        Integer v = meta.getPersistentDataContainer().get(BOOK_WORK_KEY, PersistentDataType.INTEGER);
        if (v == null || v < 1) return 1;
        return v;
    }

    private void setBookWork(ItemStack item, int work) {
        if (item == null) return;
        if (!isBookLike(item)) return;

        int safe = Math.max(1, work);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(BOOK_WORK_KEY, PersistentDataType.INTEGER, safe);
        item.setItemMeta(meta);
    }

    // count 1 => 1, count 2 => 1, count 3 => 2, count 4 => 3, ...
    private int bookWorkMultiplier(int bookWork) {
        return Math.max(1, Math.max(1, bookWork) - 1);
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    private boolean isEmpty(ItemStack it) {
        return it == null || it.getType() == Material.AIR;
    }

    private ItemStack cloneOrNull(ItemStack it) {
        if (isEmpty(it)) return null;
        return it.clone();
    }

    private void decrementClickedStack(Inventory inv, int slot, int amount) {
        if (inv == null) return;
        ItemStack cur = inv.getItem(slot);
        if (isEmpty(cur)) return;

        int amt = cur.getAmount();
        if (amt <= amount) {
            inv.setItem(slot, null);
        } else {
            cur.setAmount(amt - amount);
            inv.setItem(slot, cur);
        }
    }
}
