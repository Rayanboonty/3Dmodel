package net.vortexpvp.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.util.Cuboid;
import net.vortexpvp.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.type.FlowerBed;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.util.noise.SimplexOctaveGenerator;

public final class ArenaGenerator {
    public static final int BASE_Y = 64;
    public static final int GEN_VERSION = 6;
    private static final Map<String, ColosseumDesign> FLOOR_SHAPES = new ConcurrentHashMap<>();
    private static final Map<String, BlockData> STATES = new ConcurrentHashMap<>();
    private static final Material[] BANDS = new Material[]{
        Material.ORANGE_TERRACOTTA,
        Material.TERRACOTTA,
        Material.YELLOW_TERRACOTTA,
        Material.TERRACOTTA,
        Material.BROWN_TERRACOTTA,
        Material.RED_TERRACOTTA,
        Material.WHITE_TERRACOTTA,
        Material.LIGHT_GRAY_TERRACOTTA,
        Material.ORANGE_TERRACOTTA,
        Material.TERRACOTTA
    };
    private static final Material[] FLOWERS = new Material[]{
        Material.DANDELION, Material.POPPY, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.AZURE_BLUET
    };

    public static int depthFor(ArenaTheme theme, boolean ffa) {
        return depthFor(theme, ffa, theme == ArenaTheme.FLATLANDS);
    }

    public static int depthFor(ArenaTheme theme, boolean ffa, boolean crystal) {
        return crystal ? Cfg.i(Opt.CRYSTAL_DEPTH) : (ffa ? Cfg.i(Opt.FFA_DEPTH) : Cfg.i(Opt.DUEL_DEPTH));
    }

    public static String signature(ArenaTheme theme, boolean ffa) {
        return signature(theme, ffa, theme == ArenaTheme.FLATLANDS);
    }

    public static String signature(ArenaTheme theme, boolean ffa, boolean crystal) {
        return "9/"
            + depthFor(theme, ffa, crystal)
            + "/"
            + (crystal ? "deep/" : "")
            + Cfg.s(Opt.ARENA_WALLS)
            + "/"
            + Cfg.bool(Opt.ARENA_PLANTS)
            + "/"
            + Cfg.bool(Opt.ARENA_ORES)
            + "/"
            + Cfg.bool(Opt.COLOSSEUM_BARRIERS);
    }

    public static boolean onFloor(ArenaTheme theme, int size, Cuboid bounds, int x, int z) {
        if (theme != null && theme.colosseum != null && bounds != null && size > 0) {
            ColosseumDesign d = FLOOR_SHAPES.computeIfAbsent(
                theme.name() + ":" + size, k -> new ColosseumDesign(theme.colosseum, size, 0L, 0, 64, 0, 4, true, false)
            );
            int cx = Math.floorDiv(bounds.minX() + bounds.maxX(), 2);
            int cz = Math.floorDiv(bounds.minZ() + bounds.maxZ(), 2);
            return d.sd(x - cx, z - cz) < -0.5;
        } else {
            return true;
        }
    }

    private static ArenaGenerator.Plan colosseumPlan(ArenaTheme theme, int size, long seed, int ox, int oz, boolean ffa) {
        int depth = depthFor(theme, ffa, false);
        ColosseumDesign probe = new ColosseumDesign(theme.colosseum, size, seed, 0, 64, 0, depth, true, ffa);
        int cx = ox + probe.halfX;
        int cz = oz + probe.halfZ;
        int floorY = 64;
        if (floorY - depth < minFloorY()) {
            floorY = minFloorY() + depth;
        }

        ColosseumDesign d = new ColosseumDesign(theme.colosseum, size, seed, cx, floorY, cz, depth, true, ffa);
        int sp = d.spawnOffset();
        int y1 = d.surface(sp, 0) + 1;
        int y2 = d.surface(-sp, 0) + 1;
        Pos s1 = new Pos(cx + sp + 0.5, y1, cz + 0.5, 90.0F, 0.0F);
        Pos s2 = new Pos(cx - sp + 0.5, y2, cz + 0.5, -90.0F, 0.0F);
        Pos center = new Pos(cx + 0.5, d.surface(0, 0) + 3, cz + 0.5, 0.0F, 20.0F);
        List<Pos> ffaSpawns = new ArrayList<>();
        if (ffa) {
            int n = 12;

            for (int i = 0; i < n; i++) {
                double a = (Math.PI * 2) * i / n;
                double rx = Math.cos(a) * (d.straight * 0.7 + d.radius * 0.55);
                double rz = Math.sin(a) * d.radius * 0.55;
                int bx = (int)Math.round(rx);
                int bz = (int)Math.round(rz);
                double yaw = Math.toDegrees(Math.atan2(rx, -rz));
                ffaSpawns.add(new Pos(cx + bx + 0.5, d.surface(bx, bz) + 1, cz + bz + 0.5, (float)yaw, 0.0F));
            }
        }

        Cuboid bounds = new Cuboid(d.minX(), d.bottomY, d.minZ(), d.maxX(), d.topY, d.maxZ());
        return new ArenaGenerator.Plan(theme, size, seed, ox, oz, null, d.bottomY, bounds, s1, s2, center, ffaSpawns, false, false, d, null, null);
    }

    private static ArenaGenerator.Plan openPlan(ArenaTheme theme, int size, long seed, int ox, int oz, boolean ffa) {
        int depth = depthFor(theme, ffa, false);
        int floorY = 64;
        if (floorY - depth - 4 < minFloorY()) {
            floorY = minFloorY() + depth + 4;
        }

        int half = Math.max(64, size) / 2;
        int cx = ox + half;
        int cz = oz + half;
        OpenArenaDesign d = new OpenArenaDesign(theme.open, size, seed, cx, floorY, cz, depth, ffa);
        int sp = d.spawnOffset();
        Pos s1 = new Pos(cx - sp + 0.5, floorY + 1, cz + 0.5, -90.0F, 0.0F);
        Pos s2 = new Pos(cx + sp + 0.5, floorY + 1, cz + 0.5, 90.0F, 0.0F);
        Pos center = new Pos(cx + 0.5, floorY + 3, cz + 0.5, 0.0F, 20.0F);
        List<Pos> ffaSpawns = new ArrayList<>();
        if (ffa) {
            int n = 12;

            for (int i = 0; i < n; i++) {
                double a = (Math.PI * 2) * i / n;
                double r = d.ffaRadius();
                int bx = (int)Math.round(Math.cos(a) * r);
                int bz = (int)Math.round(Math.sin(a) * r);
                double yaw = Math.toDegrees(Math.atan2(bx, -bz));
                ffaSpawns.add(new Pos(cx + bx + 0.5, floorY + 1, cz + bz + 0.5, (float)yaw, 0.0F));
            }
        }

        Cuboid bounds = new Cuboid(d.minX(), d.bottomY, d.minZ(), d.maxX(), d.topY, d.maxZ());
        return new ArenaGenerator.Plan(theme, size, seed, ox, oz, null, d.bottomY, bounds, s1, s2, center, ffaSpawns, false, false, null, d, null);
    }

    private static ArenaGenerator.Plan showPlan(ArenaTheme theme, int size, long seed, int ox, int oz, boolean ffa) {
        boolean mole = theme.show == net.vortexpvp.build.ShowArenaDesign.Style.MOLE_MINES;
        int depth = mole ? net.vortexpvp.build.MoleMinesDesign.DEPTH : depthFor(theme, ffa, false);
        int floorY = 64;
        if (floorY - depth < minFloorY()) {
            floorY = minFloorY() + depth;
        }

        int half = mole ? net.vortexpvp.build.MoleMinesDesign.RADIUS : Math.max(60, (size - 1) / 2);
        int cx = ox + half;
        int cz = oz + half;
        net.vortexpvp.build.Voxels d = mole
            ? new net.vortexpvp.build.MoleMinesDesign(cx, floorY, cz, seed)
            : new net.vortexpvp.build.ShowArenaDesign(theme.show, size, seed, cx, floorY, cz, depth, ffa, Cfg.bool(Opt.COLOSSEUM_BARRIERS));
        net.vortexpvp.build.ArenaVoxels av = (net.vortexpvp.build.ArenaVoxels)d;
        int[] a1 = av.spawn(0);
        int[] a2 = av.spawn(1);
        Pos s1 = new Pos(cx + a1[0] + 0.5, floorY + a1[1], cz + a1[2] + 0.5, -90.0F, 0.0F);
        Pos s2 = new Pos(cx + a2[0] + 0.5, floorY + a2[1], cz + a2[2] + 0.5, 90.0F, 0.0F);
        Pos center = mole ? new Pos(cx + 36.5, floorY + 7, cz + 10.5, 90.0F, 10.0F) : new Pos(cx + 0.5, floorY + 3, cz + 0.5, 0.0F, 20.0F);
        List<Pos> ffaSpawns = new ArrayList<>();
        if (ffa) {
            int n = 12;

            for (int i = 0; i < n; i++) {
                int[] o = av.ffaSpot(i, n);
                double yaw = Math.toDegrees(Math.atan2(o[0], -o[2]));
                ffaSpawns.add(new Pos(cx + o[0] + 0.5, floorY + o[1], cz + o[2] + 0.5, (float)yaw, 0.0F));
            }
        }

        Cuboid bounds = new Cuboid(d.minX(), d.bottomY(), d.minZ(), d.maxX(), d.topY(), d.maxZ());
        return new ArenaGenerator.Plan(theme, size, seed, ox, oz, null, d.bottomY(), bounds, s1, s2, center, ffaSpawns, false, false, null, null, d);
    }

    /** Column-by-column build of a show arena. */
    private static BlockJobs.Job showJob(final World w, final ArenaGenerator.Plan plan) {
        final net.vortexpvp.build.Voxels d = plan.show();
        final int minY = w.getMinHeight();
        final int maxY = w.getMaxHeight() - 1;
        return new BlockJobs.Job() {
            int x = d.minX();
            int z = d.minZ();
            boolean biomes;
            final int[] used = new int[1];
            final net.vortexpvp.build.Voxels.Sink sink = (bx, by, bz, block) -> {
                if (by >= minY && by <= maxY) {
                    w.getBlockAt(bx, by, bz).setBlockData(ArenaGenerator.state(block), false);
                    this.used[0]++;
                }
            };

            @Override
            public int step(int budget) {
                this.used[0] = 0;

                while (this.x <= d.maxX() && this.used[0] < budget) {
                    // clear the column first: a rebuild must not leave the old map's blocks behind
                    for (int y = d.bottomY(); y <= d.topY(); y++) {
                        Block blk = w.getBlockAt(this.x, y, this.z);
                        if (!blk.getType().isAir()) {
                            blk.setType(Material.AIR, false);
                        }
                    }

                    d.column(this.x, this.z, this.sink);
                    this.used[0] += 8;
                    if (++this.z > d.maxZ()) {
                        this.z = d.minZ();
                        this.x++;
                    }

                    if (BlockJobs.overDeadline()) {
                        break;
                    }
                }

                if (this.x > d.maxX() && !this.biomes) {
                    this.biomes = true;
                    if (plan.theme().biome != null) {
                        Cuboid bd = plan.bounds();

                        for (int bx = bd.minX(); bx <= bd.maxX() + 3; bx += 4) {
                            for (int bz = bd.minZ(); bz <= bd.maxZ() + 3; bz += 4) {
                                for (int by = bd.minY(); by <= bd.maxY() + 3; by += 4) {
                                    w.setBiome(Math.min(bx, bd.maxX()), Math.min(by, bd.maxY()), Math.min(bz, bd.maxZ()), plan.theme().biome);
                                }
                            }
                        }
                    }

                    this.used[0] = this.used[0] + 200;
                }

                return this.used[0];
            }

            @Override
            public boolean done() {
                return this.x > d.maxX() && this.biomes;
            }

            @Override
            public String name() {
                return "show-" + plan.theme().name().toLowerCase(Locale.ROOT);
            }
        };
    }

    private static int minFloorY() {
        World w = Bukkit.getWorld("practice_world");
        return (w == null ? -64 : w.getMinHeight()) + 1;
    }

    private ArenaGenerator() {
    }

    public static ArenaGenerator.Plan plan(ArenaTheme theme, int size, long seed, int ox, int oz, boolean ffa, int buildHeight) {
        return plan(theme, size, seed, ox, oz, ffa, buildHeight, theme == ArenaTheme.FLATLANDS);
    }

    public static ArenaGenerator.Plan plan(ArenaTheme theme, int size, long seed, int ox, int oz, boolean ffa, int buildHeight, boolean crystal) {
        if (theme.colosseum != null) {
            return colosseumPlan(theme, size, seed, ox, oz, ffa);
        } else if (theme.open != null) {
            return openPlan(theme, size, seed, ox, oz, ffa);
        } else if (theme.show != null) {
            return showPlan(theme, size, seed, ox, oz, ffa);
        } else {
            int[][] h = new int[size][size];
            double[][] raw = new double[size][size];
            SimplexOctaveGenerator noise = new SimplexOctaveGenerator(seed, 4);
            noise.setScale(0.023809523809523808);
            SimplexOctaveGenerator ridge = new SimplexOctaveGenerator(seed ^ 25214903917L, 2);
            ridge.setScale(0.0625);
            double c = (size - 1) / 2.0;

            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    double n = noise.noise(x, z, 0.5, 0.5, true);
                    double y = 64.0 + n * theme.amplitude;
                    if (theme.mountains > 0) {
                        double d = Math.max(Math.abs(x - c), Math.abs(z - c)) / c;
                        double m = smooth((d - 0.58) / 0.42);
                        double r = (ridge.noise(x, z, 0.5, 0.5, true) + 1.0) / 2.0;
                        y += m * theme.mountains * (0.55 + 0.45 * r);
                    }

                    raw[x][z] = y;
                }
            }

            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    double sum = 0.0;
                    int n = 0;

                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int ax = x + dx;
                            int az = z + dz;
                            if (ax >= 0 && az >= 0 && ax < size && az < size) {
                                sum += raw[ax][az];
                                n++;
                            }
                        }
                    }

                    h[x][z] = (int)Math.round(sum / n);
                }
            }

            despike(h);
            List<int[]> pads = new ArrayList<>();
            int dist = Math.max(10, Math.min(34, (int)(size * 0.3)));
            int cx = (int)Math.round(c);
            int[] s1 = new int[]{cx - dist, cx};
            int[] s2 = new int[]{cx + dist, cx};
            List<Pos> ffaSpawns = new ArrayList<>();
            Random rnd = new Random(seed * 31L + 7L);
            if (ffa) {
                int count = 10;
                double radius = size * 0.32;

                for (int i = 0; i < count; i++) {
                    double a = (Math.PI * 2) * i / count + rnd.nextDouble() * 0.25;
                    double rr = radius * (0.55 + rnd.nextDouble() * 0.45);
                    int px = (int)Math.round(c + Math.cos(a) * rr);
                    int pz = (int)Math.round(c + Math.sin(a) * rr);
                    pads.add(new int[]{clamp(px, 4, size - 5), clamp(pz, 4, size - 5)});
                }
            } else {
                pads.add(s1);
                pads.add(s2);
            }

            for (int[] pad : pads) {
                flatten(h, pad[0], pad[1], ffa ? 3.5 : 6.5);
            }

            despike(h);
            int maxH = 64;
            int minH = Integer.MAX_VALUE;

            for (int[] col : h) {
                for (int v : col) {
                    maxH = Math.max(maxH, v);
                    minH = Math.min(minH, v);
                }
            }

            int depth = depthFor(theme, ffa, crystal);
            int lift = Math.max(0, minFloorY() + depth + 4 - 64);
            if (lift > 0) {
                for (int[] col : h) {
                    for (int i = 0; i < col.length; i++) {
                        col[i] += lift;
                    }
                }

                maxH += lift;
                minH += lift;
            }

            int floorY = Math.max(minFloorY(), Math.min(64 + lift - depth, minH - 4));
            int spawnY = Math.max(h[s1[0]][s1[1]], h[s2[0]][s2[1]]) + 1;
            int topY = Math.min(318, Math.max(maxH + 9, spawnY + buildHeight + 2));
            Cuboid bounds = new Cuboid(ox, floorY, oz, ox + size - 1, topY, oz + size - 1);
            Pos sp1 = new Pos(ox + s1[0] + 0.5, h[s1[0]][s1[1]] + 1, oz + s1[1] + 0.5, -90.0F, 0.0F);
            Pos sp2 = new Pos(ox + s2[0] + 0.5, h[s2[0]][s2[1]] + 1, oz + s2[1] + 0.5, 90.0F, 0.0F);
            Pos center = new Pos(ox + cx + 0.5, h[cx][cx] + 3, oz + cx + 0.5, 0.0F, 20.0F);
            if (ffa) {
                for (int[] pad : pads) {
                    double yaw = Math.toDegrees(Math.atan2(-(c - pad[0]), c - pad[1]));
                    ffaSpawns.add(new Pos(ox + pad[0] + 0.5, h[pad[0]][pad[1]] + 1, oz + pad[1] + 0.5, (float)yaw, 0.0F));
                }
            }

            return new ArenaGenerator.Plan(theme, size, seed, ox, oz, h, floorY, bounds, sp1, sp2, center, ffaSpawns, Cfg.bool(Opt.ARENA_ORES), crystal, null, null, null);
        }
    }

    private static void flatten(int[][] h, int px, int pz, double radius) {
        int size = h.length;
        int sum = 0;
        int n = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = px + dx;
                int z = pz + dz;
                if (x >= 0 && z >= 0 && x < size && z < size) {
                    sum += h[x][z];
                    n++;
                }
            }
        }

        int target = Math.round((float)sum / Math.max(1, n));
        int r = (int)Math.ceil(radius);

        for (int dx = -r; dx <= r; dx++) {
            for (int dzx = -r; dzx <= r; dzx++) {
                int x = px + dx;
                int z = pz + dzx;
                if (x >= 0 && z >= 0 && x < size && z < size) {
                    double d = Math.sqrt(dx * dx + dzx * dzx);
                    if (!(d > radius)) {
                        double f = d < radius * 0.45 ? 1.0 : 1.0 - (d - radius * 0.45) / (radius * 0.55);
                        h[x][z] = (int)Math.round(h[x][z] + (target - h[x][z]) * f);
                    }
                }
            }
        }
    }

    private static void despike(int[][] h) {
        int size = h.length;

        for (int pass = 0; pass < 2; pass++) {
            for (int x = 1; x < size - 1; x++) {
                for (int z = 1; z < size - 1; z++) {
                    int a = h[x - 1][z];
                    int b = h[x + 1][z];
                    int cc = h[x][z - 1];
                    int d = h[x][z + 1];
                    int max = Math.max(Math.max(a, b), Math.max(cc, d));
                    int min = Math.min(Math.min(a, b), Math.min(cc, d));
                    if (h[x][z] > max) {
                        h[x][z] = max;
                    } else if (h[x][z] < min) {
                        h[x][z] = min;
                    }
                }
            }
        }
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static BlockJobs.Job buildJob(World w, ArenaGenerator.Plan plan) {
        return buildJob(w, plan, -1);
    }

    public static BlockJobs.Job buildJob(final World w, final ArenaGenerator.Plan plan, final int clearToY) {
        if (plan.open() != null) {
            return openJob(w, plan);
        }

        if (plan.show() != null) {
            return showJob(w, plan);
        }

        return plan.colosseum() != null ? colosseumJob(w, plan) : new BlockJobs.Job() {
            int column = 0;
            final int total = plan.size * plan.size;
            final Random rnd = new Random(plan.seed);
            final List<ArenaGenerator.Write> trees = new ArrayList<>();
            final List<int[]> treeSpots = new ArrayList<>();
            int treeIndex = -1;
            boolean biomes;
            final SimplexOctaveGenerator deco = new SimplexOctaveGenerator(plan.seed ^ 57024L, 2);

            {
                this.deco.setScale(0.1111111111111111);
            }

            @Override
            public int step(int budget) {
                int used = 0;

                while (this.column < this.total && used < budget) {
                    int x = this.column / plan.size;
                    int z = this.column % plan.size;
                    used += ArenaGenerator.buildColumn(w, plan, x, z, this.rnd, this.deco, this.treeSpots, this.trees, clearToY);
                    this.column++;
                }

                if (this.column >= this.total && this.treeIndex < 0) {
                    this.treeIndex = 0;
                }

                for (; this.treeIndex >= 0 && this.treeIndex < this.trees.size() && used < budget; used++) {
                    ArenaGenerator.Write wr = this.trees.get(this.treeIndex++);
                    Block b = w.getBlockAt(wr.x, wr.y, wr.z);
                    if (b.getType().isAir() || !b.getType().isSolid()) {
                        if (b.getBlockData() instanceof Bisected bi) {
                            int other = bi.getHalf() == Half.BOTTOM ? 1 : -1;
                            Block o = b.getRelative(0, other, 0);
                            if (o.getType() == b.getType()) {
                                o.setType(Material.AIR, false);
                            }
                        }

                        b.setBlockData(wr.data, false);
                    }
                }

                if (this.column >= this.total && this.treeIndex >= this.trees.size() && !this.biomes) {
                    if (plan.theme.biome != null) {
                        Cuboid bd = plan.bounds;

                        for (int bx = bd.minX(); bx <= bd.maxX() + 3; bx += 4) {
                            for (int bz = bd.minZ(); bz <= bd.maxZ() + 3; bz += 4) {
                                for (int by = bd.minY(); by <= bd.maxY() + 3; by += 4) {
                                    w.setBiome(Math.min(bx, bd.maxX()), Math.min(by, bd.maxY()), Math.min(bz, bd.maxZ()), plan.theme.biome);
                                }
                            }
                        }
                    }

                    this.biomes = true;
                    used += 100;
                }

                return used;
            }

            @Override
            public boolean done() {
                return this.column >= this.total && this.treeIndex >= this.trees.size() && this.biomes;
            }

            @Override
            public String name() {
                return "generate-" + plan.theme.name().toLowerCase();
            }
        };
    }

    static BlockData state(String s) {
        BlockData d = STATES.get(s);
        if (d != null) {
            return d;
        } else {
            try {
                d = Bukkit.createBlockData(s);
            } catch (IllegalArgumentException var5) {
                int br = s.indexOf(91);
                Material m = Material.matchMaterial(br >= 0 ? s.substring(0, br) : s);
                d = (m != null && m.isBlock() ? m : Material.STONE).createBlockData();
                Bukkit.getLogger().warning("[VortexPvP] Unknown block state '" + s + "' in a colosseum, using " + d.getMaterial());
            }

            STATES.put(s, d);
            return d;
        }
    }

    /** Same column-by-column build as the colosseums, for the open designed arenas. */
    private static BlockJobs.Job openJob(final World w, final ArenaGenerator.Plan plan) {
        final OpenArenaDesign d = plan.open();
        final int minY = w.getMinHeight();
        final int maxY = w.getMaxHeight() - 1;
        return new BlockJobs.Job() {
            int x = d.minX();
            int z = d.minZ();
            boolean biomes;
            final int[] used = new int[1];
            final ColosseumDesign.Sink sink = (bx, by, bz, block) -> {
                if (by >= minY && by <= maxY) {
                    w.getBlockAt(bx, by, bz).setBlockData(ArenaGenerator.state(block), false);
                    this.used[0]++;
                }
            };

            @Override
            public int step(int budget) {
                this.used[0] = 0;

                while (this.x <= d.maxX() && this.used[0] < budget) {
                    d.column(this.x, this.z, this.sink);
                    this.used[0]++;
                    if (++this.z > d.maxZ()) {
                        this.z = d.minZ();
                        this.x++;
                    }

                    if ((this.used[0] & 255) == 0 && BlockJobs.overDeadline()) {
                        break;
                    }
                }

                if (this.x > d.maxX() && !this.biomes) {
                    this.biomes = true;
                    if (plan.theme().biome != null) {
                        Cuboid bd = plan.bounds();

                        for (int bx = bd.minX(); bx <= bd.maxX() + 3; bx += 4) {
                            for (int bz = bd.minZ(); bz <= bd.maxZ() + 3; bz += 4) {
                                for (int by = bd.minY(); by <= bd.maxY() + 3; by += 4) {
                                    w.setBiome(Math.min(bx, bd.maxX()), Math.min(by, bd.maxY()), Math.min(bz, bd.maxZ()), plan.theme().biome);
                                }
                            }
                        }
                    }

                    this.used[0] = this.used[0] + 200;
                }

                return this.used[0];
            }

            @Override
            public boolean done() {
                return this.x > d.maxX() && this.biomes;
            }

            @Override
            public String name() {
                return "open-" + plan.theme().name().toLowerCase(Locale.ROOT);
            }
        };
    }

    private static BlockJobs.Job colosseumJob(final World w, final ArenaGenerator.Plan plan) {
        final ColosseumDesign d = plan.colosseum();
        final int minY = w.getMinHeight();
        final int maxY = w.getMaxHeight() - 1;
        return new BlockJobs.Job() {
            int x = d.minX();
            int z = d.minZ();
            boolean biomes;
            final int[] used = new int[1];
            final ColosseumDesign.Sink sink = (bx, by, bz, block) -> {
                if (by >= minY && by <= maxY) {
                    w.getBlockAt(bx, by, bz).setBlockData(ArenaGenerator.state(block), false);
                    this.used[0]++;
                }
            };

            @Override
            public int step(int budget) {
                this.used[0] = 0;

                while (this.x <= d.maxX() && this.used[0] < budget) {
                    d.column(this.x, this.z, this.sink);
                    this.used[0]++;
                    if (++this.z > d.maxZ()) {
                        this.z = d.minZ();
                        this.x++;
                    }
                }

                if (this.x > d.maxX() && !this.biomes) {
                    this.biomes = true;
                    if (plan.theme().biome != null) {
                        Cuboid bd = plan.bounds();

                        for (int bx = bd.minX(); bx <= bd.maxX() + 3; bx += 4) {
                            for (int bz = bd.minZ(); bz <= bd.maxZ() + 3; bz += 4) {
                                for (int by = bd.minY(); by <= bd.maxY() + 3; by += 4) {
                                    w.setBiome(Math.min(bx, bd.maxX()), Math.min(by, bd.maxY()), Math.min(bz, bd.maxZ()), plan.theme().biome);
                                }
                            }
                        }
                    }

                    this.used[0] = this.used[0] + 200;
                }

                return this.used[0];
            }

            @Override
            public boolean done() {
                return this.x > d.maxX() && this.biomes;
            }

            @Override
            public String name() {
                return "colosseum-" + plan.theme().name().toLowerCase(Locale.ROOT);
            }
        };
    }

    private static int buildColumn(
        World w,
        ArenaGenerator.Plan p,
        int x,
        int z,
        Random rnd,
        SimplexOctaveGenerator deco,
        List<int[]> treeSpots,
        List<ArenaGenerator.Write> trees,
        int clearToY
    ) {
        int h = p.heights[x][z];
        int wx = p.ox + x;
        int wz = p.oz + z;
        double c = (p.size - 1) / 2.0;
        double d = Math.max(Math.abs(x - c), Math.abs(z - c)) / c;
        boolean mountain = p.theme.mountains > 0 && d > 0.58 && h > 67;
        int writes = 0;

        for (int y = p.floorY; y <= h; y++) {
            BlockData data = bedrock(p, wx, y, wz)
                ? Material.BEDROCK.createBlockData()
                : (y == p.floorY ? Material.DEEPSLATE.createBlockData() : layer(p.theme, h - y, wx, y, wz, mountain));
            if (p.ores() && h - y >= 3 && y > p.floorY) {
                data = ore(data, wx, y, wz);
            }

            w.getBlockAt(wx, y, wz).setBlockData(data, false);
            writes++;
        }

        int clearTop = Math.max(h + 3, clearToY);

        for (int y = h + 1; y <= clearTop; y++) {
            Block above = w.getBlockAt(wx, y, wz);
            if (!above.getType().isAir()) {
                above.setType(Material.AIR, false);
                writes++;
            }
        }

        writes += (clearTop - h) / 16;
        writes += wall(w, p, x, z, wx, wz, h);
        if (nearSpawn(p, wx, wz, 4.5)) {
            return writes;
        } else {
            double r = rnd.nextDouble();
            if (canTree(p.theme) && d > 0.6 && d < 0.93 && r < 0.012 && !mountain && farFrom(treeSpots, x, z, 5) && !nearSpawn(p, wx, wz, 7.0)) {
                treeSpots.add(new int[]{x, z});
                planTree(p.theme, wx, h + 1, wz, rnd, trees);
                return writes;
            } else if (p.theme == ArenaTheme.END
                && d > 0.42
                && d < 0.88
                && r < 0.004
                && h + 24 <= p.bounds.maxY()
                && farFrom(treeSpots, x, z, 13)
                && !nearSpawn(p, wx, wz, 10.0)
                && !nearPartyRing(p, wx, wz, 6.0)) {
                treeSpots.add(new int[]{x, z});
                planPillar(wx, h + 1, wz, rnd, trees);
                return writes;
            } else {
                double g = (deco.noise(x, z, 0.5, 0.5, true) + 1.0) / 2.0;
                double f = (deco.noise(x + 4000, z - 4000, 0.5, 0.5, true) + 1.0) / 2.0;
                if (Cfg.bool(Opt.ARENA_PLANTS) || p.theme == ArenaTheme.SNOWY) {
                    writes += decorate(w, p.theme, wx, h + 1, wz, r, rnd, mountain, g, f);
                }

                return writes;
            }
        }
    }

    /** Party FFA spreads players on a ring at 0.9x the spawn distance and drops them on the highest block (Match.spawnOf). */
    private static boolean nearPartyRing(ArenaGenerator.Plan p, int wx, int wz, double margin) {
        double ring = Math.max(4.0, Math.hypot(p.spawn1.x() - p.center.x(), p.spawn1.z() - p.center.z()) * 0.9);
        double dist = Math.hypot(wx + 0.5 - p.center.x(), wz + 0.5 - p.center.z());
        return Math.abs(dist - ring) < margin;
    }

    private static boolean nearSpawn(ArenaGenerator.Plan p, int wx, int wz, double radius) {
        if (!p.ffaSpawns.isEmpty()) {
            for (Pos s : p.ffaSpawns) {
                if (dist2(s, wx, wz) < radius * radius) {
                    return true;
                }
            }

            return false;
        } else {
            return dist2(p.spawn1, wx, wz) < radius * radius || dist2(p.spawn2, wx, wz) < radius * radius;
        }
    }

    private static double dist2(Pos s, int wx, int wz) {
        double dx = s.x() - (wx + 0.5);
        double dz = s.z() - (wz + 0.5);
        return dx * dx + dz * dz;
    }

    private static boolean farFrom(List<int[]> spots, int x, int z, int min) {
        for (int[] s : spots) {
            if (Math.abs(s[0] - x) < min && Math.abs(s[1] - z) < min) {
                return false;
            }
        }

        return true;
    }

    private static BlockData layer(ArenaTheme theme, int depth, int x, int y, int z, boolean mountain) {
        switch (theme) {
            case BADLANDS:
            case BADLANDS_DESERT:
                if (mountain) {
                    return BANDS[Math.floorMod(y, BANDS.length)].createBlockData();
                } else if (depth <= 2) {
                    return Material.RED_SAND.createBlockData();
                } else {
                    if (depth <= 5) {
                        return Material.RED_SANDSTONE.createBlockData();
                    }

                    return BANDS[Math.floorMod(y, BANDS.length)].createBlockData();
                }
            case DESERT:
                if (depth <= 3) {
                    return Material.SAND.createBlockData();
                }

                return depth <= 6 ? Material.SANDSTONE.createBlockData() : stone(x, y, z);
            case SNOWY:
                if (depth == 0) {
                    return Bukkit.createBlockData("minecraft:grass_block[snowy=true]");
                }

                return depth <= 3 ? Material.DIRT.createBlockData() : stone(x, y, z);
            case PALE:
                if (depth == 0) {
                    return Material.PALE_MOSS_BLOCK.createBlockData();
                }

                return depth <= 3 ? Material.DIRT.createBlockData() : stone(x, y, z);
            case CRIMSON:
                if (depth == 0) {
                    return Material.CRIMSON_NYLIUM.createBlockData();
                }

                return depth > 7 ? Material.BLACKSTONE.createBlockData() : Material.NETHERRACK.createBlockData();
            case END:
            case END_OUTER:
                return Material.END_STONE.createBlockData();
            case MUSHROOM:
                if (depth == 0) {
                    return Material.MYCELIUM.createBlockData();
                }

                return depth <= 3 ? Material.DIRT.createBlockData() : stone(x, y, z);
            default:
                if (depth == 0) {
                    return Material.GRASS_BLOCK.createBlockData();
                } else {
                    return depth <= 3 ? Material.DIRT.createBlockData() : stone(x, y, z);
                }
        }
    }

    private static int hash(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h ^= h >>> 13;
        h *= 25214903917L;
        return Math.floorMod(h ^ h >>> 17, 1000);
    }

    private static boolean bedrock(ArenaGenerator.Plan p, int x, int y, int z) {
        // every arena ends on bedrock, the deep crystal ones too: digging down never reaches the void
        {
            int above = y - p.floorY;
            if (above == 0) {
                return true;
            } else if (above > 3) {
                return false;
            } else {
                double chance = above == 1 ? 0.7 : (above == 2 ? 0.4 : 0.15);
                return hash(x, y, z) < chance * 1000.0;
            }
        }
    }

    private static int wall(World w, ArenaGenerator.Plan p, int x, int z, int wx, int wz, int h) {
        Opt.WallStyle style;
        try {
            style = Opt.WallStyle.valueOf(Cfg.s(Opt.ARENA_WALLS));
        } catch (IllegalArgumentException var22) {
            style = Opt.WallStyle.NONE;
        }

        int last = p.size - 1;
        boolean border = x == 0 || z == 0 || x == last || z == last;
        if (style != Opt.WallStyle.NONE && border) {
            Material wall;
            Material accent;
            Material pillar;
            Material light;
            switch (style) {
                case BLACKSTONE:
                    wall = Material.POLISHED_BLACKSTONE_BRICKS;
                    accent = Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
                    pillar = Material.CHISELED_POLISHED_BLACKSTONE;
                    light = Material.SOUL_LANTERN;
                    break;
                case QUARTZ:
                    wall = Material.QUARTZ_BRICKS;
                    accent = Material.SMOOTH_QUARTZ;
                    pillar = Material.QUARTZ_PILLAR;
                    light = Material.LANTERN;
                    break;
                case DEEPSLATE:
                    wall = Material.DEEPSLATE_TILES;
                    accent = Material.CRACKED_DEEPSLATE_TILES;
                    pillar = Material.CHISELED_DEEPSLATE;
                    light = Material.SOUL_LANTERN;
                    break;
                case NETHER:
                    wall = Material.RED_NETHER_BRICKS;
                    accent = Material.NETHER_BRICKS;
                    pillar = Material.CHISELED_NETHER_BRICKS;
                    light = Material.LANTERN;
                    break;
                default:
                    wall = Material.STONE_BRICKS;
                    accent = Material.MOSSY_STONE_BRICKS;
                    pillar = Material.CHISELED_STONE_BRICKS;
                    light = Material.LANTERN;
            }

            boolean corner = (x == 0 || x == last) && (z == 0 || z == last);
            boolean xSide = x == 0 || x == last;
            int along = xSide ? z : x;
            boolean isPillar = corner || along % 8 == 0 || along == last;
            int top;
            if (isPillar) {
                top = sideMax(p, xSide, xSide ? x : z, along - 8, along + 8) + 6;
                if (corner) {
                    top = Math.max(top, sideMax(p, false, z, x == 0 ? 0 : last - 8, x == 0 ? 8 : last) + 6);
                }
            } else {
                int seg = along / 8 * 8;
                top = sideMax(p, xSide, xSide ? x : z, seg, seg + 8) + 4;
            }

            int writes = 0;

            for (int y = h + 1; y <= top; y++) {
                Material m = isPillar ? pillar : (y == top ? wall : (hash(wx, y, wz) < 220 ? accent : wall));
                w.getBlockAt(wx, y, wz).setBlockData(m.createBlockData(), false);
                writes++;
            }

            if (isPillar) {
                w.getBlockAt(wx, top + 1, wz).setBlockData(light.createBlockData(), false);
                writes++;
            } else if (along % 2 == 0) {
                w.getBlockAt(wx, top + 1, wz).setBlockData(wall.createBlockData(), false);
                writes++;
            }

            return writes;
        } else {
            return 0;
        }
    }

    private static int sideMax(ArenaGenerator.Plan p, boolean xSide, int fixed, int from, int to) {
        int last = p.size - 1;
        int max = Integer.MIN_VALUE;

        for (int i = Math.max(0, from); i <= Math.min(last, to); i++) {
            max = Math.max(max, xSide ? p.heights[fixed][i] : p.heights[i][fixed]);
        }

        return max;
    }

    private static BlockData stone(int x, int y, int z) {
        if (y < 54) {
            int r = hash(x >> 1, y >> 1, z >> 1);
            return (r < 60 ? Material.TUFF : (r < 120 ? Material.COBBLED_DEEPSLATE : Material.DEEPSLATE)).createBlockData();
        } else {
            long h = (x >> 1) * 73856093L ^ (y >> 1) * 19349663L ^ (z >> 1) * 83492791L;
            h ^= h >>> 13;
            int r = Math.floorMod(h * 25214903917L + 11L, 100);
            if (r < 7) {
                return Material.ANDESITE.createBlockData();
            } else {
                return r < 10 ? Material.TUFF.createBlockData() : Material.STONE.createBlockData();
            }
        }
    }

    private static BlockData ore(BlockData base, int x, int y, int z) {
        Material m = base.getMaterial();
        boolean deep = m == Material.DEEPSLATE || m == Material.TUFF || m == Material.COBBLED_DEEPSLATE;
        boolean stone = m == Material.STONE || m == Material.ANDESITE;
        boolean nether = m == Material.NETHERRACK;
        if (!deep && !stone && !nether) {
            return base;
        } else {
            int cx = x >> 1;
            int cy = y >> 1;
            int cz = z >> 1;
            if (hash(cx * 31 + 7, cy * 17 - 3, cz * 13 + 11) < 20 && hash(x + 101, y - 57, z + 33) < 620) {
                int kind = hash(cx - 5, cy + 9, cz - 2) % 100;
                if (nether) {
                    return (kind < 70 ? Material.NETHER_QUARTZ_ORE : Material.NETHER_GOLD_ORE).createBlockData();
                } else if (stone) {
                    return (kind < 55 ? Material.COAL_ORE : (kind < 85 ? Material.IRON_ORE : (kind < 95 ? Material.COPPER_ORE : Material.GOLD_ORE)))
                        .createBlockData();
                } else {
                    return 64 - y > 30 && kind < 12
                        ? Material.DEEPSLATE_DIAMOND_ORE.createBlockData()
                        : (kind < 35
                                ? Material.DEEPSLATE_IRON_ORE
                                : (
                                    kind < 55
                                        ? Material.DEEPSLATE_REDSTONE_ORE
                                        : (kind < 70 ? Material.DEEPSLATE_LAPIS_ORE : (kind < 85 ? Material.DEEPSLATE_GOLD_ORE : Material.DEEPSLATE_COPPER_ORE))
                                ))
                            .createBlockData();
                }
            } else {
                return base;
            }
        }
    }

    private static int decorate(World w, ArenaTheme theme, int x, int y, int z, double r, Random rnd, boolean mountain, double g, double f) {
        BlockData d = null;
        BlockData upper = null;
        double meadow = smooth((g - 0.38) / 0.4);
        switch (theme) {
            case BADLANDS:
            case BADLANDS_DESERT:
                if (mountain) {
                    return 0;
                }

                if (r < 0.01 + 0.12 * meadow) {
                    d = rnd.nextDouble() < 0.2 ? Material.TALL_DRY_GRASS.createBlockData() : Material.SHORT_DRY_GRASS.createBlockData();
                } else if (r > 0.992) {
                    d = Material.DEAD_BUSH.createBlockData();
                }
                break;
            case DESERT:
                if (r < 0.012) {
                    d = Material.DEAD_BUSH.createBlockData();
                } else if (r < 0.012 + 0.07 * meadow) {
                    d = Material.SHORT_DRY_GRASS.createBlockData();
                }
                break;
            case SNOWY:
                d = Bukkit.createBlockData("minecraft:snow[layers=1]");
                break;
            case PALE:
                if (g > 0.5 && r < 0.75 * smooth((g - 0.5) / 0.25)) {
                    d = Material.PALE_MOSS_CARPET.createBlockData();
                }
                break;
            case CRIMSON:
                if (r < 0.08) {
                    d = Material.CRIMSON_ROOTS.createBlockData();
                } else if (r < 0.095) {
                    d = Material.CRIMSON_FUNGUS.createBlockData();
                }
                break;
            case END:
            case END_OUTER:
                return r < 0.006 && w.getBlockAt(x, y, z).getType().isAir() ? chorus(w, x, y, z, rnd) : 0;
            default:
                return 0;
            case MUSHROOM:
                if (r < 0.03) {
                    d = Material.RED_MUSHROOM.createBlockData();
                } else if (r < 0.06) {
                    d = Material.BROWN_MUSHROOM.createBlockData();
                }
                break;
            case PLAINS:
                if (f > 0.75 && r < 0.22) {
                    int cell = Math.floorMod(Math.floorDiv(x, 9) * 73856093 ^ Math.floorDiv(z, 9) * 19349663, FLOWERS.length);
                    d = FLOWERS[rnd.nextDouble() < 0.8 ? cell : rnd.nextInt(FLOWERS.length)].createBlockData();
                } else if (r < 0.02 + 0.32 * meadow) {
                    if (meadow > 0.85 && rnd.nextDouble() < 0.08) {
                        d = Bukkit.createBlockData("minecraft:tall_grass[half=lower]");
                        upper = Bukkit.createBlockData("minecraft:tall_grass[half=upper]");
                    } else {
                        d = Material.SHORT_GRASS.createBlockData();
                    }
                }
                break;
            case CHERRY:
                if (f > 0.5 && r < 0.25 + 0.4 * smooth((f - 0.5) / 0.3)) {
                    FlowerBed petals = (FlowerBed)Material.PINK_PETALS.createBlockData();
                    petals.setFlowerAmount(Math.min(petals.getMaximumFlowerAmount(), 1 + (int)(smooth((f - 0.5) / 0.4) * petals.getMaximumFlowerAmount())));
                    BlockFace[] faces = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
                    petals.setFacing(faces[rnd.nextInt(4)]);
                    d = petals;
                } else if (r < 0.02 + 0.25 * meadow) {
                    d = Material.SHORT_GRASS.createBlockData();
                }
        }

        if (d == null) {
            return 0;
        } else {
            w.getBlockAt(x, y, z).setBlockData(d, false);
            if (upper != null) {
                w.getBlockAt(x, y + 1, z).setBlockData(upper, false);
                return 2;
            } else {
                return 1;
            }
        }
    }

    /** A small chorus plant: 1-3 stems with a dead flower on top. */
    private static int chorus(World w, int x, int y, int z, Random rnd) {
        int height = 1 + rnd.nextInt(3);
        for (int i = 0; i < height; i++) {
            MultipleFacing stem = (MultipleFacing)Material.CHORUS_PLANT.createBlockData();
            stem.setFace(BlockFace.DOWN, true);
            stem.setFace(BlockFace.UP, true);
            w.getBlockAt(x, y + i, z).setBlockData(stem, false);
        }

        w.getBlockAt(x, y + height, z).setBlockData(Bukkit.createBlockData("minecraft:chorus_flower[age=5]"), false);
        return height + 1;
    }

    /** The End map's obsidian pillar with an iron-bar cage on top (like the main End island). */
    private static void planPillar(int x, int y, int z, Random rnd, List<ArenaGenerator.Write> out) {
        int height = 11 + rnd.nextInt(9);
        int radius = rnd.nextDouble() < 0.4 ? 2 : 1;
        BlockData obsidian = Material.OBSIDIAN.createBlockData();
        for (int dy = -1; dy < height; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius + 1) {
                        out.add(new ArenaGenerator.Write(x + dx, y + dy, z + dz, obsidian));
                    }
                }
            }
        }

        int top = y + height;
        out.add(new ArenaGenerator.Write(x, top, z, Material.BEDROCK.createBlockData()));
        int cage = radius + 1;
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -cage; dx <= cage; dx++) {
                for (int dz = -cage; dz <= cage; dz++) {
                    boolean edge = Math.abs(dx) == cage || Math.abs(dz) == cage;
                    if (edge || dy == 2) {
                        MultipleFacing bars = (MultipleFacing)Material.IRON_BARS.createBlockData();
                        if (dy == 2 && !edge) {
                            bars.setFace(BlockFace.EAST, true);
                            bars.setFace(BlockFace.WEST, true);
                        } else {
                            bars.setFace(BlockFace.EAST, Math.abs(dz) == cage && dx < cage);
                            bars.setFace(BlockFace.WEST, Math.abs(dz) == cage && dx > -cage);
                            bars.setFace(BlockFace.SOUTH, Math.abs(dx) == cage && dz < cage);
                            bars.setFace(BlockFace.NORTH, Math.abs(dx) == cage && dz > -cage);
                        }

                        out.add(new ArenaGenerator.Write(x + dx, top + 1 + dy, z + dz, bars));
                    }
                }
            }
        }
    }

    private static boolean canTree(ArenaTheme t) {
        return t == ArenaTheme.PLAINS || t == ArenaTheme.SNOWY || t == ArenaTheme.CHERRY || t == ArenaTheme.PALE;
    }

    private static void planTree(ArenaTheme theme, int x, int y, int z, Random rnd, List<ArenaGenerator.Write> out) {
        Material log;
        Material leaves;
        switch (theme) {
            case SNOWY:
                log = Material.SPRUCE_LOG;
                leaves = Material.SPRUCE_LEAVES;
                break;
            case PALE:
                log = Material.PALE_OAK_LOG;
                leaves = Material.PALE_OAK_LEAVES;
                break;
            case CHERRY:
                log = Material.CHERRY_LOG;
                leaves = Material.CHERRY_LEAVES;
                break;
            default:
                log = Material.OAK_LOG;
                leaves = Material.OAK_LEAVES;
        }

        int height = 4 + rnd.nextInt(3);
        BlockData leafData = leaves.createBlockData();
        if (leafData instanceof Leaves l) {
            l.setPersistent(true);
        }

        BlockData logData = log.createBlockData();

        for (int dy = 0; dy < height; dy++) {
            out.add(new ArenaGenerator.Write(x, y + dy, z, logData));
        }

        for (int dy = height - 2; dy <= height + 1; dy++) {
            int radius = dy >= height ? 1 : 2;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if ((!corner || radius != 1 && !rnd.nextBoolean())
                        && (theme != ArenaTheme.SNOWY || dy != height + 1 || dx == 0 && dz == 0)
                        && (dx != 0 || dz != 0 || dy >= height)) {
                        out.add(new ArenaGenerator.Write(x + dx, y + dy, z + dz, leafData));
                    }
                }
            }
        }
    }

    public record Plan(
        ArenaTheme theme,
        int size,
        long seed,
        int ox,
        int oz,
        int[][] heights,
        int floorY,
        Cuboid bounds,
        Pos spawn1,
        Pos spawn2,
        Pos center,
        List<Pos> ffaSpawns,
        boolean ores,
        boolean crystal,
        ColosseumDesign colosseum,
        OpenArenaDesign open,
        net.vortexpvp.build.Voxels show
    ) {
    }

    private record Write(int x, int y, int z, BlockData data) {
    }
}
