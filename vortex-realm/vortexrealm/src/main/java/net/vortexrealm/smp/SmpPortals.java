package net.vortexrealm.smp;

import net.vortexrealm.VortexRealm;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * 3.13.5 - portals in an SMP world lead to that SMP's own nether and end (made in the World Manager), not to the
 * server's default ones: nether portals scale by 8, the end portal goes to the end platform, the exit back home.
 */
public final class SmpPortals implements Listener {
    private final VortexRealm plugin;

    public SmpPortals(VortexRealm plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        World from = e.getFrom().getWorld();
        if (from == null || this.plugin.modes() == null) {
            return;
        }

        World home = this.plugin.modes().parentOf(from);
        if (home == null || !this.plugin.modes().mode(home).smp()) {
            return;
        }

        World nether = Bukkit.getWorld(home.getName() + "_nether");
        World end = Bukkit.getWorld(home.getName() + "_the_end");
        Location f = e.getFrom();
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (from.equals(home) && nether != null) {
                e.setTo(new Location(nether, f.getX() / 8.0, f.getY(), f.getZ() / 8.0, f.getYaw(), f.getPitch()));
            } else if (nether != null && from.equals(nether)) {
                e.setTo(new Location(home, f.getX() * 8.0, f.getY(), f.getZ() * 8.0, f.getYaw(), f.getPitch()));
            }
        } else if (e.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (from.equals(home) && end != null) {
                e.setTo(new Location(end, 100.5, 49, 0.5, 90, 0));
            } else if (end != null && from.equals(end)) {
                e.setTo(this.plugin.modes().spawn(home));
            }
        }
    }
}
