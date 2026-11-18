package net.vortexrealm.cosmetics;

import io.papermc.paper.entity.TeleportFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.vortexrealm.cosmetics.CosmeticModels.Model;
import net.vortexrealm.cosmetics.CosmeticModels.Part;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A model in the world: one block display per box, all riding an invisible item display (the root). Moving the
 * cosmetic is one teleport of the root (the boxes ride along); turning and animating it changes the boxes'
 * transformations, which the client blends smoothly (interpolation). Only boxes whose transformation changed are sent.
 */
public final class CosmeticRig {
    /** The pose of a model for one update: everything the animation decides. */
    public static final class Pose {
        /** Facing (Minecraft yaw, degrees). */
        public float yaw;
        /** Offset from the root in the cosmetic's own frame (blocks, before turning): e.g. on the back / a shoulder. */
        public final Vector3f offset = new Vector3f();
        /** Extra lift of the model (pixels), e.g. a bob or a hop. */
        public float lift;
        /** Roll of the whole model about its own sideways axis through its middle (radians): forward rolls. */
        public float roll;
        /** Extra size (1 = normal). */
        public float scale = 1.0f;
        /** Rotation of animated groups about their pivots (legs, tail, head, wings). */
        public final Map<String, Quaternionf> groups = new java.util.HashMap<>();

        public Pose group(String g, Quaternionf q) {
            this.groups.put(g, q);
            return this;
        }
    }

    private final Model model;
    private final ItemDisplay root;
    private final List<BlockDisplay> parts = new ArrayList<>();
    private final Matrix4f[] last;
    private final int interp;

    private CosmeticRig(Model model, ItemDisplay root, int interp) {
        this.model = model;
        this.root = root;
        this.interp = interp;
        this.last = new Matrix4f[model.parts().size()];
    }

    public Model model() {
        return this.model;
    }

    public Location location() {
        return this.root.getLocation();
    }

    public boolean valid() {
        return this.root.isValid();
    }

    /** Spawns a model at a spot (not saved with the world), posed with {@code pose}. */
    public static CosmeticRig spawn(Location at, Model model, Pose pose, int interpolation, float viewRange) {
        Location base = at.clone();
        base.setYaw(0);
        base.setPitch(0);
        ItemDisplay root = at.getWorld().spawn(base, ItemDisplay.class, r -> {
            r.setPersistent(false);
            r.setTeleportDuration(interpolation);
            r.setViewRange(viewRange);
        });
        CosmeticRig rig = new CosmeticRig(model, root, interpolation);
        List<Part> ps = model.parts();
        for (int i = 0; i < ps.size(); i++) {
            Part p = ps.get(i);
            Matrix4f m = matrix(model, p, pose);
            rig.last[i] = m;
            BlockData data = blockData(p.block());
            BlockDisplay d = at.getWorld().spawn(base, BlockDisplay.class, b -> {
                b.setPersistent(false);
                b.setBlock(data);
                b.setTransformationMatrix(m);
                b.setInterpolationDuration(interpolation);
                b.setViewRange(viewRange);
                b.setShadowRadius(0.0f);
                if (p.glow()) {
                    b.setBrightness(new Display.Brightness(15, 15));
                }
            });
            root.addPassenger(d);
            rig.parts.add(d);
        }

        return rig;
    }

    private static final Map<String, BlockData> DATA = new java.util.concurrent.ConcurrentHashMap<>();

    private static BlockData blockData(String s) {
        return DATA.computeIfAbsent(s, k -> {
            try {
                return Bukkit.createBlockData(k);
            } catch (IllegalArgumentException e) {
                return Bukkit.createBlockData("white_wool");
            }
        });
    }

    /** Moves the whole model (one packet for the root; the boxes ride it). */
    public void moveTo(Location to) {
        Location l = to.clone();
        l.setYaw(0);
        l.setPitch(0);
        if (l.getWorld() == this.root.getWorld()) {
            this.root.teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
    }

    /** Applies a pose: only the boxes whose transformation changed are sent; the client blends to it. */
    public void pose(Pose pose) {
        List<Part> ps = this.model.parts();
        for (int i = 0; i < ps.size() && i < this.parts.size(); i++) {
            Matrix4f m = matrix(this.model, ps.get(i), pose);
            if (this.last[i] == null || !this.last[i].equals(m, 1.0E-4f)) {
                this.last[i] = m;
                BlockDisplay d = this.parts.get(i);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(this.interp);
                d.setTransformationMatrix(m);
            }
        }
    }

    public void remove() {
        for (BlockDisplay d : this.parts) {
            d.remove();
        }

        this.parts.clear();
        this.root.remove();
    }

    // ------------------------------------------------------------------ the maths (no server needed)

    /**
     * Where one box goes for a pose, as an affine matrix mapping the unit cube of a block display to its place:
     * yaw * (offset + scale * (lift + whole-roll about the middle * (group rotation about its pivot * (own rotation
     * about its centre * (corner + size)))))), in blocks.
     */
    public static Matrix4f matrix(Model model, Part p, Pose pose) {
        float k = model.scale() * pose.scale / 16.0f;
        Matrix4f m = new Matrix4f();
        // turn to face the yaw (Minecraft yaw 0 = +z, 90 = -x)
        m.rotateY((float)Math.toRadians(-pose.yaw));
        m.translate(pose.offset);
        m.scale(k);
        m.translate(0, pose.lift, 0);
        if (pose.roll != 0.0f) {
            float cy = model.height() / 2.0f;
            m.translate(0, cy, 0).rotateX(pose.roll).translate(0, -cy, 0);
        }

        Quaternionf g = pose.groups.get(p.group());
        if (g != null) {
            float[] pv = model.pivot(p.group());
            m.translate(pv[0], pv[1], pv[2]).rotate(g).translate(-pv[0], -pv[1], -pv[2]);
        }

        if (p.rx() != 0 || p.ry() != 0 || p.rz() != 0) {
            float cx = p.x() + p.w() / 2;
            float cy = p.y() + p.h() / 2;
            float cz = p.z() + p.d() / 2;
            m.translate(cx, cy, cz)
                .rotateZ((float)Math.toRadians(p.rz()))
                .rotateY((float)Math.toRadians(p.ry()))
                .rotateX((float)Math.toRadians(p.rx()))
                .translate(-cx, -cy, -cz);
        }

        m.translate(p.x(), p.y(), p.z());
        m.scale(Math.max(0.01f, p.w()), Math.max(0.01f, p.h()), Math.max(0.01f, p.d()));
        return m;
    }

    public static Quaternionf rotX(double deg) {
        return new Quaternionf().rotateX((float)Math.toRadians(deg));
    }

    public static Quaternionf rotY(double deg) {
        return new Quaternionf().rotateY((float)Math.toRadians(deg));
    }

    public static Quaternionf rotZ(double deg) {
        return new Quaternionf().rotateZ((float)Math.toRadians(deg));
    }
}
