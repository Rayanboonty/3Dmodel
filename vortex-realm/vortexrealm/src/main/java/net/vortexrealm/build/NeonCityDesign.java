package net.vortexrealm.build;

import java.util.ArrayList;
import java.util.List;

public final class NeonCityDesign extends Voxels {
    private final List<NeonCityDesign.Tower> towers = new ArrayList<>();
    private final int plat;

    public NeonCityDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -14, 76, seed);
        this.plat = radius - 6;
        this.platform();
        this.avenues();
        this.plaza();
        this.vortex();
        this.placeTowers();

        for (NeonCityDesign.Tower t : this.towers) {
            this.tower(t);
        }

        this.skyBridges();
        this.planters();
        this.spawn = new double[]{0.5, 1.0, 22.5, 180.0};
        this.finish();
    }

    @Override
    public String name() {
        return "neon";
    }

    private static double dist(int x, int z) {
        return Math.sqrt(x * x + z * z);
    }

    private boolean onAvenue(int x, int z, int half) {
        return (Math.abs(x) <= half || Math.abs(z) <= half) && dist(x, z) > 15.0;
    }

    private void platform() {
        for (int x = -this.plat; x <= this.plat; x++) {
            for (int z = -this.plat; z <= this.plat; z++) {
                double d = dist(x, z);
                if (!(d > this.plat)) {
                    boolean line = Math.floorMod(x, 9) == 0 || Math.floorMod(z, 9) == 0;
                    boolean cross = Math.floorMod(x, 9) == 0 && Math.floorMod(z, 9) == 0;
                    this.set(x, 0, z, cross ? "sea_lantern" : (line ? "light_blue_concrete" : "polished_blackstone"));
                    int depth = (int)(2.0 + (1.0 - d / this.plat) * 9.0);

                    for (int k = 1; k <= depth; k++) {
                        this.set(x, -k, z, k == depth && d < this.plat - 2 ? "cyan_concrete" : "gray_concrete");
                    }

                    if (d > this.plat - 1.2) {
                        this.set(x, 1, z, "cyan_stained_glass_pane");
                        if ((x * 31 + z * 17) % 7 == 0) {
                            this.set(x, 0, z, "sea_lantern");
                        }
                    }
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            double a = (Math.PI * 2) * i / 8.0;
            int x = (int)Math.round(Math.cos(a) * this.plat * 0.6);
            int zx = (int)Math.round(Math.sin(a) * this.plat * 0.6);
            this.cone(x, zx, -13, -6, 2.5, "black_concrete");
            this.set(x, -14, zx, "sea_lantern");
        }
    }

    private void avenues() {
        for (int x = -this.plat; x <= this.plat; x++) {
            for (int z = -this.plat; z <= this.plat; z++) {
                if (!(dist(x, z) > this.plat - 1.5) && this.onAvenue(x, z, 4)) {
                    boolean centre = Math.abs(x) <= 4 && Math.abs(z) <= 4;
                    if (!centre) {
                        int across = Math.abs(x) <= 4 ? x : z;
                        int along = Math.abs(x) <= 4 ? z : x;
                        String b;
                        if (Math.abs(across) == 4) {
                            b = "gray_concrete";
                        } else if (across == 0 && Math.floorMod(along, 6) < 3) {
                            b = "white_concrete";
                        } else {
                            b = "black_concrete";
                        }

                        this.set(x, 0, z, b);
                    }
                }
            }
        }

        for (int r = 20; r < this.plat - 3; r += 8) {
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int s = -1; s <= 1; s += 2) {
                    int x = dir[0] * r + dir[1] * 6 * s;
                    int zx = dir[1] * r + dir[0] * 6 * s;
                    this.lampPost(x, 1, zx, "polished_blackstone", "polished_blackstone_wall", "sea_lantern", 4);
                    this.set(x, 6, zx, "end_rod");
                }
            }
        }
    }

    private void plaza() {
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                double d = dist(x, z);
                if (!(d > 16.0)) {
                    String b;
                    if (d > 15.0) {
                        b = "magenta_concrete";
                    } else if (d > 14.0) {
                        b = "sea_lantern";
                    } else if ((int)d == 9) {
                        b = "light_blue_concrete";
                    } else {
                        b = (Math.abs(x) + Math.abs(z)) % 2 == 0 ? "smooth_quartz" : "white_concrete";
                    }

                    this.set(x, 0, z, b);
                }
            }
        }

        for (int i = 0; i < 7; i++) {
            double a = (Math.PI * 2) * i / 7.0 - (Math.PI / 2) + (Math.PI / 7);
            this.npc(Math.cos(a) * 12.0, 1.0, Math.sin(a) * 12.0);
        }

        for (int i = 0; i < 8; i++) {
            double a = (Math.PI * 2) * i / 8.0 + (Math.PI / 8);
            int x = (int)Math.round(Math.cos(a) * 14.0);
            int zx = (int)Math.round(Math.sin(a) * 14.0);
            this.set(x, 1, zx, "quartz_stairs[facing=" + facing(Math.cos(a), Math.sin(a)) + ",half=bottom]");
        }
    }

    private void vortex() {
        this.disc(0.0, 0.0, 1, 3.5, "polished_blackstone");
        this.disc(0.0, 0.0, 2, 2.5, "black_concrete");
        this.disc(0.0, 0.0, 2, 1.2, "sea_lantern");

        for (int y = 5; y <= 26; y++) {
            double r = 1.5 + (y - 5) * 0.42;
            double twist = y * 0.45;
            int segs = (int)Math.max(8.0, r * 6.0);

            for (int s = 0; s < segs; s++) {
                double a = (Math.PI * 2) * s / segs;
                double rel = ((a - twist) % (Math.PI * 2) + (Math.PI * 2)) % (Math.PI * 2);
                if (!(rel < 1.1)) {
                    int x = (int)Math.round(Math.cos(a) * r);
                    int z = (int)Math.round(Math.sin(a) * r);
                    this.set(x, y, z, y % 2 == 0 ? "cyan_stained_glass" : "magenta_stained_glass");
                }
            }
        }

        this.sphere(0.0, 30.0, 0.0, 2.2, "purple_stained_glass");
        this.set(0, 30, 0, "sea_lantern");
    }

    private void placeTowers() {
        String[] strips = new String[]{"cyan_concrete", "magenta_concrete", "lime_concrete", "light_blue_concrete", "purple_concrete", "orange_concrete"};
        int tries = 0;

        while (this.towers.size() < 14 && tries++ < 400) {
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double r = 26.0 + this.rnd.nextDouble() * (this.plat - 38);
            int w = 7 + this.rnd.nextInt(3) * 2;
            int d = 7 + this.rnd.nextInt(3) * 2;
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            boolean ok = true;

            for (int cx2 = x - w / 2 - 2; cx2 <= x + w / 2 + 2 && ok; cx2++) {
                for (int cz2 = z - d / 2 - 2; cz2 <= z + d / 2 + 2 && ok; cz2++) {
                    if (this.onAvenue(cx2, cz2, 6) || dist(cx2, cz2) > this.plat - 4 || dist(cx2, cz2) < 19.0) {
                        ok = false;
                    }
                }
            }

            for (NeonCityDesign.Tower t : this.towers) {
                if (Math.abs(t.x - x) < (t.w + w) / 2 + 4 && Math.abs(t.z - z) < (t.d + d) / 2 + 4) {
                    ok = false;
                }
            }

            if (ok) {
                int h = 22 + this.rnd.nextInt(44);
                this.towers.add(new NeonCityDesign.Tower(x, z, w, d, h, strips[this.towers.size() % strips.length]));
            }
        }
    }

    private void tower(NeonCityDesign.Tower t) {
        int x0 = t.x - t.w / 2;
        int x1 = t.x + t.w / 2;
        int z0 = t.z - t.d / 2;
        int z1 = t.z + t.d / 2;
        String glass = this.rnd.nextBoolean() ? "light_gray_stained_glass" : "black_stained_glass";
        this.box(x0 - 1, 0, z0 - 1, x1 + 1, 0, z1 + 1, "polished_blackstone");

        for (int y = 1; y <= t.h; y++) {
            boolean band = y % 5 == 0;
            this.walls(x0, y, z0, x1, y, z1, band ? "gray_concrete" : glass);
            if (band) {
                this.box(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, "light_gray_concrete");
            }

            this.set(x0, y, z0, t.strip);
            this.set(x1, y, z0, t.strip);
            this.set(x0, y, z1, t.strip);
            this.set(x1, y, z1, t.strip);
        }

        String face = facing(-t.x, -t.z);
        int dx = face.equals("east") ? x1 : (face.equals("west") ? x0 : t.x);
        int dz = face.equals("south") ? z1 : (face.equals("north") ? z0 : t.z);

        for (int y = 1; y <= 3; y++) {
            this.set(dx, y, dz, null);
        }

        this.box(x0, t.h + 1, z0, x1, t.h + 1, z1, "black_concrete");
        this.walls(x0 - 1, t.h + 1, z0 - 1, x1 + 1, t.h + 1, z1 + 1, "sea_lantern");
        this.walls(x0, t.h + 2, z0, x1, t.h + 2, z1, t.strip.replace("concrete", "stained_glass_pane"));
        int sh = 3 + this.rnd.nextInt(6);

        for (int k = 2; k < 2 + sh; k++) {
            this.set(t.x, t.h + k, t.z, "iron_bars");
        }

        this.set(t.x, t.h + 2 + sh, t.z, "ochre_froglight");
        this.set(t.x, t.h + 3 + sh, t.z, "lightning_rod");
        if (t.h > 44) {
            int by = t.h - 14;

            for (int y = by; y < by + 6; y++) {
                for (int s = -2; s <= 2; s++) {
                    int bx = !face.equals("east") && !face.equals("west") ? t.x + s : dx + (face.equals("east") ? 1 : -1);
                    int bz = !face.equals("north") && !face.equals("south") ? t.z + s : dz + (face.equals("south") ? 1 : -1);
                    boolean edge = y == by || y == by + 5 || Math.abs(s) == 2;
                    this.set(bx, y, bz, edge ? "black_concrete" : ((y + s) % 2 == 0 ? "magenta_concrete" : "purple_concrete"));
                }
            }
        }
    }

    private void skyBridges() {
        for (int i = 0; i < this.towers.size(); i++) {
            for (int j = i + 1; j < this.towers.size(); j++) {
                NeonCityDesign.Tower a = this.towers.get(i);
                NeonCityDesign.Tower b = this.towers.get(j);
                double d = Math.hypot(a.x - b.x, a.z - b.z);
                int y = Math.min(a.h, b.h) - 6;
                if (!(d > 26.0) && y >= 14 && !(this.rnd.nextDouble() < 0.4)) {
                    y -= y % 5;
                    int n = (int)Math.ceil(d * 2.0);
                    double ux = (b.x - a.x) / d;
                    double uz = (b.z - a.z) / d;

                    for (int k = 0; k <= n; k++) {
                        double t = (double)k / n;
                        double px = a.x + (b.x - a.x) * t;
                        double pz = a.z + (b.z - a.z) * t;

                        for (int w = -1; w <= 1; w++) {
                            int x = (int)Math.round(px - uz * w);
                            int z = (int)Math.round(pz + ux * w);
                            if (this.empty(x, y, z)) {
                                this.set(x, y, z, "light_blue_stained_glass");
                            }
                        }

                        for (int s = -2; s <= 2; s += 4) {
                            int x = (int)Math.round(px - uz * s);
                            int z = (int)Math.round(pz + ux * s);
                            if (this.empty(x, y + 1, z)) {
                                this.set(x, y + 1, z, "glass_pane");
                            }
                        }
                    }
                }
            }
        }
    }

    private void planters() {
        for (int i = 0; i < 40; i++) {
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double r = 18.0 + this.rnd.nextDouble() * (this.plat - 22);
            int x = (int)Math.round(Math.cos(a) * r);
            int z = (int)Math.round(Math.sin(a) * r);
            if (!this.onAvenue(x, z, 6) && this.free(x, z, 3)) {
                this.box(x - 1, 1, z - 1, x + 1, 1, z + 1, "cyan_terracotta");
                this.set(x, 1, z, "moss_block");
                this.tree(x, 2, z, "dark_oak_log", this.rnd.nextBoolean() ? "azalea" : "cherry", 3, 2.2);
            }
        }
    }

    private boolean free(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!this.empty(x + dx, 1, z + dz)) {
                    return false;
                }

                String g = this.get(x + dx, 0, z + dz);
                if (g == null || g.contains("concrete") && !g.contains("light_blue") || g.contains("quartz")) {
                    return false;
                }
            }
        }

        return true;
    }

    private record Tower(int x, int z, int w, int d, int h, String strip) {
    }
}
