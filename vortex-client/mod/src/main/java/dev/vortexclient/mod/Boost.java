package dev.vortexclient.mod;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

/**
 * FPS Boost: switches the game's own heaviest settings to their fastest
 * values, and puts yours back when it's turned off. What it changed is kept
 * in the config, so your settings also come back after a restart.
 */
final class Boost {
    private Boost() {}

    private static String lastSync;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(SimpleOption o, String name) {
        Object cur = o.getValue();
        if (cur instanceof Enum<?> e) o.setValue(Enum.valueOf((Class) e.getDeclaringClass(), name));
    }

    /** Set one option, remembering what it was the first time. */
    private static void put(Map<String, String> saved, String key, SimpleOption<?> o, String value) {
        String now = String.valueOf(o.getValue() instanceof Enum<?> e ? e.name() : o.getValue());
        if (now.equals(value)) return;
        saved.putIfAbsent(key, now);
        write(o, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void write(SimpleOption o, String value) {
        try {
            Object cur = o.getValue();
            if (cur instanceof Enum<?>) setEnum(o, value);
            else if (cur instanceof Boolean) o.setValue(Boolean.parseBoolean(value));
            else if (cur instanceof Integer) o.setValue((int) Math.round(Double.parseDouble(value)));
            else if (cur instanceof Double) o.setValue(Double.parseDouble(value));
        } catch (RuntimeException e) {
            VortexMods.LOG.warn("FPS Boost couldn't set an option to {}", value, e);
        }
    }

    private static Map<String, SimpleOption<?>> options(GameOptions o) {
        Map<String, SimpleOption<?>> m = new LinkedHashMap<>();
        m.put("view", o.getViewDistance());
        m.put("simulation", o.getSimulationDistance());
        m.put("particles", o.getParticles());
        m.put("clouds", o.getCloudRenderMode());
        m.put("shadows", o.getEntityShadows());
        m.put("lighting", o.getAo());
        m.put("blend", o.getBiomeBlendRadius());
        m.put("entities", o.getEntityDistanceScaling());
        SimpleOption<?> g = Compat.graphicsOption(o);
        if (g != null) m.put("graphics", g);
        return m;
    }

    /** Make the game's settings match the module (called when anything changes). */
    static void sync() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        Config cfg = VortexMods.config;
        Module m = Module.get("boost");
        Map<String, SimpleOption<?>> opts = options(mc.options);
        boolean on = cfg.enabled(m);
        if (!on && cfg.boostSaved.isEmpty()) return;
        // Only when the switch or one of its options changed: other mods
        // being toggled mustn't make the world reload.
        StringBuilder sig = new StringBuilder().append(on);
        for (Module.Option o : m.options) sig.append('|').append(cfg.state(m).values.get(o.key()));
        if (sig.toString().equals(lastSync)) return;
        lastSync = sig.toString();
        // Put back everything first, then apply what's wanted now: turning
        // one part off restores just that part.
        for (Map.Entry<String, String> e : cfg.boostSaved.entrySet()) {
            SimpleOption<?> o = opts.get(e.getKey());
            if (o != null) write(o, e.getValue());
        }
        cfg.boostSaved.clear();
        if (on) {
            Map<String, String> saved = cfg.boostSaved;
            int view = (int) cfg.num(m, "view");
            if (mc.options.getViewDistance().getValue() > view) put(saved, "view", opts.get("view"), String.valueOf(view));
            if (mc.options.getSimulationDistance().getValue() > Math.max(5, view)) put(saved, "simulation", opts.get("simulation"), String.valueOf(Math.max(5, view)));
            if (cfg.bool(m, "graphics")) {
                if (opts.containsKey("graphics")) put(saved, "graphics", opts.get("graphics"), Compat.GRAPHICS_FAST);
                put(saved, "blend", opts.get("blend"), "0");
            }
            if (cfg.bool(m, "particles")) put(saved, "particles", opts.get("particles"), "MINIMAL");
            if (cfg.bool(m, "clouds")) put(saved, "clouds", opts.get("clouds"), "OFF");
            if (cfg.bool(m, "shadows")) put(saved, "shadows", opts.get("shadows"), "false");
            if (cfg.bool(m, "lighting")) put(saved, "lighting", opts.get("lighting"), "false");
            if (cfg.bool(m, "entities") && mc.options.getEntityDistanceScaling().getValue() > 0.5) put(saved, "entities", opts.get("entities"), "0.5");
        }
        mc.options.write();
        cfg.save();
    }
}
