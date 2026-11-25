package net.cmcpvp.build;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * The landscape round an arena (3.15.0): the arena stands in a valley ringed by mountains (or mesas, dunes, volcanic
 * peaks, jungle hills), with forests of the theme's trees, set pieces in clearings (villages, watchtowers, windmills,
 * camps, temples, obelisks, pyramids, pagodas, graveyards, stilt huts, ice spikes, basalt spires) and small islands
 * floating in the sky. With {@link Marks}, two giant landmarks stand behind the ends of the arena on hills high enough to
 * be seen over its walls from the floor: a signboard with the server's name and a champion statue.
 *
 * <p>The ring starts where the arena's own build ends ({@code inner} distance {@code start}) and runs out to a floating
 * island's edge with an eroded underside. Nothing of it reaches the arena: the arena's columns there stay its own, and an
 * invisible wall right outside the build keeps every fight inside.
 */
public final class ArenaLandscape extends CourtParts {
    /** Landscapes are optional (arena.landscape): off, an arena is only itself - far fewer blocks, much faster and lighter. */
    public static boolean enabled() {
        return net.cmcpvp.config.Cfg.bool(net.cmcpvp.config.Opt.ARENA_LANDSCAPE);
    }

    public enum Kind {
        /** Grass, spruce and birch, snowy peaks; a village, watchtowers, a windmill. */
        ALPINE("Alpine valley", "grass_block", "dirt", 74, 54, false, 1.0),
        /** Grass and white stone, cypress and flowering trees; quartz temples, gold-tipped obelisks, camps. */
        OLYMPUS("Olympus", "grass_block", "dirt", 62, 999, false, 1.0),
        /** Snow everywhere, snowy spruce, ice spikes, watchtowers with blue roofs. */
        FROST("Frozen peaks", "snow_block", "dirt", 80, 0, false, 1.0),
        /** Sand dunes and stepped sandstone mesas, palms; pavilions with onion domes, obelisks, a pyramid. */
        DESERT("Desert mesas", "sand", "sandstone", 46, 999, true, 0.45),
        /** Green hills with white stone, cherry trees and pink petals; pagodas, torii gates, tea houses. */
        SAKURA("Sakura hills", "grass_block", "dirt", 70, 62, false, 1.0),
        /** Banded terracotta mesas, dead trees and acacias; ruins and camps. */
        MESA("Badlands", "red_sand", "red_sandstone", 60, 999, true, 0.45),
        /** Dark oak and spruce woods on deepslate hills; graveyards, black towers, cottages. */
        DARK("Dark woods", "grass_block", "dirt", 66, 999, false, 1.0),
        /** Low green hills over beaches, palms; huts on stilts and beach camps. */
        TROPICAL("Tropical isles", "grass_block", "dirt", 50, 999, false, 0.6),
        /** Mossy jungle hills with giant trees and vines; temple ruins and stilt huts. */
        JUNGLE("Jungle hills", "grass_block", "dirt", 68, 999, false, 1.0),
        /** Blackstone and basalt peaks with glowing vents and smoke; basalt spires and ruins. */
        VOLCANIC("Volcanic peaks", "blackstone", "basalt[axis=y]", 78, 999, false, 0.4),
        /** End stone highlands with chorus trees; obsidian pillars crowned with light and purpur ruins. */
        END("End highlands", "end_stone", "end_stone", 64, 999, false, 0.5);

        public final String display;
        final String top;
        final String under;
        final int peak;
        final int snowLine;
        final boolean mesa;
        final double trees;

        Kind(String display, String top, String under, int peak, int snowLine, boolean mesa, double trees) {
            this.display = display;
            this.top = top;
            this.under = under;
            this.peak = peak;
            this.snowLine = snowLine;
            this.mesa = mesa;
            this.trees = trees;
        }
    }

    /**
     * The giant landmarks behind the two ends of the arena (on its long x axis): a signboard with {@code name} behind
     * the east end and a champion statue behind the west end. {@code wallTop} is how high the arena's build gets at
     * {@code wallX} blocks from the middle on that axis, {@code viewerX} how far a spawn is from the middle on the other
     * side: both stand on hills high enough to be seen over the build from there. {@code scale} 2 doubles their size
     * (for the colosseums), {@code color} is the theme's colour (a dye name).
     */
    public record Marks(String name, int scale, double wallTop, double wallX, double viewerX, String color) {
    }

    private final Kind kind;
    private final DoubleBinaryOperator inner;
    private final double start;
    private final int out;
    private final int depth;
    private final long seed;
    private final int halfX;
    private final int halfZ;
    private final Marks marks;
    private int[][] hm;
    private double[][] uu;
    /** Clearings: {x, z, radius, ground height}. The first six hold the set pieces, the rest the landmarks. */
    private int[][] pads = new int[0][];

    public ArenaLandscape(Kind kind, int cx, int floorY, int cz, int halfX, int halfZ, DoubleBinaryOperator inner, double start, int out, int depth, int top, long seed) {
        this(kind, cx, floorY, cz, halfX, halfZ, inner, start, out, depth, top, seed, null);
    }

    /**
     * @param inner the arena's distance function (relative x, z: negative on the floor)
     * @param start where the ring starts on that distance (the end of the arena's own build)
     * @param out   how wide the ring is
     * @param marks the giant landmarks, or null for none
     */
    public ArenaLandscape(Kind kind, int cx, int floorY, int cz, int halfX, int halfZ, DoubleBinaryOperator inner, double start, int out, int depth, int top, long seed, Marks marks) {
        super(cx, floorY, cz, Math.max(halfX, halfZ), -depth, Math.max(marks != null && marks.scale() > 1 ? 136 : 112, top), seed);
        this.kind = kind;
        this.inner = inner;
        this.start = start;
        this.out = out;
        this.depth = depth;
        this.seed = seed;
        this.halfX = halfX;
        this.halfZ = halfZ;
        this.marks = marks;
        this.build();
        this.finish();
    }

    @Override
    public String name() {
        return this.kind.display;
    }

    /** True for a column (relative x, z) that belongs to the ring. */
    public boolean owns(int rx, int rz) {
        return this.inner.applyAsDouble(rx, rz) > this.start;
    }

    private static double ease(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    // ------------------------------------------------------------------ materials

    /** Layered rock for the mountains of each kind. */
    private String rock(int x, int y, int z) {
        double w = noise(this.seed + 611L, x * 1.4, z * 1.4) * 4.0;
        int band = Math.floorMod((int)Math.floor((y + w) / 3.0), 6);
        double r = hash3(this.seed + 613L, x, y, z);
        return switch (this.kind) {
            case OLYMPUS -> r < 0.012 ? "gold_ore" : (band == 1 || band == 4 ? "calcite" : (band == 2 ? "diorite" : (r < 0.15 ? "andesite" : "stone")));
            case FROST -> band == 3 ? "packed_ice" : (band == 0 && r < 0.5 ? "calcite" : (r < 0.2 ? "andesite" : "stone"));
            case DESERT -> band == 2 ? "smooth_sandstone" : (band == 4 ? "cut_sandstone" : (band == 5 && r < 0.4 ? "orange_terracotta" : (r < 0.1 ? "smooth_sandstone" : "sandstone")));
            case SAKURA -> band == 1 ? "calcite" : (band == 4 && r < 0.5 ? "pink_terracotta" : (r < 0.2 ? "andesite" : "stone"));
            case MESA -> new String[]{"terracotta", "orange_terracotta", "red_terracotta", "yellow_terracotta", "white_terracotta", "brown_terracotta"}[band];
            case DARK -> band == 1 ? "deepslate" : (band == 4 ? "tuff" : (r < 0.15 ? "cobbled_deepslate" : (r < 0.3 ? "andesite" : "stone")));
            case TROPICAL, JUNGLE -> r < 0.25 ? "mossy_cobblestone" : (band == 2 ? "andesite" : (band == 5 && r < 0.5 ? "moss_block" : "stone"));
            case VOLCANIC -> r < 0.01 ? "magma_block" : (band % 3 == 0 ? "blackstone" : (r < 0.3 ? "smooth_basalt" : "basalt[axis=y]"));
            case END -> r < 0.02 ? "obsidian" : (band == 1 || band == 4 ? "end_stone_bricks" : "end_stone");
            default -> band == 1 ? "andesite" : (band == 4 ? "tuff" : (r < 0.08 ? "cobblestone" : "stone"));
        };
    }

    /** The ground's top block at a column of height h. */
    private String topAt(int x, int z, int h) {
        double n = noise(this.seed + 627L, x * 1.7, z * 1.7);
        return switch (this.kind) {
            case DARK -> n > 0.6 ? "podzol" : (n < 0.18 ? "coarse_dirt" : "grass_block");
            case TROPICAL -> h <= 3 ? "sand" : "grass_block";
            case JUNGLE -> n > 0.72 ? "moss_block" : (n < 0.12 ? "podzol" : "grass_block");
            case VOLCANIC -> hash3(this.seed + 629L, x, h, z) < 0.035 ? "magma_block" : (n > 0.66 ? "smooth_basalt" : "blackstone");
            case DESERT -> n > 0.8 ? "smooth_sandstone" : "sand";
            default -> this.kind.top;
        };
    }

    private boolean soil(String b) {
        return b != null && (b.equals(this.kind.top) || b.equals("grass_block") || b.equals("snow_block") || b.equals("podzol") || b.equals("moss_block")
            || b.equals("sand") || b.equals("red_sand") || b.equals("coarse_dirt"));
    }

    // ------------------------------------------------------------------ geometry

    private int padIndex(int x, int z) {
        int best = -1;
        double bd = 1.0E9;
        for (int i = 0; i < this.pads.length; i++) {
            int[] p = this.pads[i];
            double d = Math.hypot(x - p[0], z - p[1]) - p[2];
            if (d < bd) {
                bd = d;
                best = i;
            }
        }

        return best;
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

    /** How high the line of sight from the far spawn over the arena's build is at {@code x} blocks from the middle. */
    private double sight(double x) {
        Marks m = this.marks;
        return 1.6 + (m.wallTop() - 1.6) * (x + m.viewerX()) / Math.max(1.0, m.wallX() + m.viewerX());
    }

    // ------------------------------------------------------------------ the build

    private void build() {
        int n = 2 * this.R + 1;
        int skip = Integer.MIN_VALUE;
        int none = Integer.MIN_VALUE + 1;
        this.hm = new int[n][n];
        this.uu = new double[n][n];
        List<int[]> pl = new ArrayList<>();
        for (double a : new double[]{30, 90, 150, 210, 270, 330}) {
            int[] p = this.along(a, 19.0);
            pl.add(new int[]{p[0], p[1], 10, 0});
        }

        // the landmarks' hills, high enough to be seen over the build
        int[] statue = null;
        int statueLift = 0;
        int[] sign = null;
        int signLift = 0;
        int boardY = 0;
        String text = "";
        int[] sword = null;
        int swordBase = 0;
        int s = this.marks == null ? 1 : Math.max(1, this.marks.scale());
        if (this.marks != null) {
            statue = this.along(180.0, 22.0 + 9.0 * (s - 1));
            double sightS = this.sight(Math.abs(statue[0]));
            statueLift = (int)Math.round(Math.max(0.0, Math.min(this.yHi - 4 - 40.0 * s, sightS - 20.0 * s)));
            pl.add(new int[]{statue[0], statue[1], 9 * s + 4, statueLift});
            text = this.marks.name() == null ? "" : PixelFont.fit(this.marks.name(), Math.min(70, (this.R - 30) * 2 / s - 8));
            if (text.isEmpty()) {
                // no name: a giant sword stood in a rock, its guard clear of the build's top
                sword = this.along(0.0, 22.0 + 9.0 * (s - 1));
                double sightW = this.sight(Math.abs(sword[0]));
                swordBase = (int)Math.round(Math.max(1.0, Math.min(this.yHi - 4 - 32.0 * s, sightW + 2.0 - 22.0 * s)));
                pl.add(new int[]{sword[0], sword[1], 7 * s + 4, swordBase - 1});
            } else {
                sign = this.along(0.0, 24.0 + 9.0 * (s - 1));
                double sightB = this.sight(Math.abs(sign[0]));
                int hu = PixelFont.ROWS + 4;
                boardY = (int)Math.round(Math.min(this.yHi - 4 - hu * s, sightB + 1.0));
                signLift = Math.max(0, boardY - 12 * s);
                int half = (PixelFont.width(text) + 6) * s / 2;
                for (int k = -half; k <= half + 5; k += 6) {
                    pl.add(new int[]{sign[0] + 3 * s, sign[1] + Math.min(k, half), 6 + 3 * s, signLift});
                }
            }
        }

        this.pads = pl.toArray(new int[0][]);
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
                double m = t * (this.kind.peak * 0.85 * (0.4 + 0.6 * ridge) + noise(this.seed + 605L, x * 2.5, z * 2.5) * 7.0);
                if (this.kind.mesa) {
                    m = Math.floor((m + noise(this.seed + 607L, x * 1.2, z * 1.2) * 3.0) / 6.0) * 6.0;
                }

                m *= 1.0 - 0.8 * ease((u - (edge - 20.0)) / 20.0);
                m -= Math.max(0.0, u - (edge - 3.0)) * 3.0;
                int pi = this.padIndex(x, z);
                if (pi >= 0) {
                    int[] p = this.pads[pi];
                    double pd = Math.hypot(x - p[0], z - p[1]) - p[2];
                    double blend = 9.0 + p[3] * 0.6;
                    if (pd <= 0.0) {
                        m = p[3];
                    } else if (pd < blend) {
                        m = p[3] + (m - p[3]) * ease(pd / blend);
                    }
                }

                this.hm[x + this.R][z + this.R] = Math.max(0, Math.min(this.yHi - 16, (int)Math.round(m)));
            }
        }

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

                boolean snowy = h >= this.kind.snowLine;
                for (int y = 0; y <= h; y++) {
                    String b;
                    if (y == h) {
                        b = snowy && drop < 5 ? "snow_block" : (drop >= 5 ? this.rock(x, y, z) : this.topAt(x, z, h));
                    } else if (h - y <= 2 && drop < 5) {
                        b = snowy ? "snow_block" : this.kind.under;
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

        if (statue != null) {
            this.statue(statue[0], statueLift + 1, statue[1], statue[0] < 0 ? 1 : -1, 0, s);
        }

        if (sign != null) {
            this.signboard(sign[0], sign[1], sign[0] > 0 ? -1 : 1, 0, s, boardY, signLift, text);
        }

        if (sword != null) {
            this.swordMonument(sword[0], swordBase, sword[1], sword[0] > 0 ? -1 : 1, 0, s);
        }

        this.padPieces();
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

                if (!this.soil(this.get(x, h, z))) {
                    continue;
                }

                double forest = noise(this.seed + 621L, x * 0.8, z * 0.8);
                double density = (0.35 + 0.55 * ease((forest - 0.38) / 0.25)) * this.kind.trees;
                if (roll < density) {
                    this.tree(x, h + 1, z, size, kind, h >= this.kind.snowLine);
                } else if (roll > 0.94) {
                    this.blob(x, h + 1, z, 1.3 + kind, this.rock(x, h, z));
                }
            }
        }

        this.decorate();
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

    /** Grass, flowers, bushes, petals, cacti and smoking vents on the open ground. */
    private void decorate() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int h = this.hm[x + this.R][z + this.R];
                if (h < 0 || !this.empty(x, h + 1, z)) {
                    continue;
                }

                int pi = this.padIndex(x, z);
                if (pi >= 6 && this.padDist(x, z) < 1.0) {
                    continue;
                }

                String g = this.get(x, h, z);
                double v = hash3(this.seed + 623L, x, h, z);
                double w = hash3(this.seed + 625L, x, h, z);
                if ("grass_block".equals(g) || "podzol".equals(g) || "moss_block".equals(g)) {
                    boolean lush = this.kind == Kind.JUNGLE || this.kind == Kind.TROPICAL || this.kind == Kind.DARK;
                    if (v < 0.14) {
                        this.set(x, h + 1, z, lush && w < 0.5 ? "fern" : "short_grass");
                    } else if (v < 0.17) {
                        String[] fl = switch (this.kind) {
                            case OLYMPUS -> new String[]{"oxeye_daisy", "lily_of_the_valley", "allium", "poppy"};
                            case SAKURA -> new String[]{"pink_tulip", "allium", "lily_of_the_valley", "white_tulip"};
                            case DARK -> new String[]{"red_mushroom", "brown_mushroom", "lily_of_the_valley"};
                            case TROPICAL, JUNGLE -> new String[]{"blue_orchid", "orange_tulip", "allium"};
                            default -> new String[]{"poppy", "dandelion", "cornflower", "azure_bluet"};
                        };
                        this.set(x, h + 1, z, fl[(int)(w * fl.length)]);
                    } else if (v < 0.2 && this.kind == Kind.SAKURA) {
                        this.set(x, h + 1, z, "pink_petals[flower_amount=" + (1 + (int)(w * 4)) + ",facing=north]");
                    } else if (v < 0.185 && lush) {
                        this.set(x, h + 1, z, leaves(this.kind == Kind.DARK ? "dark_oak" : "jungle"));
                    }
                } else if ("sand".equals(g) || "red_sand".equals(g)) {
                    if (v < 0.012) {
                        this.set(x, h + 1, z, "dead_bush");
                    } else if (v < 0.016 && this.kind != Kind.TROPICAL) {
                        int ch = 1 + (int)(w * 3);
                        for (int k = 1; k <= ch; k++) {
                            this.set(x, h + k, z, "cactus");
                        }
                    }
                } else if (this.kind == Kind.VOLCANIC && h > this.kind.peak * 0.55 && v < 0.0035) {
                    // a smoking vent
                    this.set(x, h, z, "magma_block");
                    this.set(x, h + 1, z, "campfire[lit=true,signal_fire=true]");
                    for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        if (this.get(x + o[0], h, z + o[1]) != null) {
                            this.set(x + o[0], h, z + o[1], "magma_block");
                        }
                    }
                }
            }
        }
    }

    private void tree(int x, int y, int z, int size, double k, boolean snowy) {
        switch (this.kind) {
            case ALPINE -> {
                if (snowy || k < 0.55) {
                    this.spruce(x, y, z, 7 + size, snowy);
                } else {
                    this.broadleaf(x, y, z, k < 0.75 ? "birch_log" : "oak_log", k < 0.75 ? "birch" : "oak", 4 + size % 3, 2.6 + size * 0.15);
                }
            }
            case OLYMPUS -> {
                if (k < 0.45) {
                    // cypress: tall and narrow
                    this.conifer(x, y, z, "spruce_log", "spruce", 9 + size);
                } else if (k < 0.8) {
                    this.broadleaf(x, y, z, "oak_log", "oak", 3 + size % 2, 2.4);
                } else {
                    this.broadleaf(x, y, z, "oak_log", "flowering_azalea", 3, 2.4);
                }
            }
            case FROST -> this.spruce(x, y, z, 7 + size, true);
            case DESERT, TROPICAL -> {
                if (k < 0.75) {
                    this.palmTree(x, y, z);
                } else {
                    this.broadleaf(x, y, z, "jungle_log", "jungle", 2, 1.8);
                }
            }
            case SAKURA -> {
                if (snowy) {
                    this.spruce(x, y, z, 7 + size, true);
                } else if (k < 0.72) {
                    this.cherryTree(x, y, z);
                } else {
                    this.broadleaf(x, y, z, "oak_log", "flowering_azalea", 3, 2.4);
                }
            }
            case MESA -> {
                if (k < 0.55) {
                    this.deadTree(x, y, z, "dark_oak_log", 5 + size);
                } else {
                    this.broadleaf(x, y, z, "acacia_log", "acacia", 4, 2.6);
                }
            }
            case DARK -> {
                if (k < 0.5) {
                    this.broadleaf(x, y, z, "dark_oak_log", "dark_oak", 4 + size % 2, 3.0 + size * 0.1);
                } else if (k < 0.8) {
                    this.spruce(x, y, z, 8 + size, false);
                } else {
                    this.deadTree(x, y, z, "dark_oak_log", 5 + size);
                }
            }
            case JUNGLE -> {
                if (k < 0.16) {
                    this.jungleGiant(x, y, z, 15 + size * 2);
                } else {
                    this.broadleaf(x, y, z, "jungle_log", "jungle", 5 + size % 3, 3.2);
                }
            }
            case VOLCANIC -> this.deadTree(x, y, z, k < 0.6 ? "dark_oak_log" : "mangrove_log", 5 + size);
            case END -> this.chorus(x, y, z, size, k);
        }
    }

    /** A chorus tree: a stalk with branches stepping out and up, flowers on the tips. */
    private void chorus(int x, int y, int z, int size, double k) {
        int h = 4 + size;
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, "chorus_plant");
        }

        this.set(x, y + h, z, "chorus_flower[age=5]");
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int b = 0; b < 2 + size / 2; b++) {
            int[] d = dirs[(int)((k * 7 + b * 1.7) * 4) % 4];
            int by = y + 1 + (int)((h - 2) * ((b + 1.0) / (3 + size / 2)));
            int bx = x + d[0];
            int bz = z + d[1];
            this.set(bx, by, bz, "chorus_plant");
            this.set(bx + d[0], by, bz + d[1], "chorus_plant");
            for (int j = 1; j <= 2; j++) {
                this.set(bx + d[0], by + j, bz + d[1], "chorus_plant");
            }

            this.set(bx + d[0], by + 3, bz + d[1], "chorus_flower[age=5]");
        }
    }

    /** An obsidian pillar crowned with light in an iron cage. */
    private void obsidianPillar(int x, int z, int h) {
        for (int y = 1; y <= h; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) < 2 || y < h - 2) {
                        this.set(x + dx, y, z + dz, y % 7 == 0 ? "crying_obsidian" : "obsidian");
                    }
                }
            }
        }

        this.set(x, h + 1, z, "bedrock");
        this.set(x, h + 2, z, "sea_lantern");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = h + 1; y <= h + 3; y++) {
                    if (dx != 0 || dz != 0) {
                        this.set(x + dx, y, z + dz, "iron_bars");
                    }
                }

                this.set(x + dx, h + 4, z + dz, "iron_bars");
            }
        }

        this.set(x, h + 5, z, "end_rod[facing=up]");
    }

    // ------------------------------------------------------------------ set pieces

    private static String facingAway(int x, int z) {
        return Math.abs(x) > Math.abs(z) ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south");
    }

    private void padPieces() {
        for (int i = 0; i < Math.min(6, this.pads.length); i++) {
            int x = this.pads[i][0];
            int z = this.pads[i][1];
            switch (this.kind) {
                case ALPINE -> {
                    switch (i) {
                        case 0, 3 -> this.cottage(x, z, "calcite", "stripped_dark_oak_log", "dark_oak", "dark_oak_planks");
                        case 1, 4 -> this.watchtower(x, z, this.mixOf(641L, "stone_bricks", "cobblestone", 0.25, "mossy_stone_bricks", 0.12, "andesite", 0.1),
                            this.mixOf(645L, "red_terracotta", "red_concrete", 0.25, "red_nether_bricks", 0.08), "red", "lantern");
                        case 2 -> this.windmill(x, z);
                        default -> this.tents(x, z, "red", "white");
                    }
                }
                case OLYMPUS -> {
                    switch (i) {
                        case 0, 3 -> this.templeRuin(x, z);
                        case 1, 4 -> this.obelisk(x, z, 13, "smooth_quartz", "quartz_bricks", "chiseled_quartz_block");
                        default -> this.tents(x, z, "red", "yellow");
                    }
                }
                case FROST -> {
                    switch (i) {
                        case 0, 3 -> this.cottage(x, z, "spruce_planks", "stripped_spruce_log", "spruce", "spruce_planks");
                        case 1, 4 -> this.watchtower(x, z, this.mixOf(647L, "stone_bricks", "polished_diorite", 0.25, "cobblestone", 0.12),
                            this.mixOf(649L, "blue_terracotta", "light_blue_terracotta", 0.3, "packed_ice", 0.1), "blue", "soul_lantern");
                        default -> this.iceSpikes(x, z);
                    }
                }
                case DESERT -> {
                    switch (i) {
                        case 0, 3 -> this.domePavilion(x, z);
                        case 1, 4 -> this.obelisk(x, z, 13, "smooth_sandstone", "cut_sandstone", "chiseled_sandstone");
                        case 2 -> this.smallPyramid(x, z, 9, "smooth_sandstone", "cut_sandstone");
                        default -> this.tents(x, z, "red", "white");
                    }
                }
                case SAKURA -> {
                    switch (i) {
                        case 0, 3 -> this.pagoda(x, z);
                        case 1, 4 -> this.torii(x, z);
                        default -> this.cottage(x, z, "cherry_planks", "stripped_cherry_log", "deepslate_tile", "deepslate_tiles");
                    }
                }
                case MESA -> {
                    switch (i) {
                        case 0, 3 -> this.ruins(x, z, "red_sandstone", "cut_red_sandstone");
                        case 1, 4 -> this.obelisk(x, z, 12, "smooth_red_sandstone", "cut_red_sandstone", "chiseled_red_sandstone");
                        default -> this.tents(x, z, "orange", "white");
                    }
                }
                case DARK -> {
                    switch (i) {
                        case 0, 3 -> this.graveyard(x, z);
                        case 1, 4 -> this.watchtower(x, z, this.mixOf(651L, "deepslate_bricks", "cracked_deepslate_bricks", 0.25, "deepslate_tiles", 0.12),
                            this.mixOf(653L, "deepslate_tiles", "cobbled_deepslate", 0.2, "polished_deepslate", 0.1), "purple", "soul_lantern");
                        default -> this.cottage(x, z, "spruce_planks", "dark_oak_log", "deepslate_tile", "deepslate_tiles");
                    }
                }
                case TROPICAL -> {
                    if (i == 0 || i == 3) {
                        this.stiltHut(x, 0, z, 3, 3, facingAway(x, z));
                    } else {
                        this.beachCamp(x, z);
                    }
                }
                case JUNGLE -> {
                    switch (i) {
                        case 0, 3 -> this.ruins(x, z, "mossy_stone_bricks", "cracked_stone_bricks");
                        case 1, 4 -> this.smallPyramid(x, z, 8, "mossy_stone_bricks", "mossy_cobblestone");
                        default -> this.stiltHut(x, 0, z, 3, 3, facingAway(x, z));
                    }
                }
                case END -> {
                    if (i == 2 || i == 5) {
                        this.ruins(x, z, "purpur_block", "purpur_pillar[axis=y]");
                    } else {
                        this.obsidianPillar(x, z, 14 + i * 3);
                        this.obsidianPillar(x + 5, z - 4, 9 + i * 2);
                    }
                }
                case VOLCANIC -> {
                    if (i == 2 || i == 5) {
                        this.ruins(x, z, "polished_blackstone_bricks", "cracked_polished_blackstone_bricks");
                    } else {
                        this.spires(x, z);
                    }
                }
            }
        }
    }

    /** A cottage: stone footing, timber frame and walls, windows, a door toward the arena, a gable roof, a chimney. */
    private void cottage(int x, int z, String wall, String frame, String roofStair, String roofBody) {
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
                    String b = corner ? frame + "[axis=y]" : (y == 5 ? frame + "[axis=" + (Math.abs(dz) == hd ? "x" : "z") + "]" : wall);
                    this.set(x + dx, y, z + dz, window ? "glass_pane" : b);
                }
            }
        }

        int ddx = alongX ? 0 : (x > 0 ? -hw : hw);
        int ddz = alongX ? (z > 0 ? -hd : hd) : 0;
        this.set(x + ddx, 2, z + ddz, null);
        this.set(x + ddx, 3, z + ddz, null);
        this.gableRoof(x - hw - 1, z - hd - 1, x + hw + 1, z + hd + 1, 6, alongX, roofStair, of(roofBody), of(wall));
        int chx = x + (alongX ? hw - 1 : 0);
        int chz = z + (alongX ? 0 : hd - 1);
        for (int y = 2; y <= 12; y++) {
            this.set(chx, y, chz, "bricks");
        }

        this.set(chx, 13, chz, "brick_wall");
        this.set(x + (alongX ? -hw - 2 : 0), 1, z + (alongX ? 0 : -hd - 2), "barrel[facing=up]");
        this.set(x + (alongX ? -hw - 2 : 1), 1, z + (alongX ? 1 : -hd - 2), "hay_block[axis=y]");
    }

    /** A round watchtower with a cone roof and a flag. */
    private void watchtower(int x, int z, Mat stone, Mat roof, String flag, String light) {
        this.cyl(x, z, 3.6, 1, 2, this.mixOf(643L, "cobblestone", "mossy_cobblestone", 0.3));
        this.cyl(x, z, 3.0, 1, 18, stone);
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
                this.set(x + (int)Math.round(Math.cos(t) * 4.0), 19, z + (int)Math.round(Math.sin(t) * 4.0), stone.at(x, 19, z));
            }
        }

        this.coneRoof(x, z, 3.6, 20, 8, roof);
        for (int y = 28; y <= 31; y++) {
            this.set(x, y, z, "dark_oak_fence");
        }

        this.set(x + 1, 31, z, flag + "_wool");
        this.set(x + 2, 31, z, flag + "_wool");
        this.set(x + 1, 30, z, flag + "_wool");
        this.set(x + 4, 17, z, light + "[hanging=true]");
        this.set(x - 4, 17, z, light + "[hanging=true]");
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
            this.tent(x + (int)Math.round(Math.cos(a) * 5.5), z + (int)Math.round(Math.sin(a) * 5.5), k % 2 == 0 ? c1 : c2, c1.equals("white") ? c2 : "white");
        }

        this.set(x, 1, z, "campfire[lit=true,signal_fire=false]");
        this.set(x + 1, 1, z + 1, "barrel[facing=up]");
        this.set(x - 1, 1, z + 1, "spruce_slab[type=bottom]");
    }

    private void tent(int x, int z, String c, String stripe) {
        for (int k = 0; k <= 2; k++) {
            int hw = 2 - k;
            for (int dx = -hw; dx <= hw; dx++) {
                for (int dz = -hw - 1; dz <= hw + 1; dz++) {
                    if (Math.abs(dx) == hw || k == 2) {
                        this.set(x + dx, 1 + k, z + dz, Math.floorMod(dz, 2) == 0 ? c + "_wool" : stripe + "_wool");
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

    /** An obelisk with a gold tip on a stepped base, lanterns round it. */
    private void obelisk(int x, int z, int h, String base, String body, String band) {
        for (int k = 0; k < h; k++) {
            int half = k < 2 ? 2 : 1;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    if (k >= 2 && Math.abs(dx) == 1 && Math.abs(dz) == 1 && k > h / 2) {
                        continue;
                    }

                    this.set(x + dx, 1 + k, z + dz, k < 2 ? base : (k % 5 == 3 ? band : body));
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

    /** A small graveyard: an iron fence, rows of stones, a dead tree and a lantern. */
    private void graveyard(int x, int z) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                boolean edge = Math.abs(dx) == 7 || Math.abs(dz) == 6;
                this.set(x + dx, 0, z + dz, hash3(this.seed + 655L, x + dx, 0, z + dz) < 0.4 ? "podzol" : "coarse_dirt");
                if (edge && !(Math.abs(dx) <= 1 && dz == (z > 0 ? -6 : 6))) {
                    this.set(x + dx, 1, z + dz, "iron_bars");
                }
            }
        }

        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 4) {
                double r = hash3(this.seed + 657L, x + dx, 0, z + dz);
                this.set(x + dx, 1, z + dz, r < 0.5 ? "cobblestone_wall" : "mossy_cobblestone_wall");
                if (r < 0.3) {
                    this.set(x + dx, 2, z + dz, "stone_button[face=floor,facing=north]");
                }
            }
        }

        this.deadTree(x + 5, 1, z + (z > 0 ? 4 : -4), "dark_oak_log", 6);
        this.lantern(x - 6, 1, z, "dark_oak_fence", 2, "soul_lantern[hanging=false]");
    }

    /** A beach camp: an umbrella, palms and a sandcastle on a patch of sand. */
    private void beachCamp(int x, int z) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.hypot(dx, dz) <= 6.5) {
                    this.set(x + dx, 0, z + dz, "sand");
                }
            }
        }

        this.umbrella(x, 1, z, (x > 0) == (z > 0) ? "red" : "yellow");
        this.palmTree(x + 4, 1, z - 3);
        this.palmTree(x - 4, 1, z + 3);
        this.set(x - 2, 1, z - 3, "sandstone");
        this.set(x - 2, 2, z - 3, "sandstone_wall");
        this.set(x + 2, 1, z + 2, "barrel[facing=up]");
    }

    /** A beach umbrella: a pole and a striped round canopy. */
    private void umbrella(int x, int y, int z, String color) {
        for (int k = 0; k < 3; k++) {
            this.set(x, y + k, z, "bamboo_fence");
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 2.4) {
                    boolean stripe = Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0), 2) == 0;
                    this.set(x + dx, y + 3, z + dz, d < 1.2 ? color + "_wool" : (stripe ? color + "_carpet" : "white_carpet"));
                }
            }
        }

        this.set(x, y + 4, z, color + "_wool");
    }

    /** A ruin: a broken square foundation with walls of different heights and two columns. */
    private void ruins(int x, int z, String a, String b) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                boolean edge = Math.abs(dx) == 6 || Math.abs(dz) == 6;
                if (!edge) {
                    if (hash3(this.seed + 661L, x + dx, 0, z + dz) < 0.35) {
                        this.set(x + dx, 0, z + dz, b);
                    }

                    continue;
                }

                int h = (int)(noise(this.seed + 663L, (x + dx) * 4.0, (z + dz) * 4.0) * 7.0) - 1;
                for (int y = 1; y <= h; y++) {
                    this.set(x + dx, y, z + dz, hash3(this.seed + 665L, x + dx, y, z + dz) < 0.3 ? b : a);
                }
            }
        }

        for (int[] c : new int[][]{{-3, -3}, {3, 3}}) {
            int h = 4 + (int)(hash3(this.seed + 667L, x + c[0], 0, z + c[1]) * 6);
            for (int y = 1; y <= h; y++) {
                this.set(x + c[0], y, z + c[1], a);
            }
        }
    }

    /** Spikes of packed and blue ice standing out of the snow. */
    private void iceSpikes(int x, int z) {
        java.util.Random r = new java.util.Random(this.seed ^ (x * 31L + z));
        for (int k = 0; k < 4; k++) {
            int sx = x + r.nextInt(9) - 4;
            int sz = z + r.nextInt(9) - 4;
            int h = 8 + r.nextInt(10);
            for (int y = 1; y <= h; y++) {
                double rad = 1.8 * (1.0 - (double)y / (h + 1));
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.hypot(dx, dz) <= rad + 0.3) {
                            this.set(sx + dx, y, sz + dz, y % 5 == 0 ? "blue_ice" : "packed_ice");
                        }
                    }
                }
            }
        }
    }

    /** Volcanic spires: basalt columns narrowing to glowing tips. */
    private void spires(int x, int z) {
        java.util.Random r = new java.util.Random(this.seed ^ (x * 37L + z));
        for (int k = 0; k < 5; k++) {
            int sx = x + r.nextInt(11) - 5;
            int sz = z + r.nextInt(11) - 5;
            int h = 9 + r.nextInt(14);
            double r0 = 1.6 + r.nextDouble();
            for (int y = 1; y <= h; y++) {
                double rad = r0 * (1.0 - (double)y / (h + 2));
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (Math.hypot(dx, dz) <= rad + 0.3) {
                            this.set(sx + dx, y, sz + dz, y >= h - 1 ? "magma_block" : (y % 6 == 0 ? "blackstone" : "basalt[axis=y]"));
                        }
                    }
                }
            }
        }
    }

    /** A stepped pyramid with a gold cap and a doorway toward the arena. */
    private void smallPyramid(int x, int z, int half, String a, String b) {
        for (int k = 0; k <= half; k++) {
            int hw = half - k;
            for (int dx = -hw; dx <= hw; dx++) {
                for (int dz = -hw; dz <= hw; dz++) {
                    this.set(x + dx, 1 + k, z + dz, k == half ? "gold_block" : (hash3(this.seed + 671L, x + dx, k, z + dz) < 0.2 ? b : a));
                }
            }
        }

        boolean alongX = Math.abs(x) >= Math.abs(z);
        int f = alongX ? (x > 0 ? -1 : 1) : (z > 0 ? -1 : 1);
        for (int k = 0; k <= 2; k++) {
            for (int y = 1; y <= 3; y++) {
                this.set(x + (alongX ? f * (half - k) : 0), y, z + (alongX ? 0 : f * (half - k)), null);
            }
        }

        this.set(x + (alongX ? f * (half - 2) : 0), 3, z + (alongX ? 0 : f * (half - 2)), "lantern[hanging=true]");
    }

    /** A sandstone pavilion: arched openings, gold bands, an onion dome and four small corner domes. */
    private void domePavilion(int x, int z) {
        int half = 4;
        int h = 9;
        Mat cream = this.mixOf(675L, "smooth_sandstone", "cut_sandstone", 0.2, "sandstone", 0.1);
        this.fill(x - half - 1, 1, z - half - 1, x + half + 1, 1, z + half + 1, of("cut_sandstone"));
        this.fill(x - half, 2, z - half, x + half, h, z + half, cream);
        this.fill(x - half + 1, 2, z - half + 1, x + half - 1, h - 1, z + half - 1, null);
        for (int k = -half; k <= half; k++) {
            for (int[] e : new int[][]{{x + k, z - half}, {x + k, z + half}, {x - half, z + k}, {x + half, z + k}}) {
                this.set(e[0], 6, e[1], "gold_block");
                this.set(e[0], h, e[1], "red_terracotta");
                int a = Math.abs(k);
                if (a <= 1) {
                    for (int y = 2; y <= 5 - a; y++) {
                        this.set(e[0], y, e[1], null);
                    }
                }
            }
        }

        this.ringAt(x, z, half - 0.5, half + 1.2, h + 1, of("smooth_sandstone"));
        this.fill(x - half + 1, h + 1, z - half + 1, x + half - 1, h + 2, z + half - 1, cream);
        this.onion(x, h + 3, z, 3.5, this.mixOf(677L, "light_blue_terracotta", "cyan_terracotta", 0.25), "gold_block");
        for (int[] c : new int[][]{{x - half, z - half}, {x - half, z + half}, {x + half, z - half}, {x + half, z + half}}) {
            this.onion(c[0], h + 2, c[1], 1.0, of("gold_block"), null);
        }

        this.set(x, 2, z, "lantern[hanging=false]");
    }

    /** A hut on stilts with bamboo walls, a thatched (hay) roof and a little porch. */
    private void stiltHut(int x, int y, int z, int w, int d, String facing) {
        int floor = y + 3;
        for (int dx = -w; dx <= w; dx++) {
            for (int dz = -d; dz <= d; dz++) {
                this.set(x + dx, floor, z + dz, "spruce_planks");
                if ((Math.abs(dx) == w || Math.abs(dz) == d) && (Math.abs(dx) == w && Math.abs(dz) == d || dx == 0 || dz == 0)) {
                    for (int k = y + 1; k < floor; k++) {
                        this.set(x + dx, k, z + dz, log("stripped_jungle_log", 'y'));
                    }
                }
            }
        }

        for (int dx = -w + 1; dx <= w - 1; dx++) {
            for (int dz = -d + 1; dz <= d - 1; dz++) {
                if (Math.abs(dx) == w - 1 || Math.abs(dz) == d - 1) {
                    for (int k = 1; k <= 3; k++) {
                        boolean corner = Math.abs(dx) == w - 1 && Math.abs(dz) == d - 1;
                        this.set(x + dx, floor + k, z + dz, corner ? log("stripped_jungle_log", 'y') : (k == 2 && (dx == 0 || dz == 0) ? null : "bamboo_mosaic"));
                    }
                }
            }
        }

        this.pyramidRoof(x - w, z - d, x + w, z + d, floor + 4, null, this.mixOf(711L, "hay_block[axis=y]", "bamboo_block[axis=y]", 0.15));
        int[] f = switch (facing) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "east" -> new int[]{1, 0};
            default -> new int[]{-1, 0};
        };
        for (int k = 1; k <= 3; k++) {
            this.set(x + f[0] * (w + k), floor - k + 1, z + f[1] * (d + k), stairs("spruce", opposite(facing), false));
        }

        this.set(x + f[0] * w, floor + 4, z + f[1] * d, "lantern[hanging=true]");
    }

    /** A giant jungle tree: a 2 by 2 trunk with buttress roots, a wide crown and vines. */
    private void jungleGiant(int x, int y, int z, int h) {
        for (int k = 0; k < h; k++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    this.set(x + dx, y + k, z + dz, log("jungle_log", 'y'));
                }
            }
        }

        for (int[] r : new int[][]{{-1, 0}, {2, 1}, {0, -1}, {1, 2}}) {
            for (int k = 0; k < 3; k++) {
                this.set(x + r[0], y + k - 1, z + r[1], log("jungle_log", 'y'));
            }
        }

        this.blob(x, y + h + 1, z, 7.5, leaves("jungle"));
        this.blob(x + 4, y + h - 3, z - 3, 5.0, leaves("jungle"));
        this.blob(x - 4, y + h - 4, z + 3, 5.0, leaves("jungle"));
        this.blob(x + 1, y + h - 6, z + 5, 4.0, leaves("jungle"));
        for (int i = 0; i < 18; i++) {
            int vx = x + this.rnd.nextInt(15) - 7;
            int vz = z + this.rnd.nextInt(15) - 7;
            for (int vy = y + h + 2; vy > y + h - 10; vy--) {
                String here = this.get(vx, vy, vz);
                if (here != null && here.startsWith("jungle_leaves") && this.empty(vx, vy - 1, vz)) {
                    int len = 3 + this.rnd.nextInt(7);
                    for (int k = 1; k <= len && this.empty(vx, vy - k, vz); k++) {
                        this.set(vx, vy - k, vz, "vine[north=true]");
                    }

                    break;
                }
            }
        }
    }

    /** A three-tier pagoda: cherry walls between dark posts, wide tiled eaves with turned-up corners, a gold spire. */
    private void pagoda(int x, int z) {
        this.fill(x - 6, 1, z - 6, x + 6, 1, z + 6, this.mixOf(681L, "polished_andesite", "andesite", 0.25));
        int y = 2;
        int[] widths = {5, 4, 3};
        for (int t = 0; t < widths.length; t++) {
            int w = widths[t];
            int h = t == 0 ? 5 : 4;
            for (int dx = -w; dx <= w; dx++) {
                for (int dz = -w; dz <= w; dz++) {
                    boolean edge = Math.abs(dx) == w || Math.abs(dz) == w;
                    boolean post = Math.abs(dx) == w && Math.abs(dz) == w;
                    for (int k = 0; k < h; k++) {
                        if (!edge) {
                            this.set(x + dx, y + k, z + dz, k == 0 ? "dark_oak_planks" : null);
                        } else if (post) {
                            this.set(x + dx, y + k, z + dz, "stripped_dark_oak_log[axis=y]");
                        } else {
                            boolean window = k >= 1 && k <= h - 2 && Math.floorMod(Math.abs(dx) + Math.abs(dz), 2) == 0;
                            this.set(x + dx, y + k, z + dz, window ? "red_stained_glass_pane" : (k == h - 1 ? "stripped_dark_oak_log[axis=" + (Math.abs(dz) == w ? "x" : "z") + "]" : "cherry_planks"));
                        }
                    }
                }
            }

            // the eaves: two rings of tiles round the tier, the corners turned up
            int ey = y + h;
            int e = w + 2;
            for (int dx = -e; dx <= e; dx++) {
                for (int dz = -e; dz <= e; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    String face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                    boolean corner = Math.abs(dx) == e && Math.abs(dz) == e;
                    if (ring == e) {
                        this.set(x + dx, ey + (corner ? 1 : 0), z + dz, corner ? "deepslate_tile_slab[type=bottom]" : stairs("deepslate_tile", opposite(face), false));
                    } else if (ring == e - 1) {
                        this.set(x + dx, ey + 1, z + dz, stairs("deepslate_tile", opposite(face), false));
                    } else {
                        this.set(x + dx, ey + 1, z + dz, "deepslate_tiles");
                    }
                }
            }

            for (int[] c : new int[][]{{e - 1, e - 1}, {1 - e, e - 1}, {e - 1, 1 - e}, {1 - e, 1 - e}}) {
                this.set(x + c[0], ey, z + c[1], "lantern[hanging=true]");
            }

            y = ey + 2;
        }

        for (int k = 0; k < 4; k++) {
            this.set(x, y + k, z, k == 3 ? "gold_block" : "iron_chain[axis=y]");
        }

        this.set(x, y + 4, z, "lightning_rod[facing=up]");
        for (int s = -1; s <= 1; s += 2) {
            this.set(x + s * 6, 2, z + 6, "stone_brick_wall");
            this.set(x + s * 6, 3, z + 6, "lantern[hanging=false]");
        }
    }

    /** A torii gate: two vermilion posts, a black top beam with turned-up ends, a tie beam, stone lanterns. */
    private void torii(int x, int z) {
        boolean alongX = Math.abs(x) < Math.abs(z);
        for (int k = -1; k <= 1; k += 2) {
            int px = x + (alongX ? k * 4 : 0);
            int pz = z + (alongX ? 0 : k * 4);
            this.set(px, 1, pz, "polished_andesite");
            for (int y = 2; y <= 9; y++) {
                this.set(px, y, pz, "red_concrete");
            }
        }

        for (int k = -6; k <= 6; k++) {
            int bx = x + (alongX ? k : 0);
            int bz = z + (alongX ? 0 : k);
            if (Math.abs(k) <= 5) {
                this.set(bx, 8, bz, "red_concrete");
            }

            this.set(bx, 10, bz, Math.abs(k) == 6 ? "blackstone_slab[type=bottom]" : "polished_blackstone");
            if (Math.abs(k) >= 5) {
                this.set(bx, 11, bz, "polished_blackstone_slab[type=bottom]");
            }
        }

        this.set(x, 9, z, "gold_block");
        for (int k = -1; k <= 1; k += 2) {
            int lx = x + (alongX ? k * 7 : 3);
            int lz = z + (alongX ? 3 : k * 7);
            this.set(lx, 1, lz, "stone_bricks");
            this.set(lx, 2, lz, "stone_brick_wall");
            this.set(lx, 3, lz, "lantern[hanging=false]");
            this.set(lx, 4, lz, "stone_brick_slab[type=bottom]");
        }
    }

    // ------------------------------------------------------------------ the landmarks

    /**
     * A giant champion statue on a stepped plinth: a knight in plate armour with a cape and a plumed great helm, both
     * hands resting on the pommel of a greatsword stood point-down in front of him. Built in units of {@code s} blocks
     * from the ground {@code ground}; (fx, fz) points the way he faces.
     */
    private void statue(int x0, int ground, int z0, int fx, int fz, int s) {
        String[] st = switch (this.kind) {
            case OLYMPUS -> new String[]{"smooth_quartz", "quartz_bricks", "polished_diorite"};
            case FROST -> new String[]{"packed_ice", "blue_ice", "snow_block"};
            case DESERT, TROPICAL -> new String[]{"smooth_sandstone", "cut_sandstone", "sandstone"};
            case SAKURA -> new String[]{"calcite", "polished_diorite", "white_terracotta"};
            case MESA -> new String[]{"terracotta", "smooth_red_sandstone", "red_sandstone"};
            case DARK -> new String[]{"polished_deepslate", "deepslate_tiles", "cobbled_deepslate"};
            case JUNGLE -> new String[]{"mossy_stone_bricks", "stone_bricks", "mossy_cobblestone"};
            case VOLCANIC -> new String[]{"polished_blackstone", "blackstone", "polished_basalt[axis=y]"};
            case END -> new String[]{"purpur_block", "purpur_pillar[axis=y]", "end_stone_bricks"};
            default -> new String[]{"polished_andesite", "stone_bricks", "andesite"};
        };
        String eyes = switch (this.kind) {
            case VOLCANIC -> "shroomlight";
            case DARK, FROST -> "sea_lantern";
            default -> "black_concrete";
        };
        String cloth = (this.marks == null ? "red" : this.marks.color()) + "_wool";
        int lx = -fz;
        int lz = fx;
        int feet = ground + 6 * s;
        for (int ba = -9 * s; ba < 9 * s; ba++) {
            for (int bb = -9 * s; bb < 9 * s; bb++) {
                for (int bh = -6 * s; bh < 34 * s; bh++) {
                    char c = statueUnit(Math.floorDiv(ba, s), Math.floorDiv(bh, s), Math.floorDiv(bb, s));
                    if (c == 0) {
                        continue;
                    }

                    String b = switch (c) {
                        case 'S' -> st[0];
                        case 'P' -> st[1];
                        case 'B' -> st[2];
                        case 'T' -> "gold_block";
                        case 'C' -> cloth;
                        case 'E' -> eyes;
                        case 'M' -> "iron_block";
                        case 'G' -> "polished_blackstone";
                        case 'L' -> Math.floorMod(bh, s) == 0 ? "lantern[hanging=false]" : null;
                        default -> null;
                    };
                    if (b != null) {
                        this.set(x0 + ba * lx - bb * fx, feet + bh, z0 + ba * lz - bb * fz, b);
                    }
                }
            }
        }
    }

    /** One unit of the statue: a across (right +), h up from the feet, b depth (front -); 0 = empty. */
    private static char statueUnit(int a, int h, int b) {
        int aa = Math.abs(a);
        int ab = Math.abs(b);
        if (h < 0) {
            // the plinth: three tiers, a gold band round the top of each
            int t = h >= -2 ? 6 : (h >= -4 ? 7 : 8);
            if (aa <= t && ab <= t) {
                boolean edge = aa == t || ab == t;
                return (h == -1 || h == -3 || h == -5) && edge ? 'T' : 'B';
            }

            // lanterns on the corners of the lower tiers
            return h == -4 && aa == 8 && ab == 8 || h == -2 && aa == 7 && ab == 7 ? 'L' : 0;
        }

        // the greatsword in front: point on the plinth, hands on the pommel
        if (b == -4) {
            if (a == 0 && h <= 1) {
                return 'M';
            }

            if (aa <= 1 && h >= 2 && h <= 12) {
                return 'M';
            }

            if (h == 13 && aa <= 4 || h == 14 && aa == 4) {
                return 'T';
            }

            if (a == 0 && h >= 14 && h <= 16) {
                return 'G';
            }

            if (a == 0 && h == 17) {
                return 'T';
            }
        }

        // gauntlets round the grip, forearms back to the elbows
        if (h >= 14 && h <= 16 && b >= -5 && b <= -3 && aa >= 1 && aa <= 2) {
            return 'P';
        }

        if (h >= 15 && h <= 16 && b >= -3 && b <= -1 && aa >= b + 5 && aa <= b + 6) {
            return 'S';
        }

        // legs: sabatons, greaves, knee cops
        if (aa >= 1 && aa <= 3) {
            if (h <= 1 && b >= -2 && b <= 1) {
                return 'P';
            }

            if (h >= 2 && h <= 11 && b >= -1 && b <= 1) {
                return h >= 5 && h <= 7 && b == -1 ? 'P' : 'S';
            }
        }

        if (aa == 2 && h == 6 && b == -2) {
            return 'T';
        }

        // the armoured skirt and a gold belt
        if (h >= 11 && h <= 13 && aa <= 4 && b >= -2 && b <= 1) {
            return h == 13 ? 'T' : 'P';
        }

        // the torso: a breastplate with a gold emblem
        if (h >= 14 && h <= 22 && b >= -2 && b <= 1) {
            int w = h <= 15 ? 3 : 4;
            if (aa <= w) {
                if (b == -2) {
                    boolean emblem = aa <= 1 && h >= 18 && h <= 20 || a == 0 && (h == 17 || h == 21);
                    return emblem ? 'T' : 'P';
                }

                return 'S';
            }
        }

        // pauldrons with gold rims, the upper arms
        if (h >= 20 && h <= 23 && aa >= 5 && aa <= 7 && b >= -2 && b <= 1) {
            boolean corner = aa == 7 && (h == 23 || b == -2 || b == 1);
            if (!corner) {
                return h == 20 ? 'T' : 'P';
            }
        }

        if (h >= 15 && h <= 19 && aa >= 5 && aa <= 6 && b >= -1 && b <= 0) {
            return 'S';
        }

        // the neck and a great helm with an eye slit, a gold crest and a plume
        if (h == 23 && aa <= 1 && b >= -1 && b <= 0) {
            return 'S';
        }

        if (h >= 24 && h <= 29 && aa <= 2 && ab <= 2) {
            if (h == 29 && aa == 2 && ab == 2) {
                return 0;
            }

            if (b == -2 && h == 26 && a != 0) {
                return 'E';
            }

            return h == 24 && b == -2 ? 'T' : 'P';
        }

        if (h == 30 && aa <= 1 && ab <= 1) {
            return 'T';
        }

        if (a == 0 && (h == 31 && b >= -1 && b <= 2 || h == 32 && b >= 0 && b <= 2 || h == 30 && b >= 2 && b <= 4 || h == 29 && b >= 3 && b <= 4)) {
            return 'C';
        }

        // the cape from the shoulders, falling in folds
        if (h >= 3 && h <= 22) {
            int half = 5 + (22 - h) / 7;
            int back = (h >= 15 ? 2 : 3) + (aa % 2 == 0 && h < 20 ? 1 : 0);
            if (aa <= half && b >= 2 && b <= back) {
                return h == 3 ? 'T' : 'C';
            }
        }

        return 0;
    }

    /**
     * A giant signboard: a framed board in the theme's colour with the text in raised letters, glowstone bulbs round
     * the frame, lanterns under it, standing on two braced posts down to the hill. Built in units of {@code s} blocks;
     * (fx, fz) points the way it faces.
     */
    private void signboard(int x0, int z0, int fx, int fz, int s, int boardY, int ground, String text) {
        String color = this.marks == null ? "red" : this.marks.color();
        boolean pale = color.equals("white") || color.equals("yellow") || color.equals("light_gray") || color.equals("lime") || color.equals("pink");
        String bg = color + "_concrete";
        String fg = pale ? "black_concrete" : "white_concrete";
        String frame = this.kind == Kind.FROST ? "stripped_spruce_log" : (this.kind == Kind.SAKURA ? "stripped_cherry_log" : "stripped_dark_oak_log");
        String back = this.kind == Kind.FROST ? "spruce_planks" : "dark_oak_planks";
        int wu = PixelFont.width(text) + 6;
        int hu = PixelFont.ROWS + 4;
        // the reader faces the board: their right is (fz, -fx)
        int rx = fz;
        int rz = -fx;
        int w = wu * s;
        int h = hu * s;
        int left = -w / 2;
        String axis = rx != 0 ? "x" : "z";
        for (int bc = 0; bc < w; bc++) {
            int c = bc / s;
            for (int bj = 0; bj < h; bj++) {
                int j = bj / s;
                int y = boardY + h - 1 - bj;
                int lat = left + bc;
                boolean frameCell = c == 0 || c == wu - 1 || j == 0 || j == hu - 1;
                boolean px = !frameCell && j >= 2 && j < 2 + PixelFont.ROWS && PixelFont.textOn(text, c - 3, j - 2);
                for (int bd = -s; bd < 2 * s; bd++) {
                    int d = Math.floorDiv(bd, s);
                    String b;
                    if (frameCell) {
                        boolean bulb = (c + j) % 3 == 0 && Math.floorMod(bc, s) == 0 && Math.floorMod(bj, s) == 0;
                        b = d == 1 ? back : (bulb && d == -1 ? "glowstone" : frame + "[axis=" + (c == 0 || c == wu - 1 ? "y" : axis) + "]");
                    } else if (d == -1) {
                        b = px ? fg : null;
                    } else if (d == 0) {
                        b = px ? fg : bg;
                    } else {
                        b = back;
                    }

                    if (b != null) {
                        this.set(x0 + lat * rx - bd * fx, y, z0 + lat * rz - bd * fz, b);
                    }
                }
            }
        }

        // lanterns hanging under the bottom edge
        for (int c = 2; c < wu - 2; c += 4) {
            int lat = left + c * s + s / 2;
            this.set(x0 + lat * rx + s * fx, boardY - 1, z0 + lat * rz + s * fz, "lantern[hanging=true]");
        }

        // two posts behind it down to the hill, with cross beams
        int top = boardY + h - 1;
        int pw = Math.max(2, s + 1);
        int pa = left + w / 4;
        int pb = left + 3 * w / 4;
        for (int pc : new int[]{pa, pb}) {
            for (int dl = 0; dl < pw; dl++) {
                for (int dd = 2 * s; dd < 2 * s + pw; dd++) {
                    for (int y = ground + 1; y <= top; y++) {
                        this.set(x0 + (pc + dl) * rx - dd * fx, y, z0 + (pc + dl) * rz - dd * fz, "dark_oak_log[axis=y]");
                    }
                }
            }
        }

        for (int y = boardY - 2; y > ground + 3; y -= 7) {
            for (int lat = pa; lat <= pb + pw - 1; lat++) {
                this.set(x0 + lat * rx - 2 * s * fx, y, z0 + lat * rz - 2 * s * fz, "dark_oak_log[axis=" + axis + "]");
            }
        }
    }

    /**
     * A giant sword stood point-down in a rock: a blade with a fuller and glowing runes, a curled gold guard, a wrapped
     * grip and a pommel with a gem, its flat turned to the arena. Built in units of {@code s} blocks from {@code base}
     * (the rock's foot); (fx, fz) points to the arena.
     */
    private void swordMonument(int x0, int base, int z0, int fx, int fz, int s) {
        String[] st = switch (this.kind) {
            case FROST -> new String[]{"packed_ice", "snow_block"};
            case DESERT, TROPICAL -> new String[]{"sandstone", "smooth_sandstone"};
            case MESA -> new String[]{"terracotta", "red_sandstone"};
            case DARK -> new String[]{"cobbled_deepslate", "deepslate"};
            case JUNGLE -> new String[]{"mossy_cobblestone", "moss_block"};
            case VOLCANIC -> new String[]{"blackstone", "magma_block"};
            case END -> new String[]{"end_stone", "obsidian"};
            case OLYMPUS -> new String[]{"calcite", "diorite"};
            default -> new String[]{"stone", "cobblestone"};
        };
        String gem = switch (this.marks == null ? "red" : this.marks.color()) {
            case "blue", "light_blue", "cyan" -> "diamond_block";
            case "green", "lime" -> "emerald_block";
            case "purple", "pink", "magenta" -> "amethyst_block";
            case "yellow", "orange" -> "raw_gold_block";
            default -> "redstone_block";
        };
        String rune = this.kind == Kind.DARK || this.kind == Kind.FROST ? "sea_lantern" : (this.kind == Kind.VOLCANIC ? "shroomlight" : "glowstone");
        // the rock it stands in
        java.util.Random r = new java.util.Random(this.seed ^ 0x5D0AL);
        for (int k = 0; k < 7; k++) {
            double a = k * Math.PI * 2.0 / 7.0 + r.nextDouble() * 0.4;
            double d = k == 0 ? 0.0 : (2.5 + r.nextDouble() * 2.0) * s;
            this.blob(x0 + (int)Math.round(Math.cos(a) * d), base, z0 + (int)Math.round(Math.sin(a) * d), (k == 0 ? 5.5 : 3.2 + r.nextDouble() * 1.5) * s, st[0]);
        }

        for (int dx = -6 * s; dx <= 6 * s; dx++) {
            for (int dz = -6 * s; dz <= 6 * s; dz++) {
                for (int y = base - 2 * s; y <= base + 6 * s; y++) {
                    String b = this.get(x0 + dx, y, z0 + dz);
                    if (st[0].equals(b) && hash3(this.seed + 691L, x0 + dx, y, z0 + dz) < 0.3) {
                        this.set(x0 + dx, y, z0 + dz, st[1]);
                    }
                }
            }
        }

        int lx = -fz;
        int lz = fx;
        int foot = base + 3 * s;
        for (int ba = -6 * s; ba < 6 * s; ba++) {
            for (int bb = -2 * s; bb < 2 * s; bb++) {
                for (int bh = -6 * s; bh < 32 * s; bh++) {
                    int a = Math.floorDiv(ba, s);
                    int h = Math.floorDiv(bh, s);
                    int b = Math.floorDiv(bb, s);
                    int aa = Math.abs(a);
                    int ab = Math.abs(b);
                    String m = null;
                    if (b == 0 && h <= 21 && (aa <= 1 || aa == 2 && h >= 2 && h <= 19)) {
                        // the blade, wider above the rock, with a fuller and runes down its middle
                        m = aa == 0 ? (h > 2 && h < 20 && h % 4 == 1 ? rune : "smooth_stone") : (aa == 2 ? "iron_block" : "polished_diorite");
                        if (h < 0 && aa > 0) {
                            m = null;
                        }
                    } else if (h == 22 && (aa <= 5 && b == 0 || aa <= 1 && ab <= 1)) {
                        m = "gold_block";
                    } else if (h == 23 && aa == 5 && b == 0 || h == 21 && aa == 4 && b == 0) {
                        m = "gold_block";
                    } else if (h >= 23 && h <= 27 && a == 0 && b == 0) {
                        m = h == 24 || h == 26 ? "gold_block" : "stripped_dark_oak_log[axis=y]";
                    } else if (h >= 28 && h <= 30 && aa <= 1 && ab <= 1 && !(aa == 1 && ab == 1 && h != 29)) {
                        m = h == 29 && b == -1 && a == 0 ? gem : "gold_block";
                    }

                    if (m != null) {
                        this.set(x0 + ba * lx - bb * fx, foot + bh, z0 + ba * lz - bb * fz, m);
                    }
                }
            }
        }

        // a ring of lanterns on posts round the rock
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4.0 + 0.3;
            int px = x0 + (int)Math.round(Math.cos(a) * 8.5 * s);
            int pz = z0 + (int)Math.round(Math.sin(a) * 8.5 * s);
            int gy = base - 1;
            for (int y = gy + 1; y <= gy + 2; y++) {
                this.set(px, y, pz, "dark_oak_fence");
            }

            this.set(px, gy + 3, pz, (this.kind == Kind.DARK || this.kind == Kind.FROST ? "soul_lantern" : "lantern") + "[hanging=false]");
        }
    }

    /** Small islands floating in the sky round the arena, each with a tree or a rock on top. */
    private void skyIslets(java.util.Random rr) {
        int lift = this.marks != null && this.marks.scale() > 1 ? 22 : 0;
        for (int i = 0; i < 8; i++) {
            double deg = i * 45 + 10 + rr.nextDouble() * 25;
            if (this.marks != null) {
                // keep clear of the landmarks at the ends
                double m = Math.floorMod((int)Math.round(deg), 180);
                if (m < 32) {
                    deg += 34 - m;
                } else if (m > 148) {
                    deg -= m - 146;
                }
            }

            int[] p = this.along(deg, 14 + rr.nextDouble() * 16);
            int ox = p[0];
            int oz = p[1];
            int oy = 40 + lift + rr.nextInt(16);
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
                        this.set(x, oy - k, z, k == 0 ? (oy >= this.kind.snowLine ? "snow_block" : this.kind.top) : (k <= 1 ? this.kind.under : this.rock(x, oy - k, z)));
                    }

                    if (hash3(this.seed + 681L, ox + dx, oy, oz + dz) < 0.1 && this.kind != Kind.VOLCANIC && this.kind != Kind.DESERT && this.kind != Kind.END) {
                        this.set(ox + dx, oy - deep - 1, oz + dz, "hanging_roots");
                    }
                }
            }

            if (tree) {
                this.tree(ox, oy + 1, oz, size % 3, kind, oy >= this.kind.snowLine);
            } else {
                this.blob(ox, oy + 1, oz, 1.4, this.rock(ox, oy, oz));
            }
        }
    }
}
