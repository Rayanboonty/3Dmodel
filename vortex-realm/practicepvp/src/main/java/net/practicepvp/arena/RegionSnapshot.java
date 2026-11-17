package net.practicepvp.arena;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.practicepvp.util.Cuboid;
import net.practicepvp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * A block-for-block copy of an arena region (palette + indices + 4x4x4 biome cells). Indices are packed into one byte per
 * block when the palette is small enough (almost always), otherwise two. The file format is unchanged from 1.0.
 */
public final class RegionSnapshot {
    private static final int MAGIC = 1347441457;
    /** Chunk snapshots taken per tick while loading a region, so big arenas don't spike one tick. */
    private static final int SNAPSHOTS_PER_TICK = 32;
    /** Chunk loads requested at once. */
    private static final int MAX_IN_FLIGHT = 64;
    public final int ox;
    public final int oy;
    public final int oz;
    public final int sx;
    public final int sy;
    public final int sz;
    private final BlockData[] palette;
    private final byte[] blocks8;
    private final short[] blocks16;
    private final Biome[] biomePalette;
    private final byte[] biomes;
    /** airLayer[y] = every block of that y layer is air. */
    private final boolean[] airLayer;
    private final int airId;

    private RegionSnapshot(int ox, int oy, int oz, int sx, int sy, int sz, BlockData[] palette, short[] ids, Biome[] biomePalette, byte[] biomes) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.palette = palette;
        if (palette.length <= 256) {
            byte[] b = new byte[ids.length];

            for (int i = 0; i < ids.length; i++) {
                b[i] = (byte)ids[i];
            }

            this.blocks8 = b;
            this.blocks16 = null;
        } else {
            this.blocks8 = null;
            this.blocks16 = ids;
        }

        this.biomePalette = biomePalette;
        this.biomes = biomes;
        int air = -1;

        for (int i = 0; i < palette.length; i++) {
            if (palette[i].getMaterial() == Material.AIR) {
                air = i;
                break;
            }
        }

        this.airId = air;
        this.airLayer = new boolean[sy];
        int layer = sx * sz;

        for (int y = 0; y < sy; y++) {
            boolean all = air >= 0;

            for (int i = y * layer, end = i + layer; all && i < end; i++) {
                all = this.id(i) == air;
            }

            this.airLayer[y] = all;
        }
    }

    private int id(int i) {
        return this.blocks8 != null ? this.blocks8[i] & 255 : this.blocks16[i];
    }

    public Cuboid region() {
        return new Cuboid(this.ox, this.oy, this.oz, this.ox + this.sx - 1, this.oy + this.sy - 1, this.oz + this.sz - 1);
    }

    private int index(int x, int y, int z) {
        return (y * this.sz + z) * this.sx + x;
    }

    private int bx() {
        return this.sx + 3 >> 2;
    }

    private int by() {
        return this.sy + 3 >> 2;
    }

    private int bz() {
        return this.sz + 3 >> 2;
    }

    public BlockData blockAt(int rx, int ry, int rz) {
        return this.palette[this.id(this.index(rx, ry, rz))];
    }

    public Biome biomeAtCell(int cx, int cy, int cz) {
        return this.biomes != null && this.biomePalette.length != 0 ? this.biomePalette[this.biomes[(cy * this.bz() + cz) * this.bx() + cx] & 255] : null;
    }

    public int volume() {
        return this.sx * this.sy * this.sz;
    }

    /** Bytes held in memory for the block indices (for the self test / admin info). */
    public long memoryBytes() {
        return this.blocks8 != null ? this.blocks8.length : this.blocks16.length * 2L;
    }

    public boolean packed() {
        return this.blocks8 != null;
    }

    public int paletteSize() {
        return this.palette.length;
    }

    public static CompletableFuture<RegionSnapshot> capture(Plugin plugin, World w, Cuboid c) {
        return loadSnapshots(w, c).thenApplyAsync(snaps -> build(c, snaps));
    }

    /**
     * Loads (and generates if needed) every chunk of the region and snapshots it on the main thread, at most
     * {@link #SNAPSHOTS_PER_TICK} per tick and {@link #MAX_IN_FLIGHT} loads at a time. Each chunk is snapshotted as soon as it
     * is loaded, so slow chunks can't unload the early ones before they are read.
     */
    static CompletableFuture<Map<Long, ChunkSnapshot>> loadSnapshots(World w, Cuboid c) {
        int cx0 = c.minX() >> 4;
        int cx1 = c.maxX() >> 4;
        int cz0 = c.minZ() >> 4;
        int cz1 = c.maxZ() >> 4;
        ArrayDeque<long[]> todo = new ArrayDeque<>();

        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                todo.add(new long[]{cx, cz});
            }
        }

        int total = todo.size();
        Map<Long, ChunkSnapshot> map = new ConcurrentHashMap<>();
        ArrayDeque<Chunk> ready = new ArrayDeque<>();
        CompletableFuture<Map<Long, ChunkSnapshot>> result = new CompletableFuture<>();
        int[] inFlight = new int[1];
        BukkitTask[] task = new BukkitTask[1];
        Runnable pump = () -> {
            try {
                int taken = 0;

                while (!ready.isEmpty() && taken < SNAPSHOTS_PER_TICK) {
                    Chunk ch = ready.poll();
                    map.put(key(ch.getX(), ch.getZ()), ch.getChunkSnapshot(true, true, false));
                    taken++;
                }

                while (inFlight[0] < MAX_IN_FLIGHT && !todo.isEmpty()) {
                    long[] next = todo.poll();
                    inFlight[0]++;
                    w.getChunkAtAsync((int)next[0], (int)next[1], true).whenComplete((ch, err) -> Tasks.ensureSync(() -> {
                        inFlight[0]--;
                        if (err != null) {
                            if (!result.isDone()) {
                                result.completeExceptionally(err);
                            }
                        } else {
                            ready.add(ch);
                        }
                    }));
                }

                if (result.isDone()) {
                    Tasks.cancel(task[0]);
                } else if (map.size() >= total) {
                    Tasks.cancel(task[0]);
                    result.complete(map);
                }
            } catch (Throwable t) {
                Tasks.cancel(task[0]);
                if (!result.isDone()) {
                    result.completeExceptionally(t);
                }
            }
        };
        Tasks.ensureSync(() -> {
            if (total == 0) {
                result.complete(map);
            } else {
                task[0] = Tasks.timer(0L, 1L, pump);
            }
        });
        return result;
    }

    static long key(int cx, int cz) {
        return (long)cx << 32 ^ cz & 4294967295L;
    }

    private static RegionSnapshot build(Cuboid c, Map<Long, ChunkSnapshot> snaps) {
        RegionSnapshot.Builder b = new RegionSnapshot.Builder(c.minX(), c.minY(), c.minZ(), c.sizeX(), c.sizeY(), c.sizeZ());

        for (int y = 0; y < b.sy; y++) {
            int wy = c.minY() + y;

            for (int z = 0; z < b.sz; z++) {
                int wz = c.minZ() + z;

                for (int x = 0; x < b.sx; x++) {
                    int wx = c.minX() + x;
                    ChunkSnapshot cs = snaps.get(key(wx >> 4, wz >> 4));
                    b.set(x, y, z, cs.getBlockData(wx & 15, wy, wz & 15));
                }
            }
        }

        for (int cy = 0; cy < b.by(); cy++) {
            int wy = Math.min(c.minY() + cy * 4, c.maxY());

            for (int cz = 0; cz < b.bz(); cz++) {
                int wz = Math.min(c.minZ() + cz * 4, c.maxZ());

                for (int cx = 0; cx < b.bx(); cx++) {
                    int wx = Math.min(c.minX() + cx * 4, c.maxX());
                    ChunkSnapshot cs = snaps.get(key(wx >> 4, wz >> 4));
                    b.biome(cx, cy, cz, cs.getBiome(wx & 15, wy, wz & 15));
                }
            }
        }

        return b.build();
    }

    public CompletableFuture<List<RegionSnapshot.Fix>> diff(World w, int dx, int dz) {
        Cuboid target = this.region().shift(dx, 0, dz);
        return loadSnapshots(w, target).thenApplyAsync(snaps -> {
            List<RegionSnapshot.Fix> fixes = new ArrayList<>();
            int minY = w.getMinHeight();

            for (int y = 0; y < this.sy; y++) {
                int wy = this.oy + y;
                boolean wantAir = this.airLayer[y];

                for (int z = 0; z < this.sz; z++) {
                    int wz = this.oz + z + dz;

                    for (int x = 0; x < this.sx; x++) {
                        int wx = this.ox + x + dx;
                        ChunkSnapshot cs = snaps.get(key(wx >> 4, wz >> 4));
                        if (wantAir && cs.isSectionEmpty(wy - minY >> 4)) {
                            // the whole section is air in the world and this layer must be air: nothing to fix in this chunk row
                            int skip = 15 - (wx & 15);
                            x += Math.min(skip, this.sx - 1 - x);
                            continue;
                        }

                        BlockData want = this.palette[this.id((y * this.sz + z) * this.sx + x)];
                        BlockData have = cs.getBlockData(wx & 15, wy, wz & 15);
                        if (!have.equals(want)) {
                            fixes.add(new RegionSnapshot.Fix(wx, wy, wz, want));
                        }
                    }
                }
            }

            return fixes;
        });
    }

    public BlockJobs.Job pasteJob(World w, int dx, int dz) {
        return this.pasteJob(w, dx, dz, false);
    }

    /**
     * Writes the snapshot into the world. {@code fresh} = the target area is known to be empty (a new slot or a wiped area):
     * air is skipped entirely, which makes pasting a big terrain map several times faster.
     */
    public BlockJobs.Job pasteJob(final World w, final int dx, final int dz, final boolean fresh) {
        return new BlockJobs.Job() {
            int i = 0;
            final int total = RegionSnapshot.this.volume();
            final int layer = RegionSnapshot.this.sx * RegionSnapshot.this.sz;
            boolean biomesDone = RegionSnapshot.this.biomes == null;

            @Override
            public int step(int budget) {
                int used = 0;
                RegionSnapshot s = RegionSnapshot.this;

                while (this.i < this.total && used < budget) {
                    int y = this.i / this.layer;
                    if (fresh && s.airLayer[y]) {
                        this.i = (y + 1) * this.layer;
                        used += 8;
                        continue;
                    }

                    int id = s.id(this.i);
                    if (fresh && id == s.airId) {
                        this.i++;
                        if ((++used & 4095) == 0 && BlockJobs.overDeadline()) {
                            break;
                        }

                        continue;
                    }

                    int rest = this.i % this.layer;
                    int z = rest / s.sx;
                    int x = rest % s.sx;
                    BlockData want = s.palette[id];
                    Block b = w.getBlockAt(s.ox + x + dx, s.oy + y, s.oz + z + dz);
                    if (fresh || !b.getBlockData().equals(want)) {
                        b.setBlockData(want, false);
                        used += 4;
                    } else {
                        used++;
                    }

                    this.i++;
                    if ((this.i & 1023) == 0 && BlockJobs.overDeadline()) {
                        break;
                    }
                }

                if (this.i >= this.total && !this.biomesDone) {
                    for (int cy = 0; cy < s.by(); cy++) {
                        for (int cz = 0; cz < s.bz(); cz++) {
                            for (int cx = 0; cx < s.bx(); cx++) {
                                Biome bio = s.biomeAtCell(cx, cy, cz);
                                if (bio != null) {
                                    int wx = s.ox + Math.min(cx * 4, s.sx - 1) + dx;
                                    int wy = s.oy + Math.min(cy * 4, s.sy - 1);
                                    int wz = s.oz + Math.min(cz * 4, s.sz - 1) + dz;
                                    w.setBiome(wx, wy, wz, bio);
                                }
                            }
                        }
                    }

                    this.biomesDone = true;
                    used += 50;
                }

                return used;
            }

            @Override
            public boolean done() {
                return this.i >= this.total && this.biomesDone;
            }

            @Override
            public String name() {
                return fresh ? "paste-fresh" : "paste";
            }
        };
    }

    public static BlockJobs.Job fixJob(final World w, final List<RegionSnapshot.Fix> fixes) {
        return new BlockJobs.Job() {
            int i = 0;

            @Override
            public int step(int budget) {
                int used;
                for (used = 0; this.i < fixes.size() && used < budget; used += 4) {
                    RegionSnapshot.Fix f = fixes.get(this.i++);
                    w.getBlockAt(f.x(), f.y(), f.z()).setBlockData(f.data(), false);
                    if ((this.i & 511) == 0 && BlockJobs.overDeadline()) {
                        break;
                    }
                }

                return used;
            }

            @Override
            public boolean done() {
                return this.i >= fixes.size();
            }

            @Override
            public String name() {
                return "fix";
            }
        };
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        } else {
            File tmp = new File(file.getPath() + "." + System.nanoTime() + ".tmp");

            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp), 65536), 65536))) {
                out.writeInt(MAGIC);
                out.writeInt(this.ox);
                out.writeInt(this.oy);
                out.writeInt(this.oz);
                out.writeInt(this.sx);
                out.writeInt(this.sy);
                out.writeInt(this.sz);
                out.writeInt(this.palette.length);

                for (BlockData d : this.palette) {
                    out.writeUTF(d.getAsString());
                }

                int n = this.volume();

                for (int i = 0; i < n; i++) {
                    out.writeShort(this.id(i));
                }

                out.writeInt(this.biomePalette.length);

                for (Biome b : this.biomePalette) {
                    out.writeUTF(b.getKey().toString());
                }

                out.writeInt(this.biomes == null ? 0 : this.biomes.length);
                if (this.biomes != null) {
                    out.write(this.biomes);
                }
            }

            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static RegionSnapshot load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file), 65536), 65536))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Not a snapshot file: " + file.getName());
            }

            int ox = in.readInt();
            int oy = in.readInt();
            int oz = in.readInt();
            int sx = in.readInt();
            int sy = in.readInt();
            int sz = in.readInt();
            int pn = in.readInt();
            BlockData[] palette = new BlockData[pn];

            for (int i = 0; i < pn; i++) {
                String s = in.readUTF();

                BlockData d;
                try {
                    d = Bukkit.createBlockData(s);
                } catch (IllegalArgumentException var17) {
                    d = Bukkit.createBlockData(Material.AIR);
                }

                palette[i] = d;
            }

            short[] blocks = new short[sx * sy * sz];

            for (int i = 0; i < blocks.length; i++) {
                blocks[i] = in.readShort();
            }

            int bn = in.readInt();
            Biome[] bpal = new Biome[bn];

            for (int i = 0; i < bn; i++) {
                NamespacedKey key = NamespacedKey.fromString(in.readUTF());
                Biome b = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
                bpal[i] = b == null ? Biome.PLAINS : b;
            }

            int blen = in.readInt();
            byte[] biomes = null;
            if (blen > 0) {
                biomes = new byte[blen];
                in.readFully(biomes);
            }

            return new RegionSnapshot(ox, oy, oz, sx, sy, sz, palette, blocks, bpal, biomes);
        }
    }

    /** Builds a snapshot block by block (used by the vanilla terrain cutter, which writes target coordinates directly). */
    public static final class Builder {
        final int ox;
        final int oy;
        final int oz;
        final int sx;
        final int sy;
        final int sz;
        private final short[] ids;
        private final Map<BlockData, Integer> pal = new HashMap<>();
        private final List<BlockData> palList = new ArrayList<>();
        private final byte[] biomes;
        private final Map<Biome, Integer> bpal = new HashMap<>();
        private final List<Biome> bList = new ArrayList<>();

        public Builder(int ox, int oy, int oz, int sx, int sy, int sz) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.ids = new short[sx * sy * sz];
            this.biomes = new byte[this.bx() * this.by() * this.bz()];
            this.id(Material.AIR.createBlockData());
        }

        int bx() {
            return this.sx + 3 >> 2;
        }

        int by() {
            return this.sy + 3 >> 2;
        }

        int bz() {
            return this.sz + 3 >> 2;
        }

        private int id(BlockData d) {
            Integer id = this.pal.get(d);
            if (id == null) {
                id = this.palList.size();
                if (id > 32767) {
                    throw new IllegalStateException("Too many block types in region");
                }

                this.pal.put(d, id);
                this.palList.add(d);
            }

            return id;
        }

        public void set(int x, int y, int z, BlockData d) {
            this.ids[(y * this.sz + z) * this.sx + x] = (short)this.id(d);
        }

        public BlockData get(int x, int y, int z) {
            return this.palList.get(this.ids[(y * this.sz + z) * this.sx + x]);
        }

        public void biome(int cx, int cy, int cz, Biome b) {
            if (b == null) {
                return;
            }

            Integer id = this.bpal.get(b);
            if (id == null) {
                if (this.bList.size() > 250) {
                    id = 0;
                } else {
                    id = this.bList.size();
                    this.bpal.put(b, id);
                    this.bList.add(b);
                }
            }

            this.biomes[(cy * this.bz() + cz) * this.bx() + cx] = (byte)id.intValue();
        }

        public RegionSnapshot build() {
            Biome[] bp = this.bList.isEmpty() ? new Biome[]{Biome.PLAINS} : this.bList.toArray(new Biome[0]);
            return new RegionSnapshot(this.ox, this.oy, this.oz, this.sx, this.sy, this.sz, this.palList.toArray(new BlockData[0]), this.ids, bp, this.biomes);
        }
    }

    public record Fix(int x, int y, int z, BlockData data) {
    }
}
