package net.practicepvp.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Is anyone close enough to see an animation? Animations with nobody around are skipped (no work, no packets). */
public final class Viewers {
    private Viewers() {
    }

    public static boolean near(Location l, double radius) {
        if (l == null) {
            return false;
        }

        World w = l.getWorld();
        if (w == null || !w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
            return false;
        }

        double r2 = radius * radius;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(l) <= r2) {
                return true;
            }
        }

        return false;
    }

    public static boolean near(Location l) {
        return near(l, 48.0);
    }
}
