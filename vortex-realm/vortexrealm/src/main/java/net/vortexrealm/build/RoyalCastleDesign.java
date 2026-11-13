package net.vortexrealm.build;

/**
 * "Royal Castle": a big hub on a hilly floating island. A walled castle on a raised mound in the middle: curtain walls
 * with a walkway and battlements, round corner towers with slate cones, a gatehouse with a portcullis, and a keep with
 * turrets and a tall great tower. Inside, a paved courtyard with a tiered fountain where the NPCs stand. Around it:
 * rolling hills with woods, a lake with a waterfall, a fort on the eastern hill linked to the castle by an arched
 * bridge, a watchtower on the western hill and a small market (three stalls) on the road south of the gate.
 */
public final class RoyalCastleDesign extends MegaVoxels {
    /** Height of the castle mound (courtyard level). */
    private static final int M = 8;
    private static final String WALL = "stone_bricks";
    private static final String ROOF = "deepslate_tiles";
    private final long seed;
    private final double[][] height;
    private final boolean[][] road;
    private final boolean[][] water;
    private final int[] fort = new int[]{94, -10};
    private final int[] watch = new int[]{-92, -26};
    private final int[] lake = new int[]{-70, 64};

    public RoyalCastleDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -48, 92, seed);
        this.seed = seed;
        int n = 2 * this.R + 1;
        this.height = new double[n][n];
        this.road = new boolean[n][n];
        this.water = new boolean[n][n];
        this.roads();
        this.terrain();
        this.lakeAndFalls();
        this.castleWalls();
        this.gatehouse();
        this.keep();
        this.courtyard();
        this.bridgeAndFort();
        this.watchtower();
        this.market();
        this.nature();
        this.spawn = new double[]{0.5, M + 1.0, 28.5, 180.0};
        this.feature("deco:crown", 0, M + 13.5, 8, 0, 30);
        this.feature("holo:welcome:FRAME", 0, M + 3.2, 20, 0, 30);
        this.feature("holo:tiers:HOLO", 14, M + 2.2, 24, 0, 30);
        this.feature("holo:discord:MARQUEE", -14, M + 2.2, 24, 0, 30);
        this.feature("lb:overall:PODIUM:ELO", 0, M + 1.0, -3, 0, 30);
        this.feature("lb:overall:FRAME:WINS", -10, M + 1.0, -5, -6, 30);
        this.feature("lb:overall:FRAME:WINRATE", 10, M + 1.0, -5, 6, 30);
        this.finish();
    }

    @Override
    public String name() {
        return "Royal Castle";
    }

    // ------------------------------------------------------------------ helpers

    private int ix(int x) {
        return x + this.R;
    }

    private double h(int x, int z) {
        return x >= -this.R && x <= this.R && z >= -this.R && z <= this.R ? this.height[this.ix(x)][this.ix(z)] : -99.0;
    }

    private int top(int x, int z) {
        return (int)Math.floor(this.h(x, z));
    }

    private static double smooth(double e0, double e1, double v) {
        double t = Math.max(0.0, Math.min(1.0, (v - e0) / (e1 - e0)));
        return t * t * (3.0 - 2.0 * t);
    }

    /** Squarish distance: flat inside the castle square, round further out. */
    private static double md(double x, double z) {
        return Math.pow(Math.pow(Math.abs(x), 4.0) + Math.pow(Math.abs(z), 4.0), 0.25);
    }

    private static double segDist(double px, double pz, double x0, double z0, double x1, double z1) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double t = Math.max(0.0, Math.min(1.0, ((px - x0) * dx + (pz - z0) * dz) / (dx * dx + dz * dz)));
        return dist(px - (x0 + dx * t), pz - (z0 + dz * t));
    }

    private static final double[][] ROADS = new double[][]{
        {0.0, 44.0, 0.0, 118.0},
        {0.0, 68.0, -54.0, 34.0},
        {-54.0, 34.0, -88.0, -18.0}
    };

    private double roadDist(double x, double z) {
        double best = 99.0;

        for (double[] r : ROADS) {
            best = Math.min(best, segDist(x, z, r[0], r[1], r[2], r[3]));
        }

        return best;
    }

    private void roads() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                this.road[this.ix(x)][this.ix(z)] = this.roadDist(x, z) <= 2.6 && md(x, z) > 44.0;
            }
        }
    }

    // ------------------------------------------------------------------ terrain

    private void terrain() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = dist(x, z);
                double edge = this.R - 7 + (noise(this.seed, x * 0.8, z * 0.8) - 0.5) * 10.0;
                if (d > edge) {
                    this.height[this.ix(x)][this.ix(z)] = -99.0;
                } else {
                    double m = md(x, z);
                    double hh;
                    if (m <= 49.0) {
                        hh = M;
                    } else {
                        double slope = M * (1.0 - smooth(49.0, 64.0, m));
                        double n1 = noise(this.seed + 3L, x * 0.32, z * 0.32);
                        double hill = Math.pow(Math.max(0.0, n1 - 0.32) / 0.68, 1.35) * 24.0 * smooth(60.0, 88.0, m);
                        double rough = (noise(this.seed + 9L, x * 1.4, z * 1.4) - 0.5) * 2.4 * smooth(58.0, 70.0, m);
                        hh = Math.max(slope, slope + hill + rough);
                        // the roads stay low and smooth
                        double rd = this.roadDist(x, z);
                        if (rd < 9.0) {
                            double keep = smooth(3.0, 9.0, rd);
                            hh = slope + (hill + rough) * keep;
                        }

                        // the island edge drops away
                        hh *= 1.0 - smooth(edge - 16.0, edge - 2.0, d) * 0.85;
                        // plateaus for the fort and the watchtower
                        double df = dist(x - this.fort[0], z - this.fort[1]);
                        if (df < 30.0) {
                            hh = Math.max(hh, M - smooth(17.0, 30.0, df) * M + 0.4);
                            if (df < 17.0) {
                                hh = M;
                            }
                        }

                        double dw = dist(x - this.watch[0], z - this.watch[1]);
                        if (dw < 24.0) {
                            hh = Math.max(hh, 12.0 - smooth(11.0, 24.0, dw) * 11.0);
                            if (dw < 11.0) {
                                hh = 12.0;
                            }
                        }
                    }

                    // the ramp from the gate down to the road
                    if (Math.abs(x) <= 4 && z >= 44 && z <= 64) {
                        hh = M - (z - 44) * M / 20.0;
                    }

                    this.height[this.ix(x)][this.ix(z)] = hh;
                }
            }
        }

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double hh = this.h(x, z);
                if (!(hh < -50.0)) {
                    int t = (int)Math.floor(hh);
                    double d = dist(x, z);
                    double f = Math.max(0.0, 1.0 - d / (this.R - 4.0));
                    int bottom = -(int)Math.round(6.0 + Math.pow(f, 0.7) * 40.0 * (0.8 + 0.3 * noise(this.seed + 5L, x * 1.3, z * 1.3)));
                    bottom = Math.max(this.yLo + 1, bottom);
                    double slope = Math.max(
                        Math.max(Math.abs(this.h(x + 1, z) - hh), Math.abs(this.h(x - 1, z) - hh)),
                        Math.max(Math.abs(this.h(x, z + 1) - hh), Math.abs(this.h(x, z - 1) - hh))
                    );
                    boolean steep = slope >= 2.5 && slope < 50.0;

                    for (int y = bottom; y <= t; y++) {
                        String b;
                        if (y == t) {
                            b = steep ? (noise(this.seed + 13L, x, z) > 0.5 ? "andesite" : "stone") : "grass_block";
                        } else if (y >= t - 3) {
                            b = steep ? "stone" : "dirt";
                        } else {
                            double v = noise(this.seed + 17L, x * 2 + y, z * 2 - y);
                            b = v > 0.72 ? "andesite" : (v < 0.22 ? "tuff" : "stone");
                        }

                        this.set(x, y, z, b);
                    }

                    if (this.road[this.ix(x)][this.ix(z)]) {
                        boolean edge = this.roadDist(x, z) > 1.8;
                        this.set(x, t, z, edge ? "coarse_dirt" : (noise(this.seed + 21L, x * 2.0, z * 2.0) > 0.7 ? "gravel" : "dirt_path"));
                        if (hh - t >= 0.5 && Math.abs(x) <= 4 && z >= 44 && z <= 64) {
                            this.set(x, t + 1, z, "stone_brick_slab[type=bottom]");
                        }
                    }
                }
            }
        }

        // the gate ramp is paved, with walls at the sides
        for (int z = 44; z <= 64; z++) {
            for (int x = -4; x <= 4; x++) {
                int t = this.top(x, z);
                if (Math.abs(x) == 4) {
                    this.set(x, t, z, WALL);
                    this.set(x, t + 1, z, "stone_brick_wall");
                } else if (this.empty(x, t + 1, z)) {
                    this.set(x, t, z, (x + z) % 3 == 0 ? "cobblestone" : "stone_bricks");
                }
            }
        }
    }

    private void lakeAndFalls() {
        int lx = this.lake[0];
        int lz = this.lake[1];

        for (int x = lx - 22; x <= lx + 22; x++) {
            for (int z = lz - 22; z <= lz + 22; z++) {
                double e = Math.sqrt((x - lx) * (x - lx) / 256.0 + (z - lz) * (z - lz) / 169.0) + (noise(this.seed + 31L, x * 0.9, z * 0.9) - 0.5) * 0.25;
                if (e < 1.35 && this.h(x, z) > -50.0) {
                    int t = this.top(x, z);
                    if (e < 1.0) {
                        int bed = -2 - (int)Math.round((1.0 - e) * 4.0);

                        for (int y = bed + 1; y <= Math.max(t, 0) + 1; y++) {
                            this.set(x, y, z, null);
                        }

                        this.set(x, bed, z, e < 0.6 ? "clay" : "sand");

                        for (int y = bed + 1; y <= -1; y++) {
                            this.set(x, y, z, "water");
                        }

                        this.water[this.ix(x)][this.ix(z)] = true;
                        this.height[this.ix(x)][this.ix(z)] = -1.0;
                    } else if (t <= 2) {
                        this.set(x, t, z, "sand");
                    }
                }
            }
        }

        // a rock cliff on the north-west shore with a waterfall into the lake
        int rx = lx - 12;
        int rz = lz - 14;

        for (int x = -9; x <= 9; x++) {
            for (int z = -7; z <= 7; z++) {
                double e = Math.sqrt(x * x / 81.0 + z * z / 49.0);
                double top = (1.0 - e) * 22.0 + (noise(this.seed + 41L, x, z) - 0.5) * 5.0;

                for (int y = 0; y <= top; y++) {
                    double v = noise(this.seed + 43L, (rx + x) * 2.0 + y, (rz + z) * 2.0);
                    this.set(rx + x, y, rz + z, y >= top - 1 && v > 0.45 ? "moss_block" : (v > 0.7 ? "andesite" : (v < 0.2 ? "mossy_cobblestone" : "stone")));
                }

                if (top > 14.0 && this.rnd.nextDouble() < 0.12) {
                    this.conifer(rx + x, (int)top + 1, rz + z, "spruce_log", "spruce", 6 + this.rnd.nextInt(4));
                }
            }
        }

        for (int y = -1; y <= 17; y++) {
            this.set(rx + 3, y, rz + 7, "water");
            this.set(rx + 4, y, rz + 7, "water");
        }

        this.box(rx + 2, 18, rz + 5, rx + 5, 18, rz + 7, "water");
    }

    // ------------------------------------------------------------------ castle

    private boolean inWallGap(int x, int z, int y) {
        // gate (south), postern to the bridge (east)
        return z >= 36 && Math.abs(x) <= 3 && y <= M + 8 || x >= 36 && z >= -12 && z <= -8 && y <= M + 5;
    }

    private void castleWalls() {
        int top = M + 14;

        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                boolean ring = Math.max(Math.abs(x), Math.abs(z)) >= 36;
                if (ring) {
                    for (int y = -4; y <= top; y++) {
                        if (!this.inWallGap(x, z, y) || y <= M) {
                            double v = noise(this.seed + 51L, x * 2.3 + y, z * 2.3 - y);
                            String b = v > 0.8 ? "mossy_stone_bricks" : (v < 0.12 ? "cracked_stone_bricks" : WALL);
                            if (y == M + 1 || y == top) {
                                b = "polished_andesite";
                            }

                            this.set(x, y, z, b);
                        }
                    }

                    boolean outer = Math.max(Math.abs(x), Math.abs(z)) == 40;
                    if (outer && Math.floorMod(x + z, 2) == 0) {
                        this.set(x, top + 1, z, WALL);
                        this.set(x, top + 2, z, "stone_brick_slab[type=bottom]");
                    } else if (outer) {
                        this.set(x, top + 1, z, "stone_brick_wall");
                    }

                    // arrow slits on the outside
                    if (outer && (y(x, z) % 6 == 0)) {
                        this.set(x, M + 9, z, "dark_oak_trapdoor[facing=north,half=top,open=true]".replace("north", facing(x, z)));
                    }
                }
            }
        }

        // banners on the outer wall
        for (int i = -30; i <= 30; i += 15) {
            this.hangBanner(i, M + 12, 41, "south", i % 30 == 0 ? "red" : "blue");
            this.hangBanner(i, M + 12, -41, "north", i % 30 == 0 ? "red" : "blue");
            this.hangBanner(41, M + 12, i, "east", i % 30 == 0 ? "red" : "blue");
            this.hangBanner(-41, M + 12, i, "west", i % 30 == 0 ? "red" : "blue");
        }

        // round corner towers with slate cones
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                this.roundTower(sx * 40, sz * 40, 7.0, -6, M + 24, 14);
            }
        }

        // square mid towers on the east, west and north walls
        this.squareTower(40, 18, 5, M + 20);
        this.squareTower(-40, 0, 5, M + 20);
        this.squareTower(-40, -24, 4, M + 18);
        this.squareTower(20, -40, 5, M + 20);
        this.squareTower(-20, -40, 5, M + 20);
    }

    private static int y(int x, int z) {
        return Math.abs(x) + Math.abs(z);
    }

    private void hangBanner(int x, int yTop, int z, String face, String colour) {
        int dx = face.equals("east") ? 0 : 1;
        int dz = face.equals("east") || face.equals("west") ? 1 : 0;

        for (int w = -1; w <= 1; w++) {
            for (int k = 0; k < 7; k++) {
                int bx = x + (dx == 1 && dz == 0 ? w : 0);
                int bz = z + (dz == 1 ? w : 0);
                String s = k == 6 ? "yellow_wool" : ((k == 2 || k == 3) && w == 0 ? "yellow_wool" : colour + "_wool");
                this.set(bx, yTop - k, bz, s);
            }
        }
    }

    private void roundTower(int x, int z, double r, int y0, int y1, int cone) {
        for (int y = y0; y <= y1; y++) {
            this.disc(x, z, y, r, y > M && y < y1 ? "air_marker" : WALL);
            this.ring(x, z, y, r - 1.0, r, (y - y0) % 7 == 0 ? "polished_andesite" : WALL);
        }

        // clear the hollow (marker) and put floors every 7 blocks
        for (int y = y0; y <= y1; y++) {
            for (int dx = (int)-r; dx <= r; dx++) {
                for (int dz = (int)-r; dz <= r; dz++) {
                    if ("air_marker".equals(this.get(x + dx, y, z + dz))) {
                        this.set(x + dx, y, z + dz, (y - M) % 7 == 0 ? "spruce_planks" : null);
                    }
                }
            }
        }

        // windows
        for (int y = M + 4; y < y1 - 2; y += 7) {
            for (int a = 0; a < 4; a++) {
                int wx = x + (int)Math.round(Math.cos(a * Math.PI / 2.0 + 0.4) * (r - 0.5));
                int wz = z + (int)Math.round(Math.sin(a * Math.PI / 2.0 + 0.4) * (r - 0.5));
                this.set(wx, y, wz, "glass_pane");
                this.set(wx, y + 1, wz, "glass_pane");
            }
        }

        // battlement ring, then the cone and a flag
        this.ring(x, z, y1 + 1, r, r + 1.0, "polished_andesite");

        for (int a = 0; a < 24; a += 2) {
            double ang = a * Math.PI / 12.0;
            this.set(x + (int)Math.round(Math.cos(ang) * (r + 0.6)), y1 + 2, z + (int)Math.round(Math.sin(ang) * (r + 0.6)), WALL);
        }

        this.cone(x, z, y1 + 2, y1 + 2 + cone, r + 0.2, ROOF);
        this.set(x, y1 + 3 + cone, z, "spruce_fence");
        this.set(x, y1 + 4 + cone, z, "spruce_fence");
        this.set(x + 1, y1 + 4 + cone, z, "red_wool");
        this.set(x + 2, y1 + 4 + cone, z, "red_wool");
        this.set(x + 1, y1 + 3 + cone, z, "red_wool");
    }

    private void squareTower(int x, int z, int h, int y1) {
        for (int y = -4; y <= y1; y++) {
            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
                    boolean corner = Math.abs(dx) == h && Math.abs(dz) == h;
                    if (edge) {
                        String b = corner ? "polished_andesite" : WALL;
                        if (!corner && y > M + 2 && (y - M) % 6 >= 3 && (y - M) % 6 <= 4 && (dx == 0 || dz == 0)) {
                            b = "glass_pane";
                        }

                        this.set(x + dx, y, z + dz, b);
                    } else if (y > M) {
                        this.set(x + dx, y, z + dz, (y - M) % 6 == 0 ? "spruce_planks" : null);
                    } else {
                        this.set(x + dx, y, z + dz, WALL);
                    }
                }
            }
        }

        // pyramid roof
        for (int i = 0; i <= h + 1; i++) {
            int r = h + 1 - i;

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r || Math.abs(dz) == r || i == h + 1) {
                        this.set(x + dx, y1 + 1 + i, z + dz, ROOF);
                    }
                }
            }
        }

        this.set(x, y1 + h + 3, z, "lightning_rod");
    }

    private void gatehouse() {
        int g = 40;

        for (int sx = -1; sx <= 1; sx += 2) {
            this.roundTower(sx * 9, g, 5.0, -4, M + 22, 10);
        }

        // gate block between the towers
        for (int x = -6; x <= 6; x++) {
            for (int z = g - 4; z <= g + 3; z++) {
                for (int y = -2; y <= M + 17; y++) {
                    boolean passage = Math.abs(x) <= 3 && y > M && y <= M + 7 + (Math.abs(x) <= 1 ? 1 : 0);
                    if (passage) {
                        this.set(x, y, z, null);
                    } else {
                        this.set(x, y, z, y == M + 17 ? "polished_andesite" : WALL);
                    }
                }
            }

            if (Math.floorMod(x, 2) == 0) {
                this.set(x, M + 18, g + 3, WALL);
            }
        }

        // raised portcullis and the floor of the passage
        for (int x = -3; x <= 3; x++) {
            this.set(x, M + 7, g + 3, "iron_bars");
            this.set(x, M + 8, g + 3, Math.abs(x) <= 1 ? "iron_bars" : WALL);

            for (int z = g - 4; z <= g + 3; z++) {
                this.set(x, M, z, "stone_bricks");
            }
        }

        this.set(-4, M + 4, g + 4, "lantern[hanging=false]");
        this.set(4, M + 4, g + 4, "lantern[hanging=false]");
        this.set(-4, M + 3, g + 4, WALL);
        this.set(4, M + 3, g + 4, WALL);
        // coat of arms over the gate
        this.box(-2, M + 11, g + 4, 2, M + 14, g + 4, "red_wool");
        this.box(-1, M + 12, g + 4, 1, M + 13, g + 4, "yellow_wool");
        this.set(0, M + 15, g + 4, "gold_block");
    }

    private void keep() {
        int x0 = -20;
        int x1 = 20;
        int z0 = -36;
        int z1 = -10;
        int top = M + 22;

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;

                for (int y = M + 1; y <= top; y++) {
                    if (edge) {
                        String b = (y - M) % 8 == 0 ? "polished_andesite" : WALL;
                        boolean window = y > M + 3 && (y - M) % 8 >= 3 && (y - M) % 8 <= 6 && (z == z1 || z == z0 ? Math.floorMod(x, 6) == 3 : Math.floorMod(z, 6) == 3);
                        this.set(x, y, z, window ? "glass_pane" : b);
                    } else {
                        this.set(x, y, z, (y - M) % 8 == 0 ? "spruce_planks" : null);
                    }
                }

                // battlements on the roof
                this.set(x, top + 1, z, edge ? (Math.floorMod(x + z, 2) == 0 ? WALL : "stone_brick_wall") : "polished_andesite");
            }
        }

        // buttresses on the front
        for (int x = x0; x <= x1; x += 8) {
            for (int y = M + 1; y <= top - 4; y++) {
                this.set(x, y, z1 + 1, "polished_andesite");
            }

            this.set(x, top - 3, z1 + 1, "stone_brick_stairs[facing=north,half=bottom]");
        }

        // the great door with steps
        for (int x = -3; x <= 3; x++) {
            int arch = M + 6 + (Math.abs(x) <= 1 ? 1 : 0) - (Math.abs(x) == 3 ? 1 : 0);

            for (int y = M + 1; y <= arch; y++) {
                this.set(x, y, z1, Math.abs(x) <= 2 ? "dark_oak_planks" : "polished_andesite");
            }

            this.set(x, arch + 1, z1, "chiseled_stone_bricks");
        }

        this.set(-5, M + 4, z1 + 1, "lantern[hanging=false]");
        this.set(5, M + 4, z1 + 1, "lantern[hanging=false]");
        this.hangBanner(-11, top - 2, z1 + 1, "south", "red");
        this.hangBanner(11, top - 2, z1 + 1, "south", "red");

        // turrets on the corners and the great tower
        for (int x : new int[]{x0, x1}) {
            for (int z : new int[]{z0, z1}) {
                this.roundTower(x, z, 3.5, M + 1, top + 8, 9);
            }
        }

        this.roundTower(0, -24, 7.0, top - 2, top + 22, 18);
    }

    private void courtyard() {
        for (int x = -35; x <= 35; x++) {
            for (int z = -35; z <= 35; z++) {
                if (this.empty(x, M + 1, z) && !this.empty(x, M, z)) {
                    double n = noise(this.seed + 61L, x * 0.9, z * 0.9);
                    double fd = dist(x, z - 8);
                    boolean path = Math.abs(x) <= 3
                        || Math.abs(z - 8) <= 2 && Math.abs(x) <= 31
                        || fd <= 13.5 && fd >= 7.5
                        || Math.abs(x) >= 31
                        || z >= 30
                        || z <= -2;
                    String b;
                    if (fd < 7.5) {
                        b = "polished_andesite";
                    } else if (path) {
                        b = fd <= 13.5 && fd >= 12.5 ? "chiseled_stone_bricks" : (n > 0.8 ? "mossy_stone_bricks" : (Math.floorMod(x + z, 2) == 0 ? "polished_andesite" : "stone_bricks"));
                    } else {
                        // lawns with clipped hedges around them and flowers inside
                        boolean border = !this.lawn(x + 1, z) || !this.lawn(x - 1, z) || !this.lawn(x, z + 1) || !this.lawn(x, z - 1);
                        b = "grass_block";
                        if (border) {
                            this.set(x, M + 1, z, "oak_leaves[persistent=true]");
                        } else if (this.rnd.nextDouble() < 0.18) {
                            String[] fl = new String[]{"poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "short_grass", "short_grass"};
                            this.set(x, M + 1, z, fl[this.rnd.nextInt(fl.length)]);
                        }
                    }

                    this.set(x, M, z, b);
                }
            }
        }

        // tiered fountain
        int fz = 8;
        this.disc(0, fz, M, 6.5, "polished_andesite");
        this.ring(0, fz, M + 1, 5.6, 6.5, "stone_brick_wall");
        this.disc(0, fz, M, 5.5, "water");
        this.disc(0, fz, M - 1, 5.5, "stone_bricks");
        this.cylinder(0, fz, M, M + 4, 1.0, "chiseled_stone_bricks", false);
        this.disc(0, fz, M + 4, 2.6, "polished_andesite");
        this.disc(0, fz, M + 5, 1.8, "water");
        this.ring(0, fz, M + 5, 1.8, 2.6, "stone_brick_wall");
        this.set(0, M + 5, fz, "chiseled_stone_bricks");
        this.set(0, M + 6, fz, "chiseled_stone_bricks");
        this.set(0, M + 7, fz, "water");
        // trees and lamp posts in the courtyard corners
        for (int sx = -1; sx <= 1; sx += 2) {
            this.tree(sx * 22, M + 1, 24, "oak_log", "oak", 5, 3.4);
            this.tree(sx * 22, M + 1, 2, "birch_log", "birch", 6, 3.0);
            this.tree(sx * 24, M + 1, 14, "oak_log", "oak", 4, 2.8);

            for (int z = 30; z >= -4; z -= 8) {
                this.lampPost(sx * 6, M + 1, z, "polished_andesite", "dark_oak_fence", "lantern", 4);
            }
        }

        // NPC spots: along the main path between the gate and the fountain, facing it
        int[][] spots = new int[][]{{-7, 22}, {7, 22}, {-9, 14}, {9, 14}, {-12, 6}, {12, 6}, {-9, -1}, {9, -1}, {0, 21}};

        for (int[] s : spots) {
            this.set(s[0], M, s[1], "chiseled_stone_bricks");
            this.npc(s[0], M + 1.0, s[1]);
        }
    }

    /** Courtyard lawn cells (between the paths). */
    private boolean lawn(int x, int z) {
        double fd = dist(x, z - 8);
        return !(Math.abs(x) <= 3 || Math.abs(z - 8) <= 2 || fd <= 13.5 || Math.abs(x) >= 31 || z >= 30 || z <= -2);
    }

    // ------------------------------------------------------------------ outside

    private void bridgeAndFort() {
        int fx = this.fort[0];
        int fz = this.fort[1];
        int deck = M;
        // arched stone bridge from the east postern to the fort
        for (int x = 41; x <= fx - 13; x++) {
            for (int z = -12; z <= -8; z++) {
                this.set(x, deck, z, z == -12 || z == -8 ? "polished_andesite" : "stone_bricks");
                this.set(x, deck - 1, z, WALL);
                if (z == -12 || z == -8) {
                    this.set(x, deck + 1, z, Math.floorMod(x, 6) == 0 ? WALL : "stone_brick_wall");
                    if (Math.floorMod(x, 6) == 0) {
                        this.set(x, deck + 2, z, "lantern[hanging=false]");
                    }
                }
            }

            // arches: piers every 9 blocks, curved undersides between them
            int k = Math.floorMod(x - 41, 9);
            double arch = Math.sin(Math.PI * k / 9.0) * 3.0;

            for (int z = -12; z <= -8; z++) {
                int ground = this.top(x, z);
                int under = k == 0 ? ground : (int)Math.round(deck - 2 - arch);

                for (int y = Math.max(ground, under); y <= deck - 2; y++) {
                    this.set(x, y, z, k == 0 ? "polished_andesite" : WALL);
                }
            }
        }

        // the fort: walls, corner towers and a hall (FFA and events)
        int h = 13;

        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                this.set(fx + dx, M, fz + dz, Math.floorMod(dx + dz, 3) == 0 ? "cobblestone" : "stone_bricks");
                boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
                if (edge) {
                    boolean door = dx == -h && Math.abs(dz + 0) <= 2;
                    for (int y = M + 1; y <= M + 9; y++) {
                        if (!door || y > M + 5) {
                            this.set(fx + dx, y, fz + dz, y == M + 9 ? "polished_andesite" : WALL);
                        }
                    }

                    if (Math.floorMod(dx + dz, 2) == 0) {
                        this.set(fx + dx, M + 10, fz + dz, WALL);
                    }
                }
            }
        }

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                this.roundTower(fx + sx * h, fz + sz * h, 3.5, M - 3, M + 14, 8);
            }
        }

        // the hall with a gabled roof
        int hx0 = fx - 4;
        int hx1 = fx + 9;
        int hz0 = fz - 7;
        int hz1 = fz + 7;

        for (int x = hx0; x <= hx1; x++) {
            for (int z = hz0; z <= hz1; z++) {
                boolean edge = x == hx0 || x == hx1 || z == hz0 || z == hz1;

                for (int y = M + 1; y <= M + 8; y++) {
                    if (edge) {
                        boolean door = x == hx0 && Math.abs(z - fz) <= 1 && y <= M + 4;
                        boolean window = y >= M + 3 && y <= M + 5 && (x == hx0 || x == hx1 ? Math.floorMod(z, 4) == 0 : Math.floorMod(x, 4) == 0);
                        this.set(x, y, z, door ? null : (window ? "glass_pane" : (Math.floorMod(x + z, 7) == 0 ? "stripped_dark_oak_log[axis=y]" : "white_terracotta")));
                    } else {
                        this.set(x, y, z, null);
                    }
                }
            }

            for (int i = 0; i <= 8; i++) {
                for (int s = -1; s <= 1; s += 2) {
                    int z = fz + s * (8 - i);
                    this.set(x, M + 9 + i, z, "dark_oak_stairs[facing=" + (s < 0 ? "south" : "north") + ",half=bottom]");
                }
            }

            this.set(x, M + 17, fz, "dark_oak_slab[type=bottom]");
        }

        this.npc(fx - 7, M + 1.0, fz);
        this.feature("spectate", fx - 7, M + 1.0, fz + 4, fx - 20, fz);
    }

    private void watchtower() {
        int wx = this.watch[0];
        int wz = this.watch[1];
        int base = 12;
        this.disc(wx, wz, base, 9.5, "stone_bricks");
        this.ring(wx, wz, base + 1, 8.6, 9.5, "stone_brick_wall");
        this.roundTower(wx, wz, 5.0, base - 6, base + 30, 14);

        for (int y = base + 1; y <= base + 4; y++) {
            this.set(wx, y, wz + 5, null);
            this.set(wx, y, wz + 4, null);
        }

        this.set(wx, base + 5, wz + 5, "chiseled_stone_bricks");
    }

    /** Only three stalls by the road south of the gate. */
    private void market() {
        int[][] stalls = new int[][]{{-8, 84, 0}, {8, 84, 1}, {-8, 96, 2}};
        String[] colours = new String[]{"red", "blue", "yellow"};

        for (int[] s : stalls) {
            int x = s[0];
            int z = s[1];
            int g = this.top(x, z);
            String c = colours[s[2]];

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    this.set(x + dx, g, z + dz, "spruce_planks");
                }
            }

            for (int dx = -2; dx <= 2; dx += 4) {
                for (int dz = -2; dz <= 2; dz += 4) {
                    for (int y = g + 1; y <= g + 3; y++) {
                        this.set(x + dx, y, z + dz, "spruce_fence");
                    }
                }
            }

            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int lift = Math.abs(dz) == 3 ? 0 : 1;
                    this.set(x + dx, g + 4 + lift - (Math.abs(dz) == 3 ? 0 : 0), z + dz, Math.floorMod(dx, 2) == 0 ? c + "_wool" : "white_wool");
                }
            }

            String counter = x < 0 ? "east" : "west";
            int cx2 = x < 0 ? x + 2 : x - 2;

            for (int dz = -1; dz <= 1; dz++) {
                this.set(cx2, g + 1, z + dz, "spruce_stairs[facing=" + counter + ",half=top]");
            }

            this.set(x, g + 1, z - 1, "barrel[facing=up]");
            this.set(x, g + 1, z + 1, s[2] == 0 ? "hay_block" : (s[2] == 1 ? "barrel[facing=up]" : "pumpkin"));
            this.set(x - (x < 0 ? 1 : -1), g + 1, z, "chest[facing=" + counter + "]");
            this.npc(x + (x < 0 ? 4 : -4), g + 1.0, z);
        }
    }

    private void nature() {
        for (int x = -this.R + 4; x <= this.R - 4; x += 2) {
            for (int z = -this.R + 4; z <= this.R - 4; z += 2) {
                double hh = this.h(x, z);
                double m = md(x, z);
                if (!(hh < 0.0) && !(m < 54.0) && !this.road[this.ix(x)][this.ix(z)] && !this.water[this.ix(x)][this.ix(z)]) {
                    int t = this.top(x, z);
                    if ("grass_block".equals(this.get(x, t, z)) && this.empty(x, t + 1, z)
                        && dist(x - this.fort[0], z - this.fort[1]) > 19.0
                        && dist(x - this.watch[0], z - this.watch[1]) > 12.0
                        && this.roadDist(x, z) > 5.0
                        && Math.abs(z + 10) > 4) {
                        double dens = noise(this.seed + 71L, x * 0.25, z * 0.25);
                        double r = this.rnd.nextDouble();
                        if (r < 0.06 * dens * dens * 2.2) {
                            double kind = this.rnd.nextDouble();
                            if (kind < 0.45) {
                                this.tree(x, t + 1, z, "oak_log", "oak", 4 + this.rnd.nextInt(3), 2.8 + this.rnd.nextDouble() * 0.9);
                            } else if (kind < 0.7) {
                                this.tree(x, t + 1, z, "birch_log", "birch", 5 + this.rnd.nextInt(3), 2.5);
                            } else {
                                this.conifer(x, t + 1, z, "spruce_log", "spruce", 7 + this.rnd.nextInt(6));
                            }
                        } else if (r < 0.1) {
                            this.set(x, t + 1, z, this.rnd.nextInt(5) == 0 ? (this.rnd.nextBoolean() ? "poppy" : "dandelion") : "short_grass");
                        } else if (r < 0.103) {
                            this.ellipsoid(x, t + 0.5, z, 1.6 + this.rnd.nextDouble(), 1.2, 1.4, this.rnd.nextBoolean() ? "andesite" : "mossy_cobblestone");
                        }
                    }
                }
            }
        }
    }
}
