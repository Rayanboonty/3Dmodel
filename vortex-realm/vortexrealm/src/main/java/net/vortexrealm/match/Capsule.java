package net.vortexrealm.match;

import java.util.ArrayList;
import java.util.List;
import net.vortexrealm.arena.ChangeTracker;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * 3.13.5 - the PvP HQ style start: each player waits a few blocks above their spawn inside a glass block (team colour,
 * dark base and cap). At GO the block splits down the middle, the two halves swing apart and fall away, and the
 * player drops onto the map (no fall damage). Invisible barriers hold the player while the count runs.
 */
final class Capsule {
    /** 3.13.14: the capsule rises up to MAX_HEIGHT above the spawn, lower when the map has a roof or trees above. */
    static final int MAX_HEIGHT = 20;
    static final int MIN_HEIGHT = 4;
    /** Inner room: 3x3 blocks, 3 high (the player used to be squeezed into a single block). */
    static final int R = 1;
    static final int ROOM = 3;
    private final Player player;
    private final Session session;
    private final Location top;
    private final String glass;
    private final ChangeTracker tracker;
    private final List<Block> barriers = new ArrayList<>();
    private final List<BlockDisplay> left = new ArrayList<>();
    private final List<BlockDisplay> right = new ArrayList<>();
    /** 3.14.2: glowing shards orbiting the capsule, a glowing ring under it, and shards that burst out at GO. */
    private final List<BlockDisplay> orbit = new ArrayList<>();
    private final List<BlockDisplay> burst = new ArrayList<>();
    private BlockDisplay ring;
    private double angle;
    private int lastPulse = -1;
    /** The halves split along x (true) or z. */
    private boolean splitX;
    private boolean done;

    Capsule(Player player, Session session, Location spawn, Location lookAt, String glass, ChangeTracker tracker) {
        this.player = player;
        this.session = session;
        this.top = spawn.clone().add(0, clearance(spawn), 0);
        this.top.setX(spawn.getBlockX() + 0.5);
        this.top.setZ(spawn.getBlockZ() + 0.5);
        this.glass = glass;
        this.tracker = tracker;
        if (lookAt != null) {
            Elevator.face(this.top, lookAt);
            double dx = Math.abs(lookAt.getX() - spawn.getX());
            double dz = Math.abs(lookAt.getZ() - spawn.getZ());
            // the halves part to the player's left and right
            this.splitX = dz > dx;
        }
    }

    /** How high the capsule can go above this spawn: up to 20 blocks, stopping 1 block under anything solid. */
    static int clearance(Location spawn) {
        World w = spawn.getWorld();
        int bx = spawn.getBlockX();
        int by = spawn.getBlockY();
        int bz = spawn.getBlockZ();
        int need = ROOM + 2;
        for (int h = MAX_HEIGHT; h >= MIN_HEIGHT; h--) {
            boolean free = true;
            for (int dy = -1; dy <= need && free; dy++) {
                for (int dx = -R - 1; dx <= R + 1 && free; dx++) {
                    for (int dz = -R - 1; dz <= R + 1 && free; dz++) {
                        Material t = w.getBlockAt(bx + dx, by + h + dy, bz + dz).getType();
                        if (!t.isAir() && t != Material.BARRIER) {
                            free = false;
                        }
                    }
                }
            }

            if (free) {
                return h;
            }
        }

        return MIN_HEIGHT;
    }

    void begin() {
        World w = this.top.getWorld();
        int bx = this.top.getBlockX();
        int by = this.top.getBlockY();
        int bz = this.top.getBlockZ();
        // the invisible room that holds the player: a 3x3 floor, walls 3 high, a roof
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                this.barrier(w.getBlockAt(bx + dx, by - 1, bz + dz));
                this.barrier(w.getBlockAt(bx + dx, by + ROOM, bz + dz));
            }
        }

        for (int y = 0; y < ROOM; y++) {
            for (int i = -R; i <= R; i++) {
                this.barrier(w.getBlockAt(bx + R + 1, by + y, bz + i));
                this.barrier(w.getBlockAt(bx - R - 1, by + y, bz + i));
                this.barrier(w.getBlockAt(bx + i, by + y, bz + R + 1));
                this.barrier(w.getBlockAt(bx + i, by + y, bz - R - 1));
            }
        }

        Location corner = new Location(w, bx, by, bz);
        BlockData glassData = data(this.glass);
        BlockData base = data("polished_blackstone");
        BlockData cap = data("smooth_quartz");
        // each half of a 3x3x3 glass room (outside the barrier walls, so you look out through clean glass)
        float wide = 2 * R + 1 + 0.3f;      // 3.3 blocks across
        float half = wide / 2;               // each half 1.65
        float start = -R - 0.15f;            // left edge
        for (int part = 0; part < 2; part++) {
            List<BlockDisplay> list = part == 0 ? this.left : this.right;
            float a0 = part == 0 ? start : start + half;
            list.add(this.part(corner, glassData, a0, -0.05f, half, ROOM + 0.1f, wide, start));
            list.add(this.part(corner, base, a0, -0.35f, half, 0.3f, wide, start));
            list.add(this.part(corner, cap, a0, ROOM + 0.05f, half, 0.25f, wide, start));
        }

        // materialise: every piece grows from the centre of the room
        for (BlockDisplay d : this.all()) {
            Transformation full = d.getTransformation();
            Vector3f c = new Vector3f(0.5f, ROOM / 2f, 0.5f);
            d.setTransformation(new Transformation(c, new AxisAngle4f((float)Math.PI, 0, 1, 0), new Vector3f(0.01f), new AxisAngle4f()));
            Tasks.later(2L, () -> {
                if (d.isValid()) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(14);
                    d.setTransformation(full);
                }
            });
        }

        org.bukkit.Color col = this.color();
        BlockData shard = data(this.glass);
        for (int i = 0; i < 6; i++) {
            this.orbit.add(w.spawn(corner, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(shard);
                d.setShadowRadius(0.0F);
                d.setGlowing(true);
                d.setGlowColorOverride(col);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setTeleportDuration(2);
                d.setTransformation(new Transformation(new Vector3f(0.5f, 1.4f, 0.5f), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
            }));
        }

        this.ring = w.spawn(corner, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(data("sea_lantern"));
            d.setShadowRadius(0.0F);
            d.setGlowing(true);
            d.setGlowColorOverride(col);
            d.setTransformation(new Transformation(new Vector3f(0.5f, -0.45f, 0.5f), new AxisAngle4f(), new Vector3f(0.01f, 0.06f, 0.01f), new AxisAngle4f()));
        });
        Tasks.later(4L, () -> {
            if (this.ring != null && this.ring.isValid()) {
                this.ring.setInterpolationDelay(0);
                this.ring.setInterpolationDuration(16);
                this.ring.setTransformation(new Transformation(new Vector3f(0.5f - 2.2f, -0.45f, 0.5f - 2.2f), new AxisAngle4f(), new Vector3f(4.4f, 0.06f, 4.4f), new AxisAngle4f()));
            }
        });

        Tp.to(this.player, this.top);
        this.player.setFallDistance(0.0F);
        // a beam of light from the ground up to the capsule
        Location ground = this.top.clone().subtract(0, clearance(this.top.clone().subtract(0, 1, 0)) > 0 ? 0 : 0, 0);
        for (int y = 0; y < 22; y++) {
            Location at = ground.clone().subtract(0, y, 0);
            if (!at.getBlock().getType().isAir() && at.getBlock().getType() != Material.BARRIER) {
                break;
            }

            w.spawnParticle(Particle.END_ROD, at, 2, 0.15, 0.3, 0.15, 0.0);
        }

        w.spawnParticle(Particle.DUST, this.top.clone().add(0, 1.5, 0), 80, 1.6, 1.4, 1.6, 0.0, new Particle.DustOptions(col, 1.4F));
        Snd.play(this.player, "block.beacon.activate", 0.7F, 1.6F);
        Snd.play(this.player, "block.amethyst_block.resonate", 1.0F, 1.2F);
    }

    private List<BlockDisplay> all() {
        List<BlockDisplay> a = new ArrayList<>(this.left);
        a.addAll(this.right);
        return a;
    }

    private org.bukkit.Color color() {
        return this.glass.startsWith("red") ? org.bukkit.Color.fromRGB(0xFF4D4D) : this.glass.startsWith("light_blue") ? org.bukkit.Color.fromRGB(0x4DC3FF)
            : org.bukkit.Color.fromRGB(0xB06BFF);
    }

    private static BlockData data(String s) {
        try {
            return org.bukkit.Bukkit.createBlockData("minecraft:" + s);
        } catch (IllegalArgumentException e) {
            return Material.GLASS.createBlockData();
        }
    }

    private BlockDisplay part(Location corner, BlockData data, float a0, float y0, float aw, float h, float wide, float start) {
        // the block corner is (bx, by, bz); the room is centred on bx + 0.5
        float ax = (this.splitX ? a0 : start) + 0.5f - 0.5f;
        float az = (this.splitX ? start : a0) + 0.5f - 0.5f;
        float sx = this.splitX ? aw : wide;
        float sz = this.splitX ? wide : aw;
        return corner.getWorld().spawn(corner, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(data);
            d.setShadowRadius(0.0F);
            d.setTransformation(new Transformation(new Vector3f(ax, y0, az), new AxisAngle4f(), new Vector3f(sx, h, sz), new AxisAngle4f()));
        });
    }

    private void barrier(Block b) {
        if (b.getType().isAir()) {
            if (this.tracker != null) {
                this.tracker.record(b);
            }

            b.setType(Material.BARRIER, false);
            this.barriers.add(b);
        }
    }

    /** Keeps the player inside while the count runs, and animates: orbiting shards speed up, the last 3 seconds pulse. */
    void tick(double progress, int ticksLeft) {
        if (this.done || !this.player.isOnline()) {
            return;
        }

        World w = this.top.getWorld();
        this.angle += 0.07 + Math.min(1.0, progress) * 0.30;
        if (ticksLeft % 2 == 0) {
            float radius = (float)(2.5 - Math.min(1.0, progress) * 0.7);
            for (int i = 0; i < this.orbit.size(); i++) {
                BlockDisplay d = this.orbit.get(i);
                if (!d.isValid()) {
                    continue;
                }

                double a = this.angle + i * Math.PI * 2 / this.orbit.size();
                float y = 1.4f + (float)Math.sin(this.angle * 1.7 + i) * 0.6f;
                float sc = 0.26f;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(2);
                d.setTransformation(new Transformation(new Vector3f(0.5f + (float)Math.cos(a) * radius - sc / 2, y, 0.5f + (float)Math.sin(a) * radius - sc / 2),
                    new AxisAngle4f((float)(this.angle * 3 + i), 1, 1, 0), new Vector3f(sc), new AxisAngle4f()));
            }
        }

        if (ticksLeft % 10 == 0) {
            org.bukkit.Color col = this.color();
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8 + this.angle;
                w.spawnParticle(Particle.DUST, this.top.clone().add(Math.cos(a) * 2.2, -0.4, Math.sin(a) * 2.2), 1, 0, 0, 0, 0, new Particle.DustOptions(col, 1.0F));
            }

            w.spawnParticle(Particle.SOUL_FIRE_FLAME, this.top.clone().add(0, -0.7, 0), 6, 0.6, 0.05, 0.6, 0.01);
        }

        // the last three seconds: the capsule pulses, the sound rises
        int sec = (ticksLeft + 19) / 20;
        if (sec <= 3 && sec >= 1 && sec != this.lastPulse) {
            this.lastPulse = sec;
            Snd.play(this.player, "block.note_block.pling", 1.0F, sec == 1 ? 2.0F : sec == 2 ? 1.6F : 1.25F);
            Snd.play(this.player, "block.amethyst_cluster.hit", 1.0F, 0.8F + (3 - sec) * 0.3F);
            for (BlockDisplay d : this.all()) {
                if (!d.isValid()) {
                    continue;
                }

                Transformation t = d.getTransformation();
                Vector3f grown = new Vector3f(t.getScale()).mul(1.035f);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(4);
                d.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(), grown, t.getRightRotation()));
                Tasks.later(5L, () -> {
                    if (d.isValid() && !this.done) {
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(5);
                        d.setTransformation(t);
                    }
                });
            }

            w.spawnParticle(Particle.DUST, this.top.clone().add(0, 1.5, 0), 40, 1.5, 1.4, 1.5, 0, new Particle.DustOptions(this.color(), 1.6F));
            if (sec == 1) {
                // cracks appear
                w.spawnParticle(Particle.BLOCK, this.top.clone().add(0, 1.5, 0), 30, 1.5, 1.4, 1.5, 0.0, data(this.glass));
                Snd.play(this.player, "block.glass.hit", 1.0F, 0.6F);
            }
        }

        Location l = this.player.getLocation();
        if (Math.abs(l.getX() - this.top.getX()) > R + 0.7 || Math.abs(l.getZ() - this.top.getZ()) > R + 0.7 || l.getY() < this.top.getY() - 0.5 || l.getY() > this.top.getY() + ROOM) {
            Location back = this.top.clone();
            back.setYaw(l.getYaw());
            back.setPitch(l.getPitch());
            Tp.to(this.player, back);
        }

        this.player.setFallDistance(0.0F);
    }

    /** GO: the barriers go, the two halves swing apart and drop, the player falls safely. */
    void open() {
        if (this.done) {
            return;
        }

        this.done = true;
        for (Block b : this.barriers) {
            if (b.getType() == Material.BARRIER) {
                b.setType(Material.AIR, false);
            }
        }

        this.barriers.clear();
        for (int half = 0; half < 2; half++) {
            List<BlockDisplay> list = half == 0 ? this.left : this.right;
            float dir = half == 0 ? -1 : 1;
            for (BlockDisplay d : list) {
                if (!d.isValid()) {
                    continue;
                }

                Transformation t = d.getTransformation();
                Vector3f move = this.splitX ? new Vector3f(dir * 2.4f, -6.0f, 0) : new Vector3f(0, -6.0f, dir * 2.4f);
                AxisAngle4f tilt = this.splitX ? new AxisAngle4f(dir * -0.9f, 0, 0, 1) : new AxisAngle4f(dir * 0.9f, 1, 0, 0);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(18);
                d.setTransformation(new Transformation(new Vector3f(t.getTranslation()).add(move), tilt, new Vector3f(t.getScale()).mul(0.9f), new AxisAngle4f()));
            }
        }

        World w = this.top.getWorld();
        org.bukkit.Color col = this.color();
        // the orbiting shards fly outward and fall
        for (int i = 0; i < this.orbit.size(); i++) {
            BlockDisplay d = this.orbit.get(i);
            if (!d.isValid()) {
                continue;
            }

            double a = this.angle + i * Math.PI * 2 / this.orbit.size();
            Transformation t = d.getTransformation();
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(16);
            d.setTransformation(new Transformation(new Vector3f(t.getTranslation()).add((float)Math.cos(a) * 4f, -5f, (float)Math.sin(a) * 4f),
                new AxisAngle4f(6f, 1, 0, 1), new Vector3f(0.05f), new AxisAngle4f()));
        }

        // a burst of glass shards in every direction
        BlockData g = data(this.glass);
        Location corner = new Location(w, this.top.getBlockX(), this.top.getBlockY(), this.top.getBlockZ());
        for (int i = 0; i < 14; i++) {
            double a = Math.random() * Math.PI * 2;
            float up = (float)(Math.random() * 2.5 - 0.5);
            float dist = 3.0f + (float)Math.random() * 3.0f;
            BlockDisplay b = w.spawn(corner, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(g);
                d.setShadowRadius(0.0F);
                d.setTransformation(new Transformation(new Vector3f(0.4f, 1.4f, 0.4f), new AxisAngle4f(), new Vector3f(0.3f), new AxisAngle4f()));
            });
            this.burst.add(b);
            Tasks.later(1L, () -> {
                if (b.isValid()) {
                    b.setInterpolationDelay(0);
                    b.setInterpolationDuration(20);
                    b.setTransformation(new Transformation(new Vector3f(0.4f + (float)Math.cos(a) * dist, 1.4f + up - 3.5f, 0.4f + (float)Math.sin(a) * dist),
                        new AxisAngle4f((float)(Math.random() * 12), 1, 1, 1), new Vector3f(0.04f), new AxisAngle4f()));
                }
            });
        }

        if (this.ring != null && this.ring.isValid()) {
            this.ring.setInterpolationDelay(0);
            this.ring.setInterpolationDuration(10);
            this.ring.setTransformation(new Transformation(new Vector3f(0.5f - 4f, -0.45f, 0.5f - 4f), new AxisAngle4f(), new Vector3f(8f, 0.01f, 8f), new AxisAngle4f()));
        }

        w.spawnParticle(Particle.DUST, this.top.clone().add(0, 1.5, 0), 120, 2.2, 1.6, 2.2, 0, new Particle.DustOptions(col, 2.0F));
        w.spawnParticle(Particle.FIREWORK, this.top.clone().add(0, 1.5, 0), 50, 0.4, 0.4, 0.4, 0.25);
        w.spawnParticle(Particle.END_ROD, this.top.clone().add(0, 1.5, 0), 30, 0.2, 0.2, 0.2, 0.35);
        Snd.at(this.top, "entity.firework_rocket.large_blast", 0.9F, 1.0F);
        Snd.at(this.top, "block.amethyst_block.break", 1.0F, 0.8F);
        this.player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 12, 0, false, false, false));
        w.spawnParticle(Particle.BLOCK, this.top.clone().add(0, 1.5, 0), 60, 1.4, 1.2, 1.4, 0.0, data(this.glass));
        w.spawnParticle(Particle.CLOUD, this.top.clone().add(0, 0.1, 0), 8, 0.4, 0.05, 0.4, 0.02);
        Snd.at(this.top, "block.glass.break", 0.9F, 0.9F);
        Snd.at(this.top, "block.piston.contract", 0.7F, 1.4F);
        // a safe drop: the fall is forgiven until they land
        this.player.setFallDistance(0.0F);
        int[] left = {100}; // up to 5 s of falling from 20 blocks
        org.bukkit.Bukkit.getScheduler().runTaskTimer(net.vortexrealm.VortexRealm.get(), task -> {
            if (!this.player.isOnline() || --left[0] <= 0 || ((org.bukkit.entity.Entity)this.player).isOnGround()) {
                this.player.setFallDistance(0.0F);
                task.cancel();
                return;
            }

            this.player.setFallDistance(0.0F);
        }, 1L, 1L);
        Tasks.later(24L, this::removeDisplays);
    }

    private void removeDisplays() {
        for (BlockDisplay d : this.left) {
            d.remove();
        }

        for (BlockDisplay d : this.right) {
            d.remove();
        }

        this.left.clear();
        this.right.clear();
        for (BlockDisplay d : this.orbit) {
            d.remove();
        }

        for (BlockDisplay d : this.burst) {
            d.remove();
        }

        this.orbit.clear();
        this.burst.clear();
        if (this.ring != null) {
            this.ring.remove();
            this.ring = null;
        }
    }

    void remove() {
        this.done = true;
        for (Block b : this.barriers) {
            if (b.getType() == Material.BARRIER) {
                b.setType(Material.AIR, false);
            }
        }

        this.barriers.clear();
        this.removeDisplays();
    }
}
