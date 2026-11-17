package net.vortexpvp.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Markers our plugins leave on players are named after the plugin ("practicepvp:inv_group", "vortexrealm:inv_group").
 * When a server switches from one of them to another, the new plugin takes the old marker over here, so it still
 * knows which saved inventory and ender chest the player is carrying.
 */
public final class LegacyKeys {
    /** Plain names on purpose: the plugin generators only rename the package and the plugin name. */
    private static final String[] NAMESPACES = {"practicepvp", "vortexrealm", "vortexpvp"};

    private LegacyKeys() {
    }

    /** A text marker another of our plugins left: copied to our key, theirs removed. Null when there is none. */
    public static String takeString(Player p, NamespacedKey ours) {
        PersistentDataContainer c = p.getPersistentDataContainer();
        for (String ns : NAMESPACES) {
            if (ns.equals(ours.getNamespace())) {
                continue;
            }

            NamespacedKey k = new NamespacedKey(ns, ours.getKey());
            String v = c.get(k, PersistentDataType.STRING);
            if (v != null) {
                c.set(ours, PersistentDataType.STRING, v);
                c.remove(k);
                return v;
            }
        }

        return null;
    }

    /** A flag marker another of our plugins left: copied to our key, theirs removed. */
    public static boolean takeFlag(Player p, NamespacedKey ours) {
        PersistentDataContainer c = p.getPersistentDataContainer();
        for (String ns : NAMESPACES) {
            if (ns.equals(ours.getNamespace())) {
                continue;
            }

            NamespacedKey k = new NamespacedKey(ns, ours.getKey());
            if (c.has(k, PersistentDataType.BYTE)) {
                c.set(ours, PersistentDataType.BYTE, (byte)1);
                c.remove(k);
                return true;
            }
        }

        return false;
    }
}
