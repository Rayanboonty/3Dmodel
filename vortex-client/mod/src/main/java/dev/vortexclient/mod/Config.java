package dev.vortexclient.mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Saved state of every module (`config/vortex-mods.json`). */
public final class Config {
    public static final class ModState {
        public boolean enabled;
        /** Centre of the HUD element as a fraction of the screen, or -1 for the default spot. */
        public double x = -1;
        public double y = -1;
        public Map<String, Object> values = new LinkedHashMap<>();
    }

    public Map<String, ModState> modules = new LinkedHashMap<>();
    public List<String> favorites = new ArrayList<>();
    /** Vanilla toggle settings from before Toggle Sprint changed them. */
    public Boolean savedSprintToggle;
    public Boolean savedSneakToggle;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("vortex-mods.json");
    }

    public static Config load() {
        try {
            String text = Files.readString(path(), StandardCharsets.UTF_8);
            Config c = GSON.fromJson(text, Config.class);
            if (c != null) {
                if (c.modules == null) c.modules = new LinkedHashMap<>();
                if (c.favorites == null) c.favorites = new ArrayList<>();
                return c;
            }
        } catch (IOException | RuntimeException ignored) {
            // Missing or broken: start fresh.
        }
        return new Config();
    }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            VortexMods.LOG.warn("Could not save Vortex settings", e);
        }
    }

    public ModState state(Module m) {
        return modules.computeIfAbsent(m.id, k -> {
            ModState s = new ModState();
            s.enabled = m.defaultOn;
            return s;
        });
    }

    public boolean enabled(Module m) {
        return state(m).enabled;
    }

    public boolean bool(Module m, String key) {
        Object v = state(m).values.get(key);
        if (v instanceof Boolean b) return b;
        Module.Option o = m.option(key);
        return o != null && o.def() instanceof Boolean b && b;
    }

    public double num(Module m, String key) {
        Object v = state(m).values.get(key);
        Module.Option o = m.option(key);
        double d = v instanceof Number n ? n.doubleValue() : (o != null && o.def() instanceof Number n2 ? n2.doubleValue() : 0);
        if (o != null && o.type() == Module.Type.SLIDER) d = Math.max(o.min(), Math.min(o.max(), d));
        return d;
    }

    public String str(Module m, String key) {
        Object v = state(m).values.get(key);
        Module.Option o = m.option(key);
        if (v instanceof String s && o != null && o.choices().contains(s)) return s;
        return o != null ? String.valueOf(o.def()) : "";
    }

    public void set(Module m, String key, Object value) {
        state(m).values.put(key, value);
    }

    public void reset(Module m) {
        ModState s = state(m);
        s.values.clear();
        s.x = -1;
        s.y = -1;
    }
}
