package net.vortexpvp.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Cfg {
    private static final Map<Opt<?>, Object> VALUES = new ConcurrentHashMap<>();
    private static JavaPlugin plugin;
    private static File file;
    private static YamlConfiguration yaml;
    /** Options this start had to add (new in this version) and old values it couldn't read (kept under old-settings). */
    public static int added;
    public static int kept;
    private static final List<Consumer<Opt<?>>> LISTENERS = new CopyOnWriteArrayList<>();

    private Cfg() {
    }

    public static void load(JavaPlugin p) {
        plugin = p;
        added = 0;
        kept = 0;
        file = new File(p.getDataFolder(), "config.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
        boolean changed = !file.exists();

        for (Opt<?> opt : Opt.all()) {
            Object raw = yaml.get(opt.path);
            Object value = coerce(opt, raw);
            if (value == null) {
                if (raw != null) {
                    // a value this version can't read (an old format or a typo) is kept, not thrown away
                    yaml.set("old-settings." + opt.path, raw);
                    kept++;
                } else if (file.exists()) {
                    added++;
                }

                value = opt.def;
                yaml.set(opt.path, toYaml(opt, value));
                changed = true;
            }

            VALUES.put(opt, value);
            if (!opt.desc.isEmpty()) {
                yaml.setComments(opt.path, List.of(opt.desc));
            }
        }

        int version = yaml.getInt("config-version", 1);
        if (version < 2) {
            if ("PRACTICE".equals(VALUES.get(Opt.SERVER_NAME))) {
                VALUES.put(Opt.SERVER_NAME, Opt.SERVER_NAME.def);
                yaml.set(Opt.SERVER_NAME.path, Opt.SERVER_NAME.def);
            }

            if ("".equals(VALUES.get(Opt.LINK_DISCORD))) {
                VALUES.put(Opt.LINK_DISCORD, Opt.LINK_DISCORD.def);
                yaml.set(Opt.LINK_DISCORD.path, Opt.LINK_DISCORD.def);
            }

            yaml.set("config-version", 2);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 3) {
            if ("BOTH".equals(VALUES.get(Opt.FOUND_EFFECT))) {
                VALUES.put(Opt.FOUND_EFFECT, Opt.FOUND_EFFECT.def);
                yaml.set(Opt.FOUND_EFFECT.path, Opt.FOUND_EFFECT.def);
            }

            yaml.set("config-version", 3);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 4) {
            if ("SMOOTH".equals(VALUES.get(Opt.RANKUP_STYLE))) {
                VALUES.put(Opt.RANKUP_STYLE, Opt.RANKUP_STYLE.def);
                yaml.set(Opt.RANKUP_STYLE.path, Opt.RANKUP_STYLE.def);
            }

            yaml.set("config-version", 4);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 5) {
            Object style = VALUES.get(Opt.RANKUP_STYLE);
            if ("SCREEN".equals(style) || "SMOOTH".equals(style)) {
                VALUES.put(Opt.RANKUP_STYLE, Opt.RANKUP_STYLE.def);
                yaml.set(Opt.RANKUP_STYLE.path, Opt.RANKUP_STYLE.def);
            }

            yaml.set("config-version", 5);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 6) {
            if ("SWORD".equals(VALUES.get(Opt.FOUND_EFFECT))) {
                VALUES.put(Opt.FOUND_EFFECT, Opt.FOUND_EFFECT.def);
                yaml.set(Opt.FOUND_EFFECT.path, Opt.FOUND_EFFECT.def);
            }

            yaml.set("config-version", 6);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 7) {
            Object[][] faster = new Object[][]{
                {Opt.ELO_RANGE_START, 150}, {Opt.ELO_RANGE_EXPAND, 30}, {Opt.SAME_TIER_SECONDS, 40}, {Opt.TIER_TEST_FALLBACK, 180}
            };

            for (Object[] f : faster) {
                Opt<?> opt = (Opt<?>)f[0];
                if (VALUES.get(opt) instanceof Number n && n.intValue() == (Integer)f[1]) {
                    VALUES.put(opt, opt.def);
                    yaml.set(opt.path, opt.def);
                }
            }

            yaml.set("config-version", 7);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 8) {
            // Vortex Realm round 3: arenas twice as big, no walls, MCPVP ban timers. Only values still on the old defaults move.
            Object[][] bigger = new Object[][]{
                {Opt.DUEL_ARENA_SIZE, 96}, {Opt.FFA_ARENA_SIZE, 128}, {Opt.MACE_ARENA_SIZE, 230}, {Opt.MAP_BAN_TURN_SECONDS, 8},
                {Opt.END_DELAY, 70}
            };

            for (Object[] f : bigger) {
                Opt<?> opt = (Opt<?>)f[0];
                if (VALUES.get(opt) instanceof Number n && n.intValue() == (Integer)f[1]) {
                    VALUES.put(opt, opt.def);
                    yaml.set(opt.path, opt.def);
                }
            }

            if ("staff,friends,creators,top,endgame,sword,spear,mace".equals(VALUES.get(Opt.TAB_COLUMNS_SECTIONS))) {
                VALUES.put(Opt.TAB_COLUMNS_SECTIONS, Opt.TAB_COLUMNS_SECTIONS.def);
                yaml.set(Opt.TAB_COLUMNS_SECTIONS.path, Opt.TAB_COLUMNS_SECTIONS.def);
            }

            if ("STONE_BRICK".equals(VALUES.get(Opt.ARENA_WALLS))) {
                VALUES.put(Opt.ARENA_WALLS, Opt.ARENA_WALLS.def);
                yaml.set(Opt.ARENA_WALLS.path, Opt.ARENA_WALLS.def);
            }

            yaml.set("config-version", 8);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 9) {
            // 1.3.0: deeper arenas, every one on bedrock. Only values still on the old defaults move.
            Object[][] deeper = new Object[][]{{Opt.DUEL_DEPTH, 34}, {Opt.FFA_DEPTH, 44}};

            for (Object[] f : deeper) {
                Opt<?> opt = (Opt<?>)f[0];
                if (VALUES.get(opt) instanceof Number n && n.intValue() == (Integer)f[1]) {
                    VALUES.put(opt, opt.def);
                    yaml.set(opt.path, opt.def);
                }
            }

            yaml.set("config-version", 9);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 10) {
            // 1.4.0: the match-found pop is the kit icon at a medium size; the old default (BOTH) moves to it
            if ("BOTH".equals(String.valueOf(VALUES.get(Opt.FOUND_EFFECT)))) {
                VALUES.put(Opt.FOUND_EFFECT, Opt.FOUND_EFFECT.def);
                yaml.set(Opt.FOUND_EFFECT.path, Opt.FOUND_EFFECT.def);
            }

            yaml.set("config-version", 10);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 11) {
            // 1.5.2: back to the original match-found pop (totem with the kit's item, then the weapon)
            if ("ICON".equals(String.valueOf(VALUES.get(Opt.FOUND_EFFECT)))) {
                VALUES.put(Opt.FOUND_EFFECT, Opt.FOUND_EFFECT.def);
                yaml.set(Opt.FOUND_EFFECT.path, Opt.FOUND_EFFECT.def);
            }

            yaml.set("config-version", 11);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 12) {
            // 1.6.0: the MCPVP match-found pop (totem animation with the kit's item only)
            String fx = String.valueOf(VALUES.get(Opt.FOUND_EFFECT));
            if ("BOTH".equals(fx) || "ICON".equals(fx)) {
                VALUES.put(Opt.FOUND_EFFECT, Opt.FOUND_EFFECT.def);
                yaml.set(Opt.FOUND_EFFECT.path, Opt.FOUND_EFFECT.def);
            }

            yaml.set("config-version", 12);
            changed = true;
        }

        if (yaml.getInt("config-version", 1) < 13) {
            // 1.6.1: back to the first version's pop (totem with the kit's item, then the weapon pop)
            String fx = String.valueOf(VALUES.get(Opt.FOUND_EFFECT));
            if ("TOTEM".equals(fx) || "ICON".equals(fx)) {
                VALUES.put(Opt.FOUND_EFFECT, Opt.FOUND_EFFECT.def);
                yaml.set(Opt.FOUND_EFFECT.path, Opt.FOUND_EFFECT.def);
            }

            yaml.set("config-version", 13);
            changed = true;
        }

        yaml.options()
            .setHeader(
                List.of(
                    "VortexPvP configuration.",
                    "Everything here can also be edited in-game: /pvpadmin -> Settings.",
                    "Text fields use MiniMessage (https://docs.advntr.dev/minimessage/format.html)."
                )
            );
        if (changed) {
            save();
        }
    }

    private static Object coerce(Opt<?> opt, Object raw) {
        if (raw == null) {
            return null;
        } else {
            try {
                switch (opt.type) {
                    case BOOL:
                        if (raw instanceof Boolean b) {
                            return b;
                        }

                        return Boolean.parseBoolean(raw.toString());
                    case INT: {
                        int v = raw instanceof Number n ? n.intValue() : Integer.parseInt(raw.toString().trim());
                        return (int)Math.max(opt.min, Math.min(opt.max, (double)v));
                    }
                    case DOUBLE: {
                        double v = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(raw.toString().trim());
                        return Math.max(opt.min, Math.min(opt.max, v));
                    }
                    case STRING:
                        return raw.toString();
                    case ENUM:
                        String s = raw.toString().trim().toUpperCase(Locale.ROOT);

                        for (Enum<?> e : opt.enumClass.getEnumConstants()) {
                            if (e.name().equals(s)) {
                                return s;
                            }
                        }

                        return null;
                    case INT_LIST:
                        if (!(raw instanceof List<?> l)) {
                            return null;
                        } else {
                            List<Integer> out = new ArrayList<>();

                            for (Object o : l) {
                                out.add(o instanceof Number n ? n.intValue() : Integer.parseInt(o.toString().trim()));
                            }

                            List<Integer> def = (List<Integer>)opt.def;
                            if (out.size() == 10 && def.size() == 15) {
                                List<Integer> ex = new ArrayList<>();

                                for (int i = 0; i < 5; i++) {
                                    int lo = out.get(i * 2);
                                    int hi = out.get(i * 2 + 1);
                                    ex.add(lo);
                                    ex.add((lo + hi) / 2);
                                    ex.add(hi);
                                }

                                out = ex;
                            }

                            if (out.size() != def.size()) {
                                return null;
                            } else {
                                for (int i = 1; i < out.size(); i++) {
                                    if (out.get(i) <= out.get(i - 1)) {
                                        out.set(i, out.get(i - 1) + 1);
                                    }
                                }

                                return List.copyOf(out);
                            }
                        }
                    case STRING_LIST:
                        if (!(raw instanceof List<?> l)) {
                            return null;
                        }

                        List<String> out = new ArrayList<>();

                        for (Object o : l) {
                            out.add(String.valueOf(o));
                        }

                        return List.copyOf(out);
                    default:
                        return null;
                }
            } catch (RuntimeException var9) {
                plugin.getLogger().warning("Invalid value for " + opt.path + " (" + raw + "), using default.");
                return null;
            }
        }
    }

    private static Object toYaml(Opt<?> opt, Object value) {
        return value;
    }

    public static void save() {
        try {
            yaml.save(file);
        } catch (IOException var1) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config.yml", (Throwable)var1);
        }
    }

    public static <T> T get(Opt<T> opt) {
        Object v = VALUES.get(opt);
        return (T)(v == null ? opt.def : v);
    }

    public static boolean bool(Opt<Boolean> opt) {
        return get(opt);
    }

    public static int i(Opt<Integer> opt) {
        return get(opt);
    }

    public static double d(Opt<Double> opt) {
        return get(opt);
    }

    public static String s(Opt<String> opt) {
        return get(opt);
    }

    public static <E extends Enum<E>> E e(Opt<String> opt, Class<E> cls) {
        try {
            return Enum.valueOf(cls, get(opt));
        } catch (IllegalArgumentException var3) {
            return Enum.valueOf(cls, opt.def);
        }
    }

    public static Object set(Opt<?> opt, Object value) {
        Object v = coerce(opt, value);
        if (v == null) {
            v = opt.def;
        }

        VALUES.put(opt, v);
        yaml.set(opt.path, v);
        save();

        for (Consumer<Opt<?>> l : LISTENERS) {
            try {
                l.accept(opt);
            } catch (RuntimeException var6) {
            }
        }

        return v;
    }

    public static void onChange(Consumer<Opt<?>> listener) {
        LISTENERS.add(listener);
    }

    public static void reset(Opt<?> opt) {
        set(opt, opt.def);
    }
}
