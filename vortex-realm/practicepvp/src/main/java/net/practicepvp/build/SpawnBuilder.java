package net.practicepvp.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.practicepvp.arena.BlockJobs;
import net.practicepvp.arena.ColosseumDesign;
import net.practicepvp.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Stairs;

public final class SpawnBuilder {
    private static SpawnBuilder.Palette palette(SpawnBuilder.Style s) {
        return switch (s) {
            case TOWN, DESERT, SAKURA, NETHER, WINTER, CELESTIAL, COLOSSEUM, CITADEL, ARCHIPELAGO, NEON, KEEP, JUNGLE, GRAND_HUB, TOWN_SQUARE, CASTLE_MARKET, PRACTICE_TOWN, ROYAL_CASTLE, COLOSSEUM_CITY, SKY_EMPIRE, VORTEX_ISLES, HUB -> new SpawnBuilder.Palette(
                Material.SMOOTH_STONE,
                Material.POLISHED_ANDESITE,
                Material.STONE_BRICKS,
                Material.QUARTZ_BLOCK,
                Material.CHISELED_STONE_BRICKS,
                Material.STONE_BRICK_WALL,
                Material.SEA_LANTERN,
                Material.OAK_LEAVES,
                Material.STONE_BRICKS,
                true,
                Material.STONE_BRICK_STAIRS,
                Material.POTTED_AZALEA_BUSH
            );
            case PVP -> new SpawnBuilder.Palette(
                Material.POLISHED_BLACKSTONE_BRICKS,
                Material.POLISHED_BLACKSTONE,
                Material.CHISELED_POLISHED_BLACKSTONE,
                Material.GOLD_BLOCK,
                Material.GILDED_BLACKSTONE,
                Material.POLISHED_BLACKSTONE_WALL,
                Material.SHROOMLIGHT,
                null,
                Material.BLACKSTONE,
                false,
                Material.POLISHED_BLACKSTONE_BRICK_STAIRS,
                Material.POTTED_CRIMSON_ROOTS
            );
            case SURVIVAL -> new SpawnBuilder.Palette(
                Material.MOSS_BLOCK,
                Material.MOSSY_STONE_BRICKS,
                Material.DIRT_PATH,
                Material.POLISHED_ANDESITE,
                Material.STONE_BRICKS,
                Material.SPRUCE_FENCE,
                Material.GLOWSTONE,
                Material.OAK_LEAVES,
                Material.DIRT,
                true,
                Material.SPRUCE_STAIRS,
                Material.POTTED_RED_TULIP
            );
            case LIFESTEAL -> new SpawnBuilder.Palette(
                Material.RED_NETHER_BRICKS,
                Material.NETHER_BRICKS,
                Material.POLISHED_BLACKSTONE,
                Material.NETHER_WART_BLOCK,
                Material.CHISELED_NETHER_BRICKS,
                Material.RED_NETHER_BRICK_WALL,
                Material.SHROOMLIGHT,
                null,
                Material.NETHER_BRICKS,
                false,
                Material.RED_NETHER_BRICK_STAIRS,
                Material.POTTED_CRIMSON_FUNGUS
            );
            case STRENGTH -> new SpawnBuilder.Palette(
                Material.PURPUR_BLOCK,
                Material.AMETHYST_BLOCK,
                Material.POLISHED_DEEPSLATE,
                Material.CRYING_OBSIDIAN,
                Material.PURPUR_PILLAR,
                Material.POLISHED_DEEPSLATE_WALL,
                Material.PEARLESCENT_FROGLIGHT,
                null,
                Material.DEEPSLATE_BRICKS,
                false,
                Material.PURPUR_STAIRS,
                Material.POTTED_ALLIUM
            );
            case RANKED -> new SpawnBuilder.Palette(
                Material.SMOOTH_SANDSTONE,
                Material.CUT_SANDSTONE,
                Material.CHISELED_SANDSTONE,
                Material.GOLD_BLOCK,
                Material.CUT_RED_SANDSTONE,
                Material.SANDSTONE_WALL,
                Material.OCHRE_FROGLIGHT,
                null,
                Material.SANDSTONE,
                false,
                Material.SMOOTH_SANDSTONE_STAIRS,
                Material.POTTED_CACTUS
            );
            case ECO -> new SpawnBuilder.Palette(
                Material.SPRUCE_PLANKS,
                Material.STRIPPED_SPRUCE_WOOD,
                Material.COBBLESTONE,
                Material.EMERALD_BLOCK,
                Material.SPRUCE_LOG,
                Material.SPRUCE_FENCE,
                Material.GLOWSTONE,
                Material.OAK_LEAVES,
                Material.COBBLESTONE,
                false,
                Material.SPRUCE_STAIRS,
                Material.POTTED_OAK_SAPLING
            );
        };
    }

    private SpawnBuilder() {
    }

    public static SpawnBuilder.Result plan(Location at, SpawnBuilder.Style style, int radius) {
        if (style == SpawnBuilder.Style.COLOSSEUM) {
            return colosseum(at, radius);
        } else if (style.theme != null) {
            return town(at, style.theme, radius);
        } else if (style.voxel()) {
            return voxel(at, style, radius);
        } else {
            World w = at.getWorld();
            int r = Math.max(6, Math.min(56, radius));
            Map<String, Location> features = new LinkedHashMap<>();
            List<Location> spots = new ArrayList<>();
            SpawnBuilder.Plan p = plaza(at, style, r, features, spots);
            Location spawn = new Location(w, p.cx + 0.5, p.cy + 1, p.cz + Math.max(4.0, r * 0.35) + 0.5, 180.0F, 0.0F);
            return new SpawnBuilder.Result(job(w, new ArrayList<>(p.writes.values()), style), spawn, spots, features, null);
        }
    }

    private static SpawnBuilder.Plan plaza(Location at, SpawnBuilder.Style style, int r, Map<String, Location> features, List<Location> spots) {
        World w = at.getWorld();
        SpawnBuilder.Palette pal = palette(style);
        SpawnBuilder.Plan p = new SpawnBuilder.Plan(at.getBlockX(), at.getBlockY() - 1, at.getBlockZ());
        boolean big = style == SpawnBuilder.Style.HUB || style == SpawnBuilder.Style.PVP;
        floor(p, pal, r, big);
        centre(p, pal);
        lamps(p, pal, r, style);
        benches(p, pal, r);
        if (big && r >= 18) {
            parkour(p, pal, w, features);
        }

        if (style == SpawnBuilder.Style.PVP && r >= 20) {
            balcony(p, pal, r, w, features);
        }

        spots.addAll(pedestals(p, pal, r, style.npcSlots, w));
        return p;
    }

    /** Receives the blocks of a design for a preview: block strings like "minecraft:stone_bricks[...]". */
    public interface BlockFeed {
        void block(int x, int y, int z, String block);
    }

    /**
     * Streams every block a style would build around (0, 100, 0) without touching the world. Big designs can run off
     * the main thread; the small plazas ({@code w} needed for their spots) must run on it.
     */
    public static void feed(SpawnBuilder.Style style, World w, int radius, SpawnBuilder.BlockFeed out) {
        if (style == SpawnBuilder.Style.COLOSSEUM) {
            int r = Math.max(26, Math.min(70, radius));
            ColosseumDesign d = new ColosseumDesign(ColosseumDesign.Style.LOBBY, (int)Math.round(r / 0.3), 7L, 0, 100, 0, 6, false, false);

            for (int x = d.minX(); x <= d.maxX(); x++) {
                for (int z = d.minZ(); z <= d.maxZ(); z++) {
                    d.column(x, z, out::block);
                }
            }
        } else if (style.theme != null) {
            TownDesign d = new TownDesign(0, 100, 0, Math.max(40, Math.min(80, radius)), 7L, style.theme);

            for (int x = d.minX(); x <= d.maxX(); x++) {
                for (int z = d.minZ(); z <= d.maxZ(); z++) {
                    d.column(x, z, out::block);
                }
            }
        } else if (style.voxel()) {
            Voxels d = design(style, 0, 100, 0, radius, 7L);

            for (int x = d.minX(); x <= d.maxX(); x++) {
                for (int z = d.minZ(); z <= d.maxZ(); z++) {
                    d.column(x, z, out::block);
                }
            }
        } else {
            SpawnBuilder.Plan p = plaza(new Location(w, 0.5, 101.0, 0.5), style, Math.max(6, Math.min(56, radius)), new LinkedHashMap<>(), new ArrayList<>());

            for (Object[] wr : p.writes.values()) {
                out.block((Integer)wr[0], (Integer)wr[1], (Integer)wr[2], ((BlockData)wr[3]).getAsString());
            }
        }
    }

    private static SpawnBuilder.Result colosseum(Location at, int radius) {
        World w = at.getWorld();
        int r = Math.max(26, Math.min(70, radius));
        int size = (int)Math.round(r / 0.3);
        int floorY = at.getBlockY() - 1;
        ColosseumDesign d = new ColosseumDesign(ColosseumDesign.Style.LOBBY, size, 7L, at.getBlockX(), floorY, at.getBlockZ(), 6, false, false);
        List<Location> spots = new ArrayList<>();

        for (int[] pd : d.pedestals(8)) {
            Location l = new Location(w, d.cx + pd[0] + 0.5, floorY + 2, d.cz + pd[1] + 0.5);
            l.setYaw(yawTo(l, d.cx + 0.5, d.cz + 0.5));
            spots.add(l);
        }

        Location spawn = new Location(w, d.cx + 0.5, floorY + 1, d.cz + 9.5, 180.0F, 0.0F);
        Cuboid area = new Cuboid(d.minX(), d.bottomY, d.minZ(), d.maxX(), d.topY, d.maxZ());
        return new SpawnBuilder.Result(colosseumJob(w, d), spawn, spots, new LinkedHashMap<>(), area);
    }

    /** The voxel design of a big style (nothing is placed): used to build it and to preview it. */
    public static Voxels design(SpawnBuilder.Style style, int x, int floorY, int z, int radius, long seed) {
        return (Voxels)(switch (style) {
            case CITADEL -> new CitadelDesign(x, floorY, z, Math.max(80, Math.min(128, radius)), seed);
            case ARCHIPELAGO -> new ArchipelagoDesign(x, floorY, z, Math.max(60, Math.min(100, radius)), seed);
            case NEON -> new NeonCityDesign(x, floorY, z, Math.max(56, Math.min(96, radius)), seed);
            case KEEP -> new KeepDesign(x, floorY, z, Math.max(56, Math.min(96, radius)), seed);
            default -> new JungleTempleDesign(x, floorY, z, Math.max(56, Math.min(96, radius)), seed);
            case GRAND_HUB -> new GrandHubDesign(x, floorY, z, Math.max(120, Math.min(170, radius)), seed);
            case TOWN_SQUARE -> new ClubHubDesign(x, floorY, z, Math.max(100, Math.min(140, radius)), seed);
            case CASTLE_MARKET -> new CastleMarketDesign(x, floorY, z, Math.max(80, Math.min(130, radius)), seed);
            case PRACTICE_TOWN -> new PracticeTownDesign(x, floorY, z, Math.max(104, Math.min(130, radius)), seed);
            case ROYAL_CASTLE -> new RoyalCastleDesign(x, floorY, z, Math.max(110, Math.min(140, radius)), seed);
            case COLOSSEUM_CITY -> new ColosseumCityDesign(x, floorY, z, Math.max(200, Math.min(250, radius)), seed);
            case SKY_EMPIRE -> new SkyEmpireDesign(x, floorY, z, Math.max(200, Math.min(250, radius)), seed);
            case VORTEX_ISLES -> new VortexIslesDesign(x, floorY, z, Math.max(80, Math.min(110, radius)), seed);
        });
    }

    private static SpawnBuilder.Result voxel(Location at, SpawnBuilder.Style style, int radius) {
        World w = at.getWorld();
        int floorY = at.getBlockY() - 1;
        long seed = at.getBlockX() * 31L + at.getBlockZ();
        int x = at.getBlockX();
        int z = at.getBlockZ();

        Voxels d = design(style, x, floorY, z, radius, seed);
        List<Location> spots = new ArrayList<>();

        for (double[] n : d.npcSpots()) {
            Location l = new Location(w, n[0], n[1], n[2]);
            l.setYaw((float)n[3]);
            spots.add(l);
        }

        double[] s = d.spawn();
        Location spawn = new Location(w, s[0], s[1], s[2], (float)s[3], 0.0F);
        Cuboid area = new Cuboid(d.minX(), d.bottomY(), d.minZ(), d.maxX(), d.topY(), d.maxZ());
        Map<String, Location> features = new LinkedHashMap<>();

        for (Map.Entry<String, double[]> f : d.features().entrySet()) {
            double[] v = f.getValue();
            features.put(f.getKey(), new Location(w, v[0], v[1], v[2], (float)v[3], 0.0F));
        }

        return new SpawnBuilder.Result(voxelJob(w, d), spawn, spots, features, area);
    }

    /**
     * Loads (and generates) every chunk of a build first, then places it: placing into chunks that are still being
     * generated made Paper complain about block changes off the main thread (barrels, beds and other POI blocks).
     */
    public static java.util.concurrent.CompletableFuture<Void> build(net.practicepvp.PracticePlugin plugin, World w, Voxels d) {
        net.practicepvp.util.Cuboid c = new net.practicepvp.util.Cuboid(d.minX(), d.bottomY(), d.minZ(), d.maxX(), d.topY(), d.maxZ());
        return net.practicepvp.arena.ArenaManager.preload(w, c)
            .thenComposeAsync(v -> plugin.jobs().submit(placeJob(w, d)), net.practicepvp.util.Tasks.MAIN);
    }

    public static BlockJobs.Job placeJob(final World w, final Voxels d) {
        final Map<String, BlockData> cache = new HashMap<>();
        return new BlockJobs.Job() {
            int x = d.minX();
            int z = d.minZ();
            final int[] used = new int[1];
            final Voxels.Sink sink = (bx, by, bz, block) -> {
                if (by >= w.getMinHeight() + 1 && by < w.getMaxHeight()) {
                    BlockData data = cache.computeIfAbsent(block, str -> {
                        try {
                            return Bukkit.createBlockData(str);
                        } catch (IllegalArgumentException var2x) {
                            return Material.STONE.createBlockData();
                        }
                    });
                    w.getBlockAt(bx, by, bz).setBlockData(data, false);
                    this.used[0]++;
                }
            };

            @Override
            public int step(int budget) {
                this.used[0] = 0;

                while (this.x <= d.maxX() && this.used[0] < budget) {
                    d.column(this.x, this.z, this.sink);
                    if (++this.z > d.maxZ()) {
                        this.z = d.minZ();
                        this.x++;
                    }
                }

                return Math.max(1, this.used[0]);
            }

            @Override
            public boolean done() {
                return this.x > d.maxX();
            }

            @Override
            public String name() {
                return d.name();
            }
        };
    }

    private static BlockJobs.Job voxelJob(final World w, final Voxels d) {
        final Map<String, BlockData> cache = new HashMap<>();
        return new BlockJobs.Job() {
            int x = d.minX();
            int z = d.minZ();
            final int[] used = new int[1];
            final Voxels.Sink sink = (bx, by, bz, block) -> {
                if (by >= w.getMinHeight() + 1 && by < w.getMaxHeight()) {
                    BlockData data = cache.computeIfAbsent(block, str -> {
                        try {
                            return Bukkit.createBlockData(str);
                        } catch (IllegalArgumentException var2x) {
                            return Material.STONE.createBlockData();
                        }
                    });
                    w.getBlockAt(bx, by, bz).setBlockData(data, false);
                    this.used[0]++;
                }
            };

            @Override
            public int step(int budget) {
                this.used[0] = 0;
                int columns = 0;

                while (this.x <= d.maxX() && this.used[0] < budget && columns < budget) {
                    int top = Math.min(d.topY(), w.getHighestBlockYAt(this.x, this.z, HeightMap.WORLD_SURFACE));

                    for (int y = top; y > d.floorYAt(); y--) {
                        Block b = w.getBlockAt(this.x, y, this.z);
                        if (!b.getType().isAir()) {
                            b.setType(Material.AIR, false);
                            this.used[0]++;
                        }
                    }

                    d.column(this.x, this.z, this.sink);
                    columns++;
                    if (++this.z > d.maxZ()) {
                        this.z = d.minZ();
                        this.x++;
                    }
                }

                return Math.max(1, this.used[0]);
            }

            @Override
            public boolean done() {
                return this.x > d.maxX();
            }

            @Override
            public String name() {
                return "lobby-" + d.name();
            }
        };
    }

    private static SpawnBuilder.Result town(Location at, TownDesign.Theme theme, int radius) {
        World w = at.getWorld();
        int r = Math.max(40, Math.min(80, radius));
        int floorY = at.getBlockY() - 1;
        TownDesign d = new TownDesign(at.getBlockX(), floorY, at.getBlockZ(), r, at.getBlockX() * 31L + at.getBlockZ(), theme);
        List<Location> spots = new ArrayList<>();

        for (double[] n : d.npcSpots()) {
            Location l = new Location(w, n[0], n[1], n[2]);
            l.setYaw((float)n[3]);
            spots.add(l);
        }

        double[] s = d.spawn();
        Location spawn = new Location(w, s[0], s[1], s[2], (float)s[3], 0.0F);
        Cuboid area = new Cuboid(d.minX(), d.bottomY(), d.minZ(), d.maxX(), d.topY(), d.maxZ());
        return new SpawnBuilder.Result(townJob(w, d), spawn, spots, new LinkedHashMap<>(), area);
    }

    private static BlockJobs.Job townJob(final World w, final TownDesign d) {
        final Map<String, BlockData> cache = new HashMap<>();
        return new BlockJobs.Job() {
            int x = d.minX();
            int z = d.minZ();
            final int[] used = new int[1];
            final TownDesign.Sink sink = (bx, by, bz, block) -> {
                if (by >= w.getMinHeight() + 1 && by < w.getMaxHeight()) {
                    BlockData data = cache.computeIfAbsent(block, str -> {
                        try {
                            return Bukkit.createBlockData(str);
                        } catch (IllegalArgumentException var2x) {
                            return Material.STONE.createBlockData();
                        }
                    });
                    Block b = w.getBlockAt(bx, by, bz);
                    if (!data.getMaterial().isAir() || !b.getType().isAir()) {
                        b.setBlockData(data, false);
                        this.used[0]++;
                    }
                }
            };

            @Override
            public int step(int budget) {
                this.used[0] = 0;
                int columns = 0;

                while (this.x <= d.maxX() && this.used[0] < budget && columns < budget) {
                    d.column(this.x, this.z, this.sink);
                    columns++;
                    if (++this.z > d.maxZ()) {
                        this.z = d.minZ();
                        this.x++;
                    }
                }

                return Math.max(1, this.used[0]);
            }

            @Override
            public boolean done() {
                return this.x > d.maxX();
            }

            @Override
            public String name() {
                return "lobby-" + d.theme.name().toLowerCase(Locale.ROOT);
            }
        };
    }

    private static BlockJobs.Job colosseumJob(final World w, final ColosseumDesign d) {
        final Map<String, BlockData> cache = new HashMap<>();
        return new BlockJobs.Job() {
            int x = d.minX();
            int z = d.minZ();
            final int[] used = new int[1];
            final ColosseumDesign.Sink sink = (bx, by, bz, block) -> {
                if (by >= w.getMinHeight() + 1 && by < w.getMaxHeight()) {
                    BlockData data = cache.computeIfAbsent(block, s -> {
                        try {
                            return Bukkit.createBlockData(s);
                        } catch (IllegalArgumentException var2x) {
                            return Material.STONE.createBlockData();
                        }
                    });
                    w.getBlockAt(bx, by, bz).setBlockData(data, false);
                    this.used[0]++;
                }
            };

            @Override
            public int step(int budget) {
                this.used[0] = 0;

                while (this.x <= d.maxX() && this.used[0] < budget) {
                    int top = Math.min(d.topY, w.getHighestBlockYAt(this.x, this.z, HeightMap.WORLD_SURFACE));

                    for (int y = top; y > d.floorY; y--) {
                        Block b = w.getBlockAt(this.x, y, this.z);
                        if (!b.getType().isAir()) {
                            b.setType(Material.AIR, false);
                            this.used[0]++;
                        }
                    }

                    d.column(this.x, this.z, this.sink);
                    this.used[0]++;
                    if (++this.z > d.maxZ()) {
                        this.z = d.minZ();
                        this.x++;
                    }
                }

                return this.used[0];
            }

            @Override
            public boolean done() {
                return this.x > d.maxX();
            }

            @Override
            public String name() {
                return "colosseum-lobby";
            }
        };
    }

    private static void floor(SpawnBuilder.Plan p, SpawnBuilder.Palette pal, int r, boolean diagonals) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (!(d > r + 0.5)) {
                    int x = p.cx + dx;
                    int z = p.cz + dz;

                    for (int y = p.cy + 1; y <= p.cy + 18; y++) {
                        p.put(x, y, z, Material.AIR, true);
                    }

                    for (int y = p.cy - 1; y >= p.cy - 5; y--) {
                        p.put(x, y, z, pal.foundation(), false);
                    }

                    boolean spoke = Math.abs(dx) <= 1 || Math.abs(dz) <= 1 || diagonals && Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1;
                    Material floor;
                    if (d <= 2.6) {
                        floor = pal.accent();
                    } else if (d > r - 0.6) {
                        floor = pal.trim();
                    } else if (spoke) {
                        floor = pal.path();
                    } else if ((int)d % 6 == 5) {
                        floor = pal.ring();
                    } else {
                        floor = pal.main();
                    }

                    p.put(x, p.cy, z, floor, true);
                    if (d > r - 1.6 && d <= r - 0.6 && !spoke) {
                        p.put(x, p.cy + 1, z, pal.hedge() != null ? pal.hedge() : pal.post(), true);
                    }
                }
            }
        }
    }

    private static void centre(SpawnBuilder.Plan p, SpawnBuilder.Palette pal) {
        if (pal.pool()) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (!(d < 2.4) || dx == 0 && dz == 0) {
                        if (d >= 2.4 && d < 3.1) {
                            p.rel(dx, 1, dz, pal.trim());
                        }
                    } else {
                        p.rel(dx, 1, dz, Material.WATER);
                        p.rel(dx, 0, dz, pal.accent());
                    }
                }
            }
        }

        for (int y = 1; y <= 3; y++) {
            p.rel(0, y, 0, pal.accent());
        }

        p.rel(0, 4, 0, pal.light());
    }

    private static void lamps(SpawnBuilder.Plan p, SpawnBuilder.Palette pal, int r, SpawnBuilder.Style style) {
        int posts = Math.max(6, r / 3);
        double pr = r - 3.0;

        for (int i = 0; i < posts; i++) {
            double a = (Math.PI * 2) * i / posts + Math.PI / posts;
            int dx = (int)Math.round(Math.cos(a) * pr);
            int dz = (int)Math.round(Math.sin(a) * pr);

            for (int y = 1; y <= 3; y++) {
                p.rel(dx, y, dz, pal.post());
            }

            p.rel(dx, 4, dz, style == SpawnBuilder.Style.STRENGTH ? Material.SOUL_LANTERN : Material.LANTERN);
        }
    }

    private static void benches(SpawnBuilder.Plan p, SpawnBuilder.Palette pal, int r) {
        int out = Math.max(5, r - 7);
        int[][] dirs = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        BlockFace[] faces = new BlockFace[]{BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH};

        for (int i = 0; i < 4; i++) {
            int ox = dirs[i][0];
            int oz = dirs[i][1];
            int tx = -oz;
            int tz = ox;

            for (int side = -1; side <= 1; side += 2) {
                int bx = ox * out + tx * side * 5;
                int bz = oz * out + tz * side * 5;

                for (int k = -1; k <= 1; k++) {
                    BlockData stair = pal.stair().createBlockData();
                    if (stair instanceof Stairs s) {
                        s.setFacing(faces[i]);
                    }

                    p.rel(bx + tx * k, 1, bz + tz * k, stair);
                }

                p.rel(bx + tx * 2, 1, bz + tz * 2, pal.plant());
                p.rel(bx - tx * 2, 1, bz - tz * 2, pal.plant());
            }
        }
    }

    private static void parkour(SpawnBuilder.Plan p, SpawnBuilder.Palette pal, World w, Map<String, Location> features) {
        double radius = 10.0;
        int steps = 26;
        p.rel((int)Math.round(Math.cos(-0.5) * 10.5), 1, (int)Math.round(Math.sin(-0.5) * 10.5), pal.accent());
        p.rel((int)Math.round(Math.cos(-0.26) * 10.2), 2, (int)Math.round(Math.sin(-0.26) * 10.2), pal.accent());
        int lastY = 3;
        int lx = 0;
        int lz = 0;

        for (int i = 0; i < steps; i++) {
            double a = i * (Math.PI / 11);
            int dx = (int)Math.round(Math.cos(a) * radius);
            int dz = (int)Math.round(Math.sin(a) * radius);
            int y = 3 + i / 2;
            p.rel(dx, y, dz, i % 5 == 4 ? pal.ring() : pal.trim());
            lastY = y;
            lx = dx;
            lz = dz;
        }

        int ex = (int)Math.round(lx * 0.75);
        int ez = (int)Math.round(lz * 0.75);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.rel(ex + dx, lastY + 1, ez + dz, dx == 0 && dz == 0 ? pal.light() : pal.accent());
            }
        }

        features.put("parkour", new Location(w, p.cx + ex + 0.5, p.cy + lastY + 2, p.cz + ez + 0.5));
    }

    private static void balcony(SpawnBuilder.Plan p, SpawnBuilder.Palette pal, int r, World w, Map<String, Location> features) {
        int bx = -((int)Math.round(r * 0.6));
        int bz = (int)Math.round(r * 0.32);
        int h = 4;

        for (int dx = -3; dx <= 1; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                p.rel(bx + dx, h, bz + dz, pal.trim());
                boolean rail = dx == -3 || Math.abs(dz) == 3 || dx == 1 && Math.abs(dz) >= 2;
                if (rail) {
                    p.rel(bx + dx, h + 1, bz + dz, pal.post());
                }
            }
        }

        for (int dx : new int[]{-3, 1}) {
            for (int dzx : new int[]{-3, 3}) {
                for (int y = 1; y < h; y++) {
                    p.rel(bx + dx, y, bz + dzx, pal.post());
                }

                p.rel(bx + dx, h + 2, bz + dzx, Material.LANTERN);
            }
        }

        for (int k = 1; k <= 3; k++) {
            for (int dzx = -1; dzx <= 1; dzx++) {
                BlockData stair = pal.stair().createBlockData();
                if (stair instanceof Stairs s) {
                    s.setFacing(BlockFace.WEST);
                }

                p.rel(bx + 1 + k, h - k, bz + dzx, stair);
            }
        }

        Location l = new Location(w, p.cx + bx - 1 + 0.5, p.cy + h + 1, p.cz + bz + 0.5);
        l.setYaw(yawTo(l, p.cx + 0.5, p.cz + 0.5));
        features.put("spectate", l);
    }

    private static List<Location> pedestals(SpawnBuilder.Plan p, SpawnBuilder.Palette pal, int r, int n, World w) {
        List<Location> spots = new ArrayList<>();
        double nr = Math.max(5.0, r * 0.55);

        for (int i = 0; i < n; i++) {
            double a = Math.PI + Math.PI * (i + 1) / (n + 1);
            int dx = (int)Math.round(Math.cos(a) * nr);
            int dz = (int)Math.round(Math.sin(a) * nr);
            p.rel(dx, 1, dz, pal.trim());
            Location l = new Location(w, p.cx + dx + 0.5, p.cy + 2, p.cz + dz + 0.5);
            l.setYaw(yawTo(l, p.cx + 0.5, p.cz + 0.5));
            spots.add(l);
        }

        return spots;
    }

    private static float yawTo(Location from, double x, double z) {
        return (float)Math.toDegrees(Math.atan2(-(x - from.getX()), z - from.getZ()));
    }

    private static long key(int x, int y, int z) {
        return (long)(x & 67108863) << 38 | (long)(z & 67108863) << 12 | y & 4095;
    }

    private static BlockJobs.Job job(final World w, final List<Object[]> list, final SpawnBuilder.Style style) {
        return new BlockJobs.Job() {
            int i;

            @Override
            public int step(int budget) {
                int used = 0;
                int scanned = 0;

                while (this.i < list.size() && used < budget && scanned++ < budget * 8) {
                    Object[] wr = list.get(this.i++);
                    int x = (Integer)wr[0];
                    int y = (Integer)wr[1];
                    int z = (Integer)wr[2];
                    BlockData data = (BlockData)wr[3];
                    boolean overwrite = (Boolean)wr[4];
                    if (y >= w.getMinHeight() + 2 && y < w.getMaxHeight()) {
                        Block b = w.getBlockAt(x, y, z);
                        if (!b.getBlockData().equals(data) && (overwrite || !b.getType().isSolid())) {
                            b.setBlockData(data, false);
                            used++;
                        }
                    }
                }

                return Math.max(used, 1);
            }

            @Override
            public boolean done() {
                return this.i >= list.size();
            }

            @Override
            public String name() {
                return "spawn-" + style.name().toLowerCase(Locale.ROOT);
            }
        };
    }

    private record Palette(
        Material main,
        Material ring,
        Material path,
        Material accent,
        Material trim,
        Material post,
        Material light,
        Material hedge,
        Material foundation,
        boolean pool,
        Material stair,
        Material plant
    ) {
    }

    private static final class Plan {
        final Map<Long, Object[]> writes = new LinkedHashMap<>();
        final int cx;
        final int cy;
        final int cz;

        Plan(int cx, int cy, int cz) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
        }

        void put(int x, int y, int z, BlockData data, boolean overwrite) {
            this.writes.put(SpawnBuilder.key(x, y, z), new Object[]{x, y, z, data, overwrite});
        }

        void put(int x, int y, int z, Material m, boolean overwrite) {
            BlockData data = m.createBlockData();
            if (data instanceof Leaves leaves) {
                leaves.setPersistent(true);
            }

            this.put(x, y, z, data, overwrite);
        }

        void rel(int dx, int dy, int dz, Material m) {
            this.put(this.cx + dx, this.cy + dy, this.cz + dz, m, true);
        }

        void rel(int dx, int dy, int dz, BlockData d) {
            this.put(this.cx + dx, this.cy + dy, this.cz + dz, d, true);
        }
    }

    public record Result(BlockJobs.Job job, Location spawn, List<Location> npcSpots, Map<String, Location> features, Cuboid area) {
    }

    public static enum Style {
        TOWN(64, 8, TownDesign.Theme.TOWN),
        DESERT(64, 8, TownDesign.Theme.DESERT),
        SAKURA(64, 8, TownDesign.Theme.SAKURA),
        NETHER(64, 8, TownDesign.Theme.NETHER),
        WINTER(64, 8, TownDesign.Theme.WINTER),
        CELESTIAL(64, 8, TownDesign.Theme.CELESTIAL),
        COLOSSEUM(46, 8, null),
        CITADEL(110, 7, null),
        ARCHIPELAGO(80, 7, null),
        NEON(76, 7, null),
        KEEP(74, 7, null),
        JUNGLE(74, 7, null),
        GRAND_HUB(150, 8, null),
        TOWN_SQUARE(120, 8, null),
        CASTLE_MARKET(100, 8, null),
        PRACTICE_TOWN(110, 8, null),
        ROYAL_CASTLE(125, 9, null),
        COLOSSEUM_CITY(240, 5, null),
        SKY_EMPIRE(240, 9, null),
        VORTEX_ISLES(90, 8, null),
        HUB(40, 9, null),
        PVP(36, 7, null),
        SURVIVAL(14, 2, null),
        LIFESTEAL(14, 2, null),
        STRENGTH(14, 2, null),
        RANKED(14, 2, null),
        ECO(15, 3, null);

        public final int radius;
        public final int npcSlots;
        public final TownDesign.Theme theme;

        private Style(int radius, int npcSlots, TownDesign.Theme theme) {
            this.radius = radius;
            this.npcSlots = npcSlots;
            this.theme = theme;
        }

        public boolean big() {
            return this.theme != null || this == COLOSSEUM || this.voxel();
        }

        public boolean voxel() {
            return this == CITADEL
                || this == ARCHIPELAGO
                || this == NEON
                || this == KEEP
                || this == JUNGLE
                || this == GRAND_HUB
                || this == COLOSSEUM_CITY
                || this == SKY_EMPIRE
                || this == TOWN_SQUARE
                || this == CASTLE_MARKET
                || this == PRACTICE_TOWN
                || this == ROYAL_CASTLE
                || this == VORTEX_ISLES;
        }

        public int minRadius() {
            return switch (this) {
                case CITADEL -> 80;
                default -> 56;
                case GRAND_HUB -> 120;
                case TOWN_SQUARE -> 100;
                case CASTLE_MARKET -> 80;
                case PRACTICE_TOWN -> 104;
                case ROYAL_CASTLE -> 110;
                case COLOSSEUM_CITY, SKY_EMPIRE -> 200;
                case VORTEX_ISLES -> 80;
            };
        }

        public int maxRadius() {
            return switch (this) {
                case CITADEL -> 128;
                default -> 96;
                case GRAND_HUB -> 170;
                case TOWN_SQUARE -> 140;
                case CASTLE_MARKET -> 130;
                case PRACTICE_TOWN -> 130;
                case ROYAL_CASTLE -> 140;
                case COLOSSEUM_CITY, SKY_EMPIRE -> 250;
                case VORTEX_ISLES -> 110;
            };
        }

        public static SpawnBuilder.Style parse(String s) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
                return null;
            }
        }
    }
}
