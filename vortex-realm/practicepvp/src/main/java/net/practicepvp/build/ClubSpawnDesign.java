package net.practicepvp.build;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * "Club Spawn": the MCPVP.CLUB-style spawn map (by Xter/SFFredd) the server owner uploaded, converted from Bedrock to
 * Java block by block (stairs corners, slabs, doors, trapdoors, signs, walls, leaves, water...). Stored in the jar
 * as lobbies/club_spawn.vrmap: a palette and run-length coded columns. Its ground (y 70 in the original world) sits on
 * the build height the admin picks.
 */
public final class ClubSpawnDesign extends Voxels {
    /** Ground level of the plaza in the original map. */
    private static final int GROUND = 70;
    private static final int[] DIMS = dims();

    /** 3.13.0: where the 1.8 Legacy Hall stands (relative): at the end of the dirt path south-west of the plaza. */
    public static final int HALL_X = 4;
    public static final int HALL_Y = -6;
    public static final int HALL_Z = 154;

    public ClubSpawnDesign(int cx, int floorY, int cz, long seed) {
        this(cx, floorY, cz, seed, true);
    }

    public ClubSpawnDesign(int cx, int floorY, int cz, long seed, boolean withHall) {
        super(cx, floorY, cz, Math.max(DIMS[0], DIMS[2]) / 2, DIMS[4] - GROUND, DIMS[4] + DIMS[1] - 1 - GROUND, seed);
        this.load();
        if (withHall) {
            this.legacyHall();
        }

        this.finish();
        // the spawn: the middle of the main plaza, on top of whatever is there
        int sx = 197 - (DIMS[3] + DIMS[0] / 2);
        int sz = 202 - (DIMS[5] + DIMS[2] / 2);
        this.spawn = new double[]{sx + 0.5, this.top(sx, sz) + 1, sz + 0.5, 180.0};
        int[][] ring = {{-7, -4}, {-7, 4}, {7, -4}, {7, 4}, {-4, -8}, {4, -8}, {-4, 8}, {4, 8}};
        for (int[] r : ring) {
            this.npc(sx + r[0], this.top(sx + r[0], sz + r[1]) + 1, sz + r[1]);
        }
    }

    /** Trees, plants and anything loose: not ground. */
    private static boolean loose(String b) {
        if (b == null) {
            return true;
        }

        String n = b.indexOf('[') < 0 ? b : b.substring(0, b.indexOf('['));
        return n.equals("air") || n.endsWith("_leaves") || n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_sapling") || n.endsWith("_flower")
            || n.endsWith("grass") && !n.equals("grass_block") || n.endsWith("fern") || n.endsWith("_tulip") || n.endsWith("_petals") || n.endsWith("bush")
            || n.equals("vine") || n.equals("leaf_litter") || n.equals("poppy") || n.equals("dandelion") || n.equals("azure_bluet") || n.equals("oxeye_daisy")
            || n.equals("cornflower") || n.equals("allium") || n.equals("lily_of_the_valley") || n.equals("blue_orchid") || n.equals("peony") || n.equals("lilac")
            || n.equals("rose_bush") || n.equals("sunflower") || n.equals("wheat") || n.equals("snow") || n.equals("moss_carpet") || n.equals("brown_mushroom")
            || n.equals("red_mushroom") || n.equals("sweet_berry_bush") || n.equals("wildflowers") || n.equals("pink_petals") || n.equals("cactus") || n.equals("sugar_cane")
            || n.equals("bamboo") || n.equals("hanging_roots") || n.equals("glow_lichen") || n.endsWith("_carpet");
    }

    private static boolean tree(String b) {
        if (b == null) {
            return false;
        }

        String n = b.indexOf('[') < 0 ? b : b.substring(0, b.indexOf('['));
        return n.endsWith("_leaves") || n.endsWith("_log") || n.endsWith("_wood") || n.equals("vine") || n.equals("bee_nest");
    }

    private static long key(int x, int y, int z) {
        return (long)(x + 1024) << 42 | (long)(y + 1024) << 21 | (long)(z + 1024);
    }

    /** Relative y of the ground (not trees or plants) of a column. */
    private int ground(int x, int z) {
        for (int y = this.yHi; y >= this.yLo; y--) {
            String b = this.get(x, y, z);
            if (b != null && !loose(b)) {
                return y;
            }
        }

        return this.yLo;
    }

    /**
     * Stamps the 1.8 Legacy Hall in at the end of the path: trees on its ground are taken away whole (no floating
     * leaves), the hall's plinth goes down to the terrain and its gardens follow the ground around it.
     */
    private void legacyHall() {
        int x0 = HALL_X - 27;
        int x1 = HALL_X + 27;
        int z0 = HALL_Z - 31;
        int z1 = Math.min(this.R, HALL_Z + 25);
        java.util.ArrayDeque<int[]> trees = new java.util.ArrayDeque<>();
        int[][] ground = new int[x1 - x0 + 1][z1 - z0 + 1];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = this.ground(x, z);
                ground[x - x0][z - z0] = g;
                for (int y = g + 1; y <= this.yHi; y++) {
                    String b = this.get(x, y, z);
                    if (b != null && !b.equals("air")) {
                        if (tree(b)) {
                            trees.add(new int[]{x, y, z});
                        }

                        this.set(x, y, z, "air");
                    }
                }
            }
        }

        // whole trees go: leaves and logs joined to what was cleared, a little way around the hall
        int reach = 14;
        while (!trees.isEmpty()) {
            int[] t = trees.poll();
            // the builders' trees touch at edges and corners too
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = t[0] + dx;
                        int y = t[1] + dy;
                        int z = t[2] + dz;
                        if (x < x0 - reach || x > x1 + reach || z < z0 - reach || z > z1 + reach) {
                            continue;
                        }

                        String b = this.get(x, y, z);
                        if (tree(b)) {
                            this.set(x, y, z, "air");
                            trees.add(new int[]{x, y, z});
                        }
                    }
                }
            }
        }

        // leaf clusters left floating near the hall (they only touched the cleared trees across a gap) go too
        int bx0 = x0 - reach;
        int bx1 = x1 + reach;
        int bz0 = z0 - reach;
        int bz1 = Math.min(this.R, z1 + reach);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                for (int y = this.yLo; y <= this.yHi; y++) {
                    if (!tree(this.get(x, y, z)) || !seen.add(key(x, y, z))) {
                        continue;
                    }

                    java.util.List<int[]> part = new java.util.ArrayList<>();
                    java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                    q.add(new int[]{x, y, z});
                    boolean anchored = false;
                    while (!q.isEmpty()) {
                        int[] c = q.poll();
                        part.add(c);
                        if (c[0] == bx0 || c[0] == bx1 || c[2] == bz0 || c[2] == bz1) {
                            anchored = true;
                        }

                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    int nx = c[0] + dx;
                                    int ny = c[1] + dy;
                                    int nz = c[2] + dz;
                                    if (nx < bx0 || nx > bx1 || nz < bz0 || nz > bz1) {
                                        continue;
                                    }

                                    String b = this.get(nx, ny, nz);
                                    if (tree(b)) {
                                        if (seen.add(key(nx, ny, nz))) {
                                            q.add(new int[]{nx, ny, nz});
                                        }
                                    } else if (b != null && !b.equals("air") && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1) {
                                        anchored = true;
                                    }
                                }
                            }
                        }
                    }

                    if (!anchored) {
                        for (int[] c : part) {
                            this.set(c[0], c[1], c[2], "air");
                        }
                    }
                }
            }
        }

        LegacyHallDesign hall = new LegacyHallDesign(HALL_X, HALL_Y, HALL_Z, 1813L, (lx, lz) -> {
            int gx = HALL_X + lx - x0;
            int gz = HALL_Z + lz - z0;
            return gx >= 0 && gz >= 0 && gx < ground.length && gz < ground[0].length ? ground[gx][gz] - HALL_Y : Integer.MIN_VALUE;
        });
        for (int x = hall.minX(); x <= hall.maxX(); x++) {
            for (int z = hall.minZ(); z <= hall.maxZ(); z++) {
                hall.column(x, z, (bx, by, bz, b) -> this.set(bx, by, bz, b.substring(10)));
            }
        }

        // the plinth reaches down to the terrain where the ground is lower than the hall's foundations
        for (int x = HALL_X - 21; x <= HALL_X + 21; x++) {
            for (int z = HALL_Z - 27; z <= HALL_Z + 19; z++) {
                if (x - x0 < 0 || z - z0 < 0 || x - x0 >= ground.length || z - z0 >= ground[0].length) {
                    continue;
                }

                for (int y = ground[x - x0][z - z0] + 1; y < hall.bottomY(); y++) {
                    this.set(x, y, z, "stone_bricks");
                }
            }
        }

        this.features.putAll(hall.features());
    }

    /** Height (relative to the floor) of the highest block of a column, or 0. */
    private int top(int x, int z) {
        for (int y = this.yHi; y >= this.yLo; y--) {
            if (!this.empty(x, y, z)) {
                return y;
            }
        }

        return 0;
    }

    private static InputStream open() throws IOException {
        InputStream in = ClubSpawnDesign.class.getResourceAsStream("/lobbies/club_spawn.vrmap");
        if (in == null) {
            throw new IOException("lobbies/club_spawn.vrmap is missing from the jar");
        }

        return new GZIPInputStream(in, 1 << 16);
    }

    /** {sizeX, sizeY, sizeZ, originX, originY, originZ} of the stored map. */
    private static int[] dims() {
        try (DataInputStream d = new DataInputStream(open())) {
            d.readFully(new byte[6]);
            int[] out = new int[6];
            for (int i = 0; i < 6; i++) {
                out[i] = d.readInt();
            }

            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int varint(DataInputStream d) throws IOException {
        int r = 0;
        int s = 0;
        while (true) {
            int c = d.readUnsignedByte();
            r |= (c & 0x7F) << s;
            s += 7;
            if (c < 0x80) {
                return r;
            }
        }
    }

    private void load() {
        try (DataInputStream d = new DataInputStream(open())) {
            d.readFully(new byte[6]);
            int sx = d.readInt();
            int sy = d.readInt();
            int sz = d.readInt();
            d.readInt();
            int oy = d.readInt();
            d.readInt();
            int n = d.readInt();
            String[] pal = new String[n];
            for (int i = 0; i < n; i++) {
                byte[] b = new byte[d.readUnsignedShort()];
                d.readFully(b);
                pal[i] = new String(b, StandardCharsets.UTF_8);
            }

            long total = (long)sx * sy * sz;
            long i = 0;
            int hx = sx / 2;
            int hz = sz / 2;
            while (i < total) {
                int run = varint(d);
                int v = varint(d);
                if (v != 0) {
                    for (long k = i; k < i + run; k++) {
                        int y = (int)(k % sy);
                        long col = k / sy;
                        int z = (int)(col % sz);
                        int x = (int)(col / sz);
                        this.set(x - hx, oy + y - GROUND, z - hz, pal[v]);
                    }
                }

                i += run;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the Club Spawn map", e);
        }
    }

    @Override
    public String name() {
        return "Club Spawn";
    }
}
