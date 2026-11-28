package net.cmcpvp.build;

/**
 * 3.13.0 - designed arenas for the arcade games. Local y 0 is where players stand (the plot's y); every game keeps
 * the shape its rules expect: Sumo's ring within 7 of the middle and nothing to land on below it, the boxing ring in
 * ropes at 11, the Bridge gap free for building (|x| &lt;= 15) with the goals at x 20..22, the spleef snow at 12 with
 * the ice 7 below, the KOTH hill's gold top 5x5. Everything else in the plot is cleared first.
 */
public final class ArcadeArenaDesign extends Voxels {
    private final String mode;

    public ArcadeArenaDesign(String mode, int cx, int floorY, int cz, long seed) {
        super(cx, floorY, cz, 44, -30, 24, seed);
        this.mode = mode;
        this.box(-44, -30, -44, 44, 24, 44, "air");
        switch (mode) {
            case "SUMO" -> this.sumo();
            case "BOXING" -> this.boxing();
            case "BRIDGE" -> this.bridge();
            case "SPLEEF" -> this.spleef();
            case "OITC" -> this.oitc();
            case "KOTH" -> this.koth();
            default -> {
            }
        }

        this.finish();
    }

    private String pick(String... b) {
        return b[this.rnd.nextInt(b.length)];
    }

    private String brick() {
        int r = this.rnd.nextInt(100);
        return r < 12 ? "mossy_stone_bricks" : (r < 22 ? "cracked_stone_bricks" : "stone_bricks");
    }

    /** An upside-down rock cone under an island (no surfaces near the top to land on). */
    private void underside(int cx, int cz, int r, int top, int depth, String rock, String rock2) {
        for (int d = 0; d < depth; d++) {
            double rr = r * (1.0 - (double)d / depth) + 0.3;
            int n = (int)Math.ceil(rr);
            for (int x = -n; x <= n; x++) {
                for (int z = -n; z <= n; z++) {
                    double k = Math.sqrt(x * x + z * z) + this.rnd.nextDouble() * 0.8;
                    if (k <= rr) {
                        this.set(cx + x, top - d, cz + z, this.rnd.nextInt(5) == 0 ? rock2 : rock);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ sumo: the sky dojo

    private static boolean inRing(int x, int z) {
        return Math.abs(x) <= 7 && Math.abs(z) <= 7 && Math.abs(x) + Math.abs(z) <= 10;
    }

    private void sumo() {
        // the dohyo: a 15 x 15 octagon of packed clay with the straw ring, white start lines and a red rim
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                if (!inRing(x, z)) {
                    continue;
                }

                double d = Math.sqrt(x * x + z * z);
                boolean rim = !inRing(x + 1, z) || !inRing(x - 1, z) || !inRing(x, z + 1) || !inRing(x, z - 1);
                String top;
                if (rim) {
                    top = "red_terracotta";
                } else if (Math.abs(d - 5.5) < 0.55) {
                    top = "hay_block";
                } else {
                    top = this.rnd.nextInt(6) == 0 ? "packed_mud" : "terracotta";
                }

                this.set(x, -1, z, top);
                this.set(x, -2, z, "mud_bricks");
            }
        }

        // the two white start lines (shikiri-sen)
        this.set(-2, -1, 0, "white_concrete");
        this.set(2, -1, 0, "white_concrete");
        // under the ring: a dark wood and stone cone with hanging lanterns
        // (the cone stays inside the ring's footprint: a player pushed off must fall clear)
        this.underside(0, 0, 6, -3, 14, "deepslate_bricks", "polished_blackstone_bricks");

        // the hanging roof (tsuriyane) high over the ring, with the four coloured tassels
        int ry = 11;
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                int y = ry + (9 - edge) / 2;
                this.set(x, y, z, edge == 9 ? "dark_oak_slab[type=bottom]" : (edge % 2 == 1 ? "dark_oak_slab[type=top]" : "dark_oak_planks"));
                if (edge == 8) {
                    this.set(x, ry, z, "red_wool");
                }
            }
        }

        for (int y = ry + 5; y <= ry + 6; y++) {
            this.set(0, y, 0, "gold_block");
        }

        String[] tassels = {"green_wool", "red_wool", "white_wool", "black_wool"};
        int i = 0;
        for (int[] c : new int[][]{{-8, -8}, {8, -8}, {8, 8}, {-8, 8}}) {
            for (int y = ry - 1; y >= ry - 3; y--) {
                this.set(c[0], y, c[1], y == ry - 3 ? tassels[i] : "chain");
            }

            this.set(c[0], ry - 4, c[1], tassels[i]);
            i++;
        }

        // chains holding the roof up into the sky, lanterns on the ring corners of the roof
        for (int[] c : new int[][]{{-7, 0}, {7, 0}, {0, -7}, {0, 7}}) {
            this.set(c[0], ry, c[1], "lantern[hanging=true]");
        }

        for (int y = ry + 7; y <= 22; y++) {
            this.set(0, y, 0, "chain");
        }

        // floating cherry islands with torii gates around, low and far enough that nobody lands on them
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            int ix = (int)Math.round(Math.cos(a) * 22);
            int iz = (int)Math.round(Math.sin(a) * 22);
            this.island(ix, -9, iz, 5);
            this.cherry(ix, -8, iz);
        }

        for (int k = 0; k < 4; k++) {
            int ix = k == 0 ? 0 : (k == 1 ? 0 : (k == 2 ? 24 : -24));
            int iz = k == 0 ? 24 : (k == 1 ? -24 : 0);
            this.island(ix, -11, iz, 4);
            this.torii(ix, -10, iz, k >= 2);
        }
    }

    private void island(int cx, int top, int cz, int r) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= r * r + 1) {
                    this.set(cx + x, top, cz + z, this.rnd.nextInt(7) == 0 ? "moss_block" : "grass_block");
                    this.set(cx + x, top - 1, cz + z, "dirt");
                }
            }
        }

        this.underside(cx, cz, r, top - 2, r + 4, "stone", "andesite");
    }

    private void cherry(int x, int y, int z) {
        for (int h = 0; h < 5; h++) {
            this.set(x, y + h, z, "cherry_log[axis=y]");
        }

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 3; dy <= 6; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + (dy - 5) * (dy - 5) * 1.6);
                    if (d <= 3.2 && this.rnd.nextInt(9) > 0) {
                        this.setIfEmpty(x + dx, y + dy, z + dz, "cherry_leaves[persistent=true]");
                    }
                }
            }
        }

        this.set(x + 1, y, z, "pink_petals[flower_amount=4,facing=north]");
        this.set(x - 1, y, z + 1, "pink_petals[flower_amount=3,facing=east]");
    }

    private void torii(int x, int y, int z, boolean alongZ) {
        for (int s = -2; s <= 2; s += 4) {
            int px = alongZ ? x : x + s;
            int pz = alongZ ? z + s : z;
            for (int h = 0; h <= 4; h++) {
                this.set(px, y + h, pz, "red_concrete");
            }
        }

        for (int s = -3; s <= 3; s++) {
            int px = alongZ ? x : x + s;
            int pz = alongZ ? z + s : z;
            this.set(px, y + 3, pz, "red_concrete");
            this.set(px, y + 5, pz, Math.abs(s) == 3 ? "black_concrete" : "red_concrete");
            this.set(px, y + 6, pz, "black_concrete");
        }
    }

    // ------------------------------------------------------------------ boxing: the ring and the hall

    private void boxing() {
        // canvas with a blue border and the centre logo
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                boolean border = Math.abs(x) >= 9 || Math.abs(z) >= 9;
                boolean logo = Math.abs(x) + Math.abs(z) <= 2;
                this.set(x, -1, z, border ? "blue_concrete" : (logo ? (Math.abs(x) + Math.abs(z) == 2 ? "red_concrete" : "white_concrete") : "light_gray_concrete"));
                for (int y = -6; y <= -2; y++) {
                    this.set(x, y, z, y == -2 ? "black_concrete" : "polished_blackstone");
                }
            }
        }

        // apron and skirt
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                if (Math.abs(x) >= 11 || Math.abs(z) >= 11) {
                    this.set(x, -1, z, "black_concrete");
                    for (int y = -6; y <= -2; y++) {
                        this.set(x, y, z, y % 2 == 0 ? "black_wool" : "gray_wool");
                    }
                }
            }
        }

        // corner posts: red and blue corners, white neutral corners; ropes between them
        String[][] corners = {{"-11", "-11", "red"}, {"11", "11", "blue"}, {"-11", "11", "white"}, {"11", "-11", "white"}};
        for (String[] c : corners) {
            int x = Integer.parseInt(c[0]);
            int z = Integer.parseInt(c[1]);
            for (int y = 0; y <= 3; y++) {
                this.set(x, y, z, y == 3 ? c[2] + "_wool" : "polished_blackstone_wall");
            }
        }

        for (int i = -10; i <= 10; i++) {
            for (int y = 1; y <= 3; y++) {
                String rope = y == 2 ? "red_wool" : "white_wool";
                this.set(i, y, -11, rope);
                this.set(i, y, 11, rope);
                this.set(-11, y, i, rope);
                this.set(11, y, i, rope);
            }

            this.set(i, 0, -11, "barrier");
            this.set(i, 0, 11, "barrier");
            this.set(-11, 0, i, "barrier");
            this.set(11, 0, i, "barrier");
            for (int y = 4; y <= 5; y++) {
                this.set(i, y, -11, "barrier");
                this.set(i, y, 11, "barrier");
                this.set(-11, y, i, "barrier");
                this.set(11, y, i, "barrier");
            }
        }

        // the hall floor and the stands rising on all four sides
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                if (edge <= 12) {
                    continue;
                }

                if (edge <= 16) {
                    this.set(x, -7, z, (x + z & 1) == 0 ? "polished_deepslate" : "deepslate_tiles");
                } else if (edge <= 34) {
                    int row = (edge - 17) / 2;
                    int y = -7 + row + 1;
                    boolean seat = (edge - 17) % 2 == 1;
                    this.set(x, y, z, seat ? (Math.abs(Math.max(Math.abs(x), Math.abs(z)) == Math.abs(x) ? z : x) % 9 == 0 ? "gray_concrete" : (x > 0 ? "blue_concrete" : "red_concrete")) : "polished_blackstone_bricks");
                    for (int yy = -8; yy < y; yy++) {
                        this.set(x, yy, z, "polished_blackstone");
                    }
                } else if (edge <= 36) {
                    for (int y = -8; y <= 12; y++) {
                        this.set(x, y, z, y % 6 == 0 ? "sea_lantern" : "polished_blackstone_bricks");
                    }
                }
            }
        }

        // the light rig over the ring and banners of both corners on the walls
        for (int x = -12; x <= 12; x++) {
            for (int z : new int[]{-12, 12}) {
                this.set(x, 14, z, "iron_bars");
                this.set(z, 14, x, "iron_bars");
            }
        }

        for (int x = -12; x <= 12; x += 4) {
            for (int z = -12; z <= 12; z += 24) {
                this.set(x, 13, z, "sea_lantern");
                this.set(z, 13, x, "sea_lantern");
            }
        }

        for (int x : new int[]{-12, 12}) {
            for (int z : new int[]{-12, 12}) {
                for (int y = 14; y <= 22; y++) {
                    this.set(x, y, z, "chain");
                }
            }
        }

        for (int i = -30; i <= 30; i += 6) {
            this.set(i, 6, -34, "red_wall_banner[facing=south]");
            this.set(i, 6, 34, "blue_wall_banner[facing=north]");
        }
    }

    // ------------------------------------------------------------------ the bridge: two castles over the void

    private void bridge() {
        for (int side = -1; side <= 1; side += 2) {
            String team = side < 0 ? "red" : "blue";
            // the island: x 16..24 (mirrored), z -6..6
            for (int ax = 16; ax <= 25; ax++) {
                int x = side * ax;
                for (int z = -6; z <= 6; z++) {
                    boolean goal = ax >= 20 && ax <= 22 && Math.abs(z) <= 1;
                    boolean goalRim = ax >= 19 && ax <= 23 && Math.abs(z) <= 2 && !goal;
                    String top = goal ? team + "_stained_glass" : (goalRim ? team + "_concrete" : (ax <= 17 && Math.abs(z) <= 3 ? team + "_terracotta"
                        : (this.rnd.nextInt(5) == 0 ? "cracked_stone_bricks" : "stone_bricks")));
                    if (ax >= 24 || Math.abs(z) >= 5) {
                        top = this.brick();
                    }

                    this.set(x, -1, z, top);
                    this.set(x, -2, z, goal ? team + "_stained_glass" : "stone_bricks");
                    if (goal) {
                        // the goal is a hole you drop into: glass floor 3 down, glass sides
                        this.set(x, -1, z, "air");
                        this.set(x, -2, z, "air");
                        this.set(x, -3, z, team + "_stained_glass");
                    }
                }
            }

            // walls and towers behind and beside the goal (outside the building zone)
            for (int z = -6; z <= 6; z++) {
                for (int y = 0; y <= 4; y++) {
                    this.set(side * 25, y, z, y == 4 && Math.abs(z) % 2 == 0 ? team + "_wool" : this.brick());
                }
            }

            for (int ax = 18; ax <= 25; ax++) {
                for (int zs = -1; zs <= 1; zs += 2) {
                    for (int y = 0; y <= 2; y++) {
                        this.set(side * ax, y, zs * 6, y == 2 && ax % 2 == 0 ? "stone_brick_slab[type=bottom]" : this.brick());
                    }
                }
            }

            for (int zs = -1; zs <= 1; zs += 2) {
                int tx = side * 25;
                int tz = zs * 6;
                for (int y = 0; y <= 8; y++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (Math.abs(tx + dx) <= 25 + 1 && (Math.abs(dx) == 1 || Math.abs(dz) == 1)) {
                                this.set(tx + dx, y, tz + dz, y == 8 ? (Math.abs(dx) + Math.abs(dz) == 2 ? team + "_wool" : "air") : this.brick());
                            }
                        }
                    }
                }

                this.set(tx, 9, tz, "lantern[hanging=false]");
                this.set(tx, 8, tz, team + "_concrete");
            }

            this.set(side * 25, 5, 0, team + "_wool");
            this.set(side * 25 - side, 4, 0, team + "_wall_banner[facing=" + (side < 0 ? "east" : "west") + "]");
            this.underside(side * 21, 0, 5, -3, 12, "stone", side < 0 ? "red_terracotta" : "blue_terracotta");
        }

        // the middle: a white terracotta stand with the score monument hanging under it
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                this.set(x, -1, z, Math.abs(x) == 2 || Math.abs(z) == 2 ? "white_terracotta" : (x == 0 && z == 0 ? "gold_block" : "smooth_quartz"));
            }
        }

        this.underside(0, 0, 3, -2, 8, "quartz_block", "white_terracotta");
    }

    // ------------------------------------------------------------------ spleef: the frozen stadium

    private void spleef() {
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                this.set(x, -1, z, "snow_block");
            }
        }

        for (int x = -13; x <= 13; x++) {
            for (int z = -13; z <= 13; z++) {
                this.set(x, -7, z, "blue_ice");
                this.set(x, -8, z, "packed_ice");
            }
        }

        // the walls: packed ice posts with light blue glass panels, frosted cap
        for (int i = -13; i <= 13; i++) {
            for (int y = -7; y <= 4; y++) {
                boolean post = Math.floorMod(i, 4) == 1;
                String b = y <= -2 ? "packed_ice" : (y == 4 ? "snow_block" : (post ? "packed_ice" : "light_blue_stained_glass"));
                this.set(i, y, -13, b);
                this.set(i, y, 13, b);
                this.set(-13, y, i, b);
                this.set(13, y, i, b);
            }
        }

        for (int i = -13; i <= 13; i += 4) {
            for (int[] s : new int[][]{{i, -13}, {i, 13}, {-13, i}, {13, i}}) {
                this.set(s[0], 5, s[1], "sea_lantern");
                this.set(s[0], 6, s[1], "snow[layers=2]");
            }
        }

        // the snowy ground around with spruces and ice spikes
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                if (edge >= 14 && x * x + z * z <= 40 * 40) {
                    this.set(x, -8, z, "snow_block");
                    this.set(x, -9, z, "packed_ice");
                    if (this.rnd.nextInt(3) == 0) {
                        this.set(x, -7, z, "snow[layers=" + (1 + this.rnd.nextInt(3)) + "]");
                    }
                }
            }
        }

        this.underside(0, 0, 40, -10, 14, "packed_ice", "snow_block");
        for (int k = 0; k < 14; k++) {
            double a = k * Math.PI * 2 / 14 + this.rnd.nextDouble() * 0.3;
            int d = 20 + this.rnd.nextInt(14);
            int x = (int)Math.round(Math.cos(a) * d);
            int z = (int)Math.round(Math.sin(a) * d);
            if (k % 3 == 0) {
                int h = 6 + this.rnd.nextInt(8);
                for (int y = 0; y < h; y++) {
                    this.set(x, -7 + y, z, "packed_ice");
                    if (y < h / 2) {
                        this.set(x + 1, -7 + y, z, "packed_ice");
                        this.set(x, -7 + y, z + 1, "packed_ice");
                    }
                }
            } else {
                this.spruce(x, -7, z, 7 + this.rnd.nextInt(4));
            }
        }
    }

    private void spruce(int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, "spruce_log[axis=y]");
        }

        for (int i = 2; i <= h; i++) {
            int r = Math.max(0, (h - i) / 2);
            if (i % 2 == 1 && r > 0) {
                r--;
            }

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= r + 1) {
                        this.setIfEmpty(x + dx, y + i, z + dz, this.rnd.nextInt(4) == 0 ? "snow_block" : "spruce_leaves[persistent=true]");
                    }
                }
            }
        }

        this.set(x, y + h, z, "spruce_leaves[persistent=true]");
        this.set(x, y + h + 1, z, "snow[layers=3]");
    }

    // ------------------------------------------------------------------ OITC: a ruined courtyard

    private void oitc() {
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                boolean path = Math.abs(x) <= 1 || Math.abs(z) <= 1;
                this.set(x, -1, z, path ? this.pick("polished_andesite", "andesite", "gravel") : this.brick());
                this.set(x, -2, z, "stone");
            }
        }

        // castle walls with crenellations and torches
        for (int i = -16; i <= 16; i++) {
            for (int y = -2; y <= 5; y++) {
                String b = y == 5 ? (Math.floorMod(i, 2) == 0 ? this.brick() : "air") : this.brick();
                this.set(i, y, -16, b);
                this.set(i, y, 16, b);
                this.set(-16, y, i, b);
                this.set(16, y, i, b);
            }
        }

        for (int i = -12; i <= 12; i += 6) {
            this.set(i, 3, -15, "wall_torch[facing=south]");
            this.set(i, 3, 15, "wall_torch[facing=north]");
            this.set(-15, 3, i, "wall_torch[facing=east]");
            this.set(15, 3, i, "wall_torch[facing=west]");
        }

        // broken pillars, low walls, hay and barrels for cover (the spawn ring at 12 stays open)
        int[][] pillars = {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}, {0, -9}, {0, 9}, {-9, 0}, {9, 0}};
        for (int[] p : pillars) {
            int h = 2 + this.rnd.nextInt(3);
            for (int y = 0; y < h; y++) {
                this.set(p[0], y, p[1], y == 0 ? "chiseled_stone_bricks" : "stone_brick_wall");
                this.set(p[0] + 1, y, p[1], this.brick());
            }

            this.set(p[0] + 1, h, p[1], "stone_brick_slab[type=bottom]");
        }

        for (int x = -3; x <= 3; x++) {
            this.set(x, 0, -1, "mossy_cobblestone_wall");
            this.set(x, 0, 1, "mossy_cobblestone_wall");
        }

        this.set(-5, 0, 4, "hay_block");
        this.set(-5, 1, 4, "hay_block");
        this.set(-4, 0, 4, "hay_block");
        this.set(5, 0, -4, "barrel[facing=up]");
        this.set(5, 1, -4, "barrel[facing=up]");
        this.set(4, 0, -4, "barrel[facing=up]");
        this.set(-4, 0, -5, "oak_fence");
        this.set(4, 0, 5, "oak_fence");
        // a well in the middle-ish corner, vines on the walls, banners
        for (int i = -14; i <= 14; i++) {
            if (this.rnd.nextInt(4) == 0) {
                for (int y = 4; y >= 1 + this.rnd.nextInt(3); y--) {
                    this.setIfEmpty(i, y, -15, "vine[north=true]");
                }
            }
        }

        this.underside(0, 0, 18, -3, 12, "stone", "cobblestone");
    }

    // ------------------------------------------------------------------ KOTH: the temple hill

    private void koth() {
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                this.set(x, -1, z, this.rnd.nextInt(7) == 0 ? "moss_block" : (this.rnd.nextInt(9) == 0 ? "coarse_dirt" : "grass_block"));
                this.set(x, -2, z, "dirt");
                if (this.rnd.nextInt(6) == 0 && Math.max(Math.abs(x), Math.abs(z)) > 5) {
                    this.set(x, 0, z, this.pick("short_grass", "short_grass", "fern", "dandelion", "poppy"));
                }
            }
        }

        // the stepped hill: 7x7 base, the 5x5 gold top players stand on (rules: within 2.6 of the middle)
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int e = Math.max(Math.abs(x), Math.abs(z));
                this.set(x, 0, z, e == 4 ? "stone_brick_slab[type=bottom]" : "chiseled_stone_bricks");
                if (e <= 3) {
                    this.set(x, 0, z, e == 3 ? "stone_bricks" : "polished_andesite");
                }

                if (e <= 2) {
                    this.set(x, 1, z, e == 2 ? "gold_block" : (x == 0 && z == 0 ? "raw_gold_block" : "gold_block"));
                }
            }
        }

        for (int i = -3; i <= 3; i++) {
            this.set(i, 1, -3, "stone_brick_stairs[facing=south]");
            this.set(i, 1, 3, "stone_brick_stairs[facing=north]");
            this.set(-3, 1, i, "stone_brick_stairs[facing=east]");
            this.set(3, 1, i, "stone_brick_stairs[facing=west]");
        }

        // braziers on the four corners of the hill
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            for (int y = 0; y <= 2; y++) {
                this.set(c[0], y, c[1], y == 2 ? "campfire[lit=true]" : "mossy_stone_bricks");
            }
        }

        // the temple walls: mossy stone with columns, vines and a gate on each side
        for (int i = -16; i <= 16; i++) {
            for (int y = -2; y <= 5; y++) {
                boolean column = Math.floorMod(i, 4) == 0;
                boolean gate = Math.abs(i) <= 1 && y <= 2 && y >= 0;
                String b = gate ? "iron_bars" : (column ? "chiseled_stone_bricks" : (y == 5 ? "stone_brick_slab[type=bottom]" : this.brick()));
                this.set(i, y, -16, b);
                this.set(i, y, 16, b);
                this.set(-16, y, i, b);
                this.set(16, y, i, b);
            }
        }

        for (int i = -15; i <= 15; i++) {
            if (this.rnd.nextInt(3) == 0) {
                for (int y = 4; y >= 1 + this.rnd.nextInt(3); y--) {
                    this.setIfEmpty(i, y, 15, "vine[south=true]");
                }
            }
        }

        // jungle trees in the corners, leaves for shade
        for (int[] c : new int[][]{{-12, -12}, {12, -12}, {-12, 12}, {12, 12}}) {
            for (int y = 0; y < 7; y++) {
                this.set(c[0], y, c[1], "jungle_log[axis=y]");
            }

            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = 5; dy <= 8; dy++) {
                        if (dx * dx + dz * dz + (dy - 7) * (dy - 7) <= 10 && Math.abs(c[0] + dx) <= 15 && Math.abs(c[1] + dz) <= 15) {
                            this.setIfEmpty(c[0] + dx, dy, c[1] + dz, "jungle_leaves[persistent=true]");
                        }
                    }
                }
            }
        }

        this.underside(0, 0, 18, -3, 12, "stone", "mossy_cobblestone");
    }

    @Override
    public String name() {
        return "Arcade " + this.mode;
    }
}
