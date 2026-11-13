package net.vortexrealm.match;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntFunction;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.fx.anim.Anim;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One viewer's live 3D miniature of a map: a block display per {@link MapModel.Part}, all standing on one shared pivot.
 * The entity rotation (the same yaw and pitch on every part) turns the model toward the viewer and tilts its top into
 * view; the parts' transformations carry their place in the model, its size and its turn around its own up axis, so the
 * whole thing moves as one rigid piece.
 *
 * <p>Animation is keyframed: every {@link #STEP} ticks, while something changes, each part gets one teleport (position
 * and facing) and/or one transformation, with the client interpolating over a tick longer than the step. Eased motion
 * becomes a few short straight segments, and a model at rest sends nothing at all.
 */
final class MapModelView {
    /** Ticks between keyframes. */
    static final int STEP = 3;
    /** Idle turn around the model's own up axis, so two of its sides show. */
    static final float IDLE_TURN = (float)Math.toRadians(35.0);
    /** How far the top tilts toward the viewer. */
    static final float TILT = 28.0F;
    private final VortexRealm plugin;
    private final World world;
    private final Player viewer;
    private final MapModel model;
    private final Vector eye;
    private final IntFunction<Vector> anchor;
    private final int spawnAt;
    private final List<BlockDisplay> parts = new ArrayList<>();
    private boolean spawned;
    private boolean dead;
    private int removeAt = -1;
    private int born;
    private int nextSend;
    private float sFrom;
    private float sTo;
    private int st0;
    private int sdur = 1;
    private DoubleUnaryOperator sEase = Anim::easeOutCubic;
    // turn around the model's up axis: spinning (angle0 + ramped speed) or settling (eased to a resting angle)
    private boolean spinning;
    private float a0 = IDLE_TURN;
    private float a1 = IDLE_TURN;
    private int at0;
    private int adur = 1;
    private float speed;
    private Vector sentPos;
    private float sentYaw;
    private float sentSize;
    private float sentTurn;

    /**
     * @param anchor where the middle of the model is at a tick
     * @param spawnAt tick the parts appear at (spawns are spread out so the client never gets them all at once)
     */
    MapModelView(VortexRealm plugin, World world, Player viewer, MapModel model, Vector eye, IntFunction<Vector> anchor, int spawnAt) {
        this.plugin = plugin;
        this.world = world;
        this.viewer = viewer;
        this.model = model;
        this.eye = eye.clone();
        this.anchor = anchor;
        this.spawnAt = spawnAt;
    }

    boolean alive() {
        return !this.dead && this.removeAt < 0;
    }

    // ------------------------------------------------------------------ size

    float size(int t) {
        if (t >= this.st0 + this.sdur) {
            return this.sTo;
        } else {
            double k = this.sEase.applyAsDouble(Anim.clamp01((double)(t - this.st0) / this.sdur));
            return (float)(this.sFrom + (this.sTo - this.sFrom) * k);
        }
    }

    float sizeTarget() {
        return this.sTo;
    }

    void sizeTo(float size, int now, int ticks) {
        this.sizeTo(size, now, ticks, Anim::easeOutCubic);
    }

    void sizeTo(float size, int now, int ticks, DoubleUnaryOperator ease) {
        if (this.alive()) {
            this.sFrom = this.size(now);
            this.sTo = size;
            this.st0 = now;
            this.sdur = Math.max(1, ticks);
            this.sEase = ease;
            this.nextSend = Math.min(this.nextSend, now);
        }
    }

    // ------------------------------------------------------------------ turning

    /** Angle around the model's up axis at tick t. */
    float turn(int t) {
        int d = t - this.at0;
        if (this.spinning) {
            // speed ramps up over the first 10 ticks
            float ramp = 10.0F;
            return d < ramp ? this.a0 + this.speed * d * d / (2.0F * ramp) : this.a0 + this.speed * (d - ramp / 2.0F);
        } else if (d >= this.adur) {
            return this.a1;
        } else {
            double k = Anim.clamp01((double)d / this.adur);
            // ease-out: starts at the speed it had, ends at rest
            return (float)(this.a0 + (this.a1 - this.a0) * (1.0 - (1.0 - k) * (1.0 - k)));
        }
    }

    private float turnSpeed(int t) {
        int d = t - this.at0;
        if (this.spinning) {
            return d < 10 ? this.speed * d / 10.0F : this.speed;
        } else {
            return d >= this.adur ? 0.0F : (float)(2.0 * (this.a1 - this.a0) / this.adur * (1.0 - (double)d / this.adur));
        }
    }

    /** Starts turning at {@code degPerSec}, or (false) slows down to the nearest quarter-turn resting pose. */
    void spin(boolean on, float degPerSec, int now) {
        if (this.alive() && on != this.spinning) {
            float a = this.turn(now);
            float v = this.turnSpeed(now);
            this.at0 = now;
            this.a0 = a;
            if (on) {
                this.spinning = true;
                this.speed = (float)Math.toRadians(degPerSec) / 20.0F;
            } else {
                this.spinning = false;
                float quarter = (float)(Math.PI / 2.0);
                float min = a + Math.max(0.0F, v) * 6.0F;
                float rest = IDLE_TURN + (float)Math.ceil((min - IDLE_TURN) / quarter) * quarter;
                this.a1 = rest;
                this.adur = v > 1.0E-4F ? Math.max(6, Math.min(40, Math.round(2.0F * (rest - a) / v))) : 10;
            }

            this.nextSend = Math.min(this.nextSend, now);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** Shrinks away and removes itself. */
    void collapse(int now, int ticks) {
        if (this.alive()) {
            this.sizeTo(0.0F, now, ticks, Anim::easeInCubic);
            this.removeAt = now + ticks + STEP;
        }
    }

    void tick(int now) {
        if (this.dead) {
            return;
        }

        if (this.removeAt >= 0 && now >= this.removeAt) {
            this.remove();
            return;
        }

        if (!this.spawned) {
            if (now < this.spawnAt) {
                return;
            }

            this.spawn(now);
        }

        // the client must know a part before it can interpolate it
        if (now < this.born + 2 || now < this.nextSend) {
            return;
        }

        this.nextSend = now + STEP;
        int t = now + STEP;
        Vector pos = this.anchor.apply(t);
        float yaw = this.facing(pos);
        float size = this.size(t);
        float turn = this.turn(t);
        if (this.sentPos == null || pos.distanceSquared(this.sentPos) > 1.0E-6 || Math.abs(yaw - this.sentYaw) > 0.05F) {
            Location loc = new Location(this.world, pos.getX(), pos.getY(), pos.getZ(), yaw, TILT);

            for (BlockDisplay d : this.parts) {
                if (d.isValid()) {
                    d.teleport(loc);
                }
            }

            this.sentPos = pos;
            this.sentYaw = yaw;
        }

        if (Math.abs(size - this.sentSize) > 1.0E-4F || Math.abs(turn - this.sentTurn) > 1.0E-4F) {
            this.pose(size, turn, STEP + 1);
            this.sentSize = size;
            this.sentTurn = turn;
        }
    }

    private void spawn(int now) {
        this.spawned = true;
        this.born = now;
        World w = this.world;
        Vector pos = this.anchor.apply(now);
        float yaw = this.facing(pos);
        Location loc = new Location(w, pos.getX(), pos.getY(), pos.getZ(), yaw, TILT);
        float turn = this.turn(now);
        Quaternionf q = new Quaternionf().rotationY(turn);

        for (MapModel.Part p : this.model.parts) {
            BlockDisplay d = w.spawn(loc, BlockDisplay.class, e -> {
                e.setPersistent(false);
                e.setVisibleByDefault(false);
                e.setBlock(p.data());
                e.setBrightness(new Brightness(15, 15));
                e.setViewRange(0.5F);
                e.setTeleportDuration(STEP + 1);
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(q), new Vector3f(0.0005F), new Quaternionf()));
            });
            this.viewer.showEntity(this.plugin, d);
            this.parts.add(d);
        }

        this.sentPos = pos;
        this.sentYaw = yaw;
        this.sentSize = 0.0F;
        this.sentTurn = turn;
    }

    /** Yaw that turns the model's front (local +Z) toward the viewer's eye. */
    private float facing(Vector pos) {
        Vector d = this.eye.clone().subtract(pos);
        return (float)Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
    }

    private void pose(float size, float turn, int ticks) {
        MapModel m = this.model;
        float u = Math.max(0.0F, size) / m.span;
        float e = u * m.vertical;
        Quaternionf q = new Quaternionf().rotationY(turn);

        for (int i = 0; i < this.parts.size(); i++) {
            BlockDisplay d = this.parts.get(i);
            if (d.isValid()) {
                MapModel.Part p = m.parts.get(i);
                Vector3f at = q.transform(new Vector3f(p.x() * u, p.y() * e, p.z() * u));
                Vector3f scale = new Vector3f(Math.max(5.0E-4F, p.w() * u), Math.max(5.0E-4F, p.h() * e), Math.max(5.0E-4F, p.d() * u));
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(ticks);
                d.setTransformation(new Transformation(at, new Quaternionf(q), scale, new Quaternionf()));
            }
        }
    }

    /** How far the model reaches below its middle on screen at {@code size} (its tilted depth plus half its height). */
    float drop(float size) {
        double turn = IDLE_TURN;
        double depth = size * (Math.cos(turn) + Math.sin(turn)) * 0.5;
        double tilt = Math.toRadians(TILT);
        double half = size / this.model.span * this.model.vertical * this.model.height * 0.5;
        return (float)(depth * Math.sin(tilt) + half * Math.cos(tilt));
    }

    boolean owns(Entity e) {
        return e instanceof BlockDisplay && this.parts.contains(e);
    }

    void remove() {
        this.dead = true;

        for (BlockDisplay d : this.parts) {
            d.remove();
        }

        this.parts.clear();
    }
}
