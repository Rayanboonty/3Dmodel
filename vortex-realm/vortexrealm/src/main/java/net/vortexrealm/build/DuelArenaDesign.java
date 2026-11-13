package net.vortexrealm.build;

/**
 * Compact, detailed duel arenas in the style of the big practice servers (original designs): a clean fighting
 * floor about 72 blocks across (90 for the building kits) with a patterned centre, two raised spawn pads under
 * arches about 28 blocks apart, a decorated edge (low walls, pillars, lanterns, banners, plants) under an
 * invisible barrier, and scenery outside it - trees, cliffs, towers, water - so the view is full while the fight
 * stays clear. Sword maps are flat; the UHC maps have low hills, trees and rocks to build around.
 */
public final class DuelArenaDesign extends DetailVoxels implements ArenaVoxels {
    /** Width of the scenery outside the barrier. */
    public static final int BACKDROP = 16;
    /** Height of the barrier walls and roof above the floor. */
    public static final int CEILING = 40;
    private final DuelArenaDesign.Style style;
    private final int P;
    private final int S;
    private final int depth;
    private final boolean ffa;
    private final int[][] height;

    public static enum Style {
        TEMPLE("Temple Ruins", false),
        SAKURA("Sakura Garden", false),
        BASTION("Nether Bastion", false),
        HARBOR("Frozen Harbor", false),
        BAZAAR("Desert Bazaar", false),
        COURTYARD("Castle Courtyard", false),
        MUSHROOM("Mushroom Hollow", false),
        CITADEL("End Citadel", false),
        UHC_MEADOW("Meadow Hills", true),
        UHC_CANYON("Red Canyon", true),
        UHC_TAIGA("Taiga Valley", true),
        UHC_RUINS("Overgrown Ruins", true),
        /** The flat map of the practice bots and training dummies. */
        TRAINING("Training Grounds", false);

        public final String display;
        /** A map for the building kits (UHC, Build UHC): bigger, with hills and cover. */
        public final boolean build;

        private Style(String display, boolean build) {
            this.display = display;
            this.build = build;
        }
    }

    /** The playable size of a map of this style (the barrier square). */
    public static int playable(DuelArenaDesign.Style style, int compact, int build) {
        return Math.max(40, style.build ? build : compact);
    }

    public static int depthOf(DuelArenaDesign.Style style) {
        return style.build ? 22 : 10;
    }

    public DuelArenaDesign(DuelArenaDesign.Style style, int size, long seed, int cx, int floorY, int cz, boolean ffa) {
        super(cx, floorY, cz, Math.max(20, size / 2) + BACKDROP, -depthOf(style), CEILING, seed);
        this.style = style;
        this.P = Math.max(20, size / 2);
        this.S = style.build ? Math.max(14, (int)Math.round(this.P * 0.48)) : Math.max(11, (int)Math.round(this.P * 0.4));
        this.depth = depthOf(style);
        this.ffa = ffa;
        this.height = new int[this.R * 2 + 1][this.R * 2 + 1];
        this.terrain();
        this.ground();
        if (style == Style.TRAINING) {
            this.trainingFloor();
        } else if (!style.build) {
            this.floorPattern();
        }

        this.spawnPads();
        if (!style.build && style != Style.TRAINING) {
            this.quarters();
        }

        this.edge();
        this.channel();
        this.backdrop();
        this.barrier();
        this.finish();
    }

    @Override
    public String name() {
        return this.style.display;
    }

    // ------------------------------------------------------------------ shape

    /** Rounded-square distance: the arenas are squares with soft corners. */
    private static double sd(double x, double z) {
        double ax = Math.abs(x);
        double az = Math.abs(z);
        return Math.pow(ax * ax * ax * ax + az * az * az * az, 0.25);
    }

    private boolean inside(int x, int z) {
        return sd(x, z) <= this.P;
    }

    private int h(int x, int z) {
        int ix = x + this.R;
        int iz = z + this.R;
        return ix >= 0 && iz >= 0 && ix < this.height.length && iz < this.height.length ? this.height[ix][iz] : 0;
    }

    @Override
    public int[] spawn(int side) {
        int x = side == 0 ? -this.S : this.S;
        return new int[]{x, this.h(x, 0) + 2, 0};
    }

    @Override
    public int[] ffaSpot(int i, int n) {
        double a = Math.PI * 2.0 * i / Math.max(1, n);
        int r = (int)Math.round(this.P * 0.62);
        int x = (int)Math.round(Math.cos(a) * r);
        int z = (int)Math.round(Math.sin(a) * r);
        return new int[]{x, this.h(x, z) + 1, z};
    }

    // ------------------------------------------------------------------ terrain and ground

    private void terrain() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = sd(x, z);
                int y = 0;
                if (this.style.build) {
                    // low rolling hills, flattened around the spawns and kept low in the middle
                    double n = noise(this.dseed + 5L, x * 0.9, z * 0.9);
                    double hill = (n - 0.45) * (this.style == Style.UHC_CANYON ? 9.0 : 7.0);
                    double nearSpawn = Math.min(dist(x - this.S, z), dist(x + this.S, z));
                    double flat = Math.min(1.0, Math.max(0.0, (nearSpawn - 4.0) / 8.0));
                    y = (int)Math.round(Math.max(-1.0, hill) * flat);
                    if (this.style == Style.UHC_CANYON && d < this.P && n > 0.68 && nearSpawn > 9.0) {
                        // a few mesa pillars as cover
                        y += (int)Math.round((n - 0.68) * 40.0);
                    }
                }

                if (d > this.P) {
                    // the scenery outside rises like a valley around the arena
                    double t = (d - this.P) / BACKDROP;
                    double rise = this.backdropRise() * Math.min(1.0, t * 1.4) + (noise(this.dseed + 9L, x * 1.3, z * 1.3) - 0.5) * 5.0 * t;
                    y = Math.max(y, (int)Math.round(rise));
                }

                this.height[x + this.R][z + this.R] = y;
            }
        }
    }

    private double backdropRise() {
        return switch (this.style) {
            case HARBOR -> -1.0;
            case CITADEL -> -2.0;
            case BAZAAR -> 4.0;
            case TRAINING -> 3.0;
            case BASTION, UHC_CANYON -> 12.0;
            case TEMPLE, UHC_TAIGA, MUSHROOM -> 9.0;
            default -> 6.0;
        };
    }

    /** {top, under, deep} soil of the style at a position. */
    private String[] soil(int x, int z) {
        return switch (this.style) {
            case TEMPLE -> new String[]{this.texture(1L, x, z, "moss_block", "grass_block", "coarse_dirt", "rooted_dirt"), "dirt", "stone"};
            case SAKURA, UHC_MEADOW, TRAINING -> new String[]{this.texture(2L, x, z, "grass_block", "grass_block", "moss_block"), "dirt", "stone"};
            case BASTION -> new String[]{this.texture(3L, x, z, "netherrack", "crimson_nylium", "blackstone", "magma_block"), "netherrack", "blackstone"};
            case HARBOR -> new String[]{this.texture(4L, x, z, "snow_block", "packed_ice", "snow_block"), "packed_ice", "stone"};
            case BAZAAR -> new String[]{this.texture(5L, x, z, "sand", "sandstone", "smooth_sandstone"), "sandstone", "sandstone"};
            case COURTYARD -> new String[]{this.texture(6L, x, z, "grass_block", "coarse_dirt", "gravel"), "dirt", "stone"};
            case MUSHROOM -> new String[]{this.texture(7L, x, z, "mycelium", "podzol", "moss_block"), "dirt", "stone"};
            case CITADEL -> new String[]{this.texture(8L, x, z, "end_stone", "end_stone", "end_stone_bricks"), "end_stone", "end_stone"};
            case UHC_CANYON -> new String[]{"red_sand", "terracotta", "terracotta"};
            case UHC_TAIGA -> new String[]{this.texture(10L, x, z, "podzol", "grass_block", "coarse_dirt", "moss_block"), "dirt", "stone"};
            case UHC_RUINS -> new String[]{this.texture(11L, x, z, "grass_block", "grass_block", "coarse_dirt", "moss_block"), "dirt", "stone"};
        };
    }

    private static final String[] CANYON_BANDS = {
        "terracotta", "orange_terracotta", "yellow_terracotta", "terracotta", "red_terracotta", "white_terracotta", "light_gray_terracotta", "brown_terracotta"
    };

    private void ground() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int top = this.h(x, z);
                String[] s = this.soil(x, z);
                // the end citadel floats: islands outside the barrier only where the noise says so
                if (this.style == Style.CITADEL && !this.inside(x, z) && noise(this.dseed + 13L, x * 1.1, z * 1.1) < 0.5) {
                    continue;
                }

                if (this.style == Style.CITADEL) {
                    // a floating platform with a tapered underside, and floating islands around it
                    double dd = sd(x, z);
                    int under = dd <= this.P + 1 ? (int)Math.round(4 + (this.P + 1 - dd) * 0.6 + noise(this.dseed + 17L, x, z) * 3.0)
                        : (int)Math.round(2 + (noise(this.dseed + 13L, x * 1.1, z * 1.1) - 0.5) * 16.0);
                    for (int y = Math.max(-this.depth, top - under); y <= top; y++) {
                        this.set(x, y, z, y == top ? s[0] : (y == -this.depth ? "bedrock" : this.texture(18L, x + y * 3, z, "end_stone", "end_stone", "end_stone_bricks")));
                    }

                    continue;
                }

                if (this.style == Style.HARBOR && !this.inside(x, z) && sd(x, z) > this.P + 3) {
                    double isl = noise(this.dseed + 19L, x * 0.8, z * 0.8);
                    if (isl > 0.62) {
                        // snowy islands in the frozen sea, with spruces (added with the scenery)
                        int t = (int)Math.round((isl - 0.62) * 12.0);
                        for (int y = -this.depth; y <= t; y++) {
                            this.set(x, y, z, y == -this.depth ? "bedrock" : (y == t ? "snow_block" : "stone"));
                        }

                        this.height[x + this.R][z + this.R] = t;
                        continue;
                    }

                    // the frozen sea around the harbour
                    for (int y = -this.depth; y <= -1; y++) {
                        this.set(x, y, z, y == -this.depth ? "bedrock" : "stone");
                    }

                    this.set(x, 0, z, this.texture(14L, x, z, "packed_ice", "ice", "blue_ice", "snow_block"));
                    continue;
                }

                for (int y = -this.depth; y <= top; y++) {
                    String b;
                    if (y == -this.depth) {
                        b = "bedrock";
                    } else if (y == top) {
                        b = s[0];
                    } else if (this.style == Style.UHC_CANYON) {
                        b = CANYON_BANDS[Math.floorMod(y + 40, CANYON_BANDS.length)];
                    } else if (y >= top - 3) {
                        b = s[1];
                    } else {
                        b = this.style.build && this.hash3(15L, x, y, z) > 0.985 ? (this.hash3(16L, x, y, z) > 0.5 ? "iron_ore" : "coal_ore") : s[2];
                    }

                    this.set(x, y, z, b);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the fighting floor

    private String[] floorMats() {
        return switch (this.style) {
            case TEMPLE -> new String[]{"mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_cobblestone", "andesite"};
            case SAKURA -> new String[]{"grass_block", "moss_block", "grass_block", "rooted_dirt"};
            case BASTION -> new String[]{"polished_blackstone_bricks", "blackstone", "cracked_polished_blackstone_bricks", "gilded_blackstone", "polished_blackstone"};
            case HARBOR -> new String[]{"snow_block", "packed_ice", "snow_block", "snow_block"};
            case BAZAAR -> new String[]{"smooth_sandstone", "sandstone", "cut_sandstone", "sand"};
            case COURTYARD -> new String[]{"stone_bricks", "cobblestone", "andesite", "cracked_stone_bricks", "gravel"};
            case MUSHROOM -> new String[]{"mycelium", "podzol", "moss_block", "rooted_dirt"};
            case CITADEL -> new String[]{"end_stone_bricks", "end_stone", "purpur_block", "end_stone_bricks"};
            default -> new String[]{"grass_block"};
        };
    }

    /** {inlay, inlay accent, centre, border band, band accent} */
    private String[] inlay() {
        return switch (this.style) {
            case TEMPLE -> new String[]{"polished_andesite", "moss_block", "chiseled_stone_bricks", "stone_bricks", "mossy_cobblestone"};
            case SAKURA -> new String[]{"dirt_path", "cherry_planks", "polished_andesite", "stone_bricks", "cherry_planks"};
            case BASTION -> new String[]{"gilded_blackstone", "polished_blackstone", "chiseled_polished_blackstone", "polished_blackstone_bricks", "gilded_blackstone"};
            case HARBOR -> new String[]{"spruce_planks", "stripped_spruce_wood", "blue_ice", "spruce_planks", "polished_diorite"};
            case BAZAAR -> new String[]{"chiseled_sandstone", "orange_terracotta", "cut_red_sandstone", "cut_sandstone", "blue_terracotta"};
            case COURTYARD -> new String[]{"polished_andesite", "bricks", "chiseled_stone_bricks", "polished_andesite", "bricks"};
            case MUSHROOM -> new String[]{"mushroom_stem", "red_mushroom_block", "shroomlight", "brown_mushroom_block", "mushroom_stem"};
            case CITADEL -> new String[]{"purpur_pillar[axis=y]", "obsidian", "purpur_block", "purpur_block", "end_stone_bricks"};
            default -> new String[]{"stone_bricks", "stone_bricks", "stone_bricks", "stone_bricks", "stone_bricks"};
        };
    }

    private void floorPattern() {
        String[] fm = this.floorMats();
        String[] in = this.inlay();
        for (int x = -this.P; x <= this.P; x++) {
            for (int z = -this.P; z <= this.P; z++) {
                double d = sd(x, z);
                if (d > this.P) {
                    continue;
                }

                double r = dist(x, z);
                String b = this.texture(20L, x, z, fm);
                // a sakura path ring and a stone walk between the spawns, grass and petals elsewhere
                if (r <= 5.2) {
                    // the centre medallion: rings and a star
                    if (r <= 1.5) {
                        b = in[2];
                    } else if (r > 4.2) {
                        b = in[0];
                    } else {
                        double ang = Math.toDegrees(Math.atan2(z, x));
                        b = (int)Math.floor((ang + 22.5 + 360.0) / 45.0) % 2 == 0 ? in[1] : in[0];
                    }
                } else if (r > 8.5 && r <= 9.5) {
                    b = in[0];
                } else if (Math.abs(z) <= 1 && Math.abs(x) > 5 && Math.abs(x) < this.S - 3) {
                    // a walk from the centre to each spawn
                    b = Math.abs(z) == 1 ? in[3] : in[1];
                } else if (d > this.P - 4.0 && d <= this.P - 2.5) {
                    // the border band, with an accent every few blocks
                    b = Math.floorMod(x + z, 4) == 0 ? in[4] : in[3];
                }

                this.set(x, 0, z, b);
                if (this.style == Style.SAKURA && r > 10.0 && d < this.P - 5 && this.hash3(22L, x, 1, z) > 0.86) {
                    this.set(x, 1, z, "pink_petals[facing=" + new String[]{"north", "east", "south", "west"}[(int)(this.hash3(23L, x, 2, z) * 4)] + ",flower_amount=" + (1 + (int)(this.hash3(24L, x, 3, z) * 4)) + "]");
                } else if (this.style == Style.HARBOR && r > 10.0 && this.hash3(25L, x, 1, z) > 0.8) {
                    this.set(x, 1, z, "snow[layers=1]");
                } else if (this.style == Style.MUSHROOM && r > 10.0 && d < this.P - 5 && this.hash3(26L, x, 1, z) > 0.94) {
                    this.set(x, 1, z, this.hash3(27L, x, 2, z) > 0.5 ? "brown_mushroom" : "red_mushroom");
                } else if (this.style == Style.TEMPLE && r > 10.0 && d < this.P - 5 && this.hash3(28L, x, 1, z) > 0.9) {
                    this.set(x, 1, z, "moss_carpet");
                }
            }
        }
    }

    /**
     * The Training Grounds floor: perfectly flat smooth stone with a grid every 4 blocks (distances are easy to read),
     * distance marks every 4 blocks from the player's spawn, an obsidian pad where the dummies stand (crystals and
     * anchors go right on it) and a lit target ring in the middle.
     */
    private void trainingFloor() {
        for (int x = -this.P; x <= this.P; x++) {
            for (int z = -this.P; z <= this.P; z++) {
                if (sd(x, z) > this.P) {
                    continue;
                }

                String b = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0 ? "polished_andesite" : "smooth_stone";
                double r = dist(x, z);
                if (r > 3.5 && r <= 4.5) {
                    b = "light_blue_concrete";
                } else if (r <= 1.0) {
                    b = "sea_lantern";
                }

                if (z == 0 && x > -this.S && Math.floorMod(x + this.S, 4) == 0 && x < this.P - 4) {
                    // distance marks from the player's spawn: yellow every 4 blocks, orange every 8
                    b = Math.floorMod(x + this.S, 8) == 0 ? "orange_concrete" : "yellow_concrete";
                }

                int dx = x + this.S - 6;
                if (Math.abs(dx) <= 3 && Math.abs(z) <= 3) {
                    // the dummy's pad (6 blocks in front of the spawn): obsidian with a crying-obsidian rim
                    b = Math.abs(dx) == 3 || Math.abs(z) == 3 ? "crying_obsidian" : "obsidian";
                }

                this.set(x, 0, z, b);
                this.set(x, -1, z, "stone");
            }
        }
    }

    /** Four low flower beds or planters in the corners of the floor: detail without blocking the fight. */
    private void quarters() {
        int qx = (int)Math.round(this.P * 0.5);
        int qz = (int)Math.round(this.P * 0.55);
        String rim = this.stairMat();
        String[] flowers = switch (this.style) {
            case TEMPLE -> new String[]{"fern", "short_grass", "azure_bluet", "fern"};
            case SAKURA -> new String[]{"pink_tulip", "lily_of_the_valley", "white_tulip", "allium"};
            case BASTION -> new String[]{"crimson_roots", "crimson_fungus", "warped_roots", "nether_sprouts"};
            case HARBOR -> new String[]{"snow[layers=2]", "sweet_berry_bush[age=1]", "snow[layers=3]"};
            case BAZAAR -> new String[]{"dead_bush", "orange_tulip", "dead_bush"};
            case COURTYARD -> new String[]{"poppy", "dandelion", "cornflower", "oxeye_daisy"};
            case MUSHROOM -> new String[]{"red_mushroom", "brown_mushroom", "red_mushroom"};
            case CITADEL -> new String[]{"chorus_flower[age=5]", "end_rod[facing=up]"};
            default -> new String[]{"short_grass"};
        };
        String soil = switch (this.style) {
            case BASTION -> "crimson_nylium";
            case HARBOR -> "snow_block";
            case BAZAAR -> "sand";
            case MUSHROOM -> "mycelium";
            case CITADEL -> "end_stone";
            default -> "grass_block";
        };
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = sx * qx;
                int z = sz * qz;
                this.flowerBed(x - 2, z - 1, x + 2, z + 1, 1, rim, soil, flowers);
                this.set(x, 0, z, soil);
                this.set(x, 1, z, this.style == Style.BASTION ? "crimson_fungus" : (this.style == Style.CITADEL ? "chorus_flower[age=5]" : leaves(this.leafType())));
            }
        }
    }

    private String leafType() {
        return switch (this.style) {
            case SAKURA -> "cherry";
            case TEMPLE -> "jungle";
            case HARBOR -> "spruce";
            case MUSHROOM -> "dark_oak";
            default -> "azalea";
        };
    }

    /** A channel of water, lava or sand right outside the low wall (under the barrier, out of reach). */
    private void channel() {
        String fill = switch (this.style) {
            case BASTION -> "lava";
            case SAKURA, COURTYARD, TEMPLE, MUSHROOM -> "water";
            case HARBOR -> "ice";
            default -> null;
        };
        if (fill == null || this.style.build || this.style == Style.TRAINING) {
            return;
        }

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = sd(x, z);
                if (d > this.P + 1.6 && d <= this.P + 3.6) {
                    int y = this.h(x, z);
                    this.set(x, y - 1, z, this.style == Style.BASTION ? "blackstone" : "stone_bricks");
                    this.set(x, y, z, fill);
                    for (int yy = y + 1; yy <= y + 6; yy++) {
                        if (!this.empty(x, yy, z) && !"barrier".equals(this.get(x, yy, z))) {
                            this.set(x, yy, z, null);
                        }
                    }
                } else if (d > this.P + 3.6 && d <= this.P + 4.6) {
                    int y = this.h(x, z);
                    this.set(x, y, z, this.style == Style.BASTION ? "polished_blackstone_bricks" : "stone_bricks");
                    this.set(x, y + 1, z, slab(this.stairMat(), false));
                }
            }
        }
    }

    // ------------------------------------------------------------------ spawn pads and arches

    private void spawnPads() {
        for (int side = -1; side <= 1; side += 2) {
            int sx = side * this.S;
            int y = this.h(sx, 0);
            String[] in = this.inlay();
            String stair = this.stairMat();
            String face = side < 0 ? "east" : "west";
            // a 5x5 pad one block up with stair edges, and the centre block marked
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int yy = Math.min(y, 0); yy <= y; yy++) {
                        this.set(sx + dx, yy, dz, in[3]);
                    }

                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (edge) {
                        String f = Math.abs(dx) == 2 ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north");
                        if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                            this.set(sx + dx, y + 1, dz, slab(stair, false));
                        } else {
                            this.set(sx + dx, y + 1, dz, stairs(stair, f, false));
                        }
                    } else {
                        this.set(sx + dx, y + 1, dz, dx == 0 && dz == 0 ? in[2] : in[0]);
                    }
                }
            }

            // the arch behind the player, with banners and a hanging light
            int ax = sx - side * 3;
            String pillar = this.pillarMat();
            for (int dz = -3; dz <= 3; dz += 6) {
                for (int i = 1; i <= 6; i++) {
                    this.set(ax, y + i, dz, pillar);
                }

                this.set(ax, y + 7, dz, in[4].contains("[") ? in[3] : in[4]);
                this.banner(ax + side, y + 5, dz, this.bannerColor(), side < 0 ? "west" : "east");
                this.banner(ax - side, y + 5, dz, this.bannerColor(), face);
            }

            for (int dz = -2; dz <= 2; dz++) {
                this.set(ax, y + 7, dz, in[3]);
                this.set(ax, y + 8, dz, Math.abs(dz) == 2 ? slab(stair, false) : (dz == 0 ? in[0] : slab(stair, false)));
            }

            this.set(ax, y + 6, -2, stairs(stair, "south", true));
            this.set(ax, y + 6, 2, stairs(stair, "north", true));
            this.hangingLantern(ax, y + 6, 0, 1, this.style == Style.BASTION);
        }
    }

    private String stairMat() {
        return switch (this.style) {
            case TEMPLE, COURTYARD, UHC_RUINS, TRAINING -> "stone_brick";
            case SAKURA -> "cherry";
            case BASTION -> "polished_blackstone_brick";
            case HARBOR, UHC_TAIGA -> "spruce";
            case BAZAAR -> "smooth_sandstone";
            case MUSHROOM -> "mossy_cobblestone";
            case CITADEL -> "purpur";
            case UHC_MEADOW -> "oak";
            case UHC_CANYON -> "red_sandstone";
        };
    }

    private String pillarMat() {
        return switch (this.style) {
            case TEMPLE, UHC_RUINS -> "mossy_stone_bricks";
            case TRAINING -> "quartz_pillar[axis=y]";
            case SAKURA -> "stripped_cherry_log[axis=y]";
            case BASTION -> "polished_blackstone_bricks";
            case HARBOR, UHC_TAIGA -> "stripped_spruce_log[axis=y]";
            case BAZAAR -> "cut_sandstone";
            case COURTYARD -> "stone_bricks";
            case MUSHROOM -> "mushroom_stem";
            case CITADEL -> "purpur_pillar[axis=y]";
            case UHC_MEADOW -> "stripped_oak_log[axis=y]";
            case UHC_CANYON -> "cut_red_sandstone";
        };
    }

    private String bannerColor() {
        return switch (this.style) {
            case TEMPLE, UHC_RUINS -> "green";
            case TRAINING -> "light_blue";
            case SAKURA -> "pink";
            case BASTION -> "black";
            case HARBOR -> "light_blue";
            case BAZAAR, UHC_CANYON -> "orange";
            case COURTYARD -> "red";
            case MUSHROOM -> "brown";
            case CITADEL -> "magenta";
            case UHC_MEADOW -> "yellow";
            case UHC_TAIGA -> "cyan";
        };
    }

    private String wallMat() {
        return switch (this.style) {
            case TEMPLE, UHC_RUINS -> "mossy_stone_brick_wall";
            case TRAINING -> "stone_brick_wall";
            case SAKURA -> "cherry_fence";
            case BASTION -> "polished_blackstone_brick_wall";
            case HARBOR, UHC_TAIGA -> "spruce_fence";
            case BAZAAR -> "sandstone_wall";
            case COURTYARD -> "stone_brick_wall";
            case MUSHROOM -> "mossy_cobblestone_wall";
            case CITADEL -> "end_stone_brick_wall";
            case UHC_MEADOW -> "oak_fence";
            case UHC_CANYON -> "red_sandstone_wall";
        };
    }

    private String light() {
        return switch (this.style) {
            case BASTION -> "soul_lantern[hanging=false]";
            case HARBOR -> "sea_lantern";
            case MUSHROOM -> "shroomlight";
            case CITADEL -> "end_rod[facing=up]";
            default -> "lantern[hanging=false]";
        };
    }

    // ------------------------------------------------------------------ the decorated edge

    private void edge() {
        String wall = this.wallMat();
        String pillar = this.pillarMat();
        String light = this.light();
        String stair = this.stairMat();
        int i = 0;
        for (int x = -this.P - 1; x <= this.P + 1; x++) {
            for (int z = -this.P - 1; z <= this.P + 1; z++) {
                double d = sd(x, z);
                if (d > this.P - 0.5 && d <= this.P + 0.5) {
                    int y = this.h(x, z);
                    boolean post = Math.floorMod(x * 31 + z * 17, 7) == 0 && this.hash3(30L, x, y, z) > 0.35;
                    // a low trimmed wall around the floor: fence/wall on a slab plinth, posts with lights
                    this.set(x, y + 1, z, slab(stair, false));
                    if (post) {
                        this.pillar(x, y + 1, z, 4, pillar, pillar, pillar, light);
                    } else {
                        this.set(x, y + 2, z, wall);
                    }

                    i++;
                } else if (d > this.P - 3.5 && d <= this.P - 0.5 && !this.style.build) {
                    // a strip of plants and props just inside the wall
                    int y = this.h(x, z);
                    double r = this.hash3(32L, x, y, z);
                    if (r > 0.93) {
                        this.prop(x, y + 1, z);
                    } else if (r > 0.75) {
                        this.plant(x, y + 1, z, this.plantFor(x, z));
                    }
                }
            }
        }

        // corner towers of the low wall (big pillars with lights)
        int c = (int)Math.round(this.P * 0.707) - 1;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = sx * c;
                int z = sz * c;
                this.bigPillar(x, this.h(x, z) + 1, z, 6, this.inlay()[3], stair, pillar, light);
            }
        }

        if (this.style.build) {
            this.cover();
        }
    }

    private String plantFor(int x, int z) {
        double r = this.hash3(33L, x, 0, z);
        return switch (this.style) {
            case TEMPLE -> r > 0.6 ? "fern" : "short_grass";
            case TRAINING -> "short_grass";
            case SAKURA -> r > 0.7 ? "pink_tulip" : (r > 0.4 ? "lily_of_the_valley" : "short_grass");
            case BASTION -> r > 0.6 ? "crimson_roots" : (r > 0.3 ? "nether_sprouts" : "crimson_fungus");
            case HARBOR -> "snow[layers=2]";
            case BAZAAR -> r > 0.6 ? "dead_bush" : "flower_pot";
            case COURTYARD -> r > 0.7 ? "poppy" : (r > 0.4 ? "dandelion" : "short_grass");
            case MUSHROOM -> r > 0.5 ? "red_mushroom" : "brown_mushroom";
            case CITADEL -> "end_rod[facing=up]";
            case UHC_MEADOW -> r > 0.8 ? "cornflower" : (r > 0.6 ? "oxeye_daisy" : "short_grass");
            case UHC_CANYON -> "dead_bush";
            case UHC_TAIGA -> r > 0.5 ? "fern" : "sweet_berry_bush[age=1]";
            case UHC_RUINS -> r > 0.7 ? "azure_bluet" : "short_grass";
        };
    }

    /** A small prop by the wall: barrels, pots, hay, candles... never in the middle of the fight. */
    private void prop(int x, int y, int z) {
        double r = this.hash3(34L, x, y, z);
        switch (this.style) {
            case TEMPLE -> this.set(x, y, z, r > 0.5 ? "decorated_pot" : "mossy_cobblestone_wall");
            case SAKURA -> this.set(x, y, z, r > 0.5 ? "potted_cherry_sapling" : "bamboo_mosaic_slab[type=bottom]");
            case BASTION -> {
                this.set(x, y, z, "gilded_blackstone");
                this.set(x, y + 1, z, "soul_lantern[hanging=false]");
            }
            case HARBOR -> {
                this.set(x, y, z, "barrel[facing=up]");
                if (r > 0.7) {
                    this.set(x, y + 1, z, "barrel[facing=up]");
                }
            }
            case BAZAAR -> this.set(x, y, z, r > 0.5 ? "decorated_pot" : "potted_cactus");
            case COURTYARD -> {
                this.set(x, y, z, r > 0.5 ? "hay_block" : "barrel[facing=up]");
                if (r > 0.8) {
                    this.set(x, y + 1, z, "carved_pumpkin[facing=north]");
                }
            }
            case MUSHROOM -> this.set(x, y, z, r > 0.5 ? "potted_red_mushroom" : "potted_brown_mushroom");
            case CITADEL -> {
                this.set(x, y, z, "purpur_pillar[axis=y]");
                this.set(x, y + 1, z, "end_rod[facing=up]");
            }
            default -> this.set(x, y, z, "mossy_cobblestone");
        }
    }

    /** Cover on the building maps: trees, boulders, ruined walls - away from the spawns and the middle. */
    private void cover() {
        int n = (int)(this.P * this.P / (this.style == Style.UHC_TAIGA ? 160.0 : 110.0));
        for (int k = 0; k < n * 3 && n > 0; k++) {
            int x = (int)Math.round((this.hash3(40L + k, k, 0, 0) * 2.0 - 1.0) * (this.P - 4));
            int z = (int)Math.round((this.hash3(41L + k, 0, k, 0) * 2.0 - 1.0) * (this.P - 4));
            double ns = Math.min(dist(x - this.S, z), dist(x + this.S, z));
            if (!this.inside(x, z) || sd(x, z) > this.P - 4 || ns < 8.0 || dist(x, z) < 7.0) {
                continue;
            }

            int y = this.h(x, z) + 1;
            double r = this.hash3(42L + k, x, y, z);
            switch (this.style) {
                case UHC_MEADOW -> {
                    if (r > 0.45) {
                        this.niceTree(x, y, z, r > 0.75 ? "birch_log" : "oak_log", r > 0.75 ? "birch" : "oak", 4 + (int)(r * 3), 2.6);
                    } else if (r > 0.2) {
                        this.bush(x, y, z, "oak", 1.4);
                    } else {
                        this.boulder(x, y, z, 1.6, "stone", "andesite", "mossy_cobblestone");
                    }
                }
                case UHC_CANYON -> {
                    if (r > 0.5) {
                        this.set(x, y, z, "cactus");
                        this.set(x, y + 1, z, "cactus");
                    } else {
                        this.boulder(x, y, z, 1.8, "terracotta", "orange_terracotta", "red_sandstone");
                    }
                }
                case UHC_TAIGA -> {
                    if (r > 0.35) {
                        this.conifer(x, y, z, "spruce_log", "spruce", 6 + (int)(r * 5));
                    } else {
                        this.boulder(x, y, z, 1.8, "mossy_cobblestone", "cobblestone", "stone");
                    }
                }
                case UHC_RUINS -> {
                    if (r > 0.55) {
                        this.ruinWall(x, y, z, r > 0.8);
                    } else if (r > 0.3) {
                        this.niceTree(x, y, z, "oak_log", "oak", 4, 2.4);
                    } else {
                        this.bush(x, y, z, "azalea", 1.3);
                    }
                }
                default -> {
                }
            }

            if (k > n * 2) {
                break;
            }
        }

        // ground cover between the trees
        for (int x = -this.P; x <= this.P; x++) {
            for (int z = -this.P; z <= this.P; z++) {
                if (this.inside(x, z) && sd(x, z) < this.P - 1.5 && this.hash3(43L, x, 0, z) > 0.86) {
                    this.plant(x, this.h(x, z) + 1, z, this.plantFor(x, z));
                }
            }
        }
    }

    private void ruinWall(int x, int y, int z, boolean alongX) {
        int len = 3 + (int)(this.hash3(44L, x, y, z) * 4);
        for (int i = 0; i < len; i++) {
            int bx = alongX ? x + i : x;
            int bz = alongX ? z : z + i;
            int top = 1 + (int)(this.hash3(45L, bx, y, bz) * 3);
            for (int j = 0; j < top; j++) {
                this.set(bx, this.h(bx, bz) + 1 + j, bz, this.texture(46L, bx + j * 5, bz, "mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "mossy_cobblestone"));
            }

            if (this.hash3(47L, bx, y, bz) > 0.6) {
                this.set(bx, this.h(bx, bz) + 1 + top, bz, "moss_carpet");
            }
        }
    }

    // ------------------------------------------------------------------ the scenery outside

    private void backdrop() {
        int out = this.P + 3;
        int n = (int)(this.P * 1.6);
        for (int k = 0; k < n; k++) {
            double a = Math.PI * 2.0 * k / n + this.hash3(50L, k, 0, 0) * 0.3;
            double rr = out + this.hash3(51L, k, 1, 0) * (BACKDROP - 5);
            int x = (int)Math.round(Math.cos(a) * rr);
            int z = (int)Math.round(Math.sin(a) * rr);
            if (sd(x, z) <= this.P + 5.0 || Math.abs(x) > this.R - 2 || Math.abs(z) > this.R - 2) {
                continue;
            }

            int y = this.h(x, z) + 1;
            if (this.style == Style.CITADEL && this.empty(x, y - 1, z)) {
                continue;
            }

            double r = this.hash3(52L + k, x, y, z);
            switch (this.style) {
                case TEMPLE -> {
                    if (r > 0.8) {
                        this.ziggurat(x, y - 1, z);
                    } else {
                        this.niceTree(x, y, z, "jungle_log", "jungle", 6 + (int)(r * 6), 3.2);
                    }
                }
                case SAKURA -> {
                    if (r > 0.88) {
                        this.torii(x, y, z, Math.abs(Math.cos(a)) > 0.7);
                    } else {
                        this.niceTree(x, y, z, "cherry_log", "cherry", 4 + (int)(r * 3), 3.0);
                    }
                }
                case BASTION -> {
                    if (r > 0.6) {
                        this.fungus(x, y, z, r > 0.8);
                    } else {
                        this.cone(x, z, y - 1, y + 6 + (int)(r * 8), 3.0, "basalt[axis=y]");
                    }
                }
                case HARBOR -> {
                    if (r > 0.9 && k % 7 == 0) {
                        this.towerAt(x, 0, z, 3.0, 14, "white_concrete", "red_concrete", "red_terracotta", "sea_lantern");
                    } else if (!this.empty(x, y - 1, z) && !this.get(x, y - 1, z).contains("ice")) {
                        this.conifer(x, y, z, "spruce_log", "spruce", 6 + (int)(r * 5));
                        this.set(x, y + 7 + (int)(r * 5), z, "snow[layers=3]");
                    } else {
                        this.dock(x, z);
                    }
                }
                case BAZAAR -> {
                    if (r > 0.75) {
                        this.dome(x, y, z);
                    } else if (r > 0.4) {
                        this.palm(x, y, z);
                    } else {
                        this.tent(x, y, z);
                    }
                }
                case COURTYARD -> {
                    if (r > 0.85) {
                        this.towerAt(x, y - 1, z, 3.5, 16, "stone_bricks", "polished_andesite", "dark_oak_planks", "lantern[hanging=false]");
                    } else {
                        this.niceTree(x, y, z, "oak_log", "oak", 5, 2.8);
                    }
                }
                case MUSHROOM -> this.giantMushroom(x, y, z, r > 0.5, 6 + (int)(r * 6));
                case CITADEL -> {
                    if (r > 0.7) {
                        this.towerAt(x, y - 1, z, 2.5, 12 + (int)(r * 10), "purpur_block", "purpur_pillar[axis=y]", "purpur_block", "end_rod[facing=up]");
                    } else {
                        this.chorus(x, y, z, 3 + (int)(r * 4));
                    }
                }
                case TRAINING -> {
                    if (r > 0.75) {
                        this.lampPost(x, y, z, "polished_andesite", "stone_brick_wall", "sea_lantern", 4);
                    } else {
                        this.niceTree(x, y, z, r > 0.4 ? "birch_log" : "oak_log", r > 0.4 ? "birch" : "oak", 4 + (int)(r * 3), 2.6);
                    }
                }
                case UHC_MEADOW -> this.niceTree(x, y, z, r > 0.6 ? "birch_log" : "oak_log", r > 0.6 ? "birch" : "oak", 5 + (int)(r * 3), 3.0);
                case UHC_CANYON -> this.cone(x, z, y - 1, y + 8 + (int)(r * 12), 3.5, CANYON_BANDS[k % CANYON_BANDS.length]);
                case UHC_TAIGA -> this.conifer(x, y, z, "spruce_log", "spruce", 8 + (int)(r * 6));
                case UHC_RUINS -> {
                    if (r > 0.7) {
                        this.towerAt(x, y - 1, z, 3.0, 8 + (int)(r * 6), "mossy_stone_bricks", "cracked_stone_bricks", "mossy_cobblestone", null);
                    } else {
                        this.niceTree(x, y, z, "dark_oak_log", "dark_oak", 5, 3.0);
                    }
                }
            }
        }

        if (this.style == Style.SAKURA || this.style == Style.COURTYARD || this.style == Style.UHC_MEADOW) {
            // a pond outside one side with lily pads and reeds
            this.pond(0, this.P + 8, 5.0);
        }
    }

    /** A small stepped temple with a lit shrine on top. */
    private void ziggurat(int x, int y, int z) {
        int yy = y;
        for (int hw = 4; hw >= 1; hw--) {
            for (int dx = -hw; dx <= hw; dx++) {
                for (int dz = -hw; dz <= hw; dz++) {
                    boolean rim = Math.abs(dx) == hw || Math.abs(dz) == hw;
                    this.set(x + dx, yy, z + dz, rim ? this.texture(59L, x + dx, z + dz + yy, "mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks") : "stone_bricks");
                    this.set(x + dx, yy + 1, z + dz, rim ? "mossy_stone_bricks" : "stone_bricks");
                }
            }

            yy += 2;
        }

        this.set(x, yy, z, "chiseled_stone_bricks");
        this.set(x, yy + 1, z, "lantern[hanging=false]");
        for (int i = 0; i < 4; i++) {
            this.set(x + (i % 2 == 0 ? (i == 0 ? 2 : -2) : 0), y + 4, z + (i % 2 == 1 ? (i == 1 ? 2 : -2) : 0), "moss_block");
        }
    }

    private void torii(int x, int y, int z, boolean alongX) {
        for (int s = -2; s <= 2; s += 4) {
            int px = alongX ? x : x + s;
            int pz = alongX ? z + s : z;
            for (int i = 0; i < 6; i++) {
                this.set(px, y + i, pz, "red_terracotta");
            }
        }

        for (int s = -3; s <= 3; s++) {
            this.set(alongX ? x : x + s, y + 6, alongX ? z + s : z, "red_terracotta");
            this.set(alongX ? x : x + s, y + 7, alongX ? z + s : z, Math.abs(s) == 3 ? slab("dark_oak", false) : "dark_oak_planks");
        }

        this.set(x, y + 4, z, "red_terracotta");
    }

    private void fungus(int x, int y, int z, boolean warped) {
        String stem = warped ? "warped_stem[axis=y]" : "crimson_stem[axis=y]";
        String cap = warped ? "warped_wart_block" : "nether_wart_block";
        int h = 5 + (int)(this.hash3(53L, x, y, z) * 4);
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, stem);
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    this.set(x + dx, y + h, z + dz, this.hash3(54L, dx, h, dz) > 0.9 ? "shroomlight" : cap);
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        this.setIfEmpty(x + dx, y + h - 1, z + dz, cap);
                    }
                }
            }
        }

        this.set(x, y + h + 1, z, cap);
    }

    private void dock(int x, int z) {
        for (int i = -1; i <= 1; i++) {
            this.set(x + i, 1, z, "spruce_slab[type=bottom]");
        }

        this.set(x - 2, 1, z, "spruce_fence");
        this.set(x - 2, 2, z, "lantern[hanging=false]");
    }

    private void palm(int x, int y, int z) {
        int h = 6 + (int)(this.hash3(55L, x, y, z) * 3);
        int bend = this.hash3(56L, x, y, z) > 0.5 ? 1 : -1;
        int tx = x;
        for (int i = 0; i < h; i++) {
            if (i == h / 2) {
                tx += bend;
            }

            this.set(tx, y + i, z, "jungle_log[axis=y]");
        }

        int top = y + h;
        this.set(tx, top, z, "jungle_leaves[persistent=true]");
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int s = 1; s <= 3; s++) {
                this.set(tx + d[0] * s, top - (s == 3 ? 1 : 0), z + d[1] * s, "jungle_leaves[persistent=true]");
            }
        }

        this.set(tx + 1, top - 1, z, "cocoa[age=2,facing=west]");
    }

    private void dome(int x, int y, int z) {
        this.box(x - 3, y, z - 3, x + 3, y + 3, z + 3, "smooth_sandstone");
        this.box(x - 2, y, z - 2, x + 2, y + 3, z + 2, null);
        this.set(x, y, z - 3, null);
        this.set(x, y + 1, z - 3, null);
        this.ellipsoid(x, y + 4, z, 3.3, 3.0, 3.3, "white_terracotta");
        this.set(x, y + 7, z, "gold_block");
        this.set(x, y + 8, z, "end_rod[facing=up]");
    }

    private void tent(int x, int y, int z) {
        String wool = new String[]{"red_wool", "orange_wool", "yellow_wool", "blue_wool"}[(int)(this.hash3(57L, x, y, z) * 4)];
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                this.set(x + dx, y, z + dz, "oak_fence");
                this.set(x + dx, y + 1, z + dz, "oak_fence");
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                this.set(x + dx, y + 2 + (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? 1 : 0), z + dz, Math.floorMod(dx, 2) == 0 ? wool : "white_wool");
            }
        }

        this.set(x, y, z, "barrel[facing=up]");
    }

    private void giantMushroom(int x, int y, int z, boolean red, int h) {
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, "mushroom_stem");
        }

        String cap = red ? "red_mushroom_block" : "brown_mushroom_block";
        double r = red ? 3.2 : 4.2;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (red) {
                    if (d <= r) {
                        this.set(x + dx, y + h, z + dz, cap);
                    }

                    if (d > r - 1.2 && d <= r + 0.4) {
                        this.set(x + dx, y + h - 1, z + dz, cap);
                        this.set(x + dx, y + h - 2, z + dz, cap);
                    }
                } else if (d <= r) {
                    this.set(x + dx, y + h, z + dz, cap);
                }
            }
        }

        this.set(x, y + h - 1, z, "shroomlight");
    }

    private void chorus(int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, "chorus_plant[up=true,down=true]");
        }

        this.set(x, y + h, z, "chorus_flower[age=5]");
        this.set(x + 1, y + h - 2, z, "chorus_flower[age=5]");
    }

    private void pond(int ox, int oz, double r) {
        int n = (int)Math.ceil(r);
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz * 1.4);
                int x = ox + dx;
                int z = oz + dz;
                if (d > r || sd(x, z) <= this.P + 2.5 || Math.abs(z) > this.R - 1) {
                    continue;
                }

                int top = this.h(x, z);
                for (int y = top; y > top - 2; y--) {
                    this.set(x, y, z, "water");
                }

                for (int y = top + 1; y <= top + 4; y++) {
                    this.set(x, y, z, null);
                }

                if (this.hash3(58L, x, 0, z) > 0.8) {
                    this.set(x, top + 1, z, "lily_pad");
                }
            }
        }
    }

    // ------------------------------------------------------------------ invisible walls

    private void barrier() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = sd(x, z);
                if (d > this.P + 0.5 && d <= this.P + 1.6) {
                    for (int y = this.h(x, z) + 1; y <= CEILING; y++) {
                        if (this.empty(x, y, z)) {
                            this.set(x, y, z, "barrier");
                        }
                    }
                } else if (d <= this.P + 0.5) {
                    this.set(x, CEILING, z, "barrier");
                }
            }
        }
    }
}
