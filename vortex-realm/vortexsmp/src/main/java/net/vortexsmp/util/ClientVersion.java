package net.vortexsmp.util;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ClientVersion {
    public static final int DIALOGS = 771;
    private static Object viaApi;
    private static Method viaGetVersion;
    private static boolean viaChecked;

    private ClientVersion() {
    }

    public static int of(Player p) {
        if (!viaChecked) {
            viaChecked = true;
            if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                try {
                    Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
                    viaApi = via.getMethod("getAPI").invoke(null);
                    viaGetVersion = viaApi.getClass().getMethod("getPlayerVersion", UUID.class);
                } catch (RuntimeException | ReflectiveOperationException var4) {
                    viaApi = null;
                }
            }
        }

        if (viaApi != null) {
            try {
                if (viaGetVersion.invoke(viaApi, p.getUniqueId()) instanceof Integer i && i > 0) {
                    return i;
                }
            } catch (RuntimeException | ReflectiveOperationException var3) {
            }
        }

        return p.getProtocolVersion();
    }

    public static boolean supportsDialogs(Player p) {
        return of(p) >= 771;
    }
}
