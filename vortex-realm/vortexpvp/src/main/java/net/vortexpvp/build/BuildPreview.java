package net.vortexpvp.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A 3D miniature of a build (hub, spawn or small build) that floats in front of one admin, turning slowly, so they can
 * see a design before building it. The design runs without touching the world, is reduced to a grid of about 44 cells
 * across, and every visible run of cells becomes one block display that only that admin sees.
 */
public final class BuildPreview {
    /** Cells across the longest side of the model. */
    private static final int CELLS = 44;
    private static final int BUDGET = 1100;
    private static final Map<String, BuildPreview.Model> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, BuildPreview.View> VIEWS = new ConcurrentHashMap<>();

    private BuildPreview() {
    }

    /** One box of the model in cell units (y up from the lowest cell). */
    public record Part(int x, int y, int z, int w, int h, int d, String block) {
    }

    public record Model(String name, int nx, int ny, int nz, List<BuildPreview.Part> parts) {
    }

    /** Something that streams its blocks: a design run at the origin. */
    public interface Source {
        void blocks(SpawnBuilder.BlockFeed out);
    }

    private static String simplify(String b) {
        String s = b.startsWith("minecraft:") ? b.substring(10) : b;
        int i = s.indexOf('[');
        if (i >= 0) {
            s = s.substring(0, i);
        }

        if (s.equals("water")) {
            return "light_blue_stained_glass";
        } else if (s.equals("lava")) {
            return "magma_block";
        } else if (s.equals("air") || s.equals("barrier") || s.equals("cave_air") || s.equals("light") || s.endsWith("_pane") || s.endsWith("fence")
            || s.endsWith("_wall") || s.endsWith("torch") || s.equals("short_grass") || s.equals("tall_grass") || s.endsWith("_carpet")
            || s.endsWith("_button") || s.endsWith("_sign") || s.endsWith("_trapdoor") || s.equals("lantern") || s.equals("end_rod")
            || s.equals("lightning_rod") || s.equals("iron_bars") || s.endsWith("_rod") || s.contains("flower") || s.equals("poppy")
            || s.equals("dandelion") || s.equals("cornflower") || s.equals("azure_bluet") || s.equals("oxeye_daisy") || s.endsWith("_banner")) {
            return null;
        } else if (s.endsWith("_stairs")) {
            String base = s.substring(0, s.length() - 7);
            return base.endsWith("brick") || base.endsWith("tile") ? base + "s" : (base.equals("quartz") ? "quartz_block" : base);
        } else if (s.endsWith("_slab")) {
            String base = s.substring(0, s.length() - 5);
            return base.endsWith("brick") || base.endsWith("tile") ? base + "s" : (base.equals("quartz") ? "quartz_block" : base);
        } else {
            return s;
        }
    }

    /** Reduces a design to a model. Pure computation: safe off the main thread. */
    public static BuildPreview.Model build(String name, BuildPreview.Source src) {
        int[] box = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        src.blocks((x, y, z, b) -> {
            if (simplify(b) != null) {
                box[0] = Math.min(box[0], x);
                box[1] = Math.min(box[1], y);
                box[2] = Math.min(box[2], z);
                box[3] = Math.max(box[3], x);
                box[4] = Math.max(box[4], y);
                box[5] = Math.max(box[5], z);
            }
        });
        if (box[0] > box[3]) {
            return new BuildPreview.Model(name, 1, 1, 1, List.of());
        } else {
            int span = Math.max(box[3] - box[0], box[5] - box[2]) + 1;

            for (int c = Math.max(1, (int)Math.ceil(span / (double)CELLS)); ; c++) {
                BuildPreview.Model m = reduce(name, src, box, c);
                if (m.parts().size() <= BUDGET || c > span / 8) {
                    return m;
                }
            }
        }
    }

    private static BuildPreview.Model reduce(String name, BuildPreview.Source src, int[] box, int c) {
        int nx = (box[3] - box[0]) / c + 1;
        int ny = (box[4] - box[1]) / c + 1;
        int nz = (box[5] - box[2]) / c + 1;
        // per cell: counts of each block (small maps; most cells are empty)
        Map<Integer, Map<String, int[]>> cells = new HashMap<>();
        src.blocks((x, y, z, b) -> {
            String s = simplify(b);
            if (s != null) {
                int key = (((x - box[0]) / c) * ny + (y - box[1]) / c) * nz + (z - box[2]) / c;
                cells.computeIfAbsent(key, k -> new HashMap<>(4)).computeIfAbsent(s, k -> new int[1])[0]++;
            }
        });
        int need = Math.max(1, c * c * c / 4);
        String[][][] g = new String[nx][ny][nz];

        for (Map.Entry<Integer, Map<String, int[]>> e : cells.entrySet()) {
            int total = 0;
            String best = null;
            int bestN = 0;

            for (Map.Entry<String, int[]> b : e.getValue().entrySet()) {
                total += b.getValue()[0];
                // a cell shows its most common block; surfaces win over what is buried under them
                if (b.getValue()[0] > bestN) {
                    bestN = b.getValue()[0];
                    best = b.getKey();
                }
            }

            if (total >= need) {
                int k = e.getKey();
                int z = k % nz;
                int y = k / nz % ny;
                int x = k / nz / ny;
                g[x][y][z] = best;
            }
        }

        // only cells that can be seen, merged into rectangles per layer
        boolean[][][] used = new boolean[nx][ny][nz];
        List<BuildPreview.Part> parts = new ArrayList<>();

        for (int y = 0; y < ny; y++) {
            for (int x = 0; x < nx; x++) {
                for (int z = 0; z < nz; z++) {
                    String b = g[x][y][z];
                    if (b != null && !used[x][y][z] && visible(g, x, y, z)) {
                        int w = 1;

                        while (x + w < nx && b.equals(g[x + w][y][z]) && !used[x + w][y][z] && visible(g, x + w, y, z)) {
                            w++;
                        }

                        int d = 1;

                        grow:
                        while (z + d < nz) {
                            for (int i = 0; i < w; i++) {
                                if (!b.equals(g[x + i][y][z + d]) || used[x + i][y][z + d] || !visible(g, x + i, y, z + d)) {
                                    break grow;
                                }
                            }

                            d++;
                        }

                        for (int i = 0; i < w; i++) {
                            for (int j = 0; j < d; j++) {
                                used[x + i][y][z + j] = true;
                            }
                        }

                        parts.add(new BuildPreview.Part(x, y, z, w, 1, d, b));
                    }
                }
            }
        }

        return new BuildPreview.Model(name, nx, ny, nz, parts);
    }

    private static boolean visible(String[][][] g, int x, int y, int z) {
        return empty(g, x + 1, y, z) || empty(g, x - 1, y, z) || empty(g, x, y + 1, z) || empty(g, x, y, z + 1) || empty(g, x, y, z - 1);
    }

    private static boolean empty(String[][][] g, int x, int y, int z) {
        return x < 0 || y < 0 || z < 0 || x >= g.length || y >= g[0].length || z >= g[0][0].length || g[x][y][z] == null;
    }

    /** The cached model of a key, built in the background the first time. */
    public static CompletableFuture<BuildPreview.Model> model(String key, String name, BuildPreview.Source src, boolean offThread) {
        BuildPreview.Model m = CACHE.get(key);
        if (m != null) {
            return CompletableFuture.completedFuture(m);
        } else if (offThread) {
            return CompletableFuture.supplyAsync(() -> {
                BuildPreview.Model built = build(name, src);
                CACHE.put(key, built);
                return built;
            });
        } else {
            BuildPreview.Model built = build(name, src);
            CACHE.put(key, built);
            return CompletableFuture.completedFuture(built);
        }
    }

    // ------------------------------------------------------------------ showing

    private static final class View {
        final List<BlockDisplay> parts = new ArrayList<>();
        final List<Vector3f> offsets = new ArrayList<>();
        BukkitTask task;
    }

    public static boolean showing(Player p) {
        return VIEWS.containsKey(p.getUniqueId());
    }

    public static void stop(Player p) {
        BuildPreview.View v = VIEWS.remove(p.getUniqueId());
        if (v != null) {
            Tasks.cancel(v.task);

            for (BlockDisplay d : v.parts) {
                d.remove();
            }
        }
    }

    /**
     * Shows a model in front of the player for {@code seconds}, about 8 blocks wide, turning slowly. Only that player
     * sees it.
     */
    public static void show(VortexPvP plugin, Player p, BuildPreview.Model m, int seconds) {
        stop(p);
        if (m.parts().isEmpty()) {
            p.sendMessage(Text.mm("<red>This build has nothing to preview."));
        } else {
            Location eye = p.getLocation();
            Vector dir = eye.getDirection().setY(0.0);
            if (dir.lengthSquared() < 1.0E-4) {
                dir = new Vector(0, 0, 1);
            }

            dir.normalize();
            Location at = eye.clone().add(dir.multiply(7.5));
            at.setYaw(0.0F);
            at.setPitch(0.0F);
            at.setY(Math.floor(eye.getY()) + 0.2);
            float k = 8.0F / Math.max(m.nx(), m.nz());
            Map<String, BlockData> data = new HashMap<>();
            BuildPreview.View v = new BuildPreview.View();
            VIEWS.put(p.getUniqueId(), v);

            for (BuildPreview.Part part : m.parts()) {
                BlockData bd = data.computeIfAbsent(part.block(), s -> {
                    try {
                        return Bukkit.createBlockData("minecraft:" + s);
                    } catch (IllegalArgumentException var2) {
                        return null;
                    }
                });
                if (bd != null) {
                    Vector3f off = new Vector3f((part.x() - m.nx() / 2.0F) * k, part.y() * k, (part.z() - m.nz() / 2.0F) * k);
                    Vector3f size = new Vector3f(part.w() * k, part.h() * k, part.d() * k);
                    BlockDisplay d = at.getWorld().spawn(at, BlockDisplay.class, e -> {
                        e.setBlock(bd);
                        e.setPersistent(false);
                        e.setVisibleByDefault(false);
                        e.setBrightness(new Brightness(15, 15));
                        e.setViewRange(1.5F);
                        e.setTransformation(new Transformation(new Vector3f(off), new Quaternionf(), size, new Quaternionf()));
                    });
                    p.showEntity(plugin, d);
                    v.parts.add(d);
                    v.offsets.add(off);
                }
            }

            Snd.play(p, "block.beacon.activate", 0.6F, 1.6F);
            p.sendMessage(
                Text.mm(
                    "<gold>Preview: <white>"
                        + Text.esc(m.name())
                        + "</white> <gray>(only you see it, "
                        + seconds
                        + "s, "
                        + v.parts.size()
                        + " pieces). Walk around it. <click:run_command:'/pvpadmin stoppreview'><red>[Stop]</red></click>"
                )
            );
            int[] t = new int[]{0};
            v.task = Tasks.timer(10L, 10L, () -> {
                t[0]++;
                if (p.isOnline() && VIEWS.get(p.getUniqueId()) == v && t[0] <= seconds * 2 && p.getWorld().equals(at.getWorld())) {
                    // turn the whole model around its centre: rotation for the box, the rotated offset as translation
                    Quaternionf q = new Quaternionf().rotateY((float)Math.toRadians(t[0] * 6.0));

                    for (int i = 0; i < v.parts.size(); i++) {
                        BlockDisplay d = v.parts.get(i);
                        if (d.isValid()) {
                            Transformation old = d.getTransformation();
                            Vector3f rot = q.transform(new Vector3f(v.offsets.get(i)));
                            d.setInterpolationDelay(0);
                            d.setInterpolationDuration(10);
                            d.setTransformation(new Transformation(rot, new Quaternionf(q), old.getScale(), new Quaternionf()));
                        }
                    }
                } else {
                    stop(p);
                }
            });
        }
    }

    /** Builds the model in the background if needed, then shows it. */
    public static void preview(VortexPvP plugin, Player p, String key, String name, BuildPreview.Source src, boolean offThread, Consumer<BuildPreview.Model> after) {
        if (!CACHE.containsKey(key)) {
            p.sendActionBar(Text.mm("<gray>Building the preview of <white>" + Text.esc(name) + "</white>..."));
        }

        model(key, name, src, offThread).whenComplete((m, err) -> Tasks.ensureSync(() -> {
            if (p.isOnline()) {
                if (err != null) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Preview of " + name + " failed", err);
                    p.sendMessage(Text.mm("<red>The preview failed - see the console."));
                } else {
                    show(plugin, p, m, 30);
                    if (after != null) {
                        after.accept(m);
                    }
                }
            }
        }));
    }
}
