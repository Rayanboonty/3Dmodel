package net.practicepvp.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Closes every arena with invisible barrier walls: a ring one block outside its bounds, from just under the floor up to
 * the top of the world (no roof, so mace and wind-charge jumps keep working). Only air, plants, liquids and other
 * replaceable blocks become barriers - terrain and builds are left alone. Built in small steps (no lag), once per
 * arena copy; resets never touch it because it is outside the area they restore.
 */
public final class ArenaSeal {
    private final PracticePlugin plugin;
    private final File file;
    private final YamlConfiguration done;
    private boolean running;

    public ArenaSeal(PracticePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arena-walls.yml");
        this.done = YamlConfiguration.loadConfiguration(this.file);
    }

    private static String key(String world, Cuboid c) {
        return (world + "_" + c.minX() + "_" + c.minZ() + "_" + c.maxX() + "_" + c.maxZ()).replace('.', '_');
    }

    /** Every arena area now: duel copies, FFA arenas, bot arenas. */
    private List<Object[]> areas() {
        List<Object[]> out = new ArrayList<>();
        for (Arena a : this.plugin.arenas().all()) {
            for (ArenaInstance i : a.instances) {
                if (a.bounds != null && i.world() != null) {
                    out.add(new Object[]{i.world(), i.bounds()});
                }
            }
        }

        if (this.plugin.ffa() != null) {
            for (net.practicepvp.ffa.FfaArena f : this.plugin.ffa().all()) {
                World w = f.worldName == null ? null : Bukkit.getWorld(f.worldName);
                if (w != null && f.bounds != null) {
                    out.add(new Object[]{w, f.bounds});
                }
            }
        }

        return out;
    }

    /** Builds the walls of every arena that has none yet (or all of them with {@code force}). */
    public CompletableFuture<Integer> sealAll(boolean force) {
        if (!Cfg.bool(Opt.ARENA_BARRIER_WALLS) || this.running) {
            return CompletableFuture.completedFuture(0);
        }

        Deque<Object[]> todo = new ArrayDeque<>();
        for (Object[] a : this.areas()) {
            if (force || !this.done.getBoolean(key(((World)a[0]).getName(), (Cuboid)a[1]))) {
                todo.add(a);
            }
        }

        if (todo.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        this.running = true;
        int total = todo.size();
        this.plugin.getLogger().info("Closing " + total + " arenas with invisible walls (in the background)...");
        CompletableFuture<Integer> result = new CompletableFuture<>();
        this.next(todo, total, result);
        return result;
    }

    private void next(Deque<Object[]> todo, int total, CompletableFuture<Integer> result) {
        Object[] a = todo.poll();
        if (a == null) {
            this.running = false;
            this.save();
            this.plugin.getLogger().info("All " + total + " arenas are closed with invisible walls.");
            result.complete(total);
            return;
        }

        World w = (World)a[0];
        Cuboid c = (Cuboid)a[1];
        this.plugin.jobs().submit(this.job(w, c, true)).whenComplete((v, err) -> net.practicepvp.util.Tasks.ensureSync(() -> {
            if (err != null) {
                this.plugin.getLogger().log(Level.WARNING, "Arena walls at " + c + " failed", err);
            } else {
                this.done.set(key(w.getName(), c), true);
                int left = todo.size();
                if (left % 10 == 0) {
                    this.save();
                    this.plugin.getLogger().info("Closing arenas: " + (total - left) + "/" + total);
                }
            }

            this.next(todo, total, result);
        }));
    }

    /** Removes the walls again (option turned off). */
    public CompletableFuture<Integer> removeAll() {
        List<Object[]> all = this.areas();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Object[] a : all) {
            chain = chain.thenCompose(v -> this.plugin.jobs().submit(this.job((World)a[0], (Cuboid)a[1], false)));
        }

        return chain.thenApply(v -> {
            for (String k : this.done.getKeys(false)) {
                this.done.set(k, null);
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
