package net.vortexpvp.hook;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class AntiCheatHook implements Listener {
    private static final String[] FLAG_EVENTS = new String[]{"me.frep.vulcan.api.event.VulcanFlagEvent", "ac.grim.grimac.api.events.FlagEvent"};
    private static final String[] MOVEMENT_WORDS = new String[]{
        "speed",
        "fly",
        "flight",
        "motion",
        "velocity",
        "strafe",
        "prediction",
        "simulation",
        "jump",
        "elytra",
        "step",
        "timer",
        "antikb",
        "knockback",
        "groundspoof",
        "nofall",
        "phase"
    };
    private final Plugin plugin;
    private final Map<UUID, Long> exemptUntil = new ConcurrentHashMap<>();
    private final StringBuilder hooked = new StringBuilder();

    public AntiCheatHook(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        for (String cls : FLAG_EVENTS) {
            try {
                Class<?> c = Class.forName(cls);
                if (Event.class.isAssignableFrom(c)) {
                    Bukkit.getPluginManager()
                        .registerEvent((Class<? extends Event>)c, this, EventPriority.LOWEST, (listener, event) -> this.handle(event), this.plugin, true);
                    this.hooked.append(this.hooked.isEmpty() ? "" : ", ").append(c.getSimpleName());
                }
            } catch (ClassNotFoundException var6) {
            } catch (Throwable var7) {
                this.plugin.getLogger().warning("Could not hook " + cls + ": " + var7.getMessage());
            }
        }

        if (!this.hooked.isEmpty()) {
            this.plugin.getLogger().info("Anti-cheat compatibility hooked: " + this.hooked);
        }
    }

    public String hooked() {
        return this.hooked.isEmpty() ? "none" : this.hooked.toString();
    }

    public void exempt(Player p) {
        if (Cfg.bool(Opt.AC_EXEMPT)) {
            this.exemptUntil.put(p.getUniqueId(), System.currentTimeMillis() + Cfg.i(Opt.AC_EXEMPT_MS));
        }
    }

    public boolean isExempt(Player p) {
        Long until = this.exemptUntil.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public void forget(UUID uuid) {
        this.exemptUntil.remove(uuid);
    }

    private void handle(Event event) {
        if (event instanceof Cancellable cancellable) {
            Player p = findPlayer(event);
            if (p != null && this.isExempt(p)) {
                String check = checkName(event).toLowerCase(Locale.ROOT);
                if (check.isEmpty()) {
                    cancellable.setCancelled(true);
                } else {
                    for (String w : MOVEMENT_WORDS) {
                        if (check.contains(w)) {
                            cancellable.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    private static Player findPlayer(Event event) {
        for (String m : new String[]{"getPlayer", "getUser"}) {
            try {
                Method method = event.getClass().getMethod(m);
                Object o = method.invoke(event);
                if (o instanceof Player p) {
                    return p;
                }

                if (o != null && o.getClass().getMethod("getUniqueId").invoke(o) instanceof UUID u) {
                    return Bukkit.getPlayer(u);
                }
            } catch (ReflectiveOperationException var9) {
            }
        }

        return null;
    }

    private static String checkName(Event event) {
        try {
            Object check = event.getClass().getMethod("getCheck").invoke(event);
            if (check == null) {
                return "";
            } else {
                StringBuilder sb = new StringBuilder();

                for (String m : new String[]{"getName", "getCheckName", "getCategory", "getType", "getDisplayName"}) {
                    try {
                        Object v = check.getClass().getMethod(m).invoke(check);
                        if (v != null) {
                            sb.append(v).append(' ');
                        }
                    } catch (ReflectiveOperationException var8) {
                    }
                }

                return sb.toString();
            }
        } catch (ReflectiveOperationException var9) {
            return "";
        }
    }
}
