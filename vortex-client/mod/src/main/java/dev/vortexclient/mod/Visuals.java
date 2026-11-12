package dev.vortexclient.mod;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Clean Visuals: takes the screen effects that get in the way of fighting
 * out of the game (hurt shake, nausea and portal wobble, the darkness pulse,
 * view bobbing, speed FOV changes, a bright enchant glint). Your own values
 * come back when it's turned off, even after a restart.
 */
final class Visuals {
    private Visuals() {}

    private static String lastSync;

    private static Map<String, SimpleOption<?>> options(MinecraftClient mc) {
        Map<String, SimpleOption<?>> m = new LinkedHashMap<>();
        m.put("hurtshake", mc.options.getDamageTiltStrength());
        m.put("distortion", mc.options.getDistortionEffectScale());
        m.put("darkness", mc.options.getDarknessEffectScale());
        m.put("bobbing", mc.options.getBobView());
        m.put("fovfx", mc.options.getFovEffectScale());
        m.put("glint", mc.options.getGlintStrength());
        return m;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void write(SimpleOption o, String value) {
        try {
            if (o.getValue() instanceof Boolean) o.setValue(Boolean.parseBoolean(value));
            else o.setValue(Double.parseDouble(value));
        } catch (RuntimeException e) {
            VortexMods.LOG.warn("Clean Visuals couldn't set an option to {}", value, e);
        }
    }

    private static void put(Map<String, String> saved, String key, SimpleOption<?> o, String value) {
        String now = String.valueOf(o.getValue());
        if (now.equals(value)) return;
        saved.putIfAbsent(key, now);
        write(o, value);
    }

    static void sync() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        Config cfg = VortexMods.config;
        Module m = Module.get("visuals");
        boolean on = cfg.enabled(m);
        if (!on && cfg.visualsSaved.isEmpty()) return;
        StringBuilder sig = new StringBuilder().append(on);
        for (Module.Option o : m.options) sig.append('|').append(cfg.state(m).values.get(o.key()));
        if (sig.toString().equals(lastSync)) return;
        lastSync = sig.toString();
        Map<String, SimpleOption<?>> opts = options(mc);
        for (Map.Entry<String, String> e : cfg.visualsSaved.entrySet()) {
            SimpleOption<?> o = opts.get(e.getKey());
            if (o != null) write(o, e.getValue());
        }
        cfg.visualsSaved.clear();
        if (on) {
            Map<String, String> saved = cfg.visualsSaved;
            if (cfg.bool(m, "hurtshake")) put(saved, "hurtshake", opts.get("hurtshake"), "0.0");
            if (cfg.bool(m, "distortion")) put(saved, "distortion", opts.get("distortion"), "0.0");
            if (cfg.bool(m, "darkness")) put(saved, "darkness", opts.get("darkness"), "0.0");
            if (cfg.bool(m, "bobbing")) put(saved, "bobbing", opts.get("bobbing"), "false");
            if (cfg.bool(m, "fovfx")) put(saved, "fovfx", opts.get("fovfx"), "0.0");
            double glint = cfg.num(m, "glint") / 100.0;
            if (glint < 0.999) put(saved, "glint", opts.get("glint"), String.valueOf(glint));
        }
        mc.options.write();
        cfg.save();
    }
}
