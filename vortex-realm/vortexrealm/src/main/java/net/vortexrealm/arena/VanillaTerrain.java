package net.vortexrealm.arena;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.util.TriState;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.util.Cuboid;
import net.vortexrealm.util.Pos;
import net.vortexrealm.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitTask;

/**
 * Real vanilla terrain for the natural arenas (MCPVP style): a temporary world is generated with the same seed every
 * time, the best spot for each map is found (pure biome, flat enough to fight, no lava, little water, fair spawns), and
 * that square is cut out, optimised for PvP (caves filled, leaves made persistent, a bedrock floor or a deep crystal
 * floor) and turned into a {@link RegionSnapshot} at the arena's slot. Jobs run one at a time; the temporary worlds are
 * unloaded and deleted as soon as the queue is empty.
 */
public final class VanillaTerrain {
    public static final String SOURCE_PREFIX = "vortex_terrain";
    public static final int VERSION = 9;
    private static final boolean[] VEG = new boolean[Material.values().length];
    private static final boolean[] GROUND = new boolean[Material.values().length];
    private static final boolean[] FLUID = new boolean[Material.values().length];
    private static final boolean[] ROCK = new boolean[Material.values().length];
    private static final boolean[] HAZARD = new boolean[Material.values().length];
    private static final boolean[] FILLABLE = new boolean[Material.values().length];
    /** Counts as ground on the surface, but underground it marks a geode or dripstone cave: replaced by plain rock. */
    private static final boolean[] POCKET = new boolean[Material.values().length];
    private static final boolean[] ORE = new boolean[Material.values().length];
    private static boolean classified;
    private final VortexRealm plugin;
    private final Map<VanillaTerrain.Dim, World> sources = new EnumMap<>(VanillaTerrain.Dim.class);
    private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);
    private int pending;
    private BukkitTask idleTask;

    public VanillaTerrain(VortexRealm plugin) {
        this.plugin = plugin;
        classify();
    }

    /** True when {@code signature} was cut by this version of the site search (so its site can be reused as is). */
    public static boolean sameVersion(String signature) {
        return signature != null && signature.startsWith("vanilla/" + VERSION + "/");
    }

    public static boolean isSourceWorld(String name) {
        return name != null && name.startsWith(SOURCE_PREFIX);
    }

    public static boolean supports(ArenaTheme t) {
        return t != null && t.vanilla != null;
    }

    public static boolean enabled() {
        return Cfg.bool(Opt.VANILLA_TERRAIN);
    }

    public static long seed() {
        String s = Cfg.s(Opt.VANILLA_SEED).trim();
        if (s.isEmpty()) {
            s = "VortexRealm";
        }

        try {
            return Long.parseLong(s);
        } catch (NumberFormatException var2) {
            return s.hashCode();
        }
    }

    /** "vanilla/1/&lt;depth&gt;/&lt;crystal&gt;/&lt;fill&gt;" - changes when an option that changes the cut changes. */
    public static String signature(int depth, boolean crystal) {
        return "vanilla/" + VERSION + "/" + depth + "/" + crystal + "/" + Cfg.bool(Opt.VANILLA_FILL_CAVES) + "/" + seed();
    }

    // ------------------------------------------------------------------ queue

    /**
     * Finds a site for the theme (or re-uses {@code hint}) and cuts it for an arena whose region starts at (ox, oz).
     * Completes on the main thread's terms: the snapshot is ready to paste.
     */
    public CompletableFuture<VanillaTerrain.Cut> cut(ArenaTheme theme, int size, int ox, int oz, boolean ffa, boolean crystal, VanillaTerrain.Site hint) {
        VanillaTerrain.Spec spec = theme.vanilla;
        if (spec == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(theme + " has no vanilla terrain"));
        } else {
            int depth = crystal ? Cfg.i(Opt.CRYSTAL_DEPTH) : (ffa ? Cfg.i(Opt.FFA_DEPTH) : Cfg.i(Opt.DUEL_DEPTH));
            int buildHeight = Cfg.i(Opt.BUILD_HEIGHT);
            int s = Math.max(64, Math.min(448, size));
            return this.enqueue(() -> this.run(theme, spec, s, ox, oz, ffa, crystal, depth, buildHeight, hint));
        }
    }

    private synchronized <T> CompletableFuture<T> enqueue(Supplier<CompletableFuture<T>> job) {
        this.pending++;
        Tasks.cancel(this.idleTask);
        CompletableFuture<T> result = new CompletableFuture<>();
        this.tail = this.tail.handle((v, e) -> null).thenComposeAsync(v -> {
            CompletableFuture<T> f;
            try {
                f = job.get();
            } catch (Throwable t) {
                f = CompletableFuture.failedFuture(t);
            }

            return f.handle((r, e) -> {
                if (e != null) {
                    result.completeExceptionally(e);
                } else {
                    result.complete(r);
                }

                return null;
            });
        }, Tasks.MAIN).whenComplete((v, e) -> Tasks.ensureSync(this::jobDone));
        return result;
    }

    private void jobDone() {
        synchronized (this) {
            this.pending--;
            if (this.pending > 0) {
                return;
            }
        }

        this.scheduleRelease();
    }

    /** Deletes the terrain worlds once nothing has needed them for 5 seconds (and the arena pack is done). */
    private void scheduleRelease() {
        Tasks.cancel(this.idleTask);
        this.idleTask = Tasks.later(100L, () -> {
            synchronized (this) {
                if (this.pending > 0) {
                    return;
                }
            }

            if (this.plugin.packRunning()) {
                // the arena pack makes its maps one after another: keep the generated terrain for the next one
                // instead of deleting it and generating it all again
                this.scheduleRelease();
                return;
            }

            this.releaseSources();
        });
    }

    public synchronized boolean busy() {
        return this.pending > 0;
    }

    /** Unloads and deletes every temporary terrain world (also called on disable). */
    public void releaseSources() {
        if (Cfg.bool(Opt.VANILLA_KEEP_SOURCE)) {
            return;
        }

        for (World w : new ArrayList<>(this.sources.values())) {
            File folder = w.getWorldFolder();
            if (Bukkit.unloadWorld(w, false)) {
                deleteLater(folder, 0);
            } else {
                this.plugin.getLogger().warning("Could not unload the temporary terrain world " + w.getName() + "; it will be removed on the next start.");
            }
        }

        this.sources.clear();
    }

    /** Removes leftover terrain folders from a crash or an old run (startup, before anything is loaded). */
    public static void cleanLeftovers() {
        File[] dirs = Bukkit.getWorldContainer().listFiles((d, n) -> isSourceWorld(n));
        if (dirs != null) {
            for (File d : dirs) {
                if (Bukkit.getWorld(d.getName()) == null) {
                    deleteTree(d.toPath());
                }
            }
        }
    }

    private static void deleteLater(File folder, int attempt) {
        Tasks.async(() -> {
            if (!deleteTree(folder.toPath()) && attempt < 5) {
                Tasks.later(40L, () -> deleteLater(folder, attempt + 1));
            }
        });
    }

    private static boolean deleteTree(Path root) {
        if (!Files.exists(root)) {
            return true;
        } else {
            try (var walk = Files.walk(root)) {
                List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();

                for (Path p : all) {
                    Files.deleteIfExists(p);
                }

                return true;
            } catch (IOException | RuntimeException var6) {
                return false;
            }
        }
    }

    private World source(VanillaTerrain.Dim d) {
        long seed = seed();
        World w = this.sources.get(d);
        if (w != null && Bukkit.getWorld(w.getUID()) != null && w.getSeed() == seed) {
            return w;
        } else {
            String name = SOURCE_PREFIX + d.suffix;
            World old = Bukkit.getWorld(name);
            if (old != null) {
                Bukkit.unloadWorld(old, false);
            }

            deleteTree(new File(Bukkit.getWorldContainer(), name).toPath());
            boolean structures = Cfg.bool(Opt.VANILLA_STRUCTURES);
            long started = System.currentTimeMillis();
            w = new WorldCreator(name)
                .seed(seed)
                .environment(d.env)
                .type(WorldType.NORMAL)
                .generateStructures(structures)
                .generator(new VanillaTerrain.SourceGenerator(structures))
                .keepSpawnLoaded(TriState.FALSE)
                .createWorld();
            if (w == null) {
                throw new IllegalStateException("Could not create the terrain world " + name);
            } else if (w.getSeed() != seed) {
                Bukkit.unloadWorld(w, false);
                throw new IllegalStateException("Terrain world " + name + " came up with the wrong seed");
            } else {
                w.setAutoSave(false);
                w.setGameRule(GameRules.SPAWN_MOBS, false);
                w.setGameRule(GameRules.SPAWN_MONSTERS, false);
                w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
                w.setGameRule(GameRules.ADVANCE_TIME, false);
                w.setGameRule(GameRules.ADVANCE_WEATHER, false);
                this.sources.put(d, w);
                this.plugin.getLogger().info("Vanilla terrain: opened " + name + " (seed " + seed + ") in " + (System.currentTimeMillis() - started) + " ms.");
                return w;
            }
        }
    }

    // ------------------------------------------------------------------ the job

    private CompletableFuture<VanillaTerrain.Cut> run(
        ArenaTheme theme, VanillaTerrain.Spec spec, int size, int ox, int oz, boolean ffa, boolean crystal, int depth, int buildHeight, VanillaTerrain.Site hint
    ) {
        long seed = seed();
        World w = this.source(spec.dim);
        long started = System.currentTimeMillis();
        CompletableFuture<List<VanillaTerrain.Site>> candidates;
        if (hint != null && hint.dim == spec.dim && hint.seed == seed && hint.size == size) {
            candidates = CompletableFuture.completedFuture(List.of(hint));
        } else if (spec.mainIsland) {
            candidates = CompletableFuture.completedFuture(List.of(new VanillaTerrain.Site(spec.dim, seed, -size / 2, -size / 2, size)));
        } else {
            BiomeProvider provider = w.vanillaBiomeProvider();
            List<int[]> avoid = this.usedByOthers(theme, spec.dim, seed);
            candidates = CompletableFuture.supplyAsync(() -> search(w, provider, spec, size, seed, avoid));
        }

        return candidates.thenCompose(list -> {
            if (list.isEmpty()) {
                throw new IllegalStateException("No " + theme.display + " terrain found near spawn of seed " + seed + " - try another arena.vanilla-seed");
            } else {
                return this.pickBest(w, theme, spec, list, 0, null, size, ffa, crystal, depth, buildHeight);
            }
        }).thenApplyAsync(best -> {
            VanillaTerrain.Cut cut = cutOut(best, spec, ox, oz, ffa, crystal, depth, buildHeight, w.getMaxHeight());
            this.plugin
                .getLogger()
                .info(
                    String.format(
                        Locale.ROOT,
                        "Vanilla terrain: %s%s at %d,%d (%dx%d) - %s, cut in %.1fs",
                        theme.display,
                        crystal ? " (deep)" : "",
                        best.site.x0,
                        best.site.z0,
                        size,
                        size,
                        best.report(),
                        (System.currentTimeMillis() - started) / 1000.0
                    )
                );
            return cut;
        });
    }

    /** Loads and scores the candidates one after another; stops early on a great site, otherwise keeps the best. */
    private CompletableFuture<VanillaTerrain.Analysis> pickBest(
        World w,
        ArenaTheme theme,
        VanillaTerrain.Spec spec,
        List<VanillaTerrain.Site> list,
        int index,
        VanillaTerrain.Analysis best,
        int size,
        boolean ffa,
        boolean crystal,
        int depth,
        int buildHeight
    ) {
        // four candidates, or up to eight while none of them is acceptable (frozen lakes, steep ground...)
        if (index >= list.size() || index >= 4 && best != null && best.acceptable || index >= 8) {
            if (best == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("No usable " + theme.display + " site"));
            } else {
                return CompletableFuture.completedFuture(best);
            }
        } else {
            VanillaTerrain.Site site = list.get(index);
            int pad = 1;
            Cuboid area = new Cuboid(site.x0 - pad, w.getMinHeight(), site.z0 - pad, site.x0 + size - 1 + pad, w.getMaxHeight() - 1, site.z0 + size - 1 + pad);
            return RegionSnapshot.loadSnapshots(w, area).thenApplyAsync(snaps -> analyse(site, spec, snaps, w.getMinHeight(), w.getMaxHeight(), ffa)).thenCompose(a -> {
                VanillaTerrain.Analysis keep = best == null || a.score > best.score ? a : best;
                if (keep != a) {
                    a.snaps = null;
                } else if (best != null) {
                    best.snaps = null;
                }

                return keep.great() ? CompletableFuture.completedFuture(keep) : this.pickBest(w, theme, spec, list, index + 1, keep, size, ffa, crystal, depth, buildHeight);
            });
        }
    }

    /**
     * Sites (x0, z0, size) already cut for other maps, so two maps never show the same terrain. The deep crystal
     * variant of a map is the same theme and may share its site (it plays in another pool).
     */
    private List<int[]> usedByOthers(ArenaTheme theme, VanillaTerrain.Dim dim, long seed) {
        List<int[]> out = new ArrayList<>();
        List<Object[]> all = new ArrayList<>();

        // only maps cut by this version of the search: an outdated one is about to move anyway
        for (Arena a : this.plugin.arenas().all()) {
            if (sameVersion(a.genSignature)) {
                all.add(new Object[]{a.theme, a.site});
            }
        }

        for (net.vortexrealm.ffa.FfaArena f : this.plugin.ffa().all()) {
            if (sameVersion(f.genSignature)) {
                all.add(new Object[]{f.theme, f.site});
            }
        }

        for (Object[] o : all) {
            VanillaTerrain.Site s = o[1] instanceof String str ? VanillaTerrain.Site.parse(str) : null;
            if (o[0] != theme && s != null && s.dim() == dim && s.seed() == seed) {
                out.add(new int[]{s.x0(), s.z0(), s.size()});
            }
        }

        return out;
    }

    private static boolean overlaps(List<int[]> avoid, int x, int z, int size) {
        for (int[] a : avoid) {
            if (x < a[0] + a[2] + 32 && a[0] < x + size + 32 && z < a[1] + a[2] + 32 && a[1] < z + size + 32) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------ biome search

    /**
     * Sites where the biome covers most of the square. Rare, patchy biomes (cherry groves, pale gardens) seldom fill a
     * whole map, so the search accepts less of the biome (70%, then 50%, then 35%) before giving up.
     */
    private static List<VanillaTerrain.Site> search(World info, BiomeProvider provider, VanillaTerrain.Spec spec, int size, long seed, List<int[]> avoid) {
        for (double min : new double[]{0.7, 0.5, 0.35}) {
            List<VanillaTerrain.Site> out = search(info, provider, spec, size, seed, avoid, min);
            if (!out.isEmpty()) {
                return out;
            }
        }

        return List.of();
    }

    private static List<VanillaTerrain.Site> search(
        World info, BiomeProvider provider, VanillaTerrain.Spec spec, int size, long seed, List<int[]> avoid, double minPure
    ) {
        int step = 48;
        int radius = spec.dim == VanillaTerrain.Dim.END ? 6000 : 7000;
        int minRadius = spec.dim == VanillaTerrain.Dim.END ? 1300 : 0;
        int n = radius * 2 / step + 1;
        byte[] grid = new byte[n * n];

        for (int i = 0; i < n; i++) {
            int x = -radius + i * step;

            for (int j = 0; j < n; j++) {
                int z = -radius + j * step;
                if (minRadius > 0 && (long)x * x + (long)z * z < (long)minRadius * minRadius) {
                    grid[i * n + j] = 2;
                } else {
                    Biome b = provider.getBiome(info, x, spec.sampleY, z);
                    grid[i * n + j] = (byte)(spec.biomes.contains(b) ? 1 : (spec.isWater(b) ? 2 : 0));
                }
            }
        }

        int win = Math.max(2, (size + step - 1) / step);
        int[] good = prefix(grid, n, (byte)1);
        int[] bad = prefix(grid, n, (byte)2);
        List<double[]> scored = new ArrayList<>();

        for (int i = 0; i + win <= n; i++) {
            for (int j = 0; j + win <= n; j++) {
                double cells = win * win;
                double pure = sum(good, n, i, j, win) / cells;
                if (!(pure < minPure)) {
                    double water = sum(bad, n, i, j, win) / cells;
                    int cx = -radius + i * step + size / 2;
                    int cz = -radius + j * step + size / 2;
                    double dist = Math.sqrt((double)cx * cx + (double)cz * cz) / radius;
                    scored.add(new double[]{pure - water * 1.5 - dist * 0.05, -radius + i * step, -radius + j * step});
                }
            }
        }

        scored.sort((a, b) -> Double.compare(b[0], a[0]));
        List<VanillaTerrain.Site> out = new ArrayList<>();

        for (double[] s : scored) {
            int x = (int)s[1];
            int z = (int)s[2];
            boolean far = true;

            for (VanillaTerrain.Site o : out) {
                if (Math.abs(o.x0 - x) < size * 2 && Math.abs(o.z0 - z) < size * 2) {
                    far = false;
                    break;
                }
            }

            if (far && !overlaps(avoid, x, z, size)) {
                VanillaTerrain.Site site = refine(info, provider, spec, size, seed, x, z);
                if (!overlaps(avoid, site.x0(), site.z0(), size)) {
                    out.add(site);
                    if (out.size() >= 8) {
                        break;
                    }
                }
            }
        }

        return out;
    }

    /** Slides the square around the coarse hit on an 8-block grid to get the purest position. */
    private static VanillaTerrain.Site refine(World info, BiomeProvider provider, VanillaTerrain.Spec spec, int size, long seed, int x0, int z0) {
        int step = 8;
        int margin = 64;
        int n = (size + margin * 2) / step;
        byte[] grid = new byte[n * n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Biome b = provider.getBiome(info, x0 - margin + i * step, spec.sampleY, z0 - margin + j * step);
                grid[i * n + j] = (byte)(spec.biomes.contains(b) ? 1 : (spec.isWater(b) ? 2 : 0));
            }
        }

        int win = size / step;
        int[] good = prefix(grid, n, (byte)1);
        int[] bad = prefix(grid, n, (byte)2);
        double bestScore = -1.0E9;
        int bi = margin / step;
        int bj = margin / step;

        for (int i = 0; i + win <= n; i++) {
            for (int j = 0; j + win <= n; j++) {
                double s = sum(good, n, i, j, win) - sum(bad, n, i, j, win) * 1.5;
                if (s > bestScore) {
                    bestScore = s;
                    bi = i;
                    bj = j;
                }
            }
        }

        return new VanillaTerrain.Site(spec.dim, seed, x0 - margin + bi * step, z0 - margin + bj * step, size);
    }

    private static int[] prefix(byte[] grid, int n, byte want) {
        int[] p = new int[(n + 1) * (n + 1)];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                p[(i + 1) * (n + 1) + j + 1] = (grid[i * n + j] == want ? 1 : 0) + p[i * (n + 1) + j + 1] + p[(i + 1) * (n + 1) + j] - p[i * (n + 1) + j];
            }
        }

        return p;
    }

    private static int sum(int[] p, int n, int i, int j, int w) {
        int m = n + 1;
        return p[(i + w) * m + j + w] - p[i * m + j + w] - p[(i + w) * m + j] + p[i * m + j];
    }

    // ------------------------------------------------------------------ analysis

    private static final byte LAND = 0;
    private static final byte WATER = 1;
    private static final byte LAVA = 2;
    private static final byte VOID = 3;

    static VanillaTerrain.Analysis analyse(VanillaTerrain.Site site, VanillaTerrain.Spec spec, Map<Long, ChunkSnapshot> snaps, int minY, int maxY, boolean ffa) {
        int s = site.size;
        VanillaTerrain.Analysis a = new VanillaTerrain.Analysis(site, s);
        a.snaps = snaps;
        a.minY = minY;
        a.maxY = maxY;
        boolean end = spec.dim == VanillaTerrain.Dim.END;
        boolean nether = spec.dim == VanillaTerrain.Dim.NETHER;
        int pure = 0;
        int sampled = 0;

        for (int x = 0; x < s; x++) {
            for (int z = 0; z < s; z++) {
                int wx = site.x0 + x;
                int wz = site.z0 + z;
                ChunkSnapshot cs = snaps.get(RegionSnapshot.key(wx >> 4, wz >> 4));
                int lx = wx & 15;
                int lz = wz & 15;
                int i = x * s + z;
                int start = nether ? 126 : Math.min(maxY - 1, cs.getHighestBlockYAt(lx, lz) + 4);
                if (nether) {
                    // climb down through the bedrock/netherrack roof to the first air gap
                    while (start > minY && !cs.getBlockType(lx, start, lz).isAir()) {
                        start--;
                    }
                }

                int y = start;

                while (y > minY && cs.getBlockType(lx, y, lz).isAir()) {
                    y--;
                }

                a.top[i] = y;
                int ground = Integer.MIN_VALUE;
                int vegLow = Integer.MAX_VALUE;
                byte surf = VOID;

                for (; y > minY; y--) {
                    Material m = cs.getBlockType(lx, y, lz);
                    int o = m.ordinal();
                    if (FLUID[o]) {
                        if (surf == VOID) {
                            surf = m == Material.LAVA ? LAVA : WATER;
                        }
                    } else if (VEG[o] || end && (m == Material.OBSIDIAN || m == Material.IRON_BARS || m == Material.BEDROCK || m == Material.FIRE)) {
                        vegLow = y;
                    } else if (GROUND[o]) {
                        ground = y;
                        if (surf == VOID) {
                            surf = LAND;
                        }
                        break;
                    }
                }

                a.ground[i] = ground;
                a.surf[i] = ground == Integer.MIN_VALUE ? VOID : surf;
                a.vegLow[i] = vegLow;
                if (vegLow != Integer.MAX_VALUE) {
                    a.trees++;
                }

                if ((x & 3) == 0 && (z & 3) == 0 && ground != Integer.MIN_VALUE) {
                    sampled++;
                    if (spec.biomes.contains(cs.getBiome(lx, Math.min(maxY - 1, ground + 1), lz))) {
                        pure++;
                    }
                }
            }
        }

        a.purity = sampled == 0 ? 0.0 : (double)pure / sampled;
        score(a, spec, ffa);
        return a;
    }

    private static void score(VanillaTerrain.Analysis a, VanillaTerrain.Spec spec, boolean ffa) {
        int s = a.size;
        int margin = Math.max(6, s / 16);
        int inner = 0;
        int water = 0;
        int lava = 0;
        int land = 0;
        int rough = 0;
        List<Integer> heights = new ArrayList<>();
        int r = 4;
        long[] sum = new long[(s + 1) * (s + 1)];
        int[] cnt = new int[(s + 1) * (s + 1)];

        for (int x = 0; x < s; x++) {
            for (int z = 0; z < s; z++) {
                int i = x * s + z;
                boolean ok = a.surf[i] == LAND || a.surf[i] == WATER;
                int g = ok ? a.ground[i] : 0;
                int k = (x + 1) * (s + 1) + z + 1;
                sum[k] = g + sum[x * (s + 1) + z + 1] + sum[(x + 1) * (s + 1) + z] - sum[x * (s + 1) + z];
                cnt[k] = (ok ? 1 : 0) + cnt[x * (s + 1) + z + 1] + cnt[(x + 1) * (s + 1) + z] - cnt[x * (s + 1) + z];
            }
        }

        for (int x = margin; x < s - margin; x++) {
            for (int z = margin; z < s - margin; z++) {
                int i = x * s + z;
                inner++;
                byte t = a.surf[i];
                if (t == WATER) {
                    water++;
                } else if (t == LAVA) {
                    lava++;
                } else if (t == LAND) {
                    land++;
                }

                if (t == LAND || t == WATER) {
                    heights.add(a.ground[i]);
                    int x0 = Math.max(0, x - r);
                    int z0 = Math.max(0, z - r);
                    int x1 = Math.min(s, x + r + 1);
                    int z1 = Math.min(s, z + r + 1);
                    int c = cnt[x1 * (s + 1) + z1] - cnt[x0 * (s + 1) + z1] - cnt[x1 * (s + 1) + z0] + cnt[x0 * (s + 1) + z0];
                    long sm = sum[x1 * (s + 1) + z1] - sum[x0 * (s + 1) + z1] - sum[x1 * (s + 1) + z0] + sum[x0 * (s + 1) + z0];
                    if (c > 0 && Math.abs(a.ground[i] - (double)sm / c) > 4.0) {
                        rough++;
                    }
                }
            }
        }

        heights.sort(Integer::compare);
        int relief = heights.isEmpty() ? 0 : heights.get((int)(heights.size() * 0.95)) - heights.get((int)(heights.size() * 0.05));
        a.water = inner == 0 ? 1.0 : (double)water / inner;
        a.lava = lava;
        a.land = inner == 0 ? 0.0 : (double)land / inner;
        a.relief = relief;
        a.rough = inner == 0 ? 1.0 : (double)rough / inner;
        a.treeFrac = (double)a.trees / (s * s);
        a.groundMin = heights.isEmpty() ? 64 : heights.get(Math.min(heights.size() - 1, (int)(heights.size() * 0.01)));
        int c = s / 2;
        int dist = Math.max(14, Math.min(38, (int)Math.round(s * 0.14)));
        int[] p1 = spawnNear(a, c - dist, c, Integer.MIN_VALUE, margin, -1);
        int[] p2 = p1 == null ? null : spawnNear(a, c + dist, c, a.ground[p1[0] * s + p1[1]], margin, -1);
        a.spawn1 = p1;
        a.spawn2 = p2;
        if (ffa) {
            a.ffa = new ArrayList<>();
            double radius = s * 0.3;

            for (int k = 0; k < 10; k++) {
                double ang = Math.PI * 2 * k / 10.0;
                int px = (int)Math.round(c + Math.cos(ang) * radius);
                int pz = (int)Math.round(c + Math.sin(ang) * radius);
                int[] p = spawnNear(a, px, pz, Integer.MIN_VALUE, margin, -1);
                if (p != null) {
                    a.ffa.add(p);
                }
            }
        }

        double score = 4.0 * a.purity;
        if (spec.dim == VanillaTerrain.Dim.END) {
            score += 3.0 * a.land;
        }

        score -= 8.0 * Math.max(0.0, a.water - spec.maxWater);
        score -= lava > 0 ? 6.0 + Math.min(4.0, lava / 50.0) : 0.0;
        // 1.4.0: the map is the biome as it is - hills, forests and all. Only sheer cliffs that would break the
        // fight count a little; trees and height differences no longer push the pick toward bare, flat ground.
        score -= 0.5 * a.rough;

        if (p1 == null || p2 == null) {
            score -= 3.0;
        } else {
            score -= Math.abs(a.ground[p1[0] * s + p1[1]] - a.ground[p2[0] * s + p2[1]]) / 6.0;
        }

        a.score = score;
        a.acceptable = a.purity >= spec.minPurity && lava == 0 && p1 != null && p2 != null && a.water <= spec.maxWater * 1.5 + 0.01;
    }

    /** The best safe, flat, open column near (nx, nz): closest to the nominal spot and to {@code refY}. */
    private static int[] spawnNear(VanillaTerrain.Analysis a, int nx, int nz, int refY, int margin, int unused) {
        int s = a.size;
        int[] best = null;
        double bestCost = Double.MAX_VALUE;

        for (int radius = 20; radius <= 40 && best == null; radius += 20) {
            for (int x = Math.max(margin, nx - radius); x <= Math.min(s - 1 - margin, nx + radius); x++) {
                for (int z = Math.max(margin, nz - radius); z <= Math.min(s - 1 - margin, nz + radius); z++) {
                    if (safe(a, x, z)) {
                        int g = a.ground[x * s + z];
                        double d = Math.hypot(x - nx, z - nz);
                        double cost = d + (refY == Integer.MIN_VALUE ? 0.0 : Math.abs(g - refY) * 1.5);
                        if (cost < bestCost) {
                            bestCost = cost;
                            best = new int[]{x, z};
                        }
                    }
                }
            }
        }

        return best;
    }

    private static boolean safe(VanillaTerrain.Analysis a, int x, int z) {
        int s = a.size;
        int i = x * s + z;
        if (a.surf[i] != LAND) {
            return false;
        } else {
            int g = a.ground[i];

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int ax = x + dx;
                    int az = z + dz;
                    if (ax < 0 || az < 0 || ax >= s || az >= s) {
                        return false;
                    }

                    int j = ax * s + az;
                    if (a.vegLow[j] <= g + 5) {
                        return false;
                    }

                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && (a.surf[j] != LAND || Math.abs(a.ground[j] - g) > 1)) {
                        return false;
                    }
                }
            }

            ChunkSnapshot cs = a.snaps.get(RegionSnapshot.key(a.site.x0 + x >> 4, a.site.z0 + z >> 4));
            int lx = a.site.x0 + x & 15;
            int lz = a.site.z0 + z & 15;
            Material on = cs.getBlockType(lx, g, lz);
            if (HAZARD[on.ordinal()]) {
                return false;
            } else {
                for (int dy = 1; dy <= 3; dy++) {
                    if (g + dy >= a.maxY) {
                        break;
                    }

                    Material m = cs.getBlockType(lx, g + dy, lz);
                    if (!m.isAir() && (m.isSolid() || HAZARD[m.ordinal()])) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    // ------------------------------------------------------------------ the cut

    /** What lies under a surface block: dirt under grass, podzol, mycelium or snow; the same block under sand or stone. */
    /** The stone an ore block is set in. */
    private static BlockData oreRock(Material m, BlockData stone, BlockData deepslate, BlockData netherrack) {
        String n = m.name();
        return n.startsWith("DEEPSLATE_") ? deepslate : (n.startsWith("NETHER_") || m == Material.ANCIENT_DEBRIS ? netherrack : stone);
    }

    private static BlockData subsoil(BlockData top, BlockData dirt) {
        Material m = top.getMaterial();
        return m == Material.GRASS_BLOCK
                || m == Material.PODZOL
                || m == Material.MYCELIUM
                || m == Material.DIRT_PATH
                || m == Material.SNOW_BLOCK
                || m == Material.MOSS_BLOCK
                || m == Material.FARMLAND
            ? dirt
            : top;
    }

    private static BlockData blockAt(VanillaTerrain.Analysis a, int x, int y, int z) {
        int wx = a.site.x0 + x;
        int wz = a.site.z0 + z;
        return a.snaps.get(RegionSnapshot.key(wx >> 4, wz >> 4)).getBlockData(wx & 15, y, wz & 15);
    }

    /**
     * Narrow trenches in the land (ravines, cave mouths): a land column with a wall at least five blocks higher on
     * both sides within four blocks, along x or along z. A wall must go on two blocks to either side, so the gaps
     * between thin pillars (badlands hoodoos) are left alone. Returns per column the height to raise it to (just under
     * the lower rim) or {@link Integer#MIN_VALUE}; a few passes catch the edges of a trench once its middle is filled.
     */
    private static int[] trenches(VanillaTerrain.Analysis a) {
        int s = a.size;
        int[] g = new int[s * s];

        for (int i = 0; i < s * s; i++) {
            g[i] = a.surf[i] == LAND ? a.ground[i] : Integer.MIN_VALUE;
        }

        int[] raise = new int[s * s];
        Arrays.fill(raise, Integer.MIN_VALUE);

        for (int pass = 0; pass < 3; pass++) {
            int[] next = g.clone();
            boolean changed = false;

            for (int x = 0; x < s; x++) {
                for (int z = 0; z < s; z++) {
                    int i = x * s + z;
                    int h = g[i];
                    if (h != Integer.MIN_VALUE) {
                        int rimX = rim(g, s, x, z, 1, 0, h);
                        int rimZ = rim(g, s, x, z, 0, 1, h);
                        int rim = rimX != Integer.MIN_VALUE && rimZ != Integer.MIN_VALUE ? Math.min(rimX, rimZ) : Math.max(rimX, rimZ);
                        if (rim != Integer.MIN_VALUE && rim - 1 > h) {
                            next[i] = rim - 1;
                            raise[i] = rim - 1;
                            changed = true;
                        }
                    }
                }
            }

            g = next;
            if (!changed) {
                break;
            }
        }

        return raise;
    }

    /** The lower of the two walls around (x, z) along (dx, dz), or MIN_VALUE when one side has none. */
    private static int rim(int[] g, int s, int x, int z, int dx, int dz, int h) {
        int one = wall(g, s, x, z, -dx, -dz, h);
        if (one == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        } else {
            int two = wall(g, s, x, z, dx, dz, h);
            return two == Integer.MIN_VALUE ? Integer.MIN_VALUE : Math.min(one, two);
        }
    }

    private static int wall(int[] g, int s, int x, int z, int dx, int dz, int h) {
        for (int k = 1; k <= 4; k++) {
            int wx = x + dx * k;
            int wz = z + dz * k;
            if (wx < 0 || wz < 0 || wx >= s || wz >= s) {
                return Integer.MIN_VALUE;
            }

            int w = g[wx * s + wz];
            if (w != Integer.MIN_VALUE && w >= h + 5) {
                // across the trench (perpendicular to dx, dz) the wall goes on: not a thin pillar
                return high(g, s, wx + dz * 2, wz + dx * 2, h + 5) && high(g, s, wx - dz * 2, wz - dx * 2, h + 5) ? w : Integer.MIN_VALUE;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static boolean high(int[] g, int s, int x, int z, int min) {
        return x >= 0 && z >= 0 && x < s && z < s && g[x * s + z] != Integer.MIN_VALUE && g[x * s + z] >= min;
    }

    /** The top block of a filled trench: the most common ground block around it at rim level. */
    private static BlockData trenchTop(VanillaTerrain.Analysis a, int x, int z, int target) {
        int s = a.size;
        Map<BlockData, Integer> count = new HashMap<>();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx >= 0 && nz >= 0 && nx < s && nz < s) {
                    int j = nx * s + nz;
                    if (a.surf[j] == LAND && a.ground[j] >= target - 1 && a.ground[j] <= target + 3) {
                        BlockData d = blockAt(a, nx, a.ground[j], nz);
                        if (GROUND[d.getMaterial().ordinal()]) {
                            count.merge(d, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        BlockData best = null;
        int most = 0;

        for (Map.Entry<BlockData, Integer> e : count.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }

        return best != null ? best : blockAt(a, x, a.ground[x * s + z], z);
    }

    static VanillaTerrain.Cut cutOut(VanillaTerrain.Analysis a, VanillaTerrain.Spec spec, int ox, int oz, boolean ffa, boolean crystal, int depth, int buildHeight, int worldMax) {
        int s = a.size;
        boolean end = spec.dim == VanillaTerrain.Dim.END;
        boolean nether = spec.dim == VanillaTerrain.Dim.NETHER;
        boolean fill = Cfg.bool(Opt.VANILLA_FILL_CAVES) && !end;
        int lowestGround = Integer.MAX_VALUE;
        int highestTop = Integer.MIN_VALUE;
        int lowestSolid = Integer.MAX_VALUE;

        for (int i = 0; i < s * s; i++) {
            if (a.ground[i] != Integer.MIN_VALUE) {
                lowestGround = Math.min(lowestGround, a.ground[i]);
                highestTop = Math.max(highestTop, a.top[i]);
            }
        }

        if (lowestGround == Integer.MAX_VALUE) {
            throw new IllegalStateException("The site has no ground");
        } else {
            int floorY;
            if (end) {
                // the island's own underside: scan the lowest end stone of the whole cut
                for (int x = 0; x < s; x += 1) {
                    for (int z = 0; z < s; z += 1) {
                        int i = x * s + z;
                        if (a.ground[i] != Integer.MIN_VALUE) {
                            ChunkSnapshot cs = a.snaps.get(RegionSnapshot.key(a.site.x0 + x >> 4, a.site.z0 + z >> 4));
                            int lx = a.site.x0 + x & 15;
                            int lz = a.site.z0 + z & 15;
                            int y = a.ground[i];

                            while (y > a.minY && !cs.getBlockType(lx, y - 1, lz).isAir()) {
                                y--;
                            }

                            lowestSolid = Math.min(lowestSolid, y);
                        }
                    }
                }

                floorY = Math.max(a.minY, lowestSolid - 1);
            } else {
                floorY = Math.max(a.minY + 1, Math.min(lowestGround, a.groundMin) - depth);
            }

            int spawnY = a.spawn1 == null ? lowestGround + 1 : Math.max(a.ground[a.spawn1[0] * s + a.spawn1[1]], a.ground[a.spawn2[0] * s + a.spawn2[1]]) + 1;
            int topY = nether ? 127 : Math.min(worldMax - 2, Math.max(highestTop + 2, spawnY + buildHeight + 2));
            int sy = topY - floorY + 1;
            RegionSnapshot.Builder b = new RegionSnapshot.Builder(ox, floorY, oz, s, sy, s);
            Map<BlockData, BlockData> fixed = new HashMap<>();
            BlockData air = Material.AIR.createBlockData();
            BlockData bedrock = Material.BEDROCK.createBlockData();
            BlockData stone = Material.STONE.createBlockData();
            BlockData deepslate = Material.DEEPSLATE.createBlockData();
            BlockData netherrack = Material.NETHERRACK.createBlockData();
            BlockData snowBlock = Material.SNOW_BLOCK.createBlockData();
            BlockData dirt = Material.DIRT.createBlockData();
            // cave mouths and ravines: land columns in a narrow trench are raised to just under its rim
            int[] raise = fill ? trenches(a) : new int[s * s];
            BlockData[] raiseTop = new BlockData[s * s];
            if (fill) {
                for (int i = 0; i < s * s; i++) {
                    if (raise[i] > a.ground[i]) {
                        raiseTop[i] = trenchTop(a, i / s, i % s, raise[i]);
                    }
                }
            }

            for (int x = 0; x < s; x++) {
                for (int z = 0; z < s; z++) {
                    int i = x * s + z;
                    int wx = a.site.x0 + x;
                    int wz = a.site.z0 + z;
                    ChunkSnapshot cs = a.snaps.get(RegionSnapshot.key(wx >> 4, wz >> 4));
                    int lx = wx & 15;
                    int lz = wz & 15;
                    int ground = a.ground[i];
                    BlockData rock = null;
                    BlockData above = null;
                    boolean surfaceSeen = false;
                    boolean frozen = false;
                    int iceY = Integer.MIN_VALUE;
                    int raised = raiseTop[i] != null ? raise[i] : Integer.MIN_VALUE;
                    int lowest = Integer.MIN_VALUE;
                    // the column's own rock (sandstone under a desert dune, stone under a meadow) for the fill
                    BlockData colRock = null;
                    if (fill && ground != Integer.MIN_VALUE) {
                        for (int y = ground; y >= Math.max(floorY, ground - 48) && colRock == null; y--) {
                            BlockData c = cs.getBlockData(lx, y, lz);
                            int co = c.getMaterial().ordinal();
                            if (ROCK[co] && !POCKET[co] && c.getMaterial() != Material.DIRT && c.getMaterial() != Material.COARSE_DIRT) {
                                colRock = c;
                            }
                        }
                    }

                    for (int y = topY; y >= floorY; y--) {
                        BlockData d = cs.getBlockData(lx, y, lz);
                        Material m = d.getMaterial();
                        int o = m.ordinal();
                        if (raised != Integer.MIN_VALUE && y <= raised && y > ground) {
                            // the trench is filled: its top block like the ground around it, under it the same body
                            d = y == raised ? raiseTop[i] : subsoil(raiseTop[i], dirt);
                            above = d;
                            b.set(x, y - floorY, z, d);
                            continue;
                        }

                        if (ROCK[o] && !POCKET[o] && y <= ground) {
                            rock = d;
                        }

                        if (!surfaceSeen && !m.isAir() && m != Material.SNOW) {
                            surfaceSeen = true;
                            // a frozen lake or river: slippery, and breaking it floods the fight
                            frozen = m == Material.ICE && !end && !nether;
                            iceY = y;
                        }

                        if (frozen) {
                            // frozen lakes and rivers stay as they are in the vanilla world
                            frozen = false;
                        }

                        if (!end && (y == floorY || y <= floorY + 3 && bedrockAt(wx, y - floorY, wz))) {
                            // every map ends on bedrock, the deep crystal ones too: digging down never reaches the void
                            d = bedrock;
                        } else if (fill && ground != Integer.MIN_VALUE && y <= ground - 3 && (m.isAir() || FLUID[o] || FILLABLE[o])) {
                            // no caves: the empty space (and cave water, lava, moss and dripleaves) underground becomes
                            // the rock around it. Ores, dirt and gravel pockets stay as vanilla has them.
                            d = rock != null ? rock : (colRock != null ? colRock : (nether ? netherrack : (y < 0 ? deepslate : stone)));
                        } else if (fill && ground != Integer.MIN_VALUE && y < ground && (m.isAir() || FLUID[o] || FILLABLE[o])) {
                            // a hollow right under the surface takes the block above it (dirt under grass, sand under sand)
                            d = above != null ? above : (rock != null ? rock : dirt);
                        } else if (end && (m == Material.FIRE || m == Material.END_GATEWAY || !spec.mainIsland && m == Material.BEDROCK)) {
                            d = air;
                        } else if (d instanceof Leaves) {
                            d = fixed.computeIfAbsent(d, k -> {
                                Leaves l = (Leaves)k.clone();
                                l.setPersistent(true);
                                return l;
                            });
                        }

                        if (y <= ground && !d.getMaterial().isAir() && !FLUID[d.getMaterial().ordinal()]) {
                            above = subsoil(d, dirt);
                        }

                        if (end && !d.getMaterial().isAir()) {
                            lowest = y;
                        }

                        b.set(x, y - floorY, z, d);
                    }

                    if (end && lowest != Integer.MIN_VALUE) {
                        // End islands: the underside is bedrock, so digging through an island stops there
                        b.set(x, lowest - floorY, z, bedrock);
                    }
                }
            }

            for (int i = 0; i < s * s; i++) {
                if (raiseTop[i] != null) {
                    // spawns and the centre stand on the new surface of a filled trench
                    a.ground[i] = raise[i];
                }
            }

            for (int cy = 0; cy < b.by(); cy++) {
                int y = Math.min(floorY + cy * 4 + 2, topY);

                for (int cz = 0; cz < b.bz(); cz++) {
                    int z = Math.min(cz * 4 + 2, s - 1);

                    for (int cx = 0; cx < b.bx(); cx++) {
                        int x = Math.min(cx * 4 + 2, s - 1);
                        int wx = a.site.x0 + x;
                        int wz = a.site.z0 + z;
                        ChunkSnapshot cs = a.snaps.get(RegionSnapshot.key(wx >> 4, wz >> 4));
                        b.biome(cx, cy, cz, cs.getBiome(wx & 15, y, wz & 15));
                    }
                }
            }

            RegionSnapshot snap = b.build();
            Cuboid bounds = new Cuboid(ox, floorY, oz, ox + s - 1, topY, oz + s - 1);
            int c = s / 2;
            int[] p1 = a.spawn1 != null ? a.spawn1 : new int[]{c - 20, c};
            int[] p2 = a.spawn2 != null ? a.spawn2 : new int[]{c + 20, c};
            double yaw1 = Math.toDegrees(Math.atan2(-(p2[0] - p1[0]), p2[1] - p1[1]));
            Pos s1 = new Pos(ox + p1[0] + 0.5, ground(a, p1) + 1, oz + p1[1] + 0.5, (float)yaw1, 0.0F);
            Pos s2 = new Pos(ox + p2[0] + 0.5, ground(a, p2) + 1, oz + p2[1] + 0.5, (float)(yaw1 + 180.0), 0.0F);
            int[] mid = spawnNear(a, c, c, Integer.MIN_VALUE, 2, -1);
            int midY = mid != null ? ground(a, mid) : Math.max(ground(a, p1), ground(a, p2));
            Pos center = new Pos(ox + c + 0.5, midY + 3, oz + c + 0.5, 0.0F, 20.0F);
            List<Pos> ffaSpawns = new ArrayList<>();
            if (ffa && a.ffa != null) {
                for (int[] p : a.ffa) {
                    double yaw = Math.toDegrees(Math.atan2(-(c - p[0]), c - p[1]));
                    ffaSpawns.add(new Pos(ox + p[0] + 0.5, ground(a, p) + 1, oz + p[1] + 0.5, (float)yaw, 0.0F));
                }

                if (ffaSpawns.size() < 2) {
                    ffaSpawns.add(s1);
                    ffaSpawns.add(s2);
                }
            }

            a.snaps = null;
            return new VanillaTerrain.Cut(snap, bounds, s1, s2, center, ffaSpawns, a.site, a.report());
        }
    }

    private static int ground(VanillaTerrain.Analysis a, int[] p) {
        int g = a.ground[p[0] * a.size + p[1]];
        return g == Integer.MIN_VALUE ? 64 : g;
    }

    private static boolean bedrockAt(int x, int above, int z) {
        if (above <= 0) {
            return true;
        } else {
            double chance = above == 1 ? 0.7 : (above == 2 ? 0.4 : 0.15);
            long h = x * 73856093L ^ above * 19349663L ^ z * 83492791L;
            h ^= h >>> 13;
            h *= 25214903917L;
            return Math.floorMod(h ^ h >>> 17, 1000) < chance * 1000.0;
        }
    }

    // ------------------------------------------------------------------ material classes

    private static synchronized void classify() {
        if (!classified) {
            classified = true;
            Set<Material> veg = new java.util.HashSet<>();
            veg.addAll(Tag.LEAVES.getValues());
            veg.addAll(Tag.LOGS.getValues());
            veg.addAll(Tag.WART_BLOCKS.getValues());
            veg.addAll(
                List.of(
                    Material.SHROOMLIGHT,
                    Material.BROWN_MUSHROOM_BLOCK,
                    Material.RED_MUSHROOM_BLOCK,
                    Material.MUSHROOM_STEM,
                    Material.BAMBOO,
                    Material.CACTUS,
                    Material.SUGAR_CANE,
                    Material.CHORUS_PLANT,
                    Material.CHORUS_FLOWER,
                    Material.BEE_NEST,
                    Material.PUMPKIN,
                    Material.MELON,
                    Material.VINE,
                    Material.COCOA,
                    Material.BIG_DRIPLEAF,
                    Material.BIG_DRIPLEAF_STEM,
                    Material.AZALEA,
                    Material.FLOWERING_AZALEA,
                    Material.MANGROVE_ROOTS,
                    Material.MUDDY_MANGROVE_ROOTS,
                    Material.PALE_HANGING_MOSS,
                    Material.CREAKING_HEART,
                    Material.RESIN_CLUMP,
                    Material.HANGING_ROOTS,
                    Material.WEEPING_VINES,
                    Material.WEEPING_VINES_PLANT,
                    Material.TWISTING_VINES,
                    Material.TWISTING_VINES_PLANT,
                    Material.POINTED_DRIPSTONE
                )
            );

            for (Material m : Material.values()) {
                if (!m.isLegacy() && m.isBlock()) {
                    int o = m.ordinal();
                    VEG[o] = veg.contains(m);
                    FLUID[o] = m == Material.WATER
                        || m == Material.LAVA
                        || m == Material.BUBBLE_COLUMN
                        || m == Material.KELP
                        || m == Material.KELP_PLANT
                        || m == Material.SEAGRASS
                        || m == Material.TALL_SEAGRASS;
                    GROUND[o] = !VEG[o] && !FLUID[o] && m.isSolid() && m != Material.SNOW && !m.name().endsWith("_CARPET");
                    String n = m.name();
                    ROCK[o] = Tag.BASE_STONE_OVERWORLD.isTagged(m)
                        || Tag.BASE_STONE_NETHER.isTagged(m)
                        || n.endsWith("TERRACOTTA") && !n.contains("GLAZED")
                        || m == Material.SANDSTONE
                        || m == Material.RED_SANDSTONE
                        || m == Material.DIRT
                        || m == Material.COARSE_DIRT
                        || m == Material.CALCITE
                        || m == Material.END_STONE
                        || m == Material.SMOOTH_BASALT
                        || m == Material.PACKED_MUD
                        || m == Material.DRIPSTONE_BLOCK;
                    HAZARD[o] = m == Material.MAGMA_BLOCK
                        || m == Material.CACTUS
                        || m == Material.POWDER_SNOW
                        || m == Material.SWEET_BERRY_BUSH
                        || m == Material.WITHER_ROSE
                        || m == Material.FIRE
                        || m == Material.SOUL_FIRE
                        || m == Material.CAMPFIRE
                        || m == Material.POINTED_DRIPSTONE
                        || m == Material.LAVA
                        || m == Material.WATER
                        || m == Material.SCULK_SHRIEKER
                        || m == Material.SCULK_SENSOR;
                    POCKET[o] = m == Material.CALCITE || m == Material.SMOOTH_BASALT || m == Material.DRIPSTONE_BLOCK;
                    ORE[o] = n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
                    FILLABLE[o] = m == Material.SPAWNER
                        || m == Material.CHEST
                        || m == Material.TRAPPED_CHEST
                        || m == Material.SUSPICIOUS_SAND
                        || m == Material.SUSPICIOUS_GRAVEL
                        || m == Material.COBWEB
                        || m == Material.POINTED_DRIPSTONE
                        || m == Material.SCULK_SENSOR
                        || m == Material.SCULK_SHRIEKER
                        || m == Material.SCULK_CATALYST
                        || m == Material.GLOW_LICHEN
                        || m == Material.BUDDING_AMETHYST
                        || m == Material.AMETHYST_CLUSTER
                        || m == Material.LARGE_AMETHYST_BUD
                        || m == Material.MEDIUM_AMETHYST_BUD
                        || m == Material.SMALL_AMETHYST_BUD
                        || m == Material.HANGING_ROOTS
                        || m == Material.SPORE_BLOSSOM
                        || m == Material.CAVE_VINES
                        || m == Material.CAVE_VINES_PLANT
                        || m == Material.BIG_DRIPLEAF
                        || m == Material.BIG_DRIPLEAF_STEM
                        || m == Material.SMALL_DRIPLEAF
                        || m == Material.MOSS_CARPET
                        || m == Material.AZALEA
                        || m == Material.FLOWERING_AZALEA;
                }
            }
        }
    }

    // ------------------------------------------------------------------ types

    public static enum Dim {
        OVERWORLD("", Environment.NORMAL),
        NETHER("_nether", Environment.NETHER),
        END("_end", Environment.THE_END);

        final String suffix;
        final Environment env;

        private Dim(String suffix, Environment env) {
            this.suffix = suffix;
            this.env = env;
        }
    }

    /** What a good site of a vanilla theme looks like. */
    public static final class Spec {
        final VanillaTerrain.Dim dim;
        final Set<Biome> biomes;
        final int sampleY;
        final double minPurity;
        final double maxWater;
        final int maxRelief;
        final int minRelief;
        final double maxTrees;
        final boolean mainIsland;

        public Spec(VanillaTerrain.Dim dim, Set<Biome> biomes, int sampleY, double minPurity, double maxWater, int minRelief, int maxRelief, double maxTrees, boolean mainIsland) {
            this.dim = dim;
            this.biomes = biomes;
            this.sampleY = sampleY;
            this.minPurity = minPurity;
            this.maxWater = maxWater;
            this.minRelief = minRelief;
            this.maxRelief = maxRelief;
            this.maxTrees = maxTrees;
            this.mainIsland = mainIsland;
        }

        boolean isWater(Biome b) {
            if (this.dim != VanillaTerrain.Dim.OVERWORLD || this.biomes.contains(b)) {
                return false;
            } else {
                String k = b.getKey().getKey();
                return k.contains("ocean") || k.contains("river") || k.equals("beach") || k.contains("swamp");
            }
        }

        public static VanillaTerrain.Spec overworld(int sampleY, double maxWater, int minRelief, int maxRelief, double maxTrees, Biome... biomes) {
            return new VanillaTerrain.Spec(VanillaTerrain.Dim.OVERWORLD, Set.of(biomes), sampleY, 0.8, maxWater, minRelief, maxRelief, maxTrees, false);
        }
    }

    /** A square of the source world: enough to cut the same terrain again ("same layout" rebuilds). */
    public record Site(VanillaTerrain.Dim dim, long seed, int x0, int z0, int size) {
        public String serialize() {
            return this.dim.name() + ";" + this.seed + ";" + this.x0 + ";" + this.z0 + ";" + this.size;
        }

        public static VanillaTerrain.Site parse(String s) {
            if (s != null && !s.isBlank()) {
                try {
                    String[] p = s.split(";");
                    return new VanillaTerrain.Site(VanillaTerrain.Dim.valueOf(p[0]), Long.parseLong(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                } catch (RuntimeException var2) {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    public record Cut(RegionSnapshot snapshot, Cuboid bounds, Pos spawn1, Pos spawn2, Pos center, List<Pos> ffaSpawns, VanillaTerrain.Site site, String report) {
    }

    static final class Analysis {
        final VanillaTerrain.Site site;
        final int size;
        final int[] ground;
        final int[] top;
        final int[] vegLow;
        final byte[] surf;
        Map<Long, ChunkSnapshot> snaps;
        int minY;
        int maxY;
        int trees;
        double purity;
        double water;
        double land;
        double rough;
        double treeFrac;
        int lava;
        int relief;
        int groundMin;
        int[] spawn1;
        int[] spawn2;
        List<int[]> ffa;
        double score;
        boolean acceptable;

        Analysis(VanillaTerrain.Site site, int size) {
            this.site = site;
            this.size = size;
            this.ground = new int[size * size];
            this.top = new int[size * size];
            this.vegLow = new int[size * size];
            this.surf = new byte[size * size];
            Arrays.fill(this.ground, Integer.MIN_VALUE);
        }

        boolean great() {
            return this.acceptable && this.purity >= 0.95 && this.rough < 0.05 && this.water < 0.03;
        }

        String report() {
            return String.format(
                Locale.ROOT,
                "biome %.0f%%, water %.0f%%, relief %d, rough %.0f%%, trees %.0f%%, lava %d, score %.2f%s",
                this.purity * 100.0,
                this.water * 100.0,
                this.relief,
                this.rough * 100.0,
                this.treeFrac * 100.0,
                this.lava,
                this.score,
                this.acceptable ? "" : " (best available)"
            );
        }
    }

    /** Vanilla generation with mobs off and a fixed spawn, so creating the world never searches for a spawn point. */
    private static final class SourceGenerator extends ChunkGenerator {
        private final boolean structures;

        SourceGenerator(boolean structures) {
            this.structures = structures;
        }

        @Override
        public boolean shouldGenerateNoise() {
            return true;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return true;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return true;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return true;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return this.structures;
        }

        @Override
        public boolean isParallelCapable() {
            return true;
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 200.0, 0.5);
        }
    }
}
