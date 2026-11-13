package net.vortexpvp.build;

public final class ColosseumCityDesign extends MegaVoxels {
    private final int ri;
    private final int wallR;
    private static final String[][] PITS = new String[][]{
        {"grass_block", "oak_fence"},
        {"sand", "birch_fence"},
        {"snow_block", "spruce_fence"},
        {"netherrack", "nether_brick_fence"},
        {"end_stone", "purpur_block"},
        {"moss_block", "jungle_fence"},
        {"red_sand", "acacia_fence"},
        {"packed_ice", "spruce_fence"}
    };

    public ColosseumCityDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -38, 72, seed);
        this.ri = (int)Math.round(radius * 0.95);
        this.wallR = this.ri - 8;
        this.islandBase(this.ri, "grass_block", 34, seed);
        this.streets();
        this.colosseum();
        this.districts();
        this.cityWall();
        this.village(
            100.0,
            this.wallR - 18,
            14,
            new String[]{"white_terracotta", "stripped_birch_log[axis=y]", "bricks", "smooth_sandstone"},
            new String[]{"spruce", "dark_oak", "brick", "stone_brick"},
            "spruce_log"
        );
        this.spawn = new double[]{0.5, 1.0, 92.5, 180.0};
        this.finish();
    }

    @Override
    public String name() {
        return "colosseum_city";
    }

    private void streets() {
        for (int x = -100; x <= 100; x++) {
            for (int z = -100; z <= 100; z++) {
                double e = Math.sqrt(x * x / 7744.0 + z * z / 5476.0);
                if (e > 0.96 && e < 1.1) {
                    this.paint(x, z, !(e < 0.975) && !(e > 1.085) ? ((x + z) % 7 == 0 ? "cobblestone" : "polished_andesite") : "stone_bricks");
                }
            }
        }

        this.road(0.0, 80.0, 0.0, this.wallR, 9, "polished_andesite", "stone_bricks", 14, "spruce_fence", "lantern");
        this.road(0.0, -80.0, 0.0, -this.wallR, 9, "polished_andesite", "stone_bricks", 14, "spruce_fence", "lantern");
        this.road(94.0, 0.0, this.wallR, 0.0, 9, "polished_andesite", "stone_bricks", 14, "spruce_fence", "lantern");
        this.road(-94.0, 0.0, -this.wallR, 0.0, 9, "polished_andesite", "stone_bricks", 14, "spruce_fence", "lantern");

        for (int x = -this.wallR; x <= this.wallR; x++) {
            for (int zx = -this.wallR; zx <= this.wallR; zx++) {
                double r = dist(x, zx);
                if (Math.abs(r - (this.wallR - 14)) < 2.2) {
                    this.paint(x, zx, "dirt_path");
                }
            }
        }
    }

    private void colosseum() {
        double ax = 72.0;
        double az = 58.0;

        for (int x = -76; x <= 76; x++) {
            for (int z = -62; z <= 62; z++) {
                double e = Math.sqrt(x * x / (ax * ax) + z * z / (az * az));
                if (!(e > 1.02)) {
                    if (e > 0.985) {
                        double a = Math.toDegrees(Math.atan2(z / az, x / ax));
                        int arch = (int)Math.floor((a + 360.0) / 4.0);
                        double inArch = (a + 360.0) / 4.0 - arch;

                        for (int y = 0; y <= 30; y++) {
                            int storey = y / 10;
                            int yy = y % 10;
                            boolean opening = inArch > 0.25 && inArch < 0.75 && yy >= 1 && yy <= 6 + (inArch > 0.35 && inArch < 0.65 ? 1 : 0) && y < 30;
                            String b = yy != 0 && yy != 9 ? (storey == 2 ? "cut_sandstone" : "sandstone") : "smooth_sandstone";
                            if (x % 2 + z % 2 == 0 && yy == 8) {
                                b = "chiseled_sandstone";
                            }

                            this.set(x, y, z, opening ? "air" : b);
                        }

                        if ((int)(a + 360.0) % 8 == 0) {
                            this.set(x, 31, z, "sandstone_wall");
                            this.set(x, 32, z, "lantern");
                        }
                    } else if (!(e > 0.6)) {
                        if (e > 0.57) {
                            for (int y = 0; y <= 3; y++) {
                                this.set(x, y, z, y == 3 ? "chiseled_sandstone" : "cut_sandstone");
                            }
                        } else {
                            this.set(x, 0, z, "sand");
                            this.set(x, -1, z, "sandstone");
                        }
                    } else {
                        double f = (e - 0.6) / 0.385;
                        int h = (int)Math.round(f * 26.0) + 1;

                        for (int y = 0; y < h; y++) {
                            this.set(x, y, z, "smooth_sandstone");
                        }

                        double a = Math.toDegrees(Math.atan2(z, x));
                        this.set(
                            x,
                            h,
                            z,
                            (int)Math.round(f * 26.0) % 3 == 0
                                ? "sandstone_slab[type=bottom]"
                                : ((int)((a + 360.0) / 9.0) % 2 == 0 ? "red_wool" : "white_wool")
                        );
                    }
                }
            }
        }

        for (int s = -3; s <= 3; s++) {
            for (int t = 40; t <= 76; t++) {
                for (int y = 1; y <= 6; y++) {
                    this.set(s, y, t, "air");
                    this.set(s, y, -t, "air");
                    if (t <= 62) {
                        this.set(t, y, s, "air");
                        this.set(-t, y, s, "air");
                    }
                }

                this.set(s, 0, t, "polished_andesite");
                this.set(s, 0, -t, "polished_andesite");
            }
        }

        this.box(-6, 20, -58, 6, 20, -50, "gold_block");
        this.box(-5, 21, -57, 5, 21, -51, "red_carpet");

        for (int x = -6; x <= 6; x += 3) {
            for (int yx = 21; yx <= 26; yx++) {
                this.set(x, yx, -58, "red_wool");
            }
        }

        this.disc(0.0, 0.0, 0, 5.0, "chiseled_sandstone");
        this.set(0, 1, 0, "gold_block");
        this.npc(0.0, 2.0, 0.0);

        for (int a = 0; a < 360; a += 20) {
            int x = (int)Math.round(Math.cos(Math.toRadians(a)) * 41.0);
            int zx = (int)Math.round(Math.sin(Math.toRadians(a)) * 33.0);
            this.set(x, 4, zx, "campfire[lit=true]");
        }

        this.npc(0.0, 1.0, 72.0);
        this.npc(0.0, 1.0, -72.0);
        this.npc(80.0, 1.0, 0.0);
        this.npc(-80.0, 1.0, 0.0);
    }

    private void districts() {
        int pit = 0;

        for (int q = 0; q < 4; q++) {
            double base = q * 90 + 45;

            for (double r : new double[]{this.wallR * 0.5, this.wallR * 0.72}) {
                for (int side = -1; side <= 1; side += 2) {
                    double a = base + side * 15;
                    int x = px(r, a);
                    int z = pz(r, a);
                    this.practicePit(x, z, PITS[pit % PITS.length]);
                    pit++;
                }
            }

            for (double r = 104.0; r < this.wallR - 20; r += 13.0) {
                for (int side = -1; side <= 1; side += 2) {
                    double a = q * 90;
                    double rad = Math.toRadians(a);
                    int x = (int)Math.round(Math.cos(rad) * r - Math.sin(rad) * 13.0 * side);
                    int z = (int)Math.round(Math.sin(rad) * r + Math.cos(rad) * 13.0 * side);
                    String face = facing(Math.sin(rad) * side, -Math.cos(rad) * side);
                    if (this.empty(x, 1, z)) {
                        String roof = this.rnd.nextInt(3) == 0 ? "dark_oak" : (this.rnd.nextBoolean() ? "spruce" : "brick");
                        this.house(
                            x - 4,
                            z - 4,
                            9,
                            8,
                            5 + this.rnd.nextInt(2),
                            this.rnd.nextBoolean() ? "white_terracotta" : "stripped_birch_log[axis=y]",
                            "spruce_log",
                            roof,
                            face
                        );
                    }
                }
            }

            int gx = px(this.wallR * 0.86, base);
            int gz = pz(this.wallR * 0.86, base);
            this.grove(gx, gz, 18.0, 14, q % 2 == 0 ? "oak_log" : "birch_log", q % 2 == 0 ? "oak" : "birch");
            this.meadow(gx, gz, 20.0, 0.2);
            this.fountain(px(this.wallR * 0.62, base), pz(this.wallR * 0.62, base), 5, "stone_bricks", "chiseled_stone_bricks");
        }
    }

    private void practicePit(int ox, int oz, String[] style) {
        int h = 12;

        for (int x = -h - 2; x <= h + 2; x++) {
            for (int z = -h - 2; z <= h + 2; z++) {
                boolean edge = Math.abs(x) > h || Math.abs(z) > h;
                this.set(ox + x, 0, oz + z, edge ? "polished_andesite" : style[0]);
                this.set(ox + x, -1, oz + z, "stone");
                if ((Math.abs(x) == h + 1 || Math.abs(z) == h + 1) && Math.abs(x) > 1 && Math.abs(z) > 1) {
                    this.set(ox + x, 1, oz + z, style[1]);
                }
            }
        }

        for (int x = -h; x <= h; x++) {
            this.set(ox + x, 1, oz - h - 3, stairs("stone_brick", "south", false));
            this.set(ox + x, 2, oz - h - 4, stairs("stone_brick", "south", false));
            this.set(ox + x, 1, oz - h - 4, "stone_bricks");
        }

        for (int c = -1; c <= 1; c += 2) {
            for (int d = -1; d <= 1; d += 2) {
                this.lamp(ox + c * (h + 1), oz + d * (h + 1), 4, "stone_brick_wall", "lantern");
            }
        }
    }

    private void cityWall() {
        int towers = 12;

        for (int i = 0; i < towers; i++) {
            double a0 = i * 360.0 / towers;
            double a1 = (i + 1) * 360.0 / towers;
            this.castleWall(
                px(this.wallR, a0), pz(this.wallR, a0), px(this.wallR, a1), pz(this.wallR, a1), 12, 4, "stone_bricks", "stone_brick_wall", "lantern"
            );
        }

        for (int i = 0; i < towers; i++) {
            double a = i * 360.0 / towers;
            this.tower(px(this.wallR, a), pz(this.wallR, a), 6.0, 20, "stone_bricks", "chiseled_stone_bricks", "dark_oak_planks", "lantern");
        }

        int[][] gates = new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

        for (int[] g : gates) {
            int gx = g[0] * this.wallR;
            int gz = g[1] * this.wallR;
            boolean alongX = g[0] == 0;

            for (int s = -6; s <= 6; s++) {
                for (int d = -4; d <= 4; d++) {
                    int x = alongX ? gx + s : gx + d;
                    int z = alongX ? gz + d : gz + s;

                    for (int y = 1; y <= 13; y++) {
                        this.set(x, y, z, "air");
                    }

                    this.set(x, 0, z, "polished_andesite");
                }
            }

            this.arch(gx, gz, 14, 16, "stone_bricks", "chiseled_stone_bricks", !alongX);
            int ox = alongX ? 9 : 0;
            int oz = alongX ? 0 : 9;
            this.tower(gx + ox, gz + oz, 5.0, 22, "stone_bricks", "chiseled_stone_bricks", "red_nether_bricks", "lantern");
            this.tower(gx - ox, gz - oz, 5.0, 22, "stone_bricks", "chiseled_stone_bricks", "red_nether_bricks", "lantern");
        }
    }
}
