package net.vortexpvp.build;

public final class KeepDesign extends Voxels {
    private final int wallR;

    public KeepDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -30, 60, seed);
        this.wallR = (int)Math.round((radius - 12) * 0.78);
        this.ground();
        this.moat();
        this.curtain();
        this.gatehouse();
        this.keep();
        this.courtyard();
        this.spawn = new double[]{0.5, 1.0, this.wallR + 9.5, 180.0};
        this.finish();
    }

    @Override
    public String name() {
        return "keep";
    }

    private static double oct(int x, int z) {
        double ax = Math.abs(x);
        double az = Math.abs(z);
        return Math.max(Math.max(ax, az), (ax + az) / 1.414);
    }

    private void ground() {
        this.island(0.0, 0, 0.0, this.R - 4, "grass_block", "stone", 555L);
        String[] flowers = new String[]{"poppy", "dandelion", "oxeye_daisy", "cornflower"};

        for (int i = 0; i < 500; i++) {
            int x = this.rnd.nextInt(this.R * 2) - this.R;
            int z = this.rnd.nextInt(this.R * 2) - this.R;
            if ("grass_block".equals(this.get(x, 0, z)) && this.empty(x, 1, z) && oct(x, z) > this.wallR + 8) {
                this.set(x, 1, z, this.rnd.nextInt(3) == 0 ? flowers[this.rnd.nextInt(flowers.length)] : "short_grass");
            }
        }

        for (int ix = 0; ix < 26; ix++) {
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double r = this.wallR + 12 + this.rnd.nextDouble() * (this.R - this.wallR - 20);
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            if ("grass_block".equals(this.get(x, 0, z)) && Math.abs(x) > 5) {
                if (this.rnd.nextBoolean()) {
                    this.conifer(x, 1, z, "spruce_log", "spruce", 7 + this.rnd.nextInt(4));
                } else {
                    this.tree(x, 1, z, "oak_log", "oak", 5, 3.0);
                }
            }
        }
    }

    private void moat() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double o = oct(x, z);
                if (o >= this.wallR + 3 && o <= this.wallR + 7 && this.get(x, 0, z) != null) {
                    boolean bridge = Math.abs(x) <= 2 && z > 0;
                    if (bridge) {
                        this.set(x, 0, z, "spruce_planks");
                        this.set(x, -1, z, "spruce_log[axis=z]");
                        this.set(x, 1, z, Math.abs(x) == 2 ? "spruce_fence" : null);
                    } else {
                        this.set(x, 1, z, null);
                        this.set(x, 0, z, "water");
                        this.set(x, -1, z, "water");
                        this.set(x, -2, z, "gravel");
                    }
                } else if (o > this.wallR + 1 && o < this.wallR + 3 && "grass_block".equals(this.get(x, 0, z))) {
                    this.set(x, 0, z, "cobblestone");
                    this.set(x, 1, z, null);
                }
            }
        }

        for (int zx = this.wallR + 7; zx < this.R - 6; zx++) {
            for (int x = -2; x <= 2; x++) {
                if (this.get(x, 0, zx) != null && !this.get(x, 0, zx).equals("water")) {
                    this.set(x, 0, zx, Math.abs(x) == 2 ? "cobblestone" : "dirt_path");
                    this.set(x, 1, zx, null);
                }
            }
        }
    }

    private void curtain() {
        int h = 11;

        for (int x = -this.wallR - 1; x <= this.wallR + 1; x++) {
            for (int z = -this.wallR - 1; z <= this.wallR + 1; z++) {
                double o = oct(x, z);
                if (!(o < this.wallR - 2) && !(o > this.wallR + 0.99)) {
                    boolean outer = o > this.wallR;

                    for (int y = 1; y <= h; y++) {
                        this.set(x, y, z, y <= 2 ? "cobblestone" : ((x * 7 + y * 3 + z * 5) % 9 == 0 ? "andesite" : "stone_bricks"));
                    }

                    this.set(x, 0, z, "stone_bricks");
                    if (outer) {
                        if (Math.floorMod(x + z, 2) == 0) {
                            this.set(x, h + 1, z, "stone_bricks");
                        } else {
                            this.set(x, h + 1, z, "stone_brick_slab[type=bottom]");
                        }
                    } else if (o < this.wallR - 1) {
                        this.set(x, h, z, "polished_andesite");
                    }
                }
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int zx = this.wallR - 3; zx <= this.wallR + 1; zx++) {
                for (int y = 1; y <= 6; y++) {
                    if (y <= 5 || Math.abs(x) <= 1) {
                        this.set(x, y, zx, null);
                    }
                }

                this.set(x, 0, zx, "cobblestone");
            }
        }

        for (int i = 0; i < 8; i++) {
            double a = (Math.PI / 4) * i + (Math.PI / 8);
            double rr = this.wallR / Math.cos(Math.PI / 8);
            int x = (int)Math.round(Math.cos(a) * rr);
            int zx = (int)Math.round(Math.sin(a) * rr);
            this.tower(x, zx, 5.0, 17, i % 2 == 0 ? "red_wool" : "blue_wool");
        }
    }

    private void tower(int x, int z, double r, int h, String banner) {
        this.cylinder(x, z, 1, h, r, "stone_bricks", true);
        this.disc(x, z, 0, r, "stone_bricks");
        this.disc(x, z, h - 5, r - 1.0, "spruce_planks");

        for (int y = 4; y < h; y += 5) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                this.set(x + d[0] * (int)r, y, z + d[1] * (int)r, "glass_pane");
                this.set(x + d[0] * (int)r, y + 1, z + d[1] * (int)r, "glass_pane");
            }
        }

        this.ring(x, z, h + 1, r - 0.5, r + 1.0, "stone_brick_slab[type=bottom]");
        this.ring(x, z, h, r - 1.0, r + 1.0, "stone_bricks");
        this.cone(x, z, h + 1, h + 11, r + 0.6, "deepslate_tiles");
        this.set(x, h + 12, z, "spruce_fence");
        this.set(x, h + 13, z, "spruce_fence");
        this.set(x + 1, h + 13, z, banner);
        this.set(x + 2, h + 13, z, banner);
        this.set(x + 1, h + 12, z, banner);
    }

    private void gatehouse() {
        int z = this.wallR;

        for (int s = -1; s <= 1; s += 2) {
            this.tower(s * 5, z, 3.5, 15, "red_wool");
        }

        for (int x = -4; x <= 4; x++) {
            for (int y = 6; y <= 12; y++) {
                for (int dz = -2; dz <= 1; dz++) {
                    if (Math.abs(x) > 2 || y > 6) {
                        this.set(x, y, z + dz, "stone_bricks");
                    }
                }
            }
        }

        for (int x = -2; x <= 2; x++) {
            this.set(x, 6, z + 1, "iron_bars");
            this.set(x, 5, z + 1, "iron_bars");
        }

        this.set(0, 9, z + 2, "chiseled_stone_bricks");
        this.set(0, 10, z + 2, "lantern");
        this.npc(0.0, 1.0, z + 7);
    }

    private void keep() {
        int hx = 13;
        int hz = 10;
        int h = 20;
        this.box(-hx, 0, -hz, hx, 0, hz, "polished_andesite");

        for (int y = 1; y <= h; y++) {
            this.walls(-hx, y, -hz, hx, y, hz, y <= 2 ? "cobblestone" : (y % 6 == 0 ? "chiseled_stone_bricks" : "stone_bricks"));
        }

        this.box(-hx + 1, 8, -hz + 1, hx - 1, 8, hz - 1, "spruce_planks");
        this.box(-hx + 1, 15, -hz + 1, hx - 1, 15, hz - 1, "spruce_planks");

        for (int x = -hx + 3; x <= hx - 3; x += 4) {
            for (int y = 3; y <= 6; y++) {
                this.set(x, y, -hz, "glass_pane");
                this.set(x, y, hz, "glass_pane");
            }

            for (int y = 10; y <= 13; y++) {
                this.set(x, y, -hz, "glass_pane");
                this.set(x, y, hz, "glass_pane");
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= (Math.abs(x) <= 1 ? 5 : 4); y++) {
                this.set(x, y, hz, null);
            }
        }

        for (int x = -3; x <= 3; x++) {
            this.set(x, 0, hz + 1, "polished_andesite");
            this.set(x, 0, hz + 2, "stone_brick_stairs[facing=north,half=bottom]");
        }

        this.set(-3, 3, hz + 1, "lantern");
        this.set(3, 3, hz + 1, "lantern");

        for (int k = 0; k <= hz + 1; k++) {
            int y = h + 1 + k;

            for (int x = -hx - 1; x <= hx + 1; x++) {
                this.set(x, y, -hz - 1 + k, "dark_oak_stairs[facing=south,half=bottom]");
                this.set(x, y, hz + 1 - k, "dark_oak_stairs[facing=north,half=bottom]");

                for (int z = -hz + k; z <= hz - k; z++) {
                    if (this.empty(x, y, z)) {
                        this.set(x, y, z, Math.abs(x) == hx + 1 ? "stone_bricks" : null);
                    }
                }
            }

            if (k == hz + 1) {
                this.box(-hx - 1, y, 0, hx + 1, y, 0, "dark_oak_slab[type=bottom]");
            }
        }

        for (int k = 0; k <= hz; k++) {
            for (int zx = -hz + k; zx <= hz - k; zx++) {
                this.set(-hx, h + 1 + k, zx, "stone_bricks");
                this.set(hx, h + 1 + k, zx, "stone_bricks");
            }
        }

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = sx * hx;
                int zx = sz * hz;
                this.cylinder(x, zx, h - 3, h + 6, 2.2, "stone_bricks", false);
                this.cone(x, zx, h + 7, h + 13, 2.8, "deepslate_tiles");
            }
        }

        int top = h + hz + 3;

        for (int y = top; y <= top + 6; y++) {
            this.set(0, y, 0, "spruce_fence");
        }

        this.box(1, top + 4, 0, 4, top + 6, 0, "yellow_wool");
        this.set(2, top + 5, 0, "red_wool");
        this.npc(-8.0, 1.0, hz + 6);
        this.npc(8.0, 1.0, hz + 6);
    }

    private void courtyard() {
        for (int z = 12; z <= this.wallR - 3; z++) {
            for (int x = -2; x <= 2; x++) {
                this.set(x, 0, z, Math.abs(x) == 2 ? "cobblestone" : "stone_bricks");
            }
        }

        for (int x = -this.wallR; x <= this.wallR; x++) {
            for (int z = -this.wallR; z <= this.wallR; z++) {
                double o = oct(x, z);
                if (o >= 17.0 && o <= 19.5 && "grass_block".equals(this.get(x, 0, z))) {
                    this.set(x, 0, z, "cobblestone");
                }
            }
        }

        String[] wool = new String[]{"red_wool", "yellow_wool", "blue_wool", "lime_wool"};

        for (int i = 0; i < 4; i++) {
            int x = -this.wallR + 8;
            int zx = -12 + i * 7;
            this.stall(x, zx, wool[i]);
        }

        int wx = this.wallR - 12;
        this.ring(wx, 0.0, 1, 1.5, 2.6, "cobblestone");
        this.disc(wx, 0.0, 0, 1.6, "water");
        this.set(wx - 2, 2, 0, "spruce_fence");
        this.set(wx + 2, 2, 0, "spruce_fence");
        this.set(wx - 2, 3, 0, "spruce_fence");
        this.set(wx + 2, 3, 0, "spruce_fence");
        this.box(wx - 2, 4, 0, wx + 2, 4, 0, "spruce_slab[type=bottom]");
        int tx = 16;
        int tz = -22;

        for (int i = 0; i < 4; i++) {
            this.set(tx + i * 3, 1, tz, "hay_block[axis=y]");
            this.set(tx + i * 3, 2, tz, "carved_pumpkin[facing=south]");
        }

        String[] flowers = new String[]{"rose_bush[half=lower]", "peony[half=lower]", "lilac[half=lower]"};

        for (int i = 0; i < 40; i++) {
            int x = this.rnd.nextInt(this.wallR * 2) - this.wallR;
            int zx = this.rnd.nextInt(this.wallR * 2) - this.wallR;
            if (oct(x, zx) < this.wallR - 4
                && "grass_block".equals(this.get(x, 0, zx))
                && this.empty(x, 1, zx)
                && this.empty(x, 2, zx)
                && (Math.abs(x) > 15 || Math.abs(zx) > 12)) {
                String f = flowers[this.rnd.nextInt(flowers.length)];
                this.set(x, 1, zx, f);
                this.set(x, 2, zx, f.replace("lower", "upper"));
            }
        }

        for (int[] t : new int[][]{{-20, 18}, {20, 18}, {-24, -16}, {22, 4}}) {
            if (oct(t[0], t[1]) < this.wallR - 6) {
                this.tree(t[0], 1, t[1], "oak_log", "oak", 5, 2.8);
            }
        }
    }

    private void stall(int x, int z, String wool) {
        this.box(x - 2, 1, z - 1, x + 2, 1, z + 1, "spruce_planks");

        for (int sx = -2; sx <= 2; sx += 4) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = 2; y <= 4; y++) {
                    this.set(x + sx, y, z + sz, "spruce_fence");
                }
            }
        }

        this.box(x - 2, 5, z - 1, x + 2, 5, z + 1, wool);
        this.set(x - 1, 2, z, "barrel[facing=up]");
        this.set(x + 1, 2, z, "melon");
    }
}
