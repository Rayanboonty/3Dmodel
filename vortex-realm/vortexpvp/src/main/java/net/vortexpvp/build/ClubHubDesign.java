package net.vortexpvp.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ClubHubDesign extends Voxels {
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
        Map.entry('N', new String[]{"1.1", "111", "111", "1.1", "1.1"})
    );

    public ClubHubDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, Math.max(100, Math.min(140, radius)), -46, 64, seed);
        this.seed = seed;
        this.edge = this.R - 6;
        this.terrain();
        this.plaza();
        this.streets();
        this.spawnPad();
        this.pavilion(-13, 18, ANVIL, "KITS", "anvil");
        this.pavilion(13, 18, CHESTPLATE, "SHOP", "chest");
        this.grandOak(0, -12);
        this.marketHall(47, -13);
        this.eventHill();
        this.wheat(24, -78);
        this.houses();
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

    private void grandOak(int x, int z) {
        this.claim(x - 8, z - 8, x + 8, z + 8);

        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 5.2) {
                    this.set(x + dx, 0, z + dz, d > 4.2 ? "stone_bricks" : "grass_block");
                    if (d > 4.2) {
                        this.set(x + dx, 1, z + dz, stairs("stone_brick", facing(-dx, -dz), true));
                    } else if (this.chance(x + dx, z + dz, 21, 0.25)) {
                        this.set(x + dx, 1, z + dz, this.chance(x + dx, z + dz, 22, 0.5) ? "poppy" : "short_grass");
                    }
                }
            }
        }

        for (int y = 1; y <= 14; y++) {
            for (int[] t : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
                this.set(x + t[0], y, z + t[1], log("oak_log", 'y'));
            }
        }

        for (int[] r : new int[][]{{-1, 0}, {2, 1}, {0, 2}, {1, -1}}) {
            this.set(x + r[0], 1, z + r[1], log("oak_log", 'y'));
        }

        int[][] arms = new int[][]{{-5, 10, -3}, {6, 11, 2}, {-3, 12, 5}, {4, 12, -5}, {0, 15, 0}};

        for (int[] a : arms) {
            this.line(x + 0.5, 9.0, z + 0.5, x + a[0], a[1], z + a[2], log("oak_log", 'y'));
        }

        for (int[] a : arms) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dzx = -5; dzx <= 5; dzx++) {
                        double d = Math.sqrt(dx * dx + dy * dy * 2.2 + dzx * dzx);
                        if (d <= 4.6 - this.n(x + a[0] + dx, z + a[2] + dzx + dy * 9, 23) * 1.4) {
                            this.setIfEmpty(x + a[0] + dx, a[1] + 1 + dy, z + a[2] + dzx, leaves("oak"));
                        }
                    }
                }
            }
        }

        for (int[] a : arms) {
            this.setIfEmpty(x + a[0], a[1] - 2, z + a[2], "iron_chain[axis=y]");
            this.setIfEmpty(x + a[0], a[1] - 3, z + a[2], "lantern[hanging=true]");
        }
    }

    private void houses() {
        this.house(-31, -24, 13, 12, 3, ClubHubDesign.Roof.TEAL, true, "south");
        this.house(18, -25, 13, 11, 2, ClubHubDesign.Roof.RED, false, "south");
        ClubHubDesign.Roof[] roofs = ClubHubDesign.Roof.values();
        int i = 0;

        for (int ring = 0; ring < 2; ring++) {
            double rad = ring == 0 ? 50.0 : 72.0;

            for (double a = 0.0; a < 360.0; a += ring == 0 ? 21.0 : 17.0) {
                double r = Math.toRadians(a + ring * 7);
                int hx = (int)Math.round(Math.cos(r) * rad);
                int hz = (int)Math.round(6.0 + Math.sin(r) * rad);
                int w = 9 + (int)Math.round(this.n(hx, hz, 31) * 5.0);
                int d = 9 + (int)Math.round(this.n(hz, hx, 32) * 4.0);
                int x0 = hx - w / 2;
                int z0 = hz - d / 2;
                int x1 = x0 + w - 1;
                int z1 = z0 + d - 1;
                if (this.buildable(x0, z0, x1, z1)) {
                    double dx = -Math.cos(r);
                    double dz = -Math.sin(r);
                    String front = facing(dx, dz);
                    boolean ridgeX = front.equals("north") || front.equals("south");
                    int floors = ring == 0 ? 2 + (i % 3 == 0 ? 1 : 0) : 2;
                    this.house(x0, z0, w, d, floors, roofs[i++ % roofs.length], ridgeX, front);
                }
            }
        }
    }

    private boolean buildable(int x0, int z0, int x1, int z1) {
        if (!this.free(x0, z0, x1, z1, 3)) {
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

    private void house(int x0, int z0, int w, int d, int floors, ClubHubDesign.Roof roof, boolean ridgeX, String front) {
        int x1 = x0 + w - 1;
        int z1 = z0 + d - 1;
        this.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
        int wallTop = floors * 4;

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                this.set(x, 0, z, "cobblestone");

                for (int y = 1; y <= wallTop + 12; y++) {
                    this.set(x, y, z, null);
                }
            }
        }

        for (int y = 1; y <= wallTop; y++) {
            int floor = (y - 1) / 4;
            int iy = (y - 1) % 4;

            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edgeX = x == x0 || x == x1;
                    boolean edgeZ = z == z0 || z == z1;
                    if (!edgeX && !edgeZ) {
                        if (iy == 0 && floor > 0) {
                            this.set(x, y, z, "spruce_planks");
                        }
                    } else {
                        String b;
                        if (edgeX && edgeZ) {
                            b = floor == 0 ? "stone_bricks" : log("dark_oak_log", 'y');
                        } else if (floor == 0) {
                            b = this.cobble(x * 3, z * 3 + y);
                            if (b.contains("andesite")) {
                                b = "stone_bricks";
                            }
                        } else if (iy == 0) {
                            b = log("dark_oak_log", (char)(edgeX ? 'z' : 'x'));
                        } else {
                            b = "calcite";
                        }

                        this.set(x, y, z, b);
                    }
                }
            }
        }

        for (int f = 0; f < floors; f++) {
            int wy = f * 4 + 2;

            for (int x = x0 + 2; x <= x1 - 2; x += 3) {
                this.window(x, wy, z0, "north");
                this.window(x, wy, z1, "south");
            }

            for (int zx = z0 + 2; zx <= z1 - 2; zx += 3) {
                this.window(x0, wy, zx, "west");
                this.window(x1, wy, zx, "east");
            }
        }

        int doorX = (x0 + x1) / 2;
        int doorZ = (z0 + z1) / 2;

        int[] door = switch (front) {
            case "north" -> new int[]{doorX, z0};
            case "south" -> new int[]{doorX, z1};
            case "west" -> new int[]{x0, doorZ};
            default -> new int[]{x1, doorZ};
        };
        this.set(door[0], 1, door[1], "spruce_door[facing=" + front + ",half=lower,hinge=left,open=false]");
        this.set(door[0], 2, door[1], "spruce_door[facing=" + front + ",half=upper,hinge=left,open=false]");
        this.set(door[0], 3, door[1], "chiseled_stone_bricks");
        int ox = front.equals("east") ? 1 : (front.equals("west") ? -1 : 0);
        int oz = front.equals("south") ? 1 : (front.equals("north") ? -1 : 0);
        this.set(door[0] + ox, 3, door[1] + oz, "lantern[hanging=false]");
        this.set(door[0] + ox, 0, door[1] + oz, "cobblestone");
        this.set(door[0] + ox, 1, door[1] + oz, null);
        this.set(door[0] + ox, 2, door[1] + oz, null);
        if (floors > 1) {
            for (int x = x0; x <= x1; x += 2) {
                this.set(x, 4, z0 - 1, stairs("spruce", "south", true));
                this.set(x, 4, z1 + 1, stairs("spruce", "north", true));
            }
        }

        this.roof(x0, z0, x1, z1, wallTop, roof, ridgeX);
    }

    private void window(int x, int y, int z, String out) {
        this.set(x, y, z, "blue_stained_glass_pane");
        this.set(x, y + 1, z, "blue_stained_glass_pane");
        int ox = out.equals("east") ? 1 : (out.equals("west") ? -1 : 0);
        int oz = out.equals("south") ? 1 : (out.equals("north") ? -1 : 0);
        this.setIfEmpty(x + ox, y - 1, z + oz, "spruce_trapdoor[facing=" + out + ",half=top,open=false]");
    }

    private void roof(int x0, int z0, int x1, int z1, int top, ClubHubDesign.Roof roof, boolean ridgeX) {
        int span = ridgeX ? z1 - z0 + 3 : x1 - x0 + 3;
        int half = span / 2;

        for (int i = 0; i <= half; i++) {
            int y = top + 1 + i;
            if (ridgeX) {
                int za = z0 - 1 + i;
                int zb = z1 + 1 - i;

                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    if (za == zb) {
                        this.set(x, y, za, slab(roof.stair, false));
                    } else if (za < zb) {
                        this.set(x, y, za, stairs(roof.stair, "south", false));
                        this.set(x, y, zb, stairs(roof.stair, "north", false));

                        for (int z = za + 1; z < zb; z++) {
                            if (x == x0 || x == x1) {
                                this.set(x, y, z, x == x0 && (z - za) % 3 == 0 ? log("dark_oak_log", 'y') : "calcite");
                            }
                        }
                    }
                }
            } else {
                int xa = x0 - 1 + i;
                int xb = x1 + 1 - i;

                for (int zx = z0 - 1; zx <= z1 + 1; zx++) {
                    if (xa == xb) {
                        this.set(xa, y, zx, slab(roof.stair, false));
                    } else if (xa < xb) {
                        this.set(xa, y, zx, stairs(roof.stair, "east", false));
                        this.set(xb, y, zx, stairs(roof.stair, "west", false));

                        for (int xx = xa + 1; xx < xb; xx++) {
                            if (zx == z0 || zx == z1) {
                                this.set(xx, y, zx, zx == z0 && (xx - xa) % 3 == 0 ? log("dark_oak_log", 'y') : "calcite");
                            }
                        }
                    }
                }
            }
        }

        int ridge = top + 1 + half;
        int chX = ridgeX ? x0 + 2 : (x0 + x1) / 2 + 1;
        int chZ = ridgeX ? (z0 + z1) / 2 + 1 : z0 + 2;

        for (int y = top - 2; y <= ridge + 2; y++) {
            this.set(chX, y, chZ, "cobblestone");
        }

        this.set(chX, ridge + 3, chZ, "campfire[lit=true]");
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
                    if (g != Integer.MIN_VALUE && !nearSpawn && !this.inPlaza(lx, lz) && this.free(lx, lz, lx, lz, 0)) {
                        this.lampPost(lx, g + 1, lz, "stone_bricks", "dark_oak_fence", "lantern[hanging=false]", 4);
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
                && "grass_block".equals(this.get(x, g, z))) {
                if (g <= 8 && i % 4 != 0) {
                    this.tree(x, g + 1, z, "oak_log", "oak", 5 + i % 3, 3.2 + i % 3 * 0.6);
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
            if (g != Integer.MIN_VALUE && !(Math.hypot(x - 62, zx - -62) < 30.0)) {
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

    private static enum Roof {
        TEAL("prismarine_brick", "prismarine_bricks"),
        DARK_TEAL("dark_prismarine", "dark_prismarine"),
        RED("brick", "bricks"),
        SLATE("deepslate_tile", "deepslate_tiles"),
        CLAY("granite", "polished_granite");

        final String stair;
        final String block;

        private Roof(String stair, String block) {
            this.stair = stair;
            this.block = block;
        }
    }
}
