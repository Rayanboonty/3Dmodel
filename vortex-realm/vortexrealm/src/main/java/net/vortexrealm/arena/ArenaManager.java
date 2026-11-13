package net.vortexrealm.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.ffa.FfaArena;
import net.vortexrealm.ffa.FfaManager;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.util.Cuboid;
import net.vortexrealm.util.Pos;
import net.vortexrealm.util.Tasks;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class ArenaManager {
    public static final int SLOT_SIZE = 512;
    public static final int SLOT_Z_START = 4096;
    /** New arenas (from Vortex Realm round 3 on) are placed on a second grid far away from the old one. */
    public static final int SLOT2_Z_START = 65536;
    /** Seconds every copy of a map must be busy before Auto Copies adds another. */
    private static final long AUTO_COPY_BUSY_MS = 20000L;
    private final VortexRealm plugin;
    private final File file;
    private final File snapDir;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Set<String> autoCopying = new HashSet<>();
    private int nextSlot;
    private int nextSlot2;
    private boolean updating;
    private int updateRetries;

    public ArenaManager(VortexRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        this.snapDir = new File(plugin.getDataFolder(), "snapshots");
    }

    /** Done once every snapshot read at start-up is in place (start-up checks wait for it). */
    private volatile CompletableFuture<Void> snapshotsReady = CompletableFuture.completedFuture(null);

    public CompletableFuture<Void> snapshotsReady() {
        return this.snapshotsReady;
    }

    private CompletableFuture<Void> loadSnapshot(Arena a, File snap) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return RegionSnapshot.load(snap);
            } catch (IOException | RuntimeException var3) {
                this.plugin.getLogger().warning("Snapshot of arena " + a.id + " is unreadable: " + var3.getMessage());
                return null;
            }
        }).thenAcceptAsync(r -> {
            if (r != null && a.snapshot == null) {
                a.snapshot = r;
            }
        }, Tasks.MAIN).exceptionally(e -> null);
    }

    public void load() {
        List<CompletableFuture<Void>> snaps = new ArrayList<>();
        this.arenas.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.nextSlot = y.getInt("next-slot", 0);
        this.nextSlot2 = y.getInt("next-slot-v2", 0);
        ConfigurationSection root = y.getConfigurationSection("arenas");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s != null) {
                    try {
                        Arena a = new Arena(id, s.getString("name", id));
                        a.theme = ArenaTheme.parse(s.getString("theme"));
                        Material icon = Material.matchMaterial(s.getString("icon", a.theme.icon.name()));
                        a.iconBlock = icon == null ? a.theme.icon : icon;
                        a.worldName = s.getString("world");
                        a.bounds = Cuboid.parse(s.getString("bounds"));
                        a.spawn1 = Pos.parse(s.getString("spawn1"));
                        a.spawn2 = Pos.parse(s.getString("spawn2"));
                        a.center = Pos.parse(s.getString("center"));
                        a.kits.addAll(s.getStringList("kits"));
                        a.enabled = s.getBoolean("enabled", true);
                        a.generating = s.getBoolean("generating", false);
                        a.startMode = s.getString("start-mode", "DEFAULT");
                        a.elevatorDepth = s.getInt("elevator-depth", -1);
                        a.buildHeight = s.getInt("build-height", -1);
                        a.resetMode = s.getString("reset-mode", "DEFAULT");

                        for (String g : s.getStringList("gates")) {
                            Cuboid c = Cuboid.parse(g);
                            if (c != null) {
                                a.gates.add(c);
                            }
                        }

                        Material gm = Material.matchMaterial(s.getString("gate-material", "OAK_FENCE"));
                        a.gateMaterial = gm != null && gm.isBlock() ? gm : Material.OAK_FENCE;
                        a.generated = s.getBoolean("generated", false);
                        a.seed = s.getLong("seed", 0L);
                        a.size = s.getInt("size", 0);
                        a.genSignature = s.getString("gen-signature");
                        a.handEdited = s.getBoolean("hand-edited", false);
                        a.site = s.getString("vanilla-site");
                        a.deep = s.getBoolean("deep", false);
                        a.pool = Arena.normPool(s.getString("pool", a.generated ? (a.deep ? "CRYSTAL" : Arena.poolOf(a.theme)) : "ANY"));
                        a.instances.add(new ArenaInstance(a, 0, a.worldName == null ? "" : a.worldName, 0, 0));
                        int idx = 1;

                        for (String c : s.getStringList("copies")) {
                            String[] p = c.split(";");
                            if (p.length == 3) {
                                a.instances.add(new ArenaInstance(a, idx++, p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                            }
                        }

                        File snap = this.snapshotFile(a);
                        if (snap.exists()) {
                            snaps.add(this.loadSnapshot(a, snap));
                        }

                        this.arenas.put(id, a);
                    } catch (RuntimeException var14) {
                        this.plugin.getLogger().log(Level.SEVERE, "Arena '" + id + "' in arenas.yml is invalid", (Throwable)var14);
                    }
                }
            }

            this.plugin
                .getLogger()
                .info(
                    "Loaded " + this.arenas.size() + " duel arenas (" + this.arenas.values().stream().mapToInt(a -> a.instances.size()).sum() + " instances)."
                );
        }
        // snapshots are read off the main thread, all at once (they were most of the start-up time)
        this.snapshotsReady = CompletableFuture.allOf(snaps.toArray(new CompletableFuture[0]));
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Duel arenas. Use /pvpadmin -> Arenas to edit."));
        y.set("next-slot", this.nextSlot);
        y.set("next-slot-v2", this.nextSlot2);

        for (Arena a : this.arenas.values()) {
            String b = "arenas." + a.id + ".";
            y.set(b + "name", a.displayName);
            y.set(b + "theme", a.theme.name());
            y.set(b + "icon", a.iconBlock.name());
            y.set(b + "world", a.worldName);
            y.set(b + "bounds", a.bounds == null ? null : a.bounds.serialize());
            y.set(b + "spawn1", a.spawn1 == null ? null : a.spawn1.serialize());
            y.set(b + "spawn2", a.spawn2 == null ? null : a.spawn2.serialize());
            y.set(b + "center", a.center == null ? null : a.center.serialize());
            y.set(b + "kits", new ArrayList<>(a.kits));
            y.set(b + "enabled", a.enabled);
            y.set(b + "generating", a.generating ? true : null);
            y.set(b + "start-mode", a.startMode);
            y.set(b + "elevator-depth", a.elevatorDepth);
            y.set(b + "build-height", a.buildHeight);
            y.set(b + "reset-mode", a.resetMode);
            List<String> gates = new ArrayList<>();

            for (Cuboid c : a.gates) {
                gates.add(c.serialize());
            }

            y.set(b + "gates", gates);
            y.set(b + "gate-material", a.gateMaterial.name());
            y.set(b + "generated", a.generated);
            y.set(b + "seed", a.seed);
            y.set(b + "size", a.size);
            y.set(b + "gen-signature", a.genSignature);
            y.set(b + "hand-edited", a.handEdited);
            y.set(b + "pool", a.pool);
            y.set(b + "vanilla-site", a.site);
            y.set(b + "deep", a.deep);
            List<String> copies = new ArrayList<>();

            for (ArenaInstance i : a.instances) {
                if (i.index > 0) {
                    copies.add(i.worldName + ";" + i.dx + ";" + i.dz);
                }
            }

            y.set(b + "copies", copies);
        }

        try {
            y.save(this.file);
        } catch (IOException var9) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml", (Throwable)var9);
        }
    }

    File snapshotFile(Arena a) {
        return new File(this.snapDir, a.id + ".snap");
    }

    public Arena get(String id) {
        return id == null ? null : this.arenas.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Arena> all() {
        return Collections.unmodifiableCollection(this.arenas.values());
    }

    public boolean exists(String id) {
        return this.arenas.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public List<Arena> available(Kit kit) {
        List<Arena> out = new ArrayList<>();

        for (Arena a : this.forKit(kit)) {
            if (a.freeInstances() > 0L) {
                out.add(a);
            }
        }

        return out;
    }

    public int freeCount(Kit kit) {
        int n = 0;

        for (Arena a : this.forKit(kit)) {
            n += (int)a.freeInstances();
        }

        return n;
    }

    public boolean hasFree(Kit kit) {
        for (Arena a : this.forKit(kit)) {
            if (a.freeInstances() > 0L) {
                return true;
            }
        }

        return false;
    }

    public List<Arena> forKit(Kit kit) {
        List<Arena> out = new ArrayList<>();

        for (Arena a : this.arenas.values()) {
            if (a.ready() && a.allows(kit)) {
                out.add(a);
            }
        }

        if (out.isEmpty()) {
            boolean crystalKit = kit.playsOn("CRYSTAL");

            for (Arena ax : this.arenas.values()) {
                if (ax.ready() && ax.kits.isEmpty() && (crystalKit || !"CRYSTAL".equals(ax.pool))) {
                    out.add(ax);
                }
            }
        }

        return out;
    }

    public ArenaInstance instanceAt(Location l) {
        return l != null && l.getWorld() != null ? this.instanceAt(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ()) : null;
    }

    public ArenaInstance instanceAt(World w, int x, int y, int z) {
        for (Arena a : this.arenas.values()) {
            if (a.bounds != null) {
                for (ArenaInstance i : a.instances) {
                    if (i.state == ArenaInstance.State.IN_USE && i.contains(w, x, y, z)) {
                        return i;
                    }
                }
            }
        }

        return null;
    }

    public ArenaInstance acquire(Arena a, Object owner) {
        if (a != null && a.ready()) {
            List<ArenaInstance> free = new ArrayList<>();

            for (ArenaInstance i : a.instances) {
                if (i.state == ArenaInstance.State.FREE && i.world() != null) {
                    free.add(i);
                }
            }

            if (free.isEmpty()) {
                if (a.busySince == 0L) {
                    a.busySince = System.currentTimeMillis();
                }

                return null;
            } else {
                ArenaInstance inst = free.get(ThreadLocalRandom.current().nextInt(free.size()));
                inst.state = ArenaInstance.State.IN_USE;
                inst.owner = owner;
                inst.tracker.drain();
                this.ticket(inst, true);
                if (free.size() == 1 && a.busySince == 0L) {
                    a.busySince = System.currentTimeMillis();
                }

                return inst;
            }
        } else {
            return null;
        }
    }

    public ArenaInstance acquireAny(Kit kit, Object owner) {
        List<Arena> list = this.available(kit);
        return list.isEmpty() ? null : this.acquire(list.get(ThreadLocalRandom.current().nextInt(list.size())), owner);
    }

    public void release(ArenaInstance inst) {
        if (inst != null) {
            inst.owner = null;
            inst.state = ArenaInstance.State.RESETTING;

            CompletableFuture<Void> reset;
            try {
                reset = this.resetFull(inst);
            } catch (RuntimeException var4) {
                reset = CompletableFuture.failedFuture(var4);
            }

            // a slow reset (big vanilla map on a busy server) keeps the copy out of rotation until it is really done,
            // so no match starts on a half-reset map; only a reset stuck for 10 minutes gives it back anyway
            reset.copy().orTimeout(90L, TimeUnit.SECONDS).whenComplete((v, err) -> {
                if (err != null && (err instanceof TimeoutException || err.getCause() instanceof TimeoutException)) {
                    Tasks.ensureSync(() -> this.plugin.getLogger().info("Full reset of " + inst.label() + " is taking long (busy server); it stays out of rotation until it is done."));
                }
            });
            reset.copy().orTimeout(600L, TimeUnit.SECONDS).whenComplete((v, err) -> Tasks.ensureSync(() -> {
                if (err != null) {
                    this.plugin.getLogger().log(Level.WARNING, "Full reset of " + inst.label() + " did not finish; the arena goes back in rotation", err);
                }

                if (inst.state == ArenaInstance.State.RESETTING) {
                    inst.state = ArenaInstance.State.FREE;
                    inst.arena.busySince = 0L;
                }

                this.ticket(inst, false);
            }));
        }
    }

    /**
     * Auto Copies: when every copy of a map has been busy for a while, build one more (from the snapshot, into a fresh
     * slot) up to arena.max-copies. Runs every few seconds.
     */
    public void autoScale() {
        if (Cfg.bool(Opt.AUTO_COPIES) && this.plugin.jobs().pending() <= 2) {
            long now = System.currentTimeMillis();
            int max = Cfg.i(Opt.MAX_COPIES);

            for (Arena a : this.arenas.values()) {
                if (a.ready()
                    && a.snapshot != null
                    && !a.editing
                    && a.busySince > 0L
                    && now - a.busySince >= AUTO_COPY_BUSY_MS
                    && a.freeInstances() == 0L
                    && a.instances.size() - 1 < max
                    && this.autoCopying.add(a.id)) {
                    this.plugin.getLogger().info("Auto copies: every copy of " + a.displayName + " is busy, building another one.");
                    this.addCopies(a, 1).whenComplete((v, err) -> Tasks.ensureSync(() -> {
                        this.autoCopying.remove(a.id);
                        a.busySince = 0L;
                        if (err != null) {
                            this.plugin.getLogger().log(Level.WARNING, "Auto copy of " + a.id + " failed", err);
                        }
                    }));
                    return;
                }
            }
        }
    }

    private Opt.ResetMode resetMode(Arena a) {
        if (!"DEFAULT".equalsIgnoreCase(a.resetMode)) {
            try {
                return Opt.ResetMode.valueOf(a.resetMode.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var3) {
            }
        }

        return Cfg.e(Opt.RESET_MODE, Opt.ResetMode.class);
    }

    public void resetFast(ArenaInstance inst) {
        World w = inst.world();
        if (w != null) {
            this.clearEntities(inst);
            List<ChangeTracker.Change> changes = inst.tracker.drain();
            ChangeTracker.restore(w, changes);
            if (this.resetMode(inst.arena) == Opt.ResetMode.SNAPSHOT && inst.arena.snapshot != null) {
                inst.arena
                    .snapshot
                    .diff(w, inst.dx, inst.dz)
                    .thenAccept(fixes -> Tasks.ensureSync(() -> this.plugin.jobs().submitUrgent(RegionSnapshot.fixJob(w, (List<RegionSnapshot.Fix>)fixes))));
            }
        }
    }

    public CompletableFuture<Void> resetFull(ArenaInstance inst) {
        World w = inst.world();
        if (w == null) {
            return CompletableFuture.completedFuture(null);
        } else {
            this.clearEntities(inst);
            Opt.ResetMode mode = this.resetMode(inst.arena);
            List<ChangeTracker.Change> changes = inst.tracker.drain();
            if (mode != Opt.ResetMode.SNAPSHOT || inst.arena.snapshot == null) {
                ChangeTracker.restore(w, changes);
            }

            RegionSnapshot snap = inst.arena.snapshot;
            if (mode != Opt.ResetMode.TRACKED && snap != null) {
                CompletableFuture<Void> done = new CompletableFuture<>();
                snap.diff(w, inst.dx, inst.dz).whenComplete((fixes, err) -> Tasks.ensureSync(() -> {
                    if (err != null) {
                        done.completeExceptionally(err);
                    } else if (fixes.isEmpty()) {
                        done.complete(null);
                    } else {
                        // urgent: resets must never wait behind a long arena build
                        this.plugin.jobs().submitUrgent(RegionSnapshot.fixJob(w, fixes)).whenComplete((v, e2) -> {
                            this.clearEntities(inst);
                            if (e2 != null) {
                                done.completeExceptionally(e2);
                            } else {
                                done.complete(null);
                            }
                        });
                    }
                }));
                return done;
            } else {
                return CompletableFuture.completedFuture(null);
            }
        }
    }

    public void clearEntities(ArenaInstance inst) {
        World w = inst.world();
        if (w != null && inst.arena.bounds != null) {
            for (Entity e : w.getNearbyEntities(inst.bounds().toBox())) {
                if (!(e instanceof Player)) {
                    e.remove();
                }
            }
        }
    }

    private void ticket(ArenaInstance inst, boolean add) {
        World w = inst.world();
        if (w != null && inst.arena.bounds != null) {
            Cuboid c = inst.bounds();

            for (int cx = c.minX() >> 4; cx <= c.maxX() >> 4; cx++) {
                for (int cz = c.minZ() >> 4; cz <= c.maxZ() >> 4; cz++) {
                    if (add) {
                        w.addPluginChunkTicket(cx, cz, this.plugin);
                    } else {
                        w.removePluginChunkTicket(cx, cz, this.plugin);
                    }
                }
            }
        }
    }

    public static CompletableFuture<Void> preload(World w, Cuboid c) {
        List<CompletableFuture<Chunk>> fs = new ArrayList<>();

        for (int cx = c.minX() >> 4; cx <= c.maxX() >> 4; cx++) {
            for (int cz = c.minZ() >> 4; cz <= c.maxZ() >> 4; cz++) {
                fs.add(w.getChunkAtAsync(cx, cz, true));
            }
        }

        return CompletableFuture.allOf(fs.toArray(new CompletableFuture[0]));
    }

    /** Old-grid slot index (kept for existing arenas and for wiping their region files). */
    public synchronized int allocateSlot() {
        World w = this.plugin.worlds().practiceWorld();
        int s = this.nextSlot++;

        for (int tries = 0; tries < 4096 && slotDirty(w, slotX(s), slotZ(s)); tries++) {
            s = this.nextSlot++;
        }

        this.save();
        return s;
    }

    /** A fresh 512x512 slot on the new grid: returns {x, z} of its corner. Each slot is exactly one region file. */
    public synchronized int[] allocateOrigin() {
        World w = this.plugin.worlds().practiceWorld();
        int s = this.nextSlot2++;

        for (int tries = 0; tries < 4096 && slotDirty(w, slot2X(s), slot2Z(s)); tries++) {
            s = this.nextSlot2++;
        }

        this.save();
        return new int[]{slot2X(s), slot2Z(s)};
    }

    private static boolean slotDirty(World w, int x, int z) {
        if (w == null) {
            return false;
        } else {
            File region = new File(w.getWorldFolder(), "region" + File.separator + "r." + (x >> 9) + "." + (z >> 9) + ".mca");
            if (region.isFile() && region.length() > 8192L) {
                return true;
            } else {
                for (int cx = x >> 4; cx < x + 512 >> 4; cx += 2) {
                    for (int cz = z >> 4; cz < z + 512 >> 4; cz += 2) {
                        if (w.isChunkLoaded(cx, cz)) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }
    }

    public static BlockJobs.Job clearJob(final World w, final Cuboid c) {
        return new BlockJobs.Job() {
            int x = c.minX();
            int z = c.minZ();

            @Override
            public int step(int budget) {
                int used = 0;

                while (this.x <= c.maxX() && used < budget) {
                    int top = Math.min(c.maxY(), w.getHighestBlockYAt(this.x, this.z, HeightMap.WORLD_SURFACE));

                    for (int y = top; y >= c.minY(); y--) {
                        Block b = w.getBlockAt(this.x, y, this.z);
                        if (!b.getType().isAir()) {
                            b.setType(Material.AIR, false);
                            used += 3;
                        } else {
                            used++;
                        }
                    }

                    used++;
                    if (++this.z > c.maxZ()) {
                        this.z = c.minZ();
                        this.x++;
                    }

                    if ((used & 63) == 0 && BlockJobs.overDeadline()) {
                        break;
                    }
                }

                return used;
            }

            @Override
            public boolean done() {
                return this.x > c.maxX();
            }

            @Override
            public String name() {
                return "clear";
            }
        };
    }

    public static Cuboid wipeArea(World w, Cuboid b) {
        int margin = 32;
        int minX = b.minX() - margin;
        int maxX = b.maxX() + margin;
        int minZ = b.minZ() - margin;
        int maxZ = b.maxZ() + margin;
        int slotMinX = Math.floorDiv(b.minX(), 512) * 512;
        int slotMinZ = Math.floorDiv(b.minZ(), 512) * 512;
        if (b.maxX() < slotMinX + 512 && b.maxZ() < slotMinZ + 512) {
            minX = Math.max(minX, slotMinX);
            minZ = Math.max(minZ, slotMinZ);
            maxX = Math.min(maxX, slotMinX + 512 - 1);
            maxZ = Math.min(maxZ, slotMinZ + 512 - 1);
        } else {
            minX = b.minX();
            minZ = b.minZ();
            maxX = b.maxX();
            maxZ = b.maxZ();
        }

        return new Cuboid(minX, Math.max(w.getMinHeight(), b.minY() - 8), minZ, maxX, w.getMaxHeight() - 1, maxZ);
    }

    public static int slotX(int slot) {
        return slot % 32 * 512;
    }

    public static int slotZ(int slot) {
        return 4096 + slot / 32 * 512;
    }

    public static int slot2X(int slot) {
        return slot % 32 * 512;
    }

    public static int slot2Z(int slot) {
        return SLOT2_Z_START + slot / 32 * 512;
    }

    /** The size a generated arena of this theme should have now (arenas smaller than this grow on the next update). */
    public static int targetSize(ArenaTheme theme, boolean ffa) {
        if (theme == null || theme == ArenaTheme.CUSTOM) {
            return 0;
        } else if (theme.isVanilla()) {
            return Cfg.i(Opt.VANILLA_SIZE);
        } else if (theme == ArenaTheme.GRAND_COLOSSEUM) {
            return Cfg.i(Opt.MACE_ARENA_SIZE);
        } else if (theme == ArenaTheme.CHAMPIONS_COLOSSEUM) {
            return 224;
        } else if (theme == ArenaTheme.MOLE_MINES) {
            return net.vortexrealm.build.MoleMinesDesign.radius(ffa) * 2 + 1;
        } else if (theme.duel != null) {
            return net.vortexrealm.build.DuelArenaDesign.playable(theme.duel, Cfg.i(Opt.DUEL_COMPACT_SIZE), Cfg.i(Opt.DUEL_BUILD_SIZE));
        } else {
            return Math.min(448, (int)Math.round((ffa ? Cfg.i(Opt.FFA_ARENA_SIZE) : Cfg.i(Opt.DUEL_ARENA_SIZE)) * theme.sizeScale));
        }
    }

    public Arena create(String id, String name, World world) {
        Arena a = new Arena(id.toLowerCase(Locale.ROOT), name);
        a.worldName = world.getName();
        a.instances.add(new ArenaInstance(a, 0, a.worldName, 0, 0));
        this.arenas.put(a.id, a);
        this.save();
        return a;
    }

    public CompletableFuture<Arena> generate(String id, String name, ArenaTheme theme, int size, int copies) {
        return this.generate(id, name, theme, size, copies, false);
    }

    /** {@code deep} = the crystal variant of a vanilla map (no bedrock, crystal depth, CRYSTAL pool). */
    public CompletableFuture<Arena> generate(String id, String name, ArenaTheme theme, int size, int copies, boolean deep) {
        if (theme.isVanilla()) {
            return this.generateVanilla(id, name, theme, size, copies, deep);
        } else {
            size = Math.min(448, (int)Math.round(size * theme.sizeScale));
            World w = this.plugin.worlds().practiceWorld();
            int[] origin = this.allocateOrigin();
            int ox = origin[0];
            int oz = origin[1];
            long seed = ThreadLocalRandom.current().nextLong();
            ArenaGenerator.Plan plan = ArenaGenerator.plan(theme, size, seed, ox, oz, false, Cfg.i(Opt.BUILD_HEIGHT));
            Arena a = new Arena(id.toLowerCase(Locale.ROOT), name);
            a.theme = theme;
            a.iconBlock = theme.icon;
            a.worldName = w.getName();
            a.bounds = plan.bounds();
            a.spawn1 = plan.spawn1();
            a.spawn2 = plan.spawn2();
            a.center = plan.center();
            a.generated = true;
            a.seed = seed;
            a.size = size;
            a.genSignature = ArenaGenerator.signature(theme, false);
            a.pool = Arena.poolOf(theme);
            a.enabled = false;
            a.generating = true;
            ArenaInstance template = new ArenaInstance(a, 0, a.worldName, 0, 0);
            template.state = ArenaInstance.State.BUILDING;
            a.instances.add(template);
            this.arenas.put(a.id, a);
            this.save();
            this.busyEnter(a.id);
            CompletableFuture<Arena> result = new CompletableFuture<>();
            preload(w, plan.bounds())
                .thenComposeAsync(v -> this.plugin.jobs().submit(ArenaGenerator.buildJob(w, plan)), Tasks.MAIN)
                .thenComposeAsync(v -> this.captureSnapshot(a), Tasks.MAIN)
                .thenComposeAsync(v -> {
                    // the map itself is there: it is played on right away, copies join as each one is ready
                    template.state = ArenaInstance.State.FREE;
                    a.enabled = true;
                    a.generating = false;
                    this.save();
                    return this.addCopies(a, copies);
                }, Tasks.MAIN)
                .whenComplete((v, err) -> Tasks.ensureSync(() -> {
                    this.busyExit(a.id);
                    if (err != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Generating arena " + id + " failed", err);
                        result.completeExceptionally(err);
                    } else {
                        a.enabled = true;
                        a.generating = false;
                        this.save();
                        result.complete(a);
                    }
                }));
            return result;
        }
    }

    private CompletableFuture<Arena> generateVanilla(String id, String name, ArenaTheme theme, int size, int copies, boolean deep) {
        World w = this.plugin.worlds().practiceWorld();
        int[] origin = this.allocateOrigin();
        Arena a = new Arena(id.toLowerCase(Locale.ROOT), name);
        a.theme = theme;
        a.iconBlock = theme.icon;
        a.worldName = w.getName();
        a.generated = true;
        a.size = size;
        a.deep = deep;
        a.pool = deep ? "CRYSTAL" : Arena.poolOf(theme);
        a.enabled = false;
        a.generating = true;
        ArenaInstance template = new ArenaInstance(a, 0, a.worldName, 0, 0);
        template.state = ArenaInstance.State.BUILDING;
        a.instances.add(template);
        this.arenas.put(a.id, a);
        this.save();
        this.busyEnter(a.id);
        CompletableFuture<Arena> result = new CompletableFuture<>();
        this.plugin
            .vanilla()
            .cut(theme, size, origin[0], origin[1], false, deep, null)
            .thenComposeAsync(cut -> {
                this.applyCut(a, cut);
                return preload(w, a.bounds).thenComposeAsync(v -> this.plugin.jobs().submit(cut.snapshot().pasteJob(w, 0, 0, true)), Tasks.MAIN);
            }, Tasks.MAIN)
            .thenComposeAsync(v -> {
                // the map itself is there: it is played on right away, copies join as each one is ready
                template.state = ArenaInstance.State.FREE;
                a.enabled = true;
                a.generating = false;
                this.save();
                return this.addCopies(a, copies);
            }, Tasks.MAIN)
            .whenComplete((v, err) -> Tasks.ensureSync(() -> {
                this.busyExit(a.id);
                if (err != null) {
                    this.plugin.getLogger().log(Level.SEVERE, "Generating vanilla arena " + id + " failed", err);
                    result.completeExceptionally(err);
                } else {
                    a.enabled = true;
                    a.generating = false;
                    this.save();
                    result.complete(a);
                }
            }));
        return result;
    }

    private int depthOf(Arena a) {
        return a.deep ? Cfg.i(Opt.CRYSTAL_DEPTH) : Cfg.i(Opt.DUEL_DEPTH);
    }

    /** Stores a vanilla cut on the arena (position data, signature, snapshot) and writes the snapshot file off-thread. */
    private void applyCut(Arena a, VanillaTerrain.Cut cut) {
        a.bounds = cut.bounds();
        a.spawn1 = cut.spawn1();
        a.spawn2 = cut.spawn2();
        a.center = cut.center();
        a.site = cut.site().serialize();
        a.seed = cut.site().seed();
        a.size = cut.site().size();
        a.genSignature = VanillaTerrain.signature(this.depthOf(a), a.deep);
        a.snapshot = cut.snapshot();
        RegionSnapshot snap = cut.snapshot();
        File f = this.snapshotFile(a);
        Tasks.async(() -> {
            try {
                snap.save(f);
            } catch (IOException var4) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save snapshot of " + a.id, (Throwable)var4);
            }
        });
        this.save();
    }

    public CompletableFuture<Void> rebuild(Arena a) {
        return this.rebuild(a, false);
    }

    public CompletableFuture<Void> rebuild(Arena a, boolean sameLayout) {
        if (a.generated && a.theme != null && a.theme != ArenaTheme.CUSTOM && a.bounds != null && a.world() != null) {
            for (ArenaInstance i : a.instances) {
                if (i.state != ArenaInstance.State.FREE) {
                    return CompletableFuture.failedFuture(new IllegalStateException(a.id + " is in use"));
                }
            }

            this.busyEnter(a.id);
            CompletableFuture<Void> job;
            try {
                job = a.theme.isVanilla() ? this.rebuildVanilla(a, sameLayout) : this.rebuildGenerated(a, sameLayout);
            } catch (RuntimeException var5) {
                job = CompletableFuture.failedFuture(var5);
            }

            return job.whenComplete((v, err) -> Tasks.ensureSync(() -> this.busyExit(a.id)));
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException(a.id + " is not a generated arena"));
        }
    }

    private CompletableFuture<Void> rebuildGenerated(Arena a, boolean sameLayout) {
        World w = a.world();

        for (ArenaInstance ix : a.instances) {
            ix.state = ArenaInstance.State.BUILDING;
            ix.tracker.drain();
        }

        Cuboid old = a.bounds;
        int target = targetSize(a.theme, false);
        if (a.size > 0 && a.size < target || (a.theme.duel != null || a.theme == ArenaTheme.MOLE_MINES) && target > 0) {
            a.size = target;
        }

        long seed = sameLayout && a.seed != 0L ? a.seed : ThreadLocalRandom.current().nextLong();
        ArenaGenerator.Plan plan = ArenaGenerator.plan(a.theme, a.size > 0 ? a.size : old.sizeX(), seed, old.minX(), old.minZ(), false, Cfg.i(Opt.BUILD_HEIGHT));
        Cuboid nb = plan.bounds();
        Cuboid union = union(old, nb);
        a.bounds = nb;
        a.spawn1 = plan.spawn1();
        a.spawn2 = plan.spawn2();
        a.center = plan.center();
        a.seed = seed;
        a.site = null;
        a.snapshot = null;
        this.save();
        Cuboid wipe = wipeArea(w, union);
        CompletableFuture<Void> chain = preload(w, wipe)
            .thenComposeAsync(v -> this.plugin.jobs().submit(clearJob(w, wipe)), Tasks.MAIN)
            .thenComposeAsync(v -> this.plugin.jobs().submit(ArenaGenerator.buildJob(w, plan)), Tasks.MAIN)
            .thenComposeAsync(v -> this.captureSnapshot(a), Tasks.MAIN);
        chain = this.repasteCopies(a, w, union, chain);
        return this.finishRebuild(a, chain, ArenaGenerator.signature(a.theme, false));
    }

    private CompletableFuture<Void> rebuildVanilla(Arena a, boolean sameLayout) {
        World w = a.world();
        if (a.site == null) {
            // first upgrade of an old generated map to vanilla terrain: start with fewer copies, Auto Copies adds more
            this.trimCopies(a, Cfg.i(Opt.VANILLA_COPIES));
        }

        for (ArenaInstance ix : a.instances) {
            ix.state = ArenaInstance.State.BUILDING;
            ix.tracker.drain();
        }

        Cuboid old = a.bounds;
        int size = Math.max(a.site == null ? 0 : a.size, targetSize(a.theme, false));
        // the same site again, unless it was found by an older site search (whose rules have changed since)
        VanillaTerrain.Site hint = sameLayout && VanillaTerrain.sameVersion(a.genSignature) ? VanillaTerrain.Site.parse(a.site) : null;
        CompletableFuture<Void> chain = this.plugin.vanilla().cut(a.theme, size, old.minX(), old.minZ(), false, a.deep, hint).thenComposeAsync(cut -> {
            Cuboid union = union(old, cut.bounds());
            this.applyCut(a, cut);
            Cuboid wipe = wipeArea(w, union);
            CompletableFuture<Void> c = preload(w, wipe)
                .thenComposeAsync(v -> this.plugin.jobs().submit(clearJob(w, wipe)), Tasks.MAIN)
                .thenComposeAsync(v -> this.plugin.jobs().submit(cut.snapshot().pasteJob(w, 0, 0, true)), Tasks.MAIN);
            return this.repasteCopies(a, w, union, c);
        }, Tasks.MAIN);
        return this.finishRebuild(a, chain, null);
    }

    private static Cuboid union(Cuboid old, Cuboid nb) {
        return new Cuboid(
            Math.min(old.minX(), nb.minX()),
            Math.min(old.minY(), nb.minY()),
            Math.min(old.minZ(), nb.minZ()),
            Math.max(old.maxX(), nb.maxX()),
            Math.max(old.maxY(), nb.maxY()),
            Math.max(old.maxZ(), nb.maxZ())
        );
    }

    private CompletableFuture<Void> repasteCopies(Arena a, World w, Cuboid union, CompletableFuture<Void> chain) {
        for (ArenaInstance ix : a.instances) {
            if (ix.dx != 0 || ix.dz != 0) {
                Cuboid copyWipe = wipeArea(w, union.shift(ix.dx, 0, ix.dz));
                chain = chain.<Void>thenComposeAsync(v -> preload(w, copyWipe), Tasks.MAIN)
                    .thenComposeAsync(v -> this.plugin.jobs().submit(clearJob(w, copyWipe)), Tasks.MAIN)
                    .thenComposeAsync(v -> this.plugin.jobs().submit(a.snapshot.pasteJob(w, ix.dx, ix.dz, true)), Tasks.MAIN);
            }
        }

        return chain;
    }

    private CompletableFuture<Void> finishRebuild(Arena a, CompletableFuture<Void> chain, String signature) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        chain.whenComplete((v, err) -> Tasks.ensureSync(() -> {
            for (ArenaInstance ixx : a.instances) {
                ixx.state = ArenaInstance.State.FREE;
                ixx.tracker.drain();
            }

            if (err == null) {
                if (signature != null) {
                    a.genSignature = signature;
                }

                a.handEdited = false;
            }

            this.save();
            if (err != null) {
                this.plugin.getLogger().log(Level.SEVERE, "Rebuilding arena " + a.id + " failed", err);
                result.completeExceptionally(err);
            } else {
                result.complete(null);
            }
        }));
        return result;
    }

    public CompletableFuture<Integer> rebuildAll() {
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);

        for (Arena a : new ArrayList<>(this.arenas.values())) {
            if (a.generated && a.theme != null && a.theme != ArenaTheme.CUSTOM) {
                chain = chain.thenComposeAsync(n -> this.rebuild(a).handle((v, err) -> err == null ? n + 1 : n), Tasks.MAIN);
            }
        }

        for (FfaArena f : new ArrayList<>(this.plugin.ffa().all())) {
            if (f.generated && f.theme != null && f.theme != ArenaTheme.CUSTOM) {
                chain = chain.thenComposeAsync(n -> this.plugin.ffa().rebuild(f, false).handle((v, err) -> err == null ? n + 1 : n), Tasks.MAIN);
            }
        }

        return chain;
    }

    // ------------------------------------------------------------------ is the map really there?

    /** Arenas with a generate, copy or rebuild job running in this run (a counter: jobs nest). */
    private final Map<String, Integer> busy = new java.util.HashMap<>();

    private void busyEnter(String id) {
        this.busy.merge(id, 1, Integer::sum);
    }

    private void busyExit(String id) {
        this.busy.computeIfPresent(id, (k, n) -> n <= 1 ? null : n - 1);
    }

    /** True while a generate, copy or rebuild job of this arena runs. */
    public boolean isBusy(Arena a) {
        return this.busy.containsKey(a.id);
    }

    /** A solid block 1 to 4 below the spawn (shifted by dx/dz): the floor is there, it isn't void. */
    public static boolean floorAt(World w, Pos p, int dx, int dz) {
        if (w == null || p == null) {
            return false;
        }

        int x = p.bx() + dx;
        int z = p.bz() + dz;
        w.getChunkAt(x >> 4, z >> 4);

        for (int y = p.by() - 1; y >= p.by() - 4 && y >= w.getMinHeight(); y--) {
            if (!w.getBlockAt(x, y, z).getType().isAir()) {
                return true;
            }
        }

        return false;
    }

    /** The map of this copy (or of the template, dx = dz = 0) has its floor under both spawns. */
    public static boolean hasBlocks(Arena a, int dx, int dz) {
        World w = a.world();
        return floorAt(w, a.spawn1, dx, dz) && floorAt(w, a.spawn2, dx, dz);
    }

    /** An FFA arena has its floor under its first spawns. */
    public static boolean hasBlocks(FfaArena f) {
        World w = f.world();
        if (w == null || f.spawns.isEmpty()) {
            return false;
        }

        int n = Math.min(2, f.spawns.size());
        for (int i = 0; i < n; i++) {
            if (!floorAt(w, f.spawns.get(i), 0, 0)) {
                return false;
            }
        }

        return true;
    }

    /** An arena whose map is really there: bounds, both spawns, its world, and blocks under the spawns. */
    public static boolean complete(Arena a) {
        return a.bounds != null && a.spawn1 != null && a.spawn2 != null && a.world() != null && hasBlocks(a, 0, 0);
    }

    /** A generated map that never finished (no blocks under its spawns): it has to be made again. */
    public boolean isBroken(Arena a) {
        if (!a.generated || a.theme == null || a.theme == ArenaTheme.CUSTOM || a.editing || this.isBusy(a)
            || a.instances.stream().anyMatch(i -> i.state == ArenaInstance.State.IN_USE)) {
            return false;
        } else if (a.bounds == null || a.spawn1 == null || a.spawn2 == null) {
            return true;
        } else {
            return a.world() != null && !hasBlocks(a, 0, 0);
        }
    }

    /** The same for an FFA arena. */
    public static boolean broken(FfaArena f) {
        if (!f.generated || f.theme == null || f.theme == ArenaTheme.CUSTOM || f.editing || f.building || !f.players.isEmpty()) {
            return false;
        } else if (f.bounds == null || f.spawns.isEmpty()) {
            return true;
        } else {
            return f.world() != null && !hasBlocks(f);
        }
    }

    /** The size a map of this theme is generated at (what the arena pack uses). */
    public static int packSize(ArenaTheme t) {
        if (t == ArenaTheme.MOLE_MINES || t.duel != null) {
            return targetSize(t, false);
        } else if (t == ArenaTheme.GRAND_COLOSSEUM) {
            return Cfg.i(Opt.MACE_ARENA_SIZE);
        } else {
            return t.isVanilla() ? Cfg.i(Opt.VANILLA_SIZE) : Cfg.i(Opt.DUEL_ARENA_SIZE);
        }
    }

    /** Makes a void generated map again in place of the old entry; it switches on by itself when done. */
    public CompletableFuture<Arena> remake(Arena a) {
        String id = a.id;
        String name = a.displayName;
        ArenaTheme theme = a.theme;
        boolean deep = a.deep;
        int copies = Math.max(1, a.instances.size() - 1);
        this.delete(a);
        return this.generate(id, name, theme, packSize(theme), copies, deep);
    }

    /** What Enable All did, per arena. */
    public static final class Report {
        public int on;
        public final List<String> rebuilding = new ArrayList<>();
        public final List<String> generating = new ArrayList<>();
        public final List<String> setup = new ArrayList<>();

        /** The reply for the admin, in MiniMessage. {@code progress} = the pack's "3/12", or null. */
        public List<String> lines(String progress) {
            List<String> out = new ArrayList<>();
            out.add("<green>[Arenas] Switched on " + this.on + " arena(s)." + (this.rebuilding.isEmpty() && this.generating.isEmpty() && this.setup.isEmpty() ? " <gray>Every arena is on." : ""));
            if (!this.rebuilding.isEmpty()) {
                out.add("<yellow>Void maps, being built again now (they switch on when done): <white>" + net.vortexrealm.util.Text.esc(String.join(", ", this.rebuilding)));
            }

            if (!this.generating.isEmpty()) {
                out.add("<yellow>Still being generated" + (progress == null ? "" : " (arena pack " + progress + ")") + ", they switch on when done: <white>"
                    + net.vortexrealm.util.Text.esc(String.join(", ", this.generating)));
            }

            if (!this.setup.isEmpty()) {
                out.add("<red>Can't be switched on: <white>" + net.vortexrealm.util.Text.esc(String.join("; ", this.setup)));
            }

            return out;
        }
    }

    /**
     * Switches on every arena (duel and FFA) whose map is there. {@code onlyStuck} = only generated ones (start-up);
     * false = every arena, also ones switched off by hand. A void generated map is made again, a missing snapshot is
     * taken again, copies stuck half-built are reset; anything that can't be switched on says why.
     */
    public Report enableAll(boolean onlyStuck) {
        Report r = new Report();

        for (Arena a : new ArrayList<>(this.arenas.values())) {
            if (a.editing || onlyStuck && !a.generated && !a.generating) {
                continue;
            }

            if (this.isBusy(a)) {
                if (!a.enabled) {
                    r.generating.add(a.displayName);
                }
                continue;
            }

            World w = a.world();
            if (a.bounds == null || a.spawn1 == null || a.spawn2 == null) {
                if (a.generated && a.theme != null && a.theme != ArenaTheme.CUSTOM) {
                    r.rebuilding.add(a.displayName);
                    this.remake(a);
                } else if (!a.enabled) {
                    r.setup.add(a.displayName + " (" + String.join(", ", a.problems()) + ")");
                }
                continue;
            }

            if (w == null) {
                if (!a.enabled) {
                    r.setup.add(a.displayName + " (world " + a.worldName + " is not loaded)");
                }
                continue;
            }

            if (!hasBlocks(a, 0, 0)) {
                if (a.generated && a.theme != null && a.theme != ArenaTheme.CUSTOM && a.instances.stream().noneMatch(i -> i.state == ArenaInstance.State.IN_USE)) {
                    r.rebuilding.add(a.displayName);
                    this.remake(a);
                } else if (!a.enabled) {
                    r.setup.add(a.displayName + " (no blocks under the spawns)");
                }
                continue;
            }

            if (a.generated && a.snapshot == null) {
                // resets and copies need it; the map is there, so it is taken again now
                this.captureSnapshot(a).whenComplete((v, err) -> Tasks.ensureSync(() -> this.repairCopies(a)));
            } else {
                this.repairCopies(a);
            }

            if (!a.enabled || a.generating) {
                a.enabled = true;
                a.generating = false;
                r.on++;
            }
        }

        FfaManager ffa = this.plugin.ffa();
        for (FfaArena f : new ArrayList<>(ffa.all())) {
            if (f.editing || onlyStuck && !f.generated && !f.generating) {
                continue;
            }

            if (f.building) {
                if (!f.enabled) {
                    r.generating.add(f.displayName + " (FFA)");
                }
                continue;
            }

            boolean gen = f.generated && f.theme != null && f.theme != ArenaTheme.CUSTOM;
            if (f.bounds == null || f.spawns.isEmpty()) {
                if (gen && f.kitId != null) {
                    r.rebuilding.add(f.displayName + " (FFA)");
                    String id = f.id;
                    String name = f.displayName;
                    ArenaTheme t = f.theme;
                    String kit = f.kitId;
                    int size = f.size > 0 ? f.size : targetSize(t, true);
                    ffa.delete(f);
                    ffa.generate(id, name, t, size, kit);
                } else if (!f.enabled) {
                    r.setup.add(f.displayName + " (FFA: no spawns or bounds)");
                }
                continue;
            }

            if (f.world() == null) {
                if (!f.enabled) {
                    r.setup.add(f.displayName + " (FFA: world " + f.worldName + " is not loaded)");
                }
                continue;
            }

            if (!hasBlocks(f)) {
                if (gen && f.players.isEmpty()) {
                    r.rebuilding.add(f.displayName + " (FFA)");
                    ffa.rebuild(f, true).whenComplete((v, err) -> Tasks.ensureSync(() -> {
                        if (err == null) {
                            f.enabled = true;
                            f.generating = false;
                            ffa.save();
                        }
                    }));
                } else if (!f.enabled) {
                    r.setup.add(f.displayName + " (FFA: no blocks under the spawns)");
                }
                continue;
            }

            if (f.generated && f.snapshot == null) {
                ffa.captureSnapshot(f);
            }

            if (!f.enabled || f.generating) {
                f.enabled = true;
                f.generating = false;
                r.on++;
            }
        }

        this.save();
        ffa.save();
        return r;
    }

    /** Copies left half-built or void (a paste cut short): put back in rotation through a full reset. */
    private void repairCopies(Arena a) {
        if (a.snapshot == null || this.isBusy(a)) {
            return;
        }

        for (ArenaInstance i : new ArrayList<>(a.instances)) {
            if ((i.dx != 0 || i.dz != 0) && i.world() != null
                && (i.state == ArenaInstance.State.BUILDING || i.state == ArenaInstance.State.FREE && !hasBlocks(a, i.dx, i.dz))) {
                i.state = ArenaInstance.State.IN_USE;
                this.release(i);
            }
        }
    }

    /** True when a generated arena no longer matches the current settings (signature or size). */
    public boolean isOutdated(Arena a) {
        if (!a.generated || a.theme == null || a.theme == ArenaTheme.CUSTOM || a.handEdited || a.editing || a.bounds == null || a.world() == null) {
            return false;
        } else if (a.instances.stream().anyMatch(ix -> ix.state == ArenaInstance.State.BUILDING)) {
            // still being generated or rebuilt
            return false;
        } else {
            String want = a.theme.isVanilla() ? VanillaTerrain.signature(this.depthOf(a), a.deep) : ArenaGenerator.signature(a.theme, false);
            int target = targetSize(a.theme, false);
            // the designed-to-size maps (compact duel maps, Mole) follow their size both ways; the others only grow
            boolean fixed = a.theme.duel != null || a.theme == ArenaTheme.MOLE_MINES;
            return !want.equals(a.genSignature) || a.size > 0 && (fixed ? a.size != target : a.size < target);
        }
    }

    public List<Object> outdated() {
        List<Object> out = new ArrayList<>();

        for (Arena a : this.arenas.values()) {
            if (this.isOutdated(a)) {
                out.add(a);
            }
        }

        for (FfaArena f : this.plugin.ffa().all()) {
            if (this.plugin.ffa().isOutdated(f)) {
                out.add(f);
            }
        }

        return out;
    }

    /** Kept for the FFA manager and old callers: the grand colosseum below the mace size. */
    public static boolean tooSmall(ArenaTheme theme, int size) {
        return theme == ArenaTheme.GRAND_COLOSSEUM && size > 0 && size < Cfg.i(Opt.MACE_ARENA_SIZE);
    }

    public CompletableFuture<Integer> updateOutdated() {
        if (this.updating) {
            return CompletableFuture.completedFuture(0);
        } else if (this.plugin.packRunning()) {
            // the arena pack replaces old maps and is building new ones right now: update once it is done
            return CompletableFuture.completedFuture(0);
        } else {
            List<Object> list = this.outdated();
            if (list.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            } else {
                this.updating = true;
                List<String> ids = new ArrayList<>();
                for (Object o : list) {
                    ids.add(o instanceof Arena a ? a.id : ((FfaArena)o).id);
                }

                this.plugin.getLogger().info("Updating " + list.size() + " arena(s) to the current arena settings: " + String.join(", ", ids));
                int[] busy = new int[]{0};
                CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);

                for (Object o : list) {
                    chain = chain.thenComposeAsync(n -> {
                        CompletableFuture<Void> f = o instanceof Arena a ? this.rebuild(a, true) : this.plugin.ffa().rebuild((FfaArena)o, true);
                        return f.handle((v, err) -> {
                            if (err != null) {
                                busy[0]++;
                                return n;
                            } else {
                                return n + 1;
                            }
                        });
                    }, Tasks.MAIN);
                }

                return chain.whenComplete(
                    (n, err) -> Tasks.ensureSync(
                        () -> {
                            this.updating = false;
                            this.plugin
                                .getLogger()
                                .info("Arena update finished: " + n + " updated" + (busy[0] > 0 ? ", " + busy[0] + " busy (retrying in 5 minutes)" : "") + ".");
                            if (busy[0] > 0 && this.updateRetries++ < 12) {
                                Tasks.later(6000L, this::updateOutdated);
                            }
                        }
                    )
                );
            }
        }
    }

    public CompletableFuture<Void> captureSnapshot(Arena a) {
        World w = a.world();
        return w != null && a.bounds != null ? RegionSnapshot.capture(this.plugin, w, a.bounds).thenAccept(snap -> {
            a.snapshot = snap;

            try {
                snap.save(this.snapshotFile(a));
            } catch (IOException var4) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save snapshot of " + a.id, (Throwable)var4);
            }
        }) : CompletableFuture.failedFuture(new IllegalStateException("Arena world/bounds missing"));
    }

    public CompletableFuture<Void> addCopies(Arena a, int count) {
        if (count <= 0) {
            return CompletableFuture.completedFuture(null);
        } else {
            CompletableFuture<Void> ensureSnap = a.snapshot != null ? CompletableFuture.completedFuture(null) : this.captureSnapshot(a);
            return ensureSnap.thenComposeAsync(
                v -> {
                    World w = this.plugin.worlds().practiceWorld();
                    List<CompletableFuture<Void>> all = new ArrayList<>();
                    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

                    for (int i = 0; i < count; i++) {
                        int[] origin = this.allocateOrigin();
                        int dx = origin[0] - a.bounds.minX();
                        int dz = origin[1] - a.bounds.minZ();
                        int idx = a.instances.stream().mapToInt(x -> x.index).max().orElse(0) + 1;
                        ArenaInstance inst = new ArenaInstance(a, idx, w.getName(), dx, dz);
                        inst.state = ArenaInstance.State.BUILDING;
                        a.instances.add(inst);
                        RegionSnapshot snap = a.snapshot;
                        CompletableFuture<Void> f = chain.<Void>thenComposeAsync(x -> preload(w, a.bounds.shift(dx, 0, dz)), Tasks.MAIN)
                            .thenComposeAsync(x -> this.plugin.jobs().submit(snap.pasteJob(w, dx, dz, true)), Tasks.MAIN)
                            .handle((x, err) -> {
                                Tasks.ensureSync(() -> {
                                    if (err == null) {
                                        inst.state = ArenaInstance.State.FREE;
                                    } else {
                                        // a copy that could not be made is dropped, it never blocks the map
                                        a.instances.remove(inst);
                                        this.save();
                                        this.plugin.getLogger().log(Level.WARNING, "Copy " + idx + " of " + a.id + " failed and was removed", err);
                                    }
                                });
                                return (Void)null;
                            });
                        chain = f;
                        all.add(f);
                    }

                    this.save();
                    this.busyEnter(a.id);
                    return CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).whenComplete((x, err) -> Tasks.ensureSync(() -> this.busyExit(a.id)));
                },
                Tasks.MAIN
            );
        }
    }

    public void verifyAll() {
        for (Arena a : this.arenas.values()) {
            if (a.snapshot != null) {
                for (ArenaInstance i : a.instances) {
                    if (i.state == ArenaInstance.State.FREE && i.world() != null) {
                        i.state = ArenaInstance.State.IN_USE;
                        this.release(i);
                    }
                }
            }
        }
    }

    public int removeCopies(Arena a) {
        int removed = 0;

        for (ArenaInstance i : new ArrayList<>(a.instances)) {
            if (i.index > 0 && i.state != ArenaInstance.State.IN_USE) {
                a.instances.remove(i);
                removed++;
            }
        }

        this.save();
        return removed;
    }

    /** Removes copies beyond {@code keep} that aren't in use (vanilla maps start with fewer copies; Auto Copies adds more). */
    public int trimCopies(Arena a, int keep) {
        int removed = 0;

        for (ArenaInstance i : new ArrayList<>(a.instances)) {
            if (i.index > keep && i.state == ArenaInstance.State.FREE) {
                a.instances.remove(i);
                removed++;
            }
        }

        if (removed > 0) {
            this.save();
        }

        return removed;
    }

    public void delete(Arena a) {
        this.arenas.remove(a.id);
        File snap = this.snapshotFile(a);
        if (snap.exists() && !snap.delete()) {
            this.plugin.getLogger().warning("Could not delete " + snap);
        }

        this.save();
    }

    public void deleteSnapshot(Arena a) {
        a.snapshot = null;
        File snap = this.snapshotFile(a);
        if (snap.exists() && !snap.delete()) {
            this.plugin.getLogger().warning("Could not delete " + snap);
        }
    }

    public void syncTemplateWorld(Arena a) {
        if (!a.instances.isEmpty() && a.instances.get(0).index == 0) {
            ArenaInstance old = a.instances.get(0);
            if (!old.worldName.equals(a.worldName)) {
                a.instances.set(0, new ArenaInstance(a, 0, a.worldName, 0, 0));
            }
        }
    }

    public int inUseCount() {
        int n = 0;

        for (Arena a : this.arenas.values()) {
            for (ArenaInstance i : a.instances) {
                if (i.state == ArenaInstance.State.IN_USE) {
                    n++;
                }
            }
        }

        return n;
    }
}
