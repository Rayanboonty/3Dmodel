package net.vortexrealm.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

public record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static Cuboid of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Cuboid(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public static Cuboid of(Location a, Location b) {
        return of(a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    public boolean contains(Location l) {
        return this.contains(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public boolean containsXZ(double x, double z) {
        return x >= this.minX && x < this.maxX + 1 && z >= this.minZ && z < this.maxZ + 1;
    }

    public Cuboid shift(int dx, int dy, int dz) {
        return new Cuboid(this.minX + dx, this.minY + dy, this.minZ + dz, this.maxX + dx, this.maxY + dy, this.maxZ + dz);
    }

    public Cuboid expand(int amount) {
        return new Cuboid(this.minX - amount, this.minY - amount, this.minZ - amount, this.maxX + amount, this.maxY + amount, this.maxZ + amount);
    }

    public int sizeX() {
        return this.maxX - this.minX + 1;
    }

    public int sizeY() {
        return this.maxY - this.minY + 1;
    }

    public int sizeZ() {
        return this.maxZ - this.minZ + 1;
    }

    public long volume() {
        return (long)this.sizeX() * this.sizeY() * this.sizeZ();
    }

    public double centerX() {
        return (this.minX + this.maxX + 1) / 2.0;
    }

    public double centerZ() {
        return (this.minZ + this.maxZ + 1) / 2.0;
    }

    public Location center(World w, double y) {
        return new Location(w, this.centerX(), y, this.centerZ());
    }

    public BoundingBox toBox() {
        return new BoundingBox(this.minX, this.minY, this.minZ, this.maxX + 1, this.maxY + 1, this.maxZ + 1);
    }

    public String serialize() {
        return this.minX + "," + this.minY + "," + this.minZ + "," + this.maxX + "," + this.maxY + "," + this.maxZ;
    }

    public static Cuboid parse(String s) {
        if (s != null && !s.isBlank()) {
            String[] p = s.split(",");
            if (p.length != 6) {
                return null;
            } else {
                try {
                    return of(
                        Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()),
                        Integer.parseInt(p[3].trim()),
                        Integer.parseInt(p[4].trim()),
                        Integer.parseInt(p[5].trim())
                    );
                } catch (NumberFormatException var3) {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "(" + this.minX + "," + this.minY + "," + this.minZ + ") -> (" + this.maxX + "," + this.maxY + "," + this.maxZ + ")";
    }
}
