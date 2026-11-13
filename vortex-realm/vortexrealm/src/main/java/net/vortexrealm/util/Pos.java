package net.vortexrealm.util;

import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.World;

public record Pos(double x, double y, double z, float yaw, float pitch) {
    public static Pos of(Location l) {
        return new Pos(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location at(World w, int dx, int dy, int dz) {
        return new Location(w, this.x + dx, this.y + dy, this.z + dz, this.yaw, this.pitch);
    }

    public Location at(World w) {
        return this.at(w, 0, 0, 0);
    }

    public Pos withYaw(float newYaw) {
        return new Pos(this.x, this.y, this.z, newYaw, this.pitch);
    }

    public String serialize() {
        return String.format(Locale.US, "%.3f;%.3f;%.3f;%.2f;%.2f", this.x, this.y, this.z, this.yaw, this.pitch);
    }

    public static Pos parse(String s) {
        if (s != null && !s.isBlank()) {
            String[] p = s.split(";");
            if (p.length < 3) {
                return null;
            } else {
                try {
                    return new Pos(
                        Double.parseDouble(p[0]),
                        Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]),
                        p.length > 3 ? Float.parseFloat(p[3]) : 0.0F,
                        p.length > 4 ? Float.parseFloat(p[4]) : 0.0F
                    );
                } catch (NumberFormatException var3) {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    public int bx() {
        return (int)Math.floor(this.x);
    }

    public int by() {
        return (int)Math.floor(this.y);
    }

    public int bz() {
        return (int)Math.floor(this.z);
    }
}
