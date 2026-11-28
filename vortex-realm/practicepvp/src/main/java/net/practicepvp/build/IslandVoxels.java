package net.practicepvp.build;

import java.util.ArrayList;
import java.util.List;

/**
 * The toolkit for floating-island builds (Eco Spawn, Vortex Isles): an island with an irregular outline, rolling hills
 * that rise toward the rim with stone showing on steep slopes, a deep eroded underside that breaks into rock spikes,
 * small floating islets, branching trees with clumpy crowns, winding lantern-lit paths, a stream that falls off the
 * edge and wild flowers. Subclasses flatten pads for what they build ({@link #pad}) and place it on the terrain.
 */
public abstract class IslandVoxels extends MegaVoxels {
    protected final int plaza;
    protected final double baseEdge;
    private final int[][] top;
    private final double[][] edge;
    /** Flat pads: {x, z, radius, height, square(1)/round(0)}. */
    private final List<int[]> pads = new ArrayList<>();
    /** Places trees must stay away from: {x, z, radius}. */
    private final List<double[]> keepClear = new ArrayList<>();

    protected IslandVoxels(int cx, int floorY, int cz, int radius, int yLo, int yHi, long seed, int plaza, double baseEdge) {
        super(cx, floorY, cz, radius, yLo, yHi, seed);
        int n = 2 * radius + 1;
        this.top = new int[n][n];
        this.edge = new double[n][n];
        this.plaza = plaza;
        this.baseEdge = baseEdge;
    }

    /** A flat area at height 0 (or {@code h}), blending into the hills around it. */
    protected void pad(int x, int z, int r, boolean square) {
        this.pads.add(new int[]{x, z, r, 0, square ? 1 : 0});
        this.keepClear.add(new double[]{x, z, r + 3});
    }

    protected void keepClear(double x, double z, double r) {
        this.keepClear.add(new double[]{x, z, r});
    }

    protected double islandEdge(double x, double z) {
        double a = Math.atan2(z, x);
        return this.baseEdge + (noise(5L, Math.cos(a) * 30.0, Math.sin(a) * 30.0) - 0.5) * this.baseEdge * 0.24;
    }

    protected int h(int x, int z) {
        return x >= -this.R && x <= this.R && z >= -this.R && z <= this.R ? this.top[x + this.R][z + this.R] : Integer.MIN_VALUE;
    }

    protected boolean land(int x, int z) {
        return this.h(x, z) != Integer.MIN_VALUE;
    }

    /** Builds the ground: heights, then the terrain blocks and the underside. Call after the pads are set. */
    protected void ground() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = dist(x, z);
                double e = this.islandEdge(x, z);
                this.edge[x + this.R][z + this.R] = e;
                int hgt;
                if (d > e) {
                    hgt = Integer.MIN_VALUE;
                } else if (d <= this.plaza + 4) {
                    hgt = 0;
                } else {
                    double t = Math.min(1.0, (d - this.plaza - 4) / 22.0);
                    double hills = noise(21L, x * 1.3, z * 1.3) * 10.0 + noise(23L, x * 3.0, z * 3.0) * 2.5 - 3.5;
                    double rim = Math.max(0.0, d - (e - 4.0)) * 0.8;
                    hgt = Math.max(-1, (int)Math.round(Math.max(0.0, hills * t * t * (3.0 - 2.0 * t)) - rim));
                }

                if (hgt != Integer.MIN_VALUE) {
                    for (int[] p : this.pads) {
                        double pd = p[4] == 1 ? Math.max(Math.abs(x - p[0]), Math.abs(z - p[1])) : dist(x - p[0], z - p[1]);
                        if (pd <= p[2]) {
                            hgt = p[3];
                        } else if (pd <= p[2] + 6) {
                            hgt = Math.min(hgt, p[3] + (int)Math.round((pd - p[2]) * 0.8));
                        }
                    }
                }

                this.top[x + this.R][z + this.R] = hgt;
            }
        }

        this.terrain();
        this.underside();
    }

    private void terrain() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int t = this.h(x, z);
                if (t == Integer.MIN_VALUE) {
                    continue;
                }

                // the steepest drop to a neighbour inside the island: cliffs show their stone
                int drop = 0;
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nb = this.h(x + o[0], z + o[1]);
                    drop = Math.max(drop, nb == Integer.MIN_VALUE ? 0 : t - nb);
                }

                double n = noise(31L, x * 2.0, z * 2.0);
                for (int y = -3; y <= t; y++) {
                    String b;
                    if (y == t) {
                        if (drop >= 3) {
                            b = n > 0.55 ? "andesite" : (n < 0.3 ? "tuff" : "stone");
                        } else if (drop == 2) {
                            b = n > 0.5 ? "coarse_dirt" : "moss_block";
                        } else {
                            b = n > 0.74 ? "moss_block" : (n < 0.2 ? "podzol" : "grass_block");
                        }
                    } else if (t - y <= 2 && drop < 3) {
                        b = "dirt";
                    } else {
                        double v = noise(37L, x * 3.0 + y, z * 3.0 - y);
                        b = v > 0.66 ? "andesite" : (v < 0.24 ? "tuff" : (v < 0.34 ? "cobblestone" : "stone"));
                    }

                    this.set(x, y, z, b);
                }
            }
        }
    }

    /** Deepest in the middle, shallow at the rim, broken into spikes; dirt and roots, then stone, deepslate, blackstone. */
    private void underside() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                if (!this.land(x, z)) {
                    continue;
                }

                double e = this.edge[x + this.R][z + this.R];
                double f = Math.max(0.0, 1.0 - dist(x, z) / e);
                double spikes = Math.pow(noise(11L, x * 2.6, z * 2.6), 3.0) * 40.0;
                double dd = 4.0 + Math.pow(f, 0.8) * 34.0 * (0.6 + 0.4 * noise(14L, x * 1.4, z * 1.4)) + spikes * Math.sqrt(f);
                int deep = Math.min(-this.yLo - 2, (int)Math.round(dd));
                for (int k = 4; k <= deep; k++) {
                    double rel = (double)k / Math.max(1, deep);
                    double v = noise(18L, x * 2.4 + k * 1.7, z * 2.4 - k);
                    String b = rel < 0.16 ? (v > 0.5 ? "dirt" : "coarse_dirt")
                        : (rel < 0.6 ? (v > 0.68 ? "andesite" : (v < 0.22 ? "tuff" : "stone"))
                        : (rel < 0.9 ? (v > 0.55 ? "deepslate" : "cobbled_deepslate") : (v > 0.5 ? "blackstone" : "deepslate")));
                    this.set(x, -k, z, b);
                }

                if (deep < 10 && this.rnd.nextDouble() < 0.14) {
                    int len = 1 + this.rnd.nextInt(3);
                    for (int i = 1; i <= len; i++) {
                        this.set(x, -deep - i, z, "hanging_roots");
                    }
                }
            }
        }
    }

    /** Small floating islets with a tree each: {x, z, radius}. */
    protected void islets(int[][] spots) {
        for (int[] s : spots) {
            int y = 2 + this.rnd.nextInt(9) - 4;
            for (int x = -s[2] - 2; x <= s[2] + 2; x++) {
                for (int z = -s[2] - 2; z <= s[2] + 2; z++) {
                    double e = s[2] + (noise(91L, (s[0] + x) * 2.0, (s[1] + z) * 2.0) - 0.5) * 3.0;
                    double d = dist(x, z);
                    if (d <= e) {
                        this.set(s[0] + x, y, s[1] + z, "grass_block");
                        this.set(s[0] + x, y - 1, s[1] + z, "dirt");
                        int deep = (int)Math.round(3 + (1.0 - d / e) * s[2] * 2.2 * (0.7 + 0.5 * noise(93L, x * 3.0, z * 3.0)));
                        for (int k = 2; k <= deep; k++) {
                            this.set(s[0] + x, y - k, s[1] + z, k > deep * 0.7 ? "deepslate" : (k % 3 == 0 ? "andesite" : "stone"));
                        }
                    }
                }
            }

            this.bigTree(s[0], y + 1, s[1], this.rnd.nextBoolean() ? "cherry" : "oak", 5, 0.8);
        }
    }

    /** A stream from the plaza edge toward {@code deg} that runs off the island and falls as a waterfall. */
    protected void stream(double deg) {
        double ang = Math.toRadians(deg);
        double edgeAt = this.islandEdge(Math.cos(ang) * this.baseEdge, Math.sin(ang) * this.baseEdge);
        int lastX = 0;
        int lastZ = 0;
        for (double r = this.plaza + 1.0; r <= edgeAt + 2.0; r += 0.5) {
            double wob = Math.sin(r * 0.18) * 3.0;
            double x = Math.cos(ang) * r - Math.sin(ang) * wob;
            double z = Math.sin(ang) * r + Math.cos(ang) * wob;
            for (int w = -2; w <= 2; w++) {
                int bx = (int)Math.round(x - Math.sin(ang) * w);
                int bz = (int)Math.round(z + Math.cos(ang) * w);
                if (!this.land(bx, bz)) {
                    continue;
                }

                for (int y = 1; y <= 12; y++) {
                    this.set(bx, y, bz, null);
                }

                if (Math.abs(w) == 2) {
                    this.set(bx, 0, bz, this.rnd.nextDouble() < 0.5 ? "mossy_cobblestone" : "moss_block");
                } else {
                    this.set(bx, -1, bz, Math.abs(w) == 1 ? "mud" : "gravel");
                    this.set(bx, 0, bz, "water");
                    lastX = bx;
                    lastZ = bz;
                }
            }
        }

        for (int w = -1; w <= 1; w++) {
            for (int y = 0; y >= this.yLo + 2; y--) {
                this.setIfEmpty(lastX + w, y, lastZ + w, "water");
            }
        }

        // a little arched bridge where the ring road crosses the stream
        double br = this.plaza + 3.5;
        double bx0 = Math.cos(ang) * br;
        double bz0 = Math.sin(ang) * br;
        for (int i = -4; i <= 4; i++) {
            for (int w = -1; w <= 1; w++) {
                int x = (int)Math.round(bx0 + Math.cos(ang) * i + Math.cos(ang + Math.PI / 2) * w);
                int z = (int)Math.round(bz0 + Math.sin(ang) * i + Math.sin(ang + Math.PI / 2) * w);
                int y = Math.abs(i) <= 2 ? 1 : 0;
                this.set(x, y + 1, z, Math.abs(i) <= 2 ? "spruce_planks" : "spruce_slab[type=bottom]");
                if (Math.abs(w) == 1) {
                    this.set(x, y + 2, z, "spruce_fence");
                }
            }
        }

        this.keepClear(bx0, bz0, 4.0);
    }

    /** The ring road around the plaza. */
    protected void ringRoad() {
        for (int a = 0; a < 360; a++) {
            for (double r = this.plaza + 1.6; r <= this.plaza + 4.4; r += 0.5) {
                this.pathBlock(px(r, a), pz(r, a));
            }
        }
    }

    /** A winding path from the plaza toward {@code deg}, lit by lanterns on fence posts. */
    protected void path(double deg, int halfWidth, int from, int to) {
        double ang = Math.toRadians(deg);
        for (double r = from; r <= to; r += 0.5) {
            double wob = r < this.plaza + 6 ? 0.0 : Math.sin(r * 0.12 + deg) * 2.0;
            double x = Math.cos(ang) * r - Math.sin(ang) * wob;
            double z = Math.sin(ang) * r + Math.cos(ang) * wob;
            for (int w = -halfWidth; w <= halfWidth; w++) {
                this.pathBlock((int)Math.round(x - Math.sin(ang) * w), (int)Math.round(z + Math.cos(ang) * w));
            }

            if (Math.floorMod((int)Math.round(r * 2), 3) == 0) {
                this.keepClear(x, z, halfWidth + 3);
            }

            if (halfWidth >= 2 && Math.floorMod((int)Math.round(r * 2), 22) == 0 && r > this.plaza + 7) {
                int side = Math.floorMod((int)Math.round(r / 11.0), 2) == 0 ? 1 : -1;
                int lx = (int)Math.round(x - Math.sin(ang) * (halfWidth + 1) * side);
                int lz = (int)Math.round(z + Math.cos(ang) * (halfWidth + 1) * side);
                int t = this.h(lx, lz);
                if (t != Integer.MIN_VALUE) {
                    this.set(lx, t + 1, lz, "spruce_fence");
                    this.set(lx, t + 2, lz, "spruce_fence");
                    this.set(lx, t + 3, lz, "lantern");
                }
            }
        }
    }

    protected void pathBlock(int x, int z) {
        int t = this.h(x, z);
        if (t == Integer.MIN_VALUE || dist(x, z) <= this.plaza + 1.4) {
            return;
        }

        String cur = this.get(x, t, z);
        if (cur != null && (cur.startsWith("water") || cur.startsWith("spruce") || cur.contains("brick") || cur.contains("cobble") || cur.contains("quartz"))) {
            return;
        }

        double n = noise(61L, x * 3.0, z * 3.0);
        this.set(x, t, z, n > 0.7 ? "gravel" : (n < 0.2 ? "coarse_dirt" : "dirt_path"));
        String above = this.get(x, t + 1, z);
        if (above != null && !above.contains("lantern") && !above.contains("fence")) {
            this.set(x, t + 1, z, null);
        }
    }

    // ------------------------------------------------------------------ trees and flowers

    /** A big tree: a leaning trunk that splits into branches, each with a clumpy crown, and root flares at the foot. */
    protected void bigTree(int x, int y, int z, String kind, int trunk, double scale) {
        String logB = kind + "_log";
        String leafB = kind + "_leaves[persistent=true]";
        double lx = (this.rnd.nextDouble() - 0.5) * 2.0;
        double lz = (this.rnd.nextDouble() - 0.5) * 2.0;
        for (int i = 0; i < trunk; i++) {
            int tx = x + (int)Math.round(lx * i / trunk);
            int tz = z + (int)Math.round(lz * i / trunk);
            this.set(tx, y + i, tz, logB);
            if (i == 0) {
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (this.rnd.nextDouble() < 0.6) {
                        this.set(tx + o[0], y, tz + o[1], logB + "[axis=" + (o[0] != 0 ? "x" : "z") + "]");
                    }
                }
            }
        }

        int topX = x + (int)Math.round(lx);
        int topZ = z + (int)Math.round(lz);
        int topY = y + trunk;
        int branches = 3 + this.rnd.nextInt(2);
        for (int b = 0; b < branches; b++) {
            double a = b * Math.PI * 2 / branches + this.rnd.nextDouble() * 0.8;
            double len = (3.0 + this.rnd.nextDouble() * 2.0) * scale;
            double ex = topX + Math.cos(a) * len;
            double ez = topZ + Math.sin(a) * len;
            double ey = topY + 1.5 + this.rnd.nextDouble() * 2.0;
            this.line(topX, topY - 1, topZ, ex, ey, ez, logB);
            this.crown(ex, ey + 1.0, ez, (2.4 + this.rnd.nextDouble()) * scale, leafB);
        }

        this.crown(topX, topY + 2.5, topZ, 3.2 * scale, leafB);
    }

    protected void crown(double cx0, double cy0, double cz0, double r, String leaf) {
        int n = (int)Math.ceil(r) + 1;
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 2.2 + dz * dz);
                    double jag = noise(77L, (cx0 + dx) * 4.0 + dy * 7.0, (cz0 + dz) * 4.0 - dy * 3.0);
                    if (d <= r + 0.3 - jag * 0.8) {
                        this.setIfEmpty((int)Math.round(cx0) + dx, (int)Math.round(cy0) + dy, (int)Math.round(cz0) + dz, leaf);
                    }
                }
            }
        }
    }

    /** Scatters {@code count} big trees and conifers over the hills, away from paths, pads and the plaza. */
    protected void trees(int count, String... kinds) {
        int placed = 0;
        int lim = (int)this.baseEdge - 2;
        for (int tries = 0; tries < count * 25 && placed < count; tries++) {
            int x = this.rnd.nextInt(2 * lim + 1) - lim;
            int z = this.rnd.nextInt(2 * lim + 1) - lim;
            int t = this.h(x, z);
            if (t == Integer.MIN_VALUE || dist(x, z) < this.plaza + 8 || dist(x, z) > this.islandEdge(x, z) - 4) {
                continue;
            }

            boolean clear = true;
            for (double[] k : this.keepClear) {
                clear &= dist(x - k[0], z - k[1]) > k[2];
            }

            String cur = this.get(x, t, z);
            if (!clear || cur == null || !(cur.equals("grass_block") || cur.equals("moss_block") || cur.equals("podzol"))) {
                continue;
            }

            String k = kinds[placed % kinds.length];
            if (k.equals("spruce")) {
                this.conifer(x, t + 1, z, "spruce_log", "spruce", 10 + this.rnd.nextInt(4));
            } else {
                this.bigTree(x, t + 1, z, k, 5 + this.rnd.nextInt(3), 1.0);
            }

            this.set(x, t, z, "podzol");
            placed++;
        }
    }

    protected void flora() {
        String[] f = {"short_grass", "short_grass", "fern", "poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium"};
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int t = this.h(x, z);
                if (t == Integer.MIN_VALUE || dist(x, z) < this.plaza + 2) {
                    continue;
                }

                String cur = this.get(x, t, z);
                if (cur != null && (cur.equals("grass_block") || cur.equals("moss_block")) && this.empty(x, t + 1, z)) {
                    double n = noise(55L, x * 2.2, z * 2.2);
                    double r = this.rnd.nextDouble();
                    if (n > 0.64 && r < 0.28) {
                        this.set(x, t + 1, z, f[this.rnd.nextInt(f.length)]);
                    } else if (r < 0.08) {
                        this.set(x, t + 1, z, "short_grass");
                    } else if (n < 0.25 && r < 0.12) {
                        this.set(x, t + 1, z, "pink_petals[flower_amount=" + (1 + this.rnd.nextInt(4)) + "]");
                    }
                }
            }
        }
    }
}
