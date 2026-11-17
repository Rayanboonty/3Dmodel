package net.practicepvp.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.Arena;
import net.practicepvp.arena.RegionSnapshot;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.util.Tasks;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * A detailed miniature of an arena for the map vote, made of a few hundred block displays at most. It is built once,
 * off the main thread, from the arena's snapshot: a detailed top surface (each cell of an N x N grid gets its height
 * and its most common top block, and equal neighbours merge into one box) standing on a coarse underside. N is as fine
 * as the part budget ({@code match.map-preview-detail}) allows.
 */
public final class MapModel {
    /** Arenas are wide and flat: heights are drawn up to this much taller so walls, towers and trees read in miniature. */
    static final float EXAGGERATE = 2.5F;
    private static final int[] GRIDS = new int[]{64, 56, 48, 40, 32, 24, 16};
    private static final Map<String, MapModel> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();
    public final List<MapModel.Part> parts;
    /** Size of the arena along its longer side, in blocks. */
    public final float span;
    public final int cells;
    /** Height of the model from its lowest to its highest part, in blocks. */
    public final float height;
    /** How much taller than wide the model is drawn (at most {@link #EXAGGERATE}, and never taller than half its width). */
    public final float vertical;

    private MapModel(List<MapModel.Part> parts, float span, int cells, float height) {
        this.parts = parts;
        this.span = span;
        this.cells = cells;
        this.height = height;
        this.vertical = Math.min(EXAGGERATE, 0.5F * span / Math.max(1.0F, height));
    }

    /** One box, in blocks, relative to the middle of the model (x and z) and to its vertical middle (y). */
    public record Part(float x, float y, float z, float w, float h, float d, BlockData data) {
    }

    /** Display parts a model may use: LOW, NORMAL or HIGH detail. */
    public static int budget() {
        return switch (Cfg.e(Opt.MAP_PREVIEW_DETAIL, Opt.PreviewDetail.class)) {
            case LOW -> 140;
            case HIGH -> 400;
            default -> 240;
        };
    }

    private static String key(Arena a) {
        return a.id + ":" + a.genSignature + ":" + a.bounds + ":" + budget() + ":" + System.identityHashCode(a.snapshot);
    }

    /** True when the map vote should show this arena as a 3D miniature (match.map-ban-3d-preview). */
    public static boolean wanted(Arena a) {
        Opt.MapPreview mode = Cfg.e(Opt.MAP_PREVIEW_3D, Opt.MapPreview.class);
        if (mode == Opt.MapPreview.OFF || a == null || a.snapshot == null) {
            return false;
        } else if (mode == Opt.MapPreview.ALL) {
            return true;
        } else {
            // designed arenas (sword, pots, UHC); the biome maps keep their biome block
            String pool = Arena.normPool(a.pool);
            return pool.equals("DESIGNED") || pool.equals("DUEL") || pool.equals("DUEL_BUILD") || pool.equals("MACE") || pool.equals("ANY") && !a.vanilla();
        }
    }

    /** The finished model, or null while it is being built (the build starts on the first call). */
    public static MapModel get(Arena a) {
        if (a != null && a.snapshot != null) {
            String k = key(a);
            MapModel m = CACHE.get(k);
            if (m == null && BUSY.add(k)) {
                RegionSnapshot snap = a.snapshot;
                int budget = budget();
                CompletableFuture.runAsync(() -> {
                    try {
                        CACHE.put(k, build(snap, budget));
                    } catch (RuntimeException var9) {
                        PracticePlugin.get().getLogger().log(Level.WARNING, "3D preview of " + a.id + " failed", var9);
                    } finally {
                        BUSY.remove(k);
                    }
                });
            }

            return m;
        } else {
            return null;
        }
    }

    /** Builds the previews of every arena that shows one, spread out after the start. */
    public static void warmLater(PracticePlugin plugin) {
        Tasks.later(400L, () -> {
            List<Arena> list = new ArrayList<>(plugin.arenas().all());
            int delay = 0;

            for (Arena a : list) {
                if (wanted(a)) {
                    Tasks.later(delay, () -> get(a));
                    delay += 10;
                }
            }
        });
    }

    /** The model of a snapshot (also used by the self test and the offline renders). */
    public static MapModel build(RegionSnapshot snap, int budget) {
        int sx = snap.sx;
        int sy = snap.sy;
        int sz = snap.sz;
        int n = sx * sz;
        int[] top = new int[n];
        int[] bottom = new int[n];
        BlockData[] topData = new BlockData[n];
        BlockData[] bodyData = new BlockData[n];
        Map<Material, BlockData> fam = new HashMap<>();
        Arrays.fill(top, Integer.MIN_VALUE);
        int filled = 0;

        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                int t = Integer.MIN_VALUE;
                int b = Integer.MIN_VALUE;
                Material tm = null;

                for (int y = sy - 1; y >= 0; y--) {
                    Material m = snap.blockAt(x, y, z).getMaterial();
                    if (!empty(m)) {
                        if (t == Integer.MIN_VALUE) {
                            t = y;
                            tm = m;
                        }

                        b = y;
                    }
                }

                if (t != Integer.MIN_VALUE) {
                    int i = x * sz + z;
                    top[i] = t;
                    bottom[i] = b;
                    topData[i] = fam.computeIfAbsent(tm, MapModel::family);
                    Material mid = snap.blockAt(x, (t + b) / 2, z).getMaterial();
                    bodyData[i] = empty(mid) ? topData[i] : fam.computeIfAbsent(mid, MapModel::family);
                    filled++;
                }
            }
        }

        if (filled == 0) {
            return new MapModel(List.of(), Math.max(sx, sz), 0, 1.0F);
        } else {
            int[] tops = new int[filled];
            int k = 0;

            for (int i = 0; i < n; i++) {
                if (top[i] != Integer.MIN_VALUE) {
                    tops[k++] = top[i];
                }
            }

            Arrays.sort(tops);
            int floor = tops[tops.length / 2];
            // the base sits under the low ground too (valleys of the biome maps), not only under the floor
            int base = Math.min(floor - 2, tops[tops.length / 20] - 1);
            MapModel best = null;

            for (int cells : GRIDS) {
                MapModel m = mesh(sx, sz, top, bottom, topData, bodyData, floor, base, cells);
                best = m;
                if (m.parts.size() <= budget) {
                    break;
                }
            }

            return best;
        }
    }

    private static MapModel mesh(int sx, int sz, int[] top, int[] bottom, BlockData[] topData, BlockData[] bodyData, int floor, int base, int cells) {
        float span = Math.max(sx, sz);
        float c = span / cells;
        int gx = (int)Math.ceil(sx / c);
        int gz = (int)Math.ceil(sz / c);
        int[] ch = new int[gx * gz];
        BlockData[] cm = new BlockData[gx * gz];
        Arrays.fill(ch, Integer.MIN_VALUE);
        int[] buf = new int[(int)Math.ceil(c + 1) * (int)Math.ceil(c + 1)];
        Map<BlockData, Integer> count = new HashMap<>();

        for (int i = 0; i < gx; i++) {
            for (int j = 0; j < gz; j++) {
                int x0 = (int)(i * c);
                int x1 = Math.min(sx, (int)((i + 1) * c));
                int z0 = (int)(j * c);
                int z1 = Math.min(sz, (int)((j + 1) * c));
                int area = Math.max(1, (x1 - x0) * (z1 - z0));
                int m = 0;

                for (int x = x0; x < x1; x++) {
                    for (int z = z0; z < z1; z++) {
                        int t = top[x * sz + z];
                        if (t != Integer.MIN_VALUE && t >= base && m < buf.length) {
                            buf[m++] = t;
                        }
                    }
                }

                if (m * 3 >= area) {
                    Arrays.sort(buf, 0, m);
                    int h = buf[Math.min(m - 1, (int)(m * 0.75))];
                    if (Math.abs(h - floor) <= 1) {
                        h = floor;
                    }

                    count.clear();

                    for (int x = x0; x < x1; x++) {
                        for (int z = z0; z < z1; z++) {
                            int t = top[x * sz + z];
                            if (t != Integer.MIN_VALUE && Math.abs(t - h) <= 1) {
                                count.merge(topData[x * sz + z], 1, Integer::sum);
                            }
                        }
                    }

                    ch[i * gz + j] = h;
                    cm[i * gz + j] = mode(count);
                }
            }
        }

        // a lone cell of another block takes its neighbours' block (noise, patterns smaller than a cell)
        BlockData[] smooth = cm.clone();

        for (int i = 0; i < gx; i++) {
            for (int j = 0; j < gz; j++) {
                BlockData own = cm[i * gz + j];
                if (own != null) {
                    count.clear();
                    boolean alone = true;

                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int a = i + d[0];
                        int b = j + d[1];
                        if (a >= 0 && b >= 0 && a < gx && b < gz && cm[a * gz + b] != null) {
                            count.merge(cm[a * gz + b], 1, Integer::sum);
                            alone &= !cm[a * gz + b].equals(own);
                        }
                    }

                    if (alone && !count.isEmpty()) {
                        smooth[i * gz + j] = mode(count);
                    }
                }
            }
        }

        List<int[]> topBoxes = merge(gx, gz, ch, smooth);
        // the underside, four times coarser
        int ux = Math.max(4, cells / 4);
        float uc = span / ux;
        int ugx = (int)Math.ceil(sx / uc);
        int ugz = (int)Math.ceil(sz / uc);
        int[] uh = new int[ugx * ugz];
        BlockData[] um = new BlockData[ugx * ugz];
        Arrays.fill(uh, Integer.MIN_VALUE);
        int lowest = base;
        // deep maps (crystal, the biome maps) would be mostly underground rock: the underside stops at 15% of the width
        int cap = Math.floorDiv(base - Math.max(6, (int)(span * 0.15F)), 3) * 3;

        for (int i = 0; i < ugx; i++) {
            for (int j = 0; j < ugz; j++) {
                List<Integer> lows = new ArrayList<>();
                int samples = 0;
                count.clear();

                for (int x = (int)(i * uc); x < Math.min(sx, (int)((i + 1) * uc)); x += 2) {
                    for (int z = (int)(j * uc); z < Math.min(sz, (int)((j + 1) * uc)); z += 2) {
                        samples++;
                        int idx = x * sz + z;
                        if (top[idx] != Integer.MIN_VALUE && top[idx] >= base && bottom[idx] < base) {
                            lows.add(bottom[idx]);
                            count.merge(bodyData[idx], 1, Integer::sum);
                        }
                    }
                }

                if (!lows.isEmpty() && lows.size() * 2 >= samples) {
                    lows.sort(null);
                    int bt = Math.max(cap, Math.floorDiv(lows.get(lows.size() / 3), 3) * 3);
                    uh[i * ugz + j] = bt;
                    um[i * ugz + j] = mode(count);
                    lowest = Math.min(lowest, bt);
                }
            }
        }

        List<int[]> underBoxes = merge(ugx, ugz, uh, um);
        int highest = base + 1;

        for (int h : ch) {
            if (h != Integer.MIN_VALUE) {
                highest = Math.max(highest, h + 1);
            }
        }

        float mid = (lowest + highest) / 2.0F;
        float hx = sx / 2.0F;
        float hz = sz / 2.0F;
        List<MapModel.Part> parts = new ArrayList<>();

        for (int[] r : topBoxes) {
            float x0 = r[0] * c;
            float z0 = r[1] * c;
            float x1 = Math.min(sx, (r[2] + 1) * c);
            float z1 = Math.min(sz, (r[3] + 1) * c);
            int h = ch[r[0] * gz + r[1]];
            parts.add(new MapModel.Part(x0 - hx, base - mid, z0 - hz, x1 - x0, h - base + 1, z1 - z0, smooth[r[0] * gz + r[1]]));
        }

        for (int[] r : underBoxes) {
            float x0 = r[0] * uc;
            float z0 = r[1] * uc;
            float x1 = Math.min(sx, (r[2] + 1) * uc);
            float z1 = Math.min(sz, (r[3] + 1) * uc);
            int b = uh[r[0] * ugz + r[1]];
            parts.add(new MapModel.Part(x0 - hx, b - mid, z0 - hz, x1 - x0, base - b, z1 - z0, um[r[0] * ugz + r[1]]));
        }

        return new MapModel(parts, span, cells, highest - lowest);
    }

    /** Greedy rectangles of equal (height, block) cells: {i0, j0, i1, j1}. */
    private static List<int[]> merge(int gx, int gz, int[] h, BlockData[] m) {
        boolean[] used = new boolean[gx * gz];
        List<int[]> out = new ArrayList<>();

        for (int j = 0; j < gz; j++) {
            for (int i = 0; i < gx; i++) {
                int k = i * gz + j;
                if (!used[k] && h[k] != Integer.MIN_VALUE && m[k] != null) {
                    int i1 = i;

                    while (i1 + 1 < gx && same(h, m, used, k, (i1 + 1) * gz + j)) {
                        i1++;
                    }

                    int j1 = j;
                    boolean grow = true;

                    while (grow && j1 + 1 < gz) {
                        for (int a = i; a <= i1; a++) {
                            if (!same(h, m, used, k, a * gz + j1 + 1)) {
                                grow = false;
                                break;
                            }
                        }

                        if (grow) {
                            j1++;
                        }
                    }

                    for (int a = i; a <= i1; a++) {
                        for (int b = j; b <= j1; b++) {
                            used[a * gz + b] = true;
                        }
                    }

                    out.add(new int[]{i, j, i1, j1});
                }
            }
        }

        return out;
    }

    private static boolean same(int[] h, BlockData[] m, boolean[] used, int a, int b) {
        return !used[b] && h[b] == h[a] && m[b] != null && m[b].equals(m[a]);
    }

    private static BlockData mode(Map<BlockData, Integer> count) {
        BlockData best = null;
        int most = 0;

        for (Map.Entry<BlockData, Integer> e : count.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }

        return best;
    }

    /** Air, and thin decoration that would only blur a miniature (flowers, carpets, torches, railings...). */
    static boolean empty(Material m) {
        if (m.isAir() || m == Material.BARRIER || m == Material.LIGHT || m == Material.STRUCTURE_VOID) {
            return true;
        } else if (m == Material.WATER || m == Material.LAVA) {
            return false;
        } else if (!m.isSolid()) {
            return true;
        } else {
            String n = m.name();
            return n.endsWith("_CARPET")
                || n.endsWith("_PANE")
                || n.endsWith("_FENCE")
                || n.endsWith("_FENCE_GATE")
                || n.endsWith("_DOOR")
                || n.endsWith("_TRAPDOOR")
                || n.endsWith("_WALL")
                || n.endsWith("_BANNER")
                || n.endsWith("_SIGN")
                || n.endsWith("_HEAD")
                || n.endsWith("_SKULL")
                || n.endsWith("LANTERN")
                || n.endsWith("_CANDLE")
                || n.equals("CANDLE")
                || n.equals("IRON_BARS")
                || n.endsWith("CHAIN")
                || n.endsWith("_ROD")
                || n.endsWith("CAMPFIRE")
                || n.equals("LADDER")
                || n.equals("SCAFFOLDING")
                || n.startsWith("POTTED_")
                || n.equals("FLOWER_POT");
        }
    }

    /** The block a miniature draws for a block: variants merge into one, and every one renders as a plain cube. */
    static BlockData family(Material m) {
        return familyMaterial(m).createBlockData();
    }

    private static Material familyMaterial(Material m) {
        String n = m.name();
        if (m == Material.WATER || m == Material.BUBBLE_COLUMN) {
            return Material.LIGHT_BLUE_STAINED_GLASS;
        } else if (m == Material.LAVA) {
            return Material.ORANGE_STAINED_GLASS;
        } else if (m == Material.SNOW || m == Material.POWDER_SNOW) {
            return Material.SNOW_BLOCK;
        } else if (n.contains("STONE_BRICK")) {
            return Material.STONE_BRICKS;
        } else if (n.contains("DEEPSLATE") && !n.contains("ORE")) {
            return Material.POLISHED_DEEPSLATE;
        } else if (n.contains("BLACKSTONE")) {
            return Material.POLISHED_BLACKSTONE;
        } else if (n.contains("SANDSTONE")) {
            return n.contains("RED") ? Material.RED_SANDSTONE : Material.SANDSTONE;
        } else if (n.contains("QUARTZ")) {
            return Material.QUARTZ_BLOCK;
        } else if (n.contains("PRISMARINE")) {
            return n.contains("DARK") ? Material.DARK_PRISMARINE : Material.PRISMARINE;
        } else if (n.contains("NETHER_BRICK")) {
            return n.contains("RED") ? Material.RED_NETHER_BRICKS : Material.NETHER_BRICKS;
        } else if (n.contains("COBBLESTONE")) {
            return Material.COBBLESTONE;
        } else if (n.contains("ANDESITE")) {
            return Material.POLISHED_ANDESITE;
        } else if (n.contains("DIORITE")) {
            return Material.POLISHED_DIORITE;
        } else if (n.contains("GRANITE")) {
            return Material.POLISHED_GRANITE;
        } else if (n.contains("BASALT")) {
            return n.contains("SMOOTH") ? Material.SMOOTH_BASALT : Material.POLISHED_BASALT;
        } else if (n.contains("CHEST") || n.contains("BARREL")) {
            // block entities draw nothing in a block display
            return Material.BARREL;
        } else if (n.endsWith("SHULKER_BOX")) {
            return Material.PURPUR_BLOCK;
        } else if (n.endsWith("_BED")) {
            return Material.WHITE_WOOL;
        } else if (n.equals("BELL")) {
            return Material.GOLD_BLOCK;
        } else if (n.equals("DECORATED_POT")) {
            return Material.TERRACOTTA;
        } else if (n.endsWith("_STAIRS") || n.endsWith("_SLAB")) {
            String b = n.substring(0, n.lastIndexOf('_'));

            for (String c : new String[]{b, b + "S", b + "_PLANKS", b.replace("BRICK", "BRICKS"), b.replace("_TILE", "_TILES")}) {
                Material f = Material.matchMaterial(c);
                if (f != null && f.isBlock() && f.isSolid() && !f.name().endsWith("_SLAB") && !f.name().endsWith("_STAIRS")) {
                    return f;
                }
            }

            return Material.STONE;
        } else {
            return m.isBlock() ? m : Material.STONE;
        }
    }
}
