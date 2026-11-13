package net.vortexpvp.build;

import java.util.List;

/**
 * The Bot Practice hall: a half-round hall cut into the rock. Every map has its own alcove whose wall is built from
 * that biome (terracotta bands for the badlands, sandstone for the desert, packed snow and ice, a grassy cliff for the
 * plains, pink cherry stone, crimson nylium, end stone...), with a carved pedestal in front for the map's mob and its
 * name floating above. The floor is grass with flowers and petals; in the middle stand the search, training and
 * Academy NPCs. Feature spots: "botmap:&lt;THEME&gt;" per alcove, "botnpc:search|train|learn" in the middle.
 */
public final class BotHallDesign extends MegaVoxels {
    /** Map themes shown, in order from left to right, with their wall palette (3 blocks) and colour. */
    public static final List<String[]> MAPS = List.of(
        new String[]{"BADLANDS", "terracotta", "orange_terracotta", "white_terracotta"},
        new String[]{"BADLANDS_DESERT", "red_sandstone", "red_terracotta", "smooth_red_sandstone"},
        new String[]{"DESERT", "sandstone", "smooth_sandstone", "cut_sandstone"},
        new String[]{"PLAINS", "stone", "mossy_cobblestone", "andesite"},
        new String[]{"SNOWY", "snow_block", "packed_ice", "powder_snow_cauldron"},
        new String[]{"CHERRY", "calcite", "cherry_leaves[persistent=true]", "stone"},
        new String[]{"MUSHROOM", "mycelium", "red_mushroom_block", "stone"},
        new String[]{"CRIMSON", "crimson_nylium", "netherrack", "nether_wart_block"},
        new String[]{"END", "end_stone", "end_stone_bricks", "purpur_block"}
    );
    private static final double RING = 15.0;

    public BotHallDesign(int cx, int floorY, int cz, long seed) {
        super(cx, floorY, cz, 26, -6, 16, seed);
        this.floorAndRock();
        this.alcoves();
        this.centre();
        this.finish();
    }

    @Override
    public String name() {
        return "Bot Practice Hall";
    }

    /** Alcove i of n on the back half circle (north side, -z), angle in degrees from west to east. */
    private double angle(int i, int n) {
        return 195.0 + 150.0 * i / Math.max(1, n - 1);
    }

    private void floorAndRock() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = dist(x, z);
                for (int y = -6; y < 0; y++) {
                    this.set(x, y, z, y == -6 ? "bedrock" : (y == -1 ? "dirt" : "stone"));
                }

                if (d <= RING + 4.0 && z <= 6) {
                    double n = noise(41L, x * 0.9, z * 0.9);
                    this.set(x, 0, z, "grass_block");
                    if (d < RING - 2.0 && this.rnd.nextDouble() < 0.16) {
                        this.set(x, 1, z, n > 0.6 ? "pink_petals[flower_amount=4,facing=north]" : (n < 0.25 ? "dandelion" : "short_grass"));
                    }
                } else if (z > 6 && Math.abs(x) <= 4 && z <= this.R) {
                    // the way in from the south
                    this.set(x, 0, z, Math.abs(x) == 4 ? "mossy_stone_bricks" : "stone_bricks");
                } else {
                    // solid rock around the hall, a little higher toward the back
                    int h = 11 + (int)Math.round(noise(43L, x * 0.5, z * 0.5) * 4.0);
                    for (int y = 0; y <= h; y++) {
                        double v = noise(47L, x * 1.7 + y, z * 1.7 - y);
                        this.set(x, y, z, v > 0.72 ? "andesite" : (v < 0.22 ? "cobblestone" : "stone"));
                    }
                }
            }
        }

        // a rock roof over the back half with an open sky in the middle
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= 2; z++) {
                double d = dist(x, z);
                if (d > 9.0 && d <= RING + 4.0) {
                    int h = 12 + (int)Math.round((d - 9.0) * 0.25);
                    this.set(x, h, z, "stone");
                    this.set(x, h + 1, z, "grass_block");
                }
            }
        }
    }

    private void alcoves() {
        int n = MAPS.size();

        for (int i = 0; i < n; i++) {
            String[] m = MAPS.get(i);
            double a = Math.toRadians(this.angle(i, n));
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            // the themed wall: a 7 wide, 10 high section of the back wall in bands of the biome's blocks
            for (int w = -3; w <= 3; w++) {
                for (int depth = 0; depth <= 3; depth++) {
                    double r = RING + 1.0 + depth;
                    int x = (int)Math.round(dx * r - dz * w);
                    int z = (int)Math.round(dz * r + dx * w);

                    for (int y = 1; y <= 10; y++) {
                        String b = m[1 + Math.floorMod(y + (depth == 0 ? 0 : 1) + (Math.abs(w) == 3 ? 1 : 0), 3)];
                        if (b.equals("powder_snow_cauldron")) {
                            b = "snow_block";
                        }

                        // a stepped, cliff-like face: the middle sits deeper than the sides
                        if (depth == 0 && Math.abs(w) <= 1 && y <= 4) {
                            b = null;
                        }

                        this.set(x, y, z, b);
                    }
                }
            }

            // pedestal
            int px = (int)Math.round(dx * (RING - 3.0));
            int pz = (int)Math.round(dz * (RING - 3.0));
            this.set(px, 1, pz, "chiseled_tuff_bricks");
            this.set(px, 0, pz, "polished_tuff");
            this.set(px + 1, 1, pz, "tuff_brick_slab[type=bottom]");
            this.set(px - 1, 1, pz, "tuff_brick_slab[type=bottom]");
            this.set(px, 1, pz + 1, "tuff_brick_slab[type=bottom]");
            this.set(px, 1, pz - 1, "tuff_brick_slab[type=bottom]");
            this.feature("botmap:" + m[0], px, 2.0, pz, 0, 0);
        }
    }

    private void centre() {
        // three NPC stands on a small stone ring in the middle
        this.ring(0, -2, 0, 3.0, 4.2, "polished_andesite");
        this.disc(0, -2, 0, 2.9, "moss_block");
        String[] keys = new String[]{"search", "train", "learn"};

        for (int i = 0; i < 3; i++) {
            int x = (i - 1) * 3;
            this.set(x, 1, 1, "chiseled_stone_bricks");
            this.feature("botnpc:" + keys[i], x, 2.0, 1, 0, 12);
        }

        for (int sx = -1; sx <= 1; sx += 2) {
            this.lampPost(sx * 6, 1, 4, "mossy_stone_bricks", "stone_brick_wall", "lantern", 3);
        }

        this.spawn = new double[]{0.5, 1.0, 9.5, 180.0};
    }
}
