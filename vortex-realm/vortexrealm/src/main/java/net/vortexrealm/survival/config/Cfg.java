package net.vortexrealm.survival.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import net.vortexrealm.survival.VortexSMP;

public final class Cfg {
    private static VortexSMP plugin;
    private static FileConfiguration c;

    private Cfg() {
    }

    /** Loads config.yml and writes every missing setting with its default and a comment. */
    public static void load(VortexSMP p) {
        plugin = p;
        p.reloadConfig();
        c = p.getConfig();
        boolean changed = false;

        for (Opt<?> o : Opt.all()) {
            if (!c.isSet(o.path)) {
                c.set(o.path, o.def);
                c.setComments(o.path, List.of(o.desc));
                changed = true;
            }
        }

        if (changed) {
            p.saveConfig();
        }
    }

    public static boolean bool(Opt<Boolean> o) {
        return c == null ? o.def : c.getBoolean(o.path, o.def);
    }

    public static int i(Opt<Integer> o) {
        return c == null ? o.def : c.getInt(o.path, o.def);
    }

    public static String s(Opt<String> o) {
        return c == null ? o.def : c.getString(o.path, o.def);
    }

    public static <T> void set(Opt<T> o, T value) {
        c.set(o.path, value);
        plugin.saveConfig();
    }

    public static List<String> list(String path) {
        return c == null ? new ArrayList<>() : c.getStringList(path);
    }

    public static FileConfiguration raw() {
        return c;
    }
}
