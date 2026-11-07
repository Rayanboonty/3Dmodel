package dev.vortexclient.mod;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Clicks, reach, combo and speed, measured as you play. */
public final class Stats {
    private Stats() {}

    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();

    public static double lastReach = -1;
    public static long lastReachAt;
    public static int combo;
    public static double speed;

    private static int pendingTarget = -1;
    private static int pendingHurt;
    private static long pendingAt;
    private static long lastHitAt;
    private static int lastOwnHurt;
    private static Vec3 lastPos;

    public static void click(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) LEFT.addLast(now);
        else if (button == 1) RIGHT.addLast(now);
    }

    public static int cps(int button) {
        Deque<Long> q = button == 0 ? LEFT : RIGHT;
        long cutoff = System.currentTimeMillis() - 1000;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.removeFirst();
        return q.size();
    }

    /** The last living thing you hit (for the Target HUD). */
    public static LivingEntity lastTarget;

    public static void attacked(Player player, Entity target) {
        if (target instanceof LivingEntity le) lastTarget = le;
        Vec3 eye = player.getEyePosition();
        AABB b = target.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
        lastReach = Math.sqrt(dx * dx + dy * dy + dz * dz);
        lastReachAt = System.currentTimeMillis();
        pendingTarget = target.getId();
        pendingHurt = target instanceof LivingEntity le ? le.hurtTime : 0;
        pendingAt = lastReachAt;
    }

    public static void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        if (client.player == null || client.level == null) {
            combo = 0;
            lastPos = null;
            speed = 0;
            return;
        }
        int ownHurt = client.player.hurtTime;
        if (ownHurt > lastOwnHurt) combo = 0;
        lastOwnHurt = ownHurt;

        if (pendingTarget != -1) {
            Entity e = client.level.getEntity(pendingTarget);
            if (e instanceof LivingEntity le && le.hurtTime > pendingHurt) {
                combo++;
                lastHitAt = now;
                pendingTarget = -1;
            } else if (now - pendingAt > 500) {
                pendingTarget = -1;
            }
        }
        if (now - lastHitAt > 2500) combo = 0;

        Vec3 pos = Compat.pos(client.player);
        if (lastPos != null) {
            double dx = pos.x - lastPos.x, dz = pos.z - lastPos.z;
            double perSecond = Math.sqrt(dx * dx + dz * dz) * 20.0;
            speed += (perSecond - speed) * 0.35;
        }
        lastPos = pos;
    }
}
