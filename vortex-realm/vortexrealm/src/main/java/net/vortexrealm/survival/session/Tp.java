package net.vortexrealm.survival.session;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class Tp {
    private Tp() {
    }

    public static boolean to(Player p, Location l) {
        if (p != null && l != null && l.getWorld() != null) {
            if (p.isInsideVehicle()) {
                p.leaveVehicle();
            }

            return p.teleport(l, TeleportCause.PLUGIN);
        } else {
            return false;
        }
    }
}
