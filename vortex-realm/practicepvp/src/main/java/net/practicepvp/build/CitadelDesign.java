package net.practicepvp.build;

public final class CitadelDesign extends Voxels {
    private static final CitadelDesign.Portal[] PORTALS = new CitadelDesign.Portal[]{
        new CitadelDesign.Portal(-90.0, "polished_blackstone_bricks", "gold_block", "yellow_stained_glass_pane", "gilded_blackstone"),
        new CitadelDesign.Portal(-135.0, "mossy_stone_bricks", "moss_block", "lime_stained_glass_pane", "mossy_cobblestone"),
        new CitadelDesign.Portal(-45.0, "red_nether_bricks", "redstone_block", "red_stained_glass_pane", "nether_bricks"),
        new CitadelDesign.Portal(180.0, "purpur_block", "amethyst_block", "purple_stained_glass_pane", "purpur_pillar"),
        new CitadelDesign.Portal(0.0, "cut_sandstone", "gold_block", "orange_stained_glass_pane", "chiseled_sandstone"),
        new CitadelDesign.Portal(135.0, "dark_prismarine", "emerald_block", "green_stained_glass_pane", "prismarine_bricks"),
        new CitadelDesign.Portal(45.0, "quartz_bricks", "lapis_block", "light_blue_stained_glass_pane", "chiseled_quartz_block")
    };
    private final int ri;
    private final double portalR;

    public CitadelDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -((int)(radius * 0.42)) - 6, 84, seed);
        this.ri = (int)Math.round(radius * 0.8);
        this.portalR = this.ri * 0.64;
        this.base();
        this.plaza();
        this.canal();
        this.roads();
        this.gardens();

        for (CitadelDesign.Portal p : PORTALS) {
            this.portal(p);
        }

        this.spire();
        this.outerWall();
        this.islets();
        this.spawn = new double[]{0.5, 1.0, 44.5, 180.0};
        this.finish();
    }

    @Override
    public String name() {
        return "citadel";
    }

    private static double dist(int x, int z) {
        return Math.sqrt(x * x + z * z);
    }

    private static double angle(int x, int z) {
        return Math.toDegrees(Math.atan2(z, x));
    }

    private static double angDiff(double a, double b) {
        return Math.abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0);
    }

    private boolean onAvenue(int x, int z, double halfWidth) {
        double r = dist(x, z);
        if (!(r < 34.0) && !(r > this.ri - 4)) {
            for (CitadelDesign.Portal p : PORTALS) {
                if (r < this.portalR && perp(x, z, p.angle) <= halfWidth && angDiff(angle(x, z), p.angle) < 90.0) {
                    return true;
                }
            }

            return perp(x, z, 90.0) <= halfWidth + 1.0 && z > 0;
        } else {
            return false;
        }
    }

    private static double perp(int x, int z, double deg) {
        double a = Math.toRadians(deg);
        return Math.abs(-Math.sin(a) * x + Math.cos(a) * z);
    }

    private void base() {
        int n = this.ri;

        for (int x = -n - 2; x <= n + 2; x++) {
            for (int z = -n - 2; z <= n + 2; z++) {
                double d = dist(x, z);
                double edge = this.ri + noise(11L, x, z) * 3.0 - 1.0;
                if (!(d > edge)) {
                    this.set(x, 0, z, "grass_block");
                    double depth = 4.0 + (1.0 - d / edge) * this.ri * 0.36 + noise(23L, x, z) * 6.0;

                    for (int k = 1; k <= depth; k++) {
                        String b;
                        if (k <= 2) {
                            b = "dirt";
                        } else if (k > depth - 3.0 && d < edge * 0.5) {
                            b = "deepslate";
                        } else {
                            double v = noise(31L, x * 2 + k, z * 2 - k);
                            b = v > 0.72 ? "andesite" : (v < 0.22 ? "tuff" : "stone");
                        }

                        this.set(x, -k, z, b);
                    }
                }
            }
        }
    }

    private void plaza() {
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                double r = dist(x, z);
                if (!(r > 25.0)) {
                    double a = Math.toRadians(angle(x, z));
                    double star = 13.0 + 6.0 * Math.abs(Math.cos(a * 4.0));
                    String b;
                    if (r > 23.5) {
                        b = "polished_blackstone_bricks";
                    } else if (r > 22.5) {
                        b = "gold_block";
                    } else if (r < star && r > 9.0) {
                        b = Math.abs(Math.cos(a * 4.0)) > 0.92 && r > 15.0 ? "gilded_blackstone" : "polished_blackstone";
                    } else {
                        b = (int)Math.floor(r) % 4 == 0 ? "chiseled_quartz_block" : "smooth_quartz";
                    }

                    this.set(x, 0, z, b);
                }
            }
        }

        for (int i = 0; i < 16; i++) {
            double a = (Math.PI * 2) * i / 16.0 + (Math.PI / 16);
            int x = (int)Math.round(Math.cos(a) * 21.0);
            int zx = (int)Math.round(Math.sin(a) * 21.0);
            if (i % 2 == 0) {
                this.set(x, 1, zx, "potted_azalea_bush");
                this.set(x, 0, zx, "polished_blackstone");
            } else {
                String f = facing(-Math.cos(a), -Math.sin(a));
                this.set(x, 1, zx, stairs("dark_oak", opposite(f), false));
            }
        }
    }

    private void canal() {
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                double r = dist(x, z);
                if (!(r < 25.5) && !(r > 31.5)) {
                    boolean bridge = this.onBridge(x, z);
                    if (r < 26.5 || r > 30.5) {
                        this.set(x, 0, z, "polished_andesite");
                        if (!bridge) {
                            this.set(x, 1, z, "stone_brick_wall");
                        }
                    } else if (bridge) {
                        this.set(x, 0, z, "polished_andesite");
                        this.set(x, -1, z, "stone_bricks");
                    } else {
                        this.set(x, 0, z, "water");
                        this.set(x, -1, z, "water");
                        this.set(x, -2, z, "prismarine_bricks");
                        if ((x * 7 + z * 13) % 11 == 0) {
                            this.set(x, -1, z, "sea_lantern");
                        }
                    }
                }
            }
        }

        for (int x = -32; x <= 32; x++) {
            for (int zx = -32; zx <= 32; zx++) {
                double r = dist(x, zx);
                if (r >= 25.5 && r <= 31.5 && this.onBridge(x, zx) && !this.onBridgeInner(x, zx)) {
                    this.set(x, 1, zx, "stone_brick_wall");
                }
            }
        }
    }

    private boolean onBridge(int x, int z) {
        for (CitadelDesign.Portal p : PORTALS) {
            if (perp(x, z, p.angle) <= 4.5 && angDiff(angle(x, z), p.angle) < 90.0) {
                return true;
            }
        }

        return perp(x, z, 90.0) <= 4.5 && z > 0;
    }

    private boolean onBridgeInner(int x, int z) {
        for (CitadelDesign.Portal p : PORTALS) {
            if (perp(x, z, p.angle) <= 3.5 && angDiff(angle(x, z), p.angle) < 90.0) {
                return true;
            }
        }

        return perp(x, z, 90.0) <= 3.5 && z > 0;
    }

    private void roads() {
        for (int x = -this.ri; x <= this.ri; x++) {
            for (int z = -this.ri; z <= this.ri; z++) {
                double r = dist(x, z);
                if (r >= 31.5 && r <= 37.0) {
                    this.set(x, 0, z, !(r > 36.0) && !(r < 32.5) ? "stone_bricks" : "polished_andesite");
                } else if (this.onAvenue(x, z, 3.5)) {
                    boolean edge = !this.onAvenue(x, z, 2.5);
                    this.set(x, 0, z, edge ? "polished_andesite" : ((int)r % 5 == 0 ? "chiseled_stone_bricks" : "stone_bricks"));
                } else if (r >= this.ri - 9 && r <= this.ri - 5) {
                    this.set(x, 0, z, "stone_bricks");
                }
            }
        }

        for (CitadelDesign.Portal p : PORTALS) {
            this.avenueLamps(p.angle, this.portalR - 12.0);
        }

        this.avenueLamps(90.0, this.ri - 10);

        for (int i = 0; i < 24; i++) {
            double a = (Math.PI * 2) * i / 24.0;
            int x = (int)Math.round(Math.cos(a) * 37.5);
            int zx = (int)Math.round(Math.sin(a) * 37.5);
            if (!this.onAvenue(x, zx, 4.5)) {
                this.lampPost(x, 1, zx, "polished_andesite", "stone_brick_wall", "lantern", 4);
            }
        }
    }

    private void avenueLamps(double deg, double to) {
        double a = Math.toRadians(deg);

        for (double r = 40.0; r < to; r += 7.0) {
            for (int s = -1; s <= 1; s += 2) {
                double ox = Math.cos(a) * r - Math.sin(a) * 4.5 * s;
                double oz = Math.sin(a) * r + Math.cos(a) * 4.5 * s;
                int x = (int)Math.round(ox);
                int z = (int)Math.round(oz);
                this.set(x, 0, z, "polished_andesite");
                this.lampPost(x, 1, z, "polished_andesite", "dark_oak_fence", "lantern", 4);
            }
        }
    }

    private void gardens() {
        String[] flowers = new String[]{
            "poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "azure_bluet", "red_tulip", "lily_of_the_valley", "pink_tulip"
        };

        for (int x = -this.ri + 10; x <= this.ri - 10; x++) {
            for (int z = -this.ri + 10; z <= this.ri - 10; z++) {
                double r = dist(x, z);
                if (!(r < 38.0) && !(r > this.ri - 10) && !this.onAvenue(x, z, 5.5) && !this.nearPortal(x, z, 14.0) && "grass_block".equals(this.get(x, 0, z))) {
                    if (!this.onAvenue(x, z, 6.5) && !(r < 39.5)) {
                        double n = noise(77L, x, z);
                        if (n > 0.7 && this.rnd.nextDouble() < 0.35) {
                            this.set(x, 1, z, flowers[this.rnd.nextInt(flowers.length)]);
                        } else if (this.rnd.nextDouble() < 0.06) {
                            this.set(x, 1, z, "short_grass");
                        }
                    } else {
                        this.set(x, 1, z, leaves("azalea"));
                    }
                }
            }
        }

        for (int i = 0; i < 160; i++) {
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double r = 44.0 + this.rnd.nextDouble() * (this.ri - 58);
            int x = (int)Math.round(Math.cos(a) * r);
            int zx = (int)Math.round(Math.sin(a) * r);
            if (!this.onAvenue(x, zx, 8.0)
                && !this.nearPortal(x, zx, 17.0)
                && "grass_block".equals(this.get(x, 0, zx))
                && (this.empty(x, 1, zx) || this.get(x, 1, zx).contains("grass") || isFlower(this.get(x, 1, zx)))) {
                int kind = this.rnd.nextInt(5);
                switch (kind) {
                    case 0:
                        this.tree(x, 1, zx, "oak_log", "oak", 5 + this.rnd.nextInt(3), 3.2);
                        break;
                    case 1:
                        this.tree(x, 1, zx, "birch_log", "birch", 6 + this.rnd.nextInt(2), 2.8);
                        break;
                    case 2:
                        this.tree(x, 1, zx, "cherry_log", "cherry", 5 + this.rnd.nextInt(2), 3.6);
                        break;
                    case 3:
                        this.conifer(x, 1, zx, "spruce_log", "spruce", 8 + this.rnd.nextInt(4));
                        break;
                    default:
                        this.tree(x, 1, zx, "dark_oak_log", "flowering_azalea", 4, 2.6);
                }
            }
        }
    }

    private static boolean isFlower(String b) {
        return b != null
            && (
                b.contains("tulip")
                    || b.contains("poppy")
                    || b.contains("dandelion")
                    || b.contains("cornflower")
                    || b.contains("allium")
                    || b.contains("daisy")
                    || b.contains("bluet")
                    || b.contains("lily")
            );
    }

    private boolean nearPortal(int x, int z, double d) {
        for (CitadelDesign.Portal p : PORTALS) {
            double a = Math.toRadians(p.angle);
            double px = Math.cos(a) * this.portalR;
            double pz = Math.sin(a) * this.portalR;
            if (Math.hypot(x - px, z - pz) < d) {
                return true;
            }
        }

        return false;
    }

    private void portal(CitadelDesign.Portal p) {
        double a = Math.toRadians(p.angle);
        double ux = Math.cos(a);
        double uz = Math.sin(a);
        double px = ux * this.portalR;
        double pz = uz * this.portalR;

        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (!(d > 11.5)) {
                    int wx = (int)Math.round(px) + x;
                    int wz = (int)Math.round(pz) + z;
                    this.set(wx, 0, wz, d > 10.5 ? p.accent : (d > 9.5 ? p.trim : ((x + z) % 2 == 0 ? "polished_deepslate" : "deepslate_tiles")));

                    for (int y = 1; y <= 3; y++) {
                        if (this.get(wx, y, wz) != null && !this.get(wx, y, wz).contains("lantern")) {
                            this.set(wx, y, wz, null);
                        }
                    }
                }
            }
        }

        double gx = px + ux * 5.0;
        double gz = pz + uz * 5.0;
        double vx = -uz;
        double vz = ux;
        int half = 6;
        int top = 15;

        for (int s = -half; s <= half; s++) {
            for (int depth = 0; depth <= 1; depth++) {
                int x = (int)Math.round(gx + vx * s + ux * depth);
                int zx = (int)Math.round(gz + vz * s + uz * depth);

                for (int yx = 1; yx <= top; yx++) {
                    boolean pillar = Math.abs(s) >= half - 1;
                    boolean lintel = yx >= top - 2;
                    double archTop = top - 3 - Math.pow((double)Math.abs(s) / (half - 1), 2.0) * 3.0;
                    boolean opening = !pillar && yx <= archTop;
                    if (!pillar && !lintel && opening) {
                        if (depth == 0) {
                            this.set(x, yx, zx, p.glass);
                        }
                    } else {
                        String b = pillar && yx % 4 == 0 ? p.trim : p.frame;
                        if (lintel && Math.abs(s) <= 1 && yx == top - 1) {
                            b = p.accent;
                        }

                        this.set(x, yx, zx, b);
                    }
                }

                this.set(x, top + 1, zx, Math.abs(s) % 2 == 0 ? slab(p.frame.contains("prismarine") ? "dark_prismarine" : "stone_brick", false) : p.accent);
            }
        }

        int kx = (int)Math.round(gx);
        int kz = (int)Math.round(gz);
        this.set(kx, top + 2, kz, p.accent);
        this.set(kx, top + 3, kz, "end_rod");

        for (int s = -1; s <= 1; s += 2) {
            int x = (int)Math.round(gx + vx * (half + 1) * s);
            int zx = (int)Math.round(gz + vz * (half + 1) * s);
            this.set(x, 1, zx, p.trim);
            this.set(x, 2, zx, p.frame);
            this.set(x, 3, zx, "lantern");
        }

        for (int s = -half + 1; s <= half - 1; s++) {
            int x = (int)Math.round(gx + vx * s - ux);
            int zx = (int)Math.round(gz + vz * s - uz);
            this.set(x, 1, zx, slab("polished_deepslate", false));
        }

        this.npc(px - ux * 2.0, 1.0, pz - uz * 2.0);
    }

    private void spire() {
        this.disc(0.0, 0.0, 1, 10.0, "polished_blackstone_bricks");
        this.disc(0.0, 0.0, 2, 8.6, "smooth_quartz");
        this.ring(0.0, 0.0, 1, 9.5, 10.4, "gilded_blackstone");

        for (int y = 3; y <= 10; y++) {
            this.ring(0.0, 0.0, y, 6.2, 7.3, y == 10 ? "gold_block" : (y % 3 == 0 ? "chiseled_quartz_block" : "quartz_bricks"));
        }

        this.disc(0.0, 0.0, 11, 7.6, "smooth_quartz_slab[type=bottom]");
        this.disc(0.0, 0.0, 11, 6.5, "smooth_quartz");

        for (int d = 0; d < 4; d++) {
            int dx = d == 0 ? 1 : (d == 2 ? -1 : 0);
            int dz = d == 1 ? 1 : (d == 3 ? -1 : 0);

            for (int w = -1; w <= 1; w++) {
                for (int y = 3; y <= (w == 0 ? 7 : 6); y++) {
                    for (int r = 5; r <= 8; r++) {
                        int x = dx * r + (dz != 0 ? w : 0);
                        int z = dz * r + (dx != 0 ? w : 0);
                        this.set(x, y, z, null);
                    }
                }
            }
        }

        this.set(0, 3, 0, "beacon");
        this.disc(0.0, 0.0, 2, 2.2, "gold_block");

        for (int y = 12; y <= 62; y++) {
            double r = 3.8 - (y - 12) * 0.032;
            String b = y % 7 == 0 ? "purple_stained_glass" : "quartz_pillar[axis=y]";
            this.disc(0.0, 0.0, y, r, b);
        }

        this.cone(0.0, 0.0, 63, 70, 2.4, "quartz_block");
        this.set(0, 71, 0, "lightning_rod");
        String[] mats = new String[]{"amethyst_block", "purple_stained_glass", "magenta_stained_glass"};

        for (int k = 0; k < 3; k++) {
            for (double y = 12.0; y <= 64.0; y += 0.25) {
                double a = y * 0.17 + k * Math.PI * 2.0 / 3.0;
                double r = 7.2 - (y - 12.0) * 0.045;

                for (double t = 0.0; t <= 1.2; t += 0.6) {
                    int x = (int)Math.round(Math.cos(a) * (r + t));
                    int z = (int)Math.round(Math.sin(a) * (r + t));
                    this.set(x, (int)Math.round(y), z, mats[k]);
                }
            }
        }

        for (int y = 74; y <= 75; y++) {
            this.ring(0.0, 0.0, y, 7.2, 8.4, y == 74 ? "gold_block" : "crying_obsidian");
        }

        this.sphere(0.0, 79.5, 0.0, 3.2, "amethyst_block");
        this.sphere(0.0, 79.5, 0.0, 1.6, "sea_lantern");
        this.set(0, 83, 0, "end_rod");
        this.set(0, 76, 0, "end_rod");

        for (int i = 0; i < 6; i++) {
            double a = (Math.PI * 2) * i / 6.0;
            int x = (int)Math.round(Math.cos(a) * 11.0);
            int z = (int)Math.round(Math.sin(a) * 11.0);
            this.set(x, 70 + i % 2 * 3, z, "amethyst_block");
            this.set(x, 71 + i % 2 * 3, z, "amethyst_cluster[facing=up]");
        }
    }

    private void outerWall() {
        double wr = this.ri - 3;

        for (int x = -this.ri; x <= this.ri; x++) {
            for (int z = -this.ri; z <= this.ri; z++) {
                double r = dist(x, z);
                if (!(r < wr - 0.7) && !(r > wr + 0.7) && (!(perp(x, z, 90.0) <= 4.5) || z <= 0)) {
                    for (int y = 1; y <= 4; y++) {
                        this.set(x, y, z, y == 4 ? "chiseled_stone_bricks" : "stone_bricks");
                    }

                    this.set(x, 5, z, "stone_brick_wall");
                }
            }
        }

        for (int i = 0; i < 12; i++) {
            double a = (Math.PI * 2) * i / 12.0 + (Math.PI / 12);
            int x = (int)Math.round(Math.cos(a) * wr);
            int zx = (int)Math.round(Math.sin(a) * wr);
            this.tower(x, zx);
        }

        for (int s = -1; s <= 1; s += 2) {
            this.tower(s * 7, (int)Math.round(Math.sqrt(wr * wr - 49.0)));
        }
    }

    private void tower(int x, int z) {
        this.cylinder(x, z, 1, 14, 3.6, "stone_bricks", true);
        this.disc(x, z, 0, 3.6, "stone_bricks");

        for (int y = 4; y <= 12; y += 4) {
            for (int d = 0; d < 4; d++) {
                int dx = d == 0 ? 3 : (d == 2 ? -3 : 0);
                int dz = d == 1 ? 3 : (d == 3 ? -3 : 0);
                this.set(x + dx, y, z + dz, "glass_pane");
            }
        }

        this.ring(x, z, 15, 3.0, 4.6, "stone_brick_slab[type=bottom]");
        this.disc(x, z, 15, 3.0, "stone_bricks");
        this.cone(x, z, 16, 24, 4.0, "deepslate_tiles");
        this.set(x, 25, z, "gold_block");
        this.set(x, 26, z, "lightning_rod");
        this.set(x, 13, z, "lantern");
    }

    private void islets() {
        int n = 9;

        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2) * i / n + this.rnd.nextDouble() * 0.3;
            double r = this.ri + 4 + this.rnd.nextDouble() * (this.R - this.ri - 12);
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            int y = -8 + this.rnd.nextInt(26);
            double size = 4.0 + this.rnd.nextDouble() * 4.0;
            this.island(x, y, z, size, "grass_block", "stone", i * 977L);
            if (size > 5.5) {
                this.tree(
                    x, y + 1, z, this.rnd.nextBoolean() ? "oak_log" : "cherry_log", this.rnd.nextBoolean() ? "oak" : "cherry", 4 + this.rnd.nextInt(2), 2.4
                );
            } else {
                this.set(x, y + 1, z, "lantern");
            }
        }
    }

    private record Portal(double angle, String frame, String accent, String glass, String trim) {
    }
}
