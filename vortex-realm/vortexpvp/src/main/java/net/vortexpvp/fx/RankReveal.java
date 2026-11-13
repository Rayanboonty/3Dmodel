package net.vortexpvp.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RankReveal {
    private static final int[][] FONT = new int[128][];
    private static final int ROWS = 14;
    private static final int COLS = 10;
    private static final float P = 0.15F;

    private RankReveal() {
    }

    private static boolean px(int[] g, int r, int c) {
        return g != null && r >= 0 && r < 7 && c >= 0 && c < 5 && (g[r] >> 4 - c & 1) == 1;
    }

    static boolean[][] glyph(char ch) {
        int[] g = ch < 128 ? FONT[ch] : null;
        boolean[][] out = new boolean[14][10];

        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 5; c++) {
                boolean p = px(g, r, c);
                boolean a = px(g, r - 1, c);
                boolean b = px(g, r, c + 1);
                boolean cc = px(g, r, c - 1);
                boolean d = px(g, r + 1, c);
                boolean p1 = p;
                boolean p2 = p;
                boolean p3 = p;
                boolean p4 = p;
                if (cc == a && cc != d && a != b) {
                    p1 = a;
                }

                if (a == b && a != cc && b != d) {
                    p2 = b;
                }

                if (d == cc && d != b && cc != a) {
                    p3 = cc;
                }

                if (b == d && b != a && d != cc) {
                    p4 = d;
                }

                out[r * 2][c * 2] = p1;
                out[r * 2][c * 2 + 1] = p2;
                out[r * 2 + 1][c * 2] = p3;
                out[r * 2 + 1][c * 2 + 1] = p4;
            }
        }

        return out;
    }

    static int lerp(int a, int b, double f) {
        int r = (int)Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * f);
        int g = (int)Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * f);
        int bl = (int)Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return Math.max(0, Math.min(255, r)) << 16 | Math.max(0, Math.min(255, g)) << 8 | Math.max(0, Math.min(255, bl));
    }

    private static List<RankReveal.Quad> layout(String label, int color) {
        int top = lerp(color, 16777215, 0.55);
        int bottom = lerp(color, 0, 0.18);
        int chars = label.length();
        int totalCols = chars * 10 + (chars - 1) * 2;
        boolean[][] grid = new boolean[14][totalCols];

        for (int i = 0; i < chars; i++) {
            boolean[][] g = glyph(label.charAt(i));

            for (int r = 0; r < 14; r++) {
                System.arraycopy(g[r], 0, grid[r], i * 12, 10);
            }
        }

        List<RankReveal.Quad> out = new ArrayList<>();

        for (int r = 0; r < 14; r++) {
            int rgb = lerp(top, bottom, r / 13.0);
            int c = 0;

            while (c < totalCols) {
                if (!grid[r][c]) {
                    c++;
                } else {
                    int start = c;

                    while (c < totalCols && grid[r][c]) {
                        c++;
                    }

                    float w = (c - start) * 0.15F;
                    float cx = ((start + c) / 2.0F - totalCols / 2.0F) * 0.15F;
                    float cy = (13 - r + 0.5F) * 0.15F;
                    out.add(new RankReveal.Quad(r, cx, cy, w, 0.15F, rgb));
                }
            }
        }

        return out;
    }

    private static Transformation shape(RankReveal.Quad q, float dy, float widthMul, float extraScale) {
        float w = q.w * widthMul * extraScale;
        float h = q.h * extraScale;
        Vector3f scale = new Vector3f(8.0F * w * 1.02F, 4.0F * h * 1.04F, 1.0F);
        Vector3f tr = new Vector3f(q.cx * extraScale - 0.1F * w, q.cy * extraScale - h / 2.0F + dy, 0.0F);
        return new Transformation(tr, new Quaternionf(), scale, new Quaternionf());
    }

    private static TextDisplay spawn(World w, Location at, Transformation tf, int argb) {
        try {
            return w.spawn(at, TextDisplay.class, d -> {
                d.text(Component.text(" "));
                d.setBillboard(Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(argb));
                d.setDefaultBackground(false);
                d.setShadowed(false);
                d.setSeeThrough(false);
                d.setBrightness(new Brightness(15, 15));
                d.setTransformation(tf);
                d.setInterpolationDuration(2);
                d.setPersistent(false);
                d.setViewRange(1.5F);
            });
        } catch (RuntimeException var5) {
            return null;
        }
    }

    private static void apply(RankReveal.Quad q, int alpha, float dy, float widthMul, int rgb) {
        TextDisplay d = q.d;
        if (d != null && d.isValid()) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(2);
            d.setTransformation(shape(q, dy, widthMul, 1.0F));
            d.setBackgroundColor(Color.fromARGB(Math.max(0, Math.min(255, alpha)), rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF));
        }
    }

    private static void color(RankReveal.Quad q, int alpha, int rgb) {
        TextDisplay d = q.d;
        int argb = Math.max(0, Math.min(255, alpha)) << 24 | rgb & 16777215;
        if (d != null && d.isValid() && argb != q.lastArgb) {
            q.lastArgb = argb;
            d.setBackgroundColor(Color.fromARGB(argb));
        }
    }

    private static double ease(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return 1.0 - Math.pow(1.0 - t, 3.0);
    }

    public static int play(VortexPvP plugin, Player p, Tier from, Tier to, Vector facing) {
        World w = p.getWorld();
        Vector dir = facing == null ? p.getEyeLocation().getDirection() : facing.clone();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }

        dir.normalize();
        Vector fdir = dir.clone();
        Location base = p.getLocation().add(dir.clone().multiply(7.0)).add(0.0, 0.9, 0.0);
        // turn the camera smoothly toward the reveal instead of snapping
        RankDrop.turn(p, base.clone().add(0.0, 1.0, 0.0), 12);
        int newColor = Integer.parseInt(to.color.substring(1), 16);
        List<RankReveal.Quad> oldQ = from == null ? List.of() : layout(from.label, Integer.parseInt(from.color.substring(1), 16));
        List<RankReveal.Quad> newQ = layout(to.label, newColor);
        RankReveal.Quad sweep = new RankReveal.Quad(0, 0.0F, 0.0F, (to.label.length() * 10 + (to.label.length() - 1) * 2 + 4) * 0.15F, 0.0825F, 16777215);
        TextDisplay[] subtitle = new TextDisplay[1];
        int off = from == null ? -26 : 0;
        int oldOut = 14;
        int newIn = 34 + off;
        int impact = 60 + off;
        int fadeOut = 122 + off;
        int end = 138 + off;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] t = new int[]{0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Tasks.timer(
            0L,
            1L,
            () -> {
                int now = (int)(t[0]++);

                try {
                    if (now == 0 && !oldQ.isEmpty()) {
                        for (RankReveal.Quad q : oldQ) {
                            q.d = spawn(w, base, shape(q, 0.0F, 1.0F, 1.0F), 0 | q.rgb);
                        }

                        Snd.at(base, "block.beacon.ambient", 1.0F, 1.5F);
                    }

                    if (!oldQ.isEmpty() && now <= oldOut + 14 + 8) {
                        for (RankReveal.Quad q : oldQ) {
                            if (now < 8) {
                                apply(q, (int)(ease((double)now / 7.0) * 235.0), 0.0F, 1.0F, q.rgb);
                            } else {
                                double f = (now - oldOut - q.row * 0.9) / 7.0;
                                if (f > 0.0 && f <= 1.15) {
                                    double e = ease(f);
                                    apply(q, (int)((1.0 - e) * 235.0), (float)(-0.35 * e), (float)(1.0 - 0.35 * e), lerp(q.rgb, 16777215, e * 0.6));
                                }
                            }
                        }

                        if (now == oldOut) {
                            Snd.at(base, "block.amethyst_block.resonate", 1.0F, 0.8F);
                        }
                    }

                    if (now == oldOut + 14 + 9) {
                        for (RankReveal.Quad qx : oldQ) {
                            if (qx.d != null) {
                                qx.d.remove();
                            }
                        }
                    }

                    if (now == newIn - 2) {
                        for (RankReveal.Quad qxx : newQ) {
                            qxx.d = spawn(w, base, shape(qxx, 0.4F, 1.0F, 1.0F), qxx.rgb);
                        }

                        sweep.d = spawn(w, base, shape(sweep, 2.025F, 1.0F, 1.0F), 16777215);
                        Snd.at(base, "block.beacon.activate", 1.0F, 1.6F);
                    }

                    if (now >= newIn && now < impact + 2) {
                        double front = (now - newIn) * 0.72;

                        for (RankReveal.Quad qxx : newQ) {
                            double f = (front - qxx.row) / 6.0;
                            if (f > 0.0 && f <= 1.1) {
                                double e = ease(f);
                                apply(qxx, (int)(e * 255.0), (float)(0.4 * (1.0 - e)), 1.0F, lerp(16777215, qxx.rgb, e));
                            }
                        }

                        double sweepRow = Math.min(14.0, front);
                        int sa = front < 17.0 ? (int)(210.0 * Math.min(1.0, Math.min(front / 2.0, (17.0 - front) / 3.0))) : 0;
                        if (sweep.d != null && sweep.d.isValid()) {
                            sweep.d.setInterpolationDelay(0);
                            sweep.d.setInterpolationDuration(2);
                            sweep.d.setTransformation(shape(sweep, (float)((14.0 - sweepRow) * 0.15F - 0.075F), 1.0F, 1.0F));
                            sweep.d.setBackgroundColor(Color.fromARGB(Math.max(0, sa), 255, 255, 255));
                        }

                        if (now % 2 == 0) {
                            Snd.at(base, "block.note_block.chime", 0.25F, 0.8F + (float)(front / 14.0));
                        }
                    }

                    if (now == impact) {
                        if (sweep.d != null) {
                            sweep.d.remove();
                        }

                        Location mid = base.clone().add(0.0, 1.0500001F, 0.0);
                        Color c = Color.fromRGB(newColor);
                        w.spawnParticle(Particle.FIREWORK, mid, 90, 2.4, 1.0, 0.4, 0.1);
                        w.spawnParticle(Particle.END_ROD, mid, 40, 2.6, 1.1, 0.4, 0.05);
                        w.spawnParticle(Particle.DUST, mid, 80, 2.8, 1.2, 0.5, 0.0, new DustOptions(c, 1.6F));
                        Snd.at(base, "entity.firework_rocket.twinkle", 1.0F, 1.0F);
                        Snd.at(base, "entity.player.levelup", 1.0F, 1.2F);
                        if (p.isOnline()) {
                            Snd.play(p, "ui.toast.challenge_complete", 1.0F, 1.0F);
                            Fx.shine(p, "RANK UP", Fx.GOLD, Text.mm("<" + to.color + "><b>" + to.longName()), 60);
                        }

                        subtitle[0] = w.spawn(
                            base,
                            TextDisplay.class,
                            d -> {
                                d.text(Text.mm("<b><gradient:#FFFFFF:" + to.color + ">" + to.longName().toUpperCase() + "</gradient></b>"));
                                d.setBillboard(Billboard.CENTER);
                                d.setDefaultBackground(false);
                                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                                d.setShadowed(true);
                                d.setTextOpacity((byte)5);
                                d.setBrightness(new Brightness(15, 15));
                                d.setTransformation(
                                    new Transformation(new Vector3f(0.0F, -0.75F, 0.0F), new Quaternionf(), new Vector3f(2.2F, 2.2F, 2.2F), new Quaternionf())
                                );
                                d.setPersistent(false);
                                d.setViewRange(1.5F);
                            }
                        );
                    }

                    if (now > impact && now <= impact + 10 && subtitle[0] != null && subtitle[0].isValid()) {
                        int a = 5 + (int)(ease((now - impact) / 10.0) * 250.0);
                        subtitle[0].setTextOpacity((byte)a);
                    }

                    if (now > impact + 4 && now < fadeOut) {
                        double band = (now - impact - 4) % 22 * 0.9 - 2.0;

                        for (RankReveal.Quad qxxx : newQ) {
                            double dist = Math.abs(qxxx.row - band);
                            double glow = dist < 2.5 ? (1.0 - dist / 2.5) * 0.65 : 0.0;
                            color(qxxx, 255, lerp(qxxx.rgb, 16777215, Math.round(glow * 8.0) / 8.0));
                        }

                        if (now % 6 == 0) {
                            RankReveal.Quad qxxx = newQ.get(rnd.nextInt(newQ.size()));
                            Vector right = new Vector(-fdir.getZ(), 0.0, fdir.getX());
                            Location sparkle = base.clone().add(right.multiply(qxxx.cx)).add(0.0, qxxx.cy, 0.0);
                            w.spawnParticle(Particle.END_ROD, sparkle, 1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }

                    if (now >= fadeOut && now < end) {
                        for (RankReveal.Quad qxxx : newQ) {
                            double f = (now - fadeOut - qxxx.row * 0.5) / 7.0;
                            if (f > 0.0) {
                                double e = ease(f);
                                apply(qxxx, (int)((1.0 - e) * 255.0), (float)(0.45 * e), 1.0F, lerp(qxxx.rgb, 16777215, e * 0.4));
                            }
                        }

                        if (subtitle[0] != null && subtitle[0].isValid()) {
                            int a = 5 + (int)((1.0 - ease((now - fadeOut) / 10.0)) * 250.0);
                            subtitle[0].setTextOpacity((byte)a);
                        }
                    }

                    if (now >= end) {
                        cleanup(oldQ, newQ, sweep, subtitle[0]);
                        Tasks.cancel(task[0]);
                    }
                } catch (RuntimeException var27) {
                    cleanup(oldQ, newQ, sweep, subtitle[0]);
                    Tasks.cancel(task[0]);
                }
            }
        );
        return end;
    }

    private static void cleanup(List<RankReveal.Quad> a, List<RankReveal.Quad> b, RankReveal.Quad sweep, TextDisplay sub) {
        for (RankReveal.Quad q : a) {
            if (q.d != null && q.d.isValid()) {
                q.d.remove();
            }
        }

        for (RankReveal.Quad qx : b) {
            if (qx.d != null && qx.d.isValid()) {
                qx.d.remove();
            }
        }

        if (sweep.d != null && sweep.d.isValid()) {
            sweep.d.remove();
        }

        if (sub != null && sub.isValid()) {
            sub.remove();
        }
    }

    static {
        FONT[76] = new int[]{16, 16, 16, 16, 16, 16, 31};
        FONT[77] = new int[]{17, 27, 21, 21, 17, 17, 17};
        FONT[72] = new int[]{17, 17, 17, 31, 17, 17, 17};
        FONT[84] = new int[]{31, 4, 4, 4, 4, 4, 4};
        FONT[49] = new int[]{4, 12, 20, 4, 4, 4, 31};
        FONT[50] = new int[]{14, 17, 1, 6, 8, 16, 31};
        FONT[51] = new int[]{14, 17, 1, 6, 1, 17, 14};
        FONT[52] = new int[]{3, 5, 9, 17, 31, 1, 1};
        FONT[53] = new int[]{31, 16, 30, 1, 1, 17, 14};
    }

    private static final class Quad {
        TextDisplay d;
        final int row;
        final float cx;
        final float cy;
        final float w;
        final float h;
        final int rgb;
        int lastArgb = Integer.MIN_VALUE;

        Quad(int row, float cx, float cy, float w, float h, int rgb) {
            this.row = row;
            this.cx = cx;
            this.cy = cy;
            this.w = w;
            this.h = h;
            this.rgb = rgb;
        }
    }
}
