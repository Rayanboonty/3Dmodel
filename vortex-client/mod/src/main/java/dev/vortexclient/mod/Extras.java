package dev.vortexclient.mod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Stopwatch, server TPS, waypoints, quick chat and the FOV Changer. */
final class Extras {
    private Extras() {}

    // ---- Stopwatch (Y starts and stops; hold to reset).
    private static long swStart, swAccum;
    private static boolean swRunning;
    private static int heldTicks;

    static long stopwatch() {
        return swAccum + (swRunning ? System.currentTimeMillis() - swStart : 0);
    }

    static boolean stopwatchRunning() {
        return swRunning;
    }

    static void stopwatchKey(boolean down, boolean pressed) {
        if (pressed) {
            if (swRunning) {
                swAccum += System.currentTimeMillis() - swStart;
                swRunning = false;
            } else {
                swStart = System.currentTimeMillis();
                swRunning = true;
            }
        }
        heldTicks = down ? heldTicks + 1 : 0;
        if (heldTicks == 20) {
            swRunning = false;
            swAccum = 0;
        }
    }

    // ---- Server TPS: how far the world's clock moved in real time.
    private static long lastWorldTime = -1, lastReal;
    private static double tps = -1;

    static double tps() {
        return tps;
    }

    static void tickTps(MinecraftClient mc) {
        if (mc.world == null) {
            lastWorldTime = -1;
            tps = -1;
            return;
        }
        long now = System.currentTimeMillis();
        long t = mc.world.getTime();
        if (lastWorldTime < 0) {
            lastWorldTime = t;
            lastReal = now;
            return;
        }
        if (now - lastReal >= 2000) {
            double sample = Math.min(20.0, (t - lastWorldTime) * 1000.0 / (now - lastReal));
            tps = tps < 0 ? sample : tps * 0.6 + sample * 0.4;
            lastWorldTime = t;
            lastReal = now;
        }
    }

    // ---- Waypoints, per server (or world) and dimension.
    static String place() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String where = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address
                : mc.getServer() != null ? "sp:" + mc.getServer().getSaveProperties().getLevelName() : "unknown";
        String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        return where + "|" + dim;
    }

    static List<double[]> waypoints() {
        return VortexMods.config.waypoints.getOrDefault(place(), List.of());
    }

    static void addWaypoint(MinecraftClient mc) {
        if (mc.player == null) return;
        List<double[]> list = VortexMods.config.waypoints.computeIfAbsent(place(), k -> new ArrayList<>());
        list.add(0, new double[] {mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
        while (list.size() > 20) list.remove(list.size() - 1);
        VortexMods.config.save();
        mc.inGameHud.getChatHud().addMessage(Text.literal("[Vortex] Waypoint #1 saved at " + mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ()).formatted(Formatting.AQUA));
    }

    static void tickWaypoints() {
        Module m = Module.get("waypoints");
        if (VortexMods.config.bool(m, "clear")) {
            VortexMods.config.waypoints.remove(place());
            VortexMods.config.set(m, "clear", false);
            VortexMods.config.save();
        }
    }

    // ---- Quick Chat.
    static void quickChat(MinecraftClient mc, String key) {
        Module m = Module.get("quickchat");
        if (!VortexMods.config.enabled(m) || mc.getNetworkHandler() == null) return;
        String msg = VortexMods.config.str(m, key);
        if (msg.startsWith("/")) mc.getNetworkHandler().sendChatCommand(msg.substring(1));
        else mc.getNetworkHandler().sendChatMessage(msg);
    }

    // ---- FOV Changer.
    static float fov(float fov) {
        Module m = Module.get("fovchanger");
        if (!VortexMods.config.enabled(m)) return fov;
        MinecraftClient mc = MinecraftClient.getInstance();
        float want = (float) VortexMods.config.num(m, "fov");
        if (VortexMods.config.bool(m, "static")) return want;
        float base = mc.options.getFov().getValue();
        return base > 0 ? fov * want / base : fov;
    }

    /** The wind charge item (1.21+), or null before it existed. */
    static Item windCharge() {
        Item i = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of("minecraft", "wind_charge"));
        return i == net.minecraft.item.Items.AIR ? null : i;
    }
}
