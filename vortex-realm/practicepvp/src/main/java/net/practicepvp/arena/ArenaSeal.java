package net.practicepvp.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.ffa.FfaArena;
import net.practicepvp.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Closes every arena with invisible barrier walls: a ring one block outside its bounds, from just under the floor up
 * to the top of the world (no roof, so mace and wind-charge jumps keep working). Only air, plants, liquids and other
 * replaceable blocks become barriers - terrain and builds are left alone. Every arena is closed: duel copies, FFA arenas
 * and bot arenas, designed maps and vanilla maps alike. Built in small steps, one arena at a time (light on memory).
 *
 * <p>A rebuild wipes the area around an arena, walls included, so every rebuild and every new arena is closed again
 * right after ({@link #reseal}). On top of that a check every 5 minutes looks at the walls of the arenas that are loaded
 * (the ones players are in) and closes any gap it finds, so a wall can never stay open for long.
 */
public final class ArenaSeal {
    private final PracticePlugin plugin;
    private final File file;
    private final YamlConfiguration done;
    private boolean running;
    /** Arenas waiting for their walls: {World, Cuboid}. */
    private final Deque<Object[]> todo = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private int batchTotal;
    private final List<CompletableFuture<Integer>> waiting = new ArrayList<>();

    public ArenaSeal(PracticePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arena-walls.yml");
        this.done = YamlConfiguration.loadConfiguration(this.file);
    }

    private static String key(String world, Cuboid c) {
        return (world + "_" + c.minX() + "_" + c.minZ() + "_" + c.maxX() + "_" + c.maxZ()).replace('.', '_');
    }

    /** Every arena area now: duel copies, FFA arenas, bot arenas - vanilla maps included. */
    private List<Object[]> areas() {
        List<Object[]> out = new ArrayList<>();
        for (Arena a : this.plugin.arenas().all()) {
            out.addAll(areasOf(a));
        }

        if (this.plugin.ffa() != null) {
            for (FfaArena f : this.plugin.ffa().all()) {
                Object[] o = areaOf(f);
                if (o != null) {
                    out.add(o);
                }
            }
        }

        return out;
    }

    private static List<Object[]> areasOf(Arena a) {
        List<Object[]> out = new ArrayList<>();
        if (a.bounds != null && !a.editing) {
            for (ArenaInstance i : a.instances) {
                if (i.world() != null && i.bounds() != null) {
                    out.add(new Object[]{i.world(), i.bounds()});
                }
            }
        }

        return out;
    }

    private static Object[] areaOf(FfaArena f) {
        World w = f.worldName == null ? null : Bukkit.getWorld(f.worldName);
        return w != null && f.bounds != null && !f.editing ? new Object[]{w, f.bounds} : null;
    }

    // ------------------------------------------------------------------ while an arena is built

    /**
     * Closes one area right now, as a step of building it (before the map is pasted / generated, so an arena is never
     * open without its walls - not even while its copies are still being made).
     */
    public java.util.concurrent.CompletableFuture<Void> sealNow(World w, Cuboid c) {
        if (!Cfg.bool(Opt.ARENA_BARRIER_WALLS) || w == null || c == null) {
            return CompletableFuture.completedFuture(null);
        }

        String k = key(w.getName(), c);
        return this.plugin.jobs().submit(this.job(w, c, true)).thenRun(() -> net.practicepvp.util.Tasks.ensureSync(() -> this.done.set(k, true)));
    }

    /** After a build: walls that a build step opened again (a gap) are closed; walls that are fine are left alone. */
    public void verify(Arena a) {
        if (a == null || !Cfg.bool(Opt.ARENA_BARRIER_WALLS)) {
            return;
        }

        net.practicepvp.util.Tasks.later(100L, () -> {
            List<Object[]> broken = new ArrayList<>();
            for (Object[] o : areasOf(a)) {
                World w = (World)o[0];
                Cuboid c = (Cuboid)o[1];
                if (!this.done.getBoolean(key(w.getName(), c)) || this.hasGap(w, c)) {
                    this.done.set(key(w.getName(), c), null);
                    broken.add(o);
                }
            }

            if (!broken.isEmpty()) {
                this.enqueue(broken);
            }
        });
    }

    /** The FFA version of {@link #verify(Arena)}. */
    public void verify(FfaArena f) {
        Object[] o = f == null ? null : areaOf(f);
        if (o == null || !Cfg.bool(Opt.ARENA_BARRIER_WALLS)) {
            return;
        }

        net.practicepvp.util.Tasks.later(100L, () -> {
            World w = (World)o[0];
            Cuboid c = (Cuboid)o[1];
            if (!this.done.getBoolean(key(w.getName(), c)) || this.hasGap(w, c)) {
                this.done.set(key(w.getName(), c), null);
                this.enqueue(List.<Object[]>of(o));
            }
        });
    }

    // ------------------------------------------------------------------ after a rebuild

    /** A duel arena was generated or rebuilt: its walls were wiped with the old map, build them again. */
    public void reseal(Arena a) {
        if (a != null) {
            this.reseal(areasOf(a));
        }
    }

    /** An FFA arena was generated or rebuilt: its walls were wiped with the old map, build them again. */
    public void reseal(FfaArena f) {
        Object[] o = f == null ? null : areaOf(f);
        if (o != null) {
            this.reseal(List.<Object[]>of(o));
        }
    }

    private void reseal(List<Object[]> list) {
        if (!Cfg.bool(Opt.ARENA_BARRIER_WALLS) || list.isEmpty()) {
            return;
        }

        for (Object[] o : list) {
            this.done.set(key(((World)o[0]).getName(), (Cuboid)o[1]), null);
        }

        // a short pause: the arena's snapshot and copies are still being written right after a rebuild
        net.practicepvp.util.Tasks.later(200L, () -> this.enqueue(list));
    }

    // ------------------------------------------------------------------ one-time jobs after updates

    /**
     * After an update. 3.9.2: every generated arena (not the vanilla maps) was rebuilt once with walls. 3.10.0: the walls
     * of every arena are checked and built again once - the vanilla maps and FFA arenas had none, and rebuilt arenas
     * had lost theirs.
     */
    public void rebuildOnce() {
        if (!this.done.getBoolean("dry-v1")) {
            // 3.11.0: the water is taken out of every arena once (then the walls are checked)
            this.running = true;
            this.dryAll().whenComplete((n, err) -> net.practicepvp.util.Tasks.ensureSync(() -> {
                this.running = false;
                if (err != null) {
                    this.plugin.getLogger().log(Level.WARNING, "Taking the water out of the arenas failed", err);
                }

                this.done.set("dry-v1", true);
                this.save();
                this.rebuildOnce();
            }));
            return;
        }

        if (!Cfg.bool(Opt.ARENA_BARRIER_WALLS)) {
            return;
        }

        if (!this.done.getBoolean("walls-v4")) {
            // 3.11.0: the walls go up to the top of the world now, so every arena is closed again once
            for (String k : new ArrayList<>(this.done.getKeys(false))) {
                if (!k.equals("dry-v1")) {
                    this.done.set(k, null);
                }
            }

            this.done.set("rebuilt-with-walls", true);
            this.done.set("walls-v4", true);
            this.save();
            this.plugin.getLogger().info("Building the invisible walls of every arena up to the sky, one arena at a time...");
        }

        this.sealAll(false);
    }

    // ------------------------------------------------------------------ no water in the arenas

    /**
     * Takes the water out of every arena, one at a time: lakes, rivers and pools are filled with ground up to where the
     * water was (sand where the bed is sand, else dirt under a grass top), underwater plants and lily pads go, and
     * waterlogged blocks are drained. The arena's reset snapshot is taken again after, so resets don't bring it back.
     */
    public CompletableFuture<Integer> dryAll() {
        List<Object[]> work = new ArrayList<>();
        // 3.13.8: every arena is marked as it finishes, so a restart in the middle goes on with the next one
        for (Arena a : this.plugin.arenas().all()) {
            if (a.bounds != null && !a.editing && !a.instances.isEmpty() && !this.done.getBoolean("dry." + a.id)) {
                work.add(new Object[]{a});
            }
        }

        if (this.plugin.ffa() != null) {
            for (FfaArena f : this.plugin.ffa().all()) {
                if (areaOf(f) != null && !this.done.getBoolean("dry.ffa." + f.id)) {
                    work.add(new Object[]{f});
                }
            }
        }

        this.plugin.getLogger().info("Taking the water out of " + work.size() + " arenas (one at a time, in the background)...");
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (Object[] w : work) {
            chain = chain.thenCompose(n -> {
                CompletableFuture<Integer> f = new CompletableFuture<>();
                this.whenMemoryFree(() -> {
                    List<Object[]> areas = w[0] instanceof Arena a ? areasOf(a) : java.util.Collections.singletonList(areaOf((FfaArena)w[0]));
                    int[] changed = new int[1];
                    CompletableFuture<Void> c = CompletableFuture.completedFuture(null);
                    for (Object[] ar : areas) {
                        if (ar != null) {
                            c = c.thenCompose(v -> this.plugin.jobs().submit(this.dryJob((World)ar[0], (Cuboid)ar[1], changed)));
                        }
                    }

                    c.whenComplete((v, err) -> net.practicepvp.util.Tasks.ensureSync(() -> {
                        if (changed[0] > 0) {
                            if (w[0] instanceof Arena a) {
                                this.plugin.arenas().captureSnapshot(a);
                                this.plugin.getLogger().info("Water taken out of " + a.displayName + " (" + changed[0] + " blocks)");
                            } else {
                                FfaArena fa = (FfaArena)w[0];
                                this.plugin.ffa().captureSnapshot(fa);
                                this.plugin.getLogger().info("Water taken out of " + fa.displayName + " (" + changed[0] + " blocks)");
                            }
                        }

                        this.done.set(w[0] instanceof Arena a ? "dry." + a.id : "dry.ffa." + ((FfaArena)w[0]).id, true);
                        this.save();
                        f.complete(n + (changed[0] > 0 ? 1 : 0));
                    }));
                });
                return f;
            });
        }

        return chain;
    }

    /** Runs the next arena once memory use is under 70% (checked every 2 s; gives the garbage collector time). */
    private void whenMemoryFree(Runnable r) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        if (used < rt.maxMemory() * 0.70) {
            net.practicepvp.util.Tasks.later(40L, r);
        } else {
            net.practicepvp.util.Tasks.later(40L, () -> this.whenMemoryFree(r));
        }
    }

    private static final java.util.Map<Material, Boolean> WATERLOGGABLE = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean canWaterlog(Material m) {
        return WATERLOGGABLE.computeIfAbsent(m, k -> k.isBlock() && k.createBlockData() instanceof org.bukkit.block.data.Waterlogged);
    }

    private static boolean wet(Material m) {
        return m == Material.WATER || m == Material.KELP || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS
            || m == Material.BUBBLE_COLUMN;
    }

    /** Column by column: every run of water is filled from its bed up to where its surface was. */
    private BlockJobs.Job dryJob(World w, Cuboid c, int[] changed) {
        return new BlockJobs.Job() {
            int x = c.minX();
            int z = c.minZ();

            @Override
            public int step(int budget) {
                int used = 0;
                while (this.x <= c.maxX() && used < budget) {
                    int top = Math.min(c.maxY(), w.getHighestBlockYAt(this.x, this.z));
                    int y = top;
                    while (y >= c.minY()) {
                        Block b = w.getBlockAt(this.x, y, this.z);
                        Material m = b.getType();
                        used++;
                        if (m.isAir()) {
                            y--;
                            continue;
                        }

                        if (m == Material.LILY_PAD) {
                            b.setType(Material.AIR, false);
                            changed[0]++;
                        } else if (canWaterlog(m) && b.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) {
                            wl.setWaterlogged(false);
                            b.setBlockData(wl, false);
                            changed[0]++;
                        } else if (wet(m)) {
                            int runTop = y;
                            int bed = y;
                            while (bed >= c.minY() && wet(w.getBlockAt(this.x, bed, this.z).getType())) {
                                bed--;
                            }

                            Material bm = bed >= c.minY() ? w.getBlockAt(this.x, bed, this.z).getType() : Material.DIRT;
                            boolean sandy = bm == Material.SAND || bm == Material.GRAVEL || bm == Material.CLAY || bm == Material.RED_SAND || bm == Material.SANDSTONE;
                            Material topM = sandy ? (bm == Material.RED_SAND ? Material.RED_SAND : Material.SAND) : Material.GRASS_BLOCK;
                            Material fillM = sandy ? topM : Material.DIRT;
                            for (int yy = runTop; yy > bed; yy--) {
                                w.getBlockAt(this.x, yy, this.z).setType(yy == runTop ? topM : fillM, false);
                                changed[0]++;
                                used += 2;
                            }

                            y = bed;
                            continue;
                        }

                        y--;
                    }

                    if (++this.z > c.maxZ()) {
                        this.z = c.minZ();
                        this.x++;
                    }

                    if ((used & 255) == 0 && BlockJobs.overDeadline()) {
                        break;
                    }
                }

                return Math.max(1, used);
            }

            @Override
            public boolean done() {
                return this.x > c.maxX();
            }

            @Override
            public String name() {
                return "dry arena";
            }
        };
    }

    /**
     * Deletes every generated arena that is not a vanilla map - duel maps and FFA arenas - and builds it again from
     * zero with a new layout, one at a time; each is closed with walls right after. Vanilla maps stay as they are.
     */
    public CompletableFuture<Integer> regenerateDesigned(Consumer<String> progress) {
        List<Object> list = new ArrayList<>();
        for (Arena a : this.plugin.arenas().all()) {
            if (a.generated && a.theme != null && a.theme != ArenaTheme.CUSTOM && !a.theme.isVanilla() && !a.handEdited && !a.editing) {
                list.add(a);
            }
        }

        if (this.plugin.ffa() != null) {
            for (FfaArena f : this.plugin.ffa().all()) {
                if (f.generated && f.theme != null && f.theme != ArenaTheme.CUSTOM && !f.theme.isVanilla() && !f.handEdited && !f.editing) {
                    list.add(f);
                }
            }
        }

        int total = list.size();
        this.plugin.getLogger().info("Regenerating " + total + " designed arenas one by one (the vanilla maps stay as they are)...");
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (Object o : list) {
            chain = chain.thenCompose(n -> {
                CompletableFuture<Integer> f = new CompletableFuture<>();
                net.practicepvp.util.Tasks.later(60L, () -> {
                    String id;
                    CompletableFuture<Void> job;
                    if (o instanceof Arena a) {
                        id = a.displayName;
                        job = this.plugin.arenas().rebuild(a, false);
                    } else {
                        FfaArena fa = (FfaArena)o;
                        id = fa.displayName + " (FFA)";
                        job = this.plugin.ffa().rebuild(fa, false);
                    }

                    job.whenComplete((v, err) -> net.practicepvp.util.Tasks.ensureSync(() -> {
                        Throwable cause = err != null && err.getCause() != null ? err.getCause() : err;
                        String line = err != null
                            ? "Regenerating " + id + " failed: " + cause.getMessage()
                            : "Regenerated " + id + " (" + (n + 1) + "/" + total + ")";
                        this.plugin.getLogger().info(line);
                        if (progress != null) {
                            progress.accept(line);
                        }

                        f.complete(err == null ? n + 1 : n);
                    }));
                });
                return f;
            });
        }

        return chain;
    }

    // ------------------------------------------------------------------ building the walls

    /** Builds the walls of every arena that has none yet (or all of them with {@code force}). */
    public CompletableFuture<Integer> sealAll(boolean force) {
        if (!Cfg.bool(Opt.ARENA_BARRIER_WALLS)) {
            return CompletableFuture.completedFuture(0);
        }

        if (!force) {
            this.repairGaps();
        }

        List<Object[]> list = new ArrayList<>();
        for (Object[] a : this.areas()) {
            if (force || !this.done.getBoolean(key(((World)a[0]).getName(), (Cuboid)a[1]))) {
                list.add(a);
            }
        }

        return this.enqueue(list);
    }

    private CompletableFuture<Integer> enqueue(List<Object[]> list) {
        int added = 0;
        for (Object[] a : list) {
            if (this.queued.add(key(((World)a[0]).getName(), (Cuboid)a[1]))) {
                this.todo.add(a);
                added++;
            }
        }

        CompletableFuture<Integer> result = new CompletableFuture<>();
        if (this.todo.isEmpty() && !this.running) {
            result.complete(0);
            return result;
        }

        this.waiting.add(result);
        this.batchTotal += added;
        if (!this.running) {
            this.running = true;
            this.plugin.getLogger().info("Closing " + this.todo.size() + " arenas with invisible walls (in the background, one at a time)...");
            this.next();
        }

        return result;
    }

    private void next() {
        Object[] a = this.todo.poll();
        if (a == null) {
            this.running = false;
            this.save();
            int total = this.batchTotal;
            this.batchTotal = 0;
            if (total > 0) {
                this.plugin.getLogger().info("Invisible walls done: " + total + " arenas closed.");
            }

            List<CompletableFuture<Integer>> w = new ArrayList<>(this.waiting);
            this.waiting.clear();
            for (CompletableFuture<Integer> f : w) {
                f.complete(total);
            }

            return;
        }

        World w = (World)a[0];
        Cuboid c = (Cuboid)a[1];
        String k = key(w.getName(), c);
        if (!Cfg.bool(Opt.ARENA_BARRIER_WALLS) || Bukkit.getWorld(w.getName()) == null) {
            this.queued.remove(k);
            this.next();
            return;
        }

        this.plugin.jobs().submit(this.job(w, c, true)).whenComplete((v, err) -> net.practicepvp.util.Tasks.later(60L, () -> {
            this.queued.remove(k);
            if (err != null) {
                this.plugin.getLogger().log(Level.WARNING, "Arena walls at " + c + " failed", err);
            } else {
                this.done.set(k, true);
                if (this.todo.size() % 10 == 0) {
                    this.save();
                    if (!this.todo.isEmpty()) {
                        this.plugin.getLogger().info("Closing arenas: " + this.todo.size() + " left");
                    }
                }
            }

            this.next();
        }));
    }

    /**
     * Looks at the walls of every arena whose edge is loaded right now (no chunk is loaded for this) and queues the
     * ones with a gap: a spot on the ring that is open (air, plants, water) instead of a barrier.
     */
    private void repairGaps() {
        List<Object[]> broken = new ArrayList<>();
        for (Object[] a : this.areas()) {
            World w = (World)a[0];
            Cuboid c = (Cuboid)a[1];
            String k = key(w.getName(), c);
            if (this.done.getBoolean(k) && !this.queued.contains(k) && this.hasGap(w, c)) {
                this.done.set(k, null);
                broken.add(a);
            }
        }

        if (!broken.isEmpty()) {
            this.plugin.getLogger().info("Found " + broken.size() + " arena(s) with an open wall - closing them again.");
            this.enqueue(broken);
        }
    }

    private boolean hasGap(World w, Cuboid c) {
        int x0 = c.minX() - 1;
        int x1 = c.maxX() + 1;
        int z0 = c.minZ() - 1;
        int z1 = c.maxZ() + 1;
        int mx = (x0 + x1) / 2;
        int mz = (z0 + z1) / 2;
        int[][] spots = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {mx, z0}, {mx, z1}, {x0, mz}, {x1, mz}};
        int bottom = Math.max(w.getMinHeight(), c.minY() - 3);
        int top = w.getMaxHeight() - 1;
        for (int[] s : spots) {
            if (!w.isChunkLoaded(s[0] >> 4, s[1] >> 4)) {
                continue;
            }

            for (int y = bottom; y <= top; y += 3) {
                Block b = w.getBlockAt(s[0], y, s[1]);
                if (b.getType() != Material.BARRIER && (b.getType().isAir() || b.isReplaceable() || b.isLiquid())) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Removes the walls again (option turned off). */
    public CompletableFuture<Integer> removeAll() {
        this.todo.clear();
        this.queued.clear();
        List<Object[]> all = this.areas();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Object[] a : all) {
            chain = chain.thenCompose(v -> this.plugin.jobs().submit(this.job((World)a[0], (Cuboid)a[1], false)));
        }

        return chain.thenApply(v -> {
            for (String k : new ArrayList<>(this.done.getKeys(false))) {
                if (!k.equals("rebuilt-with-walls") && !k.equals("walls-v4") && !k.equals("dry-v1")) {
                    this.done.set(k, null);
                }
            }

            this.save();
            return all.size();
        });
    }

    private void save() {
        try {
            this.done.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save arena-walls.yml", e);
        }
    }

    /** The ring one block outside the bounds, column by column. */
    private BlockJobs.Job job(World w, Cuboid c, boolean place) {
        int x0 = c.minX() - 1;
        int x1 = c.maxX() + 1;
        int z0 = c.minZ() - 1;
        int z1 = c.maxZ() + 1;
        List<int[]> cols = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            cols.add(new int[]{x, z0});
            cols.add(new int[]{x, z1});
        }

        for (int z = z0 + 1; z < z1; z++) {
            cols.add(new int[]{x0, z});
            cols.add(new int[]{x1, z});
        }

        int bottom = Math.max(w.getMinHeight(), c.minY() - 3);
        // 3.11.0: up to the top of the world (still one arena at a time, in small steps)
        int top = w.getMaxHeight() - 1;
        return new BlockJobs.Job() {
            int col = 0;
            int y = bottom;

            @Override
            public int step(int budget) {
                int used = 0;
                while (this.col < cols.size() && used < budget) {
                    int[] xz = cols.get(this.col);
                    Block b = w.getBlockAt(xz[0], this.y, xz[1]);
                    if (place) {
                        if (b.getType() != Material.BARRIER && (b.getType().isAir() || b.isReplaceable() || b.isLiquid())) {
                            b.setType(Material.BARRIER, false);
                        }
                    } else if (b.getType() == Material.BARRIER) {
                        b.setType(Material.AIR, false);
                    }

                    used++;
                    if (++this.y > top) {
                        this.y = bottom;
                        this.col++;
                    }
                }

                return used;
            }

            @Override
            public boolean done() {
                return this.col >= cols.size();
            }

            @Override
            public String name() {
                return "arena walls";
            }
        };
    }
}
