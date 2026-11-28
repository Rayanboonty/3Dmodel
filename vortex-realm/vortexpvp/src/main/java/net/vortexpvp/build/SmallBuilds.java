package net.vortexpvp.build;

import java.util.Locale;

public final class SmallBuilds extends MegaVoxels {
    private final SmallBuilds.Type type;

    public SmallBuilds(int cx, int floorY, int cz, SmallBuilds.Type type, boolean alongX, int length, String face, long seed) {
        super(cx, floorY, cz, type.wall() ? Math.max(4, length / 2 + 2) : type.radius + 2, -2, 40, seed);
        this.type = type;
        if (type.wall()) {
            this.wall(alongX, Math.max(4, Math.min(200, length)));
        } else {
            this.build(face);
        }

        this.finish();
    }

    @Override
    public String name() {
        return "small-" + this.type.name().toLowerCase(Locale.ROOT);
    }

    private void build(String face) {
        switch (this.type) {
            case FOUNTAIN: {
                    this.disc(0.0, 0.0, 0, 7.0, "polished_andesite");
                    this.ring(0.0, 0.0, 0, 6.2, 7.0, "stone_bricks");
                    this.fountain(0, 0, 5, "stone_bricks", "chiseled_stone_bricks");

                    for (int a = 45; a < 360; a += 90) {
                        this.lamp(px(6.5, a), pz(6.5, a), 4, "stone_brick_wall", "lantern");
                    }
                    break;
            }
            case SWORD_STATUE: {
                    this.disc(0.0, 0.0, 0, 5.0, "polished_blackstone_bricks");
                    this.ring(0.0, 0.0, 0, 4.2, 5.0, "gold_block");
                    this.giantSword(
                        0, 0, 26, "diamond_block", "light_blue_concrete", "gold_block", "dark_oak_log[axis=y]", face.equals("north") || face.equals("south")
                    );
                    break;
            }
            case GAZEBO: {
                    this.disc(0.0, 0.0, 0, 6.0, "smooth_stone");

                    for (int a = 0; a < 360; a += 45) {
                        for (int y = 1; y <= 4; y++) {
                            this.set(px(5.0, a), y, pz(5.0, a), "stripped_dark_oak_log[axis=y]");
                        }
                    }

                    for (int k = 0; k <= 4; k++) {
                        this.ring(0.0, 0.0, 5 + k, Math.max(0.0, 6.0 - k * 1.4 - 1.0), 6.5 - k * 1.4, "dark_oak_planks");
                    }

                    this.set(0, 4, 0, "lantern[hanging=true]");
                    this.bench(0, -3, "south", true);
                    this.bench(0, 3, "north", true);
                    break;
            }
            case WATCHTOWER: {
                    for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                        for (int y = 0; y <= 14; y++) {
                            this.set(c[0], y, c[1], "spruce_log[axis=y]");
                        }
                    }

                    this.box(-4, 14, -4, 4, 14, 4, "spruce_planks");
                    this.walls(-4, 15, -4, 4, 15, 4, "spruce_fence");

                    for (int k = 0; k <= 4; k++) {
                        for (int x = -5 + k; x <= 5 - k; x++) {
                            this.set(x, 18 + k, -5 + k, stairs("dark_oak", "south", false));
                            this.set(x, 18 + k, 5 - k, stairs("dark_oak", "north", false));
                        }

                        for (int z = -4 + k; z <= 4 - k; z++) {
                            this.set(-5 + k, 18 + k, z, stairs("dark_oak", "east", false));
                            this.set(5 - k, 18 + k, z, stairs("dark_oak", "west", false));
                        }
                    }

                    for (int y = 1; y <= 13; y++) {
                        this.set(0, y, 3, "ladder[facing=north]");
                        this.set(0, y, 4, "spruce_planks");
                    }

                    this.set(0, 14, 3, "air");
                    this.set(0, 17, 0, "lantern[hanging=true]");
                    break;
            }
            case GATE_ARCH: {
                    boolean alongX = face.equals("east") || face.equals("west");
                    this.arch(0, 0, 12, 12, "stone_bricks", "chiseled_stone_bricks", alongX);

                    for (int s = -7; s <= 7; s += 14) {
                        int x = alongX ? 0 : s;
                        int z = alongX ? s : 0;
                        this.towerAt(x, 0, z, 2.5, 14, "stone_bricks", "stone_brick_wall", "dark_oak_planks", "lantern");
                    }
                    break;
            }
            case MARKET: {
                    String[] wool = new String[]{"red_wool", "orange_wool", "yellow_wool", "lime_wool", "light_blue_wool", "white_wool"};

                    for (int i = 0; i < 4; i++) {
                        int ox = -7 + i % 2 * 14;
                        int oz = -5 + i / 2 * 10;

                        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                            for (int y = 1; y <= 3; y++) {
                                this.set(ox + c[0], y, oz + c[1], "spruce_fence");
                            }
                        }

                        for (int x = -3; x <= 3; x++) {
                            for (int z = -3; z <= 3; z++) {
                                this.set(ox + x, 4, oz + z, (x + 3) % 2 == 0 ? wool[i] : "white_wool");
                            }
                        }

                        this.box(ox - 2, 1, oz + 2, ox + 2, 1, oz + 2, "barrel[facing=up]");
                        this.set(ox - 1, 2, oz + 2, "melon");
                        this.set(ox + 1, 2, oz + 2, "pumpkin");
                        this.set(ox, 2, oz + 2, "hay_block");
                    }
                    break;
            }
            case WELL: {
                    this.box(-2, 0, -2, 2, 0, 2, "cobblestone");
                    this.walls(-2, 1, -2, 2, 1, 2, "cobblestone");
                    this.box(-1, 0, -1, 1, 0, 1, "water");

                    for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                        this.set(c[0], 2, c[1], "oak_fence");
                        this.set(c[0], 3, c[1], "oak_fence");
                    }

                    this.box(-2, 4, -2, 2, 4, 2, "oak_slab[type=bottom]");
                    this.set(0, 3, 0, "iron_chain[axis=y]");
                    this.set(0, 2, 0, "lantern[hanging=true]");
                    break;
            }
            case GROVE: {
                    boolean was = this.soft;

                    for (int i = 0; i < 7; i++) {
                        double a = i * 51.4;
                        int x = px(3 + this.rnd.nextInt(6), a);
                        int z = pz(3 + this.rnd.nextInt(6), a);
                        this.tree(x, 1, z, i % 2 == 0 ? "oak_log" : "birch_log", i % 2 == 0 ? "oak" : "birch", 4 + this.rnd.nextInt(3), 2.6 + this.rnd.nextDouble());
                    }

                    this.soft = was;
                    this.tree(0, 1, 0, "cherry_log", "cherry", 5, 3.4);
                    break;
            }
            case BENCH_PLAZA: {
                    this.disc(0.0, 0.0, 0, 7.0, "smooth_stone");
                    this.roundPlaza(0, 0, 7.0, "smooth_stone", "polished_andesite", "stone_bricks");

                    for (int a = 0; a < 360; a += 90) {
                        int x = px(5.0, a);
                        int z = pz(5.0, a);
                        this.bench(x, z, facing(-Math.cos(Math.toRadians(a)), -Math.sin(Math.toRadians(a))), a % 180 != 0);
                    }

                    this.set(0, 1, 0, "moss_block");
                    this.tree(0, 2, 0, "oak_log", "oak", 3, 2.4);

                    for (int a = 45; a < 360; a += 90) {
                        this.lamp(px(6.0, a), pz(6.0, a), 4, "stone_brick_wall", "lantern");
                    }
                    break;
            }
            case RUINS: {
                    this.disc(0.0, 0.0, 0, 8.0, "mossy_stone_bricks");

                    for (int a = 0; a < 360; a += 36) {
                        int h = 2 + this.rnd.nextInt(7);

                        for (int y = 1; y <= h; y++) {
                            this.set(
                                px(7.0, a), y, pz(7.0, a), y == h ? "cracked_stone_bricks" : (this.rnd.nextInt(4) == 0 ? "mossy_stone_bricks" : "stone_bricks")
                            );
                        }
                    }

                    for (int i = 0; i < 14; i++) {
                        this.setIfEmpty(
                            this.rnd.nextInt(13) - 6, 1, this.rnd.nextInt(13) - 6, this.rnd.nextBoolean() ? "mossy_cobblestone" : "stone_brick_slab[type=bottom]"
                        );
                    }
                    break;
            }
            case LAMP_ROW: {
                    boolean alongX = face.equals("east") || face.equals("west");

                    for (int s = -12; s <= 12; s += 4) {
                        this.lamp(alongX ? s : -3, alongX ? -3 : s, 5, "polished_blackstone_wall", "lantern");
                        this.lamp(alongX ? s : 3, alongX ? 3 : s, 5, "polished_blackstone_wall", "lantern");
                    }

                    for (int s = -12; s <= 12; s++) {
                        for (int w = -2; w <= 2; w++) {
                            this.set(alongX ? s : w, 0, alongX ? w : s, Math.abs(w) == 2 ? "polished_blackstone_bricks" : "polished_andesite");
                        }
                    }
                    break;
            }
            default: {
                    this.box(-1, 0, -1, 1, 3, 1, "gold_block");
                    this.box(-4, 0, -1, -2, 2, 1, "iron_block");
                    this.box(2, 0, -1, 4, 1, 1, "copper_block");
                    this.box(-5, 0, -2, 5, 0, 2, "polished_blackstone_bricks");
                    this.set(0, 4, 0, "red_carpet");
                    this.set(-3, 3, 0, "red_carpet");
                    this.set(3, 2, 0, "red_carpet");
            }
        }
    }

    private void wall(boolean alongX, int len) {
        int half = len / 2;
        double x0 = alongX ? -half : 0.0;
        double z0 = alongX ? 0.0 : -half;
        double x1 = alongX ? half : 0.0;
        double z1 = alongX ? 0.0 : half;
        switch (this.type) {
            case WALL_STONE:
                this.castleWall(x0, z0, x1, z1, 6, 3, "stone_bricks", "stone_brick_wall", "lantern");

                for (int sx = -half; sx <= half; sx += 16) {
                    this.towerAt(alongX ? sx : 0, 0, alongX ? 0 : sx, 3.0, 9, "stone_bricks", "chiseled_stone_bricks", null, "lantern");
                }
                break;
            case WALL_HEDGE:
                for (int sx = -half; sx <= half; sx++) {
                    for (int y = 1; y <= 3; y++) {
                        this.set(alongX ? sx : 0, y, alongX ? 0 : sx, "oak_leaves[persistent=true]");
                    }

                    this.set(alongX ? sx : 0, 0, alongX ? 0 : sx, "moss_block");
                    if (sx % 6 == 0) {
                        this.set(alongX ? sx : 0, 4, alongX ? 0 : sx, "azalea_leaves[persistent=true]");
                    }
                }
                break;
            case WALL_IRON:
                for (int sx = -half; sx <= half; sx++) {
                    boolean pillar = sx % 5 == 0;

                    for (int y = 1; y <= 3; y++) {
                        this.set(alongX ? sx : 0, y, alongX ? 0 : sx, pillar ? "stone_bricks" : "iron_bars");
                    }

                    this.set(alongX ? sx : 0, 0, alongX ? 0 : sx, "stone_bricks");
                    if (pillar) {
                        this.set(alongX ? sx : 0, 4, alongX ? 0 : sx, "lantern");
                    }
                }
                break;
            case WALL_QUARTZ:
                for (int s = -half; s <= half; s++) {
                    boolean pillar = s % 4 == 0;
                    this.set(alongX ? s : 0, 0, alongX ? 0 : s, "smooth_quartz");
                    this.set(alongX ? s : 0, 1, alongX ? 0 : s, pillar ? "quartz_pillar[axis=y]" : "quartz_slab[type=bottom]");
                    if (pillar) {
                        this.set(alongX ? s : 0, 2, alongX ? 0 : s, "chiseled_quartz_block");
                    }
                }
                break;
            default:
                this.castleWall(x0, z0, x1, z1, 5, 3, "red_nether_bricks", "nether_brick_fence", "shroomlight");

                for (int s = -half; s <= half; s += 12) {
                    this.set(alongX ? s : 0, 7, alongX ? 0 : s, "magma_block");
                }
        }
    }

    public static enum Type {
        FOUNTAIN("Fountain", 7),
        SWORD_STATUE("Giant Sword Statue", 8),
        GAZEBO("Gazebo", 7),
        WATCHTOWER("Watchtower", 6),
        GATE_ARCH("Gate Arch", 9),
        MARKET("Market Stalls", 10),
        WELL("Well", 4),
        GROVE("Tree Grove", 10),
        BENCH_PLAZA("Bench Plaza", 8),
        RUINS("Ruins", 9),
        LAMP_ROW("Lamp Row", 12),
        PODIUM("Winners Podium", 5),
        WALL_STONE("Castle Wall", 0),
        WALL_HEDGE("Hedge Wall", 0),
        WALL_IRON("Iron Fence Wall", 0),
        WALL_QUARTZ("Quartz Balustrade", 0),
        WALL_NETHER("Nether Wall", 0);

        public final String display;
        final int radius;

        private Type(String display, int radius) {
            this.display = display;
            this.radius = radius;
        }

        public boolean wall() {
            return this.name().startsWith("WALL_");
        }
    }
}
