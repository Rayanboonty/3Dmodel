package net.vortexrealm.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ClubHubDesign extends CastleKit {
    private final long seed;
    private final double edge;
    private final List<int[]> footprints = new ArrayList<>();
    private static final int EX = 62;
    private static final int EZ = -62;
    private static final int EH = 6;
    private static final int PZ = 6;
    private static final int PLAZA = 34;
    private static final double[][] STREETS = new double[][]{
        {0.0, -27.0, 0.0, -82.0},
        {-33.0, 6.0, -86.0, 6.0},
        {33.0, 6.0, 86.0, 6.0},
        {0.0, 39.0, 0.0, 90.0},
        {18.0, -16.0, 44.0, -42.0},
        {44.0, -42.0, 52.0, -50.0}
    };
    private static final String[] ANVIL = new String[]{
        "..kkkkkkkkkkkkkk",
        "kkLLLLLLLLLLLLLk",
        ".kkDDDDDDDDDDDDk",
        "...kkkDDDDDDkkk.",
        ".....kDDDDDDk...",
        ".....kDDDDDDk...",
        "....kDDDDDDDDk..",
        "...kDDDDDDDDDDk.",
        "...kkkkkkkkkkkk."
    };
    private static final String[] CHESTPLATE = new String[]{
        ".kkkk......kkkk.",
        "kDDDDk....kDDDDk",
        "kDCDDDkkkkDDDCDk",
        "kDCDDDDDDDDDDCDk",
        "kkDCDDDDDDDDCDkk",
        ".kkDDDDDDDDDDkk.",
        "..kDDDDDDDDDDk..",
        "..kDDCCCCCCDDk..",
        "..kDDDDDDDDDDk..",
        "..kDDDDDDDDDDk..",
        "..kDDCCCCCCDDk..",
        "..kDDDDDDDDDDk..",
        "..kkkkkkkkkkkk.."
    };
    private static final String[] TROPHY = new String[]{
        "..kkkkkkkkkk..",
        "kkYWYYYYYYOYkk",
        "kYkYWYYYYYOkYk",
        "kYkYYYYYYYOkYk",
        ".kkYYYYYYYOkk.",
        "...kYYYYYOk...",
        "....kYYYOk....",
        ".....kYOk.....",
        ".....kYOk.....",
        "....kkkkkk....",
        "...kOOOOOOk...",
        "...kkkkkkkk..."
    };
    private static final Map<Character, String[]> FONT = Map.ofEntries(
        Map.entry('K', new String[]{"1.1", "1.1", "11.", "1.1", "1.1"}),
        Map.entry('I', new String[]{"111", ".1.", ".1.", ".1.", "111"}),
        Map.entry('T', new String[]{"111", ".1.", ".1.", ".1.", ".1."}),
        Map.entry('S', new String[]{"111", "1..", "111", "..1", "111"}),
        Map.entry('H', new String[]{"1.1", "1.1", "111", "1.1", "1.1"}),
        Map.entry('O', new String[]{"111", "1.1", "1.1", "1.1", "111"}),
        Map.entry('P', new String[]{"111", "1.1", "111", "1..", "1.."}),
        Map.entry('E', new String[]{"111", "1..", "111", "1..", "111"}),
        Map.entry('V', new String[]{"1.1", "1.1", "1.1", "1.1", ".1."}),
        Map.entry('N', new String[]{"1.1", "111", "111", "1.1", "1.1"}),
        Map.entry('B', new String[]{"11.", "1.1", "11.", "1.1", "11."})
    );

    public ClubHubDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, Math.max(116, Math.min(140, radius)), -46, 64, seed);
        this.seed = seed;
        this.edge = this.R - 6;
        this.terrain();
        this.plaza();
        this.streets();
        this.spawnPad();
        this.pavilion(-13, 18, ANVIL, "KITS", "anvil");
        this.pavilion(13, 18, CHESTPLATE, "SHOP", "chest");
        this.fountainPlaza();
        this.marketHall(47, -13);
        this.eventHill();
        this.trainingWing();
        this.keep();
        this.namedBuildings();
        this.marketStalls();
        this.wheat(-50, 52);
        this.windmill(-33, 62);
        this.curtainWall();
        this.gatehouse();
        this.postern(1);
        this.postern(-1);
        this.castlePark();
        this.townHouses();
        this.bunting();
        this.lamps();
        this.props();
        this.trees();
        this.spawn = new double[]{0.5, 1.0, 53.5, 180.0};
        this.npc(-13.0, 0.0, 18.0);
        this.npc(13.0, 0.0, 18.0);

        for (double a : new double[]{200.0, 230.0, 310.0, 340.0, 20.0, 160.0}) {
            double r = Math.toRadians(a);
            int nx = (int)Math.round(Math.cos(r) * 26.0);
            int nz = (int)Math.round(6.0 + Math.sin(r) * 26.0);
            this.pedestal(nx, nz);
            this.npc(nx, 2.0, nz);
        }

        this.finish();
    }

    @Override
    public String name() {
        return "Town Square";
    }

    private double n(double x, double z, int salt) {
        return noise(this.seed + salt * 977L, x, z);
    }

    private boolean chance(int x, int z, int salt, double p) {
        return this.n(x * 7.31, z * 7.31, salt) < p;
    }

    private static double smoothstep(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return c * c * (3.0 - 2.0 * c);
    }

    private int ground(int x, int z) {
        double d = Math.hypot(x, z);
        double e = this.edge * (0.92 + 0.1 * this.n(x * 0.5, z * 0.5, 1));
        if (d > e) {
            return Integer.MIN_VALUE;
        } else {
            double start = e * 0.7;
            double h = 0.0;
            if (d > start) {
                double t = (d - start) / (e - start);
                h = 30.0 * smoothstep(t / 0.82) * (0.45 + 0.55 * this.n(x * 1.4, z * 1.4, 2));
            }

            double dp = Math.hypot(x - 62, z - -62);
            if (dp < 20.0) {
                h = 6.0;
            } else if (dp < 32.0) {
                double k = smoothstep((dp - 20.0) / 12.0);
                h = 6.0 + (h - 6.0) * k;
            }

            return (int)Math.round(h);
        }
    }

    private boolean inPlaza(int x, int z) {
        return Math.hypot(x, z - 6) <= 34.0;
    }

    private static double toSegment(double px, double pz, double[] s) {
        double ax = s[0];
        double az = s[1];
        double bx = s[2];
        double bz = s[3];
        double vx = bx - ax;
        double vz = bz - az;
        double t = Math.max(0.0, Math.min(1.0, ((px - ax) * vx + (pz - az) * vz) / (vx * vx + vz * vz)));
        return Math.hypot(px - (ax + vx * t), pz - (az + vz * t));
    }

    private boolean onStreet(int x, int z, double half) {
        for (double[] s : STREETS) {
            if (toSegment(x, z, s) <= half) {
                return true;
            }
        }

        return false;
    }

    private boolean free(int x0, int z0, int x1, int z1, int margin) {
        for (int[] f : this.footprints) {
            if (x0 - margin <= f[2] && x1 + margin >= f[0] && z0 - margin <= f[3] && z1 + margin >= f[1]) {
                return false;
            }
        }

        return true;
    }

    private void claim(int x0, int z0, int x1, int z1) {
        this.footprints.add(new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
    }

    private void terrain() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int top = this.ground(x, z);
                if (top != Integer.MIN_VALUE) {
                    double d = Math.hypot(x, z);
                    double depthK = Math.pow(Math.max(0.0, 1.0 - d / this.edge), 0.55);
                    int bottom = (int)Math.round(-(3.0 + depthK * 40.0 + this.n(x * 1.7, z * 1.7, 5) * 9.0 * depthK));

                    for (int y = bottom; y < top; y++) {
                        String b;
                        if (top - y <= 3) {
                            b = "dirt";
                        } else {
                            double v = this.n(x * 2.2 + y * 3.1, z * 2.2 - y, 6);
                            b = v < 0.18 ? "andesite" : (v < 0.28 ? "cobblestone" : (v > 0.86 ? "tuff" : "stone"));
                        }

                        this.set(x, y, z, b);
                    }

                    boolean steep = top >= 3 && Math.abs(this.ground(x + 1, z) - top) + Math.abs(this.ground(x, z + 1) - top) >= 3;
                    this.set(x, top, z, steep ? (this.chance(x, z, 7, 0.4) ? "stone" : "andesite") : "grass_block");
                }
            }
        }
    }

    private String cobble(int x, int z) {
        double v = this.n(x * 1.9, z * 1.9, 11);
        double w = this.n(x * 6.1, z * 6.1, 12);
        if (v < 0.22) {
            return w < 0.5 ? "cobblestone" : "mossy_cobblestone";
        } else if (v < 0.48) {
            return w < 0.35 ? "cracked_stone_bricks" : "stone_bricks";
        } else if (v < 0.75) {
            return w < 0.5 ? "polished_andesite" : "andesite";
        } else {
            return w < 0.6 ? "stone_bricks" : "cobblestone";
        }
    }

    private void plaza() {
        for (int x = -34; x <= 34; x++) {
            for (int z = -28; z <= 40; z++) {
                double d = Math.hypot(x, z - 6);
                if (!(d > 34.0)) {
                    String b = this.cobble(x, z);
                    if (d > 32.8) {
                        b = "polished_andesite";
                    } else if (Math.abs(d - 31.0) < 0.6) {
                        b = "stone_bricks";
                    }

                    if (Math.abs(d - 14.0) < 0.7 && Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(z - 6, x))), 30) < 6) {
                        b = "chiseled_stone_bricks";
                    }

                    this.set(x, 0, z, b);
                    this.clearAbove(x, z, 0);
                }
            }
        }
    }

    private void clearAbove(int x, int z, int y) {
        for (int k = y + 1; k <= y + 12; k++) {
            String b = this.get(x, k, z);
            if (b != null
                && (b.equals("grass_block") || b.equals("dirt") || b.equals("stone") || b.equals("andesite") || b.equals("cobblestone") || b.equals("tuff"))) {
                this.set(x, k, z, null);
            }
        }
    }

    private void streets() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                if (!this.inPlaza(x, z) && this.onStreet(x, z, 3.6)) {
                    int top = this.ground(x, z);
                    if (top != Integer.MIN_VALUE) {
                        double v = this.n(x * 3.3, z * 3.3, 14);
                        String b = v < 0.55 ? "packed_mud" : (v < 0.8 ? "mud_bricks" : (v < 0.92 ? "coarse_dirt" : "dirt_path"));
                        if (this.onStreet(x, z, 3.6) && !this.onStreet(x, z, 2.6)) {
                            b = v < 0.5 ? "stone_bricks" : "cobblestone";
                        }

                        this.set(x, top, z, b);
                    }
                }
            }
        }
    }

    private void spawnPad() {
        int x0 = -4;
        int x1 = 4;
        int z0 = 38;
        int z1 = 50;

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
                if (rim) {
                    this.set(x, 0, z, "polished_andesite");
                } else {
                    this.set(x, 0, z, null);
                    boolean inner = Math.abs(x) <= 2 && z >= z0 + 2 && z <= z1 - 2;
                    this.set(x, -1, z, inner ? null : "smooth_sandstone");
                    if (inner) {
                        boolean glass = Math.abs(x) <= 1 && z >= z0 + 4 && z <= z1 - 4;
                        this.set(x, -2, z, glass ? "glass" : "smooth_sandstone");
                        if (glass) {
                            this.set(x, -3, z, "sea_lantern");
                            this.set(x, -4, z, "smooth_sandstone");
                        }
                    }
                }
            }
        }

        for (int x = x0 - 1; x <= x1 + 1; x++) {
            this.set(x, 0, z0 - 1, "cobbled_deepslate");
            this.set(x, 0, z1 + 1, "cobbled_deepslate");
        }

        for (int zx = z0 - 1; zx <= z1 + 1; zx++) {
            this.set(x0 - 1, 0, zx, "cobbled_deepslate");
            this.set(x1 + 1, 0, zx, "cobbled_deepslate");
        }
    }

    private static String pixel(char c) {
        return switch (c) {
            case 'C' -> "cyan_concrete";
            case 'D' -> "gray_concrete";
            case 'L' -> "light_gray_concrete";
            case 'O' -> "orange_concrete";
            case 'W' -> "white_concrete";
            case 'Y' -> "yellow_concrete";
            case 'k' -> "black_concrete";
            case 'R' -> "red_concrete";
            default -> null;
        };
    }

    private void sprite(String[] rows, int cx, int y, int z) {
        int w = rows[0].length();

        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < w; c++) {
                String b = pixel(rows[r].charAt(c));
                if (b != null) {
                    this.set(cx - w / 2 + c, y + rows.length - 1 - r, z, b);
                }
            }
        }
    }

    private int board(String text, int cx, int y, int z) {
        int w = text.length() * 4 - 1 + 4;
        int x0 = cx - w / 2;

        for (int x = x0; x < x0 + w; x++) {
            for (int k = 0; k < 9; k++) {
                boolean frame = x == x0 || x == x0 + w - 1 || k == 0 || k == 8;
                this.set(x, y + k, z, frame ? log("stripped_spruce_log", 'x') : "dark_oak_planks");
            }
        }

        int lx = x0 + 2;

        for (char ch : text.toCharArray()) {
            String[] g = FONT.get(ch);
            if (g != null) {
                for (int r = 0; r < 5; r++) {
                    for (int c = 0; c < 3; c++) {
                        if (g[r].charAt(c) == '1') {
                            this.set(lx + c, y + 6 - r, z + 1, "birch_planks");
                        }
                    }
                }
            }

            lx += 4;
        }

        return 9;
    }

    private void pavilion(int cx, int cz, String[] icon, String label, String kind) {
        int x0 = cx - 5;
        int x1 = cx + 5;
        int z0 = cz - 4;
        int z1 = cz + 4;
        this.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1);

        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                boolean rim = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 2;
                if (rim) {
                    String f = Math.abs(x - cx) == 3 ? (x < cx ? "west" : "east") : (z < cz ? "north" : "south");
                    this.set(x, 0, z, stairs("stone_brick", f, false));
                } else {
                    this.set(x, 0, z, null);
                    this.set(x, -1, z, "stone_bricks");
                }
            }
        }

        for (int x = cx - 4; x <= cx + 4; x++) {
            for (int zx = z1 - 1; zx <= z1 + 2; zx++) {
                if (Math.abs(x - cx) >= 3 || zx > z1) {
                    this.set(x, 1, zx, "red_carpet");
                }
            }
        }

        int top = 8;

        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            this.set(c[0], 1, c[1], "stone_bricks");

            for (int y = 2; y <= top; y++) {
                this.set(c[0], y, c[1], log("dark_oak_log", 'y'));
            }

            int ix = c[0] < cx ? 1 : -1;
            int iz = c[1] < cz ? 1 : -1;
            this.set(c[0] + ix, top - 1, c[1], stairs("spruce", ix > 0 ? "west" : "east", true));
            this.set(c[0], top - 1, c[1] + iz, stairs("spruce", iz > 0 ? "north" : "south", true));
        }

        for (int x = x0 - 1; x <= x1 + 1; x++) {
            this.set(x, top + 1, z0, log("spruce_log", 'x'));
            this.set(x, top + 1, z1, log("spruce_log", 'x'));
        }

        for (int zxx = z0; zxx <= z1; zxx++) {
            this.set(x0, top + 1, zxx, log("spruce_log", 'z'));
            this.set(x1, top + 1, zxx, log("spruce_log", 'z'));
            if ((zxx - z0) % 2 == 0) {
                this.set(cx, top + 1, zxx, log("dark_oak_log", 'z'));
            }
        }

        this.set(x0 - 2, top + 1, z0, stairs("spruce", "east", true));
        this.set(x1 + 2, top + 1, z0, stairs("spruce", "west", true));
        this.set(x0 - 2, top + 1, z1, stairs("spruce", "east", true));
        this.set(x1 + 2, top + 1, z1, stairs("spruce", "west", true));

        for (int x = x0; x <= x1; x++) {
            for (int zxxx = z0; zxxx <= z1; zxxx++) {
                this.set(x, top + 2, zxxx, slab("dark_oak", false));
            }
        }

        this.set(cx - 2, top, cz, "iron_chain[axis=y]");
        this.set(cx + 2, top, cz, "iron_chain[axis=y]");
        this.set(cx - 2, top - 1, cz, "lantern[hanging=true]");
        this.set(cx + 2, top - 1, cz, "lantern[hanging=true]");
        int bh = this.board(label, cx, top + 3, z1);

        for (int x : new int[]{cx - 3, cx + 3}) {
            this.set(x, top + 2, z1, log("dark_oak_log", 'y'));
        }

        this.sprite(icon, cx, top + 3 + bh + 1, z1);
        this.set(x0 - 1, 1, z1 + 1, "barrel[facing=up]");
        this.set(x1 + 1, 1, z1 + 1, kind.equals("chest") ? "chest[facing=south]" : "anvil[facing=east]");
    }

    /** Bot maps shown in the wing's alcoves (the mobs of the Bot Practice hall). */
    private static final String[] WING_MAPS = {"PLAINS", "DESERT", "SNOWY", "CHERRY", "CRIMSON", "END"};

    /**
     * The Training Grounds wing: a walled yard with a "BOTS" gate, six alcoves with the bot-map mobs, three NPC
     * stands (find a gamemode, training dummies, academy) and a dummy yard with targets. Placed on the first free
     * spot around the plaza; its NPCs and mobs are spawned from the features when the hub is built.
     */
    private void trainingWing() {
        int[][] spots = {{-56, -30}, {-56, 40}, {58, 38}, {-30, -58}, {30, 62}};
        int hw = 13;
        int hd = 10;
        for (int[] c : spots) {
            int wx = c[0];
            int wz = c[1];
            boolean flat = true;
            for (int x = wx - hw; x <= wx + hw && flat; x += 3) {
                for (int z = wz - hd; z <= wz + hd && flat; z += 3) {
                    int g = this.ground(x, z);
                    flat = g != Integer.MIN_VALUE && g <= 1;
                }
            }

            if (!flat || !this.free(wx - hw, wz - hd, wx + hw, wz + hd, 3)) {
                continue;
            }

            this.claim(wx - hw - 1, wz - hd - 1, wx + hw + 1, wz + hd + 4);
            this.buildWing(wx, wz, hw, hd);
            return;
        }
    }

    private void buildWing(int wx, int wz, int hw, int hd) {
        // the yard: a grid floor like the bot maps, clear above
        for (int x = wx - hw; x <= wx + hw; x++) {
            for (int z = wz - hd; z <= wz + hd; z++) {
                boolean rim = Math.abs(x - wx) == hw || Math.abs(z - wz) == hd;
                this.set(x, 0, z, rim ? "polished_andesite" : (Math.floorMod(x - wx, 4) == 0 || Math.floorMod(z - wz, 4) == 0 ? "polished_andesite" : "smooth_stone"));
                this.set(x, -1, z, "stone");
                for (int y = 1; y <= 12; y++) {
                    this.set(x, y, z, null);
                }
            }
        }

        // low walls with posts and lanterns, the gate in the south wall (towards the town)
        for (int x = wx - hw; x <= wx + hw; x++) {
            for (int z = wz - hd; z <= wz + hd; z++) {
                boolean rim = Math.abs(x - wx) == hw || Math.abs(z - wz) == hd;
                boolean gate = z == wz + hd && Math.abs(x - wx) <= 2;
                if (!rim || gate) {
                    continue;
                }

                boolean post = Math.floorMod(x - wx, 4) == 0 && Math.abs(z - wz) == hd || Math.floorMod(z - wz, 4) == 0 && Math.abs(x - wx) == hw;
                this.set(x, 1, z, "stone_bricks");
                this.set(x, 2, z, post ? "chiseled_stone_bricks" : "stone_brick_wall");
                if (post) {
                    this.set(x, 3, z, "lantern[hanging=false]");
                }
            }
        }

        // the gate: two quartz towers, a BOTS board between them and a target sprite on top
        int gz = wz + hd;
        for (int s = -3; s <= 3; s += 6) {
            for (int y = 1; y <= 9; y++) {
                this.set(wx + s, y, gz, "quartz_pillar[axis=y]");
            }

            this.set(wx + s, 10, gz, "chiseled_quartz_block");
            this.set(wx + s, 11, gz, "sea_lantern");
        }

        int bh = this.board("BOTS", wx, 6, gz);
        this.sprite(TARGET, wx, 6 + bh + 1, gz);
        for (int x = wx - 2; x <= wx + 2; x++) {
            this.set(x, 0, gz, "light_blue_concrete");
            this.set(x, 0, gz + 1, "light_blue_concrete");
            this.set(x, 0, gz + 2, "polished_andesite");
        }

        // six alcoves along the north wall, each with a bot-map mob on a pedestal
        for (int i = 0; i < WING_MAPS.length; i++) {
            int ax = wx - 10 + i * 4;
            int az = wz - hd + 1;
            this.set(ax, 0, az + 1, "chiseled_stone_bricks");
            this.set(ax, 1, az + 1, "polished_andesite_slab[type=bottom]");
            for (int y = 1; y <= 5; y++) {
                this.set(ax - 1, y, az, "stone_bricks");
                this.set(ax + 1, y, az, "stone_bricks");
            }

            this.set(ax, 6, az, "chiseled_stone_bricks");
            this.set(ax, 5, az, "lantern[hanging=true]");
            this.feature("botmap:" + WING_MAPS[i], ax, 1.5, az + 1, ax, az + 8);
        }

        // three NPC stands in the middle: find a gamemode, training dummies, academy
        String[] npcs = {"search", "train", "learn"};
        for (int i = 0; i < 3; i++) {
            int nx = wx - 5 + i * 5;
            int nz = wz;
            this.pedestal(nx, nz);
            this.feature("botnpc:" + npcs[i], nx, 2.0, nz, nx, nz + 8);
        }

        // the dummy yard (east side): hay dummies with pumpkin heads and archery targets
        for (int k = 0; k < 3; k++) {
            int dx = wx + hw - 3;
            int dz = wz - 5 + k * 5;
            this.set(dx, 1, dz, "hay_block");
            this.set(dx, 2, dz, "hay_block");
            this.set(dx, 3, dz, "carved_pumpkin[facing=west]");
            this.set(dx, 2, dz - 1, "oak_fence");
            this.set(dx, 2, dz + 1, "oak_fence");
            this.set(wx - hw + 2, 2, dz, "target");
            this.set(wx - hw + 2, 1, dz, "oak_fence");
        }

        // a weapon rack by the gate
        this.set(wx - 6, 1, gz - 1, "barrel[facing=up]");
        this.set(wx + 6, 1, gz - 1, "barrel[facing=up]");
        this.set(wx - 6, 2, gz - 1, "lantern[hanging=false]");
        this.set(wx + 6, 2, gz - 1, "lantern[hanging=false]");
    }

    private static final String[] TARGET = new String[]{
        "...kkkkk...",
        "..kWWWWWk..",
        ".kWRRRRRWk.",
        "kWRWWWWWRWk",
        "kWRWRRRWRWk",
        "kWRWRYRWRWk",
        "kWRWRRRWRWk",
        "kWRWWWWWRWk",
        ".kWRRRRRWk.",
        "..kWWWWWk..",
        "...kkkkk..."
    };

    private boolean buildable(int x0, int z0, int x1, int z1) {
        if (!this.free(x0, z0, x1, z1, 2)) {
            return false;
        } else if (Math.hypot((x0 + x1) / 2.0 - 62.0, (z0 + z1) / 2.0 - -62.0) < 34.0) {
            return false;
        } else {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    if (this.ground(x, z) != 0 || this.inPlaza(x, z) || this.onStreet(x, z, 4.2)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    // ---------------------------------------------------------------- the castle town (2.4.0)

    /** Centre and radius of the curtain wall (the plaza centre). */
    private static final int WCX = 0;
    private static final int WCZ = 6;
    private static final int WR = 88;
    /** Height of the curtain wall above its footing. */
    private static final int WH = 10;
    /** The keep's platform: x and z bounds and its height. */
    private static final int KX0 = -26;
    private static final int KX1 = 26;
    private static final int KZ0 = -102;
    private static final int KZ1 = -60;
    private static final int KP = 4;
    /** The great hall inside the keep. */
    private static final int HX0 = -14;
    private static final int HX1 = 14;
    private static final int HZ0 = -86;
    private static final int HZ1 = -68;

    private boolean nearWall(int x, int z, double margin) {
        return Math.abs(Math.hypot(x - WCX, z - WCZ) - WR) < margin;
    }

    /** Where the curtain wall gives way: the keep's platform (the keep is its own fortress) and the event hill. */
    private boolean wallGap(double x, double z) {
        return x >= KX0 - 1 && x <= KX1 + 1 && z >= KZ0 - 1 && z <= KZ1 + 1 || Math.hypot(x - EX, z - EZ) < 26.0;
    }

    private int wallBase(double ang) {
        int x = (int)Math.round(WCX + Math.cos(ang) * WR);
        int z = (int)Math.round(WCZ + Math.sin(ang) * WR);
        int g = this.ground(x, z);
        return g == Integer.MIN_VALUE ? Integer.MIN_VALUE : Math.max(0, g);
    }

    /** True for wall cells left open for the east and west posterns (the south gatehouse cuts its own passage). */
    private boolean posternCut(int x, int z) {
        return Math.abs(z - WCZ) <= 2 && Math.abs(Math.abs(x - WCX) - WR) < 4.0;
    }

    /**
     * The curtain wall: five blocks thick, battered at the foot, a string course, arrow slits, a walkable wall-walk
     * between crenellations and a low inner parapet, stepping with the land. It stops at the keep and at the event hill.
     */
    private void curtainWall() {
        int n = WR + 5;

        for (int x = WCX - n; x <= WCX + n; x++) {
            for (int z = WCZ - n; z <= WCZ + n; z++) {
                double d = Math.hypot(x - WCX, z - WCZ);
                double layer = d - WR;
                if (layer < -2.5 || layer > 3.6 || this.wallGap(x, z)) {
                    continue;
                }

                double ang = Math.atan2(z - WCZ, x - WCX);
                int base = this.wallBase(ang);
                if (base == Integer.MIN_VALUE || this.ground(x, z) == Integer.MIN_VALUE) {
                    continue;
                }

                String in = facing(-Math.cos(ang), -Math.sin(ang));
                int seg = (int)Math.floor((ang + Math.PI) * WR / 2.0);
                if (layer > 2.5) {
                    // battered foot outside the wall
                    for (int y = base - 8; y <= base + 2; y++) {
                        this.set(x, y, z, this.stone(x, y, z, base));
                    }

                    this.set(x, base + 3, z, stairs("stone_brick", in, false));
                    for (int y = base + 4; y <= base + 8; y++) {
                        String b = this.get(x, y, z);
                        if (b != null && (b.equals("grass_block") || b.equals("dirt") || b.equals("stone") || b.equals("andesite") || b.equals("tuff") || b.equals("cobblestone"))) {
                            this.set(x, y, z, null);
                        }
                    }

                    continue;
                }

                boolean outer = layer > 1.5;
                boolean inner = layer < -1.5;
                boolean cut = this.posternCut(x, z);

                for (int y = base - 8; y <= base + WH; y++) {
                    int h = y - base;
                    if (cut && h >= 1 && h <= 4) {
                        this.set(x, y, z, null);
                        continue;
                    }

                    String b = this.stone(x, y, z, base);
                    if (outer && h == 6) {
                        b = "polished_andesite";
                    } else if (outer && (h == 3 || h == 4) && Math.floorMod(seg, 9) == 4) {
                        b = "iron_bars";
                    } else if (inner && h == WH - 1 && Math.floorMod(seg, 6) == 0) {
                        // wall-walk drains / corbels on the town side
                        b = "chiseled_stone_bricks";
                    }

                    this.set(x, y, z, b);
                }

                if (cut) {
                    this.set(x, base, z, "cobblestone");
                }

                // walkway surface and parapets
                if (outer) {
                    this.set(x, base + WH + 1, z, this.stone(x, base + WH + 1, z, base + 50));
                    if (Math.floorMod(seg, 2) == 0) {
                        this.set(x, base + WH + 2, z, this.stone(x, base + WH + 2, z, base + 50));
                    }
                } else if (inner) {
                    this.set(x, base + WH + 1, z, Math.floorMod(seg, 12) == 0 ? "chiseled_stone_bricks" : "stone_brick_wall");
                    if (Math.floorMod(seg, 12) == 0) {
                        this.set(x, base + WH + 2, z, "lantern[hanging=false]");
                    }
                } else if (Math.abs(layer) < 0.5 && Math.floorMod(seg, 23) == 11) {
                    this.set(x, base + WH + 1, z, "campfire[lit=true]");
                }

                // anything the hills put above the walk goes
                for (int y = base + WH + (outer || inner ? 2 : 1) + (outer && Math.floorMod(seg, 2) == 0 ? 1 : 0); y <= base + WH + 14; y++) {
                    String b = this.get(x, y, z);
                    if (b != null && !b.contains("lantern")) {
                        this.set(x, y, z, null);
                    }
                }
            }
        }

        this.wallTowers();
    }

    /**
     * Towers along the wall: one every 30 degrees (the gates take the four main points), plus a tower at every end
     * where the wall meets the keep or the event hill. Round towers with tile cones and square towers with hipped
     * roofs alternate; the wall-walk runs through them.
     */
    private void wallTowers() {
        List<Double> at = new ArrayList<>();
        List<Boolean> ends = new ArrayList<>();
        boolean prev = this.wallGap(WCX + Math.cos(0.0) * WR, WCZ + Math.sin(0.0) * WR);

        for (int i = 1; i <= 720; i++) {
            double a = Math.toRadians(i * 0.5);
            boolean gap = this.wallGap(WCX + Math.cos(a) * WR, WCZ + Math.sin(a) * WR);
            if (gap != prev) {
                // pull the end tower back onto the wall
                at.add(a + Math.toRadians(gap ? -3.5 : 3.5));
                ends.add(true);
            }

            prev = gap;
        }

        for (int k = 0; k < 12; k++) {
            if (k % 3 == 0) {
                continue; // east and west posterns, south gatehouse, north keep
            }

            double a = Math.toRadians(k * 30.0);
            boolean clash = this.wallGap(WCX + Math.cos(a) * WR, WCZ + Math.sin(a) * WR);
            for (double e : at) {
                double diff = Math.abs(Math.atan2(Math.sin(a - e), Math.cos(a - e)));
                clash |= diff < Math.toRadians(11.0);
            }

            if (!clash) {
                at.add(a);
                ends.add(false);
            }
        }

        Tile[] roofs = {Tile.TEAL, Tile.SLATE};
        String[] flags = {"blue", "red", "yellow", "white"};

        for (int i = 0; i < at.size(); i++) {
            double a = at.get(i);
            int x = (int)Math.round(WCX + Math.cos(a) * WR);
            int z = (int)Math.round(WCZ + Math.sin(a) * WR);
            int base = this.wallBase(a);
            if (base == Integer.MIN_VALUE) {
                continue;
            }

            String in = facing(-Math.cos(a), -Math.sin(a));
            int deg = (int)Math.round(Math.toDegrees(a));
            boolean round = ends.get(i) || Math.floorMod(deg / 30, 2) == 0;
            int top = base + WH + (ends.get(i) ? 10 : 8);
            if (round) {
                double r = ends.get(i) ? 5.5 : 4.5;
                this.roundTower(x, z, r, base, top, ends.get(i) ? null : roofs[i % 2], 12, flags[i % flags.length], in);
            } else {
                this.squareTower(x, z, 4, base, top, Tile.SLATE, flags[i % flags.length], in);
            }

            // the wall-walk runs through the tower
            double tx = -Math.sin(a);
            double tz = Math.cos(a);
            for (int s = -1; s <= 1; s += 2) {
                for (int k = 3; k <= 6; k++) {
                    for (int w = -1; w <= 1; w++) {
                        int px = (int)Math.round(x + tx * k * s + Math.cos(a) * w * 0.9);
                        int pz = (int)Math.round(z + tz * k * s + Math.sin(a) * w * 0.9);
                        if (Math.hypot(px - x, pz - z) < 6.5) {
                            this.set(px, base + WH + 1, pz, null);
                            this.set(px, base + WH + 2, pz, null);
                            this.set(px, base + WH, pz, "spruce_planks");
                        }
                    }
                }
            }

            this.claim(x - 6, z - 6, x + 6, z + 6);
        }
    }

    /** The main gate: twin drum towers, a deep arched passage with a raised portcullis, machicolations, banners, a dry ditch and a drawbridge. */
    private void gatehouse() {
        int gz = WCZ + WR;
        int base = Math.max(0, this.ground(0, gz));
        int fz0 = gz - 4;
        int fz1 = gz + 4;

        // the block between the towers
        for (int x = -5; x <= 5; x++) {
            for (int z = fz0; z <= fz1; z++) {
                for (int y = base - 8; y <= base + 15; y++) {
                    int h = y - base;
                    boolean passage = Math.abs(x) <= 2 && h >= 1 && h <= 5 || Math.abs(x) <= 1 && h == 6;
                    String b = passage ? null : this.stone(x, y, z, base);
                    if (!passage && h == 6 && Math.abs(x) == 2) {
                        b = stairs("stone_brick", x < 0 ? "east" : "west", true);
                    } else if (!passage && h == 7 && Math.abs(x) <= 1 && (z == fz0 || z == fz1)) {
                        b = "chiseled_stone_bricks";
                    } else if (!passage && (h == 10 || h == 11) && Math.abs(x) == 3 && (z == fz0 || z == fz1)) {
                        b = "glass_pane";
                    } else if (h == 0 && Math.abs(x) <= 2) {
                        b = (x + z) % 3 == 0 ? "cobblestone" : "stone_bricks";
                    }

                    this.set(x, y, z, b);
                }

                for (int y = base + 16; y <= base + 30; y++) {
                    this.set(x, y, z, null);
                }
            }
        }

        // portcullis (raised) and murder holes over the passage
        for (int x = -1; x <= 1; x++) {
            this.set(x, base + 6, fz1 - 1, "iron_bars");
            this.set(x, base + 5, fz1 - 1, "iron_bars");
            this.set(x, base + 6, fz0 + 2, "iron_bars");
        }

        this.set(0, base + 7, gz, "iron_bars");
        this.hangingLantern(0, base + 6, gz - 2, 0, false);
        this.hangingLantern(0, base + 6, gz + 2, 0, false);

        // battlements with machicolations projecting over both faces
        for (int x = -6; x <= 6; x++) {
            for (int z = fz0 - 1; z <= fz1 + 1; z++) {
                boolean rim = Math.abs(x) == 6 || z == fz0 - 1 || z == fz1 + 1;
                if (!rim) {
                    this.set(x, base + 15, z, "stone_bricks");
                    continue;
                }

                if (z == fz0 - 1 || z == fz1 + 1) {
                    this.set(x, base + 14, z, stairs("stone_brick", z < gz ? "south" : "north", true));
                }

                this.set(x, base + 15, z, this.stone(x, base + 15, z, base + 50));
                if (Math.floorMod(x + z, 2) == 0) {
                    this.set(x, base + 16, z, this.stone(x, base + 16, z, base + 50));
                }
            }
        }

        this.flagPole(0, base + 16, gz, 8, "blue");

        // heraldry over the arch and gate pillars with lanterns
        this.clothBanner(0, base + 13, fz1 + 1, "south", "blue", 6);
        this.clothBanner(0, base + 13, fz0 - 1, "north", "red", 6);
        for (int s = -1; s <= 1; s += 2) {
            for (int z : new int[]{fz0 - 1, fz1 + 1}) {
                for (int y = base + 1; y <= base + 3; y++) {
                    this.set(3 * s, y, z, y == base + 1 ? "chiseled_stone_bricks" : "stone_brick_wall");
                }

                this.set(3 * s, base + 4, z, "lantern[hanging=false]");
            }
        }

        // the drum towers
        for (int s = -1; s <= 1; s += 2) {
            this.roundTower(9 * s, gz, 5.0, base, base + 20, Tile.TEAL, 14, s < 0 ? "blue" : "yellow", null);
            // from the passage into the towers
            for (int k = 3; k <= 5; k++) {
                this.set(k * s, base + 1, gz, null);
                this.set(k * s, base + 2, gz, null);
            }
        }

        // a dry ditch outside, spanned by a lowered drawbridge
        for (int x = -13; x <= 13; x++) {
            for (int z = fz1 + 3; z <= fz1 + 7; z++) {
                int g = this.ground(x, z);
                if (g == Integer.MIN_VALUE) {
                    continue;
                }

                boolean bridge = Math.abs(x) <= 2;
                int bottom = base - 4 + (Math.abs(x) > 10 ? 2 : 0);
                for (int y = bottom + 1; y <= Math.max(g, base) + 2; y++) {
                    this.set(x, y, z, null);
                }

                this.set(x, bottom, z, this.hash3(141L, x, 0, z) < 0.3 ? "podzol" : "coarse_dirt");
                if (this.hash3(143L, x, 1, z) < 0.18) {
                    this.set(x, bottom + 1, z, "short_grass");
                }

                if (bridge) {
                    this.set(x, base, z, Math.abs(x) == 2 ? log("dark_oak_log", 'z') : "spruce_planks");
                    this.set(x, base - 1, z, slab("spruce", true));
                }
            }
        }

        for (int z = fz1 + 1; z <= fz1 + 2; z++) {
            for (int x = -2; x <= 2; x++) {
                this.set(x, base, z, "stone_bricks");
                this.set(x, base + 1, z, null);
                this.set(x, base + 2, z, null);
            }
        }

        for (int s = -1; s <= 1; s += 2) {
            this.line(2 * s, base + 1, fz1 + 7, 2 * s, base + 9, fz1 + 1, "iron_chain[axis=y]");
        }

        // a forecourt cut into the hill outside, so the gate shows from the approach, then the road out
        int cut0 = fz1 + 8;
        int cut1 = fz1 + 22;
        this.cutting(0, cut0, 0, 1, cut1 - cut0, 6.0, 13.0, base);
        for (int z = cut0; z <= this.R; z++) {
            for (int x = -1; x <= 1; x++) {
                int g = this.ground(x, z);
                if (g != Integer.MIN_VALUE) {
                    int y = z <= cut1 ? base : g;
                    this.set(x, y, z, x == 0 ? "dirt_path" : "coarse_dirt");
                }
            }
        }

        this.claim(-15, fz0 - 2, 15, fz1 + 8);
    }

    /** East and west posterns: an arched gate through the wall, two square towers and a lantern. */
    private void postern(int s) {
        int x = WCX + s * WR;
        int base = Math.max(0, this.ground(x, WCZ));

        for (int dz = -2; dz <= 2; dz++) {
            for (int k = -3; k <= 3; k++) {
                int px = x + k;
                this.set(px, base + 5, WCZ + dz, Math.abs(dz) == 2 ? stairs("stone_brick", dz < 0 ? "south" : "north", true) : (Math.abs(k) == 3 ? "chiseled_stone_bricks" : this.stone(px, base + 5, WCZ + dz, base)));
            }
        }

        for (int t = -1; t <= 1; t += 2) {
            this.squareTower(x, WCZ + t * 7, 3, base, base + WH + 6, null, t < 0 ? "red" : "blue", null);
        }

        this.hangingLantern(x - s * 3, base + 4, WCZ, 0, false);
        this.hangingLantern(x + s * 3, base + 4, WCZ, 0, false);
        this.clothBanner(x + s * 4, base + WH - 1, WCZ, s > 0 ? "east" : "west", s > 0 ? "blue" : "red", 5);
        this.cutting(x + s * 4, WCZ, s, 0, 10, 3.0, 7.0, base);
        this.claim(x - 5, WCZ - 12, x + 5, WCZ + 12);
    }

    /**
     * Lowers the land in front of a gate to the gate's level: a cutting that starts {@code w0} wide (half width) and
     * opens to {@code w1} over {@code len} blocks along (dx, dz), with stone revetments holding back the hill on
     * both sides and grass on its floor.
     */
    private void cutting(int ox, int oz, int dx, int dz, int len, double w0, double w1, int base) {
        for (int k = 0; k <= len; k++) {
            double w = w0 + (w1 - w0) * k / Math.max(1, len);
            int half = (int)Math.ceil(w);

            for (int t = -half - 1; t <= half + 1; t++) {
                int x = ox + dx * k + (dz != 0 ? t : 0);
                int z = oz + dz * k + (dx != 0 ? t : 0);
                int g = this.ground(x, z);
                if (g == Integer.MIN_VALUE) {
                    continue;
                }

                if (Math.abs(t) <= half) {
                    if (g > base) {
                        for (int y = base + 1; y <= g + 3; y++) {
                            String b = this.get(x, y, z);
                            if (b != null && !b.contains("log") && !b.contains("planks") && !b.contains("chain")) {
                                this.set(x, y, z, null);
                            }
                        }

                        this.set(x, base, z, "grass_block");
                        this.set(x, base - 1, z, "dirt");
                    }
                } else if (g > base) {
                    // the revetment: a battered stone face up to the hill's surface, with a cap
                    for (int y = base - 2; y <= g; y++) {
                        this.set(x, y, z, this.stone(x, y, z, base));
                    }

                    this.set(x, g + 1, z, Math.floorMod(k, 6) == 3 ? "chiseled_stone_bricks" : "stone_brick_wall");
                    if (Math.floorMod(k, 6) == 3) {
                        this.set(x, g + 2, z, "lantern[hanging=false]");
                    }
                }
            }
        }
    }

    /**
     * The keep at the end of the market street: a stone platform with a grand stair, a buttressed great hall with
     * tall windows, rose windows and a steep slate roof, four corner turrets, a tall donjon behind, and a furnished
     * hall inside (throne dais, long tables, chandeliers, hearth). The bailey has a well, a garden and a stable.
     */
    private void keep() {
        this.claim(KX0 - 1, KZ0 - 1, KX1 + 1, KZ1 + 9);

        // the platform: rock below, paving on top, a retaining wall with a parapet
        for (int x = KX0; x <= KX1; x++) {
            for (int z = KZ0; z <= KZ1; z++) {
                boolean edge = x == KX0 || x == KX1 || z == KZ0 || z == KZ1;
                int g = this.ground(x, z);
                int bottom = g == Integer.MIN_VALUE ? -12 - (int)(this.hash3(151L, x, 0, z) * 8.0) : -10;

                for (int y = bottom; y < KP; y++) {
                    this.set(x, y, z, edge || y >= KP - 3 ? this.stone(x, y, z, KP - 3) : (y > -3 ? "dirt" : "stone"));
                }

                this.set(x, KP, z, edge ? "stone_bricks" : this.texture(153L, x, z, "stone_bricks", "polished_andesite", "cobblestone", "cracked_stone_bricks"));
                for (int y = KP + 1; y <= KP + 48; y++) {
                    this.set(x, y, z, null);
                }

                boolean stairGap = z == KZ1 && Math.abs(x) <= 5;
                if (edge && !stairGap) {
                    boolean post = Math.floorMod(x - KX0, 6) == 0 && (z == KZ0 || z == KZ1) || Math.floorMod(z - KZ0, 6) == 0 && (x == KX0 || x == KX1);
                    this.set(x, KP + 1, z, post ? "chiseled_stone_bricks" : "stone_brick_wall");
                    if (post) {
                        this.set(x, KP + 2, z, "lantern[hanging=false]");
                    }
                }
            }
        }

        // the grand stair down to the street, with walls and lamp posts
        for (int j = 0; j <= KP; j++) {
            int z = KZ1 + 1 + j;
            int y = KP - j;

            for (int x = -5; x <= 5; x++) {
                for (int k = -6; k < y; k++) {
                    this.set(x, k, z, "stone_bricks");
                }

                this.set(x, y, z, stairs("stone_brick", "north", false));
                for (int k = y + 1; k <= y + 6; k++) {
                    this.set(x, k, z, null);
                }
            }

            for (int s = -1; s <= 1; s += 2) {
                for (int k = -6; k <= y; k++) {
                    this.set(6 * s, k, z, this.stone(6 * s, k, z, 0));
                }

                this.set(6 * s, y + 1, z, "stone_brick_wall");
            }
        }

        for (int s = -1; s <= 1; s += 2) {
            int z = KZ1 + KP + 2;
            this.set(6 * s, 1, z, "chiseled_stone_bricks");
            this.set(6 * s, 2, z, "stone_brick_wall");
            this.set(6 * s, 3, z, "stone_brick_wall");
            this.set(6 * s, 4, z, "lantern[hanging=false]");
            this.bigPillar(6 * s, KP + 1, KZ1 - 1, 5, "chiseled_stone_bricks", "stone_brick", "stone_bricks", "lantern[hanging=false]");
        }

        this.greatHall();

        // corner turrets and the donjon
        String[] flags = {"red", "blue", "blue", "red"};
        int[][] corners = {{HX0, HZ0}, {HX1, HZ0}, {HX0, HZ1}, {HX1, HZ1}};
        for (int i = 0; i < 4; i++) {
            this.roundTower(corners[i][0], corners[i][1], 3.5, KP, KP + 20, Tile.SLATE, 11, flags[i], null);
        }

        this.squareTower(0, KZ0 + 7, 6, KP, KP + 32, Tile.SLATE, "red", "south");
        this.clothBanner(0, KP + 28, KZ0 + 14, "south", "red", 8);

        // the bailey: a well and a garden on the west, a stable on the east
        this.well(-21, KP + 1, -64);
        this.niceTree(-21, KP + 1, -78, "oak_log", "oak", 6, 4.2);
        this.flowerBed(-25, -72, -18, -70, KP + 1, "stone_brick", "grass_block", "rose_bush", "peony", "allium", "cornflower", "oxeye_daisy");
        this.flowerBed(-25, -88, -18, -86, KP + 1, "stone_brick", "grass_block", "poppy", "dandelion", "azure_bluet", "red_tulip");
        for (int x = -25; x <= -18; x++) {
            for (int z = -84; z <= -74; z++) {
                if (this.empty(x, KP + 1, z)) {
                    this.set(x, KP, z, "grass_block");
                    if (this.hash3(155L, x, 0, z) < 0.2) {
                        this.set(x, KP + 1, z, "short_grass");
                    }
                }
            }
        }

        this.stable(19, -84, 25, -66);
    }

    private void greatHall() {
        int top = KP + 12;

        for (int x = HX0; x <= HX1; x++) {
            for (int z = HZ0; z <= HZ1; z++) {
                boolean ex = x == HX0 || x == HX1;
                boolean ez = z == HZ0 || z == HZ1;
                if (!ex && !ez) {
                    // the floor: dark oak boards with a red runner up the middle to the dais
                    this.set(x, KP, z, Math.abs(x) <= 1 ? "red_wool" : ((x + z * 3) % 7 == 0 ? "spruce_planks" : "dark_oak_planks"));
                    continue;
                }

                for (int y = KP + 1; y <= top; y++) {
                    int h = y - KP;
                    String b = this.stone(x, y, z, KP + 1);
                    if (ex && ez) {
                        b = h % 2 == 0 ? "polished_andesite" : "stone_bricks";
                    } else if (h == 1 || h == 7 || h == 12) {
                        b = "polished_andesite";
                    } else if (ez && Math.floorMod(x, 4) == 2 && h >= 3 && h <= 10 && !(z == HZ1 && Math.abs(x) <= 3 && h <= 7)) {
                        // tall windows between the buttresses, with tracery bands
                        b = h == 10 ? stairs("stone_brick", z == HZ1 ? "north" : "south", true) : (h == 6 ? "yellow_stained_glass_pane" : "light_blue_stained_glass_pane");
                    }

                    this.set(x, y, z, b);
                }
            }
        }

        // buttresses on both long sides
        for (int x = HX0 + 2; x <= HX1 - 2; x += 4) {
            for (int s = -1; s <= 1; s += 2) {
                int wz = s < 0 ? HZ0 : HZ1;
                if (s > 0 && Math.abs(x) <= 4) {
                    continue;
                }

                for (int y = KP + 1; y <= KP + 9; y++) {
                    this.set(x, y, wz + s, this.stone(x, y, wz + s, KP + 1));
                }

                this.set(x, KP + 10, wz + s, stairs("stone_brick", s < 0 ? "south" : "north", false));
                for (int y = KP + 1; y <= KP + 5; y++) {
                    this.set(x, y, wz + 2 * s, this.stone(x, y, wz + 2 * s, KP + 1));
                }

                this.set(x, KP + 6, wz + 2 * s, stairs("stone_brick", s < 0 ? "south" : "north", false));
            }
        }

        // the entrance: an arched doorway with a porch of two columns and banners
        for (int x = -2; x <= 2; x++) {
            for (int y = KP + 1; y <= KP + 5; y++) {
                this.set(x, y, HZ1, null);
            }
        }

        this.set(-2, KP + 6, HZ1, stairs("stone_brick", "east", true));
        this.set(2, KP + 6, HZ1, stairs("stone_brick", "west", true));
        this.set(-1, KP + 6, HZ1, null);
        this.set(0, KP + 6, HZ1, null);
        this.set(1, KP + 6, HZ1, null);
        this.set(0, KP + 7, HZ1, "chiseled_stone_bricks");
        for (int s = -1; s <= 1; s += 2) {
            this.banner(3 * s, KP + 6, HZ1 + 1, "red", "south");
            this.banner(3 * s, KP + 5, HZ1 + 1, "red", "south");
            this.pillar(4 * s, KP + 1, HZ1 + 3, 6, "chiseled_stone_bricks", "stone_brick_wall", "chiseled_stone_bricks", null);
        }

        for (int x = -4; x <= 4; x++) {
            for (int z = HZ1 + 1; z <= HZ1 + 3; z++) {
                this.set(x, KP + 7, z, z == HZ1 + 3 ? stairs("deepslate_tile", "south", false) : "deepslate_tiles");
            }

            this.set(x, KP + 8, HZ1 + 1, stairs("deepslate_tile", "south", false));
        }

        this.hangingLantern(0, KP + 6, HZ1 + 2, 0, false);

        // the roof, a copper fleche on the ridge and rose windows in both gables
        int ridge = this.gableRoof(HX0, HZ0, HX1, HZ1, top + 1, true, Tile.SLATE, "stone_bricks");
        for (int y = ridge + 1; y <= ridge + 7; y++) {
            this.set(0, y, (HZ0 + HZ1) / 2, y == ridge + 1 ? "polished_deepslate" : (y < ridge + 7 ? "deepslate_tile_wall" : "lightning_rod"));
        }

        int mz = (HZ0 + HZ1) / 2;
        for (int x : new int[]{HX0, HX1}) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    double d = Math.hypot(dz, dy);
                    if (d <= 3.2) {
                        String g = d < 1.0 ? "yellow_stained_glass_pane" : (d < 2.3 ? ((dz + dy) % 2 == 0 ? "red_stained_glass_pane" : "blue_stained_glass_pane") : "chiseled_stone_bricks");
                        this.set(x, top + 5 + dy, mz + dz, g);
                    }
                }
            }
        }

        // inside: dais and throne at the north end
        for (int x = -5; x <= 5; x++) {
            for (int z = HZ0 + 1; z <= HZ0 + 3; z++) {
                this.set(x, KP + 1, z, z == HZ0 + 3 || Math.abs(x) == 5 ? stairs("polished_andesite", z == HZ0 + 3 ? "north" : (x < 0 ? "east" : "west"), false) : "polished_andesite");
            }
        }

        this.set(0, KP + 2, HZ0 + 1, "quartz_stairs[facing=south,half=bottom]");
        this.set(-1, KP + 2, HZ0 + 1, "chiseled_quartz_block");
        this.set(1, KP + 2, HZ0 + 1, "chiseled_quartz_block");
        this.set(-1, KP + 3, HZ0 + 1, "gold_block");
        this.set(1, KP + 3, HZ0 + 1, "gold_block");
        this.clothBanner(0, KP + 11, HZ0 + 1, "south", "red", 7);
        for (int s = -1; s <= 1; s += 2) {
            this.set(4 * s, KP + 2, HZ0 + 1, "lantern[hanging=false]");
        }

        // long tables with benches and candles
        for (int s = -1; s <= 1; s += 2) {
            for (int z = HZ0 + 6; z <= HZ1 - 4; z++) {
                this.set(6 * s, KP + 1, z, slab("dark_oak", true));
                this.set(4 * s, KP + 1, z, stairs("spruce", s < 0 ? "west" : "east", false));
                this.set(8 * s, KP + 1, z, stairs("spruce", s < 0 ? "east" : "west", false));
                if (Math.floorMod(z, 3) == 0) {
                    this.set(6 * s, KP + 2, z, "white_candle[candles=3,lit=true]");
                }
            }
        }

        // chandeliers from the roof, banners between the windows, the hearth on the east end
        for (int z = HZ0 + 5; z <= HZ1 - 3; z += 6) {
            this.chandelier(0, ridge - 1, z, ridge - 1 - (KP + 9));
        }

        for (int x = HX0 + 2; x <= HX1 - 2; x += 4) {
            this.banner(x, KP + 7, HZ0 + 1, "blue", "south");
            if (Math.abs(x) > 4) {
                this.banner(x, KP + 7, HZ1 - 1, "blue", "north");
            }
        }

        for (int z = mz - 2; z <= mz + 2; z++) {
            for (int y = KP + 1; y <= KP + 5; y++) {
                boolean frame = Math.abs(z - mz) == 2 || y == KP + 5;
                this.set(HX1 - 1, y, z, frame ? "bricks" : null);
            }
        }

        this.set(HX1 - 1, KP + 1, mz, "campfire[lit=true]");
        this.set(HX1 - 1, KP + 1, mz - 1, "campfire[lit=true]");
        this.set(HX1 - 1, KP + 1, mz + 1, "campfire[lit=true]");
        this.set(HX1 - 1, KP + 6, mz, "bricks");
    }

    private void stable(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean post = (x == x0 || x == x1) && Math.floorMod(z - z0, 4) == 0;
                this.set(x, KP, z, x == x1 ? "spruce_planks" : (this.hash3(157L, x, 0, z) < 0.5 ? "coarse_dirt" : "packed_mud"));
                if (post) {
                    for (int y = KP + 1; y <= KP + 4; y++) {
                        this.set(x, y, z, log("spruce_log", 'y'));
                    }
                } else if (x == x1) {
                    for (int y = KP + 1; y <= KP + 4; y++) {
                        this.set(x, y, z, y == KP + 3 ? "spruce_trapdoor[facing=east,half=bottom,open=true]" : "spruce_planks");
                    }
                }

                // a lean-to roof sloping down to the bailey
                int ry = KP + 5 - (x1 - x) / 3;
                this.set(x, ry, z, stairs("spruce", "east", false));
                if (z > z0 && z < z1 && Math.floorMod(z - z0, 4) == 0 && x > x0 && x < x1) {
                    this.set(x, KP + 1, z, "spruce_fence");
                    this.set(x, KP + 2, z, "spruce_fence");
                }
            }
        }

        for (int z = z0 + 2; z < z1; z += 4) {
            this.set(x1 - 1, KP + 1, z, "hay_block");
            this.set(x1 - 1, KP + 2, z, this.hash3(159L, 0, 0, z) < 0.5 ? "hay_block" : null);
            this.set(x1 - 2, KP + 1, z, "water_cauldron[level=3]");
            this.set(x0, KP + 1, z, "spruce_fence_gate[facing=east,open=false]");
        }

        this.set(x0 + 1, KP + 1, z1 + 2, "target");
        this.set(x0 + 1, KP + 1, z1 + 3, "hay_block");
        this.set(x0 + 1, KP + 2, z1 + 3, "carved_pumpkin[facing=west]");
    }

    /** The plaza's centrepiece: a two-tier fountain inside a compass rose. */
    private void fountainPlaza() {
        int fx = 0;
        int fz = 6;

        for (int x = -13; x <= 13; x++) {
            for (int z = -13; z <= 13; z++) {
                double d = Math.hypot(x, z);
                double a = Math.toDegrees(Math.atan2(z, x));
                if (d > 7.0 && d < 13.0) {
                    double m = Math.floorMod((int)Math.round(a), 90);
                    double m45 = Math.floorMod((int)Math.round(a), 45);
                    double width = (13.0 - d) * 0.9;
                    if (Math.min(m, 90 - m) * Math.PI / 180.0 * d < width) {
                        this.set(fx + x, 0, fz + z, d < 10.0 ? "polished_andesite" : "chiseled_stone_bricks");
                    } else if (Math.min(m45, 45 - m45) * Math.PI / 180.0 * d < width * 0.5) {
                        this.set(fx + x, 0, fz + z, "polished_diorite");
                    }
                }

                if (Math.abs(d - 7.0) < 0.6) {
                    this.set(fx + x, 0, fz + z, "stone_bricks");
                }

                if (d <= 6.3) {
                    boolean rim = d > 5.2;
                    this.set(fx + x, -1, fz + z, "stone_bricks");
                    this.set(fx + x, 0, fz + z, rim ? "stone_bricks" : "water");
                    this.set(fx + x, 1, fz + z, rim ? slab("stone_brick", false) : null);
                }

                if (d <= 3.2) {
                    boolean rim = d > 2.2;
                    this.set(fx + x, 3, fz + z, "stone_bricks");
                    this.set(fx + x, 4, fz + z, rim ? slab("polished_andesite", false) : "water");
                }
            }
        }

        // the shaft carrying the upper basin, and the finial
        for (int y = 1; y <= 3; y++) {
            for (int[] o : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                this.set(fx + o[0], y, fz + o[1], y == 3 ? "stone_bricks" : (o[0] == 0 && o[1] == 0 ? "chiseled_stone_bricks" : "stone_brick_wall"));
            }
        }

        this.set(fx, 4, fz, "chiseled_stone_bricks");
        this.set(fx, 5, fz, "stone_brick_wall");
        this.set(fx, 6, fz, "stone_brick_wall");
        this.set(fx, 7, fz, "polished_andesite");
        this.set(fx, 8, fz, "lantern[hanging=false]");
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            this.set(fx + o[0], 7, fz + o[1], stairs("polished_andesite", facing(-o[0], -o[1]), true));
            this.set(fx + o[0] * 6, 2, fz + o[1] * 6, "lantern[hanging=false]");
        }

        this.claim(fx - 7, fz - 7, fx + 7, fz + 7);
    }

    /** The named buildings: the tavern and the guild hall on the market street, the smithy and the chapel. */
    private void namedBuildings() {
        House tavern = new House().floors(3).roof(Tile.RED).front("east").plaster("white_terracotta").sign("yellow_banner[rotation=4]").dormer(true);
        this.timberHouse(-22, -51, 13, 12, 0, tavern);
        this.claim(-23, -52, -8, -38);
        for (int z : new int[]{-49, -42}) {
            this.set(-6, 1, z, "spruce_fence");
            this.set(-6, 2, z, "oak_pressure_plate");
            this.set(-6, 1, z - 1, stairs("spruce", "south", false));
            this.set(-6, 1, z + 1, stairs("spruce", "north", false));
        }

        this.crates(-23, 1, -37, 1);
        this.set(-21, 1, -37, "barrel[facing=up]");

        House guild = new House().floors(3).roof(Tile.SLATE).front("west").plaster("smooth_sandstone").sign("blue_banner[rotation=12]").dormer(true);
        this.timberHouse(9, -51, 13, 12, 0, guild);
        this.claim(7, -52, 23, -38);
        this.clothBanner(8, 10, -48, "west", "blue", 5);
        this.clothBanner(8, 10, -42, "west", "blue", 5);

        this.smithy(-63, 12);
        this.chapel(51, 14);
    }

    /** The smithy: a stone workshop with an open forge, anvils, a quench trough and a big chimney. */
    private void smithy(int x0, int z0) {
        House s = new House().floors(1).roof(Tile.SLATE).front("north").stone(true).sign("anvil[facing=east]");
        int ridge = this.timberHouse(x0, z0, 11, 9, 0, s);
        this.claim(x0 - 1, z0 - 1, x0 + 17, z0 + 10);
        int fx = x0 + 11;

        // the forge shed on the east side, open toward the street
        for (int x = fx; x <= fx + 5; x++) {
            for (int z = z0; z <= z0 + 8; z++) {
                this.set(x, 0, z, this.hash3(161L, x, 0, z) < 0.4 ? "cobblestone" : "packed_mud");
                for (int y = 1; y <= 8; y++) {
                    this.set(x, y, z, null);
                }

                boolean post = (x == fx + 5 || x == fx) && (z == z0 || z == z0 + 8);
                if (post) {
                    for (int y = 1; y <= 4; y++) {
                        this.set(x, y, z, log("dark_oak_log", 'y'));
                    }
                }

                this.set(x, 5, z, x == fx + 5 || z == z0 || z == z0 + 8 ? slab("deepslate_tile", false) : "deepslate_tiles");
            }
        }

        for (int z = z0 + 5; z <= z0 + 8; z++) {
            for (int x = fx + 3; x <= fx + 5; x++) {
                this.set(x, 1, z, "bricks");
                this.set(x, 2, z, x == fx + 4 && z == z0 + 6 ? "lava_cauldron" : "bricks");
            }
        }

        this.set(fx + 4, 2, z0 + 7, "blast_furnace[facing=north,lit=true]");
        for (int y = 3; y <= ridge + 3; y++) {
            this.set(fx + 4, y, z0 + 7, y < 6 ? "bricks" : this.stone(fx + 4, y, z0 + 7, 0));
            this.set(fx + 5, y, z0 + 7, y < 6 ? "bricks" : this.stone(fx + 5, y, z0 + 7, 0));
        }

        this.set(fx + 4, ridge + 4, z0 + 7, "campfire[lit=true]");
        this.set(fx + 1, 1, z0 + 3, "anvil[facing=east]");
        this.set(fx + 2, 1, z0 + 5, "smithing_table");
        this.set(fx + 1, 1, z0 + 6, "grindstone[face=floor,facing=north]");
        this.set(fx + 3, 1, z0 + 1, "water_cauldron[level=3]");
        this.set(fx + 4, 1, z0 + 1, "water_cauldron[level=3]");
        this.set(fx + 1, 1, z0 + 1, "barrel[facing=up]");
        this.woodpile(fx + 1, 1, z0 + 8, true);
        this.set(fx + 2, 4, z0 + 4, "lantern[hanging=true]");
    }

    /** The chapel: a stone nave with buttresses, lancet windows, a rose window over the door, pews, a bell tower and a churchyard. */
    private void chapel(int x0, int z0) {
        int w = 11;
        int d = 19;
        int x1 = x0 + w - 1;
        int z1 = z0 + d - 1;
        int top = 9;
        int mx = (x0 + x1) / 2;
        this.claim(x0 - 2, z0 - 1, x1 + 12, z1 + 6);

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean ex = x == x0 || x == x1;
                boolean ez = z == z0 || z == z1;
                for (int y = -3; y <= 0; y++) {
                    this.set(x, y, z, y == 0 && !ex && !ez ? (Math.abs(x - mx) <= 0 ? "red_wool" : "polished_andesite") : "stone_bricks");
                }

                for (int y = 1; y <= top + 12; y++) {
                    this.set(x, y, z, null);
                }

                if (!ex && !ez) {
                    continue;
                }

                for (int y = 1; y <= top; y++) {
                    String b = this.stone(x, y, z, 1);
                    if (ex && ez) {
                        b = y % 2 == 0 ? "polished_andesite" : "stone_bricks";
                    } else if (y == 1 || y == top) {
                        b = "polished_andesite";
                    } else if (ex && Math.floorMod(z - z0, 4) == 2 && y >= 3 && y <= 7) {
                        b = y == 7 ? stairs("stone_brick", x == x0 ? "east" : "west", true) : (y == 5 ? "yellow_stained_glass_pane" : "light_blue_stained_glass_pane");
                    }

                    this.set(x, y, z, b);
                }
            }
        }

        // buttresses
        for (int z = z0 + 4; z < z1; z += 4) {
            for (int s : new int[]{x0 - 1, x1 + 1}) {
                for (int y = 1; y <= 6; y++) {
                    this.set(s, y, z, this.stone(s, y, z, 1));
                }

                this.set(s, 7, z, stairs("stone_brick", s < x0 ? "east" : "west", false));
            }
        }

        // the door and rose window in the north gable
        for (int y = 1; y <= 4; y++) {
            this.set(mx, y, z0, null);
            this.set(mx - 1, y, z0, null);
            this.set(mx + 1, y, z0, null);
        }

        this.set(mx - 1, 5, z0, stairs("stone_brick", "east", true));
        this.set(mx + 1, 5, z0, stairs("stone_brick", "west", true));
        this.set(mx, 5, z0, null);
        this.set(mx, 6, z0, "chiseled_stone_bricks");
        this.set(mx - 2, 4, z0 - 1, "lantern[hanging=false]");
        this.set(mx + 2, 4, z0 - 1, "lantern[hanging=false]");
        this.set(mx - 2, 3, z0 - 1, "stone_brick_wall");
        this.set(mx + 2, 3, z0 - 1, "stone_brick_wall");
        this.set(mx - 2, 2, z0 - 1, "stone_brick_wall");
        this.set(mx + 2, 2, z0 - 1, "stone_brick_wall");
        this.set(mx - 2, 1, z0 - 1, "chiseled_stone_bricks");
        this.set(mx + 2, 1, z0 - 1, "chiseled_stone_bricks");
        this.gableRoof(x0, z0, x1, z1, top + 1, false, Tile.SLATE, "stone_bricks");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                double r = Math.hypot(dx, dy);
                if (r <= 2.2) {
                    this.set(mx + dx, top + 4 + dy, z0, r < 0.8 ? "yellow_stained_glass_pane" : (r < 1.8 ? "red_stained_glass_pane" : "chiseled_stone_bricks"));
                }
            }
        }

        // pews and the altar
        for (int z = z0 + 3; z <= z1 - 5; z += 2) {
            for (int x = x0 + 2; x <= x1 - 2; x++) {
                if (Math.abs(x - mx) > 1) {
                    this.set(x, 1, z, stairs("spruce", "south", false));
                }
            }
        }

        for (int x = mx - 2; x <= mx + 2; x++) {
            this.set(x, 1, z1 - 2, "polished_andesite");
            this.set(x, 1, z1 - 1, "polished_andesite");
        }

        this.set(mx, 2, z1 - 2, "chiseled_quartz_block");
        this.set(mx - 1, 2, z1 - 2, "white_candle[candles=4,lit=true]");
        this.set(mx + 1, 2, z1 - 2, "white_candle[candles=4,lit=true]");
        this.clothBanner(mx, 8, z1 - 1, "north", "white", 5);
        this.chandelier(mx, top + 4, z0 + 6, 3);
        this.chandelier(mx, top + 4, z0 + 12, 3);

        // bell tower behind the apse
        this.squareTower(mx, z1 + 4, 3, 0, 18, Tile.SLATE, "white", null);
        this.set(mx, 21, z1 + 4, "bell[attachment=ceiling,facing=north]");

        // the churchyard
        int gx0 = x1 + 3;
        int gx1 = x1 + 11;
        for (int x = gx0; x <= gx1; x++) {
            for (int z = z0 + 1; z <= z1; z++) {
                boolean rim = x == gx0 || x == gx1 || z == z0 + 1 || z == z1;
                int g = Math.max(0, this.ground(x, z));
                this.set(x, g, z, this.hash3(163L, x, 0, z) < 0.3 ? "podzol" : "grass_block");
                if (rim) {
                    this.set(x, g + 1, z, z == z0 + 1 && x == gx0 + 4 ? null : "cobblestone_wall");
                } else if (Math.floorMod(x - gx0, 3) == 2 && Math.floorMod(z - z0, 3) == 0) {
                    this.set(x, g + 1, z, this.hash3(165L, x, 1, z) < 0.5 ? "mossy_stone_brick_wall" : "andesite_wall");
                    this.set(x, g, z + 1, "coarse_dirt");
                    if (this.hash3(167L, x, 2, z) < 0.3) {
                        this.set(x, g + 1, z + 1, "white_candle[candles=1,lit=true]");
                    }
                }
            }
        }

        this.niceTree(gx1 - 2, 1, z1 - 2, "dark_oak_log", "dark_oak", 5, 3.0);
    }

    /**
     * The castle park in place of houses next to the keep: lawns, a gravel walk round the platform with lamp posts
     * and hedges, oaks and birches, flower beds, benches facing the keep and a small pond with lily pads.
     */
    private void castlePark() {
        int px0 = KX0 - 24;
        int px1 = KX1 + 24;
        int pz0 = KZ0;
        int pz1 = KZ1 + 22;
        java.util.List<int[]> spots = new ArrayList<>();

        for (int x = px0; x <= px1; x++) {
            for (int z = pz0; z <= pz1; z++) {
                if (x >= KX0 && x <= KX1 && z >= KZ0 && z <= KZ1 + KP + 1 || Math.abs(x) <= 6 && z > KZ1) {
                    continue; // the keep itself and the market street
                }

                int g = this.ground(x, z);
                if (g == Integer.MIN_VALUE || g > 2 || !this.free(x, z, x, z, 0) || this.onStreet(x, z, 3.8) || this.nearWall(x, z, 6.0) || this.inPlaza(x, z)) {
                    continue;
                }

                int ddx = Math.max(Math.max(KX0 - x, x - KX1), 0);
                int ddz = Math.max(Math.max(KZ0 - z, z - KZ1), 0);
                int dist = Math.max(ddx, ddz);
                for (int y = g + 1; y <= g + 10; y++) {
                    String b = this.get(x, y, z);
                    if (b != null && (b.contains("grass") || b.contains("leaves") || b.contains("log") || b.equals("stone") || b.equals("dirt"))) {
                        this.set(x, y, z, null);
                    }
                }

                if (dist == 2) {
                    // gravel walk round the platform
                    this.set(x, g, z, this.hash3(171L, x, 0, z) < 0.25 ? "coarse_dirt" : "gravel");
                    if (Math.floorMod(x + z, 9) == 0) {
                        this.bracketLamp(x, g + 1, z, facing(KX0 > x ? 1 : (x > KX1 ? -1 : 0), KZ0 > z ? 1 : (z > KZ1 ? -1 : 0)));
                    }
                } else if (dist == 1 || dist == 3) {
                    // low hedges lining the walk
                    this.set(x, g, z, "grass_block");
                    this.set(x, g + 1, z, leaves(dist == 1 ? "azalea" : "oak"));
                } else {
                    this.set(x, g, z, this.hash3(173L, x, 0, z) < 0.06 ? "moss_block" : "grass_block");
                    double v = this.hash3(175L, x, 1, z);
                    if (v < 0.08) {
                        this.set(x, g + 1, z, "short_grass");
                    } else if (v > 0.985) {
                        String[] fl = {"lily_of_the_valley", "oxeye_daisy", "cornflower", "allium", "azure_bluet"};
                        this.set(x, g + 1, z, fl[(int)(this.hash3(177L, x, 2, z) * fl.length)]);
                    }

                    if (dist >= 6 && Math.floorMod(x, 9) == 4 && Math.floorMod(z, 9) == 2) {
                        spots.add(new int[]{x, g, z, dist});
                    }
                }
            }
        }

        // trees, flower beds and benches on the lawn spots, a little jittered
        int n = 0;
        for (int[] sp : spots) {
            int x = sp[0] + (int)Math.round((this.hash3(181L, sp[0], 0, sp[2]) - 0.5) * 4.0);
            int z = sp[2] + (int)Math.round((this.hash3(183L, sp[0], 0, sp[2]) - 0.5) * 4.0);
            int g = sp[1];
            if (!this.free(x - 2, z - 2, x + 2, z + 2, 0) || this.onStreet(x, z, 5.0) || !"grass_block".equals(this.get(x, g, z))) {
                continue;
            }

            switch (n++ % 4) {
                case 0, 2 -> this.niceTree(x, g + 1, z, n % 3 == 0 ? "birch_log" : "oak_log", n % 3 == 0 ? "birch" : "oak", 5 + n % 3, 3.0 + n % 2 * 0.6);
                case 1 -> this.flowerBed(x - 2, z - 1, x + 2, z + 1, g + 1, "stone_brick", "grass_block", "rose_bush", "peony", "red_tulip", "white_tulip", "allium");
                default -> {
                    String face = facing(Math.abs(x) > KX1 ? -Math.signum(x) : 0, z > KZ1 ? -1 : 0);
                    boolean alongX = face.equals("north") || face.equals("south");
                    for (int s = -1; s <= 1; s++) {
                        this.set(alongX ? x + s : x, g + 1, alongX ? z : z + s, stairs("spruce", opposite(face), false));
                    }

                    this.set(alongX ? x - 2 : x, g + 1, alongX ? z : z - 2, "spruce_trapdoor[facing=" + (alongX ? "west" : "north") + ",half=bottom,open=true]");
                    this.set(alongX ? x + 2 : x, g + 1, alongX ? z : z + 2, "spruce_trapdoor[facing=" + (alongX ? "east" : "south") + ",half=bottom,open=true]");
                }
            }

            this.claim(x - 2, z - 2, x + 2, z + 2);
        }

        // a pond with lily pads and reeds on the west lawn
        int ox = KX0 - 14;
        int oz = (KZ0 + KZ1) / 2 + 6;
        if (this.free(ox - 6, oz - 4, ox + 6, oz + 4, 0) && this.ground(ox, oz) == 0) {
            for (int x = ox - 6; x <= ox + 6; x++) {
                for (int z = oz - 4; z <= oz + 4; z++) {
                    double e = Math.pow((x - ox) / 6.2, 2) + Math.pow((z - oz) / 4.2, 2);
                    if (e <= 1.0) {
                        boolean edge = e > 0.68;
                        this.set(x, -2, z, "mud");
                        this.set(x, -1, z, edge ? "mud" : "water");
                        this.set(x, 0, z, "water");
                        this.set(x, 1, z, edge && this.hash3(185L, x, 0, z) < 0.35 ? "sugar_cane" : (!edge && this.hash3(187L, x, 0, z) < 0.12 ? "lily_pad" : null));
                    } else if (e <= 1.35) {
                        this.set(x, 0, z, this.hash3(189L, x, 0, z) < 0.5 ? "mossy_cobblestone" : "grass_block");
                    }
                }
            }

            this.claim(ox - 7, oz - 5, ox + 7, oz + 5);
        }

        // houses keep their distance: the lawns either side of the market street
        this.claim(px0, pz0, -7, pz1);
        this.claim(7, pz0, px1, pz1);
    }

    /** True when the design leaves this block empty (self-test: walkable paths). */
    public boolean clearAt(int x, int y, int z) {
        String b = this.get(x, y, z);
        return b == null || b.equals("air") || b.contains("carpet") || b.contains("pressure_plate") || b.contains("grass") && !b.contains("block");
    }

    /** Market stalls along the street up to the keep. */
    private void marketStalls() {
        this.stall(-8, 1, -34, "east", "red", "melon", "pumpkin", "hay_block");
        this.stall(8, 1, -34, "west", "blue", "barrel[facing=up]", "bookshelf", "chest[facing=west]");
        this.claim(-11, -37, -5, -31);
        this.claim(5, -37, 11, -31);
        this.cart(-8, 1, -27, "east", "pumpkin");
    }

    /** A windmill by the wheat field: a stone tower, a timber cap and four sails. */
    private void windmill(int x, int z) {
        if (!this.free(x - 5, z - 5, x + 5, z + 5, 1)) {
            return;
        }

        this.claim(x - 5, z - 5, x + 5, z + 5);
        for (int y = -3; y <= 10; y++) {
            double r = 3.6 - y * 0.08;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d <= r) {
                        boolean shell = d > r - 1.1;
                        this.set(x + dx, y, z + dz, shell || y <= 0 ? (y > 6 ? (y % 2 == 0 ? "stripped_spruce_wood[axis=y]" : "spruce_planks") : this.stone(x + dx, y, z + dz, 0)) : (y % 5 == 0 ? "spruce_planks" : null));
                    }
                }
            }
        }

        this.set(x, 1, z + 3, "spruce_door[facing=south,half=lower,hinge=left,open=false]");
        this.set(x, 2, z + 3, "spruce_door[facing=south,half=upper,hinge=left,open=false]");
        this.set(x, 3, z + 4, "lantern[hanging=true]");
        this.set(x + 3, 5, z, "glass_pane");
        this.set(x - 3, 5, z, "glass_pane");
        this.cone(x, z, 11, 15, 3.8, "dark_oak_planks");
        this.set(x, 16, z, "dark_oak_fence");
        // the sails on the south face, set as an X
        int hz = z + 4;
        int hy = 10;
        this.set(x, hy, hz - 1, log("dark_oak_log", 'z'));
        this.set(x, hy, hz, "dark_oak_planks");
        for (int t = 1; t <= 7; t++) {
            for (int[] q : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
                int sx = x + q[0] * t;
                int sy = hy + q[1] * t;
                this.set(sx, sy, hz, "stripped_spruce_wood[axis=y]");
                if (t >= 2) {
                    // cloth on one side of each arm, so the sails turn the same way
                    this.set(sx + (q[0] == q[1] ? -q[0] : 0), sy + (q[0] == q[1] ? 0 : -q[1]), hz, "white_wool");
                }
            }
        }
    }

    /**
     * The town's houses: timber-framed and stone houses of two and three floors with jetties, shutters, flower boxes,
     * chimneys and dormers, in rings around the plaza, every one a little different. Crates, woodpiles and carts sit
     * around them.
     */
    private void townHouses() {
        Tile[] roofs = {Tile.TEAL, Tile.RED, Tile.SLATE, Tile.CLAY, Tile.TEAL, Tile.MOSS};
        String[] plasters = {"calcite", "white_terracotta", "smooth_sandstone", "calcite", "mushroom_stem"};
        String[] signs = {null, "red_banner[rotation=%d]", null, "lime_banner[rotation=%d]", null, null, "light_blue_banner[rotation=%d]"};
        int i = 0;

        double[] rings = {46.0, 58.0, 71.0};
        for (int ring = 0; ring < rings.length; ring++) {
            double rad = rings[ring];
            double step = Math.toDegrees(17.0 / rad);

            for (double a = ring * 5.0; a < 360.0; a += step) {
                double r = 0.0;
                int x0 = 0;
                int z0 = 0;
                int w = 0;
                int d = 0;
                boolean ok = false;

                // try the spot, then a little to either side, and a smaller house
                for (double shift : new double[]{0.0, step * 0.3, -step * 0.3}) {
                    for (int shrink = 0; shrink <= 2 && !ok; shrink += 2) {
                        r = Math.toRadians(a + shift);
                        int hx = (int)Math.round(WCX + Math.cos(r) * rad);
                        int hz = (int)Math.round(WCZ + Math.sin(r) * rad);
                        w = 9 + (int)Math.round(this.n(hx, hz, 31) * 4.0) - shrink;
                        d = 8 + (int)Math.round(this.n(hz, hx, 32) * 3.0) - shrink / 2;
                        x0 = hx - w / 2;
                        z0 = hz - d / 2;
                        ok = this.buildable(x0, z0, x0 + w - 1, z0 + d - 1);
                    }

                    if (ok) {
                        break;
                    }
                }

                if (!ok) {
                    continue;
                }

                int x1 = x0 + w - 1;
                int z1 = z0 + d - 1;

                String front = facing(-Math.cos(r), -Math.sin(r));
                House h = new House()
                    .floors(ring < 2 && i % 3 == 0 ? 3 : 2)
                    .roof(roofs[i % roofs.length])
                    .front(front)
                    .plaster(plasters[(i * 3 + ring) % plasters.length])
                    .stone(i % 7 == 3)
                    .dormer(i % 2 == 0);
                String sign = signs[i % signs.length];
                if (sign != null) {
                    h.sign(String.format(sign, front.equals("north") ? 8 : (front.equals("east") ? 12 : (front.equals("south") ? 0 : 4))));
                }

                if (i % 4 == 1) {
                    // some ridges run front to back, for a gable facing the street
                    h.ridge(!h.ridgeX);
                }

                this.timberHouse(x0, z0, w, d, 0, h);
                this.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
                // life around the house: crates at a back corner, firewood along a side, now and then a cart
                int bx = front.equals("east") ? x0 - 2 : (front.equals("west") ? x1 + 1 : x0);
                int bz = front.equals("south") ? z0 - 2 : (front.equals("north") ? z1 + 1 : z0);
                this.crates(bx, 1, bz, i);
                if (i % 3 == 0) {
                    boolean alongX = front.equals("east") || front.equals("west");
                    int wx = alongX ? x0 + 2 : x1 + 2;
                    int wz = alongX ? z1 + 2 : z0 + 2;
                    if (this.ground(wx, wz) == 0 && !this.onStreet(wx, wz, 3.0)) {
                        this.woodpile(wx, 1, wz, alongX);
                    }
                }

                i++;
            }
        }
    }

    private void marketHall(int cx, int cz) {
        int x0 = cx - 9;
        int x1 = cx + 9;
        int z0 = cz - 13;
        int z1 = cz + 13;
        if (this.free(x0, z0, x1, z1, 1)) {
            this.claim(x0, z0, x1, z1);

            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    this.set(
                        x,
                        0,
                        z,
                        x != x0 && x != x1 && z != z0 && z != z1 ? ((x + z) % 2 == 0 ? "spruce_planks" : "stripped_spruce_wood[axis=y]") : "stone_bricks"
                    );
                }
            }

            int top = 7;

            for (int x = x0; x <= x1; x += 3) {
                for (int z : new int[]{z0, z1}) {
                    this.pillar(x, z, top);
                }
            }

            for (int z = z0; z <= z1; z += 4) {
                this.pillar(x0, z, top);
                this.pillar(x1, z, top);
            }

            for (int z = z0 - 1; z <= z1 + 1; z++) {
                this.set(x0, top + 1, z, log("spruce_log", 'z'));
                this.set(x1, top + 1, z, log("spruce_log", 'z'));
            }

            int span = x1 - x0 + 3;

            for (int i = 0; i <= span / 2; i++) {
                int y = top + 2 + i;
                int xa = x0 - 1 + i;
                int xb = x1 + 1 - i;
                String mat = i != 2 && i != 3 ? "spruce" : "red_nether_brick";

                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    if (xa == xb) {
                        this.set(xa, y, z, slab("spruce", false));
                    } else if (xa < xb) {
                        this.set(xa, y, z, stairs(mat, "east", false));
                        this.set(xb, y, z, stairs(mat, "west", false));
                    }
                }
            }

            String[] wools = new String[]{"red_wool", "yellow_wool", "lime_wool", "light_blue_wool"};

            for (int s = 0; s < 4; s++) {
                int sz = z0 + 3 + s * 6;
                int sx = s % 2 == 0 ? x0 + 3 : x1 - 3;

                for (int k = -1; k <= 1; k++) {
                    this.set(sx, 1, sz + k, "barrel[facing=up]");
                    this.set(sx, 2, sz + k, slab("spruce", false));
                    this.set(sx, 4, sz + k, wools[s]);
                    this.set(sx + (s % 2 == 0 ? 1 : -1), 4, sz + k, wools[s]);
                }

                this.set(sx, 2, sz, s % 2 == 0 ? "melon" : "pumpkin");
                this.set(sx + (s % 2 == 0 ? 1 : -1), 1, sz + 2, "hay_block");
            }

            for (int zx = z0 + 4; zx <= z1 - 4; zx += 6) {
                this.set(cx, top + 1, zx, "iron_chain[axis=y]");
                this.set(cx, top, zx, "lantern[hanging=true]");
            }
        }
    }

    private void pillar(int x, int z, int top) {
        this.set(x, 1, z, "stone_bricks");

        for (int y = 2; y <= top; y++) {
            this.set(x, y, z, log("dark_oak_log", 'y'));
        }
    }

    private void flags(int ax, int az, int bx, int bz, int y) {
        String[] colors = new String[]{"red_wool", "lime_wool", "white_wool", "yellow_wool", "light_blue_wool"};
        double len = Math.hypot(bx - ax, bz - az);
        int steps = (int)Math.ceil(len * 2.0);
        int last = Integer.MIN_VALUE;
        int k = 0;

        for (int i = 0; i <= steps; i++) {
            double t = (double)i / steps;
            int x = (int)Math.round(ax + (bx - ax) * t);
            int z = (int)Math.round(az + (bz - az) * t);
            int yy = (int)Math.round(y - 10.4 * t * (1.0 - t));
            int key = x * 4096 + z;
            if (key != last) {
                last = key;
                this.set(x, yy, z, "dark_oak_fence");
                if (k++ % 2 == 1 && t > 0.08 && t < 0.92) {
                    String c = colors[k / 2 % colors.length];
                    this.setIfEmpty(x, yy - 1, z, c);
                    if (k / 2 % 2 == 0) {
                        this.setIfEmpty(x, yy - 2, z, c);
                    }
                }
            }
        }
    }

    private void post(int x, int z, int h) {
        int g = Math.max(0, this.ground(x, z));
        this.set(x, g, z, "stone_bricks");

        for (int y = g + 1; y <= g + h; y++) {
            this.set(x, y, z, "dark_oak_fence");
        }

        this.set(x, g + h + 1, z, "lantern[hanging=false]");
    }

    private void bunting() {
        List<int[]> posts = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            double deg = i * 30 + 15;
            if (!(deg > 50.0) || !(deg < 130.0)) {
                double a = Math.toRadians(deg);
                int[] p = new int[]{(int)Math.round(Math.cos(a) * 32.0), (int)Math.round(6.0 + Math.sin(a) * 32.0)};
                this.post(p[0], p[1], 9);
                posts.add(p);
            }
        }

        for (int ix = 0; ix + 1 < posts.size(); ix++) {
            this.flags(posts.get(ix)[0], posts.get(ix)[1], posts.get(ix + 1)[0], posts.get(ix + 1)[1], 9);
        }

        this.post(-5, -34, 9);
        this.post(5, -34, 9);
        this.flags(-5, -34, 5, -34, 9);
        this.post(-5, -52, 9);
        this.post(5, -52, 9);
        this.flags(-5, -52, 5, -52, 9);
        this.flags(-5, -34, -5, -52, 9);
        this.flags(5, -34, 5, -52, 9);
    }

    private void lamps() {
        for (double[] s : STREETS) {
            double len = Math.hypot(s[2] - s[0], s[3] - s[1]);

            for (double t = 6.0; t < len; t += 12.0) {
                double f = t / len;
                double x = s[0] + (s[2] - s[0]) * f;
                double z = s[1] + (s[3] - s[1]) * f;
                double nx = -(s[3] - s[1]) / len;
                double nz = (s[2] - s[0]) / len;

                for (int side : new int[]{-1, 1}) {
                    int lx = (int)Math.round(x + nx * 5.0 * side);
                    int lz = (int)Math.round(z + nz * 5.0 * side);
                    int g = this.ground(lx, lz);
                    boolean nearSpawn = Math.abs(lx) < 9 && lz > 34 && lz < 66;
                    if (g != Integer.MIN_VALUE && !nearSpawn && !this.inPlaza(lx, lz) && this.free(lx, lz, lx, lz, 1) && !this.nearWall(lx, lz, 7.0)) {
                        // a lamp with its lantern hanging over the street
                        this.bracketLamp(lx, g + 1, lz, facing(-nx * side, -nz * side));
                    }
                }
            }
        }
    }

    private void pedestal(int x, int z) {
        this.set(x, 1, z, "polished_andesite");
        this.set(x, 0, z, "chiseled_stone_bricks");

        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            this.set(x + o[0], 1, z + o[1], stairs("polished_andesite", facing(-o[0], -o[1]), false));
        }
    }

    private void props() {
        for (int s : new int[]{-1, 1}) {
            for (int k = 0; k < 3; k++) {
                int bx = s * (24 + k);
                int bz = 20 + k * 2;
                this.set(bx, 1, bz, stairs("spruce", "south", false));
                this.set(bx + s, 1, bz, stairs("spruce", "south", false));
            }
        }

        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5 + 8.0);
            int x = (int)Math.round(Math.cos(a) * 37.0);
            int z = (int)Math.round(6.0 + Math.sin(a) * 37.0);
            if (!this.onStreet(x, z, 5.0) && this.ground(x, z) == 0 && this.free(x, z, x, z, 1)) {
                String[] kinds = new String[]{"barrel[facing=up]", "hay_block", "composter", "barrel[facing=up]"};
                this.set(x, 1, z, kinds[i % kinds.length]);
                if (i % 3 == 0) {
                    this.set(x + 1, 1, z, "flowering_azalea");
                }
            }
        }
    }

    private void trees() {
        for (int i = 0; i < 70; i++) {
            double a = this.n(i * 3.7, i * 1.3, 41) * Math.PI * 2.0 * 3.0;
            double r = 40.0 + this.n(i * 1.9, i * 5.1, 42) * (this.edge - 46.0);
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            int g = this.ground(x, z);
            if (g != Integer.MIN_VALUE
                && !this.inPlaza(x, z)
                && !this.onStreet(x, z, 5.0)
                && this.free(x - 3, z - 3, x + 3, z + 3, 1)
                && !this.nearWall(x, z, 10.0)
                && "grass_block".equals(this.get(x, g, z))) {
                if (g <= 8 && i % 4 != 0) {
                    this.niceTree(x, g + 1, z, i % 5 == 0 ? "birch_log" : "oak_log", i % 5 == 0 ? "birch" : "oak", 5 + i % 3, 3.0 + i % 3 * 0.5);
                } else {
                    this.conifer(x, g + 1, z, "spruce_log", "spruce", 7 + i % 5);
                }

                this.claim(x - 2, z - 2, x + 2, z + 2);
            }
        }

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int g = this.ground(x, z);
                if (g != Integer.MIN_VALUE && "grass_block".equals(this.get(x, g, z)) && this.empty(x, g + 1, z)) {
                    double v = this.n(x * 4.1, z * 4.1, 43);
                    if (v < 0.16) {
                        this.set(x, g + 1, z, "short_grass");
                    } else if (v > 0.93) {
                        String[] fl = new String[]{"poppy", "dandelion", "cornflower", "oxeye_daisy", "allium"};
                        this.set(x, g + 1, z, fl[Math.floorMod(x * 31 + z * 17, fl.length)]);
                    }
                }
            }
        }

        for (int ix = 0; ix < 26; ix++) {
            double a = ix * 0.83 + this.n(ix, 3.0, 44) * 2.0;
            double r = this.edge * (0.78 + this.n(ix, 7.0, 45) * 0.16);
            int x = (int)Math.round(Math.cos(a) * r);
            int zx = (int)Math.round(Math.sin(a) * r);
            int g = this.ground(x, zx);
            if (g != Integer.MIN_VALUE && !(Math.hypot(x - 62, zx - -62) < 30.0) && !this.nearWall(x, zx, 8.0) && this.free(x - 3, zx - 3, x + 3, zx + 3, 0)) {
                this.ellipsoid(x, g + 1, zx, 2.5 + ix % 3, 1.8 + ix % 2, 2.2 + ix % 2, ix % 3 == 0 ? "mossy_cobblestone" : "stone");
            }
        }
    }

    private void eventHill() {
        this.claim(32, -92, 92, -32);

        for (int x = 42; x <= 82; x++) {
            for (int z = -82; z <= -42; z++) {
                double d = Math.hypot(x - 62, z - -62);
                if (!(d > 20.0) && Math.abs(d - 15.5) < 1.4) {
                    this.set(x, 6, z, this.chance(x, z, 51, 0.5) ? "packed_mud" : "coarse_dirt");
                }
            }
        }

        for (int i = 0; i <= 16; i++) {
            double t = i / 16.0;
            double x = 50.0 + -2.0 * t;
            double zx = -48.0 + 0.0 * t;
            int y = (int)Math.round(6.0 * t);

            for (int w = -3; w <= 3; w++) {
                int px = (int)Math.round(x + w * 0.7);
                int pz = (int)Math.round(zx - w * 0.7);

                for (int k = -6; k < y; k++) {
                    this.setIfEmpty(px, k, pz, "dirt");
                }

                this.set(px, y, pz, Math.abs(w) == 3 ? "cobblestone" : "packed_mud");

                for (int k = y + 1; k <= y + 6; k++) {
                    String b = this.get(px, k, pz);
                    if (b != null && !b.contains("leaves")) {
                        this.set(px, k, pz, null);
                    }
                }
            }
        }

        this.sumoArena(64, -64);
        this.trophySign(46, -58);
    }

    private void sumoArena(int ax, int az) {
        int base = 6;
        int r = 12;

        for (int x = ax - r - 2; x <= ax + r + 2; x++) {
            for (int z = az - r - 2; z <= az + r + 2; z++) {
                double d = Math.hypot(x - ax, z - az);
                if (!(d > r + 1.5)) {
                    this.set(x, base, z, d <= 6.5 ? (d > 5.6 ? "white_concrete" : "smooth_sandstone") : (d <= r - 1.5 ? "spruce_planks" : "stone_bricks"));
                    double ang = Math.toDegrees(Math.atan2(z - az, x - ax));
                    boolean entrance = ang > 120.0 && ang < 150.0;
                    if (d > r - 0.6 && d <= r + 0.6 && !entrance) {
                        for (int y = base + 1; y <= base + 6; y++) {
                            boolean window = y >= base + 3 && y <= base + 4 && Math.floorMod((int)Math.round(ang), 12) < 7;
                            this.set(x, y, z, window ? "spruce_fence" : (y == base + 6 ? log("stripped_dark_oak_log", 'y') : "stripped_spruce_wood[axis=y]"));
                        }
                    }

                    if (d > r - 3 && d <= r + 1.5) {
                        int y = base + 7 + (int)Math.round((r + 1.5 - d) * 0.8);
                        this.set(x, y, z, d > r ? "red_wool" : "red_terracotta");
                    }
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            int px = (int)Math.round(ax + Math.cos(a) * (r + 1));
            int pz = (int)Math.round(az + Math.sin(a) * (r + 1));

            for (int y = base + 1; y <= base + 7; y++) {
                this.set(px, y, pz, log("dark_oak_log", 'y'));
            }
        }

        int fx = ax + r + 1;

        for (int y = base + 7; y <= base + 15; y++) {
            this.set(fx, y, az, "dark_oak_fence");
        }

        for (int y = base + 12; y <= base + 14; y++) {
            for (int k = 1; k <= 4; k++) {
                this.set(fx + k, y, az, "red_wool");
            }
        }
    }

    private void trophySign(int x, int z) {
        int g = 6;

        for (int y = g + 1; y <= g + 9; y++) {
            this.set(x - 9, y, z, log("dark_oak_log", 'y'));
            this.set(x + 9, y, z, log("dark_oak_log", 'y'));
        }

        int bh = this.board("EVENT", x, g + 6, z);
        this.sprite(TROPHY, x, g + 6 + bh + 1, z);
    }

    private void wheat(int cx, int cz) {
        int x0 = cx - 8;
        int z0 = cz - 6;
        int x1 = cx + 8;
        int z1 = cz + 6;
        if (this.free(x0, z0, x1, z1, 2)) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (this.ground(x, z) != Integer.MIN_VALUE) {
                        int g = this.ground(x, z);
                        boolean water = (z - z0) % 5 == 2;
                        boolean border = x == x0 || x == x1 || z == z0 || z == z1;
                        this.set(x, g, z, border ? log("oak_log", 'x') : (water ? "water" : "farmland[moisture=7]"));
                        if (!border && !water) {
                            this.set(x, g + 1, z, "wheat[age=7]");
                        }
                    }
                }
            }

            this.claim(x0, z0, x1, z1);
        }
    }
}
