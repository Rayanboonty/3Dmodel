package net.cmcpvp.build;

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

    public ClubSpawnDesign(int cx, int floorY, int cz, long seed) {
        super(cx, floorY, cz, Math.max(DIMS[0], DIMS[2]) / 2, DIMS[4] - GROUND, DIMS[4] + DIMS[1] - 1 - GROUND, seed);
        this.load();

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
