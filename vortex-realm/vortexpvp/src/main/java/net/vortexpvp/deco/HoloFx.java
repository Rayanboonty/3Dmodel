package net.vortexpvp.deco;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.vortexpvp.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The 3D parts of the immersive hologram designs, shared by text holograms and leaderboard holograms: a wooden frame with
 * gold corners, a sci-fi projector with a scanning line, a royal gold trim with a spinning crown, and items orbiting the
 * card. Everything is display entities placed around a text card whose size is known; parts that sit on the card plane
 * use the card's facing (they can't turn to the viewer), the crown and orbit work with any card.
 */
public final class HoloFx {
    /** Blocks per text pixel of a text display at scale 1. */
    public static final double PX = 0.025;

    private HoloFx() {
    }

    public static enum Kind {
        NONE,
        FRAME,
        HOLO,
        ROYAL,
        ORBIT;
    }

    /** Width and height (blocks, scale 1) of a text card holding these MiniMessage lines. */
    public static double[] measure(List<String> lines, int lineWidthPx) {
        int widest = 0;
        int rows = 0;

        for (String l : lines) {
            for (String part : l.split("\n", -1)) {
                String plain = Text.plain(Text.mm(part));
                boolean bold = part.contains("<b>") || part.contains("<bold>");
                int px = 0;

                for (int i = 0; i < plain.length(); i++) {
                    char ch = plain.charAt(i);
                    px += (ch == 'i' || ch == '!' || ch == '.' || ch == ',' || ch == '\'' || ch == 'l' || ch == '|' ? 2 : (ch == ' ' ? 4 : 6)) + (bold ? 1 : 0);
                }

                int wraps = Math.max(1, (int)Math.ceil(px / (double)Math.max(40, lineWidthPx)));
                rows += wraps;
                widest = Math.max(widest, Math.min(px, lineWidthPx));
            }
        }

        return new double[]{(widest + 4) * PX, (rows * 10 + 2) * PX};
    }

    /** The parts of one design around a card: built, animated with {@link #tick}, removed with {@link #remove}. */
    public static final class Decor {
        final List<Display> parts = new ArrayList<>();
        final Kind kind;
        final Location anchor;
        final float yaw;
        final double w;
        final double h;
        final float scale;
        final int accent;
        BlockDisplay scan;
        ItemDisplay crown;
        final List<ItemDisplay> orbit = new ArrayList<>();

        Decor(Kind kind, Location anchor, float yaw, double w, double h, float scale, int accent) {
            this.kind = kind;
            this.anchor = anchor.clone();
            this.yaw = yaw;
            this.w = w;
            this.h = h;
            this.scale = scale;
            this.accent = accent;
        }

        public List<Display> parts() {
            return this.parts;
        }

        public boolean owns(Entity e) {
            return this.parts.contains(e);
        }

        public void remove() {
            for (Display d : this.parts) {
                d.remove();
            }

            this.parts.clear();
            this.orbit.clear();
        }

        /** Animations, called every 2 ticks. */
        public void tick(int t) {
            if (this.scan != null && this.scan.isValid()) {
                // the scanning line sweeps down the card and jumps back to the top
                double k = t % 60 / 60.0;
                double y = this.h * (1.0 - k);
                this.scan.setInterpolationDelay(0);
                this.scan.setInterpolationDuration(k < 0.04 ? 0 : 3);
                this.scan.setTransformation(box(-this.w / 2.0, y, 0.02, this.w, 0.02 * this.scale, 0.005));
            }

            if (this.crown != null && this.crown.isValid()) {
                Location l = this.crown.getLocation();
                l.setYaw(t * 5.0F % 360.0F);
                this.crown.teleport(l);
                if (t % 10 == 0) {
                    this.anchor.getWorld().spawnParticle(Particle.WAX_ON, l.clone().add(0.0, 0.1, 0.0), 2, 0.25, 0.15, 0.25, 0.0);
                }
            }

            int n = this.orbit.size();

            for (int i = 0; i < n; i++) {
                ItemDisplay it = this.orbit.get(i);
                if (it.isValid()) {
                    double a = t * 0.045 + Math.PI * 2.0 * i / n;
                    double r = this.w / 2.0 + 0.45 * this.scale;
                    Location l = this.anchor.clone().add(Math.cos(a) * r, this.h * 0.5 + Math.sin(a * 2.0) * 0.12, Math.sin(a) * r);
                    l.setYaw((float)Math.toDegrees(-a));
                    it.teleport(l);
                }
            }
        }
    }

    /** A box from (x, y, z) with size (sx, sy, sz) in the card's own space (x right, y up, z toward the reader). */
    static Transformation box(double x, double y, double z, double sx, double sy, double sz) {
        return new Transformation(
            new Vector3f((float)x, (float)y, (float)z),
            new Quaternionf(),
            new Vector3f((float)Math.max(0.001, sx), (float)Math.max(0.001, sy), (float)Math.max(0.001, sz)),
            new Quaternionf()
        );
    }

    /**
     * Builds a design's parts around a card standing at {@code anchor} (bottom middle of the text), {@code w} x {@code h}
     * blocks (already scaled), facing {@code yaw} (the side the text is read from).
     */
    public static Decor build(Kind kind, Location anchor, float yaw, double w, double h, float scale, int accentRgb, List<Material> orbitItems, Consumer<Entity> tag) {
        Decor d = new Decor(kind, anchor, yaw, w, h, scale, accentRgb);
        World world = anchor.getWorld();
        if (world == null) {
            return d;
        } else {
            Location at = anchor.clone();
            at.setYaw(yaw);
            at.setPitch(0.0F);
            double t = 0.06 * scale;
            switch (kind) {
                case FRAME: {
                    BlockData wood = Material.STRIPPED_DARK_OAK_LOG.createBlockData();
                    BlockData gold = Material.GOLD_BLOCK.createBlockData();
                    double x0 = -w / 2.0 - t;
                    double y0 = -t;
                    double fw = w + 2.0 * t;
                    double fh = h + 2.0 * t;
                    block(d, world, at, wood, box(x0, y0, -0.03, fw, t, t), tag);
                    block(d, world, at, wood, box(x0, h, -0.03, fw, t, t), tag);
                    block(d, world, at, wood, box(x0, 0.0, -0.03, t, h, t), tag);
                    block(d, world, at, wood, box(w / 2.0, 0.0, -0.03, t, h, t), tag);
                    double c = t * 1.6;

                    for (double[] p : new double[][]{{x0 - c * 0.2, y0 - c * 0.2}, {w / 2.0 - c * 0.2, y0 - c * 0.2}, {x0 - c * 0.2, h - c * 0.2}, {w / 2.0 - c * 0.2, h - c * 0.2}}) {
                        block(d, world, at, gold, box(p[0], p[1], -0.04, c, c, c), tag);
                    }

                    // a dark backing board, so the card reads as a framed sign from any light
                    block(d, world, at, Material.BLACK_CONCRETE.createBlockData(), box(-w / 2.0, 0.0, -0.06, w, h, 0.01), tag);
                    for (double side : new double[]{-1.0, 1.0}) {
                        ItemDisplay lantern = item(world, at.clone(), new ItemStack(Material.LANTERN), 0.35F * scale, tag);
                        lantern.setTransformation(new Transformation(new Vector3f((float)(side * (w / 2.0 + t * 2.5)), (float)(-0.05 * scale), 0.0F), new Quaternionf(), new Vector3f(0.35F * scale), new Quaternionf()));
                        d.parts.add(lantern);
                    }
                    break;
                }
                case HOLO: {
                    // projector: a glowing base under the card and a faint beam up to it
                    double drop = 1.2;

                    for (int dy = 1; dy <= 6; dy++) {
                        if (at.clone().add(0.0, -dy, 0.0).getBlock().getType().isSolid()) {
                            drop = at.getY() - (Math.floor(at.getY()) - dy + 1.0);
                            break;
                        }
                    }

                    block(d, world, at, Material.SEA_LANTERN.createBlockData(), box(-0.18 * scale, -drop, -0.18 * scale, 0.36 * scale, 0.12 * scale, 0.36 * scale), tag);
                    block(d, world, at, Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), box(-0.05 * scale, -drop + 0.12 * scale, -0.05 * scale, 0.1 * scale, drop - 0.12 * scale, 0.1 * scale), tag);
                    // thin cyan edges along the card and a scanning line
                    BlockData cyan = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
                    block(d, world, at, cyan, box(-w / 2.0, -0.02, -0.02, w, 0.02, 0.01), tag);
                    block(d, world, at, cyan, box(-w / 2.0, h, -0.02, w, 0.02, 0.01), tag);
                    d.scan = block(d, world, at, Material.CYAN_STAINED_GLASS.createBlockData(), box(-w / 2.0, h, 0.02, w, 0.02 * scale, 0.005), tag);
                    break;
                }
                case ROYAL: {
                    BlockData gold = Material.GOLD_BLOCK.createBlockData();
                    block(d, world, at, gold, box(-w / 2.0 - t, h, -0.03, w + 2.0 * t, t * 0.8, t), tag);
                    block(d, world, at, gold, box(-w / 2.0 - t, -t * 0.8, -0.03, w + 2.0 * t, t * 0.8, t), tag);
                    block(d, world, at, Material.PURPLE_CONCRETE.createBlockData(), box(-w / 2.0, 0.0, -0.06, w, h, 0.01), tag);
                    d.crown = item(world, at.clone().add(0.0, h + 0.35 * scale, 0.0), new ItemStack(Material.GOLDEN_HELMET), 0.55F * scale, tag);
                    d.parts.add(d.crown);
                    break;
                }
                case ORBIT: {
                    List<Material> items = orbitItems == null || orbitItems.isEmpty()
                        ? List.of(Material.DIAMOND_SWORD, Material.MACE, Material.END_CRYSTAL, Material.TOTEM_OF_UNDYING)
                        : orbitItems;

                    for (Material m : items.subList(0, Math.min(8, items.size()))) {
                        ItemDisplay it = item(world, at.clone().add(0.0, h * 0.5, 0.0), new ItemStack(m), 0.45F * scale, tag);
                        it.setTeleportDuration(3);
                        d.orbit.add(it);
                        d.parts.add(it);
                    }
                }
                case NONE:
            }

            return d;
        }
    }

    private static BlockDisplay block(Decor d, World w, Location at, BlockData data, Transformation tr, Consumer<Entity> tag) {
        BlockDisplay b = w.spawn(at, BlockDisplay.class, e -> {
            e.setPersistent(false);
            e.setBlock(data);
            e.setBrightness(new Brightness(15, 15));
            e.setViewRange(0.6F);
            e.setTransformation(tr);
            e.setRotation(at.getYaw(), 0.0F);
            tag.accept(e);
        });
        d.parts.add(b);
        return b;
    }

    private static ItemDisplay item(World w, Location at, ItemStack stack, float size, Consumer<Entity> tag) {
        return w.spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(stack);
            e.setItemDisplayTransform(ItemDisplayTransform.FIXED);
            e.setBillboard(Billboard.FIXED);
            e.setBrightness(new Brightness(15, 15));
            e.setViewRange(0.6F);
            e.setTeleportDuration(3);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, size), new Quaternionf()));
            tag.accept(e);
        });
    }

    /**
     * True when every player near {@code l} has the chunks around it on their client. A display spawned in a chunk a
     * client hasn't received yet is not sent to that client until the player moves on; holograms and NPCs wait for this.
     */
    public static boolean clientsReady(Location l) {
        if (l == null || l.getWorld() == null) {
            return false;
        } else {
            int cx = l.getBlockX() >> 4;
            int cz = l.getBlockZ() >> 4;

            for (org.bukkit.entity.Player p : l.getWorld().getPlayers()) {
                // only players whose view reaches these chunks are waited for
                int reach = Math.max(2, Math.min(p.getSendViewDistance(), p.getClientViewDistance()) - 1);
                Location pl = p.getLocation();
                // the display's own chunk (parts all stand on the anchor; the podium's side steps are a block or two off)
                boolean inView = Math.abs((pl.getBlockX() >> 4) - cx) < reach && Math.abs((pl.getBlockZ() >> 4) - cz) < reach;
                if (inView && !p.isChunkSent(org.bukkit.Chunk.getChunkKey(cx, cz))) {
                    return false;
                }
            }

            return true;
        }
    }

    /** Card background for a design (ARGB). */
    public static Color background(Kind kind, int accentRgb, int alpha) {
        return switch (kind) {
            case FRAME -> Color.fromARGB(alpha < 0 ? 200 : alpha, 24, 16, 10);
            case HOLO -> Color.fromARGB(alpha < 0 ? 70 : alpha, 10, 70, 90);
            case ROYAL -> Color.fromARGB(alpha < 0 ? 190 : alpha, 34, 12, 48);
            default -> Color.fromARGB(alpha < 0 ? 160 : alpha, accentRgb >> 16 & 0xFF, accentRgb >> 8 & 0xFF, accentRgb & 0xFF);
        };
    }

    /** A text card (no shadow on a background, fixed or facing the viewer). */
    public static TextDisplay card(Location at, float yaw, boolean fixed, float scale, Color bg, int lineWidth, Consumer<Entity> tag) {
        Location l = at.clone();
        l.setYaw(yaw);
        l.setPitch(0.0F);
        return at.getWorld().spawn(l, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(fixed ? Billboard.FIXED : Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setLineWidth(lineWidth);
            t.setShadowed(bg == null || bg.getAlpha() < 40);
            t.setDefaultBackground(false);
            t.setBackgroundColor(bg == null ? Color.fromARGB(0, 0, 0, 0) : bg);
            t.setBrightness(new Brightness(15, 15));
            t.setViewRange(0.6F);
            t.setTeleportDuration(3);
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            tag.accept(t);
        });
    }

    /** The marquee: a window of the title that moves one character every step, like a stadium screen. */
    public static String marquee(String title, int t, int window) {
        String plain = Text.plain(Text.mm(title));
        if (plain.length() <= window) {
            return title;
        } else {
            String loop = plain + "   •   ";
            int start = t / 4 % loop.length();
            String doubled = loop + loop;
            return Text.esc(doubled.substring(start, start + window));
        }
    }
}
