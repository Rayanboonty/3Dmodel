package net.vortexpvp.fx.anim;

import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntFunction;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One animated display entity. Position, scale and rotation are computed every tick from tweens (or a path), and sent
 * to the client every tick with interpolation set just longer than that, so the client always has a target to glide
 * toward and eased curves keep their shape: the motion never stops, skips or snaps. Nothing is re-sent when it hasn't
 * changed.
 */
public final class Elem {
    /** Ticks between updates sent to the client. */
    public static final int PERIOD = 1;
    public final Display entity;
    private final World world;
    private final int born;
    private Vector from;
    private Vector to;
    private int t0;
    private int dur;
    private DoubleUnaryOperator ease = Anim::easeOutCubic;
    /** Overrides the position tween while set (orbits). */
    public IntFunction<Vector> path;
    private float sFrom;
    private float sTo;
    private int st0;
    private int sdur;
    private DoubleUnaryOperator sEase = Anim::easeOutCubic;
    /** Rotation per tick (null = none). */
    public IntFunction<Quaternionf> rot;
    private Vector sentPos;
    private float sentScale;
    private Quaternionf sentRot;
    private boolean dead;

    private Elem(Display entity, Vector pos, float scale, int now) {
        this.entity = entity;
        this.world = entity.getWorld();
        this.born = now;
        this.from = pos.clone();
        this.to = pos.clone();
        this.sFrom = scale;
        this.sTo = scale;
        this.sentPos = pos.clone();
        // what spawn() gave the entity (a NaN here never compares as "changed", so the scale was never sent)
        this.sentScale = Math.max(0.001F, scale);
    }

    /** Spawns a display only {@code viewer} can see (or everyone when viewer is null). */
    public static <T extends Display> Elem spawn(
        Plugin plugin, World w, Vector pos, Class<T> cls, Player viewer, float scale, int now, Consumer<T> init
    ) {
        T d = w.spawn(new Location(w, pos.getX(), pos.getY(), pos.getZ()), cls, e -> {
            e.setPersistent(false);
            if (viewer != null) {
                e.setVisibleByDefault(false);
            }

            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(Math.max(0.001F, scale)), new Quaternionf()));
            init.accept(e);
        });
        if (viewer != null) {
            viewer.showEntity(plugin, d);
        }

        return new Elem(d, pos, scale, now);
    }

    public Elem moveTo(Vector target, int now, int ticks) {
        return this.moveTo(target, now, ticks, Anim::easeOutCubic);
    }

    public Elem moveTo(Vector target, int now, int ticks, DoubleUnaryOperator ease) {
        this.from = this.pos(now);
        this.to = target.clone();
        this.t0 = now;
        this.dur = Math.max(1, ticks);
        this.ease = ease;
        this.path = null;
        return this;
    }

    public Elem scaleTo(float target, int now, int ticks) {
        return this.scaleTo(target, now, ticks, Anim::easeOutCubic);
    }

    public Elem scaleTo(float target, int now, int ticks, DoubleUnaryOperator ease) {
        this.sFrom = this.scale(now);
        this.sTo = target;
        this.st0 = now;
        this.sdur = Math.max(1, ticks);
        this.sEase = ease;
        return this;
    }

    public Vector pos(int now) {
        if (this.path != null) {
            return this.path.apply(now);
        } else if (now >= this.t0 + this.dur) {
            return this.to.clone();
        } else {
            double k = this.ease.applyAsDouble((double)(now - this.t0) / this.dur);
            return this.from.clone().add(this.to.clone().subtract(this.from).multiply(k));
        }
    }

    public Vector target() {
        return this.to.clone();
    }

    public float scale(int now) {
        if (now >= this.st0 + this.sdur) {
            return this.sTo;
        } else {
            double k = this.sEase.applyAsDouble((double)(now - this.st0) / this.sdur);
            return (float)(this.sFrom + (this.sTo - this.sFrom) * k);
        }
    }

    public float scaleTarget() {
        return this.sTo;
    }

    public boolean valid() {
        return !this.dead && this.entity.isValid();
    }

    /** Sends the current state if it is time and something changed. */
    public void tick(int now) {
        if (this.valid() && now >= this.born + 2 && (now - this.born) % PERIOD == 0) {
            Vector p = this.pos(now + PERIOD);
            if (p.distanceSquared(this.sentPos) > 1.0E-6) {
                this.entity.setTeleportDuration(PERIOD + 1);
                this.entity.teleport(new Location(this.world, p.getX(), p.getY(), p.getZ()));
                this.sentPos = p;
            }

            float s = this.scale(now + PERIOD);
            Quaternionf q = this.rot == null ? null : this.rot.apply(now + PERIOD);
            boolean rotChanged = q != null && (this.sentRot == null || !q.equals(this.sentRot, 1.0E-4F));
            if (Math.abs(s - this.sentScale) > 1.0E-4F || rotChanged) {
                this.entity.setInterpolationDelay(0);
                this.entity.setInterpolationDuration(PERIOD + 1);
                this.entity
                    .setTransformation(
                        new Transformation(new Vector3f(), q == null ? new Quaternionf() : new Quaternionf(q), new Vector3f(Math.max(0.0005F, s)), new Quaternionf())
                    );
                this.sentScale = s;
                this.sentRot = q == null ? null : new Quaternionf(q);
            }
        }
    }

    public void remove() {
        this.dead = true;
        this.entity.remove();
    }
}
