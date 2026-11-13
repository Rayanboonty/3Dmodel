package net.vortexrealm.deco;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class ImageWalls implements TabExecutor {
    private static final long MAX_BYTES = 12582912L;
    private final VortexRealm plugin;
    private final File dir;
    private final File file;
    private final NamespacedKey key;
    private final Map<String, ImageWalls.Wall> walls = new LinkedHashMap<>();
    private static Color[] palette;

    public ImageWalls(VortexRealm plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "images");
        this.file = new File(plugin.getDataFolder(), "wall-images.yml");
        this.key = new NamespacedKey(plugin, "wall_image");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection s = y.getConfigurationSection("walls");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection d = s.getConfigurationSection(id);
                if (d != null) {
                    ImageWalls.Wall w = new ImageWalls.Wall(
                        id,
                        d.getString("world"),
                        d.getInt("x"),
                        d.getInt("y"),
                        d.getInt("z"),
                        d.getString("face", "NORTH"),
                        d.getInt("w"),
                        d.getInt("h"),
                        d.getIntegerList("maps")
                    );
                    this.walls.put(id, w);
                    File img = new File(this.dir, id + ".png");
                    if (img.isFile()) {
                        try {
                            BufferedImage full = ImageIO.read(img);
                            this.attach(w, full);
                        } catch (RuntimeException | IOException var9) {
                            this.plugin.getLogger().log(Level.WARNING, "Wall image " + id + " could not be redrawn", (Throwable)var9);
                        }
                    }
                }
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Pictures on walls (/wallimage). The images themselves are in images/."));

        for (ImageWalls.Wall w : this.walls.values()) {
            String b = "walls." + w.id() + ".";
            y.set(b + "world", w.world());
            y.set(b + "x", w.x());
            y.set(b + "y", w.y());
            y.set(b + "z", w.z());
            y.set(b + "face", w.face());
            y.set(b + "w", w.w());
            y.set(b + "h", w.h());
            y.set(b + "maps", w.maps());
        }

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save wall-images.yml", (Throwable)var5);
        }
    }

    private static synchronized Color[] palette() {
        if (palette == null) {
            List<Color> list = new ArrayList<>();

            for (int i = 0; i < 256; i++) {
                try {
                    list.add(MapPalette.getColor((byte)i));
                } catch (RuntimeException var3) {
                    break;
                }
            }

            palette = list.toArray(new Color[0]);
        }

        return palette;
    }

    private static byte[] dither(BufferedImage tile) {
        Color[] pal = palette();
        int n = 16384;
        float[] r = new float[n];
        float[] g = new float[n];
        float[] b = new float[n];
        boolean[] clear = new boolean[n];

        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int p = tile.getRGB(x, y);
                int i = y * 128 + x;
                clear[i] = p >>> 24 < 100;
                r[i] = p >> 16 & 0xFF;
                g[i] = p >> 8 & 0xFF;
                b[i] = p & 0xFF;
            }
        }

        byte[] out = new byte[n];

        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int i = y * 128 + x;
                if (clear[i]) {
                    out[i] = 0;
                } else {
                    int best = 4;
                    double bd = Double.MAX_VALUE;

                    for (int c = 4; c < pal.length; c++) {
                        Color pc = pal[c];
                        if (pc.getAlpha() >= 255) {
                            double dr = r[i] - pc.getRed();
                            double dg = g[i] - pc.getGreen();
                            double db = b[i] - pc.getBlue();
                            double d = dr * dr * 0.3 + dg * dg * 0.59 + db * db * 0.11;
                            if (d < bd) {
                                bd = d;
                                best = c;
                            }
                        }
                    }

                    out[i] = (byte)best;
                    float er = r[i] - pal[best].getRed();
                    float eg = g[i] - pal[best].getGreen();
                    float eb = b[i] - pal[best].getBlue();
                    spread(r, g, b, clear, x + 1, y, er, eg, eb, 0.4375F);
                    spread(r, g, b, clear, x - 1, y + 1, er, eg, eb, 0.1875F);
                    spread(r, g, b, clear, x, y + 1, er, eg, eb, 0.3125F);
                    spread(r, g, b, clear, x + 1, y + 1, er, eg, eb, 0.0625F);
                }
            }
        }

        return out;
    }

    private static void spread(float[] r, float[] g, float[] b, boolean[] clear, int x, int y, float er, float eg, float eb, float f) {
        if (x >= 0 && x < 128 && y < 128) {
            int i = y * 128 + x;
            if (!clear[i]) {
                r[i] += er * f;
                g[i] += eg * f;
                b[i] += eb * f;
            }
        }
    }

    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, 2);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private void attach(ImageWalls.Wall w, BufferedImage full) {
        for (int ty = 0; ty < w.h(); ty++) {
            for (int tx = 0; tx < w.w(); tx++) {
                int idx = ty * w.w() + tx;
                if (idx >= w.maps().size()) {
                    return;
                }

                MapView view = Bukkit.getMap(w.maps().get(idx));
                if (view != null) {
                    BufferedImage tile = full.getSubimage(tx * 128, ty * 128, 128, 128);

                    for (MapRenderer r : new ArrayList<>(view.getRenderers())) {
                        view.removeRenderer(r);
                    }

                    view.setTrackingPosition(false);
                    view.setUnlimitedTracking(false);
                    view.setLocked(true);
                    view.addRenderer(new ImageWalls.TileRenderer(dither(tile)));
                }
            }
        }
    }

    private void put(Player p, BufferedImage src, int widthBlocks) {
        RayTraceResult hit = p.rayTraceBlocks(10.0);
        if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
            BlockFace face = hit.getHitBlockFace();
            if (face != BlockFace.UP && face != BlockFace.DOWN) {
                int w = Math.max(1, Math.min(16, widthBlocks));
                int h = Math.max(1, Math.min(16, (int)Math.round((double)(w * src.getHeight()) / src.getWidth())));
                BufferedImage full = scale(src, w * 128, h * 128);
                Block wall = hit.getHitBlock();
                Vector out = face.getDirection();
                Vector right = out.clone().multiply(-1).crossProduct(new Vector(0, 1, 0));
                String id = "img" + Long.toString(System.currentTimeMillis() % 100000000L, 36);
                List<Integer> maps = new ArrayList<>();
                int placed = 0;
                World world = wall.getWorld();

                for (int ty = 0; ty < h; ty++) {
                    for (int tx = 0; tx < w; tx++) {
                        Block base = wall.getLocation().add(right.clone().multiply(tx)).add(0.0, -ty, 0.0).getBlock();
                        Block spot = base.getRelative(face);
                        MapView view = Bukkit.createMap(world);
                        maps.add(view.getId());
                        ItemStack item = new ItemStack(Material.FILLED_MAP);
                        item.editMeta(MapMeta.class, mm -> mm.setMapView(view));
                        if (base.getType().isSolid() && spot.getType().isAir()) {
                            try {
                                Location at = spot.getLocation();
                                world.spawn(at, GlowItemFrame.class, f -> {
                                    f.setFacingDirection(face, true);
                                    f.setItem(item, false);
                                    f.setRotation(Rotation.NONE);
                                    f.setVisible(false);
                                    f.setFixed(true);
                                    f.setInvulnerable(true);
                                    f.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, id);
                                });
                                placed++;
                            } catch (RuntimeException var23) {
                            }
                        }
                    }
                }

                ImageWalls.Wall wl = new ImageWalls.Wall(id, world.getName(), wall.getX(), wall.getY(), wall.getZ(), face.name(), w, h, maps);
                this.walls.put(id, wl);
                this.attach(wl, full);

                try {
                    if (this.dir.isDirectory() || this.dir.mkdirs()) {
                        ImageIO.write(full, "png", new File(this.dir, id + ".png"));
                    }
                } catch (IOException var24) {
                    this.plugin.getLogger().log(Level.WARNING, "Could not save wall image " + id, (Throwable)var24);
                }

                this.save();
                p.sendMessage(
                    Text.mm(
                        "<green>Picture placed: <white>"
                            + w
                            + "x"
                            + h
                            + "</white> blocks ("
                            + placed
                            + " frames). <gray>Remove it: look at it and /wallimage remove"
                    )
                );
            } else {
                p.sendMessage(Text.mm("<red>Walls only - look at the side of a block."));
            }
        } else {
            p.sendMessage(Text.mm("<red>Look at a wall (within 10 blocks)."));
        }
    }

    private void remove(Player p) {
        RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 10.0, ex -> ex instanceof ItemFrame);
        String id = null;
        if (hit != null && hit.getHitEntity() != null) {
            id = hit.getHitEntity().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        }

        if (id == null) {
            p.sendMessage(Text.mm("<red>Look at a picture placed with /wallimage."));
        } else {
            int n = 0;

            for (Entity e : p.getWorld().getEntities()) {
                if (e instanceof ItemFrame && id.equals(e.getPersistentDataContainer().get(this.key, PersistentDataType.STRING))) {
                    e.remove();
                    n++;
                }
            }

            this.walls.remove(id);
            File img = new File(this.dir, id + ".png");
            if (img.isFile() && !img.delete()) {
                img.deleteOnExit();
            }

            this.save();
            p.sendMessage(Text.mm("<green>Removed the picture (" + n + " frames)."));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p && p.hasPermission("vortex.admin"))) {
            sender.sendMessage(Text.mm("<red>Admins only, in game."));
            return true;
        } else if (args.length == 0) {
            p.sendMessage(
                Text.mm(
                    "<gold>/wallimage <link> [width]</gold> <gray>- a picture (png/jpg link) on the wall you look at, width in blocks (default 3)\n<gold>/wallimage file:<name.png> [width]</gold> <gray>- a file from plugins/VortexRealm/images\n<gold>/wallimage remove</gold> <gray>- take down the picture you look at"
                )
            );
            return true;
        } else if (args[0].equalsIgnoreCase("remove")) {
            this.remove(p);
            return true;
        } else {
            int width = 3;
            if (args.length > 1) {
                try {
                    width = Integer.parseInt(args[1]);
                } catch (NumberFormatException var10) {
                    p.sendMessage(Text.mm("<red>Width must be a number of blocks (1-16)."));
                    return true;
                }
            }

            int w = width;
            String src = args[0];
            UUID who = p.getUniqueId();
            p.sendMessage(Text.mm("<gray>Loading the picture..."));
            Tasks.async(() -> {
                String error = null;

                BufferedImage img;
                try {
                    img = this.read(src);
                    if (img == null) {
                        error = "That isn't a picture I can read (png, jpg, gif, bmp).";
                    }
                } catch (InterruptedException | IllegalArgumentException | IOException var8x) {
                    img = null;
                    error = "Couldn't load it: " + var8x.getMessage();
                }

                BufferedImage fimg = img;
                String ferr = error;
                Tasks.sync(() -> {
                    Player pl = Bukkit.getPlayer(who);
                    if (pl != null) {
                        if (fimg == null) {
                            pl.sendMessage(Text.mm("<red>" + Text.esc(ferr)));
                        } else {
                            this.put(pl, fimg, w);
                        }
                    }
                });
            });
            return true;
        }
    }

    private BufferedImage read(String src) throws IOException, InterruptedException {
        if (src.toLowerCase(Locale.ROOT).startsWith("file:")) {
            File f = new File(this.dir, src.substring(5)).getCanonicalFile();
            if (f.getPath().startsWith(this.dir.getCanonicalPath()) && f.isFile()) {
                return ImageIO.read(f);
            } else {
                throw new IOException("no such file in plugins/VortexRealm/images");
            }
        } else {
            URI uri = URI.create(src);
            if (uri.getScheme() != null && (uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(Redirect.NORMAL).build();
                HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20L)).header("User-Agent", "VortexRealm-WallImage").GET().build();
                HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());
                if (res.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + res.statusCode());
                } else {
                    byte[] data;
                    try (InputStream in = res.body()) {
                        data = in.readNBytes(12582913);
                    }

                    if (data.length > 12582912L) {
                        throw new IOException("the picture is bigger than 12 MB");
                    } else {
                        return ImageIO.read(new ByteArrayInputStream(data));
                    }
                }
            } else {
                throw new IllegalArgumentException("use an http(s) link");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return args.length == 2 ? List.of("2", "3", "4", "6", "8") : List.of();
        } else {
            List<String> out = new ArrayList<>(List.of("remove", "https://"));
            File[] files = this.dir.listFiles((d, n) -> n.matches("(?i).+\\.(png|jpe?g|gif|bmp)") && !n.startsWith("img"));
            if (files != null) {
                for (File f : files) {
                    out.add("file:" + f.getName());
                }
            }

            return out;
        }
    }

    private static final class TileRenderer extends MapRenderer {
        private final byte[] pixels;
        private boolean drawn;

        TileRenderer(byte[] pixels) {
            super(false);
            this.pixels = pixels;
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            if (!this.drawn) {
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        canvas.setPixel(x, y, this.pixels[y * 128 + x]);
                    }
                }

                this.drawn = true;
            }
        }
    }

    private record Wall(String id, String world, int x, int y, int z, String face, int w, int h, List<Integer> maps) {
    }
}
