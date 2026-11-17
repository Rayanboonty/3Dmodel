package net.vortexpvp.build;

/**
 * The Eco SMP spawn, built like a server-network lobby: a floating island with rolling hills, stone cliffs and a deep,
 * eroded underside with hanging spikes; a tiered fountain whose stream runs to the edge and falls off as a waterfall;
 * timber-framed market houses with pitched roofs (Shop, Sell, Shard Shop, Auction House, Orders); a sunken colosseum
 * Warzone in the north; an amethyst geode AFK garden in the east; big branching trees, winding lantern-lit paths and
 * small floating islets around it.
 *
 * Feature spots: "econpc:shop|sell|shards|ah|orders", "zone:afk:min/max", "zone:warzone:min/max", "zone:spawn:min/max",
 * "sign:afk", "sign:warzone", "sign:spawn", "afk:centre".
 */
public final class EcoSpawnDesign extends IslandVoxels {
    public static final int RADIUS = 76;
    private static final int PLAZA = 17;
    // the warzone colosseum (north) and the AFK geode (east)
    private static final int WX = 0;
    private static final int WZ = -42;
    private static final int WR = 14;
    private static final int AX = 42;
    private static final int AZ = 0;
    private static final int AR = 12;

    public EcoSpawnDesign(int cx, int floorY, int cz, long seed) {
        super(cx, floorY, cz, RADIUS, -52, 34, seed, PLAZA, 58.0);
        this.pad(WX, WZ, WR + 5, false);
        this.pad(AX, AZ, AR + 3, false);
        for (int[] p : this.housePads()) {
            this.pad(p[0], p[1], 6, true);
        }

        this.ground();
        this.islets(new int[][]{{-68, -34, 6}, {66, -46, 5}, {-62, 50, 7}, {64, 44, 5}, {-18, 70, 4}});
        this.plaza();
        this.fountain();
        this.stream(135.0);
        this.paths();
        this.houses();
        this.warzone();
        this.geode();
        this.trees(28, "oak", "birch", "cherry", "oak", "dark_oak", "spruce");
        this.flora();
        this.spawn = new double[]{0.5, 1.0, 15.5, 180.0};
        this.feature("sign:spawn", 0, 12.0, 0, 0, 12);
        this.features.put("zone:spawn:min", new double[]{-RADIUS, -60.0, -RADIUS, 0.0});
        this.features.put("zone:spawn:max", new double[]{RADIUS, 60.0, RADIUS, 0.0});
        this.finish();
    }

    @Override
    public String name() {
        return "Eco Spawn";
    }

    // ------------------------------------------------------------------ the plaza and fountain

    private void plaza() {
        for (int x = -PLAZA - 2; x <= PLAZA + 2; x++) {
            for (int z = -PLAZA - 2; z <= PLAZA + 2; z++) {
                double d = dist(x, z);
                if (d > PLAZA + 1.4) {
                    continue;
                }

                String b;
                if (d > PLAZA + 0.3) {
                    b = "polished_andesite";
                } else if (d > PLAZA - 1.2) {
                    b = "stone_bricks";
                } else if (Math.abs(d - 11.5) < 0.6) {
                    b = "chiseled_stone_bricks";
                } else if (d > 11.5) {
                    // a sun pattern of rays
                    double a = Math.toDegrees(Math.atan2(z, x));
                    b = Math.floorMod((int)Math.round(a / 15.0), 2) == 0 ? "smooth_stone" : "polished_andesite";
                } else {
                    b = (Math.floorMod(x, 3) == 0 || Math.floorMod(z, 3) == 0) ? "stone_bricks" : "smooth_stone";
                }

                this.set(x, 0, z, b);
            }
        }

        // a low wall with gaps for the streets and the stream, lamps on the posts
        for (int a = 0; a < 360; a += 3) {
            boolean gap = angDiff(a, 90) < 10 || angDiff(a, 270) < 10 || angDiff(a, 0) < 10 || angDiff(a, 180) < 10 || angDiff(a, 135) < 8
                || angDiff(a, 32) < 8 || angDiff(a, 148) < 8 || angDiff(a, 212) < 8 || angDiff(a, 328) < 8;
            if (!gap) {
                this.set(px(PLAZA + 0.8, a), 1, pz(PLAZA + 0.8, a), "stone_brick_wall");
            }
        }

        for (int a = 0; a < 360; a += 45) {
            int x = px(PLAZA + 0.8, a + 13);
            int z = pz(PLAZA + 0.8, a + 13);
            this.set(x, 1, z, "chiseled_stone_bricks");
            this.set(x, 2, z, "stone_brick_wall");
            this.set(x, 3, z, "lantern");
        }
    }

    private void fountain() {
        // lower basin: a quartz rim with stairs on the outside, water inside
        this.disc(0, 0, 0, 7.6, "prismarine_bricks");
        this.disc(0, 0, 1, 6.6, "water");
        for (int a = 0; a < 360; a += 2) {
            double rad = Math.toRadians(a);
            this.set(px(7.1, a), 1, pz(7.1, a), "quartz_bricks");
            this.set(px(8.0, a), 1, pz(8.0, a), stairs("quartz", facing(-Math.cos(rad), -Math.sin(rad)), false));
        }

        // middle tier and upper bowl on a column
        this.cylinder(0, 0, 1, 3, 2.6, "quartz_bricks", false);
        this.disc(0, 0, 4, 3.6, "smooth_quartz");
        this.disc(0, 0, 5, 2.9, "water");
        this.ring(0, 0, 5, 2.9, 3.7, "quartz_slab[type=bottom]");
        this.cylinder(0, 0, 5, 8, 0.9, "quartz_pillar", false);
        this.disc(0, 0, 9, 1.9, "smooth_quartz_slab[type=bottom]");
        this.set(0, 9, 0, "sea_lantern");
        this.set(0, 10, 0, "water");
        for (int a = 0; a < 360; a += 90) {
            double rad = Math.toRadians(a);
            this.set(px(1.6, a), 8, pz(1.6, a), "quartz_stairs[facing=" + facing(Math.cos(rad), Math.sin(rad)) + ",half=top]");
        }

        // benches facing the fountain
        for (int a = 20; a < 360; a += 40) {
            if (angDiff(a, 90) < 15) {
                continue;
            }

            double rad = Math.toRadians(a);
            this.set(px(9.8, a), 1, pz(9.8, a), stairs("spruce", facing(Math.cos(rad), Math.sin(rad)), false));
        }
    }

    /** Winding paths from the plaza to everything. */
    private void paths() {
        this.ringRoad();
        this.path(90.0, 2, PLAZA + 2, 52);
        this.path(270.0, 2, PLAZA + 2, -WZ - WR - 3);
        this.path(0.0, 2, PLAZA + 2, AX - AR - 3);
        this.path(180.0, 2, PLAZA + 2, 48);
        for (int[] p : this.housePads()) {
            double deg = Math.toDegrees(Math.atan2(p[1], p[0]));
            this.path(deg, 1, PLAZA + 2, (int)dist(p[0], p[1]) - 5);
        }
    }

    // ------------------------------------------------------------------ market houses

    /** House centres; each faces the plaza. */
    private int[][] housePads() {
        return new int[][]{{-27, 17}, {27, 17}, {-27, -17}, {27, -17}, {-34, 0}};
    }

    private void houses() {
        int[][] pads = this.housePads();
        String[] keys = {"shop", "sell", "shards", "ah", "orders"};
        String[] awnings = {"lime", "yellow", "purple", "orange", "light_blue"};
        for (int i = 0; i < pads.length; i++) {
            this.house(pads[i][0], pads[i][1], keys[i], awnings[i]);
        }
    }

    /**
     * A timber-framed market house: cobblestone footing, stripped-log frame with calcite plaster, framed windows with
     * shutters and flower boxes, a steep dark-oak gable roof overhanging every side, a brick chimney, a striped awning
     * over the open counter, barrels and hay inside. The NPC stands in front of the counter.
     */
    private void house(int ox, int oz, String key, String awning) {
        int fx;
        int fz;
        if (Math.abs(ox) >= Math.abs(oz) * 1.5) {
            fx = ox > 0 ? -1 : 1;
            fz = 0;
        } else {
            fx = 0;
            fz = oz > 0 ? -1 : 1;
        }

        House hs = new House(ox, oz, fx, fz);
        for (int u = -6; u <= 6; u++) {
            for (int v = -3; v <= 7; v++) {
                hs.put(u, 0, v, v < 0 ? (Math.abs(u) <= 5 ? "spruce_planks" : "stone_bricks") : "stone_bricks");
                for (int y = 1; y <= 14; y++) {
                    hs.put(u, y, v, null);
                }
            }
        }

        for (int u = -4; u <= 4; u++) {
            for (int v = 0; v <= 6; v++) {
                boolean edgeU = Math.abs(u) == 4;
                boolean edgeV = v == 6;
                if (!edgeU && !edgeV && v != 0) {
                    continue;
                }

                for (int y = 1; y <= 5; y++) {
                    String b;
                    boolean post = edgeU && (v == 0 || v == 3 || v == 6) || edgeV && (Math.abs(u) == 4 || u == 0);
                    if (v == 0 && !edgeU) {
                        b = y == 1 ? "spruce_trapdoor[facing=" + hs.rot("front") + ",half=top,open=false]" : null;
                    } else if (y == 1) {
                        b = "cobblestone";
                    } else if (post) {
                        b = "stripped_spruce_log";
                    } else if (y == 4) {
                        String axis = edgeU ? (fx == 0 ? "z" : "x") : (fx == 0 ? "x" : "z");
                        b = "stripped_spruce_log[axis=" + axis + "]";
                    } else {
                        boolean window = y == 3 && (edgeU && (v == 1 || v == 2 || v == 4 || v == 5) || edgeV && Math.abs(u) == 2);
                        b = window ? "glass_pane" : "calcite";
                    }

                    if (b != null) {
                        hs.put(u, y, v, b);
                    }
                }
            }
        }

        // flower boxes under the side windows (depth on the walls)
        for (int s = -1; s <= 1; s += 2) {
            for (int v = 1; v <= 5; v++) {
                if (v != 3) {
                    hs.put(5 * s, 2, v, "spruce_trapdoor[facing=" + hs.rot(s > 0 ? "right" : "left") + ",half=top,open=false]");
                    hs.put(5 * s, 3, v, this.rnd.nextBoolean() ? "potted_red_tulip" : "potted_allium");
                }
            }
        }

        // the roof: a steep gable along u, overhanging one block on each side, slab ridge, gable walls of plaster
        for (int v = -2; v <= 8; v++) {
            int rise = Math.min(v + 2, 8 - v);
            int y = 5 + rise;
            for (int u = -5; u <= 5; u++) {
                String b = v == 3 ? "dark_oak_slab[type=bottom]" : stairs("dark_oak", hs.rot(v < 3 ? "back" : "front"), false);
                hs.put(u, y, v, b);
                if (Math.abs(u) == 4 && v >= 0 && v <= 6) {
                    for (int yy = 6; yy < y; yy++) {
                        hs.put(u, yy, v, yy == y - 1 && v == 3 ? "stripped_spruce_log" : "calcite");
                    }
                }
            }

            // the underside of the overhang
            if (v == -2 || v == 8) {
                for (int u = -5; u <= 5; u++) {
                    hs.put(u, y - 1, v, stairs("dark_oak", hs.rot(v < 3 ? "front" : "back"), true));
                }
            }
        }

        for (int y = 6; y <= 12; y++) {
            hs.put(2, y, 5, y == 12 ? "campfire[lit=false]" : "bricks");
        }

        // a striped awning over the counter on fence posts
        for (int u = -5; u <= 5; u++) {
            String c = Math.floorMod(u, 2) == 0 ? awning + "_wool" : "white_wool";
            hs.put(u, 4, -1, c);
            hs.put(u, 3, -2, c);
        }

        for (int s = -1; s <= 1; s += 2) {
            hs.put(5 * s, 1, -2, "spruce_fence");
            hs.put(5 * s, 2, -2, "spruce_fence");
            hs.put(6 * s, 1, -1, "azalea_leaves[persistent=true]");
            hs.put(6 * s, 1, 1, "barrel[facing=up]");
        }

        hs.put(-3, 1, 1, "barrel[facing=up]");
        hs.put(-3, 2, 1, "barrel[facing=up]");
        hs.put(3, 1, 2, "hay_block");
        hs.put(2, 1, 1, "composter");
        hs.put(0, 5, 3, "lantern[hanging=true]");
        int[] npc = hs.at(0, -2);
        this.feature("econpc:" + key, npc[0], 1.0, npc[1], 0, 0);
    }

    /** Local (u across the front, v from the front to the back) to world coordinates for one house. */
    private final class House {
        final int ox;
        final int oz;
        final int fx;
        final int fz;

        House(int ox, int oz, int fx, int fz) {
            this.ox = ox;
            this.oz = oz;
            this.fx = fx;
            this.fz = fz;
        }

        int[] at(int u, int v) {
            // (fx, fz) points from the house to the plaza; v grows away from the plaza; u is across
            int rx = -this.fz;
            int rz = this.fx;
            return new int[]{this.ox + rx * u - this.fx * (v - 3), this.oz + rz * u - this.fz * (v - 3)};
        }

        void put(int u, int y, int v, String b) {
            int[] p = this.at(u, v);
            EcoSpawnDesign.this.set(p[0], y, p[1], b);
        }

        /** "front", "back", "left", "right" in this house's frame, as a compass direction. */
        String rot(String local) {
            return switch (local) {
                case "front" -> facing(this.fx, this.fz);
                case "back" -> facing(-this.fx, -this.fz);
                case "right" -> facing(-this.fz, this.fx);
                default -> facing(this.fz, -this.fx);
            };
        }
    }

    // ------------------------------------------------------------------ warzone colosseum

    private void warzone() {
        int floor = -3;
        for (int x = -WR - 9; x <= WR + 9; x++) {
            for (int z = -WR - 9; z <= WR + 9; z++) {
                double d = dist(x, z);
                int wx = WX + x;
                int wz = WZ + z;
                if (this.h(wx, wz) == Integer.MIN_VALUE || d > WR + 8.0) {
                    continue;
                }

                double n = noise(83L, wx * 1.6, wz * 1.6);
                if (d <= WR) {
                    for (int y = floor + 1; y <= 12; y++) {
                        this.set(wx, y, wz, null);
                    }

                    this.set(wx, floor, wz, n > 0.7 ? "gravel" : (n < 0.32 ? "coarse_dirt" : (n < 0.45 ? "rooted_dirt" : "packed_mud")));
                    this.set(wx, floor - 1, wz, "stone");
                } else if (d <= WR + 1.3) {
                    for (int y = floor; y <= 2; y++) {
                        this.set(wx, y, wz, y == 2 ? "polished_blackstone_brick_wall" : (y == floor || y == 1 ? "polished_blackstone" : "deepslate_tiles"));
                    }

                    for (int y = 3; y <= 12; y++) {
                        this.set(wx, y, wz, null);
                    }
                } else {
                    // tiered seating rising outward
                    int step = (int)Math.floor((d - WR - 1.3) / 1.3);
                    int y = Math.min(4, step);
                    for (int yy = floor; yy < y; yy++) {
                        this.set(wx, yy, wz, "stone_bricks");
                    }

                    double a = Math.atan2(z, x);
                    this.set(wx, y, wz, step >= 5 ? "polished_andesite" : stairs("stone_brick", facing(Math.cos(a), Math.sin(a)), false));
                    for (int yy = y + 1; yy <= 12; yy++) {
                        this.set(wx, yy, wz, null);
                    }
                }
            }
        }

        // buttresses around the rim with soul lanterns and red banners facing in
        for (int a = 0; a < 360; a += 30) {
            if (angDiff(a, 90) < 20) {
                continue;
            }

            double rad = Math.toRadians(a);
            int x = WX + px(WR + 1.6, a);
            int z = WZ + pz(WR + 1.6, a);
            for (int y = floor; y <= 6; y++) {
                this.set(x, y, z, y == 6 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
            }

            this.set(x, 7, z, "soul_lantern");
            this.set(WX + px(WR + 0.2, a), 1, WZ + pz(WR + 0.2, a), "red_wall_banner[facing=" + facing(-Math.cos(rad), -Math.sin(rad)) + "]");
        }

        // the gate on the plaza side: an arch over steps down into the pit
        for (int w = -2; w <= 2; w++) {
            for (int s = 0; s <= 9; s++) {
                int x = WX + w;
                int z = WZ + WR - 2 + s;
                int y = Math.max(floor, Math.min(0, floor + s - 2));
                for (int yy = y + 1; yy <= 8; yy++) {
                    this.set(x, yy, z, null);
                }

                this.set(x, y, z, s >= 2 && s <= 4 ? stairs("polished_blackstone_brick", "north", false) : "polished_blackstone_bricks");
            }

            this.set(WX + w, 5, WZ + WR + 1, Math.abs(w) == 2 ? "polished_blackstone_bricks"
                : (Math.abs(w) == 1 ? stairs("polished_blackstone_brick", w < 0 ? "east" : "west", true) : "chiseled_polished_blackstone"));
            this.set(WX + w, 6, WZ + WR + 1, "polished_blackstone_brick_slab[type=bottom]");
        }

        for (int s = -1; s <= 1; s += 2) {
            for (int y = 0; y <= 6; y++) {
                this.set(WX + 3 * s, y, WZ + WR + 1, "polished_blackstone_bricks");
            }

            this.set(WX + 3 * s, 7, WZ + WR + 1, "soul_lantern");
        }

        // cover in the pit: broken columns and crates
        for (int i = 0; i < 9; i++) {
            double a = this.rnd.nextDouble() * Math.PI * 2;
            double r = 3.0 + this.rnd.nextDouble() * (WR - 6);
            int x = WX + (int)Math.round(Math.cos(a) * r);
            int z = WZ + (int)Math.round(Math.sin(a) * r);
            int hh = 1 + this.rnd.nextInt(4);
            for (int y = floor + 1; y <= floor + hh; y++) {
                this.set(x, y, z, y == floor + hh && hh > 2 ? "cracked_stone_bricks" : "stone_bricks");
            }

            if (this.rnd.nextBoolean()) {
                this.set(x + 1, floor + 1, z, "barrel[facing=up]");
            }
        }

        int half = WR - 1;
        this.features.put("zone:warzone:min", new double[]{WX - half, -10.0, WZ - half, 0.0});
        this.features.put("zone:warzone:max", new double[]{WX + half, 30.0, WZ + half, 0.0});
        this.feature("sign:warzone", WX, 8.5, WZ + WR + 1, WX, 0);
    }

    // ------------------------------------------------------------------ AFK geode

    private void geode() {
        int floor = -2;
        for (int x = -AR - 3; x <= AR + 3; x++) {
            for (int z = -AR - 3; z <= AR + 3; z++) {
                double d = dist(x, z);
                int wx = AX + x;
                int wz = AZ + z;
                if (d > AR + 2.5 || this.h(wx, wz) == Integer.MIN_VALUE) {
                    continue;
                }

                if (d <= AR - 1) {
                    for (int y = floor + 1; y <= 12; y++) {
                        this.set(wx, y, wz, null);
                    }

                    double n = noise(97L, wx * 2.0, wz * 2.0);
                    this.set(wx, floor, wz, n > 0.7 ? "amethyst_block" : (n < 0.28 ? "moss_block" : "calcite"));
                    if (this.rnd.nextDouble() < 0.05) {
                        this.set(wx, floor + 1, wz, this.rnd.nextBoolean() ? "amethyst_cluster" : "medium_amethyst_bud");
                    } else if (this.rnd.nextDouble() < 0.05) {
                        this.set(wx, floor + 1, wz, "moss_carpet");
                    }
                } else {
                    // the geode's shell: smooth basalt outside, calcite, amethyst inside - broken, uneven rim
                    double a = Math.toDegrees(Math.atan2(z, x));
                    int shell = (int)Math.round(2 + noise(99L, a * 0.4, 3.0) * 7.0);
                    boolean open = angDiff(a, 180) < 14;
                    for (int y = floor; y <= floor + (open ? 0 : shell); y++) {
                        this.set(wx, y, wz, d > AR + 1.2 ? "smooth_basalt" : (d > AR ? "calcite" : "amethyst_block"));
                    }

                    if (!open && d <= AR && this.rnd.nextDouble() < 0.35) {
                        this.set(wx, floor + shell + 1, wz, "amethyst_cluster");
                    }
                }
            }
        }

        // the crystal spire in the middle, froglights around it, steps down from the west
        this.disc(AX, AZ, floor + 1, 2.4, "budding_amethyst");
        this.cone(AX, AZ, floor + 2, floor + 11, 2.0, "amethyst_block");
        this.set(AX, floor + 12, AZ, "amethyst_cluster");
        for (int a = 0; a < 360; a += 45) {
            this.set(AX + px(6.5, a), floor + 1, AZ + pz(6.5, a), "pearlescent_froglight");
        }

        for (int s = 0; s <= 3; s++) {
            for (int w = -2; w <= 2; w++) {
                this.set(AX - AR - 2 + s, floor + 2 - s, AZ + w, stairs("polished_deepslate", "west", false));
            }
        }

        int half = AR - 2;
        this.features.put("zone:afk:min", new double[]{AX - half, -10.0, AZ - half, 0.0});
        this.features.put("zone:afk:max", new double[]{AX + half, 30.0, AZ + half, 0.0});
        this.feature("sign:afk", AX - AR - 4, 6.0, AZ, 0, AZ);
        this.features.put("afk:centre", new double[]{AX - 5 + 0.5, floor + 1.0, AZ + 0.5, -90.0});
    }
}
