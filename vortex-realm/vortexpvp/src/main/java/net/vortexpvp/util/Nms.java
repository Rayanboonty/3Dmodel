package net.vortexpvp.util;

import io.netty.channel.Channel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Small reflection bridge into the server internals. Paper 1.20.5+ runs with Mojang names at runtime, so the
 * classes are looked up by their Mojang names. Every call fails soft: callers get null / false and fall back.
 */
public final class Nms {
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static Method getHandle;
    private static Method send;

    private Nms() {
    }

    /** The ServerPlayer behind a Bukkit player. */
    public static Object handle(Player p) {
        try {
            if (getHandle == null) {
                getHandle = p.getClass().getMethod("getHandle");
            }

            return getHandle.invoke(p);
        } catch (ReflectiveOperationException | RuntimeException var2) {
            return null;
        }
    }

    /** ServerGamePacketListenerImpl of a player. */
    public static Object listener(Player p) {
        Object h = handle(p);
        return h == null ? null : field(h, "connection");
    }

    /** The netty channel of a player, or null. */
    public static Channel channel(Player p) {
        Object l = listener(p);
        if (l == null) {
            return null;
        } else {
            Object conn = field(l, "connection");
            Object ch = conn == null ? null : field(conn, "channel");
            return ch instanceof Channel c ? c : null;
        }
    }

    /** Sends a vanilla packet object to the player. */
    public static boolean send(Player p, Object packet) {
        Object l = listener(p);
        if (l != null && packet != null) {
            try {
                if (send == null) {
                    Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
                    send = findMethod(l.getClass(), "send", packetClass);
                }

                send.invoke(l, packet);
                return true;
            } catch (ReflectiveOperationException | RuntimeException var3) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static Method findMethod(Class<?> c, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, args);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException var5) {
            }
        }

        throw new NoSuchMethodException(c.getName() + "." + name);
    }

    /** Reads a field by name, searching superclasses (first match wins). */
    public static Object field(Object o, String name) {
        Class<?> c = o.getClass();
        String key = c.getName() + "#" + name;

        try {
            Field f = FIELDS.get(key);
            if (f == null) {
                for (Class<?> k = c; k != null && f == null; k = k.getSuperclass()) {
                    try {
                        f = k.getDeclaredField(name);
                    } catch (NoSuchFieldException var7) {
                    }
                }

                if (f == null) {
                    return null;
                }

                f.setAccessible(true);
                FIELDS.put(key, f);
            }

            return f.get(o);
        } catch (ReflectiveOperationException | RuntimeException var8) {
            return null;
        }
    }
}
