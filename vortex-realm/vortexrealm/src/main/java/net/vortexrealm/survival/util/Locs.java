package net.vortexrealm.survival.util;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

public final class Locs {
    private Locs() {
    }

    public static String serialize(Location l) {
        return l != null && l.getWorld() != null
            ? l.getWorld().getName() + ";" + r(l.getX()) + ";" + r(l.getY()) + ";" + r(l.getZ()) + ";" + r(l.getYaw()) + ";" + r(l.getPitch())
            : "";
    }

    private static String r(double d) {
        return String.format(Locale.US, "%.3f", d);
    }

    public static Location parse(String s) {
        if (s != null && !s.isBlank()) {
            String[] p = s.split(";");
            if (p.length < 4) {
                return null;
            } else {
                World w = Bukkit.getWorld(p[0]);
                if (w == null) {
                    return null;
                } else {
                    try {
                        double x = Double.parseDouble(p[1]);
                        double y = Double.parseDouble(p[2]);
                        double z = Double.parseDouble(p[3]);
                        float yaw = p.length > 4 ? Float.parseFloat(p[4]) : 0.0F;
                        float pitch = p.length > 5 ? Float.parseFloat(p[5]) : 0.0F;
                        return new Location(w, x, y, z, yaw, pitch);
                    } catch (NumberFormatException var11) {
                        return null;
                    }
                }
            }
        } else {
            return null;
        }
    }

    public static String worldOf(String s) {
        if (s != null && !s.isBlank()) {
            int i = s.indexOf(59);
            return i < 0 ? null : s.substring(0, i);
        } else {
            return null;
        }
    }

    public static Location shift(Location base, World world, Vector offset) {
        if (base == null) {
            return null;
        } else {
            Location l = base.clone();
            l.setWorld(world);
            return l.add(offset);
        }
    }

    public static float yawTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float)Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static String pretty(Location l) {
        return l == null
            ? "<red>not set"
            : l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + (l.getWorld() != null ? " (" + l.getWorld().getName() + ")" : "");
    }

    public static long pack(int x, int y, int z) {
        return (long)(x & 67108863) << 38 | (long)(z & 67108863) << 12 | y & 4095;
    }

    public static int unpackX(long p) {
        return (int)(p >> 38);
    }

    public static int unpackY(long p) {
        int y = (int)(p & 4095L);
        return y >= 2048 ? y - 4096 : y;
    }

    public static int unpackZ(long p) {
        return (int)(p << 26 >> 38);
    }
}
