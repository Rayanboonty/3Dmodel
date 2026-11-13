package net.vortexpvp.ffa;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.vortexpvp.arena.ArenaTheme;
import net.vortexpvp.arena.ChangeTracker;
import net.vortexpvp.arena.RegionSnapshot;
import net.vortexpvp.util.Cuboid;
import net.vortexpvp.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class FfaArena {
    public final String id;
    public String displayName;
    public String kitId;
    public String worldName;
    public Cuboid bounds;
    public final List<Pos> spawns = new ArrayList<>();
    public boolean enabled = true;
    public ArenaTheme theme = ArenaTheme.CUSTOM;
    public Material iconBlock = Material.GRASS_BLOCK;
    public boolean generated;
    /** Set while the plugin generates it (saved), so an interrupted generation is noticed on the next start. */
    public boolean generating;
    public long seed;
    public int size;
    public String genSignature;
    public boolean handEdited;
    /** Vanilla terrain site (VanillaTerrain.Site) when this FFA arena is cut from vanilla terrain. */
    public String site;
    public final ChangeTracker tracker = new ChangeTracker();
    public RegionSnapshot snapshot;
    public long lastFullRegen = System.currentTimeMillis();
    public boolean regenWarned;
    public boolean building;
    public transient boolean editing;
    public final Set<UUID> players = new LinkedHashSet<>();

    public FfaArena(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public World world() {
        return this.worldName == null ? null : Bukkit.getWorld(this.worldName);
    }

    public boolean ready() {
        return this.enabled && !this.building && this.bounds != null && !this.spawns.isEmpty() && this.world() != null;
    }

    public boolean contains(Location l) {
        return l != null && l.getWorld() != null && l.getWorld().getName().equals(this.worldName) && this.bounds != null && this.bounds.contains(l);
    }

    public Location bestSpawn() {
        World w = this.world();
        List<Location> locs = new ArrayList<>();

        for (Pos p : this.spawns) {
            locs.add(p.at(w));
        }

        if (locs.isEmpty()) {
            return null;
        } else {
            List<double[]> scored = new ArrayList<>();

            for (int i = 0; i < locs.size(); i++) {
                double nearest = Double.MAX_VALUE;

                for (UUID u : this.players) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && p.getWorld().equals(w)) {
                        nearest = Math.min(nearest, p.getLocation().distanceSquared(locs.get(i)));
                    }
                }

                scored.add(new double[]{i, nearest});
            }

            scored.sort((a, b) -> Double.compare(b[1], a[1]));
            int pick = (int)scored.get(ThreadLocalRandom.current().nextInt(Math.min(3, scored.size())))[0];
            return locs.get(pick);
        }
    }
}
