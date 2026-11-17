package net.vortexrealm.survival.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.vortexrealm.survival.VortexSMP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The SMP settings: they live in the main config.yml under smp. (shown in /pvpadmin → Settings → SMP). Values from
 * the old smp/config.yml or the separate VortexSMP plugin's config.yml are moved over once.
 */
public final class Cfg {
    private static VortexSMP plugin;

    private Cfg() {
    }

    public static void load(VortexSMP p) {
        plugin = p;
        p.reloadConfig();
        YamlConfiguration own = p.getConfig();
        if (!own.getBoolean("moved-to-main-config")) {
            int moved = 0;
            List<File> olds = new ArrayList<>();
            olds.add(new File(p.getDataFolder(), "config.yml"));
            olds.add(new File(p.host().getDataFolder().getParentFile(), "VortexSMP/config.yml"));
            for (File f : olds) {
                if (!f.isFile()) {
                    continue;
                }

                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                for (Opt<?> o : Opt.all()) {
                    Object v = y.get(o.path);
                    if (v != null && !v.equals(o.def)) {
                        try {
                            net.vortexrealm.config.Cfg.set(o.main, v);
                            moved++;
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            }

            own = p.getConfig();
            own.set("moved-to-main-config", true);
            p.saveConfig();
            if (moved > 0) {
                p.getLogger().info("SMP: " + moved + " SMP settings moved into config.yml (smp.…) - change them in /pvpadmin → Settings → SMP.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean bool(Opt<Boolean> o) {
        return net.vortexrealm.config.Cfg.bool(o.main);
    }

    public static int i(Opt<Integer> o) {
        return net.vortexrealm.config.Cfg.i(o.main);
    }

    public static String s(Opt<String> o) {
        return net.vortexrealm.config.Cfg.s(o.main);
    }

    public static <T> void set(Opt<T> o, T value) {
        net.vortexrealm.config.Cfg.set(o.main, value);
    }

    public static List<String> list(String path) {
        return plugin == null ? new ArrayList<>() : plugin.getConfig().getStringList(path);
    }

    public static FileConfiguration raw() {
        return plugin == null ? null : plugin.getConfig();
    }
}
