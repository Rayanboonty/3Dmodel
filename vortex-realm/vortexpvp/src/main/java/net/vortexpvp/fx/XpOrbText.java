package net.vortexpvp.fx;

import com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.entity.TeleportFlag.EntityState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class XpOrbText implements Listener {
    private static final int[][] GLYPHS = new int[128][];
    private static final double DOT = 0.32;
    private static final double AHEAD = 3.3;
    private static final double HEIGHT = 1.15;
    private static final double[][] SPOTS = new double[][]{
        {0.0, 3.3, 1.15},
        {0.0, 2.4, 1.15},
        {24.0, 3.3, 1.15},
        {-24.0, 3.3, 1.15},
        {0.0, 3.3, 1.95},
        {24.0, 2.4, 1.15},
        {-24.0, 2.4, 1.15},
        {42.0, 3.3, 1.15},
        {-42.0, 3.3, 1.15},
        {24.0, 3.3, 1.95},
        {-24.0, 3.3, 1.95},
        {60.0, 2.8, 1.15},
        {-60.0, 2.8, 1.15},
        {0.0, 2.0, 2.2}
    };
    private static final int RECHECK = 4;
    private static final int BURST = 7;
    private static final int GATHER = 14;
    private static final int HOLD_UNTIL = 72;
    private static final int ABSORB = 14;
    private static final int VALUE = 1237;
    private static final Set<UUID> OURS = ConcurrentHashMap.newKeySet();

    /** The round score ("2-1"), bursting out of where the round was won and settling in a fixed spot in front of the viewer. */
    public static void score(VortexPvP plugin, Player viewer, Location death, String text) {
        score(plugin, viewer, death, text, null);
    }

    /** Same, keeping clear of {@code avoid} (the other player) so the text is never hidden behind or drawn over them. */
    public static void score(VortexPvP plugin, Player viewer, Location death, String text, Location avoid) {
        if (viewer.isOnline()) {
            Location from = death != null && death.getWorld() == viewer.getWorld() && death.distanceSquared(viewer.getLocation()) < 900.0
                ? death.clone().add(0.0, 1.0, 0.0)
                : null;
            play(plugin, viewer, from, text, avoid, 0.0);
        }
    }

    /** The rank points ("+25"): one line above the score text so the two never overlap. */
    public static void inFront(VortexPvP plugin, Player viewer, String text) {
        inFront(plugin, viewer, text, null);
    }

    public static void inFront(VortexPvP plugin, Player viewer, String text, Location avoid) {
        if (viewer.isOnline()) {
            play(plugin, viewer, null, text, avoid, 0.95);
        }
    }

    private static Vector flat(Vector v) {
        Vector f = v.clone().setY(0);
        return f.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : f.normalize();
    }

    /** Rotates {@code ahead} away from {@code avoid} when the other player stands within 40 degrees of it. */
    private static Vector awayFrom(Player viewer, Vector ahead, Location avoid) {
        if (avoid != null && avoid.getWorld() == viewer.getWorld()) {
            Vector to = flat(avoid.toVector().subtract(viewer.getLocation().toVector()));
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, to.dot(ahead)))));
            if (angle < 40.0) {
                double cross = ahead.getX() * to.getZ() - ahead.getZ() * to.getX();
                double turn = (40.0 - angle) * (cross > 0.0 ? -1.0 : 1.0);
                return ahead.clone().rotateAroundY(Math.toRadians(turn));
            }
        }

        return ahead;
    }

    private static void play(VortexPvP plugin, Player viewer, Location burstFrom, String text, Location avoid, double lift) {
        World w = viewer.getWorld();
        Vector ahead = awayFrom(viewer, flat(viewer.getEyeLocation().getDirection()), avoid);
        List<double[]> dots = new ArrayList<>();
        double cursor = 0.0;

        for (char ch : text.toCharArray()) {
            int[] g = ch < 128 ? GLYPHS[ch] : null;
            if (g == null) {
                cursor += 2.0;
            } else {
                for (int row = 0; row < 5; row++) {
                    for (int col = 0; col < 3; col++) {
                        if ((g[row] >> 2 - col & 1) != 0) {
                            dots.add(new double[]{cursor + col, 4 - row});
                        }
                    }
                }

                cursor += 4.0;
            }
        }

        if (!dots.isEmpty()) {
            double width = cursor - 1.0;
            int place = clearest(viewer, ahead, width, -1);
            // the text is anchored here for its whole life: it does not follow the player
            Vector centre = target(viewer, ahead, place).add(new Vector(0.0, lift, 0.0));
            Vector from = burstFrom != null
                ? burstFrom.toVector()
                : viewer.getLocation().toVector().add(ahead.clone().multiply(1.6)).add(new Vector(0.0, 0.3, 0.0));
            ThreadLocalRandom r = ThreadLocalRandom.current();
            List<XpOrbText.Orb> orbs = new ArrayList<>();

            for (double[] d : dots) {
                Vector burst = from.clone()
                    .add(new Vector(r.nextDouble(-1.0, 1.0), r.nextDouble(-0.1, 1.1), r.nextDouble(-1.0, 1.0)).normalize().multiply(0.7 + r.nextDouble(0.9)));
                int delay = (int)Math.round(d[0] / Math.max(1.0, width) * 9.0);
                XpOrbText.Orb o = new XpOrbText.Orb(d[0] - width / 2.0, d[1] - 2.0, delay, burst);
                if (spawn(plugin, viewer, w, from.toLocation(w), o)) {
                    o.pos = from.clone();
                    orbs.add(o);
                }
            }

            Snd.play(viewer, "entity.experience_orb.pickup", 0.7F, 0.6F);
            Snd.play(viewer, "block.amethyst_block.chime", 0.8F, 1.5F);
            int[] t = new int[]{0};
            Vector[] faceRight = new Vector[]{null};
            World[] world = new World[]{w};
            Vector[] anchor = new Vector[]{centre, from};
            int[] reanchors = new int[]{0};
            BukkitTask[] task = new BukkitTask[1];
            task[0] = Tasks.timer(
                1L,
                1L,
                () -> {
                    int now = ++t[0];
                    if (viewer.isOnline() && now <= 106 && reanchors[0] < 2 && now < 72
                        && (!viewer.getWorld().equals(world[0]) || viewer.getLocation().toVector().distanceSquared(anchor[0]) > 400.0)) {
                        // the viewer was sent somewhere else (lobby): set the text up again in front of them, once
                        reanchors[0]++;
                        World nw = viewer.getWorld();
                        Vector nAhead = flat(viewer.getEyeLocation().getDirection());
                        Vector jump = target(viewer, nAhead, clearest(viewer, nAhead, width, -1)).add(new Vector(0.0, lift, 0.0)).subtract(anchor[0]);
                        anchor[0] = anchor[0].clone().add(jump);
                        anchor[1] = anchor[1].clone().add(jump);
                        faceRight[0] = null;

                        for (XpOrbText.Orb ox : orbs) {
                            if (ox.orb != null) {
                                remove(ox);
                                ox.burst = ox.burst.clone().add(jump);
                                Vector at = ox.pos.clone().add(jump);
                                if (spawn(plugin, viewer, nw, at.toLocation(nw), ox)) {
                                    ox.pos = at;
                                }
                            }
                        }

                        world[0] = nw;
                    }

                    boolean moved = viewer.isOnline() && !viewer.getWorld().equals(world[0]);
                    if (viewer.isOnline() && !moved && now <= 106) {
                        if (!viewer.isDead()) {
                            // static centre; the row of digits only turns (smoothly) to keep facing the viewer
                            Vector centreNow = anchor[0];
                            Vector fromNow = anchor[1];
                            Vector eye = viewer.getEyeLocation().toVector();
                            Vector facing = flat(eye.clone().subtract(centreNow));
                            Vector wantRight = new Vector(facing.getZ(), 0.0, -facing.getX());
                            if (faceRight[0] == null) {
                                faceRight[0] = wantRight;
                            } else if (faceRight[0].angle(wantRight) > Math.toRadians(2.0)) {
                                faceRight[0] = flat(faceRight[0].clone().multiply(0.8).add(wantRight.multiply(0.2)));
                            }

                            Vector right = faceRight[0];
                            Vector[] c = new Vector[]{centreNow};
                            Vector chest = viewer.getLocation().toVector().add(new Vector(0.0, 1.0, 0.0));
                            boolean any = false;

                            for (int i = 0; i < orbs.size(); i++) {
                                XpOrbText.Orb ox = orbs.get(i);
                                if (ox.orb != null) {
                                    any = true;
                                    Vector spot = c[0].clone().add(right.clone().multiply(ox.col * 0.32)).add(new Vector(0.0, ox.row * 0.32, 0.0));
                                    Vector pos;
                                    if (now <= 7) {
                                        pos = lerp(fromNow, ox.burst, easeOut(now / 7.0));
                                    } else if (now <= 7 + ox.delay) {
                                        pos = ox.burst.clone().add(new Vector(0.0, Math.sin((now + i) * 0.5) * 0.03, 0.0));
                                    } else if (now <= 7 + ox.delay + 14) {
                                        double k = smooth((now - 7 - ox.delay) / 14.0);
                                        pos = lerp(ox.burst, spot, k).add(new Vector(0.0, Math.sin(k * Math.PI) * 0.6, 0.0));
                                        if (now == 7 + ox.delay + 14 && i % 3 == 0) {
                                            Snd.play(viewer, "entity.experience_orb.pickup", 0.3F, 0.9F + (float)i / orbs.size());
                                        }
                                    } else if (now <= 72 + i % 8) {
                                        double wave = Math.sin(now * 0.22 - ox.col * 0.7) * 0.07;
                                        pos = spot.add(new Vector(0.0, wave, 0.0));
                                        if (now == 42 && i == 0) {
                                            Snd.play(viewer, "entity.player.levelup", 0.35F, 1.7F);
                                        }
                                    } else {
                                        double k = (now - 72 - i % 8) / 14.0;
                                        if (k >= 1.0) {
                                            Snd.play(viewer, "entity.experience_orb.pickup", 0.35F, 0.55F + r.nextFloat() * 0.7F);
                                            remove(ox);
                                            continue;
                                        }

                                        double a = k * Math.PI * 2.0 + i;
                                        Vector swirl = right.clone()
                                            .multiply(Math.cos(a) * 0.5 * (1.0 - k))
                                            .add(new Vector(0.0, Math.sin(a) * 0.3 * (1.0 - k), 0.0));
                                        pos = lerp(spot, chest, k * k).add(swirl);
                                    }

                                    ox.pos = pos;
                                    move(ox, pos.toLocation(world[0]));
                                }
                            }

                            if (!any) {
                                Tasks.cancel(task[0]);
                            }
                        }
                    } else {
                        for (XpOrbText.Orb oxx : orbs) {
                            remove(oxx);
                        }

                        Tasks.cancel(task[0]);
                    }
                }
            );
        }
    }

    private static Vector target(Player viewer, Vector ahead, int spot) {
        double[] s = SPOTS[spot];
        Vector dir = ahead.clone().rotateAroundY(Math.toRadians(s[0]));
        return viewer.getLocation().toVector().add(dir.multiply(s[1])).add(new Vector(0.0, s[2], 0.0));
    }

    private static int clearest(Player viewer, Vector ahead, double width, int current) {
        Location eye = viewer.getEyeLocation();
        List<BoundingBox> bodies = new ArrayList<>();

        for (Entity e : viewer.getWorld().getNearbyEntities(eye, 9.0, 5.0, 9.0)) {
            if (e instanceof LivingEntity && !e.equals(viewer) && viewer.canSee(e) && !e.isDead()) {
                bodies.add(e.getBoundingBox().expand(0.18));
            }
        }

        int currentBlocked = current >= 0 ? blocked(viewer, eye, bodies, target(viewer, ahead, current), width) : 99;
        if (currentBlocked == 0) {
            return current;
        } else {
            int best = current >= 0 ? current : 0;
            int bestBlocked = currentBlocked;

            for (int i = 0; i < SPOTS.length && bestBlocked > 0; i++) {
                if (i != current) {
                    int b = blocked(viewer, eye, bodies, target(viewer, ahead, i), width);
                    if (b < bestBlocked) {
                        best = i;
                        bestBlocked = b;
                    }
                }
            }

            return best;
        }
    }

    private static int blocked(Player viewer, Location eye, List<BoundingBox> bodies, Vector centre, double width) {
        Vector eyeV = eye.toVector();
        Vector facing = flat(eyeV.clone().subtract(centre));
        Vector right = new Vector(facing.getZ(), 0.0, -facing.getX());
        double half = width / 2.0 * 0.32;
        int hidden = 0;

        for (int cx = -1; cx <= 1; cx++) {
            for (int cy = -1; cy <= 1; cy++) {
                Vector point = centre.clone().add(right.clone().multiply(cx * half)).add(new Vector(0.0, cy * 2.1 * 0.32, 0.0));
                Vector ray = point.clone().subtract(eyeV);
                double dist = ray.length();
                if (!(dist < 0.3)) {
                    ray.multiply(1.0 / dist);
                    boolean hit = viewer.getWorld().rayTraceBlocks(eye, ray, dist, FluidCollisionMode.NEVER, true) != null;

                    for (int b = 0; !hit && b < bodies.size(); b++) {
                        hit = bodies.get(b).rayTrace(eyeV, ray, dist) != null;
                    }

                    if (hit) {
                        hidden++;
                    }
                }
            }
        }

        return hidden;
    }

    private static boolean spawn(VortexPvP plugin, Player viewer, World w, Location at, XpOrbText.Orb o) {
        try {
            o.carrier = w.spawn(at, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setTeleportDuration(2);
                d.setBillboard(Billboard.FIXED);
            });
            o.orb = w.spawn(at, ExperienceOrb.class, e -> {
                OURS.add(e.getUniqueId());
                e.setExperience(1237);
                e.setGravity(false);
                e.setPersistent(false);
                e.setVisibleByDefault(false);
                e.setInvulnerable(true);
                e.setSilent(true);
            });
            o.carrier.addPassenger(o.orb);
            viewer.showEntity(plugin, o.carrier);
            viewer.showEntity(plugin, o.orb);
            return true;
        } catch (RuntimeException var6) {
            remove(o);
            return false;
        }
    }

    private static void move(XpOrbText.Orb o, Location to) {
        if (o.carrier != null && o.carrier.isValid()) {
            if (!o.carrier.teleport(to)) {
                try {
                    o.carrier.teleport(to, new TeleportFlag[]{EntityState.RETAIN_PASSENGERS});
                } catch (LinkageError var3) {
                }
            }

            if (o.orb != null && o.orb.isValid() && !o.carrier.getPassengers().contains(o.orb)) {
                o.carrier.addPassenger(o.orb);
            }
        } else {
            if (o.orb != null && o.orb.isValid()) {
                o.orb.setVelocity(new Vector());
                o.orb.teleport(to);
            }
        }
    }

    private static void remove(XpOrbText.Orb o) {
        if (o.orb != null) {
            OURS.remove(o.orb.getUniqueId());
            if (o.orb.isValid()) {
                o.orb.remove();
            }

            o.orb = null;
        }

        if (o.carrier != null) {
            if (o.carrier.isValid()) {
                o.carrier.remove();
            }

            o.carrier = null;
        }
    }

    private static Vector lerp(Vector a, Vector b, double k) {
        return a.clone().add(b.clone().subtract(a).multiply(k));
    }

    private static double easeOut(double k) {
        double c = Math.max(0.0, Math.min(1.0, k));
        return 1.0 - (1.0 - c) * (1.0 - c) * (1.0 - c);
    }

    private static double smooth(double k) {
        double c = Math.max(0.0, Math.min(1.0, k));
        return c * c * c * (c * (c * 6.0 - 15.0) + 10.0);
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onPickup(PlayerPickupExperienceEvent e) {
        if (OURS.contains(e.getExperienceOrb().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onMerge(ExperienceOrbMergeEvent e) {
        if (OURS.contains(e.getMergeSource().getUniqueId()) || OURS.contains(e.getMergeTarget().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    public static void clearAll() {
        for (World w : Bukkit.getWorlds()) {
            for (ExperienceOrb o : w.getEntitiesByClass(ExperienceOrb.class)) {
                if (OURS.remove(o.getUniqueId())) {
                    if (o.getVehicle() != null) {
                        o.getVehicle().remove();
                    }

                    o.remove();
                }
            }
        }
    }

    static {
        GLYPHS[48] = new int[]{7, 5, 5, 5, 7};
        GLYPHS[49] = new int[]{2, 6, 2, 2, 7};
        GLYPHS[50] = new int[]{7, 1, 7, 4, 7};
        GLYPHS[51] = new int[]{7, 1, 3, 1, 7};
        GLYPHS[52] = new int[]{5, 5, 7, 1, 1};
        GLYPHS[53] = new int[]{7, 4, 7, 1, 7};
        GLYPHS[54] = new int[]{7, 4, 7, 5, 7};
        GLYPHS[55] = new int[]{7, 1, 2, 2, 2};
        GLYPHS[56] = new int[]{7, 5, 7, 5, 7};
        GLYPHS[57] = new int[]{7, 5, 7, 1, 7};
        GLYPHS[43] = new int[]{0, 2, 7, 2, 0};
        GLYPHS[45] = new int[]{0, 0, 7, 0, 0};
    }

    private static final class Orb {
        final double col;
        final double row;
        final int delay;
        Vector burst;
        ExperienceOrb orb;
        ItemDisplay carrier;
        Vector pos;

        Orb(double col, double row, int delay, Vector burst) {
            this.col = col;
            this.row = row;
            this.delay = delay;
            this.burst = burst;
        }
    }
}
