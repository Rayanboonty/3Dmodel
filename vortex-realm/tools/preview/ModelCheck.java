import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.practicepvp.build.Voxels;
import net.practicepvp.match.MapModel;

/**
 * The map vote's 3D miniature next to its arena: runs the same voxel code as the game ({@link MapModel#voxelize}) on a
 * design, fills the miniature's boxes back in at full size and draws both from the same camera, side by side.
 * <pre>java -cp ... ModelCheck court SAKURA_TERRACE 96 out/dir [budget]
 * java -cp ... ModelCheck pro MEDIEVAL 96 out/dir
 * java -cp ... ModelCheck master DRAGONSPIRE 96 out/dir</pre>
 */
public final class ModelCheck {
    /** Blocks the game's miniature leaves out (the same idea as MapModel.empty, by name). */
    static boolean empty(String b) {
        return b.equals("air") || b.endsWith("_air") || b.equals("barrier") || b.equals("light") || b.equals("structure_void")
            || b.endsWith("_carpet") || b.endsWith("_pane") || b.endsWith("_fence") || b.endsWith("_fence_gate") || b.endsWith("_door")
            || b.endsWith("_trapdoor") || (b.endsWith("_wall") && !b.endsWith("_wall_banner")) || b.contains("banner") || b.contains("sign")
            || b.endsWith("_head") || b.endsWith("_skull") || b.endsWith("lantern") || b.contains("candle") || b.equals("iron_bars")
            || b.endsWith("chain") || b.endsWith("_rod") || b.contains("campfire") || b.equals("ladder") || b.equals("scaffolding")
            || b.startsWith("potted_") || b.equals("flower_pot") || b.contains("torch") || b.equals("short_grass") || b.equals("tall_grass")
            || b.equals("fern") || b.equals("large_fern") || b.endsWith("_petals") || b.equals("dead_bush") || b.contains("flower")
            || b.endsWith("_tulip") || b.equals("poppy") || b.equals("dandelion") || b.equals("allium") || b.equals("azure_bluet")
            || b.equals("oxeye_daisy") || b.equals("cornflower") || b.equals("lily_of_the_valley") || b.equals("vine") || b.endsWith("_vines")
            || b.equals("lily_pad") || b.equals("sugar_cane") || b.equals("snow") || b.endsWith("_button") || b.endsWith("_pressure_plate")
            || b.equals("lever") || b.equals("rail") || b.equals("seagrass") || b.equals("kelp") || b.equals("cobweb") || b.endsWith("_sapling")
            || b.equals("bamboo") || b.equals("glow_lichen") || b.equals("hanging_roots") || b.equals("moss_carpet") || b.endsWith("_coral")
            || b.endsWith("_coral_fan") || b.equals("sweet_berry_bush") || b.equals("pointed_dripstone") || b.equals("bell");
    }

    /** Variants merge into one block (stairs and slabs draw as their full block), like MapModel.family. */
    static String family(String b) {
        return MapModel.tone(family0(b));
    }

    static String family0(String b) {
        if (b.equals("water")) {
            return "light_blue_stained_glass";
        } else if (b.equals("lava")) {
            return "orange_stained_glass";
        } else if (b.endsWith("_stairs") || b.endsWith("_slab")) {
            String base = b.substring(0, b.lastIndexOf('_'));
            if (base.endsWith("brick")) {
                return base + "s";
            } else if (base.endsWith("tile")) {
                return base + "s";
            } else if (base.equals("oak") || base.equals("spruce") || base.equals("birch") || base.equals("dark_oak") || base.equals("jungle")
                || base.equals("acacia") || base.equals("cherry") || base.equals("mangrove") || base.equals("bamboo") || base.equals("crimson")
                || base.equals("warped") || base.equals("pale_oak")) {
                return base + "_planks";
            } else {
                return base;
            }
        }

        return b;
    }

    /** A design that holds the miniature, filled back in at full size. */
    static final class Mini extends Voxels {
        Mini(int cx, int floorY, int cz, int r, int yLo, int yHi) {
            super(cx, floorY, cz, r, yLo, yHi, 1L);
        }

        void put(int x, int y, int z, String b) {
            this.set(x, y, z, b);
        }

        @Override
        public String name() {
            return "mini";
        }
    }

    public static void main(String[] args) throws Exception {
        String kind = args[0];
        String style = args[1];
        int size = Integer.parseInt(args[2]);
        File out = new File(args[3]);
        out.mkdirs();
        int budget = args.length > 4 ? Integer.parseInt(args[4]) : 700;
        Voxels d = design(kind, style, size);
        int ox = d.minX();
        int oz = d.minZ();
        int oy = d.bottomY();
        int sx = d.maxX() - ox + 1;
        int sz = d.maxZ() - oz + 1;
        int sy = d.topY() - oy + 1;
        String[] grid = new String[sx * sy * sz];
        for (int x = ox; x <= d.maxX(); x++) {
            for (int z = oz; z <= d.maxZ(); z++) {
                d.column(x, z, (bx, by, bz, block) -> {
                    String b = block.startsWith("minecraft:") ? block.substring(10) : block;
                    int i = b.indexOf('[');
                    b = i < 0 ? b : b.substring(0, i);
                    int lx = bx - ox;
                    int ly = by - oy;
                    int lz = bz - oz;
                    if (lx >= 0 && ly >= 0 && lz >= 0 && lx < sx && ly < sy && lz < sz && !empty(b)) {
                        grid[(lx * sz + lz) * sy + ly] = family(b);
                    }
                });
            }
        }

        Map<String, String> intern = new HashMap<>();
        long t0 = System.currentTimeMillis();
        MapModel.Voxelized<String> v = MapModel.voxelize(sx, sy, sz, (x, y, z) -> grid[(x * sz + z) * sy + y], budget);
        long t1 = System.currentTimeMillis();
        System.out.println(style + ": " + v.boxes().size() + " parts, grid " + v.cells() + ", " + (t1 - t0) + " ms");
        // fill the boxes back in, around the same centre (the vertical middle sits at the middle of the miniature)
        int r = Math.max(sx, sz) / 2 + 2;
        Mini m = new Mini(d.minX() + sx / 2, d.floorYAt(), d.minZ() + sz / 2, r, d.bottomY() - d.floorYAt(), d.topY() - d.floorYAt());
        float lowest = Float.MAX_VALUE;
        for (MapModel.Box<String> b : v.boxes()) {
            lowest = Math.min(lowest, b.y());
        }

        // the miniature's lowest box sits where the real ground slab starts
        int groundShift = groundStart(grid, sx, sy, sz);
        for (MapModel.Box<String> b : v.boxes()) {
            int x0 = Math.round(b.x() + sx / 2.0F) - sx / 2;
            int z0 = Math.round(b.z() + sz / 2.0F) - sz / 2;
            int y0 = Math.round(b.y() - lowest) + groundShift + (d.bottomY() - d.floorYAt());
            for (int x = x0; x < x0 + Math.round(b.w()); x++) {
                for (int z = z0; z < z0 + Math.round(b.d()); z++) {
                    for (int y = y0; y < y0 + Math.round(b.h()); y++) {
                        m.put(x, y, z, intern.computeIfAbsent(b.block(), k -> k));
                    }
                }
            }
        }

        double span = Math.max(sx, sz);
        int fy = d.floorYAt();
        int W = 960;
        int H = 540;
        BufferedImage a = new ArenaPreview(d).render(span * 0.55 + d.minX() + sx / 2.0, fy + span * 0.42, span * 0.62 + d.minZ() + sz / 2.0, 138, 30, 62, W, H);
        BufferedImage b = new ArenaPreview(m).render(span * 0.55 + d.minX() + sx / 2.0, fy + span * 0.42, span * 0.62 + d.minZ() + sz / 2.0, 138, 30, 62, W, H);
        BufferedImage both = new BufferedImage(W * 2, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = both.createGraphics();
        g.drawImage(a, 0, 0, null);
        g.drawImage(b, W, 0, null);
        g.drawString("arena", 12, 20);
        g.drawString("vote preview (" + v.boxes().size() + " parts)", W + 12, 20);
        g.dispose();
        ImageIO.write(both, "png", new File(out, style.toLowerCase() + "-model.png"));
    }

    /** Height (above the design bottom) where the voxel code starts keeping ground. */
    static int groundStart(String[] grid, int sx, int sy, int sz) {
        int[] tops = new int[sx * sz];
        int k = 0;
        for (int x = 0; x < sx; x += 2) {
            for (int z = 0; z < sz; z += 2) {
                for (int y = sy - 1; y >= 0; y--) {
                    if (grid[(x * sz + z) * sy + y] != null) {
                        tops[k++] = y;
                        break;
                    }
                }
            }
        }

        java.util.Arrays.sort(tops, 0, k);
        int floor = k == 0 ? 0 : tops[k / 2];
        return Math.max(0, floor - Math.max(4, (int)(Math.max(sx, sz) * 0.12F)));
    }

    static Voxels design(String kind, String style, int size) throws Exception {
        if (kind.equals("pro")) {
            return new net.practicepvp.build.ProArenaDesign(net.practicepvp.build.ProArenaDesign.Theme.valueOf(style), size, 1234L, 0, 64, 0, 12, false, true, false);
        } else if (kind.equals("master")) {
            Class<?> c = Class.forName("net.practicepvp.build.MasterArenaDesign");
            Class<?> st = Class.forName("net.practicepvp.build.MasterArenaDesign$Style");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object s = Enum.valueOf((Class)st, style);
            return (Voxels)c.getConstructor(st, int.class, long.class, int.class, int.class, int.class, boolean.class).newInstance(s, size, 1234L, 0, 64, 0, false);
        }

        return new net.practicepvp.build.CourtArenaDesign(net.practicepvp.build.CourtArenaDesign.Style.valueOf(style), size, 1234L, 0, 64, 0, 4, false, true);
    }
}
