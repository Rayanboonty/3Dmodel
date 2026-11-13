package net.vortexrealm.build;

/**
 * Small "pro builder" touches shared by the arena designs: textured surfaces mixed from several blocks, trims of
 * stairs and slabs, pillars with a base and a capital, hanging lanterns on chains, wall banners, flower beds and
 * scattered plants. Coordinates are relative to the design centre and floor, like every {@link Voxels} method.
 */
public abstract class DetailVoxels extends MegaVoxels {
    protected final long dseed;

    protected DetailVoxels(int cx, int floorY, int cz, int radius, int yLo, int yHi, long seed) {
        super(cx, floorY, cz, radius, yLo, yHi, seed);
        this.dseed = seed;
    }

    /** A stable pseudo-random number 0..1 for a block position (the same every time for the same map). */
    protected double hash3(long salt, int x, int y, int z) {
        long h = (this.dseed + salt) * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L + z * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        return (h >>> 11 & 0xFFFFFL) / (double)0xFFFFF;
    }

    /**
     * A natural-looking mix: big patches follow smooth noise, single blocks are sprinkled in. The first block is
     * the most common one.
     */
    protected String texture(long salt, int x, int z, String... mats) {
        if (mats.length == 1) {
            return mats[0];
        }

        double n = noise(this.dseed + salt, x * 1.7, z * 1.7);
        double r = this.hash3(salt, x, 0, z);
        if (r > 0.9) {
            // sprinkles of the rarer blocks
            return mats[1 + (int)(this.hash3(salt + 7L, x, 1, z) * (mats.length - 1))];
        } else if (n > 0.62) {
            return mats[Math.min(mats.length - 1, 1)];
        } else if (n < 0.3 && mats.length > 2) {
            return mats[2];
        } else if (n > 0.55 && mats.length > 3 && r > 0.6) {
            return mats[3];
        } else {
            return mats[0];
        }
    }

    /** A column with a base block, a shaft and a capital (and an optional light on top). */
    protected void pillar(int x, int y, int z, int h, String base, String shaft, String cap, String light) {
        this.set(x, y, z, base);

        for (int i = 1; i < h - 1; i++) {
            this.set(x, y + i, z, shaft);
        }

        this.set(x, y + h - 1, z, cap);
        if (light != null) {
            this.set(x, y + h, z, light);
        }
    }

    /** A 3x3 pillar with stair skirts at the bottom and top: reads as a real column from far away. */
    protected void bigPillar(int x, int y, int z, int h, String mat, String stairMat, String core, String light) {
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, core);
        }

        String[][] side = {{"0", "-1", "south"}, {"0", "1", "north"}, {"-1", "0", "east"}, {"1", "0", "west"}};
        for (String[] s : side) {
            int dx = Integer.parseInt(s[0]);
            int dz = Integer.parseInt(s[1]);
            this.set(x + dx, y, z + dz, stairs(stairMat, s[2], false));
            this.set(x + dx, y + h - 1, z + dz, stairs(stairMat, s[2], true));
        }

        this.set(x, y + h, z, mat);
        if (light != null) {
            this.set(x, y + h + 1, z, light);
        }
    }

    /** A lantern hanging from a chain of the given length below {@code yTop}. */
    protected void hangingLantern(int x, int yTop, int z, int chain, boolean soul) {
        for (int i = 0; i < chain; i++) {
            this.set(x, yTop - i, z, "iron_chain[axis=y]");
        }

        this.set(x, yTop - chain, z, (soul ? "soul_lantern" : "lantern") + "[hanging=true]");
    }

    protected void lantern(int x, int y, int z, boolean soul) {
        this.set(x, y, z, (soul ? "soul_lantern" : "lantern") + "[hanging=false]");
    }

    /** A wall banner hanging on the face of the block it is placed against. */
    protected void banner(int x, int y, int z, String color, String facing) {
        this.set(x, y, z, color + "_wall_banner[facing=" + facing + "]");
    }

    /** A standing banner (rotation 0-15). */
    protected void standingBanner(int x, int y, int z, String color, int rotation) {
        this.set(x, y, z, color + "_banner[rotation=" + (rotation & 15) + "]");
    }

    /** Plants on top of the block at y-1 where there is air: grass, flowers, ferns... */
    protected void plant(int x, int y, int z, String plant) {
        if (this.empty(x, y, z) && !this.empty(x, y - 1, z)) {
            this.set(x, y, z, plant);
        }
    }

    /** A tall plant (lilac, rose bush, large fern, tall grass) as its two halves. */
    protected void tallPlant(int x, int y, int z, String plant) {
        if (this.empty(x, y, z) && this.empty(x, y + 1, z) && !this.empty(x, y - 1, z)) {
            this.set(x, y, z, plant + "[half=lower]");
            this.set(x, y + 1, z, plant + "[half=upper]");
        }
    }

    /** A flower bed: a slab rim around soil with mixed flowers. */
    protected void flowerBed(int x0, int z0, int x1, int z1, int y, String rim, String soil, String... flowers) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge) {
                    this.set(x, y, z, slab(rim, false));
                } else {
                    this.set(x, y - 1, z, soil);
                    this.set(x, y, z, flowers[(int)(this.hash3(31L, x, y, z) * flowers.length)]);
                }
            }
        }
    }

    /** A planter: a block with a leaf ball on top, like the hedges of big server lobbies. */
    protected void bush(int x, int y, int z, String leaves, double r) {
        int n = (int)Math.ceil(r);

        for (int dx = -n; dx <= n; dx++) {
            for (int dy = 0; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    if (Math.sqrt(dx * dx + dy * dy * 1.4 + dz * dz) <= r - this.hash3(41L, x + dx, y + dy, z + dz) * 0.5) {
                        this.setIfEmpty(x + dx, y + dy, z + dz, leaves(leaves));
                    }
                }
            }
        }
    }

    /** A round tree with a few branches and a leaf crown that is not a perfect ball. */
    protected void niceTree(int x, int y, int z, String log, String leaves, int trunk, double crown) {
        for (int i = 0; i < trunk; i++) {
            this.set(x, y + i, z, log(log, 'y'));
        }

        int top = y + trunk;
        for (int b = 0; b < 3; b++) {
            double a = this.hash3(51L + b, x, y, z) * Math.PI * 2.0;
            int bx = x + (int)Math.round(Math.cos(a) * 2.0);
            int bz = z + (int)Math.round(Math.sin(a) * 2.0);
            this.set(x + (int)Math.round(Math.cos(a)), top - 1, z + (int)Math.round(Math.sin(a)), log(log, Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? 'x' : 'z'));
            this.blob(bx, top, bz, leaves, crown * 0.7);
        }

        this.blob(x, top + 1, z, leaves, crown);
    }

    private void blob(int x, int y, int z, String leaves, double r) {
        int n = (int)Math.ceil(r);

        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.8 + dz * dz);
                    if (d <= r - this.hash3(61L, x + dx, y + dy, z + dz) * 0.8) {
                        this.setIfEmpty(x + dx, y + dy, z + dz, leaves(leaves));
                    }
                }
            }
        }
    }

    /** A small boulder of mixed stone. */
    protected void boulder(int x, int y, int z, double r, String... mats) {
        int n = (int)Math.ceil(r);

        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -1; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    if (Math.sqrt(dx * dx + dy * dy * 1.5 + dz * dz) <= r - this.hash3(71L, x + dx, y + dy, z + dz) * 0.7) {
                        this.set(x + dx, y + dy, z + dz, this.texture(73L, x + dx + dy * 7, z + dz, mats));
                    }
                }
            }
        }
    }
}
