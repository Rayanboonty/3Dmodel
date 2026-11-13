package net.vortexpvp.build;

public final class SkyEmpireDesign extends MegaVoxels {
    public SkyEmpireDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -60, 80, seed);
        this.island(0.0, 0, 0.0, 74.0, "grass_block", "stone", seed);
        this.palace();
        int n = 12;

        for (int i = 0; i < n; i++) {
            double a = i * 360.0 / n + 15.0;
            double d = (i % 2 == 0 ? 0.55 : 0.82) * (this.R - 30);
            int x = px(d, a);
            int z = pz(d, a);
            int y = (int)Math.round(Math.sin(i * 1.7) * 14.0) - (i % 2 == 0 ? 0 : 6);
            int r = 18 + i * 7 % 13;
            this.island(x, y, z, r, "grass_block", "stone", seed + i * 31);
            this.landmark(i, x, y, z, r);
            if (i % 2 == 0) {
                double rad = Math.toRadians(a);
                this.bridge(
                    Math.cos(rad) * 70.0,
                    0.0,
                    Math.sin(rad) * 70.0,
                    x - Math.cos(rad) * (r - 2),
                    y,
                    z - Math.sin(rad) * (r - 2),
                    "spruce_planks",
                    "spruce_fence"
                );
            } else {
                double a2 = (i - 1) * 360.0 / n + 15.0;
                double d2 = 0.55 * (this.R - 30);
                int x2 = px(d2, a2);
                int z2 = pz(d2, a2);
                int y2 = (int)Math.round(Math.sin((i - 1) * 1.7) * 14.0);
                this.bridge(x2, y2, z2, x, y, z, "spruce_planks", "spruce_fence");
            }
        }

        for (int ix = 0; ix < 26; ix++) {
            double a = this.rnd.nextDouble() * 360.0;
            double d = 80.0 + this.rnd.nextDouble() * (this.R - 95);
            this.cloud(px(d, a), -20 + this.rnd.nextInt(60), pz(d, a), 5 + this.rnd.nextInt(6));
        }

        this.spawn = new double[]{0.5, 1.0, 54.5, 180.0};
        this.finish();
    }

    @Override
    public String name() {
        return "sky_empire";
    }

    private void palace() {
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                double d = Math.max(Math.abs(x), Math.abs(z));
                if (d <= 40.0) {
                    this.set(x, 0, z, d > 38.0 ? "quartz_bricks" : ((x + z) % 2 == 0 ? "smooth_quartz" : "quartz_block"));
                }
            }
        }

        this.box(-5, 0, 18, 5, 0, 36, "water");
        this.box(-6, -1, 17, 6, -1, 37, "smooth_quartz");

        for (int zx = 18; zx <= 36; zx += 6) {
            this.set(-6, 1, zx, "lantern");
            this.set(6, 1, zx, "lantern");
        }

        this.box(-20, 1, -20, 20, 14, 12, "smooth_quartz");
        this.box(-19, 1, -19, 19, 13, 11, "air");

        for (int x = -20; x <= 20; x += 4) {
            for (int y = 1; y <= 14; y++) {
                this.set(x, y, 13, y == 14 ? "chiseled_quartz_block" : "quartz_pillar[axis=y]");
            }
        }

        for (int x = -18; x <= 18; x += 4) {
            for (int y = 3; y <= 10; y++) {
                this.set(x + 2, y, 12, "yellow_stained_glass_pane");
            }
        }

        this.box(-2, 1, 12, 2, 7, 12, "air");
        this.box(-22, 15, -22, 22, 15, 14, "quartz_bricks");

        for (int y = 0; y <= 18; y++) {
            double r = Math.sqrt(Math.max(0, 324 - y * y));
            this.ring(0.0, -4.0, 16 + y, Math.max(0.0, r - 1.2), r, y > 13 ? "gold_block" : "smooth_quartz");
        }

        this.cylinder(0.0, -4.0, 16, 18, 19.0, "chiseled_quartz_block", true);
        this.set(0, 35, -4, "gold_block");
        this.set(0, 36, -4, "lightning_rod");

        for (int[] c : new int[][]{{-26, -26}, {26, -26}, {-26, 18}, {26, 18}}) {
            this.cylinder(c[0], c[1], 1, 34, 2.5, "smooth_quartz", false);
            this.disc(c[0], c[1], 26, 3.6, "chiseled_quartz_block");
            this.ring(c[0], c[1], 27, 3.0, 3.6, "quartz_slab[type=bottom]");
            this.cone(c[0], c[1], 35, 42, 3.0, "gold_block");
            this.set(c[0], 43, c[1], "end_rod[facing=up]");
        }

        for (int a = 0; a < 360; a += 30) {
            this.grove(px(56.0, a), pz(56.0, a), 7.0, 3, a % 60 == 0 ? "cherry_log" : "oak_log", a % 60 == 0 ? "cherry" : "oak");
        }

        this.meadow(0, 0, 70.0, 0.08);

        for (int x = -12; x <= 12; x += 8) {
            this.set(x, 1, 16, "gold_block");
            this.npc(x, 2.0, 16.0);
        }
    }

    private void landmark(int i, int x, int y, int z, int r) {
        switch (i % 12) {
            case 0:
                this.cylinder(x, z, y + 1, y + 1, 9.0, "smooth_quartz", false);

                for (int a = 0; a < 360; a += 30) {
                    for (int k = 2; k <= 8; k++) {
                        this.set(x + px(7.0, a), y + k, z + pz(7.0, a), "quartz_pillar[axis=y]");
                    }
                }

                for (int k = 0; k <= 7; k++) {
                    double rr = Math.sqrt(Math.max(0, 64 - k * k));
                    this.ring(x, z, y + 9 + k, Math.max(0.0, rr - 1.0), rr, "gold_block");
                }

                this.npc(x, y + 2, z);
                break;
            case 1:
                this.box(x - 3, y + 1, z - 3, x + 3, y + 12, z + 3, "white_terracotta");
                this.box(x - 2, y + 1, z - 2, x + 2, y + 11, z + 2, "air");
                this.cone(x, z, y + 13, y + 18, 4.5, "spruce_planks");

                for (int k = -9; k <= 9; k++) {
                    this.set(x + k, y + 10 + k / 3, z + 4, "spruce_planks");
                    this.set(x + k / 3, y + 10 - k, z + 4, "spruce_planks");
                }

                this.set(x, y + 10, z + 4, "spruce_log[axis=z]");
                break;
            case 2:
                this.groveAt(x, y, z, r * 0.6, 9, "cherry_log", "cherry");
                this.set(x, y + 1, z, "gold_block");
                this.npc(x, y + 2, z);
                break;
            case 3:
                this.cylinder(x, z, y + 1, y + 10, 5.0, "polished_deepslate", true);

                for (int k = 0; k <= 5; k++) {
                    double rr = Math.sqrt(Math.max(0, 36 - k * k));
                    this.ring(x, z, y + 11 + k, Math.max(0.0, rr - 1.0), rr, k > 3 ? "copper_block" : "cut_copper");
                }

                this.line(x, y + 14, z, x + 7, y + 21, z - 3, "iron_block");
                break;
            case 4:
                this.giantSwordAt(x, y + 1, z, 30, "iron_block", "light_gray_concrete", "gold_block", "dark_oak_log[axis=y]", i % 2 == 0);
                break;
            case 5:
                this.cylinder(x, z, y + 1, y + 22, 3.5, "white_concrete", false);

                for (int k = 3; k <= 22; k += 6) {
                    this.ring(x, z, y + k, 2.5, 3.5, "red_concrete");
                }

                this.cylinder(x, z, y + 23, y + 25, 3.0, "yellow_stained_glass", true);
                this.set(x, y + 24, z, "sea_lantern");
                this.cone(x, z, y + 26, y + 29, 3.5, "red_concrete");
                break;
            case 6:
                for (int a = 0; a < 360; a += 40) {
                    int h = 3 + this.rnd.nextInt(8);

                    for (int k = 1; k <= h; k++) {
                        this.set(x + px(9.0, a), y + k, z + pz(9.0, a), k == h ? "cracked_stone_bricks" : "stone_bricks");
                    }
                }

                this.disc(x, z, y, 6.0, "mossy_stone_bricks");
                this.set(x, y + 1, z, "gold_block");
                this.npc(x, y + 2, z);
                break;
            case 7:
                for (int t = 0; t < 4; t++) {
                    int s = 6 - t;
                    int yy = y + 1 + t * 5;
                    this.box(x - s + 1, yy, z - s + 1, x + s - 1, yy + 3, z + s - 1, "dark_oak_planks");
                    this.box(x - s, yy + 4, z - s, x + s, yy + 4, z + s, "red_nether_bricks");

                    for (int k = -s - 1; k <= s + 1; k++) {
                        this.set(x + k, yy + 4, z - s - 1, stairs("red_nether_brick", "south", false));
                        this.set(x + k, yy + 4, z + s + 1, stairs("red_nether_brick", "north", false));
                        this.set(x - s - 1, yy + 4, z + k, stairs("red_nether_brick", "east", false));
                        this.set(x + s + 1, yy + 4, z + k, stairs("red_nether_brick", "west", false));
                    }
                }

                this.set(x, y + 22, z, "lantern");
                break;
            case 8:
                this.disc(x, z, y, r * 0.45, "water");
                this.ring(x, z, y, r * 0.45, r * 0.45 + 1.3, "prismarine_bricks");
                this.disc(x, z, y - 1, r * 0.5, "prismarine_bricks");
                this.groveAt(x, y, z, r * 0.8, 5, "oak_log", "oak");
                break;
            case 9:
                this.walls(x - 7, y + 1, z - 7, x + 7, y + 7, z + 7, "stone_bricks");

                for (int[] c : new int[][]{{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
                    this.towerAt(x + c[0], y + 1, z + c[1], 2.5, 10, "stone_bricks", "stone_brick_wall", "spruce_planks", "lantern");
                }

                this.box(x - 1, y + 1, z + 7, x + 1, y + 4, z + 7, "air");
                break;
            case 10:
                for (int k = 0; k < 9; k++) {
                    double a = k * 40;
                    int h = 4 + this.rnd.nextInt(7);
                    this.line(x + px(5.0, a), y + 1, z + pz(5.0, a), x + px(7.0, a), y + h, z + pz(7.0, a), "amethyst_block");
                }

                this.set(x, y + 1, z, "budding_amethyst");
                break;
            default:
                this.groveAt(x, y, z, r * 0.7, 8, "birch_log", "birch");
        }
    }
}
