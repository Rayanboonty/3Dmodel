package net.practicepvp.match;

import java.util.LinkedHashMap;
import java.util.Map;
import net.practicepvp.arena.ChangeTracker;
import net.practicepvp.session.Session;
import net.practicepvp.session.Tp;
import net.practicepvp.util.Tasks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * The PvP HQ start, from real blocks: each player waits in a dark brick pod hanging on chains above their spawn, a
 * lantern inside and a window toward the opponent. PUNCH TO READY UP: when everyone has punched, the count drops to 3.
 * At GO the pod breaks apart (block particles, sounds) and the player drops onto the map with no fall damage. Every
 * block is put back afterwards.
 */
final class Capsule {
    /** The pod sits up to MAX_HEIGHT above the spawn, lower when the map has a roof or trees above. */
    static final int MAX_HEIGHT = 12;
    static final int MIN_HEIGHT = 4;
    /** Inner room: the player stands in the middle of a 3x3 pod. */
    static final int R = 1;
    static final int ROOM = 3;
    private final Player player;
    private final Session session;
    private final Location top;
    private final String glass;
    private final ChangeTracker tracker;
    private final Map<Block, BlockData> placed = new LinkedHashMap<>();
    private int lastSecond = -1;
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
        }

        this.face(lookAt);
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

    private void put(int dx, int dy, int dz, BlockData data) {
        Block b = this.top.getWorld().getBlockAt(this.top.getBlockX() + dx, this.top.getBlockY() + dy, this.top.getBlockZ() + dz);
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
    /** Front of the pod (toward the opponent). */
    private int fx;
    private int fz = 1;

    private void face(Location lookAt) {
        if (lookAt == null) {
            return;
        }

        double dx = lookAt.getX() - this.top.getX();
        double dz = lookAt.getZ() - this.top.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            this.fx = dx > 0 ? 1 : -1;
            this.fz = 0;
        } else {
            this.fx = 0;
            this.fz = dz > 0 ? 1 : -1;
        }
    }

    private boolean window(int dx, int dz) {
        return dx == this.fx && dz == this.fz || dx == -this.fx && dz == -this.fz;
    }

    private static BlockData lantern(Material m) {
        org.bukkit.block.data.type.Lantern l = (org.bukkit.block.data.type.Lantern)m.createBlockData();
        l.setHanging(true);
        return l;
    }

    void begin() {
        // a hanging pod of dark bricks: 3x3 outside, a 1x1 room three blocks high, a window toward the opponent and
        // behind (an invisible wall holds the player), a lantern inside, chains up from the roof
        org.bukkit.block.data.type.Slab lintel = (org.bukkit.block.data.type.Slab)Material.DEEPSLATE_BRICK_SLAB.createBlockData();
        lintel.setType(org.bukkit.block.data.type.Slab.Type.TOP);
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                boolean corner = Math.abs(dx) == R && Math.abs(dz) == R;
                this.put(dx, -1, dz, dx == 0 && dz == 0 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_DEEPSLATE);
                this.put(dx, 3, dz, dx == 0 && dz == 0 ? Material.DEEPSLATE_TILES : Material.POLISHED_BLACKSTONE_BRICKS);
                if (dx == 0 && dz == 0) {
                    continue;
                }

                for (int y = 0; y <= 2; y++) {
                    if (this.window(dx, dz)) {
                        if (y == 2) {
                            this.put(dx, y, dz, lintel);
                        } else {
                            this.put(dx, y, dz, Material.BARRIER);
                        }
                    } else {
                        this.put(dx, y, dz, corner ? Material.DEEPSLATE_TILES : Material.POLISHED_BLACKSTONE_BRICKS);
                    }
                }
            }
        }

        this.put(0, 2, 0, lantern(Material.LANTERN));
        Material chain = Material.matchMaterial("iron_chain") != null ? Material.matchMaterial("iron_chain") : Material.matchMaterial("chain");
        for (int y = 4; y <= 9; y++) {
            this.put(-R, y, -R, chain);
            this.put(R, y, R, chain);
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
        this.top.getWorld().getBlockAt(this.top.getBlockX(), this.top.getBlockY() + 2, this.top.getBlockZ()).setBlockData(lantern(Material.SOUL_LANTERN), false);
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

    /** GO: the pod breaks apart and the player drops onto the map. */
    void open() {
        if (this.done) {
            return;
        }

        this.done = true;
        World w = this.top.getWorld();
        for (Map.Entry<Block, BlockData> e : this.placed.entrySet()) {
            Block b = e.getKey();
            BlockData was = b.getBlockData();
            if (was.getMaterial() != Material.BARRIER && !was.getMaterial().isAir()) {
                w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0.0, was);
            }

            b.setBlockData(e.getValue(), false);
        }

        this.placed.clear();
        w.playSound(this.top, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1.0F, 0.8F);
        w.playSound(this.top, Sound.BLOCK_CHAIN_BREAK, 0.8F, 0.9F);
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
        }.runTaskTimer(net.practicepvp.PracticePlugin.get(), 1L, 1L);
    }

    /** Takes the pod down without the effects (match cancelled). */
    void remove() {
        this.done = true;
        for (Map.Entry<Block, BlockData> e : this.placed.entrySet()) {
            e.getKey().setBlockData(e.getValue(), false);
        }

        this.placed.clear();
    }
}
