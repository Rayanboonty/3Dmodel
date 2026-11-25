package net.practicepvp.build;

import java.util.Locale;

/**
 * The pro arenas (3.15.0): every designed arena rebuilt the way big build teams make them - depth in every wall,
 * layer behind layer, a texture in every surface, a light in every niche, and a whole world around the fight.
 *
 * <p><b>Colosseums</b> (the tournament arena, the mace arena, the gladiator pit) stand round a racetrack-shaped floor.
 * From the floor outward: a podium of pilasters with arched niches set one block back (a banner or a hanging lantern in
 * each), a terrace with a railing, urns and cypresses, the lower stands in coloured sectors with aisles and walkways, an
 * arcade with a banner in every arch, the upper stands, a two-storey colonnade with a gallery behind it, and over it a
 * tiled awning on timber rafters with masts flying banners. The outer wall is three storeys of open arches. Two
 * monumental gates with temple fronts, red pediments and portcullises stand at the ends.
 *
 * <p><b>Courts</b> (every duel arena) have a rounded rectangle floor. Around it: stone walls with wooden pilasters,
 * light arched panels and banners in the niches, a balcony with flower boxes and lantern posts, an arcade with arched
 * openings, buildings with windows, tiled roofs with rafters and chimneys, round towers on the corners (cones, onion
 * domes, pyramids or spires by theme) and a fortified gatehouse on each short side.
 *
 * <p>The floors are flat (nothing to bump into) and plain: natural (sand with sandstone, red sand, snow) or tonal
 * (shades of one colour), with no decoration. Only the signature arena has a small sword inlaid in the middle of its
 * floor. No ice or magma on a floor. Behind everything, the
 * landscape ring of {@link ArenaLandscape} with a giant statue and a giant sword monument. An invisible wall over the
 * floor's edge keeps every fight on the floor.
 *
 * <p>Only the signature arena carries the server's name: giant cloths over its arcades and a giant signboard in place of
 * the sword.
 */
public final class ProArenaDesign extends CourtParts implements ArenaVoxels {
    public enum Theme {
        ROMAN(true), GRAND(true), GLADIATOR(true),
        MEDIEVAL(false), FROST(false), DESERT(false), HIGHLAND(false), SAKURA(false), EMBER(false),
        GOTHIC(false), TROPICAL(false), JUNGLE(false), VOLCANO(false), HARBOR(false), PHARAOH(false),
        ATLANTIS(false), STARFALL(false);

        public final boolean stadium;

        Theme(boolean stadium) {
            this.stadium = stadium;
        }

        /** A court of the ring design: walls with an arcade, roofs and towers all round the floor. */
        public boolean walled() {
            return this == ATLANTIS || this == STARFALL;
        }
    }

    /** The blocks of a theme. */
    static final class Pal {
        /** The floor's blocks, the main one first. */
        String[] floorMix;
        /** Their shares (tonal floors), or null for a natural floor of patches. */
        double[] floorW;
        /** A giant weapon inlaid in the floor, or null. */
        Motif motif;
        String emDark = "polished_blackstone";
        String emGold = "gold_block";
        String wall;
        String wallAlt;
        String wallAlt2;
        String trim;
        /** The band round the top of the colosseum podium (the trim when not set). */
        String band;
        String pillar;
        String capital;
        String base;
        String panel;
        String panelAlt;
        String window;
        String log = "stripped_dark_oak_log";
        String planks = "spruce_planks";
        String fence = "spruce_fence";
        String roofStair;
        String roofBlock;
        String roofAlt;
        String dome;
        String seat;
        String seat2;
        String stand;
        String walkway;
        String aisle;
        String arch;
        String column;
        String[] banners = {"red", "white"};
        String clothBg = "red";
        String clothFg = "white";
        String clothTrim = "yellow";
        String light = "lantern";
        String leaves = "spruce";
        String[] flowers = {"poppy", "dandelion", "azure_bluet", "oxeye_daisy", "red_tulip"};
        String glass = "glass_pane";
        ArenaLandscape.Kind land = ArenaLandscape.Kind.ALPINE;
        /** Tower tops: CONE, ONION, PYRAMID, SPIRE. */
        String towerTop = "CONE";
        /** Courts: the main building (CASTLE, PALACE, CATHEDRAL, PAGODA, ZIGGURAT, PYRAMID). */
        String court = "CASTLE";
        /** Courts: what faces it (STAND, LOGGIA, PAVILION). */
        String south = "STAND";
        /** Courts: the garden trees (OAK, SPRUCE, SNOWY, PALM, CHERRY, DARK, DEAD, JUNGLE). */
        String tree = "OAK";
        /** Courts: the gardens' ground, the walk round the floor, the hedges (null for none). */
        String[] plaza = {"grass_block", "moss_block", "coarse_dirt"};
        String paving = "polished_andesite";
        String hedge = "oak";
        /** Courts: the castle stands on a rock; a lighthouse in place of its keep. */
        boolean rock;
        boolean lighthouse;
    }

    public final Theme theme;
    private final boolean named;
    private final long seed;
    private final int depth;
    private final Pal pal;
    private final double radius;
    private final double straight;
    private final int hx;
    private final int hz;
    private final int rc;
    private final int pH;
    private final int n1;
    private final int n2;
    private final int bay;
    private final double bayLen;
    private final int dOut;
    private final int full;
    private final int out;
    private final int yTer;
    private final int yArcBase;
    private final int yArcTop;
    private final int yColBase;
    private final int yColTop;
    private final int storey;
    private final ArenaLandscape ring;

    private static final int ARC = 7;
    private static final int COL = 13;
    private static final int AH = 8;
    /** Half width of a colosseum gate. */
    private final int gateW;

    /**
     * @param named the signature arena: the server's name on giant cloths over its arcades and on a giant signboard in
     *              its landscape. Every other arena has a giant sword monument in the sign's place and no name.
     */
    public ProArenaDesign(Theme theme, int size, long seed, int cx, int floorY, int cz, int depth, boolean ffa, boolean barriers, boolean named) {
        this(theme, dims(theme, size), seed, cx, floorY, cz, depth, barriers, named);
    }

    private ProArenaDesign(Theme theme, double[] dm, long seed, int cx, int floorY, int cz, int depth, boolean barriers, boolean named) {
        super(cx, floorY, cz, (int)dm[9], -depth, (int)dm[10], seed);
        this.theme = theme;
        this.seed = seed;
        this.depth = depth;
        this.radius = dm[0];
        this.straight = dm[1];
        this.hx = (int)dm[2];
        this.hz = (int)dm[3];
        this.rc = (int)dm[4];
        this.pH = (int)dm[5];
        this.n1 = (int)dm[6];
        this.n2 = (int)dm[7];
        this.dOut = (int)dm[8];
        this.out = (int)dm[11];
        this.full = (int)dm[12];
        this.bay = theme.stadium ? 7 : 6;
        double perimeter = theme.stadium
            ? 4.0 * this.straight + 2.0 * Math.PI * this.radius
            : 4.0 * (this.hx - this.rc) + 4.0 * (this.hz - this.rc) + 2.0 * Math.PI * this.rc;
        this.bayLen = perimeter / Math.max(8L, Math.round(perimeter / this.bay));
        this.yTer = this.pH + 1;
        this.yArcBase = this.yTer + this.n1;
        this.yArcTop = this.yArcBase + ARC;
        this.yColBase = this.yArcTop + this.n2;
        this.yColTop = this.yColBase + COL;
        this.storey = Math.max(6, this.yColBase / 3);
        this.gateW = theme == Theme.GLADIATOR ? 9 : 16;
        this.named = named;
        this.pal = palette(theme);
        this.ground();
        if (theme.stadium) {
            this.perimeter();
            this.stadiumGates();
            if (named) {
                this.cloth();
            }
        } else if (theme.walled()) {
            this.perimeter();
            this.courtTowers();
            this.courtGates();
        } else {
            this.court();
        }

        if (barriers) {
            this.barrierRing();
        }

        this.finish();
        if (this.out >= 12) {
            int sp = this.spawn(1)[0];
            // what hides the landmarks from the far spawn: the gate's pediment (colosseums), the gate towers' roofs (courts)
            ArenaLandscape.Marks marks = theme.stadium
                ? new ArenaLandscape.Marks(named ? PixelFont.serverName() : null, theme == Theme.GLADIATOR ? 1 : 2, this.yColTop + this.gateW * 6 / 11 + 3.0,
                    this.straight + this.radius, sp, this.pal.clothBg)
                : new ArenaLandscape.Marks(named ? PixelFont.serverName() : null, 1, theme.walled() ? this.pH + 1 + AH + 14.0 : 24.0,
                    theme.walled() ? this.hx + 2.0 : this.hx + 6.0, sp, this.pal.clothBg);
            this.ring = new ArenaLandscape(this.pal.land, cx, floorY, cz, this.full, this.full, this::sd, this.dOut + 2.0, this.out, Math.max(6, depth), 112, seed, marks);
        } else {
            this.ring = null;
        }
    }

    /** {radius, straight, hx, hz, rc, podium, rows1, rows2, dOut, ownRadius, yHi, ring, full}. */
    private static double[] dims(Theme t, int size) {
        int inner = Math.max(64, (size - 1) / 2);
        int hx = (int)Math.round(Math.max(28.0, Math.min(72.0, inner * 0.5)));
        int hz = (int)Math.round(hx * 0.7);
        int rc = Math.max(4, (int)Math.round(hz * 0.25));
        double radius = 0.0;
        double straight = 0.0;
        int pod = 6;
        int r1 = 0;
        int r2 = 0;
        int dOut;
        int own;
        if (t.stadium) {
            switch (t) {
                case ROMAN -> {
                    radius = Math.max(24, Math.min(110, Math.round(size * 0.27)));
                    straight = Math.max(6, Math.min(44, Math.round(size * 0.11)));
                    r1 = (int)Math.max(6, Math.min(10, radius / 7));
                    r2 = r1 - 1;
                }
                case GRAND -> {
                    radius = Math.max(26, Math.min(140, Math.round(size * 0.3)));
                    r1 = 10;
                    r2 = 9;
                }
                default -> {
                    radius = hz;
                    straight = hx - hz;
                    pod = 8;
                    r1 = 6;
                    r2 = 5;
                }
            }

            dOut = 8 + r1 + r2 + 3;
            own = (int)Math.ceil(straight + radius) + dOut + 4;
        } else {
            dOut = t.walled() ? 16 : 44;
            own = Math.max(hx, hz) + dOut + 6;
        }

        int ring = ArenaLandscape.enabled() ? Math.max(0, Math.min(56, 255 - own)) : 0;
        if (ring < 12) {
            ring = 0;
        }

        return new double[]{radius, straight, hx, hz, rc, pod, r1, r2, dOut, own, 112, ring, own + ring};
    }

    /** Half the side of the whole footprint (the build and its landscape) of a pro arena of this theme and size. */
    public static int half(Theme t, int size) {
        return (int)dims(t, size)[12];
    }

    /**
     * The blocks of a theme for a small room in its style (the waiting room before a match): floor, wall, wall
     * accent, pillar, trim, roof, glass pane, light (a lantern kind), banner colour.
     */
    public static String[] roomBlocks(Theme t) {
        Pal p = palette(t);
        String pillar = t.stadium && p.pillar != null ? p.pillar : p.log + "[axis=y]";
        return new String[]{p.floorMix[0], p.wall, p.wallAlt, pillar, p.trim, p.roofBlock, p.glass, p.light, p.banners[0]};
    }

    /** Signed distance from the floor's edge of a pro arena of this theme and size (negative on the floor), without building it. */
    public static double floorDistance(Theme t, int size, double x, double z) {
        double[] dm = dims(t, size);
        if (t.stadium) {
            double px = Math.max(Math.abs(x) - dm[1], 0.0);
            return Math.sqrt(px * px + z * z) - dm[0];
        }

        double qx = Math.abs(x) - (dm[2] - dm[4]);
        double qz = Math.abs(z) - (dm[3] - dm[4]);
        double ox = Math.max(qx, 0.0);
        double oz = Math.max(qz, 0.0);
        return Math.sqrt(ox * ox + oz * oz) + Math.min(Math.max(qx, qz), 0.0) - dm[4];
    }

    @Override
    public String name() {
        return this.theme.name().charAt(0) + this.theme.name().substring(1).toLowerCase(Locale.ROOT) + " Arena";
    }

    // ------------------------------------------------------------------ geometry

    /** Signed distance from the edge of the fighting floor (negative on the floor). */
    public double sd(double x, double z) {
        if (this.theme.stadium) {
            double px = Math.max(Math.abs(x) - this.straight, 0.0);
            return Math.sqrt(px * px + z * z) - this.radius;
        }

        double qx = Math.abs(x) - (this.hx - this.rc);
        double qz = Math.abs(z) - (this.hz - this.rc);
        double ox = Math.max(qx, 0.0);
        double oz = Math.max(qz, 0.0);
        return Math.sqrt(ox * ox + oz * oz) + Math.min(Math.max(qx, qz), 0.0) - this.rc;
    }

    /** The position along the floor's edge (blocks), for spacing bays round the arena. */
    private double along(double x, double z) {
        if (this.theme.stadium) {
            double l = this.straight;
            double r = this.radius;
            if (Math.abs(x) <= l) {
                return z >= 0.0 ? x + l : 2.0 * l + Math.PI * r + (l - x);
            } else if (x > l) {
                return 2.0 * l + (Math.PI / 2.0 - Math.atan2(z, x - l)) * r;
            } else {
                double a = Math.atan2(z, x + l);
                if (a < 0.0) {
                    a += Math.PI * 2.0;
                }

                return 4.0 * l + Math.PI * r + (Math.PI * 1.5 - a) * r;
            }
        }

        double a = this.hx - this.rc;
        double b = this.hz - this.rc;
        double r = this.rc;
        double q = Math.PI / 2.0 * r;
        if (Math.abs(x) <= a) {
            return z >= 0.0 ? x + a : 2 * a + 2 * b + 2 * q + (a - x);
        } else if (Math.abs(z) <= b) {
            return x > 0.0 ? 2 * a + q + (b - z) : 4 * a + 2 * b + 3 * q + (z + b);
        } else {
            double ang = Math.atan2(z - Math.signum(z) * b, x - Math.signum(x) * a);
            if (x > 0 && z > 0) {
                return 2 * a + (Math.PI / 2.0 - ang) * r;
            } else if (x > 0) {
                return 2 * a + 2 * b + q + (-ang) * r;
            } else if (z < 0) {
                return 4 * a + 2 * b + 2 * q + (-Math.PI / 2.0 - ang + (ang > 0 ? 2 * Math.PI : 0)) * r;
            } else {
                return 4 * a + 4 * b + 3 * q + (Math.PI - ang) * r;
            }
        }
    }

    /** Facing name pointing away from the floor at (x, z). */
    private String outward(double x, double z) {
        double gx = this.sd(x + 0.5, z) - this.sd(x - 0.5, z);
        double gz = this.sd(x, z + 0.5) - this.sd(x, z - 0.5);
        return facing(gx, gz);
    }

    /** Horizontal log axis along the wall that faces {@code out}. */
    private static String alongAxis(String out) {
        return out.equals("north") || out.equals("south") ? "x" : "z";
    }

    /** Horizontal log axis across the wall (pointing out of it). */
    private static String acrossAxis(String out) {
        return out.equals("north") || out.equals("south") ? "z" : "x";
    }

    private String tex(long salt, int x, int y, int z, String a, String b, String c) {
        double r = hash3(this.seed + salt, x, y, z);
        return r < 0.16 ? b : (r < 0.24 ? c : a);
    }

    private int spawnOffset() {
        return (int)Math.round(this.theme.stadium ? this.straight + this.radius * 0.6 : this.hx * 0.62);
    }

    @Override
    public int[] spawn(int side) {
        int sp = this.spawnOffset();
        return new int[]{side == 0 ? -sp : sp, 1, 0};
    }

    @Override
    public int[] ffaSpot(int i, int n) {
        double t = Math.PI * 2.0 * i / n;
        double ax = (this.theme.stadium ? this.straight + this.radius : this.hx) * 0.62;
        double bz = (this.theme.stadium ? this.radius : this.hz) * 0.62;
        return new int[]{(int)Math.round(Math.cos(t) * ax), 1, (int)Math.round(Math.sin(t) * bz)};
    }

    // ------------------------------------------------------------------ the ring of landscape round it

    @Override
    public int minX() {
        return this.cx - this.full;
    }

    @Override
    public int maxX() {
        return this.cx + this.full;
    }

    @Override
    public int minZ() {
        return this.cz - this.full;
    }

    @Override
    public int maxZ() {
        return this.cz + this.full;
    }

    @Override
    public int topY() {
        return this.ring == null ? super.topY() : Math.max(super.topY(), this.ring.topY());
    }

    @Override
    public void column(int wx, int wz, Voxels.Sink sink) {
        if (this.ring != null && this.ring.owns(wx - this.cx, wz - this.cz)) {
            this.ring.column(wx, wz, sink);
        } else {
            super.column(wx, wz, sink);
        }
    }

    // ------------------------------------------------------------------ ground and floor

    private void ground() {
        this.buildMosaic();
        int lim = this.dOut + 2;
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d > lim) {
                    continue;
                }

                if (d < 0.0) {
                    this.set(x, -this.depth, z, "bedrock");
                }

                int bottom = d < 0.0 ? -this.depth + 1 : -Math.min(this.depth - 1, 6 + (int)Math.round(Math.max(0, lim - d) * 0.5));
                for (int y = bottom; y < 0; y++) {
                    double v = noise(this.seed + 41L, x * 2 + y, z * 2 - y);
                    this.set(x, y, z, y >= -3 ? "dirt" : (v > 0.74 ? "andesite" : (v < 0.2 ? "tuff" : "stone")));
                }

                this.set(x, 0, z, d < 0.0 ? this.floorBlock(x, z, d) : (this.theme.stadium || this.theme.walled() ? this.pal.base : this.plazaBlock(x, z, d)));
            }
        }
    }

    /**
     * A small sword inlaid in the signature arena's floor, drawn like pixel art: a dark outline, a lit rim on the side the light comes
     * from and a shaded rim on the other, a stone texture inside, a bright cutting edge and a drop shadow on the floor.
     * {@code metal} = outline, shaded rim, inside, inside (texture), lit rim, cutting edge, fuller; {@code wood} = shaded,
     * inside, lit, grip wrap, grip wrap; {@code gold} = shaded, gold, gem.
     */
    static final class Motif {
        final String shape;
        final String[] metal;
        final String[] wood;
        final String[] gold;
        final String shadow;

        Motif(String shape, String[] metal, String[] wood, String[] gold, String shadow) {
            this.shape = shape;
            this.metal = metal;
            this.wood = wood;
            this.gold = gold;
            this.shadow = shadow;
        }

        static Motif ironSword() {
            return new Motif("SWORD", new String[]{"polished_blackstone", "polished_andesite", "smooth_stone", "stone", "polished_diorite", "iron_block", "andesite"},
                new String[]{"dark_oak_planks", "spruce_planks", "oak_planks", "red_terracotta", "red_nether_bricks"},
                new String[]{"raw_gold_block", "gold_block", "redstone_block"}, "deepslate_bricks");
        }
    }

    /** The floor's mosaic: the block of each column of the floor that is part of it (or its shadow), else null. */
    private String[][] mosaic;

    /** The floor: one texture of the theme's blocks; only the signature arena has a small sword inlaid in it. */
    private String floorBlock(int x, int z, double d) {
        Pal p = this.pal;
        if (this.mosaic != null) {
            String m = this.mosaic[x + this.R][z + this.R];
            if (m != null) {
                return m;
            }
        }

        String[] mix = p.floorMix;
        double h = hash3(this.seed + 13L, x, 0, z);
        if (p.floorW != null) {
            // a tonal floor: shades of one colour, block by block
            double acc = 0.0;
            for (int i = 0; i < mix.length; i++) {
                acc += p.floorW[i];
                if (h < acc) {
                    return mix[i];
                }
            }

            return mix[0];
        }

        // a natural floor: the main block with patches and specks of the others
        double n = noise(this.seed + 11L, x * 1.25, z * 1.25);
        double n2 = noise(this.seed + 17L, x * 2.2 + 50.0, z * 2.2);
        if (mix.length > 1 && n > 0.7) {
            return mix[1];
        }

        if (mix.length > 2 && n2 > 0.78) {
            return mix[2];
        }

        if (mix.length > 1 && h > 0.965) {
            return mix[mix.length > 2 ? 2 : 1];
        }

        return mix[0];
    }

    /**
     * One pixel of the small sword, in diagonal coordinates: p runs along it (the tip toward the north-east), q across
     * it (negative toward the north-west, where the light comes from). Null outside it.
     */
    private static String swordPixel(Motif m, int p, int q) {
        int aq = Math.abs(q);
        if (p >= -4 && p <= 14) {
            // the blade: a bright edge on the lit side, a lighter tip
            int hw = p >= 13 ? 0 : 1;
            if (aq <= hw) {
                return p >= 13 ? m.metal[4] : (q < 0 ? m.metal[5] : (q == 0 ? m.metal[2] : m.metal[1]));
            }

            return null;
        }

        if (p == -6 || p == -5) {
            // the guard, a gem in its middle
            if (aq <= 6) {
                return p == -6 && q == 0 ? m.gold[2] : (q > 0 ? m.gold[0] : m.gold[1]);
            }

            return null;
        }

        if (p >= -13 && p <= -7) {
            // the grip, wrapped
            if (aq <= 1) {
                return Math.floorMod(p, 4) < 2 ? m.wood[3] : (q < 0 ? m.wood[2] : (q == 0 ? m.wood[1] : m.wood[0]));
            }

            return null;
        }

        if (p >= -17 && p <= -14 && Math.abs(2 * p + 31) + 2 * aq <= 5) {
            // the pommel
            return p == -16 && q == 0 ? m.gold[2] : (q > 0 ? m.gold[0] : m.gold[1]);
        }

        return null;
    }

    /** The signature arena's small sword in the middle of the floor: pixel art with an outline and a drop shadow. */
    private void buildMosaic() {
        Motif m = this.pal.motif;
        if (m == null) {
            return;
        }

        int n = 2 * this.R + 1;
        int e = 14;
        this.mosaic = new String[n][n];
        boolean[][] in = new boolean[n][n];
        for (int x = -e; x <= e; x++) {
            for (int z = -e; z <= e; z++) {
                String b = swordPixel(m, x - z, x + z);
                if (b != null) {
                    this.mosaic[x + this.R][z + this.R] = b;
                    in[x + this.R][z + this.R] = true;
                }
            }
        }

        boolean[][] line = new boolean[n][n];
        for (int x = -e; x <= e; x++) {
            for (int z = -e; z <= e; z++) {
                if (!in[x + this.R][z + this.R] && (in[x + 1 + this.R][z + this.R] || in[x - 1 + this.R][z + this.R] || in[x + this.R][z + 1 + this.R] || in[x + this.R][z - 1 + this.R])) {
                    this.mosaic[x + this.R][z + this.R] = m.metal[0];
                    line[x + this.R][z + this.R] = true;
                }
            }
        }

        // the drop shadow, cast away from the light
        for (int x = -e; x <= e; x++) {
            for (int z = -e; z <= e; z++) {
                int ix = x + this.R;
                int iz = z + this.R;
                if (this.mosaic[ix][iz] == null && (in[ix - 1][iz - 1] || line[ix - 1][iz - 1])) {
                    this.mosaic[ix][iz] = m.shadow;
                }
            }
        }
    }

    // ------------------------------------------------------------------ the perimeter, column by column

    private void perimeter() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.0 || d >= this.dOut) {
                    continue;
                }

                double t = this.along(x, z) / this.bayLen;
                int bi = (int)Math.floor(t);
                double u = (t - bi) * this.bay;
                if (this.theme.stadium) {
                    this.stadiumColumn(x, z, d, u, bi, this.outward(x, z));
                } else {
                    this.courtColumn(x, z, d, u, bi, this.outward(x, z));
                }
            }
        }
    }

    private void fillTo(int x, int z, int top, String a, String b, String c) {
        for (int y = 1; y <= top; y++) {
            this.set(x, y, z, this.tex(301L, x, y, z, a, b, c));
        }
    }

    /** How far up an arched opening goes at this point of the bay: {@code top} in the middle, {@code rise} less at the sides. */
    private double archTop(double top, double u, double rise) {
        double m = this.mid(u);
        return top - m * m * rise;
    }

    /** 0 in the middle of a bay's opening, 1 at its sides (more on the pilaster). */
    private double mid(double u) {
        return Math.abs((u - 2.0) / (this.bay - 2.0) - 0.5) * 2.0;
    }

    private String wallBanner(String color, String facing) {
        return color + "_wall_banner[facing=" + facing + "]";
    }

    // ---------------------------------------------------------------- colosseum

    private void stadiumColumn(int x, int z, double d, double u, int bi, String out) {
        Pal p = this.pal;
        String in = opposite(out);
        boolean pil = u < 2.0;
        double mid = this.mid(u);
        int c0 = 8 + this.n1 + this.n2;
        if (d < 1.0) {
            // the podium's front: pilasters, and arched niches set one block back
            double at = this.archTop(this.pH - 1, u, 2.2);
            for (int y = 1; y <= this.pH; y++) {
                if (pil) {
                    this.set(x, y, z, y == 1 ? p.base : (y == this.pH ? p.capital : p.pillar));
                } else if (y > at) {
                    this.set(x, y, z, y == this.pH ? p.band : this.tex(311L, x, y, z, p.wall, p.wallAlt, p.wallAlt2));
                }
            }

            if (!pil && mid < 0.2) {
                if (Math.floorMod(bi, 2) == 0) {
                    this.set(x, this.pH - 1, z, this.wallBanner(p.banners[Math.floorMod(bi / 2, p.banners.length)], in));
                } else {
                    this.set(x, (int)Math.floor(at), z, p.light + "[hanging=true]");
                }
            }
        } else if (d < 2.0) {
            for (int y = 1; y <= this.pH; y++) {
                this.set(x, y, z, y == 1 ? p.base : (y == this.pH ? p.band : (pil ? p.pillar : this.tex(313L, x, y, z, p.panel, p.panelAlt, p.panel))));
            }
        } else if (d < 3.0) {
            // the wall's core, the terrace's edge and its railing with posts and urns
            this.fillTo(x, z, this.pH, p.wall, p.wallAlt, p.wallAlt2);
            this.set(x, this.yTer, z, p.trim);
            if (pil) {
                this.set(x, this.yTer + 1, z, p.pillar);
                this.set(x, this.yTer + 2, z, p.capital);
                this.set(x, this.yTer + 3, z, Math.floorMod(bi, 3) == 0 ? "decorated_pot[facing=" + in + "]" : p.light + "[hanging=false]");
            } else {
                this.set(x, this.yTer + 1, z, p.fence);
            }
        } else if (d < 5.0) {
            // the terrace: a paved walk and cypresses
            this.fillTo(x, z, this.yTer - 1, p.stand, p.stand, p.wallAlt);
            this.set(x, this.yTer, z, this.tex(317L, x, 0, z, p.walkway, p.trim, p.walkway));
            if (d < 4.0 && !pil && mid < 0.2 && Math.floorMod(bi, 2) == 1) {
                this.cypress(x, this.yTer + 1, z);
            }
        } else if (d < 5.0 + this.n1) {
            this.stands(x, z, (int)Math.floor(d - 5.0), this.yTer + 1, u, bi, out);
        } else if (d < 8.0 + this.n1) {
            // the arcade between the tiers: arches, a banner hanging in every one
            int k = (int)Math.floor(d - 5.0 - this.n1);
            this.fillTo(x, z, this.yArcBase, p.stand, p.stand, p.wallAlt);
            double at = this.archTop(this.yArcTop - 2, u, 2.0);
            for (int y = this.yArcBase + 1; y <= this.yArcTop; y++) {
                String b;
                if (k == 0) {
                    if (pil) {
                        b = y == this.yArcBase + 1 ? p.base : (y == this.yArcTop - 1 ? p.capital : (y == this.yArcTop ? p.trim : p.column));
                    } else if (y == this.yArcBase + 1) {
                        b = p.walkway;
                    } else if (y <= at) {
                        b = null;
                    } else {
                        b = y == this.yArcTop ? p.trim : (mid < 0.2 && y == (int)Math.floor(at) + 1 ? p.emGold : this.tex(321L, x, y, z, p.arch, p.wallAlt, p.arch));
                    }
                } else if (k == 1) {
                    b = y == this.yArcTop ? p.trim : this.tex(323L, x, y, z, p.panel, p.panelAlt, p.panel);
                } else {
                    b = this.tex(325L, x, y, z, p.wall, p.wallAlt, p.wallAlt2);
                }

                this.set(x, y, z, b);
            }

            if (k == 0) {
                if (!pil && mid < 0.25) {
                    this.set(x, this.yArcTop - 3, z, this.wallBanner(p.banners[Math.floorMod(bi, p.banners.length)], in));
                }

                this.set(x, this.yArcTop + 1, z, pil ? p.light + "[hanging=false]" : p.fence);
            }
        } else if (d < c0) {
            this.stands(x, z, (int)Math.floor(d - 8.0 - this.n1), this.yArcTop + 1, u, bi, out);
        } else {
            this.colonnade(x, z, (int)Math.floor(d - c0), u, bi, out, mid, pil);
        }

        // the tiled awning over the upper stands and the colonnade, on timber rafters, masts on the outer edge
        double rs = d - (c0 - 4.0);
        if (rs >= 0.0 && rs < 7.0) {
            int ry = this.yColTop + 1 + (int)Math.floor(rs * 0.5);
            if (d >= c0) {
                for (int y = this.yColTop + 1; y < ry; y++) {
                    this.set(x, y, z, this.tex(329L, x, y, z, p.roofBlock, p.roofAlt, p.roofBlock));
                }
            } else if (pil) {
                this.set(x, ry - 1, z, p.log + "[axis=" + acrossAxis(out) + "]");
            }

            this.set(x, ry, z, stairs(p.roofStair, out, false));
            if (rs >= 6.0) {
                if (pil && Math.floorMod(bi, 3) == 0) {
                    // a flagpole with a flag flying along the wall
                    for (int y = ry + 1; y <= ry + 11; y++) {
                        this.set(x, y, z, p.fence);
                    }

                    this.set(x, ry + 12, z, p.emGold);
                    String flag = p.banners[Math.floorMod(bi / 3, p.banners.length)] + "_wool";
                    String along = alongAxis(out);
                    int sgn = Math.floorMod(bi / 3, 2) == 0 ? 1 : -1;
                    for (int k = 1; k <= 3; k++) {
                        for (int y = ry + 9; y <= ry + 11 - (k == 3 ? 1 : 0); y++) {
                            this.set(along.equals("x") ? x + sgn * k : x, y, along.equals("z") ? z + sgn * k : z, flag);
                        }
                    }
                } else {
                    this.set(x, ry + 1, z, pil ? p.capital : p.trim);
                }
            }
        }
    }

    private static int rotationOf(String facing) {
        return switch (facing) {
            case "west" -> 4;
            case "north" -> 8;
            case "east" -> 12;
            default -> 0;
        };
    }

    /** A tier of seats: coloured sectors, aisles every third bay, walkways every fourth row, lanterns where they meet. */
    private void stands(int x, int z, int row, int yBase, double u, int bi, String out) {
        Pal p = this.pal;
        int top = yBase + row;
        this.fillTo(x, z, top - 1, p.stand, p.stand, p.wallAlt);
        boolean aisle = Math.floorMod(bi, 3) == 0 && u < 2.0;
        boolean walk = row % 4 == 3;
        if (aisle) {
            this.set(x, top, z, stairs(p.aisle, out, false));
        } else if (walk) {
            this.set(x, top, z, p.walkway);
            if (Math.floorMod(bi, 3) == 0 && u < 3.0) {
                this.set(x, top + 1, z, p.light + "[hanging=false]");
            }
        } else {
            this.set(x, top, z, stairs(Math.floorMod(bi / 3, 2) == 0 ? p.seat : p.seat2, out, false));
        }
    }

    /** Is this point of the outer wall an open arch? Three storeys below the colonnade, two in it. */
    private boolean facadeOpen(double mid, boolean pil, int y) {
        if (pil) {
            return false;
        }

        if (y <= this.yColBase) {
            int k = (y - 1) / this.storey;
            int y0 = 1 + k * this.storey;
            int top = y0 + this.storey - 1;
            return top <= this.yColBase && y >= y0 + 1 && y <= top - 2 - mid * mid * 2.0;
        }

        int midY = this.yColBase + 7;
        return y > this.yColBase + 1 && y < midY && y <= this.yColBase + 5 - mid * mid * 2.0 || y > midY && y <= this.yColTop - 2 - mid * mid * 1.6;
    }

    /** The two-storey colonnade on top: columns and arches, a gallery behind, the outer wall of open arches. */
    private void colonnade(int x, int z, int k, double u, int bi, String out, double mid, boolean pil) {
        Pal p = this.pal;
        int midY = this.yColBase + 7;
        if (k == 0) {
            this.fillTo(x, z, this.yColBase, p.stand, p.stand, p.wallAlt);
            for (int y = this.yColBase + 1; y <= this.yColTop; y++) {
                String b;
                if (pil) {
                    b = y == this.yColBase + 1 || y == midY || y == this.yColTop ? p.capital : p.column;
                } else if (y == midY || y == this.yColTop) {
                    b = p.trim;
                } else if (y < midY && y <= this.yColBase + 5 - mid * mid * 2.0 || y > midY && y <= this.yColTop - 2 - mid * mid * 1.6) {
                    b = null;
                } else {
                    b = this.tex(331L, x, y, z, p.arch, p.wallAlt, p.arch);
                }

                this.set(x, y, z, b);
            }

            this.set(x, this.yColBase + 1, z, pil ? p.capital : p.fence);
            if (!pil && mid < 0.2) {
                this.set(x, (int)Math.floor(this.yColTop - 2 - 0.0), z, p.light + "[hanging=true]");
            }
        } else if (k == 1) {
            // the gallery: open above the stands; below, the backs of the outer wall's arches
            for (int y = 1; y <= this.yColBase; y++) {
                this.set(x, y, z, this.facadeOpen(mid, pil, y) ? p.panelAlt : this.tex(333L, x, y, z, p.stand, p.stand, p.wallAlt));
            }

            this.set(x, this.yColBase, z, p.walkway);
            this.set(x, midY, z, p.planks);
            this.set(x, this.yColTop, z, p.trim);
            if (!pil && mid < 0.2) {
                this.set(x, midY - 1, z, p.light + "[hanging=true]");
            }
        } else {
            // the outer wall: storeys of open arches between engaged columns, a cornice on each
            for (int y = 1; y <= this.yColTop; y++) {
                String b;
                if (this.facadeOpen(mid, pil, y)) {
                    b = null;
                } else if (y <= this.yColBase) {
                    int ky = (y - 1) % this.storey;
                    if (ky == this.storey - 1 || y == this.yColBase) {
                        b = p.trim;
                    } else if (pil) {
                        b = ky == 0 ? p.base : (ky == this.storey - 2 ? p.capital : p.column);
                    } else {
                        b = this.tex(335L, x, y, z, p.arch, p.wallAlt, p.arch);
                    }
                } else if (y == midY || y == this.yColTop) {
                    b = p.trim;
                } else {
                    b = pil ? p.column : this.tex(337L, x, y, z, p.arch, p.wallAlt, p.arch);
                }

                this.set(x, y, z, b);
            }

            if (!pil && mid < 0.2 && Math.floorMod(bi, 2) == 0) {
                this.set(x, (int)Math.floor(this.storey - 2.0), z, p.light + "[hanging=true]");
            }
        }
    }

    /** A cypress: tall and narrow, in a stone planter. */
    private void cypress(int x, int y, int z) {
        this.set(x, y, z, this.pal.capital);
        this.set(x, y + 1, z, "moss_block");
        for (int k = 2; k <= 7; k++) {
            this.set(x, y + k, z, leaves(this.pal.leaves));
            if (k >= 3 && k <= 5) {
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    this.setIfEmpty(x + o[0], y + k, z + o[1], leaves(this.pal.leaves));
                }
            }
        }

        this.set(x, y + 8, z, leaves(this.pal.leaves));
    }

    /**
     * The two monumental gates at the ends: a great arch with a portcullis between engaged columns and recessed panels
     * with banners, an entablature, an attic with windows, and a temple front with a pediment and acroteria on top.
     * Nothing stands out over the floor.
     */
    private void stadiumGates() {
        Pal p = this.pal;
        int end = (int)Math.round(this.straight + this.radius);
        int gw = this.gateW;
        int h1 = this.yArcTop + 2;
        int h2 = this.yColTop;
        int ph = gw * 6 / 11;
        for (int side = -1; side <= 1; side += 2) {
            String in = side > 0 ? "west" : "east";
            for (int dd = 0; dd < this.dOut; dd++) {
                int x = side * (end + dd);
                for (int z = -gw; z <= gw; z++) {
                    int az = Math.abs(z);
                    for (int y = 1; y <= this.yColTop + ph + 16; y++) {
                        this.set(x, y, z, null);
                    }

                    for (int y = 1; y <= h2; y++) {
                        this.set(x, y, z, this.gateCell(x, z, dd, az, y, h1, h2));
                    }

                    // the pediment in front, the gable roof behind it
                    int ridge = (gw - az) * ph / gw;
                    for (int kk = 0; kk <= ridge; kk++) {
                        int y = h2 + 1 + kk;
                        String b;
                        if (kk == ridge) {
                            b = az == 0 ? p.roofBlock : stairs(p.roofStair, z > 0 ? "north" : "south", false);
                        } else if (dd == 0) {
                            b = kk == 0 ? p.trim : p.panel;
                        } else {
                            b = this.tex(343L, x, y, z, p.roofBlock, p.roofAlt, p.roofBlock);
                        }

                        this.set(x, y, z, b);
                    }
                }
            }

            int fx = side * end;
            // a gold emblem in the pediment, acroteria on its corners and apex
            this.set(fx, h2 + 2, 0, p.emGold);
            this.set(fx, h2 + 3, 0, p.emGold);
            this.set(fx, h2 + 2, -1, p.emGold);
            this.set(fx, h2 + 2, 1, p.emGold);
            this.set(fx, h2 + ph + 2, 0, p.emGold);
            this.set(fx, h2 + ph + 3, 0, "lightning_rod[facing=up]");
            for (int k = -1; k <= 1; k += 2) {
                this.set(fx, h2 + 1, k * gw, "decorated_pot[facing=" + in + "]");
                // flagpoles on the roof
                int px = side * (end + this.dOut / 2);
                for (int y = h2 + 1; y <= h2 + ph + 12; y++) {
                    this.set(px, y, k * (gw - 2), p.log + "[axis=y]");
                }

                this.set(px, h2 + ph + 13, k * (gw - 2), p.banners[0] + "_banner[rotation=" + rotationOf(in) + "]");
                // banners in the recessed panels and lanterns under them
                int pz = k * ((this.gateW >= 14 ? 6 : 4) + 3 + this.gateW - 1) / 2;
                for (int j = -1; j <= 1; j++) {
                    this.set(fx, h1 - 3, pz + j, this.wallBanner(p.banners[Math.floorMod(j, p.banners.length)], in));
                    this.set(fx, h1 - 5, pz + j, this.wallBanner(p.banners[Math.floorMod(j, p.banners.length)], in));
                }

                this.set(fx, 3, pz, p.light + "[hanging=false]");
            }
        }
    }

    /** The top of a gate's round arch at az from its middle. */
    private static int gateArch(int az, int archW, int archTop) {
        double t = (double)az / archW;
        return archTop - (int)Math.round(3.0 * t * t);
    }

    /** One block of a colosseum gate: dd blocks behind its face, az from its middle. */
    private String gateCell(int x, int z, int dd, int az, int y, int h1, int h2) {
        Pal p = this.pal;
        int gw = this.gateW;
        int archW = gw >= 14 ? 6 : 4;
        int archTop = gw >= 14 ? 15 : 11;
        boolean passage = az <= archW && y <= gateArch(az, archW, archTop);
        if (passage && dd < 7) {
            if (dd == 1) {
                return "iron_bars";
            }

            if (dd == 6) {
                return Math.floorMod(az, 2) == 0 ? "dark_oak_planks" : "spruce_planks";
            }

            return null;
        }

        String stone = this.tex(341L, x, y, z, p.arch, p.wallAlt, p.arch);
        if (dd == 0) {
            if (az == archW + 1 && y <= gateArch(archW, archW, archTop) + 1 || az <= archW && y == gateArch(az, archW, archTop) + 1) {
                return az == 0 ? p.emGold : p.panelAlt;
            }

            // a tablet over the arch: a framed recess with a gold crest in it
            if (gw >= 14 && y >= archTop + 3 && y <= h1 - 2) {
                if (az <= 3 && y >= archTop + 4 && y <= h1 - 3) {
                    return null;
                }

                if (az <= 4) {
                    return p.trim;
                }
            }

            int c1 = archW + 2;
            boolean col = az == c1 || az == gw - 1 || gw >= 14 && az == c1 + 1;
            if (y <= h1) {
                if (col) {
                    return y == 1 ? p.base : (y >= h1 - 1 ? p.capital : p.column);
                }

                if (az > c1 + 1 && az < gw - 1 && y >= 3 && y <= h1 - 2) {
                    return null;
                }

                return stone;
            }

            if (y == h1 + 1 || y == h1 + 3 || y == h2) {
                return p.trim;
            }

            if (y == h1 + 2) {
                return p.panelAlt;
            }

            boolean win = (az <= 1 || Math.abs(az - (gw - 3)) <= 1) && y >= h1 + 6 && y <= h2 - 3;
            return win ? null : stone;
        }

        if (dd == 1) {
            if (az > archW + 3 && az < gw - 1 && y >= 3 && y <= h1 - 2) {
                return p.panel;
            }

            if (gw >= 14 && az <= 3 && y >= archTop + 4 && y <= h1 - 3) {
                int cy = (archTop + 4 + h1 - 3) / 2;
                return az + Math.abs(y - cy) <= 1 ? p.emGold : p.panel;
            }

            if ((az <= 1 || Math.abs(az - (gw - 3)) <= 1) && y >= h1 + 6 && y <= h2 - 3) {
                return p.panelAlt;
            }
        }

        if (az == gw && (y == h1 + 1 || y == h2 || y % 8 == 0)) {
            return p.trim;
        }

        return stone;
    }

    // ---------------------------------------------------------------- courts

    /** A tower's top by theme: a cone with a flag, an onion dome, a stepped pyramid or a tall spire. */
    private void towerTop(int tx, int y, int tz, double r) {
        Pal p = this.pal;
        switch (p.towerTop) {
            case "ONION" -> this.onion(tx, y, tz, r - 0.6, of(p.dome), p.emGold);
            case "PYRAMID" -> {
                int w = (int)Math.round(r) - 1;
                for (int k = 0; k <= w; k++) {
                    this.fill(tx - (w - k), y + k, tz - (w - k), tx + (w - k), y + k, tz + (w - k), of(p.roofBlock));
                }

                this.set(tx, y + w + 1, tz, "gold_block");
            }
            case "SPIRE" -> {
                this.coneRoof(tx, tz, r + 0.6, y, 18, (x, yy, z) -> this.tex(373L, x, yy, z, p.roofBlock, p.roofAlt, p.roofBlock));
                this.set(tx, y + 19, tz, "lightning_rod[facing=up]");
            }
            default -> {
                this.coneRoof(tx, tz, r + 1.2, y, 11, (x, yy, z) -> this.tex(375L, x, yy, z, p.roofBlock, p.roofAlt, p.roofBlock));
                for (int k = y + 11; k <= y + 14; k++) {
                    this.set(tx, k, tz, p.fence);
                }

                this.set(tx + 1, y + 14, tz, p.banners[0] + "_wool");
                this.set(tx + 2, y + 14, tz, p.banners[0] + "_wool");
                this.set(tx + 1, y + 13, tz, p.banners[0] + "_wool");
            }
        }
    }

    /** The stairs block that goes with a wall block. */
    private static String stairOf(String wall) {
        return switch (wall) {
            case "stone_bricks", "cracked_stone_bricks" -> "stone_brick";
            case "mossy_stone_bricks" -> "mossy_stone_brick";
            case "deepslate_bricks", "cracked_deepslate_bricks" -> "deepslate_brick";
            case "polished_blackstone_bricks", "cracked_polished_blackstone_bricks" -> "polished_blackstone_brick";
            case "quartz_bricks", "calcite", "smooth_quartz" -> "smooth_quartz";
            case "cut_sandstone", "sandstone", "smooth_sandstone" -> "smooth_sandstone";
            case "red_sandstone", "cut_red_sandstone", "smooth_red_sandstone" -> "smooth_red_sandstone";
            case "cobblestone", "mossy_cobblestone" -> "cobblestone";
            default -> "stone_brick";
        };
    }

    // ================================================================== courts: a composition, not a ring (3.15.0)

    private Mat wallM() {
        Pal p = this.pal;
        return (x, y, z) -> this.tex(401L, x, y, z, p.wall, p.wallAlt, p.wallAlt2);
    }

    private Mat roofM() {
        Pal p = this.pal;
        return (x, y, z) -> this.tex(403L, x, y, z, p.roofBlock, p.roofAlt, p.roofBlock);
    }

    private static String woodStair(String planks) {
        return planks.replace("_planks", "");
    }

    /** The ground of the gardens round the floor. */
    private String plazaBlock(int x, int z, double d) {
        Pal p = this.pal;
        if (d < 3.0) {
            return p.paving;
        }

        double n = noise(this.seed + 23L, x * 1.4, z * 1.4);
        double h = hash3(this.seed + 29L, x, 0, z);
        String[] m = p.plaza;
        if (m.length > 1 && n > 0.68) {
            return m[1];
        }

        if (m.length > 2 && h < 0.06) {
            return m[2];
        }

        return m[0];
    }

    /**
     * The court: a giant main building on the north long side, something low facing it on the south side, a gateway at
     * each end, and gardens everywhere else - the corners are open, with trees, fountains and statues.
     */
    private void court() {
        Pal p = this.pal;
        int zf = -(this.hz + 5);
        int w = Math.max(16, this.hx - this.rc - 4);
        switch (p.court) {
            case "PALACE" -> this.palace(w, zf);
            case "CATHEDRAL" -> this.cathedral(w, zf);
            case "PAGODA" -> this.pagodaTemple(w, zf);
            case "ZIGGURAT" -> this.ziggurat(w, zf);
            case "PYRAMID" -> this.pharaohPyramid(w, zf);
            default -> this.castle(w, zf);
        }

        int zs = this.hz + 4;
        switch (p.south) {
            case "LOGGIA" -> this.loggia(zs);
            case "PAVILION" -> this.pavilions(zs);
            default -> this.grandstand(zs);
        }

        for (int side = -1; side <= 1; side += 2) {
            this.gateway(side);
        }

        this.edge();
        this.gardens();
    }

    // ---------------------------------------------------------------- main buildings

    /** A round tower: a plinth, bands with windows, a corbelled crown with merlons and the theme's top. */
    private void bigTower(int cx, int cz, double r, int base, int h) {
        Pal p = this.pal;
        Mat wm = this.wallM();
        this.cyl(cx, cz, r + 1.0, base + 1, base + 3, of(p.base));
        this.cyl(cx, cz, r, base + 1, h, wm);
        int n = 0;
        for (int y = base + 9; y < h - 3; y += 8) {
            this.ringAt(cx, cz, r - 0.3, r + 0.6, y, of(p.trim));
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2.0 + Math.PI / 4.0 + n * 0.5;
                int wx = cx + (int)Math.round(Math.cos(a) * r);
                int wz = cz + (int)Math.round(Math.sin(a) * r);
                this.set(wx, y + 2, wz, p.glass);
                this.set(wx, y + 3, wz, p.glass);
            }

            n++;
        }

        this.ringAt(cx, cz, r - 0.4, r + 1.3, h, of(p.capital));
        int m = (int)Math.max(8L, Math.round((r + 1.0) * Math.PI));
        for (int k = 0; k < 2 * m; k += 2) {
            double a = k * Math.PI / m;
            this.set(cx + (int)Math.round(Math.cos(a) * (r + 0.9)), h + 1, cz + (int)Math.round(Math.sin(a) * (r + 0.9)), p.wall);
        }

        this.towerTop(cx, h + 1, cz, r + 0.4);
    }

    private void slit(Face f, int u, int y) {
        f.set(u, y, 0, null);
        f.set(u, y + 1, 0, null);
        f.set(u, y, 1, "polished_blackstone");
        f.set(u, y + 1, 1, "polished_blackstone");
    }

    private String wool(int i) {
        return this.pal.banners[Math.floorMod(i, this.pal.banners.length)] + "_wool";
    }

    /**
     * A castle: a battlemented curtain wall with banners and a gatehouse between two great round towers, palace wings
     * behind it and a massive keep with corner turrets and a tall roof (or a lighthouse), on a rock when the theme asks.
     */
    private void castle(int w, int zf) {
        Pal p = this.pal;
        Mat wm = this.wallM();
        String st = stairOf(p.wall);
        int lift = p.rock ? 7 : 0;
        Face f = new Face(0, zf, 0, -1);
        if (lift > 0) {
            // the rock it stands on, its edges broken
            for (int u = -w - 9; u <= w + 9; u++) {
                for (int ww = -3; ww <= 34; ww++) {
                    double out = Math.max(Math.max(-ww - 0.5, Math.abs(u) - w - 2.0), ww - 31.0);
                    int h = (int)Math.round(lift - Math.max(0.0, out) * 1.5 + noise(this.seed + 31L, u * 3.0, ww * 3.0) * 3.0 - 1.0);
                    for (int y = 1; y <= Math.min(lift, h); y++) {
                        f.set(u, y, ww, this.tex(33L, f.x(u, ww), y, f.z(u, ww), "stone", "andesite", "mossy_cobblestone"));
                    }

                    if (h >= 1 && h < lift && out > 0.0) {
                        f.set(u, Math.min(lift, h) + 1, ww, hash3(this.seed + 35L, u, h, ww) < 0.5 ? "moss_carpet" : null);
                    }
                }
            }
        }

        int hw = lift + 15;
        f.box(-w, lift + 1, 0, w, hw, 3, wm);
        for (int u = -w; u <= w; u++) {
            f.set(u, lift + 1, -1, stairs(st, f.back(), false));
        }

        this.cornice(f, -w, w, hw - 1, st, 1);
        this.merlons(f, -w, w, hw + 1, wm, 0);
        for (int u = -w + 4; u <= w - 4; u += 6) {
            if (Math.abs(u) > 10) {
                this.slit(f, u, lift + 9);
            }
        }

        int bi = 0;
        for (int u = -w + 7; u <= w - 7; u += 9) {
            if (Math.abs(u) > 11) {
                this.cloth(f, u, hw - 2, 3, 8, this.wool(bi++), p.clothTrim + "_wool", p.emGold);
            }
        }

        // the gatehouse: a deep arch with a portcullis and doors, machicolations, turrets
        int hg = hw + 9;
        Face g = f.moved(0, -2);
        g.box(-8, lift + 1, 0, 8, hg, 8, wm);
        this.arch(g, 0, lift + 1, 7, 11, false, 6, "dark_oak_planks", p.capital);
        for (int u = -3; u <= 3; u++) {
            for (int y = lift + 1; y <= lift + 11; y++) {
                if (inArch(u, y, 0, 3.0, lift + 1, lift + 11, false)) {
                    g.set(u, y, 1, y > lift + 6 ? "iron_bars" : null);
                }
            }
        }

        this.cornice(g, -8, 8, hg - 3, st, 1);
        this.merlons(g, -8, 8, hg + 1, wm, 1);
        this.window(g, -4, hg - 9, 2, 3, false, p.glass, "glowstone", p.trim, st, 1);
        this.window(g, 4, hg - 9, 2, 3, false, p.glass, "glowstone", p.trim, st, 1);
        this.cloth(g, 0, hg - 5, 5, 4, this.wool(0), p.clothTrim + "_wool", p.emGold);
        g.set(0, lift + 12, -1, p.emGold);
        for (int s = -1; s <= 1; s += 2) {
            int tx = g.x(s * 8, 1);
            int tz = g.z(s * 8, 1);
            this.cyl(tx, tz, 2.2, hg - 6, hg + 5, wm);
            this.ringAt(tx, tz, 1.8, 3.0, hg + 5, of(p.capital));
            this.coneRoof(tx, tz, 3.2, hg + 6, 7, this.roofM());
        }

        // the two great towers on the ends of the wall
        for (int s = -1; s <= 1; s += 2) {
            this.bigTower(f.x(s * w, 1), f.z(s * w, 1), 6.5, lift, hw + 16);
        }

        // palace wings behind the wall
        for (int s = -1; s <= 1; s += 2) {
            int u0 = s * 10;
            int u1 = s * (w - 8);
            int top = hw + 6;
            f.box(u0, lift + 1, 6, u1, top, 15, wm);
            Face wf = f.moved(0, 6);
            for (int u = Math.min(u0, u1) + 2; u <= Math.max(u0, u1) - 2; u += 4) {
                this.window(wf, u, hw + 1, 2, 3, true, p.glass, "glowstone", p.trim, st, 1);
            }

            int x0 = f.x(Math.min(u0, u1) - 1, 0);
            int x1 = f.x(Math.max(u0, u1) + 1, 0);
            this.gableRoof(Math.min(x0, x1), f.z(0, 16), Math.max(x0, x1), f.z(0, 5), top + 1, true, p.roofStair, this.roofM(), wm);
        }

        if (p.lighthouse) {
            // a striped lighthouse in place of the keep
            int lx = f.x(0, 20);
            int lz = f.z(0, 20);
            this.cyl(lx, lz, 9.0, lift + 1, hw + 4, wm);
            int top = lift + 58;
            for (int y = hw + 5; y <= top; y++) {
                double r = 6.0 - (y - hw) * 0.04;
                this.cyl(lx, lz, r, y, y, of((y - hw) / 5 % 2 == 0 ? p.clothBg + "_concrete" : "white_concrete"));
            }

            this.ringAt(lx, lz, 0, 6.5, top + 1, of(p.capital));
            this.ringAt(lx, lz, 5.6, 6.5, top + 2, of("iron_bars"));
            this.cyl(lx, lz, 3.4, top + 2, top + 5, of("glass"));
            this.cyl(lx, lz, 2.0, top + 2, top + 5, of("sea_lantern"));
            this.coneRoof(lx, lz, 4.5, top + 6, 5, this.roofM());
            this.set(lx, top + 12, lz, "lightning_rod[facing=up]");
            return;
        }

        // the keep: massive, with rows of lit windows, long banners, corner turrets and a tall roof
        int hk = lift + 46;
        f.box(-10, lift + 1, 14, 10, hk, 30, wm);
        Face k = f.moved(0, 14);
        for (int y = lift + 20; y < hk - 6; y += 8) {
            for (int u = -6; u <= 6; u += 6) {
                this.window(k, u, y, 3, 5, true, p.glass, "glowstone", p.trim, st, 1);
            }
        }

        this.cloth(k, -3, hk - 6, 3, 14, this.wool(0), p.clothTrim + "_wool", p.emGold);
        this.cloth(k, 3, hk - 6, 3, 14, this.wool(1), p.clothTrim + "_wool", p.emGold);
        this.cornice(k, -10, 10, hk - 1, st, 1);
        for (int su = -1; su <= 1; su += 2) {
            for (int sw = 0; sw <= 1; sw++) {
                int tx = f.x(su * 10, 14 + sw * 16);
                int tz = f.z(su * 10, 14 + sw * 16);
                this.cyl(tx, tz, 3.4, hk - 14, hk + 8, wm);
                this.ringAt(tx, tz, 3.0, 4.4, hk + 8, of(p.capital));
                this.coneRoof(tx, tz, 4.6, hk + 9, 10, this.roofM());
                this.set(tx, hk + 20, tz, "lightning_rod[facing=up]");
            }
        }

        int kx0 = Math.min(f.x(-9, 0), f.x(9, 0));
        int kx1 = Math.max(f.x(-9, 0), f.x(9, 0));
        int top = this.pyramidRoof(kx0, f.z(0, 29), kx1, f.z(0, 15), hk + 1, p.roofStair, this.roofM());
        for (int y = top + 1; y <= top + 8; y++) {
            this.set(0, y, f.z(0, 22), p.fence);
        }

        for (int y = top + 5; y <= top + 7; y++) {
            for (int kk = 1; kk <= 4; kk++) {
                this.set(kk, y, f.z(0, 22), this.wool(y));
            }
        }
    }

    /**
     * A palace: a two-storey arcade front with cresting, a towering portal with a deep pointed arch, a great onion dome
     * on a windowed drum, smaller domes on the wings and minarets with balconies.
     */
    private void palace(int w, int zf) {
        Pal p = this.pal;
        Mat wm = this.wallM();
        String st = stairOf(p.wall);
        Face f = new Face(0, zf, 0, -1);
        int h1 = 9;
        int h2 = 17;
        f.box(-w, 1, 0, w, h2, 8, wm);
        for (int u = -w + 4; u <= w - 4; u += 5) {
            if (Math.abs(u) <= 12) {
                continue;
            }

            this.arch(f, u, 1, 3, 7, true, 3, p.window, p.trim);
            this.window(f, u, h1 + 2, 3, 4, true, p.glass, "glowstone", p.trim, null, 1);
        }

        for (int u = -w; u <= w; u++) {
            f.set(u, h1, -1, p.trim);
            f.set(u, h2 + 1, 0, Math.floorMod(u, 3) == 0 ? p.capital : (Math.floorMod(u, 3) == 1 ? stairs(st, "east", false) : stairs(st, "west", false)));
        }

        // the portal
        Face g = f.moved(0, -2);
        g.box(-11, 1, 0, 11, 30, 10, wm);
        this.arch(g, 0, 1, 15, 24, true, 6, p.window, p.trim);
        for (int u = -2; u <= 2; u++) {
            for (int y = 1; y <= 6; y++) {
                g.set(u, y, 6, "dark_oak_planks");
            }
        }

        for (int u = -11; u <= 11; u++) {
            g.set(u, 29, -1, p.trim);
            g.set(u, 31, 0, Math.floorMod(u, 2) == 0 ? p.capital : null);
        }

        for (int y = 2; y <= 28; y++) {
            g.set(-12, y, 0, p.trim);
            g.set(12, y, 0, p.trim);
        }

        for (int s = -1; s <= 1; s += 2) {
            this.minaret(g.x(s * 12, 1), g.z(s * 12, 1), 1.8, 34);
            this.minaret(f.x(s * (w + 1), 2), f.z(s * (w + 1), 2), 2.6, 40);
            this.minaret(f.x(s * (w - 3), 32), f.z(s * (w - 3), 32), 2.2, 48);
        }

        // the great dome
        int dz = f.z(0, 22);
        this.cyl(0, dz, 10.0, 1, 31, wm);
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8.0;
            for (int y = 23; y <= 27; y++) {
                this.set((int)Math.round(Math.cos(a) * 10.0), y, dz + (int)Math.round(Math.sin(a) * 10.0), y == 23 || y == 27 ? p.trim : p.glass);
            }
        }

        this.ringAt(0, dz, 9.5, 11.4, 31, of(p.trim));
        this.onion(0, 32, dz, 10.0, of(p.dome), p.emGold);
        for (int s = -1; s <= 1; s += 2) {
            int sx = s * (w / 2 + 3);
            int sz = f.z(0, 12);
            this.cyl(sx, sz, 5.0, 1, 21, wm);
            this.ringAt(sx, sz, 4.6, 6.2, 21, of(p.trim));
            this.onion(sx, 22, sz, 5.4, of(p.dome), p.emGold);
        }
    }

    private void minaret(int x, int z, double r, int h) {
        Pal p = this.pal;
        this.cyl(x, z, r + 0.9, 1, 3, of(p.base));
        this.cyl(x, z, r, 1, h, this.wallM());
        for (int y = 8; y < h - 4; y += 7) {
            this.ringAt(x, z, r - 0.3, r + 0.4, y, of(p.window));
        }

        for (int y : new int[]{(int)(h * 0.62), h - 5}) {
            this.ringAt(x, z, r - 0.2, r + 1.5, y, of(p.trim));
            this.ringAt(x, z, r + 1.0, r + 1.5, y + 1, of(p.fence));
        }

        this.onion(x, h + 1, z, r + 0.5, of(p.dome), p.emGold);
    }

    /**
     * A cathedral: a west front of two tall towers with lancet windows, belfries and spires with pinnacles, a portal and a
     * rose window under a steep gable, a long nave with flying buttresses, a spire over the crossing and side chapels.
     */
    private void cathedral(int w, int zf) {
        Pal p = this.pal;
        Mat wm = this.wallM();
        String st = stairOf(p.wall);
        Face f = new Face(0, zf, 0, -1);
        int hn = 30;
        f.box(-6, 1, 2, 6, hn, 34, wm);
        // a steep roof over the nave, its gable over the front
        for (int k = 0; k <= 7; k++) {
            for (int dy = 0; dy < 2; dy++) {
                int y = hn + 1 + 2 * k + dy;
                for (int ww = 2; ww <= 34; ww++) {
                    for (int u = -7 + k; u <= 7 - k; u++) {
                        boolean edge = Math.abs(u) == 7 - k;
                        String b = ww == 2 ? (edge ? p.trim : this.tex(405L, u, y, ww, p.wall, p.wallAlt, p.wallAlt2))
                            : (edge && dy == 1 ? stairs(p.roofStair, u < 0 ? "east" : "west", false) : this.tex(407L, u, y, ww, p.roofBlock, p.roofAlt, p.roofBlock));
                        f.set(u, y, ww, b);
                    }
                }
            }
        }

        // the rose window with its tracery, the portal
        int ry = 21;
        for (int u = -6; u <= 6; u++) {
            for (int y = ry - 6; y <= ry + 6; y++) {
                double d = Math.hypot(u, y - ry);
                if (d <= 4.3) {
                    boolean tracery = u == 0 || y == ry || Math.abs(u) == Math.abs(y - ry) || d < 1.2;
                    f.set(u, y, 2, tracery ? p.trim : p.window);
                    f.set(u, y, 3, "glowstone");
                } else if (d <= 5.3) {
                    f.set(u, y, 1, p.trim);
                }
            }
        }

        this.arch(f.moved(0, 2), 0, 1, 7, 13, true, 2, "dark_oak_planks", p.trim);
        // the towers of the front
        for (int s = -1; s <= 1; s += 2) {
            f.box(s * 7, 1, 0, s * 17, 42, 10, wm);
            for (int y : new int[]{8, 21}) {
                this.window(f, s * 12, y, 3, 9, true, p.window, "glowstone", p.trim, st, 1);
            }

            this.arch(f, s * 12, 33, 5, 7, true, 2, null, p.trim);
            this.cornice(f, s * 7, s * 17, 41, st, 1);
            for (int u : new int[]{s * 7, s * 17}) {
                for (int ww : new int[]{0, 10}) {
                    this.spire(f.x(u, ww), 43, f.z(u, ww), 5, p.wall, "lightning_rod[facing=up]");
                }

                for (int y = 1; y <= 38; y++) {
                    f.set(u, y, -1, y % 9 == 0 ? stairs(st, f.back(), false) : p.capital);
                }
            }

            int cx = f.x(s * 12, 5);
            int cz = f.z(s * 12, 5);
            this.ringAt(cx, cz, 0, 5.6, 42, of(p.trim));
            this.coneRoof(cx, cz, 5.2, 43, 26, this.roofM());
            this.set(cx, 70, cz, p.emGold);
            this.set(cx, 71, cz, "lightning_rod[facing=up]");
        }

        // aisles with flying buttresses
        for (int s = -1; s <= 1; s += 2) {
            f.box(s * 7, 1, 11, s * 13, 16, 34, wm);
            for (int ww = 13; ww <= 33; ww += 5) {
                Face side = new Face(f.x(s * 13, ww), f.z(s * 13, ww), -s, 0);
                this.window(side, 0, 5, 2, 7, true, p.window, "glowstone", p.trim, null, 1);
                int px = f.x(s * 17, ww);
                int pz = f.z(s * 17, ww);
                for (int y = 1; y <= 22; y++) {
                    this.set(px, y, pz, y >= 21 ? p.capital : p.wall);
                    this.set(px + s, y, pz, y <= 14 ? p.wall : null);
                }

                this.spire(px, 23, pz, 3, p.wall, "lightning_rod[facing=up]");
                this.line(px - s, 21, pz, f.x(s * 7, ww), 28, pz, p.wall);
            }

            this.pyramidRoof(Math.min(f.x(s * 7, 0), f.x(s * 13, 0)), f.z(0, 34), Math.max(f.x(s * 7, 0), f.x(s * 13, 0)), f.z(0, 11), 17, null, this.roofM());
            // side chapels out to the ends of the front
            int u0 = s * 18;
            int u1 = s * w;
            if (Math.abs(u1) > Math.abs(u0) + 3) {
                f.box(u0, 1, 2, u1, 13, 14, wm);
                for (int u = Math.min(u0, u1) + 2; u <= Math.max(u0, u1) - 2; u += 4) {
                    this.window(f.moved(0, 2), u, 4, 2, 6, true, p.window, "glowstone", p.trim, st, 1);
                }

                int x0 = Math.min(f.x(u0, 0), f.x(u1, 0));
                int x1 = Math.max(f.x(u0, 0), f.x(u1, 0));
                this.gableRoof(x0 - 1, f.z(0, 15), x1 + 1, f.z(0, 1), 14, true, p.roofStair, this.roofM(), wm);
            }
        }

        // the spire over the crossing
        int cz = f.z(0, 22);
        this.cyl(0, cz, 3.0, hn + 10, hn + 18, wm);
        this.coneRoof(0, cz, 3.6, hn + 19, 24, this.roofM());
        this.set(0, hn + 44, cz, p.emGold);
    }

    /** A pagoda temple: a long hall on a stone platform with wide curved eaves, a five-tier pagoda behind, two smaller ones. */
    private void pagodaTemple(int w, int zf) {
        Pal p = this.pal;
        Face f = new Face(0, zf, 0, -1);
        f.box(-w, 1, 0, w, 2, 30, of(p.base));
        for (int u = -w; u <= w; u++) {
            f.set(u, 1, -1, stairs(stairOf(p.base), f.back(), false));
        }

        // the hall
        int u0 = -w + 3;
        int u1 = w - 3;
        for (int u = u0; u <= u1; u++) {
            for (int ww = 2; ww <= 10; ww++) {
                boolean edge = u == u0 || u == u1 || ww == 2 || ww == 10;
                boolean post = Math.floorMod(u - u0, 4) == 0 && (ww == 2 || ww == 10) || (u == u0 || u == u1) && ww % 4 == 2;
                for (int y = 3; y <= 10; y++) {
                    String b;
                    if (!edge) {
                        b = y == 3 ? p.planks : null;
                    } else if (post) {
                        b = p.log + "[axis=y]";
                    } else if (y == 10) {
                        b = p.log + "[axis=" + (ww == 2 || ww == 10 ? "x" : "z") + "]";
                    } else if (ww == 2 && Math.abs(u) <= 3 && y <= 8) {
                        b = null;
                    } else {
                        b = y >= 5 && y <= 8 && ww == 2 ? p.window : p.wall;
                    }

                    f.set(u, y, ww, b);
                }
            }
        }

        int x0 = Math.min(f.x(u0, 0), f.x(u1, 0));
        int x1 = Math.max(f.x(u0, 0), f.x(u1, 0));
        this.eaves(x0, f.z(0, 10), x1, f.z(0, 2), 11, 3);
        for (int u = u0; u <= u1; u += 6) {
            f.set(u, 9, 0, p.light + "[hanging=true]");
        }

        // the pagodas
        this.tieredPagoda(0, f.z(0, 21), 5, 7, 3);
        for (int s = -1; s <= 1; s += 2) {
            this.tieredPagoda(f.x(s * (w - 7), 0), f.z(0, 22), 3, 4, 3);
        }
    }

    /** Wide eaves over a rectangle: tiers of tiles stepping in, their corners turned up. */
    private void eaves(int x0, int z0, int x1, int z1, int y0, int over) {
        Pal p = this.pal;
        int k = 0;
        while (x0 - over + k <= x1 + over - k && z0 - over + k <= z1 + over - k) {
            int ax = x0 - over + k;
            int bx = x1 + over - k;
            int az = z0 - over + k;
            int bz = z1 + over - k;
            int y = y0 + k;
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) {
                    boolean ex = x == ax || x == bx;
                    boolean ez = z == az || z == bz;
                    if (ex && ez) {
                        this.set(x, y + (k == 0 ? 1 : 0), z, k == 0 ? slab(p.roofStair, false) : p.roofBlock);
                    } else if (ex) {
                        this.set(x, y, z, stairs(p.roofStair, x == ax ? "east" : "west", false));
                    } else if (ez) {
                        this.set(x, y, z, stairs(p.roofStair, z == az ? "south" : "north", false));
                    } else {
                        this.set(x, y, z, p.roofBlock);
                    }
                }
            }

            if (k == 0) {
                for (int[] c : new int[][]{{ax + 1, az + 1}, {bx - 1, az + 1}, {ax + 1, bz - 1}, {bx - 1, bz - 1}}) {
                    this.set(c[0], y - 1, c[1], p.light + "[hanging=true]");
                }
            }

            k++;
        }
    }

    /** A pagoda: tiers of walls between dark posts, each under wide eaves, a finial of rings and gold on top. */
    private void tieredPagoda(int cx, int cz, int tiers, int half, int y0) {
        Pal p = this.pal;
        int y = y0;
        for (int t = 0; t < tiers; t++) {
            int w = Math.max(2, half - t);
            int h = t == 0 ? 6 : 5;
            for (int dx = -w; dx <= w; dx++) {
                for (int dz = -w; dz <= w; dz++) {
                    boolean edge = Math.abs(dx) == w || Math.abs(dz) == w;
                    boolean post = Math.abs(dx) == w && Math.abs(dz) == w;
                    for (int k = 0; k < h; k++) {
                        String b;
                        if (!edge) {
                            b = k == 0 ? p.planks : null;
                        } else if (post) {
                            b = p.log + "[axis=y]";
                        } else if (k == h - 1) {
                            b = p.log + "[axis=" + (Math.abs(dz) == w ? "x" : "z") + "]";
                        } else {
                            b = k >= 1 && k <= h - 2 && Math.floorMod(dx + dz, 2) == 0 ? p.window : p.wall;
                        }

                        this.set(cx + dx, y + k, cz + dz, b);
                    }
                }
            }

            this.eaves(cx - w, cz - w, cx + w, cz + w, y + h, 2);
            y += h + 2;
        }

        for (int k = 0; k < 6; k++) {
            this.set(cx, y + k, cz, k % 2 == 0 ? "gold_block" : "iron_chain[axis=y]");
        }

        this.set(cx, y + 6, cz, "lightning_rod[facing=up]");
    }

    /** A jungle ziggurat: steps of mossy stone hung with vines, braziers on the corners, a shrine with a copper roof on top. */
    private void ziggurat(int w, int zf) {
        Pal p = this.pal;
        Mat wm = this.wallM();
        Face f = new Face(0, zf, 0, -1);
        int steps = 7;
        int sh = 5;
        for (int k = 0; k < steps; k++) {
            int u0 = -w + 3 * k;
            int u1 = w - 3 * k;
            int w0 = 2 * k;
            int w1 = 34 - 2 * k;
            f.box(u0, 1 + k * sh, w0, u1, (k + 1) * sh, w1, wm);
            for (int u = u0; u <= u1; u++) {
                f.set(u, (k + 1) * sh, w0, p.trim);
                if (hash3(this.seed + 41L, u, k, 0) < 0.35) {
                    int len = 1 + (int)(hash3(this.seed + 43L, u, k, 1) * 4);
                    for (int y = (k + 1) * sh - 1; y > (k + 1) * sh - 1 - len && y > k * sh; y--) {
                        f.set(u, y, w0 - 1, "vine[north=true]");
                    }
                }
            }

            for (int u : new int[]{u0, u1}) {
                f.set(u, (k + 1) * sh + 1, w0, "campfire[lit=true,signal_fire=false]");
            }
        }

        // a carved band up the middle with gold eyes
        for (int k = 0; k < steps; k++) {
            for (int y = 1 + k * sh; y < (k + 1) * sh; y++) {
                for (int u = -2; u <= 2; u++) {
                    f.set(u, y, 2 * k, Math.abs(u) == 2 ? p.capital : (y == k * sh + 3 && Math.abs(u) == 1 ? p.emGold : p.window));
                }
            }
        }

        int top = steps * sh;
        f.box(-6, top + 1, 15, 6, top + 7, 20, wm);
        this.arch(f.moved(0, 15), 0, top + 1, 3, 5, false, 2, null, p.capital);
        int x0 = Math.min(f.x(-7, 0), f.x(7, 0));
        int x1 = Math.max(f.x(-7, 0), f.x(7, 0));
        this.pyramidRoof(x0, f.z(0, 21), x1, f.z(0, 14), top + 8, p.roofStair, this.roofM());
    }

    /** A pharaoh's steep pyramid behind a pylon gate: tapering towers with bands and banners, a sun disk, a gold capstone. */
    private void pharaohPyramid(int w, int zf) {
        Pal p = this.pal;
        Mat wm = this.wallM();
        Face f = new Face(0, zf, 0, -1);
        int half = 17;
        int pz = f.z(0, 21);
        for (int y = 1; y <= 2 * half + 1; y++) {
            int hh = half - (y - 1) / 2;
            boolean upper = (y - 1) % 2 == 1;
            for (int dx = -hh; dx <= hh; dx++) {
                for (int dz = -hh; dz <= hh; dz++) {
                    boolean ex = Math.abs(dx) == hh;
                    boolean ez = Math.abs(dz) == hh;
                    String b = y >= 2 * half - 2 ? "gold_block" : (ex || ez ? "smooth_sandstone" : "sandstone");
                    if (upper && ex && !ez) {
                        b = stairs("smooth_sandstone", dx > 0 ? "west" : "east", false);
                    } else if (upper && ez && !ex) {
                        b = stairs("smooth_sandstone", dz > 0 ? "north" : "south", false);
                    }

                    this.set(dx, y, pz + dz, b);
                }
            }
        }

        // the pylons
        int hp = 20;
        for (int s = -1; s <= 1; s += 2) {
            for (int y = 1; y <= hp; y++) {
                int inset = y / 6;
                int ua = s * 6;
                int ub = s * (w - inset);
                for (int u = Math.min(ua, ub); u <= Math.max(ua, ub); u++) {
                    for (int ww = inset / 2; ww <= 8 - inset / 2; ww++) {
                        String b = y == hp - 4 || y == hp - 6 ? p.window : (y == hp ? p.capital : wm.at(f.x(u, ww), y, f.z(u, ww)));
                        f.set(u, y, ww, b);
                    }
                }
            }

            this.cloth(f, s * (w + 6) / 2 - s * 2, hp - 8, 5, 12, this.wool(s > 0 ? 0 : 1), p.clothTrim + "_wool", p.emGold);
        }

        // the gate between them with a winged sun disk
        f.box(-5, 1, 1, 5, 16, 7, wm);
        this.arch(f.moved(0, 1), 0, 1, 5, 10, false, 3, "dark_oak_planks", p.capital);
        for (int u = -5; u <= 5; u++) {
            f.set(u, 14, 0, Math.abs(u) <= 1 ? "gold_block" : p.window);
        }

        f.set(0, 15, 0, "gold_block");
        f.set(0, 13, 0, "gold_block");
    }

    // ---------------------------------------------------------------- the south side

    /** Covered stands facing the floor: rows of wooden seats with aisles, a canopy on posts, banners under it. */
    private void grandstand(int zs) {
        Pal p = this.pal;
        Mat wm = this.wallM();
        int ws = Math.max(14, (int)Math.round(this.hx * 0.6));
        Face f = new Face(0, zs, 0, 1);
        String seat = woodStair(p.planks);
        for (int k = 0; k < 7; k++) {
            for (int u = -ws; u <= ws; u++) {
                for (int y = 1; y <= k; y++) {
                    f.set(u, y, k, wm.at(f.x(u, k), y, f.z(u, k)));
                }

                boolean aisle = u == 0 || Math.abs(u) == ws / 2;
                f.set(u, k + 1, k, aisle ? stairs(stairOf(p.wall), f.back(), false) : stairs(seat, f.back(), false));
            }
        }

        f.box(-ws - 1, 1, 7, ws + 1, 13, 8, wm);
        for (int s = -1; s <= 1; s += 2) {
            for (int ww = 0; ww <= 7; ww++) {
                for (int y = 1; y <= 4 + ww; y++) {
                    f.set(s * (ws + 1), y, ww, wm.at(f.x(s * (ws + 1), ww), y, f.z(s * (ws + 1), ww)));
                }
            }
        }

        for (int ww = -1; ww <= 8; ww++) {
            int y = 12 + (ww + 1) / 4;
            for (int u = -ws - 2; u <= ws + 2; u++) {
                f.set(u, y, ww, stairs(p.roofStair, f.back(), false));
                f.set(u, y - 1, ww, ww >= 7 ? wm.at(f.x(u, ww), y - 1, f.z(u, ww)) : null);
            }
        }

        for (int u = -ws; u <= ws; u += 6) {
            for (int y = 1; y <= 11; y++) {
                f.set(u, y, 0, p.log + "[axis=y]");
            }

            f.set(u, 10, -1, p.light + "[hanging=true]");
        }

        int bi = 0;
        for (int u = -ws + 3; u <= ws - 3; u += 6) {
            this.cloth(f, u, 11, 3, 4, this.wool(bi++), p.clothTrim + "_wool", null);
        }

        if (this.named) {
            this.nameBoard(f, 15);
        }
    }

    /** The server's name on a big board standing on the stands, facing the floor (the signature arena only). */
    private void nameBoard(Face f, int y0) {
        Pal p = this.pal;
        String text = PixelFont.fit(PixelFont.serverName(), 60);
        if (text.isEmpty()) {
            return;
        }

        int w = PixelFont.width(text) + 4;
        int h = PixelFont.ROWS + 2;
        int left = -w / 2;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                boolean edge = i == 0 || i == w - 1 || j == 0 || j == h - 1;
                String b = edge ? p.clothTrim : p.clothBg;
                if (!edge && PixelFont.textOn(text, i - 2, h - 2 - j)) {
                    b = p.clothFg;
                }

                f.set(left + i, y0 + j, 6, b + "_wool");
            }
        }

        for (int u : new int[]{left + 3, -left - 3}) {
            for (int y = 14; y < y0; y++) {
                f.set(u, y, 7, p.log + "[axis=y]");
            }
        }
    }

    /** An arcade facing the floor: open arches under a terrace with a parapet and palms in planters. */
    private void loggia(int zs) {
        Pal p = this.pal;
        Mat wm = this.wallM();
        int ws = Math.max(14, (int)Math.round(this.hx * 0.6));
        Face f = new Face(0, zs, 0, 1);
        boolean pointed = !p.court.equals("PYRAMID");
        f.box(-ws, 1, 0, ws, 11, 6, wm);
        for (int u = -ws + 3; u <= ws - 3; u += 5) {
            this.arch(f, u, 1, 3, 7, pointed, 4, null, p.trim);
            this.window(f, u, 9, 1, 1, false, p.glass, "glowstone", null, null, 1);
            f.set(u, 7, 2, p.light + "[hanging=true]");
        }

        for (int u = -ws; u <= ws; u++) {
            f.set(u, 8, -1, p.trim);
            f.set(u, 12, 0, Math.floorMod(u, 2) == 0 ? p.capital : null);
        }

        for (int u = -ws + 4; u <= ws - 4; u += 8) {
            int x = f.x(u, 4);
            int z = f.z(u, 4);
            this.set(x, 12, z, "moss_block");
            this.palmTree(x, 13, z);
        }
    }

    /** Two round garden pavilions with tiled roofs and a great tree between them. */
    private void pavilions(int zs) {
        Pal p = this.pal;
        int ox = (int)Math.round(this.hx * 0.42);
        int cz = zs + 6;
        for (int s = -1; s <= 1; s += 2) {
            int cx = s * ox;
            this.cyl(cx, cz, 4.6, 1, 1, of(p.base));
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3.0;
                int px = cx + (int)Math.round(Math.cos(a) * 3.6);
                int pz = cz + (int)Math.round(Math.sin(a) * 3.6);
                for (int y = 2; y <= 6; y++) {
                    this.set(px, y, pz, p.log + "[axis=y]");
                }
            }

            this.ringAt(cx, cz, 3.6, 5.4, 7, this.roofM());
            this.ringAt(cx, cz, 2.4, 4.0, 8, this.roofM());
            this.ringAt(cx, cz, 0, 2.6, 9, this.roofM());
            this.set(cx, 10, cz, p.roofBlock);
            this.set(cx, 11, cz, "gold_block");
            this.set(cx, 6, cz, p.light + "[hanging=true]");
        }

        this.cyl(0, cz, 3.5, 1, 1, of(p.base));
        this.featureTree(0, 2, cz, 3);
    }

    // ---------------------------------------------------------------- the ends

    /** A freestanding gateway at one end of the floor, in the theme's style. */
    private void gateway(int side) {
        Pal p = this.pal;
        Mat wm = this.wallM();
        Face g = new Face(side * (this.hx + 4), 0, side, 0);
        String st = stairOf(p.wall);
        switch (p.court) {
            case "PALACE" -> {
                g.box(-7, 1, 0, 7, 19, 3, wm);
                this.arch(g, 0, 1, 7, 13, true, 4, null, p.trim);
                for (int u = -7; u <= 7; u++) {
                    g.set(u, 20, 0, Math.floorMod(u, 2) == 0 ? p.capital : null);
                    g.set(u, 15, -1, p.trim);
                }

                for (int s = -1; s <= 1; s += 2) {
                    this.minaret(g.x(s * 8, 1), g.z(s * 8, 1), 1.3, 22);
                }
            }
            case "CATHEDRAL" -> {
                g.box(-6, 1, 0, 6, 16, 2, wm);
                this.arch(g, 0, 1, 7, 12, true, 3, null, p.trim);
                for (int k = 0; k <= 6; k++) {
                    for (int u = -6 + k; u <= 6 - k; u++) {
                        g.set(u, 17 + k, 1, Math.abs(u) == 6 - k ? stairs(st, u < 0 ? g.right() : g.left(), false) : p.wall);
                    }
                }

                for (int s = -1; s <= 1; s += 2) {
                    for (int y = 1; y <= 20; y++) {
                        g.set(s * 7, y, 1, p.capital);
                    }

                    this.spire(g.x(s * 7, 1), 21, g.z(s * 7, 1), 4, p.wall, "lightning_rod[facing=up]");
                }
            }
            case "PAGODA" -> {
                for (int s = -1; s <= 1; s += 2) {
                    for (int y = 1; y <= 12; y++) {
                        g.set(s * 5, y, 1, y == 1 ? p.base : "red_concrete");
                    }
                }

                for (int u = -7; u <= 7; u++) {
                    if (Math.abs(u) <= 6) {
                        g.set(u, 10, 1, "red_concrete");
                    }

                    g.set(u, 13, 1, Math.abs(u) >= 7 ? "blackstone_slab[type=bottom]" : "polished_blackstone");
                    if (Math.abs(u) >= 6) {
                        g.set(u, 14, 1, "polished_blackstone_slab[type=bottom]");
                    }
                }

                g.set(0, 11, 1, "gold_block");
                g.set(0, 12, 1, "gold_block");
            }
            case "ZIGGURAT", "PYRAMID" -> {
                for (int s = -1; s <= 1; s += 2) {
                    for (int y = 1; y <= 15; y++) {
                        int inset = y / 6;
                        for (int u = s * 3; Math.abs(u) <= 8 - inset; u += s) {
                            for (int ww = inset / 2; ww <= 3 - inset / 2; ww++) {
                                g.set(u, y, ww, y == 12 ? p.window : (y == 15 ? p.capital : wm.at(g.x(u, ww), y, g.z(u, ww))));
                            }
                        }
                    }

                    g.set(s * 6, 16, 1, "campfire[lit=true,signal_fire=false]");
                }

                for (int u = -3; u <= 3; u++) {
                    for (int y = 11; y <= 13; y++) {
                        g.set(u, y, 1, wm.at(g.x(u, 1), y, g.z(u, 1)));
                    }

                    g.set(u, 12, 0, Math.abs(u) <= 1 ? "gold_block" : p.window);
                }
            }
            default -> {
                // a small castle gate: two square towers and a battlemented arch between them
                for (int s = -1; s <= 1; s += 2) {
                    g.box(s * 5, 1, 0, s * 9, 17, 4, wm);
                    this.slit(g, s * 7, 10);
                    int x0 = Math.min(g.x(s * 5, 0), g.x(s * 9, 4));
                    int x1 = Math.max(g.x(s * 5, 0), g.x(s * 9, 4));
                    int z0 = Math.min(g.z(s * 5, 0), g.z(s * 9, 4));
                    int z1 = Math.max(g.z(s * 5, 0), g.z(s * 9, 4));
                    int top = this.pyramidRoof(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 18, p.roofStair, this.roofM());
                    this.set((x0 + x1) / 2, top + 1, (z0 + z1) / 2, p.fence);
                    this.set((x0 + x1) / 2, top + 2, (z0 + z1) / 2, p.banners[0] + "_banner[rotation=0]");
                }

                g.box(-4, 9, 1, 4, 13, 3, wm);
                this.merlons(g.moved(0, 1), -4, 4, 14, wm, 0);
                for (int u = -4; u <= 4; u++) {
                    g.set(u, 9, 1, p.trim);
                }

                this.cloth(g.moved(0, 1), 0, 12, 3, 4, this.wool(0), p.clothTrim + "_wool", p.emGold);
            }
        }
    }

    // ---------------------------------------------------------------- the edge and the gardens

    /** A low curb round the floor with lamp posts, a paved walk, and hedges with gaps beyond it. */
    private void edge() {
        Pal p = this.pal;
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.0 || d >= 4.0 || !this.empty(x, 1, z)) {
                    continue;
                }

                double s = this.along(x, z);
                if (d < 1.0) {
                    this.set(x, 1, z, p.trim);
                    if (Math.floorMod((int)Math.round(s), 12) == 0) {
                        this.set(x, 2, z, p.fence);
                        this.set(x, 3, z, p.fence);
                        this.set(x, 4, z, p.light + "[hanging=false]");
                    }
                } else if (d >= 3.0 && p.hedge != null && Math.floorMod((int)Math.round(s), 10) >= 2 && this.empty(x, 3, z)) {
                    this.set(x, 1, z, leaves(p.hedge));
                    if (hash3(this.seed + 51L, x, 1, z) < 0.3) {
                        this.set(x, 2, z, p.flowers[(int)(hash3(this.seed + 53L, x, 2, z) * p.flowers.length)]);
                    }
                }
            }
        }
    }

    private boolean clear(int x, int z, int r, int h) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int y = 1; y <= h; y++) {
                    if (!this.empty(x + dx, y, z + dz)) {
                        return false;
                    }
                }

                if (this.sd(x + dx, z + dz) < 4.0 || this.sd(x + dx, z + dz) > this.dOut) {
                    return false;
                }
            }
        }

        return true;
    }

    /** A tree of the theme. */
    private void featureTree(int x, int y, int z, int size) {
        switch (this.pal.tree) {
            case "SPRUCE" -> this.spruce(x, y, z, 8 + size * 2, false);
            case "SNOWY" -> this.spruce(x, y, z, 8 + size * 2, true);
            case "PALM" -> this.palmTree(x, y, z);
            case "CHERRY" -> this.cherryTree(x, y, z);
            case "DARK" -> this.broadleaf(x, y, z, "dark_oak_log", "dark_oak", 4 + size, 3.0 + size * 0.4);
            case "DEAD" -> this.deadTree(x, y, z, "dark_oak_log", 6 + size * 2);
            case "JUNGLE" -> this.broadleaf(x, y, z, "jungle_log", "jungle", 5 + size, 2.8 + size * 0.3);
            default -> this.broadleaf(x, y, z, size % 2 == 0 ? "oak_log" : "birch_log", size % 2 == 0 ? "oak" : "birch", 4 + size, 2.8 + size * 0.4);
        }
    }

    /** A fountain basin (glass for water: no water in arenas) with a tiered column. */
    private void dryFountain(int x, int z) {
        Pal p = this.pal;
        this.ringAt(x, z, 4.0, 5.0, 1, of(p.trim));
        this.ringAt(x, z, 0, 4.0, 1, of("light_blue_stained_glass"));
        for (int y = 1; y <= 4; y++) {
            this.set(x, y, z, p.capital);
        }

        this.ringAt(x, z, 0, 2.0, 4, of(p.trim));
        this.set(x, 4, z, p.capital);
        this.ringAt(x, z, 0, 1.2, 5, of("light_blue_stained_glass"));
        this.set(x, 5, z, p.capital);
        this.set(x, 6, z, p.emGold);
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2.0 + Math.PI / 4.0;
            int lx = x + (int)Math.round(Math.cos(a) * 6.0);
            int lz = z + (int)Math.round(Math.sin(a) * 6.0);
            this.lantern(lx, 1, lz, p.fence, 3, p.light + "[hanging=false]");
        }
    }

    /** A statue of a knight with a sword on a plinth, or an obelisk for the desert themes. */
    private void gardenStatue(int x, int z) {
        Pal p = this.pal;
        this.fill(x - 2, 1, z - 2, x + 2, 1, z + 2, of(p.base));
        this.fill(x - 1, 2, z - 1, x + 1, 3, z + 1, of(p.trim));
        if (p.court.equals("PALACE") || p.court.equals("PYRAMID")) {
            for (int y = 4; y <= 16; y++) {
                this.set(x, y, z, y % 4 == 0 ? p.capital : p.wall);
            }

            this.set(x, 17, z, "gold_block");
            return;
        }

        String s = p.wallAlt;
        this.set(x, 4, z, s);
        this.set(x, 5, z, s);
        this.set(x, 6, z, s);
        this.set(x, 7, z, s);
        this.set(x - 1, 7, z, s);
        this.set(x + 1, 7, z, s);
        this.set(x, 8, z, p.capital);
        this.set(x + 1, 6, z, "iron_block");
        this.set(x + 1, 5, z, "gold_block");
        this.set(x + 1, 4, z, "iron_block");
        this.set(x + 1, 8, z, "iron_block");
        this.set(x + 1, 9, z, "iron_block");
        for (int[] o : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            this.set(x + o[0], 2, z + o[1], p.light + "[hanging=false]");
        }
    }

    /** The gardens: a fountain or a statue in each open corner, then trees and flowers on the free ground. */
    private void gardens() {
        Pal p = this.pal;
        int k = 0;
        for (int sz = 1; sz >= -1; sz -= 2) {
            for (int sx = -1; sx <= 1; sx += 2) {
                int cx = (int)Math.round(sx * (this.hx - this.rc + (this.rc + 12) * 0.7071));
                int cz = (int)Math.round(sz * (this.hz - this.rc + (this.rc + 12) * 0.7071));
                if (this.clear(cx, cz, 5, 9)) {
                    if (k++ % 2 == 0) {
                        this.dryFountain(cx, cz);
                    } else {
                        this.gardenStatue(cx, cz);
                    }
                }
            }
        }

        java.util.Random r = new java.util.Random(this.seed ^ 0x6A7DL);
        for (int x = -this.R + 2; x <= this.R - 2; x += 10) {
            for (int z = -this.R + 2; z <= this.R - 2; z += 10) {
                int tx = x + r.nextInt(5) - 2;
                int tz = z + r.nextInt(5) - 2;
                int size = r.nextInt(3);
                double d = this.sd(tx, tz);
                boolean skip = r.nextDouble() < 0.35;
                if (skip || d < 10.0 || d > this.dOut - 2 || Math.abs(tx) > this.hx && Math.abs(tz) < 13 || !this.clear(tx, tz, 2, 10)) {
                    continue;
                }

                this.set(tx, 0, tz, p.plaza[0].contains("sand") ? "coarse_dirt" : "grass_block");
                this.featureTree(tx, 1, tz, size);
                for (int[] o : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
                    if (r.nextDouble() < 0.5 && this.empty(tx + o[0], 1, tz + o[1])) {
                        this.set(tx + o[0], 1, tz + o[1], p.flowers[r.nextInt(p.flowers.length)]);
                    }
                }
            }
        }
    }

    // ================================================================== walled courts: the ring design (3.15.0)

    /**
     * One column of a walled court's ring: wooden pilasters and arched niches round the floor, light panels, a balcony
     * with flower boxes, an arcade, buildings behind it and a tiled roof with rafters and chimneys.
     */
    private void courtColumn(int x, int z, double d, double u, int bi, String out) {
        Pal p = this.pal;
        String in = opposite(out);
        boolean pil = u < 2.0;
        double mid = this.mid(u);
        int top = this.pH;
        if (d < 1.0) {
            // wooden pilasters, arched niches one block back
            double at = this.archTop(top - 1, u, 2.4);
            for (int y = 1; y <= top; y++) {
                if (pil) {
                    this.set(x, y, z, y == 1 ? p.base : (y == top ? p.capital : p.log + "[axis=y]"));
                } else if (y > at) {
                    this.set(x, y, z, y == top ? p.log + "[axis=" + alongAxis(out) + "]" : this.tex(351L, x, y, z, p.wall, p.wallAlt, p.wallAlt2));
                }
            }

            if (!pil && mid < 0.2) {
                if (Math.floorMod(bi, 3) == 0) {
                    this.set(x, top - 2, z, this.wallBanner(p.banners[Math.floorMod(bi / 3, p.banners.length)], in));
                } else {
                    this.set(x, (int)Math.floor(at), z, p.light + "[hanging=true]");
                }
            }
        } else if (d < 2.0) {
            // the niche's back: stone below, a light arched panel
            double at = this.archTop(top - 1, u, 2.4) - 0.8;
            for (int y = 1; y <= top; y++) {
                String b;
                if (pil) {
                    b = this.tex(353L, x, y, z, p.wall, p.wallAlt, p.wallAlt2);
                } else if (y <= 2) {
                    b = this.tex(355L, x, y, z, p.wall, p.wallAlt2, p.wallAlt);
                } else if (y <= at && mid < 0.85) {
                    b = p.window;
                } else {
                    b = y == top ? p.trim : this.tex(357L, x, y, z, p.wall, p.wallAlt, p.wallAlt2);
                }

                this.set(x, y, z, b);
            }
        } else if (d < 3.0) {
            // the wall's core, the balcony's edge with a railing, flower boxes and lantern posts
            this.fillTo(x, z, top, p.wall, p.wallAlt, p.wallAlt2);
            this.set(x, top + 1, z, p.planks);
            if (pil) {
                this.set(x, top + 2, z, p.log + "[axis=y]");
                this.set(x, top + 3, z, p.light + "[hanging=false]");
            } else if (mid < 0.35) {
                this.set(x, top + 2, z, leaves(p.leaves));
                this.set(x, top + 3, z, p.flowers[(int)(hash3(this.seed + 359L, x, 0, z) * p.flowers.length)]);
            } else {
                this.set(x, top + 2, z, p.fence);
            }
        } else if (d < 6.0) {
            // the balcony, and the posts holding the roof's overhang
            this.fillTo(x, z, top, p.wall, p.wallAlt, p.wallAlt2);
            this.set(x, top + 1, z, p.planks);
            if (d >= 5.0 && pil) {
                for (int y = top + 2; y <= top + 1 + AH; y++) {
                    this.set(x, y, z, p.log + "[axis=y]");
                }
            }
        } else if (d < 9.0) {
            // the arcade: arched openings between wooden posts, a corridor, a back wall with small windows
            int k = (int)Math.floor(d - 6.0);
            this.fillTo(x, z, top + 1, p.wall, p.wallAlt, p.wallAlt2);
            int at = (int)Math.floor(this.archTop(top + AH - 2, u, 2.6));
            for (int y = top + 2; y <= top + 1 + AH; y++) {
                String b;
                if (k == 0) {
                    if (pil) {
                        b = p.log + "[axis=y]";
                    } else if (y <= at) {
                        b = null;
                    } else {
                        b = y == top + 1 + AH ? p.log + "[axis=" + alongAxis(out) + "]" : (mid < 0.2 && y == at + 1 ? p.capital : this.tex(361L, x, y, z, p.arch, p.arch, p.wallAlt));
                    }
                } else if (k == 1) {
                    b = y == top + 1 + AH ? p.planks : null;
                } else {
                    boolean win = !pil && mid < 0.3 && y >= top + 4 && y <= top + 5;
                    b = win ? p.glass : this.tex(363L, x, y, z, p.arch, p.arch, p.wallAlt);
                }

                this.set(x, y, z, b);
            }

            if (k == 0) {
                this.set(x, top + 2, z, pil ? p.log + "[axis=y]" : p.planks);
                if (!pil && mid < 0.2 && Math.floorMod(bi, 2) == 0) {
                    this.set(x, at, z, p.light + "[hanging=true]");
                }
            } else if (k == 1 && !pil && mid < 0.2) {
                this.set(x, top + AH, z, p.light + "[hanging=true]");
            }
        } else if (d < 14.0) {
            // the buildings behind, windows on their outer face
            this.fillTo(x, z, top + 1 + AH, p.wall, p.wallAlt, p.wallAlt2);
            if (d >= 13.0) {
                for (int y = 2; y <= top + AH; y++) {
                    boolean win = !pil && mid < 0.3 && (y % 5 == 3 || y % 5 == 4);
                    this.set(x, y, z, win ? p.glass : this.tex(365L, x, y, z, p.arch, p.arch, p.wallAlt));
                }
            }
        }

        // the tiled roof: an overhang over the balcony up to a ridge, rafters under the eave, chimneys
        double rs = d - 4.0;
        if (rs >= 0.0 && rs < 10.0) {
            int ry = top + 2 + AH + (int)Math.floor(rs * 0.7);
            this.set(x, ry, z, rs > 9.0 ? this.tex(367L, x, ry, z, p.roofBlock, p.roofAlt, p.roofBlock) : stairs(p.roofStair, out, false));
            if (rs < 2.0) {
                if (pil) {
                    this.set(x, ry - 1, z, p.log + "[axis=" + acrossAxis(out) + "]");
                }
            } else {
                for (int y = top + 2 + AH; y < ry; y++) {
                    this.set(x, y, z, this.tex(369L, x, y, z, p.roofBlock, p.roofAlt, p.roofBlock));
                }
            }

            if (rs > 6.0 && rs < 7.0 && pil && Math.floorMod(bi, 4) == 2) {
                for (int y = ry + 1; y <= ry + 4; y++) {
                    this.set(x, y, z, "bricks");
                }

                this.set(x, ry + 5, z, "campfire[lit=true,signal_fire=false]");
            }
        }
    }

    /** Round towers on the four corners, topped by theme: cones, onion domes, pyramids or spires. */
    private void courtTowers() {
        Pal p = this.pal;
        double off = this.rc * 0.29 + 9.0;
        int h = this.pH + AH + 12;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int tx = (int)Math.round(sx * (this.hx - this.rc + (this.rc + off) * 0.7071));
                int tz = (int)Math.round(sz * (this.hz - this.rc + (this.rc + off) * 0.7071));
                double r = 5.0;
                Mat stone = (x, y, z) -> this.tex(371L, x, y, z, p.wall, p.wallAlt, p.wallAlt2);
                this.cyl(tx, tz, r + 1.0, 1, 3, of(p.base));
                this.cyl(tx, tz, r, 1, h, stone);
                this.cyl(tx, tz, r - 1.2, 2, h - 1, of("air"));
                for (int y = 2; y < h; y++) {
                    for (int dx = -5; dx <= 5; dx++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            if ("air".equals(this.get(tx + dx, y, tz + dz))) {
                                this.set(tx + dx, y, tz + dz, null);
                            }
                        }
                    }
                }

                // bands, windows and lanterns
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI / 8.0;
                    int wx = tx + (int)Math.round(Math.cos(a) * r);
                    int wz = tz + (int)Math.round(Math.sin(a) * r);
                    for (int y = 6; y < h; y += 7) {
                        this.set(wx, y, wz, p.trim);
                        if (k % 4 == 0 && y + 3 < h) {
                            this.set(wx, y + 2, wz, p.glass);
                            this.set(wx, y + 3, wz, p.glass);
                        }
                    }
                }

                this.ringAt(tx, tz, r - 0.4, r + 1.3, h, of(p.capital));
                for (int k = 0; k < 20; k++) {
                    double a = k * Math.PI / 10.0;
                    if (k % 2 == 0) {
                        this.set(tx + (int)Math.round(Math.cos(a) * (r + 0.9)), h + 1, tz + (int)Math.round(Math.sin(a) * (r + 0.9)), p.wall);
                    }
                }

                this.towerTop(tx, h + 1, tz, r);
            }
        }
    }


    /**
     * A fortified gatehouse in the middle of each short side: two square towers standing forward with roofs and flags,
     * between them a recessed gate front under a corbelled parapet with battlements, a great arch with a portcullis and
     * doors, banners and lanterns. Nothing stands out over the floor.
     */
    private void courtGates() {
        Pal p = this.pal;
        int gh = this.pH + 1 + AH + 3;
        int th = gh + 6;
        int low = gh - 4;
        for (int side = -1; side <= 1; side += 2) {
            String in = side > 0 ? "west" : "east";
            String out = opposite(in);
            for (int dd = 0; dd < 10; dd++) {
                int x = side * (this.hx + dd);
                for (int z = -11; z <= 11; z++) {
                    int az = Math.abs(z);
                    boolean tower = az >= 6 && dd <= 5;
                    for (int y = 1; y <= th + 20; y++) {
                        this.set(x, y, z, null);
                    }

                    for (int y = 1; y <= (tower ? th : gh); y++) {
                        this.set(x, y, z, this.courtGateCell(x, z, dd, az, y, tower, gh, th, low, out));
                    }

                    // battlements on the middle part's parapet
                    if (!tower && dd == 0 && az <= 5 && Math.floorMod(az, 2) == 1) {
                        this.set(x, gh + 1, z, this.tex(383L, x, gh + 1, z, p.wall, p.wallAlt, p.wallAlt2));
                    }
                }
            }

            // the towers' roofs
            for (int sz = -1; sz <= 1; sz += 2) {
                int x0 = side > 0 ? this.hx : -this.hx - 5;
                int z0 = sz > 0 ? 6 : -11;
                double tcx = x0 + 2.5;
                double tcz = z0 + 2.5;
                switch (p.towerTop) {
                    case "ONION" -> {
                        this.fill(x0, th + 1, z0, x0 + 5, th + 1, z0 + 5, of(p.trim));
                        this.onion(tcx, th + 2, tcz, 2.6, of(p.dome), p.emGold);
                    }
                    case "SPIRE" -> {
                        for (int k = 0; k < 14; k++) {
                            int w = 3 - k / 4;
                            this.fill(x0 + 3 - w, th + 1 + k, z0 + 3 - w, x0 + 2 + w, th + 1 + k, z0 + 2 + w,
                                (xx, yy, zz) -> this.tex(385L, xx, yy, zz, p.roofBlock, p.roofAlt, p.roofBlock));
                        }

                        this.set(x0 + 2, th + 15, z0 + 2, "lightning_rod[facing=up]");
                    }
                    case "PYRAMID" -> {
                        for (int k = 0; k <= 3; k++) {
                            this.fill(x0 - 1 + k, th + 1 + k, z0 - 1 + k, x0 + 6 - k, th + 1 + k, z0 + 6 - k, of(k == 0 ? p.trim : p.roofBlock));
                        }

                        this.fill(x0 + 2, th + 5, z0 + 2, x0 + 3, th + 5, z0 + 3, of("gold_block"));
                    }
                    default -> {
                        int ty = this.pyramidRoof(x0 - 1, z0 - 1, x0 + 6, z0 + 6, th + 1, p.roofStair,
                            (xx, yy, zz) -> this.tex(387L, xx, yy, zz, p.roofBlock, p.roofAlt, p.roofBlock));
                        for (int y = ty + 1; y <= ty + 4; y++) {
                            this.set(x0 + 2, y, z0 + 2, p.fence);
                        }

                        this.set(x0 + 2, ty + 5, z0 + 2, p.banners[0] + "_banner[rotation=" + rotationOf(in) + "]");
                    }
                }
            }

            // the gate front: the theme's banner over the arch, two more under the parapet, lanterns under the corbels
            int fx = side * this.hx;
            this.set(fx, 12, 0, this.wallBanner(p.banners[0], in));
            for (int k = -1; k <= 1; k += 2) {
                this.set(fx, low - 1, k * 5, this.wallBanner(p.banners[Math.min(1, p.banners.length - 1)], in));
                this.set(fx, low, k * 3, p.light + "[hanging=true]");
            }
        }
    }

    /** One block of a court gatehouse: dd blocks behind its face, az from its middle. */
    private String courtGateCell(int x, int z, int dd, int az, int y, boolean tower, int gh, int th, int low, String out) {
        Pal p = this.pal;
        boolean passage = az <= 3 && y <= 8 - (az == 3 ? 1 : 0);
        if (passage && dd <= 5) {
            return dd == 2 ? "iron_bars" : (dd == 5 ? (Math.floorMod(az, 2) == 0 ? "dark_oak_planks" : "spruce_planks") : null);
        }

        String stone = this.tex(381L, x, y, z, p.wall, p.wallAlt, p.wallAlt2);
        if (!tower && az <= 5) {
            if (dd == 0) {
                // the recess under the parapet, the corbels, the parapet
                if (y <= low) {
                    return null;
                }

                if (y == low + 1) {
                    return stairs(stairOf(p.wall), out, true);
                }

                return y == gh - 1 ? p.trim : stone;
            }

            if (dd == 1) {
                // the recessed front: a great arch with voussoirs and a gold keystone
                if (az == 4 && y <= 7 || az == 3 && y == 8 || az <= 2 && y == 9) {
                    return az == 0 ? p.emGold : p.capital;
                }

                if (y == 1 || y == low) {
                    return p.trim;
                }
            }

            return stone;
        }

        if (tower && dd == 0) {
            if (az == 6 || az == 11) {
                return y / 2 % 2 == 0 ? p.capital : stone;
            }

            boolean slit = (az == 8 || az == 9) && (y == 6 || y == 7);
            boolean win = (az == 8 || az == 9) && (y == 13 || y == 14 || y == th - 4 || y == th - 3);
            if (slit) {
                return null;
            }

            if (win) {
                return p.glass;
            }

            if (y == 1 || y == gh - 1 || y == th - 1 || y == th) {
                return p.trim;
            }
        }

        if (tower && dd == 1 && (az == 8 || az == 9)) {
            if (y == 6 || y == 7) {
                return "polished_blackstone";
            }

            if (y == 13 || y == 14 || y == th - 4 || y == th - 3) {
                return "glowstone";
            }
        }

        if (tower && dd == 5 && (az == 6 || az == 11)) {
            return y / 2 % 2 == 0 ? p.capital : stone;
        }

        return stone;
    }


    // ------------------------------------------------------------------ the giant cloth with the server's name

    /** A giant cloth with the server's name in pixel letters, hung over the arcade on both long sides, facing the floor. */
    private void cloth() {
        Pal p = this.pal;
        int wall;
        int yb;
        int room;
        if (this.theme.stadium) {
            room = (int)Math.round(this.straight * 2.0) + 1;
            wall = (int)Math.round(this.radius) + 5 + this.n1;
            yb = this.yArcBase;
        } else {
            room = (this.hx - this.rc) * 2 + 1;
            wall = this.hz + 5;
            yb = this.pH + 2;
        }

        String text = PixelFont.fit(PixelFont.serverName(), room - 6);
        if (text.isEmpty()) {
            return;
        }

        int w = PixelFont.width(text) + 4;
        int h = PixelFont.ROWS + 2;
        int x0 = -w / 2;
        for (int side = -1; side <= 1; side += 2) {
            int z = side * wall;
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    boolean edge = i == 0 || i == w - 1 || j == 0 || j == h - 1;
                    String b = edge ? p.clothTrim : p.clothBg;
                    if (!edge && PixelFont.textOn(text, i - 2, h - 2 - j)) {
                        b = p.clothFg;
                    }

                    // read from the floor: left to right is +x seen from the south side, -x from the north
                    int x = side > 0 ? x0 + w - 1 - i : x0 + i;
                    this.set(x, yb + j, z, b + "_wool");
                }
            }
        }
    }

    // ------------------------------------------------------------------ barriers

    private void barrierRing() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d >= 0.0 && d < 1.0) {
                    for (int y = 1; y <= this.yHi; y++) {
                        if (this.empty(x, y, z)) {
                            this.set(x, y, z, "barrier");
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ palettes

    private static Pal palette(Theme t) {
        Pal p = new Pal();
        switch (t) {
            case ROMAN -> {
                natural(p, "sand", "smooth_sandstone", "sandstone");
                stadium(p, "quartz_bricks", "smooth_quartz", "calcite", "quartz_pillar[axis=y]", "chiseled_quartz_block", "polished_diorite");
                p.trim = "smooth_quartz";
                p.panel = "granite";
                p.panelAlt = "polished_granite";
                p.arch = "quartz_bricks";
                p.column = "quartz_pillar[axis=y]";
                roof(p, "red_sandstone", "smooth_red_sandstone", "cut_red_sandstone");
                seats(p, "quartz", "polished_granite", "polished_diorite", "smooth_quartz", "quartz");
                p.land = ArenaLandscape.Kind.OLYMPUS;
            }
            case GRAND -> {
                natural(p, "grass_block", "moss_block");
                stadium(p, "stone_bricks", "andesite", "cracked_stone_bricks", "stripped_spruce_log[axis=y]", "chiseled_stone_bricks", "polished_andesite");
                p.trim = "smooth_sandstone";
                p.panel = "spruce_planks";
                p.panelAlt = "stripped_spruce_log[axis=y]";
                p.arch = "smooth_sandstone";
                p.column = "cut_sandstone";
                p.log = "stripped_spruce_log";
                roof(p, "brick", "bricks", "granite");
                seats(p, "spruce", "dark_oak", "stone_bricks", "smooth_sandstone", "stone_brick");
                p.banners = new String[]{"red", "blue"};
                p.land = ArenaLandscape.Kind.ALPINE;
            }
            case GLADIATOR -> {
                tonal(p, new String[]{"terracotta", "packed_mud", "mud_bricks", "granite"}, 0.55, 0.2, 0.13, 0.12);
                stadium(p, "stone_bricks", "polished_andesite", "cracked_stone_bricks", "polished_andesite", "chiseled_stone_bricks", "polished_andesite");
                p.trim = "smooth_stone";
                p.band = "gold_block";
                p.panel = "stripped_oak_log[axis=y]";
                p.panelAlt = "dark_oak_planks";
                p.arch = "stone_bricks";
                p.column = "polished_andesite";
                roof(p, "dark_oak", "dark_oak_planks", "spruce_planks");
                seats(p, "oak", "birch", "spruce_planks", "polished_andesite", "stone_brick");
                p.banners = new String[]{"orange", "black"};
                p.clothBg = "orange";
                p.clothFg = "black";
                p.clothTrim = "white";
                p.land = ArenaLandscape.Kind.OLYMPUS;
            }
            case MEDIEVAL -> {
                tonal(p, new String[]{"stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "polished_andesite", "andesite"}, 0.5, 0.15, 0.1, 0.15, 0.1);
                p.motif = Motif.ironSword();
                court(p, "stone_bricks", "andesite", "cobblestone", "birch_planks", "smooth_sandstone");
                roof(p, "brick", "bricks", "granite");
                p.banners = new String[]{"red", "blue"};
                p.leaves = "flowering_azalea";
                p.land = ArenaLandscape.Kind.ALPINE;
            }
            case FROST -> {
                natural(p, "snow_block", "white_concrete_powder", "calcite");
                court(p, "stone_bricks", "polished_diorite", "cobblestone", "light_blue_terracotta", "calcite");
                roof(p, "dark_prismarine", "dark_prismarine", "prismarine_bricks");
                p.banners = new String[]{"blue", "white"};
                p.clothBg = "blue";
                p.clothTrim = "light_blue";
                p.flowers = new String[]{"snow[layers=2]"};
                p.light = "soul_lantern";
                p.land = ArenaLandscape.Kind.FROST;
            }
            case DESERT -> {
                natural(p, "sand", "sandstone", "smooth_sandstone");
                p.log = "stripped_jungle_log";
                p.planks = "jungle_planks";
                p.fence = "jungle_fence";
                court(p, "cut_sandstone", "smooth_sandstone", "sandstone", "light_blue_glazed_terracotta", "smooth_sandstone");
                roof(p, "red_sandstone", "orange_terracotta", "smooth_red_sandstone");
                p.dome = "light_blue_terracotta";
                p.banners = new String[]{"red", "light_blue"};
                p.clothTrim = "light_blue";
                p.capital = "chiseled_sandstone";
                p.leaves = "jungle";
                p.towerTop = "ONION";
                p.land = ArenaLandscape.Kind.DESERT;
            }
            case HIGHLAND -> {
                tonal(p, new String[]{"coarse_dirt", "packed_mud", "rooted_dirt", "podzol"}, 0.5, 0.2, 0.15, 0.15);
                p.log = "stripped_spruce_log";
                court(p, "cobblestone", "stone", "mossy_cobblestone", "spruce_planks", "spruce_planks");
                roof(p, "deepslate_tile", "deepslate_tiles", "cobbled_deepslate");
                p.banners = new String[]{"green", "white"};
                p.clothBg = "green";
                p.land = ArenaLandscape.Kind.ALPINE;
            }
            case SAKURA -> {
                tonal(p, new String[]{"calcite", "polished_diorite", "smooth_quartz", "diorite"}, 0.45, 0.2, 0.2, 0.15);
                p.log = "stripped_cherry_log";
                p.planks = "cherry_planks";
                p.fence = "cherry_fence";
                court(p, "calcite", "smooth_quartz", "polished_diorite", "pink_terracotta", "white_terracotta");
                roof(p, "deepslate_tile", "deepslate_tiles", "polished_deepslate");
                p.banners = new String[]{"pink", "white"};
                p.clothBg = "pink";
                p.clothTrim = "white";
                p.clothFg = "black";
                p.leaves = "cherry";
                p.flowers = new String[]{"pink_tulip", "allium", "lily_of_the_valley"};
                p.towerTop = "PYRAMID";
                p.land = ArenaLandscape.Kind.SAKURA;
            }
            case EMBER -> {
                natural(p, "red_sand", "red_sandstone", "smooth_red_sandstone");
                p.log = "stripped_crimson_stem";
                p.planks = "crimson_planks";
                p.fence = "crimson_fence";
                court(p, "red_sandstone", "smooth_red_sandstone", "cut_red_sandstone", "orange_terracotta", "polished_blackstone");
                roof(p, "crimson", "crimson_planks", "nether_wart_block");
                p.banners = new String[]{"black", "orange"};
                p.clothBg = "black";
                p.clothFg = "orange";
                p.clothTrim = "red";
                p.light = "soul_lantern";
                p.leaves = "dark_oak";
                p.land = ArenaLandscape.Kind.MESA;
            }
            case GOTHIC -> {
                tonal(p, new String[]{"polished_tuff", "tuff_bricks", "tuff", "chiseled_tuff"}, 0.45, 0.3, 0.15, 0.1);
                p.planks = "dark_oak_planks";
                p.fence = "dark_oak_fence";
                court(p, "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles", "purple_stained_glass", "polished_blackstone");
                roof(p, "deepslate_tile", "deepslate_tiles", "blackstone");
                p.banners = new String[]{"purple", "black"};
                p.clothBg = "purple";
                p.clothTrim = "black";
                p.glass = "purple_stained_glass_pane";
                p.light = "soul_lantern";
                p.leaves = "dark_oak";
                p.towerTop = "SPIRE";
                p.land = ArenaLandscape.Kind.DARK;
            }
            case TROPICAL -> {
                natural(p, "sand", "smooth_sandstone", "sandstone");
                p.log = "stripped_jungle_log";
                p.planks = "bamboo_planks";
                p.fence = "bamboo_fence";
                court(p, "smooth_sandstone", "sandstone", "cut_sandstone", "bamboo_mosaic", "stripped_jungle_log[axis=y]");
                roof(p, "bamboo_mosaic", "bamboo_mosaic", "bamboo_planks");
                p.banners = new String[]{"lime", "yellow"};
                p.clothBg = "lime";
                p.clothFg = "white";
                p.leaves = "jungle";
                p.flowers = new String[]{"blue_orchid", "allium", "orange_tulip"};
                p.land = ArenaLandscape.Kind.TROPICAL;
            }
            case JUNGLE -> {
                tonal(p, new String[]{"mossy_stone_bricks", "mossy_cobblestone", "stone_bricks", "moss_block", "cracked_stone_bricks"}, 0.45, 0.2, 0.15, 0.1, 0.1);
                p.log = "stripped_jungle_log";
                p.planks = "jungle_planks";
                p.fence = "jungle_fence";
                court(p, "mossy_stone_bricks", "stone_bricks", "mossy_cobblestone", "chiseled_stone_bricks", "jungle_planks");
                roof(p, "waxed_oxidized_cut_copper", "waxed_oxidized_copper", "waxed_weathered_cut_copper");
                p.banners = new String[]{"green", "lime"};
                p.clothBg = "green";
                p.clothTrim = "lime";
                p.leaves = "jungle";
                p.towerTop = "PYRAMID";
                p.land = ArenaLandscape.Kind.JUNGLE;
            }
            case VOLCANO -> {
                tonal(p, new String[]{"polished_blackstone", "blackstone", "polished_blackstone_bricks", "cracked_polished_blackstone_bricks"}, 0.4, 0.25, 0.25, 0.1);
                p.log = "polished_basalt";
                p.planks = "crimson_planks";
                p.fence = "nether_brick_fence";
                court(p, "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "blackstone", "shroomlight", "gilded_blackstone");
                roof(p, "nether_brick", "nether_bricks", "red_nether_bricks");
                p.banners = new String[]{"red", "black"};
                p.clothBg = "black";
                p.clothFg = "red";
                p.clothTrim = "orange";
                p.leaves = "dark_oak";
                p.land = ArenaLandscape.Kind.VOLCANIC;
            }
            case HARBOR -> {
                tonal(p, new String[]{"spruce_planks", "stripped_spruce_wood[axis=y]", "dark_oak_planks", "oak_planks"}, 0.6, 0.2, 0.1, 0.1);
                court(p, "stone_bricks", "mossy_stone_bricks", "cobblestone", "oak_planks", "spruce_planks");
                roof(p, "spruce", "spruce_planks", "dark_oak_planks");
                p.banners = new String[]{"black", "white"};
                p.clothBg = "black";
                p.clothFg = "white";
                p.clothTrim = "red";
                p.leaves = "oak";
                p.land = ArenaLandscape.Kind.TROPICAL;
            }
            case ATLANTIS -> {
                tonal(p, new String[]{"prismarine_bricks", "prismarine", "dark_prismarine", "prismarine_bricks"}, 0.45, 0.25, 0.15, 0.15);
                p.log = "stripped_warped_stem";
                p.planks = "warped_planks";
                p.fence = "warped_fence";
                court(p, "prismarine_bricks", "prismarine", "dark_prismarine", "sea_lantern", "dark_prismarine");
                roof(p, "dark_prismarine", "dark_prismarine", "prismarine_bricks");
                p.capital = "chiseled_quartz_block";
                p.dome = "dark_prismarine";
                p.towerTop = "ONION";
                p.banners = new String[]{"cyan", "light_blue"};
                p.clothBg = "cyan";
                p.clothTrim = "light_blue";
                p.light = "soul_lantern";
                p.leaves = "azalea";
                p.flowers = new String[]{"sea_pickle[pickles=3,waterlogged=false]", "blue_orchid"};
                p.glass = "light_blue_stained_glass_pane";
                p.land = ArenaLandscape.Kind.TROPICAL;
            }
            case STARFALL -> {
                natural(p, "end_stone_bricks", "end_stone");
                p.log = "purpur_pillar";
                p.planks = "purpur_block";
                p.fence = "end_rod";
                court(p, "end_stone_bricks", "purpur_block", "end_stone", "magenta_stained_glass", "purpur_block");
                roof(p, "purpur", "purpur_block", "end_stone_bricks");
                p.capital = "purpur_pillar[axis=y]";
                p.banners = new String[]{"magenta", "black"};
                p.clothBg = "magenta";
                p.clothTrim = "black";
                p.light = "soul_lantern";
                p.leaves = "azalea";
                p.flowers = new String[]{"chorus_flower"};
                p.glass = "magenta_stained_glass_pane";
                p.land = ArenaLandscape.Kind.END;
            }
            case PHARAOH -> {
                tonal(p, new String[]{"smooth_sandstone", "cut_sandstone", "sandstone", "chiseled_sandstone"}, 0.45, 0.25, 0.2, 0.1);
                p.log = "stripped_birch_log";
                p.planks = "birch_planks";
                p.fence = "birch_fence";
                court(p, "cut_sandstone", "smooth_sandstone", "sandstone", "lapis_block", "chiseled_sandstone");
                roof(p, "smooth_sandstone", "smooth_sandstone", "cut_sandstone");
                p.banners = new String[]{"blue", "yellow"};
                p.clothBg = "blue";
                p.clothFg = "yellow";
                p.clothTrim = "yellow";
                p.leaves = "jungle";
                p.towerTop = "PYRAMID";
                p.land = ArenaLandscape.Kind.DESERT;
            }
        }

        if (!t.stadium && !t.walled()) {
            layout(p, t);
        }

        if (p.dome == null) {
            p.dome = p.roofBlock;
        }

        if (p.band == null) {
            p.band = p.trim;
        }

        if (p.window == null) {
            p.window = p.panel;
        }

        if (p.walkway == null) {
            p.walkway = t.stadium ? p.trim : p.planks;
        }

        return p;
    }

    /** A natural floor: the main block with patches and specks of the others. */
    /** How a court is composed: its main building, what faces it, its trees and its gardens' ground. */
    private static void layout(Pal p, Theme t) {
        switch (t) {
            case FROST -> {
                p.towerTop = "SPIRE";
                p.tree = "SNOWY";
                p.plaza = new String[]{"snow_block", "white_concrete_powder", "packed_ice"};
                p.paving = "polished_diorite";
                p.hedge = "spruce";
            }
            case DESERT -> {
                p.court = "PALACE";
                p.south = "LOGGIA";
                p.tree = "PALM";
                p.plaza = new String[]{"sand", "smooth_sandstone", "sandstone"};
                p.paving = "smooth_sandstone";
                p.hedge = "jungle";
            }
            case HIGHLAND -> {
                p.rock = true;
                p.tree = "SPRUCE";
                p.plaza = new String[]{"grass_block", "coarse_dirt", "podzol"};
                p.paving = "cobblestone";
                p.hedge = "spruce";
            }
            case SAKURA -> {
                p.court = "PAGODA";
                p.south = "PAVILION";
                p.tree = "CHERRY";
                p.plaza = new String[]{"grass_block", "moss_block", "pink_petals"};
                p.paving = "polished_diorite";
                p.hedge = "flowering_azalea";
            }
            case EMBER -> {
                p.court = "PALACE";
                p.south = "LOGGIA";
                p.tree = "DEAD";
                p.plaza = new String[]{"red_sand", "terracotta", "red_sandstone"};
                p.paving = "smooth_red_sandstone";
                p.hedge = null;
            }
            case GOTHIC -> {
                p.court = "CATHEDRAL";
                p.tree = "DARK";
                p.plaza = new String[]{"grass_block", "podzol", "coarse_dirt"};
                p.paving = "polished_deepslate";
                p.hedge = "dark_oak";
            }
            case TROPICAL -> {
                p.court = "PAGODA";
                p.south = "PAVILION";
                p.tree = "PALM";
                p.plaza = new String[]{"sand", "grass_block", "smooth_sandstone"};
                p.paving = "smooth_sandstone";
                p.hedge = "jungle";
            }
            case JUNGLE -> {
                p.court = "ZIGGURAT";
                p.south = "PAVILION";
                p.tree = "JUNGLE";
                p.plaza = new String[]{"grass_block", "moss_block", "podzol"};
                p.paving = "mossy_stone_bricks";
                p.hedge = "jungle";
            }
            case VOLCANO -> {
                p.court = "CATHEDRAL";
                p.tree = "DEAD";
                p.plaza = new String[]{"blackstone", "smooth_basalt", "tuff"};
                p.paving = "polished_blackstone_bricks";
                p.hedge = null;
                p.window = "red_stained_glass";
            }
            case HARBOR -> {
                p.lighthouse = true;
                p.plaza = new String[]{"grass_block", "sand", "gravel"};
                p.paving = "spruce_planks";
            }
            case PHARAOH -> {
                p.court = "PYRAMID";
                p.south = "LOGGIA";
                p.tree = "PALM";
                p.plaza = new String[]{"sand", "sandstone", "smooth_sandstone"};
                p.paving = "cut_sandstone";
                p.hedge = null;
            }
            default -> {
            }
        }
    }

    private static void natural(Pal p, String... blocks) {
        p.floorMix = blocks;
        p.floorW = null;
    }

    /** A tonal floor: shades of one colour mixed block by block in these shares. */
    private static void tonal(Pal p, String[] blocks, double... shares) {
        p.floorMix = blocks;
        p.floorW = shares;
    }

    private static void stadium(Pal p, String wall, String alt, String alt2, String pillar, String capital, String base) {
        p.wall = wall;
        p.wallAlt = alt;
        p.wallAlt2 = alt2;
        p.pillar = pillar;
        p.capital = capital;
        p.base = base;
    }

    /** A court's walls: stone with wooden pilasters, the light panels' block and the arcade's block. */
    private static void court(Pal p, String wall, String alt, String alt2, String window, String arch) {
        p.wall = wall;
        p.wallAlt = alt;
        p.wallAlt2 = alt2;
        p.window = window;
        p.arch = arch;
        p.base = alt2;
        p.capital = wall.contains("sandstone") ? "chiseled_sandstone"
            : (wall.contains("deepslate") ? "chiseled_deepslate" : (wall.contains("blackstone") ? "chiseled_polished_blackstone" : "chiseled_stone_bricks"));
        p.trim = alt;
        p.panel = window.contains("glass") ? alt : window;
        p.panelAlt = alt2;
    }

    private static void roof(Pal p, String stair, String block, String alt) {
        p.roofStair = stair;
        p.roofBlock = block;
        p.roofAlt = alt;
    }

    private static void seats(Pal p, String seat, String seat2, String stand, String walkway, String aisle) {
        p.seat = seat;
        p.seat2 = seat2;
        p.stand = stand;
        p.walkway = walkway;
        p.aisle = aisle;
    }
}
