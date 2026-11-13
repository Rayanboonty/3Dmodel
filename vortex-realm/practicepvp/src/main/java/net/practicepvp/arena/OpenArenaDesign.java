package net.practicepvp.arena;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.bukkit.block.Biome;
import org.bukkit.util.noise.SimplexOctaveGenerator;

/**
 * Open designed arenas for the sword kits: a big floating plaza with a themed floor, decorations placed with four-way
 * symmetry (both spawns see the same map) and no walls at all - the world border is the edge. Built column by column
 * like {@link ColosseumDesign}, through the same block job.
 */
public final class OpenArenaDesign {
    private final OpenArenaDesign.Style style;
    private final int size;
    private final int half;
    private final int cx;
    private final int cz;
    private final int floorY;
    private final boolean ffa;
    public final int bottomY;
    public final int topY;
    private final int coreDepth;
    private final SimplexOctaveGenerator noise;
    private final SimplexOctaveGenerator detail;
    private final Map<Long, TreeMap<Integer, String>> decor = new HashMap<>();
    private int decorTop;

    public OpenArenaDesign(OpenArenaDesign.Style style, int size, long seed, int cx, int floorY, int cz, int depth, boolean ffa) {
        this.style = style;
        this.size = Math.max(64, size);
        this.half = this.size / 2;
        this.cx = cx;
        this.cz = cz;
        this.floorY = floorY;
        this.ffa = ffa;
        this.coreDepth = Math.max(8, depth);
        this.noise = new SimplexOctaveGenerator(seed ^ 0x5EEDL, 3);
        this.noise.setScale(1.0 / 28.0);
        this.detail = new SimplexOctaveGenerator(seed ^ 0xBEEFL, 2);
        this.detail.setScale(1.0 / 6.0);
        this.decorTop = floorY + 2;
        this.decorate(new Random(seed * 31L + style.ordinal()));
        this.bottomY = floorY - this.coreDepth - 4;
        this.topY = Math.max(this.decorTop + 6, floorY + 32);
    }

    public int minX() {
        return this.cx - this.half;
    }

    public int maxX() {
        return this.cx + this.half - 1;
    }

    public int minZ() {
        return this.cz - this.half;
    }

    public int maxZ() {
        return this.cz + this.half - 1;
    }

    public int spawnOffset() {
        return Math.max(14, Math.min(40, (int)Math.round(this.size * 0.16)));
    }

    public double ffaRadius() {
        return this.half * 0.55;
    }

    // ------------------------------------------------------------------ shape

    /** Superellipse distance: 0 at the centre, 1 at the edge of the square (corners rounded). */
    private double dist(double dx, double dz) {
        double u = Math.abs(dx) / this.half;
        double v = Math.abs(dz) / this.half;
        return Math.pow(Math.pow(u, 6.0) + Math.pow(v, 6.0), 1.0 / 6.0);
    }

    private double edge(int x, int z) {
        return 0.985 + this.noise.noise(x, z, 0.5, 0.5, true) * 0.035;
    }

    /** Surface Y of a column, or Integer.MIN_VALUE outside the island. */
    public int surface(int x, int z) {
        double dx = x - this.cx + 0.5;
        double dz = z - this.cz + 0.5;
        double d = this.dist(dx, dz);
        double e = this.edge(x, z);
        if (d > e) {
            return Integer.MIN_VALUE;
        } else if (d < 0.9) {
            return this.floorY;
        } else {
            double t = (d - 0.9) / Math.max(0.01, e - 0.9);
            double rise = this.style.rimRise * Math.sin(Math.min(1.0, t) * Math.PI * 0.5);
            return this.floorY + (int)Math.round(rise + this.detail.noise(x, z, 0.5, 0.5, true) * (this.style.rimRise > 3 ? 1.5 : 0.5));
        }
    }

    // ------------------------------------------------------------------ columns

    public void column(int x, int z, ColosseumDesign.Sink out) {
        int top = this.surface(x, z);
        long key = key(x, z);
        TreeMap<Integer, String> deco = this.decor.get(key);
        if (top != Integer.MIN_VALUE) {
            double dx = x - this.cx + 0.5;
            double dz = z - this.cz + 0.5;
            double d = this.dist(dx, dz);
            double e = this.edge(x, z);
            double thick = this.coreDepth * Math.sqrt(Math.max(0.0, 1.0 - Math.pow(d / e, 2.0))) + 2.0 + this.detail.noise(x * 0.7, z * 0.7, 0.5, 0.5, true) * 2.0;
            int bottom = Math.max(this.bottomY, this.floorY - (int)Math.round(thick));
            boolean core = d < 0.55;
            boolean rim = d >= 0.9;

            for (int y = bottom; y <= top; y++) {
                String b;
                if (y == top) {
                    b = rim ? this.style.rimTop(this, x, z) : this.style.floor(this, x, z, dx, dz, d);
                } else if (y == bottom) {
                    // the whole underside is bedrock, out to the rim: digging through the island never reaches the void
                    b = "minecraft:bedrock";
                } else if (y >= top - 3) {
                    b = this.style.sub;
                } else if (core && (y == bottom || y <= bottom + 3 && bedrockAt(x, y - bottom, z))) {
                    b = "minecraft:bedrock";
                } else {
                    b = this.style.stone(this, x, y, z);
                }

                out.set(x, y, z, b);
            }

            if (!rim) {
                String under = this.style.underFloor(this, x, z, dx, dz, d);
                if (under != null) {
                    out.set(x, top - 1, z, under);
                }
            }

            String plant = deco == null ? this.style.ground(this, x, z, d, rim) : null;
            if (plant != null) {
                out.set(x, top + 1, z, plant);
            }
        }

        if (deco != null) {
            for (Map.Entry<Integer, String> en : deco.entrySet()) {
                out.set(x, en.getKey(), z, en.getValue());
            }
        }
    }

    private static boolean bedrockAt(int x, int above, int z) {
        if (above <= 0) {
            return true;
        } else {
            double chance = above == 1 ? 0.7 : (above == 2 ? 0.4 : 0.15);
            return hash(x, above, z) < chance * 1000.0;
        }
    }

    static int hash(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h ^= h >>> 13;
        h *= 25214903917L;
        return Math.floorMod(h ^ h >>> 17, 1000);
    }

    private static long key(int x, int z) {
        return (long)x << 32 ^ z & 4294967295L;
    }

    // ------------------------------------------------------------------ decorations

    private void put(int x, int y, int z, String state) {
        this.decor.computeIfAbsent(key(x, z), k -> new TreeMap<>()).put(y, state);
        this.decorTop = Math.max(this.decorTop, y);
    }

    /** Free of the spawns, the lane between them and the FFA ring, and well inside the island. */
    private boolean clear(double dx, double dz, double radius) {
        int so = this.spawnOffset();
        if (Math.hypot(dx - so, dz) < 9.0 + radius || Math.hypot(dx + so, dz) < 9.0 + radius) {
            return false;
        } else if (Math.abs(dz) < 5.0 + radius && Math.abs(dx) < so + 4) {
            return false;
        } else if (this.dist(dx, dz) > 0.86) {
            return false;
        } else {
            if (this.ffa) {
                double r = Math.hypot(dx, dz);
                if (Math.abs(r - this.ffaRadius()) < 5.0 + radius) {
                    return false;
                }
            }

            return Math.hypot(dx, dz) > 8.0 + radius;
        }
    }

    /** Calls {@code at} for the point and its three mirror images (fair for both spawns). */
    private void mirrored(double dx, double dz, double radius, java.util.function.BiConsumer<Integer, Integer> at) {
        double[][] pts = new double[][]{{dx, dz}, {-dx, dz}, {dx, -dz}, {-dx, -dz}};
        java.util.Set<Long> done = new java.util.HashSet<>();

        for (double[] p : pts) {
            if (this.clear(p[0], p[1], radius)) {
                int x = (int)Math.floor(this.cx + p[0]);
                int z = (int)Math.floor(this.cz + p[1]);
                if (done.add(key(x, z))) {
                    at.accept(x, z);
                }
            }
        }
    }

    /** Places features on a ring at relative radius {@code r} (of the half size), {@code count} per quadrant. */
    private void ring(double r, int perQuadrant, double featureRadius, Random rnd, java.util.function.BiConsumer<Integer, Integer> at) {
        for (int i = 0; i < perQuadrant; i++) {
            double a = (Math.PI / 2.0) * (i + 0.5) / perQuadrant;
            double rr = r * this.half * (0.94 + rnd.nextDouble() * 0.12);
            this.mirrored(Math.cos(a) * rr, Math.sin(a) * rr, featureRadius, at);
        }
    }

    private void decorate(Random rnd) {
        int y = this.floorY + 1;
        switch (this.style) {
            case SAKURA:
                this.ring(0.62, 2, 4.0, rnd, (x, z) -> this.cherryTree(x, y, z, rnd));
                this.ring(0.36, 2, 1.0, rnd, (x, z) -> this.lanternPost(x, y, z, "minecraft:stone_brick_wall", "minecraft:lantern[hanging=false]", 2));
                this.mirrored(0.0, this.half * 0.8, 4.0, (x, z) -> this.torii(x, y, z, true));
                this.mirrored(this.half * 0.8, this.half * 0.8, 4.0, (x, z) -> this.cherryTree(x, y, z, rnd));
                break;
            case RUINS:
                this.ring(0.5, 3, 2.0, rnd, (x, z) -> this.column(x, y, z, 3 + rnd.nextInt(7), rnd));
                this.ring(0.74, 2, 3.0, rnd, (x, z) -> this.fallenColumn(x, y, z, rnd));
                this.mirrored(this.half * 0.62, this.half * 0.62, 4.0, (x, z) -> this.arch(x, y, z));
                this.ring(0.3, 1, 1.0, rnd, (x, z) -> this.bush(x, y, z, "minecraft:azalea_leaves[persistent=true]"));
                break;
            case OASIS:
                this.mirrored(0.0, this.half * 0.42, 7.0, (x, z) -> this.pool(x, z, 5));
                this.mirrored(this.half * 0.18, this.half * 0.42, 3.0, (x, z) -> this.palm(x, y, z, rnd));
                this.mirrored(this.half * 0.62, this.half * 0.62, 2.0, (x, z) -> this.obelisk(x, y, z));
                this.ring(0.75, 2, 3.0, rnd, (x, z) -> this.palm(x, y, z, rnd));
                break;
            case FROZEN:
                this.ring(0.7, 2, 3.0, rnd, (x, z) -> this.iceSpike(x, y, z, 7 + rnd.nextInt(8), rnd));
                this.mirrored(0.0, this.half * 0.55, 6.0, (x, z) -> this.colonnade(x, y, z));
                this.ring(0.4, 1, 1.0, rnd, (x, z) -> this.lanternPost(x, y, z, "minecraft:quartz_pillar[axis=y]", "minecraft:sea_lantern", 3));
                break;
            case VOLCANO:
                this.ring(0.68, 2, 3.0, rnd, (x, z) -> this.spire(x, y, z, 8 + rnd.nextInt(8), rnd));
                this.ring(0.42, 2, 2.0, rnd, (x, z) -> this.basaltCluster(x, y, z, rnd));
                break;
            case DEEP_DARK:
                this.mirrored(0.0, this.half * 0.6, 7.0, (x, z) -> this.frame(x, y, z));
                this.ring(0.42, 2, 1.0, rnd, (x, z) -> this.lanternPost(x, y, z, "minecraft:deepslate_brick_wall", "minecraft:soul_lantern[hanging=false]", 3));
                this.ring(0.7, 2, 2.0, rnd, (x, z) -> this.column(x, y, z, 4 + rnd.nextInt(5), rnd));
                break;
            case MUSHROOM:
                this.ring(0.64, 2, 5.0, rnd, (x, z) -> {
                    if (rnd.nextBoolean()) {
                        this.redMushroom(x, y, z, 5 + rnd.nextInt(3));
                    } else {
                        this.brownMushroom(x, y, z, 5 + rnd.nextInt(3));
                    }
                });
                break;
            case SKY_CASTLE:
                this.mirrored(this.half * 0.66, this.half * 0.66, 6.0, (x, z) -> this.tower(x, y, z));
                this.mirrored(0.0, this.half * 0.5, 4.0, (x, z) -> this.well(x, y, z));
                this.ring(0.36, 2, 1.0, rnd, (x, z) -> this.bannerPole(x, y, z, rnd));
        }
    }

    private void cherryTree(int x, int y, int z, Random rnd) {
        int h = 5 + rnd.nextInt(3);

        for (int i = 0; i < h; i++) {
            this.put(x, y + i, z, "minecraft:cherry_log[axis=y]");
        }

        int top = y + h;

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    double r = Math.sqrt(dx * dx + dz * dz + dy * dy * 3.0);
                    if (r <= 4.2 - (dy > 0 ? 1.4 : 0.0) && hash(x + dx, top + dy, z + dz) < 880) {
                        this.put(x + dx, top + dy, z + dz, "minecraft:cherry_leaves[persistent=true]");
                    }
                }
            }
        }

        for (int i = 0; i < 6; i++) {
            int dx = rnd.nextInt(7) - 3;
            int dz = rnd.nextInt(7) - 3;
            this.put(x + dx, top - 3, z + dz, "minecraft:cherry_leaves[persistent=true]");
        }
    }

    private void lanternPost(int x, int y, int z, String post, String light, int h) {
        for (int i = 0; i < h; i++) {
            this.put(x, y + i, z, post);
        }

        this.put(x, y + h, z, light);
    }

    private void torii(int x, int y, int z, boolean alongX) {
        int w = 4;

        for (int i = 0; i < 6; i++) {
            this.put(x - w, y + i, z, "minecraft:crimson_hyphae[axis=y]");
            this.put(x + w, y + i, z, "minecraft:crimson_hyphae[axis=y]");
        }

        for (int dx = -w - 1; dx <= w + 1; dx++) {
            this.put(x + dx, y + 6, z, "minecraft:red_concrete");
            this.put(x + dx, y + 7, z, Math.abs(dx) == w + 1 ? "minecraft:dark_oak_slab[type=bottom]" : "minecraft:dark_oak_planks");
            if (Math.abs(dx) < w) {
                this.put(x + dx, y + 4, z, "minecraft:red_concrete");
            }
        }

        this.put(x, y + 5, z, "minecraft:dark_oak_planks");
    }

    private void column(int x, int y, int z, int h, Random rnd) {
        String mat = this.style == OpenArenaDesign.Style.DEEP_DARK ? "minecraft:deepslate_bricks" : "minecraft:stone_bricks";
        String base = this.style == OpenArenaDesign.Style.DEEP_DARK ? "minecraft:chiseled_deepslate" : "minecraft:chiseled_stone_bricks";

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                this.put(x + dx, y, z + dz, base);
            }
        }

        for (int i = 1; i <= h; i++) {
            String m = this.style == OpenArenaDesign.Style.RUINS && hash(x, y + i, z) < 300 ? "minecraft:mossy_stone_bricks" : mat;
            this.put(x, y + i, z, m);
        }

        if (rnd.nextBoolean()) {
            this.put(x, y + h + 1, z, this.style == OpenArenaDesign.Style.DEEP_DARK ? "minecraft:deepslate_brick_slab[type=bottom]" : "minecraft:stone_brick_slab[type=bottom]");
        } else if (this.style == OpenArenaDesign.Style.RUINS) {
            this.put(x + 1, y + h, z, "minecraft:vine[west=true]");
        }
    }

    private void fallenColumn(int x, int y, int z, Random rnd) {
        boolean alongX = rnd.nextBoolean();

        for (int i = -3; i <= 3; i++) {
            int bx = alongX ? x + i : x;
            int bz = alongX ? z : z + i;
            this.put(bx, y, bz, i % 3 == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:mossy_stone_bricks");
        }
    }

    private void arch(int x, int y, int z) {
        for (int i = 0; i < 6; i++) {
            this.put(x - 3, y + i, z, "minecraft:stone_bricks");
            this.put(x + 3, y + i, z, "minecraft:mossy_stone_bricks");
        }

        for (int dx = -3; dx <= 3; dx++) {
            this.put(x + dx, y + 6, z, Math.abs(dx) == 3 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
        }

        this.put(x - 2, y + 5, z, "minecraft:stone_brick_stairs[facing=east,half=top]");
        this.put(x + 2, y + 5, z, "minecraft:stone_brick_stairs[facing=west,half=top]");
        this.put(x, y + 7, z, "minecraft:moss_carpet");
    }

    private void bush(int x, int y, int z, String leaves) {
        this.put(x, y, z, leaves);
        this.put(x + 1, y, z, leaves);
        this.put(x, y, z + 1, leaves);
        this.put(x, y + 1, z, leaves);
    }

    private void pool(int x, int z, int r) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r) {
                    this.put(x + dx, this.floorY, z + dz, "minecraft:water");
                    this.put(x + dx, this.floorY - 1, z + dz, "minecraft:sand");
                } else if (d <= r + 1.2) {
                    this.put(x + dx, this.floorY, z + dz, "minecraft:smooth_sandstone");
                }
            }
        }
    }

    private void palm(int x, int y, int z, Random rnd) {
        int h = 6 + rnd.nextInt(3);
        int lean = rnd.nextBoolean() ? 1 : -1;

        for (int i = 0; i < h; i++) {
            this.put(x + (i > h / 2 ? lean : 0), y + i, z, "minecraft:jungle_log[axis=y]");
        }

        int tx = x + lean;
        int top = y + h;
        this.put(tx, top, z, "minecraft:jungle_leaves[persistent=true]");
        int[][] arms = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

        for (int[] a : arms) {
            for (int k = 1; k <= 3; k++) {
                this.put(tx + a[0] * k, top - (k == 3 ? 1 : 0), z + a[1] * k, "minecraft:jungle_leaves[persistent=true]");
            }
        }

        this.put(tx + 1, top - 1, z, "minecraft:cocoa[age=2,facing=west]");
    }

    private void obelisk(int x, int y, int z) {
        for (int i = 0; i < 9; i++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    this.put(x + dx, y + i, z + dz, i == 0 ? "minecraft:chiseled_sandstone" : (i % 4 == 0 ? "minecraft:cut_sandstone" : "minecraft:smooth_sandstone"));
                }
            }
        }

        this.put(x, y + 9, z, "minecraft:chiseled_red_sandstone");
        this.put(x + 1, y + 9, z + 1, "minecraft:chiseled_red_sandstone");
    }

    private void iceSpike(int x, int y, int z, int h, Random rnd) {
        for (int i = 0; i < h; i++) {
            double r = 2.2 * (1.0 - (double)i / h) + 0.4;
            int ri = (int)Math.ceil(r);

            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        this.put(x + dx, y + i, z + dz, i % 5 == 4 ? "minecraft:blue_ice" : "minecraft:packed_ice");
                    }
                }
            }
        }
    }

    private void colonnade(int x, int y, int z) {
        for (int k = -2; k <= 2; k++) {
            int px = x + k * 4;

            for (int i = 0; i < 6; i++) {
                this.put(px, y + i, z, i == 0 || i == 5 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_pillar[axis=y]");
            }
        }

        for (int dx = -9; dx <= 9; dx++) {
            this.put(x + dx, y + 6, z, "minecraft:smooth_quartz");
            if (dx % 2 == 0) {
                this.put(x + dx, y + 7, z, "minecraft:quartz_slab[type=bottom]");
            }
        }
    }

    private void spire(int x, int y, int z, int h, Random rnd) {
        for (int i = 0; i < h; i++) {
            int r = i < h / 3 ? 1 : 0;

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    this.put(x + dx, y + i, z + dz, hash(x + dx, y + i, z + dz) < 250 ? "minecraft:crying_obsidian" : "minecraft:obsidian");
                }
            }
        }

        this.put(x, y + h, z, "minecraft:end_rod[facing=up]");
    }

    private void basaltCluster(int x, int y, int z, Random rnd) {
        for (int k = 0; k < 5; k++) {
            int bx = x + rnd.nextInt(5) - 2;
            int bz = z + rnd.nextInt(5) - 2;
            int h = 1 + rnd.nextInt(4);

            for (int i = 0; i < h; i++) {
                this.put(bx, y + i, bz, "minecraft:basalt[axis=y]");
            }
        }
    }

    private void frame(int x, int y, int z) {
        for (int i = 0; i < 9; i++) {
            this.put(x - 5, y + i, z, "minecraft:reinforced_deepslate");
            this.put(x + 5, y + i, z, "minecraft:reinforced_deepslate");
        }

        for (int dx = -5; dx <= 5; dx++) {
            this.put(x + dx, y + 9, z, "minecraft:reinforced_deepslate");
        }

        for (int dx = -4; dx <= 4; dx++) {
            this.put(x + dx, y + 8, z, "minecraft:deepslate_tile_slab[type=top]");
        }

        this.put(x - 6, y, z, "minecraft:candle[candles=3,lit=true]");
        this.put(x + 6, y, z, "minecraft:candle[candles=4,lit=true]");
    }

    private void redMushroom(int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            this.put(x, y + i, z, "minecraft:mushroom_stem");
        }

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    boolean shell = dy == 2 ? r <= 2.0 : (dy == 1 ? r <= 3.2 && r > 1.8 : r <= 3.4 && r > 2.6);
                    if (shell) {
                        this.put(x + dx, y + h - 1 + dy, z + dz, "minecraft:red_mushroom_block");
                    }
                }
            }
        }
    }

    private void brownMushroom(int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            this.put(x, y + i, z, "minecraft:mushroom_stem");
        }

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 6) {
                    this.put(x + dx, y + h, z + dz, "minecraft:brown_mushroom_block");
                }
            }
        }
    }

    private void tower(int x, int y, int z) {
        int r = 4;
        int h = 16;

        for (int i = 0; i <= h; i++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= r + 0.3 && d > r - 1.2) {
                        boolean window = i > 3 && i % 5 == 0 && (dx == 0 || dz == 0);
                        this.put(x + dx, y + i, z + dz, window ? "minecraft:glass_pane" : (hash(x + dx, y + i, z + dz) < 180 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks"));
                    } else if (d <= r - 1.2 && i == 0) {
                        this.put(x + dx, y + i, z + dz, "minecraft:spruce_planks");
                    }
                }
            }
        }

        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r + 1.3) {
                    this.put(x + dx, y + h + 1, z + dz, "minecraft:stone_bricks");
                    if (d > r && (dx + dz) % 2 == 0) {
                        this.put(x + dx, y + h + 2, z + dz, "minecraft:stone_brick_wall");
                    }
                }
            }
        }

        for (int i = 0; i < 4; i++) {
            this.put(x, y + h + 2 + i, z, i == 3 ? "minecraft:red_banner[rotation=0]" : "minecraft:spruce_fence");
        }
    }

    private void well(int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                this.put(x + dx, y - 1, z + dz, rim ? "minecraft:stone_bricks" : "minecraft:water");
                if (rim) {
                    this.put(x + dx, y, z + dz, "minecraft:stone_brick_wall");
                }
            }
        }

        for (int i = 1; i <= 3; i++) {
            this.put(x - 2, y + i, z - 2, "minecraft:spruce_fence");
            this.put(x + 2, y + i, z + 2, "minecraft:spruce_fence");
            this.put(x - 2, y + i, z + 2, "minecraft:spruce_fence");
            this.put(x + 2, y + i, z - 2, "minecraft:spruce_fence");
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                this.put(x + dx, y + 4, z + dz, "minecraft:spruce_slab[type=bottom]");
            }
        }

        this.put(x, y + 3, z, "minecraft:lantern[hanging=true]");
    }

    private void bannerPole(int x, int y, int z, Random rnd) {
        String[] colors = new String[]{"red", "blue", "yellow", "white"};

        for (int i = 0; i < 4; i++) {
            this.put(x, y + i, z, "minecraft:dark_oak_fence");
        }

        this.put(x, y + 4, z, "minecraft:" + colors[rnd.nextInt(colors.length)] + "_banner[rotation=" + rnd.nextInt(16) + "]");
    }

    // ------------------------------------------------------------------ styles

    public static enum Style {
        SAKURA("Sakura Dojo", Biome.CHERRY_GROVE, "minecraft:dirt", 2) {
            @Override
            String floor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
                double rr = Math.hypot(dx, dz) / d.half;
                if (Math.abs(dx) < 7.0 && Math.abs(dz) < 7.0) {
                    return (Math.floorMod(x + z, 2) == 0) ? "minecraft:dark_oak_planks" : "minecraft:spruce_planks";
                } else if (Math.abs(rr - 0.46) < 0.022 || Math.abs(dz) < 1.5 && rr > 0.1) {
                    return hash(x, 0, z) < 250 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks";
                } else {
                    return d.noise.noise(x, z, 0.5, 0.5, true) > 0.45 ? "minecraft:moss_block" : "minecraft:grass_block";
                }
            }

            @Override
            String ground(OpenArenaDesign d, int x, int z, double dist, boolean rim) {
                int h = hash(x, 7, z);
                if (h < 260) {
                    String[] f = new String[]{"north", "east", "south", "west"};
                    return "minecraft:pink_petals[facing=" + f[h & 3] + ",flower_amount=" + (1 + h % 4) + "]";
                } else {
                    return h < 300 ? "minecraft:short_grass" : null;
                }
            }
        },
        RUINS("Ancient Ruins", Biome.FOREST, "minecraft:dirt", 3) {
            @Override
            String floor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
                double n = d.noise.noise(x, z, 0.5, 0.5, true);
                if (n > 0.38) {
                    return "minecraft:grass_block";
                } else if (n > 0.28) {
                    return "minecraft:moss_block";
                } else {
                    int h = hash(x, 1, z);
                    return h < 380 ? "minecraft:stone_bricks" : (h < 620 ? "minecraft:mossy_stone_bricks" : (h < 780 ? "minecraft:cracked_stone_bricks" : (h < 900 ? "minecraft:cobblestone" : "minecraft:mossy_cobblestone")));
                }
            }

            @Override
            String ground(OpenArenaDesign d, int x, int z, double dist, boolean rim) {
                int h = hash(x, 9, z);
                return h < 40 ? "minecraft:moss_carpet" : (h < 70 && d.noise.noise(x, z, 0.5, 0.5, true) > 0.38 ? "minecraft:fern" : null);
            }
        },
        OASIS("Desert Oasis", Biome.DESERT, "minecraft:sandstone", 2) {
            @Override
            String floor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
                double a = Math.atan2(dz, dx);
                double rr = Math.hypot(dx, dz) / d.half;
                boolean star = Math.abs(Math.sin(a * 4.0)) < 0.06 && rr < 0.8;
                if (rr < 0.12) {
                    return Math.floorMod(x - z, 3) == 0 ? "minecraft:chiseled_sandstone" : "minecraft:cut_sandstone";
                } else if (star || Math.abs(rr - 0.3) < 0.015) {
                    return "minecraft:cut_sandstone";
                } else {
                    return d.noise.noise(x, z, 0.5, 0.5, true) > 0.5 ? "minecraft:smooth_sandstone" : "minecraft:sand";
                }
            }

            @Override
            String ground(OpenArenaDesign d, int x, int z, double dist, boolean rim) {
                int h = hash(x, 5, z);
                return h < 6 ? "minecraft:dead_bush" : (h < 30 ? "minecraft:short_dry_grass" : null);
            }
        },
        FROZEN("Frozen Palace", Biome.SNOWY_PLAINS, "minecraft:packed_ice", 3) {
            @Override
            String floor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
                double rr = Math.hypot(dx, dz) / d.half;
                if (rr < 0.38) {
                    return Math.floorMod(Math.floorDiv(x, 2) + Math.floorDiv(z, 2), 2) == 0 ? "minecraft:polished_diorite" : "minecraft:smooth_quartz";
                } else if (Math.abs(rr - 0.4) < 0.02) {
                    return "minecraft:light_blue_concrete";
                } else {
                    return "minecraft:snow_block";
                }
            }

            @Override
            String ground(OpenArenaDesign d, int x, int z, double dist, boolean rim) {
                return hash(x, 3, z) < 180 && Math.hypot(x - d.cx, z - d.cz) / d.half > 0.42 ? "minecraft:snow[layers=1]" : null;
            }

            @Override
            String rimTop(OpenArenaDesign d, int x, int z) {
                return "minecraft:snow_block";
            }
        },
        VOLCANO("Volcanic Crater", Biome.BASALT_DELTAS, "minecraft:blackstone", 8) {
            @Override
            String floor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
                if (this.crack(d, x, z)) {
                    return "minecraft:orange_stained_glass";
                } else {
                    int h = hash(x, 2, z);
                    return h < 450 ? "minecraft:blackstone" : (h < 750 ? "minecraft:polished_blackstone" : (h < 900 ? "minecraft:basalt[axis=y]" : "minecraft:smooth_basalt"));
                }
            }

            @Override
            String underFloor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
                return this.crack(d, x, z) ? "minecraft:shroomlight" : null;
            }

            private boolean crack(OpenArenaDesign d, int x, int z) {
                return Math.abs(d.noise.noise(x * 1.7, z * 1.7, 0.5, 0.5, true)) < 0.035;
            }

            @Override
            String rimTop(OpenArenaDesign d, int x, int z) {
                return hash(x, 4, z) < 300 ? "minecraft:basalt[axis=y]" : "minecraft:blackstone";
            }

            @Override
            String stone(OpenArenaDesign d, int x, int y, int z) {
                return hash(x, y, z) < 150 ? "minecraft:basalt[axis=y]" : "minecraft:blackstone";
            }
        },
        DEEP_DARK("Deep Dark Plaza", Biome.DEEP_DARK, "minecraft:deepslate", 2) {
            @Override
            String floor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
                if (d.noise.noise(x, z, 0.5, 0.5, true) > 0.42) {
                    return "minecraft:sculk";
                } else {
                    return Math.floorMod(Math.floorDiv(x, 3) + Math.floorDiv(z, 3), 2) == 0 ? "minecraft:deepslate_tiles" : "minecraft:polished_deepslate";
                }
            }

            @Override
            String stone(OpenArenaDesign d, int x, int y, int z) {
                return hash(x, y, z) < 120 ? "minecraft:tuff" : "minecraft:deepslate";
            }
        },
        MUSHROOM("Mushroom Isle", Biome.MUSHROOM_FIELDS, "minecraft:dirt", 3) {
            @Override
            String floor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
                double n = d.noise.noise(x, z, 0.5, 0.5, true);
                return n > 0.45 ? "minecraft:podzol" : (n < -0.55 ? "minecraft:moss_block" : "minecraft:mycelium");
            }

            @Override
            String ground(OpenArenaDesign d, int x, int z, double dist, boolean rim) {
                int h = hash(x, 6, z);
                return h < 12 ? "minecraft:red_mushroom" : (h < 24 ? "minecraft:brown_mushroom" : null);
            }
        },
        SKY_CASTLE("Sky Castle Courtyard", Biome.PLAINS, "minecraft:dirt", 2) {
            @Override
            String floor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
                double rr = Math.hypot(dx, dz) / d.half;
                if (rr < 0.5) {
                    return Math.floorMod(x + z, 4) < 2 ? "minecraft:stone_bricks" : "minecraft:polished_andesite";
                } else if (Math.abs(dx) < 2.0 || Math.abs(dz) < 2.0) {
                    return "minecraft:gravel";
                } else {
                    return "minecraft:grass_block";
                }
            }

            @Override
            String ground(OpenArenaDesign d, int x, int z, double dist, boolean rim) {
                int h = hash(x, 8, z);
                if (Math.hypot(x - d.cx, z - d.cz) / d.half < 0.52) {
                    return null;
                } else {
                    return h < 120 ? "minecraft:short_grass" : (h < 135 ? "minecraft:poppy" : (h < 150 ? "minecraft:dandelion" : null));
                }
            }
        };

        public final String display;
        public final Biome biome;
        final String sub;
        final int rimRise;

        private Style(String display, Biome biome, String sub, int rimRise) {
            this.display = display;
            this.biome = biome;
            this.sub = sub;
            this.rimRise = rimRise;
        }

        abstract String floor(OpenArenaDesign d, int x, int z, double dx, double dz, double r);

        String underFloor(OpenArenaDesign d, int x, int z, double dx, double dz, double r) {
            return null;
        }

        String ground(OpenArenaDesign d, int x, int z, double dist, boolean rim) {
            return null;
        }

        String rimTop(OpenArenaDesign d, int x, int z) {
            return this.floor(d, x, z, x - d.cx, z - d.cz, 0.95);
        }

        String stone(OpenArenaDesign d, int x, int y, int z) {
            int h = hash(x, y, z);
            return y < 0 ? "minecraft:deepslate" : (h < 70 ? "minecraft:andesite" : (h < 100 ? "minecraft:tuff" : "minecraft:stone"));
        }
    }
}
