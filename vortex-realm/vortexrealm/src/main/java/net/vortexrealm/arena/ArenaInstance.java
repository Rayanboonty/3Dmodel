package net.vortexrealm.arena;

import net.vortexrealm.util.Cuboid;
import net.vortexrealm.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class ArenaInstance {
    public final Arena arena;
    public final int index;
    public final String worldName;
    public final int dx;
    public final int dz;
    public volatile ArenaInstance.State state = ArenaInstance.State.FREE;
    public Object owner;
    public final ChangeTracker tracker = new ChangeTracker();

    public ArenaInstance(Arena arena, int index, String worldName, int dx, int dz) {
        this.arena = arena;
        this.index = index;
        this.worldName = worldName;
        this.dx = dx;
        this.dz = dz;
    }

    public World world() {
        return Bukkit.getWorld(this.worldName);
    }

    public Cuboid bounds() {
        return this.arena.bounds.shift(this.dx, 0, this.dz);
    }

    public Location loc(Pos p) {
        return p == null ? null : p.at(this.world(), this.dx, 0, this.dz);
    }

    public Location spawn1() {
        return this.loc(this.arena.spawn1);
    }

    public Location spawn2() {
        return this.loc(this.arena.spawn2);
    }

    public Location center() {
        return this.loc(this.arena.centerOrMid());
    }

    public boolean contains(Location l) {
        return l != null && l.getWorld() != null && l.getWorld().getName().equals(this.worldName) && this.bounds().contains(l);
    }

    public boolean contains(World w, int x, int y, int z) {
        return w != null && w.getName().equals(this.worldName) && this.bounds().contains(x, y, z);
    }

    public int maxBuildY() {
        return this.arena.maxBuildY();
    }

    public String label() {
        return this.arena.id + "#" + this.index;
    }

    public static enum State {
        FREE,
        IN_USE,
        RESETTING,
        BUILDING,
        DISABLED;
    }
}
