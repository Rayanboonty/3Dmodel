package net.vortexrealm.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** 3.14.1: the nearest spot where a player can stand (two free blocks, solid ground, no lava or fire). */
public final class SafeSpot {
    private SafeSpot() {
    }

    public static boolean safe(Block feet) {
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid()
            && feet.getType() != Material.FIRE && feet.getType() != Material.SOUL_FIRE && feet.getType() != Material.LAVA
            && (ground.getType().isSolid() || ground.getType() == Material.WATER) && ground.getType() != Material.MAGMA_BLOCK
            && ground.getType() != Material.LAVA;
    }

    /** The closest safe spot within {@code radius} blocks (closest first, then upwards), or null. */
    public static Location near(Location l, int radius) {
        if (l == null || l.getWorld() == null) {
            return null;
        }

        World w = l.getWorld();
        int bx = l.getBlockX();
        int by = l.getBlockY();
        int bz = l.getBlockZ();
        if (safe(w.getBlockAt(bx, by, bz))) {
            return l;
        }

        for (int r = 1; r <= radius; r++) {
            for (int dy = 0; dy <= r; dy++) {
                for (int sign : new int[] {1, -1}) {
                    int y = by + dy * sign;
                    if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 2) {
                        continue;
                    }

                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r && dy != r) {
                                continue;
                            }

                            Block b = w.getBlockAt(bx + dx, y, bz + dz);
                            if (safe(b)) {
                                Location out = b.getLocation().add(0.5, 0, 0.5);
                                out.setYaw(l.getYaw());
                                out.setPitch(l.getPitch());
                                return out;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }
}
