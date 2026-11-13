package net.vortexpvp.deco;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.smp.WorldMode;
import net.vortexpvp.util.Text;
import org.bukkit.Material;

public final class ShrineModels {
    private ShrineModels() {
    }

    public static void refresh(VortexPvP plugin) {
        if (plugin.kits() != null) {
            for (Kit k : plugin.kits().sorted()) {
                Material icon = k.iconStack().getType();
                String label = k.color + "<b>" + Text.esc(k.displayName);
                shrine("kit_" + k.id, k.displayName + " Shrine", "Kit Shrines", icon, concrete(k.color), label);
            }
        }

        shrine("mode_ranked", "Ranked Shrine", "Gamemode Shrines", Material.NETHER_STAR, Material.ORANGE_CONCRETE, "<#FFB547><b>RANKED");
        shrine("mode_duels", "Duels Shrine", "Gamemode Shrines", Material.DIAMOND_SWORD, Material.LIGHT_BLUE_CONCRETE, "<aqua><b>DUELS");
        shrine("mode_ffa", "FFA Shrine", "Gamemode Shrines", Material.NETHERITE_AXE, Material.RED_CONCRETE, "<red><b>FREE FOR ALL");
        shrine("mode_party", "Party Shrine", "Gamemode Shrines", Material.CAKE, Material.PINK_CONCRETE, "<light_purple><b>PARTIES");
        shrine("mode_events", "Tournament Shrine", "Gamemode Shrines", Material.GOLDEN_HELMET, Material.YELLOW_CONCRETE, "<gold><b>TOURNAMENTS");

        for (WorldMode m : WorldMode.values()) {
            if (m != WorldMode.NONE) {
                shrine(
                    "mode_" + m.name().toLowerCase(Locale.ROOT),
                    m.display + " Shrine",
                    "Gamemode Shrines",
                    m.icon,
                    concrete(m.color),
                    m.color + "<b>" + m.display.toUpperCase(Locale.ROOT)
                );
            }
        }
    }

    private static void shrine(String id, String name, String cat, Material icon, Material accent, String label) {
        Material item = icon != null && !icon.isAir() && icon.isItem() ? icon : Material.NETHER_STAR;
        DecoModels.put(
            id,
            name,
            cat,
            item,
            DecoModels.Anim.SPIN_BOB,
            b -> {
                b.box(Material.POLISHED_BLACKSTONE_BRICKS, 0.0, 0.15, 0.0, 2.0, 0.3, 2.0)
                    .box(Material.GOLD_BLOCK, 0.0, 0.33, 0.0, 1.7, 0.06, 1.7)
                    .box(Material.POLISHED_BLACKSTONE, 0.0, 0.45, 0.0, 1.5, 0.2, 1.5)
                    .box(accent, 0.0, 0.85, 0.0, 1.0, 0.6, 1.0)
                    .box(Material.GOLD_BLOCK, 0.0, 1.18, 0.0, 1.1, 0.06, 1.1);

                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        b.box(Material.POLISHED_BLACKSTONE_WALL, sx * 0.82, 0.75, sz * 0.82, 0.2, 0.7, 0.2)
                            .box(Material.GOLD_BLOCK, sx * 0.82, 1.15, sz * 0.82, 0.24, 0.12, 0.24)
                            .item(Material.LANTERN, sx * 0.82, 1.42, sz * 0.82, 0.45, 0.0, 0.0, 0.0);
                    }
                }

                b.glowItem(
                    item,
                    0.0,
                    2.15,
                    0.0,
                    1.9,
                    0.0,
                    0.0,
                    !item.name().endsWith("_SWORD") && !item.name().endsWith("_AXE") && item != Material.MACE && item != Material.TRIDENT ? 0.0 : -45.0
                );
                b.text(label, 0.0, 3.35, 0.0, 1.6, 0.0);
            }
        );
    }

    private static Material concrete(String mini) {
        TextColor c = null;

        try {
            Component comp = Text.mm(mini + "x");
            c = comp.color();
            if (c == null && !comp.children().isEmpty()) {
                c = comp.children().get(0).color();
            }
        } catch (RuntimeException var16) {
        }

        if (c == null) {
            return Material.WHITE_CONCRETE;
        } else {
            Object[][] palette = new Object[][]{
                {Material.WHITE_CONCRETE, 13620694},
                {Material.LIGHT_GRAY_CONCRETE, 8224115},
                {Material.GRAY_CONCRETE, 3619390},
                {Material.BLACK_CONCRETE, 526863},
                {Material.RED_CONCRETE, 9314593},
                {Material.ORANGE_CONCRETE, 14704897},
                {Material.YELLOW_CONCRETE, 15838997},
                {Material.LIME_CONCRETE, 6203672},
                {Material.GREEN_CONCRETE, 4807460},
                {Material.CYAN_CONCRETE, 1406856},
                {Material.LIGHT_BLUE_CONCRETE, 2329030},
                {Material.BLUE_CONCRETE, 2895503},
                {Material.PURPLE_CONCRETE, 6561948},
                {Material.MAGENTA_CONCRETE, 11088031},
                {Material.PINK_CONCRETE, 13985166},
                {Material.BROWN_CONCRETE, 6306591}
            };
            int r = c.red();
            int g = c.green();
            int bl = c.blue();
            Material best = Material.WHITE_CONCRETE;
            double bestD = Double.MAX_VALUE;

            for (Object[] p : palette) {
                int v = (Integer)p[1];
                double d = Math.pow(r - (v >> 16 & 0xFF), 2.0) * 0.3 + Math.pow(g - (v >> 8 & 0xFF), 2.0) * 0.59 + Math.pow(bl - (v & 0xFF), 2.0) * 0.11;
                if (d < bestD) {
                    bestD = d;
                    best = (Material)p[0];
                }
            }

            return best;
        }
    }
}
