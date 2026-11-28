package net.vortexpvp.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Msg {
    private static YamlConfiguration lang = new YamlConfiguration();

    private Msg() {
    }

    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        lang = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                lang.setDefaults(defaults);
                boolean changed = false;
                Pattern elo = Pattern.compile("\\bElo\\b");

                for (String key : lang.getKeys(true)) {
                    String v = lang.isString(key) ? lang.getString(key) : null;
                    String def = defaults.getString(key);
                    if (v != null && def != null && elo.matcher(v).find() && !elo.matcher(def).find()) {
                        lang.set(key, def);
                        changed = true;
                    }
                }

                if (changed) {
                    try {
                        lang.save(file);
                    } catch (IOException var11) {
                        plugin.getLogger().warning("Could not update messages.yml: " + var11.getMessage());
                    }
                }
            }
        } catch (Exception var13) {
            plugin.getLogger().warning("Could not read bundled messages.yml: " + var13.getMessage());
        }
    }

    public static String raw(String key) {
        String s = lang.getString(key);
        return s == null ? "<red>[missing message: " + key + "]" : s;
    }

    public static Component get(String key, Object... vars) {
        String s = raw(key);
        if (s.indexOf(123) >= 0) {
            for (int i = 0; i + 1 < vars.length; i += 2) {
                if (!(vars[i + 1] instanceof Component)) {
                    String v = String.valueOf(vars[i + 1]).replace("'", "").replace("\"", "").replace("\\", "").replace("<", "").replace(">", "");
                    s = s.replace("{" + vars[i] + "}", v);
                }
            }
        }

        return Text.mm(s, Text.vars(vars));
    }

    public static Component prefix() {
        return Text.mm(Cfg.s(Opt.CHAT_PREFIX));
    }

    public static void send(CommandSender to, String key, Object... vars) {
        if (to != null) {
            to.sendMessage(prefix().append(get(key, vars)));
        }
    }

    public static void plain(CommandSender to, String key, Object... vars) {
        if (to != null) {
            to.sendMessage(get(key, vars));
        }
    }

    public static void error(CommandSender to, String key, Object... vars) {
        send(to, key, vars);
        if (to instanceof Player p) {
            Snd.error(p);
        }
    }
}
