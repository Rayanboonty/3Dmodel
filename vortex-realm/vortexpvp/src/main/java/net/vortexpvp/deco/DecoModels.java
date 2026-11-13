package net.vortexpvp.deco;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DecoModels {
    private static final Map<String, DecoModels.Model> ALL = new LinkedHashMap<>();

    private static void add(String id, String name, String cat, Material icon, DecoModels.Anim anim, Consumer<DecoModels.B> build) {
        DecoModels.B b = new DecoModels.B();
        build.accept(b);
        ALL.put(id, new DecoModels.Model(id, name, cat, icon, anim, List.copyOf(b.parts)));
    }

    public static void put(String id, String name, String cat, Material icon, DecoModels.Anim anim, Consumer<DecoModels.B> build) {
        add(id, name, cat, icon, anim, build);
    }

    public static Map<String, DecoModels.Model> all() {
        return ALL;
    }

    public static DecoModels.Model get(String id) {
        return ALL.get(id);
    }

    public static List<String> categories() {
        List<String> out = new ArrayList<>();

        for (DecoModels.Model m : ALL.values()) {
            if (!out.contains(m.category())) {
                out.add(m.category());
            }
        }

        return out;
    }

    private DecoModels() {
    }

    static {
        add(
            "giant_sword",
            "Giant Diamond Sword",
            "Weapons",
            Material.DIAMOND_SWORD,
            DecoModels.Anim.SPIN_BOB,
            b -> b.glowItem(Material.DIAMOND_SWORD, 0.0, 2.6, 0.0, 3.6, 0.0, 0.0, -45.0)
                .box(Material.POLISHED_BLACKSTONE, 0.0, 0.25, 0.0, 1.6, 0.5, 1.6)
                .box(Material.GOLD_BLOCK, 0.0, 0.55, 0.0, 1.0, 0.1, 1.0)
        );
        add(
            "sword_in_stone",
            "Sword in the Stone",
            "Weapons",
            Material.NETHERITE_SWORD,
            DecoModels.Anim.NONE,
            b -> b.box(Material.MOSSY_COBBLESTONE, 0.0, 0.4, 0.0, 1.8, 0.8, 1.4)
                .box(Material.COBBLESTONE, 0.1, 0.95, -0.05, 1.2, 0.4, 1.0, 0.0, 12.0, 0.0)
                .box(Material.STONE, -0.1, 1.25, 0.05, 0.8, 0.3, 0.7, 0.0, -8.0, 0.0)
                .item(Material.NETHERITE_SWORD, 0.0, 2.15, 0.0, 1.9, 0.0, 0.0, 135.0)
                .box(Material.MOSS_CARPET, 0.6, 0.82, 0.4, 0.5, 0.05, 0.4)
        );
        add(
            "crossed_swords",
            "Crossed Swords Crest",
            "Weapons",
            Material.SHIELD,
            DecoModels.Anim.NONE,
            b -> b.item(Material.SHIELD, 0.0, 1.6, 0.0, 1.6, 0.0, 0.0, 0.0)
                .item(Material.DIAMOND_SWORD, -0.2, 1.7, -0.15, 2.0, 0.0, 0.0, -45.0)
                .item(Material.NETHERITE_SWORD, 0.2, 1.7, -0.15, 2.0, 0.0, 180.0, -45.0)
        );
        add(
            "giant_mace",
            "Giant Mace",
            "Weapons",
            Material.MACE,
            DecoModels.Anim.SPIN_BOB,
            b -> b.glowItem(Material.MACE, 0.0, 2.4, 0.0, 3.4, 0.0, 0.0, -45.0)
                .box(Material.CHISELED_TUFF, 0.0, 0.3, 0.0, 1.4, 0.6, 1.4)
                .box(Material.COPPER_BLOCK, 0.0, 0.65, 0.0, 0.9, 0.1, 0.9)
        );
        add("weapon_rack", "Weapon Rack", "Weapons", Material.IRON_AXE, DecoModels.Anim.NONE, b -> {
            b.box(Material.SPRUCE_PLANKS, 0.0, 0.1, 0.0, 2.4, 0.2, 0.6);
            b.box(Material.SPRUCE_LOG, -1.1, 1.0, 0.0, 0.2, 1.8, 0.2).box(Material.SPRUCE_LOG, 1.1, 1.0, 0.0, 0.2, 1.8, 0.2);
            b.box(Material.STRIPPED_SPRUCE_LOG, 0.0, 1.8, 0.0, 2.4, 0.15, 0.2);
            Material[] w = new Material[]{Material.DIAMOND_SWORD, Material.IRON_AXE, Material.NETHERITE_SWORD, Material.BOW, Material.TRIDENT};

            for (int i = 0; i < w.length; i++) {
                b.item(w[i], -0.8 + i * 0.4, 1.0, 0.15, 1.1, 0.0, 0.0, w[i] == Material.BOW ? 0.0 : -45.0);
            }
        });
        add(
            "totem_shrine",
            "Totem Shrine",
            "Weapons",
            Material.TOTEM_OF_UNDYING,
            DecoModels.Anim.SPIN_BOB,
            b -> b.box(Material.EMERALD_BLOCK, 0.0, 0.2, 0.0, 1.4, 0.4, 1.4)
                .box(Material.GOLD_BLOCK, 0.0, 0.5, 0.0, 0.9, 0.2, 0.9)
                .glowItem(Material.TOTEM_OF_UNDYING, 0.0, 1.9, 0.0, 2.2, 0.0, 0.0, 0.0)
        );
        add(
            "armor_display",
            "Armour Display",
            "Weapons",
            Material.NETHERITE_CHESTPLATE,
            DecoModels.Anim.SPIN,
            b -> b.box(Material.POLISHED_DEEPSLATE, 0.0, 0.1, 0.0, 1.0, 0.2, 1.0)
                .box(Material.DARK_OAK_FENCE, 0.0, 1.0, 0.0, 0.12, 1.6, 0.12)
                .item(Material.NETHERITE_HELMET, 0.0, 2.05, 0.0, 0.9, 0.0, 0.0, 0.0)
                .item(Material.NETHERITE_CHESTPLATE, 0.0, 1.45, 0.0, 1.1, 0.0, 0.0, 0.0)
                .item(Material.NETHERITE_LEGGINGS, 0.0, 0.95, 0.0, 1.0, 0.0, 0.0, 0.0)
                .item(Material.NETHERITE_BOOTS, 0.0, 0.45, 0.0, 0.9, 0.0, 0.0, 0.0)
        );
        add(
            "crystal_pedestal",
            "End Crystal Pedestal",
            "Weapons",
            Material.END_CRYSTAL,
            DecoModels.Anim.SPIN_BOB,
            b -> b.box(Material.OBSIDIAN, 0.0, 0.3, 0.0, 1.2, 0.6, 1.2)
                .box(Material.CRYING_OBSIDIAN, 0.0, 0.7, 0.0, 0.8, 0.2, 0.8)
                .glowItem(Material.END_CRYSTAL, 0.0, 1.7, 0.0, 1.8, 0.0, 0.0, 0.0)
        );
        add(
            "trophy",
            "Golden Trophy",
            "Trophies",
            Material.GOLD_INGOT,
            DecoModels.Anim.SPIN,
            b -> b.box(Material.POLISHED_BLACKSTONE, 0.0, 0.15, 0.0, 1.0, 0.3, 1.0)
                .box(Material.GOLD_BLOCK, 0.0, 0.4, 0.0, 0.6, 0.2, 0.6)
                .box(Material.GOLD_BLOCK, 0.0, 0.75, 0.0, 0.18, 0.5, 0.18)
                .box(Material.GOLD_BLOCK, 0.0, 1.05, 0.0, 0.5, 0.12, 0.5)
                .box(Material.GOLD_BLOCK, 0.0, 1.4, 0.0, 0.8, 0.6, 0.8)
                .box(Material.YELLOW_STAINED_GLASS, 0.0, 1.68, 0.0, 0.66, 0.06, 0.66)
                .box(Material.GOLD_BLOCK, -0.5, 1.45, 0.0, 0.12, 0.4, 0.12)
                .box(Material.GOLD_BLOCK, 0.5, 1.45, 0.0, 0.12, 0.4, 0.12)
                .box(Material.GOLD_BLOCK, -0.42, 1.67, 0.0, 0.2, 0.08, 0.1)
                .box(Material.GOLD_BLOCK, 0.42, 1.67, 0.0, 0.2, 0.08, 0.1)
                .item(Material.NETHER_STAR, 0.0, 2.05, 0.0, 0.6, 0.0, 0.0, 0.0)
        );
        add(
            "podium",
            "Champion Podium",
            "Trophies",
            Material.GOLD_BLOCK,
            DecoModels.Anim.NONE,
            b -> b.box(Material.GOLD_BLOCK, 0.0, 0.75, 0.0, 1.4, 1.5, 1.4)
                .box(Material.IRON_BLOCK, -1.4, 0.5, 0.0, 1.4, 1.0, 1.4)
                .box(Material.COPPER_BLOCK, 1.4, 0.35, 0.0, 1.4, 0.7, 1.4)
                .text("<gold><b>1", 0.0, 1.0, 0.72, 2.2, 0.0)
                .text("<white><b>2", -1.4, 0.6, 0.72, 2.0, 0.0)
                .text("<#E8A87C><b>3", 1.4, 0.4, 0.72, 2.0, 0.0)
                .box(Material.RED_CARPET, 0.0, 1.52, 0.0, 1.2, 0.04, 1.2)
        );
        add(
            "medal",
            "Giant Medal",
            "Trophies",
            Material.GOLD_NUGGET,
            DecoModels.Anim.SPIN_BOB,
            b -> {
                b.box(Material.RED_WOOL, -0.25, 2.6, 0.0, 0.18, 1.0, 0.05, 0.0, 0.0, 15.0)
                    .box(Material.BLUE_WOOL, 0.25, 2.6, 0.0, 0.18, 1.0, 0.05, 0.0, 0.0, -15.0);

                for (int i = 0; i < 16; i++) {
                    double a = (Math.PI * 2) * i / 16.0;
                    b.box(Material.GOLD_BLOCK, Math.cos(a) * 0.55, 1.7 + Math.sin(a) * 0.55, 0.0, 0.24, 0.24, 0.12, 0.0, 0.0, Math.toDegrees(a));
                }

                b.box(Material.RAW_GOLD_BLOCK, 0.0, 1.7, 0.0, 0.8, 0.8, 0.1).item(Material.NETHER_STAR, 0.0, 1.7, 0.08, 0.5, 0.0, 0.0, 0.0);
            }
        );
        add(
            "leaderboard_pillar",
            "Leaderboard Pillar",
            "Trophies",
            Material.QUARTZ_PILLAR,
            DecoModels.Anim.NONE,
            b -> b.box(Material.QUARTZ_PILLAR, 0.0, 1.5, 0.0, 0.8, 3.0, 0.8)
                .box(Material.CHISELED_QUARTZ_BLOCK, 0.0, 0.2, 0.0, 1.2, 0.4, 1.2)
                .box(Material.CHISELED_QUARTZ_BLOCK, 0.0, 3.1, 0.0, 1.2, 0.3, 1.2)
                .glowItem(Material.NETHER_STAR, 0.0, 3.8, 0.0, 0.9, 0.0, 0.0, 0.0)
                .text("<gold><b>TOP PLAYERS", 0.0, 2.4, 0.45, 0.8, 0.0)
        );
        add("crown", "Floating Crown", "Trophies", Material.GOLDEN_HELMET, DecoModels.Anim.SPIN_BOB, b -> {
            b.ring(Material.GOLD_BLOCK, 2.2, 0.55, 12, 0.32, 0.35);

            for (int i = 0; i < 6; i++) {
                double a = (Math.PI * 2) * i / 6.0;
                b.box(Material.GOLD_BLOCK, Math.cos(a) * 0.55, 2.55, Math.sin(a) * 0.55, 0.2, 0.4, 0.2, 0.0, -Math.toDegrees(a), 0.0);
                b.box(i % 2 == 0 ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK, Math.cos(a) * 0.58, 2.2, Math.sin(a) * 0.58, 0.14, 0.14, 0.14);
            }
        });
        add(
            "lamp_post",
            "Lamp Post",
            "Lights",
            Material.LANTERN,
            DecoModels.Anim.NONE,
            b -> b.box(Material.POLISHED_BLACKSTONE, 0.0, 0.15, 0.0, 0.6, 0.3, 0.6)
                .box(Material.DARK_OAK_FENCE, 0.0, 1.6, 0.0, 0.18, 2.8, 0.18)
                .box(Material.POLISHED_BLACKSTONE, 0.0, 3.05, 0.35, 0.14, 0.14, 0.9)
                .box(Material.IRON_CHAIN, 0.0, 2.85, 0.75, 0.12, 0.3, 0.12)
                .box(Material.LANTERN, 0.0, 2.5, 0.75, 0.45, 0.45, 0.45)
        );
        add(
            "double_lamp",
            "Double Street Lamp",
            "Lights",
            Material.SOUL_LANTERN,
            DecoModels.Anim.NONE,
            b -> {
                b.box(Material.STONE_BRICKS, 0.0, 0.25, 0.0, 0.7, 0.5, 0.7)
                    .box(Material.IRON_BARS, 0.0, 2.0, 0.0, 0.2, 3.2, 0.2)
                    .box(Material.IRON_BLOCK, 0.0, 3.5, 0.0, 1.8, 0.12, 0.12);

                for (int s = -1; s <= 1; s += 2) {
                    b.box(Material.IRON_CHAIN, s * 0.8, 3.3, 0.0, 0.12, 0.3, 0.12).box(Material.SOUL_LANTERN, s * 0.8, 2.95, 0.0, 0.5, 0.5, 0.5);
                }
            }
        );
        add(
            "brazier",
            "Fire Brazier",
            "Lights",
            Material.CAMPFIRE,
            DecoModels.Anim.NONE,
            b -> b.box(Material.POLISHED_BLACKSTONE_BRICKS, 0.0, 0.5, 0.0, 0.6, 1.0, 0.6)
                .box(Material.POLISHED_BLACKSTONE, 0.0, 1.1, 0.0, 1.0, 0.2, 1.0)
                .box(Material.CAMPFIRE, 0.0, 1.45, 0.0, 0.9, 0.5, 0.9)
                .box(Material.MAGMA_BLOCK, 0.0, 1.2, 0.0, 0.8, 0.05, 0.8)
        );
        add("lantern_string", "Lantern String", "Lights", Material.IRON_CHAIN, DecoModels.Anim.NONE, b -> {
            for (int i = -3; i <= 3; i++) {
                double sag = 3.2 - 0.4 * (1.0 - i * i / 9.0);
                b.box(Material.IRON_CHAIN, i * 0.6, sag, 0.0, 0.6, 0.06, 0.06, 0.0, 0.0, i * 6);
                if (i % 2 != 0) {
                    b.box(i % 4 != 1 && i % 4 != -3 ? Material.SOUL_LANTERN : Material.LANTERN, i * 0.6, sag - 0.35, 0.0, 0.4, 0.4, 0.4);
                }
            }

            b.box(Material.SPRUCE_FENCE, -2.1, 1.7, 0.0, 0.18, 3.4, 0.18).box(Material.SPRUCE_FENCE, 2.1, 1.7, 0.0, 0.18, 3.4, 0.18);
        });
        add(
            "beacon_tower",
            "Beacon Tower",
            "Lights",
            Material.BEACON,
            DecoModels.Anim.NONE,
            b -> b.box(Material.IRON_BLOCK, 0.0, 0.5, 0.0, 1.6, 1.0, 1.6)
                .box(Material.DIAMOND_BLOCK, 0.0, 1.15, 0.0, 1.2, 0.3, 1.2)
                .box(Material.BEACON, 0.0, 1.75, 0.0, 0.9, 0.9, 0.9)
                .box(Material.LIGHT_BLUE_STAINED_GLASS, 0.0, 3.0, 0.0, 0.3, 1.6, 0.3)
                .box(Material.WHITE_STAINED_GLASS, 0.0, 4.4, 0.0, 0.2, 1.2, 0.2)
        );
        add(
            "glow_orb",
            "Glow Orb",
            "Lights",
            Material.GLOWSTONE,
            DecoModels.Anim.BOB,
            b -> b.ball(Material.GLOWSTONE, 0.0, 2.2, 0.0, 0.6, 0.3).box(Material.YELLOW_STAINED_GLASS, 0.0, 2.2, 0.0, 1.0, 1.0, 1.0, 45.0, 45.0, 0.0)
        );
        add(
            "bonsai",
            "Bonsai Tree",
            "Nature",
            Material.OAK_SAPLING,
            DecoModels.Anim.NONE,
            b -> b.box(Material.TERRACOTTA, 0.0, 0.3, 0.0, 1.0, 0.6, 1.0)
                .box(Material.PODZOL, 0.0, 0.62, 0.0, 0.85, 0.05, 0.85)
                .box(Material.DARK_OAK_LOG, 0.0, 1.0, 0.0, 0.2, 0.8, 0.2, 0.0, 0.0, 12.0)
                .box(Material.DARK_OAK_LOG, 0.15, 1.5, 0.0, 0.15, 0.6, 0.15, 0.0, 0.0, -25.0)
                .ball(Material.AZALEA_LEAVES, 0.3, 1.9, 0.0, 0.45, 0.2)
                .ball(Material.FLOWERING_AZALEA_LEAVES, -0.25, 1.65, 0.1, 0.32, 0.2)
        );
        add(
            "cherry_tree",
            "Cherry Blossom Tree",
            "Nature",
            Material.CHERRY_SAPLING,
            DecoModels.Anim.NONE,
            b -> b.box(Material.CHERRY_LOG, 0.0, 1.5, 0.0, 0.5, 3.0, 0.5)
                .box(Material.CHERRY_LOG, 0.6, 2.8, 0.0, 0.35, 1.2, 0.35, 0.0, 0.0, -40.0)
                .box(Material.CHERRY_LOG, -0.5, 3.0, 0.2, 0.35, 1.0, 0.35, 20.0, 0.0, 35.0)
                .ball(Material.CHERRY_LEAVES, 0.0, 4.0, 0.0, 1.6, 0.6)
                .ball(Material.CHERRY_LEAVES, 1.2, 3.5, 0.3, 0.9, 0.45)
                .ball(Material.CHERRY_LEAVES, -1.1, 3.6, -0.3, 0.9, 0.45)
                .box(Material.PINK_PETALS, 0.8, 0.03, 0.9, 1.2, 0.05, 1.0)
        );
        add(
            "floating_island",
            "Floating Island",
            "Nature",
            Material.GRASS_BLOCK,
            DecoModels.Anim.BOB,
            b -> {
                b.box(Material.GRASS_BLOCK, 0.0, 2.0, 0.0, 2.4, 0.4, 2.4);
                b.box(Material.DIRT, 0.0, 1.6, 0.0, 2.0, 0.4, 2.0)
                    .box(Material.DIRT, 0.0, 1.2, 0.0, 1.4, 0.4, 1.4)
                    .box(Material.STONE, 0.0, 0.85, 0.0, 0.8, 0.3, 0.8)
                    .box(Material.STONE, 0.0, 0.6, 0.0, 0.4, 0.2, 0.4);
                b.box(Material.OAK_LOG, 0.5, 2.8, 0.4, 0.3, 1.2, 0.3).ball(Material.OAK_LEAVES, 0.5, 3.6, 0.4, 0.75, 0.3);
                b.box(Material.POPPY, -0.6, 2.4, -0.5, 0.4, 0.4, 0.4).box(Material.DANDELION, -0.3, 2.4, 0.7, 0.4, 0.4, 0.4);
            }
        );
        add(
            "crystal_cluster",
            "Amethyst Cluster",
            "Nature",
            Material.AMETHYST_CLUSTER,
            DecoModels.Anim.NONE,
            b -> b.box(Material.BUDDING_AMETHYST, 0.0, 0.3, 0.0, 1.4, 0.6, 1.2)
                .box(Material.AMETHYST_BLOCK, 0.0, 1.4, 0.0, 0.5, 2.2, 0.5, 0.0, 0.0, 8.0)
                .box(Material.AMETHYST_BLOCK, 0.5, 1.0, 0.2, 0.4, 1.5, 0.4, 0.0, 0.0, -25.0)
                .box(Material.AMETHYST_BLOCK, -0.5, 0.9, -0.1, 0.35, 1.3, 0.35, 10.0, 0.0, 28.0)
                .box(Material.AMETHYST_BLOCK, 0.1, 0.8, -0.5, 0.3, 1.0, 0.3, -25.0, 0.0, 0.0)
                .glowItem(Material.AMETHYST_CLUSTER, -0.2, 0.75, 0.55, 0.8, 0.0, 0.0, 0.0)
        );
        add("planter", "Flower Planter", "Nature", Material.POPPY, DecoModels.Anim.NONE, b -> {
            b.box(Material.SPRUCE_PLANKS, 0.0, 0.3, 0.0, 2.2, 0.6, 0.8).box(Material.ROOTED_DIRT, 0.0, 0.58, 0.0, 2.0, 0.05, 0.6);
            Material[] f = new Material[]{Material.POPPY, Material.CORNFLOWER, Material.ALLIUM, Material.OXEYE_DAISY, Material.ORANGE_TULIP};

            for (int i = 0; i < f.length; i++) {
                b.box(f[i], -0.8 + i * 0.4, 0.85, i % 2 * 0.15 - 0.07, 0.45, 0.45, 0.45);
            }
        });
        add(
            "mushroom",
            "Giant Mushroom",
            "Nature",
            Material.RED_MUSHROOM,
            DecoModels.Anim.NONE,
            b -> b.box(Material.MUSHROOM_STEM, 0.0, 1.0, 0.0, 0.6, 2.0, 0.6)
                .box(Material.RED_MUSHROOM_BLOCK, 0.0, 2.2, 0.0, 2.2, 0.5, 2.2)
                .box(Material.RED_MUSHROOM_BLOCK, 0.0, 2.6, 0.0, 1.6, 0.3, 1.6)
                .box(Material.WHITE_CONCRETE, 0.5, 2.46, 0.6, 0.3, 0.05, 0.3)
                .box(Material.WHITE_CONCRETE, -0.6, 2.46, -0.3, 0.3, 0.05, 0.3)
        );
        add(
            "globe",
            "Spinning Globe",
            "Nature",
            Material.HEART_OF_THE_SEA,
            DecoModels.Anim.SPIN,
            b -> {
                b.box(Material.DARK_OAK_PLANKS, 0.0, 0.1, 0.0, 1.0, 0.2, 1.0).box(Material.GOLD_BLOCK, 0.0, 0.6, 0.0, 0.1, 0.8, 0.1);
                int n = 4;
                double c = 0.26;

                for (int x = -n; x <= n; x++) {
                    for (int y = -n; y <= n; y++) {
                        for (int z = -n; z <= n; z++) {
                            double d = Math.sqrt(x * x + y * y + z * z) * c;
                            if (d <= 1.0 && d > 1.0 - c * 1.8) {
                                double lon = Math.atan2(z, x);
                                double lat = Math.asin(y * c / Math.max(0.01, d));
                                boolean land = Math.sin(lon * 3.0 + lat * 2.0) + Math.cos(lat * 4.0 - lon) * 0.6 > 0.55;
                                b.box(
                                    land ? (Math.abs(lat) > 1.1 ? Material.SNOW_BLOCK : Material.LIME_CONCRETE) : Material.BLUE_CONCRETE,
                                    x * c,
                                    2.1 + y * c,
                                    z * c,
                                    c,
                                    c,
                                    c
                                );
                            }
                        }
                    }
                }
            }
        );
        add(
            "bench",
            "Park Bench",
            "Furniture",
            Material.OAK_STAIRS,
            DecoModels.Anim.NONE,
            b -> {
                for (int s = -1; s <= 1; s += 2) {
                    b.box(Material.DARK_OAK_FENCE, s * 0.9, 0.25, 0.0, 0.12, 0.5, 0.5);
                }

                b.box(Material.OAK_PLANKS, 0.0, 0.5, 0.0, 2.2, 0.1, 0.6)
                    .box(Material.OAK_PLANKS, 0.0, 0.95, -0.3, 2.2, 0.5, 0.08)
                    .box(Material.DARK_OAK_FENCE, -1.05, 0.7, 0.0, 0.1, 0.1, 0.6)
                    .box(Material.DARK_OAK_FENCE, 1.05, 0.7, 0.0, 0.1, 0.1, 0.6);
            }
        );
        add(
            "picnic_table",
            "Picnic Table",
            "Furniture",
            Material.OAK_PLANKS,
            DecoModels.Anim.NONE,
            b -> b.box(Material.SPRUCE_PLANKS, 0.0, 0.85, 0.0, 2.4, 0.12, 1.0)
                .box(Material.SPRUCE_LOG, -0.9, 0.42, 0.0, 0.15, 0.85, 0.15)
                .box(Material.SPRUCE_LOG, 0.9, 0.42, 0.0, 0.15, 0.85, 0.15)
                .box(Material.SPRUCE_SLAB, 0.0, 0.45, 0.85, 2.4, 0.1, 0.4)
                .box(Material.SPRUCE_SLAB, 0.0, 0.45, -0.85, 2.4, 0.1, 0.4)
                .item(Material.CAKE, -0.4, 1.0, 0.0, 0.5, 0.0, 0.0, 0.0)
                .item(Material.GOLDEN_APPLE, 0.5, 0.97, 0.1, 0.35, 90.0, 0.0, 0.0)
        );
        add(
            "throne",
            "Royal Throne",
            "Furniture",
            Material.GOLD_BLOCK,
            DecoModels.Anim.NONE,
            b -> b.box(Material.RED_CARPET, 0.0, 0.02, 1.2, 1.2, 0.04, 2.4)
                .box(Material.GOLD_BLOCK, 0.0, 0.3, 0.0, 1.4, 0.6, 1.2)
                .box(Material.RED_WOOL, 0.0, 0.65, 0.05, 1.1, 0.1, 1.0)
                .box(Material.GOLD_BLOCK, 0.0, 1.6, -0.5, 1.4, 2.2, 0.2)
                .box(Material.RED_WOOL, 0.0, 1.4, -0.38, 1.0, 1.4, 0.05)
                .box(Material.GOLD_BLOCK, -0.65, 0.95, 0.0, 0.15, 0.5, 1.1)
                .box(Material.GOLD_BLOCK, 0.65, 0.95, 0.0, 0.15, 0.5, 1.1)
                .box(Material.EMERALD_BLOCK, 0.0, 2.8, -0.5, 0.3, 0.3, 0.25, 0.0, 0.0, 45.0)
        );
        add(
            "market_stall",
            "Market Stall",
            "Furniture",
            Material.BARREL,
            DecoModels.Anim.NONE,
            b -> {
                b.box(Material.SPRUCE_PLANKS, 0.0, 0.9, 0.3, 2.6, 0.12, 1.0).box(Material.SPRUCE_PLANKS, 0.0, 0.45, 0.3, 2.6, 0.9, 0.1);

                for (int s = -1; s <= 1; s += 2) {
                    b.box(Material.SPRUCE_FENCE, s * 1.25, 1.4, 0.7, 0.14, 2.8, 0.14).box(Material.SPRUCE_FENCE, s * 1.25, 1.4, -0.4, 0.14, 2.8, 0.14);
                }

                for (int i = 0; i < 6; i++) {
                    b.box(i % 2 == 0 ? Material.RED_WOOL : Material.WHITE_WOOL, -1.15 + i * 0.46, 2.85, 0.15, 0.46, 0.12, 1.6, 18.0, 0.0, 0.0);
                }

                b.box(Material.BARREL, -0.8, 1.2, 0.3, 0.5, 0.5, 0.5)
                    .item(Material.APPLE, 0.1, 1.05, 0.4, 0.4, 90.0, 0.0, 0.0)
                    .item(Material.MELON_SLICE, 0.5, 1.05, 0.3, 0.4, 90.0, 0.0, 0.0)
                    .item(Material.BREAD, 0.85, 1.05, 0.45, 0.4, 90.0, 30.0, 0.0);
            }
        );
        add(
            "well",
            "Village Well",
            "Furniture",
            Material.COBBLESTONE_WALL,
            DecoModels.Anim.NONE,
            b -> {
                b.ring(Material.COBBLESTONE, 0.45, 1.0, 14, 0.42, 0.9);
                b.box(Material.LIGHT_BLUE_STAINED_GLASS, 0.0, 0.6, 0.0, 1.4, 0.05, 1.4);

                for (int s = -1; s <= 1; s += 2) {
                    b.box(Material.SPRUCE_FENCE, s * 1.0, 1.6, 0.0, 0.15, 2.2, 0.15);
                }

                b.box(Material.SPRUCE_LOG, 0.0, 2.4, 0.0, 2.2, 0.15, 0.15)
                    .box(Material.IRON_CHAIN, 0.0, 1.9, 0.0, 0.1, 0.9, 0.1)
                    .box(Material.CAULDRON, 0.0, 1.3, 0.0, 0.35, 0.35, 0.35)
                    .box(Material.SPRUCE_STAIRS, -0.6, 2.9, 0.0, 1.4, 0.15, 2.0, 0.0, 0.0, 30.0)
                    .box(Material.SPRUCE_STAIRS, 0.6, 2.9, 0.0, 1.4, 0.15, 2.0, 0.0, 0.0, -30.0);
            }
        );
        add(
            "anvil_forge",
            "Blacksmith Forge",
            "Furniture",
            Material.ANVIL,
            DecoModels.Anim.NONE,
            b -> b.box(Material.ANVIL, -0.7, 0.4, 0.0, 0.8, 0.8, 0.8)
                .box(Material.SMITHING_TABLE, 0.6, 0.4, 0.0, 0.8, 0.8, 0.8)
                .box(Material.BLAST_FURNACE, 1.5, 0.45, -0.2, 0.9, 0.9, 0.9)
                .box(Material.GRINDSTONE, -1.6, 0.35, 0.2, 0.6, 0.7, 0.6)
                .item(Material.IRON_INGOT, 0.6, 0.85, 0.1, 0.3, 90.0, 20.0, 0.0)
                .item(Material.DIAMOND, 0.75, 0.85, -0.1, 0.3, 90.0, 60.0, 0.0)
        );
        add(
            "treasure",
            "Treasure Chest",
            "Furniture",
            Material.CHEST,
            DecoModels.Anim.NONE,
            b -> b.box(Material.CHEST, 0.0, 0.35, 0.0, 0.9, 0.7, 0.7)
                .box(Material.RAW_GOLD_BLOCK, 0.7, 0.15, 0.4, 0.6, 0.3, 0.5, 0.0, 30.0, 0.0)
                .item(Material.GOLD_INGOT, -0.6, 0.06, 0.5, 0.4, 90.0, 40.0, 0.0)
                .item(Material.DIAMOND, 0.3, 0.06, 0.7, 0.35, 90.0, 10.0, 0.0)
                .item(Material.EMERALD, -0.2, 0.06, 0.65, 0.35, 90.0, 70.0, 0.0)
                .item(Material.GOLDEN_APPLE, 0.85, 0.42, 0.45, 0.35, 0.0, 0.0, 0.0)
        );
        add("campfire_circle", "Campfire Circle", "Furniture", Material.CAMPFIRE, DecoModels.Anim.NONE, b -> {
            b.box(Material.CAMPFIRE, 0.0, 0.25, 0.0, 0.9, 0.5, 0.9);

            for (int i = 0; i < 4; i++) {
                double a = (Math.PI / 2) * i + (Math.PI / 4);
                b.box(Material.STRIPPED_OAK_LOG, Math.cos(a) * 1.5, 0.2, Math.sin(a) * 1.5, 1.2, 0.4, 0.4, 0.0, -Math.toDegrees(a) + 90.0, 0.0);
            }
        });
        add(
            "vortex_portal",
            "Vortex Portal",
            "Magic",
            Material.ENDER_EYE,
            DecoModels.Anim.SPIN,
            b -> {
                for (int i = 0; i < 20; i++) {
                    double a = (Math.PI * 2) * i / 20.0;
                    b.box(
                        i % 2 == 0 ? Material.PURPLE_STAINED_GLASS : Material.MAGENTA_STAINED_GLASS,
                        Math.cos(a) * 1.6,
                        2.0 + Math.sin(a) * 1.6,
                        0.0,
                        0.5,
                        0.5,
                        0.3,
                        0.0,
                        0.0,
                        Math.toDegrees(a)
                    );
                    b.box(Material.CRYING_OBSIDIAN, Math.cos(a) * 1.95, 2.0 + Math.sin(a) * 1.95, 0.0, 0.3, 0.3, 0.3, 0.0, 0.0, Math.toDegrees(a));
                }

                b.glowItem(Material.ENDER_EYE, 0.0, 2.0, 0.0, 1.2, 0.0, 0.0, 0.0);
                b.box(Material.OBSIDIAN, 0.0, 0.15, 0.0, 1.4, 0.3, 0.8);
            }
        );
        add(
            "orbit_rings",
            "Orbit Rings",
            "Magic",
            Material.END_ROD,
            DecoModels.Anim.SPIN,
            b -> {
                for (int r = 0; r < 3; r++) {
                    for (int i = 0; i < 18; i++) {
                        double a = (Math.PI * 2) * i / 18.0;
                        double rad = 0.8 + r * 0.45;
                        double tilt = r * 35;
                        Vector3f v = new Vector3f((float)(Math.cos(a) * rad), 0.0F, (float)(Math.sin(a) * rad));
                        new Quaternionf().rotateX((float)Math.toRadians(tilt)).transform(v);
                        b.box(
                            r == 0 ? Material.GOLD_BLOCK : (r == 1 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_STAINED_GLASS),
                            v.x,
                            2.4 + v.y,
                            v.z,
                            0.16,
                            0.16,
                            0.16
                        );
                    }
                }

                b.ball(Material.SEA_LANTERN, 0.0, 2.4, 0.0, 0.35, 0.18);
            }
        );
        add("rune_stones", "Rune Stones", "Magic", Material.CHISELED_STONE_BRICKS, DecoModels.Anim.NONE, b -> {
            for (int i = 0; i < 5; i++) {
                double a = (Math.PI * 2) * i / 5.0;
                double h = 1.6 + i % 2 * 0.6;
                b.box(Material.DEEPSLATE_TILES, Math.cos(a) * 2.0, h / 2.0, Math.sin(a) * 2.0, 0.6, h, 0.5, 0.0, -Math.toDegrees(a) + 90.0, 4 - i * 2);
                b.text("<aqua>✦", Math.cos(a) * 1.72, h * 0.6, Math.sin(a) * 1.72, 1.2, -Math.toDegrees(a) + 90.0 + 90.0);
            }

            b.box(Material.SCULK, 0.0, 0.03, 0.0, 2.0, 0.05, 2.0).glowItem(Material.ECHO_SHARD, 0.0, 1.2, 0.0, 1.0, 0.0, 0.0, 0.0);
        });
        add("floating_heart", "Floating Heart", "Magic", Material.RED_DYE, DecoModels.Anim.SPIN_BOB, b -> {
            String[] rows = new String[]{".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."};

            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    if (rows[y].charAt(x) == 'X') {
                        b.box(y == 1 && x == 1 ? Material.PINK_CONCRETE : Material.RED_CONCRETE, (x - 3) * 0.25, 2.6 - y * 0.25, 0.0, 0.25, 0.25, 0.25);
                    }
                }
            }
        });
        add(
            "lucky_block",
            "Lucky Block",
            "Magic",
            Material.GOLD_BLOCK,
            DecoModels.Anim.SPIN_BOB,
            b -> b.box(Material.GOLD_BLOCK, 0.0, 2.0, 0.0, 1.0, 1.0, 1.0)
                .text("<dark_gray><b>?", 0.0, 1.8, 0.51, 3.0, 0.0)
                .text("<dark_gray><b>?", 0.0, 1.8, -0.51, 3.0, 180.0)
                .text("<dark_gray><b>?", 0.51, 1.8, 0.0, 3.0, 90.0)
                .text("<dark_gray><b>?", -0.51, 1.8, 0.0, 3.0, -90.0)
        );
        add("coin_stack", "Coin Tower", "Magic", Material.SUNFLOWER, DecoModels.Anim.SPIN, b -> {
            for (int i = 0; i < 9; i++) {
                b.box(Material.GOLD_BLOCK, Math.sin(i * 1.3) * 0.06, 0.08 + i * 0.16, Math.cos(i * 1.3) * 0.06, 0.8, 0.14, 0.8, 0.0, i * 11, 0.0);
            }

            b.glowItem(Material.EMERALD, 0.0, 1.85, 0.0, 0.8, 0.0, 0.0, 0.0).text("<#3DDC84><b>$", 0.0, 2.5, 0.0, 2.0, 0.0);
        });
        add(
            "windmill",
            "Windmill",
            "Magic",
            Material.WHITE_WOOL,
            DecoModels.Anim.ROTOR,
            b -> {
                b.box(Material.STONE_BRICKS, 0.0, 1.5, -0.6, 1.4, 3.0, 1.4)
                    .box(Material.SPRUCE_PLANKS, 0.0, 3.3, -0.6, 1.6, 0.6, 1.6)
                    .box(Material.SPRUCE_LOG, 0.0, 2.6, 0.25, 0.25, 0.25, 0.5);
                b.rotor(true).hub(0.0, 2.6, 0.55);

                for (int i = 0; i < 4; i++) {
                    b.blade(Material.WHITE_WOOL, 1.8, 0.45, 0.06, i * 90);
                }
            }
        );
        add("hot_air_balloon", "Hot Air Balloon", "Magic", Material.RED_WOOL, DecoModels.Anim.BOB, b -> {
            Material[] stripes = new Material[]{Material.RED_WOOL, Material.YELLOW_WOOL};

            for (int y = 0; y < 8; y++) {
                double r = Math.sin(Math.PI * (y + 1) / 9.0) * 1.3 + 0.2;
                b.ring(stripes[y % 2], 3.5 + y * 0.32, r, 14, 0.35, 0.34);
            }

            b.box(Material.ORANGE_WOOL, 0.0, 6.1, 0.0, 0.8, 0.12, 0.8);

            for (int s = 0; s < 4; s++) {
                double a = (Math.PI / 2) * s + (Math.PI / 4);
                b.box(Material.IRON_CHAIN, Math.cos(a) * 0.55, 2.5, Math.sin(a) * 0.55, 0.06, 1.3, 0.06);
            }

            b.box(Material.SPRUCE_PLANKS, 0.0, 1.6, 0.0, 1.0, 0.7, 1.0).box(Material.CAMPFIRE, 0.0, 2.2, 0.0, 0.4, 0.3, 0.4);
        });
        add(
            "rocket",
            "Rocket",
            "Magic",
            Material.FIREWORK_ROCKET,
            DecoModels.Anim.NONE,
            b -> {
                b.box(Material.WHITE_CONCRETE, 0.0, 2.2, 0.0, 0.9, 3.0, 0.9)
                    .cone(Material.RED_CONCRETE, 0.0, 3.7, 0.0, 0.45, 1.2, 6)
                    .box(Material.LIGHT_BLUE_STAINED_GLASS, 0.0, 2.9, 0.46, 0.4, 0.4, 0.05);

                for (int i = 0; i < 4; i++) {
                    double a = (Math.PI / 2) * i;
                    b.box(Material.RED_CONCRETE, Math.cos(a) * 0.65, 0.9, Math.sin(a) * 0.65, 0.5, 1.0, 0.1, 0.0, -Math.toDegrees(a), 0.0);
                }

                b.box(Material.GRAY_CONCRETE, 0.0, 0.6, 0.0, 0.6, 0.3, 0.6).box(Material.MAGMA_BLOCK, 0.0, 0.4, 0.0, 0.4, 0.1, 0.4);
            }
        );
        add(
            "christmas_tree",
            "Holiday Tree",
            "Seasonal",
            Material.SPRUCE_SAPLING,
            DecoModels.Anim.NONE,
            b -> {
                b.box(Material.SPRUCE_LOG, 0.0, 0.4, 0.0, 0.4, 0.8, 0.4).cone(Material.SPRUCE_LEAVES, 0.0, 0.7, 0.0, 1.4, 3.4, 9);
                Material[] lights = new Material[]{
                    Material.RED_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.LIME_STAINED_GLASS
                };

                for (int i = 0; i < 14; i++) {
                    double y = 0.9 + i * 0.21;
                    double r = 1.4 * (1.0 - (y - 0.7) / 3.4) + 0.05;
                    double a = i * 2.3;
                    b.box(lights[i % 4], Math.cos(a) * r, y, Math.sin(a) * r, 0.16, 0.16, 0.16);
                }

                b.glowItem(Material.NETHER_STAR, 0.0, 4.35, 0.0, 0.7, 0.0, 0.0, 0.0);
                b.box(Material.RED_WOOL, 0.9, 0.2, 0.6, 0.4, 0.4, 0.4)
                    .box(Material.BLUE_WOOL, -0.7, 0.18, 0.8, 0.35, 0.35, 0.35)
                    .box(Material.LIME_WOOL, 0.2, 0.15, 1.0, 0.3, 0.3, 0.3);
            }
        );
        add(
            "snowman",
            "Snowman",
            "Seasonal",
            Material.SNOW_BLOCK,
            DecoModels.Anim.NONE,
            b -> b.ball(Material.SNOW_BLOCK, 0.0, 0.6, 0.0, 0.6, 0.3)
                .ball(Material.SNOW_BLOCK, 0.0, 1.5, 0.0, 0.45, 0.25)
                .box(Material.CARVED_PUMPKIN, 0.0, 2.25, 0.0, 0.6, 0.6, 0.6)
                .box(Material.SPRUCE_FENCE, -0.6, 1.6, 0.0, 0.6, 0.08, 0.08, 0.0, 0.0, 25.0)
                .box(Material.SPRUCE_FENCE, 0.6, 1.6, 0.0, 0.6, 0.08, 0.08, 0.0, 0.0, -25.0)
                .box(Material.RED_WOOL, 0.0, 1.92, 0.0, 0.7, 0.12, 0.7)
        );
        add(
            "gift_pile",
            "Gift Pile",
            "Seasonal",
            Material.RED_WOOL,
            DecoModels.Anim.NONE,
            b -> {
                Material[][] g = new Material[][]{
                    {Material.RED_WOOL, Material.YELLOW_WOOL},
                    {Material.BLUE_WOOL, Material.WHITE_WOOL},
                    {Material.LIME_WOOL, Material.RED_WOOL},
                    {Material.PURPLE_WOOL, Material.YELLOW_WOOL}
                };
                double[][] pos = new double[][]{{0.0, 0.35, 0.0, 0.7}, {0.7, 0.25, 0.3, 0.5}, {-0.6, 0.3, 0.25, 0.6}, {0.1, 0.9, 0.05, 0.45}};

                for (int i = 0; i < 4; i++) {
                    double s = pos[i][3];
                    b.box(g[i][0], pos[i][0], pos[i][1], pos[i][2], s, s, s, 0.0, i * 20, 0.0);
                    b.box(g[i][1], pos[i][0], pos[i][1], pos[i][2], s + 0.02, s + 0.02, s * 0.18, 0.0, i * 20, 0.0);
                    b.box(g[i][1], pos[i][0], pos[i][1] + s / 2.0 + 0.06, pos[i][2], s * 0.35, 0.12, s * 0.35, 0.0, i * 20 + 45, 0.0);
                }
            }
        );
        add(
            "pumpkin_patch",
            "Pumpkin Patch",
            "Seasonal",
            Material.JACK_O_LANTERN,
            DecoModels.Anim.NONE,
            b -> b.box(Material.JACK_O_LANTERN, 0.0, 0.4, 0.0, 0.8, 0.8, 0.8)
                .box(Material.PUMPKIN, 0.9, 0.3, -0.4, 0.6, 0.6, 0.6, 0.0, 25.0, 0.0)
                .box(Material.CARVED_PUMPKIN, -0.8, 0.3, 0.3, 0.6, 0.6, 0.6, 0.0, -20.0, 0.0)
                .box(Material.HAY_BLOCK, 0.3, 0.35, -1.1, 1.0, 0.7, 0.7)
                .item(Material.SKELETON_SKULL, -0.3, 0.2, 0.9, 0.5, 0.0, 30.0, 0.0)
        );
        add(
            "ice_sculpture",
            "Ice Sculpture",
            "Seasonal",
            Material.BLUE_ICE,
            DecoModels.Anim.NONE,
            b -> b.box(Material.PACKED_ICE, 0.0, 0.3, 0.0, 1.4, 0.6, 1.4)
                .box(Material.BLUE_ICE, 0.0, 1.2, 0.0, 0.6, 1.2, 0.6, 0.0, 45.0, 0.0)
                .box(Material.ICE, 0.0, 2.1, 0.0, 0.9, 0.6, 0.9, 30.0, 45.0, 0.0)
                .box(Material.BLUE_ICE, 0.0, 2.8, 0.0, 0.4, 0.8, 0.4, 0.0, 45.0, 15.0)
                .box(Material.PACKED_ICE, 0.5, 1.6, 0.0, 0.3, 0.9, 0.3, 0.0, 0.0, -35.0)
                .box(Material.PACKED_ICE, -0.5, 1.6, 0.0, 0.3, 0.9, 0.3, 0.0, 0.0, 35.0)
        );
        add(
            "neon_pvp",
            "Neon PvP Sign",
            "Signs",
            Material.REDSTONE_LAMP,
            DecoModels.Anim.NONE,
            b -> b.box(Material.BLACK_CONCRETE, 0.0, 2.0, 0.0, 3.2, 1.3, 0.15)
                .box(Material.REDSTONE_LAMP, 0.0, 2.0, -0.05, 3.4, 1.5, 0.08)
                .text("<#FF3B3B><b>PvP", 0.0, 1.7, 0.09, 4.5, 0.0)
                .box(Material.IRON_BARS, -1.3, 0.75, 0.0, 0.12, 1.5, 0.12)
                .box(Material.IRON_BARS, 1.3, 0.75, 0.0, 0.12, 1.5, 0.12)
        );
        add(
            "welcome_arch",
            "Welcome Arch",
            "Signs",
            Material.QUARTZ_BLOCK,
            DecoModels.Anim.NONE,
            b -> {
                for (int s = -1; s <= 1; s += 2) {
                    b.box(Material.QUARTZ_PILLAR, s * 2.2, 2.0, 0.0, 0.8, 4.0, 0.8)
                        .box(Material.CHISELED_QUARTZ_BLOCK, s * 2.2, 0.2, 0.0, 1.1, 0.4, 1.1)
                        .box(Material.LANTERN, s * 2.2, 4.4, 0.0, 0.5, 0.5, 0.5);
                }

                b.box(Material.QUARTZ_BLOCK, 0.0, 4.3, 0.0, 5.4, 0.6, 0.8)
                    .box(Material.GOLD_BLOCK, 0.0, 4.65, 0.0, 5.0, 0.1, 0.6)
                    .text("<gold><b>VORTEX REALM", 0.0, 4.1, 0.42, 1.6, 0.0);
            }
        );
        add(
            "signpost",
            "Direction Signpost",
            "Signs",
            Material.OAK_SIGN,
            DecoModels.Anim.NONE,
            b -> b.box(Material.SPRUCE_LOG, 0.0, 1.3, 0.0, 0.25, 2.6, 0.25)
                .box(Material.SPRUCE_PLANKS, 0.55, 2.2, 0.0, 1.0, 0.3, 0.08, 0.0, 0.0, 0.0)
                .text("<white>Duels →", 0.55, 2.12, 0.05, 0.6, 0.0)
                .box(Material.SPRUCE_PLANKS, -0.55, 1.8, 0.0, 1.0, 0.3, 0.08, 0.0, 35.0, 0.0)
                .text("<white>← FFA", -0.55, 1.72, 0.05, 0.6, 35.0)
                .box(Material.SPRUCE_PLANKS, 0.5, 1.4, 0.0, 1.0, 0.3, 0.08, 0.0, -30.0, 0.0)
                .text("<white>SMP →", 0.5, 1.32, 0.05, 0.6, -30.0)
        );
        add(
            "statue",
            "Warrior Statue",
            "Signs",
            Material.DIAMOND_CHESTPLATE,
            DecoModels.Anim.NONE,
            b -> b.box(Material.POLISHED_ANDESITE, 0.0, 0.3, 0.0, 1.8, 0.6, 1.8)
                .box(Material.SMOOTH_STONE, 0.0, 0.75, 0.0, 1.4, 0.3, 1.4)
                .box(Material.DIAMOND_BLOCK, -0.18, 1.55, 0.0, 0.3, 1.3, 0.3)
                .box(Material.DIAMOND_BLOCK, 0.18, 1.55, 0.0, 0.3, 1.3, 0.3)
                .box(Material.LIGHT_BLUE_CONCRETE, 0.0, 2.6, 0.0, 0.75, 1.0, 0.4)
                .box(Material.WHITE_CONCRETE, 0.0, 3.35, 0.0, 0.55, 0.55, 0.55)
                .box(Material.DIAMOND_BLOCK, 0.0, 3.55, 0.0, 0.6, 0.25, 0.6)
                .box(Material.LIGHT_BLUE_CONCRETE, -0.5, 2.7, 0.15, 0.25, 0.9, 0.25, -30.0, 0.0, 0.0)
                .box(Material.LIGHT_BLUE_CONCRETE, 0.5, 2.55, 0.0, 0.25, 0.9, 0.25)
                .item(Material.DIAMOND_SWORD, -0.55, 3.35, 0.55, 1.6, 0.0, 90.0, -45.0)
        );
        add(
            "lighthouse",
            "Mini Lighthouse",
            "Signs",
            Material.SEA_LANTERN,
            DecoModels.Anim.NONE,
            b -> {
                for (int i = 0; i < 6; i++) {
                    b.box(i % 2 == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE, 0.0, 0.4 + i * 0.7, 0.0, 1.4 - i * 0.12, 0.7, 1.4 - i * 0.12);
                }

                b.box(Material.SEA_LANTERN, 0.0, 4.6, 0.0, 0.6, 0.6, 0.6)
                    .box(Material.GLASS, 0.0, 4.6, 0.0, 0.8, 0.7, 0.8)
                    .cone(Material.RED_CONCRETE, 0.0, 5.0, 0.0, 0.55, 0.6, 4);
            }
        );
        add(
            "airship",
            "Mini Airship",
            "Signs",
            Material.SPRUCE_BOAT,
            DecoModels.Anim.BOB,
            b -> {
                b.box(Material.SPRUCE_PLANKS, 0.0, 3.0, 0.0, 3.0, 0.6, 1.2)
                    .box(Material.DARK_OAK_PLANKS, 0.0, 2.65, 0.0, 2.4, 0.2, 0.8)
                    .box(Material.SPRUCE_PLANKS, 1.75, 3.15, 0.0, 0.6, 0.3, 0.8, 0.0, 0.0, 20.0);

                for (int y = 0; y < 3; y++) {
                    b.box(Material.WHITE_WOOL, 0.0, 4.4 + y * 0.35, 0.0, 3.4 - Math.abs(y - 1) * 0.8, 0.35, 1.6 - Math.abs(y - 1) * 0.4);
                }

                b.box(Material.IRON_CHAIN, -1.0, 3.75, 0.0, 0.06, 0.9, 0.06)
                    .box(Material.IRON_CHAIN, 1.0, 3.75, 0.0, 0.06, 0.9, 0.06)
                    .box(Material.RED_WOOL, -1.75, 3.3, 0.0, 0.1, 0.6, 0.5);
            }
        );
    }

    public static enum Anim {
        NONE,
        SPIN,
        BOB,
        SPIN_BOB,
        ROTOR;
    }

    public static final class B {
        final List<DecoModels.Part> parts = new ArrayList<>();
        boolean rotor;
        Vector3f hub = new Vector3f();

        public DecoModels.B hub(double x, double y, double z) {
            this.hub = new Vector3f((float)x, (float)y, (float)z);
            return this;
        }

        public DecoModels.B box(Material m, double cx, double cy, double cz, double sx, double sy, double sz) {
            return this.box(m, cx, cy, cz, sx, sy, sz, 0.0, 0.0, 0.0);
        }

        public DecoModels.B box(Material m, double cx, double cy, double cz, double sx, double sy, double sz, double rx, double ry, double rz) {
            Quaternionf q = new Quaternionf().rotateY((float)Math.toRadians(ry)).rotateX((float)Math.toRadians(rx)).rotateZ((float)Math.toRadians(rz));
            Vector3f half = new Vector3f((float)sx / 2.0F, (float)sy / 2.0F, (float)sz / 2.0F);
            q.transform(half);
            Vector3f t = new Vector3f((float)cx, (float)cy, (float)cz).sub(half);
            this.parts.add(new DecoModels.Part(DecoModels.Kind.BLOCK, m, null, t, q, new Vector3f((float)sx, (float)sy, (float)sz), this.rotor, false));
            return this;
        }

        public DecoModels.B blade(Material m, double len, double width, double thick, double angleDeg) {
            Quaternionf q = new Quaternionf().rotateZ((float)Math.toRadians(angleDeg));
            this.parts
                .add(
                    new DecoModels.Part(
                        DecoModels.Kind.BLOCK, m, null, new Vector3f(this.hub), q, new Vector3f((float)len, (float)width, (float)thick), true, false
                    )
                );
            return this;
        }

        public DecoModels.B item(Material m, double cx, double cy, double cz, double scale, double rx, double ry, double rz) {
            Quaternionf q = new Quaternionf().rotateY((float)Math.toRadians(ry)).rotateX((float)Math.toRadians(rx)).rotateZ((float)Math.toRadians(rz));
            this.parts
                .add(
                    new DecoModels.Part(
                        DecoModels.Kind.ITEM, m, null, new Vector3f((float)cx, (float)cy, (float)cz), q, new Vector3f((float)scale), this.rotor, false
                    )
                );
            return this;
        }

        public DecoModels.B glowItem(Material m, double cx, double cy, double cz, double scale, double rx, double ry, double rz) {
            this.item(m, cx, cy, cz, scale, rx, ry, rz);
            DecoModels.Part p = this.parts.remove(this.parts.size() - 1);
            this.parts.add(new DecoModels.Part(p.kind(), p.material(), p.text(), p.translation(), p.rotation(), p.scale(), p.rotor(), true));
            return this;
        }

        public DecoModels.B text(String mini, double cx, double cy, double cz, double scale, double ry) {
            Quaternionf q = new Quaternionf().rotateY((float)Math.toRadians(ry));
            this.parts
                .add(
                    new DecoModels.Part(
                        DecoModels.Kind.TEXT,
                        Material.AIR,
                        mini,
                        new Vector3f((float)cx, (float)cy, (float)cz),
                        q,
                        new Vector3f((float)scale),
                        this.rotor,
                        false
                    )
                );
            return this;
        }

        public DecoModels.B ring(Material m, double cy, double r, int n, double size, double height) {
            for (int i = 0; i < n; i++) {
                double a = (Math.PI * 2) * i / n;
                this.box(m, Math.cos(a) * r, cy, Math.sin(a) * r, size, height, size * 1.6, 0.0, -Math.toDegrees(a), 0.0);
            }

            return this;
        }

        public DecoModels.B ball(Material m, double cx, double cy, double cz, double r, double c) {
            int n = (int)Math.ceil(r / c);

            for (int x = -n; x <= n; x++) {
                for (int y = -n; y <= n; y++) {
                    for (int z = -n; z <= n; z++) {
                        double d = Math.sqrt(x * x + y * y + z * z) * c;
                        if (d <= r && d > r - c * 1.8) {
                            this.box(m, cx + x * c, cy + y * c, cz + z * c, c, c, c);
                        }
                    }
                }
            }

            return this;
        }

        public DecoModels.B cone(Material m, double cx, double baseY, double cz, double r, double h, int steps) {
            for (int i = 0; i < steps; i++) {
                double f = 1.0 - (double)i / steps;
                double s = r * 2.0 * f;
                this.box(m, cx, baseY + h * i / steps + h / steps / 2.0, cz, s, h / steps, s);
            }

            return this;
        }

        public DecoModels.B rotor(boolean on) {
            this.rotor = on;
            return this;
        }
    }

    public static enum Kind {
        BLOCK,
        ITEM,
        TEXT;
    }

    public record Model(String id, String name, String category, Material icon, DecoModels.Anim anim, List<DecoModels.Part> parts) {
    }

    public record Part(
        DecoModels.Kind kind, Material material, String text, Vector3f translation, Quaternionf rotation, Vector3f scale, boolean rotor, boolean glow
    ) {
    }
}
