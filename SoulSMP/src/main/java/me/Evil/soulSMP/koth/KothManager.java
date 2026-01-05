package me.Evil.soulSMP.koth;

import me.Evil.soulSMP.team.Team;
import me.Evil.soulSMP.team.TeamManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class KothManager {

    private final Plugin plugin;
    private final TeamManager teams;
    private final KothKitSettings kit;

    private boolean active = false;
    private boolean prepared = false;         // ✅ lobby phase: /koth x y z prepared, waiting /koth start
    private Location center;

    private final Set<UUID> participants = new HashSet<>();
    private final Map<String, Integer> progressSecondsByTeam = new HashMap<>();

    private Scoreboard board;
    private Objective obj;
    private int taskId = -1;

    // ======================
    // Return location (teleport back)
    // ======================

    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Set<UUID> pendingReturn = new HashSet<>();

    // ======================
    // Inventory save/restore
    // ======================

    private static class SavedInv {
        final ItemStack[] contents;
        final ItemStack[] armor;
        final ItemStack offhand;

        final int level;
        final float exp;
        final int totalExp;

        SavedInv(ItemStack[] contents, ItemStack[] armor, ItemStack offhand,
                 int level, float exp, int totalExp) {
            this.contents = contents;
            this.armor = armor;
            this.offhand = offhand;
            this.level = level;
            this.exp = exp;
            this.totalExp = totalExp;
        }
    }

    private final Map<UUID, SavedInv> savedInventories = new HashMap<>();
    private final Set<UUID> pendingRestore = new HashSet<>();

    // ======================
    // Per-event win seconds override (for /koth 60)
    // ======================

    private Integer winSecondsOverride = null;

    public KothManager(Plugin plugin, TeamManager teams, KothKitSettings kit) {
        this.plugin = plugin;
        this.teams = teams;
        this.kit = kit;
    }

    public boolean isActive() { return active; }
    public boolean isPrepared() { return prepared; }

    public boolean isParticipant(UUID id) {
        return id != null && participants.contains(id);
    }

    public Set<UUID> getParticipantsSnapshot() {
        return new HashSet<>(participants);
    }

    public Location getCenter() { return center == null ? null : center.clone(); }

    /** Current goal seconds (supports /koth <seconds> override) */
    public int getWinSeconds() {
        return (winSecondsOverride != null ? winSecondsOverride : kit.getWinSeconds());
    }

    /** For /koth status (snapshot so callers can’t mutate live map) */
    public Map<String, Integer> getProgressSnapshot() {
        return new HashMap<>(progressSecondsByTeam);
    }

    // ----------------------
    // Pending restore helpers
    // ----------------------

    public void restoreIfPending(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        if (!pendingRestore.contains(id)) return;
        restorePlayerInventory(p);
    }

    // ----------------------
    // Pending return helpers
    // ----------------------

    public void teleportBackIfPending(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        if (!pendingReturn.contains(id)) return;

        Location back = returnLocations.get(id);
        if (back != null && back.getWorld() != null) {
            p.teleport(back);
        }
        pendingReturn.remove(id);
        returnLocations.remove(id);
    }

    private void savePlayerInventory(Player p) {
        if (p == null) return;

        UUID id = p.getUniqueId();

        // Only save once per KOTH session
        if (savedInventories.containsKey(id)) return;

        PlayerInventory inv = p.getInventory();

        ItemStack[] contents = inv.getContents().clone();
        ItemStack[] armor = inv.getArmorContents().clone();
        ItemStack offhand = inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone();

        int level = p.getLevel();
        float exp = p.getExp();
        int totalExp = p.getTotalExperience();

        savedInventories.put(id, new SavedInv(contents, armor, offhand, level, exp, totalExp));
    }

    private boolean restorePlayerInventory(Player p) {
        if (p == null) return false;

        UUID id = p.getUniqueId();
        SavedInv saved = savedInventories.get(id);
        if (saved == null) return false;

        PlayerInventory inv = p.getInventory();

        inv.clear();
        inv.setArmorContents(saved.armor);
        inv.setContents(saved.contents);
        inv.setItemInOffHand(saved.offhand);

        p.setTotalExperience(0);
        p.setLevel(saved.level);
        p.setExp(saved.exp);
        p.setTotalExperience(saved.totalExp);

        savedInventories.remove(id);
        pendingRestore.remove(id);
        return true;
    }

    // ======================
    // Lifecycle
    // ======================

    /** /koth x y z -> prepares KOTH and opens join (uses default kit win seconds) */
    public void prepare(Location c) {
        prepareInternal(c, null);
    }

    /** /koth <seconds> -> prepares KOTH at sender block for custom win seconds */
    public void prepareForSeconds(Location c, int seconds) {
        int clamped = Math.max(5, seconds); // prevent silly 0/negative; feel free to change min
        prepareInternal(c, clamped);
    }

    private void prepareInternal(Location c, Integer winSecondsOverride) {
        if (c == null || c.getWorld() == null) return;
        if (active) return;

        this.center = c.clone();
        this.prepared = true;

        this.progressSecondsByTeam.clear();
        this.participants.clear();

        // reset return tracking each new prepare
        this.returnLocations.clear();
        this.pendingReturn.clear();

        // set per-event win seconds
        this.winSecondsOverride = winSecondsOverride;

        int goal = getWinSeconds();

        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "KOTH is ready!"
                + ChatColor.YELLOW + " Goal: " + ChatColor.AQUA + formatTime(goal)
                + ChatColor.YELLOW + " — Hill at "
                + ChatColor.AQUA + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ()
                + ChatColor.YELLOW + " — type " + ChatColor.GREEN + "/koth join"
                + ChatColor.YELLOW + " to join.");
    }

    public boolean join(Player p) {
        if (p == null) return false;
        if (!prepared) {
            p.sendMessage(ChatColor.RED + "KOTH is not prepared. Use /koth <x> <y> <z> or /koth <seconds> first.");
            return false;
        }
        if (active) {
            p.sendMessage(ChatColor.RED + "KOTH already started.");
            return false;
        }
        if (p.isOp()) {
            p.sendMessage(ChatColor.RED + "Operators cannot join KOTH.");
            return false;
        }

        boolean added = participants.add(p.getUniqueId());
        if (added) {
            // ✅ save their "return to" location exactly when they join
            returnLocations.putIfAbsent(p.getUniqueId(), p.getLocation().clone());

            p.sendMessage(ChatColor.GREEN + "You joined KOTH. (" + participants.size() + " players)");
        } else {
            p.sendMessage(ChatColor.YELLOW + "You are already joined.");
        }
        return added;
    }

    public boolean leave(Player p) {
        if (p == null) return false;
        boolean removed = participants.remove(p.getUniqueId());
        if (removed) {
            p.sendMessage(ChatColor.YELLOW + "You left KOTH.");
        } else {
            p.sendMessage(ChatColor.RED + "You are not in KOTH.");
        }
        return removed;
    }

    /** /koth start -> starts KOTH with only joined players */
    public boolean startPrepared(Player sender) {
        if (active) return false;
        if (!prepared || center == null || center.getWorld() == null) return false;

        if (participants.isEmpty()) {
            if (sender != null) sender.sendMessage(ChatColor.RED + "No one joined KOTH. Use /koth join first.");
            return false;
        }

        this.active = true;
        this.prepared = false;
        this.progressSecondsByTeam.clear();

        setupScoreboard(); // will set scoreboard only for participants

        World w = center.getWorld();
        int teleported = 0;

        for (UUID id : new HashSet<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            Location loc = randomSpawnLocation(w);
            if (loc == null) continue;

            savePlayerInventory(p);

            Location aimed = faceTowardCenter(loc);
            p.teleport(aimed);
            applyKothKit(p);
            teleported++;
        }

        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();

        int goal = getWinSeconds();

        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "KOTH started!"
                + ChatColor.YELLOW + " Goal: " + ChatColor.AQUA + formatTime(goal)
                + ChatColor.YELLOW + " Participants: " + ChatColor.AQUA + teleported
                + ChatColor.YELLOW + " — Hill at "
                + ChatColor.AQUA + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ());

        return true;
    }

    /** Ends the event and restores inventories for participants only */
    public void stop(boolean announce) {
        if (!active && !prepared) return;

        boolean wasActive = active;

        active = false;
        prepared = false;

        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // ✅ Teleport all joined players back to where they were when they joined
        for (UUID id : new HashSet<>(participants)) {
            Location back = returnLocations.get(id);
            if (back == null || back.getWorld() == null) continue;

            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.teleport(back);
            } else {
                // if they reconnect later, we can still return them
                pendingReturn.add(id);
            }
        }

        // ✅ Restore inventories for all who were saved (participants)
        for (UUID id : new HashSet<>(savedInventories.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                restorePlayerInventory(p);
            } else {
                pendingRestore.add(id);
            }
        }

        progressSecondsByTeam.clear();

        // Remove scoreboard from participants that still have it
        Scoreboard main = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        for (UUID id : new HashSet<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && p.getScoreboard() == board && main != null) {
                p.setScoreboard(main);
            }
        }

        board = null;
        obj = null;

        // Clear participants; keep pendingReturn/returnLocations only for offline returns
        participants.clear();

        // reset per-event win seconds after event finishes/cancels
        winSecondsOverride = null;

        if (announce) {
            Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + (wasActive ? "KOTH ended." : "KOTH cancelled."));
        }
    }

    private void tick() {
        if (!active || center == null || center.getWorld() == null) return;

        drawHillBorder();

        Map<String, Integer> teamsOnHill = new HashMap<>();

        for (Player p : center.getWorld().getPlayers()) {
            if (!isParticipant(p.getUniqueId())) continue;
            if (p.isDead()) continue;
            if (!isInsideHill(p.getLocation())) continue;

            Team t = teams.getTeamByPlayer(p);
            if (t == null) continue;

            teamsOnHill.merge(t.getName(), 1, Integer::sum);
        }

        String status;
        String controlling = null;

        if (teamsOnHill.isEmpty()) {
            status = "No one on hill";
        } else if (teamsOnHill.size() >= 2) {
            status = "CONTESTED";
        } else {
            controlling = teamsOnHill.keySet().iterator().next();
            status = "Capturing: " + controlling;

            int newVal = progressSecondsByTeam.getOrDefault(controlling, 0) + 1;
            progressSecondsByTeam.put(controlling, newVal);

            int goal = getWinSeconds();

            if (newVal >= goal) {
                Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "KOTH Winner: "
                        + ChatColor.AQUA + controlling
                        + ChatColor.GREEN + " (" + formatTime(newVal) + ")");
                stop(true);
                return;
            }
        }

        updateSidebar(controlling, status);
    }

    private boolean isInsideHill(Location loc) {
        if (loc == null || center == null) return false;
        if (loc.getWorld() != center.getWorld()) return false;

        // ✅ Vertical capture band:
        // within 5 blocks above center, and within 2 blocks below center
        int cy = center.getBlockY();
        int py = loc.getBlockY();
        if (py > cy + 5) return false;
        if (py < cy - 2) return false;

        // Horizontal radius
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double distSq = dx * dx + dz * dz;

        int r = kit.getHillRadius();
        return distSq <= (r * r);
    }

    private Location faceTowardCenter(Location from) {
        if (from == null || center == null) return from;

        Location to = center;
        if (to.getWorld() == null || from.getWorld() != to.getWorld()) return from;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = (to.getY() + 1.5) - from.getY();

        double xz = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, xz));

        Location out = from.clone();
        out.setYaw(yaw);
        out.setPitch(pitch);
        return out;
    }

    /** Public wrapper so listeners can face spawns without duplicating math */
    public Location faceTowardCenterPublic(Location from) {
        return faceTowardCenter(from);
    }

    // ✅ Particle border "pillar" (vertical cylinder) around hill
    private void drawHillBorder() {
        if (center == null || center.getWorld() == null) return;

        World w = center.getWorld();
        int r = kit.getHillRadius();
        double ringR = r + 0.35;

        int height = 3;
        int stepY = 1;
        int points = 50;

        int baseY = center.getBlockY();
        int minY = Math.max(baseY, w.getMinHeight() + 1);
        int maxY = Math.min(baseY + height, w.getMaxHeight() - 2);

        for (int yy = minY; yy <= maxY; yy += stepY) {
            double y = yy + 0.2;

            for (int i = 0; i < points; i++) {
                double t = (Math.PI * 2) * (i / (double) points);
                double x = center.getX() + Math.cos(t) * ringR;
                double z = center.getZ() + Math.sin(t) * ringR;

                w.spawnParticle(Particle.GLOW, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    public Location randomSpawnLocation(World w) {
        if (center == null || w == null) return null;
        if (center.getWorld() != w) return null;

        int inner = kit.getSpawnInnerRadius();
        int outer = kit.getSpawnOuterRadius();

        inner = Math.max(3, inner);
        outer = Math.max(inner + 1, outer);

        Location c = center;

        for (int attempt = 0; attempt < 60; attempt++) {

            double t = ThreadLocalRandom.current().nextDouble();
            double r = Math.sqrt(t * (outer * (double) outer - inner * (double) inner) + inner * (double) inner);
            double theta = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;

            int bx = (int) Math.floor(c.getX() + r * Math.cos(theta));
            int bz = (int) Math.floor(c.getZ() + r * Math.sin(theta));

            int surfaceY = w.getHighestBlockYAt(bx, bz);

            Location safe = findSafeY(new Location(w, bx + 0.5, surfaceY, bz + 0.5));
            if (safe != null) return safe;
        }

        return null;
    }

    private Location findSafeY(Location base) {
        World w = base.getWorld();
        if (w == null) return null;

        int bx = base.getBlockX();
        int bz = base.getBlockZ();

        int startY = base.getBlockY();
        int minY = w.getMinHeight() + 1;
        int maxY = w.getMaxHeight() - 2;

        for (int dy = 0; dy <= 20; dy++) {
            int up = startY + dy;
            int down = startY - dy;

            if (up <= maxY && isStandable(w, bx, up, bz)) {
                return new Location(w, bx + 0.5, up, bz + 0.5);
            }
            if (down >= minY && isStandable(w, bx, down, bz)) {
                return new Location(w, bx + 0.5, down, bz + 0.5);
            }
        }

        return null;
    }

    private boolean isStandable(World w, int x, int y, int z) {
        Material feet = w.getBlockAt(x, y, z).getType();
        Material head = w.getBlockAt(x, y + 1, z).getType();
        Material below = w.getBlockAt(x, y - 1, z).getType();

        if (!feet.isAir() || !head.isAir()) return false;
        if (!below.isSolid()) return false;

        // avoid common hazards
        if (below == Material.MAGMA_BLOCK) return false;
        if (below == Material.CAMPFIRE || below == Material.SOUL_CAMPFIRE) return false;
        if (below == Material.CACTUS) return false;
        if (below == Material.LAVA) return false;

        return true;
    }

    public void applyKothKit(Player p) {
        if (p == null) return;

        PlayerInventory inv = p.getInventory();

        if (kit.isClearInventory()) {
            inv.clear();
            inv.setArmorContents(null);
            inv.setItemInOffHand(null);
        }

        inv.setHelmet(kit.getHelmet());
        inv.setChestplate(kit.getChest());
        inv.setLeggings(kit.getLegs());
        inv.setBoots(kit.getBoots());

        for (ItemStack it : kit.getItems()) {
            if (it != null) inv.addItem(it);
        }

        for (var pe : kit.getEffects()) {
            if (pe != null) p.addPotionEffect(pe);
        }

        p.updateInventory();
    }

    private void setupScoreboard() {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;

        board = sm.getNewScoreboard();
        obj = board.registerNewObjective("koth", Criteria.DUMMY, ChatColor.GOLD + "" + ChatColor.BOLD + "KOTH");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // ✅ Only participants get the KOTH scoreboard
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.setScoreboard(board);
        }

        updateSidebar(null, "Waiting...");
    }

    private void updateSidebar(String controllingTeam, String status) {
        if (obj == null || board == null) return;

        for (String entry : board.getEntries()) board.resetScores(entry);

        int line = 15;

        obj.getScore(ChatColor.GRAY + "Status: " + formatStatus(status)).setScore(line--);
        obj.getScore(ChatColor.GRAY + "Goal: " + ChatColor.AQUA + formatTime(getWinSeconds())).setScore(line--);
        obj.getScore(ChatColor.DARK_GRAY.toString()).setScore(line--);

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(progressSecondsByTeam.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int shown = 0;
        int goal = getWinSeconds();

        for (Map.Entry<String, Integer> e : sorted) {
            if (shown >= 5) break;

            String team = e.getKey();
            int secs = e.getValue();

            ChatColor nameColor = team != null && team.equals(controllingTeam) ? ChatColor.AQUA : ChatColor.WHITE;

            String lineText =
                    nameColor + team
                            + ChatColor.GRAY + " "
                            + ChatColor.YELLOW + formatTime(secs)
                            + ChatColor.GRAY + " / "
                            + ChatColor.AQUA + formatTime(goal);

            obj.getScore(makeUnique(lineText, shown)).setScore(line--);
            shown++;
        }

        if (shown == 0) {
            obj.getScore(ChatColor.DARK_GRAY + "(no progress yet)").setScore(line--);
        }

        // ✅ Don't force scoreboard onto non-participants
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && p.getScoreboard() != board) {
                p.setScoreboard(board);
            }
        }
    }

    /** Used by /koth tp: participants only */
    public int teleportAllParticipantsToRandomSpawn() {
        if (!active || center == null || center.getWorld() == null) return 0;

        World w = center.getWorld();
        int teleported = 0;

        for (UUID id : new HashSet<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            Location loc = randomSpawnLocation(w);
            if (loc == null) continue;

            savePlayerInventory(p);

            p.teleport(faceTowardCenter(loc));
            applyKothKit(p);
            teleported++;
        }

        return teleported;
    }

    private String makeUnique(String s, int salt) {
        String suffix = switch (salt) {
            case 0 -> ChatColor.BLACK.toString();
            case 1 -> ChatColor.DARK_BLUE.toString();
            case 2 -> ChatColor.DARK_GREEN.toString();
            case 3 -> ChatColor.DARK_AQUA.toString();
            default -> ChatColor.DARK_GRAY.toString();
        };

        int maxLen = 40 - suffix.length();
        if (s.length() > maxLen) s = s.substring(0, maxLen);
        return s + suffix;
    }

    private String formatStatus(String status) {
        if ("CONTESTED".equals(status)) return ChatColor.RED + "" + ChatColor.BOLD + "CONTESTED";
        if ("No one on hill".equals(status)) return ChatColor.DARK_GRAY + status;
        return ChatColor.YELLOW + status;
    }

    public String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }
}
