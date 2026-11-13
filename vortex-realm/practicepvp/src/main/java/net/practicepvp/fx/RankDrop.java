package net.practicepvp.fx;

import io.papermc.paper.entity.LookAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.rank.Tier;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RankDrop {
    private static final int ROWS = 14;
    private static final int COLS = 10;
    private static final float P = 0.22F;
    private static final float HEIGHT = 3.08F;
    private static final double DISTANCE = 8.5;

    private RankDrop() {
    }

    private static List<RankDrop.Quad> layout(String label, int color) {
        int top = RankReveal.lerp(color, 16777215, 0.5);
        int bottom = RankReveal.lerp(color, 0, 0.2);
        int chars = label.length();
        int totalCols = chars * 10 + (chars - 1) * 2;
        boolean[][] grid = new boolean[14][totalCols];

        for (int i = 0; i < chars; i++) {
            boolean[][] g = RankReveal.glyph(label.charAt(i));

            for (int r = 0; r < 14; r++) {
                System.arraycopy(g[r], 0, grid[r], i * 12, 10);
            }
        }

        List<RankDrop.Quad> front = new ArrayList<>();
        List<RankDrop.Quad> back = new ArrayList<>();

        for (int r = 0; r < 14; r++) {
            int rgb = RankReveal.lerp(top, bottom, r / 13.0);
            int c = 0;

            while (c < totalCols) {
                if (!grid[r][c]) {
                    c++;
                } else {
                    int start = c;

                    while (c < totalCols && grid[r][c]) {
                        c++;
                    }

                    float w = (c - start) * 0.22F;
                    float cx = ((start + c) / 2.0F - totalCols / 2.0F) * 0.22F;
                    float cy = (13 - r + 0.5F) * 0.22F;
                    front.add(new RankDrop.Quad(r, cx, cy, w, 0.22F, rgb, false));
                    back.add(new RankDrop.Quad(r, cx, cy, w, 0.22F, RankReveal.lerp(color, 0, 0.62), true));
                }
            }
        }

        back.addAll(front);
        return back;
    }

    private static float width(String label) {
        return (label.length() * 10 + (label.length() - 1) * 2) * 0.22F;
    }

    private static Transformation shape(RankDrop.Quad q, RankDrop.Pose pose) {
        double grow = Math.max(0.0, Math.min(1.2, pose.grow[q.row]));
        double h = q.h * pose.sy;
        double centre = q.cy * pose.sy + pose.dy;
        double bottom = centre - h / 2.0;
        double top = centre + h / 2.0;
        if (bottom < 0.0) {
            bottom = 0.0;
        }

        double visible = Math.max(0.0, top - bottom);
        double mid = (top + bottom) / 2.0;
        double height = visible * grow;
        double width = q.w * pose.sx * (0.55 + 0.45 * Math.min(1.0, grow));
        double x = q.cx * pose.sx;
        double z = 0.0;
        if (q.back) {
            mid -= 0.07;
            z = -0.16;
        }

        if (height < 0.002) {
            height = 1.0E-4;
            width = 1.0E-4;
        }

        Vector3f scale = new Vector3f((float)(8.0 * width * 1.02), (float)(4.0 * height * 1.05), 1.0F);
        Vector3f tr = new Vector3f((float)(x - 0.1 * width), (float)(mid - height / 2.0), (float)z);
        return new Transformation(tr, new Quaternionf(), scale, new Quaternionf());
    }

    private static void apply(List<RankDrop.Quad> quads, RankDrop.Pose pose) {
        for (RankDrop.Quad q : quads) {
            TextDisplay d = q.d;
            if (d != null && d.isValid()) {
                Transformation t = shape(q, pose);
                String key = t.getTranslation().toString() + t.getScale();
                if (!key.equals(q.last)) {
                    q.last = key;
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(pose.duration);
                    d.setTransformation(t);
                }
            }
        }
    }

    private static void color(RankDrop.Quad q, int rgb) {
        int argb = 0xFF000000 | rgb & 16777215;
        if (q.d != null && q.d.isValid() && argb != q.lastArgb) {
            q.lastArgb = argb;
            q.d.setBackgroundColor(Color.fromARGB(argb));
        }
    }

    private static void spawn(World w, Location at, List<RankDrop.Quad> quads, RankDrop.Pose pose) {
        for (RankDrop.Quad q : quads) {
            try {
                q.d = w.spawn(at, TextDisplay.class, d -> {
                    d.text(Component.text(" "));
                    d.setBillboard(Billboard.VERTICAL);
                    d.setBackgroundColor(Color.fromARGB(0xFF000000 | q.rgb));
                    d.setDefaultBackground(false);
                    d.setShadowed(false);
                    d.setSeeThrough(false);
                    d.setBrightness(new Brightness(15, 15));
                    d.setTransformation(shape(q, pose));
                    d.setPersistent(false);
                    d.setViewRange(1.6F);
                });
                q.lastArgb = 0xFF000000 | q.rgb;
            } catch (RuntimeException var7) {
                q.d = null;
            }
        }
    }

    private static void remove(List<RankDrop.Quad> quads) {
        for (RankDrop.Quad q : quads) {
            if (q.d != null && q.d.isValid()) {
                q.d.remove();
            }

            q.d = null;
        }
    }

    private static double clamp(double k) {
        return Math.max(0.0, Math.min(1.0, k));
    }

    private static double easeOut(double k) {
        k = clamp(k);
        return 1.0 - Math.pow(1.0 - k, 3.0);
    }

    private static double easeIn(double k) {
        k = clamp(k);
        return k * k;
    }

    private static double smooth(double k) {
        k = clamp(k);
        return k * k * (3.0 - 2.0 * k);
    }

    public static void turn(Player p, Location target, int ticks) {
        Location start = p.getLocation();
        float y0 = start.getYaw();
        float p0 = start.getPitch();
        Location eye = p.getEyeLocation();
        double dx = target.getX() - eye.getX();
        double dy = target.getY() - eye.getY();
        double dz = target.getZ() - eye.getZ();
        float y1 = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float p1 = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        float dyaw = ((y1 - y0) % 360.0F + 540.0F) % 360.0F - 180.0F;
        float dpitch = p1 - p0;
        if (!(Math.abs(dyaw) < 2.0F) || !(Math.abs(dpitch) < 2.0F)) {
            int[] t = new int[]{0};
            BukkitTask[] task = new BukkitTask[1];
            task[0] = Tasks.timer(0L, 1L, () -> {
                t[0]++;
                if (p.isOnline() && t[0] <= ticks) {
                    double k = smooth((double)t[0] / ticks);
                    double yaw = Math.toRadians(y0 + dyaw * k);
                    double pitch = Math.toRadians(p0 + dpitch * k);
                    Location e = p.getEyeLocation();
                    double x = -Math.sin(yaw) * Math.cos(pitch);
                    double y = -Math.sin(pitch);
                    double z = Math.cos(yaw) * Math.cos(pitch);

                    try {
                        p.lookAt(e.getX() + x * 10.0, e.getY() + y * 10.0, e.getZ() + z * 10.0, LookAnchor.EYES);
                    } catch (LinkageError | RuntimeException var22) {
                        Tasks.cancel(task[0]);
                    }
                } else {
                    Tasks.cancel(task[0]);
                }
            });
        }
    }

    private static double groundY(Location l) {
        World w = l.getWorld();
        int x = l.getBlockX();
        int z = l.getBlockZ();

        for (int y = l.getBlockY() + 3; y > l.getBlockY() - 12 && y > w.getMinHeight(); y--) {
            if (w.getBlockAt(x, y, z).getType().isSolid() && !w.getBlockAt(x, y + 1, z).getType().isSolid()) {
                return y + 1;
            }
        }

        return l.getY();
    }

    public static int play(PracticePlugin plugin, Player p, Tier from, Tier to, Vector facing) {
        World w = p.getWorld();
        Vector dir = facing == null ? p.getEyeLocation().getDirection() : facing.clone();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }

        dir.normalize();
        Vector right = new Vector(-dir.getZ(), 0.0, dir.getX());
        Location base = p.getLocation().add(dir.clone().multiply(8.5));
        base.setY(groundY(base));
        Block under = w.getBlockAt(base.getBlockX(), (int)Math.floor(base.getY()) - 1, base.getBlockZ());
        BlockData ground = under.getType().isSolid() ? under.getBlockData() : Material.STONE.createBlockData();
        turn(p, base.clone().add(0.0, 1.6939999580383303, 0.0), 14);
        int newColor = Integer.parseInt(to.color.substring(1), 16);
        Color flameTint = Color.fromRGB(RankReveal.lerp(newColor, 16742938, 0.5));
        List<RankDrop.Quad> oldQ = from == null ? List.of() : layout(from.label, Integer.parseInt(from.color.substring(1), 16));
        List<RankDrop.Quad> newQ = layout(to.label, newColor);
        float newWidth = width(to.label);
        RankDrop.Pose oldPose = new RankDrop.Pose();
        RankDrop.Pose newPose = new RankDrop.Pose();
        TextDisplay[] sub = new TextDisplay[1];
        int off = from == null ? -40 : 0;
        int oldIn = 4;
        int oldSink = 30;
        int oldGone = 52;
        int dropStart = 50 + off;
        int fall = 15;
        int impact = dropStart + fall;
        int sinkOut = impact + 100;
        int end = sinkOut + 26;
        double dropHeight = 10.0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] t = new int[]{0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Tasks.timer(
            0L,
            1L,
            () -> {
                int now = (int)(t[0]++);

                try {
                    if (!oldQ.isEmpty() && now <= oldGone) {
                        if (now == oldIn) {
                            spawn(w, base, oldQ, oldPose);
                            Snd.at(base, "block.beacon.ambient", 1.0F, 1.4F);
                        }

                        if (now > oldIn && now <= oldIn + 16) {
                            for (int r = 0; r < 14; r++) {
                                oldPose.grow[r] = easeOut((now - oldIn - (13 - r) * 0.6) / 6.0);
                            }

                            oldPose.duration = 1;
                            apply(oldQ, oldPose);
                        }

                        if (now >= oldSink && now < oldGone) {
                            double k = easeIn((double)(now - oldSink) / (oldGone - oldSink - 2));
                            oldPose.dy = -3.2339999198913576 * k;

                            for (int r = 0; r < 14; r++) {
                                oldPose.grow[r] = 1.0;
                            }

                            oldPose.duration = 1;
                            apply(oldQ, oldPose);
                            if (now % 2 == 0) {
                                for (int i = 0; i < 6; i++) {
                                    double x = (rnd.nextDouble() - 0.5) * width(from.label);
                                    Location at = base.clone().add(right.clone().multiply(x)).add(0.0, 0.05, 0.0);
                                    w.spawnParticle(Particle.BLOCK, at, 3, 0.15, 0.05, 0.15, 0.1, ground);
                                }

                                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, base.clone().add(0.0, 0.1, 0.0), 1, width(from.label) / 3.0F, 0.02, 0.2, 0.01);
                            }

                            if (now % 5 == 0) {
                                Snd.at(base, "block.grindstone.use", 0.5F, 0.55F);
                            }
                        }

                        if (now == oldSink) {
                            Snd.at(base, "block.piston.contract", 0.9F, 0.6F);
                        }

                        if (now == oldGone) {
                            remove(oldQ);
                        }
                    }

                    if (now == dropStart) {
                        newPose.dy = dropHeight;
                        spawn(w, base, newQ, newPose);
                        Snd.at(base, "entity.ender_dragon.flap", 0.8F, 1.3F);
                        Snd.at(base, "item.trident.riptide_1", 0.7F, 0.8F);
                    }

                    if (now > dropStart && now <= impact) {
                        double k = (double)(now - dropStart) / fall;
                        newPose.dy = dropHeight * (1.0 - easeIn(k));

                        for (int r = 0; r < 14; r++) {
                            newPose.grow[r] = easeOut((now - dropStart - r * 0.55) / 7.0);
                        }

                        newPose.sx = 1.0;
                        newPose.sy = 1.0 + 0.12 * k;
                        newPose.duration = 1;
                        if (now == impact) {
                            newPose.dy = 0.0;

                            for (int r = 0; r < 14; r++) {
                                newPose.grow[r] = 1.0;
                            }
                        }

                        apply(newQ, newPose);
                        if (now < impact && now % 2 == 0) {
                            Location trail = base.clone().add(0.0, newPose.dy + 3.08F, 0.0);
                            w.spawnParticle(Particle.FLAME, trail, 6, newWidth / 3.0F, 0.2, 0.15, 0.01);
                        }
                    }

                    if (now == impact) {
                        impact(w, base, right, newWidth, ground, flameTint);
                        newPose.sx = 1.14;
                        newPose.sy = 0.7;
                        newPose.duration = 2;
                        apply(newQ, newPose);
                        if (p.isOnline()) {
                            Snd.play(p, "ui.toast.challenge_complete", 1.0F, 1.0F);
                            Fx.shine(p, "RANK UP", Fx.GOLD, Text.mm("<" + to.color + "><b>" + to.longName()), 70);
                        }
                    }

                    if (now == impact + 2) {
                        newPose.dy = 0.85;
                        newPose.sx = 0.95;
                        newPose.sy = 1.1;
                        newPose.duration = 4;
                        apply(newQ, newPose);
                    }

                    if (now == impact + 6) {
                        newPose.dy = 0.0;
                        newPose.sx = 1.0;
                        newPose.sy = 1.0;
                        newPose.duration = 4;
                        apply(newQ, newPose);
                    }

                    if (now == impact + 10) {
                        newPose.sx = 1.05;
                        newPose.sy = 0.9;
                        newPose.duration = 2;
                        apply(newQ, newPose);
                        w.spawnParticle(Particle.BLOCK, base.clone().add(0.0, 0.1, 0.0), 30, newWidth / 3.0F, 0.05, 0.3, 0.1, ground);
                        Snd.at(base, "block.stone.fall", 1.0F, 0.7F);
                    }

                    if (now == impact + 12) {
                        newPose.sx = 1.0;
                        newPose.sy = 1.0;
                        newPose.duration = 3;
                        apply(newQ, newPose);
                    }

                    if (now == impact + 14) {
                        sub[0] = subtitle(w, base, to);
                    }

                    if (now > impact + 14 && now <= impact + 24 && sub[0] != null && sub[0].isValid()) {
                        sub[0].setTextOpacity((byte)(5 + (int)(easeOut((now - impact - 14) / 10.0) * 250.0)));
                    }

                    if (now > impact && now < sinkOut) {
                        if (now % 3 == 0) {
                            for (int i = 0; i < 4; i++) {
                                double x = (rnd.nextDouble() - 0.5) * newWidth;
                                Location at = base.clone().add(right.clone().multiply(x)).add(0.0, 0.05, 0.0);
                                w.spawnParticle(Particle.FLAME, at, 1, 0.08, 0.02, 0.08, 0.015);
                                if (rnd.nextInt(3) == 0) {
                                    w.spawnParticle(Particle.SMALL_FLAME, at.add(0.0, 0.2, 0.0), 1, 0.05, 0.1, 0.05, 0.01);
                                }
                            }

                            if (now % 9 == 0) {
                                w.spawnParticle(
                                    Particle.LAVA, base.clone().add(right.clone().multiply((rnd.nextDouble() - 0.5) * newWidth)), 1, 0.0, 0.0, 0.0, 0.0
                                );
                            }
                        }

                        if (now > impact + 16) {
                            double band = (now - impact - 16) % 26 * 0.85 - 2.0;

                            for (RankDrop.Quad q : newQ) {
                                if (!q.back) {
                                    double dist = Math.abs(q.row - band);
                                    double glow = dist < 2.5 ? (1.0 - dist / 2.5) * 0.6 : 0.0;
                                    color(q, RankReveal.lerp(q.rgb, 16777215, Math.round(glow * 8.0) / 8.0));
                                }
                            }
                        }

                        if (now % 7 == 0) {
                            RankDrop.Quad qx = newQ.get(rnd.nextInt(newQ.size()));
                            Location sparkle = base.clone().add(right.clone().multiply(qx.cx)).add(0.0, qx.cy, 0.0);
                            w.spawnParticle(Particle.END_ROD, sparkle, 1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }

                    if (now >= sinkOut && now < end) {
                        double k = easeIn((double)(now - sinkOut) / (end - sinkOut - 2));
                        newPose.dy = -3.2339999198913576 * k;
                        newPose.duration = 1;
                        apply(newQ, newPose);
                        if (now % 2 == 0) {
                            w.spawnParticle(Particle.BLOCK, base.clone().add(0.0, 0.05, 0.0), 8, newWidth / 3.0F, 0.03, 0.2, 0.1, ground);
                        }

                        if (sub[0] != null && sub[0].isValid()) {
                            sub[0].setTextOpacity((byte)(5 + (int)((1.0 - easeOut((now - sinkOut) / 10.0)) * 250.0)));
                        }

                        if (now == sinkOut) {
                            Snd.at(base, "block.piston.contract", 0.8F, 0.7F);
                        }
                    }

                    if (now >= end) {
                        cleanup(oldQ, newQ, sub[0]);
                        Tasks.cancel(task[0]);
                    }
                } catch (RuntimeException var36) {
                    cleanup(oldQ, newQ, sub[0]);
                    Tasks.cancel(task[0]);
                }
            }
        );
        return end;
    }

    private static void impact(World w, Location base, Vector right, float width, BlockData ground, Color tint) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location mid = base.clone().add(0.0, 0.1, 0.0);

        for (double x = -width / 2.0F; x <= width / 2.0F; x += 0.35) {
            Location at = base.clone().add(right.clone().multiply(x)).add(0.0, 0.05, 0.0);
            w.spawnParticle(Particle.DUST_PILLAR, at, 4, 0.1, 0.05, 0.25, 0.0, ground);
            w.spawnParticle(Particle.FLAME, at, 3, 0.05, 0.05, 0.05, 0.09);
            w.spawnParticle(Particle.BLOCK, at, 4, 0.1, 0.05, 0.3, 0.2, ground);
        }

        w.spawnParticle(Particle.EXPLOSION, mid.clone().add(0.0, 0.4, 0.0), 2, width / 4.0F, 0.1, 0.2, 0.0);
        w.spawnParticle(Particle.LAVA, mid, 14, width / 3.0F, 0.1, 0.3, 0.0);
        w.spawnParticle(Particle.DUST, mid.clone().add(0.0, 0.6, 0.0), 60, width / 2.5, 0.5, 0.4, 0.0, new DustOptions(tint, 1.8F));
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, mid, 10, width / 3.0F, 0.05, 0.4, 0.02);

        for (int step = 1; step <= 5; step++) {
            int s = step;
            Tasks.later(
                s,
                () -> {
                    double radius = 1.0 + s * 1.1;
                    int n = 18 + s * 6;

                    for (int i = 0; i < n; i++) {
                        double a = (Math.PI * 2) * i / n + rnd.nextDouble() * 0.1;
                        Vector forward = new Vector(right.getZ(), 0.0, -right.getX());
                        Location atx = base.clone()
                            .add(right.clone().multiply(Math.cos(a) * radius * (0.6 + width / 10.0F)))
                            .add(forward.multiply(Math.sin(a) * radius * 0.7))
                            .add(0.0, 0.08, 0.0);
                        w.spawnParticle(Particle.BLOCK, atx, 1, 0.05, 0.02, 0.05, 0.0, ground);
                        if (i % 3 == 0) {
                            w.spawnParticle(Particle.CLOUD, atx, 1, 0.0, 0.0, 0.0, 0.01);
                        }
                    }
                }
            );
        }

        Snd.at(base, "item.mace.smash_ground_heavy", 1.0F, 0.75F);
        Snd.at(base, "entity.generic.explode", 0.6F, 1.25F);
        Snd.at(base, "item.firecharge.use", 1.0F, 0.7F);
        Snd.at(base, "block.anvil.land", 0.45F, 0.6F);
        Snd.at(base, "entity.player.levelup", 1.0F, 1.15F);
    }

    private static TextDisplay subtitle(World w, Location base, Tier to) {
        try {
            return w.spawn(
                base,
                TextDisplay.class,
                d -> {
                    d.text(Text.mm("<b><gradient:#FFFFFF:" + to.color + ">" + to.longName().toUpperCase() + "</gradient></b>"));
                    d.setBillboard(Billboard.VERTICAL);
                    d.setDefaultBackground(false);
                    d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    d.setShadowed(true);
                    d.setTextOpacity((byte)5);
                    d.setBrightness(new Brightness(15, 15));
                    d.setTransformation(
                        new Transformation(new Vector3f(0.0F, 3.4299998F, 0.0F), new Quaternionf(), new Vector3f(2.4F, 2.4F, 2.4F), new Quaternionf())
                    );
                    d.setPersistent(false);
                    d.setViewRange(1.6F);
                }
            );
        } catch (RuntimeException var4) {
            return null;
        }
    }

    private static void cleanup(List<RankDrop.Quad> a, List<RankDrop.Quad> b, TextDisplay sub) {
        remove(a);
        remove(b);
        if (sub != null && sub.isValid()) {
            sub.remove();
        }
    }

    private static final class Pose {
        double dy;
        double sx = 1.0;
        double sy = 1.0;
        final double[] grow = new double[14];
        int duration = 1;
    }

    private static final class Quad {
        TextDisplay d;
        final int row;
        final float cx;
        final float cy;
        final float w;
        final float h;
        final int rgb;
        final boolean back;
        String last = "";
        int lastArgb = Integer.MIN_VALUE;

        Quad(int row, float cx, float cy, float w, float h, int rgb, boolean back) {
            this.row = row;
            this.cx = cx;
            this.cy = cy;
            this.w = w;
            this.h = h;
            this.rgb = rgb;
            this.back = back;
        }
    }
}
