package net.vortexrealm.match;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.vortexrealm.arena.ChangeTracker;
import net.vortexrealm.session.PlayerReset;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Snd;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.util.BoundingBox;

final class Elevator {
    private final Player player;
    private final Session session;
    private final Location top;
    private final Location lookAt;
    private final Player opponent;
    private final int depth;
    private final ChangeTracker tracker;
    private final Map<Block, BlockData> removed = new LinkedHashMap<>();
    private final List<BlockDisplay> blocks = new ArrayList<>();
    private static final NamespacedKey LIFT = new NamespacedKey("vortexrealm", "elevator_lift");
    private static final double DRAG = 0.98;
    private double lastG;
    private double expectedVy;
    private boolean hadFlight;
    private final ArrayDeque<Double> history = new ArrayDeque<>();
    private int groundY;
    private int ticks;
    private boolean finished;

    Elevator(Player player, Session session, Location top, Location lookAt, Player opponent, int depth, ChangeTracker tracker) {
        this.player = player;
        this.session = session;
        this.opponent = opponent;
        this.top = top.clone();
        this.top.setX(top.getBlockX() + 0.5);
        this.top.setZ(top.getBlockZ() + 0.5);
        this.lookAt = lookAt;
        this.depth = Math.max(3, depth);
        this.tracker = tracker;
        if (lookAt != null) {
            face(this.top, lookAt);
        }
    }

    static void face(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        from.setYaw((float)Math.toDegrees(Math.atan2(-dx, dz)));
        from.setPitch((float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))));
    }

    void begin() {
        World w = this.top.getWorld();
        int bx = this.top.getBlockX();
        int bz = this.top.getBlockZ();
        this.groundY = this.top.getBlockY() - 1;
        int startY = this.groundY - this.depth;
        BlockData[][] ground = new BlockData[3][3];
        Block centre = w.getBlockAt(bx, this.groundY, bz);
        BlockData filler = centre.getType().isSolid() ? centre.getBlockData() : Material.STONE.createBlockData();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block g = w.getBlockAt(bx + dx, this.groundY, bz + dz);
                ground[dx + 1][dz + 1] = g.getType().isSolid() ? g.getBlockData() : filler;
            }
        }

        for (int y = startY; y <= this.groundY + 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = w.getBlockAt(bx + dx, y, bz + dz);
                    if (!b.getType().isAir()) {
                        if (this.tracker != null) {
                            this.tracker.record(b);
                        }

                        this.removed.put(b, b.getBlockData());
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dzx = -1; dzx <= 1; dzx++) {
                BlockData data = ground[dx + 1][dzx + 1];
                Location at = new Location(w, bx + dx, startY, bz + dzx);
                this.blocks.add(w.spawn(at, BlockDisplay.class, d -> {
                    d.setBlock(data);
                    d.setTeleportDuration(2);
                    d.setPersistent(false);
                }));
            }
        }

        Location start = new Location(w, this.top.getX(), startY + 1, this.top.getZ(), this.top.getYaw(), this.top.getPitch());
        Tp.to(this.player, start);
        PlayerReset.noJump(this.player);
        this.session.noGravity = true;
        this.hadFlight = this.player.getAllowFlight();
        this.player.setAllowFlight(true);
        this.player.setFlying(false);
        this.lastG = this.groundY - this.depth;
        this.expectedVy = 0.0;
        this.setLift(0.0);
        Snd.play(this.player, "block.piston.extend", 0.9F, 0.6F);
        Snd.play(this.player, "block.grindstone.use", 0.5F, 0.6F);
    }

    void tick(double progress) {
        if (!this.finished && this.player.isOnline()) {
            this.ticks++;
            double t = Math.max(0.0, Math.min(1.0, progress));
            double eased = t * t * (3.0 - 2.0 * t);
            double g = this.groundY - this.depth + this.depth * eased;
            World w = this.top.getWorld();
            int i = 0;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockDisplay d = this.blocks.get(i++);
                    if (d.isValid()) {
                        d.teleport(new Location(w, this.top.getBlockX() + dx, g, this.top.getBlockZ() + dz));
                    }
                }
            }

            this.drive(g);
            this.keepOnPlatform(g);
            if (this.ticks % 3 == 0) {
                w.spawnParticle(Particle.FALLING_DUST, this.top.getX(), g - 0.05, this.top.getZ(), 3, 0.9, 0.0, 0.9, 0.0, this.blocks.get(4).getBlock());
            }

            if (this.ticks % 20 == 0) {
                Snd.at(new Location(w, this.top.getX(), g, this.top.getZ()), "block.piston.extend", 0.45F, 0.6F + (float)t * 0.6F);
            }
        }
    }

    private void drive(double g) {
        double step = g - this.lastG;
        this.lastG = g;
        this.history.addLast(g);
        int lag = Math.max(1, Math.min(20, Math.round(this.player.getPing() / 50.0F) + 1));

        while (this.history.size() > lag + 1) {
            this.history.removeFirst();
        }

        double feetThen = this.history.peekFirst() + 1.0;
        double error = feetThen - this.player.getLocation().getY();
        double correction = Math.abs(error) > 2.5 ? 0.0 : Math.max(-0.08, Math.min(0.08, error * 0.12));
        double wantVy = Math.max(-0.3, Math.min(0.6, step + correction));
        double gravity = this.expectedVy - wantVy / 0.98;
        this.setLift(Math.max(-0.9, Math.min(0.9, gravity)));
        this.expectedVy = wantVy;
        if (Math.abs(error) > 2.5) {
            Location l = this.player.getLocation();
            l.setY(g + 1.0);
            Tp.to(this.player, l);
            this.expectedVy = 0.0;
        }
    }

    private void keepOnPlatform(double g) {
        Location l = this.player.getLocation();
        double dx = l.getX() - this.top.getX();
        double dz = l.getZ() - this.top.getZ();
        double lim = 1.2;
        if (Math.abs(dx) > lim + 0.25 || Math.abs(dz) > lim + 0.25) {
            l.setX(this.top.getX() + Math.max(-lim, Math.min(lim, dx)));
            l.setZ(this.top.getZ() + Math.max(-lim, Math.min(lim, dz)));
            l.setY(Math.max(l.getY(), g + 1.0));
            Tp.to(this.player, l);
            this.expectedVy = 0.0;
        }
    }

    private void setLift(double gravity) {
        AttributeInstance a = this.player.getAttribute(Attribute.GRAVITY);
        if (a != null) {
            a.removeModifier(LIFT);
            double base = a.getValue();
            a.addTransientModifier(new AttributeModifier(LIFT, gravity - base, Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }

    private void endLift() {
        AttributeInstance a = this.player.getAttribute(Attribute.GRAVITY);
        if (a != null) {
            a.removeModifier(LIFT);
        }

        this.player.setFlying(false);
        this.player.setAllowFlight(this.hadFlight);
    }

    static boolean riding(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.GRAVITY);
        return a != null && a.getModifier(LIFT) != null;
    }

    static void clear(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.GRAVITY);
        if (a != null) {
            a.removeModifier(LIFT);
        }
    }

    private void restoreBlocks() {
        for (Entry<Block, BlockData> e : this.removed.entrySet()) {
            e.getKey().setBlockData(e.getValue(), false);
        }

        this.removed.clear();
    }

    private void removeEntities() {
        for (BlockDisplay d : this.blocks) {
            d.remove();
        }

        this.blocks.clear();
    }

    void arrive() {
        if (!this.finished) {
            this.finished = true;
            this.session.noGravity = false;
            this.removeEntities();
            this.endLift();
            this.restoreBlocks();
            if (this.player.isOnline()) {
                Location now = this.player.getLocation();
                double dx = now.getX() - this.top.getX();
                double dz = now.getZ() - this.top.getZ();
                Location dest = null;
                if (!(now.getY() > this.top.getY() + 1.5) && !(dx * dx + dz * dz > 4.0)) {
                    if (now.getY() < this.top.getY() - 0.001) {
                        dest = now.clone();
                        dest.setY(this.top.getY());
                    }
                } else {
                    dest = this.top.clone();
                }

                Location check = dest != null ? dest : now;
                if (this.blocked(check)) {
                    dest = this.top.clone();
                }

                if (dest != null) {
                    for (int up = 0; up < 6 && this.blocked(dest); up++) {
                        dest.add(0.0, 1.0, 0.0);
                    }

                    dest.setYaw(now.getYaw());
                    dest.setPitch(now.getPitch());
                    Tp.to(this.player, dest);
                }

                World w = this.top.getWorld();
                w.spawnParticle(
                    Particle.BLOCK,
                    this.top.clone().add(0.0, 0.1, 0.0),
                    40,
                    1.2,
                    0.1,
                    1.2,
                    0.0,
                    w.getBlockAt(this.top.getBlockX(), this.groundY, this.top.getBlockZ()).getBlockData()
                );
                w.spawnParticle(Particle.CLOUD, this.top.clone().add(0.0, 0.1, 0.0), 12, 1.0, 0.05, 1.0, 0.02);
                Snd.at(this.top, "block.piston.contract", 0.9F, 1.1F);
                Snd.at(this.top, "block.stone.place", 1.0F, 0.8F);
            }
        }
    }

    private boolean blocked(Location feet) {
        World w = feet.getWorld();
        BoundingBox body = new BoundingBox(
            feet.getX() - 0.299, feet.getY() + 0.001, feet.getZ() - 0.299, feet.getX() + 0.299, feet.getY() + 1.799, feet.getZ() + 0.299
        );

        for (int x = (int)Math.floor(body.getMinX()); x <= (int)Math.floor(body.getMaxX()); x++) {
            for (int y = (int)Math.floor(body.getMinY()); y <= (int)Math.floor(body.getMaxY()); y++) {
                for (int z = (int)Math.floor(body.getMinZ()); z <= (int)Math.floor(body.getMaxZ()); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.isPassable()) {
                        for (BoundingBox part : b.getCollisionShape().getBoundingBoxes()) {
                            if (part.shift(x, y, z).overlaps(body)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    void cancel() {
        if (!this.finished) {
            this.finished = true;
            this.session.noGravity = false;
            this.removeEntities();
            this.restoreBlocks();
            this.endLift();
        }
    }
}
