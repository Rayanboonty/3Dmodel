package net.vortexrealm.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public abstract class Voxels {
    protected final int cx;
    protected final int floorY;
    protected final int cz;
    protected final int R;
    protected final int yLo;
    protected final int yHi;
    protected final Random rnd;
    private final int size;
    private final int height;
    private byte[] small;
    private short[] data;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> index = new HashMap<>();
    protected final List<double[]> npcs = new ArrayList<>();
    protected double[] spawn = new double[]{0.5, 1.0, 10.5, 180.0};
    protected boolean soft;

    protected Voxels(int cx, int floorY, int cz, int radius, int yLo, int yHi, long seed) {
        this.cx = cx;
        this.floorY = floorY;
        this.cz = cz;
        this.R = radius;
        this.yLo = yLo;
        this.yHi = yHi;
        this.rnd = new Random(seed);
        this.size = radius * 2 + 1;
        this.height = yHi - yLo + 1;
        this.small = new byte[this.size * this.size * this.height];
        this.palette.add(null);
        this.index.put("", (short)0);
    }

    protected final void finish() {
        this.connect();
    }

    private int idx(int x, int y, int z) {
        return x >= -this.R && x <= this.R && z >= -this.R && z <= this.R && y >= this.yLo && y <= this.yHi
            ? ((x + this.R) * this.size + z + this.R) * this.height + (y - this.yLo)
            : -1;
    }

    protected void set(int x, int y, int z, String block) {
        int i = this.idx(x, y, z);
        if (i >= 0) {
            if (block == null) {
                if (this.small != null) {
                    this.small[i] = 0;
                } else {
                    this.data[i] = 0;
                }
            } else if (!this.soft || this.at(i) == 0) {
                Short s = this.index.get(block);
                if (s == null) {
                    s = (short)this.palette.size();
                    this.palette.add(block);
                    this.index.put(block, s);
                    if (this.small != null && s > 255) {
                        this.data = new short[this.small.length];

                        for (int k = 0; k < this.small.length; k++) {
                            this.data[k] = (short)(this.small[k] & 255);
                        }

                        this.small = null;
                    }
                }

                if (this.small != null) {
                    this.small[i] = (byte)s.shortValue();
                } else {
                    this.data[i] = s;
                }
            }
        }
    }

    private int at(int i) {
        return this.small != null ? this.small[i] & 0xFF : this.data[i];
    }

    protected String get(int x, int y, int z) {
        int i = this.idx(x, y, z);
        return i < 0 ? null : this.palette.get(this.at(i));
    }

    protected boolean empty(int x, int y, int z) {
        String b = this.get(x, y, z);
        return b == null || b.equals("air");
    }

    protected void setIfEmpty(int x, int y, int z, String block) {
        if (this.empty(x, y, z)) {
            this.set(x, y, z, block);
        }
    }

    protected void box(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    this.set(x, y, z, b);
                }
            }
        }
    }

    protected void walls(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                this.set(x, y, z0, b);
                this.set(x, y, z1, b);
            }

            for (int z = z0; z <= z1; z++) {
                this.set(x0, y, z, b);
                this.set(x1, y, z, b);
            }
        }
    }

    protected void disc(double ox, double oz, int y, double r, String b) {
        int n = (int)Math.ceil(r);

        for (int x = -n; x <= n; x++) {
            for (int z = -n; z <= n; z++) {
                if (x * x + z * z <= r * r + r * 0.6) {
                    this.set((int)Math.round(ox) + x, y, (int)Math.round(oz) + z, b);
                }
            }
        }
    }

    protected void ring(double ox, double oz, int y, double rIn, double rOut, String b) {
        int n = (int)Math.ceil(rOut);

        for (int x = -n; x <= n; x++) {
            for (int z = -n; z <= n; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= rIn - 0.01 && d <= rOut + 0.3) {
                    this.set((int)Math.round(ox) + x, y, (int)Math.round(oz) + z, b);
                }
            }
        }
    }

    protected void cylinder(double ox, double oz, int y0, int y1, double r, String b, boolean hollow) {
        for (int y = y0; y <= y1; y++) {
            if (hollow) {
                this.ring(ox, oz, y, r - 1.0, r, b);
            } else {
                this.disc(ox, oz, y, r, b);
            }
        }
    }

    protected void cone(double ox, double oz, int y0, int y1, double r, String b) {
        for (int y = y0; y <= y1; y++) {
            double f = 1.0 - (double)(y - y0) / Math.max(1, y1 - y0 + 1);
            this.disc(ox, oz, y, Math.max(0.3, r * f), b);
        }
    }

    protected void sphere(double ox, double oy, double oz, double r, String b) {
        this.ellipsoid(ox, oy, oz, r, r, r, b);
    }

    protected void ellipsoid(double ox, double oy, double oz, double rx, double ry, double rz, String b) {
        for (int x = (int)Math.floor(ox - rx); x <= Math.ceil(ox + rx); x++) {
            for (int y = (int)Math.floor(oy - ry); y <= Math.ceil(oy + ry); y++) {
                for (int z = (int)Math.floor(oz - rz); z <= Math.ceil(oz + rz); z++) {
                    double dx = (x - ox) / rx;
                    double dy = (y - oy) / ry;
                    double dz = (z - oz) / rz;
                    if (dx * dx + dy * dy + dz * dz <= 1.0) {
                        this.set(x, y, z, b);
                    }
                }
            }
        }
    }

    protected void line(double x0, double y0, double z0, double x1, double y1, double z1, String b) {
        double len = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        int n = (int)Math.ceil(len * 2.0) + 1;

        for (int i = 0; i <= n; i++) {
            double t = (double)i / n;
            this.set((int)Math.round(x0 + (x1 - x0) * t), (int)Math.round(y0 + (y1 - y0) * t), (int)Math.round(z0 + (z1 - z0) * t), b);
        }
    }

    protected static String stairs(String mat, String facing, boolean top) {
        return mat + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    protected static String slab(String mat, boolean top) {
        return mat + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    protected static String leaves(String type) {
        return type + "_leaves[persistent=true]";
    }

    protected static String log(String type, char axis) {
        return type + "[axis=" + axis + "]";
    }

    protected static String facing(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0.0 ? "east" : "west";
        } else {
            return dz > 0.0 ? "south" : "north";
        }
    }

    protected static String opposite(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    protected void tree(int x, int y, int z, String logType, String leafType, int trunk, double crown) {
        for (int i = 0; i < trunk; i++) {
            this.set(x, y + i, z, log(logType, 'y'));
        }

        double top = y + trunk;
        int n = (int)Math.ceil(crown);

        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (d <= crown - this.rnd.nextDouble() * 0.6) {
                        this.setIfEmpty(x + dx, (int)top + dy, z + dz, leaves(leafType));
                    }
                }
            }
        }
    }

    protected void conifer(int x, int y, int z, String logType, String leafType, int h) {
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, log(logType, 'y'));
        }

        for (int i = 2; i <= h + 1; i++) {
            double r = (h + 2 - i) * 0.45;
            if (i % 2 == 1) {
                r *= 0.75;
            }

            this.disc(x, z, y + i, Math.max(0.6, r), leaves(leafType));
            this.set(x, y + i, z, i <= h ? log(logType, 'y') : leaves(leafType));
        }
    }

    protected void lampPost(int x, int y, int z, String base, String post, String light, int h) {
        this.set(x, y, z, base);

        for (int i = 1; i < h; i++) {
            this.set(x, y + i, z, post);
        }

        this.set(x, y + h, z, light);
    }

    protected static double noise(long seed, double x, double z) {
        double s = 0.0;
        double amp = 1.0;
        double f = 0.06;

        for (int o = 0; o < 3; o++) {
            s += amp * smooth(seed + o * 101, x * f, z * f);
            amp *= 0.5;
            f *= 2.1;
        }

        return s / 1.75;
    }

    private static double smooth(long seed, double x, double z) {
        int x0 = (int)Math.floor(x);
        int z0 = (int)Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double a = hash(seed, x0, z0);
        double b = hash(seed, x0 + 1, z0);
        double c = hash(seed, x0, z0 + 1);
        double d = hash(seed, x0 + 1, z0 + 1);
        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uz = fz * fz * (3.0 - 2.0 * fz);
        return a + (b - a) * ux + (c - a) * uz + (a - b - c + d) * ux * uz;
    }

    private static double hash(long seed, int x, int z) {
        long h = seed * -7046029254386353131L + x * -4417276706812531889L + z * 1609587929392839161L;
        h ^= h >>> 29;
        h *= -4658895280553007687L;
        h ^= h >>> 32;
        return (h >>> 11 & 1048575L) / 1048575.0;
    }

    protected void island(double ox, int y, double oz, double r, String top, String under, long seed) {
        int n = (int)Math.ceil(r) + 2;

        for (int x = -n; x <= n; x++) {
            for (int z = -n; z <= n; z++) {
                double d = Math.sqrt(x * x + z * z);
                double edge = r * (0.88 + 0.24 * noise(seed, ox + x, oz + z));
                if (!(d > edge)) {
                    int wx = (int)Math.round(ox) + x;
                    int wz = (int)Math.round(oz) + z;
                    this.set(wx, y, wz, top);
                    double depth = (1.0 - d / edge) * r * 0.75 + 1.0 + noise(seed + 7L, wx, wz) * 3.0;

                    for (int k = 1; k <= depth; k++) {
                        this.set(wx, y - k, wz, k <= 2 ? "dirt" : under);
                    }
                }
            }
        }
    }

    private static String base(String b) {
        int i = b.indexOf(91);
        return i < 0 ? b : b.substring(0, i);
    }

    private static boolean isFence(String b) {
        return b != null && base(b).endsWith("_fence");
    }

    private static boolean isWall(String b) {
        return b != null
            && base(b).endsWith("_wall")
            && !base(b).contains("sign")
            && !base(b).contains("banner")
            && !base(b).contains("torch")
            && !base(b).contains("head")
            && !base(b).contains("skull")
            && !base(b).contains("fan");
    }

    private static boolean isPane(String b) {
        return b != null && (base(b).endsWith("_pane") || base(b).equals("iron_bars"));
    }

    private static boolean solid(String b) {
        if (b != null && !b.equals("air")) {
            String n = base(b);
            if (!n.equals("sea_lantern") && !n.equals("jack_o_lantern") && !n.equals("bedrock")) {
                for (String no : new String[]{
                    "slab",
                    "stairs",
                    "fence",
                    "_wall",
                    "pane",
                    "bars",
                    "lantern",
                    "torch",
                    "carpet",
                    "door",
                    "button",
                    "sign",
                    "banner",
                    "leaves",
                    "chain",
                    "rod",
                    "flower",
                    "tulip",
                    "poppy",
                    "dandelion",
                    "orchid",
                    "allium",
                    "bluet",
                    "daisy",
                    "cornflower",
                    "lily",
                    "rose",
                    "bush",
                    "fern",
                    "grass",
                    "vine",
                    "sapling",
                    "candle",
                    "campfire",
                    "water",
                    "lava",
                    "pot",
                    "head",
                    "skull",
                    "cake",
                    "anvil",
                    "bell",
                    "bed",
                    "chest",
                    "barrel",
                    "scaffolding",
                    "pressure",
                    "rail",
                    "ladder",
                    "cluster",
                    "amethyst_bud",
                    "glow_lichen",
                    "petals",
                    "moss_carpet",
                    "snow[",
                    "cobweb"
                }) {
                    if (n.contains(no)) {
                        return n.endsWith("_block") || n.equals("moss_block") || n.equals("snow_block") || n.endsWith("planks") && !n.contains("slab");
                    }
                }

                return true;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private void connect() {
        int[][] dirs = new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        String[] names = new String[]{"north", "east", "south", "west"};

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                for (int y = this.yLo; y <= this.yHi; y++) {
                    String b = this.get(x, y, z);
                    if (b != null && b.indexOf(91) < 0) {
                        boolean fence = isFence(b);
                        boolean wall = !fence && isWall(b);
                        boolean pane = !fence && !wall && isPane(b);
                        if (fence || wall || pane) {
                            StringBuilder sb = new StringBuilder(b).append('[');
                            boolean[] con = new boolean[4];

                            for (int d = 0; d < 4; d++) {
                                String n = this.get(x + dirs[d][0], y, z + dirs[d][1]);
                                boolean c;
                                if (fence) {
                                    c = isFence(n) || n != null && base(n).endsWith("fence_gate") || solid(n);
                                } else if (wall) {
                                    c = isWall(n) || solid(n) || n != null && base(n).endsWith("fence_gate");
                                } else {
                                    c = isPane(n) || solid(n) || isWall(n);
                                }

                                con[d] = c;
                            }

                            for (int d = 0; d < 4; d++) {
                                if (d > 0) {
                                    sb.append(',');
                                }

                                sb.append(names[d]).append('=');
                                if (wall) {
                                    sb.append(con[d] ? "low" : "none");
                                } else {
                                    sb.append(con[d]);
                                }
                            }

                            if (wall) {
                                boolean straight = con[0] && con[2] && !con[1] && !con[3] || con[1] && con[3] && !con[0] && !con[2];
                                String above = this.get(x, y + 1, z);
                                boolean up = !straight || above != null && !above.equals("air");
                                sb.append(",up=").append(up);
                            }

                            sb.append(']');
                            this.set(x, y, z, sb.toString());
                        }
                    }
                }
            }
        }
    }

    public int minX() {
        return this.cx - this.R;
    }

    public int maxX() {
        return this.cx + this.R;
    }

    public int minZ() {
        return this.cz - this.R;
    }

    public int maxZ() {
        return this.cz + this.R;
    }

    public int floorYAt() {
        return this.floorY;
    }

    public int bottomY() {
        return this.floorY + this.yLo;
    }

    public int topY() {
        return this.floorY + this.yHi;
    }

    public void column(int wx, int wz, Voxels.Sink sink) {
        int x = wx - this.cx;
        int z = wz - this.cz;
        if (x >= -this.R && x <= this.R && z >= -this.R && z <= this.R) {
            int base = ((x + this.R) * this.size + z + this.R) * this.height;

            for (int y = 0; y < this.height; y++) {
                int s = this.at(base + y);
                if (s != 0) {
                    String b = this.palette.get(s);
                    sink.set(wx, this.floorY + this.yLo + y, wz, "minecraft:" + b);
                }
            }
        }
    }

    public List<double[]> npcSpots() {
        List<double[]> out = new ArrayList<>();

        for (double[] n : this.npcs) {
            out.add(new double[]{this.cx + n[0], this.floorY + n[1], this.cz + n[2], n[3]});
        }

        return out;
    }

    public double[] spawn() {
        return new double[]{this.cx + this.spawn[0], this.floorY + this.spawn[1], this.cz + this.spawn[2], this.spawn[3]};
    }

    /** Named spots for things placed after the build (holograms, decorations): key -> {x, y, z, yaw}, relative. */
    protected final java.util.Map<String, double[]> features = new java.util.LinkedHashMap<>();

    /** Feature spots in world coordinates. */
    public java.util.Map<String, double[]> features() {
        java.util.Map<String, double[]> out = new java.util.LinkedHashMap<>();

        for (java.util.Map.Entry<String, double[]> e : this.features.entrySet()) {
            double[] f = e.getValue();
            out.put(e.getKey(), new double[]{this.cx + f[0], this.floorY + f[1], this.cz + f[2], f[3]});
        }

        return out;
    }

    /** A spot facing toward (tx, tz). */
    protected void feature(String key, double x, double y, double z, double tx, double tz) {
        float yaw = (float)Math.toDegrees(Math.atan2(-(tx - x), tz - z));
        this.features.put(key, new double[]{x + 0.5, y, z + 0.5, yaw});
    }

    protected void npc(double x, double y, double z) {
        float yaw = (float)Math.toDegrees(Math.atan2(x, -z));
        this.npcs.add(new double[]{x + 0.5, y, z + 0.5, yaw});
    }

    public abstract String name();

    public interface Sink {
        void set(int var1, int var2, int var3, String var4);
    }
}
