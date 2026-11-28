package net.vortexrealm.build;

public abstract class MegaVoxels extends Voxels {
    protected MegaVoxels(int cx, int floorY, int cz, int radius, int yLo, int yHi, long seed) {
        super(cx, floorY, cz, radius, yLo, yHi, seed);
    }

    protected static double dist(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    protected static double angle(double x, double z) {
        return Math.toDegrees(Math.atan2(z, x));
    }

    protected static double angDiff(double a, double b) {
        return Math.abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0);
    }

    protected static int px(double r, double deg) {
        return (int)Math.round(Math.cos(Math.toRadians(deg)) * r);
    }

    protected static int pz(double r, double deg) {
        return (int)Math.round(Math.sin(Math.toRadians(deg)) * r);
    }

    protected void islandBase(double r, String top, int maxDepth, long seed) {
        int n = (int)Math.ceil(r) + 4;

        for (int x = -n; x <= n; x++) {
            for (int z = -n; z <= n; z++) {
                double d = dist(x, z);
                double edge = r + (noise(seed, x, z) - 0.5) * 8.0;
                if (!(d > edge)) {
                    this.set(x, 0, z, top);
                    double f = 1.0 - d / edge;
                    double depth = 3.0 + Math.pow(f, 0.7) * maxDepth * (0.75 + 0.35 * noise(seed + 5L, x * 1.7, z * 1.7));
                    depth = Math.min((double)maxDepth, depth);

                    for (int k = 1; k <= depth; k++) {
                        String b;
                        if (k <= 2) {
                            b = "dirt";
                        } else {
                            double v = noise(seed + 9L, x * 2 + k, z * 2 - k);
                            b = k > depth - 2.0 && f > 0.4 ? "deepslate" : (v > 0.7 ? "andesite" : (v < 0.25 ? "tuff" : "stone"));
                        }

                        this.set(x, -k, z, b);
                    }
                }
            }
        }
    }

    protected void squareBase(int h, int cr, String top, int maxDepth, long seed) {
        for (int x = -h - 3; x <= h + 3; x++) {
            for (int z = -h - 3; z <= h + 3; z++) {
                double ex = Math.max(0, Math.abs(x) - (h - cr));
                double ez = Math.max(0, Math.abs(z) - (h - cr));
                double d = Math.sqrt(ex * ex + ez * ez);
                double edge = cr + (noise(seed, x, z) - 0.5) * 5.0;
                if (!(d > edge)) {
                    this.set(x, 0, z, top);
                    double inner = (double)Math.min(h - Math.max(Math.abs(x), Math.abs(z)), h) / h;
                    double depth = Math.min((double)maxDepth, 3.0 + Math.max(0.0, inner) * maxDepth * (0.7 + 0.4 * noise(seed + 3L, x, z)) + (cr - d) * 0.3);

                    for (int k = 1; k <= depth; k++) {
                        double v = noise(seed + 9L, x * 2 + k, z * 2 - k);
                        this.set(x, -k, z, k <= 2 ? "dirt" : (v > 0.7 ? "andesite" : (v < 0.25 ? "tuff" : "stone")));
                    }
                }
            }
        }
    }

    protected void paint(int x, int z, String b) {
        if (!this.empty(x, 0, z)) {
            this.set(x, 0, z, b);
        }
    }

    protected void road(double x0, double z0, double x1, double z1, int w, String surface, String border, int lampEvery, String lampPost, String light) {
        double len = dist(x1 - x0, z1 - z0);
        if (!(len < 1.0)) {
            double ux = (x1 - x0) / len;
            double uz = (z1 - z0) / len;
            double nx = -uz;
            double nz = ux;
            double half = w / 2.0;

            for (double t = 0.0; t <= len; t += 0.5) {
                for (double s = -half - 1.0; s <= half + 1.0; s += 0.5) {
                    int x = (int)Math.round(x0 + ux * t + nx * s);
                    int z = (int)Math.round(z0 + uz * t + nz * s);
                    this.paint(x, z, Math.abs(s) > half - 0.2 ? border : surface);
                }
            }

            if (lampEvery > 0) {
                for (double t = lampEvery / 2.0; t < len - 2.0; t += lampEvery) {
                    for (int side = -1; side <= 1; side += 2) {
                        int x = (int)Math.round(x0 + ux * t + nx * (half + 2.0) * side);
                        int z = (int)Math.round(z0 + uz * t + nz * (half + 2.0) * side);
                        if (!this.empty(x, 0, z) && this.empty(x, 1, z)) {
                            this.lamp(x, z, 5, lampPost, light);
                        }
                    }
                }
            }
        }
    }

    protected void lamp(int x, int z, int h, String post, String light) {
        this.set(x, 1, z, "polished_blackstone");

        for (int i = 2; i < h; i++) {
            this.set(x, i, z, post);
        }

        this.set(x, h, z, light);
        this.set(x, h + 1, z, "polished_blackstone_slab[type=bottom]");
    }

    protected void roundPlaza(int ox, int oz, double r, String a, String b, String rim) {
        int n = (int)Math.ceil(r);

        for (int x = -n; x <= n; x++) {
            for (int z = -n; z <= n; z++) {
                double d = dist(x, z);
                if (!(d > r)) {
                    String blk = d > r - 1.2 ? rim : ((int)d % 3 == 0 ? b : a);
                    this.paint(ox + x, oz + z, blk);
                }
            }
        }
    }

    protected void house(int x0, int z0, int w, int d, int h, String wall, String log, String roofMat, String face) {
        int x1 = x0 + w - 1;
        int z1 = z0 + d - 1;
        this.box(x0, 0, z0, x1, 0, z1, "cobblestone");
        this.walls(x0, 1, z0, x1, h, z1, wall);
        this.box(x0 + 1, 1, z0 + 1, x1 - 1, h, z1 - 1, "air");
        this.box(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, "spruce_planks");

        for (int y = 1; y <= h; y++) {
            this.set(x0, y, z0, log(log, 'y'));
            this.set(x1, y, z0, log(log, 'y'));
            this.set(x0, y, z1, log(log, 'y'));
            this.set(x1, y, z1, log(log, 'y'));
        }

        for (int x = x0; x <= x1; x++) {
            this.set(x, h, z0, log(log, 'x'));
            this.set(x, h, z1, log(log, 'x'));
        }

        for (int z = z0; z <= z1; z++) {
            this.set(x0, h, z, log(log, 'z'));
            this.set(x1, h, z, log(log, 'z'));
        }

        for (int x = x0 + 2; x < x1 - 1; x += 3) {
            this.set(x, 2, z0, "glass_pane");
            this.set(x, 2, z1, "glass_pane");
        }

        for (int z = z0 + 2; z < z1 - 1; z += 3) {
            this.set(x0, 2, z, "glass_pane");
            this.set(x1, 2, z, "glass_pane");
        }

        int mx = (x0 + x1) / 2;
        int mz = (z0 + z1) / 2;
        switch (face) {
            case "north":
                this.door(mx, z0);
                break;
            case "south":
                this.door(mx, z1);
                break;
            case "west":
                this.door(x0, mz);
                break;
            default:
                this.door(x1, mz);
        }

        this.set(mx, h - 1, mz, "lantern[hanging=true]");
        boolean alongX = w >= d;
        int span = alongX ? d : w;

        for (int i = 0; i <= span / 2; i++) {
            int y = h + 1 + i;
            if (alongX) {
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    this.set(x, y, z0 - 1 + i, stairs(roofMat, "south", false));
                    this.set(x, y, z1 + 1 - i, stairs(roofMat, "north", false));
                }

                if (z0 - 1 + i >= z1 + 1 - i - 1) {
                    for (int x = x0 - 1; x <= x1 + 1; x++) {
                        this.set(x, y, (z0 + z1) / 2, slab(roofMat, false));
                    }
                }

                for (int z = z0 + i; z <= z1 - i; z++) {
                    this.set(x0, y, z, wall);
                    this.set(x1, y, z, wall);
                }
            } else {
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    this.set(x0 - 1 + i, y, z, stairs(roofMat, "east", false));
                    this.set(x1 + 1 - i, y, z, stairs(roofMat, "west", false));
                }

                if (x0 - 1 + i >= x1 + 1 - i - 1) {
                    for (int z = z0 - 1; z <= z1 + 1; z++) {
                        this.set((x0 + x1) / 2, y, z, slab(roofMat, false));
                    }
                }

                for (int x = x0 + i; x <= x1 - i; x++) {
                    this.set(x, y, z0, wall);
                    this.set(x, y, z1, wall);
                }
            }
        }
    }

    private void door(int x, int z) {
        this.set(x, 1, z, "air");
        this.set(x, 2, z, "air");
    }

    protected void tower(int ox, int oz, double r, int h, String wall, String trim, String roof, String light) {
        this.towerAt(ox, 0, oz, r, h, wall, trim, roof, light);
    }

    protected void towerAt(int ox, int by, int oz, double r, int h, String wall, String trim, String roof, String light) {
        this.cylinder(ox, oz, by, by + h, r, wall, false);
        this.cylinder(ox, oz, by + 1, by + h - 1, r - 1.2, "air", false);
        this.disc(ox, oz, by + h, r, trim);

        for (int y = 4; y < h - 2; y += 5) {
            for (int a = 0; a < 360; a += 90) {
                this.set(ox + px(r, a + y * 7), by + y, oz + pz(r, a + y * 7), "black_stained_glass_pane");
            }
        }

        int n = (int)Math.max(8L, Math.round(r * 2.0 * Math.PI / 2.0));

        for (int i = 0; i < n; i += 2) {
            double a = 360.0 * i / n;
            this.set(ox + px(r, a), by + h + 1, oz + pz(r, a), trim);
        }

        if (roof != null) {
            this.cone(ox, oz, by + h + 1, by + h + 1 + (int)Math.round(r * 2.2), r + 0.8, roof);
            this.set(ox, by + h + 3 + (int)Math.round(r * 2.2), oz, light);
        } else if (light != null) {
            this.set(ox, by + h + 1, oz, light);
        }
    }

    protected void castleWall(double x0, double z0, double x1, double z1, int h, int thick, String body, String trim, String light) {
        double len = dist(x1 - x0, z1 - z0);
        if (!(len < 1.0)) {
            double ux = (x1 - x0) / len;
            double uz = (z1 - z0) / len;
            double nx = -uz;
            double nz = ux;
            int step = 0;

            for (double t = 0.0; t <= len; t += 0.5) {
                step++;

                for (double s = -thick / 2.0; s <= thick / 2.0; s += 0.5) {
                    int x = (int)Math.round(x0 + ux * t + nx * s);
                    int z = (int)Math.round(z0 + uz * t + nz * s);

                    for (int y = 1; y <= h; y++) {
                        this.set(x, y, z, body);
                    }

                    this.set(x, 0, z, body);
                }

                boolean merlon = (int)t % 3 != 2;

                for (int side = -1; side <= 1; side += 2) {
                    int x = (int)Math.round(x0 + ux * t + nx * (thick / 2.0) * side);
                    int z = (int)Math.round(z0 + uz * t + nz * (thick / 2.0) * side);
                    this.set(x, h + 1, z, merlon ? trim : "air");
                }

                if (step % 24 == 0) {
                    int x = (int)Math.round(x0 + ux * t + nx * (thick / 2.0 + 1.0));
                    int z = (int)Math.round(z0 + uz * t + nz * (thick / 2.0 + 1.0));
                    this.set(x, h - 2, z, light);
                }
            }
        }
    }

    protected void skyscraper(int x0, int z0, int w, int d, int h, String frame, String glass, String accent, String roof) {
        int x1 = x0 + w - 1;
        int z1 = z0 + d - 1;
        int setback = h > 40 ? h - h / 4 : h + 1;

        for (int y = 0; y <= h; y++) {
            int in = y >= setback ? 2 : 0;
            int ax0 = x0 + in;
            int ax1 = x1 - in;
            int az0 = z0 + in;
            int az1 = z1 - in;
            boolean floor = y % 4 == 0;

            for (int x = ax0; x <= ax1; x++) {
                for (int z = az0; z <= az1; z++) {
                    boolean edge = x == ax0 || x == ax1 || z == az0 || z == az1;
                    if (!edge) {
                        if (floor) {
                            this.set(x, y, z, "smooth_stone");
                        }
                    } else {
                        boolean corner = (x == ax0 || x == ax1) && (z == az0 || z == az1);
                        boolean mullion = (x - ax0) % 4 == 0 && (z == az0 || z == az1) || (z - az0) % 4 == 0 && (x == ax0 || x == ax1);
                        String b = !corner && !floor && !mullion ? glass : frame;
                        if (floor && y % 12 == 0 && y > 0) {
                            b = accent;
                        }

                        this.set(x, y, z, b);
                    }
                }
            }
        }

        int top = h + 1;
        this.box(x0 + 2, top, z0 + 2, x1 - 2, top, z1 - 2, roof);
        int mx = (x0 + x1) / 2;
        int mz = (z0 + z1) / 2;
        int mast = Math.max(4, h / 8);

        for (int i = 1; i <= mast; i++) {
            this.set(mx, top + i, mz, "iron_bars");
        }

        this.set(mx, top + mast + 1, mz, "redstone_lamp[lit=true]");
        int ex = (x0 + x1) / 2;
        this.box(ex - 1, 1, z0, ex + 1, 3, z0, "air");
        this.box(ex - 2, 4, z0 - 1, ex + 2, 4, z0 - 1, slab("smooth_stone", false));
    }

    protected void pyramid(int ox, int oz, int half, int stepH, String block, String trim, String top) {
        int y = 0;

        for (int h = half; h > 2; h -= 2) {
            for (int k = 0; k < stepH; k++) {
                y++;
                this.box(ox - h, y, oz - h, ox + h, y, oz + h, k == stepH - 1 ? trim : block);
            }
        }

        for (int yy = 1; yy <= y; yy++) {
            int z = oz + half + 2 - yy;

            for (int dx = -2; dx <= 2; dx++) {
                this.set(ox + dx, yy, z, stairs("sandstone", "north", false));

                for (int up = 1; up <= 3; up++) {
                    if (yy + up <= y) {
                        this.set(ox + dx, yy + up, z, "air");
                    }
                }
            }
        }

        this.box(ox - 2, y + 1, oz - 2, ox + 2, y + 4, oz + 2, block);
        this.box(ox - 1, y + 1, oz - 1, ox + 1, y + 3, oz + 1, "air");
        this.set(ox, y + 1, oz + 2, "air");
        this.set(ox, y + 2, oz + 2, "air");
        this.box(ox - 3, y + 5, oz - 3, ox + 3, y + 5, oz + 3, trim);
        this.set(ox, y + 6, oz, top);
    }

    protected void giantSword(int ox, int oz, int h, String blade, String edge, String guard, String grip, boolean facingX) {
        this.giantSwordAt(ox, 0, oz, h, blade, edge, guard, grip, facingX);
    }

    protected void giantSwordAt(int ox, int by, int oz, int h, String blade, String edge, String guard, String grip, boolean facingX) {
        int bladeH = (int)(h * 0.68);

        for (int y = 0; y <= bladeH; y++) {
            int w = y > bladeH - 4 ? 0 : 1;

            for (int s = -w; s <= w; s++) {
                int x = facingX ? ox + s : ox;
                int z = facingX ? oz : oz + s;
                this.set(x, by + y, z, s == 0 ? blade : edge);
            }
        }

        int gy = bladeH + 1;

        for (int s = -5; s <= 5; s++) {
            this.set(facingX ? ox + s : ox, by + gy, facingX ? oz : oz + s, guard);
        }

        this.set(facingX ? ox - 5 : ox, by + gy + 1, facingX ? oz : oz - 5, guard);
        this.set(facingX ? ox + 5 : ox, by + gy + 1, facingX ? oz : oz + 5, guard);

        for (int y = gy + 1; y <= h - 2; y++) {
            this.set(ox, by + y, oz, grip);
        }

        this.sphere(ox, by + h - 1, oz, 1.4, guard);

        for (int a = 0; a < 360; a += 30) {
            for (double r = 2.0; r < 5 + this.rnd.nextInt(3); r++) {
                int x = ox + px(r, a);
                int z = oz + pz(r, a);
                if (!this.empty(x, by, z)) {
                    this.set(x, by, z, this.rnd.nextBoolean() ? "cracked_stone_bricks" : "gravel");
                }
            }
        }
    }

    protected void fountain(int ox, int oz, int r, String rim, String accent) {
        this.ring(ox, oz, 0, r - 1, r, rim);
        this.ring(ox, oz, 1, r - 0.5, r, slab(rim.replace("_bricks", "_brick").replace("quartz_block", "quartz"), false));
        this.disc(ox, oz, 0, r - 1.5, "water");
        this.disc(ox, oz, -1, r - 1, rim);
        this.cylinder(ox, oz, 1, 4, 1.0, accent, false);
        this.disc(ox, oz, 5, 2.2, rim);
        this.set(ox, 6, oz, "water");
        this.set(ox, 5, oz, accent);
    }

    protected void arch(int ox, int oz, int width, int h, String block, String key, boolean alongX) {
        int half = width / 2;

        for (int s = -half; s <= half; s++) {
            double t = (double)s / half;
            int top = (int)Math.round(h - 3 + Math.sqrt(Math.max(0.0, 1.0 - t * t)) * 3.0);

            for (int depth = -1; depth <= 1; depth++) {
                int x = alongX ? ox + depth : ox + s;
                int z = alongX ? oz + s : oz + depth;
                if (Math.abs(s) >= half - 1) {
                    for (int y = 1; y <= h; y++) {
                        this.set(x, y, z, block);
                    }
                } else {
                    this.set(x, top, z, s == 0 ? key : block);
                    this.set(x, top + 1, z, block);
                }
            }
        }
    }

    protected void bridge(double x0, double y0, double z0, double x1, double y1, double z1, String deck, String rail) {
        double len = dist(x1 - x0, z1 - z0);
        if (!(len < 1.0)) {
            double ux = (x1 - x0) / len;
            double uz = (z1 - z0) / len;
            double nx = -uz;
            double nz = ux;

            for (double t = 0.0; t <= len; t += 0.5) {
                double f = t / len;
                double sag = Math.sin(f * Math.PI) * Math.min(4.0, len / 14.0);
                int y = (int)Math.round(y0 + (y1 - y0) * f - sag);

                for (double s = -1.5; s <= 1.5; s += 0.5) {
                    int x = (int)Math.round(x0 + ux * t + nx * s);
                    int z = (int)Math.round(z0 + uz * t + nz * s);
                    this.set(x, y, z, deck);
                }

                for (int side = -1; side <= 1; side += 2) {
                    int x = (int)Math.round(x0 + ux * t + nx * 2.5 * side);
                    int z = (int)Math.round(z0 + uz * t + nz * 2.5 * side);
                    this.set(x, y + 1, z, rail);
                }
            }
        }
    }

    protected void grove(int ox, int oz, double r, int count, String logType, String leafType) {
        for (int i = 0; i < count; i++) {
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(this.rnd.nextDouble()) * r;
            int x = ox + (int)Math.round(Math.cos(a) * d);
            int z = oz + (int)Math.round(Math.sin(a) * d);
            if (!this.empty(x, 0, z) && this.empty(x, 1, z) && this.empty(x, 3, z)) {
                this.tree(x, 1, z, logType, leafType, 4 + this.rnd.nextInt(3), 2.6 + this.rnd.nextDouble() * 1.2);
            }
        }
    }

    protected void groveAt(int ox, int by, int oz, double r, int count, String logType, String leafType) {
        for (int i = 0; i < count; i++) {
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(this.rnd.nextDouble()) * r;
            int x = ox + (int)Math.round(Math.cos(a) * d);
            int z = oz + (int)Math.round(Math.sin(a) * d);
            if (!this.empty(x, by, z) && this.empty(x, by + 1, z) && this.empty(x, by + 3, z)) {
                this.tree(x, by + 1, z, logType, leafType, 4 + this.rnd.nextInt(3), 2.6 + this.rnd.nextDouble() * 1.2);
            }
        }
    }

    protected void meadow(int ox, int oz, double r, double density) {
        String[] fl = new String[]{
            "poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "short_grass", "short_grass", "short_grass", "fern"
        };
        int n = (int)Math.ceil(r);

        for (int x = -n; x <= n; x++) {
            for (int z = -n; z <= n; z++) {
                if (!(dist(x, z) > r) && !(this.rnd.nextDouble() > density)) {
                    String g = this.get(ox + x, 0, oz + z);
                    if ("grass_block".equals(g) && this.empty(ox + x, 1, oz + z)) {
                        this.set(ox + x, 1, oz + z, fl[this.rnd.nextInt(fl.length)]);
                    }
                }
            }
        }
    }

    protected void cloud(int ox, int oy, int oz, double r) {
        boolean was = this.soft;
        this.soft = true;

        for (int i = 0; i < 5; i++) {
            this.ellipsoid(
                ox + this.rnd.nextInt((int)r + 1) - r / 2.0,
                oy + this.rnd.nextInt(2),
                oz + this.rnd.nextInt((int)r + 1) - r / 2.0,
                r * (0.5 + this.rnd.nextDouble() * 0.5),
                r * 0.3,
                r * (0.4 + this.rnd.nextDouble() * 0.4),
                "white_concrete_powder"
            );
        }

        this.soft = was;
    }

    protected boolean free(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!"grass_block".equals(this.get(x, 0, z))) {
                    return false;
                }

                for (int y = 1; y <= 8; y++) {
                    if (!this.empty(x, y, z)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    protected void village(double rMin, double rMax, int cell, String[] walls, String[] roofs, String log) {
        int gx = -this.R;

        while (gx <= this.R) {
            for (int gz = -this.R; gz <= this.R; gz += cell) {
                double d = dist(gx, gz);
                if (!(d < rMin) && !(d > rMax)) {
                    int w = 7 + this.rnd.nextInt(3);
                    int dd = 6 + this.rnd.nextInt(3);
                    if (!this.free(gx - 2, gz - 2, gx + w + 1, gz + dd + 1)) {
                        if (this.rnd.nextInt(3) == 0 && this.free(gx, gz, gx + 1, gz + 1)) {
                            this.tree(
                                gx,
                                1,
                                gz,
                                this.rnd.nextBoolean() ? "oak_log" : "birch_log",
                                this.rnd.nextBoolean() ? "oak" : "birch",
                                4 + this.rnd.nextInt(2),
                                2.5
                            );
                        }
                    } else {
                        String face = facing(-gx, -gz);
                        this.house(
                            gx, gz, w, dd, 4 + this.rnd.nextInt(3), walls[this.rnd.nextInt(walls.length)], log, roofs[this.rnd.nextInt(roofs.length)], face
                        );

                        for (int x = gx - 1; x <= gx + w; x++) {
                            this.paint(x, gz - 2, "dirt_path");
                        }

                        if (this.rnd.nextInt(3) == 0) {
                            this.set(gx + w + 1, 1, gz + dd, "composter");
                        }
                    }
                }
            }

            gx += cell;
        }
    }

    protected void bench(int x, int z, String face, boolean alongX) {
        for (int s = -1; s <= 1; s++) {
            this.set(alongX ? x + s : x, 1, alongX ? z : z + s, stairs("spruce", face, false));
        }
    }
}
