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
 * Closes every arena with invisible barrier walls: a ring one block outside its bounds, from just under the floor to
 * 20 blocks above its top (no roof, so mace and wind-charge jumps keep working). Only air, plants, liquids and other
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
        if (!Cfg.bool(Opt.ARENA_BARRIER_WALLS)) {
            return;
        }

        if (!this.done.getBoolean("walls-v3")) {
            for (String k : new ArrayList<>(this.done.getKeys(false))) {
                this.done.set(k, null);
            }

            this.done.set("rebuilt-with-walls", true);
            this.done.set("walls-v3", true);
            this.save();
            this.plugin.getLogger().info("Checking the invisible walls of every arena once (FFA and vanilla maps too)...");
        }

        this.sealAll(false);
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
        int top = Math.min(w.getMaxHeight() - 1, c.maxY() + 20);
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
                if (!k.equals("rebuilt-with-walls") && !k.equals("walls-v3")) {
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
        // 20 blocks above the arena's top: high enough to stop everyone, low enough to keep memory use small
        int top = Math.min(w.getMaxHeight() - 1, c.maxY() + 20);
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
