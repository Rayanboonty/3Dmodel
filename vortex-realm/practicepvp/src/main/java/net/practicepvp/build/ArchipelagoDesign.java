package net.practicepvp.build;

public final class ArchipelagoDesign extends Voxels {
    private static final int MAIN = 24;

    public ArchipelagoDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -36, 48, seed);
        this.mainIsland();
        this.temple();
        double ring = Math.min(radius - 16, 54);

        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60 + 30);
            double d = ring + (i % 2 == 0 ? -4 : 3);
            int x = (int)Math.round(Math.cos(a) * d);
            int z = (int)Math.round(Math.sin(a) * d);
            int y = new int[]{-4, 6, -1, 9, 2, -6}[i];
            int r = 10 + i % 3;
            this.island(x, y, z, r, "grass_block", "stone", 300L + i);
            this.decorateTop(x, y, z, r, i);
            switch (i) {
                case 0:
                    this.windmill(x, y + 1, z);
                    break;
                case 1:
                    this.lighthouse(x, y + 1, z);
                    break;
                case 2:
                    this.giantTree(x, y + 1, z);
                    break;
                case 3:
                    this.ruins(x, y + 1, z);
                    break;
                case 4:
                    this.crystals(x, y + 1, z);
                    break;
                default:
                    this.camp(x, y + 1, z);
            }

            this.bridge(a, y, Math.hypot(x, z) - r + 1.0);
        }

        this.clouds();
        this.spawn = new double[]{0.5, 1.0, 17.5, 180.0};
        this.finish();
    }

    @Override
    public String name() {
        return "archipelago";
    }

    private void mainIsland() {
        this.island(0.0, 0, 0.0, 24.0, "grass_block", "stone", 99L);
        String[] flowers = new String[]{"poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "pink_tulip"};

        for (int i = 0; i < 160; i++) {
            int x = this.rnd.nextInt(48) - 24;
            int z = this.rnd.nextInt(48) - 24;
            double d = Math.hypot(x, z);
            if (!(d < 13.0) && !(d > 22.0) && "grass_block".equals(this.get(x, 0, z))) {
                this.set(x, 1, z, this.rnd.nextInt(4) == 0 ? "short_grass" : flowers[this.rnd.nextInt(flowers.length)]);
            }
        }

        this.ring(0.0, 0.0, 0, 12.5, 14.5, "dirt_path");

        for (int ix = 0; ix < 6; ix++) {
            double a = Math.toRadians(ix * 60 + 30);

            for (double r = 14.0; r <= 24.0; r += 0.5) {
                for (int w = -1; w <= 1; w++) {
                    int x = (int)Math.round(Math.cos(a) * r - Math.sin(a) * w);
                    int z = (int)Math.round(Math.sin(a) * r + Math.cos(a) * w);
                    if ("grass_block".equals(this.get(x, 0, z)) || this.get(x, 0, z) != null && this.get(x, 0, z).equals("dirt")) {
                        this.set(x, 0, z, "dirt_path");
                        this.set(x, 1, z, null);
                    }
                }
            }

            this.npc(Math.cos(a) * 16.0, 1.0, Math.sin(a) * 16.0);
        }

        for (int[] t : new int[][]{{-18, -6}, {17, 8}, {-8, 19}, {9, -19}, {20, -10}, {-16, 13}}) {
            if (Math.hypot(t[0], t[1]) < 21.0) {
                this.tree(t[0], 1, t[1], "cherry_log", "cherry", 5, 3.4);
            }
        }
    }

    private void temple() {
        this.disc(0.0, 0.0, 0, 11.5, "smooth_quartz");
        this.ring(0.0, 0.0, 0, 10.5, 11.5, "gold_block");
        this.ring(0.0, 0.0, 0, 4.5, 5.2, "chiseled_quartz_block");
        this.disc(0.0, 0.0, 1, 2.5, "quartz_bricks");
        this.set(0, 2, 0, "chiseled_quartz_block");
        this.sphere(0.0, 6.0, 0.0, 1.6, "amethyst_block");
        this.set(0, 6, 0, "sea_lantern");

        for (int i = 0; i < 12; i++) {
            double a = (Math.PI * 2) * i / 12.0;
            int x = (int)Math.round(Math.cos(a) * 10.0);
            int z = (int)Math.round(Math.sin(a) * 10.0);
            this.set(x, 1, z, "chiseled_quartz_block");

            for (int y = 2; y <= 8; y++) {
                this.set(x, y, z, "quartz_pillar[axis=y]");
            }

            this.set(x, 9, z, "chiseled_quartz_block");
        }

        this.ring(0.0, 0.0, 10, 9.5, 11.5, "quartz_block");
        this.ring(0.0, 0.0, 11, 10.5, 11.8, "smooth_quartz_slab[type=bottom]");

        for (int y = 11; y <= 19; y++) {
            double r = Math.sqrt(Math.max(0.0, 110.25 - Math.pow((y - 10) * 1.15, 2.0)));
            this.ring(0.0, 0.0, y, Math.max(0.0, r - 1.0), r, y % 3 == 0 ? "light_blue_stained_glass" : "white_stained_glass");
        }

        this.set(0, 20, 0, "gold_block");
        this.set(0, 21, 0, "end_rod");
        this.npc(0.0, 1.0, 7.0);
    }

    private void decorateTop(int ox, int oy, int oz, int r, int i) {
        for (int k = 0; k < r * 3; k++) {
            int x = ox + this.rnd.nextInt(r * 2) - r;
            int z = oz + this.rnd.nextInt(r * 2) - r;
            if ("grass_block".equals(this.get(x, oy, z)) && this.empty(x, oy + 1, z) && Math.hypot(x - ox, z - oz) > 4.0) {
                this.set(x, oy + 1, z, this.rnd.nextBoolean() ? "short_grass" : (i % 2 == 0 ? "dandelion" : "azure_bluet"));
            }
        }
    }

    private void windmill(int x, int y, int z) {
        this.cylinder(x, z, y, y + 12, 3.2, "stone_bricks", true);
        this.disc(x, z, y + 12, 3.2, "spruce_planks");
        this.cone(x, z, y + 13, y + 18, 3.8, "spruce_planks");
        this.set(x, y + 1, z + 3, null);
        this.set(x, y + 2, z + 3, null);
        this.set(x, y + 6, z + 3, "glass_pane");
        this.set(x, y + 9, z - 3, "glass_pane");
        int hy = y + 10;
        int hz = z + 4;
        this.set(x, hy, hz, "spruce_log[axis=z]");

        for (int s = 1; s <= 7; s++) {
            this.set(x + s, hy, hz, "spruce_planks");
            this.set(x - s, hy, hz, "spruce_planks");
            this.set(x, hy + s, hz, "spruce_planks");
            this.set(x, hy - s, hz, "spruce_planks");
            if (s >= 2) {
                this.set(x + s, hy + 1, hz, "white_wool");
                this.set(x - s, hy - 1, hz, "white_wool");
                this.set(x - 1, hy + s, hz, "white_wool");
                this.set(x + 1, hy - s, hz, "white_wool");
            }
        }
    }

    private void lighthouse(int x, int y, int z) {
        for (int k = 0; k < 18; k++) {
            this.ring(x, z, y + k, 2.2, 3.3, k / 3 % 2 == 0 ? "red_concrete" : "white_concrete");
        }

        this.disc(x, z, y + 18, 3.6, "stone_bricks");
        this.ring(x, z, y + 19, 3.0, 3.8, "iron_bars");

        for (int k = 19; k <= 21; k++) {
            this.ring(x, z, k, 1.5, 2.3, "glass");
        }

        this.disc(x, z, y + 19, 1.2, "sea_lantern");
        this.disc(x, z, y + 20, 1.2, "sea_lantern");
        this.cone(x, z, y + 22, y + 25, 2.6, "red_concrete");
        this.set(x, y + 26, z, "lightning_rod");
    }

    private void giantTree(int x, int y, int z) {
        for (int k = 0; k < 15; k++) {
            this.box(x, y + k, z, x + 1, y + k, z + 1, "oak_log[axis=y]");
        }

        for (int[] b : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            this.line(x + 0.5, y + 9, z + 0.5, x + 0.5 + b[0] * 5, y + 13, z + 0.5 + b[1] * 5, "oak_log[axis=y]");
        }

        this.ellipsoid(x + 0.5, y + 16, z + 0.5, 7.5, 5.0, 7.5, leaves("oak"));
        this.ellipsoid(x + 4, y + 13.5, z + 2, 4.0, 3.0, 4.0, leaves("oak"));
        this.ellipsoid(x - 3, y + 14, z - 3, 4.0, 3.0, 4.0, leaves("oak"));

        for (int[] b : new int[][]{{2, 0}, {-1, 1}, {1, -1}, {0, 2}}) {
            this.set(x + b[0], y, z + b[1], "rooted_dirt");
        }

        this.set(x - 3, y, z + 2, "oak_stairs[facing=east,half=bottom]");
    }

    private void ruins(int x, int y, int z) {
        this.disc(x, z, y - 1, 7.0, "cracked_stone_bricks");

        for (int i = 0; i < 8; i++) {
            double a = (Math.PI * 2) * i / 8.0;
            int px = x + (int)Math.round(Math.cos(a) * 6.0);
            int pz = z + (int)Math.round(Math.sin(a) * 6.0);
            int h = 2 + this.rnd.nextInt(6);

            for (int k = 0; k < h; k++) {
                this.set(px, y + k, pz, k == h - 1 && this.rnd.nextBoolean() ? "cracked_stone_bricks" : "stone_bricks");
            }

            if (this.rnd.nextBoolean()) {
                this.set(px, y + h, pz, leaves("azalea"));
            }
        }

        for (int s = -2; s <= 2; s++) {
            for (int k = 0; k < 6; k++) {
                if (Math.abs(s) == 2 || k == 5) {
                    this.set(x + s, y + k, z, "mossy_stone_bricks");
                }
            }
        }

        this.set(x + 1, y + 5, z, null);
        this.set(x, y, z + 2, "chiseled_stone_bricks");
        this.set(x, y + 1, z + 2, "lantern");
    }

    private void crystals(int x, int y, int z) {
        this.disc(x, z, y - 1, 5.0, "amethyst_block");

        for (int i = 0; i < 9; i++) {
            int px = x + this.rnd.nextInt(9) - 4;
            int pz = z + this.rnd.nextInt(9) - 4;
            int h = 2 + this.rnd.nextInt(7);
            double lean = (this.rnd.nextDouble() - 0.5) * 3.0;
            this.line(px, y, pz, px + lean, y + h, pz + lean * 0.5, i % 3 == 0 ? "purple_stained_glass" : "amethyst_block");
            this.set((int)Math.round(px + lean), y + h + 1, (int)Math.round(pz + lean * 0.5), "amethyst_cluster[facing=up]");
        }

        this.set(x, y, z, "budding_amethyst");
    }

    private void camp(int x, int y, int z) {
        this.set(x, y, z, "campfire");

        for (int i = 0; i < 4; i++) {
            double a = (Math.PI / 2) * i + 0.4;
            this.set(
                x + (int)Math.round(Math.cos(a) * 2.5), y, z + (int)Math.round(Math.sin(a) * 2.5), "stripped_oak_log[axis=" + (i % 2 == 0 ? "x" : "z") + "]"
            );
        }

        for (int t = -1; t <= 1; t += 2) {
            int tx = x + t * 6;
            String wool = t < 0 ? "red_wool" : "blue_wool";

            for (int dz = -2; dz <= 2; dz++) {
                for (int k = 0; k < 3; k++) {
                    this.set(tx - 2 + k, y + k, z + dz, wool);
                    this.set(tx + 2 - k, y + k, z + dz, wool);
                }

                this.set(tx, y + 3, z + dz, "white_wool");
            }

            this.set(tx, y, z - 2, null);
            this.set(tx, y + 1, z - 2, null);
        }

        this.set(x + 3, y, z + 4, "barrel[facing=up]");
        this.set(x - 3, y, z + 4, "crafting_table");
    }

    private void bridge(double a, int y1, double far) {
        double near = 23.0;
        int n = (int)Math.ceil((far - near) * 2.0);
        double ux = Math.cos(a);
        double uz = Math.sin(a);
        double vx = -uz;
        double vz = ux;

        for (int i = 0; i <= n; i++) {
            double t = (double)i / n;
            double r = near + (far - near) * t;
            double y = (1.0 - t) * 0.0 + t * y1 - Math.sin(Math.PI * t) * 3.2;
            int yy = (int)Math.round(y);

            for (int w = -1; w <= 1; w++) {
                int x = (int)Math.round(ux * r + vx * w);
                int z = (int)Math.round(uz * r + vz * w);
                if (this.empty(x, yy, z) || this.get(x, yy, z).contains("grass")) {
                    this.set(x, yy, z, "spruce_planks");
                }

                this.set(x, yy + 1, z, null);
                this.set(x, yy + 2, z, null);
            }

            for (int s = -2; s <= 2; s += 4) {
                int x = (int)Math.round(ux * r + vx * s);
                int z = (int)Math.round(uz * r + vz * s);
                if (this.empty(x, yy + 1, z)) {
                    this.set(x, yy + 1, z, i % 8 == 0 ? "spruce_log[axis=y]" : "spruce_fence");
                }

                if (i % 8 == 0) {
                    this.set(x, yy + 2, z, "lantern");
                }
            }
        }
    }

    private void clouds() {
        this.soft = true;

        for (int i = 0; i < 26; i++) {
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double d = 32.0 + this.rnd.nextDouble() * (this.R - 36);
            int x = (int)Math.round(Math.cos(a) * d);
            int z = (int)Math.round(Math.sin(a) * d);
            int y = this.rnd.nextBoolean() ? 22 + this.rnd.nextInt(20) : -30 + this.rnd.nextInt(10);
            double s = 2.2 + this.rnd.nextDouble() * 2.6;
            this.ellipsoid(x, y, z, s * 1.8, s * 0.55, s, "white_wool");
            this.ellipsoid(x + s, y + 1, z + 1, s * 1.1, s * 0.6, s * 0.8, "white_wool");
        }

        this.soft = false;
    }
}
