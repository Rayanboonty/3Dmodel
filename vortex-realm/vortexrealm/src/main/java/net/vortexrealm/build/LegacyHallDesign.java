package net.vortexrealm.build;

/**
 * 3.13.0 - "The 1.8 Legacy Hall": a white quartz temple with teal prismarine accents (1.8 was the prismarine
 * update). A columned portico with a pediment leads into a nave lined by colonnades; in the side aisles eight
 * pedestals carry the 1.8 kit fighters, each under an arch with its kit colour; at the back a raised stage holds the
 * arcade modes (The Bridge in the middle) under a "1.8" mosaic. Floor level is y 0 (players stand on y 0).
 * <p>
 * Features: {@code legacy:<kit id>} for the eight kit pedestals, {@code legacy:arcade:<mode>} for the stage,
 * {@code legacy:title} for the sign over the door and {@code legacy:spawn} at the entrance.
 */
public final class LegacyHallDesign extends Voxels {
    /** Kit pedestals, west side then east side, front to back: id and wool colour. */
    public static final String[][] KITS = {
        {"nodebuff18", "red"}, {"debuff", "lime"}, {"gapple18", "yellow"}, {"soup", "brown"},
        {"classic", "light_blue"}, {"archer", "green"}, {"combo18", "magenta"}, {"builduhc18", "orange"}
    };
    /** Arcade modes on the stage, west to east. */
    public static final String[] ARCADE = {"sumo", "bridge", "boxing", "spleef"};
    /** Half sizes of the cella (outer wall faces). */
    private static final int HX = 18;
    private static final int HZ = 16;
    private static final int WALL_TOP = 12;

    /** Terrain height (local y of the top block) at a local x, z, or null for flat ground at y -4. */
    private final java.util.function.IntBinaryOperator ground;

    public LegacyHallDesign(int cx, int floorY, int cz, long seed) {
        this(cx, floorY, cz, seed, null);
    }

    /**
     * @param floorY world y of the hall floor level (players stand on y 0); the ground in front of the steps is 4 lower
     * @param ground the terrain around the hall (local x, z -> local y of the top block), null = flat at y -4
     */
    public LegacyHallDesign(int cx, int floorY, int cz, long seed, java.util.function.IntBinaryOperator ground) {
        super(cx, floorY, cz, 30, -12, 26, seed);
        this.ground = ground;
        this.plinth();
        this.cella();
        this.portico();
        this.roof();
        this.interior();
        this.pedestals();
        this.stage();
        this.grounds();
        this.finish();
        this.spawn = new double[]{0.5, -3.0, -29.5, 0.0};
        this.features.put("legacy:spawn", new double[]{0.5, -3.0, -29.5, 0.0});
        this.features.put("legacy:title", new double[]{0.5, 13.6, -25.0, 180.0});
    }

    /** Front edge (z) of the stepped plinth. */
    public static int frontZ() {
        return -27;
    }

    // ------------------------------------------------------------------ helpers

    private String stoneBrick() {
        int r = this.rnd.nextInt(100);
        return r < 10 ? "mossy_stone_bricks" : (r < 18 ? "cracked_stone_bricks" : "stone_bricks");
    }

    private static String st(String mat, String facing, boolean top) {
        return mat + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    /** The 3x5 pixel font for the "1.8" mosaic (rows top to bottom). */
    private static final String[] ONE = {".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###"};
    private static final String[] DOT = {".", ".", ".", ".", ".", ".", "#"};
    private static final String[] EIGHT = {".#.", "#.#", "#.#", ".#.", "#.#", "#.#", ".#."};

    // ------------------------------------------------------------------ structure

    /** A stepped stone plinth the whole temple stands on; its top (y -1) is the walkway around the walls. */
    private void plinth() {
        for (int x = -21; x <= 21; x++) {
            for (int z = -24; z <= 19; z++) {
                boolean edge = Math.abs(x) == 21 || z == -24 || z == 19;
                for (int y = this.yLo; y <= -2; y++) {
                    this.set(x, y, z, edge ? this.stoneBrick() : "stone");
                }

                boolean corner = Math.abs(x) == 21 && (z == -24 || z == 19);
                this.set(x, -1, z, corner ? "chiseled_stone_bricks" : (edge ? "stone_brick_slab[type=double]" : "polished_andesite"));
                // a plinth base course just above the ground band
                if (edge) {
                    this.set(x, -5, z, "chiseled_stone_bricks");
                }
            }
        }

        // front steps down from the portico, full width of the portico
        for (int s = 1; s <= 3; s++) {
            int z = -24 - s;
            for (int x = -13; x <= 13; x++) {
                this.set(x, -1 - s, z, "quartz_stairs[facing=south,half=bottom]");
                for (int y = this.yLo; y < -1 - s; y++) {
                    this.set(x, y, z, "stone_bricks");
                }
            }

            // stepped cheek walls
            for (int side = -1; side <= 1; side += 2) {
                for (int y = this.yLo; y <= -1 - s; y++) {
                    this.set(side * 14, y, z, this.stoneBrick());
                }

                this.set(side * 14, -s, z, "stone_brick_slab[type=bottom]");
            }
        }

        // the walkway border: sea lantern posts at the corners and along the sides
        for (int[] c : new int[][]{{-21, -24}, {21, -24}, {-21, 19}, {21, 19}, {-21, -3}, {21, -3}}) {
            this.set(c[0], 0, c[1], "quartz_pillar[axis=y]");
            this.set(c[0], 1, c[1], "quartz_pillar[axis=y]");
            this.set(c[0], 2, c[1], "sea_lantern");
            this.set(c[0], 3, c[1], "quartz_slab[type=bottom]");
        }
    }

    /** The cella: quartz walls with pilasters, a prismarine base course and high windows. */
    private void cella() {
        for (int x = -HX; x <= HX; x++) {
            for (int z = -HZ; z <= HZ; z++) {
                boolean wall = Math.abs(x) == HX || Math.abs(z) == HZ;
                for (int y = 0; y <= WALL_TOP; y++) {
                    if (!wall) {
                        this.set(x, y, z, "air");
                        continue;
                    }

                    // along the long sides a pilaster every 5 blocks (z = -15, -10 ... 15), along the ends every 4
                    boolean side = Math.abs(x) == HX;
                    int along = side ? z : x;
                    boolean pilaster = side ? Math.floorMod(along + 15, 5) == 0 : Math.floorMod(along, 4) == 2 || Math.abs(along) == HX;
                    String b;
                    if (y == 0) {
                        b = "dark_prismarine";
                    } else if (y == 1) {
                        b = pilaster ? "chiseled_quartz_block" : "prismarine_bricks";
                    } else if (y == WALL_TOP) {
                        b = "chiseled_quartz_block";
                    } else if (pilaster) {
                        b = y == WALL_TOP - 1 ? "chiseled_quartz_block" : "quartz_pillar[axis=y]";
                    } else {
                        b = "quartz_block";
                    }

                    this.set(x, y, z, b);
                }
            }
        }

        // high windows in the side walls, two panes wide in the middle of each bay (above the kit arches), y 7..10
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -14; z <= 14; z++) {
                int k = Math.floorMod(z + 15, 5);
                if (k != 2 && k != 3) {
                    continue;
                }

                for (int y = 7; y <= 10; y++) {
                    this.set(side * HX, y, z, "light_blue_stained_glass_pane");
                }

                this.set(side * HX, 6, z, "quartz_slab[type=top]");
                this.set(side * HX, 11, z, "chiseled_quartz_block");
            }
        }

        // windows in the back wall either side of the mosaic
        for (int x : new int[]{-15, -14, -13, 13, 14, 15}) {
            for (int y = 3; y <= 10; y++) {
                this.set(x, y, HZ, x == -14 || x == 14 ? "light_blue_stained_glass_pane" : "iron_bars");
            }
        }

        // the grand door: 5 wide, 7 high, with a chiseled frame and a sea lantern keystone
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 6; y++) {
                this.set(x, y, -HZ, "air");
            }
        }

        for (int y = 0; y <= 7; y++) {
            this.set(-3, y, -HZ, "chiseled_quartz_block");
            this.set(3, y, -HZ, "chiseled_quartz_block");
        }

        for (int x = -2; x <= 2; x++) {
            this.set(x, 7, -HZ, x == 0 ? "sea_lantern" : "chiseled_quartz_block");
        }

        this.set(-2, 6, -HZ, st("quartz", "east", true));
        this.set(2, 6, -HZ, st("quartz", "west", true));
        // banners either side of the door
        for (int x : new int[]{-6, 6}) {
            this.set(x, 6, -HZ - 1, "red_wall_banner[facing=north]");
            this.set(x, 7, -HZ - 1, "sea_lantern");
            this.set(x, 7, -HZ, "quartz_block");
        }
    }

    /** A temple front: six columns, an entablature and the pediment of the roof. */
    private void portico() {
        // floor of the portico
        for (int x = -13; x <= 13; x++) {
            for (int z = -24; z <= -HZ - 1; z++) {
                boolean line = x == -10 || x == -6 || x == -2 || x == 2 || x == 6 || x == 10;
                this.set(x, -1, z, (x + z & 1) == 0 ? "quartz_block" : "smooth_quartz");
                if (line && z == -22) {
                    this.set(x, -1, z, "chiseled_quartz_block");
                }

                for (int y = 0; y <= 11; y++) {
                    this.set(x, y, z, "air");
                }
            }
        }

        // the red carpet from the steps to the door
        for (int z = -26; z <= -HZ - 1; z++) {
            for (int x = -1; x <= 1; x++) {
                if (z >= -24) {
                    this.set(x, 0, z, "red_carpet");
                }
            }
        }

        for (int x : new int[]{-10, -6, -2, 2, 6, 10}) {
            this.column(x, -22);
        }

        // corner piers of the portico (against the cella)
        for (int x : new int[]{-13, 13}) {
            for (int y = 0; y <= 11; y++) {
                this.set(x, y, -17, y == 0 || y == 11 ? "chiseled_quartz_block" : "quartz_pillar[axis=y]");
            }
        }

        // entablature over the columns and the portico ceiling
        for (int x = -14; x <= 14; x++) {
            for (int z = -23; z <= -HZ - 1; z++) {
                this.set(x, 12, z, z == -23 || Math.abs(x) == 14 ? "quartz_block" : "smooth_quartz");
            }

            this.set(x, 12, -24, st("quartz", "south", true));
        }

        // a sea lantern frieze under the cornice
        for (int x = -12; x <= 12; x += 4) {
            this.set(x, 11, -23, "sea_lantern");
        }
    }

    /** A free-standing column: base, fluted shaft, capital. */
    private void column(int x, int z) {
        this.set(x, 0, z, "chiseled_quartz_block");
        for (int y = 1; y <= 9; y++) {
            this.set(x, y, z, "quartz_pillar[axis=y]");
        }

        this.set(x, 10, z, "chiseled_quartz_block");
        this.set(x, 11, z, "quartz_block");
        // a wider capital: slabs on the four sides
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}}) {
            String f = d[0] == 1 ? "west" : (d[0] == -1 ? "east" : (d[1] == 1 ? "north" : "south"));
            this.setIfEmpty(x + d[0], 10, z + d[1], st("quartz", f, true));
            this.setIfEmpty(x + d[0], 0, z + d[1], st("quartz", f, false));
        }
    }

    /**
     * A 1:2 gable roof of dark prismarine slabs, ridge along z, over the cella and the portico, with pediments of
     * quartz at both ends, a sea lantern oculus at the front and lantern finials on the ridge.
     */
    private void roof() {
        int half = HX + 1;
        for (int z = -24; z <= HZ + 1; z++) {
            for (int x = -half; x <= half; x++) {
                int h2 = half - Math.abs(x);
                int y = WALL_TOP + 1 + h2 / 2;
                boolean top = (h2 & 1) == 1;
                boolean ridge = h2 == half;
                if (ridge) {
                    this.set(x, y, z, "dark_prismarine");
                    this.set(x, y + 1, z, "dark_prismarine_slab[type=bottom]");
                } else {
                    this.set(x, y, z, "dark_prismarine_slab[type=" + (top ? "top" : "bottom") + "]");
                }

                // under the slab: solid fill down to the walls so the roof never looks hollow
                for (int yy = WALL_TOP + 1; yy < y; yy++) {
                    boolean end = z == -24 || z == HZ + 1;
                    this.set(x, yy, z, end ? "air" : "prismarine_bricks");
                }

                // the eaves line
                if (Math.abs(x) == half) {
                    this.set(x, WALL_TOP, z, z == -24 || z == HZ + 1 ? "quartz_block" : st("quartz", x < 0 ? "east" : "west", true));
                }
            }
        }

        // pediments: the triangle under the roof at both ends, set back one block
        for (int zEnd : new int[]{-23, HZ}) {
            for (int x = -half + 1; x <= half - 1; x++) {
                int h2 = half - Math.abs(x);
                int y = WALL_TOP + 1 + h2 / 2;
                for (int yy = WALL_TOP + 1; yy < y + ((h2 & 1) == 1 ? 1 : 0); yy++) {
                    this.set(x, yy, zEnd, yy == WALL_TOP + 1 ? "quartz_block" : "smooth_quartz");
                }
            }

            int oz = zEnd;
            // the oculus: a sea lantern ringed with quartz stairs
            this.set(0, 17, oz, "sea_lantern");
            this.set(-1, 17, oz, st("quartz", "east", false));
            this.set(1, 17, oz, st("quartz", "west", false));
            this.set(0, 18, oz, "quartz_slab[type=top]");
            this.set(0, 16, oz, "chiseled_quartz_block");
            // acroteria: finials on the ridge ends and the corners
            this.set(0, WALL_TOP + 1 + half / 2 + 2, zEnd == -23 ? -24 : HZ + 1, "sea_lantern");
            this.set(0, WALL_TOP + 1 + half / 2 + 3, zEnd == -23 ? -24 : HZ + 1, "quartz_slab[type=bottom]");
            if (zEnd == HZ) {
                // the back gable has no oculus: the mosaic is behind it
                this.set(0, 17, oz, "smooth_quartz");
                this.set(-1, 17, oz, "smooth_quartz");
                this.set(1, 17, oz, "smooth_quartz");
                this.set(0, 18, oz, "smooth_quartz");
                this.set(0, 16, oz, "smooth_quartz");
            }
        }

        for (int x : new int[]{-half, half}) {
            for (int z : new int[]{-24, HZ + 1}) {
                this.set(x, WALL_TOP + 1, z, "chiseled_quartz_block");
                this.set(x, WALL_TOP + 2, z, "sea_lantern");
                this.set(x, WALL_TOP + 3, z, "quartz_slab[type=bottom]");
            }
        }
    }

    /** Floor, colonnades, ceiling coffers and chandeliers. */
    private void interior() {
        int ix = HX - 1;
        int iz = HZ - 1;
        for (int x = -ix; x <= ix; x++) {
            for (int z = -iz; z <= iz; z++) {
                String f;
                if (Math.abs(x) <= 1) {
                    f = "polished_andesite";
                } else if (Math.abs(x) == 2 || Math.abs(x) == ix || Math.abs(z) == iz) {
                    f = "dark_prismarine";
                } else if (Math.abs(x) <= 6) {
                    // the nave: a diamond pattern of quartz on smooth quartz
                    f = (Math.abs(x - 4) + Math.abs(Math.floorMod(z, 6) - 3)) % 3 == 0 ? "chiseled_quartz_block" : "smooth_quartz";
                } else {
                    f = (x + z & 1) == 0 ? "quartz_block" : "polished_diorite";
                }

                this.set(x, -1, z, f);
            }
        }

        // red carpet down the nave to the stage
        for (int z = -iz; z <= 7; z++) {
            for (int x = -1; x <= 1; x++) {
                this.set(x, 0, z, x == 0 ? "red_carpet" : "white_carpet");
            }
        }

        // colonnades between the nave and the aisles, with arches
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 8;
            for (int z = -12; z <= 8; z += 5) {
                this.set(x, 0, z, "chiseled_quartz_block");
                for (int y = 1; y <= 8; y++) {
                    this.set(x, y, z, "quartz_pillar[axis=y]");
                }

                this.set(x, 9, z, "chiseled_quartz_block");
            }

            // the arcade beam and arches between the columns
            for (int z = -14; z <= 12; z++) {
                this.set(x, 11, z, "quartz_block");
                int k = Math.floorMod(z + 12, 5);
                if (z >= -12 && z <= 8 && k != 0) {
                    // a round-ish arch: stairs next to the columns, a slab at the crown
                    if (k == 1) {
                        this.set(x, 10, z, "quartz_block");
                        this.set(x, 9, z, st("quartz", "north", true));
                    } else if (k == 4) {
                        this.set(x, 10, z, "quartz_block");
                        this.set(x, 9, z, st("quartz", "south", true));
                    } else {
                        this.set(x, 10, z, "quartz_slab[type=top]");
                    }
                } else if (z < -12 || z > 8) {
                    this.set(x, 10, z, "quartz_slab[type=top]");
                }
            }
        }

        // the coffered ceiling: dark oak beams, quartz panels, a sea lantern in every other coffer
        for (int x = -ix; x <= ix; x++) {
            for (int z = -iz; z <= iz; z++) {
                boolean beam = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
                String b = beam ? "dark_oak_planks" : "smooth_quartz";
                if (!beam && Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2 && (Math.floorDiv(x, 4) + Math.floorDiv(z, 4) & 1) == 0) {
                    b = "sea_lantern";
                }

                this.set(x, WALL_TOP, z, b);
            }
        }

        // inside the entrance: a quartz arch and two hanging banners facing the stage
        for (int x = -4; x <= 4; x++) {
            this.set(x, 8, -HZ + 1, Math.abs(x) == 4 ? "chiseled_quartz_block" : (x == 0 ? "sea_lantern" : "quartz_slab[type=top]"));
        }

        for (int x : new int[]{-4, 4}) {
            for (int y = 0; y <= 7; y++) {
                this.set(x, y, -HZ + 1, y == 0 ? "chiseled_quartz_block" : "quartz_pillar[axis=y]");
            }
        }

        for (int x : new int[]{-6, 6}) {
            this.set(x, 7, -HZ + 1, "red_wall_banner[facing=south]");
        }

        // two chandeliers over the nave
        for (int z : new int[]{-8, 2}) {
            for (int y = 9; y <= 11; y++) {
                this.set(0, y, z, "iron_bars");
            }

            this.set(0, 8, z, "sea_lantern");
            this.set(-1, 8, z, "iron_bars");
            this.set(1, 8, z, "iron_bars");
            this.set(0, 8, z - 1, "iron_bars");
            this.set(0, 8, z + 1, "iron_bars");
            for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                this.set(d[0], 7, z + d[1], "end_rod[facing=down]");
            }

            this.set(0, 7, z, "glowstone");
        }

        // soft extra light so no corner is dark
        for (int x = -12; x <= 12; x += 6) {
            for (int z = -12; z <= 12; z += 6) {
                this.setIfEmpty(x, 6, z, "light[level=11]");
            }
        }
    }

    /** Eight kit pedestals in the aisles, each under an arch with its kit colour, the fighter facing the nave. */
    private void pedestals() {
        int[] zs = {-11, -6, -1, 4};
        for (int i = 0; i < KITS.length; i++) {
            int side = i < 4 ? -1 : 1;
            int x = side * 13;
            int z = zs[i % 4];
            String wool = KITS[i][1];
            // base ring of quartz steps, chiseled top, the kit colour around it in the floor
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        this.set(x + dx, -1, z + dz, wool + "_concrete");
                    }
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String b;
                    if (dx == 0 && dz == 0) {
                        b = "chiseled_quartz_block";
                    } else if (dx != 0 && dz != 0) {
                        b = "quartz_slab[type=bottom]";
                    } else {
                        String f = dx == 1 ? "west" : (dx == -1 ? "east" : (dz == 1 ? "north" : "south"));
                        b = st("quartz", f, false);
                    }

                    this.set(x + dx, 0, z + dz, b);
                }
            }

            this.set(x, 1, z, wool + "_carpet");
            // the alcove arch on the outer wall behind the fighter: pillars, a colour banner, a lantern
            int wx = side * (HX - 1);
            for (int dz : new int[]{-2, 2}) {
                for (int y = 0; y <= 5; y++) {
                    this.set(wx, y, z + dz, y == 0 || y == 5 ? "chiseled_quartz_block" : "quartz_pillar[axis=y]");
                }
            }

            for (int dz = -1; dz <= 1; dz++) {
                this.set(wx, 6, z + dz, dz == 0 ? "sea_lantern" : st("quartz", dz < 0 ? "south" : "north", true));
                for (int y = 0; y <= 5; y++) {
                    this.set(wx, y, z + dz, dz == 0 && y == 0 ? "dark_prismarine" : (dz == 0 ? "prismarine_bricks" : "smooth_quartz"));
                }
            }

            String facing = side < 0 ? "east" : "west";
            this.set(wx + (side < 0 ? 1 : -1), 4, z, wool + "_wall_banner[facing=" + facing + "]");
            this.set(wx + (side < 0 ? 1 : -1), 1, z, "flower_pot");
            // the fighter stands on the chiseled block (y 1), facing the nave
            this.features.put("legacy:" + KITS[i][0], new double[]{x + 0.5, 1.0, z + 0.5, side < 0 ? -90.0 : 90.0});
        }
    }

    /** The stage at the back: three steps up, four arcade NPCs, a Bridge model and the "1.8" mosaic. */
    private void stage() {
        int ix = HX - 1;
        for (int x = -ix; x <= ix; x++) {
            for (int z = 8; z <= HZ - 1; z++) {
                this.set(x, 0, z, z == 8 ? st("dark_prismarine", "south", false) : "dark_prismarine");
                if (z >= 9 && Math.abs(x) <= 13) {
                    this.set(x, 1, z, z == 9 ? st("quartz", "south", false) : "smooth_quartz");
                }
            }
        }

        // a band of sea lanterns along the stage front
        for (int x = -12; x <= 12; x += 3) {
            this.set(x, 0, 8, "sea_lantern");
        }

        // a little Bridge model behind the NPCs: red and blue goals joined by a white bridge
        for (int x = -6; x <= 6; x++) {
            this.set(x, 2, 14, Math.abs(x) <= 1 ? "white_terracotta" : (x < 0 ? "red_terracotta" : "blue_terracotta"));
        }

        this.set(-7, 2, 14, "red_terracotta");
        this.set(-7, 3, 14, "red_stained_glass");
        this.set(7, 2, 14, "blue_terracotta");
        this.set(7, 3, 14, "blue_stained_glass");
        int[] xs = {-9, -3, 3, 9};
        for (int i = 0; i < ARCADE.length; i++) {
            this.set(xs[i], 1, 12, "chiseled_quartz_block");
            this.features.put("legacy:arcade:" + ARCADE[i], new double[]{xs[i] + 0.5, 2.0, 12.5, 180.0});
        }

        // the "1.8" mosaic on the back wall: gold on dark prismarine, framed in quartz
        String[][] glyphs = {ONE, DOT, EIGHT};
        int width = 3 + 1 + 1 + 1 + 3;
        int x0 = -width / 2;
        int top = 11;
        for (int x = x0 - 2; x <= x0 + width + 1; x++) {
            for (int y = top - 8; y <= top + 1; y++) {
                boolean frame = x == x0 - 2 || x == x0 + width + 1 || y == top - 8 || y == top + 1;
                this.set(x, y, HZ - 1, frame ? "chiseled_quartz_block" : "dark_prismarine");
            }
        }

        // seen from the nave (looking south) +x is on the left, so the text is written toward -x
        int gx = x0 + width - 1;
        for (String[] g : glyphs) {
            for (int row = 0; row < g.length; row++) {
                for (int col = 0; col < g[row].length(); col++) {
                    if (g[row].charAt(col) == '#') {
                        this.set(gx - col, top - row, HZ - 1, row == 0 || row == g.length - 1 ? "sea_lantern" : "gold_block");
                    }
                }
            }

            gx -= g[0].length() + 1;
        }
    }

    private int groundAt(int x, int z) {
        if (this.ground == null) {
            return -4;
        }

        int g = this.ground.applyAsInt(x, z);
        return g == Integer.MIN_VALUE ? -4 : Math.max(this.yLo, Math.min(-2, g));
    }

    /** Hedges, flowers and lamps around the plinth, and the paved forecourt in front of the steps. */
    private void grounds() {
        String[] flowers = {"poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower", "allium"};
        for (int x = -26; x <= 26; x++) {
            for (int z = -30; z <= 24; z++) {
                boolean inPlinth = Math.abs(x) <= 21 && z >= -24 && z <= 19 || Math.abs(x) <= 14 && z >= -27 && z <= -25;
                if (inPlinth) {
                    continue;
                }

                int g = this.groundAt(x, z);
                // the forecourt: paving at the foot of the steps, on a stone bed down to the terrain
                if (z >= -30 && z <= -28 && Math.abs(x) <= 15) {
                    for (int y = Math.min(g + 1, -5); y <= -5; y++) {
                        this.set(x, y, z, "stone_bricks");
                    }

                    this.set(x, -4, z, Math.abs(x) == 15 || z == -30 ? "chiseled_stone_bricks" : ((x + z & 1) == 0 ? "polished_andesite" : "stone_bricks"));
                    for (int y = -3; y <= 3; y++) {
                        this.set(x, y, z, "air");
                    }

                    continue;
                }

                // a hedge ring hugging the plinth, flowers outside it
                boolean ring = Math.abs(x) == 22 && z >= -24 && z <= 20 || z == 20 && Math.abs(x) <= 22;
                if (ring) {
                    this.set(x, g + 1, z, "oak_leaves[persistent=true]");
                    this.set(x, g + 2, z, "oak_leaves[persistent=true]");
                    if (Math.floorMod(z, 6) == 0 || z == 20 && Math.floorMod(x, 6) == 0) {
                        this.set(x, g + 3, z, "oak_leaves[persistent=true]");
                    }
                } else if ((Math.abs(x) == 23 || z == 21 && Math.abs(x) <= 23) && this.rnd.nextInt(3) == 0) {
                    this.set(x, g + 1, z, flowers[this.rnd.nextInt(flowers.length)]);
                }
            }
        }

        // two lamp posts at the foot of the steps
        for (int x : new int[]{-15, 15}) {
            for (int y = -3; y <= -1; y++) {
                this.set(x, y, -29, "quartz_pillar[axis=y]");
            }

            this.set(x, 0, -29, "sea_lantern");
            this.set(x, 1, -29, "quartz_slab[type=bottom]");
        }
    }

    @Override
    public String name() {
        return "1.8 Legacy Hall";
    }
}
