package net.cmcpvp.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.cmcpvp.arena.ChangeTracker;
import net.cmcpvp.session.Session;
import net.cmcpvp.session.Tp;
import net.cmcpvp.util.Tasks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The PvP HQ start, from real blocks: each player waits in a glass pod hanging on chains above their spawn - a 3x3 room
 * four blocks high with clear glass on all four sides (the whole map can be seen), dark posts in the corners, a band
 * in the team's colour, a tiled roof with a lantern. PUNCH TO READY UP: when everyone has punched, the count drops to
 * 3. At GO the floor swings open like two trapdoors, the glass shatters outward and the roof lifts away (block
 * displays animate it while the real blocks are already gone), and the player drops onto the map with no fall damage.
 * Every block is put back afterwards.
 */
final class Capsule {
    /** The pod sits up to MAX_HEIGHT above the spawn, lower when the map has a roof or trees above. */
    static final int MAX_HEIGHT = 12;
    static final int MIN_HEIGHT = 4;
    /** Outer half-size: the pod is 5x5 outside, the room 3x3 inside. */
    static final int R = 2;
    /** Room height (the roof is at ROOM). */
    static final int ROOM = 4;
    private static final int SWING = 8;
    private static final int CLEAN = 24;
    private final Player player;
    private final Session session;
    private final Location top;
    private final Material band;
    private final ChangeTracker tracker;
    private final Map<Block, BlockData> placed = new LinkedHashMap<>();
    private final List<BlockDisplay> shards = new ArrayList<>();
    private int lastSecond = -1;
    private boolean done;

    Capsule(Player player, Session session, Location spawn, Location lookAt, String glass, ChangeTracker tracker) {
        this.player = player;
        this.session = session;
        this.top = spawn.clone().add(0, clearance(spawn), 0);
        this.top.setX(spawn.getBlockX() + 0.5);
        this.top.setZ(spawn.getBlockZ() + 0.5);
        Material m = glass == null ? null : Material.matchMaterial(glass.replace("_pane", ""));
        this.band = m != null && m.isBlock() ? m : Material.GRAY_STAINED_GLASS;
        this.tracker = tracker;
        if (lookAt != null) {
            Elevator.face(this.top, lookAt);
        }
    }

    /** How high the pod can go above this spawn: up to 12 blocks, stopping under anything solid. */
    static int clearance(Location spawn) {
        World w = spawn.getWorld();
        int bx = spawn.getBlockX();
        int by = spawn.getBlockY();
        int bz = spawn.getBlockZ();
        int need = ROOM + 2;
        for (int h = MAX_HEIGHT; h >= MIN_HEIGHT; h--) {
            boolean free = true;
            for (int dy = -1; dy <= need && free; dy++) {
                for (int dx = -R; dx <= R && free; dx++) {
                    for (int dz = -R; dz <= R && free; dz++) {
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

    private Block at(int dx, int dy, int dz) {
        return this.top.getWorld().getBlockAt(this.top.getBlockX() + dx, this.top.getBlockY() + dy, this.top.getBlockZ() + dz);
    }

    private void put(int dx, int dy, int dz, BlockData data) {
        Block b = this.at(dx, dy, dz);
        if (!this.placed.containsKey(b)) {
            this.placed.put(b, b.getBlockData());
            if (this.tracker != null) {
                this.tracker.record(b);
            }
        }

        b.setBlockData(data, false);
    }

    private void put(int dx, int dy, int dz, Material m) {
        this.put(dx, dy, dz, m.createBlockData());
    }

    /** Ready (punched). */
    boolean ready;

    private static BlockData lantern(Material m) {
        org.bukkit.block.data.type.Lantern l = (org.bukkit.block.data.type.Lantern)m.createBlockData();
        l.setHanging(true);
        return l;
    }

    void begin() {
        // floor: a dark rim round a polished 3x3 with a light in the middle
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                boolean rim = Math.abs(dx) == R || Math.abs(dz) == R;
                boolean corner = Math.abs(dx) == R && Math.abs(dz) == R;
                this.put(dx, -1, dz, dx == 0 && dz == 0 ? Material.SEA_LANTERN : rim ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_DEEPSLATE);
                // roof: tiles on a rim of bricks
                this.put(dx, ROOM, dz, rim ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_TILES);
                if (!rim) {
                    continue;
                }

                for (int y = 0; y < ROOM; y++) {
                    if (corner) {
                        this.put(dx, y, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    } else {
                        // clear glass all round (the player sees every direction), a band of the team colour on top
                        this.put(dx, y, dz, y == ROOM - 1 ? this.band : Material.GLASS);
                    }
                }
            }
        }

        this.put(0, ROOM - 1, 0, lantern(Material.LANTERN));
        Material chain = Material.matchMaterial("iron_chain") != null ? Material.matchMaterial("iron_chain") : Material.matchMaterial("chain");
        for (int y = ROOM + 1; y <= ROOM + 6; y++) {
            this.put(-R, y, -R, chain);
            this.put(R, y, R, chain);
            this.put(-R, y, R, chain);
            this.put(R, y, -R, chain);
        }

        this.player.setFallDistance(0.0F);
        Tp.to(this.player, this.top);
        this.player.playSound(this.top, Sound.BLOCK_CHAIN_PLACE, 0.8F, 0.8F);
    }

    Player player() {
        return this.player;
    }

    /** Punched: ready - the lantern turns into a soul lantern. */
    void markReady() {
        if (this.ready || this.done) {
            return;
        }

        this.ready = true;
        this.at(0, ROOM - 1, 0).setBlockData(lantern(Material.SOUL_LANTERN), false);
    }

    /** The last three seconds: a tick each second. */
    void tick(double progress, int ticksLeft) {
        if (this.done) {
            return;
        }

        int second = (ticksLeft + 19) / 20;
        if (second != this.lastSecond && second >= 1 && second <= 3) {
            this.player.playSound(this.top, Sound.BLOCK_NOTE_BLOCK_HAT, 0.9F, 1.0F + (3 - second) * 0.2F);
        }

        this.lastSecond = second;
    }

    /**
     * GO: the real blocks go back at once (so the player falls), and block displays play the opening: the floor
     * swings down as two trapdoors hinged on its west and east edges, the glass flies outward and shrinks, the roof
     * and posts lift away.
     */
    void open() {
        if (this.done) {
            return;
        }

        this.done = true;
        World w = this.top.getWorld();
        int bx = this.top.getBlockX();
        int by = this.top.getBlockY();
        int bz = this.top.getBlockZ();
        for (Map.Entry<Block, BlockData> e : this.placed.entrySet()) {
            Block b = e.getKey();
            BlockData was = b.getBlockData();
            int dx = b.getX() - bx;
            int dy = b.getY() - by;
            int dz = b.getZ() - bz;
            b.setBlockData(e.getValue(), false);
            Material m = was.getMaterial();
            if (m.isAir() || m == Material.BARRIER || m.name().endsWith("CHAIN") || m.name().endsWith("LANTERN")) {
                continue;
            }

            if (dy == -1) {
                this.floorPiece(w, b, was, dx);
            } else if (m == Material.GLASS || m == this.band) {
                w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.0, was);
                this.fly(w, b, was, Integer.signum(dx) * 1.6F, 0.4F, Integer.signum(dz) * 1.6F, 10);
            } else {
                this.fly(w, b, was, dx * 0.35F, 2.2F, dz * 0.35F, 14);
            }
        }

        this.placed.clear();
        w.playSound(this.top, Sound.BLOCK_GLASS_BREAK, 1.0F, 0.9F);
        w.playSound(this.top, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0F, 0.6F);
        w.playSound(this.top, Sound.BLOCK_CHAIN_BREAK, 0.8F, 0.9F);
        Tasks.later(CLEAN, () -> {
            for (BlockDisplay d : this.shards) {
                if (d.isValid()) {
                    d.remove();
                }
            }

            this.shards.clear();
        });
        // a safe drop: the fall is forgiven until they land (up to 5 s)
        int[] left = {100};
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                Capsule.this.player.setFallDistance(0.0F);
                if (!Capsule.this.player.isOnline() || left[0]-- <= 0 || Capsule.this.player.isOnGround()) {
                    this.cancel();
                }
            }
        }.runTaskTimer(net.cmcpvp.CMCPvP.get(), 1L, 1L);
    }

    private BlockDisplay show(World w, Block b, BlockData data) {
        BlockDisplay d = w.spawn(b.getLocation(), BlockDisplay.class, e -> {
            e.setPersistent(false);
            e.setBlock(data);
            e.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        });
        this.shards.add(d);
        return d;
    }

    /**
     * A floor block swings down with its trapdoor: blocks west of the middle turn round the west edge, east of it
     * round the east edge (the middle row drops straight down).
     */
    private void floorPiece(World w, Block b, BlockData data, int dx) {
        BlockDisplay d = this.show(w, b, data);
        Tasks.later(1L, () -> {
            if (!d.isValid()) {
                return;
            }

            Transformation t;
            if (dx == 0) {
                t = new Transformation(new Vector3f(0.0F, -2.5F, 0.0F), new Quaternionf(), new Vector3f(0.6F), new Quaternionf());
            } else {
                // hinge on the floor's outer edge, at the top of the block; turn 90 degrees down about the z axis
                float angle = (float)Math.toRadians(dx < 0 ? -90 : 90);
                Quaternionf q = new Quaternionf(new AxisAngle4f(angle, 0.0F, 0.0F, 1.0F));
                Vector3f pivot = new Vector3f(dx < 0 ? -(R + dx) : R + 1 - dx, 1.0F, 0.0F);
                Vector3f turned = q.transform(new Vector3f(pivot));
                Vector3f shift = new Vector3f(pivot).sub(turned);
                t = new Transformation(shift, q, new Vector3f(1.0F), new Quaternionf());
            }

            d.setInterpolationDelay(0);
            d.setInterpolationDuration(SWING);
            d.setTransformation(t);
        });
    }

    /** A wall, roof or post piece flies off (by dx, dy, dz blocks) and shrinks away. */
    private void fly(World w, Block b, BlockData data, float mx, float my, float mz, int ticks) {
        BlockDisplay d = this.show(w, b, data);
        Tasks.later(1L, () -> {
            if (!d.isValid()) {
                return;
            }

            Quaternionf spin = new Quaternionf(new AxisAngle4f((float)Math.toRadians(35), 0.3F, 1.0F, 0.2F));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(new Transformation(new Vector3f(mx + 0.4F, my + 0.4F, mz + 0.4F), spin, new Vector3f(0.2F), new Quaternionf()));
        });
    }

    /** Takes the pod down without the effects (match cancelled). */
    void remove() {
        this.done = true;
        for (Map.Entry<Block, BlockData> e : this.placed.entrySet()) {
            e.getKey().setBlockData(e.getValue(), false);
        }

        this.placed.clear();
        for (BlockDisplay d : this.shards) {
            if (d.isValid()) {
                d.remove();
            }
        }

        this.shards.clear();
    }
}
