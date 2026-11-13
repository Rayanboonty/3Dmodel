package net.vortexrealm.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TownDesign {
    private static final byte OUT = 0;
    private static final byte GRASS = 1;
    private static final byte ROAD = 2;
    private static final byte PLAZA = 3;
    private static final byte BUILD = 4;
    private static final byte RESV = 5;
    private static final byte PATH = 6;
    private static final byte WATER = 7;
    private static final byte ROCK = 8;
    public final int cx;
    public final int floorY;
    public final int cz;
    public final int radius;
    public final int ext;
    public final int down;
    public final int up;
    public final TownDesign.Theme theme;
    private final TownDesign.Pal pal;
    private final long seed;
    private final int span;
    private final int height;
    private final short[] grid;
    private final byte[] plan;
    private final byte[] top;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> ids = new HashMap<>();
    private final short air;
    private final List<double[]> npcs = new ArrayList<>();
    private double[] spawn;
    private final int plazaR;
    private final int ringR;
    private final int townR;
    private final int rh;
    private int pavX0;
    private int pavX1;
    private int pavZ0;
    private int pavZ1;
    private int archX;
    private int[] fall;
    private final List<double[]> spires = new ArrayList<>();
    private int houses;
    private static final String[] FACE = new String[]{"north", "east", "south", "west"};
    private static final int[] FX = new int[]{0, 1, 0, -1};
    private static final int[] FZ = new int[]{-1, 0, 1, 0};

    private static TownDesign.Pal pal(TownDesign.Theme t) {
        TownDesign.Pal p = new TownDesign.Pal();
        switch (t) {
            case DESERT:
                p.surface = "sand";
                p.sub = "sandstone";
                p.rock = new String[]{
                    "sandstone",
                    "sandstone",
                    "sandstone",
                    "sandstone",
                    "smooth_sandstone",
                    "red_sandstone",
                    "red_sandstone",
                    "terracotta",
                    "orange_terracotta",
                    "yellow_terracotta"
                };
                p.band = "cut_red_sandstone";
                p.paving = new String[]{
                    "smooth_sandstone",
                    "smooth_sandstone",
                    "smooth_sandstone",
                    "sandstone",
                    "sandstone",
                    "cut_sandstone",
                    "cut_sandstone",
                    "chiseled_sandstone",
                    "sand"
                };
                p.patch = "cut_red_sandstone";
                p.curb = "cut_sandstone";
                p.curb2 = "chiseled_sandstone";
                p.plazaLine = "orange_terracotta";
                p.plazaRadial = "cut_red_sandstone";
                p.plazaBorder = "chiseled_sandstone";
                p.plazaFill = new String[]{"smooth_sandstone", "smooth_sandstone", "cut_sandstone", "sandstone"};
                p.plasters = new String[]{"smooth_sandstone", "smooth_sandstone", "smooth_sandstone", "sandstone", "white_terracotta", "terracotta"};
                p.frames = new String[]{"stripped_jungle_log", "cut_sandstone", "cut_sandstone"};
                p.bases = new String[]{"sandstone", "cut_sandstone"};
                p.roofs = new String[][]{
                    {"smooth_sandstone_stairs", "smooth_sandstone", "smooth_sandstone_slab"},
                    {"sandstone_stairs", "sandstone", "sandstone_slab"},
                    {"red_sandstone_stairs", "red_sandstone", "red_sandstone_slab"}
                };
                p.roof = TownDesign.Roof.FLAT;
                p.parapet = "sandstone_wall";
                p.banners = new String[]{"orange_wall_banner", "red_wall_banner", "yellow_wall_banner", "white_wall_banner"};
                p.door = "jungle_door";
                p.interior = "jungle_planks";
                p.trees = TownDesign.Trees.PALM;
                p.monument = TownDesign.Monument.OBELISK;
                p.sky = TownDesign.Sky.BALLOONS;
                p.hedges = new String[0];
                p.plants = new String[]{"dead_bush", "dead_bush", "dead_bush", "cactus", "short_dry_grass"};
                p.lampBase = "sandstone_wall";
                p.lampPost = "sandstone_wall";
                p.pillar = "cut_sandstone";
                p.pavRoof = new String[]{"sandstone_stairs", "sandstone", "sandstone_slab"};
                p.pavFloor = "smooth_sandstone";
                p.pavEdge = "cut_sandstone";
                p.pavFence = "sandstone_wall";
                p.pavBeam = "cut_red_sandstone";
                p.pondFloor = "sand";
                p.pondEdge1 = "cut_sandstone";
                p.pondEdge2 = "sandstone";
                p.bench = "sandstone_stairs";
                p.plant = "potted_cactus";
                p.pedestal = "chiseled_sandstone";
                p.pedestalTop = "cut_sandstone";
                p.wood = "jungle";
                break;
            case SAKURA:
                p.rock = new String[]{"stone", "stone", "stone", "stone", "andesite", "andesite", "calcite", "calcite", "diorite", "tuff", "mossy_cobblestone"};
                p.band = "calcite";
                p.paving = new String[]{
                    "stone_bricks",
                    "stone_bricks",
                    "stone_bricks",
                    "mossy_stone_bricks",
                    "mossy_stone_bricks",
                    "polished_andesite",
                    "polished_andesite",
                    "gravel",
                    "cobblestone"
                };
                p.patch = "tuff_bricks";
                p.curb = "mossy_stone_bricks";
                p.curb2 = "stone_bricks";
                p.plazaLine = "polished_andesite";
                p.plazaRadial = "mossy_stone_bricks";
                p.plazaBorder = "stone_bricks";
                p.plazaFill = new String[]{"stone_bricks", "stone_bricks", "polished_andesite", "gravel", "andesite"};
                p.plasters = new String[]{"calcite", "calcite", "calcite", "white_concrete", "cherry_planks"};
                p.frames = new String[]{"dark_oak_log", "dark_oak_log", "stripped_dark_oak_log", "cherry_log"};
                p.bases = new String[]{"stone_bricks", "mossy_stone_bricks"};
                p.roofs = new String[][]{
                    {"deepslate_tile_stairs", "deepslate_tiles", "deepslate_tile_slab"},
                    {"dark_oak_stairs", "dark_oak_planks", "dark_oak_slab"},
                    {"cherry_stairs", "cherry_planks", "cherry_slab"}
                };
                p.roof = TownDesign.Roof.PAGODA;
                p.banners = new String[]{"red_wall_banner", "pink_wall_banner", "white_wall_banner"};
                p.door = "cherry_door";
                p.interior = "cherry_planks";
                p.trees = TownDesign.Trees.CHERRY;
                p.monument = TownDesign.Monument.PAGODA;
                p.sky = TownDesign.Sky.LANTERNS;
                p.hedges = new String[]{"cherry_leaves", "cherry_leaves", "azalea_leaves", "flowering_azalea_leaves"};
                p.plants = new String[]{
                    "pink_petals[facing=north,flower_amount=4]",
                    "pink_petals[facing=east,flower_amount=3]",
                    "short_grass",
                    "short_grass",
                    "lily_of_the_valley",
                    "azure_bluet"
                };
                p.lampBase = "andesite_wall";
                p.lampPost = "andesite_wall";
                p.pillar = "stripped_crimson_stem";
                p.pavRoof = new String[]{"dark_oak_stairs", "dark_oak_planks", "dark_oak_slab"};
                p.pavFloor = "cherry_planks";
                p.pavEdge = "stripped_crimson_stem";
                p.pavFence = "cherry_fence";
                p.pavBeam = "stripped_crimson_stem";
                p.bench = "cherry_stairs";
                p.plant = "potted_cherry_sapling";
                p.wood = "cherry";
                break;
            case NETHER:
                p.surface = "crimson_nylium";
                p.sub = "netherrack";
                p.rock = new String[]{
                    "netherrack",
                    "netherrack",
                    "netherrack",
                    "netherrack",
                    "blackstone",
                    "blackstone",
                    "basalt[axis=y]",
                    "smooth_basalt",
                    "magma_block",
                    "nether_bricks"
                };
                p.band = "blackstone";
                p.paving = new String[]{
                    "polished_blackstone_bricks",
                    "polished_blackstone_bricks",
                    "polished_blackstone_bricks",
                    "blackstone",
                    "polished_blackstone",
                    "nether_bricks",
                    "cracked_polished_blackstone_bricks",
                    "polished_basalt[axis=y]"
                };
                p.patch = "red_nether_bricks";
                p.curb = "chiseled_polished_blackstone";
                p.curb2 = "polished_blackstone";
                p.plazaLine = "red_nether_bricks";
                p.plazaRadial = "chiseled_nether_bricks";
                p.plazaBorder = "polished_blackstone_bricks";
                p.plazaFill = new String[]{"polished_blackstone_bricks", "polished_blackstone_bricks", "blackstone", "nether_bricks"};
                p.plasters = new String[]{"crimson_planks", "crimson_planks", "warped_planks", "nether_bricks", "red_nether_bricks"};
                p.frames = new String[]{"blackstone", "polished_basalt", "polished_basalt", "crimson_stem"};
                p.bases = new String[]{"blackstone", "polished_blackstone_bricks"};
                p.roofs = new String[][]{
                    {"nether_brick_stairs", "nether_bricks", "nether_brick_slab"},
                    {"red_nether_brick_stairs", "red_nether_bricks", "red_nether_brick_slab"},
                    {"blackstone_stairs", "blackstone", "blackstone_slab"}
                };
                p.banners = new String[]{"red_wall_banner", "black_wall_banner", "orange_wall_banner"};
                p.door = "crimson_door";
                p.interior = "crimson_planks";
                p.liquid = "lava";
                p.trees = TownDesign.Trees.FUNGUS;
                p.monument = TownDesign.Monument.PORTAL;
                p.sky = TownDesign.Sky.FUNGI;
                p.hedges = new String[]{"nether_wart_block", "nether_wart_block", "warped_wart_block"};
                p.plants = new String[]{"crimson_roots", "crimson_roots", "warped_roots", "crimson_fungus", "nether_sprouts"};
                p.lampBase = "polished_blackstone_wall";
                p.lampPost = "polished_blackstone_wall";
                p.lampLight = "soul_lantern[hanging=false,waterlogged=false]";
                p.hanging = "soul_lantern[hanging=true,waterlogged=false]";
                p.pillar = "crimson_stem";
                p.pavRoof = new String[]{"blackstone_stairs", "blackstone", "blackstone_slab"};
                p.pavFloor = "crimson_planks";
                p.pavEdge = "stripped_crimson_stem";
                p.pavFence = "nether_brick_fence";
                p.pavBeam = "polished_basalt";
                p.pondFloor = "magma_block";
                p.pondEdge1 = "blackstone";
                p.pondEdge2 = "basalt[axis=y]";
                p.bench = "crimson_stairs";
                p.plant = "potted_crimson_fungus";
                p.pedestal = "chiseled_polished_blackstone";
                p.pedestalTop = "polished_blackstone";
                p.wood = "crimson";
                break;
            case WINTER:
                p.surface = "snow_block";
                p.rock = new String[]{
                    "stone", "stone", "stone", "stone", "packed_ice", "packed_ice", "snow_block", "snow_block", "diorite", "calcite", "blue_ice"
                };
                p.band = "packed_ice";
                p.paving = new String[]{
                    "stone_bricks",
                    "stone_bricks",
                    "polished_diorite",
                    "polished_diorite",
                    "polished_diorite",
                    "snow_block",
                    "snow_block",
                    "packed_ice",
                    "cobblestone"
                };
                p.patch = "blue_ice";
                p.curb = "polished_diorite";
                p.curb2 = "stone_bricks";
                p.plazaLine = "packed_ice";
                p.plazaRadial = "blue_ice";
                p.plazaBorder = "polished_diorite";
                p.plazaFill = new String[]{"snow_block", "snow_block", "polished_diorite", "polished_diorite", "stone_bricks"};
                p.plasters = new String[]{"spruce_planks", "spruce_planks", "spruce_planks", "birch_planks", "white_concrete"};
                p.frames = new String[]{"spruce_log", "spruce_log", "dark_oak_log"};
                p.bases = new String[]{"cobblestone", "stone_bricks"};
                p.roofs = new String[][]{
                    {"smooth_quartz_stairs", "smooth_quartz", "smooth_quartz_slab"},
                    {"diorite_stairs", "diorite", "diorite_slab"},
                    {"smooth_quartz_stairs", "smooth_quartz", "smooth_quartz_slab"}
                };
                p.banners = new String[]{"light_blue_wall_banner", "white_wall_banner", "blue_wall_banner"};
                p.door = "spruce_door";
                p.iceFalls = true;
                p.trees = TownDesign.Trees.SNOWY;
                p.monument = TownDesign.Monument.XMAS;
                p.sky = TownDesign.Sky.AIRSHIPS;
                p.hedges = new String[]{"spruce_leaves"};
                p.plants = new String[0];
                p.snow = true;
                p.pondFloor = "packed_ice";
                p.pondEdge1 = "snow_block";
                p.pondEdge2 = "packed_ice";
                break;
            case CELESTIAL:
                p.surface = "end_stone";
                p.sub = "end_stone";
                p.rock = new String[]{
                    "end_stone",
                    "end_stone",
                    "end_stone",
                    "end_stone",
                    "end_stone",
                    "end_stone",
                    "end_stone_bricks",
                    "end_stone_bricks",
                    "calcite",
                    "calcite",
                    "purpur_block"
                };
                p.band = "end_stone_bricks";
                p.paving = new String[]{
                    "end_stone_bricks",
                    "end_stone_bricks",
                    "end_stone_bricks",
                    "end_stone",
                    "purpur_block",
                    "quartz_bricks",
                    "quartz_bricks",
                    "polished_diorite",
                    "smooth_quartz"
                };
                p.patch = "purpur_block";
                p.curb = "purpur_block";
                p.curb2 = "quartz_bricks";
                p.plazaLine = "purpur_block";
                p.plazaRadial = "quartz_bricks";
                p.plazaBorder = "purpur_block";
                p.plazaFill = new String[]{"end_stone_bricks", "end_stone_bricks", "smooth_quartz", "polished_diorite"};
                p.plasters = new String[]{"smooth_quartz", "smooth_quartz", "quartz_bricks", "end_stone_bricks", "purpur_block"};
                p.frames = new String[]{"purpur_pillar", "purpur_pillar", "quartz_pillar"};
                p.bases = new String[]{"end_stone_bricks", "purpur_block"};
                p.roofs = new String[][]{
                    {"purpur_stairs", "purpur_block", "purpur_slab"},
                    {"quartz_stairs", "quartz_block", "quartz_slab"},
                    {"end_stone_brick_stairs", "end_stone_bricks", "end_stone_brick_slab"}
                };
                p.roof = TownDesign.Roof.FLAT;
                p.parapet = "end_stone_brick_wall";
                p.banners = new String[]{"purple_wall_banner", "magenta_wall_banner", "white_wall_banner"};
                p.door = "warped_door";
                p.interior = "quartz_bricks";
                p.trees = TownDesign.Trees.CRYSTAL;
                p.monument = TownDesign.Monument.CRYSTAL;
                p.sky = TownDesign.Sky.CRYSTALS;
                p.hedges = new String[0];
                p.plants = new String[]{"small_amethyst_bud[facing=up,waterlogged=false]"};
                p.lampBase = "purpur_pillar";
                p.lampPost = "purpur_pillar";
                p.lampLight = "end_rod[facing=up]";
                p.hanging = "end_rod[facing=down]";
                p.pillar = "quartz_pillar";
                p.pavRoof = new String[]{"purpur_stairs", "purpur_block", "purpur_slab"};
                p.pavFloor = "quartz_bricks";
                p.pavEdge = "purpur_pillar";
                p.pavFence = "end_stone_brick_wall";
                p.pavBeam = "purpur_pillar";
                p.pondFloor = "obsidian";
                p.pondEdge1 = "end_stone_bricks";
                p.pondEdge2 = "purpur_block";
                p.bench = "purpur_stairs";
                p.plant = "potted_allium";
                p.pedestal = "purpur_pillar";
                p.pedestalTop = "quartz_bricks";
                p.wood = "warped";
                break;
            default:
                p.rock = new String[]{
                    "stone",
                    "stone",
                    "stone",
                    "stone",
                    "stone",
                    "stone",
                    "stone",
                    "stone",
                    "stone",
                    "andesite",
                    "andesite",
                    "andesite",
                    "andesite",
                    "andesite",
                    "cobblestone",
                    "cobblestone",
                    "mossy_cobblestone",
                    "tuff",
                    "tuff",
                    "diorite"
                };
                p.band = "tuff";
                p.paving = new String[]{
                    "cobblestone",
                    "cobblestone",
                    "cobblestone",
                    "cobblestone",
                    "andesite",
                    "andesite",
                    "stone",
                    "gravel",
                    "tuff",
                    "polished_andesite",
                    "mossy_cobblestone"
                };
                p.patch = "cobbled_deepslate";
                p.curb = "stone_bricks";
                p.curb2 = "mossy_stone_bricks";
                p.plazaLine = "polished_andesite";
                p.plazaRadial = "stone_bricks";
                p.plazaBorder = "stone_bricks";
                p.plazaFill = new String[]{
                    "cobblestone", "cobblestone", "cobblestone", "cobblestone", "andesite", "andesite", "stone", "stone", "tuff", "polished_andesite"
                };
                p.plasters = new String[]{
                    "calcite",
                    "calcite",
                    "calcite",
                    "calcite",
                    "calcite",
                    "white_concrete",
                    "white_concrete",
                    "spruce_planks",
                    "spruce_planks",
                    "spruce_planks"
                };
                p.frames = new String[]{"dark_oak_log", "dark_oak_log", "spruce_log", "stripped_dark_oak_log"};
                p.bases = new String[]{"stone_bricks", "cobblestone", "cobblestone", "mossy_cobblestone"};
                p.roofs = new String[][]{
                    {"deepslate_tile_stairs", "deepslate_tiles", "deepslate_tile_slab"},
                    {"dark_oak_stairs", "dark_oak_planks", "dark_oak_slab"},
                    {"cobbled_deepslate_stairs", "cobbled_deepslate", "cobbled_deepslate_slab"},
                    {"spruce_stairs", "spruce_planks", "spruce_slab"},
                    {"deepslate_tile_stairs", "deepslate_tiles", "deepslate_tile_slab"}
                };
                p.banners = new String[]{"cyan_wall_banner", "light_blue_wall_banner", "white_wall_banner", "blue_wall_banner", "cyan_wall_banner"};
                p.trees = TownDesign.Trees.CLASSIC;
                p.monument = TownDesign.Monument.SWORD;
                p.sky = TownDesign.Sky.AIRSHIPS;
                p.hedges = new String[]{"oak_leaves", "oak_leaves", "oak_leaves", "azalea_leaves", "azalea_leaves", "flowering_azalea_leaves"};
                p.plants = new String[]{
                    "short_grass",
                    "short_grass",
                    "short_grass",
                    "short_grass",
                    "poppy",
                    "dandelion",
                    "cornflower",
                    "azure_bluet",
                    "oxeye_daisy",
                    "fern",
                    "sweet_berry_bush[age=3]"
                };
        }

        return p;
    }

    public TownDesign(int cx, int floorY, int cz, int radius, long seed) {
        this(cx, floorY, cz, radius, seed, TownDesign.Theme.TOWN);
    }

    public TownDesign(int cx, int floorY, int cz, int radius, long seed, TownDesign.Theme theme) {
        this.cx = cx;
        this.floorY = floorY;
        this.cz = cz;
        this.radius = Math.max(40, Math.min(80, radius));
        this.seed = seed;
        this.theme = theme == null ? TownDesign.Theme.TOWN : theme;
        this.pal = pal(this.theme);
        int r = this.radius;
        this.ext = (int)Math.round(r * 1.25) + 18;
        this.down = 50;
        this.up = 56;
        this.span = this.ext * 2 + 1;
        this.height = this.down + this.up + 1;
        this.grid = new short[this.span * this.span * this.height];
        this.plan = new byte[this.span * this.span];
        this.top = new byte[this.span * this.span];
        this.palette.add(null);
        this.air = this.id("air");
        this.plazaR = (int)Math.round(r * 0.27);
        this.ringR = (int)Math.round(r * 0.58);
        this.townR = (int)Math.round(r * 0.84);
        this.rh = r >= 52 ? 4 : 3;
        this.layout();
        this.island();
        this.streets();
        this.plaza();
        this.pavilion();
        this.houses();
        this.streetDecor();
        this.landmarks();
        this.gardens();
        this.sky();
        if (this.pal.snow) {
            this.snowCover();
        }
    }

    public int minX() {
        return this.cx - this.ext;
    }

    public int maxX() {
        return this.cx + this.ext;
    }

    public int minZ() {
        return this.cz - this.ext;
    }

    public int maxZ() {
        return this.cz + this.ext;
    }

    public int bottomY() {
        return this.floorY - this.down;
    }

    public int topY() {
        return this.floorY + this.up;
    }

    public int houseCount() {
        return this.houses;
    }

    public double[] spawn() {
        return new double[]{this.cx + this.spawn[0], this.floorY + this.spawn[1], this.cz + this.spawn[2], this.spawn[3]};
    }

    public List<double[]> npcSpots() {
        List<double[]> out = new ArrayList<>();

        for (double[] n : this.npcs) {
            out.add(new double[]{this.cx + n[0], this.floorY + n[1], this.cz + n[2], n[3]});
        }

        return out;
    }

    public void column(int x, int z, TownDesign.Sink out) {
        int dx = x - this.cx;
        int dz = z - this.cz;
        if (this.inside(dx, dz)) {
            int base = ((dx + this.ext) * this.span + dz + this.ext) * this.height;

            for (int i = 0; i < this.height; i++) {
                short v = this.grid[base + i];
                if (v != 0) {
                    out.set(x, this.floorY - this.down + i, z, this.palette.get(v));
                }
            }
        }
    }

    private short id(String b) {
        String full = b.startsWith("minecraft:") ? b : "minecraft:" + b;
        Short v = this.ids.get(full);
        if (v == null) {
            v = (short)this.palette.size();
            this.palette.add(full);
            this.ids.put(full, v);
        }

        return v;
    }

    private boolean inside(int dx, int dz) {
        return dx >= -this.ext && dx <= this.ext && dz >= -this.ext && dz <= this.ext;
    }

    private int ci(int dx, int dz) {
        return (dx + this.ext) * this.span + dz + this.ext;
    }

    private void set(int dx, int dy, int dz, String b) {
        if (this.inside(dx, dz) && dy >= -this.down && dy <= this.up) {
            this.grid[this.ci(dx, dz) * this.height + dy + this.down] = this.id(b);
        }
    }

    private boolean free(int dx, int dy, int dz) {
        if (this.inside(dx, dz) && dy >= -this.down && dy <= this.up) {
            short v = this.grid[this.ci(dx, dz) * this.height + dy + this.down];
            return v == 0 || v == this.air;
        } else {
            return false;
        }
    }

    private String get(int dx, int dy, int dz) {
        if (this.inside(dx, dz) && dy >= -this.down && dy <= this.up) {
            short v = this.grid[this.ci(dx, dz) * this.height + dy + this.down];
            return v == 0 ? null : this.palette.get(v);
        } else {
            return null;
        }
    }

    private byte planAt(int dx, int dz) {
        return this.inside(dx, dz) ? this.plan[this.ci(dx, dz)] : 0;
    }

    private void mark(int dx, int dz, byte p) {
        if (this.inside(dx, dz)) {
            this.plan[this.ci(dx, dz)] = p;
        }
    }

    private int topAt(int dx, int dz) {
        return this.inside(dx, dz) ? this.top[this.ci(dx, dz)] : 0;
    }

    private double rnd(int x, int z, int salt) {
        long h = this.seed ^ x * -7046029254386353131L ^ z * -4417276706812531889L ^ salt * 1609587929392839161L;
        h = (h ^ h >>> 30) * -4658895280553007687L;
        h = (h ^ h >>> 27) * -7723592293110705685L;
        h ^= h >>> 31;
        return (double)(h >>> 11) * 1.1102230246251565E-16;
    }

    private double rnd3(int x, int y, int z, int salt) {
        return this.rnd(x * 73 + y * 1031, z * 59 - y * 977, salt);
    }

    private double noise(double x, double z, int salt) {
        int x0 = (int)Math.floor(x);
        int z0 = (int)Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        fx = fx * fx * (3.0 - 2.0 * fx);
        fz = fz * fz * (3.0 - 2.0 * fz);
        double a = this.rnd(x0, z0, salt);
        double b = this.rnd(x0 + 1, z0, salt);
        double c = this.rnd(x0, z0 + 1, salt);
        double d = this.rnd(x0 + 1, z0 + 1, salt);
        double ab = a + (b - a) * fx;
        double cd = c + (d - c) * fx;
        return ab + (cd - ab) * fz;
    }

    private double fractal(double x, double z, int salt) {
        return this.noise(x, z, salt) * 0.65 + this.noise(x * 2.1, z * 2.1, salt + 1) * 0.35;
    }

    private String pick(String[] options, double r) {
        return options != null && options.length != 0 ? options[Math.min(options.length - 1, (int)(r * options.length))] : null;
    }

    private static String log(String wood, char axis) {
        if (wood.contains("[")) {
            return wood;
        } else {
            boolean axial = wood.endsWith("_log")
                || wood.endsWith("_wood")
                || wood.endsWith("_stem")
                || wood.endsWith("_hyphae")
                || wood.endsWith("basalt")
                || wood.endsWith("_pillar")
                || wood.equals("bone_block")
                || wood.equals("hay_block");
            return axial ? wood + "[axis=" + axis + "]" : wood;
        }
    }

    private static String stairs(String id, String facing, boolean top) {
        return id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=straight,waterlogged=false]";
    }

    private static String leaves(String id) {
        return !id.endsWith("wart_block") && !id.equals("shroomlight") && !id.endsWith("amethyst_block") ? id + "[persistent=true,waterlogged=false]" : id;
    }

    private static String pane(boolean alongX) {
        return alongX
            ? "glass_pane[east=true,west=true,north=false,south=false,waterlogged=false]"
            : "glass_pane[north=true,south=true,east=false,west=false,waterlogged=false]";
    }

    private static String slab(String id) {
        return id + "[type=bottom,waterlogged=false]";
    }

    private String rock(int x, int y, int z) {
        double band = this.noise(x * 0.06 + y * 0.23, z * 0.06, 51);
        if (band > 0.74) {
            return this.pal.band;
        } else {
            double r = this.rnd3(x, y, z, 50);
            return this.theme == TownDesign.Theme.TOWN && r > 0.985 ? "coal_ore" : this.pick(this.pal.rock, r);
        }
    }

    private String paving(int x, int z) {
        double n = this.noise(x * 0.16, z * 0.16, 60);
        double r = this.rnd(x, z, 61);
        if (n > 0.7 && r < 0.78) {
            return this.pal.patch;
        } else {
            return this.theme == TownDesign.Theme.TOWN && n < 0.24 && r < 0.55 ? "gravel" : this.pick(this.pal.paving, r);
        }
    }

    private String surface(int x, int z) {
        if (this.theme == TownDesign.Theme.NETHER) {
            return this.noise(x * 0.07, z * 0.07, 9) > 0.55 ? "warped_nylium" : "crimson_nylium";
        } else {
            return this.pal.surface;
        }
    }

    private String water() {
        return this.pal.liquid + "[level=0]";
    }

    private String falling() {
        return this.pal.iceFalls ? "blue_ice" : this.pal.liquid + "[level=8]";
    }

    private double edge(int dx, int dz) {
        return this.radius + (this.fractal(dx * 0.05, dz * 0.05, 1) - 0.5) * 10.0;
    }

    private void layout() {
        double r = this.radius;
        this.archX = this.townR + 4;
        double fa = Math.toRadians(225.0);
        int wx = (int)Math.round(Math.cos(fa) * r * 0.86);
        int wz = (int)Math.round(Math.sin(fa) * r * 0.86);
        double ux = -Math.cos(fa);
        double uz = -Math.sin(fa);
        int px = (int)Math.round(wx + ux * 11.0);
        int pz = (int)Math.round(wz + uz * 11.0);
        this.fall = new int[]{wx, wz, px, pz};

        for (int i = 0; i < 8; i++) {
            double a = (Math.PI * 2) * (i + 0.5) / 8.0 + (this.rnd(i, 0, 5) - 0.5) * 0.18;
            double d = r * (0.9 + this.rnd(i, 1, 5) * 0.07);
            double rb = 3.0 + this.rnd(i, 2, 5) * 2.2;
            int h = 15 + (int)(this.rnd(i, 3, 5) * 16.0);
            this.spires.add(new double[]{Math.cos(a) * d, Math.sin(a) * d, rb, h, i % 3 == 0 ? 1.0 : 0.0, i});
        }

        int hd = Math.max(4, Math.min(6, (this.townR - this.ringR - 5) / 2));
        this.pavZ1 = -(this.ringR + 4);
        this.pavZ0 = this.pavZ1 - 2 * hd;
        this.pavX0 = -(hd + 2);
        this.pavX1 = hd + 2;

        for (int dx = -this.ext; dx <= this.ext; dx++) {
            for (int dz = -this.ext; dz <= this.ext; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double e = this.edge(dx, dz);
                int c = this.ci(dx, dz);
                if (d > e) {
                    this.plan[c] = 0;
                } else {
                    byte t;
                    if (d <= this.plazaR) {
                        t = 3;
                    } else if (!(d <= this.townR + 3) || Math.abs(dx) > this.rh && Math.abs(dz) > this.rh) {
                        if (Math.abs(d - this.ringR) <= 2.5) {
                            t = 2;
                        } else {
                            t = 1;
                        }
                    } else {
                        t = 2;
                    }

                    if (t == 2 && Math.abs(dx) <= this.rh && dz < this.pavZ0) {
                        t = 1;
                    }

                    if (dx >= this.pavX0 && dx <= this.pavX1 && dz >= this.pavZ0 && dz <= this.pavZ1) {
                        t = 4;
                    }

                    this.plan[c] = t;
                    if (t == 1) {
                        this.top[c] = (byte)this.hill(dx, dz, d, e);
                    }
                }
            }
        }

        for (int dx = -this.ext; dx <= this.ext; dx++) {
            for (int dzx = -this.ext; dzx <= this.ext; dzx++) {
                int c = this.ci(dx, dzx);
                if (this.plan[c] != 0) {
                    if (Math.hypot(dx - this.archX, Math.abs(dzx) - 10) <= 4.5 && this.plan[c] != 2) {
                        this.plan[c] = 8;
                    }

                    if (Math.hypot(dx - wx, dzx - wz) <= 10.5) {
                        this.plan[c] = 8;
                    }

                    if (Math.hypot(dx - px, dzx - pz) <= 6.5 && this.plan[c] != 8) {
                        this.plan[c] = 7;
                    }

                    for (double[] s : this.spires) {
                        if (Math.hypot(dx - s[0], dzx - s[1]) <= s[2] + 1.5) {
                            this.plan[c] = 8;
                        }
                    }
                }
            }
        }
    }

    private int hill(int dx, int dz, double d, double e) {
        if (d <= this.townR + 2) {
            return 0;
        } else {
            double ramp = Math.min(1.0, (d - this.townR - 2.0) / 7.0);
            double n = this.fractal(dx * 0.08, dz * 0.08, 3);
            double h = n > 0.4 ? (n - 0.4) * 19.0 : 0.0;
            double fallOff = Math.max(0.0, Math.min(1.0, (e - d) / 3.0));
            return (int)Math.round(Math.min(7.0, h * ramp * fallOff));
        }
    }

    private void island() {
        for (int dx = -this.ext; dx <= this.ext; dx++) {
            for (int dz = -this.ext; dz <= this.ext; dz++) {
                int c = this.ci(dx, dz);
                if (this.plan[c] != 0) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    double e = this.edge(dx, dz);
                    int t = this.top[c];
                    int depth = (int)Math.round(Math.min((double)(this.down - 4), 4.0 + (e - d) * 0.85 + this.noise(dx * 0.09, dz * 0.09, 5) * 8.0));

                    for (int y = -depth; y <= t; y++) {
                        int below = t - y;
                        String b;
                        if (below == 0) {
                            b = this.surface(dx, dz);
                        } else if (below <= 3) {
                            b = this.theme == TownDesign.Theme.TOWN && this.rnd3(dx, y, dz, 6) < 0.12 ? "coarse_dirt" : this.pal.sub;
                        } else {
                            b = this.rock(dx, y, dz);
                        }

                        this.set(dx, y, dz, b);
                    }

                    if (this.rnd(dx, dz, 7) < 0.07 && depth > 6) {
                        if (this.theme == TownDesign.Theme.NETHER) {
                            this.set(dx, -depth - 1, dz, "glowstone");
                        } else if (this.theme == TownDesign.Theme.WINTER) {
                            this.set(dx, -depth - 1, dz, "packed_ice");
                        } else if (this.theme != TownDesign.Theme.CELESTIAL) {
                            this.set(dx, -depth - 1, dz, "pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]");
                        }
                    }

                    for (int y = t + 1; y <= 44; y++) {
                        this.set(dx, y, dz, "air");
                    }
                }
            }
        }
    }

    private boolean roadLike(byte p) {
        return p == 2 || p == 3 || p == 6;
    }

    private void streets() {
        for (int dx = -this.ext; dx <= this.ext; dx++) {
            for (int dz = -this.ext; dz <= this.ext; dz++) {
                byte p = this.plan[this.ci(dx, dz)];
                if (p == 2 || p == 6) {
                    boolean curb = p == 2
                        && (
                            !this.roadLike(this.planAt(dx + 1, dz))
                                || !this.roadLike(this.planAt(dx - 1, dz))
                                || !this.roadLike(this.planAt(dx, dz + 1))
                                || !this.roadLike(this.planAt(dx, dz - 1))
                        );
                    this.set(dx, 0, dz, curb ? (this.rnd(dx, dz, 62) < 0.75 ? this.pal.curb : this.pal.curb2) : this.paving(dx, dz));
                    this.set(dx, -1, dz, this.pal.sub.equals("dirt") ? "stone" : this.pal.sub);
                }
            }
        }
    }

    private void plaza() {
        for (int dx = -this.plazaR; dx <= this.plazaR; dx++) {
            for (int dz = -this.plazaR; dz <= this.plazaR; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (!(d > this.plazaR + 0.5)) {
                    double ang = Math.atan2(dz, dx);
                    String b;
                    if (d > this.plazaR - 1.2) {
                        b = this.pal.plazaBorder;
                    } else if (d % 5.0 < 1.0) {
                        b = this.pal.plazaLine;
                    } else if (Math.abs(Math.sin(4.0 * ang)) * d < 0.75) {
                        b = this.pal.plazaRadial;
                    } else {
                        b = this.pick(this.pal.plazaFill, this.rnd(dx, dz, 63));
                    }

                    this.set(dx, 0, dz, b);
                    this.set(dx, -1, dz, this.pal.sub.equals("dirt") ? "stone" : this.pal.sub);
                    if (d <= 7.5 && this.pal.monument != TownDesign.Monument.XMAS) {
                        if (d > 6.5) {
                            this.set(dx, 0, dz, this.pal.plazaBorder);
                            this.set(dx, 1, dz, this.pal.plazaBorder);
                        } else if (d > 2.5) {
                            this.set(dx, -1, dz, this.pal.plazaBorder);
                            this.set(dx, 0, dz, this.pal.iceFalls ? "ice" : this.water());
                            if (d > 3.3 && this.rnd(dx, dz, 64) < 0.07 && "water".equals(this.pal.liquid) && !this.pal.iceFalls) {
                                this.set(dx, 1, dz, "lily_pad");
                            }
                        } else {
                            this.set(dx, 0, dz, this.pal.plazaBorder);
                        }
                    }
                }
            }
        }

        this.monument();

        for (int i = 0; i < 4; i++) {
            double a = (Math.PI / 4) + i * Math.PI / 2.0;
            this.lamp((int)Math.round(Math.cos(a) * (this.plazaR - 2)), (int)Math.round(Math.sin(a) * (this.plazaR - 2)), 0);
        }

        this.bench(-6, -11, 2);
        this.bench(6, -11, 2);
        this.bench(-11, -5, 1);
        this.bench(11, -5, 3);
        double sz = this.plazaR + 3;
        this.spawn = new double[]{0.5, 1.0, sz + 0.5, 180.0};
        double[] angles = new double[]{18.0, 34.0, 50.0, 66.0, 114.0, 130.0, 146.0, 162.0};
        double nr = this.plazaR - 4;

        for (double deg : angles) {
            double a = Math.toRadians(deg);
            int x = (int)Math.round(Math.cos(a) * nr);
            int z = (int)Math.round(Math.sin(a) * nr);
            this.set(x, 0, z, this.pal.pedestal.endsWith("_pillar") ? log(this.pal.pedestal, 'y') : this.pal.pedestal);
            this.set(x, 1, z, this.pal.pedestalTop);
            double yaw = Math.toDegrees(Math.atan2(-(0 - x), sz - z));
            this.npcs.add(new double[]{x + 0.5, 2.0, z + 0.5, yaw});
        }
    }

    private void monument() {
        switch (this.pal.monument) {
            case OBELISK:
                this.obelisk();
                break;
            case PAGODA:
                this.pagodaTower();
                break;
            case PORTAL:
                this.portal();
                break;
            case XMAS:
                this.xmasTree();
                break;
            case CRYSTAL:
                this.crystal();
                break;
            default:
                this.sword();
        }
    }

    private void pedestal(int h, String main, String accent) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (!(d > 2.5)) {
                    for (int y = 0; y < h; y++) {
                        this.set(dx, y, dz, d <= 1.8 && y >= h - 3 ? accent : main);
                    }
                }
            }
        }
    }

    private void sword() {
        this.pedestal(5, "stone_bricks", "chiseled_stone_bricks");

        for (int y = 5; y <= 16; y++) {
            for (int x = -1; x <= 1; x++) {
                this.set(x, y, 0, x == 0 ? "light_blue_concrete" : "diamond_block");
            }
        }

        this.set(0, 17, 0, "diamond_block");

        for (int x = -4; x <= 4; x++) {
            this.set(x, 18, 0, "dark_prismarine");
        }

        this.set(-4, 19, 0, "dark_prismarine");
        this.set(4, 19, 0, "dark_prismarine");

        for (int z = -1; z <= 1; z += 2) {
            for (int x = -1; x <= 1; x++) {
                this.set(x, 18, z, "dark_prismarine");
            }
        }

        for (int y = 19; y <= 22; y++) {
            this.set(0, y, 0, log("dark_oak_log", 'y'));
        }

        this.set(0, 23, 0, "dark_prismarine");
        this.set(0, 24, 0, "sea_lantern");
    }

    private void obelisk() {
        this.pedestal(3, "cut_sandstone", "chiseled_sandstone");
        int h = 20;

        for (int y = 3; y <= 3 + h; y++) {
            double f = (double)(y - 3) / h;
            int half = f < 0.75 ? 1 : 0;

            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    this.set(dx, y, dz, (y - 3) % 5 == 4 ? "chiseled_sandstone" : "smooth_sandstone");
                }
            }
        }

        this.set(0, h + 4, 0, "gold_block");
        this.set(0, h + 5, 0, "gold_block");

        for (int f = 0; f < 4; f++) {
            this.set(FX[f] * 2, 3, FZ[f] * 2, "gold_block");
        }

        for (int i = 0; i < 4; i++) {
            double a = (Math.PI / 4) + i * Math.PI / 2.0;
            this.palm((int)Math.round(Math.cos(a) * 9.0), 1, (int)Math.round(Math.sin(a) * 9.0), 7, i);
        }
    }

    private void pagodaTower() {
        this.pedestal(2, "stone_bricks", "mossy_stone_bricks");
        int y = 2;
        int[] halves = new int[]{4, 4, 3, 3, 2};

        for (int tier = 0; tier < halves.length; tier++) {
            int hw = halves[tier];

            for (int dx = -hw + 1; dx <= hw - 1; dx++) {
                for (int dz = -hw + 1; dz <= hw - 1; dz++) {
                    boolean edge = Math.abs(dx) == hw - 1 || Math.abs(dz) == hw - 1;

                    for (int k = 0; k < 3; k++) {
                        if (edge) {
                            boolean corner = Math.abs(dx) == hw - 1 && Math.abs(dz) == hw - 1;
                            this.set(
                                dx,
                                y + k,
                                dz,
                                corner ? log("stripped_crimson_stem", 'y') : (k == 1 ? (Math.abs(dx) == hw - 1 ? pane(false) : pane(true)) : "calcite")
                            );
                        } else {
                            this.set(dx, y + k, dz, "air");
                        }
                    }
                }
            }

            int ry = y + 3;

            for (int dx = -hw - 1; dx <= hw + 1; dx++) {
                for (int dz = -hw - 1; dz <= hw + 1; dz++) {
                    int lx = hw + 1 - Math.abs(dx);
                    int lz = hw + 1 - Math.abs(dz);
                    int lvl = Math.min(lx, lz);
                    boolean corner = Math.abs(dx) == hw + 1 && Math.abs(dz) == hw + 1;
                    String s;
                    if (lvl >= 2) {
                        s = "deepslate_tiles";
                    } else if (lx < lz) {
                        s = stairs("deepslate_tile_stairs", dx < 0 ? "east" : "west", false);
                    } else if (lz < lx) {
                        s = stairs("deepslate_tile_stairs", dz < 0 ? "south" : "north", false);
                    } else {
                        s = "deepslate_tiles";
                    }

                    this.set(dx, ry + (lvl >= 2 ? 1 : 0), dz, s);
                    if (corner) {
                        this.set(dx, ry + 1, dz, slab("deepslate_tile_slab"));
                        this.set(dx, ry, dz, "deepslate_tiles");
                        this.set(dx, ry - 1, dz, "lantern[hanging=true,waterlogged=false]");
                    }
                }
            }

            y = ry + 2;
        }

        for (int kx = 0; kx < 4; kx++) {
            this.set(0, y + kx, 0, kx == 3 ? "gold_block" : "chain[axis=y]".replace("chain", "iron_chain"));
        }

        this.set(0, y + 4, 0, "lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    private void portal() {
        this.pedestal(2, "polished_blackstone_bricks", "chiseled_polished_blackstone");
        int hw = 4;
        int h = 13;

        for (int x = -hw; x <= hw; x++) {
            for (int y = Math.abs(x) == hw ? 0 : 2; y <= 2 + h; y++) {
                boolean frame = Math.abs(x) == hw || y == 2 || y == 2 + h;
                if (frame) {
                    this.set(x, y, 0, this.rnd3(x, y, 0, 70) < 0.25 ? "crying_obsidian" : "obsidian");
                } else {
                    this.set(x, y, 0, "purple_stained_glass");
                }
            }
        }

        for (int x = -hw - 1; x <= hw + 1; x += 2 * hw + 2) {
            for (int yx = 2; yx <= 6; yx++) {
                this.set(x, yx, 0, "polished_blackstone_bricks");
            }

            this.set(x, 7, 0, "soul_lantern[hanging=false,waterlogged=false]");
        }

        this.set(-hw, 3 + h, 0, "magma_block");
        this.set(hw, 3 + h, 0, "magma_block");
        this.set(0, 3 + h, 0, "gilded_blackstone");
    }

    private void xmasTree() {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                if (Math.hypot(dx, dz) <= 7.5) {
                    this.set(dx, 0, dz, "snow_block");
                }
            }
        }

        int h = 26;

        for (int y = 1; y <= h; y++) {
            this.set(0, y, 0, log("spruce_log", 'y'));
        }

        String[] balls = new String[]{"red_wool", "glowstone", "light_blue_wool", "yellow_wool", "sea_lantern", "red_wool", "magenta_wool"};

        for (int y = 4; y <= h + 1; y++) {
            double f = (double)(y - 4) / (h - 2);
            double r = 7.5 * (1.0 - f) + 0.5 + (y % 3 == 0 ? 0.8 : 0.0);
            int ri = (int)Math.ceil(r);

            for (int dx = -ri; dx <= ri; dx++) {
                for (int dzx = -ri; dzx <= ri; dzx++) {
                    double d = Math.hypot(dx, dzx);
                    if (d <= r && (dx != 0 || dzx != 0)) {
                        boolean shell = d > r - 1.1;
                        String b = shell && this.rnd3(dx, y, dzx, 71) < 0.09 ? balls[(int)(this.rnd3(dx, y, dzx, 72) * balls.length)] : leaves("spruce_leaves");
                        this.set(dx, y, dzx, b);
                        if (shell && this.free(dx, y + 1, dzx) && this.rnd3(dx, y, dzx, 73) < 0.5) {
                            this.set(dx, y + 1, dzx, "snow[layers=1]");
                        }
                    }
                }
            }
        }

        this.set(0, h + 2, 0, "gold_block");
        this.set(0, h + 3, 0, "glowstone");

        for (int f = 0; f < 4; f++) {
            this.set(FX[f], h + 2, FZ[f], "gold_block");
        }

        String[] wraps = new String[]{"red_wool", "lime_wool", "light_blue_wool", "purple_wool", "orange_wool"};

        for (int i = 0; i < 8; i++) {
            double a = (Math.PI * 2) * i / 8.0 + 0.3;
            int x = (int)Math.round(Math.cos(a) * 9.0);
            int z = (int)Math.round(Math.sin(a) * 9.0);
            this.set(x, 1, z, wraps[i % wraps.length]);
            if (i % 2 == 0) {
                this.set(x, 2, z, "white_carpet");
            }
        }
    }

    private void crystal() {
        this.pedestal(3, "purpur_block", "quartz_bricks");

        for (int f = 0; f < 4; f++) {
            this.set(FX[f] * 2, 3, FZ[f] * 2, "end_rod[facing=up]");
        }

        int cy = 16;
        int r = 5;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r * 2; dy <= r * 2; dy++) {
                    double m = Math.abs(dx) + Math.abs(dz) + Math.abs(dy) * 0.5;
                    if (m <= r) {
                        this.set(dx, cy + dy, dz, m > r - 1.4 ? "amethyst_block" : "purple_stained_glass");
                    }
                }
            }
        }

        this.set(0, cy + 2 * r + 1, 0, "end_rod[facing=up]");
        this.set(0, cy - 2 * r - 1, 0, "end_rod[facing=down]");
    }

    private void lamp(int x, int z, int y0) {
        this.set(x, y0 + 1, z, log(this.pal.lampBase, 'y'));
        this.set(x, y0 + 2, z, log(this.pal.lampPost, 'y'));
        this.set(x, y0 + 3, z, log(this.pal.lampPost, 'y'));
        this.set(x, y0 + 4, z, this.pal.lampLight);
    }

    private void bench(int x, int z, int back) {
        int tx = FZ[back] != 0 ? 1 : 0;
        int tz = FZ[back] != 0 ? 0 : 1;

        for (int k = -1; k <= 1; k++) {
            this.set(x + tx * k, 1, z + tz * k, stairs(this.pal.bench, FACE[back], false));
        }

        this.set(x + tx * 2, 1, z + tz * 2, this.pal.plant);
        this.set(x - tx * 2, 1, z - tz * 2, this.pal.plant);
    }

    private void pavilion() {
        int x0 = this.pavX0;
        int x1 = this.pavX1;
        int z0 = this.pavZ0;
        int z1 = this.pavZ1;

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                this.set(x, 0, z, this.pal.plazaBorder);
                this.set(x, 1, z, !edge ? this.pal.pavFloor : log(this.pal.pavEdge, (char)(x != x0 && x != x1 ? 'x' : 'z')));
                boolean pillar = (x == x0 || x == x1 || (x - x0) % 4 == 0) && (z == z0 || z == z1 || (z - z0) % 4 == 0) && edge;
                if (pillar) {
                    for (int y = 2; y <= 7; y++) {
                        this.set(x, y, z, log(this.pal.pillar, 'y'));
                    }
                } else if (edge && (z != z1 || Math.abs(x) > 2)) {
                    this.set(x, 2, z, this.pal.pavFence);
                }

                this.set(x, 8, z, edge ? log(this.pal.pavBeam, (char)(x != x0 && x != x1 ? 'x' : 'z')) : this.pal.pavFloor);
                if (!edge && (x - x0) % 4 == 2 && (z - z0) % 4 == 2) {
                    this.set(x, 7, z, this.pal.hanging);
                }
            }
        }

        for (int x = -2; x <= 2; x++) {
            this.set(x, 1, z1 + 1, stairs(this.pal.bench, "north", false));
            this.set(x, 0, z1 + 1, this.pal.plazaBorder);
        }

        int rx0 = x0 - 1;
        int rx1 = x1 + 1;
        int rz0 = z0 - 1;
        int rz1 = z1 + 1;

        for (int x = rx0; x <= rx1; x++) {
            for (int z = rz0; z <= rz1; z++) {
                int lx = Math.min(x - rx0, rx1 - x);
                int lz = Math.min(z - rz0, rz1 - z);
                int lvl = Math.min(lx, lz);
                int y = 8 + lvl;
                int maxZ = (rz1 - rz0) / 2;
                if (lvl >= maxZ) {
                    this.set(x, y, z, this.pal.pavRoof[1]);
                    this.set(x, y + 1, z, slab(this.pal.pavRoof[2]));
                } else if (lx == lz) {
                    this.set(x, y, z, this.pal.pavRoof[1]);
                } else if (lx < lz) {
                    this.set(x, y, z, stairs(this.pal.pavRoof[0], x < 0 ? "east" : "west", false));
                } else {
                    this.set(x, y, z, stairs(this.pal.pavRoof[0], z < (rz0 + rz1) / 2 ? "south" : "north", false));
                }

                if (lvl > 0) {
                    for (int yy = 9; yy < y; yy++) {
                        this.set(x, yy, z, this.pal.pavRoof[1]);
                    }
                }
            }
        }

        int i = 0;

        for (int x = x0; x <= x1; x += 4) {
            this.set(x, 7, z1 + 1, this.pal.banners[i++ % this.pal.banners.length] + "[facing=south]");
        }

        this.set(x1, 7, z1 + 1, this.pal.banners[0] + "[facing=south]");
    }

    private boolean lotFree(int x0, int z0, int x1, int z1) {
        for (int x = x0 - 2; x <= x1 + 2; x++) {
            int z = z0 - 2;

            while (z <= z1 + 2) {
                byte p = this.planAt(x, z);
                boolean core = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                boolean ring1 = x >= x0 - 1 && x <= x1 + 1 && z >= z0 - 1 && z <= z1 + 1;
                if (p == 0) {
                    return false;
                }

                if (!core || p == 1 && !(Math.hypot(x, z) > this.townR - 1) && this.topAt(x, z) == 0) {
                    if (!ring1 || p != 4 && p != 8 && p != 7) {
                        if (!ring1 && p == 4) {
                            return false;
                        }

                        z++;
                        continue;
                    }

                    return false;
                }

                return false;
            }
        }

        return true;
    }

    private void claim(TownDesign.Lot l) {
        for (int x = l.x0 - 1; x <= l.x1 + 1; x++) {
            for (int z = l.z0 - 1; z <= l.z1 + 1; z++) {
                boolean core = x >= l.x0 && x <= l.x1 && z >= l.z0 && z <= l.z1;
                if (core) {
                    this.mark(x, z, (byte)4);
                } else if (this.planAt(x, z) == 1) {
                    this.mark(x, z, (byte)5);
                }
            }
        }
    }

    private void houses() {
        List<TownDesign.Lot> lots = new ArrayList<>();
        int salt = 0;

        for (int axis = 0; axis < 2; axis++) {
            for (int sg = -1; sg <= 1; sg += 2) {
                for (int side = -1; side <= 1; side += 2) {
                    int t = this.plazaR + 3;

                    while (t < this.townR) {
                        int w = 7 + 2 * (int)(this.rnd(t, ++salt, 21) * 4.0);
                        int dep = 7 + (int)(this.rnd(t, salt, 22) * 5.0);
                        int a1 = t + w - 1;
                        int p0 = this.rh + 2;
                        int p1 = this.rh + 1 + dep;
                        TownDesign.Lot l;
                        if (axis == 0) {
                            l = new TownDesign.Lot(side > 0 ? p0 : -p1, sg > 0 ? t : -a1, side > 0 ? p1 : -p0, sg > 0 ? a1 : -t, side > 0 ? 3 : 1);
                        } else {
                            l = new TownDesign.Lot(sg > 0 ? t : -a1, side > 0 ? p0 : -p1, sg > 0 ? a1 : -t, side > 0 ? p1 : -p0, side > 0 ? 0 : 2);
                        }

                        if (this.lotFree(l.x0, l.z0, l.x1, l.z1)) {
                            lots.add(l);
                            this.claim(l);

                            for (int a = t; a <= a1; a++) {
                                int x = axis == 0 ? side * (this.rh + 1) : sg * a;
                                int z = axis == 0 ? sg * a : side * (this.rh + 1);
                                if (this.planAt(x, z) == 1 || this.planAt(x, z) == 5) {
                                    this.mark(x, z, (byte)6);
                                }
                            }

                            t = a1 + 3 + (int)(this.rnd(t, salt, 23) * 2.0);
                        } else {
                            t += 2;
                        }
                    }
                }
            }
        }

        int attempts = (int)(1100.0 * Math.pow(this.radius / 64.0, 2.0));

        for (int i = 0; i < attempts; i++) {
            double ax = this.rnd(i, 0, 31) * Math.PI * 2.0;
            double dd = this.plazaR + 6 + this.rnd(i, 1, 31) * (this.townR - 4 - this.plazaR - 6);
            int hx = (int)Math.round(Math.cos(ax) * dd);
            int hz = (int)Math.round(Math.sin(ax) * dd);
            int face = this.nearestRoad(hx, hz, 13);
            if (face >= 0) {
                int wx = 7 + (int)(this.rnd(i, 2, 31) * 5.0);
                int depx = 7 + (int)(this.rnd(i, 3, 31) * 4.0);
                int sx = FZ[face] != 0 ? wx : depx;
                int sz = FZ[face] != 0 ? depx : wx;
                TownDesign.Lot lx = new TownDesign.Lot(hx - sx / 2, hz - sz / 2, hx - sx / 2 + sx - 1, hz - sz / 2 + sz - 1, face);
                if (this.lotFree(lx.x0, lx.z0, lx.x1, lx.z1)) {
                    lots.add(lx);
                    this.claim(lx);
                    int[] door = doorCell(lx);
                    int x = door[0] + FX[face];
                    int z = door[1] + FZ[face];

                    for (int s = 0; s < 16; s++) {
                        byte p = this.planAt(x, z);
                        if (p == 2 || p == 3 || p == 0 || p == 4) {
                            break;
                        }

                        if (p == 1 || p == 5 || p == 6) {
                            this.mark(x, z, (byte)6);
                        }

                        x += FX[face];
                        z += FZ[face];
                    }
                }
            }
        }

        for (int dx = -this.ext; dx <= this.ext; dx++) {
            for (int dz = -this.ext; dz <= this.ext; dz++) {
                if (this.plan[this.ci(dx, dz)] == 6) {
                    this.set(dx, 0, dz, this.paving(dx, dz));
                    this.set(dx, -1, dz, this.pal.sub.equals("dirt") ? "stone" : this.pal.sub);
                }
            }
        }

        for (TownDesign.Lot lx : lots) {
            this.house(lx, 0);
        }

        this.houses = lots.size();
        if (this.theme == TownDesign.Theme.SAKURA) {
            this.torii();
        }
    }

    private int nearestRoad(int x, int z, int max) {
        int best = -1;
        int bestD = Integer.MAX_VALUE;

        for (int f = 0; f < 4; f++) {
            for (int s = 1; s <= max; s++) {
                byte p = this.planAt(x + FX[f] * s, z + FZ[f] * s);
                if (p == 2 || p == 3) {
                    if (s < bestD) {
                        bestD = s;
                        best = f;
                    }
                    break;
                }

                if (p == 0 || p == 4 || p == 8 || p == 7) {
                    break;
                }
            }
        }

        return best;
    }

    private static int[] doorCell(TownDesign.Lot l) {
        return switch (l.face) {
            case 0 -> new int[]{(l.x0 + l.x1) / 2, l.z0};
            case 1 -> new int[]{l.x1, (l.z0 + l.z1) / 2};
            case 2 -> new int[]{(l.x0 + l.x1) / 2, l.z1};
            default -> new int[]{l.x0, (l.z0 + l.z1) / 2};
        };
    }

    private void house(TownDesign.Lot l, int y0) {
        int w = l.x1 - l.x0 + 1;
        int dpt = l.z1 - l.z0 + 1;
        double r1 = this.rnd(l.x0, l.z0, 40);
        double r2 = this.rnd(l.x0, l.z0, 41);
        double r3 = this.rnd(l.x0, l.z0, 42);
        double r4 = this.rnd(l.x0, l.z0, 43);
        int floors = w >= 9 && dpt >= 9 && r1 < 0.35 ? 3 : (w * dpt < 56 && r1 > 0.85 ? 1 : 2);
        boolean jetty = this.pal.roof == TownDesign.Roof.GABLE && floors >= 2 && r2 < 0.55;
        String plaster = this.pick(this.pal.plasters, r3);
        String frame = this.pick(this.pal.frames, r4);
        String base = this.pick(this.pal.bases, r2);
        String[] roof = this.pal.roofs[(int)(this.rnd(l.x1, l.z1, 44) * this.pal.roofs.length)];
        String banner = this.pick(this.pal.banners, this.rnd(l.x1, l.z0, 45));
        int door = 0;

        for (int k = 0; k < floors; k++) {
            int e = jetty && k >= 1 ? 1 : 0;
            int x0 = l.x0 - e;
            int x1 = l.x1 + e;
            int z0 = l.z0 - e;
            int z1 = l.z1 + e;
            int yb = y0 + 1 + 4 * k;
            boolean topFloor = k == floors - 1;

            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edgeX = x == x0 || x == x1;
                    boolean edgeZ = z == z0 || z == z1;
                    if (!edgeX && !edgeZ) {
                        for (int y = yb; y <= yb + 2; y++) {
                            this.set(x, y, z, "air");
                        }

                        this.set(x, yb + 3, z, topFloor && this.pal.roof == TownDesign.Roof.FLAT ? roof[1] : this.pal.interior);
                        if (k == 0) {
                            this.set(x, y0, z, this.pal.interior);
                        }
                    } else {
                        boolean corner = edgeX && edgeZ;
                        boolean alongX = edgeZ && !edgeX;
                        int i = alongX ? x - x0 : z - z0;
                        int len = alongX ? x1 - x0 + 1 : z1 - z0 + 1;
                        int wallFace = alongX ? (z == z0 ? 0 : 2) : (x == x0 ? 3 : 1);
                        boolean doorWall = k == 0 && wallFace == l.face;
                        int c = len / 2;
                        if (e == 1) {
                            this.set(x, yb - 1, z, corner ? log(frame, 'y') : log(frame, (char)(alongX ? 'x' : 'z')));
                        }

                        for (int y = yb; y <= yb + 3; y++) {
                            boolean post = i % 3 == 0 || i == len - 1 || doorWall && (i == c - 1 || i == c + 1);
                            String bl;
                            if (corner) {
                                bl = log(frame, 'y');
                            } else if (y == yb + 3) {
                                bl = log(frame, (char)(alongX ? 'x' : 'z'));
                            } else if (doorWall && i == c && y <= yb + 1) {
                                bl = this.pal.door
                                    + "[facing="
                                    + FACE[l.face]
                                    + ",half="
                                    + (y == yb ? "lower" : "upper")
                                    + ",hinge=left,open=false,powered=false]";
                                door++;
                            } else if (post) {
                                bl = log(frame, 'y');
                            } else if (k == 0 && y == yb) {
                                bl = base;
                            } else if (y != yb + 1 || doorWall && Math.abs(i - c) <= 2) {
                                bl = plaster;
                            } else {
                                bl = pane(alongX);
                            }

                            this.set(x, y, z, bl);
                        }
                    }
                }
            }
        }

        int[] dc = doorCell(l);
        int ox = dc[0] + FX[l.face];
        int oz = dc[1] + FZ[l.face];
        int tx = FZ[l.face] != 0 ? 1 : 0;
        int tz = FZ[l.face] != 0 ? 0 : 1;
        if (!jetty) {
            for (int k = -1; k <= 1; k++) {
                if (this.theme == TownDesign.Theme.DESERT) {
                    this.set(ox + tx * k, y0 + 4, oz + tz * k, k == 0 ? "white_wool" : "orange_wool");
                } else {
                    this.set(ox + tx * k, y0 + 4, oz + tz * k, stairs(roof[0], FACE[(l.face + 2) % 4], false));
                }
            }
        }

        this.set(ox, y0 + 3, oz, this.pal.hanging);
        int e = jetty ? 1 : 0;
        int tx0 = l.x0 - e;
        int tx1 = l.x1 + e;
        int tz0 = l.z0 - e;
        int tz1 = l.z1 + e;
        int beam = y0 + 4 * floors;
        if (this.pal.roof == TownDesign.Roof.FLAT) {
            this.flatRoof(tx0, tz0, tx1, tz1, beam, roof, r1);
        } else {
            this.gableRoof(tx0, tz0, tx1, tz1, beam, roof, plaster, frame, this.pal.roof == TownDesign.Roof.PAGODA);
            if (this.rnd(l.x0, l.z1, 46) < 0.5 && this.theme != TownDesign.Theme.SAKURA) {
                boolean ridgeX = tx1 - tx0 >= tz1 - tz0;
                int across = ridgeX ? tz1 - tz0 + 3 : tx1 - tx0 + 3;
                int chx = ridgeX ? tx1 - 2 : tx0 + 1;
                int chz = ridgeX ? tz0 + 1 : tz1 - 2;
                int ridgeY = beam + (across - 1) / 2;

                for (int y = beam + 1; y <= ridgeY + 2; y++) {
                    this.set(chx, y, chz, this.theme == TownDesign.Theme.NETHER ? "blackstone" : (this.rnd(l.x0, l.z1, 47) < 0.5 ? "bricks" : "cobblestone"));
                }

                this.set(
                    chx,
                    ridgeY + 3,
                    chz,
                    (this.theme == TownDesign.Theme.NETHER ? "soul_campfire" : "campfire") + "[facing=north,lit=true,signal_fire=false,waterlogged=false]"
                );
            }
        }

        int k = floors - 1;
        int ee = jetty && k >= 1 ? 1 : 0;
        int yb = y0 + 1 + 4 * k;
        int fx0 = l.x0 - ee;
        int fx1 = l.x1 + ee;
        int fz0 = l.z0 - ee;
        int fz1 = l.z1 + ee;

        int[][] spots = switch (l.face) {
            case 0 -> new int[][]{{fx0 + 1, fz0 - 1}, {fx1 - 1, fz0 - 1}};
            case 1 -> new int[][]{{fx1 + 1, fz0 + 1}, {fx1 + 1, fz1 - 1}};
            case 2 -> new int[][]{{fx0 + 1, fz1 + 1}, {fx1 - 1, fz1 + 1}};
            default -> new int[][]{{fx0 - 1, fz0 + 1}, {fx0 - 1, fz1 - 1}};
        };
        if (floors >= 2) {
            for (int[] s : spots) {
                this.set(s[0], yb + 2, s[1], banner + "[facing=" + FACE[l.face] + "]");
            }
        }

        if (door == 0) {
            this.set(dc[0], y0 + 1, dc[1], this.pal.door + "[facing=" + FACE[l.face] + ",half=lower,hinge=left,open=false,powered=false]");
            this.set(dc[0], y0 + 2, dc[1], this.pal.door + "[facing=" + FACE[l.face] + ",half=upper,hinge=left,open=false,powered=false]");
        }
    }

    private void gableRoof(int tx0, int tz0, int tx1, int tz1, int beam, String[] roof, String plaster, String frame, boolean pagoda) {
        boolean ridgeX = tx1 - tx0 >= tz1 - tz0;
        int over = pagoda ? 2 : 1;
        int rx0 = tx0 - over;
        int rx1 = tx1 + over;
        int rz0 = tz0 - over;
        int rz1 = tz1 + over;
        int across = ridgeX ? rz1 - rz0 + 1 : rx1 - rx0 + 1;
        int maxL = (across - 1) / 2;
        int base = beam - (over - 1);

        for (int x = rx0; x <= rx1; x++) {
            for (int z = rz0; z <= rz1; z++) {
                int pos = ridgeX ? z - rz0 : x - rx0;
                int lvl = Math.min(pos, across - 1 - pos);
                int y = base + lvl;
                if (across % 2 == 1 && lvl == maxL) {
                    this.set(x, y, z, roof[1]);
                    this.set(x, y + 1, z, slab(roof[2]));
                } else {
                    boolean low = pos * 2 < across - 1;
                    String facing = ridgeX ? (low ? "south" : "north") : (low ? "east" : "west");
                    this.set(x, y, z, stairs(roof[0], facing, false));
                }

                boolean endCol = ridgeX ? x == rx0 || x == rx1 : z == rz0 || z == rz1;
                if (pagoda && lvl == 0 && endCol) {
                    this.set(x, y + 1, z, slab(roof[2]));
                }

                boolean gable = ridgeX ? (x == tx0 || x == tx1) && z >= tz0 && z <= tz1 : (z == tz0 || z == tz1) && x >= tx0 && x <= tx1;
                if (gable) {
                    int mid = ridgeX ? (tz0 + tz1) / 2 : (tx0 + tx1) / 2;
                    int along = ridgeX ? z : x;

                    for (int yy = beam + 1; yy < y; yy++) {
                        String b;
                        if (along == mid) {
                            b = log(frame, 'y');
                        } else if (yy == beam + 2 && Math.abs(along - mid) == 1 && y - beam >= 4) {
                            b = pane(!ridgeX);
                        } else {
                            b = plaster;
                        }

                        this.set(x, yy, z, b);
                    }
                }
            }
        }

        if (pagoda) {
            int ridgeY = base + maxL;
            if (ridgeX) {
                this.set(rx0, ridgeY + 1, (rz0 + rz1) / 2, roof[1]);
                this.set(rx1, ridgeY + 1, (rz0 + rz1) / 2, roof[1]);
            } else {
                this.set((rx0 + rx1) / 2, ridgeY + 1, rz0, roof[1]);
                this.set((rx0 + rx1) / 2, ridgeY + 1, rz1, roof[1]);
            }
        }
    }

    private void flatRoof(int tx0, int tz0, int tx1, int tz1, int beam, String[] roof, double r) {
        for (int x = tx0; x <= tx1; x++) {
            for (int z = tz0; z <= tz1; z++) {
                boolean edge = x == tx0 || x == tx1 || z == tz0 || z == tz1;
                if (edge) {
                    this.set(x, beam + 1, z, this.pal.parapet);
                }
            }
        }

        int w = tx1 - tx0 + 1;
        int d = tz1 - tz0 + 1;
        if (w >= 7 && d >= 7 && r < 0.6) {
            double cx = (tx0 + tx1) / 2.0;
            double czz = (tz0 + tz1) / 2.0;
            double rr = Math.min(w, d) / 2.0 - 1.2;
            int ri = (int)Math.ceil(rr);

            for (int x = (int)Math.floor(cx - ri); x <= (int)Math.ceil(cx + ri); x++) {
                for (int zx = (int)Math.floor(czz - ri); zx <= (int)Math.ceil(czz + ri); zx++) {
                    for (int y = 0; y <= ri; y++) {
                        double dist = Math.sqrt((x - cx) * (x - cx) + (zx - czz) * (zx - czz) + y * y * 1.1);
                        if (dist <= rr && dist > rr - 1.2) {
                            this.set(x, beam + 1 + y, zx, roof[1]);
                        }
                    }
                }
            }

            this.set((int)Math.round(cx), beam + 2 + ri, (int)Math.round(czz), this.theme == TownDesign.Theme.CELESTIAL ? "end_rod[facing=up]" : "gold_block");
        }
    }

    private void torii() {
        int at = this.plazaR + 2;

        for (int f = 0; f < 4; f++) {
            int px = FX[f];
            int pz = FZ[f];
            int tx = pz != 0 ? 1 : 0;
            int tz = pz != 0 ? 0 : 1;
            int bx = px * at;
            int bz = pz * at;

            for (int s = -1; s <= 1; s += 2) {
                int x = bx + tx * s * (this.rh + 1);
                int z = bz + tz * s * (this.rh + 1);

                for (int y = 1; y <= 7; y++) {
                    this.set(x, y, z, log("stripped_crimson_stem", 'y'));
                }
            }

            for (int k = -(this.rh + 2); k <= this.rh + 2; k++) {
                int x = bx + tx * k;
                int z = bz + tz * k;
                this.set(x, 6, z, "crimson_planks");
                this.set(x, 8, z, Math.abs(k) == this.rh + 2 ? slab("dark_oak_slab") : "dark_oak_planks");
            }

            this.set(bx, 7, bz, "crimson_planks");
        }
    }

    private void streetDecor() {
        int n = 0;

        for (int t = this.plazaR + 5; t <= this.townR; t += 9) {
            for (int f = 0; f < 4; f++) {
                int side = n++ % 2 == 0 ? 1 : -1;
                int x = FX[f] * t + (FX[f] == 0 ? side * this.rh : 0);
                int z = FZ[f] * t + (FZ[f] == 0 ? side * this.rh : 0);
                if (this.planAt(x, z) == 2) {
                    this.lamp(x, z, 0);
                }
            }
        }

        int count = (int)((Math.PI * 2) * this.ringR / 13.0);

        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2) * (i + 0.5) / count;
            int x = (int)Math.round(Math.cos(a) * (this.ringR + 2));
            int z = (int)Math.round(Math.sin(a) * (this.ringR + 2));
            if (this.planAt(x, z) == 2 && Math.abs(x) > this.rh + 1 && Math.abs(z) > this.rh + 1) {
                this.lamp(x, z, 0);
            }
        }

        for (int dx = -this.ext; dx <= this.ext; dx++) {
            for (int dz = -this.ext; dz <= this.ext; dz++) {
                byte p = this.plan[this.ci(dx, dz)];
                if ((p == 1 || p == 5) && this.topAt(dx, dz) == 0) {
                    boolean street = false;
                    boolean path = false;

                    for (int fx = 0; fx < 4; fx++) {
                        byte q = this.planAt(dx + FX[fx], dz + FZ[fx]);
                        street |= q == 2 || q == 3;
                        path |= q == 6;
                    }

                    if (street && !path && this.rnd(dx, dz, 65) > 0.08) {
                        if (this.pal.hedges.length == 0) {
                            if (this.rnd(dx, dz, 67) < 0.7) {
                                this.set(dx, 1, dz, this.pal.parapet);
                            }
                        } else {
                            double r = this.rnd(dx, dz, 66);
                            this.set(dx, 1, dz, leaves(this.pick(this.pal.hedges, r)));
                            if (this.theme == TownDesign.Theme.NETHER && r > 0.9) {
                                this.set(dx, 1, dz, "shroomlight");
                            }
                        }
                    }
                }
            }
        }
    }

    private void gardens() {
        int cell = 7;

        for (int gx = -this.ext / cell; gx <= this.ext / cell; gx++) {
            for (int gz = -this.ext / cell; gz <= this.ext / cell; gz++) {
                if (!(this.rnd(gx, gz, 80) > 0.62)) {
                    int x = gx * cell + (int)(this.rnd(gx, gz, 81) * (cell - 2));
                    int z = gz * cell + (int)(this.rnd(gx, gz, 82) * (cell - 2));
                    if (this.treeRoom(x, z)) {
                        int t = this.topAt(x, z);
                        this.tree(x, t + 1, z, this.rnd(gx, gz, 83), gx * 31 + gz);
                    }
                }
            }
        }

        for (int dx = -this.ext; dx <= this.ext; dx++) {
            for (int dz = -this.ext; dz <= this.ext; dz++) {
                byte p = this.plan[this.ci(dx, dz)];
                if (p == 1 || p == 5) {
                    int t = this.topAt(dx, dz);
                    if (this.free(dx, t + 1, dz) && this.free(dx, t + 2, dz) && this.pal.plants.length != 0) {
                        double r = this.rnd(dx, dz, 90);
                        double chance = this.theme == TownDesign.Theme.DESERT ? 0.05 : (this.theme == TownDesign.Theme.CELESTIAL ? 0.03 : 0.22);
                        if (r < chance) {
                            String plant = this.pick(this.pal.plants, this.rnd(dx, dz, 91));
                            this.set(dx, t + 1, dz, plant);
                            if ("cactus".equals(plant)) {
                                int h = 1 + (int)(this.rnd(dx, dz, 92) * 3.0);

                                for (int y = 2; y <= h; y++) {
                                    this.set(dx, t + y, dz, "cactus");
                                }
                            }
                        } else if (this.theme == TownDesign.Theme.TOWN && r < 0.226) {
                            this.set(dx, t + 1, dz, "rose_bush[half=lower]");
                            this.set(dx, t + 2, dz, "rose_bush[half=upper]");
                        }
                    }
                }
            }
        }
    }

    private void tree(int x, int y, int z, double r, int salt) {
        switch (this.pal.trees) {
            case PALM:
                this.palm(x, y, z, 6 + (int)(r * 5.0), salt);
                break;
            case CHERRY:
                this.cherryTree(x, y, z, 4 + (int)(r * 3.0));
                break;
            case FUNGUS:
                this.fungus(x, y, z, 6 + (int)(r * 6.0), r < 0.5);
                break;
            case SNOWY:
                this.spruce(x, y, z, 9 + (int)(r * 8.0), true);
                break;
            case CRYSTAL:
                this.crystalTree(x, y, z, 4 + (int)(r * 4.0));
                break;
            default:
                if (r < 0.62) {
                    this.spruce(x, y, z, 9 + (int)(this.rnd(salt, 1, 84) * 8.0), false);
                } else if (r < 0.9) {
                    this.roundTree(x, y, z, 4 + (int)(this.rnd(salt, 2, 85) * 3.0), this.rnd(salt, 3, 86) < 0.5);
                } else if (Math.hypot(x, z) < this.townR) {
                    int h = 3 + (int)(this.rnd(salt, 4, 87) * 3.0);

                    for (int k = 1; k <= h; k++) {
                        this.set(x, y - 1 + k, z, k == h ? "chiseled_stone_bricks" : "stone_bricks");
                    }

                    this.roundTree(x, y + h, z, 2, true);
                } else {
                    this.spruce(x, y, z, 12 + (int)(this.rnd(salt, 5, 84) * 6.0), false);
                }
        }
    }

    private boolean treeRoom(int x, int z) {
        for (int ax = -2; ax <= 2; ax++) {
            for (int az = -2; az <= 2; az++) {
                byte p = this.planAt(x + ax, z + az);
                if (p != 1 && p != 5) {
                    return false;
                }
            }
        }

        return this.planAt(x, z) == 1;
    }

    private void spruce(int x, int y0, int z, int h, boolean snowy) {
        String leaf = leaves("spruce_leaves");
        int topY = y0 + h;
        int maxR = h >= 13 ? 4 : 3;

        for (int y = y0 + 2; y <= topY; y++) {
            int fromTop = topY - y;
            int r = fromTop % 2 == 1 ? Math.min(maxR, 1 + fromTop / 3) : Math.max(1, Math.min(maxR - 1, fromTop / 3));

            for (int ax = -r; ax <= r; ax++) {
                for (int az = -r; az <= r; az++) {
                    double d = Math.sqrt(ax * ax + az * az);
                    if (d <= r + 0.35 && (ax != 0 || az != 0) && this.free(x + ax, y, z + az) && (!(d > r - 0.6) || !(this.rnd3(x + ax, y, z + az, 93) < 0.25))
                        )
                     {
                        this.set(x + ax, y, z + az, leaf);
                    }
                }
            }
        }

        for (int i = 0; i < h; i++) {
            this.set(x, y0 + i, z, log("spruce_log", 'y'));
        }

        this.set(x, topY, z, leaf);
        this.set(x, topY + 1, z, leaf);
        if (snowy) {
            for (int ax = -maxR; ax <= maxR; ax++) {
                for (int azx = -maxR; azx <= maxR; azx++) {
                    for (int y = topY + 1; y >= y0 + 2; y--) {
                        String b = this.get(x + ax, y, z + azx);
                        if (b != null && b.contains("spruce_leaves")) {
                            if (this.free(x + ax, y + 1, z + azx)) {
                                this.set(x + ax, y + 1, z + azx, "snow[layers=2]");
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void roundTree(int x, int y0, int z, int trunk, boolean azalea) {
        for (int i = 0; i < trunk; i++) {
            this.set(x, y0 + i, z, log(azalea ? "oak_log" : "dark_oak_log", 'y'));
        }

        double r = trunk >= 4 ? 3.1 : 2.3;
        int cy = y0 + trunk;
        int ri = (int)Math.ceil(r);

        for (int ax = -ri; ax <= ri; ax++) {
            for (int ay = -ri; ay <= ri; ay++) {
                for (int az = -ri; az <= ri; az++) {
                    double d = Math.sqrt(ax * ax + ay * ay * 1.6 + az * az);
                    if (!(d > r) && this.free(x + ax, cy + ay, z + az)) {
                        double q = this.rnd3(x + ax, cy + ay, z + az, 94);
                        if (!(d > r - 0.8) || !(q < 0.3)) {
                            String leaf = azalea ? (q < 0.35 ? "flowering_azalea_leaves" : (q < 0.7 ? "azalea_leaves" : "oak_leaves")) : "dark_oak_leaves";
                            this.set(x + ax, cy + ay, z + az, leaves(leaf));
                        }
                    }
                }
            }
        }
    }

    private void palm(int x, int y0, int z, int h, int salt) {
        int dir = (int)(this.rnd(salt, 7, 95) * 4.0);
        double lean = 0.18 + this.rnd(salt, 8, 95) * 0.12;
        double px = x;
        double pz = z;
        int tx = x;
        int tz = z;

        for (int i = 0; i < h; i++) {
            tx = (int)Math.round(px);
            tz = (int)Math.round(pz);
            this.set(tx, y0 + i, tz, log("jungle_log", 'y'));
            px += FX[dir] * lean * ((double)i / h) * 1.6;
            pz += FZ[dir] * lean * ((double)i / h) * 1.6;
        }

        int cy = y0 + h;
        this.set(tx, cy, tz, leaves("jungle_leaves"));

        for (int f = 0; f < 8; f++) {
            double a = (Math.PI * 2) * f / 8.0 + this.rnd(salt, f, 96) * 0.3;

            for (int s = 1; s <= 4; s++) {
                int lx = tx + (int)Math.round(Math.cos(a) * s);
                int lz = tz + (int)Math.round(Math.sin(a) * s);
                int ly = cy - (s >= 3 ? s - 2 : 0);
                if (this.free(lx, ly, lz)) {
                    this.set(lx, ly, lz, leaves("jungle_leaves"));
                }
            }
        }

        this.set(tx + 1, cy - 1, tz, "cocoa[age=2,facing=west]");
    }

    private void cherryTree(int x, int y0, int z, int trunk) {
        for (int i = 0; i < trunk; i++) {
            this.set(x, y0 + i, z, log("cherry_log", 'y'));
        }

        double r = 3.4;
        int cy = y0 + trunk;
        int ri = (int)Math.ceil(r);

        for (int ax = -ri; ax <= ri; ax++) {
            for (int ay = -2; ay <= 2; ay++) {
                for (int az = -ri; az <= ri; az++) {
                    double d = Math.sqrt(ax * ax + ay * ay * 2.4 + az * az);
                    if (!(d > r) && this.free(x + ax, cy + ay, z + az) && (!(d > r - 0.8) || !(this.rnd3(x + ax, cy + ay, z + az, 97) < 0.3))) {
                        this.set(x + ax, cy + ay, z + az, leaves("cherry_leaves"));
                    }
                }
            }
        }

        for (int ax = -4; ax <= 4; ax++) {
            for (int azx = -4; azx <= 4; azx++) {
                if (Math.hypot(ax, azx) <= 4.0 && this.free(x + ax, y0, z + azx) && this.rnd(x + ax, z + azx, 98) < 0.35 && this.planAt(x + ax, z + azx) == 1) {
                    this.set(
                        x + ax,
                        y0,
                        z + azx,
                        "pink_petals[facing="
                            + FACE[(int)(this.rnd(x + ax, z + azx, 99) * 4.0)]
                            + ",flower_amount="
                            + (1 + (int)(this.rnd(x + ax, z + azx, 100) * 4.0))
                            + "]"
                    );
                }
            }
        }
    }

    private void fungus(int x, int y0, int z, int h, boolean crimson) {
        String stem = crimson ? "crimson_stem" : "warped_stem";
        String cap = crimson ? "nether_wart_block" : "warped_wart_block";

        for (int i = 0; i < h; i++) {
            this.set(x, y0 + i, z, log(stem, 'y'));
        }

        double r = 3.2 + (h - 6) * 0.15;
        int cy = y0 + h;
        int ri = (int)Math.ceil(r);

        for (int ax = -ri; ax <= ri; ax++) {
            for (int az = -ri; az <= ri; az++) {
                for (int ay = 0; ay <= ri; ay++) {
                    double d = Math.sqrt(ax * ax + az * az + ay * ay * 2.2);
                    if (d <= r && d > r - 1.3 && this.free(x + ax, cy + ay - 1, z + az)) {
                        this.set(x + ax, cy + ay - 1, z + az, this.rnd3(x + ax, ay, z + az, 101) < 0.12 ? "shroomlight" : cap);
                    }
                }

                double dd = Math.hypot(ax, az);
                if (crimson && dd > r - 1.3 && dd <= r && this.rnd(x + ax, z + az, 102) < 0.3) {
                    int len = 1 + (int)(this.rnd(x + ax, z + az, 103) * 3.0);

                    for (int k = 1; k <= len; k++) {
                        this.set(x + ax, cy - 1 - k, z + az, k == len ? "weeping_vines[age=25]" : "weeping_vines_plant");
                    }
                }
            }
        }
    }

    private void crystalTree(int x, int y0, int z, int h) {
        for (int i = 0; i < h; i++) {
            this.set(x, y0 + i, z, log("purpur_pillar", 'y'));
        }

        int cy = y0 + h;

        for (int ax = -2; ax <= 2; ax++) {
            for (int ay = -1; ay <= 3; ay++) {
                for (int az = -2; az <= 2; az++) {
                    double m = Math.abs(ax) + Math.abs(az) + Math.abs(ay - 1) * 0.8;
                    if (m <= 2.6 && this.free(x + ax, cy + ay, z + az)) {
                        this.set(x + ax, cy + ay, z + az, m > 1.6 ? "amethyst_block" : "purple_stained_glass");
                    }
                }
            }
        }

        this.set(x, cy + 4, z, "end_rod[facing=up]");

        for (int f = 0; f < 4; f++) {
            this.set(x + FX[f] * 3, cy + 1, z + FZ[f] * 3, "end_rod[facing=" + FACE[f] + "]");
        }
    }

    private void landmarks() {
        this.arch();
        this.waterfall();

        for (double[] s : this.spires) {
            this.spire((int)Math.round(s[0]), (int)Math.round(s[1]), (int)s[3], s[2], s[4] > 0.0, (int)s[5]);
        }
    }

    private void arch() {
        int ax = this.archX;
        int ra = 10;
        int ha = 14;
        Set<Long> placed = new HashSet<>();
        double[][] curve = new double[97][];

        for (int s = 0; s <= 96; s++) {
            double t = Math.PI * s / 96.0;
            curve[s] = new double[]{ra * Math.cos(t), ha * Math.sin(t), 2.4 + 1.4 * (1.0 - Math.sin(t))};
        }

        for (int x = ax - 6; x <= ax + 6; x++) {
            for (int z = -ra - 6; z <= ra + 6; z++) {
                for (int y = -4; y <= ha + 5; y++) {
                    double best = 1.0E9;
                    double rad = 0.0;

                    for (double[] p : curve) {
                        double dd = (x - ax) * (x - ax) + (z - p[0]) * (z - p[0]) + (y - p[1]) * (y - p[1]);
                        if (dd < best) {
                            best = dd;
                            rad = p[2];
                        }
                    }

                    rad += (this.noise(x * 0.35 + y * 0.25, z * 0.35, 71) - 0.5) * 1.6;
                    if (Math.sqrt(best) <= rad) {
                        this.set(x, y, z, this.rock(x, y, z));
                        placed.add(key(x, y, z));
                    }
                }
            }
        }

        this.mossTops(placed);
    }

    private static long key(int x, int y, int z) {
        return (long)(x + 4096) << 40 | (long)(y + 4096) << 20 | z + 4096;
    }

    private void mossTops(Set<Long> placed) {
        for (long k : placed) {
            int x = (int)(k >>> 40) - 4096;
            int y = (int)(k >>> 20 & 1048575L) - 4096;
            int z = (int)(k & 1048575L) - 4096;
            if (!placed.contains(key(x, y + 1, z)) && this.free(x, y + 1, z) && y > this.topAt(x, z)) {
                double r = this.rnd3(x, y, z, 72);
                if (r < 0.55) {
                    String topBlock = switch (this.theme) {
                        case DESERT -> r < 0.35 ? "sand" : "smooth_sandstone";
                        default -> r < 0.35 ? "moss_block" : "grass_block[snowy=false]";
                        case NETHER -> r < 0.35 ? "crimson_nylium" : "warped_nylium";
                        case WINTER -> "snow_block";
                        case CELESTIAL -> r < 0.35 ? "end_stone" : "end_stone_bricks";
                    };
                    this.set(x, y, z, topBlock);
                    if (r < 0.12 && this.theme == TownDesign.Theme.TOWN) {
                        this.set(x, y + 1, z, "moss_carpet");
                    } else if (r < 0.3 && this.theme == TownDesign.Theme.WINTER) {
                        this.set(x, y + 1, z, "snow[layers=1]");
                    }
                }
            }
        }
    }

    private void waterfall() {
        int wx = this.fall[0];
        int wz = this.fall[1];
        int px = this.fall[2];
        int pz = this.fall[3];
        int rc = 9;
        int hc = 18;
        Map<Long, Integer> heights = new HashMap<>();
        Set<Long> placed = new HashSet<>();

        for (int x = wx - rc - 3; x <= wx + rc + 3; x++) {
            for (int z = wz - rc - 3; z <= wz + rc + 3; z++) {
                double d = Math.hypot(x - wx, z - wz) + (this.noise(x * 0.3, z * 0.3, 79) - 0.5) * 3.0;
                if (!(d > rc)) {
                    int h = (int)Math.round(hc * Math.sqrt(Math.max(0.0, 1.0 - d / rc * (d / rc))) * (0.7 + 0.4 * this.noise(x * 0.25, z * 0.25, 80)));
                    h = Math.max(h, this.topAt(x, z) + 1);
                    heights.put(key(x, 0, z), h);

                    for (int y = -6; y <= h; y++) {
                        this.set(x, y, z, this.rock(x, y, z));
                        placed.add(key(x, y, z));
                    }
                }
            }
        }

        this.mossTops(placed);
        double ux = px - wx;
        double uz = pz - wz;
        double ul = Math.hypot(ux, uz);
        ux /= ul;
        uz /= ul;
        int sx = (int)Math.round(wx + ux * rc * 0.45);
        int sz = (int)Math.round(wz + uz * rc * 0.45);
        Integer sh = heights.get(key(sx, 0, sz));
        int spring = sh == null ? hc / 2 : sh;
        int perpX = (int)Math.round(-uz);
        int perpZ = (int)Math.round(ux);

        for (int s = 0; s <= rc + 1; s++) {
            int x = (int)Math.round(sx + ux * s);
            int zx = (int)Math.round(sz + uz * s);

            for (int w = 0; w <= 1; w++) {
                int xx = x + perpX * w;
                int zz = zx + perpZ * w;
                Integer h = heights.get(key(xx, 0, zz));
                int ht = h == null ? 1 : h;
                if (s == 0) {
                    this.set(xx, spring, zz, this.pal.iceFalls ? "packed_ice" : this.water());
                } else {
                    for (int y = 1; y <= Math.min(spring, ht + 1); y++) {
                        this.set(xx, y, zz, this.falling());
                    }
                }
            }
        }

        for (int x = px - 7; x <= px + 7; x++) {
            for (int zx = pz - 7; zx <= pz + 7; zx++) {
                double d = Math.hypot(x - px, zx - pz) + (this.noise(x * 0.4, zx * 0.4, 81) - 0.5) * 1.5;
                if (!(d <= 5.2)) {
                    if (d <= 6.4 && this.planAt(x, zx) != 8) {
                        this.set(x, 0, zx, this.rnd(x, zx, 83) < 0.5 ? this.pal.pondEdge1 : this.pal.pondEdge2);
                        if (this.rnd(x, zx, 84) < 0.3 || "lava".equals(this.pal.liquid)) {
                            this.set(
                                x,
                                1,
                                zx,
                                "lava".equals(this.pal.liquid) ? "nether_brick_fence" : (this.rnd(x, zx, 85) < 0.5 ? this.pal.pondEdge1 : this.pal.pondEdge2)
                            );
                        }
                    }
                } else {
                    this.set(x, -2, zx, this.pal.pondFloor);
                    this.set(x, -1, zx, d < 3.5 ? (this.pal.iceFalls ? "ice" : this.water()) : this.pal.pondFloor);
                    this.set(x, 0, zx, this.pal.iceFalls ? "ice" : this.water());

                    for (int y = 1; y <= 4; y++) {
                        if (!placed.contains(key(x, y, zx))) {
                            this.set(x, y, zx, "air");
                        }
                    }

                    if (d > 2.5 && this.rnd(x, zx, 82) < 0.06 && "water".equals(this.pal.liquid) && !this.pal.iceFalls) {
                        this.set(x, 1, zx, "lily_pad");
                    }
                }
            }
        }

        int x = px;
        int zxx = pz;

        for (int s = 0; s < 20; s++) {
            x += (int)Math.signum((float)(-px)) * (s % 2 == 0 ? 1 : 0);
            zxx += (int)Math.signum((float)(-pz)) * (s % 2 == 1 ? 1 : 0);
            byte p = this.planAt(x, zxx);
            if (p == 2) {
                break;
            }

            if (p == 1 && Math.hypot(x - px, zxx - pz) > 6.4) {
                this.set(x, 0, zxx, this.paving(x, zxx));
                this.mark(x, zxx, (byte)6);
            }
        }
    }

    private void spire(int sx, int sz, int h, double rb, boolean tree, int salt) {
        int t = this.topAt(sx, sz);
        double lx = (this.rnd(salt, 1, 73) - 0.5) * 0.22;
        double lz = (this.rnd(salt, 2, 73) - 0.5) * 0.22;
        Set<Long> placed = new HashSet<>();
        int topY = t;

        for (int y = -8; y <= t + h; y++) {
            int yy = Math.max(0, y - t);
            double rr = rb * Math.pow(Math.max(0.0, 1.0 - (double)yy / (h + 1)), 0.7) + 0.6;
            double ox = sx + lx * yy;
            double oz = sz + lz * yy;
            int r0 = (int)Math.ceil(rr + 1.0);

            for (int x = (int)Math.floor(ox) - r0; x <= (int)Math.ceil(ox) + r0; x++) {
                for (int z = (int)Math.floor(oz) - r0; z <= (int)Math.ceil(oz) + r0; z++) {
                    double dd = Math.hypot(x - ox, z - oz) + (this.noise(x * 0.45 + y * 0.3, z * 0.45, 74 + salt) - 0.5) * 1.6;
                    if (dd <= rr) {
                        this.set(x, y, z, this.rock(x, y, z));
                        placed.add(key(x, y, z));
                        topY = Math.max(topY, y);
                    }
                }
            }
        }

        this.mossTops(placed);
        if (tree) {
            int x = (int)Math.round(sx + lx * h);
            int zx = (int)Math.round(sz + lz * h);
            int y = topY;

            while (y > t && this.free(x, y, zx)) {
                y--;
            }

            this.smallTree(x, y + 1, zx, salt);
        }
    }

    private void smallTree(int x, int y, int z, int salt) {
        switch (this.pal.trees) {
            case PALM:
                this.palm(x, y, z, 5, salt);
                break;
            case CHERRY:
                this.cherryTree(x, y, z, 3);
                break;
            case FUNGUS:
                this.fungus(x, y, z, 5, salt % 2 == 0);
                break;
            case SNOWY:
                this.spruce(x, y, z, 7, true);
                break;
            case CRYSTAL:
                this.crystalTree(x, y, z, 3);
                break;
            default:
                this.roundTree(x, y, z, 3, true);
        }
    }

    private void sky() {
        double r = this.radius;
        double[][] isles = new double[][]{
            {300.0, 1.1, 26.0, 8.0, 0.0}, {205.0, 1.12, 20.0, 7.0, 1.0}, {35.0, 1.08, 30.0, 6.0, 2.0}, {140.0, 1.15, 23.0, 7.0, 3.0}
        };

        for (double[] is : isles) {
            double a = Math.toRadians(is[0]);
            this.floatingIsland((int)Math.round(Math.cos(a) * r * is[1]), (int)is[2], (int)Math.round(Math.sin(a) * r * is[1]), (int)is[3], (int)is[4]);
        }

        for (int i = 0; i < 7; i++) {
            double a = this.rnd(i, 7, 75) * Math.PI * 2.0;
            double d = r * (0.95 + this.rnd(i, 8, 75) * 0.25);
            this.floatingIsland(
                (int)Math.round(Math.cos(a) * d),
                10 + (int)(this.rnd(i, 9, 75) * 26.0),
                (int)Math.round(Math.sin(a) * d),
                2 + (int)(this.rnd(i, 10, 75) * 2.0),
                -1
            );
        }

        switch (this.pal.sky) {
            case BALLOONS:
                this.balloon((int)Math.round(r * 0.45), 30, (int)Math.round(-r * 0.7), "red_wool", "yellow_wool");
                this.balloon((int)Math.round(-r * 0.75), 25, (int)Math.round(r * 0.25), "blue_wool", "white_wool");
                this.balloon((int)Math.round(r * 0.55), 22, (int)Math.round(r * 0.6), "orange_wool", "white_wool");
                break;
            case LANTERNS:
                for (int i = 0; i < 40; i++) {
                    double a = this.rnd(i, 11, 76) * Math.PI * 2.0;
                    double d = r * (0.2 + this.rnd(i, 12, 76) * 0.95);
                    int x = (int)Math.round(Math.cos(a) * d);
                    int z = (int)Math.round(Math.sin(a) * d);
                    int y = 18 + (int)(this.rnd(i, 13, 76) * 24.0);
                    this.set(x, y, z, "red_wool");
                    this.set(x, y - 1, z, "shroomlight");
                    this.set(x, y + 1, z, slab("dark_oak_slab"));
                }
            case FUNGI:
            case CRYSTALS:
                break;
            default:
                this.airship((int)Math.round(r * 0.42), 31, (int)Math.round(-r * 0.74), true, 1);
                this.airship((int)Math.round(-r * 0.8), 24, (int)Math.round(r * 0.22), false, 1);
        }
    }

    private void floatingIsland(int fx, int fy, int fz, int r, int deco) {
        for (int x = fx - r - 3; x <= fx + r + 3; x++) {
            for (int z = fz - r - 3; z <= fz + r + 3; z++) {
                double d = Math.hypot(x - fx, z - fz) + (this.noise(x * 0.35, z * 0.35, 76) - 0.5) * 2.5;
                if (!(d > r)) {
                    int depth = (int)Math.round((r - d) * 1.6 + 2.0 + this.noise(x * 0.3, z * 0.3, 77) * 3.0);
                    int bump = deco < 0 ? 0 : (int)Math.round((1.0 - d / r) * 1.4);

                    for (int y = fy - depth; y <= fy + bump; y++) {
                        int below = fy + bump - y;
                        String b = deco < 0 ? this.rock(x, y, z) : (below == 0 ? this.surface(x, z) : (below <= 2 ? this.pal.sub : this.rock(x, y, z)));
                        this.set(x, y, z, b);
                    }

                    if (deco >= 0 && this.pal.plants.length > 0 && this.rnd(x, z, 78) < 0.16 && d < r - 1) {
                        this.set(x, fy + bump + 1, z, this.pick(this.pal.plants, this.rnd(x, z, 79)));
                    }

                    if (this.pal.sky == TownDesign.Sky.FUNGI && this.rnd(x, z, 104) < 0.05) {
                        this.set(x, fy - depth - 1, z, "glowstone");
                    }
                }
            }
        }

        switch (deco) {
            case 0:
                this.house(new TownDesign.Lot(fx - 3, fz - 2, fx + 3, fz + 3, 2), fy + 1);
                break;
            case 1:
                this.tree(fx, fy + 3, fz, 0.3, fx);
                break;
            case 2:
                this.tree(fx, fy + 3, fz, 0.7, fz);
                break;
            case 3:
                this.tree(fx - 2, fy + 2, fz + 1, 0.2, fx + 1);
                this.smallTree(fx + 3, fy + 2, fz - 2, fz + 1);
                break;
            default:
                if (this.pal.sky == TownDesign.Sky.CRYSTALS && this.rnd(fx, fz, 105) < 0.6) {
                    this.set(fx, fy + 1, fz, "amethyst_block");
                    this.set(fx, fy + 2, fz, "amethyst_cluster[facing=up,waterlogged=false]");
                }
        }
    }

    private void balloon(int ox, int oy, int oz, String c1, String c2) {
        int r = 5;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -r; y <= r + 1; y++) {
                    double d = Math.sqrt(x * x + z * z + (y > 0 ? y * y * 0.8 : y * y * 1.25));
                    if (d <= r && d > r - 1.2) {
                        double ang = Math.atan2(z, x);
                        this.set(ox + x, oy + y, oz + z, (int)Math.floor((ang + Math.PI) / (Math.PI / 4)) % 2 == 0 ? c1 : c2);
                    }
                }
            }
        }

        int by = oy - r - 4;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                this.set(ox + x, by, oz + z, "spruce_planks");
                if (Math.abs(x) == 1 || Math.abs(z) == 1) {
                    this.set(ox + x, by + 1, oz + z, slab("spruce_slab"));
                }
            }
        }

        for (int yx = by + 1; yx < oy - r + 1; yx++) {
            this.set(ox - 1, yx, oz - 1, "iron_chain[axis=y]");
            this.set(ox + 1, yx, oz + 1, "iron_chain[axis=y]");
        }

        this.set(ox, by + 1, oz, "campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
    }

    private void airship(int ox, int oy, int oz, boolean alongX, int dir) {
        int hl = 13;
        int hw = 4;
        String keel = log("dark_oak_log", (char)(alongX ? 'x' : 'z'));
        String deck = log("stripped_spruce_log", (char)(alongX ? 'x' : 'z'));
        String spar = log("dark_oak_log", (char)(alongX ? 'z' : 'x'));
        int[] railW = new int[hl * 2 + 1];

        for (int u = -hl; u <= hl; u++) {
            double fu = u >= 0 ? Math.sqrt(Math.max(0.0, 1.0 - Math.pow(u / (hl + 0.5), 2.0))) : 1.0 - Math.pow(-u / (hl + 0.5), 6.0) * 0.45;
            int kmin = u > 0 ? (int)Math.round(Math.pow((double)u / hl, 2.0) * 3.0) : (int)Math.round(Math.pow((double)(-u) / hl, 4.0) * 2.0);
            railW[u + hl] = -1;

            for (int k = kmin; k <= 5; k++) {
                double hk = hw * fu * (0.45 + 0.55 * Math.pow(k / 5.0, 0.6));
                int iw = (int)Math.round(hk);
                if (iw >= 0 && !(fu <= 0.05)) {
                    for (int v = -iw; v <= iw; v++) {
                        boolean shell = Math.abs(v) == iw || k == kmin || u == -hl;
                        String b = null;
                        if (k == 5) {
                            b = Math.abs(v) == iw ? "dark_oak_planks" : deck;
                        } else if (k == kmin && v == 0) {
                            b = keel;
                        } else if (shell) {
                            b = k == 3 ? "dark_oak_planks" : "spruce_planks";
                        }

                        if (b != null) {
                            this.ship(ox, oy, oz, alongX, dir, u, k, v, b);
                        }
                    }

                    if (k == 5) {
                        railW[u + hl] = iw;
                    }
                }
            }
        }

        for (int u = -hl; u <= hl; u++) {
            int iw = railW[u + hl];
            if (iw >= 0) {
                int prev = u > -hl ? railW[u + hl - 1] : -1;
                int next = u < hl ? railW[u + hl + 1] : -1;

                for (int v = -iw; v <= iw; v++) {
                    boolean rim = Math.abs(v) == iw || u == -hl || Math.abs(v) > prev || Math.abs(v) > next;
                    if (rim) {
                        this.ship(ox, oy, oz, alongX, dir, u, 6, v, slab("spruce_slab"));
                    }
                }
            }
        }

        int[][] masts = new int[][]{{-3, 17}, {5, 19}};

        for (int[] m : masts) {
            int mu = m[0];
            int mh = m[1];

            for (int kx = 1; kx <= mh; kx++) {
                this.ship(ox, oy, oz, alongX, dir, mu, kx, 0, log("spruce_log", 'y'));
            }

            for (int vx = -6; vx <= 6; vx++) {
                this.ship(ox, oy, oz, alongX, dir, mu, mh - 2, vx, spar);
                this.ship(ox, oy, oz, alongX, dir, mu, 8, vx, spar);
            }

            for (int kx = 9; kx <= mh - 3; kx++) {
                for (int vx = -5; vx <= 5; vx++) {
                    boolean bulge = Math.abs(vx) <= 3 && kx >= 10 && kx <= mh - 5;
                    this.ship(ox, oy, oz, alongX, dir, mu + (bulge ? 2 : 1), kx, vx, "white_wool");
                }
            }

            String flag = this.theme == TownDesign.Theme.WINTER ? "light_blue_wool" : "cyan_wool";
            this.ship(ox, oy, oz, alongX, dir, mu - 1, mh, 0, flag);
            this.ship(ox, oy, oz, alongX, dir, mu - 2, mh, 0, flag);
            this.ship(ox, oy, oz, alongX, dir, mu - 1, mh - 1, 0, flag);
        }

        for (int ux = -12; ux <= -8; ux++) {
            for (int vx = -3; vx <= 3; vx++) {
                boolean wall = ux == -12 || ux == -8 || Math.abs(vx) == 3;

                for (int kx = 6; kx <= 8; kx++) {
                    if (wall) {
                        boolean window = kx == 7 && (ux == -10 || vx == 0);
                        this.ship(ox, oy, oz, alongX, dir, ux, kx, vx, window ? "glass" : "dark_oak_planks");
                    }
                }

                this.ship(ox, oy, oz, alongX, dir, ux, 9, vx, this.theme == TownDesign.Theme.WINTER ? "snow_block" : slab("dark_oak_slab"));
            }
        }

        this.ship(ox, oy, oz, alongX, dir, -12, 10, -3, "lantern[hanging=false,waterlogged=false]");
        this.ship(ox, oy, oz, alongX, dir, -12, 10, 3, "lantern[hanging=false,waterlogged=false]");

        for (int kxx = 1; kxx <= 5; kxx++) {
            this.ship(ox, oy, oz, alongX, dir, -hl - 1, kxx, 0, "dark_oak_planks");
        }

        for (int s = 1; s <= 5; s++) {
            this.ship(ox, oy, oz, alongX, dir, hl - 1 + s, 5 + s / 2, 0, "spruce_fence");
        }
    }

    private void ship(int ox, int oy, int oz, boolean alongX, int dir, int u, int k, int v, String b) {
        int x = alongX ? ox + u * dir : ox + v;
        int z = alongX ? oz + v : oz + u * dir;
        this.set(x, oy + k, z, b);
    }

    private void snowCover() {
        for (int dx = -this.ext; dx <= this.ext; dx++) {
            for (int dz = -this.ext; dz <= this.ext; dz++) {
                byte p = this.planAt(dx, dz);
                if (p == 1 || p == 5 || p == 8) {
                    for (int y = this.up - 1; y > -this.down; y--) {
                        String b = this.get(dx, y, dz);
                        if (b != null && !b.endsWith("air")) {
                            if ((b.contains("snow_block") || b.contains("stone") || b.contains("ice") || b.contains("diorite") || b.contains("calcite"))
                                && this.free(dx, y + 1, dz)
                                && !b.contains("[")) {
                                this.set(dx, y + 1, dz, "snow[layers=1]");
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private record Lot(int x0, int z0, int x1, int z1, int face) {
    }

    private static enum Monument {
        SWORD,
        OBELISK,
        PAGODA,
        PORTAL,
        XMAS,
        CRYSTAL;
    }

    private static final class Pal {
        String surface = "grass_block[snowy=false]";
        String sub = "dirt";
        String[] rock;
        String band;
        String[] paving;
        String patch;
        String curb;
        String curb2;
        String plazaLine;
        String plazaRadial;
        String plazaBorder;
        String[] plazaFill;
        String[] plasters;
        String[] frames;
        String[] bases;
        String[][] roofs;
        TownDesign.Roof roof = TownDesign.Roof.GABLE;
        String parapet = "stone_brick_wall";
        String[] banners;
        String door = "dark_oak_door";
        String interior = "spruce_planks";
        String liquid = "water";
        boolean iceFalls;
        TownDesign.Trees trees;
        TownDesign.Monument monument;
        TownDesign.Sky sky;
        String[] hedges;
        String[] plants;
        boolean snow;
        String lampBase = "stone_brick_wall";
        String lampPost = "spruce_fence";
        String lampLight = "lantern[hanging=false,waterlogged=false]";
        String hanging = "lantern[hanging=true,waterlogged=false]";
        String pillar = "stripped_spruce_log";
        String[] pavRoof = new String[]{"deepslate_tile_stairs", "deepslate_tiles", "deepslate_tile_slab"};
        String pavFloor = "spruce_planks";
        String pavEdge = "stripped_spruce_log";
        String pavFence = "spruce_fence";
        String pavBeam = "dark_oak_log";
        String pondFloor = "clay";
        String pondEdge1 = "mossy_cobblestone";
        String pondEdge2 = "stone";
        String bench = "spruce_stairs";
        String plant = "potted_azalea_bush";
        String pedestal = "chiseled_stone_bricks";
        String pedestalTop = "polished_andesite";
        String wood = "spruce";
    }

    private static enum Roof {
        GABLE,
        FLAT,
        PAGODA;
    }

    public interface Sink {
        void set(int var1, int var2, int var3, String var4);
    }

    private static enum Sky {
        AIRSHIPS,
        BALLOONS,
        LANTERNS,
        FUNGI,
        CRYSTALS;
    }

    public static enum Theme {
        TOWN("Medieval Town"),
        DESERT("Desert Bazaar"),
        SAKURA("Sakura Village"),
        NETHER("Infernal Citadel"),
        WINTER("Frostfall Village"),
        CELESTIAL("Celestial Isles");

        public final String display;

        private Theme(String display) {
            this.display = display;
        }
    }

    private static enum Trees {
        CLASSIC,
        PALM,
        CHERRY,
        FUNGUS,
        SNOWY,
        CRYSTAL;
    }
}
