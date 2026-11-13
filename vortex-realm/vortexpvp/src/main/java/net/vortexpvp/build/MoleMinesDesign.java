package net.vortexpvp.build;

/**
 * "Mole Mines": the Mole PvP map, 401 x 401 blocks and about 80 deep - the biggest map on the server. Everything is
 * underground: real rock layers with ore veins and pockets, three levels of old mine shafts (oak frames, rails,
 * lanterns, ladders between levels), two crystal caverns lit by amethyst, glowstone and froglights, an underground
 * lake, lava falls behind glass, and sealed spawn chambers (both ends for duels, a ring of twelve for FFA). Players
 * dig their way to each other. On top: a mining camp with a big mine entrance, cranes, rails, tents and a viewing
 * deck. A barrier ceiling keeps the fight underground, bedrock is under every column and barriers close the sides.
 */
public final class MoleMinesDesign extends MegaVoxels implements ArenaVoxels {
    public static final int RADIUS = 200;
    /** Depth of the rock below the surface. */
    public static final int DEPTH = 82;
    private static final int[] LEVELS = new int[]{-20, -38, -56};
    private final long seed;

    public MoleMinesDesign(int cx, int floorY, int cz, long seed) {
        super(cx, floorY, cz, RADIUS, -DEPTH, 34, seed);
        this.seed = seed;
        this.rock();
        this.shafts();
        this.caverns();
        this.lake();
        this.chambers();
        this.surface();
        this.camp();
        this.shell();
        this.finish();
    }

    @Override
    public String name() {
        return "Mole Mines";
    }

    // ------------------------------------------------------------------ rock

    private String rockAt(int x, int y, int z, int top) {
        int d = top - y;
        if (y == -DEPTH) {
            return "bedrock";
        } else if (d <= 3) {
            return "dirt";
        } else {
            double n = noise(this.seed + 3L, x * 0.9 + y * 0.7, z * 0.9 - y * 0.5);
            double v = noise(this.seed + 11L, x * 2.2 + y, z * 2.2 - y);
            String base = y > -32 ? "stone" : (y > -54 ? (n > 0.62 ? "tuff" : "stone") : "deepslate");
            if (y > -54 && y <= -32 && n < 0.22) {
                base = "andesite";
            }

            // pockets of granite, diorite, gravel and dirt like a vanilla underground
            if (v > 0.86) {
                base = y > -54 ? "granite" : "tuff";
            } else if (v < 0.1) {
                base = y > -40 ? "gravel" : "deepslate";
            } else if (v > 0.78 && y > -30) {
                base = "diorite";
            }

            // ore veins
            double o = noise(this.seed + 29L, x * 3.1 - y * 1.3, z * 3.1 + y * 1.1);
            if (o > 0.9) {
                boolean deep = y <= -54;
                if (y <= -66 && o > 0.95) {
                    return "deepslate_diamond_ore";
                } else if (y <= -45 && o > 0.94) {
                    return deep ? "deepslate_redstone_ore" : "redstone_ore";
                } else if (y <= -30 && o > 0.93) {
                    return deep ? "deepslate_gold_ore" : "gold_ore";
                } else if (o > 0.92) {
                    return deep ? "deepslate_iron_ore" : "iron_ore";
                } else if (y > -40) {
                    return o > 0.91 ? "copper_ore" : "coal_ore";
                } else {
                    return deep ? "deepslate_lapis_ore" : "lapis_ore";
                }
            }

            return base;
        }
    }

    private int surfaceAt(int x, int z) {
        return (int)Math.round((noise(this.seed + 5L, x * 0.35, z * 0.35) - 0.5) * 6.0);
    }

    private void rock() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int top = this.surfaceAt(x, z);

                for (int y = -DEPTH; y <= top; y++) {
                    this.set(x, y, z, y == top ? "grass_block" : this.rockAt(x, y, z, top));
                }
            }
        }
    }

    // ------------------------------------------------------------------ mine shafts

    private void corridor(int x0, int z0, int x1, int z1, int y) {
        boolean alongX = z0 == z1;
        int len = alongX ? Math.abs(x1 - x0) : Math.abs(z1 - z0);
        int sx = Integer.signum(x1 - x0);
        int sz = Integer.signum(z1 - z0);

        for (int i = 0; i <= len; i++) {
            int cx = x0 + sx * i;
            int cz = z0 + sz * i;

            for (int w = -1; w <= 1; w++) {
                int bx = alongX ? cx : cx + w;
                int bz = alongX ? cz + w : cz;

                for (int h = 0; h < 3; h++) {
                    this.set(bx, y + h, bz, null);
                }

                this.set(bx, y - 1, bz, (i + w) % 5 == 0 ? "gravel" : "oak_planks");
            }

            // rails down the middle
            if ((i / 9) % 3 != 2) {
                this.set(cx, y, cz, alongX ? "rail[shape=east_west]" : "rail[shape=north_south]");
            }

            // oak frames every 4 blocks
            if (i % 4 == 0) {
                for (int w = -1; w <= 1; w += 2) {
                    int bx = alongX ? cx : cx + w;
                    int bz = alongX ? cz + w : cz;
                    this.set(bx, y, bz, "oak_fence");
                    this.set(bx, y + 1, bz, "oak_fence");
                }

                for (int w = -1; w <= 1; w++) {
                    this.set(alongX ? cx : cx + w, y + 2, alongX ? cz + w : cz, "oak_planks");
                }

                if (i % 12 == 0) {
                    this.set(cx, y + 1, cz, "lantern[hanging=true]");
                }
            } else if (this.rnd.nextDouble() < 0.025) {
                this.set(alongX ? cx : cx + 1, y + 2, alongX ? cz + 1 : cz, "cobweb");
            }
        }
    }

    private void shafts() {
        for (int li = 0; li < LEVELS.length; li++) {
            int y = LEVELS[li];
            int off = 0;

            for (int k = -160 + off; k <= 160; k += 60) {
                this.corridor(-185, k, 185, k, y);
                this.corridor(k, -185, k, 185, y);
            }
        }

        // ladders between the levels at a few crossings
        for (int k = -160; k <= 160; k += 120) {
            for (int j = -160; j <= 160; j += 120) {
                for (int y = LEVELS[2]; y <= LEVELS[0] + 2; y++) {
                    this.set(k + 2, y, j + 2, null);
                    this.set(k + 3, y, j + 2, "oak_planks");
                    this.set(k + 2, y, j + 2, "ladder[facing=west]");
                }
            }
        }

        // minecarts' chests left behind (decoration)
        for (int i = 0; i < 18; i++) {
            int x = -170 + this.rnd.nextInt(340);
            int k = -160 + this.rnd.nextInt(6) * 60;
            this.set(x, LEVELS[this.rnd.nextInt(3)], k + 1, "barrel[facing=up]");
        }
    }

    // ------------------------------------------------------------------ caverns, lake, lava

    private void cavern(int ox, int oy, int oz, double rx, double ry, double rz) {
        for (int x = (int)-rx - 2; x <= rx + 2; x++) {
            for (int y = (int)-ry - 2; y <= ry + 2; y++) {
                for (int z = (int)-rz - 2; z <= rz + 2; z++) {
                    double e = x * x / (rx * rx) + y * y / (ry * ry) + z * z / (rz * rz);
                    double wob = (noise(this.seed + 41L, (ox + x) * 1.3 + y, (oz + z) * 1.3) - 0.5) * 0.35;
                    if (e < 1.0 + wob) {
                        this.set(ox + x, oy + y, oz + z, null);
                    } else if (e < 1.35 + wob) {
                        double n = noise(this.seed + 43L, (ox + x) * 2.0 + y, (oz + z) * 2.0 - y);
                        this.set(ox + x, oy + y, oz + z, n > 0.82 ? "glowstone" : (n > 0.72 ? "budding_amethyst" : (n < 0.2 ? "calcite" : "amethyst_block")));
                    }
                }
            }
        }

        // a floor of smooth basalt and crystals growing up from it, froglights hanging from the roof
        for (int x = (int)-rx; x <= rx; x++) {
            for (int z = (int)-rz; z <= rz; z++) {
                int floor = oy - (int)Math.round(ry * Math.sqrt(Math.max(0.0, 1.0 - x * x / (rx * rx) - z * z / (rz * rz))));
                if (this.empty(ox + x, floor, oz + z) && !this.empty(ox + x, floor - 1, oz + z)) {
                    this.set(ox + x, floor - 1, oz + z, (x + z) % 7 == 0 ? "smooth_basalt" : "amethyst_block");
                    if (this.rnd.nextDouble() < 0.06) {
                        this.set(ox + x, floor, oz + z, "amethyst_cluster[facing=up]");
                    }
                }

                int roof = oy + (int)Math.round(ry * Math.sqrt(Math.max(0.0, 1.0 - x * x / (rx * rx) - z * z / (rz * rz))));
                if (this.rnd.nextDouble() < 0.012 && this.empty(ox + x, roof, oz + z)) {
                    this.set(ox + x, roof, oz + z, "pearlescent_froglight");
                }
            }
        }

        // lava falls behind tinted glass in the wall
        for (int i = 0; i < 2; i++) {
            int lx = ox + (int)Math.round(rx * (i == 0 ? -0.7 : 0.7));
            int lz = oz + (int)Math.round(rz * 0.6);

            for (int y = oy - (int)ry + 2; y <= oy + (int)(ry * 0.6); y++) {
                this.set(lx, y, lz, "tinted_glass");
                this.set(lx + 1, y, lz, "tinted_glass");
                this.set(lx, y, lz + 1, "lava");
                this.set(lx + 1, y, lz + 1, "lava");
                this.set(lx, y, lz + 2, "magma_block");
                this.set(lx + 1, y, lz + 2, "magma_block");
            }
        }
    }

    private void caverns() {
        this.cavern(-92, -42, 64, 28.0, 13.0, 22.0);
        this.cavern(100, -46, -72, 24.0, 12.0, 26.0);
    }

    private void lake() {
        int ox = 24;
        int oy = -28;
        int oz = 122;
        double rx = 30.0;
        double ry = 8.0;
        double rz = 20.0;

        for (int x = (int)-rx - 1; x <= rx + 1; x++) {
            for (int y = (int)-ry; y <= ry; y++) {
                for (int z = (int)-rz - 1; z <= rz + 1; z++) {
                    double e = x * x / (rx * rx) + y * y / (ry * ry) + z * z / (rz * rz);
                    if (e < 1.0) {
                        this.set(ox + x, oy + y, oz + z, y < -2 ? "water" : null);
                    } else if (e < 1.25 && y >= -2 && this.rnd.nextDouble() < 0.04) {
                        this.set(ox + x, oy + y, oz + z, "glow_lichen[down=true]");
                    }
                }
            }
        }

        // a few lights on the shore
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            this.set(ox + (int)(Math.cos(a) * (rx - 2.0)), oy - 2, oz + (int)(Math.sin(a) * (rz - 2.0)), "sea_lantern");
        }
    }

    // ------------------------------------------------------------------ spawn chambers

    private void chamber(int x, int y, int z) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    this.set(x + dx, y + dy, z + dz, null);
                }

                this.set(x + dx, y - 1, z + dz, (dx + dz) % 2 == 0 ? "spruce_planks" : "stripped_spruce_log[axis=x]");
            }
        }

        for (int sx = -4; sx <= 4; sx += 8) {
            for (int sz = -4; sz <= 4; sz += 8) {
                for (int dy = 0; dy <= 4; dy++) {
                    this.set(x + sx, y + dy, z + sz, "stripped_spruce_log[axis=y]");
                }
            }
        }

        this.set(x, y + 4, z, "lantern[hanging=true]");
        this.set(x - 3, y, z - 3, "barrel[facing=up]");
        this.set(x + 3, y, z - 3, "crafting_table");
    }

    private static final int SPAWN_Y = -36;

    private void chambers() {
        this.chamber(-168, SPAWN_Y, 0);
        this.chamber(168, SPAWN_Y, 0);

        for (int i = 0; i < 12; i++) {
            int[] p = this.ffaSpot(i, 12);
            this.chamber(p[0], p[1], p[2]);
        }
    }

    @Override
    public int[] spawn(int side) {
        return new int[]{side == 0 ? -168 : 168, SPAWN_Y, 0};
    }

    @Override
    public int[] ffaSpot(int i, int n) {
        double a = Math.PI * 2.0 * i / Math.max(1, n);
        int y = i % 2 == 0 ? SPAWN_Y + 6 : SPAWN_Y - 8;
        return new int[]{(int)Math.round(Math.cos(a) * 128.0), y, (int)Math.round(Math.sin(a) * 128.0)};
    }

    // ------------------------------------------------------------------ surface and camp

    private void surface() {
        for (int x = -this.R + 6; x <= this.R - 6; x += 3) {
            for (int z = -this.R + 6; z <= this.R - 6; z += 3) {
                if (Math.abs(x) > 46 || Math.abs(z) > 46) {
                    int t = this.surfaceAt(x, z);
                    double n = noise(this.seed + 61L, x * 0.3, z * 0.3);
                    if (n > 0.62 && this.rnd.nextDouble() < 0.12) {
                        this.conifer(x, t + 1, z, "spruce_log", "spruce", 7 + this.rnd.nextInt(6));
                    } else if (this.rnd.nextDouble() < 0.02) {
                        this.ellipsoid(x, t + 0.5, z, 1.8, 1.3, 1.5, this.rnd.nextBoolean() ? "andesite" : "mossy_cobblestone");
                    }
                }
            }
        }
    }

    private void crane(int x, int z, int h, int armDx) {
        int t = this.surfaceAt(x, z);

        for (int y = t + 1; y <= t + h; y++) {
            this.set(x, y, z, "stripped_spruce_log[axis=y]");
            this.set(x + 1, y, z, y % 3 == 0 ? "spruce_planks" : "spruce_fence");
        }

        for (int i = -2; i <= armDx; i++) {
            this.set(x + i, t + h + 1, z, "spruce_planks");
        }

        for (int y = t + h; y >= t + h - 6; y--) {
            this.set(x + armDx, y, z, "iron_chain[axis=y]");
        }

        this.set(x + armDx, t + h - 7, z, "barrel[facing=up]");
    }

    private void tent(int x, int z, String wool) {
        int t = this.surfaceAt(x, z);

        for (int i = 0; i <= 3; i++) {
            for (int dz = -3; dz <= 3; dz++) {
                this.set(x - 3 + i, t + 1 + i, z + dz, wool);
                this.set(x + 3 - i, t + 1 + i, z + dz, wool);
            }
        }

        this.set(x, t + 1, z - 4, "spruce_fence");
        this.set(x, t + 1, z + 4, "spruce_fence");
    }

    private void camp() {
        // flatten the camp
        for (int x = -46; x <= 46; x++) {
            for (int z = -46; z <= 46; z++) {
                int t = this.surfaceAt(x, z);

                for (int y = t + 1; y <= 3; y++) {
                    this.set(x, y, z, null);
                }

                this.set(x, 0, z, (x * x + z * z) % 11 == 0 ? "gravel" : (Math.abs(x) < 3 || Math.abs(z) < 3 ? "dirt_path" : "coarse_dirt"));
            }
        }

        // the mine entrance: a timber portal in a rock mound, its tunnel blocked by the barrier ceiling below
        this.ellipsoid(0, 0, -22, 16.0, 13.0, 12.0, "stone");
        this.ellipsoid(0, 6, -22, 13.0, 8.0, 10.0, "andesite");

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 1; dy <= 7; dy++) {
                for (int dz = -24; dz <= -10; dz++) {
                    this.set(dx, dy, dz, null);
                }
            }
        }

        for (int dz = -24; dz <= -10; dz += 3) {
            for (int dy = 1; dy <= 7; dy++) {
                this.set(-4, dy, dz, "stripped_dark_oak_log[axis=y]");
                this.set(4, dy, dz, "stripped_dark_oak_log[axis=y]");
            }

            for (int dx = -4; dx <= 4; dx++) {
                this.set(dx, 8, dz, "dark_oak_planks");
            }

            this.set(0, 7, dz, "lantern[hanging=true]");
        }

        for (int dz = -24; dz <= -6; dz++) {
            this.set(0, 1, dz, "rail[shape=north_south]");
        }

        for (int dx = -5; dx <= 5; dx++) {
            this.set(dx, 9, -10, "dark_oak_planks");
        }

        // cranes, tents, stacked logs and the viewing deck
        this.crane(-30, -18, 18, 8);
        this.crane(30, -26, 22, -9);
        this.tent(-26, 18, "white_wool");
        this.tent(-12, 30, "light_gray_wool");
        this.tent(22, 22, "white_wool");

        for (int i = 0; i < 6; i++) {
            this.set(14 + i, 1, -6, "spruce_log[axis=x]");
            this.set(14 + i, 2, -6, i < 4 ? "spruce_log[axis=x]" : null);
            this.set(14 + i, 1, -7, "spruce_log[axis=x]");
        }

        for (int x = 30; x <= 42; x++) {
            for (int z = 4; z <= 16; z++) {
                this.set(x, 6, z, "spruce_planks");
                if (x == 30 || x == 42 || z == 4 || z == 16) {
                    this.set(x, 7, z, "spruce_fence");
                }
            }
        }

        for (int sx = 30; sx <= 42; sx += 12) {
            for (int sz = 4; sz <= 16; sz += 12) {
                for (int y = 1; y <= 5; y++) {
                    this.set(sx, y, sz, "spruce_log[axis=y]");
                }
            }
        }

        for (int y = 1; y <= 5; y++) {
            this.set(31, y, 10, "ladder[facing=east]");
        }

        for (int i = 0; i < 10; i++) {
            this.lampPost(-40 + i * 9, 1, 40, "cobblestone", "spruce_fence", "lantern", 3);
        }

        this.spawn = new double[]{36.5, 7.0, 10.5, 90.0};
    }

    /** Barrier ceiling just under the dirt and barrier walls round the sides: the fight stays underground. */
    private void shell() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int t = this.surfaceAt(x, z);
                if (Math.abs(x) <= 46 && Math.abs(z) <= 46) {
                    t = 0;
                }

                this.set(x, Math.min(t, 0) - 5, z, "barrier");
                boolean edge = Math.abs(x) == this.R || Math.abs(z) == this.R;
                if (edge) {
                    for (int y = -DEPTH + 1; y < Math.min(t, 0) - 5; y++) {
                        this.set(x, y, z, "barrier");
                    }
                }
            }
        }
    }
}
