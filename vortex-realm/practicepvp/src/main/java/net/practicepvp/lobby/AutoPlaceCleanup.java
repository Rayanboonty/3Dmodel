package net.practicepvp.lobby;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.practicepvp.PracticePlugin;
import net.practicepvp.npc.Npc;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 1.0.10: lobby NPCs are placed by hand again. Version 1.0.9 built a booth for every lobby NPC its design had no spot
 * for; this takes those NPCs and their booths away once (the posts, the lights and the inlaid floor, which gets the
 * floor block around it back). NPCs placed or moved by hand are never touched: only the ids 1.0.9 wrote down.
 */
public final class AutoPlaceCleanup {
    private AutoPlaceCleanup() {
    }

    public static void run(PracticePlugin plugin) {
        File f = new File(plugin.getDataFolder(), "lobby-autoplace.yml");
        if (!f.exists() || plugin.npcs() == null) {
            return;
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<String> booths = y.getStringList("booths");
        int removed = 0;
        for (String id : booths) {
            Npc n = plugin.npcs().get(id);
            if (n == null || n.loc == null || n.loc.getWorld() == null) {
                continue;
            }

            clearBooth(n.loc, id);
            plugin.npcs().delete(n);
            removed++;
        }

        if (!f.delete()) {
            y.set("booths", null);
            try {
                y.save(f);
            } catch (IOException ignored) {
            }
        }

        if (removed > 0) {
            plugin.getLogger().info("Lobby: took away the " + removed + " NPC booths 1.0.9 placed by itself. Place lobby NPCs with /npc.");
        }
    }

    /** The materials 1.0.9 used for each booth: trim, inner floor, centre, posts, light. */
    private static Material[] style(String id) {
        if (id.endsWith("_cosmetics")) {
            return new Material[]{Material.AMETHYST_BLOCK, Material.SMOOTH_QUARTZ, Material.PURPUR_BLOCK, Material.PURPUR_PILLAR, Material.AMETHYST_CLUSTER};
        } else if (id.endsWith("_events")) {
            return new Material[]{Material.POLISHED_BLACKSTONE_BRICKS, Material.SMOOTH_STONE, Material.GOLD_BLOCK, Material.POLISHED_BLACKSTONE_WALL, Material.LANTERN};
        } else if (id.endsWith("_bots")) {
            return new Material[]{Material.MOSSY_STONE_BRICKS, Material.STONE_BRICKS, Material.EMERALD_BLOCK, Material.MOSSY_STONE_BRICK_WALL, Material.LANTERN};
        } else if (id.endsWith("_stats")) {
            return new Material[]{Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE, Material.SEA_LANTERN, Material.PRISMARINE_WALL, Material.SEA_LANTERN};
        } else if (id.endsWith("_academy")) {
            return new Material[]{Material.DARK_OAK_PLANKS, Material.POLISHED_DEEPSLATE, Material.LAPIS_BLOCK, Material.DARK_OAK_FENCE, Material.LANTERN};
        } else if (id.endsWith("_welcome")) {
            return new Material[]{Material.QUARTZ_BRICKS, Material.SMOOTH_QUARTZ, Material.CHISELED_QUARTZ_BLOCK, Material.END_STONE_BRICK_WALL, Material.LANTERN};
        }

        return new Material[]{Material.POLISHED_ANDESITE, Material.SMOOTH_STONE, Material.CHISELED_STONE_BRICKS, Material.ANDESITE_WALL, Material.LANTERN};
    }

    private static void clearBooth(Location at, String id) {
        Material[] s = style(id);
        Set<Material> floor = Set.of(s[0], s[1], s[2]);
        World w = at.getWorld();
        int x = at.getBlockX();
        int y = at.getBlockY() - 1;
        int z = at.getBlockZ();
        // the posts and their lights
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                for (int dy = 1; dy <= 3; dy++) {
                    Block b = w.getBlockAt(x + dx, y + dy, z + dz);
                    if (b.getType() == s[3] || b.getType() == s[4]) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }

        // the floor gets the most common floor block of the ring just outside the booth
        Map<Material, Integer> count = new HashMap<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) {
                    Material m = w.getBlockAt(x + dx, y, z + dz).getType();
                    if (m.isSolid() && m.isOccluding() && !floor.contains(m) && !Tag.LEAVES.isTagged(m)) {
                        count.merge(m, 1, Integer::sum);
                    }
                }
            }
        }

        Material back = count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (back == null) {
            return;
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block b = w.getBlockAt(x + dx, y, z + dz);
                if (floor.contains(b.getType())) {
                    b.setType(back, false);
                }
            }
        }
    }
}
