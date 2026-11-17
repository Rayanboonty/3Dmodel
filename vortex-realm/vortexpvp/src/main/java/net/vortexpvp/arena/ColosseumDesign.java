package net.vortexpvp.arena;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.Map.Entry;

public final class ColosseumDesign {
    public final ColosseumDesign.Style style;
    public final long seed;
    public final int cx;
    public final int cz;
    public final int floorY;
    public final int bottomY;
    public final boolean bedrock;
    public final boolean ffa;
    public final int radius;
    public final int straight;
    public final int wallH;
    public final int rows;
    public final int galleryD;
    public final int galleryH;
    public final int barrierH;
    public final int outer;
    public final int landscape;
    public final int halfX;
    public final int halfZ;
    public final int topY;
    private final boolean grand;
    private final ColosseumDesign.Palette pal;
    private final int lastRowTop;
    private final int galleryTop;
    private final int towerTop;
    private final int gateHalf;
    private final int archH;
    private final double[] rockX;
    private final double[] rockZ;
    private final double[] rockR;
    private final int[] rockH;
    private Map<Long, TreeMap<Integer, String>> statue;
    private int stX;
    private int stY;
    private int stZ;
    private double stS = 1.0;

    public ColosseumDesign(ColosseumDesign.Style style, int size, long seed, int cx, int floorY, int cz, int depth, boolean bedrock, boolean ffa) {
        this.style = style;
        this.seed = seed;
        this.cx = cx;
        this.cz = cz;
        this.floorY = floorY;
        this.bottomY = floorY - Math.max(4, depth);
        this.bedrock = bedrock;
        this.ffa = ffa;
        this.grand = style == ColosseumDesign.Style.GRAND || style == ColosseumDesign.Style.LOBBY;
        if (this.grand) {
            this.radius = clamp((int)Math.round(size * 0.3), 26, 140);
            this.straight = 0;
            this.wallH = style == ColosseumDesign.Style.LOBBY ? 2 : 3;
            this.rows = clamp(this.radius / 5 + 2, 8, 14);
            this.galleryD = 6;
            this.galleryH = 15;
            this.barrierH = 70;
            this.landscape = 32;
            this.archH = 6;
        } else {
            this.radius = clamp((int)Math.round(size * 0.27), 16, 96);
            this.straight = clamp((int)Math.round(size * 0.11), 4, 44);
            this.wallH = 4;
            this.rows = clamp(this.radius / 3, 7, 13);
            this.galleryD = 4;
            this.galleryH = 11;
            this.barrierH = 40;
            this.landscape = 0;
            this.archH = 5;
        }

        this.outer = 1 + this.rows + this.galleryD;
        this.gateHalf = this.grand ? 4 : 3;
        this.halfX = this.straight + this.radius + this.outer + this.landscape + 6 + (style == ColosseumDesign.Style.GLADIATOR ? 20 : 0);
        this.halfZ = this.radius + this.outer + this.landscape + 6;
        this.lastRowTop = floorY + this.wallH + this.rows - 1;
        this.galleryTop = this.lastRowTop + this.galleryH;
        this.towerTop = this.galleryTop + (this.grand ? 16 : 11);
        int top = this.towerTop + 14;
        if (style == ColosseumDesign.Style.SPIRES) {
            top = Math.max(top, this.galleryTop + 34);
        }

        if (style == ColosseumDesign.Style.GLADIATOR) {
            top = Math.max(top, floorY + this.wallH + this.rows / 2 + 100);
        }

        if (this.grand) {
            top = Math.max(top, floorY + 92);
        }

        this.topY = top;
        this.pal = palette(style);
        if (style == ColosseumDesign.Style.GLADIATOR) {
            this.pal.floor = new String[]{"sand", "sand", "sand", "sand", "smooth_sandstone"};
            this.pal.banners = new String[]{"red_wall_banner"};
            this.buildStatue();
        }

        Random r = new Random(seed ^ 20973L);
        int rocks = this.grand ? 8 : 0;
        this.rockX = new double[rocks];
        this.rockZ = new double[rocks];
        this.rockR = new double[rocks];
        this.rockH = new int[rocks];

        for (int i = 0; i < rocks; i++) {
            double a = (Math.PI * 2) * i / rocks + r.nextDouble() * 0.45;
            double dist = this.radius + this.outer + this.landscape * (0.5 + r.nextDouble() * 0.3);
            this.rockX[i] = Math.cos(a) * dist;
            this.rockZ[i] = Math.sin(a) * dist;
            this.rockR[i] = 3.5 + r.nextDouble() * 3.0;
            this.rockH[i] = 36 + r.nextInt(30);
        }
    }

    /** Invisible barrier columns around the floor (arena.colosseum-barriers, off by default: the world border is the limit). */
    private static boolean barriers() {
        return net.vortexpvp.config.Cfg.bool(net.vortexpvp.config.Opt.COLOSSEUM_BARRIERS);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public int minX() {
        return this.cx - this.halfX;
    }

    public int maxX() {
        return this.cx + this.halfX;
    }

    public int minZ() {
        return this.cz - this.halfZ;
    }

    public int maxZ() {
        return this.cz + this.halfZ;
    }

    public int spawnOffset() {
        return this.straight + (int)Math.round(this.radius * (this.grand ? 0.55 : 0.62));
    }

    public int[][] pedestals(int count) {
        int[][] out = new int[count][2];
        double r = this.radius * 0.62;

        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2) * i / count + Math.PI / count;
            out[i][0] = (int)Math.round(Math.cos(a) * r);
            out[i][1] = (int)Math.round(Math.sin(a) * r);
        }

        return out;
    }

    public int surface(int rx, int rz) {
        if (this.style != ColosseumDesign.Style.GRAND) {
            return this.floorY;
        } else {
            double sp = this.spawnOffset();
            double near = Math.min(dist(rx, rz, sp, 0.0), dist(rx, rz, -sp, 0.0));
            double hs = this.rawField(rx >= 0 ? (int)sp : (int)(-sp), 0);
            double f = Math.min(1.0, Math.max(0.0, near - 4.0) / 8.0);
            double h = hs + (this.rawField(rx, rz) - hs) * f;
            return this.floorY + Math.max(0, (int)Math.floor(h));
        }
    }

    private double rawField(int rx, int rz) {
        double n = this.noise(rx * 0.045, rz * 0.045, 3) * 0.7 + this.noise(rx * 0.11, rz * 0.11, 9) * 0.3;
        double rr = Math.sqrt((double)rx * rx + (double)rz * rz);
        return Math.max(0.0, (n * 2.4 + 1.2) * (0.55 + 0.45 * rr / this.radius));
    }

    public double sd(double x, double z) {
        double px = Math.max(Math.abs(x) - this.straight, 0.0);
        return Math.sqrt(px * px + z * z) - this.radius;
    }

    private double outX(double x, double z) {
        double px = Math.abs(x) <= this.straight ? 0.0 : x - Math.signum(x) * this.straight;
        double len = Math.sqrt(px * px + z * z);
        return len < 1.0E-6 ? 0.0 : px / len;
    }

    private double outZ(double x, double z) {
        double px = Math.abs(x) <= this.straight ? 0.0 : x - Math.signum(x) * this.straight;
        double len = Math.sqrt(px * px + z * z);
        return len < 1.0E-6 ? 1.0 : z / len;
    }

    private String outward(double x, double z) {
        double ux = this.outX(x, z);
        double uz = this.outZ(x, z);
        if (Math.abs(ux) >= Math.abs(uz)) {
            return ux > 0.0 ? "east" : "west";
        } else {
            return uz > 0.0 ? "south" : "north";
        }
    }

    private static int stepX(String f) {
        return f.equals("east") ? 1 : (f.equals("west") ? -1 : 0);
    }

    private static int stepZ(String f) {
        return f.equals("south") ? 1 : (f.equals("north") ? -1 : 0);
    }

    private static String opposite(String f) {
        return switch (f) {
            case "east" -> "west";
            case "west" -> "east";
            case "north" -> "south";
            default -> "north";
        };
    }

    private double along(double x, double z, double r) {
        double l = this.straight;
        if (Math.abs(x) <= l) {
            return z >= 0.0 ? x + l : 2.0 * l + Math.PI * r + (l - x);
        } else if (x > l) {
            double a = Math.atan2(z, x - l);
            return 2.0 * l + ((Math.PI / 2) - a) * r;
        } else {
            double a = Math.atan2(z, x + l);
            if (a < 0.0) {
                a += Math.PI * 2;
            }

            return 4.0 * l + Math.PI * r + ((Math.PI * 3.0 / 2.0) - a) * r;
        }
    }

    private double spacingOn(double ringR, double spacing) {
        double p = 4 * this.straight + (Math.PI * 2) * ringR;
        return p / Math.max(4, (int)Math.round(p / spacing));
    }

    private double phase(double x, double z, double ringR, double spacing) {
        double sp = this.spacingOn(ringR, spacing);
        double m = this.along(x, z, ringR) % sp;
        return m < 0.0 ? m + sp : m;
    }

    private int index(double x, double z, double ringR, double spacing) {
        return (int)Math.floor(this.along(x, z, ringR) / this.spacingOn(ringR, spacing));
    }

    private static double dist(double x, double z, double px, double pz) {
        return Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
    }

    private int hash(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ this.seed;
        h ^= h >>> 13;
        h *= 25214903917L;
        return Math.floorMod(h ^ h >>> 17, 1000);
    }

    private double rnd(int x, int z, int salt) {
        return this.hash(x, salt, z) / 1000.0;
    }

    private double noise(double x, double z, int salt) {
        int x0 = (int)Math.floor(x);
        int z0 = (int)Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double sx = fx * fx * (3.0 - 2.0 * fx);
        double sz = fz * fz * (3.0 - 2.0 * fz);
        double a = this.rnd(x0, z0, salt);
        double b = this.rnd(x0 + 1, z0, salt);
        double c = this.rnd(x0, z0 + 1, salt);
        double d = this.rnd(x0 + 1, z0 + 1, salt);
        double v = a + (b - a) * sx + (c - a) * sz + (a - b - c + d) * sx * sz;
        return v * 2.0 - 1.0;
    }

    private String pick(String[] options, int x, int z, int salt) {
        int h = this.hash(Math.floorDiv(x, 2) + salt, salt * 7, Math.floorDiv(z, 2) - salt);
        int h2 = this.hash(x, salt, z);
        int i = (h * 3 + h2) % 1000;
        return i < 600 ? options[0] : options[1 + i % (options.length - 1)];
    }

    private static ColosseumDesign.Palette palette(ColosseumDesign.Style s) {
        ColosseumDesign.Palette p = new ColosseumDesign.Palette();
        p.banners = new String[]{"red_wall_banner", "yellow_wall_banner"};
        p.light = "lantern";
        p.railing = "dark_oak_fence";
        p.carpet = "red_carpet";
        p.flag = "red_wool";
        p.gate = "dark_oak_planks";
        switch (s) {
            case DESERT:
            case GLADIATOR:
                p.floor = new String[]{"sand", "sand", "sandstone", "smooth_sandstone", "suspicious_sand"};
                p.floorLine = "cut_sandstone";
                p.floorLine2 = "orange_terracotta";
                p.floorCenter = "smooth_sandstone";
                p.floorCenter2 = "chiseled_sandstone";
                p.floorBorder = "sandstone";
                p.pad = "smooth_sandstone";
                p.wall = "cut_sandstone";
                p.wallPanel = "dark_oak_planks";
                p.wallBand = "red_terracotta";
                p.wallPillar = "chiseled_sandstone";
                p.wallTrim = "smooth_sandstone_slab";
                p.stand = "sandstone";
                p.stand2 = "cut_sandstone";
                p.seat = "smooth_sandstone_stairs";
                p.seat2 = "red_sandstone_stairs";
                p.walkway = "cut_sandstone";
                p.aisle = "sandstone_stairs";
                p.gallery = "smooth_sandstone";
                p.gallery2 = "cut_red_sandstone";
                p.pillar = "chiseled_sandstone";
                p.cornice = "smooth_sandstone_stairs";
                p.roof = "red_terracotta";
                p.roofStairs = "granite_stairs";
                p.tower = "smooth_sandstone";
                p.towerBand = "red_terracotta";
                p.towerTop = "cut_sandstone";
                p.towerRoof = "red_terracotta";
                p.ground = "sandstone";
                break;
            case TEMPLE:
                p.floor = new String[]{"mud_bricks", "packed_mud", "packed_mud", "packed_mud", "packed_mud", "brown_terracotta"};
                p.floorLine = "sandstone";
                p.floorLine2 = "smooth_sandstone";
                p.floorCenter = "smooth_sandstone";
                p.floorCenter2 = "chiseled_sandstone";
                p.floorBorder = "packed_mud";
                p.pad = "smooth_quartz";
                p.wall = "stone_bricks";
                p.wallPanel = "red_terracotta";
                p.wallBand = "chiseled_stone_bricks";
                p.wallPillar = "chiseled_stone_bricks";
                p.wallTrim = "stone_brick_slab";
                p.stand = "stone_bricks";
                p.stand2 = "cracked_stone_bricks";
                p.seat = "stone_brick_stairs";
                p.seat2 = "mossy_stone_brick_stairs";
                p.walkway = "polished_andesite";
                p.aisle = "cobblestone_stairs";
                p.gallery = "stone_bricks";
                p.gallery2 = "red_terracotta";
                p.pillar = "chiseled_stone_bricks";
                p.cornice = "stone_brick_stairs";
                p.roof = "terracotta";
                p.roofStairs = "mud_brick_stairs";
                p.banners = new String[]{"red_wall_banner", "green_wall_banner"};
                p.tower = "stone_bricks";
                p.towerBand = "chiseled_stone_bricks";
                p.towerTop = "mossy_cobblestone";
                p.towerRoof = "mossy_stone_bricks";
                p.foliage = "oak_leaves";
                p.ground = "packed_mud";
                p.gate = "spruce_planks";
                p.railing = "spruce_fence";
                p.carpet = "green_carpet";
                break;
            case SPIRES:
                p.floor = new String[]{"sand", "sandstone", "sand", "smooth_sandstone"};
                p.floorLine = "tuff";
                p.floorLine2 = "polished_tuff";
                p.floorCenter = "waxed_cut_copper";
                p.floorCenter2 = "polished_granite";
                p.floorBorder = "polished_andesite";
                p.pad = "polished_tuff";
                p.wall = "deepslate_bricks";
                p.wallPanel = "brown_terracotta";
                p.wallBand = "red_terracotta";
                p.wallPillar = "chiseled_tuff_bricks";
                p.wallTrim = "polished_deepslate_slab";
                p.stand = "deepslate_tiles";
                p.stand2 = "cracked_deepslate_tiles";
                p.seat = "polished_deepslate_stairs";
                p.seat2 = "tuff_brick_stairs";
                p.walkway = "polished_tuff";
                p.aisle = "deepslate_tile_stairs";
                p.gallery = "deepslate_bricks";
                p.gallery2 = "tuff_bricks";
                p.pillar = "chiseled_tuff_bricks";
                p.cornice = "deepslate_brick_stairs";
                p.roof = "polished_deepslate";
                p.roofStairs = "deepslate_tile_stairs";
                p.banners = new String[]{"red_wall_banner", "black_wall_banner"};
                p.tower = "deepslate_tiles";
                p.towerBand = "red_terracotta";
                p.towerTop = "polished_blackstone_bricks";
                p.towerRoof = "red_terracotta";
                p.ground = "tuff";
                p.gate = "iron_bars";
                p.railing = "iron_bars";
                p.carpet = "red_carpet";
                break;
            case NETHER:
                p.floor = new String[]{"nether_bricks", "cracked_nether_bricks", "nether_bricks", "cracked_nether_bricks"};
                p.floorLine = "polished_blackstone";
                p.floorLine2 = "blackstone";
                p.floorCenter = "gilded_blackstone";
                p.floorCenter2 = "chiseled_polished_blackstone";
                p.floorBorder = "blackstone";
                p.pad = "polished_blackstone";
                p.wall = "polished_blackstone_bricks";
                p.wallPanel = "red_nether_bricks";
                p.wallBand = "gilded_blackstone";
                p.wallPillar = "chiseled_polished_blackstone";
                p.wallTrim = "polished_blackstone_slab";
                p.stand = "red_nether_bricks";
                p.stand2 = "nether_bricks";
                p.seat = "red_nether_brick_stairs";
                p.seat2 = "polished_blackstone_brick_stairs";
                p.walkway = "polished_blackstone";
                p.aisle = "blackstone_stairs";
                p.gallery = "polished_blackstone_bricks";
                p.gallery2 = "red_nether_bricks";
                p.pillar = "chiseled_polished_blackstone";
                p.cornice = "polished_blackstone_brick_stairs";
                p.roof = "nether_wart_block";
                p.roofStairs = "red_nether_brick_stairs";
                p.banners = new String[]{"red_wall_banner", "orange_wall_banner"};
                p.light = "soul_lantern";
                p.tower = "polished_blackstone_bricks";
                p.towerBand = "gilded_blackstone";
                p.towerTop = "blackstone";
                p.towerRoof = "nether_wart_block";
                p.ground = "netherrack";
                p.gate = "iron_bars";
                p.railing = "nether_brick_fence";
                p.flag = "orange_wool";
                break;
            case FROST:
                p.floor = new String[]{"snow_block", "snow_block", "white_concrete", "calcite"};
                p.floorLine = "light_blue_concrete";
                p.floorLine2 = "packed_ice";
                p.floorCenter = "smooth_quartz";
                p.floorCenter2 = "light_blue_glazed_terracotta";
                p.floorBorder = "polished_diorite";
                p.pad = "smooth_quartz";
                p.wall = "polished_diorite";
                p.wallPanel = "packed_ice";
                p.wallBand = "light_blue_concrete";
                p.wallPillar = "quartz_pillar";
                p.wallTrim = "smooth_quartz_slab";
                p.stand = "calcite";
                p.stand2 = "diorite";
                p.seat = "polished_diorite_stairs";
                p.seat2 = "prismarine_brick_stairs";
                p.walkway = "smooth_quartz";
                p.aisle = "quartz_stairs";
                p.gallery = "quartz_bricks";
                p.gallery2 = "packed_ice";
                p.pillar = "quartz_pillar";
                p.cornice = "quartz_stairs";
                p.roof = "packed_ice";
                p.roofStairs = "prismarine_brick_stairs";
                p.banners = new String[]{"light_blue_wall_banner", "white_wall_banner"};
                p.light = "sea_lantern";
                p.tower = "quartz_bricks";
                p.towerBand = "packed_ice";
                p.towerTop = "polished_diorite";
                p.towerRoof = "blue_ice";
                p.ground = "calcite";
                p.gate = "iron_bars";
                p.railing = "iron_bars";
                p.carpet = "light_blue_carpet";
                p.flag = "light_blue_wool";
                break;
            case CHERRY:
                p.floor = new String[]{"white_terracotta", "white_terracotta", "calcite", "white_terracotta"};
                p.floorLine = "cherry_planks";
                p.floorLine2 = "white_terracotta";
                p.floorCenter = "pink_concrete";
                p.floorCenter2 = "pink_glazed_terracotta";
                p.floorBorder = "stripped_cherry_wood";
                p.pad = "smooth_quartz";
                p.wall = "smooth_quartz";
                p.wallPanel = "cherry_planks";
                p.wallBand = "pink_terracotta";
                p.wallPillar = "stripped_cherry_log";
                p.wallTrim = "smooth_quartz_slab";
                p.stand = "white_terracotta";
                p.stand2 = "calcite";
                p.seat = "cherry_stairs";
                p.seat2 = "quartz_stairs";
                p.walkway = "smooth_quartz";
                p.aisle = "cherry_stairs";
                p.gallery = "smooth_quartz";
                p.gallery2 = "pink_terracotta";
                p.pillar = "stripped_cherry_log";
                p.cornice = "quartz_stairs";
                p.roof = "pink_terracotta";
                p.roofStairs = "cherry_stairs";
                p.banners = new String[]{"pink_wall_banner", "white_wall_banner"};
                p.tower = "smooth_quartz";
                p.towerBand = "pink_terracotta";
                p.towerTop = "cherry_planks";
                p.towerRoof = "pink_terracotta";
                p.foliage = "cherry_leaves";
                p.ground = "white_terracotta";
                p.gate = "cherry_planks";
                p.railing = "cherry_fence";
                p.carpet = "pink_carpet";
                p.flag = "pink_wool";
                break;
            case GRAND:
            case LOBBY:
            default:
                boolean lobby = s == ColosseumDesign.Style.LOBBY;
                p.floor = lobby
                    ? new String[]{"polished_andesite", "smooth_stone", "polished_andesite", "andesite"}
                    : new String[]{"grass_block", "grass_block", "moss_block"};
                p.floorLine = lobby ? "smooth_sandstone" : "grass_block";
                p.floorLine2 = lobby ? "cut_sandstone" : "grass_block";
                p.floorCenter = lobby ? "polished_deepslate" : "grass_block";
                p.floorCenter2 = lobby ? "gold_block" : "grass_block";
                p.floorBorder = lobby ? "stone_bricks" : "coarse_dirt";
                p.pad = lobby ? "chiseled_stone_bricks" : "grass_block";
                p.wall = "stone_bricks";
                p.wallPanel = "spruce_planks";
                p.wallBand = "smooth_sandstone";
                p.wallPillar = "chiseled_sandstone";
                p.wallTrim = "smooth_sandstone_slab";
                p.stand = "terracotta";
                p.stand2 = "brown_terracotta";
                p.seat = "spruce_stairs";
                p.seat2 = "dark_oak_stairs";
                p.walkway = "smooth_sandstone";
                p.aisle = "stone_brick_stairs";
                p.gallery = "smooth_sandstone";
                p.gallery2 = "terracotta";
                p.pillar = "chiseled_sandstone";
                p.cornice = "smooth_sandstone_stairs";
                p.roof = "red_terracotta";
                p.roofStairs = "red_nether_brick_stairs";
                p.banners = new String[]{"red_wall_banner", "blue_wall_banner"};
                p.tower = "smooth_sandstone";
                p.towerBand = "terracotta";
                p.towerTop = "cut_sandstone";
                p.towerRoof = "red_terracotta";
                p.foliage = "spruce_leaves";
                p.ground = "dirt";
                p.gate = "spruce_planks";
                p.railing = "spruce_fence";
                break;
            case CHAMPIONS:
                p.floor = new String[]{"polished_deepslate", "polished_deepslate", "deepslate_tiles", "polished_blackstone", "deepslate_bricks"};
                p.floorLine = "orange_terracotta";
                p.floorLine2 = "red_nether_bricks";
                p.floorCenter = "gold_block";
                p.floorCenter2 = "chiseled_quartz_block";
                p.floorBorder = "quartz_bricks";
                p.pad = "smooth_quartz";
                p.wall = "quartz_bricks";
                p.wallPanel = "red_nether_bricks";
                p.wallBand = "orange_terracotta";
                p.wallPillar = "quartz_pillar";
                p.wallTrim = "smooth_quartz_slab";
                p.stand = "quartz_bricks";
                p.stand2 = "smooth_quartz";
                p.seat = "red_nether_brick_stairs";
                p.seat2 = "quartz_stairs";
                p.walkway = "polished_blackstone_bricks";
                p.aisle = "quartz_stairs";
                p.gallery = "smooth_quartz";
                p.gallery2 = "red_nether_bricks";
                p.pillar = "quartz_pillar";
                p.cornice = "quartz_stairs";
                p.roof = "red_nether_bricks";
                p.roofStairs = "red_nether_brick_stairs";
                p.tower = "quartz_bricks";
                p.towerBand = "gold_block";
                p.towerTop = "chiseled_quartz_block";
                p.towerRoof = "red_nether_bricks";
                p.ground = "polished_deepslate";
                p.banners = new String[]{"red_wall_banner", "orange_wall_banner"};
                p.flag = "orange_wool";
                p.railing = "nether_brick_fence";
                p.gate = "crimson_planks";
        }

        return p;
    }

    private String hanging() {
        String l = this.pal.light.endsWith("lantern") && !this.pal.light.equals("sea_lantern") ? this.pal.light : "soul_lantern";
        return "minecraft:" + l + "[hanging=true]";
    }

    private static String b(String id) {
        return "minecraft:" + id;
    }

    private static String stairs(String id, String facing, boolean top) {
        return "minecraft:" + id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=straight]";
    }

    private static String slab(String id) {
        return "minecraft:" + id + "[type=bottom]";
    }

    private static String leaves(String id) {
        return "minecraft:" + id + "[persistent=true]";
    }

    public void column(int x, int z, ColosseumDesign.Sink out) {
        this.columnInner(x, z, out);
        if (this.statue != null) {
            TreeMap<Integer, String> col = this.statue.get(statueKey(x - this.cx, z - this.cz));
            if (col != null) {
                for (Entry<Integer, String> e : col.entrySet()) {
                    out.set(x, this.floorY + e.getKey(), z, e.getValue());
                }
            }
        }
    }

    private static long statueKey(int rx, int rz) {
        return (long)rx << 32 ^ rz & 4294967295L;
    }

    private void sv(int rx, int y, int rz, String block) {
        if (!(this.stS > 1.0001)) {
            this.statue.computeIfAbsent(statueKey(rx, rz), k -> new TreeMap<>()).put(y, block);
        } else {
            int mx = rx - this.stX;
            int my = y - this.stY;
            int mz = rz - this.stZ;

            for (int wx = (int)Math.floor(mx * this.stS); wx < (int)Math.floor((mx + 1) * this.stS); wx++) {
                for (int wy = (int)Math.floor(my * this.stS); wy < (int)Math.floor((my + 1) * this.stS); wy++) {
                    for (int wz = (int)Math.floor(mz * this.stS); wz < (int)Math.floor((mz + 1) * this.stS); wz++) {
                        this.statue.computeIfAbsent(statueKey(this.stX + wx, this.stZ + wz), k -> new TreeMap<>()).put(this.stY + wy, block);
                    }
                }
            }
        }
    }

    private String stoneAt(int x, int y, int z) {
        int h = this.hash(x, y, z);
        return b(h < 300 ? "andesite" : (h < 520 ? "polished_andesite" : (h < 760 ? "stone" : (h < 900 ? "light_gray_concrete" : "tuff"))));
    }

    private void box(int sx, int sz, int x0, int x1, int y0, int y1, int z0, int z1, String block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    this.sv(sx + x, y, sz + z, block == null ? this.stoneAt(x, y, z) : block);
                }
            }
        }
    }

    private void ball(int sx, int sz, double bx, double by, double bz, double r) {
        int ri = (int)Math.ceil(r);

        for (int x = (int)Math.floor(bx) - ri; x <= (int)Math.ceil(bx) + ri; x++) {
            for (int y = (int)Math.floor(by) - ri; y <= (int)Math.ceil(by) + ri; y++) {
                for (int z = (int)Math.floor(bz) - ri; z <= (int)Math.ceil(bz) + ri; z++) {
                    if ((x - bx) * (x - bx) + (y - by) * (y - by) + (z - bz) * (z - bz) <= r * r) {
                        this.sv(sx + x, y, sz + z, this.stoneAt(x, y, z));
                    }
                }
            }
        }
    }

    private void buildStatue() {
        this.statue = new HashMap<>();
        int sx = -(this.straight + this.radius + this.outer + 12);
        int sz = 0;
        int base = this.wallH + this.rows / 2;
        this.box(sx, sz, -6, 6, -6, base + 1, -9, 9, b("smooth_sandstone"));
        this.box(sx, sz, -7, 7, base + 2, base + 2, -10, 10, b("chiseled_sandstone"));
        int y0 = base + 3;
        this.stX = sx;
        this.stY = y0;
        this.stZ = sz;
        this.stS = 1.7;
        this.box(sx, sz, -1, 1, y0, y0 + 3, -4, -2, b("polished_andesite"));
        this.box(sx, sz, -1, 1, y0, y0 + 3, 2, 4, b("polished_andesite"));
        this.box(sx, sz, -1, 1, y0 + 4, y0 + 13, -4, -2, null);
        this.box(sx, sz, -1, 1, y0 + 4, y0 + 13, 2, 4, null);
        this.box(sx, sz, -2, 2, y0 + 12, y0 + 15, -6, 6, null);
        this.box(sx, sz, -2, 2, y0 + 16, y0 + 27, -5, 5, null);
        this.box(sx, sz, -2, 2, y0 + 16, y0 + 16, -5, 5, b("gold_block"));
        this.box(sx, sz, 3, 3, y0 + 19, y0 + 25, -3, 3, b("polished_andesite"));
        this.ball(sx, sz, 0.0, y0 + 26, -7.0, 2.6);
        this.ball(sx, sz, 0.0, y0 + 26, 7.0, 2.6);
        this.box(sx, sz, -1, 1, y0 + 19, y0 + 25, 7, 9, null);
        this.box(sx, sz, 0, 4, y0 + 19, y0 + 21, 7, 9, null);
        this.box(sx, sz, -1, 1, y0 + 19, y0 + 25, -9, -7, null);
        this.box(sx, sz, 0, 3, y0 + 19, y0 + 21, -9, -7, null);
        this.box(sx, sz, -1, 1, y0 + 28, y0 + 29, -1, 1, null);
        this.box(sx, sz, -2, 2, y0 + 30, y0 + 35, -3, 3, b("light_gray_concrete"));
        this.box(sx, sz, 3, 3, y0 + 32, y0 + 32, -2, 2, b("gray_concrete"));

        for (int x = -3; x <= 3; x++) {
            int h = 3 - Math.abs(x) / 2;

            for (int y = 0; y < h; y++) {
                this.sv(sx + x, y0 + 36 + y, sz, b("red_wool"));
            }
        }

        int spx = sx + 5;
        int spz = sz + 8;

        for (int y = y0; y <= y0 + 44; y++) {
            this.sv(spx, y, spz, b("stripped_dark_oak_log[axis=y]"));
        }

        for (int y = y0 + 45; y <= y0 + 49; y++) {
            int r = (y0 + 49 - y) / 2;

            for (int dz = -r; dz <= r; dz++) {
                this.sv(spx, y, spz + dz, b("iron_block"));
            }
        }

        int shx = sx + 4;
        double scy = y0 + 20;
        double scz = sz - 7;
        double sr = 7.5;

        for (int y = (int)(scy - sr) - 1; y <= (int)(scy + sr) + 1; y++) {
            for (int z = (int)(scz - sr) - 1; z <= (int)(scz + sr) + 1; z++) {
                double d = Math.sqrt((y - scy) * (y - scy) + (z - scz) * (z - scz));
                if (d <= sr) {
                    String c = d < 1.6
                        ? "gold_block"
                        : (d < 3.2 ? "red_concrete" : (d < 4.6 ? "white_concrete" : (d < 6.2 ? "red_concrete" : "white_concrete")));
                    this.sv(shx, y, z, b(c));
                    if (d < 1.6) {
                        this.sv(shx + 1, y, z, b("gold_block"));
                    }
                }
            }
        }

        this.stS = 1.0;
    }

    private void columnInner(int x, int z, ColosseumDesign.Sink out) {
        int rx = x - this.cx;
        int rz = z - this.cz;
        double d = this.sd(rx, rz);
        if (this.grand) {
            this.rocks(x, z, rx, rz, out);
        }

        if (!(d > this.outer + this.landscape + 2)) {
            if (!this.gate(x, z, rx, rz, d, out)) {
                if (d < 0.0) {
                    this.floor(x, z, rx, rz, d, out);
                } else if (d < 1.0) {
                    this.ground(x, z, this.floorY, out);
                    this.podium(x, z, rx, rz, out);
                } else if (d < 1 + this.rows) {
                    this.ground(x, z, this.floorY, out);
                    this.stands(x, z, rx, rz, d, out);
                } else if (d < this.outer) {
                    this.ground(x, z, this.floorY, out);
                    this.gallery(x, z, rx, rz, d, out);
                } else if (this.landscape > 0) {
                    this.hills(x, z, rx, rz, d, out);
                } else if (d < this.outer + 1.2) {
                    for (int y = this.floorY - 4; y <= this.floorY + 1; y++) {
                        out.set(x, y, z, b(y == this.floorY + 1 ? this.pal.wallBand : (y == this.floorY - 4 && this.bedrock ? "bedrock" : this.pal.wall)));
                    }
                }
            }
        }
    }

    private void ground(int x, int z, int top, ColosseumDesign.Sink out) {
        for (int y = this.bottomY; y <= top; y++) {
            int below = top - y;
            int above = y - this.bottomY;
            String blk;
            if (!this.bedrock || above != 0 && (above > 3 || this.hash(x, y, z) >= (above == 1 ? 700 : (above == 2 ? 400 : 150)))) {
                if (below <= 2) {
                    blk = this.pal.ground;
                } else if (this.style == ColosseumDesign.Style.NETHER) {
                    blk = this.hash(x >> 1, y >> 1, z >> 1) < 150 ? "blackstone" : "netherrack";
                } else if (y < this.floorY - 12) {
                    int h = this.hash(x >> 1, y >> 1, z >> 1);
                    blk = h < 60 ? "tuff" : (h < 110 ? "cobbled_deepslate" : "deepslate");
                } else {
                    int h = this.hash(x >> 1, y >> 1, z >> 1);
                    blk = h < 70 ? "andesite" : (h < 100 ? "tuff" : "stone");
                }
            } else {
                blk = "bedrock";
            }

            out.set(x, y, z, b(blk));
        }
    }

    private void floor(int x, int z, int rx, int rz, double d, ColosseumDesign.Sink out) {
        if (this.style != ColosseumDesign.Style.GRAND) {
            this.ground(x, z, this.floorY - 1, out);
            out.set(x, this.floorY, z, b(this.floorBlock(x, z, rx, rz, d)));
            if (this.style == ColosseumDesign.Style.LOBBY) {
                this.lobbyCentre(x, z, rx, rz, out);
            }
        } else {
            int top = this.surface(rx, rz);
            this.ground(x, z, top - 1, out);
            boolean step = this.surface(rx + 1, rz) < top || this.surface(rx - 1, rz) < top || this.surface(rx, rz + 1) < top || this.surface(rx, rz - 1) < top;
            String g = d > -2.5 ? "coarse_dirt" : (step && this.hash(x, 3, z) < 300 ? "moss_block" : "grass_block");
            out.set(x, top, z, b(g));
            this.tree(x, z, rx, rz, out);
        }
    }

    private String floorBlock(int x, int z, int rx, int rz, double d) {
        double r = Math.sqrt((double)rx * rx + (double)rz * rz);
        int sp = this.spawnOffset();
        if (this.ffa || this.style == ColosseumDesign.Style.LOBBY || Math.abs(rz) > 2 || Math.abs(rx - sp) > 2 && Math.abs(rx + sp) > 2) {
            if (d > -1.5) {
                return this.pal.floorBorder;
            } else if (d > -3.0 && d < -2.0) {
                return this.pal.floorLine;
            } else {
                double c = this.radius * 0.32;
                double ang = Math.atan2(rz, rx);
                switch (this.style) {
                    case DESERT:
                        if (r < c * 0.5) {
                            return Math.abs(Math.cos(ang * 4.0)) > 0.85 ? this.pal.floorLine2 : this.pal.floorCenter2;
                        } else if (r < c) {
                            return Math.abs(r - c * 0.75) < 0.6 ? this.pal.floorLine : this.pal.floorCenter;
                        } else if (Math.abs(r - c) < 1.0) {
                            return this.pal.floorLine;
                        } else if (Math.abs(r - c * 2.1) < 0.6) {
                            return this.pal.floorLine2;
                        } else if (r < c * 2.1 && Math.abs(Math.sin(ang * 4.0)) * r < 0.8) {
                            return this.pal.floorLine2;
                        } else {
                            if (Math.abs(rz) <= 1 && Math.abs(rx) > c) {
                                return rz == 0 ? this.pal.floorCenter : this.pal.floorLine;
                            }

                            return this.pick(this.pal.floor, x, z, 11);
                        }
                    case TEMPLE:
                        if (!(r < c * 0.6)) {
                            if (!(Math.abs(r - c * 0.6) < 0.7) && !(Math.abs(r - c * 1.3) < 0.7)) {
                                if (r < c * 1.3) {
                                    return this.pick(new String[]{"mud_bricks", "packed_mud"}, x, z, 4);
                                }

                                int dx = Math.abs(rx);
                                int dz = Math.abs(rz);
                                if (Math.abs(dx - dz) <= 1) {
                                    return Math.abs(dx - dz) == 0 ? this.pal.floorLine2 : this.pal.floorLine;
                                }

                                if (Math.abs(r - this.radius * 0.8) < 0.7) {
                                    return this.pal.floorLine;
                                }

                                return this.pick(this.pal.floor, x, z, 5);
                            }

                            return this.pal.floorLine;
                        }

                        return Math.abs(rx) != Math.abs(rz) && rx != 0 && rz != 0 ? this.pal.floorCenter : this.pal.floorCenter2;
                    case SPIRES:
                        double diamond = Math.abs(rx) * 0.75 + Math.abs(rz);
                        if (diamond < c * 0.9) {
                            if (Math.abs(diamond - c * 0.45) < 0.6) {
                                return "polished_granite";
                            }

                            return diamond < c * 0.45
                                ? ((rx + rz & 1) == 0 ? "waxed_cut_copper" : "waxed_copper_block")
                                : ((Math.floorDiv(rx, 2) + Math.floorDiv(rz, 2) & 1) == 0 ? "granite" : "polished_granite");
                        } else if (diamond < c * 1.5) {
                            return this.pick(new String[]{"polished_andesite", "andesite", "tuff", "stone"}, x, z, 6);
                        } else {
                            if (Math.abs(diamond - c * 1.5) < 0.7) {
                                return this.pal.floorLine;
                            }

                            return this.pick(this.pal.floor, x, z, 7);
                        }
                    case NETHER:
                        if (r < c * 0.55) {
                            return (rx + rz & 1) == 0 ? this.pal.floorCenter : this.pal.floorCenter2;
                        } else {
                            if (!(Math.abs(r - c * 0.55) < 0.7) && !(Math.abs(r - c * 1.35) < 0.7)) {
                                if (Math.abs(Math.abs(rx) - Math.abs(rz)) <= 0) {
                                    return this.pal.floorLine;
                                }

                                return this.pick(this.pal.floor, x, z, 8);
                            }

                            return "polished_blackstone_bricks";
                        }
                    case FROST:
                        if (r < c * 0.55) {
                            return Math.abs(Math.cos(ang * 4.0)) > 0.92 - r * 0.02 ? this.pal.floorCenter2 : this.pal.floorCenter;
                        } else {
                            if (!(Math.abs(r - c * 0.55) < 0.7) && !(Math.abs(r - c * 1.6) < 0.7)) {
                                if (r < c * 1.6 && Math.abs(Math.sin(ang * 4.0)) * r < 0.7) {
                                    return this.pal.floorLine2;
                                }

                                return this.pick(this.pal.floor, x, z, 12);
                            }

                            return this.pal.floorLine;
                        }
                    case CHERRY:
                        double petal = r / (c * (0.8 + 0.25 * Math.cos(ang * 6.0)));
                        if (petal < 0.35) {
                            return this.pal.floorCenter2;
                        } else if (petal < 1.0) {
                            return Math.abs(petal - 0.7) < 0.08 ? this.pal.floorLine : this.pal.floorCenter;
                        } else {
                            if (!(Math.abs(petal - 1.0) < 0.05) && !(Math.abs(r - c * 1.9) < 0.7)) {
                                return this.pick(this.pal.floor, x, z, 13);
                            }

                            return this.pal.floorLine;
                        }
                    default:
                        if (r < 5.5) {
                            return "polished_deepslate";
                        } else {
                            double star = Math.abs(Math.cos(ang * 4.0));
                            if (r < 14.0 && star > 0.93 + r * 0.004) {
                                return "gold_block";
                            } else if (!(Math.abs(r - 14.0) < 0.6)
                                && !(Math.abs(r - this.radius * 0.62) < 0.6)
                                && (!(Math.abs(r - this.radius * 0.62) > 3.5) || !(Math.abs(r - this.radius * 0.62) < 4.1))) {
                                if (r < 14.0) {
                                    return (Math.floorDiv(rx, 2) + Math.floorDiv(rz, 2) & 1) == 0 ? "polished_andesite" : "smooth_stone";
                                } else {
                                    return this.pick(this.pal.floor, x, z, 9);
                                }
                            } else {
                                return "smooth_sandstone";
                            }
                        }
                }
            }
        } else {
            boolean edge = Math.abs(rz) == 2 || Math.abs(Math.abs(rx) - sp) == 2;
            return edge ? this.pal.floorLine : this.pal.pad;
        }
    }

    private void tree(int x, int z, int rx, int rz, ColosseumDesign.Sink out) {
        double[][] trees = new double[][]{{0.42, 0.55}, {-0.38, -0.62}, {-0.55, 0.38}, {0.1, -0.78}};

        for (double[] t : trees) {
            int tx = (int)Math.round(t[0] * this.radius);
            int tz = (int)Math.round(t[1] * this.radius);
            double dd = dist(rx, rz, tx, tz);
            if (!(dd > 3.6)) {
                int base = this.surface(tx, tz) + 1;
                if (dd < 0.5) {
                    for (int y = base; y < base + 5; y++) {
                        out.set(x, y, z, b("oak_log"));
                    }
                }

                for (int y = base + 3; y <= base + 7; y++) {
                    int lr = y >= base + 6 ? 1 : 2;
                    if (dd <= lr + 0.6 && (!(dd < 0.5) || y >= base + 5) && this.hash(x, y, z) < 900) {
                        out.set(x, y, z, leaves("oak_leaves"));
                    }
                }
            }
        }
    }

    private void lobbyCentre(int x, int z, int rx, int rz, ColosseumDesign.Sink out) {
        double r = Math.sqrt((double)rx * rx + (double)rz * rz);
        if (r < 5.5) {
            if (r >= 4.5) {
                out.set(x, this.floorY + 1, z, b("polished_deepslate_wall"));
            } else if (r < 1.0) {
                for (int y = this.floorY + 1; y <= this.floorY + 4; y++) {
                    out.set(x, y, z, b(y == this.floorY + 4 ? "chiseled_quartz_block" : "quartz_pillar"));
                }

                out.set(x, this.floorY + 5, z, b("water"));
            } else {
                out.set(x, this.floorY, z, b("water"));
            }
        }

        for (int[] pd : this.pedestals(8)) {
            int dx = rx - pd[0];
            int dz = rz - pd[1];
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                boolean centre = dx == 0 && dz == 0;
                out.set(x, this.floorY + 1, z, b(centre ? "chiseled_stone_bricks" : "polished_deepslate"));
                if (centre) {
                    out.set(x, this.floorY, z, b("gold_block"));
                }
            }
        }
    }

    private boolean aisle(int rx, int rz) {
        return this.phase(rx, rz, this.radius + 1 + this.rows / 2.0, 16.0) < 2.0;
    }

    private void podium(int x, int z, int rx, int rz, ColosseumDesign.Sink out) {
        String outward = this.outward(rx, rz);
        if (this.style == ColosseumDesign.Style.LOBBY && this.aisle(rx, rz)) {
            out.set(x, this.floorY + 1, z, stairs(this.pal.aisle, outward, false));
        } else {
            double ring = this.radius + 0.5;
            double ph = this.phase(rx, rz, ring, 6.0);
            double sp = this.spacingOn(ring, 6.0);
            boolean pillar = ph < 1.0;

            for (int y = this.floorY + 1; y <= this.floorY + this.wallH; y++) {
                int h = y - this.floorY;
                String blk;
                if (pillar) {
                    blk = this.pal.wallPillar;
                } else if (h == this.wallH) {
                    blk = this.pal.wallBand;
                } else {
                    double mid = Math.abs(ph - sp / 2.0) / (sp / 2.0);
                    double archTop = this.wallH - 1.2 - mid * mid * 1.4;
                    blk = h <= archTop ? this.pal.wallPanel : this.pal.wall;
                    if (this.style == ColosseumDesign.Style.TEMPLE && blk.equals(this.pal.wall) && this.hash(x, y, z) < 220) {
                        blk = "mossy_stone_bricks";
                    }
                }

                out.set(x, y, z, b(blk));
            }

            out.set(x, this.floorY + this.wallH + 1, z, pillar ? b(this.pal.wallPillar) : slab(this.pal.wallTrim));
            if (pillar) {
                out.set(x, this.floorY + this.wallH + 2, z, b(this.pal.light));
            }

            for (int y = this.floorY + this.wallH + (pillar ? 3 : 2); y <= this.floorY + this.barrierH && this.style != ColosseumDesign.Style.LOBBY && barriers(); y++) {
                out.set(x, y, z, b("barrier"));
            }

            if (this.style == ColosseumDesign.Style.FROST && !pillar) {
                out.set(x, this.floorY + this.wallH + 2, z, "minecraft:snow[layers=2]");
            }

            if (this.style == ColosseumDesign.Style.CHERRY && !pillar && this.hash(x, 5, z) < 500) {
                out.set(x, this.floorY + this.wallH + 2, z, "minecraft:pink_petals[facing=" + outward + ",flower_amount=" + (1 + this.hash(x, 6, z) % 4) + "]");
            }
        }
    }

    private boolean inVip(int rx, int rz) {
        return !this.grand && rz < 0 && Math.abs(rx) <= 4;
    }

    private void stands(int x, int z, int rx, int rz, double d, ColosseumDesign.Sink out) {
        int row = (int)Math.floor(d - 1.0);
        int top = this.floorY + this.wallH + row;
        String outward = this.outward(rx, rz);
        if (this.inVip(rx, rz) && row >= this.rows - 5) {
            this.vipBox(x, z, rx, rz, row, out);
        } else {
            boolean aisle = this.aisle(rx, rz);
            boolean walk = row % 4 == 3;

            for (int y = this.floorY + 1; y < top; y++) {
                out.set(x, y, z, b(this.hash(x, y, z) < 140 ? this.pal.stand2 : this.pal.stand));
            }

            String seatBlk;
            if (aisle) {
                seatBlk = stairs(this.pal.aisle, outward, false);
            } else if (walk) {
                seatBlk = b(this.pal.walkway);
            } else {
                seatBlk = stairs(row % 3 == 1 ? this.pal.seat2 : this.pal.seat, outward, false);
            }

            out.set(x, top, z, seatBlk);
            if (this.style == ColosseumDesign.Style.GRAND && row == 0 && this.hash(x, 1, z) < 750) {
                out.set(x, top + 1, z, leaves("spruce_leaves"));
            }

            if (this.style == ColosseumDesign.Style.FROST && walk) {
                out.set(x, top + 1, z, "minecraft:snow[layers=1]");
            }

            if (this.style == ColosseumDesign.Style.TEMPLE) {
                this.waterfall(x, z, rx, rz, row, top, out);
            }

            if (row == this.rows - 1) {
                int bx = rx + stepX(outward);
                int bz = rz + stepZ(outward);
                if (this.sd(bx, bz) >= 1 + this.rows) {
                    double gRing = this.radius + 1 + this.rows + 0.5;
                    double gp = this.phase(bx, bz, gRing, this.pillarSpacing());
                    if (gp < 1.2) {
                        int idx = this.index(bx, bz, gRing, this.pillarSpacing());
                        String banner = this.pal.banners[Math.floorMod(idx, this.pal.banners.length)];
                        out.set(x, this.galleryTop - 1, z, "minecraft:" + banner + "[facing=" + opposite(outward) + "]");
                    }
                }
            }
        }
    }

    private void vipBox(int x, int z, int rx, int rz, int row, ColosseumDesign.Sink out) {
        int base = this.floorY + this.wallH + this.rows - 5;

        for (int y = this.floorY + 1; y < base; y++) {
            out.set(x, y, z, b(this.pal.stand));
        }

        boolean front = row == this.rows - 5;
        boolean side = Math.abs(rx) == 4;
        out.set(x, base, z, b(this.pal.walkway));
        if (!side) {
            out.set(x, base + 1, z, b(front ? this.pal.railing : this.pal.carpet));
        }

        int roofY = this.galleryTop - 2;
        if (side && (front || row == this.rows - 1)) {
            for (int y = base + 1; y < roofY; y++) {
                out.set(x, y, z, b(this.pal.pillar));
            }
        } else if (side) {
            out.set(x, base + 1, z, b(this.pal.railing));
        }

        out.set(x, roofY, z, b(this.pal.gallery2));
        out.set(x, roofY + 1, z, front ? stairs(this.pal.roofStairs, "north", false) : b(this.pal.roof));
        if (front && Math.abs(rx) == 2) {
            out.set(x, roofY - 1, z, this.hanging());
        }
    }

    private double pillarSpacing() {
        return this.grand ? 7.0 : 5.0;
    }

    private boolean inWaterfall(int rx, int rz) {
        return rz > 0 && rx >= -this.straight - 1 && rx <= -this.straight + 3;
    }

    private void waterfall(int x, int z, int rx, int rz, int row, int top, ColosseumDesign.Sink out) {
        if (this.inWaterfall(rx, rz)) {
            boolean edge = rx == -this.straight - 1 || rx == -this.straight + 3;
            int basin = this.floorY + this.wallH;
            if (edge) {
                for (int y = top; y <= this.galleryTop; y++) {
                    out.set(x, y, z, b(this.hash(x, y, z) < 350 ? "mossy_stone_bricks" : "stone_bricks"));
                }

                if (this.hash(x, 9, z) < 600) {
                    out.set(x, this.galleryTop + 1, z, leaves("oak_leaves"));
                }
            } else if (row == 0) {
                out.set(x, top, z, b("mossy_stone_bricks"));
            } else {
                for (int y = basin - 1; y < basin; y++) {
                    out.set(x, y, z, b("stone_bricks"));
                }

                for (int y = basin + 1; y <= top; y++) {
                    out.set(x, y, z, "minecraft:air");
                }

                out.set(x, basin, z, "minecraft:water[level=0]");
                if (row == this.rows - 1) {
                    for (int y = basin + 1; y < this.galleryTop; y++) {
                        out.set(x, y, z, "minecraft:water[level=8]");
                    }

                    out.set(x, this.galleryTop, z, "minecraft:water[level=0]");
                } else if (row == 1 && this.hash(x, 4, z) < 400) {
                    out.set(x, basin + 1, z, "minecraft:lily_pad");
                }
            }
        }
    }

    private void gallery(int x, int z, int rx, int rz, double d, ColosseumDesign.Sink out) {
        int layer = (int)Math.floor(d - 1.0 - this.rows);
        String outward = this.outward(rx, rz);
        double ring = this.radius + 1 + this.rows + layer + 0.5;
        double ph = this.phase(rx, rz, ring, this.pillarSpacing());
        double sp = this.spacingOn(ring, this.pillarSpacing());
        boolean pillar = ph < 1.2;
        boolean inner = layer == 0;
        boolean outerFace = layer == this.galleryD - 1;
        int floorG = this.lastRowTop;
        if (this.style == ColosseumDesign.Style.TEMPLE && this.inWaterfall(rx, rz) && inner) {
            for (int y = this.floorY + 1; y <= this.galleryTop + 1; y++) {
                out.set(x, y, z, b(this.hash(x, y, z) < 450 ? "mossy_stone_bricks" : "stone_bricks"));
            }

            this.plants(x, z, this.galleryTop + 2, out, 950);
        } else {
            for (int y = this.floorY + 1; y <= floorG; y++) {
                out.set(x, y, z, b(this.hash(x, y, z) < 150 ? this.pal.stand2 : this.pal.stand));
            }

            double mid = Math.abs(ph - sp / 2.0) / Math.max(0.5, sp / 2.0 - 0.6);
            if (!inner && !outerFace) {
                out.set(x, this.galleryTop, z, b(this.pal.gallery2));
                out.set(x, floorG + this.archH + 1, z, b(this.pal.gallery2));
            } else {
                for (int y = floorG + 1; y <= this.galleryTop; y++) {
                    int h = y - floorG;
                    boolean band = h == this.archH + 1 || h == this.galleryH;
                    String blk = band ? this.pal.gallery2 : this.pal.gallery;
                    boolean opening = false;
                    if (pillar) {
                        blk = band ? this.pal.gallery2 : this.pal.pillar;
                    } else {
                        double archTop = this.archH - mid * mid * 1.6;
                        if (h >= 1 && h <= archTop) {
                            opening = true;
                        }

                        int upper = this.archH + 3;
                        if (h >= upper && h <= upper + (this.grand ? 3 : 2) - (mid > 0.5 ? 1 : 0) && mid < 0.62) {
                            opening = true;
                        }
                    }

                    if (opening) {
                        if (inner && h == 1) {
                            out.set(x, y, z, b(this.pal.railing));
                        } else if (outerFace && h <= 2) {
                            out.set(x, y, z, b(this.pal.railing));
                        }
                    } else {
                        if (this.style == ColosseumDesign.Style.TEMPLE && blk.equals(this.pal.gallery) && this.hash(x, y, z) < 160) {
                            blk = "mossy_stone_bricks";
                        }

                        out.set(x, y, z, b(blk));
                    }
                }

                if (inner && !pillar && mid < 0.3) {
                    out.set(x, floorG + this.archH, z, this.hanging());
                }
            }

            int roofY = this.galleryTop + 1;
            if (inner) {
                out.set(x, roofY, z, stairs(this.pal.cornice, outward, true));
                if (pillar) {
                    out.set(x, roofY + 1, z, b(this.pal.pillar));
                    out.set(x, roofY + 2, z, b(this.pal.light));
                } else if (this.style == ColosseumDesign.Style.FROST) {
                    out.set(x, roofY + 1, z, "minecraft:snow[layers=3]");
                }
            } else if (outerFace) {
                out.set(x, roofY, z, b(this.pal.gallery));
                if (Math.floorMod((int)Math.floor(ph * 2.0), 2) == 0 && !this.grand) {
                    out.set(x, roofY + 1, z, b(this.pal.gallery2));
                }
            } else if (this.style == ColosseumDesign.Style.SPIRES) {
                out.set(x, roofY, z, b(this.pal.roof));
            } else {
                int rise = layer;

                for (int y = roofY; y < roofY + rise; y++) {
                    out.set(x, y, z, b(this.pal.roof));
                }

                out.set(x, roofY + rise, z, stairs(this.pal.roofStairs, outward, false));
                if (this.style == ColosseumDesign.Style.FROST) {
                    out.set(x, roofY + rise + 1, z, "minecraft:snow[layers=2]");
                }
            }

            if ((this.style == ColosseumDesign.Style.TEMPLE || this.style == ColosseumDesign.Style.CHERRY) && (inner || outerFace) && this.hash(x, 77, z) < 380
                )
             {
                this.plants(x, z, roofY + (inner && pillar ? 3 : (outerFace ? 2 : 1)), out, 700);
            }

            if (this.style == ColosseumDesign.Style.TEMPLE && inner && !pillar && this.hash(x, 31, z) < 260) {
                int len = 2 + this.hash(x, 32, z) % 4;

                for (int y = this.galleryTop - len; y < this.galleryTop; y++) {
                    out.set(x + stepX(opposite(outward)), y, z + stepZ(opposite(outward)), "minecraft:vine[" + outward + "=true]");
                }
            }

            if (this.style == ColosseumDesign.Style.NETHER && inner && !pillar && mid < 0.3) {
                for (int y = floorG + this.archH - 2; y < floorG + this.archH; y++) {
                    out.set(x, y, z, b("iron_chain"));
                }
            }

            if (this.style == ColosseumDesign.Style.SPIRES && outerFace) {
                this.spire(x, z, rx, rz, roofY + 1, out);
            }

            if (this.style == ColosseumDesign.Style.CHERRY && outerFace) {
                this.cherryTree(x, z, rx, rz, roofY + 1, out);
            }

            if (this.grand && outerFace) {
                this.grandTower(x, z, rx, rz, roofY + 1, out);
            }
        }
    }

    private void plants(int x, int z, int y, ColosseumDesign.Sink out, int chance) {
        if (this.pal.foliage != null && this.hash(x, y, z) <= chance) {
            out.set(x, y, z, leaves(this.pal.foliage));
            if (this.hash(x, y + 1, z) < 420) {
                out.set(
                    x, y + 1, z, leaves(this.style == ColosseumDesign.Style.TEMPLE && this.hash(x, 5, z) < 300 ? "flowering_azalea_leaves" : this.pal.foliage)
                );
            }
        }
    }

    private void spire(int x, int z, int rx, int rz, int base, ColosseumDesign.Sink out) {
        double ring = this.radius + this.outer;
        double sp = this.spacingOn(ring, 8.0);
        double local = this.along(rx, rz, ring) % sp;
        if (local < 0.0) {
            local += sp;
        }

        if (!(local > 4.0)) {
            int idx = this.index(rx, rz, ring, 8.0);
            int height = 8 + (int)(this.rnd(idx, 13, 77) * 22.0);
            String[] mats = new String[]{
                "tuff", "deepslate_tiles", "polished_deepslate", "calcite", "terracotta", "tuff_bricks", "deepslate_bricks", "smooth_basalt"
            };
            String m = mats[Math.floorMod(idx * 3 + 1, mats.length)];
            double taper = Math.abs(local - 2.0) / 2.0;
            int h = (int)Math.round(height * (1.0 - taper * 0.6));

            for (int y = base; y < base + h; y++) {
                boolean band = (y - base) % 7 == 6;
                out.set(x, y, z, b(band ? "red_terracotta" : (this.hash(x, y, z) < 180 ? "polished_tuff" : m)));
            }
        }
    }

    private void cherryTree(int x, int z, int rx, int rz, int base, ColosseumDesign.Sink out) {
        double ring = this.radius + this.outer - 0.5;
        double sp = this.spacingOn(ring, 17.0);
        double s = this.along(rx, rz, ring);
        double local = s % sp;
        if (local < 0.0) {
            local += sp;
        }

        double dl = Math.abs(local - sp / 2.0);
        if (!(dl > 4.5)) {
            if (dl < 0.6) {
                for (int y = base; y < base + 4; y++) {
                    out.set(x, y, z, b("cherry_log"));
                }
            }

            for (int y = base + 3; y <= base + 6; y++) {
                double rr = y >= base + 6 ? 2.0 : 4.0;
                if (dl <= rr && this.hash(x, y, z) < 920 && (!(dl < 0.6) || y >= base + 4)) {
                    out.set(x, y, z, leaves("cherry_leaves"));
                }
            }
        }
    }

    private void grandTower(int x, int z, int rx, int rz, int base, ColosseumDesign.Sink out) {
        double ring = this.radius + this.outer - 2;
        double sp = this.spacingOn(ring, 24.0);
        double local = this.along(rx, rz, ring) % sp;
        if (local < 0.0) {
            local += sp;
        }

        if (!(local > 5.0)) {
            int dx = (int)Math.round(local) - 2;
            int towerH = 10;

            for (int y = base; y < base + towerH; y++) {
                int h = y - base;
                boolean window = h % 4 == 2 && dx == 0;
                out.set(x, y, z, b(window ? "dark_oak_trapdoor" : (h % 4 == 3 ? this.pal.gallery2 : this.pal.tower)));
            }

            int roofH = 3 - Math.abs(dx);

            for (int y = 0; y <= Math.max(0, roofH); y++) {
                out.set(x, base + towerH + y, z, b(this.pal.towerRoof));
            }

            if (dx == 0) {
                out.set(x, base + towerH + roofH + 1, z, b("spruce_fence"));
                out.set(x, base + towerH + roofH + 2, z, b("spruce_fence"));
                out.set(x, base + towerH + roofH + 3, z, b(this.pal.flag));
            }
        }
    }

    private boolean gate(int x, int z, int rx, int rz, double d, ColosseumDesign.Sink out) {
        if (d < -0.5 || d >= this.outer + 4 || Math.abs(rz) > this.gateHalf + 8) {
            return false;
        } else if (this.style == ColosseumDesign.Style.GLADIATOR && rx < 0) {
            return false;
        } else {
            int end = this.straight + this.radius;
            int ax = Math.abs(rx);
            if (ax < end - 2) {
                return false;
            } else {
                String outward = rx > 0 ? "east" : "west";
                int toward = rx > 0 ? -1 : 1;
                int dz = Math.abs(rz);
                int ring = ax - end;
                int tz = this.gateHalf + 4;
                int tx = end + this.outer / 2 + 1;
                if (dz >= this.gateHalf + 1 && dz <= this.gateHalf + 7 && Math.abs(ax - tx) <= 3) {
                    this.tower(x, z, ax - tx, dz - tz, rx, rz, out);
                    return true;
                } else if (dz <= this.gateHalf && !(d < 0.0)) {
                    this.ground(x, z, this.floorY, out);
                    int gateTop = this.floorY + (this.grand ? 8 : 6);
                    double mid = (double)dz / this.gateHalf;
                    int archTop = gateTop - (int)Math.round(mid * mid * 2.0);

                    for (int y = this.floorY + 1; y <= this.galleryTop + 2; y++) {
                        if (y <= archTop) {
                            if (ring == 0) {
                                String g = this.pal.gate.equals("iron_bars")
                                    ? "iron_bars"
                                    : (dz == this.gateHalf ? "dark_oak_log" : ((y - this.floorY) % 3 == 0 ? "dark_oak_planks" : this.pal.gate));
                                out.set(x, y, z, b(g));
                            } else if (ring == 1 && barriers()) {
                                out.set(x, y, z, b("barrier"));
                            }
                        } else {
                            String blk = y == archTop + 1
                                ? this.pal.pillar
                                : (y >= this.galleryTop ? this.pal.towerTop : ((y - this.floorY) % 6 == 0 ? this.pal.towerBand : this.pal.tower));
                            if (this.style == ColosseumDesign.Style.TEMPLE && this.hash(x, y, z) < 200) {
                                blk = "mossy_stone_bricks";
                            }

                            out.set(x, y, z, b(blk));
                        }
                    }

                    if (ring == 0) {
                        if (dz == 0) {
                            out.set(
                                x + toward, archTop + 3, z, "minecraft:" + this.pal.banners[1 % this.pal.banners.length] + "[facing=" + opposite(outward) + "]"
                            );
                        }

                        for (int yx = this.galleryTop + 3; yx <= this.floorY + this.barrierH && barriers(); yx++) {
                            out.set(x, yx, z, b("barrier"));
                        }
                    }

                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    private void tower(int x, int z, int lx, int lz, int rx, int rz, ColosseumDesign.Sink out) {
        this.ground(x, z, this.floorY, out);
        boolean wallCell = Math.abs(lx) == 3 || Math.abs(lz) == 3;
        boolean corner = Math.abs(lx) == 3 && Math.abs(lz) == 3;

        for (int y = this.floorY + 1; y <= this.towerTop; y++) {
            int h = y - this.floorY;
            if (!wallCell) {
                if (h % 8 == 0 || y == this.towerTop) {
                    out.set(x, y, z, b(this.pal.towerTop));
                }
            } else {
                String blk = corner ? this.pal.pillar : (h % 6 == 0 ? this.pal.towerBand : this.pal.tower);
                boolean window = !corner && (lx == 0 || lz == 0) && h % 6 >= 2 && h % 6 <= 3 && h > this.wallH + 4;
                if (window) {
                    out.set(x, y, z, b(this.pal.gate.equals("iron_bars") ? "iron_bars" : "dark_oak_trapdoor"));
                } else {
                    if (this.style == ColosseumDesign.Style.TEMPLE && this.hash(x, y, z) < 240) {
                        blk = this.hash(x, y + 1, z) < 500 ? "mossy_stone_bricks" : "cracked_stone_bricks";
                    }

                    out.set(x, y, z, b(blk));
                }
            }
        }

        int top = this.towerTop + 1;
        if (wallCell && (corner || (lx + lz & 1) == 0)) {
            out.set(x, top, z, b(this.pal.towerTop));
        }

        int maxL = Math.max(Math.abs(lx), Math.abs(lz));
        switch (this.style) {
            case TEMPLE: {
                    if (maxL <= 2) {
                        out.set(x, top, z, b("moss_block"));
                        this.plants(x, z, top + 1, out, 960);
                        if (maxL <= 1) {
                            this.plants(x, z, top + 2, out, 850);
                        }
                    }
                    break;
            }
            case SPIRES: {
                    int sh = (3 - maxL) * 6 + 2;

                    for (int yx = top; yx < top + sh; yx++) {
                        out.set(x, yx, z, b(yx >= top + sh - 2 && maxL == 0 ? "red_terracotta" : ((yx - top) % 5 == 4 ? "red_terracotta" : "polished_deepslate")));
                    }
                    break;
            }
            case NETHER:
            case FROST:
            default: {
                    int rh = 4 - maxL;

                    for (int yx = top; yx < top + rh; yx++) {
                        out.set(x, yx, z, b(this.pal.towerRoof));
                    }

                    if (maxL == 0) {
                        boolean nether = this.style == ColosseumDesign.Style.NETHER;
                        out.set(x, top + rh, z, b(nether ? "polished_blackstone_wall" : "spruce_fence"));
                        out.set(x, top + rh + 1, z, b(nether ? "polished_blackstone_wall" : "spruce_fence"));
                        out.set(x, top + rh + 2, z, b(nether ? "soul_lantern" : this.pal.flag));
                    }

                    if (this.style == ColosseumDesign.Style.FROST && maxL <= 3) {
                        out.set(x, top + rh, z, "minecraft:snow[layers=2]");
                    }
                    break;
            }
            case CHERRY: {
                    if (maxL <= 3) {
                        int rh = 3 - Math.max(0, maxL - 1);

                        for (int yx = top; yx < top + rh; yx++) {
                            out.set(x, yx, z, leaves("cherry_leaves"));
                        }
                    }
            }
        }
    }

    private int hillTop(int rx, int rz) {
        double d = this.sd(rx, rz);
        double t = (d - this.outer) / this.landscape;
        if (!(t < 0.0) && !(t > 1.05)) {
            double n = this.noise(rx * 0.06, rz * 0.06, 21) * 0.6 + this.noise(rx * 0.15, rz * 0.15, 22) * 0.4;
            double rise = Math.sin(Math.min(1.0, t * 1.25) * Math.PI) * (20.0 + n * 12.0) + 7.0 + n * 5.0;
            if (t > 0.8) {
                rise *= Math.max(0.0, (1.05 - t) / 0.25);
            }

            int top = this.floorY + (int)Math.round(rise);
            return top <= this.floorY - 3 ? Integer.MIN_VALUE : top;
        } else {
            return Integer.MIN_VALUE;
        }
    }

    private boolean cliff(int rx, int rz) {
        return Math.abs(this.noise(rx * 0.18, rz * 0.18, 23)) > 0.42;
    }

    private void hills(int x, int z, int rx, int rz, double d, ColosseumDesign.Sink out) {
        int top = this.hillTop(rx, rz);
        if (top != Integer.MIN_VALUE) {
            double rise = top - this.floorY;
            int bottom = this.floorY - 8 - (int)Math.round(rise * 0.35);
            boolean cliff = this.cliff(rx, rz);

            int lowest = Math.max(this.bottomY, bottom);

            for (int y = lowest; y <= top; y++) {
                int below = top - y;
                String blk;
                if (y == lowest && this.bedrock && below > 0) {
                    // the hills end on bedrock too: digging through them never reaches the void
                    blk = "bedrock";
                } else if (below == 0) {
                    blk = cliff ? (this.hash(x, y, z) < 500 ? "stone" : "andesite") : "grass_block";
                } else if (below < 3 && !cliff) {
                    blk = "dirt";
                } else {
                    int h = this.hash(x, y, z);
                    blk = h < 120 ? "andesite" : (h < 170 ? "cobblestone" : (h < 200 ? "mossy_cobblestone" : "stone"));
                }

                out.set(x, y, z, b(blk));
            }

            if (!cliff && this.hash(x, 92, z) < 60) {
                out.set(x, top + 1, z, b("short_grass"));
            }

            this.spruces(x, z, rx, rz, out);
        }
    }

    private void spruces(int x, int z, int rx, int rz, ColosseumDesign.Sink out) {
        int gx = Math.floorDiv(rx, 8);
        int gz = Math.floorDiv(rz, 8);

        for (int ix = gx - 1; ix <= gx + 1; ix++) {
            for (int iz = gz - 1; iz <= gz + 1; iz++) {
                if (this.hash(ix, 55, iz) <= 520) {
                    int tx = ix * 8 + 1 + this.hash(ix, 56, iz) % 6;
                    int tz = iz * 8 + 1 + this.hash(ix, 57, iz) % 6;
                    double t = (this.sd(tx, tz) - this.outer) / this.landscape;
                    if (!(t < 0.12) && !(t > 0.85) && !this.cliff(tx, tz)) {
                        int ground = this.hillTop(tx, tz);
                        if (ground != Integer.MIN_VALUE) {
                            int dx = Math.abs(rx - tx);
                            int dz = Math.abs(rz - tz);
                            if (dx <= 3 && dz <= 3) {
                                int h = 8 + this.hash(ix, 58, iz) % 6;
                                int base = ground + 1;
                                if (dx == 0 && dz == 0) {
                                    for (int y = base; y < base + h - 1; y++) {
                                        out.set(x, y, z, b("spruce_log"));
                                    }
                                }

                                for (int y = base + 2; y <= base + h; y++) {
                                    int fromTop = base + h - y;
                                    int r = fromTop == 0 ? 0 : (fromTop % 2 == 1 ? Math.min(3, 1 + fromTop / 3) : Math.min(2, fromTop / 3));
                                    double dd = Math.sqrt(dx * dx + dz * dz);
                                    if (dd <= r + 0.3 && (dx != 0 || dz != 0 || y >= base + h - 1)) {
                                        out.set(x, y, z, leaves("spruce_leaves"));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void spruce(int x, int z, int base, ColosseumDesign.Sink out) {
        int h = 6 + this.hash(x, 3, z) % 4;

        for (int y = base; y < base + h; y++) {
            out.set(x, y, z, b("spruce_log"));
        }

        out.set(x, base + h, z, leaves("spruce_leaves"));
        out.set(x, base + h + 1, z, leaves("spruce_leaves"));
    }

    private void rocks(int x, int z, int rx, int rz, ColosseumDesign.Sink out) {
        for (int i = 0; i < this.rockX.length; i++) {
            double dd = dist(rx, rz, this.rockX[i], this.rockZ[i]);
            double rr = this.rockR[i];
            if (!(dd > rr + 4.0)) {
                int baseY = this.floorY + 6;
                int topY2 = this.floorY + this.rockH[i];

                for (int y = baseY; y <= topY2; y++) {
                    double f = (double)(y - baseY) / (topY2 - baseY);
                    double w = rr * (0.45 + 0.55 * Math.sin(f * Math.PI * 0.9 + 0.15))
                        + (f > 0.85 ? 3.0 * (f - 0.85) / 0.15 : 0.0)
                        + this.noise(x * 0.3 + y * 0.2, z * 0.3, 30 + i) * 1.3;
                    if (dd <= w) {
                        boolean topLayer = y == topY2 || dd > w - 1.2 && y >= topY2 - 1;
                        out.set(
                            x,
                            y,
                            z,
                            b(topLayer ? "grass_block" : (this.hash(x, y, z) < 160 ? "andesite" : (this.hash(x, y, z) < 220 ? "mossy_cobblestone" : "stone")))
                        );
                    }
                }

                if (dd < 1.0 && this.hash(i, 1, 1) < 700) {
                    this.spruce(x, z, topY2 + 1, out);
                }
            }
        }
    }

    private static final class Palette {
        String[] floor;
        String floorLine;
        String floorLine2;
        String floorCenter;
        String floorCenter2;
        String floorBorder;
        String pad;
        String wall;
        String wallPanel;
        String wallBand;
        String wallPillar;
        String wallTrim;
        String stand;
        String stand2;
        String seat;
        String seat2;
        String walkway;
        String aisle;
        String gallery;
        String gallery2;
        String pillar;
        String railing;
        String cornice;
        String roof;
        String roofStairs;
        String[] banners;
        String light;
        String tower;
        String towerBand;
        String towerTop;
        String towerRoof;
        String flag;
        String foliage;
        String ground;
        String gate;
        String carpet;
    }

    public interface Sink {
        void set(int var1, int var2, int var3, String var4);
    }

    public static enum Style {
        DESERT("Sandstone Colosseum"),
        TEMPLE("Jungle Temple"),
        SPIRES("Dark Spires"),
        NETHER("Inferno Colosseum"),
        FROST("Frozen Colosseum"),
        CHERRY("Cherry Blossom Colosseum"),
        GLADIATOR("Gladiator Colosseum"),
        GRAND("Grand Colosseum"),
        LOBBY("Grand Lobby"),
        CHAMPIONS("Champions Colosseum");

        public final String display;

        private Style(String display) {
            this.display = display;
        }
    }
}
