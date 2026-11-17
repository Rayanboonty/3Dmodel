import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.practicepvp.build.Voxels;

/**
 * Renders a voxel design (any net.practicepvp.build.Voxels) to PNG pictures without a server: a small ray caster with
 * sun shadows, flat block colours, slabs / fences / plants as smaller boxes. Good enough to judge a build's shape,
 * colours and detail before it goes in a release.
 *
 * <pre>
 * javac -cp practicepvp/target/classes -d /tmp/prev tools/preview/ArenaPreview.java
 * java -cp practicepvp/target/classes:/tmp/prev ArenaPreview court ROYAL_COURTYARD 96 out/dir
 * java -cp ... ArenaPreview show PALACE 96 out/dir
 * </pre>
 * Writes &lt;style&gt;-floor.png (from the fighting floor), -air.png (3/4 view from above) and -back.png.
 */
public final class ArenaPreview {
    private final int ox;
    private final int oy;
    private final int oz;
    private final int sx;
    private final int sy;
    private final int sz;
    private final short[] grid;
    private final List<String> names = new ArrayList<>();
    private final Map<String, Short> ids = new HashMap<>();
    private int[] rgb;
    private int[] shape;

    private ArenaPreview(Voxels d) {
        this.ox = d.minX();
        this.oz = d.minZ();
        this.oy = d.bottomY();
        this.sx = d.maxX() - d.minX() + 1;
        this.sz = d.maxZ() - d.minZ() + 1;
        this.sy = d.topY() - d.bottomY() + 1;
        this.grid = new short[this.sx * this.sy * this.sz];
        this.names.add("air");
        for (int x = d.minX(); x <= d.maxX(); x++) {
            for (int z = d.minZ(); z <= d.maxZ(); z++) {
                d.column(x, z, (bx, by, bz, block) -> {
                    String b = block.startsWith("minecraft:") ? block.substring(10) : block;
                    Short id = this.ids.get(b);
                    if (id == null) {
                        id = (short)this.names.size();
                        this.names.add(b);
                        this.ids.put(b, id);
                    }

                    int i = this.idx(bx - this.ox, by - this.oy, bz - this.oz);
                    if (i >= 0) {
                        this.grid[i] = id;
                    }
                });
            }
        }

        this.rgb = new int[this.names.size()];
        this.shape = new int[this.names.size()];
        List<String> unknown = new ArrayList<>();
        for (int i = 1; i < this.names.size(); i++) {
            String n = this.names.get(i);
            this.shape[i] = shapeOf(n);
            Integer c = colorOf(n);
            if (c == null) {
                unknown.add(base(n));
                c = 0xFF00FF;
            }

            this.rgb[i] = c;
        }

        if (!unknown.isEmpty()) {
            System.out.println("no colour for: " + new java.util.TreeSet<>(unknown));
        }
    }

    private int idx(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < this.sx && y < this.sy && z < this.sz ? (x * this.sz + z) * this.sy + y : -1;
    }

    private short at(int x, int y, int z) {
        int i = this.idx(x, y, z);
        return i < 0 ? 0 : this.grid[i];
    }

    // ------------------------------------------------------------------ block looks

    static final int FULL = 0;
    static final int NONE = 1;
    static final int SLAB_LO = 2;
    static final int SLAB_HI = 3;
    static final int POST = 4;
    static final int SMALL = 5;
    static final int CARPET = 6;
    static final int PANE = 7;

    static String base(String n) {
        int i = n.indexOf('[');
        return i < 0 ? n : n.substring(0, i);
    }

    static int shapeOf(String n) {
        String b = base(n);
        if (b.equals("barrier") || b.equals("light") || b.equals("structure_void") || b.endsWith("air")) {
            return NONE;
        }

        if (b.endsWith("_slab")) {
            return n.contains("type=top") ? SLAB_HI : (n.contains("type=double") ? FULL : SLAB_LO);
        }

        if (b.endsWith("_fence") || b.endsWith("_wall") && !b.endsWith("_wall_banner") && !b.endsWith("_wall_sign") && !b.endsWith("wall_torch") && !b.endsWith("wall_head")
            || b.equals("chain") || b.endsWith("_chain") || b.equals("end_rod") || b.equals("lightning_rod") || b.equals("bamboo") || b.endsWith("_fence_gate")) {
            return POST;
        }

        if (b.endsWith("_pane") || b.equals("iron_bars")) {
            return PANE;
        }

        if (b.endsWith("_carpet") || b.equals("snow") || b.endsWith("_pressure_plate") || b.equals("lily_pad") || b.endsWith("rail")
            || b.equals("moss_carpet") || b.equals("pale_moss_carpet") || b.endsWith("_trapdoor") && !n.contains("open=true")) {
            return CARPET;
        }

        if (b.contains("torch") || b.contains("lantern") || b.endsWith("_flower") || b.equals("short_grass") || b.equals("grass") || b.equals("fern")
            || b.endsWith("tulip") || b.equals("poppy") || b.equals("dandelion") || b.equals("allium") || b.equals("azure_bluet") || b.equals("oxeye_daisy")
            || b.equals("cornflower") || b.equals("lily_of_the_valley") || b.endsWith("_sapling") || b.endsWith("_button") || b.endsWith("_banner")
            || b.endsWith("_sign") || b.equals("vine") || b.equals("ladder") || b.endsWith("_head") || b.endsWith("_skull") || b.equals("dead_bush")
            || b.endsWith("_mushroom") || b.equals("sweet_berry_bush") || b.equals("flower_pot") || b.startsWith("potted_") || b.equals("campfire")
            || b.equals("soul_campfire") || b.equals("bell") || b.endsWith("_candle") || b.equals("candle") || b.equals("tall_grass") || b.equals("large_fern")
            || b.equals("rose_bush") || b.equals("peony") || b.equals("lilac") || b.equals("sunflower") || b.equals("pink_petals") || b.equals("cobweb")
            || b.equals("hanging_roots") || b.equals("glow_lichen") || b.equals("weeping_vines") || b.equals("twisting_vines") || b.endsWith("_roots")
            || b.equals("crimson_fungus") || b.equals("warped_fungus") || b.equals("nether_sprouts") || b.equals("brewing_stand") || b.equals("lever")
            || b.equals("tripwire_hook") || b.equals("cave_vines") || b.equals("spore_blossom") || b.equals("hanging_moss") || b.equals("pale_hanging_moss")
            || b.endsWith("_coral") || b.endsWith("_coral_fan") || b.equals("seagrass") || b.equals("kelp") || b.equals("firefly_bush") || b.equals("bush")
            || b.equals("short_dry_grass") || b.equals("tall_dry_grass") || b.equals("leaf_litter") || b.equals("wildflowers")) {
            return SMALL;
        }

        return FULL;
    }

    static Integer colorOf(String n) {
        String b = base(n);
        if (b.startsWith("waxed_")) {
            return colorOf(b.substring(6));
        }

        Integer c = COLORS.get(b);
        if (c != null) {
            return c;
        }

        for (String suf : new String[]{"_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_trapdoor", "_door", "_pane"}) {
            if (b.endsWith(suf)) {
                String m = b.substring(0, b.length() - suf.length());
                for (String t : new String[]{m, m + "s", m + "_block", m + "_planks", m.replace("brick", "bricks"), m.replace("tile", "tiles")}) {
                    if (COLORS.containsKey(t)) {
                        return COLORS.get(t);
                    }
                }
            }
        }

        for (String[] dye : DYES) {
            if (b.startsWith(dye[0] + "_")) {
                int col = Integer.parseInt(dye[1], 16);
                if (b.endsWith("_terracotta") && !b.endsWith("glazed_terracotta")) {
                    return mix(col, 0x9A6A55, 0.55);
                }

                if (b.endsWith("_concrete_powder")) {
                    return mix(col, 0xFFFFFF, 0.15);
                }

                if (b.contains("glass")) {
                    return mix(col, 0xFFFFFF, 0.35);
                }

                if (b.endsWith("_glazed_terracotta")) {
                    return mix(col, 0xFFFFFF, 0.2);
                }

                return col;
            }
        }

        if (b.endsWith("_log") || b.endsWith("_wood") || b.endsWith("_stem") || b.endsWith("_hyphae")) {
            if (b.startsWith("stripped_")) {
                String t = b.substring(9).replaceAll("_(log|wood|stem|hyphae)$", "");
                Integer p = COLORS.get(t + "_planks");
                return p == null ? 0xA88A5A : p;
            }

            String t = b.replaceAll("_(log|wood|stem|hyphae)$", "");
            Integer bark = BARK.get(t);
            return bark == null ? 0x6B5233 : bark;
        }

        if (b.endsWith("_leaves")) {
            return switch (b) {
                case "cherry_leaves" -> 0xE8A6C4;
                case "azalea_leaves" -> 0x5E7C2E;
                case "flowering_azalea_leaves" -> 0x6E7E3E;
                case "pale_oak_leaves" -> 0x8B958A;
                case "spruce_leaves" -> 0x3D5E3D;
                case "birch_leaves" -> 0x6B8F45;
                case "dark_oak_leaves" -> 0x3F6A23;
                case "mangrove_leaves" -> 0x5A8A2A;
                case "jungle_leaves" -> 0x4E8A1E;
                case "acacia_leaves" -> 0x5E8A26;
                default -> 0x4F7E2A;
            };
        }

        if (b.endsWith("_carpet")) {
            return colorOf(b.replace("_carpet", "_wool"));
        }

        if (b.startsWith("potted_") || b.equals("flower_pot")) {
            return 0x7A4A35;
        }

        if (b.endsWith("_banner") || b.endsWith("_wall_banner")) {
            for (String[] dye : DYES) {
                if (b.startsWith(dye[0] + "_")) {
                    return Integer.parseInt(dye[1], 16);
                }
            }
        }

        if (b.endsWith("_sign") || b.endsWith("_hanging_sign")) {
            return 0xA0824F;
        }

        if (b.contains("torch") || b.contains("lantern") || b.contains("candle") || b.contains("campfire")) {
            return b.contains("soul") ? 0x7FD7E0 : 0xFFC860;
        }

        if (b.endsWith("_head") || b.endsWith("_skull")) {
            return 0x5A4A3A;
        }

        if (b.endsWith("_door")) {
            return 0x7A5A35;
        }

        if (b.endsWith("_button") || b.endsWith("_pressure_plate")) {
            return 0x8A8A8A;
        }

        if (shapeOf(n) == SMALL) {
            return 0x5E9A3A;
        }

        return null;
    }

    static int mix(int a, int b, double t) {
        int r = (int)(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int)(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int)((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    static final String[][] DYES = {
        {"light_gray", "9D9D97"}, {"light_blue", "3AAFD9"}, {"white", "E9ECEC"}, {"orange", "F07613"}, {"magenta", "BD44B3"}, {"yellow", "F8C627"},
        {"lime", "70B919"}, {"pink", "ED8DAC"}, {"gray", "3E4447"}, {"cyan", "158991"}, {"purple", "792AAC"}, {"blue", "35399D"},
        {"brown", "724728"}, {"green", "546D1B"}, {"red", "A12722"}, {"black", "141519"}
    };
    static final Map<String, Integer> BARK = new HashMap<>();
    static final Map<String, Integer> COLORS = new HashMap<>();

    static void c(int rgb, String... names) {
        for (String n : names) {
            COLORS.put(n, rgb);
        }
    }

    static {
        BARK.put("oak", 0x6D5532);
        BARK.put("spruce", 0x3B2812);
        BARK.put("birch", 0xD8D7D2);
        BARK.put("jungle", 0x56461E);
        BARK.put("acacia", 0x676157);
        BARK.put("dark_oak", 0x3C2E1A);
        BARK.put("mangrove", 0x544030);
        BARK.put("cherry", 0x3A2024);
        BARK.put("pale_oak", 0x5A5250);
        BARK.put("crimson", 0x5C1A1E);
        BARK.put("warped", 0x3A3B4E);
        BARK.put("mushroom", 0xCBC4B9);
        c(0x7D7D7D, "stone", "infested_stone");
        c(0x7A7A7A, "stone_bricks", "infested_stone_bricks");
        c(0x6E7766, "mossy_stone_bricks", "mossy_cobblestone");
        c(0x767676, "cracked_stone_bricks");
        c(0x777777, "chiseled_stone_bricks");
        c(0x9E9E9E, "smooth_stone");
        c(0x7F7F7F, "cobblestone");
        c(0x888889, "andesite");
        c(0x848687, "polished_andesite");
        c(0xBCBCBC, "diorite");
        c(0xC4C4C6, "polished_diorite");
        c(0x95674F, "granite");
        c(0x9A6B56, "polished_granite");
        c(0x6C6D66, "tuff", "chiseled_tuff");
        c(0x626963, "tuff_bricks", "polished_tuff", "chiseled_tuff_bricks");
        c(0x505052, "deepslate", "cobbled_deepslate");
        c(0x484849, "polished_deepslate");
        c(0x464648, "deepslate_bricks", "cracked_deepslate_bricks");
        c(0x363637, "deepslate_tiles", "cracked_deepslate_tiles", "chiseled_deepslate");
        c(0xDFE0DA, "calcite");
        c(0xD8CB9B, "sandstone", "chiseled_sandstone", "cut_sandstone");
        c(0xE0D6AA, "smooth_sandstone");
        c(0xDBCFA3, "sand", "suspicious_sand");
        c(0xBE6621, "red_sand");
        c(0xB5621F, "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone", "smooth_red_sandstone");
        c(0x986A56, "terracotta");
        c(0x5A3F2D, "dirt", "rooted_dirt");
        c(0x77553B, "coarse_dirt");
        c(0x8F7350, "dirt_path");
        c(0x5D9440, "grass_block");
        c(0x5E4A25, "podzol");
        c(0x6F6366, "mycelium");
        c(0x3C3940, "mud");
        c(0x8C6B50, "packed_mud");
        c(0x8A6B53, "mud_bricks");
        c(0x8C7457, "gravel");
        c(0x9CA3B1, "clay");
        c(0xF4FBFB, "snow_block", "snow", "powder_snow");
        c(0x8DB4FB, "ice");
        c(0x8DB4FA, "packed_ice");
        c(0x74A8FD, "blue_ice");
        c(0x2E5BDB, "water");
        c(0xD45A12, "lava");
        c(0x575C53, "moss_block_old");
        c(0x596E2D, "moss_block", "moss_carpet");
        c(0x6E7C78, "pale_moss_block", "pale_moss_carpet");
        c(0xA28251, "oak_planks");
        c(0x725431, "spruce_planks");
        c(0xC0AF79, "birch_planks");
        c(0xA07351, "jungle_planks");
        c(0xA85A32, "acacia_planks");
        c(0x432B14, "dark_oak_planks");
        c(0x763631, "mangrove_planks");
        c(0xE3B3AD, "cherry_planks");
        c(0xE3D9D6, "pale_oak_planks");
        c(0x653046, "crimson_planks");
        c(0x2B6963, "warped_planks");
        c(0xC4B04C, "bamboo_planks", "bamboo_block", "bamboo_mosaic");
        c(0x96603F, "bricks");
        c(0x2C1519, "nether_bricks", "cracked_nether_bricks", "chiseled_nether_bricks");
        c(0x450709, "red_nether_bricks");
        c(0x6F3635, "netherrack");
        c(0x2A2329, "blackstone", "polished_blackstone", "chiseled_polished_blackstone");
        c(0x302A31, "polished_blackstone_bricks", "cracked_polished_blackstone_bricks");
        c(0x383538, "gilded_blackstone");
        c(0x515156, "basalt", "polished_basalt", "smooth_basalt");
        c(0x8F1E1E, "crimson_nylium");
        c(0x2B7265, "warped_nylium");
        c(0x7B0000, "nether_wart_block");
        c(0x167E86, "warped_wart_block");
        c(0xFCF6B8, "shroomlight");
        c(0xABA187, "glowstone");
        c(0x5C1A1E, "crimson_stem");
        c(0xEBE5DE, "quartz_block", "chiseled_quartz_block", "quartz_pillar", "smooth_quartz", "quartz_bricks");
        c(0xDBDEA0, "end_stone");
        c(0xDCE2A3, "end_stone_bricks");
        c(0xA97DA9, "purpur_block", "purpur_pillar");
        c(0x0F0B19, "obsidian", "crying_obsidian");
        c(0x553A5F, "amethyst_block");
        c(0x63A096, "prismarine");
        c(0x63AB9E, "prismarine_bricks");
        c(0x335B4B, "dark_prismarine");
        c(0xACC7BE, "sea_lantern");
        c(0xF8D33D, "gold_block");
        c(0xDCDCDC, "iron_block");
        c(0x62ECE4, "diamond_block");
        c(0x2ABF5C, "emerald_block");
        c(0x1F4392, "lapis_block");
        c(0xAF1A05, "redstone_block");
        c(0xBF6B50, "copper_block", "cut_copper", "chiseled_copper");
        c(0xA07A5F, "exposed_copper", "exposed_cut_copper");
        c(0x6D9375, "weathered_copper", "weathered_cut_copper", "weathered_chiseled_copper");
        c(0x52A284, "oxidized_copper", "oxidized_cut_copper", "oxidized_chiseled_copper");
        c(0x7BA85E, "waxed_oxidized_copper");
        c(0xE6E9EA, "white_wool");
        c(0xC0D8D8, "glass", "glass_pane");
        c(0x6A6A6A, "iron_bars", "chain", "anvil");
        c(0x5D5D5D, "cauldron", "hopper");
        c(0xF5E0B0, "end_rod");
        c(0xB06A4A, "lightning_rod");
        c(0x8F6F3A, "barrel", "composter");
        c(0x9F7944, "bookshelf", "chiseled_bookshelf");
        c(0x5E4A30, "crafting_table", "loom", "fletching_table", "smithing_table", "cartography_table");
        c(0xB78E3A, "hay_block");
        c(0x7D7D7D, "furnace", "blast_furnace", "smoker");
        c(0x9C6B30, "chest", "trapped_chest");
        c(0xB5642C, "pumpkin", "carved_pumpkin", "jack_o_lantern");
        c(0x6E9133, "melon");
        c(0xD0A02C, "bee_nest", "beehive");
        c(0x4F4F4F, "lodestone");
        c(0xD03F3A, "target");
        c(0x7A1F1F, "red_mushroom_block");
        c(0x957054, "brown_mushroom_block");
        c(0xCBC4B9, "mushroom_stem");
        c(0x0A3A43, "sculk", "sculk_catalyst");
        c(0x5C5C5C, "dripstone_block", "pointed_dripstone");
        c(0x7F7F7F, "magma_block_old");
        c(0x8E3F1E, "magma_block");
        c(0x8C8C8C, "bedrock");
        c(0xAAAAAA, "scaffolding_old");
        c(0xAB8B5A, "scaffolding");
        c(0x5D9440, "short_grass", "tall_grass", "fern", "large_fern", "grass");
        c(0x6B4A2A, "dead_bush");
        c(0x3F7A20, "vine", "cave_vines", "glow_lichen");
        c(0xD23A3A, "poppy", "red_tulip", "rose_bush");
        c(0xF5D93A, "dandelion", "sunflower");
        c(0xB06AE0, "allium", "lilac");
        c(0xE6E6F0, "oxeye_daisy", "azure_bluet", "lily_of_the_valley", "white_tulip");
        c(0x4466DD, "cornflower");
        c(0xF39B3A, "orange_tulip");
        c(0xF09AC0, "pink_tulip", "peony", "pink_petals");
        c(0x2E8A2E, "lily_pad");
        c(0x9B6A3A, "ladder");
        c(0xCFA346, "bell");
        c(0x7A6A55, "brewing_stand");
        c(0xB6B8BA, "cobweb");
        c(0x2F5F2F, "kelp", "seagrass");
        c(0x8A2525, "crimson_fungus", "crimson_roots", "weeping_vines");
        c(0x1F7A70, "warped_fungus", "warped_roots", "twisting_vines", "nether_sprouts");
        c(0x6A4A2A, "sweet_berry_bush");
        c(0xE0A8C8, "spore_blossom");
        c(0x6E7C78, "pale_hanging_moss");
        c(0x7E6040, "hanging_roots");
        c(0x9E8E70, "leaf_litter", "short_dry_grass", "tall_dry_grass");
        c(0x5E9A3A, "bush", "firefly_bush", "wildflowers");
        c(0xBFB89A, "bone_block");
        c(0x8A5A3A, "rail", "powered_rail", "detector_rail", "activator_rail");
        c(0x6C4C30, "lever", "tripwire_hook");
        c(0x7A7A7A, "stonecutter", "grindstone");
        c(0xE7E2CC, "white_concrete_powder_old");
        c(0x7C5A2E, "honeycomb_block_old");
        c(0xE69A1C, "honeycomb_block", "honey_block");
        c(0x5A6E8A, "respawn_anchor");
        c(0x7A3A9A, "enchanting_table");
        c(0x0F0B19, "end_portal_frame");
        c(0x3A3A3A, "coal_block");
        c(0x7FB8D4, "beacon");
        c(0x8A6A3A, "lectern", "jukebox", "note_block");
        c(0x7D7D7D, "dispenser", "dropper", "observer", "piston", "sticky_piston");
        c(0xD8D7D2, "birch_wood");
        c(0x5E5146, "cracked_mud_old");
        c(0x7A9A9A, "creaking_heart");
        c(0x9D9D97, "light_gray_wool");
        c(0xA44A3A, "decorated_pot");
        c(0x6A6A6A, "heavy_core");
        c(0xB5A26A, "resin_block", "resin_bricks", "chiseled_resin_bricks");
        c(0xDE7B1E, "resin_clump");
    }

    // ------------------------------------------------------------------ ray casting

    private static final double[] SUN = norm(-0.45, 0.82, -0.35);

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    /** {t, faceAxis(0..2), faceSign, id, fracU, fracV} or null. */
    private double[] cast(double px, double py, double pz, double dx, double dy, double dz, double maxT, boolean anyHit) {
        int x = (int)Math.floor(px);
        int y = (int)Math.floor(py);
        int z = (int)Math.floor(pz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tdx = Math.abs(1.0 / dx);
        double tdy = Math.abs(1.0 / dy);
        double tdz = Math.abs(1.0 / dz);
        double tmx = dx > 0 ? (x + 1 - px) * tdx : (px - x) * tdx;
        double tmy = dy > 0 ? (y + 1 - py) * tdy : (py - y) * tdy;
        double tmz = dz > 0 ? (z + 1 - pz) * tdz : (pz - z) * tdz;
        double t = 0.0;
        int axis = -1;
        while (t < maxT) {
            if (x >= 0 && y >= 0 && z >= 0 && x < this.sx && y < this.sy && z < this.sz) {
                short id = this.grid[(x * this.sz + z) * this.sy + y];
                if (id != 0 && this.shape[id] != NONE) {
                    double[] hit = this.boxHit(id, x, y, z, px, py, pz, dx, dy, dz, t, axis, stepX, stepY, stepZ);
                    if (hit != null) {
                        return hit;
                    }
                }
            } else if (outside(x, y, z, stepX, stepY, stepZ)) {
                return null;
            }

            if (tmx < tmy && tmx < tmz) {
                t = tmx;
                tmx += tdx;
                x += stepX;
                axis = 0;
            } else if (tmy < tmz) {
                t = tmy;
                tmy += tdy;
                y += stepY;
                axis = 1;
            } else {
                t = tmz;
                tmz += tdz;
                z += stepZ;
                axis = 2;
            }
        }

        return null;
    }

    private boolean outside(int x, int y, int z, int sxs, int sys, int szs) {
        return x < 0 && sxs < 0 || y < 0 && sys < 0 || z < 0 && szs < 0 || x >= this.sx && sxs > 0 || y >= this.sy && sys > 0 || z >= this.sz && szs > 0;
    }

    private double[] boxHit(short id, int x, int y, int z, double px, double py, double pz, double dx, double dy, double dz, double tEnter, int axis,
        int stepX, int stepY, int stepZ) {
        int sh = this.shape[id];
        double x0 = 0, y0 = 0, z0 = 0, x1 = 1, y1 = 1, z1 = 1;
        switch (sh) {
            case SLAB_LO -> y1 = 0.5;
            case SLAB_HI -> y0 = 0.5;
            case POST -> {
                x0 = 0.3;
                x1 = 0.7;
                z0 = 0.3;
                z1 = 0.7;
            }
            case PANE -> {
                x0 = 0.4;
                x1 = 0.6;
                z0 = 0.4;
                z1 = 0.6;
                String n = this.names.get(id);
                if (n.contains("east=true") || n.contains("west=true")) {
                    x0 = 0;
                    x1 = 1;
                }

                if (n.contains("north=true") || n.contains("south=true")) {
                    z0 = 0;
                    z1 = 1;
                }
            }
            case SMALL -> {
                x0 = 0.32;
                x1 = 0.68;
                z0 = 0.32;
                z1 = 0.68;
                y1 = 0.6;
            }
            case CARPET -> y1 = 0.08;
            default -> {
            }
        }

        if (sh == FULL) {
            double hx = px + dx * tEnter - x;
            double hy = py + dy * tEnter - y;
            double hz = pz + dz * tEnter - z;
            int ax = axis < 0 ? 1 : axis;
            int sign = ax == 0 ? -stepX : (ax == 1 ? -stepY : -stepZ);
            double u = ax == 0 ? hz : hx;
            double v = ax == 1 ? hz : hy;
            return new double[]{tEnter, ax, sign, id, u, v};
        }

        // ray / box slab test
        double ox = px - x, oy = py - y, oz = pz - z;
        double tmin = -1e9, tmax = 1e9;
        int hitAxis = 1;
        double[][] lim = {{x0, x1}, {y0, y1}, {z0, z1}};
        double[] o = {ox, oy, oz};
        double[] d = {dx, dy, dz};
        for (int k = 0; k < 3; k++) {
            if (Math.abs(d[k]) < 1e-12) {
                if (o[k] < lim[k][0] || o[k] > lim[k][1]) {
                    return null;
                }
            } else {
                double ta = (lim[k][0] - o[k]) / d[k];
                double tb = (lim[k][1] - o[k]) / d[k];
                double lo = Math.min(ta, tb);
                double hi = Math.max(ta, tb);
                if (lo > tmin) {
                    tmin = lo;
                    hitAxis = k;
                }

                tmax = Math.min(tmax, hi);
            }
        }

        if (tmin > tmax || tmax < tEnter) {
            return null;
        }

        double th = Math.max(tmin, tEnter);
        int sign = d[hitAxis] > 0 ? -1 : 1;
        double hx = ox + dx * th, hy = oy + dy * th, hz = oz + dz * th;
        double u = hitAxis == 0 ? hz : hx;
        double v = hitAxis == 1 ? hz : hy;
        return new double[]{th, hitAxis, sign, id, u, v};
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ h >>> 15;
    }

    /** cam = world position, yaw (0 = looking +z/south, 90 = -x/west), pitch (positive looks down). */
    BufferedImage render(double cx, double cy, double cz, double yawDeg, double pitchDeg, double fovDeg, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double[] fwd = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
        double[] right = norm(-fwd[2], 0, fwd[0]);
        double[] up = {right[1] * fwd[2] - right[2] * fwd[1], right[2] * fwd[0] - right[0] * fwd[2], right[0] * fwd[1] - right[1] * fwd[0]};
        if (up[1] < 0) {
            up = new double[]{-up[0], -up[1], -up[2]};
            right = new double[]{-right[0], -right[1], -right[2]};
        }

        double f = Math.tan(Math.toRadians(fovDeg) / 2.0);
        double px = cx - this.ox, py = cy - this.oy, pz = cz - this.oz;
        final double[] R = right, U = up;
        java.util.stream.IntStream.range(0, h).parallel().forEach(j -> {
            for (int i = 0; i < w; i++) {
                double sxp = (2.0 * (i + 0.5) / w - 1.0) * f * w / h;
                double syp = (1.0 - 2.0 * (j + 0.5) / h) * f;
                double dx = fwd[0] + R[0] * sxp + U[0] * syp;
                double dy = fwd[1] + R[1] * sxp + U[1] * syp;
                double dz = fwd[2] + R[2] * sxp + U[2] * syp;
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= l;
                dy /= l;
                dz /= l;
                img.setRGB(i, j, this.shade(px, py, pz, dx, dy, dz));
            }
        });
        return img;
    }

    private int sky(double dy) {
        double t = Math.max(0, Math.min(1, dy * 1.6 + 0.2));
        return mix(0xC9DDF2, 0x6E9FDB, t);
    }

    private int shade(double px, double py, double pz, double dx, double dy, double dz) {
        double[] hit = this.cast(px, py, pz, dx, dy, dz, 700, false);
        if (hit == null) {
            return this.sky(dy);
        }

        double t = hit[0];
        int ax = (int)hit[1];
        int sign = (int)hit[2];
        int id = (int)hit[3];
        double hx = px + dx * t, hy = py + dy * t, hz = pz + dz * t;
        int bx = (int)Math.floor(hx - (ax == 0 ? sign * 0.001 : 0));
        int by = (int)Math.floor(hy - (ax == 1 ? sign * 0.001 : 0));
        int bz = (int)Math.floor(hz - (ax == 2 ? sign * 0.001 : 0));
        int base = this.rgb[id];
        String name = this.names.get(id);
        boolean glow = name.contains("lantern") || name.contains("torch") || name.contains("glowstone") || name.contains("shroomlight") || name.contains("sea_lantern")
            || name.contains("lava") || name.contains("end_rod") || name.contains("froglight") || name.contains("campfire");
        // per-block variation and a soft texture
        int hb = hash(bx, by, bz);
        double var = 1.0 + ((hb & 255) / 255.0 - 0.5) * 0.10;
        double u = hit[4] - Math.floor(hit[4]);
        double v = hit[5] - Math.floor(hit[5]);
        int tex = hash(bx * 16 + (int)(u * 4), by * 16 + (int)(v * 4), bz * 16 + ax);
        var *= 1.0 + ((tex & 255) / 255.0 - 0.5) * 0.08;
        boolean edge = u < 0.04 || u > 0.96 || v < 0.04 || v > 0.96;
        if (edge && this.shape[id] == FULL) {
            var *= 0.9;
        }

        double nx = ax == 0 ? sign : 0, ny = ax == 1 ? sign : 0, nz = ax == 2 ? sign : 0;
        // Minecraft-like face shading (top 1.0, north/south 0.8, east/west 0.6, bottom 0.5) and soft sun shadows
        double face = ax == 1 ? (sign > 0 ? 1.0 : 0.5) : (ax == 2 ? 0.8 : 0.62);
        double light = face * 1.08;
        double lambert = nx * SUN[0] + ny * SUN[1] + nz * SUN[2];
        if (lambert > 0) {
            double[] sh = this.cast(hx + nx * 0.01, hy + ny * 0.01, hz + nz * 0.01, SUN[0], SUN[1], SUN[2], 160, true);
            if (sh != null) {
                light *= 0.72;
            }
        } else {
            light *= 0.86;
        }

        // crude ambient occlusion from the blocks around the hit face
        int occ = 0;
        int fx = bx + (int)nx, fy = by + (int)ny, fz = bz + (int)nz;
        for (int a = -1; a <= 1; a++) {
            for (int b2 = -1; b2 <= 1; b2++) {
                int ox2 = ax == 0 ? 0 : a, oy2 = ax == 1 ? 0 : (ax == 0 ? a : b2), oz2 = ax == 2 ? 0 : b2;
                if (ax == 0) {
                    oy2 = a;
                    oz2 = b2;
                }

                short n = this.at(fx + ox2, fy + oy2, fz + oz2);
                if (n != 0 && this.shape[n] == FULL) {
                    occ++;
                }
            }
        }

        light *= 1.0 - occ * 0.035;
        if (glow) {
            light = Math.max(light, 1.05);
        }

        light *= var;
        int r = (int)Math.min(255, ((base >> 16) & 255) * light);
        int g = (int)Math.min(255, ((base >> 8) & 255) * light);
        int b = (int)Math.min(255, (base & 255) * light);
        int col = r << 16 | g << 8 | b;
        double fog = Math.min(0.75, Math.max(0, (t - 90) / 420.0));
        return mix(col, this.sky(0.1), fog);
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        String kind = args[0];
        String style = args[1];
        int size = Integer.parseInt(args[2]);
        File out = new File(args[3]);
        out.mkdirs();
        long seed = args.length > 4 ? Long.parseLong(args[4]) : 1234L;
        Voxels d;
        if (kind.equals("show")) {
            d = new net.practicepvp.build.ShowArenaDesign(net.practicepvp.build.ShowArenaDesign.Style.valueOf(style), size, seed, 0, 64, 0, 4, false, false);
        } else {
            Class<?> c = Class.forName("net.practicepvp.build.CourtArenaDesign");
            Class<?> st = Class.forName("net.practicepvp.build.CourtArenaDesign$Style");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object s = Enum.valueOf((Class)st, style);
            d = (Voxels)c.getConstructor(st, int.class, long.class, int.class, int.class, int.class, int.class, boolean.class, boolean.class)
                .newInstance(s, size, seed, 0, 64, 0, 4, kind.equals("courtffa"), true);
        }

        long t0 = System.currentTimeMillis();
        ArenaPreview p = new ArenaPreview(d);
        int[] s0 = ((net.practicepvp.build.ArenaVoxels)d).spawn(0);
        int fy = d.floorYAt();
        int W = 1280, H = 720;
        String name = style.toLowerCase();
        // from the fighting floor, near spawn 0, looking across to the other side (like a player at the start)
        ImageIO.write(p.render(s0[0] - 2, fy + 2.6, s0[2], -90, -6, 80, W, H), "png", new File(out, name + "-floor.png"));
        ImageIO.write(p.render(-s0[0] + 2, fy + 2.6, s0[2], 90, -6, 80, W, H), "png", new File(out, name + "-back.png"));
        // looking north from the south side of the floor
        ImageIO.write(p.render(0, fy + 2.6, 18, 180, -8, 80, W, H), "png", new File(out, name + "-north.png"));
        // 3/4 view from above
        double r = d.maxX() - d.minX();
        ImageIO.write(p.render(r * 0.55, fy + r * 0.42, r * 0.62, 138, 30, 62, W, H), "png", new File(out, name + "-air.png"));
        System.out.println(name + ": " + p.names.size() + " block states, " + (System.currentTimeMillis() - t0) + " ms");
    }
}
