package net.practicepvp.build;

/**
 * The masterpiece arenas (1.0.15): four hand-planned maps, each one a place rather than a floor in a ring.
 *
 * <ul>
 * <li><b>Dragonspire Keep</b> - the fight is in the inner bailey of a real castle: curtain walls with a wall-walk,
 * machicolations and battlements, round corner towers with cone roofs, timber lean-to houses along the walls, a
 * twin-towered gatehouse with a portcullis over a moat and drawbridge, and a five-storey buttressed keep with a slate
 * roof and a great black dragon perched on its ridge.</li>
 * <li><b>Koi Shrine Summit</b> - a raked-gravel terrace on a mountain top: a five-tier pagoda on a podium against a
 * cliff with a waterfall, a koi pond with stepping stones and a red arched bridge, a shrine hall in a bamboo grove,
 * stone lanterns, sculpted cherry trees and cloud pines, and a path of torii going down the mountain.</li>
 * <li><b>Corsair Harbor</b> - a dock plaza: a three-masted galleon moored along it, a row of jettied timber houses on
 * the quay behind (each one different), a striped lighthouse on its rock, a treadwheel crane with cargo.</li>
 * <li><b>Stormpeak Sanctuary</b> - a slate plateau high in the mountains: a monastery stepping up the rock with white
 * battered walls and red bands, a bell tower with a copper roof, prayer flags, a rope bridge to a spire with a stupa and
 * a waterfall, rock spires in layered stone, and clouds below.</li>
 * </ul>
 *
 * <p>Every floor is flat and fair (the long axis is x, spawn 0 west, spawn 1 east); an invisible wall rises over the
 * floor's edge. Local coordinates: floor surface y = 0, players stand on y = 1.
 */
public final class MasterArenaDesign extends CourtParts implements ArenaVoxels {
    public enum Style {
        DRAGONSPIRE("Dragonspire Keep"),
        KOI_SHRINE("Koi Shrine Summit"),
        CORSAIR_HARBOR("Corsair Harbor"),
        STORMPEAK("Stormpeak Sanctuary");

        public final String title;

        Style(String title) {
            this.title = title;
        }
    }

    public static final int RADIUS = 92;
    public static final int DEPTH = 12;
    static final int TOP = 92;
    private final Style style;
    private final long seed;
    /** Floor half sizes and corner radius. */
    final int hx = 32;
    final int hz = 21;
    final int rc = 7;

    public MasterArenaDesign(Style style, int size, long seed, int cx, int floorY, int cz, boolean ffa) {
        super(cx, floorY, cz, RADIUS, -DEPTH, TOP, seed);
        this.style = style;
        this.seed = seed;
        switch (style) {
            case DRAGONSPIRE -> this.dragonspire();
            case KOI_SHRINE -> this.koiShrine();
            case CORSAIR_HARBOR -> this.corsair();
            case STORMPEAK -> this.stormpeak();
        }

        this.barrierRing();
        this.finish();
    }

    @Override
    public String name() {
        return this.style.title;
    }

    @Override
    public int[] spawn(int side) {
        int sp = this.hx - 8;
        return new int[]{side == 0 ? -sp : sp, 1, 0};
    }

    @Override
    public int[] ffaSpot(int i, int n) {
        double t = Math.PI * 2.0 * i / n;
        return new int[]{(int)Math.round(Math.cos(t) * this.hx * 0.66), 1, (int)Math.round(Math.sin(t) * this.hz * 0.66)};
    }

    /** Signed distance from the floor's edge (negative on the floor): a rounded rectangle. */
    public double sd(double x, double z) {
        double qx = Math.abs(x) - (this.hx - this.rc);
        double qz = Math.abs(z) - (this.hz - this.rc);
        double ox = Math.max(qx, 0.0);
        double oz = Math.max(qz, 0.0);
        return Math.sqrt(ox * ox + oz * oz) + Math.min(Math.max(qx, qz), 0.0) - this.rc;
    }

    /** Signed distance of the static floor (for the generator's "is this the floor" check). */
    public static double floorDistance(double x, double z) {
        double qx = Math.abs(x) - 25;
        double qz = Math.abs(z) - 14;
        double ox = Math.max(qx, 0.0);
        double oz = Math.max(qz, 0.0);
        return Math.sqrt(ox * ox + oz * oz) + Math.min(Math.max(qx, qz), 0.0) - 7;
    }

    private void barrierRing() {
        for (int x = -this.hx - 3; x <= this.hx + 3; x++) {
            for (int z = -this.hz - 3; z <= this.hz + 3; z++) {
                double d = this.sd(x, z);
                if (d >= 0.0 && d < 1.0) {
                    for (int y = 1; y <= 48; y++) {
                        if (this.empty(x, y, z)) {
                            this.set(x, y, z, "barrier");
                        }
                    }
                }
            }
        }
    }

    // ================================================================== shared pieces

    private double n(long salt, double x, double z) {
        return noise(this.seed + salt, x, z);
    }

    private double h(long salt, int x, int y, int z) {
        return hash3(this.seed + salt, x, y, z);
    }

    /** Fills a column from the bottom of the design to {@code top} (inclusive), the top block on top. */
    private void column(int x, int z, int top, String topBlock, String under, Mat rock) {
        for (int y = this.yLo; y <= top; y++) {
            String b = y == top ? topBlock : (y >= top - 3 ? under : rock.at(x, y, z));
            this.set(x, y, z, b);
        }
    }

    /** The flat fighting floor: {@code m} on top, solid ground under it. */
    private void floor(Mat m, String under) {
        for (int x = -this.hx - 1; x <= this.hx + 1; x++) {
            for (int z = -this.hz - 1; z <= this.hz + 1; z++) {
                if (this.sd(x, z) < 0.5) {
                    for (int y = this.yLo; y < 0; y++) {
                        this.set(x, y, z, under);
                    }

                    this.set(x, 0, z, m.at(x, 0, z));
                    for (int y = 1; y < 8; y++) {
                        this.set(x, y, z, null);
                    }
                }
            }
        }
    }

    /** A gable roof over x0..x1 / z0..z1 from y0, ridge along x (or z), {@code over} blocks of eave overhang. */
    private int gable(int x0, int x1, int z0, int z1, int y0, String stairMat, String body, boolean alongX, int over) {
        int a0 = alongX ? z0 - over : x0 - over;
        int a1 = alongX ? z1 + over : x1 + over;
        int b0 = alongX ? x0 - over : z0 - over;
        int b1 = alongX ? x1 + over : z1 + over;
        int k = 0;
        int y = y0;
        while (a0 + k <= a1 - k) {
            for (int b = b0; b <= b1; b++) {
                for (int a = a0 + k; a <= a1 - k; a++) {
                    boolean lo = a == a0 + k;
                    boolean hi = a == a1 - k;
                    String blk;
                    if (lo && hi) {
                        blk = slab(stairMat, false);
                    } else if (lo) {
                        blk = stairs(stairMat, alongX ? "south" : "east", false);
                    } else if (hi) {
                        blk = stairs(stairMat, alongX ? "north" : "west", false);
                    } else if (b == b0 || b == b1) {
                        blk = body;
                    } else {
                        continue;
                    }

                    if (alongX) {
                        this.set(b, y, a, blk);
                    } else {
                        this.set(a, y, b, blk);
                    }
                }
            }

            // the gable ends are walled up under the slopes
            k++;
            y++;
        }

        return y;
    }

    /** A small boulder of mixed stone. */
    private void boulder(int x, int y, int z, double r, String... mats) {
        int n = (int)Math.ceil(r);
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -1; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    if (Math.sqrt(dx * dx + dy * dy * 1.5 + dz * dz) <= r - this.h(71L, x + dx, y + dy, z + dz) * 0.7) {
                        this.set(x + dx, y + dy, z + dz, mats[(int)(this.h(73L, x + dx, y + dy, z + dz) * mats.length)]);
                    }
                }
            }
        }
    }

    /** A stone lantern (toro): a base, a post, a lit box with a slab roof. */
    private void stoneLantern(int x, int y, int z) {
        this.set(x, y, z, "stone_bricks");
        this.set(x, y + 1, z, "stone_brick_wall");
        this.set(x, y + 2, z, "lantern[hanging=false]");
        this.set(x, y + 3, z, slab("stone_brick", false));
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            this.setIfEmpty(x + d[0], y + 3, z + d[1], stairs("stone_brick", d[0] > 0 ? "west" : d[0] < 0 ? "east" : d[1] > 0 ? "north" : "south", false));
        }
    }

    /** A broadleaf tree: a trunk that leans, three or four boughs, and a crown of uneven leaf clusters. */
    private void broadTree(int x, int y, int z, int height, String log, String leaf, String leaf2) {
        double lean = this.h(901L, x, y, z) * Math.PI * 2.0;
        double lx = Math.cos(lean) * 0.12;
        double lz = Math.sin(lean) * 0.12;
        double tx = x;
        double tz = z;
        for (int i = 0; i < height; i++) {
            tx += lx;
            tz += lz;
            this.set((int)Math.round(tx), y + i, (int)Math.round(tz), log + "[axis=y]");
            if (i == 0 && height >= 8) {
                this.set((int)Math.round(tx) + 1, y, (int)Math.round(tz), log + "[axis=y]");
                this.set((int)Math.round(tx), y, (int)Math.round(tz) + 1, log + "[axis=y]");
            }
        }

        int top = y + height;
        int boughs = 3 + (int)(this.h(903L, x, y, z) * 2.0);
        for (int b = 0; b < boughs; b++) {
            double a = (b + this.h(905L, x, b, z) * 0.6) * Math.PI * 2.0 / boughs;
            double len = height * (0.35 + this.h(907L, x, b, z) * 0.2);
            int by = top - 2 - (int)(this.h(909L, x, b, z) * 3.0);
            double ex = tx + Math.cos(a) * len;
            double ez = tz + Math.sin(a) * len;
            double ey = by + len * 0.45;
            char axis = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? 'x' : 'z';
            this.line(tx, by, tz, ex, ey, ez, log + "[axis=" + axis + "]");
            this.cluster(ex, ey + 1.0, ez, 2.6 + len * 0.25, leaf, leaf2);
        }

        this.cluster(tx, top + 1.5, tz, 3.2 + height * 0.18, leaf, leaf2);
    }

    /** A soft leaf cluster: a squashed ball with a ragged edge, a second leaf mixed in. */
    private void cluster(double ox, double oy, double oz, double r, String leaf, String leaf2) {
        int n = (int)Math.ceil(r) + 1;
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    int x = (int)Math.round(ox) + dx;
                    int y = (int)Math.round(oy) + dy;
                    int z = (int)Math.round(oz) + dz;
                    double d = Math.sqrt(dx * dx + dy * dy * 2.2 + dz * dz);
                    if (d <= r - this.h(911L, x, y, z) * 1.1) {
                        String l = leaf2 != null && this.h(913L, x, y, z) < 0.22 ? leaf2 : leaf;
                        this.setIfEmpty(x, y, z, leaves(l));
                    }
                }
            }
        }
    }

    /** A spruce with uneven tiers (and snow on its branches when snowy). */
    private void pine(int x, int y, int z, int height, boolean snowy) {
        for (int i = 0; i < height; i++) {
            this.set(x, y + i, z, "spruce_log[axis=y]");
        }

        for (int i = 3; i <= height + 1; i++) {
            double t = (double)(i - 3) / Math.max(1, height - 2);
            double r = (1.0 - t) * (2.2 + height * 0.16);
            if (i % 3 == 1) {
                r *= 0.6;
            }

            int ry = (int)Math.ceil(r);
            for (int dx = -ry; dx <= ry; dx++) {
                for (int dz = -ry; dz <= ry; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= r + 0.3 - this.h(921L, x + dx, y + i, z + dz) * 0.6 && (dx != 0 || dz != 0)) {
                        this.setIfEmpty(x + dx, y + i, z + dz, leaves("spruce"));
                        if (snowy && this.empty(x + dx, y + i + 1, z + dz) && this.h(923L, x + dx, y + i, z + dz) < 0.6) {
                            this.set(x + dx, y + i + 1, z + dz, "snow[layers=2]");
                        }
                    }
                }
            }
        }

        this.set(x, y + height + 1, z, leaves("spruce"));
        this.set(x, y + height + 2, z, snowy ? "snow[layers=3]" : leaves("spruce"));
    }

    private void banner(int x, int y, int z, String color, String facing) {
        this.set(x, y, z, color + "_wall_banner[facing=" + facing + "]");
    }

    // ================================================================== DRAGONSPIRE KEEP

    /** Inner faces of the curtain walls (x and z), their thickness and height. */
    private static final int DK_WX = 41;
    private static final int DK_WZ = 30;
    private static final int DK_T = 5;
    private static final int DK_H = 16;

    private void dragonspire() {
        Mat ground = this.mixOf(this.seed + 11L, "stone_bricks", "polished_andesite", 0.18, "cracked_stone_bricks", 0.06, "andesite", 0.08, "cobblestone", 0.05);
        // the bailey floor: stone flags with a border band and a compass in the middle (all flat)
        this.floor((x, y, z) -> {
            double d = this.sd(x, z);
            if (d > -1.6) {
                return "polished_deepslate";
            } else if (d > -3.0) {
                return "polished_andesite";
            }

            double r = Math.hypot(x, z);
            if (r < 7.5 && (Math.abs(x) <= 0 || Math.abs(z) <= 0 || Math.abs(Math.abs(x) - Math.abs(z)) == 0 && r < 5.0)) {
                return r < 1.5 ? "chiseled_stone_bricks" : "polished_deepslate";
            } else if (r > 7.2 && r < 8.4) {
                return "polished_deepslate";
            }

            if (Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0) {
                return this.h(15L, x, 0, z) < 0.3 ? "cobblestone" : "andesite";
            }

            return ground.at(x, y, z);
        }, "stone");
        this.dkGround();
        this.dkBailey();
        this.dkWalls();
        this.dkTowers();
        this.dkGatehouse();
        this.dkLeanTos();
        this.dkKeep();
        this.dkDragon();
        this.dkCountry();
    }

    /** Distance outside the curtain walls' outer face (negative inside). */
    private double dkOut(double x, double z) {
        double ox = Math.abs(x) - (DK_WX + DK_T);
        double oz = Math.abs(z) - (DK_WZ + DK_T);
        return Math.sqrt(Math.max(ox, 0) * Math.max(ox, 0) + Math.max(oz, 0) * Math.max(oz, 0)) + Math.min(Math.max(ox, oz), 0.0);
    }

    /** Ground everywhere: the bailey inside, the moat round the walls, then rolling hills to the island's edge. */
    private void dkGround() {
        Mat rock = this.mixOf(this.seed + 13L, "stone", "andesite", 0.25, "tuff", 0.1, "cobblestone", 0.05);
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                if (this.sd(x, z) < 0.5) {
                    continue;
                }

                double r = Math.hypot(x, z);
                double edge = this.R - 3 - this.n(17L, x * 0.06, z * 0.06) * 8.0;
                if (r > edge) {
                    continue;
                }

                double out = this.dkOut(x, z);
                if (out < 0) {
                    // the bailey: packed earth and cobbles with grass at the edges
                    double g = this.n(19L, x * 0.15, z * 0.15);
                    String top = g > 0.66 ? "grass_block" : (g > 0.5 ? "coarse_dirt" : (this.h(21L, x, 0, z) < 0.3 ? "cobblestone" : "dirt_path"));
                    this.column(x, z, 0, top, "dirt", rock);
                } else if (out < 2.5) {
                    this.column(x, z, 0, "grass_block", "dirt", rock);
                } else if (out < 10.0) {
                    // the moat: water three deep, muddy bottom, reeds on the banks
                    int bottom = out < 3.5 || out > 9.0 ? -2 : -4;
                    this.column(x, z, bottom, "mud", "mud", rock);
                    for (int y = bottom + 1; y <= -1; y++) {
                        this.set(x, y, z, "water");
                    }

                } else {
                    // hills rising toward the edge, falling off as an island rim
                    double rise = Math.min(1.0, (out - 10.0) / 40.0);
                    int top = (int)Math.round(rise * rise * 14.0 + this.n(25L, x * 0.05, z * 0.05) * 9.0 * rise + this.n(27L, x * 0.13, z * 0.13) * 2.0);
                    double rim = edge - r;
                    if (rim < 6) {
                        top = Math.min(top, (int)(rim * 1.5) - 1);
                    }

                    this.column(x, z, top, "grass_block", "dirt", rock);
                    if (rim < 6) {
                        // the eroded underside of the island
                        int cut = this.yLo + (int)((6 - rim) * 2.0);
                        for (int y = this.yLo; y < cut; y++) {
                            this.set(x, y, z, null);
                        }
                    }
                }
            }
        }
    }

    /** Market life in the bailey (never on the floor): stalls, a well, carts, hay, a smithy corner, training posts. */
    private void dkBailey() {
        // stalls along the south side, both halves
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 3; k++) {
                int x = s * (12 + k * 8);
                this.stall(x, this.hz + 4, k % 2 == 0 ? "red" : "blue", "south");
            }
        }

        // a well in each corner of the bailey
        for (int s = -1; s <= 1; s += 2) {
            this.well(s * (this.hx + 2), -(this.hz + 4));
        }

        // hay, barrels and crates against the walls
        for (int x = -DK_WX + 3; x <= DK_WX - 3; x += 1) {
            double r = this.h(31L, x, 0, 0);
            if (r < 0.12) {
                this.set(x, 1, DK_WZ - 1, "hay_block[axis=y]");
            } else if (r < 0.2) {
                this.set(x, 1, DK_WZ - 1, "barrel[facing=up]");
            }
        }

        // training dummies by the west houses
        for (int z = -8; z <= 8; z += 8) {
            int x = -(this.hx + 4);
            if (this.sd(x, z) > 2.0) {
                this.set(x, 1, z, "spruce_fence");
                this.set(x, 2, z, "hay_block[axis=y]");
                this.set(x, 3, z, "carved_pumpkin[facing=east]");
            }
        }
    }

    private void stall(int x, int z, String color, String facing) {
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = 0; dz <= 2; dz += 2) {
                for (int y = 1; y <= 3; y++) {
                    this.set(x + dx, y, z + dz, "spruce_fence");
                }
            }
        }

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                boolean stripe = Math.floorMod(dx, 2) == 0;
                this.set(x + dx, 4, z + dz, (stripe ? color : "white") + "_wool");
            }
        }

        this.set(x - 1, 1, z + 1, "barrel[facing=up]");
        this.set(x, 1, z + 1, "spruce_slab[type=top]");
        this.set(x + 1, 1, z + 1, "crafting_table");
        this.set(x, 2, z + 1, "melon");
    }

    private void well(int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (rim) {
                    this.set(x + dx, 1, z + dz, "cobblestone_wall");
                    this.set(x + dx, 0, z + dz, "cobblestone");
                } else {
                    this.set(x + dx, 0, z + dz, "water");
                    this.set(x + dx, -1, z + dz, "water");
                }
            }
        }

        for (int y = 2; y <= 4; y++) {
            this.set(x - 2, y, z, "spruce_fence");
            this.set(x + 2, y, z, "spruce_fence");
        }

        this.line(x - 2, 4, z, x + 2, 4, z, "spruce_log[axis=x]");
        this.set(x, 3, z, "iron_chain[axis=y]");
        this.set(x, 2, z, "lantern[hanging=true]");
        this.gable(x - 2, x + 2, z - 1, z + 1, 5, "spruce", "spruce_planks", true, 1);
    }

    /** The curtain walls: a battered plinth, buttresses, a wall-walk with battlements and machicolations outside. */
    private void dkWalls() {
        Mat outer = this.mixOf(this.seed + 41L, "stone_bricks", "cracked_stone_bricks", 0.12, "andesite", 0.08, "cobblestone", 0.05, "tuff_bricks", 0.06);
        Mat mossy = this.mixOf(this.seed + 43L, "mossy_stone_bricks", "stone_bricks", 0.4, "mossy_cobblestone", 0.2);
        for (int x = -(DK_WX + DK_T); x <= DK_WX + DK_T; x++) {
            for (int z = -(DK_WZ + DK_T); z <= DK_WZ + DK_T; z++) {
                boolean inX = Math.abs(x) >= DK_WX;
                boolean inZ = Math.abs(z) >= DK_WZ;
                if (!inX && !inZ) {
                    continue;
                }

                // the keep replaces the middle of the north wall
                if (z < 0 && Math.abs(x) <= 22 && !inX) {
                    continue;
                }

                boolean face = Math.abs(x) == DK_WX + DK_T - 1 || Math.abs(z) == DK_WZ + DK_T - 1 || Math.abs(x) == DK_WX + DK_T || Math.abs(z) == DK_WZ + DK_T;
                for (int y = this.yLo; y <= DK_H; y++) {
                    Mat m = y <= 3 && face ? mossy : outer;
                    this.set(x, y, z, m.at(x, y, z));
                }

                // battered plinth (a sloping skirt outside)
                if (Math.abs(x) == DK_WX + DK_T || Math.abs(z) == DK_WZ + DK_T) {
                    int ox = x == 0 ? 0 : Integer.signum(x) * (Math.abs(x) == DK_WX + DK_T ? 1 : 0);
                    int oz = z == 0 ? 0 : Integer.signum(z) * (Math.abs(z) == DK_WZ + DK_T ? 1 : 0);
                    this.set(x + ox, 1, z + oz, stairs("stone_brick", dir(-ox, -oz), false));
                    this.set(x + ox, 0, z + oz, "mossy_stone_bricks");
                }
            }
        }

        // the four wall runs as faces (outside looking in): parapets, crenels, machicolations, buttresses, banners
        int[][] runs = {
            {0, -(DK_WZ + DK_T), 0, 1, DK_WX + DK_T},   // north (outer face, pointing in = +z)
            {0, DK_WZ + DK_T, 0, -1, DK_WX + DK_T},     // south
            {-(DK_WX + DK_T), 0, 1, 0, DK_WZ + DK_T},   // west
            {DK_WX + DK_T, 0, -1, 0, DK_WZ + DK_T}      // east
        };
        for (int[] r : runs) {
            Face f = new Face(r[0], r[1], r[2], r[3]);
            int half = r[4];
            for (int u = -half; u <= half; u++) {
                int wx = f.x(u, 0);
                int wz = f.z(u, 0);
                if (r[1] < 0 && Math.abs(wx) <= 22 && Math.abs(wz) >= DK_WZ) {
                    continue;
                }

                // machicolation corbels and a parapet one block proud of the wall
                f.set(u, DK_H - 1, -1, stairs("stone_brick", f.back(), true));
                f.set(u, DK_H, -1, "stone_bricks");
                f.set(u, DK_H + 1, -1, Math.floorMod(u, 3) == 0 ? null : "stone_bricks");
                f.set(u, DK_H + 2, -1, Math.floorMod(u, 3) == 0 ? null : (Math.floorMod(u, 3) == 1 ? "stone_brick_wall" : "stone_bricks"));
                // inner railing of the wall-walk
                f.set(u, DK_H + 1, DK_T, "stone_brick_wall");
                // buttresses every 10 blocks, stepping out at the bottom
                if (Math.floorMod(u, 10) == 5 && Math.abs(u) < half - 6) {
                    for (int y = this.yLo; y <= DK_H - 3; y++) {
                        f.set(u, y, -1, outer.at(f.x(u, -1), y, f.z(u, -1)));
                        if (y <= 6) {
                            f.set(u, y, -2, outer.at(f.x(u, -2), y, f.z(u, -2)));
                        }
                    }

                    f.set(u, DK_H - 2, -1, stairs("stone_brick", f.back(), false));
                    f.set(u, 7, -2, stairs("stone_brick", f.back(), false));
                }

                // arrow slits between the buttresses
                if (Math.floorMod(u, 10) == 0 && Math.abs(u) < half - 6) {
                    f.set(u, 9, 0, null);
                    f.set(u, 10, 0, null);
                    f.set(u, 9, 1, "polished_blackstone");
                    f.set(u, 10, 1, "polished_blackstone");
                }

                // banners hanging over the battlements
                if (Math.floorMod(u, 20) == 10 && Math.abs(u) < half - 8) {
                    for (int y = DK_H - 3; y <= DK_H - 1; y++) {
                        f.set(u, y, -2, null);
                    }

                    this.banner(f.x(u, -2), DK_H - 2, f.z(u, -2), "red", f.front());
                }
            }
        }
    }

    /** Round corner towers with cone roofs. */
    private void dkTowers() {
        Mat wall = this.mixOf(this.seed + 51L, "stone_bricks", "cracked_stone_bricks", 0.1, "andesite", 0.08, "mossy_stone_bricks", 0.06);
        Mat roof = this.mixOf(this.seed + 53L, "deepslate_tiles", "cobbled_deepslate", 0.2, "polished_deepslate", 0.15);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int tx = sx * (DK_WX + DK_T - 1);
                int tz = sz * (DK_WZ + DK_T - 1);
                double r = 7.5;
                int top = 30 + (sx * sz > 0 ? 3 : 0);
                this.cyl(tx, tz, r + 1.0, this.yLo, 2, of("mossy_stone_bricks"));
                this.cyl(tx, tz, r, 3, top, wall);
                this.cyl(tx, tz, r - 2.0, 1, top - 1, of("cave_air"));
                // a corbelled crown and battlements
                this.ringAt(tx, tz, r - 0.2, r + 1.2, top + 1, of("stone_bricks"));
                int m = 28;
                for (int k = 0; k < m; k++) {
                    double a = k * Math.PI * 2.0 / m;
                    int x = tx + (int)Math.round(Math.cos(a) * (r + 1.0));
                    int z = tz + (int)Math.round(Math.sin(a) * (r + 1.0));
                    this.set(x, top, z, stairs("stone_brick", dir(tx - x, tz - z), true));
                    if (k % 2 == 0) {
                        this.set(x, top + 2, z, "stone_bricks");
                    }
                }

                // slits round the tower
                for (int y = 8; y < top - 3; y += 7) {
                    for (int k = 0; k < 6; k++) {
                        double a = k * Math.PI / 3.0 + y * 0.3;
                        int x = tx + (int)Math.round(Math.cos(a) * r);
                        int z = tz + (int)Math.round(Math.sin(a) * r);
                        this.set(x, y, z, "polished_blackstone");
                        this.set(x, y + 1, z, "polished_blackstone");
                    }
                }

                // a cone roof inside the battlements, flared at the eave, with a finial and a pennant
                this.coneRoof(tx, tz, r - 0.4, top + 2, 15, roof);
                this.ringAt(tx, tz, r - 0.6, r + 0.4, top + 2, (x, y, z) -> stairs("deepslate_tile", dir(tx - x, tz - z), false));
                int peak = top + 18;
                for (int y = peak; y <= peak + 3; y++) {
                    this.set(tx, y, tz, "iron_chain[axis=y]");
                }

                this.set(tx, peak + 4, tz, "lightning_rod");
                this.set(tx + 1, peak + 2, tz, "red_wool");
                this.set(tx + 2, peak + 2, tz, "red_wool");
                this.set(tx + 1, peak + 3, tz, "red_wool");
            }
        }
    }

    /** The gatehouse in the south wall: two D-shaped towers, a deep arched gate with a portcullis, a drawbridge. */
    private void dkGatehouse() {
        Mat wall = this.mixOf(this.seed + 61L, "stone_bricks", "cracked_stone_bricks", 0.1, "tuff_bricks", 0.08, "andesite", 0.06);
        int zf = DK_WZ + DK_T;
        for (int s = -1; s <= 1; s += 2) {
            int tx = s * 8;
            this.cyl(tx, zf + 1, 5.5, this.yLo, 24, wall);
            this.cyl(tx, zf + 1, 3.5, 1, 23, of("cave_air"));
            this.ringAt(tx, zf + 1, 5.3, 6.6, 25, of("stone_bricks"));
            for (int k = 0; k < 22; k += 2) {
                double a = k * Math.PI * 2.0 / 22;
                this.set(tx + (int)Math.round(Math.cos(a) * 6.2), 26, zf + 1 + (int)Math.round(Math.sin(a) * 6.2), "stone_bricks");
            }

            this.coneRoof(tx, zf + 1, 5.6, 26, 11, of("deepslate_tiles"));
            this.set(tx, 38, zf + 1, "lightning_rod");
        }

        // the gate block between the towers, two storeys above the arch
        this.fill(-4, this.yLo, zf - DK_T - 1, 4, 22, zf + 3, wall);
        Face f = new Face(0, zf + 3, 0, -1);
        this.arch(f, 0, 1, 7, 10, false, DK_T + 6, null, "polished_andesite");
        // portcullis half raised, timber gates open to the inside
        for (int u = -3; u <= 3; u++) {
            for (int y = 6; y <= 10; y++) {
                if (inArch(u, y, 0, 3, 1, 10, false)) {
                    f.set(u, y, 1, "iron_bars");
                }
            }
        }

        // a hall over the gate: windows and a coat of arms
        this.window(f, -2, 13, 1, 3, true, "glass_pane", "dark_oak_planks", "polished_andesite", "stone_brick", 1);
        this.window(f, 2, 13, 1, 3, true, "glass_pane", "dark_oak_planks", "polished_andesite", "stone_brick", 1);
        this.cloth(f, 0, 21, 3, 6, "red_wool", "yellow_wool", "black_wool");
        this.merlons(f, -4, 4, 23, of("stone_bricks"), 0);
        // the drawbridge over the moat, on chains
        for (int z = zf + 3; z <= zf + 13; z++) {
            for (int x = -3; x <= 3; x++) {
                this.set(x, 0, z, Math.abs(x) == 3 ? "spruce_log[axis=z]" : "spruce_planks");
            }
        }

        for (int s = -1; s <= 1; s += 2) {
            this.line(s * 3, 12, zf + 3, s * 3, 1, zf + 12, "iron_chain[axis=y]");
        }
    }

    /** Timber houses leaning on the east and west walls: stone ground floors, jettied plaster upper floors, roofs. */
    private void dkLeanTos() {
        String[] plaster = {"white_terracotta", "calcite", "smooth_sandstone", "light_gray_terracotta"};
        for (int s = -1; s <= 1; s += 2) {
            int wallX = s * DK_WX;
            int z = -DK_WZ + 3;
            int i = 0;
            while (z < DK_WZ - 4) {
                int w = 7 + (int)(this.h(71L, s, i, 0) * 4.0);
                int z1 = Math.min(DK_WZ - 3, z + w - 1);
                int depth = 6;
                int x0 = wallX - s * depth;
                int storeys = 2 + (this.h(73L, s, i, 1) < 0.4 ? 1 : 0);
                String pl = plaster[Math.floorMod(i + (s > 0 ? 2 : 0), plaster.length)];
                int y = 1;
                for (int st = 0; st < storeys; st++) {
                    int jet = st == 0 ? 0 : 1;
                    int fx = x0 - s * jet;
                    int h = 4;
                    for (int yy = y; yy < y + h; yy++) {
                        for (int zz = z; zz <= z1; zz++) {
                            for (int xx = Math.min(fx, wallX); xx <= Math.max(fx, wallX); xx++) {
                                boolean front = xx == fx;
                                boolean post = zz == z || zz == z1 || Math.floorMod(zz - z, 3) == 0 && front;
                                String b;
                                if (st == 0) {
                                    b = front ? (post ? "stone_bricks" : "cobblestone") : "stone_bricks";
                                } else {
                                    b = front && post ? "stripped_dark_oak_log[axis=y]" : (front && yy == y ? "dark_oak_planks" : pl);
                                }

                                if (front || zz == z || zz == z1) {
                                    this.set(xx, yy, zz, b);
                                } else {
                                    this.set(xx, yy, zz, "cave_air");
                                }
                            }
                        }
                    }

                    // windows (and a door on the ground floor)
                    for (int zz = z + 2; zz < z1 - 1; zz += 3) {
                        if (st == 0 && zz == z + 2) {
                            this.set(fx, y, zz, "spruce_door[facing=" + (s > 0 ? "west" : "east") + ",half=lower]");
                            this.set(fx, y + 1, zz, "spruce_door[facing=" + (s > 0 ? "west" : "east") + ",half=upper]");
                        } else {
                            this.set(fx, y + 1, zz, "glass_pane");
                            this.set(fx, y + 2, zz, "glass_pane");
                            this.set(fx - s, y + 1, zz, "spruce_trapdoor[facing=" + (s > 0 ? "west" : "east") + ",open=true]");
                        }
                    }

                    // the jetty beam under an upper floor
                    if (jet > 0) {
                        for (int zz = z; zz <= z1; zz++) {
                            this.set(fx, y - 1, zz, stairs("dark_oak", s > 0 ? "east" : "west", true));
                        }
                    }

                    y += h;
                }

                // a lean-to roof from the front up against the wall, and a chimney
                for (int k = 0; k <= depth + 1; k++) {
                    int xx = x0 - s * 2 + s * k;
                    for (int zz = z - 1; zz <= z1 + 1; zz++) {
                        this.set(xx, y + k / 2, zz, k % 2 == 0 ? stairs(i % 2 == 0 ? "spruce" : "deepslate_tile", s > 0 ? "east" : "west", false)
                            : (i % 2 == 0 ? "spruce_planks" : "deepslate_tiles"));
                    }
                }

                int cz = z + 1 + (int)(this.h(75L, s, i, 2) * (z1 - z - 2));
                for (int yy = 1; yy <= y + 6; yy++) {
                    this.set(wallX - s * 2, yy, cz, yy > y + 3 ? "bricks" : this.get(wallX - s * 2, yy, cz) == null ? "bricks" : this.get(wallX - s * 2, yy, cz));
                }

                this.set(wallX - s * 2, y + 7, cz, "campfire[lit=true]");
                z = z1 + 2;
                i++;
            }
        }
    }

    /** The keep: five storeys, buttresses, tall windows, corner turrets, a slate roof, the great spire on one corner. */
    private void dkKeep() {
        Mat wall = this.mixOf(this.seed + 81L, "stone_bricks", "cracked_stone_bricks", 0.09, "andesite", 0.07, "tuff_bricks", 0.07, "mossy_stone_bricks", 0.04);
        int x0 = -22;
        int x1 = 22;
        int zFront = -DK_WZ + 1;
        int zBack = -DK_WZ - 22;
        int top = 40;
        this.fill(x0, this.yLo, zBack, x1, top, zFront, wall);
        this.fill(x0 + 2, 1, zBack + 2, x1 - 2, top - 1, zFront - 2, of("cave_air"));
        // the front: buttresses between bays, three rows of tall pointed windows, a great door with steps
        Face f = new Face(0, zFront, 0, -1);
        for (int u = -21; u <= 21; u += 7) {
            for (int y = 1; y <= top - 4; y++) {
                f.set(u, y, -1, wall.at(f.x(u, -1), y, f.z(u, -1)));
                if (y <= 12) {
                    f.set(u, y, -2, wall.at(f.x(u, -2), y, f.z(u, -2)));
                }
            }

            f.set(u, 13, -2, stairs("stone_brick", f.back(), false));
            f.set(u, top - 3, -1, stairs("stone_brick", f.back(), false));
        }

        for (int bay = -2; bay <= 2; bay++) {
            double uc = bay * 7 + 3.5;
            if (uc > 20) {
                continue;
            }

            for (int row = 0; row < 3; row++) {
                int y = 7 + row * 10;
                if (bay == 0 && row == 0) {
                    continue;
                }

                this.window(f, uc, y, 3, row == 2 ? 5 : 6, true, "glass_pane", "black_concrete", "polished_andesite", "stone_brick", 1);
            }
        }

        // the great door, with a stair and a carved arch, banners each side
        this.arch(f, 3.5, 1, 5, 8, true, 2, "dark_oak_planks", "chiseled_stone_bricks");
        this.window(f, 3.5, 11, 5, 5, true, "red_stained_glass_pane", "black_concrete", "chiseled_stone_bricks", "stone_brick", 1);
        for (int s = -1; s <= 1; s += 2) {
            int u = 3 + s * 4 + (s > 0 ? 1 : 0);
            this.banner(f.x(u, -1), 8, f.z(u, -1), "red", f.front());
        }

        // battlements round the top, corner turrets with cones
        for (int x = x0; x <= x1; x++) {
            for (int z = zBack; z <= zFront; z++) {
                boolean rim = x == x0 || x == x1 || z == zBack || z == zFront;
                if (rim) {
                    this.set(x, top + 1, z, Math.floorMod(x + z, 3) == 0 ? null : "stone_bricks");
                    this.set(x, top, z, "stone_bricks");
                }
            }
        }

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = 0; sz <= 1; sz++) {
                int tx = sx > 0 ? x1 : x0;
                int tz = sz == 0 ? zFront : zBack;
                if (sx > 0 && sz == 1) {
                    continue;
                }

                this.cyl(tx, tz, 3.6, 8, top + 8, wall);
                this.cyl(tx, tz, 4.3, top + 9, top + 9, of("stone_bricks"));
                this.coneRoof(tx, tz, 4.2, top + 10, 12, of("deepslate_tiles"));
                this.set(tx, top + 23, tz, "lightning_rod");
                for (int y = 8; y > 4; y--) {
                    this.set(tx, y, tz, stairs("stone_brick", sz == 0 ? "north" : "south", true));
                }
            }
        }

        // the slate roof (ridge along x) with dormers and a chimney
        int ry = this.gable(x0 + 2, x1 - 2, zBack + 2, zFront - 2, top + 1, "deepslate_tile", "deepslate_tiles", true, 0);
        for (int x = x0 + 3; x <= x1 - 3; x++) {
            this.set(x, ry, (zBack + zFront) / 2, "deepslate_tile_slab[type=bottom]");
        }

        for (int u = -12; u <= 12; u += 12) {
            int dz = zFront - 4;
            this.fill(u - 1, top + 2, dz - 1, u + 1, top + 4, dz, of("stone_bricks"));
            this.set(u, top + 3, dz, "glass_pane");
            this.gable(u - 1, u + 1, dz - 1, dz, top + 5, "deepslate_tile", "deepslate_tiles", false, 0);
        }

        // the dragon spire: a tall round tower on the north-east corner
        int sx = x1;
        int sz = zBack;
        this.cyl(sx, sz, 6.0, this.yLo, 58, wall);
        this.cyl(sx, sz, 4.0, 1, 57, of("cave_air"));
        for (int y = 12; y < 56; y += 9) {
            this.ringAt(sx, sz, 5.8, 6.6, y, of("polished_andesite"));
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2.0 + Math.PI / 4.0;
                int x = sx + (int)Math.round(Math.cos(a) * 6.0);
                int z = sz + (int)Math.round(Math.sin(a) * 6.0);
                this.set(x, y + 2, z, "glass_pane");
                this.set(x, y + 3, z, "glass_pane");
            }
        }

        this.ringAt(sx, sz, 5.8, 7.4, 59, of("stone_bricks"));
        for (int k = 0; k < 30; k += 2) {
            double a = k * Math.PI * 2.0 / 30;
            this.set(sx + (int)Math.round(Math.cos(a) * 7.0), 60, sz + (int)Math.round(Math.sin(a) * 7.0), "stone_bricks");
        }

        this.coneRoof(sx, sz, 6.4, 60, 22, this.mixOf(this.seed + 83L, "deepslate_tiles", "polished_deepslate", 0.2));
        for (int y = 83; y <= 86; y++) {
            this.set(sx, y, sz, "iron_chain[axis=y]");
        }

        this.set(sx, 87, sz, "gold_block");
        this.set(sx, 88, sz, "lightning_rod");
    }

    /** The great black dragon perched on the keep's ridge, wings half spread, looking down at the fight. */
    private void dkDragon() {
        String scale = "blackstone";
        String belly = "polished_blackstone_bricks";
        String wing = "red_nether_bricks";
        double bx = -4.0;
        double by = 45.0;
        double bz = -35.0;
        // body, chest and haunches
        this.ellipsoid(bx, by, bz, 3.2, 2.8, 5.6, scale);
        this.ellipsoid(bx, by - 1.0, bz + 3.0, 2.6, 2.4, 2.8, belly);
        // neck rising toward the bailey and the head looking down
        double[][] neck = {{bx, by + 1.5, bz + 4.0}, {bx, by + 4.0, bz + 6.5}, {bx, by + 6.0, bz + 8.5}, {bx, by + 6.5, bz + 10.5}};
        for (int i = 0; i + 1 < neck.length; i++) {
            for (double t = 0; t <= 1.0; t += 0.15) {
                double x = neck[i][0] + (neck[i + 1][0] - neck[i][0]) * t;
                double y = neck[i][1] + (neck[i + 1][1] - neck[i][1]) * t;
                double z = neck[i][2] + (neck[i + 1][2] - neck[i][2]) * t;
                this.sphere(x, y, z, 1.9 - i * 0.25, scale);
            }
        }

        double hx = bx;
        double hy = by + 6.0;
        double hz = bz + 12.5;
        this.ellipsoid(hx, hy, hz, 1.7, 1.5, 2.8, scale);
        this.ellipsoid(hx, hy - 1.2, hz + 0.8, 1.3, 0.7, 2.2, belly);
        this.set((int)Math.round(hx - 1), (int)Math.round(hy + 0.4), (int)Math.round(hz + 1), "shroomlight");
        this.set((int)Math.round(hx + 1), (int)Math.round(hy + 0.4), (int)Math.round(hz + 1), "shroomlight");
        // horns sweeping back
        for (int s = -1; s <= 1; s += 2) {
            this.line(hx + s * 1.0, hy + 1.2, hz - 1.0, hx + s * 2.0, hy + 3.5, hz - 4.0, "bone_block[axis=y]");
        }

        // spines down the back
        for (double z = bz - 4.0; z <= bz + 9.0; z += 2.0) {
            double y = z < bz + 4.0 ? by + 3.0 : by + 3.0 + (z - bz - 4.0) * 0.6;
            this.set((int)Math.round(bx), (int)Math.round(y), (int)Math.round(z), "polished_blackstone_wall");
        }

        // legs gripping the ridge
        for (int s = -1; s <= 1; s += 2) {
            this.line(bx + s * 2.5, by - 1.0, bz + 3.0, bx + s * 3.0, by - 3.5, bz + 5.0, scale);
            this.line(bx + s * 2.5, by - 1.0, bz - 3.0, bx + s * 3.2, by - 3.5, bz - 3.5, scale);
            this.set((int)Math.round(bx + s * 3.0), (int)Math.round(by - 3.0), (int)Math.round(bz + 6.0), "polished_blackstone_wall");
        }

        // the tail coiling over the roof and down its back slope
        double[][] tail = {{bx, by, bz - 5.0}, {bx + 3.0, by + 1.5, bz - 9.0}, {bx + 8.0, by + 3.0, bz - 11.0}, {bx + 13.0, by + 2.0, bz - 9.0}, {bx + 16.0, by + 0.5, bz - 5.0}};
        for (int i = 0; i + 1 < tail.length; i++) {
            for (double t = 0; t <= 1.0; t += 0.12) {
                this.sphere(tail[i][0] + (tail[i + 1][0] - tail[i][0]) * t, tail[i][1] + (tail[i + 1][1] - tail[i][1]) * t,
                    tail[i][2] + (tail[i + 1][2] - tail[i][2]) * t, Math.max(0.6, 1.8 - i * 0.35), scale);
            }
        }

        // the wings: a bone from the shoulder to the wrist and three fingers, the membrane between them
        for (int s = -1; s <= 1; s += 2) {
            double[] sh = {bx + s * 2.6, by + 2.0, bz + 1.0};
            double[] wr = {bx + s * 11.0, by + 10.0, bz - 1.0};
            double[][] tips = {{bx + s * 19.0, by + 8.0, bz - 4.0}, {bx + s * 17.0, by + 2.0, bz - 6.0}, {bx + s * 12.0, by - 1.0, bz - 7.0}};
            this.line(sh[0], sh[1], sh[2], wr[0], wr[1], wr[2], scale);
            double[] prev = sh;
            for (double[] tp : tips) {
                this.line(wr[0], wr[1], wr[2], tp[0], tp[1], tp[2], "blackstone_wall");
                this.membrane(wr, prev == sh ? new double[]{bx + s * 3.0, by + 1.0, bz - 4.0} : prev, tp, wing);
                prev = tp;
            }

            this.membrane(sh, wr, tips[0], wing);
            this.membrane(sh, tips[2], new double[]{bx + s * 3.0, by + 1.0, bz - 4.0}, wing);
        }
    }

    /** Fills a triangle of wing membrane (sampled densely, one block thick). */
    private void membrane(double[] a, double[] b, double[] c, String block) {
        for (double u = 0; u <= 1.0; u += 0.04) {
            for (double v = 0; v <= 1.0 - u; v += 0.04) {
                double x = a[0] + (b[0] - a[0]) * u + (c[0] - a[0]) * v;
                double y = a[1] + (b[1] - a[1]) * u + (c[1] - a[1]) * v;
                double z = a[2] + (b[2] - a[2]) * u + (c[2] - a[2]) * v;
                this.setIfEmpty((int)Math.round(x), (int)Math.round(y), (int)Math.round(z), block);
            }
        }
    }

    /** The countryside: oak and birch woods in clusters, spruce on the hills, a road from the drawbridge, a hamlet. */
    private void dkCountry() {
        // the road south from the drawbridge
        for (int z = DK_WZ + DK_T + 14; z < this.R - 6; z++) {
            for (int x = -2; x <= 2; x++) {
                int top = this.topAt(x, z);
                if (top > -50) {
                    this.set(x, top, z, Math.abs(x) == 2 ? "coarse_dirt" : "dirt_path");
                }
            }
        }

        // woods: clusters of broadleaf trees, spruce higher up
        for (int i = 0; i < 70; i++) {
            double a = this.h(91L, i, 0, 0) * Math.PI * 2.0;
            double r = 62 + this.h(93L, i, 0, 0) * 24;
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            if (Math.abs(x) < 6 && z > 0) {
                continue;
            }

            int top = this.topAt(x, z);
            if (top < -1 || !"grass_block".equals(this.get(x, top, z))) {
                continue;
            }

            if (top > 9) {
                this.pine(x, top + 1, z, 9 + (int)(this.h(95L, i, 1, 0) * 6), false);
            } else {
                boolean birch = this.h(97L, i, 2, 0) < 0.3;
                this.broadTree(x, top + 1, z, 6 + (int)(this.h(99L, i, 3, 0) * 4), birch ? "birch_log" : "oak_log", birch ? "birch" : "oak",
                    birch ? "oak" : "azalea");
            }
        }

        // a hamlet by the road
        for (int k = 0; k < 4; k++) {
            int x = (k % 2 == 0 ? -1 : 1) * (10 + k * 3);
            int z = 62 + k * 5;
            int top = this.topAt(x, z);
            if (top >= 0 && top < 10) {
                this.cottage(x, top + 1, z, k);
            }
        }
    }

    /** The y of the highest block of a column (or a very low number when it is empty). */
    private int topAt(int x, int z) {
        for (int y = this.yHi; y >= this.yLo; y--) {
            String b = this.get(x, y, z);
            if (b != null && !b.equals("cave_air") && !b.equals("barrier")) {
                return y;
            }
        }

        return -99;
    }

    private void cottage(int x, int y, int z, int k) {
        String wall = k % 2 == 0 ? "white_terracotta" : "smooth_sandstone";
        this.fill(x - 3, y, z - 2, x + 3, y + 3, z + 2, of(wall));
        this.fill(x - 2, y, z - 1, x + 2, y + 2, z + 1, of("cave_air"));
        for (int[] c : new int[][]{{-3, -2}, {3, -2}, {-3, 2}, {3, 2}}) {
            for (int yy = y; yy <= y + 3; yy++) {
                this.set(x + c[0], yy, z + c[1], "stripped_spruce_log[axis=y]");
            }
        }

        this.set(x, y + 1, z - 2, "glass_pane");
        this.set(x, y + 1, z + 2, "glass_pane");
        this.set(x - 3, y, z, "spruce_door[facing=west,half=lower]");
        this.set(x - 3, y + 1, z, "spruce_door[facing=west,half=upper]");
        this.gable(x - 3, x + 3, z - 2, z + 2, y + 4, k % 2 == 0 ? "spruce" : "dark_oak", k % 2 == 0 ? "spruce_planks" : "dark_oak_planks", true, 1);
    }

    // ================================================================== KOI SHRINE SUMMIT

    private void koiShrine() {
        // the raked gravel floor: pale stone in long raked bands, a dark stone border, a sakura medallion
        this.floor((x, y, z) -> {
            double d = this.sd(x, z);
            if (d > -1.6) {
                return "polished_andesite";
            } else if (d > -2.6) {
                return "andesite";
            }

            double r = Math.hypot(x * 0.9, z);
            if (r < 5.5) {
                return r < 1.5 ? "pink_glazed_terracotta" : (r < 3.4 ? "calcite" : "pink_terracotta");
            }

            // raked lines round the medallion and along the length
            int band = r < 14 ? (int)Math.floor(r) : Math.floorMod(z, 3);
            return band % 3 == 0 ? "smooth_stone" : (this.h(201L, x, 0, z) < 0.15 ? "diorite" : "calcite");
        }, "stone");
        this.ksMountain();
        this.ksPagoda();
        this.ksPond();
        this.ksShrineHall();
        this.ksTorii();
        this.ksGarden();
    }

    /** Height of the mountain round the terrace: high cliffs behind (north), slopes down to the south. */
    private int ksHeight(int x, int z) {
        double d = this.sd(x, z);
        if (d < 8) {
            return 0;
        }

        double nn = this.n(211L, x * 0.045, z * 0.045);
        double fine = this.n(213L, x * 0.16, z * 0.16);
        if (z < -this.hz - 30) {
            // the cliff behind the pagoda rises in steps
            double t = Math.min(1.0, (-z - this.hz - 30) / 22.0);
            // steps of rock: flat ledges and steep faces
            double raw = 6 + t * 38 + nn * 10;
            return (int)Math.round(Math.floor(raw / 5.0) * 5.0 + Math.min(5.0, (raw % 5.0) * 2.5) * 0.4 + fine * 2);
        } else if (z > this.hz + 4) {
            // the mountainside falls away to the south
            double t = Math.min(1.0, (z - this.hz - 4) / 50.0);
            return (int)Math.round(-t * 22 + nn * 5 * t + fine * 2);
        }

        double side = Math.min(1.0, (Math.abs(x) - this.hx - 26) / 30.0);
        return side < 0 ? 0 : (int)Math.round(side * 20 + nn * 12 * side + fine * 2);
    }

    private void ksMountain() {
        Mat rock = (x, y, z) -> {
            double w = this.n(221L, x * 0.3, z * 0.3) * 3.0;
            int band = Math.floorMod((int)Math.floor((y + w) / 3.0), 5);
            return band == 1 ? "andesite" : (band == 3 ? "calcite" : (hash3(this.seed + 223L, x, y, z) < 0.12 ? "mossy_cobblestone" : "stone"));
        };
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                if (this.sd(x, z) < 0.5) {
                    continue;
                }

                double r = Math.hypot(x, z);
                double edge = this.R - 2 - this.n(225L, x * 0.07, z * 0.07) * 7.0;
                if (r > edge) {
                    continue;
                }

                int top = this.ksHeight(x, z);
                double slope = Math.abs(this.ksHeight(x + 1, z) - top) + Math.abs(this.ksHeight(x, z + 1) - top);
                boolean bare = slope >= 2 || top > 16 && this.n(228L, x * 0.12, z * 0.12) > 0.4;
                String t = bare ? rock.at(x, top, z) : (this.n(227L, x * 0.2, z * 0.2) > 0.68 ? "moss_block" : "grass_block");
                if (this.sd(x, z) < 8 && z < this.hz + 6) {
                    // the terrace round the floor: stone paving with moss in the joints
                    t = this.h(229L, x, 0, z) < 0.2 ? "mossy_stone_bricks" : "stone_bricks";
                }

                this.column(x, z, top, t, slope > 2 ? rock.at(x, top - 1, z) : "dirt", rock);
                if (r > edge - 5) {
                    int cut = this.yLo + (int)((5 - (edge - r)) * 3.0);
                    for (int y = this.yLo; y < cut; y++) {
                        this.set(x, y, z, null);
                    }
                }
            }
        }

        // the waterfall: from the top of the cliff, down its face, into the pond behind the pagoda
        int wx = 26;
        for (int y = 0; y <= 46; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                int z = -this.hz - 30 - (int)Math.round(Math.max(0, y - 6) * 22.0 / 38.0) - 1;
                if (y > 1) {
                    this.set(wx + dx, y, z, "water");
                    this.set(wx + dx, y, z - 1, rock.at(wx + dx, y, z - 1));
                }
            }
        }

        for (int x = wx - 6; x <= wx + 6; x++) {
            for (int z = -this.hz - 34; z <= -this.hz - 24; z++) {
                if (Math.hypot(x - wx, (z + this.hz + 29) * 1.2) < 5.5) {
                    this.set(x, 0, z, "water");
                    this.set(x, -1, z, "water");
                    this.set(x, -2, z, "gravel");
                }
            }
        }
    }

    /** A five-tier pagoda on a stone podium: red posts, white walls, dark flared eaves with upturned corners, a spire. */
    private void ksPagoda() {
        int pz = -this.hz - 22;
        // the podium with a central stair down to the terrace
        this.fill(-14, this.yLo, pz - 12, 14, 3, pz + 12, this.mixOf(this.seed + 231L, "stone_bricks", "mossy_stone_bricks", 0.2, "cracked_stone_bricks", 0.08));
        for (int x = -14; x <= 14; x++) {
            this.set(x, 4, pz + 12, x % 2 == 0 ? "stone_brick_wall" : null);
            this.set(x, 4, pz - 12, x % 2 == 0 ? "stone_brick_wall" : null);
        }

        for (int k = 0; k < 4; k++) {
            for (int x = -3; x <= 3; x++) {
                this.set(x, 3 - k, pz + 13 + k, stairs("stone_brick", "north", false));
                this.set(x, 4, pz + 12, null);
            }
        }

        for (int s = -1; s <= 1; s += 2) {
            this.stoneLantern(s * 6, 4, pz + 10);
        }

        int y = 4;
        int half = 7;
        for (int tier = 0; tier < 5; tier++) {
            int h = tier == 0 ? 6 : 4;
            // walls: red posts at corners and between bays, white plaster, a balcony on the first tier
            for (int x = -half; x <= half; x++) {
                for (int z = pz - half; z <= pz + half; z++) {
                    boolean edge = Math.abs(x) == half || Math.abs(z - pz) == half;
                    if (!edge) {
                        continue;
                    }

                    for (int yy = y; yy < y + h; yy++) {
                        boolean post = Math.abs(x) == half && Math.abs(z - pz) == half || Math.floorMod(x, 3) == 0 && Math.abs(z - pz) == half
                            || Math.floorMod(z - pz, 3) == 0 && Math.abs(x) == half;
                        boolean win = !post && yy >= y + 1 && yy <= y + h - 2;
                        this.set(x, yy, z, post ? "stripped_mangrove_log[axis=y]" : (win ? (tier == 0 && yy <= y + 3 && Math.abs(x) <= 1 ? null : "white_stained_glass_pane") : "white_concrete"));
                    }
                }
            }

            this.fill(-half + 1, y, pz - half + 1, half - 1, y + h - 1, pz + half - 1, of("cave_air"));
            this.fill(-half, y - 1, pz - half, half, y - 1, pz + half, of("dark_oak_planks"));
            // the eave: two rows of dark tiles sticking out, the corners curling up
            int over = 3;
            int ey = y + h;
            for (int x = -half - over; x <= half + over; x++) {
                for (int z = pz - half - over; z <= pz + half + over; z++) {
                    int dx = Math.abs(x);
                    int dz = Math.abs(z - pz);
                    int out = Math.max(dx, dz) - half;
                    boolean corner = dx > half && dz > half;
                    if (out <= 0) {
                        this.set(x, ey + 1, z, "deepslate_tiles");
                        continue;
                    }

                    int lift = corner ? (out >= over ? 2 : 1) : (out >= over ? 1 : 0);
                    String face = dx >= dz ? (x > 0 ? "west" : "east") : (z > pz ? "north" : "south");
                    this.set(x, ey + lift - (out == 1 ? 0 : 0), z, stairs("deepslate_tile", face, false));
                    if (out == 1) {
                        this.set(x, ey + 1, z, stairs("deepslate_tile", face, false));
                    }
                }
            }

            // a red rail under the eave
            for (int x = -half - 1; x <= half + 1; x++) {
                this.set(x, ey - 1, pz - half - 1, stairs("mangrove", "south", true));
                this.set(x, ey - 1, pz + half + 1, stairs("mangrove", "north", true));
            }

            for (int z = pz - half - 1; z <= pz + half + 1; z++) {
                this.set(-half - 1, ey - 1, z, stairs("mangrove", "east", true));
                this.set(half + 1, ey - 1, z, stairs("mangrove", "west", true));
            }

            y = ey + 2;
            half = Math.max(3, half - 1);
        }

        // the sorin spire: rings on a pole, a jewel on top
        for (int k = 0; k < 9; k++) {
            this.set(0, y + k, pz, k % 2 == 0 ? "gold_block" : "iron_chain[axis=y]");
        }

        this.set(0, y + 9, pz, "end_rod[facing=up]");
        // lanterns hanging at each tier's corners
        for (int s = -1; s <= 1; s += 2) {
            for (int t = -1; t <= 1; t += 2) {
                this.set(s * 9, 9, pz + t * 9, "lantern[hanging=true]");
            }
        }
    }

    /** The koi pond east of the floor: an irregular shore, stepping stones, lilies, koi under the water, a red bridge. */
    private void ksPond() {
        int px = this.hx + 18;
        int pz = 2;
        for (int x = px - 16; x <= px + 16; x++) {
            for (int z = pz - 20; z <= pz + 20; z++) {
                double r = Math.hypot((x - px) / 1.0, (z - pz) / 1.25) + this.n(241L, x * 0.15, z * 0.15) * 4.0;
                if (r < 13 && this.sd(x, z) > 3) {
                    int depth = r < 9 ? 3 : 2;
                    for (int y = -depth; y <= 0; y++) {
                        this.set(x, y, z, "water");
                    }

                    this.set(x, -depth - 1, z, this.h(243L, x, 0, z) < 0.3 ? "clay" : "gravel");
                    double k = this.h(245L, x, 1, z);
                    if (k < 0.025) {
                        this.set(x, -depth, z, "orange_concrete");
                    } else if (k < 0.04) {
                        this.set(x, -depth, z, "white_concrete");
                    } else if (k > 0.95) {
                        this.set(x, 1, z, "lily_pad");
                    }
                } else if (r < 15 && this.sd(x, z) > 3) {
                    // the shore: mossy rocks and moss
                    this.set(x, 0, z, this.h(247L, x, 0, z) < 0.35 ? "mossy_cobblestone" : "moss_block");
                    if (this.h(249L, x, 2, z) < 0.12) {
                        this.boulder(x, 1, z, 1.4, "stone", "mossy_cobblestone", "andesite");
                    }
                }
            }
        }

        // stepping stones across the north part
        for (int i = 0; i < 7; i++) {
            this.set(px - 9 + i * 3, 0, pz - 8 + (i % 2), "smooth_stone_slab[type=double]");
        }

        // a red arched bridge across the middle (east-west)
        for (int x = px - 11; x <= px + 11; x++) {
            double t = (x - px) / 11.0;
            int y = (int)Math.round((1.0 - t * t) * 4.0);
            for (int z = pz + 3; z <= pz + 5; z++) {
                this.set(x, y, z, z == pz + 4 ? "mangrove_planks" : "mangrove_slab[type=top]");
            }

            this.set(x, y + 1, pz + 2, "mangrove_fence");
            this.set(x, y + 1, pz + 6, "mangrove_fence");
            if (Math.floorMod(x - px, 4) == 0) {
                this.set(x, y + 2, pz + 2, "red_concrete");
                this.set(x, y + 2, pz + 6, "red_concrete");
            }
        }
    }

    /** A shrine hall west of the floor in a bamboo grove: a raised floor, red posts, white walls, a curved roof. */
    private void ksShrineHall() {
        int sx = -this.hx - 22;
        int sz = 0;
        this.fill(sx - 10, this.yLo, sz - 12, sx + 8, 1, sz + 12, of("stone_bricks"));
        for (int z = sz - 3; z <= sz + 3; z++) {
            this.set(sx + 9, 1, z, stairs("stone_brick", "west", false));
            this.set(sx + 10, 0, z, stairs("stone_brick", "west", false));
        }

        for (int x = sx - 8; x <= sx + 6; x++) {
            for (int z = sz - 10; z <= sz + 10; z++) {
                boolean edge = x == sx - 8 || x == sx + 6 || Math.abs(z - sz) == 10;
                for (int y = 2; y <= 8; y++) {
                    if (edge) {
                        boolean post = Math.floorMod(z - sz, 4) == 0 || x == sx - 8 && Math.abs(z - sz) == 10 || x == sx + 6 && Math.abs(z - sz) == 10;
                        this.set(x, y, z, post ? "stripped_mangrove_log[axis=y]" : (y >= 4 && y <= 6 ? "birch_trapdoor[facing=east,half=bottom,open=true]" : "white_concrete"));
                    } else {
                        this.set(x, y, z, y == 2 ? "dark_oak_planks" : "cave_air");
                    }
                }
            }
        }

        // the open front between the posts
        for (int z = sz - 3; z <= sz + 3; z++) {
            for (int y = 2; y <= 6; y++) {
                this.set(sx + 6, y, z, Math.floorMod(z - sz, 4) == 0 ? "stripped_mangrove_log[axis=y]" : null);
            }
        }

        // a curved roof: steep at the top, flat at the eaves, upturned corners
        int top = this.gable(sx - 9, sx + 7, sz - 11, sz + 11, 9, "deepslate_tile", "deepslate_tiles", false, 2);
        for (int z = sz - 13; z <= sz + 13; z++) {
            this.set(sx - 1, top, z, "deepslate_tile_slab[type=bottom]");
        }

        this.set(sx - 1, top + 1, sz - 13, "gold_block");
        this.set(sx - 1, top + 1, sz + 13, "gold_block");
        // the bamboo grove round it
        for (int i = 0; i < 160; i++) {
            int x = sx - 24 + (int)(this.h(251L, i, 0, 0) * 40);
            int z = sz - 34 + (int)(this.h(253L, i, 0, 0) * 68);
            if (Math.abs(x - sx) < 13 && Math.abs(z - sz) < 15 || this.sd(x, z) < 6) {
                continue;
            }

            int base = this.topAt(x, z);
            if (base < -2 || base > 12) {
                continue;
            }

            int h = 8 + (int)(this.h(255L, i, 1, 0) * 8);
            for (int y = 1; y <= h; y++) {
                this.set(x, base + y, z, "bamboo[age=1,leaves=" + (y > h - 3 ? "large" : (y > h - 5 ? "small" : "none")) + ",stage=0]");
            }
        }

        this.stoneLantern(sx + 10, 1, sz - 6);
        this.stoneLantern(sx + 10, 1, sz + 6);
    }

    /** Torii going down the mountain to the south, on stone steps. */
    private void ksTorii() {
        for (int k = 0; k < 5; k++) {
            int z = this.hz + 9 + k * 9;
            int base = this.topAt(0, z);
            if (base < this.yLo + 4) {
                continue;
            }

            // the steps under each gate
            for (int zz = z - 4; zz <= z + 4; zz++) {
                int t = this.topAt(0, zz);
                for (int x = -3; x <= 3; x++) {
                    this.set(x, t, zz, Math.abs(x) == 3 ? "mossy_stone_bricks" : "stone_bricks");
                }
            }

            for (int s = -1; s <= 1; s += 2) {
                for (int y = 1; y <= 8; y++) {
                    this.set(s * 4, base + y, z, y == 1 ? "black_concrete" : "red_concrete");
                }
            }

            for (int x = -6; x <= 6; x++) {
                this.set(x, base + 9, z, Math.abs(x) >= 5 ? stairs("dark_oak", x > 0 ? "west" : "east", true) : "black_concrete");
                if (Math.abs(x) <= 5) {
                    this.set(x, base + 7, z, "red_concrete");
                }
            }

            this.set(0, base + 8, z, "red_concrete");
        }
    }

    /** Sculpted cherry trees and cloud pines on the terrace and slopes, stone lanterns, petals on the ground. */
    private void ksGarden() {
        int[][] cherries = {{-this.hx - 8, -this.hz - 8}, {this.hx + 6, -this.hz - 9}, {-this.hx - 4, this.hz + 9}, {-18, -this.hz - 13},
            {18, -this.hz - 12}, {this.hx + 38, -22}, {-this.hx - 40, 26}, {this.hx + 30, 30}, {-56, -48}, {50, -52}, {-24, 44}, {28, 46}};
        for (int[] c : cherries) {
            int base = this.topAt(c[0], c[1]);
            if (base > this.yLo + 3 && this.sd(c[0], c[1]) > 6) {
                this.cherry(c[0], base + 1, c[1], 6 + (int)(this.h(261L, c[0], 0, c[1]) * 3));
            }
        }

        for (int i = 0; i < 46; i++) {
            double a = this.h(263L, i, 0, 0) * Math.PI * 2.0;
            double r = 55 + this.h(265L, i, 0, 0) * 30;
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            int base = this.topAt(x, z);
            if (base > this.yLo + 3) {
                if (this.h(267L, i, 1, 0) < 0.5) {
                    this.cloudPine(x, base + 1, z, 7 + (int)(this.h(269L, i, 2, 0) * 5));
                } else {
                    this.cherry(x, base + 1, z, 5 + (int)(this.h(269L, i, 3, 0) * 3));
                }
            }
        }

        // lanterns along the floor's long sides
        for (int x = -this.hx + 6; x <= this.hx - 6; x += 12) {
            this.stoneLantern(x, 1, -this.hz - 4);
            this.stoneLantern(x, 1, this.hz + 4);
        }
    }

    /** A cherry tree with a twisting trunk, spreading boughs and wide, flat clouds of blossom; petals beneath. */
    private void cherry(int x, int y, int z, int height) {
        double a = this.h(271L, x, y, z) * Math.PI * 2.0;
        double tx = x;
        double tz = z;
        for (int i = 0; i < height; i++) {
            tx += Math.cos(a + i * 0.5) * 0.35;
            tz += Math.sin(a + i * 0.5) * 0.35;
            this.set((int)Math.round(tx), y + i, (int)Math.round(tz), "cherry_log[axis=y]");
        }

        int top = y + height;
        for (int b = 0; b < 4; b++) {
            double ba = a + b * Math.PI / 2.0 + this.h(273L, x, b, z) * 0.8;
            double len = 3.5 + this.h(275L, x, b, z) * 3.0;
            double ex = tx + Math.cos(ba) * len;
            double ez = tz + Math.sin(ba) * len;
            double ey = top - 1 + len * 0.3;
            this.line(tx, top - 2, tz, ex, ey, ez, "cherry_log[axis=" + (Math.abs(Math.cos(ba)) > Math.abs(Math.sin(ba)) ? "x" : "z") + "]");
            this.ellipsoid(ex, ey + 1.0, ez, 3.4, 1.6, 3.4, leaves("cherry"));
        }

        this.ellipsoid(tx, top + 1.5, tz, 4.0, 1.8, 4.0, leaves("cherry"));
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz < 30 && this.h(277L, x + dx, 0, z + dz) < 0.4) {
                    int t = this.topAt(x + dx, z + dz);
                    if (t > this.yLo && t < y + 1 && this.empty(x + dx, t + 1, z + dz) && !"water".equals(this.get(x + dx, t, z + dz))) {
                        this.set(x + dx, t + 1, z + dz, "pink_petals[facing=north,flower_amount=" + (1 + (int)(this.h(279L, dx, 0, dz) * 4)) + "]");
                    }
                }
            }
        }
    }

    /** A Japanese black pine: a leaning trunk and flat cloud pads of needles at the ends of its branches. */
    private void cloudPine(int x, int y, int z, int height) {
        double a = this.h(281L, x, y, z) * Math.PI * 2.0;
        double tx = x;
        double tz = z;
        for (int i = 0; i < height; i++) {
            tx += Math.cos(a) * 0.3;
            tz += Math.sin(a) * 0.3;
            this.set((int)Math.round(tx), y + i, (int)Math.round(tz), "spruce_log[axis=y]");
        }

        for (int b = 0; b < 4; b++) {
            double ba = b * 1.7 + a;
            int by = y + height - 1 - b * 2;
            double ex = tx + Math.cos(ba) * (2.5 + b);
            double ez = tz + Math.sin(ba) * (2.5 + b);
            this.line(tx, by, tz, ex, by + 1, ez, "spruce_log[axis=" + (Math.abs(Math.cos(ba)) > Math.abs(Math.sin(ba)) ? "x" : "z") + "]");
            this.ellipsoid(ex, by + 1.5, ez, 2.6, 0.9, 2.6, leaves("spruce"));
        }

        this.ellipsoid(tx, y + height + 0.5, tz, 2.4, 1.0, 2.4, leaves("spruce"));
    }

    // ================================================================== CORSAIR HARBOR

    private static final int SEA = -2;

    private void corsair() {
        // the dock: spruce planks with dark oak beams every few rows and an oak border
        this.floor((x, y, z) -> {
            double d = this.sd(x, z);
            if (d > -1.6) {
                return "stripped_dark_oak_log[axis=x]";
            } else if (Math.floorMod(x, 9) == 0) {
                return "dark_oak_planks";
            }

            return this.h(301L, x, 0, z) < 0.1 ? "oak_planks" : "spruce_planks";
        }, "spruce_planks");
        this.chGround();
        this.chTown();
        this.chShip();
        this.chLighthouse();
        this.chCrane();
    }

    /** The coast: the quay and town to the north on a rocky shore, the sea everywhere else, piles under the dock. */
    private void chGround() {
        Mat rock = this.mixOf(this.seed + 311L, "stone", "andesite", 0.25, "diorite", 0.08, "cobblestone", 0.06);
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double r = Math.hypot(x, z);
                if (r > this.R - 1) {
                    continue;
                }

                if (this.sd(x, z) < 0.5) {
                    // piles under the dock where the water would be
                    if (Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0) {
                        for (int y = this.yLo; y < 0; y++) {
                            this.set(x, y, z, "spruce_log[axis=y]");
                        }
                    }

                    continue;
                }

                double coast = -this.hz - 8 - this.n(313L, x * 0.05, 0) * 6.0;
                if (z < coast) {
                    // the land: a stone quay wall, then cobbled street, then hills behind the town
                    double back = coast - z;
                    int top;
                    if (back < 3) {
                        top = 1;
                    } else if (back < 26) {
                        top = 1;
                    } else {
                        top = 1 + (int)Math.round(Math.min(1.0, (back - 26) / 30.0) * 26 + this.n(315L, x * 0.06, z * 0.06) * 12);
                    }

                    String t = back < 26 ? (this.h(317L, x, 0, z) < 0.25 ? "andesite" : "cobblestone")
                        : (this.n(318L, x * 0.15, z * 0.15) > 0.7 && top > 12 ? rock.at(x, top, z) : "grass_block");
                    this.column(x, z, top, t, back < 26 ? "stone" : "dirt", rock);
                    if (back < 1.5) {
                        for (int y = SEA - 3; y <= 1; y++) {
                            this.set(x, y, z, "stone_bricks");
                        }
                    }
                } else {
                    // the sea: water over a sandy, rocky bottom, deeper away from the shore
                    int bottom = SEA - 3 - (int)Math.round(Math.min(1.0, (z - coast) / 30.0) * 6);
                    for (int y = this.yLo; y <= bottom; y++) {
                        this.set(x, y, z, y == bottom ? (this.n(319L, x * 0.1, z * 0.1) > 0.6 ? "gravel" : "sand") : rock.at(x, y, z));
                    }

                    for (int y = bottom + 1; y <= SEA; y++) {
                        this.set(x, y, z, "water");
                    }

                    if (bottom + 1 < SEA - 1 && this.h(321L, x, 0, z) < 0.03) {
                        this.set(x, bottom + 1, z, "seagrass");
                    }
                }
            }
        }

        // the dock's edge posts with rope (fences) toward the sea
        for (int x = -this.hx; x <= this.hx; x += 4) {
            int z = this.hz + 1;
            this.set(x, 1, z, "spruce_log[axis=y]");
            for (int y = SEA; y <= 0; y++) {
                this.set(x, y, z, "spruce_log[axis=y]");
            }
        }
    }

    /** The harbour town: a row of jettied timber houses along the quay, every one its own width, height and roof. */
    private void chTown() {
        String[] plaster = {"white_terracotta", "calcite", "light_blue_terracotta", "yellow_terracotta", "white_concrete", "pink_terracotta", "smooth_sandstone"};
        String[] roofs = {"deepslate_tile", "dark_oak", "brick", "spruce", "mud_brick"};
        String[] roofBody = {"deepslate_tiles", "dark_oak_planks", "bricks", "spruce_planks", "mud_bricks"};
        int x = -this.R + 12;
        int i = 0;
        while (x < this.R - 14) {
            int w = 7 + (int)(this.h(331L, i, 0, 0) * 5);
            int coast = (int)Math.round(-this.hz - 8 - this.n(313L, (x + w / 2) * 0.05, 0) * 6.0);
            int zf = coast - 7;
            int depth = 9;
            int storeys = 3 + (int)(this.h(333L, i, 1, 0) * 2.5);
            if (Math.abs(x + w / 2) < 8) {
                storeys = 5;
            }

            String pl = plaster[i % plaster.length];
            int r = (int)(this.h(335L, i, 2, 0) * roofs.length);
            int y = 2;
            for (int st = 0; st < storeys; st++) {
                int jet = st == 0 ? 0 : Math.min(st, 2);
                int front = zf + jet;
                int h = st == 0 ? 5 : 4;
                for (int yy = y; yy < y + h; yy++) {
                    for (int xx = x; xx < x + w; xx++) {
                        for (int zz = zf - depth; zz <= front; zz++) {
                            boolean shell = zz == front || zz == zf - depth || xx == x || xx == x + w - 1;
                            if (!shell) {
                                this.set(xx, yy, zz, "cave_air");
                                continue;
                            }

                            boolean post = xx == x || xx == x + w - 1 || (zz == front && Math.floorMod(xx - x, 3) == 0);
                            String b;
                            if (st == 0) {
                                b = post ? "stone_bricks" : "cobblestone";
                            } else if (post) {
                                b = "stripped_dark_oak_log[axis=y]";
                            } else if (yy == y && zz == front) {
                                b = "dark_oak_planks";
                            } else if (zz == front && (yy - y) % 4 == 2 && Math.floorMod(xx - x, 3) != 0) {
                                b = "glass_pane";
                            } else {
                                b = pl;
                            }

                            this.set(xx, yy, zz, b);
                        }
                    }
                }

                if (st == 0) {
                    // a shopfront: an arched opening with crates and a hanging sign bracket
                    int mid = x + w / 2;
                    for (int yy = y; yy < y + 3; yy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            this.set(mid + dx, yy, front, yy == y + 2 && dx != 0 ? stairs("stone_brick", dx < 0 ? "east" : "west", true) : null);
                        }
                    }

                    this.set(mid - 2, y, front + 1, this.h(337L, i, 0, 0) < 0.5 ? "barrel[facing=up]" : "composter");
                    this.set(mid + 2, y, front + 1, "chest[facing=south]");
                    this.set(mid, y + 3, front + 1, "spruce_fence");
                    this.set(mid, y + 2, front + 2, "lantern[hanging=true]");
                    this.set(mid, y + 3, front + 2, "spruce_fence");
                } else {
                    // shutters by the windows
                    for (int xx = x + 1; xx < x + w - 1; xx++) {
                        if (Math.floorMod(xx - x, 3) != 0 && this.h(339L, xx, st, 0) < 0.4) {
                            this.set(xx, y + 2, front + 1, "spruce_trapdoor[facing=south,open=true]");
                        }
                    }

                    // the jetty beam
                    for (int xx = x; xx < x + w; xx++) {
                        this.set(xx, y - 1, front, stairs("dark_oak", "north", true));
                    }
                }

                y += h;
            }

            // the roof: gable end to the street, steep, with an overhang
            int frontZ = zf + Math.min(storeys - 1, 2);
            this.gable(x, x + w - 1, zf - depth, frontZ, y, roofs[r], roofBody[r], false, 1);
            // a chimney through the roof
            int cx = x + 1 + (int)(this.h(341L, i, 3, 0) * (w - 2));
            for (int yy = 2; yy <= y + w / 2 + 3; yy++) {
                this.set(cx, yy, zf - depth + 1, yy > y ? "bricks" : this.get(cx, yy, zf - depth + 1));
            }

            this.set(cx, y + w / 2 + 4, zf - depth + 1, "campfire[lit=true]");
            x += w + (this.h(343L, i, 4, 0) < 0.3 ? 2 : 0);
            i++;
        }
    }

    /** A three-masted galleon moored along the dock: curved hull, gun ports, stern castle, masts, yards and sails. */
    private void chShip() {
        int zc = this.hz + 15;
        int len = 36;
        int deck = 4;
        int keel = SEA - 7;
        for (int x = -len; x <= len; x++) {
            double t = x / (double)len;
            // the plan: full amidships, a fine bow (west), a broad stern (east)
            double plan = t < 0 ? Math.sqrt(Math.max(0, 1.0 - Math.pow(-t, 2.2))) : Math.sqrt(Math.max(0, 1.0 - Math.pow(t, 5.0)));
            for (int y = keel; y <= deck + (t > 0.62 ? 7 : (t < -0.72 ? 3 : 0)); y++) {
                double v = Math.max(0, Math.min(1.0, (y - keel) / (double)(deck - keel)));
                double half = 7.2 * plan * Math.pow(v, 0.45);
                if (y > deck) {
                    half = 7.2 * plan * 0.92;
                }

                int hw = (int)Math.round(half);
                if (hw < 1 && y > keel + 1) {
                    continue;
                }

                for (int dz = -hw; dz <= hw; dz++) {
                    boolean shell = Math.abs(dz) == hw || y == keel || Math.abs(dz) >= hw - 0 && y <= deck;
                    int z = zc + dz;
                    String b;
                    if (y == deck && Math.abs(dz) < hw) {
                        b = "spruce_planks";
                    } else if (y > deck && Math.abs(dz) < hw) {
                        b = t > 0.62 && y == deck + 7 ? "spruce_planks" : (t > 0.62 ? "cave_air" : null);
                    } else if (!shell) {
                        b = y < SEA ? "water" : "cave_air";
                    } else if (y == deck - 2 && Math.floorMod(x, 4) == 0 && Math.abs(t) < 0.6) {
                        b = "coal_block";
                    } else if (y == deck - 1 || y == deck + 1) {
                        b = "stripped_spruce_log[axis=x]";
                    } else if (y < SEA) {
                        b = "dark_oak_planks";
                    } else {
                        b = y == 0 ? "yellow_terracotta" : "dark_oak_planks";
                    }

                    if (b != null) {
                        this.set(x, y, z, b);
                    }
                }
            }
        }

        // the rails along the deck, the stern windows and lanterns
        for (int x = -len + 4; x <= len - 2; x++) {
            for (int s = -1; s <= 1; s += 2) {
                int z = zc + s * 7;
                if (this.get(x, deck, z) != null) {
                    this.setIfEmpty(x, deck + 1, z, "dark_oak_fence");
                }
            }
        }

        for (int dz = -5; dz <= 5; dz += 2) {
            this.set(len - 1, deck + 3, zc + dz, "orange_stained_glass_pane");
            this.set(len - 1, deck + 4, zc + dz, "orange_stained_glass_pane");
        }

        this.set(len - 2, deck + 9, zc - 5, "lantern[hanging=false]");
        this.set(len - 2, deck + 9, zc + 5, "lantern[hanging=false]");
        // the bowsprit and a figurehead
        this.line(-len, deck + 1, zc, -len - 12, deck + 8, zc, "spruce_log[axis=x]");
        this.set(-len - 1, deck - 1, zc, "gold_block");
        this.set(-len - 2, deck, zc, "gold_block");
        // masts with yards and billowing sails, a crow's nest on the main mast, flags on top
        int[][] masts = {{-18, 28}, {0, 36}, {20, 26}};
        for (int[] m : masts) {
            int mx = m[0];
            int top = deck + m[1];
            for (int y = deck; y <= top; y++) {
                this.set(mx, y, zc, "spruce_log[axis=y]");
            }

            for (int k = 0; k < 2; k++) {
                int yTop = top - 2 - k * 11;
                int yBot = yTop - 9;
                int span = 9 - k * 0 - (m[1] < 30 ? 2 : 0) + k;
                for (int dz = -span; dz <= span; dz++) {
                    this.set(mx, yTop + 1, zc + dz, "spruce_log[axis=z]");
                }

                for (int y = yBot; y <= yTop; y++) {
                    double v = (yTop - y) / (double)(yTop - yBot);
                    int belly = (int)Math.round(Math.sin(v * Math.PI) * 2.2);
                    for (int dz = -span + 1; dz <= span - 1; dz++) {
                        double u = dz / (double)span;
                        int bulge = (int)Math.round(belly * (1.0 - u * u));
                        this.set(mx + 1 + bulge, y, zc + dz, y == yBot + 1 ? "red_wool" : "white_wool");
                        // fill the step between bulge columns so the sail has no holes
                        int prev = (int)Math.round(belly * (1.0 - Math.pow((dz - 1) / (double)span, 2)));
                        for (int b = Math.min(prev, bulge); b < Math.max(prev, bulge); b++) {
                            this.setIfEmpty(mx + 1 + b, y, zc + dz, "white_wool");
                        }

                        // and between this row and the one above (the sail curves up and down too)
                        double vUp = (yTop - (y + 1)) / (double)(yTop - yBot);
                        int up = (int)Math.round(Math.round(Math.sin(Math.max(0, vUp) * Math.PI) * 2.2) * (1.0 - u * u));
                        if (y < yTop) {
                            for (int bb = Math.min(up, bulge); bb < Math.max(up, bulge); bb++) {
                                this.setIfEmpty(mx + 1 + bb, y, zc + dz, "white_wool");
                            }
                        }
                    }
                }
            }

            if (mx == 0) {
                this.ring(mx, zc, top - 6, 0, 2.2, "spruce_planks");
                this.ring(mx, zc, top - 5, 2.0, 2.4, "spruce_fence");
            }

            this.set(mx, top + 1, zc, "black_wool");
            this.set(mx - 1, top + 1, zc, "black_wool");
            this.set(mx - 2, top + 1, zc, "black_wool");
            this.set(mx - 1, top + 2, zc, "white_wool");
            // shrouds: rope lines from the rails to the mast tops
            for (int s = -1; s <= 1; s += 2) {
                this.line(mx - 2, deck + 1, zc + s * 7, mx, top - 4, zc + s * 1, "iron_chain[axis=y]");
                this.line(mx + 2, deck + 1, zc + s * 7, mx, top - 4, zc + s * 1, "iron_chain[axis=y]");
            }
        }

        // a gangplank from the dock to the deck
        for (int k = 0; k <= 6; k++) {
            for (int x = 5; x <= 7; x++) {
                this.set(x, 1 + k / 2, this.hz + 2 + k, k % 2 == 0 ? "spruce_slab[type=bottom]" : "spruce_slab[type=top]");
            }
        }

        // cannons on the deck
        for (int x = -12; x <= 12; x += 8) {
            this.set(x, deck + 1, zc - 5, "anvil[facing=south]");
            this.set(x, deck + 1, zc + 5, "anvil[facing=north]");
        }
    }

    /** A striped lighthouse on a rock island in the west of the bay. */
    private void chLighthouse() {
        int lx = -60;
        int lz = 38;
        for (int x = lx - 9; x <= lx + 9; x++) {
            for (int z = lz - 9; z <= lz + 9; z++) {
                double r = Math.hypot(x - lx, z - lz) + this.n(351L, x * 0.3, z * 0.3) * 2.5;
                if (r < 8.5) {
                    int top = (int)Math.round(3 - r * 0.45 + this.n(353L, x * 0.4, z * 0.4) * 2);
                    for (int y = this.yLo; y <= top; y++) {
                        this.set(x, y, z, this.h(355L, x, y, z) < 0.3 ? "andesite" : "stone");
                    }
                }
            }
        }

        for (int y = 3; y <= 40; y++) {
            boolean red = Math.floorMod(y - 3, 8) < 4;
            this.cylinder(lx, lz, y, y, y < 30 ? 4.2 - (y - 3) * 0.02 : 3.6, red ? "red_concrete" : "white_concrete", true);
            this.disc(lx, lz, y, y < 30 ? 3.0 : 2.4, "cave_air");
            if (y % 8 == 6) {
                this.set(lx + 4, y, lz, "glass_pane");
                this.set(lx - 4, y, lz, "glass_pane");
            }
        }

        this.disc(lx, lz, 41, 5.0, "polished_andesite");
        this.ring(lx, lz, 42, 4.6, 5.0, "iron_bars");
        for (int y = 42; y <= 45; y++) {
            this.cylinder(lx, lz, y, y, 2.8, "glass", true);
        }

        this.set(lx, 43, lz, "sea_lantern");
        this.set(lx, 44, lz, "sea_lantern");
        this.coneRoof(lx, lz, 3.6, 46, 5, of("red_concrete"));
        this.set(lx, 52, lz, "lightning_rod");
    }

    /** A treadwheel crane on the east end of the dock lifting a cargo net over the water, crates and barrels round it. */
    private void chCrane() {
        int cx = this.hx + 10;
        int cz = this.hz - 2;
        // a little pier for it
        for (int x = cx - 6; x <= cx + 6; x++) {
            for (int z = cz - 8; z <= cz + 10; z++) {
                if (this.sd(x, z) > 1) {
                    this.set(x, 0, z, "spruce_planks");
                    for (int y = this.yLo; y < 0; y++) {
                        this.setIfEmpty(x, y, z, Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? "spruce_log[axis=y]" : null);
                    }
                }
            }
        }

        // the frame, the wheel and the jib
        for (int y = 1; y <= 14; y++) {
            this.set(cx - 2, y, cz, "spruce_log[axis=y]");
            this.set(cx + 2, y, cz, "spruce_log[axis=y]");
        }

        for (double a = 0; a < Math.PI * 2; a += 0.12) {
            this.set(cx, 7 + (int)Math.round(Math.sin(a) * 5.0), cz + (int)Math.round(Math.cos(a) * 5.0), "spruce_planks");
        }

        this.line(cx, 7, cz - 5, cx, 7, cz + 5, "spruce_log[axis=z]");
        this.line(cx, 7, cz, cx, 7, cz, "spruce_log[axis=x]");
        this.line(cx, 14, cz, cx, 18, cz + 14, "spruce_log[axis=z]");
        for (int y = 6; y <= 17; y++) {
            this.set(cx, y, cz + 14, "iron_chain[axis=y]");
        }

        this.box(cx - 1, 3, cz + 13, cx + 1, 5, cz + 15, "barrel[facing=up]");
        for (int i = 0; i < 10; i++) {
            int x = cx - 5 + (int)(this.h(361L, i, 0, 0) * 10);
            int z = cz - 7 + (int)(this.h(363L, i, 0, 0) * 6);
            if (this.sd(x, z) > 2) {
                this.set(x, 1, z, i % 3 == 0 ? "barrel[facing=up]" : "spruce_planks");
                if (i % 4 == 0) {
                    this.set(x, 2, z, "spruce_planks");
                }
            }
        }

        // rowing boats tied up by the dock (two hulls of slabs)
        for (int k = 0; k < 2; k++) {
            int bx = -this.hx + 6 + k * 10;
            int bz = this.hz + 4;
            for (int x = bx - 2; x <= bx + 2; x++) {
                this.set(x, SEA, bz, "spruce_slab[type=top]");
                this.set(x, SEA + 1, bz - 1, "spruce_slab[type=bottom]");
                this.set(x, SEA + 1, bz + 1, "spruce_slab[type=bottom]");
            }
        }
    }

    // ================================================================== STORMPEAK SANCTUARY

    private void stormpeak() {
        this.floor((x, y, z) -> {
            double d = this.sd(x, z);
            if (d > -1.6) {
                return "waxed_cut_copper";
            } else if (d > -2.6) {
                return "polished_deepslate";
            }

            double r = Math.hypot(x, z * 1.2);
            if (r < 6.0) {
                return Math.floorMod((int)Math.floor(Math.atan2(z, x) * 4.0 / Math.PI), 2) == 0 ? "polished_deepslate" : "deepslate_tiles";
            }

            return this.h(401L, x, 0, z) < 0.18 ? "polished_tuff" : (Math.floorMod(x + z, 2) == 0 ? "deepslate_tiles" : "polished_deepslate");
        }, "deepslate");
        this.spMountain();
        this.spMonastery();
        this.spBellTower();
        this.spBridge();
        this.spFlags();
        this.spPines();
    }

    /** Layered rock: stone, andesite, tuff and deepslate in tilted bands, calcite streaks. */
    private String spRock(int x, int y, int z) {
        double w = this.n(411L, x * 0.08, z * 0.08) * 6.0 + x * 0.08;
        int band = Math.floorMod((int)Math.floor((y + w) / 2.5), 7);
        return switch (band) {
            case 0, 4 -> "stone";
            case 1 -> "andesite";
            case 2 -> "tuff";
            case 3 -> "deepslate";
            case 5 -> this.h(413L, x, y, z) < 0.5 ? "calcite" : "stone";
            default -> "cobbled_deepslate";
        };
    }

    /** The plateau with a parapet, the cliffs falling away round it, rock spires rising out of the clouds. */
    private void spMountain() {
        Mat rock = this::spRock;
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.5) {
                    continue;
                }

                double r = Math.hypot(x, z);
                double plateau = 12 + this.n(421L, x * 0.06, z * 0.06) * 6;
                if (z < -this.hz - 4) {
                    plateau = 40;
                }

                if (d < plateau) {
                    // the plateau: slate paving, then broken ground toward the edge; it rises to the monastery
                    int top = z < -this.hz - 8 ? (int)Math.min(18, Math.max(0, (-z - this.hz - 8) * 0.6)) : 0;
                    String t = d < 6 ? (this.h(423L, x, 0, z) < 0.25 ? "polished_tuff" : "polished_deepslate")
                        : (this.n(425L, x * 0.2, z * 0.2) > 0.6 ? "moss_block" : "snow_block");
                    this.column(x, z, top, t, rock.at(x, top - 1, z), rock);
                    // the cliff under the plateau's edge tapers down
                    int bottom = this.yLo + (int)Math.max(0, (d - plateau + 10) * 0.8);
                    for (int y = this.yLo; y < bottom; y++) {
                        this.set(x, y, z, null);
                    }
                }
            }
        }

        // the parapet round the plateau's edge (outside the floor) where it is open
        for (int x = -this.hx - 10; x <= this.hx + 10; x++) {
            for (int z = -this.hz - 4; z <= this.hz + 12; z++) {
                double d = this.sd(x, z);
                if (d > 6 && d < 7 && z > -this.hz - 4) {
                    this.set(x, 1, z, "polished_deepslate_wall");
                    if (Math.floorMod(x + z, 9) == 0) {
                        this.set(x, 2, z, "polished_deepslate_wall");
                        this.set(x, 3, z, "lantern[hanging=false]");
                    }
                }
            }
        }

        // rock spires out of the clouds all round
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2.0 / 18 + this.h(431L, i, 0, 0) * 0.3;
            double r = 60 + this.h(433L, i, 0, 0) * 22;
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            if (z < -40 && Math.abs(x) < 30) {
                continue;
            }

            int h = 10 + (int)(this.h(435L, i, 1, 0) * 40);
            double base = 6 + this.h(437L, i, 2, 0) * 6;
            for (int y = this.yLo; y <= h; y++) {
                double t = (double)(y - this.yLo) / (h - this.yLo);
                double rr = base * (1.0 - t * 0.75) + this.n(439L, y * 0.2, i) * 1.5;
                for (int dx = -(int)Math.ceil(rr); dx <= Math.ceil(rr); dx++) {
                    for (int dz = -(int)Math.ceil(rr); dz <= Math.ceil(rr); dz++) {
                        if (dx * dx + dz * dz <= rr * rr) {
                            this.set(x + dx, y, z + dz, y >= h - 1 ? "snow_block" : this.spRock(x + dx, y, z + dz));
                        }
                    }
                }
            }

            if (this.h(441L, i, 3, 0) < 0.5) {
                this.pine(x, h + 1, z, 5 + (int)(this.h(443L, i, 4, 0) * 4), true);
            }
        }

        // a sea of clouds below
        for (int i = 0; i < 40; i++) {
            double a = this.h(451L, i, 0, 0) * Math.PI * 2.0;
            double r = 45 + this.h(453L, i, 0, 0) * 40;
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            this.cloudPuff(x, -8 + (int)(this.h(455L, i, 1, 0) * 4), z, 4 + this.h(457L, i, 2, 0) * 5);
        }
    }

    private void cloudPuff(int x, int y, int z, double r) {
        for (int k = 0; k < 4; k++) {
            double ox = x + (this.h(461L, x, k, z) - 0.5) * r * 1.6;
            double oz = z + (this.h(463L, x, k, z) - 0.5) * r * 1.6;
            int n = (int)Math.ceil(r);
            for (int dx = -n; dx <= n; dx++) {
                for (int dy = -n / 2; dy <= n / 2; dy++) {
                    for (int dz = -n; dz <= n; dz++) {
                        if (dx * dx + dy * dy * 4 + dz * dz <= r * r) {
                            this.setIfEmpty((int)Math.round(ox) + dx, y + dy, (int)Math.round(oz) + dz, dy > 0 ? "white_concrete" : "white_wool");
                        }
                    }
                }
            }
        }
    }

    /** The monastery: three halls stepping up the rock behind the floor, white battered walls, red bands, gold roofs. */
    private void spMonastery() {
        int[][] halls = {{0, -this.hz - 16, 26, 12, 0}, {-30, -this.hz - 28, 16, 10, 7}, {28, -this.hz - 30, 18, 10, 9}, {0, -this.hz - 40, 22, 12, 13}};
        for (int[] hl : halls) {
            int cx = hl[0];
            int cz = hl[1];
            int w = hl[2];
            int d = hl[3];
            int base = hl[4];
            int h = 12 + (base > 10 ? 4 : 0);
            // a stone podium up to the hall's floor
            this.fill(cx - w / 2 - 1, this.yLo, cz - d / 2 - 1, cx + w / 2 + 1, base, cz + d / 2 + 1, (x, y, z) -> this.spRock(x, y, z));
            for (int y = base + 1; y <= base + h; y++) {
                // battered walls: one block narrower every 6
                int in = (y - base - 1) / 6;
                for (int x = cx - w / 2 + in; x <= cx + w / 2 - in; x++) {
                    for (int z = cz - d / 2 + in; z <= cz + d / 2 - in; z++) {
                        boolean shell = x == cx - w / 2 + in || x == cx + w / 2 - in || z == cz - d / 2 + in || z == cz + d / 2 - in;
                        String b;
                        if (!shell) {
                            b = "cave_air";
                        } else if (y >= base + h - 2) {
                            b = y == base + h ? "red_nether_bricks" : "red_terracotta";
                        } else if ((y - base) % 4 == 2 && Math.floorMod(x + z, 4) == 0) {
                            b = "black_stained_glass_pane";
                        } else {
                            b = this.h(471L, x, y, z) < 0.08 ? "calcite" : "white_concrete";
                        }

                        this.set(x, y, z, b);
                    }
                }
            }

            // window frames (dark lintels) and the door in the front, toward the floor
            int fz = cz + d / 2;
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int y = base + 1; y <= base + 5; y++) {
                    this.set(x, y, fz, Math.abs(x - cx) == 2 || y == base + 5 ? "red_terracotta" : (y <= base + 4 ? null : "red_terracotta"));
                }
            }

            // a flat roof with a gilded pavilion on top
            int ry = base + h + 1;
            int in = (h - 1) / 6;
            this.fill(cx - w / 2 + in - 1, ry, cz - d / 2 + in - 1, cx + w / 2 - in + 1, ry, cz + d / 2 - in + 1, of("dark_oak_planks"));
            this.fill(cx - 3, ry + 1, cz - 2, cx + 3, ry + 3, cz + 2, of("red_terracotta"));
            this.pyramidRoof(cx - 4, cz - 3, cx + 4, cz + 3, ry + 4, "waxed_cut_copper", of("gold_block"));
            this.set(cx, ry + 9, cz, "gold_block");
            this.set(cx, ry + 10, cz, "end_rod[facing=up]");
            // gilded finials on the roof corners
            for (int s = -1; s <= 1; s += 2) {
                for (int t = -1; t <= 1; t += 2) {
                    this.set(cx + s * (w / 2 - in), ry + 1, cz + t * (d / 2 - in), "gold_block");
                    this.set(cx + s * (w / 2 - in), ry + 2, cz + t * (d / 2 - in), "lightning_rod");
                }
            }
        }

        // stairs from the plateau up between the halls
        for (int k = 0; k < 13; k++) {
            for (int x = -2; x <= 2; x++) {
                this.set(x, k, -this.hz - 23 - k, stairs("polished_deepslate", "south", false));
                for (int y = this.yLo; y < k; y++) {
                    this.setIfEmpty(x, y, -this.hz - 23 - k, "polished_deepslate");
                }
            }
        }
    }

    /** A tall bell tower on the west of the plateau with a copper pagoda roof and a bell. */
    private void spBellTower() {
        int tx = -this.hx - 10;
        int tz = -this.hz + 2;
        this.fill(tx - 4, this.yLo, tz - 4, tx + 4, 0, tz + 4, (x, y, z) -> this.spRock(x, y, z));
        for (int y = 1; y <= 34; y++) {
            for (int x = tx - 4; x <= tx + 4; x++) {
                for (int z = tz - 4; z <= tz + 4; z++) {
                    boolean shell = Math.abs(x - tx) == 4 || Math.abs(z - tz) == 4;
                    boolean corner = Math.abs(x - tx) == 4 && Math.abs(z - tz) == 4;
                    if (!shell) {
                        this.set(x, y, z, "cave_air");
                    } else if (corner) {
                        this.set(x, y, z, "stripped_dark_oak_log[axis=y]");
                    } else if (y > 26 && y < 33) {
                        this.set(x, y, z, Math.abs(x - tx) <= 1 && Math.abs(z - tz) == 4 || Math.abs(z - tz) <= 1 && Math.abs(x - tx) == 4 ? null : "white_concrete");
                    } else {
                        this.set(x, y, z, y % 9 == 0 ? "red_terracotta" : "white_concrete");
                    }
                }
            }
        }

        this.set(tx, 32, tz, "dark_oak_fence");
        this.set(tx, 31, tz, "bell[attachment=ceiling,facing=north]");
        // three copper roofs, the eaves flaring out
        int y = 34;
        for (int k = 0; k < 3; k++) {
            int half = 6 - k;
            int top = this.pyramidRoof(tx - half, tz - half, tx + half, tz + half, y, "waxed_oxidized_cut_copper", of("waxed_oxidized_copper"));
            y += 3;
            if (k < 2) {
                this.fill(tx - 2, y, tz - 2, tx + 2, y + 2, tz + 2, of("white_concrete"));
                y += 3;
            }
        }

        this.set(tx, y + 2, tz, "gold_block");
        this.set(tx, y + 3, tz, "lightning_rod");
    }

    /** A rope bridge from the east edge of the plateau to a spire with a stupa and a waterfall. */
    private void spBridge() {
        int ix = this.hx + 44;
        int iz = 4;
        // the spire island
        for (int y = this.yLo; y <= 2; y++) {
            double rr = 9 - Math.max(0, -y) * 0.15;
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    if (dx * dx + dz * dz <= rr * rr + this.n(481L, dx * 0.3, dz * 0.3) * 6) {
                        this.set(ix + dx, y, iz + dz, y == 2 ? "moss_block" : this.spRock(ix + dx, y, iz + dz));
                    }
                }
            }
        }

        // the stupa: a stepped base, a white dome, a gilded spire
        for (int k = 0; k < 3; k++) {
            this.fill(ix - 4 + k, 3 + k, iz - 4 + k, ix + 4 - k, 3 + k, iz + 4 - k, of(k == 2 ? "red_terracotta" : "white_concrete"));
        }

        this.ellipsoid(ix, 7, iz, 3.6, 3.4, 3.6, "white_concrete");
        this.fill(ix - 1, 10, iz - 1, ix + 1, 11, iz + 1, of("gold_block"));
        for (int y = 12; y <= 17; y++) {
            this.set(ix, y, iz, y % 2 == 0 ? "gold_block" : "iron_chain[axis=y]");
        }

        this.set(ix, 18, iz, "end_rod[facing=up]");
        // the waterfall pouring off the island's side into the clouds
        for (int y = this.yLo; y <= 2; y++) {
            for (int dz = -1; dz <= 1; dz++) {
                this.set(ix + 9, y, iz + dz, "water");
            }
        }

        // the bridge: planks hanging in a curve, rope rails, posts at both ends
        int x0 = this.hx + 12;
        int x1 = ix - 9;
        for (int x = x0; x <= x1; x++) {
            double t = (x - x0) / (double)(x1 - x0);
            int y = (int)Math.round(-Math.sin(t * Math.PI) * 3.0);
            for (int dz = -1; dz <= 1; dz++) {
                this.set(x, y, iz + dz, Math.floorMod(x, 3) == 0 ? "spruce_slab[type=bottom]" : "spruce_planks");
            }

            this.set(x, y + 1, iz - 2, "spruce_fence");
            this.set(x, y + 1, iz + 2, "spruce_fence");
        }

        for (int s = -1; s <= 1; s += 2) {
            for (int y = 1; y <= 4; y++) {
                this.set(x0, y, iz + s * 2, "spruce_log[axis=y]");
                this.set(x1, y, iz + s * 2, "spruce_log[axis=y]");
            }

            this.set(x0, 5, iz + s * 2, "lantern[hanging=false]");
            this.set(x1, 5, iz + s * 2, "lantern[hanging=false]");
        }

        // a path from the floor's edge to the bridge
        for (int x = this.hx + 2; x < x0; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                this.set(x, 0, iz + dz, "polished_tuff");
            }
        }
    }

    /** Strings of prayer flags between poles round the plateau and from the monastery to the bell tower. */
    private void spFlags() {
        String[] colors = {"blue", "white", "red", "green", "yellow"};
        int[][] lines = {{-this.hx - 10, 34, -this.hz + 2, 0, 30, -this.hz - 16}, {0, 30, -this.hz - 16, 28, 26, -this.hz - 30},
            {-this.hx - 6, 8, this.hz + 8, this.hx + 6, 8, this.hz + 8}, {-this.hx - 10, 20, -this.hz + 2, -30, 22, -this.hz - 28}};
        for (int[] l : lines) {
            double len = Math.hypot(l[3] - l[0], l[5] - l[2]);
            int n = (int)Math.ceil(len);
            for (int i = 0; i <= n; i++) {
                double t = (double)i / n;
                double sag = Math.sin(t * Math.PI) * Math.min(4.0, len / 12.0);
                int x = (int)Math.round(l[0] + (l[3] - l[0]) * t);
                int y = (int)Math.round(l[1] + (l[4] - l[1]) * t - sag);
                int z = (int)Math.round(l[2] + (l[5] - l[2]) * t);
                if (this.sd(x, z) < 1.5 && y < 49) {
                    continue;
                }

                this.setIfEmpty(x, y, z, i % 2 == 0 ? colors[(i / 2) % colors.length] + "_wool" : "iron_chain[axis=x]");
            }
        }

        // poles for the line in front of the floor
        for (int s = -1; s <= 1; s += 2) {
            for (int y = 1; y <= 8; y++) {
                this.set(s * (this.hx + 6), y, this.hz + 8, "dark_oak_fence");
            }
        }
    }

    /** Snowy pines and junipers on the plateau's edges and down the slopes. */
    private void spPines() {
        for (int i = 0; i < 40; i++) {
            double a = this.h(491L, i, 0, 0) * Math.PI * 2.0;
            double r = 32 + this.h(493L, i, 0, 0) * 18;
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r * 0.8);
            if (this.sd(x, z) < 8 || z < -this.hz - 8 && Math.abs(x) < 40) {
                continue;
            }

            int top = this.topAt(x, z);
            if (top < -3 || this.get(x, top, z) == null) {
                continue;
            }

            String t = this.get(x, top, z);
            if (t.startsWith("snow") || t.startsWith("moss") || t.startsWith("grass")) {
                this.pine(x, top + 1, z, 6 + (int)(this.h(495L, i, 1, 0) * 6), true);
            }
        }
    }
}
