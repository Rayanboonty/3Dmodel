package net.cmcpvp.session;

import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.entity.TeleportFlag.Relative;
import net.cmcpvp.CMCPvP;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class Tp {
    private static boolean relativeRotation = true;

    private Tp() {
    }

    public static boolean to(Player p, Location l) {
        if (p != null && l != null && l.getWorld() != null) {
            Session s = CMCPvP.get().sessions().get(p);
            s.internalTeleport = true;

            boolean var3;
            try {
                if (p.isInsideVehicle()) {
                    p.leaveVehicle();
                }

                // 3.13.3: wings and shoulder pets ride the player; a player with riders can't change worlds
                if (l.getWorld() != p.getWorld() && CMCPvP.get().companions() != null) {
                    CMCPvP.get().companions().detach(p);
                }

                var3 = p.teleport(l, TeleportCause.PLUGIN);
            } finally {
                s.internalTeleport = false;
            }

            return var3;
        } else {
            return false;
        }
    }

    public static void keepLook(Player p, Location l) {
        Session s = CMCPvP.get().sessions().get(p);
        s.internalTeleport = true;

        try {
            Location target = l.clone();
            if (relativeRotation) {
                try {
                    target.setYaw(0.0F);
                    target.setPitch(0.0F);
                    p.teleport(target, TeleportCause.PLUGIN, new TeleportFlag[]{Relative.YAW, Relative.PITCH});
                    return;
                } catch (IllegalArgumentException | LinkageError var8) {
                    relativeRotation = false;
                }
            }

            target.setYaw(p.getLocation().getYaw());
            target.setPitch(p.getLocation().getPitch());
            p.teleport(target, TeleportCause.PLUGIN);
        } finally {
            s.internalTeleport = false;
        }
    }
}
