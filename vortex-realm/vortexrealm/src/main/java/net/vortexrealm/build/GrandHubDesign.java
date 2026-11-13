package net.vortexrealm.build;

public final class GrandHubDesign extends MegaVoxels {
    private static final String[][] GATES = new String[][]{
        {"orange_stained_glass_pane", "orange_concrete", "gold_block"},
        {"red_stained_glass_pane", "red_concrete", "redstone_block"},
        {"yellow_stained_glass_pane", "yellow_concrete", "gold_block"},
        {"lime_stained_glass_pane", "lime_concrete", "emerald_block"},
        {"light_blue_stained_glass_pane", "light_blue_concrete", "lapis_block"},
        {"white_stained_glass_pane", "white_concrete", "quartz_block"},
        {"magenta_stained_glass_pane", "magenta_concrete", "amethyst_block"},
        {"black_stained_glass_pane", "gray_concrete", "netherite_block"}
    };
    private final int ri;
    private final int gateR;

    public GrandHubDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -44, 96, seed);
        this.ri = (int)Math.round(radius * 0.84);
        this.gateR = (int)Math.round(this.ri * 0.8);
        this.islandBase(this.ri, "grass_block", 40, seed);
        this.plaza();
        this.canal();
        this.avenues();
        this.gardens();

        for (int i = 0; i < 8; i++) {
            this.gate(i * 45 + 22.5, GATES[i]);
        }

        this.vortex();
        this.edge();
        this.islets();
        this.spawn = new double[]{0.5, 1.0, 26.5, 180.0};
        this.finish();
    }

    @Override
    public String name() {
        return "grand_hub";
    }

    private boolean onAvenue(int x, int z, double half) {
        double r = dist(x, z);
        if (!(r < 30.0) && !(r > this.gateR + 2)) {
            for (int i = 0; i < 8; i++) {
                double a = i * 45 + 22.5;
                double rad = Math.toRadians(a);
                double perp = Math.abs(-Math.sin(rad) * x + Math.cos(rad) * z);
                if (perp <= half && angDiff(angle(x, z), a) < 90.0) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private void plaza() {
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                double r = dist(x, z);
                if (!(r > 30.0)) {
                    double a = Math.toRadians(angle(x, z));
                    double star = 12.0 + 8.0 * Math.pow(Math.abs(Math.cos(a * 4.0)), 3.0);
                    String b;
                    if (r > 28.6) {
                        b = "polished_blackstone_bricks";
                    } else if (r > 27.6) {
                        b = "gold_block";
                    } else if (r < star && r > 9.0) {
                        double s = Math.abs(Math.cos(a * 4.0));
                        b = s > 0.96 ? "orange_glazed_terracotta" : (s > 0.7 ? "red_nether_bricks" : "polished_blackstone");
                    } else if (r <= 9.0) {
                        b = "polished_blackstone_bricks";
                    } else {
                        b = (int)Math.floor(r) % 4 == 0 ? "chiseled_quartz_block" : "smooth_quartz";
                    }

                    this.set(x, 0, z, b);
                }
            }
        }

        for (int a = 0; a < 360; a += 30) {
            if (a % 45 != 0 && (a + 22) % 45 >= 10) {
                int x = px(25.0, a);
                int zx = pz(25.0, a);
                this.set(x, 1, zx, "oak_leaves[persistent=true]");
                this.set(x, 0, zx, "moss_block");
            }
        }
    }

    private void vortex() {
        for (int t = 0; t < 3; t++) {
            this.disc(0.0, 0.0, 1 + t, 8.5 - t * 1.5, t == 2 ? "gold_block" : "polished_blackstone_bricks");
        }

        int top = 50;
        this.cylinder(0.0, 0.0, 4, top, 2.2, "smooth_quartz", false);

        for (int y = 4; y <= top; y += 6) {
            this.disc(0.0, 0.0, y, 2.6, "chiseled_quartz_block");
        }

        String[] rib = new String[]{"red_concrete", "orange_concrete", "yellow_concrete"};

        for (int k = 0; k < 3; k++) {
            for (double y = 4.0; y <= top - 2; y += 0.08) {
                double f = (y - 4.0) / (top - 6);
                double r = 7.5 - f * 4.2;
                double a = k * 120 + y * 14.0;

                for (double w = 0.0; w <= 1.6; w += 0.4) {
                    this.set(px(r + w, a), (int)y, pz(r + w, a), rib[k]);
                    this.set(px(r + w, a), (int)y + 1, pz(r + w, a), rib[k]);
                }

                if ((int)(y * 12.5) % 70 == 0) {
                    this.set(px(r + 2.2, a), (int)y, pz(r + 2.2, a), "shroomlight");
                }
            }
        }

        for (int a = 0; a < 360; a += 2) {
            for (double t = -0.6; t <= 0.6; t += 0.6) {
                this.set(px(9.0 + t, a), top + 4 + (int)Math.round(Math.sin(Math.toRadians(a * 2)) * 1.5), pz(9.0 + t, a), "gold_block");
            }
        }

        this.sphere(0.0, top + 5, 0.0, 3.6, "orange_stained_glass");
        this.sphere(0.0, top + 5, 0.0, 2.2, "shroomlight");
        this.set(0, top + 1, 0, "smooth_quartz");
        this.set(0, top + 2, 0, "end_rod[facing=up]");
    }

    private void canal() {
        for (int x = -42; x <= 42; x++) {
            for (int z = -42; z <= 42; z++) {
                double r = dist(x, z);
                if (r >= 33.0 && r <= 39.0) {
                    this.set(x, 0, z, !(r < 34.0) && !(r > 38.0) ? "water" : "polished_blackstone_bricks");
                    this.set(x, -1, z, "dark_prismarine");
                    if (r < 34.0 || r > 38.0) {
                        this.set(x, 1, z, (int)(angle(x, z) + 360.0) % 6 == 0 ? "polished_blackstone_brick_wall" : "air");
                    }
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            double a = i * 45 + 22.5;

            for (double r = 32.0; r <= 40.0; r += 0.5) {
                for (double s = -3.5; s <= 3.5; s += 0.5) {
                    double rad = Math.toRadians(a);
                    int x = (int)Math.round(Math.cos(rad) * r - Math.sin(rad) * s);
                    int zx = (int)Math.round(Math.sin(rad) * r + Math.cos(rad) * s);
                    this.set(x, 0, zx, Math.abs(s) > 3.0 ? "polished_blackstone_bricks" : "smooth_quartz");
                    this.set(x, 1, zx, Math.abs(s) > 3.0 ? "polished_blackstone_brick_wall" : "air");
                }
            }
        }
    }

    private void avenues() {
        for (int i = 0; i < 8; i++) {
            double a = i * 45 + 22.5;
            this.road(
                px(40.0, a),
                pz(40.0, a),
                px(this.gateR - 6, a),
                pz(this.gateR - 6, a),
                7,
                "polished_andesite",
                "red_nether_bricks",
                12,
                "polished_blackstone_wall",
                "lantern"
            );
        }

        for (int x = -this.ri; x <= this.ri; x++) {
            for (int z = -this.ri; z <= this.ri; z++) {
                double r = dist(x, z);
                if (Math.abs(r - this.gateR * 0.62) < 2.5) {
                    this.paint(x, z, Math.abs(r - this.gateR * 0.62) > 1.8 ? "red_nether_bricks" : "polished_andesite");
                }
            }
        }
    }

    private void gardens() {
        for (int i = 0; i < 8; i++) {
            double a = i * 45;

            for (double r : new double[]{this.gateR * 0.45, this.gateR * 0.8}) {
                int x = px(r, a);
                int z = pz(r, a);
                this.grove(
                    x,
                    z,
                    r * 0.16,
                    (int)(r * 0.09) + 3,
                    i % 3 == 0 ? "cherry_log" : (i % 3 == 1 ? "oak_log" : "birch_log"),
                    i % 3 == 0 ? "cherry" : (i % 3 == 1 ? "oak" : "birch")
                );
                this.meadow(x, z, r * 0.2, 0.18);
            }

            int x = px(this.gateR * 0.62 + 10.0, a);
            int z = pz(this.gateR * 0.62 + 10.0, a);
            this.ring(x, z, 0, 3.5, 4.5, "smooth_quartz");
            this.disc(x, z, 0, 3.4, "water");
            this.disc(x, z, -1, 4.0, "smooth_quartz");
            this.set(x, 1, z, "lily_pad");
            this.bench(
                px(this.gateR * 0.62 + 5.0, a),
                pz(this.gateR * 0.62 + 5.0, a),
                facing(-Math.cos(Math.toRadians(a)), -Math.sin(Math.toRadians(a))),
                Math.abs(Math.sin(Math.toRadians(a))) > 0.7
            );
        }

        for (int x = -this.ri; x <= this.ri; x++) {
            for (int z = -this.ri; z <= this.ri; z++) {
                if (this.onAvenue(x, z, 7.0) && !this.onAvenue(x, z, 6.0) && "grass_block".equals(this.get(x, 0, z)) && this.empty(x, 1, z)) {
                    this.set(x, 1, z, "oak_leaves[persistent=true]");
                }
            }
        }
    }

    private void gate(double a, String[] c) {
        String pane = c[0];
        String color = c[1];
        String accent = c[2];
        int gx = px(this.gateR, a);
        int gz = pz(this.gateR, a);
        double rad = Math.toRadians(a);

        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                double d = dist(x, z);
                if (d <= 15.0) {
                    this.set(gx + x, 0, gz + z, d > 14.0 ? color : ((int)d % 4 == 0 ? "polished_blackstone" : "smooth_quartz"));
                }
            }
        }

        for (int k = -60; k <= 60; k += 12) {
            double ca = a + k;
            int x = gx + (int)Math.round(Math.cos(Math.toRadians(ca)) * 13.0);
            int zx = gz + (int)Math.round(Math.sin(Math.toRadians(ca)) * 13.0);

            for (int y = 1; y <= 9; y++) {
                this.set(x, y, zx, y != 1 && y != 9 ? "quartz_pillar[axis=y]" : "chiseled_quartz_block");
            }

            this.set(x, 10, zx, "lantern");
        }

        double tx = -Math.sin(rad);
        double tz = Math.cos(rad);
        int w = 6;

        for (int s = -w; s <= w; s++) {
            int x = gx + (int)Math.round(Math.cos(rad) * 4.0 + tx * s);
            int zx = gz + (int)Math.round(Math.sin(rad) * 4.0 + tz * s);
            double t = (double)s / w;
            int archTop = 14 + (int)Math.round(Math.sqrt(Math.max(0.0, 1.0 - t * t)) * 4.0);
            if (Math.abs(s) >= w - 1) {
                for (int y = 1; y <= 19; y++) {
                    this.set(x, y, zx, y % 6 == 0 ? accent : "polished_blackstone_bricks");
                }

                this.set(x, 20, zx, "gold_block");
                this.set(x, 21, zx, "end_rod[facing=up]");
            } else {
                for (int y = 1; y < archTop; y++) {
                    this.set(x, y, zx, pane);
                }

                this.set(x, archTop, zx, color);
                this.set(x, archTop + 1, zx, "polished_blackstone_bricks");
            }
        }

        this.set(gx + (int)Math.round(Math.cos(rad) * 4.0), 20, gz + (int)Math.round(Math.sin(rad) * 4.0), accent);
        double nx = gx - Math.cos(rad) * 3.0;
        double nz = gz - Math.sin(rad) * 3.0;
        this.set((int)Math.round(nx), 0, (int)Math.round(nz), accent);
        this.npc(Math.round(nx), 1.0, Math.round(nz));
    }

    private void edge() {
        int r = this.ri - 3;

        for (int a10 = 0; a10 < 3600; a10 += 4) {
            double a = a10 / 10.0;
            int x = px(r, a);
            int z = pz(r, a);
            boolean gap = false;

            for (int i = 0; i < 8; i++) {
                gap |= angDiff(a, i * 45 + 22.5) < 6.0;
            }

            if (!gap && !this.empty(x, 0, z)) {
                this.set(x, 1, z, "quartz_slab[type=bottom]");
                if (a10 % 40 == 0) {
                    this.set(x, 1, z, "quartz_pillar[axis=y]");
                    this.set(x, 2, z, "lantern");
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            double a = i * 45;
            this.tower(px(r - 2, a), pz(r - 2, a), 4.5, 18, "polished_blackstone_bricks", "red_nether_bricks", "red_nether_bricks", "shroomlight");
        }
    }

    private void islets() {
        for (int i = 0; i < 10; i++) {
            double a = i * 36 + this.rnd.nextInt(15);
            double d = this.ri + 12 + this.rnd.nextInt(Math.max(4, this.R - this.ri - 18));
            if (d > this.R - 8) {
                d = this.R - 8;
            }

            int x = px(d, a);
            int z = pz(d, a);
            int y = -6 + this.rnd.nextInt(26);
            this.island(x, y, z, 5 + this.rnd.nextInt(4), "grass_block", "stone", 77 + i);
            if (this.rnd.nextBoolean()) {
                this.tree(x, y + 1, z, "oak_log", "oak", 4, 2.8);
            } else {
                this.set(x, y + 1, z, "lantern");
            }

            this.cloud(x + 10, y + 8, z - 6, 4.0);
        }
    }
}
