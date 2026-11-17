package net.vortexsmp.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class Tasks {
    private static Plugin plugin;
    public static final Executor MAIN = Tasks::ensureSync;
    private static final Map<String, Long> LAST_ERROR = new ConcurrentHashMap<>();

    private Tasks() {
    }

    public static void init(Plugin p) {
        plugin = p;
    }

    private static boolean alive() {
        return plugin != null && plugin.isEnabled();
    }

    public static BukkitTask sync(Runnable r) {
        return !alive() ? null : Bukkit.getScheduler().runTask(plugin, r);
    }

    public static void ensureSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else if (alive()) {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    public static BukkitTask later(long ticks, Runnable r) {
        return !alive() ? null : Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(0L, ticks));
    }

    public static BukkitTask timer(long delay, long period, Runnable r) {
        return !alive() ? null : Bukkit.getScheduler().runTaskTimer(plugin, guarded(r), delay, Math.max(1L, period));
    }

    private static Runnable guarded(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (LinkageError | RuntimeException var7) {
                StackTraceElement[] st = var7.getStackTrace();
                String where = var7.getClass().getName() + (st.length > 0 ? "@" + st[0] : "");
                long now = System.currentTimeMillis();
                Long last = LAST_ERROR.get(where);
                if (last == null || now - last > 60000L) {
                    LAST_ERROR.put(where, now);
                    plugin.getLogger().log(Level.SEVERE, "A repeating task failed (shown once a minute). Please send this error:", (Throwable)var7);
                }
            }
        };
    }

    public static BukkitTask async(Runnable r) {
        return !alive() ? null : Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    public static void cancel(BukkitTask t) {
        if (t != null) {
            try {
                t.cancel();
            } catch (IllegalStateException var2) {
            }
        }
    }
}
