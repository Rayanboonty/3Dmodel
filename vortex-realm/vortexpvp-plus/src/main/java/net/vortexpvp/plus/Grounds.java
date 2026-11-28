package net.vortexpvp.plus;

import net.vortexpvp.VortexPvP;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The add-on's own building ground: one fresh 512x512 slot of the practice world (asked from the arena manager
 * once and remembered), cut into a 5x5 grid of 96-block plots for the arcade arenas and the training lanes.
 */
final class Grounds {
    private Grounds() {
    }

    static Location plot(VortexPvP plugin, PlusModule plus, int px, int pz) {
        YamlConfiguration y = plus.store().doc("ground");
        World w = plugin.worlds().practiceWorld();
        if (!y.contains("x")) {
            int[] o = plugin.arenas().allocateOrigin();
            y.set("x", o[0]);
            y.set("z", o[1]);
            plus.store().dirty("ground");
            plus.store().saveDirty();
        }

        return new Location(w, y.getInt("x") + 48 + px * 96 + 0.5, 100.0, y.getInt("z") + 48 + pz * 96 + 0.5);
    }

    static void box(World w, int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    w.getBlockAt(x, y, z).setType(m, false);
                }
            }
        }
    }

    /** Hollow walls (no floor or roof) around a square. */
    static void walls(World w, int cx, int cz, int r, int y1, int y2, Material m) {
        for (int i = -r; i <= r; i++) {
            for (int y = y1; y <= y2; y++) {
                w.getBlockAt(cx + i, y, cz - r).setType(m, false);
                w.getBlockAt(cx + i, y, cz + r).setType(m, false);
                w.getBlockAt(cx - r, y, cz + i).setType(m, false);
                w.getBlockAt(cx + r, y, cz + i).setType(m, false);
            }
        }
    }

    static Location facing(Location from, Location to) {
        Location l = from.clone();
        l.setDirection(to.toVector().subtract(from.toVector()));
        l.setPitch(0.0F);
        return l;
    }
}
