package net.practicepvp.build;

/**
 * Castle and medieval-town parts for the big lobby designs: weathered stone mixes, round and square towers with
 * corbelled tops, tiled roofs with proper corners, timber-framed houses with jetties, shutters and chimneys, and the
 * small street props (wells, carts, stalls, crates) that make a town look lived in. Coordinates are relative to the
 * design centre and floor, like every {@link Voxels} method.
 */
public abstract class CastleKit extends DetailVoxels {
    protected CastleKit(int cx, int floorY, int cz, int radius, int yLo, int yHi, long seed) {
        super(cx, floorY, cz, radius, yLo, yHi, seed);
    }

    /** Roof tiles: the stair/slab material, its full block, and a second material mixed in as weathered tiles. */
    public enum Tile {
        TEAL("dark_prismarine", "dark_prismarine", "prismarine_brick", "prismarine_bricks"),
        SLATE("deepslate_tile", "deepslate_tiles", "cobbled_deepslate", "cobbled_deepslate"),
        RED("brick", "bricks", "granite", "granite"),
        CLAY("granite", "granite", "brick", "bricks"),
        MOSS("spruce", "spruce_planks", "dark_oak", "dark_oak_planks"),
        COPPER("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper", "waxed_weathered_cut_copper", "waxed_weathered_cut_copper");

        public final String stair;
        public final String block;
        public final String altStair;
        public final String altBlock;

        Tile(String stair, String block, String altStair, String altBlock) {
            this.stair = stair;
            this.block = block;
            this.altStair = altStair;
            this.altBlock = altBlock;
        }
    }

    protected static int dx(String f) {
        return f.equals("east") ? 1 : (f.equals("west") ? -1 : 0);
    }

    protected static int dz(String f) {
        return f.equals("south") ? 1 : (f.equals("north") ? -1 : 0);
    }

    /** Weathered castle stone: mostly stone bricks, with cracked, cobble and andesite patches, mossy near the ground. */
    protected String stone(int x, int y, int z, int base) {
        double r = this.hash3(101L, x, y, z);
        double m = this.hash3(103L, x, y, z);
        int h = y - base;
        if (h <= 1 && m < 0.4) {
            return m < 0.18 ? "mossy_cobblestone" : "mossy_stone_bricks";
        } else if (h <= 4 && m < 0.1) {
            return "mossy_stone_bricks";
        } else if (r < 0.08) {
            return "cobblestone";
        } else if (r < 0.15) {
            return "andesite";
        } else if (r < 0.24) {
            return "cracked_stone_bricks";
        } else {
            return r > 0.975 ? "mossy_stone_bricks" : "stone_bricks";
        }
    }

    /** One roof tile as a stair (the second material shows up as single weathered tiles). */
    protected String tileStair(Tile t, int x, int y, int z, String facing) {
        return stairs(this.hash3(111L, x, y, z) < 0.14 ? t.altStair : t.stair, facing, false);
    }

    protected String tileBlock(Tile t, int x, int y, int z) {
        return this.hash3(111L, x, y, z) < 0.14 ? t.altBlock : t.block;
    }

    private static String stairShape(String mat, String facing, String shape) {
        return mat + "_stairs[facing=" + facing + ",half=bottom,shape=" + shape + "]";
    }

    /** One square course of roof stairs rising toward the middle, with real outer corners. */
    protected void stairRing(int x0, int z0, int x1, int z1, int y, Tile t) {
        if (x0 > x1 || z0 > z1) {
            return;
        }

        if (x0 == x1 || z0 == z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    this.set(x, y, z, slab(t.stair, false));
                }
            }

            return;
        }

        for (int x = x0 + 1; x < x1; x++) {
            this.set(x, y, z0, this.tileStair(t, x, y, z0, "south"));
            this.set(x, y, z1, this.tileStair(t, x, y, z1, "north"));
        }

        for (int z = z0 + 1; z < z1; z++) {
            this.set(x0, y, z, this.tileStair(t, x0, y, z, "east"));
            this.set(x1, y, z, this.tileStair(t, x1, y, z, "west"));
        }

        this.set(x0, y, z0, stairShape(t.stair, "south", "outer_left"));
        this.set(x1, y, z0, stairShape(t.stair, "south", "outer_right"));
        this.set(x0, y, z1, stairShape(t.stair, "north", "outer_right"));
        this.set(x1, y, z1, stairShape(t.stair, "north", "outer_left"));
    }

    /** A hipped (pyramid) roof over a rectangle, one course per block of height, closed inside. Returns the top y. */
    protected int hipRoof(int x0, int z0, int x1, int z1, int y, Tile t) {
        int i = 0;

        while (x0 + i <= x1 - i && z0 + i <= z1 - i) {
            this.stairRing(x0 + i, z0 + i, x1 - i, z1 - i, y + i, t);
            if (x0 + i + 1 <= x1 - i - 1 && z0 + i + 1 <= z1 - i - 1) {
                for (int x = x0 + i + 1; x <= x1 - i - 1; x++) {
                    for (int z = z0 + i + 1; z <= z1 - i - 1; z++) {
                        // the course below is closed with full tiles so nobody looks into a hollow roof
                        if (x == x0 + i + 1 || x == x1 - i - 1 || z == z0 + i + 1 || z == z1 - i - 1) {
                            this.set(x, y + i, z, this.tileBlock(t, x, y + i, z));
                        }
                    }
                }
            }

            i++;
        }

        return y + i - 1;
    }

    /**
     * A gable roof over a rectangle with a one-block overhang on every side. {@code ridgeX}: the ridge runs east-west.
     * The gable walls are filled by {@code gable} (a block name, or "frame" for a timber-framed gable). Returns the ridge y.
     */
    protected int gableRoof(int x0, int z0, int x1, int z1, int y, boolean ridgeX, Tile t, String gable) {
        int a0 = ridgeX ? z0 : x0;
        int a1 = ridgeX ? z1 : x1;
        int b0 = ridgeX ? x0 : z0;
        int b1 = ridgeX ? x1 : z1;
        int half = (a1 - a0 + 2) / 2;
        int ridge = y;

        for (int i = 0; i <= half + 1; i++) {
            int lo = a0 - 1 + i;
            int hi = a1 + 1 - i;
            if (lo > hi) {
                break;
            }

            int yy = y + i;
            ridge = yy;

            for (int b = b0 - 1; b <= b1 + 1; b++) {
                boolean end = b == b0 - 1 || b == b1 + 1;
                if (lo == hi) {
                    this.put(ridgeX, b, yy, lo, slab(t.stair, false));
                } else {
                    this.put(ridgeX, b, yy, lo, this.tileStair(t, b, yy, lo, ridgeX ? "south" : "east"));
                    this.put(ridgeX, b, yy, hi, this.tileStair(t, b, yy, hi, ridgeX ? "north" : "west"));
                    if (i > 0 && end) {
                        // the underside of the overhang at the gable ends
                        this.putIfEmpty(ridgeX, b, yy - 1, lo, stairs(t.stair, ridgeX ? "north" : "west", true));
                        this.putIfEmpty(ridgeX, b, yy - 1, hi, stairs(t.stair, ridgeX ? "south" : "east", true));
                    }
                }

                // gable walls
                if ((b == b0 || b == b1) && gable != null) {
                    for (int a = lo + 1; a < hi; a++) {
                        String g = gable;
                        if (gable.equals("frame")) {
                            int mid = (a0 + a1) / 2;
                            boolean post = a == mid || (a1 - a0) % 2 == 1 && a == mid + 1;
                            boolean rafter = a == lo + 1 || a == hi - 1;
                            g = post || rafter ? log("dark_oak_log", post ? 'y' : (ridgeX ? 'z' : 'x')) : "calcite";
                        }

                        this.put(ridgeX, b, yy, a, g);
                    }
                }
            }
        }

        // a ridge beam that sticks out at both ends
        if (gable != null) {
            this.put(ridgeX, b0 - 1, ridge - 1, (a0 + a1) / 2, log("dark_oak_log", ridgeX ? 'x' : 'z'));
            this.put(ridgeX, b1 + 1, ridge - 1, (a0 + a1) / 2, log("dark_oak_log", ridgeX ? 'x' : 'z'));
        }

        return ridge;
    }

    /** set() with the two horizontal axes swapped when the roof ridge runs north-south. */
    private void put(boolean ridgeX, int b, int y, int a, String block) {
        if (ridgeX) {
            this.set(b, y, a, block);
        } else {
            this.set(a, y, b, block);
        }
    }

    private void putIfEmpty(boolean ridgeX, int b, int y, int a, String block) {
        if (ridgeX) {
            this.setIfEmpty(b, y, a, block);
        } else {
            this.setIfEmpty(a, y, b, block);
        }
    }

    /**
     * A round tower from {@code base} (the ground it stands on) up to its platform at {@code top}: a battered foot,
     * stone bands, arrow slits, plank floors every five blocks with a ladder, a corbelled top and either a
     * crenellated platform with a brazier ({@code roof == null}) or a lantern room under a cone roof. The door opens
     * toward {@code doorFace} (null = no door).
     */
    protected void roundTower(int ox, int oz, double r, int base, int top, Tile roof, int roofH, String flag, String doorFace) {
        int n = (int)Math.ceil(r) + 3;
        int ladder = -(int)Math.floor(r - 1.3);

        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = ox + dx;
                int z = oz + dz;
                if (d <= r + 0.3) {
                    boolean shell = d > r - 1.2;

                    for (int y = base - 6; y <= top; y++) {
                        int h = y - base;
                        if (shell || h <= 0) {
                            this.set(x, y, z, h > 0 && h % 5 == 0 ? "polished_andesite" : this.stone(x, y, z, base));
                        } else {
                            boolean hole = dx == 0 && dz == ladder;
                            this.set(x, y, z, h % 5 == 0 && !hole ? "spruce_planks" : null);
                        }
                    }

                    this.set(x, top, z, dx == 0 && dz == ladder ? "spruce_trapdoor[facing=south,half=top,open=false]" : this.stone(x, top, z, base + 50));
                } else if (d <= r + 1.3) {
                    // battered foot
                    for (int y = base - 6; y <= base + 2; y++) {
                        this.set(x, y, z, this.stone(x, y, z, base));
                    }

                    this.setIfEmpty(x, base + 3, z, slab("stone_brick", false));
                    // corbels carrying the wider top
                    this.set(x, top - 1, z, stairs("stone_brick", facing(-dx, -dz), true));
                    this.set(x, top, z, this.stone(x, top, z, base + 50));
                }
            }
        }

        // ladder up the north side of the shaft
        for (int y = base + 1; y < top; y++) {
            this.set(ox, y, oz + ladder, "ladder[facing=south]");
        }

        // arrow slits on every floor, turned a little from floor to floor
        for (int h = 3; base + h + 1 < top - 1; h += 5) {
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(45 + k * 90 + (h / 5 % 2) * 45);
                int wx = ox + (int)Math.round(Math.cos(a) * (r - 0.4));
                int wz = oz + (int)Math.round(Math.sin(a) * (r - 0.4));
                this.set(wx, base + h, wz, "iron_bars");
                this.set(wx, base + h + 1, wz, "iron_bars");
            }
        }

        if (doorFace != null) {
            int fx = dx(doorFace);
            int fz = dz(doorFace);

            for (int k = (int)Math.floor(r - 2.0); k <= (int)Math.ceil(r + 1.4); k++) {
                this.set(ox + fx * k, base + 1, oz + fz * k, null);
                this.set(ox + fx * k, base + 2, oz + fz * k, null);
                this.set(ox + fx * k, base, oz + fz * k, "stone_bricks");
            }

            int k = (int)Math.round(r);
            this.set(ox + fx * k, base + 3, oz + fz * k, "chiseled_stone_bricks");
            this.set(ox + fx * (k + 1), base + 3, oz + fz * (k + 1), "lantern[hanging=true]");
        }

        if (roof == null) {
            // crenellated platform with a brazier and a flag
            for (int dx = -n; dx <= n; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r + 0.3 && d <= r + 1.3) {
                        int x = ox + dx;
                        int z = oz + dz;
                        this.set(x, top + 1, z, this.stone(x, top + 1, z, base + 50));
                        int seg = (int)Math.floor((Math.atan2(dz, dx) + Math.PI) * (r + 1.0) / 1.6);
                        if (seg % 2 == 0) {
                            this.set(x, top + 2, z, this.stone(x, top + 2, z, base + 50));
                        }
                    }
                }
            }

            this.set(ox + 1, top + 1, oz + 1, "polished_andesite");
            this.set(ox + 1, top + 2, oz + 1, "campfire[lit=true]");
            this.flagPole(ox, top + 1, oz, 7, flag);
        } else {
            // lantern room: stone piers with open arches, then the cone roof with an overhanging eave
            for (int dx = -n; dx <= n; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    int x = ox + dx;
                    int z = oz + dz;
                    if (d > r - 0.9 && d <= r + 0.3) {
                        int seg = (int)Math.floor((Math.atan2(dz, dx) + Math.PI) * r / 2.2);
                        boolean pier = seg % 2 == 0;
                        this.set(x, top + 1, z, this.stone(x, top + 1, z, base + 50));
                        this.set(x, top + 2, z, pier ? this.stone(x, top + 2, z, base + 50) : null);
                        this.set(x, top + 3, z, pier ? this.stone(x, top + 3, z, base + 50) : stairs("stone_brick", facing(-dx, -dz), true));
                        this.set(x, top + 4, z, "polished_andesite");
                    } else if (d > r + 0.3 && d <= r + 1.3) {
                        this.set(x, top + 1, z, slab("stone_brick", false));
                    }

                    if (d > r + 0.3 && d <= r + 1.6) {
                        this.set(x, top + 5, z, slab(roof.stair, false));
                    }
                }
            }

            this.set(ox, top + 3, oz, "lantern[hanging=true]");
            this.set(ox, top + 4, oz, "iron_chain[axis=y]");

            for (int i = 0; i <= roofH; i++) {
                double rr = (r + 0.9) * (1.0 - (double)i / (roofH + 1));
                int m = (int)Math.ceil(rr) + 1;

                for (int dx = -m; dx <= m; dx++) {
                    for (int dz = -m; dz <= m; dz++) {
                        if (dx * dx + dz * dz <= rr * rr + rr * 0.6) {
                            this.set(ox + dx, top + 5 + i, oz + dz, this.tileBlock(roof, ox + dx, top + 5 + i, oz + dz));
                        }
                    }
                }
            }

            this.set(ox, top + 6 + roofH, oz, "polished_andesite");
            this.flagPole(ox, top + 7 + roofH, oz, 4, flag);
        }
    }

    /** A flag pole with a small cloth flag flying east. */
    protected void flagPole(int x, int y, int z, int h, String colour) {
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, "dark_oak_fence");
        }

        if (colour != null) {
            for (int k = 1; k <= 3; k++) {
                this.set(x + k, y + h - 1, z, colour + "_wool");
                if (k < 3) {
                    this.set(x + k, y + h - 2, z, colour + "_wool");
                }
            }
        }
    }

    /**
     * A square tower: quoined corners, battered foot, slits, floors with a ladder, corbelled top, and a hipped tile
     * roof over an open belfry stage ({@code roof} != null) or battlements.
     */
    protected void squareTower(int ox, int oz, int half, int base, int top, Tile roof, String flag, String doorFace) {
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            for (int dz = -half - 1; dz <= half + 1; dz++) {
                int x = ox + dx;
                int z = oz + dz;
                int ax = Math.abs(dx);
                int az = Math.abs(dz);
                boolean outer = ax == half + 1 || az == half + 1;
                boolean edge = !outer && (ax == half || az == half);
                boolean corner = ax == half && az == half;
                boolean hole = dx == 0 && dz == -half + 1;
                if (outer) {
                    for (int y = base - 6; y <= base + 2; y++) {
                        this.set(x, y, z, this.stone(x, y, z, base));
                    }

                    this.setIfEmpty(x, base + 3, z, slab("stone_brick", false));
                    String f = ax == half + 1 && az == half + 1 ? facing(-dx, 0) : (ax == half + 1 ? facing(-dx, 0) : facing(0, -dz));
                    this.set(x, top - 1, z, stairs("stone_brick", f, true));
                    this.set(x, top, z, this.stone(x, top, z, base + 50));
                } else {
                    for (int y = base - 6; y <= top; y++) {
                        int h = y - base;
                        if (edge || h <= 0) {
                            String b = corner ? (Math.floorMod(h, 2) == 0 ? "polished_andesite" : "stone_bricks") : (h > 0 && h % 5 == 0 ? "polished_andesite" : this.stone(x, y, z, base));
                            this.set(x, y, z, b);
                        } else {
                            this.set(x, y, z, h % 5 == 0 && !hole ? "spruce_planks" : null);
                        }
                    }

                    this.set(x, top, z, hole ? "spruce_trapdoor[facing=south,half=top,open=false]" : this.stone(x, top, z, base + 50));
                }
            }
        }

        for (int y = base + 1; y < top; y++) {
            this.set(ox, y, oz - half + 1, "ladder[facing=south]");
        }

        for (int h = 3; base + h + 1 < top - 1; h += 5) {
            this.set(ox + half, base + h, oz, "iron_bars");
            this.set(ox + half, base + h + 1, oz, "iron_bars");
            this.set(ox - half, base + h, oz, "iron_bars");
            this.set(ox - half, base + h + 1, oz, "iron_bars");
            this.set(ox, base + h, oz + half, "iron_bars");
            this.set(ox, base + h + 1, oz + half, "iron_bars");
            this.set(ox, base + h, oz - half, "iron_bars");
        }

        if (doorFace != null) {
            int fx = dx(doorFace);
            int fz = dz(doorFace);

            for (int k = half - 1; k <= half + 1; k++) {
                this.set(ox + fx * k, base + 1, oz + fz * k, null);
                this.set(ox + fx * k, base + 2, oz + fz * k, null);
                this.set(ox + fx * k, base, oz + fz * k, "stone_bricks");
            }

            this.set(ox + fx * half, base + 3, oz + fz * half, "chiseled_stone_bricks");
            this.set(ox + fx * (half + 1), base + 3, oz + fz * (half + 1), "lantern[hanging=true]");
        }

        int e = half + 1;
        if (roof == null) {
            for (int dx = -e; dx <= e; dx++) {
                for (int dz = -e; dz <= e; dz++) {
                    if (Math.abs(dx) == e || Math.abs(dz) == e) {
                        this.set(ox + dx, top + 1, oz + dz, this.stone(ox + dx, top + 1, oz + dz, base + 50));
                        if (Math.floorMod(dx + dz, 2) == 0) {
                            this.set(ox + dx, top + 2, oz + dz, this.stone(ox + dx, top + 2, oz + dz, base + 50));
                        }
                    }
                }
            }

            this.set(ox + 1, top + 1, oz + 1, "polished_andesite");
            this.set(ox + 1, top + 2, oz + 1, "campfire[lit=true]");
            this.flagPole(ox, top + 1, oz, 7, flag);
        } else {
            // open belfry stage: corner piers and arched openings, then the hipped roof
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    int ax = Math.abs(dx);
                    int az = Math.abs(dz);
                    if (ax == half || az == half) {
                        boolean pier = ax >= half - 1 && az >= half - 1 || half >= 4 && (dx == 0 || dz == 0);
                        int x = ox + dx;
                        int z = oz + dz;
                        this.set(x, top + 1, z, this.stone(x, top + 1, z, base + 50));
                        this.set(x, top + 2, z, pier ? this.stone(x, top + 2, z, base + 50) : null);
                        this.set(x, top + 3, z, pier ? this.stone(x, top + 3, z, base + 50) : stairs("stone_brick", ax == half ? facing(-dx, 0) : facing(0, -dz), true));
                        this.set(x, top + 4, z, "polished_andesite");
                    }
                }
            }

            for (int dx = -e; dx <= e; dx++) {
                for (int dz = -e; dz <= e; dz++) {
                    if (Math.abs(dx) == e || Math.abs(dz) == e) {
                        this.set(ox + dx, top + 1, oz + dz, slab("stone_brick", false));
                    }
                }
            }

            this.set(ox, top + 3, oz, "lantern[hanging=true]");
            this.set(ox, top + 4, oz, "iron_chain[axis=y]");
            int peak = this.hipRoof(ox - e, oz - e, ox + e, oz + e, top + 5, roof);
            this.set(ox, peak + 1, oz, "polished_andesite");
            this.flagPole(ox, peak + 2, oz, 4, flag);
        }
    }

    /** A long cloth banner (three wide, tapering) hanging on a wall face, with a gold stripe. */
    protected void clothBanner(int x, int yTop, int z, String face, String colour, int len) {
        boolean alongX = face.equals("north") || face.equals("south");

        for (int w = -1; w <= 1; w++) {
            for (int k = 0; k < len; k++) {
                if (k == len - 1 && w != 0) {
                    continue;
                }

                String s = k == 0 || k == len - 2 && w == 0 ? "yellow_wool" : (w == 0 && k >= 2 && k <= 3 ? "yellow_wool" : colour + "_wool");
                this.set(x + (alongX ? w : 0), yTop - k, z + (alongX ? 0 : w), s);
            }
        }
    }

    /** A chandelier: a ring of lanterns on chains under a wooden wheel. */
    protected void chandelier(int x, int yTop, int z, int drop) {
        for (int i = 0; i < drop; i++) {
            this.set(x, yTop - i, z, "iron_chain[axis=y]");
        }

        int y = yTop - drop;
        this.set(x, y, z, "dark_oak_fence");

        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            this.set(x + o[0], y, z + o[1], "dark_oak_fence");
            this.set(x + o[0] * 2, y, z + o[1] * 2, "dark_oak_fence");
            this.set(x + o[0] * 2, y - 1, z + o[1] * 2, "lantern[hanging=true]");
        }
    }

    /** A stone well with a roof, a bucket chain and water. */
    protected void well(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean mid = dx == 0 && dz == 0;
                this.set(x + dx, y - 1, z + dz, mid ? "water" : "cobblestone");
                this.set(x + dx, y - 2, z + dz, "cobblestone");
                this.set(x + dx, y, z + dz, mid ? null : (dx != 0 && dz != 0 ? "mossy_cobblestone" : "cobblestone_wall"));
            }
        }

        for (int s = -1; s <= 1; s += 2) {
            this.set(x + s, y + 1, z + s, "spruce_fence");
            this.set(x + s, y + 2, z + s, "spruce_fence");
            this.set(x + s, y + 1, z - s, "spruce_fence");
            this.set(x + s, y + 2, z - s, "spruce_fence");
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                this.set(x + dx, y + 3 + (m == 2 ? 0 : (m == 1 ? 0 : 1)), z + dz, m == 2 ? slab("spruce", false) : (m == 1 ? slab("spruce", true) : slab("dark_oak", false)));
            }
        }

        this.set(x, y + 2, z, "iron_chain[axis=y]");
        this.set(x, y + 1, z, "cauldron");
    }

    /** A hand cart loaded with goods, its shafts pointing along {@code face}. */
    protected void cart(int x, int y, int z, String face, String load) {
        int fx = dx(face);
        int fz = dz(face);
        int sx = fz != 0 ? 1 : 0;
        int sz = fx != 0 ? 1 : 0;

        for (int k = 0; k <= 2; k++) {
            for (int s = -1; s <= 1; s++) {
                int bx = x - fx * k + sx * s;
                int bz = z - fz * k + sz * s;
                if (s == 0) {
                    this.set(bx, y + 1, bz, slab("spruce", false));
                    this.set(bx, y + 1, bz, k == 1 ? load : "hay_block");
                } else {
                    this.set(bx, y + 1, bz, "spruce_trapdoor[facing=" + (s > 0 ? (sx == 1 ? "east" : "south") : (sx == 1 ? "west" : "north")) + ",half=bottom,open=true]");
                }
            }
        }

        // the load sits on a plank bed, wheels under the middle
        for (int k = 0; k <= 2; k++) {
            this.set(x - fx * k, y, z - fz * k, slab("spruce", true));
        }

        this.set(x - fx + sx, y, z - fz + sz, "dark_oak_trapdoor[facing=" + (sx == 1 ? "east" : "south") + ",half=bottom,open=true]");
        this.set(x - fx - sx, y, z - fz - sz, "dark_oak_trapdoor[facing=" + (sx == 1 ? "west" : "north") + ",half=bottom,open=true]");
        this.set(x + fx, y, z + fz, "spruce_fence");
        this.set(x + fx * 2, y, z + fz * 2, "spruce_fence");
    }

    /** A market stall: counter, goods, corner posts and a striped cloth roof. {@code face}: the side customers stand on. */
    protected void stall(int x, int y, int z, String face, String cloth, String... goods) {
        boolean alongX = face.equals("north") || face.equals("south");
        int fx = dx(face);
        int fz = dz(face);

        for (int s = -2; s <= 2; s++) {
            int bx = x + (alongX ? s : 0);
            int bz = z + (alongX ? 0 : s);
            boolean end = Math.abs(s) == 2;
            // counter on the customer side, goods on it, storage behind
            this.set(bx + fx, y, bz + fz, end ? "spruce_fence" : (s == 0 ? "barrel[facing=up]" : stairs("spruce", opposite(face), true)));
            if (!end && goods.length > 0) {
                this.set(bx + fx, y + 1, bz + fz, goods[Math.floorMod(s + 1, goods.length)]);
            }

            this.set(bx - fx, y, bz - fz, end ? "spruce_fence" : (s == 1 ? "barrel[facing=up]" : (s == -1 ? "composter" : null)));
            if (end) {
                for (int h = 1; h <= 2; h++) {
                    this.set(bx + fx, y + h, bz + fz, "spruce_fence");
                    this.set(bx - fx, y + h, bz - fz, "spruce_fence");
                }
            }

            // cloth roof, lower at the front
            String c = Math.floorMod(s, 2) == 0 ? cloth + "_wool" : "white_wool";
            this.set(bx + fx * 2, y + 2, bz + fz * 2, c.replace("_wool", "_carpet"));
            this.set(bx + fx, y + 3, bz + fz, c.replace("_wool", "_carpet"));
            this.set(bx + fx, y + 2, bz + fz, end ? "spruce_fence" : null);
            this.set(bx, y + 3, bz, c);
            this.set(bx - fx, y + 3, bz - fz, c.replace("_wool", "_carpet"));
        }

        // carpets need something under them
        for (int s = -2; s <= 2; s++) {
            int bx = x + (alongX ? s : 0);
            int bz = z + (alongX ? 0 : s);
            this.set(bx + fx * 2, y + 1, bz + fz * 2, Math.abs(s) == 2 ? "spruce_fence" : null);
            this.set(bx + fx * 2, y + 2, bz + fz * 2, slab("spruce", true));
            this.set(bx + fx, y + 3, bz + fz, Math.floorMod(s, 2) == 0 ? cloth + "_wool" : "white_wool");
            this.set(bx - fx, y + 3, bz - fz, Math.floorMod(s, 2) == 0 ? cloth + "_wool" : "white_wool");
        }

        this.set(x + (alongX ? 2 : 0) + fx * 2, y, z + (alongX ? 0 : 2) + fz * 2, "spruce_fence");
        this.set(x - (alongX ? 2 : 0) + fx * 2, y, z - (alongX ? 0 : 2) + fz * 2, "spruce_fence");
        this.set(x, y + 2, z, "lantern[hanging=true]");
    }

    /** A pile of crates, barrels and sacks. */
    protected void crates(int x, int y, int z, int salt) {
        String[] kinds = {"barrel[facing=up]", "barrel[facing=east]", "hay_block", "composter", "spruce_planks", "barrel[facing=south]"};
        int[][] spots = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {-1, 0}};

        for (int i = 0; i < spots.length; i++) {
            double r = this.hash3(121L + salt, x + i, y, z);
            if (r < 0.75) {
                this.setIfEmpty(x + spots[i][0], y, z + spots[i][1], kinds[(int)(r * 97) % kinds.length]);
                if (r < 0.22) {
                    this.setIfEmpty(x + spots[i][0], y + 1, z + spots[i][1], kinds[(int)(r * 193) % kinds.length]);
                }
            }
        }
    }

    /** A stack of firewood with a roof slab. */
    protected void woodpile(int x, int y, int z, boolean alongX) {
        for (int k = 0; k < 3; k++) {
            int bx = x + (alongX ? k : 0);
            int bz = z + (alongX ? 0 : k);
            this.setIfEmpty(bx, y, bz, log(k == 1 ? "oak_log" : "spruce_log", alongX ? 'z' : 'x'));
            if (k < 2) {
                this.setIfEmpty(bx, y + 1, bz, log("spruce_log", alongX ? 'z' : 'x'));
            }
        }
    }

    /** A street lamp: a stone foot, a post and a lantern hanging from a bracket toward {@code face}. */
    protected void bracketLamp(int x, int y, int z, String face) {
        this.set(x, y, z, "cobblestone_wall");
        for (int i = 1; i <= 3; i++) {
            this.set(x, y + i, z, "dark_oak_fence");
        }

        this.set(x, y + 4, z, "dark_oak_fence");
        this.set(x + dx(face), y + 4, z + dz(face), "dark_oak_fence");
        this.set(x + dx(face), y + 3, z + dz(face), "lantern[hanging=true]");
    }

    /** A notice board on two posts. */
    protected void noticeBoard(int x, int y, int z, boolean alongX) {
        for (int s = -1; s <= 1; s++) {
            int bx = x + (alongX ? s : 0);
            int bz = z + (alongX ? 0 : s);
            this.set(bx, y, bz, s == 0 ? null : "spruce_fence");
            this.set(bx, y + 1, bz, s == 0 ? "bookshelf" : "stripped_spruce_wood[axis=y]");
            this.set(bx, y + 2, bz, s == 0 ? "bookshelf" : "stripped_spruce_wood[axis=y]");
            this.set(bx, y + 3, bz, slab("spruce", false));
        }
    }

    /** What a timber-framed house looks like. */
    public static final class House {
        public int floors = 2;
        public Tile roof = Tile.TEAL;
        public String front = "south";
        /** The ridge runs east-west. */
        public boolean ridgeX = true;
        /** The upper floors stick out one block over the front. */
        public boolean jetty = true;
        /** Plaster between the timbers. */
        public String plaster = "calcite";
        public String frame = "dark_oak_log";
        public boolean chimney = true;
        public boolean dormer;
        /** A hanging shop sign block by the door (null = none). */
        public String sign;
        /** A stone house: upper floors of stone instead of timber frame. */
        public boolean stoneUpper;

        public House floors(int f) {
            this.floors = f;
            return this;
        }

        public House roof(Tile t) {
            this.roof = t;
            return this;
        }

        public House front(String f) {
            this.front = f;
            this.ridgeX = f.equals("north") || f.equals("south");
            return this;
        }

        public House ridge(boolean x) {
            this.ridgeX = x;
            return this;
        }

        public House plaster(String p) {
            this.plaster = p;
            return this;
        }

        public House sign(String s) {
            this.sign = s;
            return this;
        }

        public House dormer(boolean d) {
            this.dormer = d;
            return this;
        }

        public House stone(boolean s) {
            this.stoneUpper = s;
            return this;
        }

        public House chimney(boolean c) {
            this.chimney = c;
            return this;
        }
    }

    /**
     * A timber-framed town house on a stone ground floor: framed and braced upper floors that jetty out over the
     * front, shuttered windows with flower boxes, a porch, a tiled roof with overhangs and a smoking chimney, floors
     * and a few furnishings inside. The footprint is x0..x0+w-1, z0..z0+d-1 at ground level {@code g}.
     * Returns the ridge height.
     */
    protected int timberHouse(int x0, int z0, int w, int d, int g, House s) {
        int x1 = x0 + w - 1;
        int z1 = z0 + d - 1;
        int fx = dx(s.front);
        int fz = dz(s.front);
        int wallTop = g + s.floors * 4;

        // footing and clear space
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean in = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                for (int y = g - 3; y <= g; y++) {
                    if (in || this.empty(x, y, z)) {
                        this.set(x, y, z, in && y == g ? "spruce_planks" : this.stone(x, y, z, g));
                    }
                }

                for (int y = g + 1; y <= wallTop + 14; y++) {
                    this.set(x, y, z, null);
                }
            }
        }

        for (int f = 0; f < s.floors; f++) {
            // the jetty: upper floors are one block deeper toward the front
            int jx0 = x0 + (f > 0 && s.jetty && fx < 0 ? -1 : 0);
            int jx1 = x1 + (f > 0 && s.jetty && fx > 0 ? 1 : 0);
            int jz0 = z0 + (f > 0 && s.jetty && fz < 0 ? -1 : 0);
            int jz1 = z1 + (f > 0 && s.jetty && fz > 0 ? 1 : 0);
            int yb = g + f * 4 + 1;

            for (int x = jx0; x <= jx1; x++) {
                for (int z = jz0; z <= jz1; z++) {
                    boolean ex = x == jx0 || x == jx1;
                    boolean ez = z == jz0 || z == jz1;
                    if (!ex && !ez) {
                        if (f > 0) {
                            this.set(x, yb - 1, z, "spruce_planks");
                        }

                        continue;
                    }

                    boolean corner = ex && ez;
                    int along = ex ? z - jz0 : x - jx0;
                    int len = ex ? jz1 - jz0 : jx1 - jx0;
                    boolean post = corner || along % 3 == 0 && along < len - 1;

                    for (int i = 0; i < 4; i++) {
                        int y = yb + i;
                        String b;
                        if (f == 0) {
                            b = corner ? "stone_bricks" : this.stone(x, y, z, g + 2);
                            if (i == 3) {
                                // the beam the upper floor rests on
                                b = log(s.frame, ex && !corner ? 'z' : 'x');
                            }
                        } else if (s.stoneUpper) {
                            b = corner ? (i % 2 == 0 ? "polished_andesite" : "stone_bricks") : this.stone(x, y, z, g + 40);
                        } else if (post) {
                            b = log(s.frame, 'y');
                        } else if (i == 3) {
                            b = log(s.frame, ex ? 'z' : 'x');
                        } else if (i == 1 && (along % 3 == 1 && Math.floorMod(along / 3 + f, 2) == 0)) {
                            // a rail under some windows breaks the plaster into panels
                            b = log("stripped_" + s.frame, ex ? 'z' : 'x');
                        } else {
                            b = s.plaster;
                        }

                        this.set(x, y, z, b);
                    }
                }
            }

            // windows with shutters and flower boxes
            for (int side = 0; side < 4; side++) {
                String out = new String[]{"north", "south", "west", "east"}[side];
                boolean ns = side < 2;
                int a0 = ns ? jx0 : jz0;
                int a1 = ns ? jx1 : jz1;
                int fixed = side == 0 ? jz0 : (side == 1 ? jz1 : (side == 2 ? jx0 : jx1));

                for (int a = a0 + 2; a <= a1 - 2; a += 3) {
                    if ((a - a0) % 3 != 2) {
                        continue;
                    }

                    int wx = ns ? a : fixed;
                    int wz = ns ? fixed : a;
                    boolean isFront = out.equals(s.front);
                    if (f == 0 && isFront && Math.abs(a - (a0 + a1) / 2) <= 1) {
                        continue; // the door goes here
                    }

                    this.houseWindow(wx, yb + 1, wz, out, f, a);
                }
            }
        }

        // jetty brackets under the overhang
        if (s.jetty && s.floors > 1) {
            boolean ns = fz != 0;
            int a0 = ns ? x0 : z0;
            int a1 = ns ? x1 : z1;
            int fixed = fx > 0 ? x1 + 1 : (fx < 0 ? x0 - 1 : (fz > 0 ? z1 + 1 : z0 - 1));

            for (int a = a0; a <= a1; a++) {
                int bx = ns ? a : fixed;
                int bz = ns ? fixed : a;
                boolean bracket = (a - a0) % 3 == 0 || a == a1;
                this.set(bx, g + 4, bz, bracket ? stairs("dark_oak", opposite(s.front), true) : slab("spruce", true));
            }
        }

        // door, porch and steps
        int doorA = fz != 0 ? (x0 + x1) / 2 : (z0 + z1) / 2;
        int doorX = fx > 0 ? x1 : (fx < 0 ? x0 : doorA);
        int doorZ = fz > 0 ? z1 : (fz < 0 ? z0 : doorA);
        this.set(doorX, g + 1, doorZ, "spruce_door[facing=" + s.front + ",half=lower,hinge=left,open=false]");
        this.set(doorX, g + 2, doorZ, "spruce_door[facing=" + s.front + ",half=upper,hinge=left,open=false]");
        this.set(doorX, g + 3, doorZ, "chiseled_stone_bricks");
        this.set(doorX + fx, g, doorZ + fz, "stone_bricks");
        this.set(doorX + fx, g + 1, doorZ + fz, null);
        this.set(doorX + fx, g + 2, doorZ + fz, null);
        this.set(doorX + fx * 2, g, doorZ + fz * 2, slab("stone_brick", false).replace("type=bottom", "type=top"));
        int px = fz != 0 ? 1 : 0;
        int pz = fx != 0 ? 1 : 0;
        if (s.sign != null) {
            this.set(doorX + px * 2 + fx, g + 3, doorZ + pz * 2 + fz, "dark_oak_fence");
            this.set(doorX + px * 2 + fx * 2, g + 3, doorZ + pz * 2 + fz * 2, "dark_oak_fence");
            this.set(doorX + px * 2 + fx * 2, g + 2, doorZ + pz * 2 + fz * 2, s.sign);
        }

        this.set(doorX - px * 2 + fx, g + 3, doorZ - pz * 2 + fz, "lantern[hanging=true]");
        if (!s.jetty || s.floors == 1) {
            // a small porch roof where no jetty shelters the door
            for (int k = -1; k <= 1; k++) {
                this.set(doorX + px * k + fx, g + 3, doorZ + pz * k + fz, slab(s.roof.stair, false));
            }

            this.set(doorX - px * 2 + fx, g + 3, doorZ - pz * 2 + fz, null);
            this.set(doorX + px + fx, g + 2, doorZ + pz + fz, "lantern[hanging=true]");
        }

        // roof over the widest (jettied) outline
        int rx0 = x0 + (s.jetty && s.floors > 1 && fx < 0 ? -1 : 0);
        int rx1 = x1 + (s.jetty && s.floors > 1 && fx > 0 ? 1 : 0);
        int rz0 = z0 + (s.jetty && s.floors > 1 && fz < 0 ? -1 : 0);
        int rz1 = z1 + (s.jetty && s.floors > 1 && fz > 0 ? 1 : 0);
        int ridge = this.gableRoof(rx0, rz0, rx1, rz1, wallTop + 1, s.ridgeX, s.roof, s.stoneUpper ? "stone_bricks" : "frame");

        // attic window in each gable
        int mid = s.ridgeX ? (rz0 + rz1) / 2 : (rx0 + rx1) / 2;
        if (ridge - wallTop >= 5) {
            if (s.ridgeX) {
                this.set(rx0, wallTop + 3, mid, "glass_pane");
                this.set(rx1, wallTop + 3, mid, "glass_pane");
            } else {
                this.set(mid, wallTop + 3, rz0, "glass_pane");
                this.set(mid, wallTop + 3, rz1, "glass_pane");
            }
        }

        if (s.dormer) {
            this.dormer(rx0, rz0, rx1, rz1, wallTop + 1, s);
        }

        if (s.chimney) {
            // the chimney climbs the back wall and clears the ridge
            int cx = fx != 0 ? (fx > 0 ? x0 : x1) : x0 + 2;
            int cz = fz != 0 ? (fz > 0 ? z0 : z1) : z0 + 2;
            int ox = fx != 0 ? -fx : 0;
            int oz = fz != 0 ? -fz : 0;

            for (int y = g + 1; y <= ridge + 2; y++) {
                this.set(cx + ox, y, cz + oz, y <= g + 2 ? "cobblestone" : this.stone(cx, y, cz, g));
            }

            this.set(cx + ox, g, cz + oz, "cobblestone");
            this.set(cx + ox, ridge + 3, cz + oz, "cobblestone_wall");
            this.set(cx + ox, ridge + 4, cz + oz, "campfire[lit=true]");
            // the hearth inside
            this.set(cx, g + 1, cz, "campfire[lit=true]");
            this.set(cx, g + 2, cz, null);
        }

        this.furnish(x0, z0, x1, z1, g, s);
        return ridge;
    }

    private void houseWindow(int x, int y, int z, String out, int floor, int salt) {
        int ox = dx(out);
        int oz = dz(out);
        int sx = oz != 0 ? 1 : 0;
        int sz = ox != 0 ? 1 : 0;
        this.set(x, y, z, "glass_pane");
        this.set(x, y + 1, z, "glass_pane");
        // shutters hug the wall on both sides, a flower box sits under the sill
        for (int k = -1; k <= 1; k += 2) {
            for (int h = 0; h <= 1; h++) {
                this.setIfEmpty(x + sx * k + ox, y + h, z + sz * k + oz, "spruce_trapdoor[facing=" + out + ",half=bottom,open=true]");
            }
        }

        this.setIfEmpty(x + ox, y - 1, z + oz, "spruce_trapdoor[facing=" + out + ",half=top,open=false]");
        if (floor > 0 || this.hash3(131L, x, y, z) < 0.5) {
            String[] pots = {"potted_red_tulip", "potted_dandelion", "potted_azure_bluet", "potted_poppy", "potted_cornflower", "potted_pink_tulip", "potted_allium"};
            this.setIfEmpty(x + ox, y, z + oz, pots[Math.floorMod(salt + floor * 3 + x + z, pots.length)]);
        }
    }

    private void dormer(int x0, int z0, int x1, int z1, int y, House s) {
        // one small gabled window on the front slope
        int fx = dx(s.front);
        int fz = dz(s.front);
        if (s.ridgeX != (fz != 0)) {
            return; // the front is a gable end: no slope to put a dormer on
        }

        int m = fz != 0 ? (x0 + x1) / 2 + 3 : (z0 + z1) / 2 + 3;
        int ex = fx > 0 ? x1 - 1 : (fx < 0 ? x0 + 1 : m);
        int ez = fz > 0 ? z1 - 1 : (fz < 0 ? z0 + 1 : m);
        this.set(ex, y + 2, ez, "glass_pane");
        this.set(ex, y + 3, ez, slab(s.roof.stair, false));
        int sx = fz != 0 ? 1 : 0;
        int sz = fx != 0 ? 1 : 0;
        this.set(ex + sx, y + 2, ez + sz, log(s.frame, 'y'));
        this.set(ex - sx, y + 2, ez - sz, log(s.frame, 'y'));
        this.set(ex + sx, y + 3, ez + sz, stairs(s.roof.stair, sx == 1 ? "west" : "north", false));
        this.set(ex - sx, y + 3, ez - sz, stairs(s.roof.stair, sx == 1 ? "east" : "south", false));
        this.set(ex - fx, y + 3, ez - fz, s.roof.block);
        this.set(ex + sx - fx, y + 3, ez + sz - fz, s.roof.block);
        this.set(ex - sx - fx, y + 3, ez - sz - fz, s.roof.block);
    }

    /** A few things inside so the windows show a lived-in room. */
    private void furnish(int x0, int z0, int x1, int z1, int g, House s) {
        int mx = (x0 + x1) / 2;
        int mz = (z0 + z1) / 2;
        this.set(mx, g + 3, mz, "lantern[hanging=true]");
        this.setIfEmpty(x0 + 1, g + 1, z0 + 1, "barrel[facing=up]");
        this.setIfEmpty(x1 - 1, g + 1, z0 + 1, "crafting_table");
        this.setIfEmpty(x1 - 1, g + 1, z1 - 1, "bookshelf");
        this.setIfEmpty(x0 + 1, g + 1, z1 - 1, "chest[facing=" + (s.front.equals("east") || s.front.equals("west") ? "south" : "east") + "]");
        this.set(mx, g + 1, mz, "spruce_fence");
        this.set(mx, g + 2, mz, "brown_carpet");
        if (s.floors > 1) {
            this.set(mx, g + 7, mz, "lantern[hanging=true]");
        }
    }
}
