package net.vortexpvp.build;

/**
 * Vortex Isles: the flagship hub. A floating island with rolling hills and an eroded underside; in the middle the
 * Vortex Spire - an obsidian core wound by two rising ribbons of amethyst and purpur, floating rings around it and a
 * beacon of end rods at the top - on a stepped plaza with a sun-ray floor. Around the plaza eight domed shrines (one per
 * gamemode NPC) with columns, coloured roof bands and a pedestal; winding paths, a waterfall, big trees and islets.
 */
public final class VortexIslesDesign extends IslandVoxels {
    private static final int PLAZA = 20;
    private static final int SHRINES = 8;
    private static final double SHRINE_R = 33.0;
    private static final String[] COLORS = {"red", "orange", "yellow", "lime", "cyan", "light_blue", "purple", "magenta"};

    public VortexIslesDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, Math.max(80, radius), -54, 44, seed, PLAZA, Math.max(80, radius) - 16.0);
        for (int i = 0; i < SHRINES; i++) {
            double a = this.shrineAngle(i);
            this.pad((int)Math.round(Math.cos(a) * SHRINE_R), (int)Math.round(Math.sin(a) * SHRINE_R), 6, false);
        }

        this.ground();
        int e = (int)this.baseEdge;
        this.islets(new int[][]{{-e - 10, -e / 2, 6}, {e + 8, -e / 2 - 6, 5}, {-e / 2 - 8, e + 6, 7}, {e / 2 + 10, e + 4, 5}, {e + 12, 10, 4}});
        this.plazaFloor();
        this.ringRoad();
        for (int i = 0; i < SHRINES; i++) {
            double deg = Math.toDegrees(this.shrineAngle(i));
            this.path(deg, 2, PLAZA + 2, (int)SHRINE_R - 7);
        }

        this.path(90.0, 2, PLAZA + 2, e - 2);
        this.stream(112.5 + 22.5);
        this.spire();
        for (int i = 0; i < SHRINES; i++) {
            this.shrine(i);
        }

        this.trees(30, "oak", "cherry", "birch", "dark_oak", "spruce", "oak");
        this.flora();
        this.spawn = new double[]{0.5, 1.0, PLAZA - 3.5, 180.0};
        this.finish();
    }

    @Override
    public String name() {
        return "Vortex Isles";
    }

    /** The shrines sit around the plaza, leaving the south (the way in, +z) open. */
    private double shrineAngle(int i) {
        return Math.toRadians(90.0 + 360.0 / (SHRINES + 1) * (i + 1));
    }

    private void plazaFloor() {
        for (int x = -PLAZA - 2; x <= PLAZA + 2; x++) {
            for (int z = -PLAZA - 2; z <= PLAZA + 2; z++) {
                double d = dist(x, z);
                if (d > PLAZA + 1.4) {
                    continue;
                }

                String b;
                double a = Math.toDegrees(Math.atan2(z, x));
                if (d > PLAZA + 0.3) {
                    b = "polished_blackstone_bricks";
                } else if (d > PLAZA - 1.0) {
                    b = "chiseled_stone_bricks";
                } else if (d < 9.5) {
                    b = "polished_blackstone";
                } else if (Math.abs(d - 13.5) < 0.6) {
                    b = "purpur_block";
                } else {
                    // sun rays: alternating light and dark wedges, a vortex twist toward the rim
                    double twist = a + d * 4.0;
                    b = Math.floorMod((int)Math.round(twist / 11.25), 2) == 0 ? "smooth_quartz" : "polished_andesite";
                }

                this.set(x, 0, z, b);
            }
        }

        // three steps up to the spire's platform
        for (int step = 0; step < 3; step++) {
            double r = 9.0 - step * 1.4;
            this.disc(0, 0, step + 1, r, step == 2 ? "polished_blackstone_bricks" : "polished_deepslate");
            for (int a = 0; a < 360; a += 2) {
                double rad = Math.toRadians(a);
                this.set(px(r, a), step + 1, pz(r, a), stairs("polished_deepslate", facing(-Math.cos(rad), -Math.sin(rad)), false));
            }
        }

        // lamp posts around the rim
        for (int a = 0; a < 360; a += 30) {
            int x = px(PLAZA - 2.0, a);
            int z = pz(PLAZA - 2.0, a);
            this.set(x, 1, z, "polished_blackstone_bricks");
            this.set(x, 2, z, "polished_blackstone_wall");
            this.set(x, 3, z, "polished_blackstone_wall");
            this.set(x, 4, z, "end_rod");
        }
    }

    /** The Vortex Spire: an obsidian core with two helical ribbons, floating rings and a crown of end rods. */
    private void spire() {
        int base = 4;
        int height = 34;
        for (int y = base; y <= base + height; y++) {
            double t = (double)(y - base) / height;
            double coreR = 2.3 - t * 1.4;
            this.disc(0, 0, y, Math.max(0.6, coreR), y % 7 == 3 ? "crying_obsidian" : "obsidian");
            // two ribbons winding up in opposite phase
            for (int k = 0; k < 2; k++) {
                double ang = t * Math.PI * 5.0 + k * Math.PI;
                double rr = coreR + 1.6 + Math.sin(t * Math.PI) * 1.4;
                for (double w = -0.5; w <= 0.5; w += 0.5) {
                    int x = (int)Math.round(Math.cos(ang + w * 0.25) * rr);
                    int z = (int)Math.round(Math.sin(ang + w * 0.25) * rr);
                    this.set(x, y, z, k == 0 ? "amethyst_block" : "purpur_block");
                }
            }
        }

        // floating rings at three heights
        int[] ringY = {base + 9, base + 18, base + 26};
        double[] ringR = {7.5, 6.0, 4.5};
        for (int i = 0; i < ringY.length; i++) {
            this.ring(0, 0, ringY[i], ringR[i] - 0.7, ringR[i] + 0.5, i == 1 ? "purpur_block" : "quartz_bricks");
            for (int a = 0; a < 360; a += 45) {
                this.set(px(ringR[i], a), ringY[i] - 1, pz(ringR[i], a), "end_rod[facing=down]");
            }
        }

        // the crown
        int topY = base + height + 1;
        this.set(0, topY, 0, "beacon");
        this.set(0, topY - 1, 0, "diamond_block");
        for (int a = 0; a < 360; a += 45) {
            this.set(px(1.4, a), topY, pz(1.4, a), "end_rod");
        }

        this.set(0, topY + 1, 0, "end_rod");
    }

    /**
     * A domed gamemode shrine: a round stone base with steps, six columns, a coloured band under a dome of stairs and
     * slabs, a lantern hanging inside and a pedestal for the NPC facing the plaza.
     */
    private void shrine(int i) {
        double a = this.shrineAngle(i);
        int sx = (int)Math.round(Math.cos(a) * SHRINE_R);
        int sz = (int)Math.round(Math.sin(a) * SHRINE_R);
        String color = COLORS[i % COLORS.length];
        // base and steps
        this.disc(sx, sz, 0, 5.6, "polished_andesite");
        this.disc(sx, sz, 1, 4.6, "smooth_stone");
        for (int d = 0; d < 360; d += 3) {
            double rad = Math.toRadians(d);
            this.set(sx + px(5.0, d), 1, sz + pz(5.0, d), stairs("stone_brick", facing(-Math.cos(rad), -Math.sin(rad)), false));
        }

        for (int y = 2; y <= 12; y++) {
            this.disc(sx, sz, y, 4.4, null);
        }

        // six columns with capitals
        for (int c = 0; c < 6; c++) {
            double ca = a + Math.PI / 6 + c * Math.PI / 3;
            int x = sx + (int)Math.round(Math.cos(ca) * 3.6);
            int z = sz + (int)Math.round(Math.sin(ca) * 3.6);
            for (int y = 2; y <= 6; y++) {
                this.set(x, y, z, y == 6 ? "chiseled_quartz_block" : "quartz_pillar");
            }
        }

        // coloured band and the dome
        this.ring(sx, sz, 7, 3.0, 4.6, color + "_concrete");
        for (int y = 8; y <= 10; y++) {
            double r = 4.4 * Math.cos(Math.toRadians((y - 7) * 24.0));
            this.ring(sx, sz, y, Math.max(0.0, r - 1.1), r, "smooth_quartz");
        }

        this.disc(sx, sz, 11, 1.6, "smooth_quartz_slab[type=bottom]");

        this.set(sx, 12, sz, "end_rod");
        this.set(sx, 6, sz, "lantern[hanging=true]");
        this.set(sx, 7, sz, "iron_chain");
        // the NPC stands on a pedestal on the plaza side, facing the spire
        int nx = sx - (int)Math.round(Math.cos(a) * 1.5);
        int nz = sz - (int)Math.round(Math.sin(a) * 1.5);
        this.set(nx, 1, nz, "chiseled_stone_bricks");
        this.npc(nx, 2.0, nz);
        // flowers in front of each shrine
        for (int s = -1; s <= 1; s += 2) {
            int fx = sx - (int)Math.round(Math.cos(a) * 6.0 - Math.sin(a) * 3.0 * s);
            int fz = sz - (int)Math.round(Math.sin(a) * 6.0 + Math.cos(a) * 3.0 * s);
            this.set(fx, 1, fz, "flowering_azalea_leaves[persistent=true]");
        }
    }
}
