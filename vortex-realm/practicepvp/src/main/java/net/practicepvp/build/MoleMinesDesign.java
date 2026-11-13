package net.practicepvp.build;

/**
 * "Mole Hollow": the Mole PvP map. There is no surface - only solid rock, 81 x 81 blocks (101 for FFA) and 46 deep,
 * with real layers (stone, then tuff and andesite, then deepslate), pockets of granite, diorite and gravel, ore veins
 * and a few small dripstone caves. Each player starts in a small furnished base sealed in the rock - about 26 blocks
 * from the other one - and digs to find the enemy. A bedrock roof and floor and barrier sides keep everyone inside.
 */
public final class MoleMinesDesign extends DetailVoxels implements ArenaVoxels {
    public static final int RADIUS = 40;
    public static final int FFA_RADIUS = 50;
    /** Depth of the rock below the roof. */
    public static final int DEPTH = 46;
    private static final int BASE_Y = -22;
    private final long seed;
    private final boolean ffa;
    private final int[][] bases;

    public static int radius(boolean ffa) {
        return ffa ? FFA_RADIUS : RADIUS;
    }

    public MoleMinesDesign(int cx, int floorY, int cz, long seed) {
        this(cx, floorY, cz, seed, false);
    }

    public MoleMinesDesign(int cx, int floorY, int cz, long seed, boolean ffa) {
        super(cx, floorY, cz, radius(ffa), -DEPTH, 2, seed);
        this.seed = seed;
        this.ffa = ffa;
        this.bases = ffa ? this.ring(12) : this.pair();
        this.rock();
        this.caves();
        for (int[] b : this.bases) {
            this.base(b[0], b[1], b[2], b[3] > 0);
        }

        this.spectatorRoom();
        this.shell();
        this.finish();
    }

    @Override
    public String name() {
        return "Mole Hollow";
    }

    /** Two bases about 26 blocks apart on X, a little offset in height and depth so nobody knows the exact spot. */
    private int[][] pair() {
        int dz = (int)Math.round((this.hash3(1L, 0, 0, 0) - 0.5) * 8.0);
        int dy = (int)Math.round((this.hash3(2L, 0, 0, 0) - 0.5) * 6.0);
        return new int[][]{{-13, BASE_Y + dy, -dz, 1}, {13, BASE_Y - dy, dz, -1}};
    }

    private int[][] ring(int n) {
        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n;
            out[i] = new int[]{(int)Math.round(Math.cos(a) * 32.0), BASE_Y + (i % 2 == 0 ? 5 : -5), (int)Math.round(Math.sin(a) * 32.0), Math.cos(a) < 0 ? 1 : -1};
        }

        return out;
    }

    @Override
    public int[] spawn(int side) {
        int[] b = this.bases[Math.min(side, this.bases.length - 1)];
        return new int[]{b[0], b[1] + 1, b[2]};
    }

    @Override
    public int[] ffaSpot(int i, int n) {
        int[] b = this.bases[Math.floorMod(i, this.bases.length)];
        return new int[]{b[0], b[1] + 1, b[2]};
    }

    // ------------------------------------------------------------------ rock

    private String rockAt(int x, int y, int z) {
        if (y <= -DEPTH || y >= 0) {
            return "bedrock";
        } else if (y >= -2) {
            // the roof: deepslate above the stone so the top looks like the deep underground
            return this.hash3(3L, x, y, z) > 0.5 ? "deepslate" : "tuff";
        } else {
            double n = noise(this.seed + 3L, x * 0.9 + y * 0.7, z * 0.9 - y * 0.5);
            double v = noise(this.seed + 11L, x * 2.2 + y, z * 2.2 - y);
            String base = y > -14 ? "stone" : (y > -30 ? (n > 0.6 ? "tuff" : (n < 0.24 ? "andesite" : "stone")) : "deepslate");
            if (y <= -30 && y > -34 && this.hash3(4L, x, y, z) > 0.5) {
                // a ragged border between stone and deepslate, like vanilla
                base = "stone";
            }

            if (v > 0.86) {
                base = y > -30 ? "granite" : "tuff";
            } else if (v < 0.1) {
                base = y > -24 ? "gravel" : "cobbled_deepslate";
            } else if (v > 0.8 && y > -20) {
                base = "diorite";
            } else if (v < 0.14 && y <= -24) {
                base = "dripstone_block";
            }

            double o = noise(this.seed + 29L, x * 3.1 - y * 1.3, z * 3.1 + y * 1.1);
            if (o > 0.9) {
                boolean deep = y <= -30;
                if (y <= -36 && o > 0.955) {
                    return "deepslate_diamond_ore";
                } else if (y <= -26 && o > 0.94) {
                    return deep ? "deepslate_redstone_ore" : "redstone_ore";
                } else if (o > 0.935) {
                    return deep ? "deepslate_gold_ore" : "gold_ore";
                } else if (o > 0.92) {
                    return deep ? "deepslate_iron_ore" : "iron_ore";
                } else if (y > -24) {
                    return o > 0.91 ? "copper_ore" : "coal_ore";
                } else {
                    return deep ? "deepslate_lapis_ore" : "lapis_ore";
                }
            }

            return base;
        }
    }

    private void rock() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                for (int y = -DEPTH; y <= 0; y++) {
                    this.set(x, y, z, this.rockAt(x, y, z));
                }
            }
        }
    }

    /** True when (x, y, z) is within d blocks of any base. */
    private boolean nearBase(int x, int y, int z, double d) {
        for (int[] b : this.bases) {
            double dx = x - b[0];
            double dy = (y - b[1]) * 1.3;
            double dz = z - b[2];
            if (dx * dx + dy * dy + dz * dz < d * d) {
                return true;
            }
        }

        return false;
    }

    /** A few small dripstone caves (never close to a base) - pockets to find while digging. */
    private void caves() {
        int n = this.ffa ? 7 : 4;
        for (int k = 0; k < n; k++) {
            int x = (int)Math.round((this.hash3(10L + k, k, 0, 0) - 0.5) * (this.R - 8) * 2.0);
            int z = (int)Math.round((this.hash3(20L + k, 0, k, 0) - 0.5) * (this.R - 8) * 2.0);
            int y = -10 - (int)(this.hash3(30L + k, 0, 0, k) * 28.0);
            if (this.nearBase(x, y, z, 11.0)) {
                continue;
            }

            double rx = 2.5 + this.hash3(40L + k, k, k, 0) * 2.5;
            double ry = 1.6 + this.hash3(50L + k, k, 0, k) * 1.2;
            double rz = 2.5 + this.hash3(60L + k, 0, k, k) * 2.5;
            int n2 = (int)Math.ceil(Math.max(rx, rz));
            for (int dx = -n2; dx <= n2; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -n2; dz <= n2; dz++) {
                        double e = dx * dx / (rx * rx) + dy * dy / (ry * ry) + dz * dz / (rz * rz);
                        if (e <= 1.0 - this.hash3(70L, x + dx, y + dy, z + dz) * 0.25 && y + dy > -DEPTH + 1 && y + dy < -3) {
                            this.set(x + dx, y + dy, z + dz, null);
                        }
                    }
                }
            }

            // dripstone from the ceiling and the floor, glow lichen-like light from glow berries
            for (int dx = -n2; dx <= n2; dx++) {
                for (int dz = -n2; dz <= n2; dz++) {
                    int top = y + 3;
                    while (top > y - 3 && !this.empty(x + dx, top, z + dz)) {
                        top--;
                    }

                    if (top <= y - 3 || !this.empty(x + dx, top, z + dz)) {
                        continue;
                    }

                    int ceil = top;
                    while (ceil < y + 4 && this.empty(x + dx, ceil + 1, z + dz)) {
                        ceil++;
                    }

                    double r = this.hash3(80L, x + dx, y, z + dz);
                    if (r > 0.82 && this.empty(x + dx, ceil, z + dz) && !this.empty(x + dx, ceil + 1, z + dz)) {
                        this.set(x + dx, ceil + 1, z + dz, "dripstone_block");
                        this.set(x + dx, ceil, z + dz, "pointed_dripstone[vertical_direction=down,thickness=tip]");
                    } else if (r < 0.1) {
                        int fl = y - 3;
                        while (fl < ceil && !this.empty(x + dx, fl + 1, z + dz)) {
                            fl++;
                        }

                        if (this.empty(x + dx, fl + 1, z + dz) && !this.empty(x + dx, fl, z + dz)) {
                            this.set(x + dx, fl + 1, z + dz, r < 0.04 ? "lantern[hanging=false]" : "pointed_dripstone[vertical_direction=up,thickness=tip]");
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ bases

    /**
     * A 7x7 base, 4 high inside: a log frame with plank walls, a lantern-lit ceiling, a bed, a crafting table, a
     * furnace, a chest and a banner. {@code face} = which way the entrance wall (towards the enemy) looks.
     */
    private void base(int bx, int by, int bz, boolean facingEast) {
        String[] woods = {"spruce", "oak", "dark_oak", "birch"};
        String wood = woods[(int)(this.hash3(90L, bx, by, bz) * woods.length)];
        String log = wood + "_log";
        String in = facingEast ? "east" : "west";
        String out = facingEast ? "west" : "east";
        int dir = facingEast ? 1 : -1;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    boolean edgeX = Math.abs(dx) == 4;
                    boolean edgeZ = Math.abs(dz) == 4;
                    boolean shell = edgeX || edgeZ || dy == -1 || dy == 4;
                    int x = bx + dx;
                    int y = by + dy;
                    int z = bz + dz;
                    if (!shell) {
                        this.set(x, y, z, null);
                    } else if (edgeX && edgeZ) {
                        this.set(x, y, z, log(log, 'y'));
                    } else if (dy == -1) {
                        this.set(x, y, z, (dx + dz & 1) == 0 ? wood + "_planks" : "stripped_" + log + "[axis=" + (Math.abs(dx) > Math.abs(dz) ? 'x' : 'z') + "]");
                    } else if (dy == 4) {
                        this.set(x, y, z, edgeX || edgeZ ? log(log, edgeX ? 'z' : 'x') : wood + "_planks");
                    } else if (dy == 0 || dy == 3) {
                        this.set(x, y, z, log(log, edgeX ? 'z' : 'x'));
                    } else {
                        this.set(x, y, z, wood + "_planks");
                    }
                }
            }
        }

        // a rug, lights and furniture
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                this.set(bx + dx, by, bz + dz, (dx + dz & 1) == 0 ? "red_carpet" : "white_carpet");
            }
        }

        this.hangingLantern(bx, by + 3, bz, 0, false);
        this.set(bx - 2, by + 3, bz - 2, "lantern[hanging=true]");
        this.set(bx + 2, by + 3, bz + 2, "lantern[hanging=true]");
        int back = -dir * 3;
        this.set(bx + back, by, bz - 3, "crafting_table");
        this.set(bx + back, by, bz - 2, "furnace[facing=" + in + ",lit=false]");
        this.set(bx + back, by, bz + 3, "chest[facing=" + in + ",type=single]");
        this.set(bx + back, by, bz + 2, "barrel[facing=up]");
        this.set(bx + back, by + 1, bz + 2, "flower_pot");
        this.set(bx - 2, by, bz + 3, "red_bed[facing=south,part=head]");
        this.set(bx - 2, by, bz + 2, "red_bed[facing=south,part=foot]");
        this.banner(bx + back - dir, by + 2, bz, new String[]{"red", "blue", "green", "yellow"}[(int)(this.hash3(91L, bx, by, bz) * 4)], in);
        this.set(bx + 3 * dir, by + 1, bz - 3, "wall_torch[facing=" + out + "]");
        this.set(bx + 2, by, bz - 3, "composter[level=0]");
    }

    /** A small pocket for the spectator camera in the middle, closed by barriers so diggers can't break in. */
    private void spectatorRoom() {
        int y = -12;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || Math.abs(dy) == 2;
                    this.set(dx, y + dy, dz, shell ? "barrier" : null);
                }
            }
        }

        this.set(0, y + 1, 0, "light[level=15]");
    }

    /** Barriers on the four sides above and below the rock, so nobody walks out of the map. */
    private void shell() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                boolean side = Math.abs(x) == this.R || Math.abs(z) == this.R;
                if (side) {
                    for (int y = -DEPTH; y <= 0; y++) {
                        this.set(x, y, z, y <= -DEPTH || y >= 0 ? "bedrock" : "barrier");
                    }
                }

                this.set(x, 1, z, "barrier");
            }
        }
    }
}
