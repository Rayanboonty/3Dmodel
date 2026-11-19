package net.practicepvp.match;

import java.util.ArrayList;
import java.util.List;
import net.practicepvp.arena.ChangeTracker;
import net.practicepvp.session.Session;
import net.practicepvp.session.Tp;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
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
    static final int HEIGHT = 4;
    private final Player player;
    private final Session session;
    private final Location top;
    private final String glass;
    private final ChangeTracker tracker;
    private final List<Block> barriers = new ArrayList<>();
    private final List<BlockDisplay> left = new ArrayList<>();
    private final List<BlockDisplay> right = new ArrayList<>();
    /** The halves split along x (true) or z. */
    private boolean splitX;
    private boolean done;

    Capsule(Player player, Session session, Location spawn, Location lookAt, String glass, ChangeTracker tracker) {
        this.player = player;
        this.session = session;
        this.top = spawn.clone().add(0, HEIGHT, 0);
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

    void begin() {
        World w = this.top.getWorld();
        int bx = this.top.getBlockX();
        int by = this.top.getBlockY();
        int bz = this.top.getBlockZ();
        // the invisible box that holds the player: floor, walls two high, roof
        this.barrier(w.getBlockAt(bx, by - 1, bz));
        this.barrier(w.getBlockAt(bx, by + 2, bz));
        for (int y = 0; y <= 1; y++) {
            this.barrier(w.getBlockAt(bx + 1, by + y, bz));
            this.barrier(w.getBlockAt(bx - 1, by + y, bz));
            this.barrier(w.getBlockAt(bx, by + y, bz + 1));
            this.barrier(w.getBlockAt(bx, by + y, bz - 1));
        }

        Location corner = new Location(w, bx, by, bz);
        BlockData glassData = data(this.glass);
        BlockData base = data("polished_blackstone");
        BlockData cap = data("smooth_quartz");
        // each half: a glass wall box, a dark base and a light cap; the split runs through the middle
        for (int half = 0; half < 2; half++) {
            List<BlockDisplay> list = half == 0 ? this.left : this.right;
            float a0 = half == 0 ? -0.35f : 0.5f;
            list.add(this.part(corner, glassData, a0, -0.05f, 0.85f, 2.2f));
            list.add(this.part(corner, base, a0, -0.3f, 0.85f, 0.25f));
            list.add(this.part(corner, cap, a0, 2.15f, 0.85f, 0.2f));
        }

        Tp.to(this.player, this.top);
        this.player.setFallDistance(0.0F);
        Snd.play(this.player, "block.beacon.activate", 0.6F, 1.6F);
    }

    private static BlockData data(String s) {
        try {
            return org.bukkit.Bukkit.createBlockData("minecraft:" + s);
        } catch (IllegalArgumentException e) {
            return Material.GLASS.createBlockData();
        }
    }

    private BlockDisplay part(Location corner, BlockData data, float a0, float y0, float aw, float h) {
        float ax = this.splitX ? a0 : -0.35f;
        float az = this.splitX ? -0.35f : a0;
        float sx = this.splitX ? aw : 1.7f;
        float sz = this.splitX ? 1.7f : aw;
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

    /** Keeps the player inside while the count runs (a lag or a push can't take them out). */
    void tick() {
        if (this.done || !this.player.isOnline()) {
            return;
        }

        Location l = this.player.getLocation();
        if (Math.abs(l.getX() - this.top.getX()) > 0.6 || Math.abs(l.getZ() - this.top.getZ()) > 0.6 || l.getY() < this.top.getY() - 0.5 || l.getY() > this.top.getY() + 1.5) {
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
                Vector3f move = this.splitX ? new Vector3f(dir * 1.6f, -3.2f, 0) : new Vector3f(0, -3.2f, dir * 1.6f);
                AxisAngle4f tilt = this.splitX ? new AxisAngle4f(dir * -0.9f, 0, 0, 1) : new AxisAngle4f(dir * 0.9f, 1, 0, 0);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(14);
                d.setTransformation(new Transformation(new Vector3f(t.getTranslation()).add(move), tilt, new Vector3f(t.getScale()).mul(0.9f), new AxisAngle4f()));
            }
        }

        World w = this.top.getWorld();
        w.spawnParticle(Particle.BLOCK, this.top.clone().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.0, data(this.glass));
        w.spawnParticle(Particle.CLOUD, this.top.clone().add(0, 0.1, 0), 8, 0.4, 0.05, 0.4, 0.02);
        Snd.at(this.top, "block.glass.break", 0.9F, 0.9F);
        Snd.at(this.top, "block.piston.contract", 0.7F, 1.4F);
        // a safe drop: the fall is forgiven until they land
        this.player.setFallDistance(0.0F);
        int[] left = {30};
        org.bukkit.Bukkit.getScheduler().runTaskTimer(net.practicepvp.PracticePlugin.get(), task -> {
            if (!this.player.isOnline() || --left[0] <= 0 || ((org.bukkit.entity.Entity)this.player).isOnGround()) {
                this.player.setFallDistance(0.0F);
                task.cancel();
                return;
            }

            this.player.setFallDistance(0.0F);
        }, 1L, 1L);
        Tasks.later(16L, this::removeDisplays);
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
