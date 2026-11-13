package net.vortexrealm.build;

public final class JungleTempleDesign extends Voxels {
    private static final int PYR = 15;

    public JungleTempleDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -34, 52, seed);
        this.ground();
        this.paths();
        this.pyramid();
        this.ruins();
        this.pools();
        this.jungle();
        this.spawn = new double[]{0.5, 1.0, 29.5, 180.0};
        this.finish();
    }

    @Override
    public String name() {
        return "jungle";
    }

    private void ground() {
        this.island(0.0, 0, 0.0, this.R - 5, "grass_block", "stone", 777L);

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                if ("grass_block".equals(this.get(x, 0, z))) {
                    double n = noise(41L, x, z);
                    if (n > 0.68) {
                        this.set(x, 0, z, "moss_block");
                    } else if (n < 0.2) {
                        this.set(x, 0, z, "coarse_dirt");
                    }
                }
            }
        }
    }

    private boolean onPath(int x, int z) {
        boolean cross = (Math.abs(x) <= 2 || Math.abs(z) <= 2) && Math.max(Math.abs(x), Math.abs(z)) > 17;
        return cross && Math.hypot(x, z) < this.R - 10;
    }

    private void paths() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                if (this.onPath(x, z) && this.get(x, 0, z) != null) {
                    double n = noise(5L, x * 3, z * 3);
                    this.set(x, 0, z, n > 0.6 ? "mossy_stone_bricks" : (n < 0.25 ? "cracked_stone_bricks" : "stone_bricks"));
                }
            }
        }

        for (int r = 21; r < this.R - 12; r += 7) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int s = -1; s <= 1; s += 2) {
                    int x = d[0] * r + d[1] * 4 * s;
                    int zx = d[1] * r + d[0] * 4 * s;
                    if (this.get(x, 0, zx) != null) {
                        this.set(x, 1, zx, "mossy_cobblestone_wall");
                        this.set(x, 2, zx, "mossy_cobblestone_wall");
                        this.set(x, 3, zx, "torch");
                    }
                }
            }
        }
    }

    private void pyramid() {
        int tiers = 7;

        for (int t = 0; t < tiers; t++) {
            int half = 15 - t * 2;
            int y0 = 1 + t * 3;

            for (int y = y0; y < y0 + 3; y++) {
                for (int x = -half; x <= half; x++) {
                    for (int z = -half; z <= half; z++) {
                        double n = noise(9 + t, x * 2.0 + y, z * 2.0 - y);
                        String b = n > 0.62
                            ? "mossy_stone_bricks"
                            : (n < 0.2 ? "cracked_stone_bricks" : ((x + z + y) % 11 == 0 ? "chiseled_stone_bricks" : "stone_bricks"));
                        this.set(x, y, z, b);
                    }
                }
            }

            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    boolean ledge = Math.abs(x) >= half - 1 || Math.abs(z) >= half - 1;
                    if (ledge && this.rnd.nextDouble() < 0.18) {
                        this.set(x, y0 + 3, z, "moss_carpet");
                    }
                }
            }
        }

        int topY = 1 + tiers * 3;

        for (int k = 0; k < tiers * 3; k++) {
            int y = 1 + k;
            int reach = 16 - (int)Math.floor(k * 2.0 / 3.0);

            for (int w = -2; w <= 2; w++) {
                this.set(w, y, reach, "stone_brick_stairs[facing=north,half=bottom]");
                this.set(w, y, -reach, "stone_brick_stairs[facing=south,half=bottom]");
                this.set(reach, y, w, "stone_brick_stairs[facing=west,half=bottom]");
                this.set(-reach, y, w, "stone_brick_stairs[facing=east,half=bottom]");

                for (int c = 1; c <= 3; c++) {
                    this.set(w, y + c, reach, null);
                    this.set(w, y + c, -reach, null);
                    this.set(reach, y + c, w, null);
                    this.set(-reach, y + c, w, null);
                }
            }
        }

        int half = 15 - tiers * 2 + 1;
        this.box(-half, topY, -half, half, topY, half, "chiseled_stone_bricks");

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = topY + 1; y <= topY + 5; y++) {
                    this.set(sx * half, y, sz * half, "mossy_stone_bricks");
                }

                this.set(sx * half, topY + 6, sz * half, "campfire");
            }
        }

        this.box(-half, topY + 6, -half, half, topY + 6, half, "stone_brick_slab[type=bottom]");

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                this.set(sx * half, topY + 6, sz * half, "chiseled_stone_bricks");
                this.set(sx * half, topY + 7, sz * half, "campfire");
            }
        }

        this.set(0, topY + 1, 0, "gold_block");
        this.set(0, topY + 2, 0, "gold_block");
        this.set(0, topY + 3, 0, "carved_pumpkin[facing=south]");
        this.set(0, topY + 4, 0, "gold_block");
        this.set(-1, topY + 2, 0, "gold_block");
        this.set(1, topY + 2, 0, "gold_block");
        this.npc(0.0, topY + 1, 2.0);
        this.npc(0.0, 1.0, 21.0);
        this.npc(21.0, 1.0, 0.0);
        this.npc(-21.0, 1.0, 0.0);
        this.npc(0.0, 1.0, -21.0);
    }

    private void ruins() {
        for (int i = 0; i < 14; i++) {
            double a = (Math.PI * 2) * i / 14.0 + 0.2;
            double r = 24 + i % 3 * 3;
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            if (!this.onPath(x, z) && !this.onPath(x + 1, z) && !this.onPath(x, z + 1)) {
                int h = 2 + this.rnd.nextInt(6);
                this.set(x, 1, z, "chiseled_stone_bricks");

                for (int y = 2; y <= h; y++) {
                    this.set(x, y, z, this.rnd.nextDouble() < 0.3 ? "mossy_stone_bricks" : "stone_bricks");
                }

                if (h > 5) {
                    this.set(x, h + 1, z, leaves("jungle"));
                }
            }
        }

        for (int s = -1; s <= 1; s += 2) {
            int ax = s * 33;

            for (int w = -4; w <= 4; w++) {
                for (int y = 1; y <= 7; y++) {
                    boolean leg = Math.abs(w) >= 3;
                    boolean top = y >= 6;
                    if ((leg || top) && (s <= 0 || w <= 1 || y != 7)) {
                        this.set(ax, y, w, (w + y) % 3 == 0 ? "mossy_stone_bricks" : "stone_bricks");
                    }
                }
            }

            this.set(ax, 8, 0, "moss_block");
            this.set(ax, 9, 0, leaves("jungle"));
        }
    }

    private void pools() {
        int[][] at = new int[][]{{-30, -26}, {28, 30}, {-26, 32}};

        for (int[] p : at) {
            if (!(Math.hypot(p[0], p[1]) > this.R - 14)) {
                for (int x = -6; x <= 6; x++) {
                    for (int z = -5; z <= 5; z++) {
                        double d = Math.pow(x / 6.0, 2.0) + Math.pow(z / 5.0, 2.0);
                        int wx = p[0] + x;
                        int wz = p[1] + z;
                        if (!this.onPath(wx, wz)) {
                            if (d <= 0.75) {
                                this.set(wx, 0, wz, "water");
                                this.set(wx, -1, wz, "water");
                                this.set(wx, -2, wz, "clay");
                                this.set(wx, 1, wz, this.rnd.nextDouble() < 0.12 ? "lily_pad" : null);
                            } else if (d <= 1.05) {
                                this.set(wx, 0, wz, this.rnd.nextBoolean() ? "mossy_cobblestone" : "moss_block");
                                this.set(wx, 1, wz, this.rnd.nextDouble() < 0.3 ? "fern" : null);
                            }
                        }
                    }
                }
            }
        }
    }

    private void jungle() {
        for (int i = 0; i < 130; i++) {
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double r = 29.0 + this.rnd.nextDouble() * (this.R - 15 - 26);
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            if (this.clear(x, z, 2)) {
                if (this.rnd.nextInt(3) == 0) {
                    this.bigJungleTree(x, z, 14 + this.rnd.nextInt(10));
                } else {
                    this.tree(x, 1, z, "jungle_log", "jungle", 5 + this.rnd.nextInt(3), 3.0);
                }
            }
        }

        for (int ix = 0; ix < 900; ix++) {
            int x = this.rnd.nextInt(this.R * 2) - this.R;
            int z = this.rnd.nextInt(this.R * 2) - this.R;
            String g = this.get(x, 0, z);
            if (g != null && (g.equals("grass_block") || g.equals("moss_block")) && this.empty(x, 1, z) && Math.max(Math.abs(x), Math.abs(z)) > 17) {
                double roll = this.rnd.nextDouble();
                if (roll < 0.3) {
                    this.set(x, 1, z, leaves("jungle"));
                } else if (roll < 0.55) {
                    this.set(x, 1, z, "fern");
                } else if (roll < 0.62 && this.empty(x, 2, z)) {
                    this.set(x, 1, z, "large_fern[half=lower]");
                    this.set(x, 2, z, "large_fern[half=upper]");
                } else {
                    this.set(x, 1, z, "short_grass");
                }
            }
        }
    }

    private boolean clear(int x, int z, int r) {
        for (int dx = -r; dx <= r + 1; dx++) {
            for (int dz = -r; dz <= r + 1; dz++) {
                String g = this.get(x + dx, 0, z + dz);
                if (g == null || this.onPath(x + dx, z + dz) || g.equals("water") || !this.empty(x + dx, 1, z + dz)) {
                    return false;
                }
            }
        }

        return true;
    }

    private void bigJungleTree(int x, int z, int h) {
        for (int y = 1; y <= h; y++) {
            this.box(x, y, z, x + 1, y, z + 1, "jungle_log[axis=y]");
        }

        this.ellipsoid(x + 0.5, h + 1, z + 0.5, 5.5, 3.0, 5.5, leaves("jungle"));
        this.ellipsoid(x + 0.5, h - 4, z + 3.5, 3.0, 2.0, 3.0, leaves("jungle"));
        this.ellipsoid(x - 2.5, h - 6, z + 0.5, 2.5, 1.8, 2.5, leaves("jungle"));
        this.set(x - 1, 1, z, "jungle_log[axis=x]");
        this.set(x + 2, 1, z + 1, "jungle_log[axis=x]");
        this.set(x, 1, z + 2, "jungle_log[axis=z]");
        this.set(x + 1, 1, z - 1, "jungle_log[axis=z]");

        for (int i = 0; i < 8; i++) {
            int vx = x + this.rnd.nextInt(9) - 4;
            int vz = z + this.rnd.nextInt(9) - 4;

            for (int y = h - 2; y > h - 2 - this.rnd.nextInt(4); y--) {
                if (this.empty(vx, y, vz) && this.leafAbove(vx, y, vz)) {
                    this.set(vx, y, vz, leaves("jungle"));
                }
            }
        }
    }

    private boolean leafAbove(int x, int y, int z) {
        for (int k = 1; k <= 6; k++) {
            String b = this.get(x, y + k, z);
            if (b != null && (b.contains("leaves") || b.startsWith("vine"))) {
                return true;
            }
        }

        return false;
    }
}
