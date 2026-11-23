package net.practicepvp.build;

import java.util.function.DoubleBinaryOperator;

/**
 * The landscape round a colosseum arena (3.15.0): the mace arena (Grand Colosseum) and the tournament arena
 * (Champions Colosseum) stand in a valley ringed by mountains, with forests, set pieces in clearings (a village,
 * watchtowers and a windmill in the Alps; temples, obelisks and champions' camps round Olympus) and small islands
 * floating in the sky. The ring starts where the colosseum's own build ends ({@code inner} distance
 * {@code start}) and runs out to a floating island's edge with an eroded underside. Nothing of it reaches the
 * colosseum: the arena's columns there stay its own.
 */
public final class ArenaLandscape extends CourtParts {
    public enum Kind {
        /** Grass, spruce and birch, snowy peaks; a village, watchtowers, a windmill. */
        ALPINE,
        /** Grass and white stone, cypress and flowering trees; quartz temples, gold-tipped obelisks, camps. */
        OLYMPUS
    }

    private final Kind kind;
    private final DoubleBinaryOperator inner;
    private final double start;
    private final int out;
    private final int depth;
    private final long seed;
    private final int halfX;
    private final int halfZ;
    private int[][] hm;
    private double[][] uu;
    private int[][] pads = new int[0][];

    /**
     * @param inner the arena's distance function (relative x, z: negative on the floor)
     * @param start where the ring starts on that distance (the end of the arena's own build)
     * @param out   how wide the ring is
     */
    public ArenaLandscape(Kind kind, int cx, int floorY, int cz, int halfX, int halfZ, DoubleBinaryOperator inner, double start, int out, int depth, int top, long seed) {
        super(cx, floorY, cz, Math.max(halfX, halfZ), -depth, Math.max(112, top), seed);
        this.kind = kind;
        this.inner = inner;
        this.start = start;
        this.out = out;
        this.depth = depth;
        this.seed = seed;
        this.halfX = halfX;
        this.halfZ = halfZ;
        this.build();
        this.finish();
    }

    @Override
    public String name() {
        return this.kind == Kind.ALPINE ? "Alpine valley" : "Olympus";
    }

    /** True for a column (relative x, z) that belongs to the ring. */
    public boolean owns(int rx, int rz) {
        return this.inner.applyAsDouble(rx, rz) > this.start;
    }

    private static double ease(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private int peak() {
        return this.kind == Kind.ALPINE ? 74 : 62;
    }

    private int snowLine() {
        return this.kind == Kind.ALPINE ? 54 : 999;
    }

    private String rock(int x, int y, int z) {
        double w = noise(this.seed + 611L, x * 1.4, z * 1.4) * 4.0;
        int band = Math.floorMod((int)Math.floor((y + w) / 3.0), 6);
        double r = hash3(this.seed + 613L, x, y, z);
        if (this.kind == Kind.OLYMPUS) {
            if (r < 0.012) {
                return "gold_ore";
            }

            return band == 1 || band == 4 ? "calcite" : (band == 2 ? "diorite" : (r < 0.15 ? "andesite" : "stone"));
        }

        return band == 1 ? "andesite" : (band == 4 ? "tuff" : (r < 0.08 ? "cobblestone" : "stone"));
    }

    private double padDist(int x, int z) {
        double best = 1.0E9;
        for (int[] p : this.pads) {
            best = Math.min(best, Math.hypot(x - p[0], z - p[1]) - p[2]);
        }

        return best;
    }

    /** The point straight out from the middle at angle {@code deg} that is {@code u} blocks past the arena's build. */
    private int[] along(double deg, double u) {
        double c = Math.cos(Math.toRadians(deg));
        double s = Math.sin(Math.toRadians(deg));
        for (int r = 0; r < this.R; r++) {
            if (this.inner.applyAsDouble(c * r, s * r) >= this.start + u) {
                return new int[]{(int)Math.round(c * r), (int)Math.round(s * r)};
            }
        }

        return new int[]{(int)Math.round(c * (this.R - 12)), (int)Math.round(s * (this.R - 12))};
    }

    private void build() {
        int n = 2 * this.R + 1;
        int skip = Integer.MIN_VALUE;
        int none = Integer.MIN_VALUE + 1;
        this.hm = new int[n][n];
        this.uu = new double[n][n];
        double[] angles = {30, 90, 150, 210, 270, 330};
        this.pads = new int[angles.length][];
        for (int i = 0; i < angles.length; i++) {
            int[] p = this.along(angles[i], 19.0);
            this.pads[i] = new int[]{p[0], p[1], 10};
        }

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double u = this.inner.applyAsDouble(x, z) - this.start;
                this.uu[x + this.R][z + this.R] = u;
                if (u <= 0.0) {
                    this.hm[x + this.R][z + this.R] = skip;
                    continue;
                }

                double ang = Math.atan2(z, x);
                double edge = this.out - noise(this.seed + 601L, Math.cos(ang) * 24.0, Math.sin(ang) * 24.0) * 14.0;
                // the footprint is a rectangle: keep inside it
                edge = Math.min(edge, u + Math.min(this.halfX - Math.abs(x), this.halfZ - Math.abs(z)) - 3.0);
                if (u > edge) {
                    this.hm[x + this.R][z + this.R] = none;
                    continue;
                }

                double t = ease(u / (this.out * 0.62));
                double ridge = 1.0 - Math.abs(noise(this.seed + 603L, x * 0.9, z * 0.9) * 2.0 - 1.0);
                double m = t * (this.peak() * 0.85 * (0.4 + 0.6 * ridge) + noise(this.seed + 605L, x * 2.5, z * 2.5) * 7.0);
                m *= 1.0 - 0.8 * ease((u - (edge - 20.0)) / 20.0);
                m -= Math.max(0.0, u - (edge - 3.0)) * 3.0;
                double pd = this.padDist(x, z);
                if (pd <= 0.0) {
                    m = 0.0;
                } else if (pd < 9.0) {
                    m *= ease(pd / 9.0);
                }

                this.hm[x + this.R][z + this.R] = Math.max(0, Math.min(this.yHi - 16, (int)Math.round(m)));
            }
        }

        String top = "grass_block";
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int h = this.hm[x + this.R][z + this.R];
                if (h < 0) {
                    continue;
                }

                int drop = 0;
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ix = x + o[0] + this.R;
                    int iz = z + o[1] + this.R;
                    if (ix >= 0 && iz >= 0 && ix < n && iz < n && this.hm[ix][iz] >= 0) {
                        drop = Math.max(drop, h - this.hm[ix][iz]);
                    }
                }

                boolean snowy = h >= this.snowLine();
                for (int y = 0; y <= h; y++) {
                    String b;
                    if (y == h) {
                        b = snowy && drop < 5 ? "snow_block" : (drop >= 5 ? this.rock(x, y, z) : top);
                    } else if (h - y <= 2 && drop < 5) {
                        b = snowy ? "snow_block" : "dirt";
                    } else {
                        b = this.rock(x, y, z);
                    }

                    this.set(x, y, z, b);
                }

                if (snowy && drop < 3 && hash3(this.seed + 615L, x, h, z) < 0.4) {
                    this.set(x, h + 1, z, "snow[layers=" + (1 + (int)(hash3(this.seed + 617L, x, h, z) * 3)) + "]");
                }

                // the underside: thick near the arena, thin and spiky toward the edge
                double u = this.uu[x + this.R][z + this.R];
                double rim = ease(u / Math.max(1.0, this.out));
                int bottom = (int)Math.round(4 + (1.0 - rim) * (this.depth - 4) + Math.pow(noise(this.seed + 609L, x * 2.4, z * 2.4), 3.0) * 10.0 * (1.0 - rim));
                bottom = Math.max(3, Math.min(this.depth, bottom));
                for (int y = -bottom; y < 0; y++) {
                    this.set(x, y, z, this.rock(x, y, z));
                }
            }
        }

        java.util.Random rr = new java.util.Random(this.seed ^ 0x0575L);
        for (int gx = -this.R; gx <= this.R; gx += 5) {
            for (int gz = -this.R; gz <= this.R; gz += 5) {
                int x = gx + rr.nextInt(5) - 2;
                int z = gz + rr.nextInt(5) - 2;
                double roll = rr.nextDouble();
                double kind = rr.nextDouble();
                int size = rr.nextInt(6);
                if (x < -this.R || x > this.R || z < -this.R || z > this.R) {
                    continue;
                }

                int h = this.hm[x + this.R][z + this.R];
                if (h < 0 || this.uu[x + this.R][z + this.R] < 3.0 || this.padDist(x, z) < 2.0 || !this.empty(x, h + 1, z)) {
                    continue;
                }

                String t = this.get(x, h, z);
                if (t == null || !(t.equals(top) || t.equals("snow_block"))) {
                    continue;
                }

                double forest = noise(this.seed + 621L, x * 0.8, z * 0.8);
                double density = 0.35 + 0.55 * ease((forest - 0.38) / 0.25);
                if (roll < density) {
                    this.tree(x, h + 1, z, size, kind, h >= this.snowLine());
                } else if (roll > 0.94) {
                    this.blob(x, h + 1, z, 1.3 + kind, this.rock(x, h, z));
                }
            }
        }

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int h = this.hm[x + this.R][z + this.R];
                if (h < 0 || !top.equals(this.get(x, h, z)) || !this.empty(x, h + 1, z)) {
                    continue;
                }

                double v = hash3(this.seed + 623L, x, h, z);
                if (v < 0.14) {
                    this.set(x, h + 1, z, "short_grass");
                } else if (v < 0.17) {
                    String[] fl = this.kind == Kind.OLYMPUS ? new String[]{"oxeye_daisy", "lily_of_the_valley", "allium", "poppy"}
                        : new String[]{"poppy", "dandelion", "cornflower", "azure_bluet"};
                    this.set(x, h + 1, z, fl[(int)(hash3(this.seed + 625L, x, h, z) * fl.length)]);
                }
            }
        }

        this.padPieces();
        this.skyIslets(rr);
        // the landscape is only to look at: an invisible wall right outside the arena's own build keeps every fight
        // (mace flights too) where it was
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double u = this.uu[x + this.R][z + this.R];
                if (u > 0.0 && u <= 1.5) {
                    for (int y = 1; y <= this.yHi; y++) {
                        if (this.empty(x, y, z)) {
                            this.set(x, y, z, "barrier");
                        }
                    }
                }
            }
        }
    }

    private void tree(int x, int y, int z, int size, double kind, boolean snowy) {
        if (snowy || this.kind == Kind.ALPINE && kind < 0.55) {
            this.spruce(x, y, z, 7 + size, snowy);
        } else if (this.kind == Kind.ALPINE) {
            this.broadleaf(x, y, z, kind < 0.75 ? "birch_log" : "oak_log", kind < 0.75 ? "birch" : "oak", 4 + size % 3, 2.6 + size * 0.15);
        } else if (kind < 0.45) {
            // cypress: tall and narrow
            this.conifer(x, y, z, "spruce_log", "spruce", 9 + size);
        } else if (kind < 0.8) {
            this.broadleaf(x, y, z, "oak_log", "oak", 3 + size % 2, 2.4);
        } else {
            this.broadleaf(x, y, z, "oak_log", "flowering_azalea", 3, 2.4);
        }
    }

    // ------------------------------------------------------------------ set pieces

    private void padPieces() {
        for (int i = 0; i < this.pads.length; i++) {
            int x = this.pads[i][0];
            int z = this.pads[i][1];
            if (this.kind == Kind.ALPINE) {
                switch (i % 6) {
                    case 0, 3 -> this.cottage(x, z);
                    case 1, 4 -> this.watchtower(x, z);
                    case 2 -> this.windmill(x, z);
                    default -> this.tents(x, z, "red", "white");
                }
            } else {
                switch (i % 6) {
                    case 0, 3 -> this.templeRuin(x, z);
                    case 1, 4 -> this.obelisk(x, z, 13);
                    default -> this.tents(x, z, "red", "yellow");
                }
            }
        }
    }

    /** A cottage: stone footing, timber frame and white walls, windows, a door toward the arena, a gable roof. */
    private void cottage(int x, int z) {
        boolean alongX = Math.abs(x) < Math.abs(z);
        int hw = alongX ? 4 : 3;
        int hd = alongX ? 3 : 4;
        for (int dx = -hw - 1; dx <= hw + 1; dx++) {
            for (int dz = -hd - 1; dz <= hd + 1; dz++) {
                this.set(x + dx, 0, z + dz, hash3(this.seed + 631L, x + dx, 0, z + dz) < 0.5 ? "coarse_dirt" : "gravel");
            }
        }

        for (int dx = -hw; dx <= hw; dx++) {
            for (int dz = -hd; dz <= hd; dz++) {
                boolean edge = Math.abs(dx) == hw || Math.abs(dz) == hd;
                boolean corner = Math.abs(dx) == hw && Math.abs(dz) == hd;
                this.set(x + dx, 1, z + dz, edge ? "cobblestone" : "spruce_planks");
                for (int y = 2; y <= 5; y++) {
                    if (!edge) {
                        this.set(x + dx, y, z + dz, null);
                        continue;
                    }

                    boolean window = !corner && y >= 3 && y <= 4 && (Math.abs(dz) == hd ? Math.floorMod(dx, 3) == 1 : Math.floorMod(dz, 3) == 1);
                    String b = corner ? "stripped_dark_oak_log[axis=y]" : (y == 5 ? "stripped_dark_oak_log[axis=" + (Math.abs(dz) == hd ? "x" : "z") + "]" : "calcite");
                    this.set(x + dx, y, z + dz, window ? "glass_pane" : b);
                }
            }
        }

        int ddx = alongX ? 0 : (x > 0 ? -hw : hw);
        int ddz = alongX ? (z > 0 ? -hd : hd) : 0;
        this.set(x + ddx, 2, z + ddz, null);
        this.set(x + ddx, 3, z + ddz, null);
        this.gableRoof(x - hw - 1, z - hd - 1, x + hw + 1, z + hd + 1, 6, alongX, "dark_oak", of("dark_oak_planks"), of("calcite"));
        int chx = x + (alongX ? hw - 1 : 0);
        int chz = z + (alongX ? 0 : hd - 1);
        for (int y = 2; y <= 12; y++) {
            this.set(chx, y, chz, "bricks");
        }

        this.set(chx, 13, chz, "brick_wall");
        this.set(x + (alongX ? -hw - 2 : 0), 1, z + (alongX ? 0 : -hd - 2), "barrel[facing=up]");
    }

    /** A round stone watchtower with a red cone roof and a flag. */
    private void watchtower(int x, int z) {
        Mat st = this.mixOf(641L, "stone_bricks", "cobblestone", 0.25, "mossy_stone_bricks", 0.12, "andesite", 0.1);
        this.cyl(x, z, 3.6, 1, 2, this.mixOf(643L, "cobblestone", "mossy_cobblestone", 0.3));
        this.cyl(x, z, 3.0, 1, 18, st);
        this.cyl(x, z, 2.0, 2, 17, of("air"));
        this.clearAir(x, z, 4, 1, 18);
        for (int y = 6; y <= 15; y += 5) {
            for (int k = 0; k < 4; k++) {
                double t = k * Math.PI / 2.0 + y;
                this.set(x + (int)Math.round(Math.cos(t) * 3.0), y, z + (int)Math.round(Math.sin(t) * 3.0), "iron_bars");
            }
        }

        this.ringAt(x, z, 2.6, 4.2, 18, of("polished_andesite"));
        for (int k = 0; k < 16; k++) {
            double t = k * Math.PI * 2.0 / 16.0;
            if (k % 2 == 0) {
                this.set(x + (int)Math.round(Math.cos(t) * 4.0), 19, z + (int)Math.round(Math.sin(t) * 4.0), "stone_bricks");
            }
        }

        this.coneRoof(x, z, 3.6, 20, 8, this.mixOf(645L, "red_terracotta", "red_concrete", 0.25, "red_nether_bricks", 0.08));
        for (int y = 28; y <= 31; y++) {
            this.set(x, y, z, "dark_oak_fence");
        }

        this.set(x + 1, 31, z, "red_wool");
        this.set(x + 2, 31, z, "red_wool");
        this.set(x + 1, 30, z, "red_wool");
    }

    private void clearAir(int x, int z, int r, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if ("air".equals(this.get(x + dx, y, z + dz))) {
                        this.set(x + dx, y, z + dz, null);
                    }
                }
            }
        }
    }

    /** A stone windmill with a wooden cap and four sails turned toward the arena. */
    private void windmill(int x, int z) {
        Mat st = this.mixOf(633L, "stone_bricks", "cobblestone", 0.3, "mossy_stone_bricks", 0.12);
        this.cyl(x, z, 3.4, 1, 13, st);
        this.cyl(x, z, 2.4, 2, 12, of("air"));
        this.clearAir(x, z, 3, 2, 12);
        this.coneRoof(x, z, 4.0, 14, 6, of("spruce_planks"));
        boolean alongX = Math.abs(x) >= Math.abs(z);
        int f = alongX ? (x > 0 ? -1 : 1) : (z > 0 ? -1 : 1);
        int hx = x + (alongX ? f * 4 : 0);
        int hz = z + (alongX ? 0 : f * 4);
        int hy = 12;
        this.set(hx, hy, hz, "dark_oak_planks");
        for (int[] a : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            for (int k = 1; k <= 7; k++) {
                int u = a[0] * k;
                int v = a[1] * k;
                int sx = alongX ? hx : hx + u;
                int sz = alongX ? hz + u : hz;
                this.set(sx, hy + v, sz, "spruce_fence");
                if (k >= 2) {
                    int px = alongX ? hx : hx + u + a[1];
                    int pz = alongX ? hz + u + a[1] : hz;
                    int py = hy + v - a[0];
                    if (py > 1 && this.empty(px, py, pz)) {
                        this.set(px, py, pz, "white_wool");
                    }
                }
            }
        }
    }

    /** A camp: three striped tents round a fire. */
    private void tents(int x, int z, String c1, String c2) {
        for (int k = 0; k < 3; k++) {
            double a = k * 2.094 + 0.5;
            this.tent(x + (int)Math.round(Math.cos(a) * 5.5), z + (int)Math.round(Math.sin(a) * 5.5), k % 2 == 0 ? c1 : c2);
        }

        this.set(x, 1, z, "campfire[lit=true,signal_fire=false]");
        this.set(x + 1, 1, z + 1, "barrel[facing=up]");
    }

    private void tent(int x, int z, String c) {
        for (int k = 0; k <= 2; k++) {
            int hw = 2 - k;
            for (int dx = -hw; dx <= hw; dx++) {
                for (int dz = -hw - 1; dz <= hw + 1; dz++) {
                    if (Math.abs(dx) == hw || k == 2) {
                        this.set(x + dx, 1 + k, z + dz, Math.floorMod(dz, 2) == 0 ? c + "_wool" : "white_wool");
                    }
                }
            }
        }

        this.set(x, 1, z - 3, null);
        this.set(x, 2, z - 3, null);
        this.set(x, 4, z, "spruce_fence");
        this.set(x, 5, z, c + "_banner[rotation=0]");
    }

    /** A quartz temple ruin: a two-step platform, a ring of columns (some broken), part of the roof beam. */
    private void templeRuin(int x, int z) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                this.set(x + dx, 1, z + dz, "smooth_quartz");
                if (Math.abs(dx) <= 6 && Math.abs(dz) <= 4) {
                    this.set(x + dx, 2, z + dz, hash3(this.seed + 651L, x + dx, 2, z + dz) < 0.12 ? "chiseled_quartz_block" : "quartz_bricks");
                }
            }
        }

        for (int dx = -6; dx <= 6; dx += 2) {
            for (int dz : new int[]{-4, 4}) {
                boolean intact = dz < 0 && Math.abs(dx) <= 4;
                int h = intact ? 8 : 1 + (int)(hash3(this.seed + 653L, x + dx, 0, z + dz) * 5);
                for (int y = 3; y < 3 + h; y++) {
                    this.set(x + dx, y, z + dz, "quartz_pillar[axis=y]");
                }

                if (intact) {
                    this.set(x + dx, 3 + h, z + dz, "chiseled_quartz_block");
                }
            }
        }

        for (int dx = -5; dx <= 5; dx++) {
            this.set(x + dx, 12, z - 4, "smooth_quartz");
            this.set(x + dx, 13, z - 4, dx == 0 ? "gold_block" : "quartz_slab[type=bottom]");
        }

        this.set(x, 3, z, "gold_block");
        this.set(x, 4, z, "lantern[hanging=false]");
        this.set(x + 3, 3, z + 2, "quartz_pillar[axis=x]");
        this.set(x + 4, 3, z + 2, "quartz_pillar[axis=x]");
    }

    /** A white obelisk with a gold tip on a stepped base. */
    private void obelisk(int x, int z, int h) {
        for (int k = 0; k < h; k++) {
            int half = k < 2 ? 2 : 1;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    if (k >= 2 && Math.abs(dx) == 1 && Math.abs(dz) == 1 && k > h / 2) {
                        continue;
                    }

                    this.set(x + dx, 1 + k, z + dz, k < 2 ? "smooth_quartz" : (k % 5 == 3 ? "chiseled_quartz_block" : "quartz_bricks"));
                }
            }
        }

        this.set(x, 1 + h, z, "gold_block");
        this.set(x, 2 + h, z, "lightning_rod[facing=up]");
        for (int[] o : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            this.set(x + o[0], 1, z + o[1], "polished_blackstone_bricks");
            this.set(x + o[0], 2, z + o[1], "lantern[hanging=false]");
        }
    }

    /** Small islands floating in the sky round the arena, each with a tree or a rock on top. */
    private void skyIslets(java.util.Random rr) {
        for (int i = 0; i < 8; i++) {
            double deg = i * 45 + 10 + rr.nextDouble() * 25;
            int[] p = this.along(deg, 4 + rr.nextDouble() * 14);
            int ox = p[0];
            int oz = p[1];
            int oy = 40 + rr.nextInt(16);
            double r = 2.5 + rr.nextDouble() * 3.0;
            boolean tree = rr.nextBoolean();
            int size = rr.nextInt(6);
            double kind = rr.nextDouble();
            int n = (int)Math.ceil(r);
            for (int dx = -n; dx <= n; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > r) {
                        continue;
                    }

                    int deep = (int)Math.round((r - d) * 1.5) + 1;
                    for (int k = 0; k <= deep; k++) {
                        int x = ox + dx;
                        int z = oz + dz;
                        this.set(x, oy - k, z, k == 0 ? (oy >= this.snowLine() ? "snow_block" : "grass_block") : (k <= 1 ? "dirt" : this.rock(x, oy - k, z)));
                    }

                    if (hash3(this.seed + 681L, ox + dx, oy, oz + dz) < 0.1) {
                        this.set(ox + dx, oy - deep - 1, oz + dz, "hanging_roots");
                    }
                }
            }

            if (tree) {
                this.tree(ox, oy + 1, oz, size % 3, kind, oy >= this.snowLine());
            } else {
                this.blob(ox, oy + 1, oz, 1.4, this.rock(ox, oy, oz));
            }
        }
    }
}
