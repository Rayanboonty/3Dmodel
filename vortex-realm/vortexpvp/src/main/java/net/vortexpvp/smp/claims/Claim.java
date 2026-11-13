package net.vortexpvp.smp.claims;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Claim {
    public final int id;
    public UUID owner;
    public final String world;
    public int minX;
    public int minZ;
    public int maxX;
    public int maxZ;
    public String name = "";
    public long created = System.currentTimeMillis();
    public final Map<UUID, Claim.Trust> trusted = new LinkedHashMap<>();

    public Claim(int id, UUID owner, String world, int x1, int z1, int x2, int z2) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.setBox(x1, z1, x2, z2);
    }

    public void setBox(int x1, int z1, int x2, int z2) {
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean admin() {
        return this.owner == null;
    }

    public int width() {
        return this.maxX - this.minX + 1;
    }

    public int length() {
        return this.maxZ - this.minZ + 1;
    }

    public int area() {
        return this.width() * this.length();
    }

    public boolean contains(int x, int z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }

    public boolean overlaps(int x1, int z1, int x2, int z2) {
        return Math.min(x1, x2) <= this.maxX && Math.max(x1, x2) >= this.minX && Math.min(z1, z2) <= this.maxZ && Math.max(z1, z2) >= this.minZ;
    }

    public boolean isCorner(int x, int z) {
        return (x == this.minX || x == this.maxX) && (z == this.minZ || z == this.maxZ);
    }

    public Claim.Trust trustOf(UUID u) {
        return this.owner != null && this.owner.equals(u) ? Claim.Trust.MANAGE : this.trusted.get(u);
    }

    public boolean allows(UUID u, Claim.Trust need) {
        Claim.Trust t = this.trustOf(u);
        return t != null && t.ordinal() >= need.ordinal();
    }

    public String serializeBox() {
        return this.minX + ";" + this.minZ + ";" + this.maxX + ";" + this.maxZ;
    }

    public static enum Trust {
        ACCESS,
        CONTAINER,
        BUILD,
        MANAGE;

        public static Claim.Trust parse(String s, Claim.Trust def) {
            if (s == null) {
                return def;
            } else {
                try {
                    return valueOf(s.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException var3) {
                    return def;
                }
            }
        }
    }
}
