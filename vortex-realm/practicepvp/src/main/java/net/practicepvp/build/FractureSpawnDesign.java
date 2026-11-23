package net.practicepvp.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Fracture: the C MCPvP spawn. A floating island about 350 blocks across, split in two by a glowing chasm and held
 * together by three bridges.
 *
 * <ul>
 *   <li>South half: the Crown Plaza (white marble, crimson rays, gold rings) with the glowing heart of the fracture (a
 *   low cracked amethyst geode, the middle open to the sky) and eight domed queue pavilions, one per kit colour; the Arrival square (spawn) with its crowned gatehouse; the FFA Colosseum (east), the Training Yard (west),
 *   the Parkour Spire (south-west) and the Tournament Stage (south-east).</li>
 *   <li>North half: the Hall of Champions (colonnade, spectator balcony, crowned cupola, podiums), the Kit Forge, the
 *   Cosmetic Spire and the mountains, with streams pouring into the chasm as waterfalls.</li>
 *   <li>Everywhere: meadows in crimson and gold flowers, forests and groves, snowy peaks, boulders, crystal shards on
 *   the chasm's lips, rocks floating up out of the crack, road lamps, benches, floating islets and an eroded underside
 *   with glowing veins down the chasm walls.</li>
 * </ul>
 */
public final class FractureSpawnDesign extends CastleKit {
    private static final int RAD = 176;
    private static final double EDGE = 154.0;
    private static final int PLAZA = 34;
    private static final double PAV_R = 46.0;
    /** Pavilion angles in degrees (0 = east, 90 = south): the four roads leave between them. */
    private static final double[] PAV_ANGLES = {22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5};
    private static final String[] KIT_COLORS = {"red", "orange", "yellow", "lime", "cyan", "light_blue", "purple", "magenta"};

    private static final int[] HALL = {0, -106};
    private static final int[] FORGE = {74, -100};
    private static final int[] BOUTIQUE = {-74, -100};
    private static final int[] FFA = {100, 0};
    private static final int[] TRAINING = {-100, 0};
    private static final int[] ARRIVAL = {0, 100};
    private static final int[] PARKOUR = {-70, 84};
    private static final int[] STAGE = {70, 84};
    /** Ponds in the southern meadows: {x, z, radius}. */
    private static final int[][] PONDS = {{-46, 60, 9}, {40, 60, 8}};
    /** Where the three bridges cross the chasm (x), so nothing else is put there. */
    private static final int[] BRIDGE_X = {-75, 0, 75};

    private final long s;
    private final int[][] top;
    private final double[][] edge;
    /** Flat areas: {x, z, rx, rz, kind} with kind 0 = circle (radius rx), 1 = rectangle (half sizes rx, rz). */
    private final List<double[]> flats = new ArrayList<>();
    /** Road corridors: {x0, z0, x1, z1, halfWidth}. */
    private final List<double[]> lanes = new ArrayList<>();
    /** The island's south edge on the arrival axis, where the lookout juts out. */
    private int lookZ;

    public FractureSpawnDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, RAD, -78, 92, seed);
        this.s = seed;
        int n = 2 * RAD + 1;
        this.top = new int[n][n];
        this.edge = new double[n][n];
        this.layout();
        this.heights();
        this.terrain();
        this.underside();
        this.roads();
        this.plaza();
        this.monument();
        for (int i = 0; i < PAV_ANGLES.length; i++) {
            this.pavilion(i);
        }

        this.bridges();
        this.hall();
        this.colosseum();
        this.training();
        this.boutique();
        this.forge();
        this.stage();
        this.parkour();
        this.arrival();
        this.lookout();
        this.ponds();
        this.waterfalls();
        this.shards();
        this.nature();
        this.debris();
        this.islets();
        this.spots();
        this.finish();
    }

    @Override
    public String name() {
        return "The Fracture";
    }

    // ------------------------------------------------------------------ geometry

    private static double smooth01(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double segDist(double px, double pz, double x0, double z0, double x1, double z1) {
        double vx = x1 - x0;
        double vz = z1 - z0;
        double len2 = vx * vx + vz * vz;
        double t = len2 < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((px - x0) * vx + (pz - z0) * vz) / len2));
        double dx = px - (x0 + vx * t);
        double dz = pz - (z0 + vz * t);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Centre line of the chasm (its z for a given x): straight under the hall, bending south toward the rims. */
    private double crackZ(double x) {
        return -68.0 + 6.0 * (1.0 - Math.cos(x / 70.0)) + 1.6 * Math.sin(x / 13.0 + 1.1) + 0.9 * Math.sin(x / 5.3);
    }

    /** Signed distance to the chasm's lip: negative inside the gap (about 17 blocks wide). */
    private double crack(double x, double z) {
        double half = 8.5 + 1.5 * (noise(this.s + 91L, x * 0.8, 3.0) - 0.5) * 2.0 + 0.8 * Math.sin(x / 23.0);
        double jag = (noise(this.s + 93L, x * 2.3, z * 2.3) - 0.5) * 2.4;
        return Math.abs(z - this.crackZ(x)) - half + jag;
    }

    private double islandEdge(double x, double z) {
        double a = Math.atan2(z, x);
        double e = EDGE + (noise(this.s + 5L, Math.cos(a) * 30.0, Math.sin(a) * 30.0) - 0.5) * 40.0 + Math.max(0.0, -Math.sin(a)) * 8.0;
        return Math.min(RAD - 3, e);
    }

    private int h(int x, int z) {
        return x >= -RAD && x <= RAD && z >= -RAD && z <= RAD ? this.top[x + RAD][z + RAD] : Integer.MIN_VALUE;
    }

    private boolean land(int x, int z) {
        return this.h(x, z) != Integer.MIN_VALUE;
    }

    /** Distance from (x, z) to the nearest flat area or road (negative inside one). */
    private double flatDist(double x, double z) {
        double best = 1.0E9;
        for (double[] f : this.flats) {
            double d;
            if (f[4] == 1.0) {
                double qx = Math.abs(x - f[0]) - f[2];
                double qz = Math.abs(z - f[1]) - f[3];
                d = qx > 0.0 || qz > 0.0 ? Math.sqrt(Math.max(qx, 0.0) * Math.max(qx, 0.0) + Math.max(qz, 0.0) * Math.max(qz, 0.0)) : Math.max(qx, qz);
            } else {
                d = dist(x - f[0], z - f[1]) - f[2];
            }

            best = Math.min(best, d);
        }

        for (double[] l : this.lanes) {
            best = Math.min(best, segDist(x, z, l[0], l[1], l[2], l[3]) - l[4]);
        }

        return best;
    }

    private void circle(double x, double z, double r) {
        this.flats.add(new double[]{x, z, r, r, 0.0});
    }

    private void rect(double x0, double z0, double x1, double z1) {
        this.flats.add(new double[]{(x0 + x1) / 2.0, (z0 + z1) / 2.0, Math.abs(x1 - x0) / 2.0, Math.abs(z1 - z0) / 2.0, 1.0});
    }

    private void lane(double x0, double z0, double x1, double z1, double half) {
        this.lanes.add(new double[]{x0, z0, x1, z1, half});
    }

    private static double ax(double deg, double r) {
        return Math.cos(Math.toRadians(deg)) * r;
    }

    private static double az(double deg, double r) {
        return Math.sin(Math.toRadians(deg)) * r;
    }

    private boolean nearBridge(int x, int gap) {
        for (int b : BRIDGE_X) {
            if (Math.abs(x - b) < gap) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------ ground

    private void layout() {
        this.circle(0, 0, PLAZA + 1);
        for (double a : PAV_ANGLES) {
            this.circle(ax(a, PAV_R), az(a, PAV_R), 8);
        }

        this.rect(HALL[0] - 34, HALL[1] - 20, HALL[0] + 34, HALL[1] + 21);
        this.circle(FORGE[0], FORGE[1], 17);
        this.circle(BOUTIQUE[0], BOUTIQUE[1], 17);
        this.circle(FFA[0], FFA[1], 28);
        this.rect(TRAINING[0] - 24, TRAINING[1] - 20, TRAINING[0] + 24, TRAINING[1] + 36);
        this.rect(ARRIVAL[0] - 17, ARRIVAL[1] - 17, ARRIVAL[0] + 17, ARRIVAL[1] + 17);
        this.circle(PARKOUR[0], PARKOUR[1], 16);
        this.circle(STAGE[0], STAGE[1], 25);
        for (int[] p : PONDS) {
            this.circle(p[0], p[1], p[2] + 5);
        }

        this.lookZ = (int)Math.floor(this.islandEdge(0, 1)) - 1;
        this.circle(0, this.lookZ - 6, 10);
        this.lane(0, ARRIVAL[1] + 17, 0, this.lookZ - 12, 3);
        // the four roads out of the plaza
        this.lane(0, PLAZA, 0, ARRIVAL[1] - 17, 4);
        this.lane(0, -PLAZA, 0, HALL[1] + 18, 4);
        this.lane(PLAZA, 0, FFA[0] - 22, 0, 3);
        this.lane(-PLAZA, 0, TRAINING[0] + 20, 0, 3);
        // east and west bridges, from the side roads to the forge and the spire
        this.lane(68, -3, FORGE[0] + 4, FORGE[1] + 17, 3);
        this.lane(-68, -3, BOUTIQUE[0] - 4, BOUTIQUE[1] + 17, 3);
        // north side: hall to forge and spire
        this.lane(HALL[0] + 34, HALL[1] + 4, FORGE[0] - 16, FORGE[1], 3);
        this.lane(HALL[0] - 34, HALL[1] + 4, BOUTIQUE[0] + 16, BOUTIQUE[1], 3);
        // south side: arrival to parkour and stage
        this.lane(ARRIVAL[0] - 17, ARRIVAL[1] - 2, PARKOUR[0] + 15, PARKOUR[1] + 3, 3);
        this.lane(ARRIVAL[0] + 17, ARRIVAL[1] - 2, STAGE[0] - 24, STAGE[1] + 4, 3);
    }

    private void heights() {
        for (int x = -RAD; x <= RAD; x++) {
            for (int z = -RAD; z <= RAD; z++) {
                double d = dist(x, z);
                double e = this.islandEdge(x, z);
                this.edge[x + RAD][z + RAD] = e;
                double c = this.crack(x, z);
                if (d > e || c < 0.0) {
                    this.top[x + RAD][z + RAD] = Integer.MIN_VALUE;
                    continue;
                }

                double a = Math.atan2(z, x);
                double northness = (1.0 - Math.sin(a)) / 2.0;
                double hills = noise(this.s + 21L, x * 1.1, z * 1.1) * 9.0 + noise(this.s + 23L, x * 3.0, z * 3.0) * 2.5 - 3.5;
                double ridge = 1.0 - Math.abs(noise(this.s + 25L, x * 0.75, z * 0.75) * 2.0 - 1.0);
                // mountains: the far north, and a band of peaks along the rim (high in the north, low in the south)
                double north = smooth01((-z - 118.0) / 34.0);
                double rimBand = smooth01((d - (e - 44.0)) / 30.0) * (0.3 + 0.7 * northness);
                double mount = Math.max(north, rimBand) * (14.0 + ridge * 30.0 + noise(this.s + 27L, x * 2.0, z * 2.0) * 6.0);
                double hgt = Math.max(0.0, hills) + mount;
                // the peaks settle before the rim, so the edge is a cliff and not a wall; a dip toward the chasm's lip
                hgt *= 1.0 - 0.55 * smooth01((d - (e - 16.0)) / 16.0);
                hgt -= Math.max(0.0, d - (e - 5.0)) * 2.0;
                hgt -= Math.max(0.0, 5.0 - c) * 0.6;
                // districts and roads are flat at 0 and blend into the land around them
                double fd = this.flatDist(x, z);
                if (fd <= 0.0) {
                    hgt = 0.0;
                } else if (fd < 12.0) {
                    hgt *= smooth01(fd / 12.0);
                }

                this.top[x + RAD][z + RAD] = Math.max(-3, (int)Math.round(hgt));
            }
        }
    }

    private void terrain() {
        for (int x = -RAD; x <= RAD; x++) {
            for (int z = -RAD; z <= RAD; z++) {
                int t = this.h(x, z);
                if (t == Integer.MIN_VALUE) {
                    continue;
                }

                int drop = 0;
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nb = this.h(x + o[0], z + o[1]);
                    drop = Math.max(drop, nb == Integer.MIN_VALUE ? 0 : t - nb);
                }

                double n = noise(this.s + 31L, x * 2.0, z * 2.0);
                double c = this.crack(x, z);
                for (int y = -4; y <= t; y++) {
                    String b;
                    if (y == t) {
                        if (t >= 34 && drop < 4) {
                            b = "snow_block";
                        } else if (drop >= 3) {
                            b = n > 0.55 ? "andesite" : (n < 0.3 ? "tuff" : "stone");
                        } else if (c < 2.5) {
                            // the lip of the chasm: a cracked dark crust with embers in it
                            double r = this.hash3(901L, x, 0, z);
                            b = r < 0.06 ? "shroomlight" : (r < 0.4 ? "cobbled_deepslate" : (r < 0.65 ? "tuff" : "blackstone"));
                        } else if (c < 4.5 && this.hash3(903L, x, 0, z) < 0.5) {
                            b = this.hash3(905L, x, 0, z) < 0.5 ? "coarse_dirt" : "tuff";
                        } else if (drop == 2) {
                            b = n > 0.5 ? "coarse_dirt" : "moss_block";
                        } else {
                            b = n > 0.76 ? "moss_block" : (n < 0.16 ? "podzol" : (n > 0.7 ? "coarse_dirt" : "grass_block"));
                        }
                    } else if (t - y <= 2 && drop < 3) {
                        b = t >= 34 ? "snow_block" : "dirt";
                    } else if (t - y <= 2 && this.hash3(953L, x, y, z) < 0.3) {
                        b = t >= 34 ? "snow_block" : "mossy_cobblestone";
                    } else {
                        b = this.rock(x, y, z);
                    }

                    this.set(x, y, z, b);
                }

                if (t >= 38 && drop < 3 && this.hash3(907L, x, t, z) < 0.5) {
                    this.set(x, t + 1, z, "snow[layers=" + (1 + (int)(this.hash3(909L, x, t, z) * 3)) + "]");
                }
            }
        }
    }

    /** Layered rock for cliff faces: wavy bands of stone, andesite, tuff and pale diorite, darker deeper down. */
    private String rock(int x, int y, int z) {
        double w = noise(this.s + 39L, x * 1.5, z * 1.5) * 5.0;
        int band = Math.floorMod((int)Math.floor((y + w) / 3.0), 8);
        double r = this.hash3(951L, x, y, z);
        boolean deep = y < -24 + (int)(w * 2.0);
        if (r < 0.05) {
            return deep ? "cobbled_deepslate" : "cobblestone";
        } else if (r < 0.09) {
            return deep ? "deepslate" : "andesite";
        }

        return switch (band) {
            case 0, 4 -> deep ? "deepslate" : "stone";
            case 1 -> deep ? "cobbled_deepslate" : "andesite";
            case 2 -> "stone";
            case 3 -> "tuff";
            case 5 -> deep ? "tuff" : "diorite";
            case 6 -> deep ? "deepslate" : "stone";
            default -> deep ? "polished_deepslate" : "andesite";
        };
    }

    /** The eroded underside, and the chasm walls with their glowing veins. */
    private void underside() {
        for (int x = -RAD; x <= RAD; x++) {
            for (int z = -RAD; z <= RAD; z++) {
                if (!this.land(x, z)) {
                    continue;
                }

                double e = this.edge[x + RAD][z + RAD];
                double f = Math.max(0.0, 1.0 - dist(x, z) / e);
                double c = this.crack(x, z);
                double spikes = Math.pow(noise(this.s + 11L, x * 2.6, z * 2.6), 3.0) * 44.0;
                double dd = 5.0 + Math.pow(f, 0.8) * 40.0 * (0.6 + 0.4 * noise(this.s + 14L, x * 1.4, z * 1.4)) + spikes * Math.sqrt(f);
                // the chasm shows deep walls
                if (c < 10.0) {
                    dd = Math.max(dd, 34.0 + 10.0 * noise(this.s + 15L, x * 1.2, z * 1.2) - c * 1.5);
                }

                int deep = Math.min(-this.yLo - 4, (int)Math.round(dd));
                boolean wall = c < 4.0;
                for (int k = 5; k <= deep; k++) {
                    double rel = (double)k / Math.max(1, deep);
                    double v = noise(this.s + 18L, x * 2.4 + k * 1.7, z * 2.4 - k);
                    String b;
                    if (wall) {
                        double vein = Math.abs(noise(this.s + 71L, x * 0.9 + k * 0.33, z * 0.9 - k * 0.21) - 0.5);
                        double r = this.hash3(911L, x, -k, z);
                        if (vein < 0.022) {
                            b = r < 0.5 ? "shroomlight" : (r < 0.8 ? "crying_obsidian" : "magma_block");
                        } else if (vein < 0.04) {
                            b = r < 0.5 ? "amethyst_block" : "obsidian";
                        } else {
                            b = v > 0.62 ? "deepslate" : (v < 0.25 ? "blackstone" : (v < 0.36 ? "tuff" : "cobbled_deepslate"));
                        }
                    } else {
                        b = rel < 0.12 ? (v > 0.5 ? "dirt" : "coarse_dirt")
                            : (rel < 0.85 ? this.rock(x, -k, z) : (v > 0.5 ? "cobbled_deepslate" : "deepslate"));
                    }

                    this.set(x, -k, z, b);
                }

                if (deep < 12 && this.hash3(913L, x, 0, z) < 0.14) {
                    int len = 1 + (int)(this.hash3(915L, x, 0, z) * 3);
                    for (int i = 1; i <= len; i++) {
                        this.set(x, -deep - i, z, "hanging_roots");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ roads

    private void roads() {
        for (double[] l : this.lanes) {
            double len = dist(l[2] - l[0], l[3] - l[1]);
            double ux = (l[2] - l[0]) / len;
            double uz = (l[3] - l[1]) / len;
            double nx = -uz;
            double nz = ux;
            double half = l[4];
            for (double t = 0.0; t <= len; t += 0.5) {
                for (double sd = -half; sd <= half; sd += 0.5) {
                    int x = (int)Math.round(l[0] + ux * t + nx * sd);
                    int z = (int)Math.round(l[1] + uz * t + nz * sd);
                    if (!this.land(x, z) || this.h(x, z) != 0) {
                        continue;
                    }

                    boolean side = Math.abs(sd) > half - 0.6;
                    boolean mid = half >= 4 && Math.abs(sd) < 0.6;
                    String b = side ? "polished_blackstone_bricks"
                        : (mid ? "red_nether_bricks" : this.texture(201L, x, z, "polished_andesite", "andesite", "stone_bricks", "cracked_stone_bricks", "polished_andesite"));
                    this.set(x, 0, z, b);
                }
            }

            // lamps on both sides every 12 blocks
            for (double t = 6.0; t < len - 4.0; t += 12.0) {
                for (int side = -1; side <= 1; side += 2) {
                    int x = (int)Math.round(l[0] + ux * t + nx * (half + 1.5) * side);
                    int z = (int)Math.round(l[1] + uz * t + nz * (half + 1.5) * side);
                    if (this.land(x, z) && this.h(x, z) == 0 && this.empty(x, 1, z) && this.crack(x, z) > 3.0) {
                        this.roadLamp(x, 1, z);
                    }
                }
            }
        }
    }

    private void roadLamp(int x, int y, int z) {
        this.set(x, y, z, "polished_blackstone");
        for (int i = 1; i <= 3; i++) {
            this.set(x, y + i, z, "polished_blackstone_brick_wall");
        }

        this.set(x, y + 4, z, "chiseled_polished_blackstone");
        this.set(x, y + 5, z, "lantern[hanging=false]");
    }

    // ------------------------------------------------------------------ the Crown Plaza

    private void plaza() {
        for (int x = -PLAZA - 1; x <= PLAZA + 1; x++) {
            for (int z = -PLAZA - 1; z <= PLAZA + 1; z++) {
                double d = dist(x, z);
                if (d > PLAZA + 0.5) {
                    continue;
                }

                double ang = Math.atan2(z, x);
                String b;
                if (d > PLAZA - 1.0) {
                    b = "polished_blackstone_bricks";
                } else if (d > PLAZA - 2.0) {
                    b = Math.floorMod((int)Math.round(Math.toDegrees(ang) / 4.0), 2) == 0 ? "gold_block" : "polished_blackstone";
                } else if (d > PLAZA - 7.0) {
                    int seg = (int)Math.floor(Math.toDegrees(ang + Math.PI) / 11.25);
                    b = Math.floorMod(seg, 2) == 0 ? "polished_blackstone_bricks" : "polished_deepslate";
                    if (d > PLAZA - 4.6 && d < PLAZA - 3.4 && Math.floorMod(seg, 4) == 0) {
                        b = "red_nether_bricks";
                    }
                } else if (d > PLAZA - 8.0) {
                    b = "gold_block";
                } else if (d < 9.0) {
                    b = "polished_blackstone_bricks";
                } else if (d < 10.0) {
                    b = "gold_block";
                } else {
                    // white marble with sixteen crimson rays and a dark ring halfway out
                    double ray = Math.abs(Math.sin(ang * 8.0));
                    if (ray > 0.93 - (d - 10.0) * 0.004) {
                        b = d > 17.6 && d < 19.4 ? "gold_block" : "red_nether_bricks";
                    } else if (Math.abs(d - 18.5) < 0.6) {
                        b = "polished_blackstone";
                    } else {
                        b = this.texture(211L, x, z, "calcite", "polished_diorite", "smooth_quartz", "diorite", "calcite");
                    }
                }

                this.set(x, 0, z, b);
            }
        }

        // lamps and planters with benches around the rim, leaving the exits open
        for (int k = 0; k < 32; k++) {
            double deg = k * 11.25 + 5.625;
            if (this.nearExit(deg, 8.0)) {
                continue;
            }

            int x = (int)Math.round(ax(deg, PLAZA - 1.5));
            int z = (int)Math.round(az(deg, PLAZA - 1.5));
            if (k % 2 == 0) {
                this.plazaLamp(x, 1, z);
            } else {
                this.planter(x, z, facing(-ax(deg, 1), -az(deg, 1)));
            }
        }

        // an inner ring of flower beds around the monument, between the rays
        for (int k = 0; k < 8; k++) {
            double deg = k * 45.0 + 22.5;
            int x = (int)Math.round(ax(deg, 14.5));
            int z = (int)Math.round(az(deg, 14.5));
            this.flowerBed(x - 2, z - 2, x + 2, z + 2, 1, "polished_blackstone_brick", "moss_block",
                "red_tulip", "poppy", "orange_tulip", "dandelion", "torchflower");
        }
    }

    /** Is this rim angle where a road or a pavilion path leaves the plaza? */
    private boolean nearExit(double deg, double tol) {
        for (double e : new double[]{0, 90, 180, 270}) {
            if (Math.abs(((deg - e) % 360 + 540) % 360 - 180) < tol) {
                return true;
            }
        }

        for (double e : PAV_ANGLES) {
            if (Math.abs(((deg - e) % 360 + 540) % 360 - 180) < tol * 0.6) {
                return true;
            }
        }

        return false;
    }

    private void plazaLamp(int x, int y, int z) {
        this.set(x, y, z, "polished_blackstone_bricks");
        this.set(x, y + 1, z, "polished_blackstone_brick_wall");
        this.set(x, y + 2, z, "polished_blackstone_brick_wall");
        this.set(x, y + 3, z, "polished_blackstone_brick_wall");
        this.set(x, y + 4, z, "gold_block");
        this.set(x, y + 5, z, "ochre_froglight");
        this.set(x, y + 6, z, "polished_blackstone_slab[type=bottom]");
    }

    /** A planter with crimson and gold flowers and a bench in front of it. */
    private void planter(int x, int z, String face) {
        this.set(x, 1, z, "polished_blackstone_bricks");
        this.set(x, 2, z, "moss_block");
        this.set(x, 3, z, this.hash3(221L, x, 0, z) < 0.5 ? "red_tulip" : "orange_tulip");
        String side = face.equals("north") || face.equals("south") ? "east" : "south";
        for (int k = -1; k <= 1; k++) {
            int fx = x + dx(face) + dx(side) * k;
            int fz = z + dz(face) + dz(side) * k;
            this.set(fx, 1, fz, stairs("dark_oak", opposite(face), false));
        }
    }

    // ------------------------------------------------------------------ the heart of the fracture

    private void monument() {
        // stepped pedestal
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double d = dist(x, z);
                if (d <= 8.4) {
                    this.set(x, 1, z, d > 7.5 ? stairs("polished_blackstone_brick", facing(-x, -z), false) : "polished_blackstone_bricks");
                }

                if (d <= 6.6) {
                    this.set(x, 2, z, d > 5.8 ? stairs("polished_blackstone_brick", facing(-x, -z), false) : (d > 5.0 ? "gilded_blackstone" : "polished_blackstone"));
                }

                if (d <= 4.8) {
                    this.set(x, 3, z, "chiseled_polished_blackstone");
                }
            }
        }

        // glowing cracks in the floor running away from the blade
        for (int i = 0; i < 9; i++) {
            double rad = Math.toRadians(i * 40.0 + this.hash3(231L, i, 0, 0) * 20.0);
            double wob = 0.0;
            double end = 17.0 + this.hash3(233L, i, 0, 0) * 9.0;
            for (double r = 8.6; r < end; r += 0.7) {
                wob += (this.hash3(235L, i, (int)(r * 10), 0) - 0.5) * 0.18;
                int x = (int)Math.round(Math.cos(rad + wob) * r);
                int z = (int)Math.round(Math.sin(rad + wob) * r);
                this.set(x, 0, z, r < 12 || this.hash3(237L, x, 0, z) < 0.6 ? "shroomlight" : "gilded_blackstone");
            }
        }

        // the heart of the fracture: a low cracked geode on the pedestal (no tower: the middle stays open to the sky)
        for (int x = -7; x <= 7; x++) {
            for (int y = 3; y <= 7; y++) {
                for (int z = -7; z <= 7; z++) {
                    double q = (x * x) / 30.0 + ((y - 3.0) * (y - 3.0)) / 16.0 + (z * z) / 30.0;
                    if (q > 1.0) {
                        continue;
                    }

                    double r = this.hash3(241L, x, y, z);
                    String b;
                    if (q > 0.62) {
                        b = r < 0.12 ? "shroomlight" : (r < 0.55 ? "calcite" : "smooth_basalt");
                    } else {
                        b = r < 0.2 ? "budding_amethyst" : "amethyst_block";
                    }

                    this.set(x, y, z, b);
                }
            }
        }

        // short crystal shards breaking out of it
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(i * 36.0 + 11.0);
            double tilt = 0.45 + this.hash3(245L, i, 0, 0) * 0.3;
            int len = 2 + (int)(this.hash3(247L, i, 0, 0) * 3);
            double x = Math.cos(a) * 3.5;
            double z = Math.sin(a) * 3.5;
            double y = 5.0;
            for (int k = 0; k < len; k++) {
                x += Math.cos(a) * (1.0 - tilt) * 1.2;
                z += Math.sin(a) * (1.0 - tilt) * 1.2;
                y += tilt;
                this.set((int)Math.round(x), (int)Math.round(y), (int)Math.round(z), k == len - 1 ? "amethyst_cluster[facing=up]" : "amethyst_block");
            }
        }

        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5);
            this.set((int)Math.round(Math.cos(a) * 11), 1, (int)Math.round(Math.sin(a) * 11), "end_rod[facing=up]");
        }
    }

    // ------------------------------------------------------------------ queue pavilions

    private void pavilion(int i) {
        double a = PAV_ANGLES[i];
        int px = (int)Math.round(ax(a, PAV_R));
        int pz = (int)Math.round(az(a, PAV_R));
        String col = KIT_COLORS[i];
        // path from the plaza rim
        for (double r = PLAZA; r <= PAV_R - 5; r += 0.5) {
            for (double w = -2; w <= 2; w += 0.5) {
                int x = (int)Math.round(ax(a, r) + ax(a + 90, w));
                int z = (int)Math.round(az(a, r) + az(a + 90, w));
                if (this.land(x, z)) {
                    this.set(x, 0, z, Math.abs(w) > 1.6 ? "polished_blackstone_bricks" : this.texture(251L, x, z, "calcite", "polished_diorite"));
                }
            }
        }

        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double d = dist(x, z);
                int wx = px + x;
                int wz = pz + z;
                if (d <= 6.6) {
                    this.set(wx, 0, wz, "polished_blackstone_bricks");
                }

                if (d <= 5.6) {
                    this.set(wx, 1, wz, d > 4.8 ? stairs("polished_blackstone_brick", facing(-x, -z), false)
                        : (d < 1.5 ? "gold_block" : (d < 2.6 ? col + "_glazed_terracotta" : "smooth_quartz")));
                }
            }
        }

        // eight white columns with banners in the kit colour
        for (int k = 0; k < 8; k++) {
            double ca = Math.toRadians(22.5 + k * 45);
            int x = px + (int)Math.round(Math.cos(ca) * 4.3);
            int z = pz + (int)Math.round(Math.sin(ca) * 4.3);
            this.set(x, 2, z, "chiseled_quartz_block");
            for (int y = 3; y <= 6; y++) {
                this.set(x, y, z, "quartz_pillar[axis=y]");
            }

            this.set(x, 7, z, "chiseled_quartz_block");
            String out = facing(Math.cos(ca), Math.sin(ca));
            this.set(x + dx(out), 6, z + dz(out), col + "_wall_banner[facing=" + out + "]");
        }

        // the dome: gold band, coloured shell, a lantern on top and one hanging inside
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                for (int y = 0; y <= 6; y++) {
                    double q = (x * x + z * z) / 30.25 + (y * y) / 30.25;
                    double qi = (x * x + z * z) / 20.25 + (y * y) / 20.25;
                    if (q <= 1.0 && qi > 1.0) {
                        this.set(px + x, 8 + y, pz + z, y == 0 || y >= 5 ? "gold_block" : col + "_concrete");
                    }
                }
            }
        }

        this.set(px, 14, pz, "gold_block");
        this.set(px, 15, pz, "lantern[hanging=false]");
        this.hangingLantern(px, 12, pz, 3, false);
    }

    // ------------------------------------------------------------------ bridges over the chasm

    private void bridges() {
        for (double[] l : this.lanes) {
            double len = dist(l[2] - l[0], l[3] - l[1]);
            double ux = (l[2] - l[0]) / len;
            double uz = (l[3] - l[1]) / len;
            double nx = -uz;
            double nz = ux;
            double half = l[4] + 1.0;
            double t0 = -1;
            double t1 = -1;
            for (double t = 0.0; t <= len; t += 0.5) {
                if (this.crack(l[0] + ux * t, l[1] + uz * t) < 0.0) {
                    if (t0 < 0) {
                        t0 = t;
                    }

                    t1 = t;
                }
            }

            if (t0 < 0) {
                continue;
            }

            t0 = Math.max(0.0, t0 - 4.0);
            t1 = Math.min(len, t1 + 4.0);
            double span = t1 - t0;
            for (double t = t0; t <= t1; t += 0.4) {
                double u = (t - t0) / span;
                // an arch under the deck: deep at the ends, thin in the middle
                int depth = (int)Math.round(2 + 10 * (1.0 - Math.sqrt(Math.max(0.0, 1.0 - Math.pow(2.0 * u - 1.0, 2.0)))));
                for (double sd = -half; sd <= half; sd += 0.4) {
                    int x = (int)Math.round(l[0] + ux * t + nx * sd);
                    int z = (int)Math.round(l[1] + uz * t + nz * sd);
                    boolean rail = Math.abs(sd) > half - 0.6;
                    boolean mid = Math.abs(sd) < 0.6;
                    this.set(x, 0, z, rail ? "polished_blackstone_bricks"
                        : (mid ? "red_nether_bricks" : this.texture(261L, x, z, "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "polished_blackstone")));
                    for (int y = -1; y >= -depth; y--) {
                        this.set(x, y, z, y == -depth ? "polished_blackstone_brick_slab[type=top]" : (y == -1 ? "gilded_blackstone" : "polished_blackstone_bricks"));
                    }

                    if (rail) {
                        this.set(x, 1, z, "polished_blackstone_brick_wall");
                    }
                }
            }

            // posts with lanterns and gold caps along both rails, lanterns hanging under the deck
            for (double t = t0; t <= t1 + 0.1; t += span / Math.max(1, Math.round(span / 5.0))) {
                for (int side = -1; side <= 1; side += 2) {
                    int x = (int)Math.round(l[0] + ux * t + nx * half * side);
                    int z = (int)Math.round(l[1] + uz * t + nz * half * side);
                    this.set(x, 1, z, "polished_blackstone_bricks");
                    this.set(x, 2, z, "polished_blackstone_bricks");
                    this.set(x, 3, z, "gold_block");
                    this.set(x, 4, z, "lantern[hanging=false]");
                    int ox = (int)Math.round(l[0] + ux * t + nx * (half + 1.0) * side);
                    int oz = (int)Math.round(l[1] + uz * t + nz * (half + 1.0) * side);
                    if (this.empty(ox, 0, oz)) {
                        this.set(ox, 0, oz, "polished_blackstone_brick_slab[type=top]");
                        this.hangingLantern(ox, -1, oz, 1, true);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Hall of Champions (north)

    private void hall() {
        int x0 = HALL[0] - 27;
        int x1 = HALL[0] + 27;
        int z0 = HALL[1] - 15;
        int z1 = HALL[1] + 13;
        int zw = z1 - 6;
        // plinth with steps on the front (south)
        for (int x = x0 - 3; x <= x1 + 3; x++) {
            for (int z = z0 - 2; z <= z1 + 5; z++) {
                this.set(x, 0, z, "polished_blackstone_bricks");
                if (z <= z1 + 2) {
                    this.set(x, 1, z, z == z1 + 2 ? stairs("polished_blackstone_brick", "north", false) : "polished_blackstone_bricks");
                }

                if (z <= z1) {
                    this.set(x, 2, z, z == z1 ? stairs("polished_blackstone_brick", "north", false) : "smooth_quartz");
                }
            }
        }

        // walls: dark brick with gilded courses and tall red windows; a checkered floor inside
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= zw; z++) {
                boolean edgeX = x == x0 || x == x1;
                boolean edgeZ = z == z0 || z == zw;
                if (!edgeX && !edgeZ) {
                    this.set(x, 2, z, (x + z & 1) == 0 ? "polished_deepslate" : "deepslate_tiles");
                    continue;
                }

                for (int y = 3; y <= 17; y++) {
                    String b = y == 10 || y == 17 ? "gilded_blackstone" : (this.hash3(301L, x, y, z) < 0.1 ? "cracked_deepslate_bricks" : "deepslate_bricks");
                    int along = edgeZ ? x : z;
                    if (y >= 5 && y <= 14 && y != 10 && Math.floorMod(along, 6) == 3 && !(edgeX && edgeZ)) {
                        b = "red_stained_glass";
                    }

                    this.set(x, y, z, b);
                }
            }
        }

        // the door, gold framed
        for (int x = HALL[0] - 3; x <= HALL[0] + 3; x++) {
            for (int y = 3; y <= 9; y++) {
                boolean frame = Math.abs(x - HALL[0]) == 3 || y == 9;
                this.set(x, y, zw, frame ? "gold_block" : null);
            }
        }

        // the roof: deep slate tiles with a gold ridge
        int ry = this.gableRoof(x0, z0, x1, zw, 18, true, Tile.SLATE, "deepslate_bricks");
        int rz = (z0 + zw) / 2;
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            this.set(x, ry + 1, rz, "gold_block");
        }

        // the colonnade in front: ten big white columns under a portico; its roof is the spectator balcony
        for (int k = 0; k < 5; k++) {
            for (int side = -1; side <= 1; side += 2) {
                int x = HALL[0] + side * (4 + k * 5);
                this.bigPillar(x, 3, z1 - 2, 15, "quartz_block", "quartz", "quartz_pillar[axis=y]", null);
                this.set(x, 14, z1 - 1, "red_wall_banner[facing=south]");
            }
        }

        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = zw; z <= z1 + 1; z++) {
                this.set(x, 18, z, z == z1 + 1 ? stairs("polished_blackstone_brick", "south", true) : "polished_blackstone_bricks");
                this.set(x, 19, z, z == z1 + 1 ? "gold_block" : "chiseled_polished_blackstone");
                boolean rim = z == z1 + 1 || x == x0 - 1 || x == x1 + 1;
                if (rim && z > zw) {
                    this.set(x, 20, z, "polished_blackstone_brick_wall");
                }
            }
        }

        for (int x = x0 + 3; x <= x1 - 3; x += 8) {
            this.set(x, 21, z1 + 1, "lantern[hanging=false]");
        }

        // a cupola on the ridge with the golden crown on top
        int cupTop = ry + 5;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = 17; y <= cupTop; y++) {
                    boolean shell = Math.abs(x) == 5 || Math.abs(z) == 5;
                    if (!shell) {
                        if (y == cupTop) {
                            this.set(HALL[0] + x, y, rz + z, "polished_blackstone_bricks");
                        }

                        continue;
                    }

                    boolean corner = Math.abs(x) == 5 && Math.abs(z) == 5;
                    String b = corner ? "quartz_pillar[axis=y]" : (y == cupTop || y == cupTop - 5 ? "gilded_blackstone" : "deepslate_bricks");
                    if (!corner && y > cupTop - 5 && y < cupTop && (Math.abs(x) <= 2 || Math.abs(z) <= 2)) {
                        b = "red_stained_glass";
                    }

                    this.set(HALL[0] + x, y, rz + z, b);
                }
            }
        }

        this.crown(HALL[0], cupTop + 1, rz, 5.0);
        // inside: a red carpet aisle, three podiums, chandeliers
        for (int z = z0 + 1; z < zw; z++) {
            for (int x = HALL[0] - 2; x <= HALL[0] + 2; x++) {
                this.set(x, 3, z, Math.abs(x - HALL[0]) == 2 ? "yellow_carpet" : "red_carpet");
            }
        }

        this.podium(HALL[0], z0 + 4, 3, "gold_block");
        this.podium(HALL[0] - 7, z0 + 5, 2, "iron_block");
        this.podium(HALL[0] + 7, z0 + 5, 1, "copper_block");
        for (int x = x0 + 8; x <= x1 - 8; x += 12) {
            this.chandelier(x, 16, rz, 3);
        }

        // flag towers at the back corners
        this.roundTower(x0 - 3, z0 - 1, 4.5, 2, 24, Tile.SLATE, 8, "red", "south");
        this.roundTower(x1 + 3, z0 - 1, 4.5, 2, 24, Tile.SLATE, 8, "red", "south");
    }

    private void podium(int x, int z, int h, String top) {
        for (int y = 3; y < 3 + h; y++) {
            this.set(x, y, z, y == 2 + h ? top : "polished_blackstone_bricks");
            this.set(x + 1, y, z, stairs("polished_blackstone_brick", "west", false));
            this.set(x - 1, y, z, stairs("polished_blackstone_brick", "east", false));
        }

        this.set(x, 3 + h, z, "player_head[rotation=8]");
    }

    /** A golden crown lying flat: a ring with five tall points capped with gems, small points between, a red cushion. */
    private void crown(int ox, int y, int oz, double r) {
        int n = (int)Math.ceil(r);
        for (int x = -n; x <= n; x++) {
            for (int z = -n; z <= n; z++) {
                double d = dist(x, z);
                if (d > r - 1.6 && d <= r + 0.2) {
                    double deg = Math.toDegrees(Math.atan2(z, x)) + 360.0;
                    this.set(ox + x, y, oz + z, "gold_block");
                    this.set(ox + x, y + 1, oz + z, Math.floorMod((int)Math.round(deg / 24.0), 3) == 0 ? "redstone_block" : "gold_block");
                    double big = Math.abs(((deg + 18.0) % 72.0) - 36.0);
                    double small = Math.abs(((deg + 54.0) % 72.0) - 36.0);
                    if (big > 31.0 && d > r - 0.9) {
                        this.set(ox + x, y + 2, oz + z, "gold_block");
                        this.set(ox + x, y + 3, oz + z, "gold_block");
                        this.set(ox + x, y + 4, oz + z, "lapis_block");
                    } else if (small > 32.0 && d > r - 0.9) {
                        this.set(ox + x, y + 2, oz + z, "gold_block");
                    }
                } else if (d <= r - 1.6) {
                    this.set(ox + x, y, oz + z, "red_wool");
                    if (d < (r - 1.6) / 2.0) {
                        this.set(ox + x, y + 1, oz + z, "red_wool");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ FFA Colosseum (east)

    private void colosseum() {
        int ox = FFA[0];
        int oz = FFA[1];
        int r = 22;
        for (int x = -r - 2; x <= r + 2; x++) {
            for (int z = -r - 2; z <= r + 2; z++) {
                double d = dist(x, z);
                int wx = ox + x;
                int wz = oz + z;
                if (d <= r - 6) {
                    // the sand pit with a ring of red stone and a crest in the middle
                    String b = this.texture(301L, wx, wz, "sand", "smooth_sandstone", "sand", "sandstone");
                    if (Math.abs(d - (r - 6.5)) < 0.6) {
                        b = "red_sandstone";
                    } else if (d < 3.5) {
                        b = d < 1.5 ? "gold_block" : "red_nether_bricks";
                    }

                    this.set(wx, 0, wz, b);
                    this.set(wx, -1, wz, "sandstone");
                } else if (d <= r - 1) {
                    // tiers of seats rising outward
                    int tier = (int)Math.round((d - (r - 6)) * 1.2);
                    for (int y = 1; y <= tier; y++) {
                        this.set(wx, y, wz, y == tier ? stairs("red_nether_brick", facing(-x, -z), false) : "nether_bricks");
                    }

                    this.set(wx, 0, wz, "nether_bricks");
                } else if (d <= r + 0.6) {
                    // the outer wall with two rows of arches
                    double deg = Math.toDegrees(Math.atan2(z, x)) + 360.0;
                    boolean archCol = Math.floorMod((int)Math.round(deg / 3.0), 5) != 0;
                    for (int y = 1; y <= 16; y++) {
                        boolean opening = archCol && (y >= 2 && y <= 5 || y >= 9 && y <= 12);
                        String b = y == 7 || y == 14 ? "gilded_blackstone" : "polished_blackstone_bricks";
                        if (y == 16) {
                            b = Math.floorMod((int)Math.round(deg / 3.0), 2) == 0 ? "polished_blackstone_brick_wall" : null;
                        }

                        if (!opening) {
                            this.set(wx, y, wz, b);
                        } else if (y == 5 || y == 12) {
                            this.set(wx, y, wz, stairs("polished_blackstone_brick", facing(x, z), true));
                        }
                    }

                    this.set(wx, 0, wz, "polished_blackstone_bricks");
                }
            }
        }

        // braziers and banners on the wall
        for (int k = 0; k < 12; k++) {
            double a = k * 30.0 + 15.0;
            int x = ox + (int)Math.round(ax(a, r));
            int z = oz + (int)Math.round(az(a, r));
            this.set(x, 16, z, "polished_blackstone_bricks");
            this.set(x, 17, z, "campfire[lit=true,signal_fire=false]");
            String out = facing(ax(a, 1), az(a, 1));
            this.set(x + dx(out), 15, z + dz(out), "red_wall_banner[facing=" + out + "]");
        }

        // the grand gate facing the plaza (west), with a tunnel through the seats
        int gx = ox - r;
        for (int dz2 = -5; dz2 <= 5; dz2++) {
            for (int y = 1; y <= 14; y++) {
                boolean inside = Math.abs(dz2) <= 3 && y <= 8 + (Math.abs(dz2) <= 1 ? 1 : 0);
                for (int dx2 = -1; dx2 <= 1; dx2++) {
                    this.set(gx + dx2, y, oz + dz2, inside ? null : (y == 14 ? "gold_block" : (y == 13 ? "gilded_blackstone" : "polished_blackstone_bricks")));
                }
            }

            for (int dx2 = -1; dx2 <= 7; dx2++) {
                if (Math.abs(dz2) <= 3) {
                    this.set(gx + dx2, 0, oz + dz2, Math.abs(dz2) <= 1 ? "red_nether_bricks" : "polished_blackstone_bricks");
                }

                if (dx2 > 1 && Math.abs(dz2) <= 3) {
                    for (int y = 1; y <= 4; y++) {
                        this.set(gx + dx2, y, oz + dz2, null);
                    }

                    this.set(gx + dx2, 5, oz + dz2, Math.abs(dz2) == 3 ? "nether_bricks" : "polished_blackstone_brick_slab[type=top]");
                }
            }
        }

        this.set(gx - 2, 11, oz - 4, "red_wall_banner[facing=west]");
        this.set(gx - 2, 11, oz + 4, "red_wall_banner[facing=west]");
        this.giantSwordAt(gx, 15, oz - 4, 9, "iron_block", "iron_block", "gold_block", "red_nether_bricks", false);
        this.giantSwordAt(gx, 15, oz + 4, 9, "iron_block", "iron_block", "gold_block", "red_nether_bricks", false);
    }

    // ------------------------------------------------------------------ Training Yard (west)

    private void training() {
        int ox = TRAINING[0];
        int oz = TRAINING[1];
        int hx = 20;
        int hz = 16;
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                int wx = ox + x;
                int wz = oz + z;
                boolean wall = Math.abs(x) == hx || Math.abs(z) == hz;
                this.set(wx, 0, wz, wall ? "stone_bricks" : this.texture(311L, wx, wz, "coarse_dirt", "gravel", "packed_mud", "dirt_path", "coarse_dirt"));
                if (wall) {
                    boolean gate = Math.abs(z) <= 3 && x == hx;
                    this.set(wx, 1, wz, gate ? null : this.stone(wx, 1, wz, 0));
                    this.set(wx, 2, wz, gate ? null : "stone_brick_wall");
                    if (!gate && Math.floorMod(x + z, 8) == 0) {
                        this.set(wx, 3, wz, "lantern[hanging=false]");
                    }
                }
            }
        }

        // gate posts with banners
        for (int side = -1; side <= 1; side += 2) {
            int z = oz + side * 4;
            for (int y = 1; y <= 5; y++) {
                this.set(ox + hx, y, z, y == 5 ? "lantern[hanging=false]" : this.stone(ox + hx, y, z, 0));
            }

            this.set(ox + hx + 1, 3, z, "red_wall_banner[facing=east]");
        }

        // archery lanes along the north wall
        for (int k = 0; k < 4; k++) {
            int lx = ox - 15 + k * 6;
            this.set(lx, 1, oz - hz + 2, "hay_block[axis=y]");
            this.set(lx, 2, oz - hz + 2, "target");
            this.set(lx, 3, oz - hz + 2, "hay_block[axis=x]");
            for (int z = oz - hz + 4; z <= oz - 4; z++) {
                if (Math.floorMod(z, 3) == 0) {
                    this.set(lx - 2, 1, z, "spruce_fence");
                }
            }
        }

        // dummies along the south side
        for (int k = 0; k < 5; k++) {
            int x = ox - 14 + k * 7;
            int z = oz + 10;
            this.set(x, 1, z, "spruce_fence");
            this.set(x, 2, z, "hay_block[axis=y]");
            this.set(x, 3, z, "carved_pumpkin[facing=north]");
            this.set(x - 1, 2, z, "spruce_fence");
            this.set(x + 1, 2, z, "spruce_fence");
        }

        // two sparring rings
        for (int c = -1; c <= 1; c += 2) {
            int rx = ox + c * 8;
            int rz = oz;
            this.ring(rx, rz, 0, 3.5, 4.4, "polished_andesite");
            this.ring(rx, rz, 1, 4.0, 4.4, "spruce_fence");
            this.set(rx, 0, rz, "red_concrete");
        }

        // a watchtower on the north-west corner and a weapon stall in the yard
        this.squareTower(ox - hx - 2, oz - hz - 2, 2, 0, 15, Tile.RED, "red", "east");
        this.stall(ox + 15, 1, oz + 13, "north", "red", "anvil[facing=east]", "smithing_table", "grindstone[face=floor,facing=north]");
        // barracks and a weapon store behind the yard (south-west)
        this.timberHouse(ox - 18, oz + 20, 12, 13, 0, new House().floors(2).roof(Tile.RED).front("north"));
        this.timberHouse(ox - 2, oz + 22, 9, 9, 0, new House().floors(1).roof(Tile.RED).front("north").chimney(false));
        this.crates(ox + 10, 1, oz + 22, 3);
        this.woodpile(ox + 12, 1, oz + 26, true);
        this.well(ox + 14, 1, oz + 30);
    }

    // ------------------------------------------------------------------ Cosmetic Spire (north-west)

    private void boutique() {
        int ox = BOUTIQUE[0];
        int oz = BOUTIQUE[1];
        String[] glass = {"magenta", "purple", "blue", "light_blue", "cyan", "lime", "yellow", "orange", "red", "pink"};
        String[] fl = {"allium", "pink_tulip", "blue_orchid", "lilac", "peony", "azure_bluet", "white_tulip"};
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                double d = dist(x, z);
                if (d <= 16 && d > 9.5) {
                    int wx = ox + x;
                    int wz = oz + z;
                    boolean path = Math.abs(x) <= 2 || Math.abs(z) <= 2;
                    this.set(wx, 0, wz, path ? (Math.abs(x) == 2 || Math.abs(z) == 2 ? "polished_diorite" : "calcite") : "grass_block");
                    if (!path && d < 15.5 && this.hash3(321L, wx, 0, wz) < 0.4) {
                        String f = fl[(int)(this.hash3(323L, wx, 0, wz) * fl.length)];
                        if (f.equals("lilac") || f.equals("peony")) {
                            this.tallPlant(wx, 1, wz, f);
                        } else {
                            this.plant(wx, 1, wz, f);
                        }
                    } else if (!path && d >= 15.5) {
                        this.set(wx, 1, wz, leaves("flowering_azalea"));
                    }
                }
            }
        }

        // the tower: white stone with rainbow glass bands and a purple and gold cone
        int r = 8;
        int topY = 38;
        for (int y = 0; y <= topY; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    double d = dist(x, z);
                    if (d > r + 0.5) {
                        continue;
                    }

                    int wx = ox + x;
                    int wz = oz + z;
                    if (y == 0) {
                        this.set(wx, 0, wz, d < 3 ? "amethyst_block" : "polished_diorite");
                    } else if (d > r - 0.6) {
                        int band = y / 4;
                        boolean window = y % 4 != 0 && y > 2 && Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(z, x)) / 20.0), 2) == 0;
                        this.set(wx, y, wz, window ? glass[band % glass.length] + "_stained_glass" : (y % 4 == 0 ? "gold_block" : "calcite"));
                    } else if (y % 12 == 0) {
                        this.set(wx, y, wz, "smooth_quartz");
                    }
                }
            }
        }

        for (int y = 1; y <= 4; y++) {
            for (int x = -1; x <= 1; x++) {
                this.set(ox + x, y, oz + r, null);
            }
        }

        this.set(ox - 2, 3, oz + r + 1, "magenta_wall_banner[facing=south]");
        this.set(ox + 2, 3, oz + r + 1, "magenta_wall_banner[facing=south]");
        this.coneRoof(ox, oz, r + 1.5, topY + 1, 18);
        for (int y = topY + 19; y <= topY + 24; y++) {
            this.set(ox, y, oz, y == topY + 24 ? "end_rod[facing=up]" : "gold_block");
        }

        this.hangingLantern(ox, topY - 1, oz, 6, true);
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + k * 90);
            int tx = ox + (int)Math.round(Math.cos(a) * 10.5);
            int tz = oz + (int)Math.round(Math.sin(a) * 10.5);
            for (int y = 0; y <= 16; y++) {
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        double d = dist(x, z);
                        if (d <= 2.6) {
                            this.set(tx + x, y, tz + z, d > 1.7 || y == 0 ? (y % 4 == 0 ? "gold_block" : (y > 2 && y % 4 == 2 && d > 2.2 ? glass[(k * 3 + y / 4) % glass.length] + "_stained_glass" : "calcite")) : null);
                        }
                    }
                }
            }

            this.coneRoof(tx, tz, 3.6, 17, 8);
            this.set(tx, 25, tz, "gold_block");
            this.set(tx, 26, tz, "end_rod[facing=up]");
        }
    }

    /** A cone of purpur and amethyst for the spire top. */
    private void coneRoof(int ox, int oz, double r, int y0, int h) {
        for (int y = 0; y < h; y++) {
            double rr = r * (1.0 - (double)y / h);
            for (int x = (int)-r - 1; x <= r + 1; x++) {
                for (int z = (int)-r - 1; z <= r + 1; z++) {
                    double d = dist(x, z);
                    if (d <= rr + 0.4 && d > rr - 1.2) {
                        this.set(ox + x, y0 + y, oz + z, y % 5 == 0 ? "gold_block" : (this.hash3(331L, x, y, z) < 0.3 ? "amethyst_block" : "purpur_block"));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Kit Forge (north-east)

    private void forge() {
        int ox = FORGE[0];
        int oz = FORGE[1];
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                if (dist(x, z) <= 16) {
                    this.set(ox + x, 0, oz + z, this.texture(341L, ox + x, oz + z, "cobblestone", "stone", "gravel", "mossy_cobblestone", "cobblestone"));
                }
            }
        }

        this.timberHouse(ox - 10, oz - 10, 20, 13, 0, new House().floors(2).roof(Tile.SLATE).front("south").chimney(true).stone(true));
        // the giant anvil in the yard, a hammer resting on it
        int ax0 = ox;
        int az0 = oz + 9;
        for (int x = -4; x <= 4; x++) {
            for (int z = -2; z <= 2; z++) {
                this.set(ax0 + x, 1, az0 + z, Math.abs(x) <= 3 ? "polished_basalt[axis=y]" : null);
                this.set(ax0 + x, 2, az0 + z, Math.abs(x) <= 1 ? "iron_block" : null);
                this.set(ax0 + x, 3, az0 + z, Math.abs(x) <= 2 ? "iron_block" : null);
                this.set(ax0 + x, 4, az0 + z, "iron_block");
                this.set(ax0 + x, 5, az0 + z, Math.abs(z) <= 1 ? "iron_block" : null);
            }
        }

        this.set(ax0 + 5, 5, az0, "iron_block");
        this.set(ax0 + 6, 5, az0, "iron_block");
        for (int y = 6; y <= 10; y++) {
            this.set(ax0 + 1, y, az0, "stripped_dark_oak_log[axis=y]");
        }

        this.box(ax0, 10, az0 - 1, ax0 + 2, 11, az0 + 1, "polished_deepslate");
        // workstations and stock
        this.set(ox - 12, 1, oz + 6, "blast_furnace[facing=south,lit=true]");
        this.set(ox - 11, 1, oz + 6, "anvil[facing=east]");
        this.set(ox - 10, 1, oz + 6, "grindstone[face=floor,facing=south]");
        this.set(ox + 12, 1, oz + 6, "smithing_table");
        this.set(ox + 11, 1, oz + 6, "blast_furnace[facing=south,lit=true]");
        this.set(ox + 10, 1, oz + 6, "anvil[facing=west]");
        this.crates(ox + 10, 1, oz + 10, 7);
        this.woodpile(ox - 12, 1, oz + 10, true);
        for (int side = -1; side <= 1; side += 2) {
            this.bracketLamp(ox + side * 6, 1, oz + 14, side < 0 ? "east" : "west");
        }
    }

    // ------------------------------------------------------------------ Tournament Stage (south-east)

    private void stage() {
        int ox = STAGE[0];
        int oz = STAGE[1];
        // the open side faces west-north-west, toward the arrival road and the plaza; seats curve round the back
        double faceDeg = 200.0;
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                double d = dist(x, z);
                if (d > 24.5) {
                    continue;
                }

                int wx = ox + x;
                int wz = oz + z;
                double rel = Math.toDegrees(Math.atan2(z, x)) - faceDeg;
                rel = ((rel % 360) + 540) % 360 - 180;
                boolean front = Math.abs(rel) < 70;
                if (d <= 9) {
                    this.set(wx, 0, wz, "polished_blackstone_bricks");
                    this.set(wx, 1, wz, d > 8.2 ? "gold_block" : (d < 2.5 ? "gold_block" : (Math.floorMod((int)Math.floor(d), 2) == 0 ? "red_concrete" : "red_terracotta")));
                } else if (!front && d >= 12) {
                    int tier = Math.min(7, (int)((d - 12) / 1.6) + 1);
                    for (int y = 1; y <= tier; y++) {
                        this.set(wx, y, wz, y == tier ? stairs("dark_oak", facing(-x, -z), false) : "polished_blackstone_bricks");
                    }

                    this.set(wx, 0, wz, "polished_blackstone_bricks");
                } else {
                    this.set(wx, 0, wz, d < 11 ? "gold_block" : this.texture(351L, wx, wz, "polished_andesite", "andesite", "polished_andesite"));
                }
            }
        }

        // the trophy in the middle of the stage
        int y = 2;
        this.cylinder(ox, oz, y, y + 2, 0.6, "gold_block", false);
        for (int k = 0; k <= 5; k++) {
            double rr = 1.2 + k * 0.45;
            this.ring(ox, oz, y + 3 + k, rr - 0.7, rr, "gold_block");
        }

        this.set(ox - 3, y + 6, oz, "gold_block");
        this.set(ox + 3, y + 6, oz, "gold_block");
        this.set(ox - 4, y + 5, oz, "gold_block");
        this.set(ox + 4, y + 5, oz, "gold_block");
        this.set(ox, y + 9, oz, "redstone_block");
        // light towers at the back of the seats
        for (int side = -1; side <= 1; side += 2) {
            double a = faceDeg + 180 + side * 50;
            int tx = ox + (int)Math.round(ax(a, 23));
            int tz = oz + (int)Math.round(az(a, 23));
            for (int yy = 1; yy <= 16; yy++) {
                this.set(tx, yy, tz, yy == 16 ? "sea_lantern" : (yy == 15 ? "gold_block" : "polished_blackstone_brick_wall"));
            }

            String in = facing(ox - tx, oz - tz);
            this.set(tx + dx(in), 13, tz + dz(in), "red_wall_banner[facing=" + in + "]");
        }
    }

    // ------------------------------------------------------------------ Parkour Spire (south-west)

    private void parkour() {
        int ox = PARKOUR[0];
        int oz = PARKOUR[1];
        int h = 50;
        for (int y = 0; y <= h; y++) {
            double r = 3.6 - y * 0.03;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (dist(x, z) <= r) {
                        String b = y % 9 == 0 ? "gold_block" : (y % 9 == 1 ? "red_nether_bricks"
                            : this.texture(361L, x + y * 13, z, "polished_blackstone_bricks", "polished_blackstone", "cracked_polished_blackstone_bricks"));
                        this.set(ox + x, y, oz + z, b);
                    }
                }
            }
        }

        // the course: blocks spiralling up around the spire, one block higher each jump, a checkpoint every sixth
        String[] pads = {"red_concrete", "orange_concrete", "yellow_concrete", "lime_concrete", "light_blue_concrete", "purple_concrete", "magenta_concrete", "pink_concrete", "white_concrete"};
        double a = 90.0;
        int jumps = 0;
        for (int y = 1; y <= h; y++) {
            int x = ox + (int)Math.round(ax(a, 7.5));
            int z = oz + (int)Math.round(az(a, 7.5));
            this.set(x, y, z, jumps % 6 == 5 ? "gold_block" : pads[(jumps / 6) % pads.length]);
            // every block of the course is handed to the hub parkour (timer, checkpoints, top 10)
            this.feature("pk:b" + jumps, x, y, z, ox, oz);
            jumps++;
            a += 23.0;
        }

        // the finish platform with a flag
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double d = dist(x, z);
                if (d <= 5.5) {
                    this.set(ox + x, h + 1, oz + z, d > 4.6 ? "gold_block" : "smooth_quartz");
                    if (d > 5.0) {
                        this.set(ox + x, h + 2, oz + z, "polished_blackstone_brick_wall");
                    }
                }
            }
        }

        // a gap in the railing where the last jump lands
        int lx = (int)Math.round(ax(a - 23.0, 5.3));
        int lz = (int)Math.round(az(a - 23.0, 5.3));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                this.set(ox + lx + x, h + 2, oz + lz + z, null);
            }
        }

        // the finish: a ring of diamond round the flag
        int fin = 0;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double d = dist(x, z);
                if (d >= 1.4 && d <= 2.6) {
                    this.set(ox + x, h + 1, oz + z, "diamond_block");
                    this.feature("pk:f" + fin++, ox + x, h + 1, oz + z, ox, oz);
                }
            }
        }

        this.flagPole(ox, h + 2, oz, 6, "red");
        // the start: a lime block in the ground (step on it to start the timer)
        int sx = ox + (int)Math.round(ax(90.0, 11.0));
        int sz = oz + (int)Math.round(az(90.0, 11.0));
        this.set(sx, 0, sz, "lime_concrete");
        this.feature("pk:start", sx, 0, sz, ox, oz);
        this.feature("parkour", ox, h + 2, oz, ox, oz + 20);
        this.feature("parkour_start", sx, 1, sz, ox, oz);
    }

    // ------------------------------------------------------------------ Arrival (spawn)

    private void arrival() {
        int ox = ARRIVAL[0];
        int oz = ARRIVAL[1];
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                int wx = ox + x;
                int wz = oz + z;
                boolean rim = Math.abs(x) == 16 || Math.abs(z) == 16;
                String b = rim ? "polished_blackstone_bricks"
                    : (Math.abs(x) <= 2 ? (Math.abs(x) == 2 ? "gold_block" : "red_nether_bricks")
                    : this.texture(371L, wx, wz, "calcite", "polished_diorite", "smooth_quartz"));
                this.set(wx, 0, wz, b);
            }
        }

        // the crowned gatehouse toward the plaza (north side)
        int gz = oz - 16;
        for (int x = -7; x <= 7; x++) {
            for (int dz2 = -3; dz2 <= 3; dz2++) {
                for (int y = 1; y <= 13; y++) {
                    boolean open = Math.abs(x) <= 4 && y <= 9 + (Math.abs(x) <= 2 ? 1 : 0);
                    boolean thin = Math.abs(dz2) > 1 && y < 11;
                    String b = y == 11 ? "gilded_blackstone" : (y == 13 ? "gold_block" : "polished_blackstone_bricks");
                    this.set(ox + x, y, gz + dz2, open || thin && Math.abs(x) < 6 ? null : b);
                }
            }
        }

        this.set(ox - 6, 8, gz + 4, "red_wall_banner[facing=south]");
        this.set(ox + 6, 8, gz + 4, "red_wall_banner[facing=south]");
        this.set(ox - 6, 8, gz - 4, "red_wall_banner[facing=north]");
        this.set(ox + 6, 8, gz - 4, "red_wall_banner[facing=north]");
        this.hangingLantern(ox, 10, gz, 2, false);
        this.crown(ox, 14, gz, 3.8);
        this.roundTower(ox - 11, gz, 3.5, 1, 18, Tile.SLATE, 6, "red", "east");
        this.roundTower(ox + 11, gz, 3.5, 1, 18, Tile.SLATE, 6, "yellow", "west");
        // fountain at the back of the square, lamps and flower beds along the sides
        this.fountain(ox, oz + 9, 4, "polished_blackstone_bricks", "gold_block");
        for (int side = -1; side <= 1; side += 2) {
            for (int z = oz - 10; z <= oz + 12; z += 6) {
                this.plazaLamp(ox + side * 12, 1, z);
            }

            this.flowerBed(ox + side * 9 - 2, oz - 9, ox + side * 9 + 2, oz - 3, 1, "polished_blackstone_brick", "moss_block",
                "red_tulip", "poppy", "dandelion", "orange_tulip");
            this.flowerBed(ox + side * 9 - 2, oz + 3, ox + side * 9 + 2, oz + 13, 1, "polished_blackstone_brick", "moss_block",
                "red_tulip", "poppy", "dandelion", "orange_tulip");
        }

        this.spawn = new double[]{ox + 0.5, 1.0, oz - 1.5, 180.0};
    }

    // ------------------------------------------------------------------ the lookout (south edge)

    /** A balcony jutting out over the south edge, past the arrival: railings, posts with lanterns, benches. */
    private void lookout() {
        int cz = this.lookZ - 3;
        for (int x = -11; x <= 11; x++) {
            for (int z = -7; z <= 11; z++) {
                double d = dist(x, z);
                boolean in = z < 0 ? Math.abs(x) <= 9 : d <= 9.5;
                if (!in) {
                    continue;
                }

                int wx = x;
                int wz = cz + z;
                boolean rim = z >= 0 ? d > 8.6 : Math.abs(x) == 9;
                String b = rim ? "polished_blackstone_bricks"
                    : (z >= 0 && d < 3.0 ? (d < 1.5 ? "gold_block" : "red_nether_bricks")
                    : (Math.abs(d - 6.0) < 0.5 && z >= 0 ? "gold_block" : this.texture(471L, wx, wz, "calcite", "polished_diorite", "smooth_quartz")));
                this.set(wx, 0, wz, b);
                for (int y = 1; y <= 8; y++) {
                    this.set(wx, y, wz, null);
                }

                if (rim && z > -7) {
                    this.set(wx, 1, wz, "polished_blackstone_brick_wall");
                }

                if (!this.land(wx, wz)) {
                    int depth = (int)Math.round(2 + Math.max(0.0, 9.5 - d) * 0.9);
                    for (int y = -1; y >= -depth; y--) {
                        this.set(wx, y, wz, y == -depth ? "polished_blackstone_brick_slab[type=top]" : (y == -1 ? "gilded_blackstone" : "polished_blackstone_bricks"));
                    }
                }
            }
        }

        for (int k = 0; k <= 6; k++) {
            double a = Math.toRadians(k * 30.0);
            int x = (int)Math.round(Math.cos(a) * 9.0);
            int z = cz + (int)Math.round(Math.sin(a) * 9.0);
            this.set(x, 1, z, "polished_blackstone_bricks");
            this.set(x, 2, z, "polished_blackstone_bricks");
            this.set(x, 3, z, "gold_block");
            this.set(x, 4, z, "lantern[hanging=false]");
        }

        for (int side = -1; side <= 1; side += 2) {
            for (int k = -1; k <= 1; k++) {
                this.set(side * 4 + k, 1, cz + 3, stairs("dark_oak", "north", false));
            }

            this.plazaLamp(side * 9, 1, cz - 7);
        }

        this.feature("lookout", 0, 1.0, cz + 6, 0, cz + 40);
    }

    /** Ponds with sandy shores, reeds, lily pads and a small wooden dock. */
    private void ponds() {
        for (int[] p : PONDS) {
            int ox = p[0];
            int oz = p[1];
            int r = p[2];
            for (int x = -r - 4; x <= r + 4; x++) {
                for (int z = -r - 4; z <= r + 4; z++) {
                    int wx = ox + x;
                    int wz = oz + z;
                    if (!this.land(wx, wz)) {
                        continue;
                    }

                    double a = Math.atan2(z, x);
                    double rr = r + (noise(this.s + 461L, ox + Math.cos(a) * 14.0, oz + Math.sin(a) * 14.0) - 0.5) * 5.0;
                    double d = dist(x, z);
                    double h1 = this.hash3(463L, wx, 0, wz);
                    if (d <= rr) {
                        int depth = d < rr - 3.0 ? 3 : (d < rr - 1.5 ? 2 : 1);
                        for (int y = 1; y <= 4; y++) {
                            this.set(wx, y, wz, null);
                        }

                        for (int y = 0; y > -depth; y--) {
                            this.set(wx, y, wz, "water");
                        }

                        this.set(wx, -depth, wz, h1 < 0.4 ? "clay" : (h1 < 0.75 ? "sand" : "gravel"));
                        if ((d > rr - 2.0 && h1 > 0.86) || h1 > 0.97) {
                            this.set(wx, 1, wz, "lily_pad");
                        }
                    } else if (d <= rr + 1.8) {
                        for (int y = 1; y <= 3; y++) {
                            this.set(wx, y, wz, null);
                        }

                        this.set(wx, 0, wz, h1 < 0.5 ? "sand" : (h1 < 0.8 ? "gravel" : "mud"));
                    }
                }
            }

            // reeds where the shore touches the water
            for (int x = -r - 4; x <= r + 4; x++) {
                for (int z = -r - 4; z <= r + 4; z++) {
                    int wx = ox + x;
                    int wz = oz + z;
                    String g = this.get(wx, 0, wz);
                    if (g == null || !(g.equals("sand") || g.equals("mud")) || this.hash3(467L, wx, 0, wz) > 0.3) {
                        continue;
                    }

                    boolean wet = false;
                    for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        wet |= "water".equals(this.get(wx + o[0], 0, wz + o[1]));
                    }

                    if (wet && g.equals("sand")) {
                        int hgt = 1 + (int)(this.hash3(469L, wx, 0, wz) * 3);
                        for (int y = 1; y <= hgt; y++) {
                            this.set(wx, y, wz, "sugar_cane");
                        }
                    }
                }
            }

            // a little dock on the side facing the arrival road, a lantern at its end
            int dir = ox < 0 ? 1 : -1;
            for (int k = 0; k <= 6; k++) {
                for (int w = -1; w <= 1; w++) {
                    int wx = ox + dir * (r + 2) - dir * k;
                    int wz = oz + w;
                    this.set(wx, 0, wz, "spruce_planks");
                    this.set(wx, 1, wz, null);
                    if (k % 3 == 0 && Math.abs(w) == 1) {
                        this.set(wx, -1, wz, "spruce_log[axis=y]");
                        this.set(wx, -2, wz, "spruce_log[axis=y]");
                    }
                }
            }

            int ex = ox + dir * (r + 2) - dir * 6;
            this.set(ex, 1, oz - 1, "spruce_fence");
            this.set(ex, 2, oz - 1, "lantern[hanging=false]");
            this.set(ex, 1, oz + 1, "spruce_fence");
            this.set(ex, 2, oz + 1, "lantern[hanging=false]");
        }
    }

    // ------------------------------------------------------------------ water

    /** Streams down from the north, pouring off the chasm's lip as waterfalls into stone basins on the wall below. */
    private void waterfalls() {
        for (int x : new int[]{-46, 40, -127, 126}) {
            int lipZ = (int)Math.round(this.crackZ(x));
            while (!this.land(x, lipZ) && lipZ > -RAD + 2) {
                lipZ--;
            }

            int t0 = this.h(x, lipZ);
            if (t0 == Integer.MIN_VALUE) {
                continue;
            }

            for (int z = lipZ; z > lipZ - 40 && this.land(x, z); z--) {
                if (this.flatDist(x, z) < 2.0) {
                    break;
                }

                for (int w = 0; w <= 1; w++) {
                    int t = this.h(x + w, z);
                    if (t != Integer.MIN_VALUE) {
                        this.set(x + w, t, z, "water");
                        this.set(x + w, t - 1, z, "stone");
                        int bank = x + (w == 0 ? -1 : 2);
                        int tb = this.h(bank, z);
                        if (tb != Integer.MIN_VALUE && tb <= t + 1) {
                            this.set(bank, tb, z, this.hash3(381L, bank, t, z) < 0.5 ? "mossy_cobblestone" : "stone");
                        }
                    }
                }
            }

            int fz = lipZ + 1;
            for (int y = t0; y >= -30; y--) {
                for (int w = 0; w <= 1; w++) {
                    this.set(x + w, y, fz, "water");
                }
            }

            for (int dx2 = -2; dx2 <= 3; dx2++) {
                for (int dz2 = 0; dz2 <= 3; dz2++) {
                    boolean rim = dx2 == -2 || dx2 == 3 || dz2 == 3;
                    this.set(x + dx2, -32, fz + dz2, "mossy_stone_bricks");
                    this.set(x + dx2, -31, fz + dz2, rim ? "mossy_stone_brick_wall" : "water");
                    if (rim && this.hash3(383L, dx2, 0, dz2) < 0.3) {
                        this.set(x + dx2, -33, fz + dz2, "hanging_roots");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the fracture's details

    /** Amethyst shards jutting out of the ground along both lips of the chasm, leaning away from it. */
    private void shards() {
        Random r = new Random(this.s ^ 0x5A7DL);
        for (int i = 0; i < 90; i++) {
            int x = -160 + r.nextInt(321);
            int side = r.nextBoolean() ? 1 : -1;
            int len = 2 + r.nextInt(6);
            double lean = (0.15 + r.nextDouble() * 0.3) * side;
            double drift = (r.nextDouble() - 0.5) * 0.4;
            int back = 1 + r.nextInt(3);
            if (this.nearBridge(x, 9)) {
                continue;
            }

            double cz = this.crackZ(x);
            int z = (int)Math.round(cz);
            while (!this.land(x, z) && Math.abs(z - cz) < 20) {
                z += side;
            }

            z += side * back;
            if (!this.land(x, z) || this.flatDist(x, z) < 2.0) {
                continue;
            }

            int t = this.h(x, z);
            for (int k = 1; k <= len; k++) {
                int xx = x + (int)Math.round(drift * k);
                int zz = z + (int)Math.round(lean * k);
                this.set(xx, t + k, zz, k == len ? "amethyst_cluster[facing=up]" : (k == 1 ? "calcite" : "amethyst_block"));
                if (k <= len / 2) {
                    this.setIfEmpty(xx + 1, t + k, zz, k == 1 ? "calcite" : "amethyst_block");
                    this.setIfEmpty(xx, t + k, zz - side, "amethyst_block");
                }
            }
        }
    }

    /** Chunks of rock floating up out of the crack, with glowing undersides. */
    private void debris() {
        Random r = new Random(this.s ^ 0xDEB12L);
        for (int i = 0; i < 26; i++) {
            int x = -150 + r.nextInt(301);
            int oy = 2 + r.nextInt(26);
            double want = 1.4 + r.nextDouble() * 2.6;
            boolean crystal = r.nextBoolean();
            if (this.nearBridge(x, 12)) {
                continue;
            }

            double cz = this.crackZ(x);
            double rr = Math.min(want, -this.crack(x, cz) - 0.8);
            if (rr < 1.2) {
                continue;
            }

            int oz = (int)Math.round(cz);
            int n = (int)Math.ceil(rr);
            for (int dx2 = -n; dx2 <= n; dx2++) {
                for (int dz2 = -n; dz2 <= n; dz2++) {
                    double d = dist(dx2, dz2);
                    if (d > rr) {
                        continue;
                    }

                    int depth = (int)Math.round((rr - d) * 1.6) + 1;
                    for (int k = 0; k <= depth; k++) {
                        String b;
                        if (k == 0) {
                            b = this.hash3(939L, x + dx2, oy, oz + dz2) < 0.7 ? "grass_block" : "moss_block";
                        } else if (k == depth) {
                            b = this.hash3(941L, x + dx2, k, oz + dz2) < 0.35 ? "shroomlight" : "cobbled_deepslate";
                        } else {
                            b = this.hash3(943L, x + dx2, k, oz + dz2) < 0.5 ? "stone" : "tuff";
                        }

                        this.set(x + dx2, oy - k, oz + dz2, b);
                    }

                    if (d < rr - 1 && this.hash3(945L, x + dx2, 0, oz + dz2) < 0.25) {
                        this.plant(x + dx2, oy + 1, oz + dz2, "short_grass");
                    }
                }
            }

            if (rr > 2.6 && crystal) {
                this.set(x, oy + 1, oz, "amethyst_block");
                this.set(x, oy + 2, oz, "amethyst_cluster[facing=up]");
            }
        }
    }

    // ------------------------------------------------------------------ nature

    private void nature() {
        Random r = new Random(this.s ^ 0xF00DL);
        // trees on a jittered grid, thick where the forest noise is high and on the slopes
        for (int gx = -RAD; gx <= RAD; gx += 5) {
            for (int gz = -RAD; gz <= RAD; gz += 5) {
                int x = gx + r.nextInt(5) - 2;
                int z = gz + r.nextInt(5) - 2;
                double roll = r.nextDouble();
                double kind = r.nextDouble();
                int size = r.nextInt(8);
                double crownJitter = r.nextDouble();
                int t = this.h(x, z);
                if (t == Integer.MIN_VALUE || t >= 40 || this.flatDist(x, z) < 3.5 || this.crack(x, z) < 5.0) {
                    continue;
                }

                double forest = noise(this.s + 441L, x * 0.9, z * 0.9);
                double density = 0.05 + 0.9 * smooth01((forest - 0.44) / 0.2);
                if (t > 6) {
                    density = Math.max(density, 0.6);
                }

                if (roll > density) {
                    continue;
                }

                String surf = this.get(x, t, z);
                if (surf == null || !(surf.equals("grass_block") || surf.equals("moss_block") || surf.equals("podzol") || surf.equals("coarse_dirt"))
                    || !this.empty(x, t + 1, z)) {
                    continue;
                }

                if (t > 12 || z < -112) {
                    this.spruceAt(x, t + 1, z, 7 + size, t > 26);
                } else if (z > 24 && kind < 0.4) {
                    this.niceTree(x, t + 1, z, "cherry_log", "cherry", 4 + size % 2, 2.8 + crownJitter);
                } else if (z > 24 && kind < 0.6) {
                    this.niceTree(x, t + 1, z, "dark_oak_log", "dark_oak", 4 + size % 2, 3.0 + crownJitter);
                } else if (kind < 0.55) {
                    this.niceTree(x, t + 1, z, "oak_log", kind < 0.15 ? "flowering_azalea" : "oak", 4 + size % 3, 2.6 + crownJitter * 1.2);
                } else if (kind < 0.8) {
                    this.niceTree(x, t + 1, z, "birch_log", "birch", 5 + size % 2, 2.4 + crownJitter);
                } else {
                    this.spruceAt(x, t + 1, z, 6 + size % 5, false);
                }
            }
        }

        // flowers, grass, bushes: crimson meadows and gold meadows
        for (int x = -RAD; x <= RAD; x++) {
            for (int z = -RAD; z <= RAD; z++) {
                int t = this.h(x, z);
                if (t == Integer.MIN_VALUE || !this.empty(x, t + 1, z)) {
                    continue;
                }

                String surf = this.get(x, t, z);
                if (!"grass_block".equals(surf) && !"moss_block".equals(surf)) {
                    continue;
                }

                double v = this.hash3(411L, x, t, z);
                double red = noise(this.s + 413L, x * 1.3, z * 1.3);
                double gold = noise(this.s + 417L, x * 1.3, z * 1.3);
                double fd = this.flatDist(x, z);
                if (red > 0.6 && v < 0.35) {
                    this.set(x, t + 1, z, v < 0.12 ? "poppy" : (v < 0.22 ? "red_tulip" : (v < 0.3 ? "short_grass" : "torchflower")));
                } else if (gold > 0.62 && v < 0.3) {
                    this.set(x, t + 1, z, v < 0.12 ? "dandelion" : (v < 0.2 ? "orange_tulip" : "short_grass"));
                } else if (v < 0.2) {
                    this.set(x, t + 1, z, v < 0.14 ? "short_grass" : (v < 0.16 ? "fern" : (v < 0.18 ? "oxeye_daisy" : "cornflower")));
                } else if (v > 0.996 && fd > 3) {
                    this.tallPlant(x, t + 1, z, gold > 0.55 ? "sunflower" : (red > 0.5 ? "rose_bush" : "tall_grass"));
                } else if (v > 0.991 && fd > 5) {
                    this.bush(x, t + 1, z, this.hash3(419L, x, t, z) < 0.5 ? "azalea" : "flowering_azalea", 1.6);
                }
            }
        }

        for (int i = 0; i < 110; i++) {
            int x = r.nextInt(2 * RAD + 1) - RAD;
            int z = r.nextInt(2 * RAD + 1) - RAD;
            double size = 1.5 + r.nextDouble() * 1.8;
            int t = this.h(x, z);
            if (t != Integer.MIN_VALUE && this.flatDist(x, z) > 6 && this.crack(x, z) > 5) {
                this.boulder(x, t + 1, z, size, "stone", "andesite", "mossy_cobblestone", "cobblestone");
            }
        }
    }

    private void spruceAt(int x, int y, int z, int h, boolean snowy) {
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, "spruce_log[axis=y]");
        }

        for (int i = 2; i <= h + 1; i++) {
            double rr = (h + 2 - i) * 0.42;
            if (i % 2 == 1) {
                rr *= 0.72;
            }

            int n = (int)Math.ceil(rr);
            for (int dx2 = -n; dx2 <= n; dx2++) {
                for (int dz2 = -n; dz2 <= n; dz2++) {
                    if (Math.sqrt(dx2 * dx2 + dz2 * dz2) <= Math.max(0.6, rr)) {
                        this.setIfEmpty(x + dx2, y + i, z + dz2, "spruce_leaves[persistent=true]");
                        if (snowy && i % 2 == 0) {
                            this.setIfEmpty(x + dx2, y + i + 1, z + dz2, "snow[layers=1]");
                        }
                    }
                }
            }
        }

        this.set(x, y + h + 1, z, "spruce_leaves[persistent=true]");
    }

    /** Floating islets around the island: grass, a tree or a small ruin, roots below. */
    private void islets() {
        Random r = new Random(this.s ^ 0x15L);
        for (int i = 0; i < 12; i++) {
            double a = i * 30.0 + r.nextDouble() * 18.0;
            double d = Math.min(RAD - 8, this.islandEdge(ax(a, 100), az(a, 100)) + 9 + r.nextDouble() * 6);
            int ox = (int)Math.round(ax(a, d));
            int oz = (int)Math.round(az(a, d));
            int oy = -6 + r.nextInt(26);
            double rr = 3.5 + r.nextDouble() * 4.0;
            int hh0 = 3 + r.nextInt(3);
            int hh1 = 3 + r.nextInt(3);
            for (int x = (int)-rr - 1; x <= rr + 1; x++) {
                for (int z = (int)-rr - 1; z <= rr + 1; z++) {
                    double dd = dist(x, z);
                    if (dd > rr) {
                        continue;
                    }

                    int depth = (int)Math.round((rr - dd) * 1.4 + 1);
                    for (int k = 0; k <= depth; k++) {
                        this.set(ox + x, oy - k, oz + z, k == 0 ? "grass_block" : (k < 2 ? "dirt" : (this.hash3(431L, x, k, z) < 0.5 ? "stone" : "andesite")));
                    }

                    if (this.hash3(433L, ox + x, 0, oz + z) < 0.08) {
                        this.set(ox + x, oy - depth - 1, oz + z, "hanging_roots");
                    }
                }
            }

            if (i % 3 == 0) {
                this.niceTree(ox, oy + 1, oz, "cherry_log", "cherry", 4, 2.6);
            } else if (i % 3 == 1) {
                this.spruceAt(ox, oy + 1, oz, 6, false);
            } else {
                for (int k = -1; k <= 1; k += 2) {
                    int hh = k < 0 ? hh0 : hh1;
                    for (int y = 1; y <= hh; y++) {
                        this.set(ox + k * 2, oy + y, oz, this.stone(ox + k * 2, oy + y, oz, oy));
                    }
                }

                this.set(ox, oy + 1, oz, "lantern[hanging=false]");
            }
        }
    }

    // ------------------------------------------------------------------ NPCs and features

    /** A pavilion's NPC spot (in its middle, on the gold block). */
    private void pavilionNpc(double angle) {
        this.npc(Math.round(ax(angle, PAV_R)), 2.0, Math.round(az(angle, PAV_R)));
    }

    /**
     * NPC spots in the order the PvP lobby hands out its NPCs: duels, ranked, FFA, leaderboards, kit editor, parties,
     * settings, spectate, then bots, events, cosmetics, stats, welcome, parkour and the academy. So each district gets
     * its own NPC: FFA at the colosseum gate, leaderboards in the Hall of Champions, the kit editor at the forge,
     * spectate at the tournament stage, bots at the training yard, cosmetics at the spire.
     */
    private void spots() {
        int z1 = HALL[1] + 13;
        int stageX = STAGE[0] + (int)Math.round(ax(200.0, 7.0));
        int stageZ = STAGE[1] + (int)Math.round(az(200.0, 7.0));
        this.pavilionNpc(67.5);
        this.pavilionNpc(112.5);
        this.npc(FFA[0] - 26, 1.0, FFA[1] + 5);
        this.npc(HALL[0] + 6, 3.0, z1 - 4);
        this.npc(FORGE[0] - 5, 1.0, FORGE[1] + 14);
        this.pavilionNpc(22.5);
        this.pavilionNpc(157.5);
        this.npc(stageX, 2.0, stageZ);
        this.npc(TRAINING[0] + 23, 1.0, TRAINING[1] + 6);
        this.pavilionNpc(337.5);
        this.npc(BOUTIQUE[0] + 2, 1.0, BOUTIQUE[1] + 12);
        this.pavilionNpc(202.5);
        this.pavilionNpc(292.5);
        this.npc(PARKOUR[0] + 3, 1.0, PARKOUR[1] + 12);
        this.pavilionNpc(247.5);
        // the spectate NPC stands on the tournament stage (the same spot as its place in the list above)
        this.feature("spectate", stageX, 2.0, stageZ, 0, 0);
        this.feature("ffa", FFA[0], 1.0, FFA[1], 0, 0);
        this.feature("hall", HALL[0], 3.0, HALL[1] - 4, HALL[0], HALL[1] + 20);
        this.feature("leaderboard", HALL[0] - 12, 3.0, z1 + 4, HALL[0] - 12, 0);
        this.feature("leaderboard_2", HALL[0] + 12, 3.0, z1 + 4, HALL[0] + 12, 0);
        this.feature("stage", STAGE[0], 2.0, STAGE[1], 0, 0);
        this.feature("monument", 0, 9.0, 0, 0, 30);
        this.launchPads();
        // glowing motes rise out of the chasm, sparks round the crystal heart
        int k = 0;
        for (int x = -150; x <= 150; x += 15) {
            double cz = this.crackZ(x);
            if (this.crack(x, cz) < -2.0 && !this.nearBridge(x, 6)) {
                this.feature("fx:chasm:" + k++, x, -6.0, (int)Math.round(cz), 0, 0);
            }
        }

        this.feature("fx:heart", 0, 5.0, 0, 0, 0);
    }

    /** Gold plates on the four roads that throw you to the next district: over the chasm to the hall, to the FFA
     *  colosseum, the training yard and the arrival. */
    private void launchPads() {
        int[][] pads = {{0, -46, 0, -84}, {36, 0, 74, 0}, {-36, 0, -76, 0}, {0, 36, 0, 74}};
        String[] names = {"hall", "ffa", "training", "arrival"};
        for (int i = 0; i < pads.length; i++) {
            int[] p = pads[i];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    this.set(p[0] + dx, 0, p[1] + dz, dx == 0 && dz == 0 ? "gold_block" : "polished_blackstone_bricks");
                }
            }

            this.set(p[0], 1, p[1], "light_weighted_pressure_plate");
            this.feature("pad:" + names[i], p[0], 1.0, p[1], p[2], p[3]);
            this.feature("padto:" + names[i], p[2], 1.0, p[3], p[0], p[1]);
        }
    }
}
