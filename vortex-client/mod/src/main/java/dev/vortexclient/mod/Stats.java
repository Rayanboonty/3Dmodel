package dev.vortexclient.mod;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
    static long lastHitAt;
    private static int lastOwnHurt;
    private static Vec3d lastPos;

    // ---- 0.9: session numbers and history for the new HUD elements.
    public static int kills, deaths, bestCombo, peakCps;
    public static double distance, vspeed;
    /** When the last kill happened (Hit Marker turns red, Kill Sound plays). */
    public static long lastKillAt;
    private static int countedKill = -1;
    /** FPS every quarter second and ping every second, newest last. */
    public static final int[] FPS_HISTORY = new int[80];
    public static final int[] PING_HISTORY = new int[60];
    public static int fpsCount, pingCount;
    private static long lastFpsSample, lastPingSample;
    /** Last time you moved, looked around or clicked (AFK Timer). */
    public static long lastActive = System.currentTimeMillis();
    private static float lastYaw, lastPitch;

    static void push(int[] ring, int count, int value) {
        if (count < ring.length) ring[count] = value;
        else {
            System.arraycopy(ring, 1, ring, 0, ring.length - 1);
            ring[ring.length - 1] = value;
        }
    }

    /** The values in a history ring, oldest first. */
    public static int[] history(int[] ring, int count) {
        return java.util.Arrays.copyOf(ring, Math.min(count, ring.length));
    }

    public static void reset() {
        kills = 0;
        deaths = 0;
        bestCombo = 0;
        peakCps = 0;
        distance = 0;
        countedKill = -1;
    }

    public static void click(int button) {
        long now = System.currentTimeMillis();
        lastActive = now;
        if (button == 0) LEFT.addLast(now);
        else if (button == 1) RIGHT.addLast(now);
    }

    public static int cps(int button) {
        Deque<Long> q = button == 0 ? LEFT : RIGHT;
        long cutoff = System.currentTimeMillis() - 1000;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.removeFirst();
        if (q.size() > peakCps) peakCps = q.size();
        return q.size();
    }

    /** The last living thing you hit (for the Target HUD). */
    public static LivingEntity lastTarget;

    public static void attacked(PlayerEntity player, Entity target) {
        if (target instanceof LivingEntity le) lastTarget = le;
        Vec3d eye = player.getEyePos();
        Box b = target.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
        lastReach = Math.sqrt(dx * dx + dy * dy + dz * dz);
        lastReachAt = System.currentTimeMillis();
        pendingTarget = target.getId();
        pendingHurt = target instanceof LivingEntity le ? le.hurtTime : 0;
        pendingAt = lastReachAt;
    }

    public static void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (client.player == null || client.world == null) {
            combo = 0;
            lastPos = null;
            speed = 0;
            return;
        }
        int ownHurt = client.player.hurtTime;
        if (ownHurt > lastOwnHurt) combo = 0;
        lastOwnHurt = ownHurt;

        if (pendingTarget != -1) {
            Entity e = client.world.getEntityById(pendingTarget);
            if (e instanceof LivingEntity le && le.hurtTime > pendingHurt) {
                combo++;
                if (combo > bestCombo) bestCombo = combo;
                lastHitAt = now;
                pendingTarget = -1;
            } else if (now - pendingAt > 500) {
                pendingTarget = -1;
            }
        }
        if (now - lastHitAt > 2500) combo = 0;

        // A kill: what you last hit died soon after.
        if (lastTarget != null && lastTarget.getId() != countedKill && (lastTarget.isDead() || lastTarget.getHealth() <= 0)
                && now - lastReachAt < 3000) {
            countedKill = lastTarget.getId();
            kills++;
            lastKillAt = now;
            VortexMods.onKill(client);
        }

        Vec3d pos = Compat.pos(client.player);
        if (lastPos != null) {
            double dx = pos.x - lastPos.x, dz = pos.z - lastPos.z, dy = pos.y - lastPos.y;
            double step = Math.sqrt(dx * dx + dz * dz);
            double perSecond = step * 20.0;
            speed += (perSecond - speed) * 0.35;
            vspeed += (dy * 20.0 - vspeed) * 0.35;
            // Teleports and respawns don't count as travelling.
            if (step < 10) distance += step;
            if (step > 0.01 || Math.abs(dy) > 0.01) lastActive = now;
        }
        lastPos = pos;
        float yaw = client.player.getYaw(), pitch = client.player.getPitch();
        if (Math.abs(yaw - lastYaw) > 0.5f || Math.abs(pitch - lastPitch) > 0.5f) lastActive = now;
        lastYaw = yaw;
        lastPitch = pitch;

        if (now - lastFpsSample >= 250) {
            lastFpsSample = now;
            push(FPS_HISTORY, fpsCount++, client.getCurrentFps());
        }
        if (now - lastPingSample >= 1000) {
            lastPingSample = now;
            push(PING_HISTORY, pingCount++, Hud.ping(client));
        }
    }
}
