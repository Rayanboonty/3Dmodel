package net.vortexrealm.realm;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Candle;

/**
 * The Rift Shrine at the capital: a 15x15 dark plaza with a crying-obsidian ring, four amethyst pillars, a glowing
 * centre where the Unmaker rises, and candles. Built only when an admin types /realm shrine.
 */
final class Shrine {
    private Shrine() {
    }

    static void build(Location c) {
        World w = c.getWorld();
        int cx = c.getBlockX();
        int cy = c.getBlockY() - 1;
        int cz = c.getBlockZ();
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 7.4) {
                    continue;
                }

                // a solid floor and clear air above it
                for (int y = 1; y <= 8; y++) {
                    w.getBlockAt(cx + x, cy + y, cz + z).setType(Material.AIR, false);
                }

                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.DEEPSLATE_TILES, false);
                Material top = d > 6.4 ? Material.CRYING_OBSIDIAN : d > 5.5 ? Material.POLISHED_BLACKSTONE_BRICKS
                    : (x + z) % 2 == 0 ? Material.POLISHED_BLACKSTONE : Material.CHISELED_POLISHED_BLACKSTONE;
                if (d < 1.6) {
                    top = Material.PURPUR_BLOCK;
                }

                w.getBlockAt(cx + x, cy, cz + z).setType(top, false);
            }
        }

        w.getBlockAt(cx, cy, cz).setType(Material.AMETHYST_BLOCK, false);
        w.getBlockAt(cx, cy + 1, cz).setType(Material.AMETHYST_CLUSTER, false);
        int[][] pillars = {{5, 5}, {-5, 5}, {5, -5}, {-5, -5}};
        for (int[] p : pillars) {
            for (int y = 1; y <= 4; y++) {
                w.getBlockAt(cx + p[0], cy + y, cz + p[1]).setType(y == 4 ? Material.AMETHYST_BLOCK : Material.CRYING_OBSIDIAN, false);
            }

            w.getBlockAt(cx + p[0], cy + 5, cz + p[1]).setType(Material.END_ROD, false);
        }

        int[][] candles = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
        for (int[] k : candles) {
            Block b = w.getBlockAt(cx + k[0], cy + 1, cz + k[1]);
            b.setType(Material.PURPLE_CANDLE, false);
            if (b.getBlockData() instanceof Candle cd) {
                cd.setCandles(1 + Math.abs(k[0] + k[1]) % 4);
                cd.setLit(true);
                b.setBlockData(cd, false);
            }
        }
    }
}
