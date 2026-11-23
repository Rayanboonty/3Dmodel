package net.cmcpvp.build;

/**
 * Court arenas (3.10.0): a flat fighting floor closed by a low wall or terrace, inside a big detailed build that is
 * all backdrop - castle wings with gothic windows and red roofs, a palace with arcades and onion domes, a colosseum
 * with stands, columns and a giant statue, plazas in front of cliffs and forests, a gothic harbour town. Players fight
 * on the floor only: an invisible barrier rises over the floor edge, so the backdrop can't be climbed.
 *
 * <p>The long axis is x: spawn 0 is on the west half looking east at the main building, spawn 1 on the east half
 * looking west at the second one. Local coordinates: floor surface y = 0, players stand on y = 1.
 */
public final class CourtArenaDesign extends CourtParts implements ArenaVoxels {
    public enum Layout {
        CASTLE,
        PALACE,
        COLOSSEUM,
        PLAZA,
        GOTHIC,
        BEACH,
        TEMPLE,
        VOLCANO,
        COVE,
        TOMB
    }

    public enum Style {
        ROYAL_COURTYARD("Royal Courtyard", Layout.CASTLE),
        FROSTHOLD("Frosthold Keep", Layout.CASTLE),
        SULTAN_PALACE("Sultan's Palace", Layout.PALACE),
        TITAN_COLOSSEUM("Titan Colosseum", Layout.COLOSSEUM),
        HIGHLAND_PLAZA("Highland Plaza", Layout.PLAZA),
        SAKURA_TERRACE("Sakura Terrace", Layout.PLAZA),
        EMBER_SANDS("Ember Sands", Layout.PLAZA),
        GOTHIC_HARBOR("Gothic Harbor", Layout.GOTHIC),
        TROPICAL_BEACH("Tropical Beach", Layout.BEACH),
        JUNGLE_TEMPLE("Jungle Temple", Layout.TEMPLE),
        VOLCANO_FORGE("Volcano Forge", Layout.VOLCANO),
        PIRATE_COVE("Pirate Cove", Layout.COVE),
        PHARAOH_TOMB("Pharaoh's Tomb", Layout.TOMB);

        public final String display;
        public final Layout layout;

        Style(String display, Layout layout) {
            this.display = display;
            this.layout = layout;
        }
    }

    public final Style style;
    private final long seed;
    private final int depth;
    private final boolean ffa;
    private final boolean rect;
    /** Half sizes of the fighting floor: rectangle hx by hz, or oval half-axes a by b. */
    private final int hx;
    private final int hz;
    private final double a;
    private final double b;

    /** Extra landscape round the build (3.15.0): mountains, forests and set pieces out to the island's edge. */
    public static final int OUTSKIRTS = 56;

    /** How many blocks of landscape fit round a court arena of this size inside its 512-block slot. */
    public static int outskirts(int size) {
        int r0 = Math.max(64, (size - 1) / 2);
        return Math.max(0, Math.min(OUTSKIRTS, 255 - r0));
    }

    /** The radius of the build itself (the old footprint); the landscape reaches out from here to R. */
    private final int inner;

    public CourtArenaDesign(Style style, int size, long seed, int cx, int floorY, int cz, int depth, boolean ffa, boolean barriers) {
        super(cx, floorY, cz, Math.max(64, (size - 1) / 2) + outskirts(size), -depth, 112, seed);
        this.inner = Math.max(64, (size - 1) / 2);
        this.style = style;
        this.seed = seed;
        this.depth = depth;
        this.ffa = ffa;
        Layout l = style.layout;
        this.rect = l == Layout.CASTLE || l == Layout.PALACE || l == Layout.GOTHIC || l == Layout.TEMPLE || l == Layout.TOMB;
        this.hx = (int)Math.round(Math.max(28.0, Math.min(72.0, this.inner * 0.5)));
        this.hz = (int)Math.round(this.hx * 0.7);
        this.a = Math.max(28.0, Math.min(70.0, this.inner * 0.53));
        this.b = Math.round(this.a * 0.78);
        this.base();
        switch (l) {
            case CASTLE -> this.castle();
            case PALACE -> this.palace();
            case COLOSSEUM -> this.colosseum();
            case PLAZA -> this.plaza();
            case GOTHIC -> this.gothic();
            case BEACH -> this.beach();
            case TEMPLE -> this.temple();
            case VOLCANO -> this.volcano();
            case COVE -> this.cove();
            case TOMB -> this.tomb();
        }

        this.outskirts();
        this.barrierRing();
        this.finish();
    }

    @Override
    public String name() {
        return this.style.display;
    }

    private boolean frost() {
        return this.style == Style.FROSTHOLD;
    }

    // ------------------------------------------------------------------ geometry

    /** Signed distance (blocks) from the edge of the fighting floor; negative on the floor. */
    public double sd(double x, double z) {
        if (this.rect) {
            double dx = Math.abs(x) - this.hx;
            double dz = Math.abs(z) - this.hz;
            double ox = Math.max(dx, 0.0);
            double oz = Math.max(dz, 0.0);
            return Math.sqrt(ox * ox + oz * oz) + Math.min(Math.max(dx, dz), 0.0);
        }

        double e = Math.sqrt(x * x / (this.a * this.a) + z * z / (this.b * this.b));
        if (e < 1.0E-6) {
            return -Math.min(this.a, this.b);
        }

        double g = Math.sqrt(x * x / (this.a * this.a * this.a * this.a) + z * z / (this.b * this.b * this.b * this.b)) / e;
        return (e - 1.0) / g;
    }

    /** Facing name pointing away from the floor at (x, z). */
    private String outward(double x, double z) {
        double gx = this.sd(x + 0.5, z) - this.sd(x - 0.5, z);
        double gz = this.sd(x, z + 0.5) - this.sd(x, z - 0.5);
        return facing(gx, gz);
    }

    /** Position along the floor edge (blocks), for spacing pillars, bays and aisles. */
    private double along(double x, double z) {
        if (this.rect) {
            return Math.abs(x) - this.hx > Math.abs(z) - this.hz ? z + (x > 0 ? 1000 : 3000) : x + (z > 0 ? 2000 : 0);
        }

        double t = Math.atan2(z / this.b, x / this.a);
        return t * (this.a + this.b) / 2.0;
    }

    private int spawnX() {
        return (int)Math.round((this.rect ? this.hx : this.a) * 0.62);
    }

    @Override
    public int[] spawn(int side) {
        int sp = this.spawnX();
        return new int[]{side == 0 ? -sp : sp, 1, 0};
    }

    @Override
    public int[] ffaSpot(int i, int n) {
        double t = Math.PI * 2.0 * i / n;
        double ax = (this.rect ? this.hx : this.a) * 0.66;
        double bz = (this.rect ? this.hz : this.b) * 0.66;
        return new int[]{(int)Math.round(Math.cos(t) * ax), 1, (int)Math.round(Math.sin(t) * bz)};
    }

    /** Floor spots that must stay dry and clear: the spawns, the FFA ring and the middle. */
    private boolean keepClear(int x, int z) {
        int sp = this.spawnX();
        if (Math.abs(Math.abs(x) - sp) <= 5 && Math.abs(z) <= 5 || Math.abs(x) <= 5 && Math.abs(z) <= 5) {
            return true;
        }

        for (int i = 0; this.ffa && i < 12; i++) {
            int[] s = this.ffaSpot(i, 12);
            if (Math.abs(s[0] - x) <= 2 && Math.abs(s[2] - z) <= 2) {
                return true;
            }
        }

        return this.sd(x, z) > -3.0;
    }

    // ------------------------------------------------------------------ ground

    private String subBlock() {
        return switch (this.style) {
            case SULTAN_PALACE, TITAN_COLOSSEUM, EMBER_SANDS, GOTHIC_HARBOR, TROPICAL_BEACH, PIRATE_COVE, PHARAOH_TOMB -> "sandstone";
            case VOLCANO_FORGE -> "blackstone";
            default -> "dirt";
        };
    }

    /** The ground outside the build (and under it). */
    private String groundBlock(int x, int z) {
        double n = noise(this.seed + 31L, x * 1.3, z * 1.3);
        return switch (this.style) {
            case FROSTHOLD -> n > 0.8 ? "packed_ice" : "snow_block";
            case SULTAN_PALACE, EMBER_SANDS -> n > 0.8 ? "sandstone" : "sand";
            case GOTHIC_HARBOR -> n > 0.7 ? "coarse_dirt" : "grass_block";
            case TROPICAL_BEACH, PIRATE_COVE -> n > 0.85 ? "sandstone" : "sand";
            case PHARAOH_TOMB -> n > 0.8 ? "sandstone" : "sand";
            case VOLCANO_FORGE -> n > 0.7 ? "basalt" : (n < 0.2 ? "smooth_basalt" : "blackstone");
            case JUNGLE_TEMPLE -> n > 0.75 ? "moss_block" : (n < 0.12 ? "coarse_dirt" : "grass_block");
            case SAKURA_TERRACE -> n > 0.85 ? "pink_petals_ground" : "grass_block";
            default -> n > 0.86 ? "coarse_dirt" : (n < 0.1 ? "podzol" : "grass_block");
        };
    }

    /** Solid ground under every column of the footprint, bedrock at the bottom; the floor pattern on top. */
    private void base() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                this.set(x, -this.depth, z, "bedrock");
                for (int y = -this.depth + 1; y < 0; y++) {
                    String s;
                    if (y >= -3) {
                        s = this.subBlock();
                    } else {
                        double v = noise(this.seed + 41L, x * 2 + y, z * 2 - y);
                        s = y < -this.depth + 4 ? "deepslate" : (v > 0.74 ? "andesite" : (v < 0.2 ? "tuff" : "stone"));
                    }

                    this.set(x, y, z, s);
                }

                double d = this.sd(x, z);
                String g = this.groundBlock(x, z);
                if (g.equals("pink_petals_ground")) {
                    g = "grass_block";
                    this.set(x, 1, z, "pink_petals[flower_amount=" + (1 + (int)(hash3(this.seed, x, 1, z) * 4)) + ",facing=north]");
                }

                this.set(x, 0, z, d < 0.0 ? this.floorBlock(x, z) : g);
            }
        }

        this.floorFeatures();
    }

    /** The fighting floor. */
    private String floorBlock(int x, int z) {
        double n = noise(this.seed + 11L, x * 1.9, z * 1.9);
        double m = noise(this.seed + 23L, x * 0.8, z * 0.8);
        switch (this.style) {
            case ROYAL_COURTYARD:
                return n > 0.83 ? "moss_block" : "grass_block";
            case FROSTHOLD:
                return n > 0.78 ? "packed_ice" : (m > 0.75 ? "powder_snow_safe" : "snow_block");
            case SULTAN_PALACE:
                return n > 0.82 ? "smooth_sandstone" : "sand";
            case TITAN_COLOSSEUM:
                return n > 0.85 ? "sandstone" : "sand";
            case GOTHIC_HARBOR:
                return this.harborFloor(x, z);
            case TROPICAL_BEACH: {
                double r = Math.sqrt(x * x + z * z);
                double ang = Math.atan2(z, x);
                if (r < 2.2) {
                    return "orange_terracotta";
                }

                if (r < 4.5) {
                    return "yellow_terracotta";
                }

                if (r < 9 && Math.abs(Math.sin(ang * 6)) > 0.88) {
                    return "orange_terracotta";
                }

                if (r < 9.6 && r > 8.6) {
                    return "yellow_terracotta";
                }

                return n > 0.8 ? "smooth_sandstone" : (hash3(this.seed, x, 0, z) < 0.012 ? "pink_terracotta" : "sand");
            }
            case JUNGLE_TEMPLE: {
                double r = Math.sqrt(x * x + z * z);
                if (r < 7.5) {
                    int ring = (int)r;
                    return ring == 6 || ring == 3 ? "chiseled_stone_bricks" : (ring == 0 ? "gold_block" : (ring % 2 == 0 ? "stone_bricks" : "mossy_stone_bricks"));
                }

                if (Math.floorMod(x, 8) == 0 && Math.floorMod(z, 8) == 0) {
                    return "chiseled_stone_bricks";
                }

                return n > 0.72 ? "moss_block" : (m > 0.7 ? "mossy_stone_bricks" : (n < 0.18 ? "cracked_stone_bricks" : (m < 0.3 ? "mossy_cobblestone" : "stone_bricks")));
            }
            case VOLCANO_FORGE: {
                double c = noise(this.seed + 77L, x * 1.6, z * 1.6);
                if (Math.abs(c - 0.5) < 0.009 && noise(this.seed + 79L, x * 3.0, z * 3.0) > 0.4) {
                    return "shroomlight";
                }

                return n > 0.7 ? "polished_blackstone" : (m > 0.72 ? "basalt" : (n < 0.2 ? "polished_blackstone_bricks" : "blackstone"));
            }
            case PIRATE_COVE: {
                if (Math.abs(z) <= 1 && Math.abs(x) < this.a * 0.85) {
                    return Math.abs(z) == 1 ? "stripped_spruce_wood[axis=x]" : "oak_planks";
                }

                return n > 0.8 ? "coarse_dirt" : (m > 0.78 ? "smooth_sandstone" : "sand");
            }
            case PHARAOH_TOMB: {
                double r = Math.sqrt(x * x + z * z);
                if (r < 2.5) {
                    return "gold_block";
                }

                if (r < 3.5) {
                    return "orange_terracotta";
                }

                if (r < 6 && Math.abs(Math.sin(Math.atan2(z, x) * 8)) > 0.8) {
                    return "yellow_terracotta";
                }

                if (r > 5.9 && r < 6.8) {
                    return "lapis_block";
                }

                if (Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0) {
                    return Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0 ? "chiseled_sandstone" : "cut_sandstone";
                }

                return n > 0.8 ? "sandstone" : "smooth_sandstone";
            }
            case HIGHLAND_PLAZA:
                return this.swirlFloor(x, z, "smooth_stone", "stone", "cobblestone", "andesite", "chiseled_stone_bricks");
            case SAKURA_TERRACE:
                return this.swirlFloor(x, z, "polished_diorite", "calcite", "pink_terracotta", "white_terracotta", "cherry_planks");
            case EMBER_SANDS:
                return this.inlayFloor(x, z);
            default:
                return "sand";
        }
    }

    /** Curling bands (like a paved square): light base, darker curls, a ring of slabs near the wall. */
    private String swirlFloor(int x, int z, String base, String alt, String curl, String curl2, String accent) {
        double d = this.sd(x, z);
        if (d > -2.0) {
            return curl2;
        }

        if (d > -3.0) {
            return alt;
        }

        double r = Math.sqrt(x * x + z * z);
        double ang = Math.atan2(z, x);
        if (r < 3.2) {
            return accent;
        }

        if (r < 4.5) {
            return curl;
        }

        double p = Math.sin(r * 0.55 + 2.4 * Math.sin(ang * 3.0 + r * 0.12));
        double q = Math.sin(ang * 6.0 + r * 0.25);
        if (Math.abs(p) < 0.24) {
            return curl;
        }

        if (Math.abs(p) < 0.4 && q > 0.1) {
            return curl2;
        }

        double n = hash3(this.seed + 5L, x, 0, z);
        return n < 0.12 ? alt : base;
    }

    /** Sand and smooth sandstone with a dark geometric inlay (two crossed squares in a ring) in the middle. */
    private String inlayFloor(int x, int z) {
        double d = this.sd(x, z);
        if (d > -2.0) {
            return "polished_blackstone_bricks";
        }

        int ax = Math.abs(x);
        int az = Math.abs(z);
        double r = Math.sqrt(x * x + z * z);
        int man = ax + az;
        int cheb = Math.max(ax, az);
        double ring = this.b * 0.62;
        if (Math.abs(r - ring) < 0.8 || Math.abs(r - ring - 2.5) < 0.6) {
            return "polished_blackstone";
        }

        if (r < ring) {
            int s = (int)Math.round(ring * 0.7);
            if (cheb == s || man == (int)Math.round(s * 1.41)) {
                return "polished_blackstone";
            }

            if (cheb == s - 2 || man == (int)Math.round(s * 1.41) - 3) {
                return "deepslate_tiles";
            }

            if (cheb < s - 2 && man < s * 1.41 - 3) {
                return (x + z & 1) == 0 ? "smooth_sandstone" : "cut_sandstone";
            }
        }

        // long diagonal bands toward the ends, like paths
        if (Math.floorMod(x + z, 14) == 0 || Math.floorMod(x - z, 14) == 0) {
            return r > ring + 3.5 ? "smooth_sandstone" : "sand";
        }

        double n = hash3(this.seed + 7L, x, 0, z);
        return n < 0.1 ? "sandstone" : "sand";
    }

    /** Sand with tiles of smooth sandstone and pale stone, around shallow pools. */
    private String harborFloor(int x, int z) {
        double n = noise(this.seed + 61L, x * 1.5, z * 1.5);
        if (Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0) {
            return "polished_diorite";
        }

        return n > 0.72 ? "smooth_sandstone" : (n < 0.18 ? "white_concrete_powder" : "sand");
    }

    /** Pools and other features cut into the floor (shallow, one block of water). */
    private void floorFeatures() {
        switch (this.style) {
            // 3.11.0: no water on the floors any more: patterned patches where the pools were
            case FROSTHOLD -> this.pools(2, 4.0, "packed_ice", "packed_ice", "snow_block", false);
            case TITAN_COLOSSEUM -> this.pools(3, 4.2, "smooth_sandstone", "sandstone", "cut_sandstone", false);
            case GOTHIC_HARBOR -> this.pools(3, 5.0, "dark_prismarine", "prismarine", "prismarine_bricks", false);
            default -> {
            }
        }

        // fix the snow floor placeholder
        if (this.frost()) {
            for (int x = -this.R; x <= this.R; x++) {
                for (int z = -this.R; z <= this.R; z++) {
                    if ("powder_snow_safe".equals(this.get(x, 0, z))) {
                        this.set(x, 0, z, "white_concrete_powder");
                    }
                }
            }
        }
    }

    /** {@code n} irregular shallow pools on the floor, away from the spawns, the middle and the edge. */
    private void pools(int n, double r, String liquid, String bottom, String rim, boolean lilies) {
        java.util.Random rr = new java.util.Random(this.seed ^ 0x5EEDL);
        int made = 0;
        for (int tries = 0; tries < 60 && made < n; tries++) {
            double ex = this.rect ? this.hx : this.a * 0.8;
            double ez = this.rect ? this.hz : this.b * 0.8;
            int px = (int)Math.round((rr.nextDouble() * 2 - 1) * ex * 0.8);
            int pz = (int)Math.round((rr.nextDouble() * 2 - 1) * ez * 0.75);
            double pr = r * (0.75 + rr.nextDouble() * 0.5);
            boolean ok = true;
            for (int x = (int)(px - pr - 2); x <= px + pr + 2 && ok; x++) {
                for (int z = (int)(pz - pr - 2); z <= pz + pr + 2 && ok; z++) {
                    if (Math.hypot(x - px, z - pz) <= pr + 1.5 && this.keepClear(x, z)) {
                        ok = false;
                    }
                }
            }

            if (!ok) {
                continue;
            }

            made++;
            long ps = rr.nextLong();
            for (int x = (int)(px - pr - 3); x <= px + pr + 3; x++) {
                for (int z = (int)(pz - pr - 3); z <= pz + pr + 3; z++) {
                    double d = Math.hypot((x - px) * 1.0, (z - pz) * 1.3) - pr * (0.75 + 0.5 * noise(ps, x * 2.0, z * 2.0));
                    if (d < 0.0) {
                        this.set(x, 0, z, liquid);
                        this.set(x, -1, z, bottom);
                        if (lilies && hash3(ps, x, 1, z) < 0.04) {
                            this.set(x, 1, z, "lily_pad");
                        }
                    } else if (d < 1.0 && !liquid.endsWith("ice")) {
                        this.set(x, 0, z, rim);
                    }
                }
            }
        }
    }

    /** Invisible wall over the floor edge, so nobody climbs out into the build. */
    private void barrierRing() {
        int ex = (this.rect ? this.hx : (int)Math.ceil(this.a)) + 3;
        int ez = (this.rect ? this.hz : (int)Math.ceil(this.b)) + 3;
        for (int x = -ex; x <= ex; x++) {
            for (int z = -ez; z <= ez; z++) {
                double d = this.sd(x, z);
                if (d >= 0.0 && d < 1.0) {
                    for (int y = 1; y <= 48; y++) {
                        if (this.empty(x, y, z)) {
                            this.set(x, y, z, "barrier");
                        }
                    }
                }
            }
        }
    }

    // ================================================================== OUTSKIRTS (3.15.0)

    /**
     * The landscape of a style round its build: top and under blocks, the height of its peaks, its trees (0 oak and
     * birch, 1 snowy spruce, 2 palms, 3 cherry, 4 dead, 5 dark oak, 6 jungle, 7 oak and cypress, 8 spruce), flat-topped
     * mesas or not, and where snow starts.
     */
    private record Land(String top, String under, int peak, int trees, boolean mesa, int snowLine) {
    }

    private Land land() {
        return switch (this.style) {
            case ROYAL_COURTYARD -> new Land("grass_block", "dirt", 70, 0, false, 52);
            case FROSTHOLD -> new Land("snow_block", "dirt", 76, 1, false, 0);
            case SULTAN_PALACE -> new Land("sand", "sandstone", 48, 2, true, 999);
            case TITAN_COLOSSEUM -> new Land("grass_block", "dirt", 60, 7, false, 999);
            case HIGHLAND_PLAZA -> new Land("grass_block", "dirt", 78, 8, false, 54);
            case SAKURA_TERRACE -> new Land("grass_block", "dirt", 72, 3, false, 62);
            case EMBER_SANDS -> new Land("red_sand", "red_sandstone", 58, 4, true, 999);
            case GOTHIC_HARBOR -> new Land("grass_block", "dirt", 64, 5, false, 999);
            case TROPICAL_BEACH -> new Land("grass_block", "dirt", 54, 2, false, 999);
            case JUNGLE_TEMPLE -> new Land("grass_block", "dirt", 70, 6, false, 999);
            case VOLCANO_FORGE -> new Land("blackstone", "basalt[axis=y]", 74, 4, false, 999);
            case PIRATE_COVE -> new Land("grass_block", "dirt", 58, 2, false, 999);
            case PHARAOH_TOMB -> new Land("sand", "sandstone", 44, 2, true, 999);
        };
    }

    private static double ease(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    /** A rounded-square distance: the landscape follows the square footprint with soft corners. */
    private static double sq(double x, double z) {
        return Math.pow(Math.pow(Math.abs(x), 6.0) + Math.pow(Math.abs(z), 6.0), 1.0 / 6.0);
    }

    /** Layered rock for the mountains of each style. */
    private String outRock(int x, int y, int z) {
        double w = noise(this.seed + 611L, x * 1.4, z * 1.4) * 4.0;
        int band = Math.floorMod((int)Math.floor((y + w) / 3.0), 6);
        double r = hash3(this.seed + 613L, x, y, z);
        return switch (this.style) {
            case SULTAN_PALACE, PHARAOH_TOMB -> band == 2 ? "smooth_sandstone" : (band == 4 ? "cut_sandstone" : (r < 0.1 ? "smooth_sandstone" : "sandstone"));
            case EMBER_SANDS -> new String[]{"terracotta", "orange_terracotta", "red_terracotta", "yellow_terracotta", "white_terracotta", "brown_terracotta"}[band];
            case VOLCANO_FORGE -> band % 3 == 0 ? "blackstone" : (r < 0.3 ? "smooth_basalt" : "basalt[axis=y]");
            case FROSTHOLD -> band == 3 ? "packed_ice" : (r < 0.2 ? "andesite" : "stone");
            case JUNGLE_TEMPLE, TROPICAL_BEACH -> r < 0.25 ? "mossy_cobblestone" : (band == 2 ? "andesite" : "stone");
            case SAKURA_TERRACE -> band == 1 ? "calcite" : (r < 0.2 ? "andesite" : "stone");
            default -> band == 1 ? "andesite" : (band == 4 ? "tuff" : (r < 0.08 ? "cobblestone" : "stone"));
        };
    }

    /** Clearings in the landscape for the set pieces: {x, z, radius}; 0 and 1 off the long sides, 2-5 off the corners. */
    private int[][] pads = new int[0][];

    private double padDist(int x, int z) {
        double best = 1.0E9;
        for (int[] p : this.pads) {
            best = Math.min(best, Math.hypot(x - p[0], z - p[1]) - p[2]);
        }

        return best;
    }

    /**
     * The landscape round the build (3.15.0): mountains (or mesas, dunes, volcanic peaks) rising out to a floating
     * island's edge with an eroded underside, forests of the style's trees, clearings with set pieces (villages,
     * watchtowers, windmills, tents, temples, pyramids...) and small islands floating in the sky. Nothing changes on or
     * round the fighting floor: all of it is behind the build.
     */
    private void outskirts() {
        int out = this.R - this.inner;
        if (out < 12) {
            return;
        }

        Land land = this.land();
        boolean flat = this.style.layout != Layout.PLAZA && this.style.layout != Layout.COVE;
        if (flat) {
            int d = this.inner + 20;
            int c = this.inner + 12;
            this.pads = new int[][]{{0, -d, 11}, {0, d, 11}, {-c, -c, 10}, {c, -c, 10}, {-c, c, 10}, {c, c, 10}};
        }

        int n = 2 * this.R + 1;
        int skip = Integer.MIN_VALUE;
        int none = Integer.MIN_VALUE + 1;
        int[][] hm = new int[n][n];
        double[][] edges = new double[n][n];
        double from = this.inner - 6;
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double q = sq(x, z);
                if (q < from) {
                    hm[x + this.R][z + this.R] = skip;
                    continue;
                }

                double ang = Math.atan2(z, x);
                double edge = this.R - 3 - noise(this.seed + 601L, Math.cos(ang) * 24.0, Math.sin(ang) * 24.0) * 16.0;
                edges[x + this.R][z + this.R] = edge;
                if (q > edge) {
                    hm[x + this.R][z + this.R] = none;
                    continue;
                }

                double t = ease((q - from) / (out * 0.62));
                double ridge = 1.0 - Math.abs(noise(this.seed + 603L, x * 0.9, z * 0.9) * 2.0 - 1.0);
                double m = t * (land.peak() * 0.85 * (0.4 + 0.6 * ridge) + noise(this.seed + 605L, x * 2.5, z * 2.5) * 7.0);
                if (land.mesa()) {
                    m = Math.floor((m + noise(this.seed + 607L, x * 1.2, z * 1.2) * 3.0) / 6.0) * 6.0;
                }

                m *= 1.0 - 0.8 * ease((q - (edge - 20.0)) / 20.0);
                m -= Math.max(0.0, q - (edge - 3.0)) * 3.0;
                if (flat) {
                    double pd = this.padDist(x, z);
                    if (pd <= 0.0) {
                        m = 0.0;
                    } else if (pd < 9.0) {
                        m *= ease(pd / 9.0);
                    }
                } else if (this.heights != null) {
                    // the cliffs of a plaza or a cove go on into the mountains
                    int bx = Math.max(-this.inner, Math.min(this.inner, x));
                    int bz = Math.max(-this.inner, Math.min(this.inner, z));
                    double plateau = this.heights[bx + this.R][bz + this.R];
                    m = Math.max(m + plateau * 0.6, plateau * (1.0 - 0.5 * ease((q - this.inner) / 30.0)));
                    m -= Math.max(0.0, q - (edge - 3.0)) * 3.0;
                }

                hm[x + this.R][z + this.R] = Math.max(0, Math.min(this.yHi - 16, (int)Math.round(m)));
            }
        }

        // the ground, column by column
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int h = hm[x + this.R][z + this.R];
                if (h == skip) {
                    continue;
                }

                if (h == none) {
                    for (int y = this.yLo; y <= this.yHi; y++) {
                        this.set(x, y, z, null);
                    }

                    continue;
                }

                double q = sq(x, z);
                if (q <= this.inner) {
                    // still inside the old footprint: only raise the ground, never cut the build
                    String g = this.get(x, h, z);
                    if (h <= 3 || g != null) {
                        continue;
                    }
                }

                for (int y = 1; y <= this.yHi; y++) {
                    this.set(x, y, z, null);
                }

                int drop = 0;
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ix = x + o[0] + this.R;
                    int iz = z + o[1] + this.R;
                    if (ix >= 0 && iz >= 0 && ix < n && iz < n && hm[ix][iz] >= 0) {
                        drop = Math.max(drop, h - hm[ix][iz]);
                    }
                }

                boolean snowy = h >= land.snowLine();
                for (int y = 0; y <= h; y++) {
                    String b;
                    if (y == h) {
                        b = snowy && drop < 5 ? "snow_block" : (drop >= 5 ? this.outRock(x, y, z) : land.top());
                    } else if (h - y <= 2 && drop < 5) {
                        b = snowy ? "snow_block" : land.under();
                    } else {
                        b = this.outRock(x, y, z);
                    }

                    this.set(x, y, z, b);
                }

                if (snowy && drop < 3 && hash3(this.seed + 615L, x, h, z) < 0.4) {
                    this.set(x, h + 1, z, "snow[layers=" + (1 + (int)(hash3(this.seed + 617L, x, h, z) * 3)) + "]");
                }

                // the underside gets shallower toward the edge, with spikes hanging under it
                double edge = edges[x + this.R][z + this.R];
                double rim = ease((q - (edge - 22.0)) / 22.0);
                int bottom = (int)Math.round(4 + (1.0 - rim) * (this.depth - 4) + Math.pow(noise(this.seed + 609L, x * 2.4, z * 2.4), 3.0) * 10.0 * (1.0 - rim));
                bottom = Math.max(3, Math.min(this.depth, bottom));
                for (int y = -this.depth; y < -bottom; y++) {
                    this.set(x, y, z, null);
                }

                if ("bedrock".equals(this.get(x, -bottom, z))) {
                    this.set(x, -bottom, z, "deepslate");
                }

                for (int y = -bottom; y < 0; y++) {
                    if (this.empty(x, y, z)) {
                        this.set(x, y, z, this.outRock(x, y, z));
                    }
                }
            }
        }

        java.util.Random rr = new java.util.Random(this.seed ^ 0x0575L);
        // forests, rocks, flowers
        for (int gx = -this.R; gx <= this.R; gx += 5) {
            for (int gz = -this.R; gz <= this.R; gz += 5) {
                int x = gx + rr.nextInt(5) - 2;
                int z = gz + rr.nextInt(5) - 2;
                double roll = rr.nextDouble();
                double kind = rr.nextDouble();
                int size = rr.nextInt(6);
                if (x < -this.R || x > this.R || z < -this.R || z > this.R || sq(x, z) <= this.inner + 2) {
                    continue;
                }

                int h = hm[x + this.R][z + this.R];
                if (h < 0 || flat && this.padDist(x, z) < 2.0 || !this.empty(x, h + 1, z)) {
                    continue;
                }

                String top = this.get(x, h, z);
                if (top == null || !(top.equals(land.top()) || top.equals("snow_block"))) {
                    continue;
                }

                double forest = noise(this.seed + 621L, x * 0.8, z * 0.8);
                double density = 0.35 + 0.55 * ease((forest - 0.38) / 0.25);
                if (land.trees() == 2 || land.trees() == 4) {
                    density *= 0.45;
                }

                if (roll < density) {
                    this.outTree(land.trees(), x, h + 1, z, size, kind, h >= land.snowLine());
                } else if (roll > 0.94) {
                    this.blob(x, h + 1, z, 1.3 + kind, this.outRock(x, h, z));
                }
            }
        }

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int h = hm[x + this.R][z + this.R];
                if (h < 0 || sq(x, z) <= this.inner || !"grass_block".equals(this.get(x, h, z)) || !this.empty(x, h + 1, z)) {
                    continue;
                }

                double v = hash3(this.seed + 623L, x, h, z);
                if (v < 0.14) {
                    this.set(x, h + 1, z, "short_grass");
                } else if (v < 0.17) {
                    String[] fl = this.style == Style.SAKURA_TERRACE ? new String[]{"pink_tulip", "allium", "lily_of_the_valley"}
                        : new String[]{"poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet"};
                    this.set(x, h + 1, z, fl[(int)(hash3(this.seed + 625L, x, h, z) * fl.length)]);
                } else if (v < 0.18 && this.style == Style.SAKURA_TERRACE) {
                    this.set(x, h + 1, z, "pink_petals[flower_amount=3,facing=north]");
                }
            }
        }

        if (flat) {
            this.padPieces();
        }

        this.skyIslets(land, rr);
    }

    private void outTree(int k, int x, int y, int z, int size, double kind, boolean snowy) {
        if (snowy && k != 2 && k != 4) {
            this.spruce(x, y, z, 7 + size, true);
            return;
        }

        switch (k) {
            case 0 -> this.broadleaf(x, y, z, kind < 0.3 ? "birch_log" : "oak_log", kind < 0.3 ? "birch" : "oak", 4 + size % 3, 2.6 + size * 0.15);
            case 1 -> this.spruce(x, y, z, 7 + size, true);
            case 2 -> this.palmTree(x, y, z);
            case 3 -> {
                if (kind < 0.7) {
                    this.cherryTree(x, y, z);
                } else {
                    this.broadleaf(x, y, z, "oak_log", "flowering_azalea", 3, 2.4);
                }
            }
            case 4 -> this.deadTree(x, y, z, kind < 0.5 ? "dark_oak_log" : "oak_log", 5 + size);
            case 5 -> this.broadleaf(x, y, z, "dark_oak_log", "dark_oak", 4 + size % 2, 3.0 + size * 0.1);
            case 6 -> {
                if (kind < 0.18) {
                    this.jungleGiant(x, y, z, 16 + size * 2);
                } else {
                    this.broadleaf(x, y, z, "jungle_log", "jungle", 5 + size % 3, 3.2);
                }
            }
            case 7 -> {
                if (kind < 0.45) {
                    this.conifer(x, y, z, "spruce_log", "spruce", 9 + size);
                } else {
                    this.broadleaf(x, y, z, "oak_log", "oak", 3 + size % 2, 2.4);
                }
            }
            default -> this.spruce(x, y, z, 7 + size, false);
        }
    }

    /** The set pieces in the clearings, by layout. */
    private void padPieces() {
        int[][] p = this.pads;
        switch (this.style.layout) {
            case CASTLE -> {
                boolean fr = this.frost();
                String roofStair = fr ? "spruce" : "dark_oak";
                String roofBody = fr ? "spruce_planks" : "dark_oak_planks";
                this.cottage(p[0][0], p[0][1], fr ? "spruce_planks" : "calcite", "stripped_dark_oak_log", roofStair, roofBody);
                this.cottage(p[1][0], p[1][1], fr ? "spruce_planks" : "calcite", "stripped_dark_oak_log", roofStair, roofBody);
                this.roundTower(p[2][0], p[2][1], 3.5, 20, 1);
                this.roundTower(p[5][0], p[5][1], 3.5, 20, 1);
                if (fr) {
                    this.iceSpikes(p[3][0], p[3][1]);
                    this.iceSpikes(p[4][0], p[4][1]);
                } else {
                    this.windmill(p[3][0], p[3][1]);
                    this.cottage(p[4][0], p[4][1], "calcite", "stripped_dark_oak_log", roofStair, roofBody);
                }
            }
            case PALACE -> {
                this.tents(p[0][0], p[0][1], "red", "white");
                this.tents(p[1][0], p[1][1], "orange", "white");
                this.domePavilion(p[2][0], p[2][1], 4, 9, 3.5);
                this.domePavilion(p[5][0], p[5][1], 4, 9, 3.5);
                this.obelisk(p[3][0], 1, p[3][1], 12);
                this.obelisk(p[4][0], 1, p[4][1], 12);
            }
            case COLOSSEUM -> {
                this.templeRuin(p[0][0], p[0][1], false);
                this.templeRuin(p[1][0], p[1][1], false);
                this.tents(p[2][0], p[2][1], "red", "white");
                this.tents(p[5][0], p[5][1], "red", "white");
                this.obelisk(p[3][0], 1, p[3][1], 11);
                this.obelisk(p[4][0], 1, p[4][1], 11);
            }
            case GOTHIC -> {
                this.castlePalette();
                this.roofMat = this.mixOf(105L, "deepslate_tiles", "cobbled_deepslate", 0.2, "polished_deepslate", 0.1);
                this.cottage(p[0][0], p[0][1], "spruce_planks", "dark_oak_log", "deepslate_tile", "deepslate_tiles");
                this.cottage(p[1][0], p[1][1], "spruce_planks", "dark_oak_log", "deepslate_tile", "deepslate_tiles");
                this.graveyard(p[2][0], p[2][1]);
                this.graveyard(p[5][0], p[5][1]);
                this.roundTower(p[3][0], p[3][1], 3.5, 22, 1);
                this.roundTower(p[4][0], p[4][1], 3.5, 22, 1);
            }
            case BEACH -> {
                this.stiltHut(p[0][0], 0, p[0][1], 3, 3, p[0][1] > 0 ? "north" : "south");
                this.stiltHut(p[1][0], 0, p[1][1], 3, 3, p[1][1] > 0 ? "north" : "south");
                for (int i = 2; i < 6; i++) {
                    this.beachCamp(p[i][0], p[i][1]);
                }
            }
            case TEMPLE -> {
                this.ruins(p[0][0], p[0][1], "mossy_stone_bricks", "cracked_stone_bricks");
                this.ruins(p[1][0], p[1][1], "mossy_stone_bricks", "cracked_stone_bricks");
                this.jungleGiant(p[2][0], 1, p[2][1], 22);
                this.jungleGiant(p[5][0], 1, p[5][1], 22);
                this.ruins(p[3][0], p[3][1], "mossy_cobblestone", "mossy_stone_bricks");
                this.ruins(p[4][0], p[4][1], "mossy_cobblestone", "mossy_stone_bricks");
            }
            case VOLCANO -> {
                for (int i = 0; i < 6; i++) {
                    if (i == 2 || i == 5) {
                        this.ruins(p[i][0], p[i][1], "polished_blackstone_bricks", "cracked_polished_blackstone_bricks");
                    } else {
                        this.spires(p[i][0], p[i][1]);
                    }
                }
            }
            case TOMB -> {
                this.tents(p[0][0], p[0][1], "white", "yellow");
                this.tents(p[1][0], p[1][1], "white", "orange");
                this.smallPyramid(p[2][0], p[2][1], 9);
                this.smallPyramid(p[5][0], p[5][1], 9);
                this.obelisk(p[3][0], 1, p[3][1], 12);
                this.obelisk(p[4][0], 1, p[4][1], 12);
            }
            default -> {
            }
        }
    }

    /** A cottage: a stone footing, timber frame and walls, windows, a door toward the arena, a gable roof, a chimney. */
    private void cottage(int x, int z, String wall, String frame, String roofStair, String roofBody) {
        boolean alongX = Math.abs(x) < Math.abs(z);
        int hw = alongX ? 4 : 3;
        int hd = alongX ? 3 : 4;
        for (int dx = -hw - 1; dx <= hw + 1; dx++) {
            for (int dz = -hd - 1; dz <= hd + 1; dz++) {
                this.set(x + dx, 0, z + dz, hash3(this.seed + 631L, x + dx, 0, z + dz) < 0.5 ? "coarse_dirt" : "gravel");
            }
        }

        for (int dx = -hw; dx <= hw; dx++) {
            for (int dz = -hd; dz <= hd; dz++) {
                boolean edge = Math.abs(dx) == hw || Math.abs(dz) == hd;
                boolean corner = Math.abs(dx) == hw && Math.abs(dz) == hd;
                this.set(x + dx, 1, z + dz, edge ? "cobblestone" : "spruce_planks");
                for (int y = 2; y <= 5; y++) {
                    if (!edge) {
                        this.set(x + dx, y, z + dz, null);
                        continue;
                    }

                    boolean window = !corner && y >= 3 && y <= 4 && (Math.abs(dz) == hd ? Math.floorMod(dx, 3) == 1 : Math.floorMod(dz, 3) == 1);
                    String b = corner ? frame + "[axis=y]" : (y == 5 ? frame + "[axis=" + (Math.abs(dz) == hd ? "x" : "z") + "]" : wall);
                    this.set(x + dx, y, z + dz, window ? "glass_pane" : b);
                }
            }
        }

        int ddx = alongX ? 0 : (x > 0 ? -hw : hw);
        int ddz = alongX ? (z > 0 ? -hd : hd) : 0;
        this.set(x + ddx, 2, z + ddz, null);
        this.set(x + ddx, 3, z + ddz, null);
        this.set(x + ddx + Integer.signum(ddx), 1, z + ddz + Integer.signum(ddz), "cobblestone_slab[type=bottom]");
        this.set(x + ddx + (alongX ? 1 : Integer.signum(ddx)), 3, z + ddz + (alongX ? Integer.signum(ddz) : 1), "lantern[hanging=false]");
        this.gableRoof(x - hw - 1, z - hd - 1, x + hw + 1, z + hd + 1, 6, alongX, roofStair, of(roofBody), of(wall));
        int chx = x + (alongX ? hw - 1 : 0);
        int chz = z + (alongX ? 0 : hd - 1);
        for (int y = 2; y <= 12; y++) {
            this.set(chx, y, chz, y > 9 ? "bricks" : (y > 5 ? "bricks" : this.get(chx, y, chz) == null ? null : "bricks"));
        }

        this.set(chx, 13, chz, "brick_wall");
        this.crates2(x + (alongX ? -hw - 2 : 0), z + (alongX ? 0 : -hd - 2));
    }

    private void crates2(int x, int z) {
        this.set(x, 1, z, "barrel[facing=up]");
        this.set(x + 1, 1, z, "hay_block[axis=y]");
        this.set(x, 2, z, "spruce_slab[type=bottom]");
    }

    /** A stone windmill with a wooden cap and four sails turned toward the arena. */
    private void windmill(int x, int z) {
        Mat st = this.mixOf(633L, "stone_bricks", "cobblestone", 0.3, "mossy_stone_bricks", 0.12);
        this.cyl(x, z, 3.4, 1, 13, st);
        this.cyl(x, z, 2.4, 2, 12, of("air"));
        for (int y = 2; y <= 12; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if ("air".equals(this.get(x + dx, y, z + dz))) {
                        this.set(x + dx, y, z + dz, null);
                    }
                }
            }
        }

        this.coneRoof(x, z, 4.0, 14, 6, of("spruce_planks"));
        boolean alongX = Math.abs(x) >= Math.abs(z);
        int f = alongX ? (x > 0 ? -1 : 1) : (z > 0 ? -1 : 1);
        int hx2 = x + (alongX ? f * 4 : 0);
        int hz2 = z + (alongX ? 0 : f * 4);
        int hy = 12;
        this.set(hx2, hy, hz2, "dark_oak_planks");
        int[][] arms = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] a : arms) {
            for (int k = 1; k <= 7; k++) {
                int u = a[0] * k;
                int v = a[1] * k;
                int sx = alongX ? hx2 : hx2 + u;
                int sz = alongX ? hz2 + u : hz2;
                int sy = hy + v;
                this.set(sx, sy, sz, "spruce_fence");
                if (k >= 2) {
                    int px = alongX ? hx2 : hx2 + u + a[1];
                    int pz = alongX ? hz2 + u + a[1] : hz2;
                    int py = sy - a[0];
                    if (this.empty(px, py, pz) && py > 1) {
                        this.set(px, py, pz, "white_wool");
                    }
                }
            }
        }
    }

    /** A camp: three striped tents round a fire, crates beside it. */
    private void tents(int x, int z, String c1, String c2) {
        for (int k = 0; k < 3; k++) {
            double a = k * 2.094 + 0.5;
            this.tent(x + (int)Math.round(Math.cos(a) * 5.5), z + (int)Math.round(Math.sin(a) * 5.5), k % 2 == 0 ? c1 : c2, c1.equals("white") ? c2 : "white");
        }

        this.set(x, 1, z, "campfire[lit=true,signal_fire=false]");
        this.set(x + 1, 1, z + 1, "barrel[facing=up]");
        this.set(x - 1, 1, z + 1, "spruce_slab[type=bottom]");
    }

    private void tent(int x, int z, String c, String stripe) {
        for (int k = 0; k <= 2; k++) {
            int hw = 2 - k;
            for (int dx = -hw; dx <= hw; dx++) {
                for (int dz = -hw - 1; dz <= hw + 1; dz++) {
                    boolean shell = Math.abs(dx) == hw || k == 2;
                    if (shell) {
                        this.set(x + dx, 1 + k, z + dz, Math.floorMod(dz, 2) == 0 ? c + "_wool" : stripe + "_wool");
                    }
                }
            }
        }

        this.set(x, 1, z - 3, null);
        this.set(x, 2, z - 3, null);
        this.set(x, 4, z, "spruce_fence");
        this.set(x, 5, z, c + "_banner[rotation=0]");
    }

    /** A temple ruin on a two-step platform: a ring of columns, some broken, a piece of the roof beam still up. */
    private void templeRuin(int x, int z, boolean mossy) {
        String base = mossy ? "mossy_stone_bricks" : "smooth_quartz";
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                this.set(x + dx, 1, z + dz, base);
                if (Math.abs(dx) <= 6 && Math.abs(dz) <= 4) {
                    this.set(x + dx, 2, z + dz, hash3(this.seed + 641L, x + dx, 2, z + dz) < 0.15 ? "cracked_stone_bricks" : base);
                }
            }
        }

        for (int dx = -6; dx <= 6; dx += 2) {
            for (int dz : new int[]{-4, 4}) {
                boolean intact = dz < 0 && Math.abs(dx) <= 4;
                int h = intact ? 8 : 1 + (int)(hash3(this.seed + 643L, x + dx, 0, z + dz) * 5);
                for (int y = 3; y < 3 + h; y++) {
                    this.set(x + dx, y, z + dz, mossy ? "mossy_stone_bricks" : "quartz_pillar[axis=y]");
                }

                if (intact) {
                    this.set(x + dx, 3 + h, z + dz, "chiseled_quartz_block");
                }
            }
        }

        for (int dx = -5; dx <= 5; dx++) {
            this.set(x + dx, 12, z - 4, "smooth_quartz");
            this.set(x + dx, 13, z - 4, dx == 0 ? "chiseled_quartz_block" : "quartz_slab[type=bottom]");
        }

        this.set(x + 3, 3, z + 2, "quartz_pillar[axis=x]");
        this.set(x + 4, 3, z + 2, "quartz_pillar[axis=x]");
        this.set(x - 9, 1, z + 3, "quartz_pillar[axis=z]");
    }

    /** A small graveyard: an iron fence, rows of stones, a dead tree and a lantern. */
    private void graveyard(int x, int z) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                boolean edge = Math.abs(dx) == 7 || Math.abs(dz) == 6;
                this.set(x + dx, 0, z + dz, hash3(this.seed + 651L, x + dx, 0, z + dz) < 0.4 ? "podzol" : "coarse_dirt");
                if (edge && !(Math.abs(dx) <= 1 && dz == (z > 0 ? -6 : 6))) {
                    this.set(x + dx, 1, z + dz, "iron_bars");
                }
            }
        }

        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 4) {
                double r = hash3(this.seed + 653L, x + dx, 0, z + dz);
                this.set(x + dx, 1, z + dz, r < 0.5 ? "cobblestone_wall" : "mossy_cobblestone_wall");
                if (r < 0.3) {
                    this.set(x + dx, 2, z + dz, "stone_button[face=floor,facing=north]");
                }
            }
        }

        this.deadTree(x + 5, 1, z + (z > 0 ? 4 : -4), "dark_oak_log", 6);
        this.lantern(x - 6, 1, z, "dark_oak_fence", 2, "soul_lantern[hanging=false]");
    }

    /** A beach camp: an umbrella, palms and a sandcastle on a patch of sand. */
    private void beachCamp(int x, int z) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.hypot(dx, dz) <= 6.5) {
                    this.set(x + dx, 0, z + dz, "sand");
                }
            }
        }

        this.umbrella(x, 1, z, (x > 0) == (z > 0) ? "red" : "yellow");
        this.palmTree(x + 4, 1, z - 3);
        this.palmTree(x - 4, 1, z + 3);
        this.set(x - 2, 1, z - 3, "sandstone");
        this.set(x - 2, 2, z - 3, "sandstone_wall");
    }

    /** A ruin: a broken square foundation with walls of different heights and two columns. */
    private void ruins(int x, int z, String a, String b) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                boolean edge = Math.abs(dx) == 6 || Math.abs(dz) == 6;
                if (!edge) {
                    if (hash3(this.seed + 661L, x + dx, 0, z + dz) < 0.35) {
                        this.set(x + dx, 0, z + dz, b);
                    }

                    continue;
                }

                int h = (int)(noise(this.seed + 663L, (x + dx) * 4.0, (z + dz) * 4.0) * 7.0) - 1;
                for (int y = 1; y <= h; y++) {
                    this.set(x + dx, y, z + dz, hash3(this.seed + 665L, x + dx, y, z + dz) < 0.3 ? b : a);
                }
            }
        }

        for (int[] c : new int[][]{{-3, -3}, {3, 3}}) {
            int h = 4 + (int)(hash3(this.seed + 667L, x + c[0], 0, z + c[1]) * 6);
            for (int y = 1; y <= h; y++) {
                this.set(x + c[0], y, z + c[1], a);
            }
        }
    }

    /** Spikes of packed and blue ice standing out of the snow. */
    private void iceSpikes(int x, int z) {
        java.util.Random r = new java.util.Random(this.seed ^ (x * 31L + z));
        for (int k = 0; k < 4; k++) {
            int sx = x + r.nextInt(9) - 4;
            int sz = z + r.nextInt(9) - 4;
            int h = 8 + r.nextInt(10);
            for (int y = 1; y <= h; y++) {
                double rad = 1.8 * (1.0 - (double)y / (h + 1));
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.hypot(dx, dz) <= rad + 0.3) {
                            this.set(sx + dx, y, sz + dz, y % 5 == 0 ? "blue_ice" : "packed_ice");
                        }
                    }
                }
            }
        }
    }

    /** Volcanic spires: basalt columns narrowing to glowing tips. */
    private void spires(int x, int z) {
        java.util.Random r = new java.util.Random(this.seed ^ (x * 37L + z));
        for (int k = 0; k < 5; k++) {
            int sx = x + r.nextInt(11) - 5;
            int sz = z + r.nextInt(11) - 5;
            int h = 9 + r.nextInt(14);
            double r0 = 1.6 + r.nextDouble();
            for (int y = 1; y <= h; y++) {
                double rad = r0 * (1.0 - (double)y / (h + 2));
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (Math.hypot(dx, dz) <= rad + 0.3) {
                            this.set(sx + dx, y, sz + dz, y >= h - 1 ? "magma_block" : (y % 6 == 0 ? "blackstone" : "basalt[axis=y]"));
                        }
                    }
                }
            }
        }
    }

    /** A stepped sandstone pyramid with a gold cap. */
    private void smallPyramid(int x, int z, int half) {
        for (int k = 0; k <= half; k++) {
            int hw = half - k;
            for (int dx = -hw; dx <= hw; dx++) {
                for (int dz = -hw; dz <= hw; dz++) {
                    this.set(x + dx, 1 + k, z + dz, k == half ? "gold_block" : (hash3(this.seed + 671L, x + dx, k, z + dz) < 0.2 ? "cut_sandstone" : "smooth_sandstone"));
                }
            }
        }
    }

    /** Small islands floating in the sky round the arena, each with a tree or a rock on top. */
    private void skyIslets(Land land, java.util.Random rr) {
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 10 + rr.nextDouble() * 25);
            double rad = this.inner + 4 + rr.nextDouble() * 14;
            int ox = (int)Math.round(Math.cos(a) * rad);
            int oz = (int)Math.round(Math.sin(a) * rad);
            int oy = 34 + rr.nextInt(16);
            double r = 2.5 + rr.nextDouble() * 3.0;
            boolean tree = rr.nextBoolean();
            int size = rr.nextInt(6);
            double kind = rr.nextDouble();
            int n = (int)Math.ceil(r);
            for (int dx = -n; dx <= n; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > r) {
                        continue;
                    }

                    int depthI = (int)Math.round((r - d) * 1.5) + 1;
                    for (int k = 0; k <= depthI; k++) {
                        int x = ox + dx;
                        int z = oz + dz;
                        this.set(x, oy - k, z, k == 0 ? (land.snowLine() <= oy ? "snow_block" : land.top()) : (k <= 1 ? land.under() : this.outRock(x, oy - k, z)));
                    }

                    if (hash3(this.seed + 681L, ox + dx, oy, oz + dz) < 0.1) {
                        this.set(ox + dx, oy - depthI - 1, oz + dz, "hanging_roots");
                    }
                }
            }

            if (tree) {
                this.outTree(land.trees(), ox, oy + 1, oz, size % 3, kind, land.snowLine() <= oy);
            } else {
                this.blob(ox, oy + 1, oz, 1.4, this.outRock(ox, oy, oz));
            }
        }
    }

    // ------------------------------------------------------------------ shared outside

    /** Trees on open ground away from the build (only where nothing is built yet). */
    private void grove(double fromD, double chance, int kind) {
        java.util.Random rr = new java.util.Random(this.seed ^ 0x7EE5L ^ kind);
        for (int x = -this.R + 3; x <= this.R - 3; x += 5) {
            for (int z = -this.R + 3; z <= this.R - 3; z += 5) {
                int tx = x + rr.nextInt(3) - 1;
                int tz = z + rr.nextInt(3) - 1;
                if (this.sd(tx, tz) < fromD || rr.nextDouble() > chance) {
                    continue;
                }

                int gy = this.groundTop(tx, tz);
                if (gy < 0 || !this.clearAbove(tx, gy + 1, tz, 2)) {
                    continue;
                }

                String top = this.get(tx, gy, tz);
                if (top == null || !(top.startsWith("grass") || top.startsWith("snow") || top.startsWith("sand") || top.startsWith("podzol") || top.startsWith("coarse"))) {
                    continue;
                }

                switch (kind) {
                    case 0 -> this.broadleaf(tx, gy + 1, tz, rr.nextInt(4) == 0 ? "birch_log" : "oak_log", rr.nextInt(4) == 0 ? "birch" : "oak", 4 + rr.nextInt(3), 2.8 + rr.nextDouble());
                    case 1 -> this.spruce(tx, gy + 1, tz, 7 + rr.nextInt(5), this.frost());
                    case 2 -> this.palmTree(tx, gy + 1, tz);
                    case 3 -> this.cherryTree(tx, gy + 1, tz);
                    case 4 -> this.deadTree(tx, gy + 1, tz, "dark_oak_log", 5 + rr.nextInt(4));
                    default -> this.broadleaf(tx, gy + 1, tz, "dark_oak_log", "dark_oak", 5, 3.2);
                }
            }
        }
    }

    private int groundTop(int x, int z) {
        for (int y = this.yHi; y >= 0; y--) {
            String s = this.get(x, y, z);
            if (s != null) {
                return s.startsWith("snow[") || s.startsWith("pink_petals") ? y - 1 : y;
            }
        }

        return -1;
    }

    private boolean clearAbove(int x, int y, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy < 6; dy++) {
                    String s = this.get(x + dx, y + dy, z + dz);
                    if (s != null && !s.startsWith("snow[") && !s.startsWith("pink_petals") && !s.startsWith("short_grass")) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /** Low rolling hills on the ground outside the build. */
    private void hills(double fromD, int maxH, String top, String under) {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < fromD) {
                    continue;
                }

                double ramp = Math.min(1.0, (d - fromD) / 8.0);
                int h = (int)Math.round(ramp * maxH * noise(this.seed + 99L, x * 0.9, z * 0.9));
                if (!this.empty(x, 1, z)) {
                    continue;
                }

                for (int y = 1; y <= h; y++) {
                    if (this.empty(x, y, z)) {
                        this.set(x, y, z, y == h ? top : under);
                    }
                }

                if (h > 0) {
                    this.set(x, 0, z, under);
                }
            }
        }
    }

    // ================================================================== CASTLE

    private Mat castleWall;
    private Mat castlePlinth;
    private Mat roofMat;
    private String frame;
    private String sill;
    private String glass;
    private String trimStairs;
    private String bannerColor;

    private void castlePalette() {
        if (this.frost()) {
            this.castleWall = this.mixOf(101L, "stone_bricks", "polished_andesite", 0.18, "polished_diorite", 0.06, "cracked_stone_bricks", 0.05);
            this.castlePlinth = this.mixOf(103L, "cobblestone", "stone_bricks", 0.4, "andesite", 0.2);
            this.roofMat = this.mixOf(105L, "blue_concrete", "light_blue_terracotta", 0.18, "cyan_terracotta", 0.1);
            this.frame = "polished_deepslate";
            this.sill = "deepslate_brick";
            this.glass = "light_blue_stained_glass_pane";
            this.bannerColor = "blue";
        } else {
            this.castleWall = this.mixOf(
                101L, "stone_bricks", "polished_andesite", 0.22, "andesite", 0.08, "cracked_stone_bricks", 0.06, "mossy_stone_bricks", 0.05, "smooth_stone", 0.05
            );
            this.castlePlinth = this.mixOf(103L, "cobblestone", "mossy_cobblestone", 0.25, "andesite", 0.2);
            this.roofMat = this.mixOf(105L, "red_concrete", "red_terracotta", 0.28, "red_nether_bricks", 0.08);
            this.frame = "deepslate_bricks";
            this.sill = "deepslate_brick";
            this.glass = "brown_stained_glass_pane";
            this.bannerColor = "red";
        }

        this.trimStairs = "stone_brick";
    }

    private void castle() {
        this.castlePalette();
        int yb = 3;
        // the grass terrace with its dirt edge, then two stone steps up to the buildings
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.0 || d >= 5.0) {
                    continue;
                }

                String out = this.outward(x, z);
                if (d < 3.0) {
                    this.set(x, 1, z, this.frost() ? "snow_block" : "grass_block");
                    this.set(x, 0, z, "dirt");
                } else if (d < 4.0) {
                    this.set(x, 1, z, this.castlePlinth.at(x, 1, z));
                    this.set(x, 2, z, stairs("stone_brick", out, false));
                } else {
                    this.set(x, 1, z, this.castlePlinth.at(x, 1, z));
                    this.set(x, 2, z, this.castlePlinth.at(x, 2, z));
                    this.set(x, 3, z, stairs("stone_brick", out, false));
                }
            }
        }

        int front = 5;
        int depthW = 12;
        int h = 17;
        Face east = new Face(this.hx + front, 0, 1, 0);
        Face west = new Face(-this.hx - front, 0, -1, 0);
        Face south = new Face(0, this.hz + front, 0, 1);
        Face north = new Face(0, -this.hz - front, 0, -1);
        int lx = this.hx + front;
        int lz = this.hz + front;
        this.castleWing(north, -lx, lx, depthW, h, yb);
        this.castleWing(south, -lx, lx, depthW, h, yb);
        this.castleWing(east, -lz, lz, depthW, h, yb);
        this.castleWing(west, -lz, lz, depthW, h, yb);
        // corner towers where the wings meet
        int tc = front + depthW / 2;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                this.roundTower(sx * (this.hx + tc), sz * (this.hz + tc), 6.5, h + 9, yb);
            }
        }

        this.gatehouse(east, yb);
        this.greatHall(west, yb);
        this.sideTower(north, 0, h, yb);
        this.sideTower(south, 0, h, yb);
        this.hills(front + depthW + 6, 5, this.frost() ? "snow_block" : "grass_block", "dirt");
        this.grove(front + depthW + 4, 0.75, this.frost() ? 1 : 0);
        this.grove(front + depthW + 4, 0.25, 1);
    }

    /** One castle wing: a long building with buttresses, two rows of windows, battlements and a roof behind them. */
    private void castleWing(Face f, int u0, int u1, int depthW, int h, int yb) {
        f.box(u0, 1, 0, u1, h, depthW - 1, this.castleWall);
        // plinth and battered base
        for (int u = u0; u <= u1; u++) {
            for (int y = 1; y <= yb + 1; y++) {
                f.set(u, y, 0, this.castlePlinth.at(f.x(u, 0), y, f.z(u, 0)));
            }

            f.set(u, yb + 2, 0, "polished_andesite");
        }

        int bay = 6;
        for (int u = u0 + 3; u <= u1 - 3; u++) {
            int m = Math.floorMod(u, bay);
            if (m == 0) {
                // buttress: two steps out, capped with stairs
                for (int y = yb; y <= h - 3; y++) {
                    f.set(u, y, -1, this.castleWall.at(f.x(u, -1), y, f.z(u, -1)));
                }

                f.set(u, h - 2, -1, stairs("stone_brick", f.back(), false));
                for (int y = yb; y <= yb + 4; y++) {
                    f.set(u, y, -2, this.castlePlinth.at(f.x(u, -2), y, f.z(u, -2)));
                }

                f.set(u, yb + 5, -2, stairs("stone_brick", f.back(), false));
            } else if (m == 3) {
                // a bay: a recessed round arch below and a tall pointed arch above, each with its window set back
                Face in = f.moved(0, 1);
                this.arch(f, u, yb + 1, 5, 6, false, 1, null, null);
                this.window(in, u, yb + 2, 3, 4, false, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
                this.arch(f, u, yb + 9, 5, h - yb - 11, true, 1, null, null);
                this.window(in, u, yb + 10, 3, h - yb - 14, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
                if (Math.floorMod(Math.floorDiv(u, bay), 4) == 1) {
                    f.set(u, h - 3, 0, this.bannerColor + "_wall_banner[facing=" + f.front() + "]");
                    f.set(u, h - 3, 1, this.castleWall.at(f.x(u, 1), h - 3, f.z(u, 1)));
                }
            }
        }

        // string course between the floors and the cornice under the battlements
        this.cornice(f, u0, u1, yb + 8, "stone_brick", 1);
        this.cornice(f, u0, u1, h - 1, "stone_brick", 1);
        for (int u = u0; u <= u1; u++) {
            f.set(u, h, -1, this.castleWall.at(f.x(u, -1), h, f.z(u, -1)));
        }

        Mat merl = this.castleWall;
        for (int u = u0; u <= u1; u++) {
            if (Math.floorMod(u, 3) != 2) {
                f.set(u, h + 1, -1, merl.at(f.x(u, -1), h + 1, f.z(u, -1)));
            }
        }

        // vines hanging from the cornice
        java.util.Random vr = new java.util.Random(this.seed ^ f.cx * 31L ^ f.cz);
        for (int u = u0 + 2; u <= u1 - 2; u++) {
            if (!this.frost() && vr.nextDouble() < 0.16) {
                int len = 3 + vr.nextInt(8);
                for (int k = 0; k < len; k++) {
                    int y = h - 2 - k;
                    if (y <= yb + 1) {
                        break;
                    }

                    if (f.empty(u, y, -1)) {
                        f.set(u, y, -1, "vine[" + f.back() + "=true]");
                    }
                }
            }
        }

        // the roof behind the battlements: a gable along the wing
        int ax = Math.min(f.x(u0, 1), f.x(u1, depthW - 1));
        int bx = Math.max(f.x(u0, 1), f.x(u1, depthW - 1));
        int az = Math.min(f.z(u0, 1), f.z(u1, depthW - 1));
        int bz = Math.max(f.z(u0, 1), f.z(u1, depthW - 1));
        this.gableRoof(ax, az, bx, bz, h + 1, f.rx != 0, null, this.roofMat, this.castleWall);
        // dormer chimneys along the ridge
        for (int u = u0 + 8; u <= u1 - 8; u += 14) {
            int mw = depthW / 2;
            for (int y = h + 1; y <= h + depthW / 2 + 3; y++) {
                f.set(u, y, mw, this.castlePlinth.at(f.x(u, mw), y, f.z(u, mw)));
            }

            f.set(u, h + depthW / 2 + 4, mw, "stone_brick_slab[type=bottom]");
        }
    }

    /** A round tower: battered base, arrow slits, machicolations, battlements and a cone roof with a spire. */
    private void roundTower(int x, int z, double r, int h, int yb) {
        this.cyl(x, z, r + 1.0, 1, yb + 1, this.castlePlinth);
        this.cyl(x, z, r, 1, h, this.castleWall);
        for (int y = yb + 4; y < h - 3; y += 5) {
            for (int k = 0; k < 8; k++) {
                double t = k * Math.PI / 4.0 + (y / 5 % 2) * Math.PI / 8.0;
                int sx = x + (int)Math.round(Math.cos(t) * r);
                int sz = z + (int)Math.round(Math.sin(t) * r);
                this.set(sx, y, sz, "deepslate_bricks");
                this.set(sx, y + 1, sz, "deepslate_bricks");
            }
        }

        this.ringAt(x, z, r + 0.2, r + 1.2, h - 1, of("polished_andesite"));
        this.ringAt(x, z, r + 0.2, r + 1.2, h, this.castleWall);
        for (int k = 0; k < 24; k++) {
            double t = k * Math.PI * 2.0 / 24.0;
            if (k % 2 == 0) {
                this.set(x + (int)Math.round(Math.cos(t) * (r + 1)), h + 1, z + (int)Math.round(Math.sin(t) * (r + 1)), this.castleWall.at(x, h + 1, z));
            }
        }

        int rh = (int)Math.round(r * 2.3);
        this.coneRoof(x, z, r + 0.6, h + 1, rh, this.roofMat);
        this.spire(x, h + rh + 2, z, 3, "stone_brick_wall", "lightning_rod[facing=up]");
    }

    /** The gatehouse in the middle of a wing: a tall gate block between two square towers with pyramid roofs. */
    private void gatehouse(Face f, int yb) {
        int gh = 24;
        f.box(-9, 1, -1, 9, gh, 13, this.castleWall);
        for (int u = -9; u <= 9; u++) {
            for (int y = 1; y <= yb + 1; y++) {
                f.set(u, y, -1, this.castlePlinth.at(f.x(u, -1), y, f.z(u, -1)));
            }
        }

        // the gate: a deep round arch with doors and a portcullis
        Face g = f;
        f = f.moved(0, -1);
        this.arch(f, 0, yb + 1, 7, 11, false, 4, "dark_oak_planks", "polished_andesite");
        for (int u = -3; u <= 3; u++) {
            for (int y = yb + 1; y <= yb + 11; y++) {
                if (inArch(u, y, 0, 3.0, yb + 1, yb + 11, false)) {
                    f.set(u, y, 4, Math.floorMod(u, 2) == 0 ? "dark_oak_planks" : "spruce_planks");
                    if (y > yb + 7) {
                        f.set(u, y, 1, "iron_bars");
                    }
                }
            }
        }

        f.set(0, yb + 13, -1, "chiseled_stone_bricks");
        // wide stairs from the floor up to the gate
        for (int u = -5; u <= 5; u++) {
            f.set(u, yb, -2, stairs("stone_brick", f.back(), false));
            f.set(u, yb - 1, -3, stairs("stone_brick", f.back(), false));
            f.set(u, yb - 1, -2, "stone_bricks");
        }

        // rose window and lancets
        double rc = yb + 16.5;
        for (int u = -4; u <= 4; u++) {
            for (int y = (int)rc - 4; y <= (int)rc + 4; y++) {
                double dd = Math.hypot(u, y - rc);
                if (dd < 3.2) {
                    f.set(u, y, 1, dd < 1.0 ? "orange_stained_glass_pane" : (Math.abs(u) == Math.abs((int)Math.round(y - rc)) || u == 0 || Math.round(y - rc) == 0 ? "iron_bars" : this.glass.replace("brown", "red")));
                    f.set(u, y, 0, null);
                    f.set(u, y, 2, "dark_oak_planks");
                } else if (dd < 4.2) {
                    f.set(u, y, 0, this.frame);
                }
            }
        }

        for (int u : new int[]{-6, 6}) {
            this.window(f, u, yb + 4, 1, 5, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
            this.window(f, u, yb + 13, 1, 6, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
        }

        this.cornice(f, -9, 9, gh - 1, "stone_brick", 2);
        // a stepped gable crest with the roof running back from it
        for (int k = 0; k <= 8; k++) {
            for (int u = -9 + k; u <= 9 - k; u++) {
                f.set(u, gh + 1 + k, -1, this.castleWall.at(f.x(u, -1), gh + 1 + k, f.z(u, -1)));
            }

            f.set(-9 + k, gh + 2 + k, -1, stairs("stone_brick", f.right(), false));
            f.set(9 - k, gh + 2 + k, -1, stairs("stone_brick", f.left(), false));
        }

        f.set(0, gh + 10, -1, "chiseled_stone_bricks");
        this.spire(f.x(0, -1), gh + 11, f.z(0, -1), 2, "stone_brick_wall", "lightning_rod[facing=up]");
        int ax = Math.min(f.x(-8, 0), f.x(8, 13));
        int bx = Math.max(f.x(-8, 0), f.x(8, 13));
        int az = Math.min(f.z(-8, 0), f.z(8, 13));
        int bz = Math.max(f.z(-8, 0), f.z(8, 13));
        this.gableRoof(ax, az, bx, bz, gh + 1, f.rx == 0, null, this.roofMat, this.castleWall);
        // the two towers
        f = g;
        for (int side = -1; side <= 1; side += 2) {
            int u0 = side < 0 ? -17 : 10;
            int u1 = side < 0 ? -10 : 17;
            int th = 32;
            f.box(u0, 1, -3, u1, th, 9, this.castleWall);
            for (int u = u0; u <= u1; u++) {
                for (int y = 1; y <= yb + 1; y++) {
                    f.set(u, y, -3, this.castlePlinth.at(f.x(u, -3), y, f.z(u, -3)));
                }
            }

            int uc = (u0 + u1) / 2;
            Face tf = f.moved(0, -3);
            for (int y : new int[]{yb + 4, yb + 12, yb + 20}) {
                this.window(tf, uc - 1, y, 1, 5, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
                this.window(tf, uc + 2, y, 1, 5, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
            }

            // corner quoins
            for (int y = 1; y <= th; y++) {
                if (y % 2 == 0) {
                    tf.set(u0, y, 0, "polished_andesite");
                    tf.set(u1, y, 0, "polished_andesite");
                }
            }

            this.cornice(tf, u0, u1, th, "stone_brick", 1);
            int ax2 = Math.min(f.x(u0 - 1, -4), f.x(u1 + 1, 10));
            int bx2 = Math.max(f.x(u0 - 1, -4), f.x(u1 + 1, 10));
            int az2 = Math.min(f.z(u0 - 1, -4), f.z(u1 + 1, 10));
            int bz2 = Math.max(f.z(u0 - 1, -4), f.z(u1 + 1, 10));
            int top = this.pyramidRoof(ax2, az2, bx2, bz2, th + 1, null, this.roofMat);
            int px = (ax2 + bx2) / 2;
            int pz = (az2 + bz2) / 2;
            this.spire(px, top + 1, pz, 3, "stone_brick_wall", "lightning_rod[facing=up]");
            // pinnacles on the tower corners
            for (int[] c : new int[][]{{ax2, az2}, {ax2, bz2}, {bx2, az2}, {bx2, bz2}}) {
                this.spire(c[0], th + 1, c[1], 3, "stone_brick_wall", "lantern[hanging=false]");
            }
        }

        // banners on the gate front
        for (int u : new int[]{-8, 8}) {
            this.cloth(f.moved(0, -1), u, gh - 3, 3, 8, this.bannerColor + "_wool", "yellow_wool", "yellow_wool");
        }
    }

    /** The great hall: a cathedral-like front with a huge gothic window, buttresses, stair turrets and a steep roof. */
    private void greatHall(Face f, int yb) {
        int hh = 22;
        f.box(-13, 1, -2, 13, hh, 18, this.castleWall);
        for (int u = -13; u <= 13; u++) {
            for (int y = 1; y <= yb + 1; y++) {
                f.set(u, y, -2, this.castlePlinth.at(f.x(u, -2), y, f.z(u, -2)));
            }
        }

        Face ff = f.moved(0, -2);
        // the great window with tracery
        this.window(ff, 0, yb + 9, 7, 13, true, this.glass.replace("brown", "red"), "dark_oak_planks", this.frame, this.sill, 1);
        for (int y = yb + 9; y <= yb + 21; y++) {
            for (int u = -3; u <= 3; u++) {
                if (inArch(u, y, 0, 3.0, yb + 9, yb + 21, true) && (u == 0 || Math.abs(u) == 2 && y < yb + 17 || (y - yb) % 5 == 0)) {
                    ff.set(u, y, 1, "iron_bars");
                }
            }
        }

        // door
        this.arch(ff, 0, yb + 1, 3, 5, true, 2, "dark_oak_planks", "polished_andesite");
        for (int u = -2; u <= 2; u++) {
            ff.set(u, yb, -1, stairs("stone_brick", ff.back(), false));
        }

        // side windows and buttresses with pinnacles
        for (int u : new int[]{-9, 9}) {
            this.window(ff, u, yb + 4, 3, 5, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
            this.window(ff, u, yb + 13, 3, 6, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
        }

        for (int u : new int[]{-13, -5, 5, 13}) {
            for (int y = 1; y <= hh - 2; y++) {
                ff.set(u, y, -1, this.castleWall.at(ff.x(u, -1), y, ff.z(u, -1)));
                if (y <= yb + 8) {
                    ff.set(u, y, -2, this.castleWall.at(ff.x(u, -2), y, ff.z(u, -2)));
                }
            }

            ff.set(u, yb + 9, -2, stairs("stone_brick", ff.back(), false));
            this.spire(ff.x(u, -1), hh - 1, ff.z(u, -1), 4, "stone_brick_wall", "lightning_rod[facing=up]");
        }

        this.cornice(ff, -13, 13, hh, "stone_brick", 1);
        // the steep roof, its gable facing the floor
        int ax = Math.min(f.x(-14, -2), f.x(14, 18));
        int bx = Math.max(f.x(-14, -2), f.x(14, 18));
        int az = Math.min(f.z(-14, -2), f.z(14, 18));
        int bz = Math.max(f.z(-14, -2), f.z(14, 18));
        int top = this.gableRoof(ax, az, bx, bz, hh + 1, f.rx == 0, null, this.roofMat, this.castleWall);
        // a second roof step makes it steeper
        int ax3 = Math.min(f.x(-9, -1), f.x(9, 17));
        int bx3 = Math.max(f.x(-9, -1), f.x(9, 17));
        int az3 = Math.min(f.z(-9, -1), f.z(9, 17));
        int bz3 = Math.max(f.z(-9, -1), f.z(9, 17));
        top = Math.max(top, this.gableRoof(ax3, az3, bx3, bz3, hh + 6, f.rx == 0, null, this.roofMat, this.castleWall));
        // the front gable edge in stone, with a small round window
        for (int y = top; y >= hh + 1; y--) {
            for (int u = -14; u <= 14; u++) {
                String s = ff.get(u, y, 0);
                if (s != null && !s.startsWith("stone_brick_wall")) {
                    ff.set(u, y, 0, this.castleWall.at(ff.x(u, 0), y, ff.z(u, 0)));
                    if (ff.empty(u, y + 1, 0)) {
                        ff.set(u, y, -1, slab("stone_brick", false));
                    }
                }
            }
        }

        ff.set(0, hh + 6, 1, this.glass.replace("brown", "orange"));
        ff.set(0, hh + 6, 0, null);
        this.spire(ff.x(0, 0), top + 1, ff.z(0, 0), 3, "stone_brick_wall", "lightning_rod[facing=up]");
        // stair turrets at both corners
        for (int u : new int[]{-16, 16}) {
            int tx = ff.x(u, 1);
            int tz = ff.z(u, 1);
            this.cyl(tx, tz, 2.6, 1, hh + 8, this.castleWall);
            for (int y = yb + 4; y < hh + 6; y += 4) {
                this.set(tx + (int)Math.round(Math.signum(-f.bx) * 2.6), y, tz + (int)Math.round(Math.signum(-f.bz) * 2.6), "deepslate_bricks");
            }

            this.ringAt(tx, tz, 2.4, 3.4, hh + 8, of("polished_andesite"));
            this.coneRoof(tx, tz, 3.4, hh + 9, 9, this.roofMat);
            this.spire(tx, hh + 19, tz, 2, "stone_brick_wall", "lightning_rod[facing=up]");
        }
    }

    /** A square tower in the middle of a side wing, with a pyramid roof and a banner. */
    private void sideTower(Face f, int uc, int h, int yb) {
        int th = h + 8;
        f.box(uc - 4, 1, -2, uc + 4, th, 8, this.castleWall);
        for (int u = uc - 4; u <= uc + 4; u++) {
            for (int y = 1; y <= yb + 1; y++) {
                f.set(u, y, -2, this.castlePlinth.at(f.x(u, -2), y, f.z(u, -2)));
            }
        }

        Face tf = f.moved(0, -2);
        this.window(tf, uc, yb + 3, 3, 5, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
        this.window(tf, uc - 2, yb + 12, 1, 5, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
        this.window(tf, uc + 2, yb + 12, 1, 5, true, this.glass, "dark_oak_planks", this.frame, this.sill, 1);
        this.cornice(tf, uc - 4, uc + 4, th, "stone_brick", 1);
        int ax = Math.min(f.x(uc - 5, -3), f.x(uc + 5, 9));
        int bx = Math.max(f.x(uc - 5, -3), f.x(uc + 5, 9));
        int az = Math.min(f.z(uc - 5, -3), f.z(uc + 5, 9));
        int bz = Math.max(f.z(uc - 5, -3), f.z(uc + 5, 9));
        int top = this.pyramidRoof(ax, az, bx, bz, th + 1, null, this.roofMat);
        this.spire((ax + bx) / 2, top + 1, (az + bz) / 2, 3, "stone_brick_wall", "lightning_rod[facing=up]");
        this.cloth(tf, uc, th - 2, 3, 7, this.bannerColor + "_wool", "yellow_wool", "white_wool");
    }

    // ================================================================== other layouts (filled in below)

    // ================================================================== PALACE

    private Mat cream;
    private Mat creamTrim;
    private Mat dome;
    private static final String RED = "red_concrete";
    private static final String GOLD = "yellow_concrete";

    private Mat blueColumn() {
        return (x, y, z) -> Math.floorMod(y, 3) == 0 ? "blue_terracotta" : "light_blue_glazed_terracotta";
    }

    private void palace() {
        this.cream = this.mixOf(201L, "smooth_sandstone", "cut_sandstone", 0.22, "sandstone", 0.08);
        this.creamTrim = of("cut_sandstone");
        this.dome = this.mixOf(203L, "orange_terracotta", "terracotta", 0.3, "brown_terracotta", 0.05);
        // a cream curb round the floor
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d >= 0.0 && d < 1.0) {
                    this.set(x, 1, z, "cut_sandstone_slab[type=bottom]");
                } else if (d >= 1.0 && d < 2.0) {
                    this.set(x, 1, z, "smooth_sandstone");
                }
            }
        }

        int front = 2;
        int dw = 11;
        Face east = new Face(this.hx + front, 0, 1, 0);
        Face west = new Face(-this.hx - front, 0, -1, 0);
        Face south = new Face(0, this.hz + front, 0, 1);
        Face north = new Face(0, -this.hz - front, 0, -1);
        int lx = this.hx + front;
        int lz = this.hz + front;
        this.arcadeWing(north, -lx, lx, dw);
        this.arcadeWing(south, -lx, lx, dw);
        // the east wing opens in the middle onto the palace forecourt
        this.arcadeWing(east, -lz, -18, dw);
        this.arcadeWing(east, 18, lz, dw);
        this.arcadeWing(west, -lz, lz, dw);
        for (int u = -17; u <= 17; u++) {
            for (int w = 0; w < 6; w++) {
                east.set(u, 0, w, w % 3 == 0 || u % 4 == 0 ? "cut_sandstone" : "smooth_sandstone");
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int px = sx * (this.hx + front + dw / 2);
                int pz = sz * (this.hz + front + dw / 2);
                this.domePavilion(px, pz, 4, 21, 3.4);
            }
        }

        this.mainPalace(new Face(this.hx + front + 6, 0, 1, 0));
        this.iwanGate(west);
        this.hills(front + dw + 6, 4, "sand", "sandstone");
        this.grove(front + dw + 3, 0.35, 2);
    }

    /** A two-storey arcade: cream piers, red arches below, gold and red stripes, a red upper floor, domed kiosks and palms. */
    private void arcadeWing(Face f, int u0, int u1, int dw) {
        int h = 14;
        f.box(u0, 1, 0, u1, h, dw - 1, this.cream);
        for (int u = u0; u <= u1; u++) {
            int m = Math.floorMod(u, 5);
            if (m == 0) {
                for (int y = 1; y <= h; y++) {
                    f.set(u, y, -1, y == 1 ? "chiseled_sandstone" : this.cream.at(f.x(u, -1), y, f.z(u, -1)));
                }

                f.set(u, 6, -2, stairs("sandstone", f.back(), true));
                f.set(u, h + 1, -1, "sandstone_wall");
                f.set(u, h + 2, -1, "lantern[hanging=false]");
            } else {
                // ground floor: a red arch recess
                for (int y = 1; y <= 7; y++) {
                    f.set(u, y, 0, this.cream.at(f.x(u, 0), y, f.z(u, 0)));
                }
            }

            f.set(u, 7, 0, GOLD);
            f.set(u, 8, 0, RED);
            f.set(u, 9, 0, GOLD);
            for (int y = 10; y <= h - 1; y++) {
                if (m != 0) {
                    f.set(u, y, 0, RED);
                }
            }

            f.set(u, h, -1, stairs("smooth_sandstone", f.back(), true));
            if (m != 0) {
                f.set(u, h + 1, 0, Math.floorMod(u, 2) == 0 ? "sandstone_wall" : "smooth_sandstone_slab[type=bottom]");
            }
        }

        for (int u = u0; u <= u1; u++) {
            if (Math.floorMod(u, 5) == 1) {
                this.arch(f, u + 1.5, 1, 4, 5, false, 1, RED, null);
                f.set((int)Math.floor(u + 1.5), 6, 0, "chiseled_sandstone");
                // upper floor: a blue column pair round a small arched window
                if (Math.floorMod(Math.floorDiv(u, 5), 2) == 0) {
                    for (int y = 10; y <= h - 1; y++) {
                        f.set(u, y, -1, this.blueColumn().at(0, y, 0));
                        f.set(u + 3, y, -1, this.blueColumn().at(0, y, 0));
                    }

                    this.window(f, u + 1.5, 10, 2, 3, false, "yellow_stained_glass_pane", "black_concrete", GOLD, null, 1);
                }
            }
        }

        // roof terrace with palms and domed kiosks
        java.util.Random pr = new java.util.Random(this.seed ^ f.cx * 7L ^ f.cz);
        for (int u = u0 + 6; u <= u1 - 6; u++) {
            if (Math.floorMod(u, 15) == 7) {
                this.kiosk(f, u, h + 1);
            } else if (Math.floorMod(u, 15) == 0 && pr.nextDouble() < 0.8) {
                int px = f.x(u, 5);
                int pz = f.z(u, 5);
                this.set(px, h, pz, "grass_block");
                this.palmTree(px, h + 1, pz);
            }
        }
    }

    /** A small domed kiosk on a parapet: a cream column with arched openings under an onion dome. */
    private void kiosk(Face f, int u, int y0) {
        f.box(u - 1, y0, 0, u + 1, y0 + 4, 2, this.cream);
        f.set(u, y0 + 1, 0, null);
        f.set(u, y0 + 2, 0, null);
        f.set(u, y0 + 1, 1, null);
        f.set(u, y0 + 2, 1, null);
        f.set(u, y0 + 3, 0, stairs("sandstone", f.front(), true));
        for (int du = -1; du <= 1; du++) {
            f.set(u + du, y0 + 4, -1, stairs("smooth_sandstone", f.back(), true));
        }

        this.onion(f.x(u, 1), y0 + 5, f.z(u, 1), 1.8, this.dome, GOLD);
    }

    /** A square corner pavilion with arched openings and a big onion dome. */
    private void domePavilion(int x, int z, int half, int h, double r) {
        this.fill(x - half, 1, z - half, x + half, h, z + half, this.cream);
        for (int y = 1; y <= h; y++) {
            if (y % 6 == 0) {
                for (int k = -half; k <= half; k++) {
                    for (int[] e : new int[][]{{x + k, z - half}, {x + k, z + half}, {x - half, z + k}, {x + half, z + k}}) {
                        this.set(e[0], y, e[1], y % 12 == 0 ? RED : GOLD);
                    }
                }
            }
        }

        for (Face f : new Face[]{new Face(x - half, z, 1, 0), new Face(x + half, z, -1, 0), new Face(x, z - half, 0, 1), new Face(x, z + half, 0, -1)}) {
            this.window(f, 0, h - 6, 3, 4, false, "light_blue_stained_glass_pane", "black_concrete", "cut_sandstone", "sandstone", 1);
            this.window(f, 0, 8, 3, 5, true, "yellow_stained_glass_pane", "black_concrete", "cut_sandstone", "sandstone", 1);
        }

        this.ringAt(x, z, half - 0.5, half + 1.2, h + 1, of("smooth_sandstone"));
        this.fill(x - half + 1, h + 1, z - half + 1, x + half - 1, h + 2, z + half - 1, this.cream);
        this.onion(x, h + 3, z, r, this.dome, GOLD);
        for (int[] c : new int[][]{{x - half, z - half}, {x - half, z + half}, {x + half, z - half}, {x + half, z + half}}) {
            this.onion(c[0], h + 2, c[1], 1.0, this.dome, null);
        }
    }

    /** The palace: grand stairs, a gate between golden niches and blue columns, three stepped tiers and a great dome. */
    private void mainPalace(Face f) {
        int yb = 5;
        // grand stairs from the floor curb up to the palace terrace
        for (int u = -10; u <= 10; u++) {
            for (int k = 0; k < 4; k++) {
                int w = -6 + k;
                for (int y = 1; y <= k; y++) {
                    f.set(u, y, w, this.cream.at(f.x(u, w), y, f.z(u, w)));
                }

                f.set(u, k + 1, w, stairs("smooth_sandstone", f.back(), false));
            }

            for (int w = -2; w <= -1; w++) {
                for (int y = 1; y <= yb - 1; y++) {
                    f.set(u, y, w, this.cream.at(f.x(u, w), y, f.z(u, w)));
                }
            }
        }

        for (int u : new int[]{-11, 11}) {
            for (int w = -6; w <= -1; w++) {
                int top = Math.min(yb, 2 + (w + 6));
                for (int y = 1; y <= top; y++) {
                    f.set(u, y, w, this.creamTrim.at(0, y, 0));
                }

                f.set(u, top + 1, w, "sandstone_wall");
            }

            f.set(u, yb + 2, -1, "lantern[hanging=false]");
        }

        // tier 1
        int t1 = yb + 13;
        f.box(-16, 1, 0, 16, t1, 16, this.cream);
        for (int u = -16; u <= 16; u++) {
            f.set(u, t1 - 1, 0, GOLD);
            f.set(u, t1, 0, RED);
            f.set(u, t1 + 1, -1, stairs("smooth_sandstone", f.back(), true));
            f.set(u, t1 + 2, 0, Math.floorMod(u, 2) == 0 ? "sandstone_wall" : "smooth_sandstone_slab[type=bottom]");
        }

        // the door and the golden niches
        this.arch(f, 0, yb, 3, 6, true, 2, "dark_oak_planks", RED);
        for (int du = -1; du <= 1; du++) {
            f.set(du, yb, 2, "dark_oak_door[facing=" + f.front() + ",half=lower,hinge=" + (du < 0 ? "left" : "right") + "]");
            f.set(du, yb + 1, 2, "dark_oak_door[facing=" + f.front() + ",half=upper,hinge=" + (du < 0 ? "left" : "right") + "]");
        }

        for (int u : new int[]{-5, 5}) {
            this.arch(f, u, yb, 3, 10, true, 1, "gold_block", RED);
            f.set(u, yb + 11, -1, "chiseled_sandstone");
        }

        for (int u : new int[]{-8, -2, 2, 8, -13, 13}) {
            for (int y = yb; y <= t1 - 2; y++) {
                f.set(u, y, -1, y == yb ? "chiseled_sandstone" : this.blueColumn().at(0, y, 0));
            }

            f.set(u, t1 - 1, -1, stairs("sandstone", f.back(), true));
        }

        for (int u : new int[]{-11, 11, -16, 16}) {
            for (int y = 1; y <= t1 - 2; y++) {
                f.set(u, y, -1, Math.floorMod(y, 4) == 0 ? GOLD : RED);
            }
        }

        // tier 2
        int t2 = t1 + 10;
        f.box(-11, t1 + 1, 4, 11, t2, 14, this.cream);
        Face f2 = f.moved(0, 4);
        this.window(f2, 0, t1 + 3, 5, 6, false, "light_blue_stained_glass_pane", "black_concrete", RED, "smooth_sandstone", 1);
        for (int u : new int[]{-4, 4, -8, 8}) {
            for (int y = t1 + 2; y <= t2 - 1; y++) {
                f2.set(u, y, -1, this.blueColumn().at(0, y, 0));
            }
        }

        for (int u = -11; u <= 11; u++) {
            f2.set(u, t2 - 1, 0, GOLD);
            f2.set(u, t2, 0, RED);
            f2.set(u, t2 + 1, -1, stairs("smooth_sandstone", f2.back(), true));
            f2.set(u, t2 + 2, 0, Math.floorMod(u, 2) == 0 ? "sandstone_wall" : "smooth_sandstone_slab[type=bottom]");
        }

        // balcony on tier 1 in front of tier 2
        for (int u = -11; u <= 11; u++) {
            f.set(u, t1 + 2, 3, "sandstone_wall");
        }

        // tier 3: the drum of the great dome
        int t3 = t2 + 8;
        f.box(-6, t2 + 1, 7, 6, t3, 13, this.cream);
        Face f3 = f.moved(0, 7);
        this.window(f3, 0, t2 + 2, 3, 5, false, "light_blue_stained_glass_pane", "black_concrete", GOLD, "smooth_sandstone", 1);
        for (int u : new int[]{-3, 3}) {
            for (int y = t2 + 1; y <= t3 - 1; y++) {
                f3.set(u, y, -1, this.blueColumn().at(0, y, 0));
            }
        }

        for (int u = -6; u <= 6; u++) {
            f3.set(u, t3, 0, RED);
            f3.set(u, t3 + 1, -1, stairs("smooth_sandstone", f3.back(), true));
        }

        int dx = f.x(0, 10);
        int dz = f.z(0, 10);
        this.fill(dx - 5, t3 + 1, dz - 5, dx + 5, t3 + 1, dz + 5, this.cream);
        this.onion(dx, t3 + 2, dz, 5.2, this.dome, GOLD);
        // small domes on the tier corners
        for (int[] c : new int[][]{{-16, 0}, {16, 0}, {-16, 15}, {16, 15}}) {
            int cx2 = f.x(c[0], c[1]);
            int cz2 = f.z(c[0], c[1]);
            this.fill(cx2 - 1, t1 + 1, cz2 - 1, cx2 + 1, t1 + 4, cz2 + 1, this.cream);
            this.onion(cx2, t1 + 5, cz2, 1.9, this.dome, GOLD);
        }

        for (int[] c : new int[][]{{-11, 4}, {11, 4}}) {
            int cx2 = f.x(c[0], c[1]);
            int cz2 = f.z(c[0], c[1]);
            this.fill(cx2 - 1, t2 + 1, cz2 - 1, cx2 + 1, t2 + 3, cz2 + 1, this.cream);
            this.onion(cx2, t2 + 4, cz2, 1.7, this.dome, GOLD);
        }

        // flanking towers with domes
        for (int u : new int[]{-20, 20}) {
            int tx = f.x(u, -3);
            int tz = f.z(u, -3);
            this.domePavilion(tx, tz, 3, 26, 2.8);
        }
    }

    /** The far gate: a tall pointed iwan with layered bands, framed by two minarets. */
    private void iwanGate(Face f) {
        int gh = 25;
        f.box(-10, 1, -1, 10, gh, 12, this.cream);
        Face g = f.moved(0, -1);
        for (int k = 0; k < 3; k++) {
            int width = 13 - k * 2;
            int height = 19 - k;
            this.arch(g.moved(0, k), 0, 2, width, height, true, 1, null, null);
        }

        Face back = g.moved(0, 3);
        back.box(-5, 2, 0, 5, 18, 0, this.cream);
        this.arch(back, 0, 2, 3, 6, true, 1, "dark_oak_planks", RED);
        this.window(back, 0, 10, 3, 6, true, "light_blue_stained_glass_pane", "black_concrete", GOLD, null, 1);
        for (int u = -10; u <= 10; u++) {
            for (int y = 1; y <= gh; y++) {
                if (g.get(u, y, 0) != null && (Math.abs(u) >= 8 || y >= 21)) {
                    String b = y == 21 || y == 1 ? GOLD : (y == 22 ? RED : (Math.abs(u) == 9 && y % 4 == 0 ? RED : null));
                    if (b != null) {
                        g.set(u, y, 0, b);
                    }
                }
            }

            g.set(u, gh + 1, 0, Math.floorMod(u, 2) == 0 ? "sandstone_wall" : "smooth_sandstone_slab[type=bottom]");
        }

        for (int u : new int[]{-12, 12}) {
            int mx = g.x(u, 1);
            int mz = g.z(u, 1);
            this.cyl(mx, mz, 2.3, 1, 34, this.cream);
            for (int y : new int[]{12, 22, 30}) {
                this.ringAt(mx, mz, 2.0, 3.3, y, of("smooth_sandstone"));
                this.ringAt(mx, mz, 2.6, 3.3, y + 1, of("sandstone_wall"));
                this.ringAt(mx, mz, 2.0, 2.4, y - 1, of(GOLD));
            }

            this.onion(mx, 35, mz, 2.2, this.dome, GOLD);
        }
    }

    // ================================================================== COLOSSEUM

    private static final int TIERS = 14;

    private void colosseum() {
        Mat podium = this.mixOf(301L, "stone_bricks", "polished_andesite", 0.12, "cracked_stone_bricks", 0.05);
        Mat support = this.mixOf(303L, "sandstone", "smooth_sandstone", 0.3);
        int wallH = 4;
        int top = wallH + TIERS + 1;
        int reach = (int)Math.ceil(this.a) + 24;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double d = this.sd(x, z);
                if (d < 0.0) {
                    continue;
                }

                double s = this.along(x, z);
                String out = this.outward(x, z);
                if (d < 2.0) {
                    // the podium wall with pale pilasters
                    boolean pil = Math.floorMod((int)Math.round(s), 6) == 0;
                    for (int y = 1; y <= wallH; y++) {
                        this.set(x, y, z, pil ? (y == 2 ? "chiseled_stone_bricks" : "polished_andesite") : podium.at(x, y, z));
                    }

                    this.set(x, wallH, z, d < 1.0 ? "polished_andesite" : podium.at(x, wallH, z));
                } else if (d < 2.0 + TIERS) {
                    int t = (int)(d - 2.0);
                    int seat = wallH + 1 + t;
                    for (int y = 1; y < seat; y++) {
                        this.set(x, y, z, support.at(x, y, z));
                    }

                    boolean aisle = Math.floorMod((int)Math.round(s), 30) < 3;
                    if (aisle) {
                        this.set(x, seat, z, "blue_concrete");
                    } else if (t % 5 == 4) {
                        this.set(x, seat, z, "smooth_sandstone");
                    } else {
                        this.set(x, seat, z, stairs(t % 2 == 0 ? "smooth_sandstone" : "sandstone", out, false));
                    }
                } else if (d < 2.0 + TIERS + 6) {
                    // the top terrace and the outer wall behind it
                    for (int y = 1; y <= top - 1; y++) {
                        this.set(x, y, z, support.at(x, y, z));
                    }

                    this.set(x, top - 1, z, d < 2.0 + TIERS + 4 ? "smooth_sandstone" : "cut_sandstone");
                    if (d >= 2.0 + TIERS + 4) {
                        for (int y = top; y <= top + 2; y++) {
                            this.set(x, y, z, y == top + 2 ? "smooth_sandstone_slab[type=bottom]" : "cut_sandstone");
                        }

                        // the outer face: two rows of arches
                        if (d >= 2.0 + TIERS + 5) {
                            double lu = Math.floorMod((int)Math.round(s), 7) - 3.0;
                            for (int y = 2; y <= top - 2; y++) {
                                boolean open = inArch(lu, y, 0, 1.5, 2, 8, false) || inArch(lu, y, 0, 1.5, 11, 16, false);
                                if (open) {
                                    this.set(x, y, z, null);
                                }
                            }
                        }
                    }
                }
            }
        }

        this.colonnade(top);
        this.guardian(top);
        this.royalBox(top);
        this.vomitoria(wallH);
        // pines on the terrace between the columns, and a forest outside
        for (int k = 0; k < 8; k++) {
            double t = Math.PI * 2.0 * (k + 0.5) / 8.0;
            if (Math.abs(Math.cos(t)) > 0.92) {
                continue;
            }

            int px = (int)Math.round(Math.cos(t) * (this.a + 2.0 + TIERS + 2.0));
            int pz = (int)Math.round(Math.sin(t) * (this.b + 2.0 + TIERS + 2.0));
            this.set(px, top - 1, pz, "grass_block");
            if (k % 2 == 0) {
                this.spruce(px, top, pz, 9, false);
            } else {
                this.broadleaf(px, top, pz, "oak_log", "oak", 4, 2.8);
            }
        }

        this.grove(2.0 + TIERS + 8, 0.7, 1);
        this.grove(2.0 + TIERS + 8, 0.3, 0);
    }

    /** Tall sandstone columns on the top terrace, arches between them, a ring beam with balusters and red cloths. */
    private void colonnade(int top) {
        double dc = 2.0 + TIERS + 1.6;
        int colTop = top + 16;
        int reach = (int)Math.ceil(this.a + dc) + 2;
        double span = 9.0;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double d = this.sd(x, z);
                if (d < dc - 0.9 || d >= dc + 0.9) {
                    continue;
                }

                if (x > 0 && Math.abs(z) < 10) {
                    continue; // the statue stands there
                }

                double s = this.along(x, z);
                double local = s - Math.round(s / span) * span;
                if (Math.abs(local) <= 1.0) {
                    for (int y = top; y <= colTop; y++) {
                        String b = y == top ? "cut_sandstone" : (y >= colTop - 1 ? "chiseled_sandstone" : "smooth_sandstone");
                        if (Math.abs(local) < 0.5 && y > top + 2 && y < colTop - 2 && d < dc) {
                            b = "red_terracotta";
                        }

                        this.set(x, y, z, b);
                    }
                } else {
                    double hw = span / 2.0 - 1.0;
                    double ya = colTop - hw + Math.sqrt(Math.max(0.0, hw * hw - (Math.abs(local) - 0.0) * (Math.abs(local) - 0.0)));
                    int yy = (int)Math.round(ya);
                    this.set(x, yy, z, "smooth_sandstone");
                    for (int y = yy + 1; y <= colTop + 3; y++) {
                        if (Math.floorMod((int)Math.round(s), 2) == 0) {
                            this.set(x, y, z, "sandstone_wall");
                        }
                    }
                }

                this.set(x, colTop + 4, z, "smooth_sandstone");
                this.set(x, colTop + 5, z, d < dc ? "smooth_sandstone_slab[type=bottom]" : "cut_sandstone");
            }
        }

        // red cloths hanging inside every other arch
        int n = (int)Math.round(Math.PI * (this.a + this.b + 2 * dc) / span);
        for (int k = 0; k < n; k += 2) {
            double s = (k + 0.5) * span;
            double t = s / ((this.a + this.b) / 2.0 + dc);
            double px = Math.cos(t) * (this.a + dc - 1.2);
            double pz = Math.sin(t) * (this.b + dc - 1.2);
            if (px > 0 && Math.abs(pz) < 12) {
                continue;
            }

            int bx = Math.abs(px) > Math.abs(pz) * this.a / this.b ? (int)Math.signum(px) : 0;
            int bz = bx == 0 ? (int)Math.signum(pz) : 0;
            Face f = new Face((int)Math.round(px) + bx, (int)Math.round(pz) + bz, bx, bz);
            this.cloth(f, 0, colTop + 3, 3, 9, "red_wool", "yellow_wool", "white_wool");
        }
    }

    /** A giant stone guardian at the east end: legs, tunic, armoured body, a crested helmet, spear and round shield. */
    private void guardian(int top) {
        int gx = (int)Math.round(this.a) + 2 + TIERS + 3;
        Mat stone = this.mixOf(311L, "andesite", "stone", 0.3, "polished_andesite", 0.25, "tuff", 0.06);
        Mat armour = this.mixOf(313L, "polished_andesite", "stone", 0.2, "andesite", 0.15);
        // pedestal
        this.fill(gx - 5, top, -6, gx + 5, top + 3, 6, of("polished_andesite"));
        this.fill(gx - 6, top, -7, gx + 6, top, 7, of("stone_bricks"));
        for (int[] c : new int[][]{{-5, -6}, {-5, 6}, {5, -6}, {5, 6}}) {
            this.fill(gx + c[0], top, c[1], gx + c[0], top + 4, c[1], of("chiseled_stone_bricks"));
        }

        this.fill(gx - 6, top + 1, -2, gx - 6, top + 2, 2, of("gold_block"));
        int y0 = top + 4;
        // legs
        for (int side = -1; side <= 1; side += 2) {
            this.ellipsoid(gx, y0 + 5, side * 2.2, 1.6, 6.0, 1.6, stone);
            this.ellipsoid(gx - 0.5, y0 + 1, side * 2.2, 2.2, 1.2, 1.4, armour);
        }

        // tunic and body
        this.ellipsoid(gx, y0 + 12, 0, 2.6, 3.2, 4.2, armour);
        this.ellipsoid(gx, y0 + 18, 0, 2.6, 5.0, 4.0, stone);
        this.ellipsoid(gx - 1, y0 + 18, 0, 1.8, 3.5, 3.0, armour);
        // shoulders and head
        for (int side = -1; side <= 1; side += 2) {
            this.ellipsoid(gx, y0 + 22, side * 4.6, 2.1, 1.9, 2.1, armour);
        }

        this.ellipsoid(gx, y0 + 24, 0, 1.0, 1.2, 1.0, stone);
        this.ellipsoid(gx, y0 + 27, 0, 2.3, 2.6, 2.2, armour);
        this.fill(gx - 3, y0 + 26, -1, gx - 3, y0 + 27, 1, of("black_concrete"));
        for (int dx = -3; dx <= 3; dx++) {
            int hy = y0 + 30 + (int)Math.round(Math.sqrt(Math.max(0, 9 - dx * dx)) * 0.7);
            this.set(gx + dx, hy, 0, "red_wool");
            this.set(gx + dx, hy - 1, 0, "red_wool");
        }

        // spear arm (south side) and spear
        this.line(gx, y0 + 21, 5.2, gx - 2, y0 + 15, 6.4, "andesite");
        this.ellipsoid(gx - 2, y0 + 15, 6.4, 1.2, 1.2, 1.2, armour);
        for (int y = top + 1; y <= y0 + 36; y++) {
            this.set(gx - 2, y, 6, log("dark_oak_log", 'y'));
            this.set(gx - 2, y, 7, null);
        }

        this.set(gx - 2, y0 + 37, 6, "iron_block");
        this.set(gx - 2, y0 + 38, 6, "iron_block");
        this.set(gx - 2, y0 + 39, 6, "lightning_rod[facing=up]");
        this.set(gx - 2, y0 + 36, 5, "iron_trapdoor[facing=north,half=bottom,open=true]");
        this.set(gx - 2, y0 + 36, 7, "iron_trapdoor[facing=south,half=bottom,open=true]");
        // shield arm (north side) and the round shield in front of the body
        this.line(gx, y0 + 21, -5.2, gx - 3, y0 + 17, -5.5, "andesite");
        double sx = gx - 4.5;
        double sy = y0 + 16;
        double sz = -5.0;
        for (int dy = -5; dy <= 5; dy++) {
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.hypot(dy, dz);
                if (r > 4.8) {
                    continue;
                }

                String b = r > 3.9 ? "white_concrete" : (r < 1.2 ? "gold_block" : (Math.abs(dy) <= 0 || Math.abs(dz) <= 0 ? "white_concrete" : "red_concrete"));
                this.set((int)Math.round(sx), (int)Math.round(sy) + dy, (int)Math.round(sz) + dz, b);
                this.set((int)Math.round(sx) + 1, (int)Math.round(sy) + dy, (int)Math.round(sz) + dz, "red_terracotta");
            }
        }
    }

    private void ellipsoid(double ox, double oy, double oz, double rx, double ry, double rz, Mat m) {
        for (int x = (int)Math.floor(ox - rx); x <= (int)Math.ceil(ox + rx); x++) {
            for (int y = (int)Math.floor(oy - ry); y <= (int)Math.ceil(oy + ry); y++) {
                for (int z = (int)Math.floor(oz - rz); z <= (int)Math.ceil(oz + rz); z++) {
                    double e = (x - ox) * (x - ox) / (rx * rx) + (y - oy) * (y - oy) / (ry * ry) + (z - oz) * (z - oz) / (rz * rz);
                    if (e <= 1.05) {
                        this.set(x, y, z, m.at(x, y, z));
                    }
                }
            }
        }
    }

    /** The royal box at the west end: a canopy on columns over the top of the stands, with striped awning and banners. */
    private void royalBox(int top) {
        int bx = -(int)Math.round(this.a) - 2 - TIERS + 2;
        Face f = new Face(bx, 0, -1, 0);
        f.box(-6, top - 4, 0, 6, top - 1, 6, of("smooth_sandstone"));
        for (int u = -6; u <= 6; u++) {
            f.set(u, top - 4, -1, stairs("smooth_sandstone", f.back(), false));
            f.set(u, top, 0, Math.abs(u) == 6 || u % 2 == 0 ? "sandstone_wall" : null);
        }

        for (int u : new int[]{-6, -2, 2, 6}) {
            for (int y = top; y <= top + 7; y++) {
                f.set(u, y, 1, "quartz_pillar[axis=y]");
                f.set(u, y, 6, "quartz_pillar[axis=y]");
            }
        }

        for (int u = -7; u <= 7; u++) {
            for (int w = 0; w <= 7; w++) {
                int y = top + 8 + (w < 2 ? 0 : (w < 5 ? 1 : 2));
                f.set(u, y, w, Math.floorMod(u, 2) == 0 ? "red_wool" : "yellow_wool");
            }

            f.set(u, top + 7, -1, Math.floorMod(u, 2) == 0 ? "red_wool" : "yellow_wool");
        }

        f.set(0, top, 3, "gold_block");
        f.set(0, top + 1, 3, "gold_block");
        f.set(0, top + 2, 3, stairs("quartz", f.front(), false));
        for (int u : new int[]{-4, 4}) {
            this.cloth(f.moved(0, 0), u, top - 2, 3, 6, "red_wool", "yellow_wool", "white_wool");
        }
    }

    /** Barred gates in the podium on the north and south sides. */
    private void vomitoria(int wallH) {
        for (int side = -1; side <= 1; side += 2) {
            int z = side * (int)Math.round(this.b);
            Face f = new Face(0, z, 0, side);
            f.box(-3, 1, 0, 3, wallH + 1, 1, "stone_bricks");
            this.arch(f, 0, 1, 3, 4, false, 2, "black_concrete", "polished_andesite");
            for (int u = -1; u <= 1; u++) {
                for (int y = 1; y <= 3; y++) {
                    if (inArch(u, y, 0, 1.0, 1, 4, false)) {
                        f.set(u, y, 0, "iron_bars");
                    }
                }
            }

            f.set(0, wallH + 1, -1, "chiseled_stone_bricks");
        }
    }

    // ================================================================== PLAZA (cliffs behind a panelled wall)

    private int[][] heights;

    private void plaza() {
        String panel;
        String panel2;
        Mat pillar;
        String cap;
        String ledge;
        String light;
        int maxH;
        switch (this.style) {
            case SAKURA_TERRACE -> {
                panel = "white_terracotta";
                panel2 = "pink_terracotta";
                pillar = of("stripped_dark_oak_wood[axis=y]");
                cap = "dark_oak_slab[type=bottom]";
                ledge = "polished_diorite";
                light = "lantern[hanging=false]";
                maxH = 26;
            }
            case EMBER_SANDS -> {
                panel = "orange_terracotta";
                panel2 = "terracotta";
                pillar = this.mixOf(401L, "polished_andesite", "stone_bricks", 0.3);
                cap = "polished_andesite_slab[type=bottom]";
                ledge = "smooth_sandstone";
                light = "soul_lantern[hanging=false]";
                maxH = 30;
            }
            default -> {
                panel = "cyan_terracotta";
                panel2 = "cyan_terracotta";
                pillar = this.mixOf(401L, "stone_bricks", "polished_andesite", 0.25, "mossy_stone_bricks", 0.1);
                cap = "polished_andesite_slab[type=bottom]";
                ledge = "polished_andesite";
                light = "lantern[hanging=false]";
                maxH = 38;
            }
        }

        // the panelled wall, pillars with lanterns, and the walkway behind
        int reach = this.R;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double d = this.sd(x, z);
                if (d < 0.0 || d >= 6.0) {
                    continue;
                }

                double s = this.along(x, z);
                double local = s - Math.round(s / 7.0) * 7.0;
                if (d < 1.6) {
                    if (Math.abs(local) < 1.0) {
                        for (int y = 1; y <= 4; y++) {
                            this.set(x, y, z, pillar.at(x, y, z));
                        }

                        this.set(x, 5, z, this.style == Style.SAKURA_TERRACE ? "dark_oak_planks" : "chiseled_stone_bricks");
                        if (Math.abs(local) < 0.5 && d < 0.8) {
                            this.set(x, 6, z, light);
                        }
                    } else {
                        for (int y = 1; y <= 3; y++) {
                            this.set(x, y, z, y == 3 ? panel2 : panel);
                        }

                        this.set(x, 4, z, cap);
                    }
                } else {
                    for (int y = 1; y <= 2; y++) {
                        this.set(x, y, z, "stone");
                    }

                    this.set(x, 3, z, Math.abs(local) < 0.6 && d > 4.5 ? "chiseled_stone_bricks" : ledge);
                }
            }
        }

        // lamp posts and banner poles on the walkway
        int n = (int)Math.round(Math.PI * (this.a + this.b + 7) / 14.0);
        for (int k = 0; k < n; k++) {
            double t = Math.PI * 2.0 * (k + 0.25) / n;
            int px = (int)Math.round(Math.cos(t) * (this.a + 4.2));
            int pz = (int)Math.round(Math.sin(t) * (this.b + 4.2));
            if (k % 2 == 0) {
                this.lantern(px, 4, pz, this.style == Style.SAKURA_TERRACE ? "dark_oak_fence" : "stone_brick_wall", 3, light);
            } else {
                String col = this.style == Style.EMBER_SANDS ? "orange" : (this.style == Style.SAKURA_TERRACE ? "pink" : (k % 4 == 1 ? "red" : "blue"));
                for (int y = 4; y <= 10; y++) {
                    this.set(px, y, pz, "spruce_fence");
                }

                int bx = (int)Math.round(Math.cos(t));
                int bz = (int)Math.round(Math.sin(t));
                this.set(px - (Math.abs(bx) >= Math.abs(bz) ? 0 : 1), 10, pz - (Math.abs(bx) >= Math.abs(bz) ? 1 : 0), col + "_wool");
                for (int y = 6; y <= 10; y++) {
                    this.set(px - Math.round(Math.signum(bx)) * 0, y, pz, y == 10 ? "spruce_fence" : "spruce_fence");
                }

                Face f = new Face(px - (Math.abs(bx) >= Math.abs(bz) ? (int)Math.signum(bx) : 0), pz - (Math.abs(bx) < Math.abs(bz) ? (int)Math.signum(bz) : 0),
                    Math.abs(bx) >= Math.abs(bz) ? (int)Math.signum(bx) : 0, Math.abs(bx) < Math.abs(bz) ? (int)Math.signum(bz) : 0);
                this.cloth(f.moved(0, 2), 0, 9, 3, 5, col + "_wool", "white_wool", "white_wool");
            }
        }

        this.terrain(maxH);
        switch (this.style) {
            case SAKURA_TERRACE -> {
                this.pagoda();
                this.torii();
                this.grove(7.0, 0.55, 3);
            }
            case EMBER_SANDS -> {
                this.wyvern();
                this.aqueduct();
                this.grove(7.0, 0.3, 4);
            }
            default -> {
                this.lodge();
                this.watchtower();
                this.grove(7.0, 0.8, 1);
                this.grove(7.0, 0.2, 0);
            }
        }
    }

    /** A spot on the cliffs behind the wall (sector: 0 = east end, 1 = west end) flattened to a shelf. */
    private int[] shelf(boolean east, double dist, int halfW, int halfD, int h) {
        int cx = (int)Math.round((east ? 1 : -1) * (this.a + dist));
        for (int x = cx - halfD - 3; x <= cx + halfD + 3; x++) {
            for (int z = -halfW - 3; z <= halfW + 3; z++) {
                int ix = x + this.R;
                int iz = z + this.R;
                if (ix < 0 || iz < 0 || ix >= this.heights.length || iz >= this.heights.length) {
                    continue;
                }

                boolean inside = Math.abs(x - cx) <= halfD && Math.abs(z) <= halfW;
                int want = inside ? h : Math.max(h, this.heights[ix][iz]);
                this.heights[ix][iz] = inside ? h : want;
            }
        }

        return new int[]{cx, h};
    }

    /** Cliffs and slopes rising behind the walkway: rock strata on steep faces, grass (or sand) on gentle tops. */
    private void terrain(int maxH) {
        int size = this.R * 2 + 1;
        this.heights = new int[size][size];
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                int h = 0;
                if (d >= 6.0) {
                    double rise = Math.pow(d - 6.0, 1.1) * 1.15;
                    double n = noise(this.seed + 501L, x * 0.7, z * 0.7);
                    double crag = noise(this.seed + 503L, x * 2.2, z * 2.2);
                    double raw = Math.min(maxH, rise * (0.45 + 0.8 * n)) + (crag > 0.7 ? (crag - 0.7) * 26 : 0);
                    // stepped into ledges: rock faces with flat tops
                    double jitter = noise(this.seed + 507L, x * 1.1, z * 1.1) * 5.0;
                    int step = this.style == Style.SAKURA_TERRACE ? 4 : 6;
                    h = 3 + (int)(Math.floor((raw + jitter) / step) * step);
                    h = Math.max(3, Math.min(h, maxH + 6));
                }

                this.heights[x + this.R][z + this.R] = h;
            }
        }

        switch (this.style) {
            case SAKURA_TERRACE -> this.shelf(true, 15, 9, 9, 14);
            case EMBER_SANDS -> this.shelf(true, 15, 7, 7, 16);
            default -> {
                this.shelf(true, 15, 8, 8, 14);
                this.shelf(false, 15, 5, 5, 12);
            }
        }

        if (this.style == Style.SAKURA_TERRACE) {
            this.shelf(false, 10, 6, 4, 3);
        }

        if (this.style == Style.EMBER_SANDS) {
            this.shelf(false, 12, 10, 4, 4);
        }

        String[] rock;
        String top;
        switch (this.style) {
            case SAKURA_TERRACE -> {
                rock = new String[]{"stone", "calcite", "andesite", "diorite", "stone"};
                top = "grass_block";
            }
            case EMBER_SANDS -> {
                rock = new String[]{"calcite", "white_terracotta", "diorite", "calcite", "smooth_sandstone"};
                top = "sand";
            }
            case VOLCANO_FORGE -> {
                rock = new String[]{"basalt[axis=y]", "blackstone", "tuff", "smooth_basalt", "blackstone", "deepslate"};
                top = "blackstone";
            }
            case PIRATE_COVE -> {
                rock = new String[]{"stone", "andesite", "stone", "cobblestone", "tuff", "andesite"};
                top = "grass_block";
            }
            default -> {
                rock = new String[]{"stone", "andesite", "tuff", "stone", "cobblestone", "andesite"};
                top = "grass_block";
            }
        }

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                int h = this.heights[x + this.R][z + this.R];
                if (h <= 3 || this.sd(x, z) < 6.0) {
                    continue;
                }

                int slope = 0;
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ix = x + o[0] + this.R;
                    int iz = z + o[1] + this.R;
                    if (ix >= 0 && iz >= 0 && ix < size && iz < size) {
                        slope = Math.max(slope, Math.abs(this.heights[ix][iz] - h));
                    }
                }

                for (int y = 1; y <= h; y++) {
                    String b;
                    if (y == h && slope <= 1) {
                        b = top;
                        if (this.style == Style.HIGHLAND_PLAZA && hash3(this.seed, x, y, z) < 0.12) {
                            b = "podzol";
                        }
                    } else if (y >= h - 2 && slope <= 1 && !top.equals("sand")) {
                        b = "dirt";
                    } else {
                        double v = noise(this.seed + 509L, x * 1.4 + y * 0.6, z * 1.4 - y * 0.6);
                        b = rock[Math.floorMod((int)(y * 0.45 + v * 3.0), rock.length)];
                    }

                    this.set(x, y, z, b);
                }

                if (slope <= 1 && this.style == Style.HIGHLAND_PLAZA && hash3(this.seed + 1, x, h, z) < 0.07) {
                    this.set(x, h + 1, z, hash3(this.seed + 2, x, h, z) < 0.3 ? "fern" : "short_grass");
                }

                if (slope <= 1 && this.style == Style.SAKURA_TERRACE && hash3(this.seed + 1, x, h, z) < 0.3) {
                    this.set(x, h + 1, z, "pink_petals[flower_amount=" + (1 + (int)(hash3(this.seed + 3, x, h, z) * 4)) + ",facing=east]");
                }
            }
        }
    }

    /** Highland: a timber lodge on the east shelf - stone base, timber frame with white infill, dark roof, balcony, chimney. */
    private void lodge() {
        int cx = (int)Math.round(this.a + 15);
        int y0 = 15;
        Face f = new Face(cx - 6, 0, 1, 0);
        Mat stoneBase = this.mixOf(411L, "cobblestone", "mossy_cobblestone", 0.3, "stone_bricks", 0.2);
        f.box(-8, y0, 0, 8, y0 + 3, 11, stoneBase);
        f.box(-8, y0 + 4, 0, 8, y0 + 12, 11, of("white_terracotta"));
        for (int u = -8; u <= 8; u++) {
            for (int y = y0 + 4; y <= y0 + 12; y++) {
                boolean post = Math.floorMod(u, 4) == 0 || Math.abs(u) == 8;
                boolean beam = y == y0 + 4 || y == y0 + 8 || y == y0 + 12;
                if (post || beam) {
                    f.set(u, y, 0, post ? log("stripped_spruce_log", 'y') : log("stripped_spruce_log", f.rx != 0 ? 'x' : 'z'));
                } else if (Math.floorMod(u + y, 5) == 0) {
                    f.set(u, y, 0, log("stripped_spruce_log", 'y'));
                }
            }
        }

        for (int u : new int[]{-6, -2, 2, 6}) {
            f.set(u, y0 + 6, 0, "glass_pane");
            f.set(u, y0 + 10, 0, "glass_pane");
            f.set(u, y0 + 6, -1, "spruce_trapdoor[facing=" + f.front() + ",half=top,open=false]");
            f.set(u, y0 + 10, -1, "spruce_trapdoor[facing=" + f.front() + ",half=top,open=false]");
        }

        this.arch(f, 0, y0, 3, 4, false, 1, "spruce_planks", "stone_bricks");
        // balcony
        for (int u = -7; u <= 7; u++) {
            f.set(u, y0 + 8, -1, "spruce_slab[type=top]");
            f.set(u, y0 + 9, -1, "spruce_fence");
        }

        int ax = Math.min(f.x(-9, -1), f.x(9, 12));
        int bx = Math.max(f.x(-9, -1), f.x(9, 12));
        int az = Math.min(f.z(-9, -1), f.z(9, 12));
        int bz = Math.max(f.z(-9, -1), f.z(9, 12));
        this.gableRoof(ax, az, bx, bz, y0 + 13, true, "dark_oak", of("dark_oak_planks"), of("white_terracotta"));
        for (int y = y0; y <= y0 + 22; y++) {
            this.set(f.x(5, 8), y, f.z(5, 8), stoneBase.at(0, y, 0));
        }

        this.set(f.x(5, 8), y0 + 23, f.z(5, 8), "campfire[lit=false]");
        this.cloth(f, -4, y0 + 12, 3, 4, "red_wool", "white_wool", "white_wool");
        this.cloth(f, 4, y0 + 12, 3, 4, "blue_wool", "white_wool", "white_wool");
    }

    /** Highland: a round stone watchtower with a spruce cone roof on the west shelf. */
    private void watchtower() {
        int cx = -(int)Math.round(this.a + 15);
        int y0 = 12;
        Mat st = this.mixOf(421L, "stone_bricks", "mossy_stone_bricks", 0.25, "cobblestone", 0.15);
        this.cyl(cx, 0, 4.5, y0, y0 + 18, st);
        for (int y = y0 + 3; y <= y0 + 15; y += 4) {
            for (int k = 0; k < 4; k++) {
                double t = k * Math.PI / 2 + y;
                this.set(cx + (int)Math.round(Math.cos(t) * 4.5), y, (int)Math.round(Math.sin(t) * 4.5), "spruce_trapdoor[facing=east,half=bottom,open=true]");
            }
        }

        this.ringAt(cx, 0, 4.0, 5.6, y0 + 19, of("spruce_planks"));
        this.ringAt(cx, 0, 5.0, 5.6, y0 + 20, of("spruce_fence"));
        for (int k = 0; k < 6; k++) {
            double t = k * Math.PI / 3;
            for (int y = y0 + 20; y <= y0 + 23; y++) {
                this.set(cx + (int)Math.round(Math.cos(t) * 5), y, (int)Math.round(Math.sin(t) * 5), log("spruce_log", 'y'));
            }
        }

        this.coneRoof(cx, 0, 6.5, y0 + 24, 9, this.mixOf(423L, "spruce_planks", "dark_oak_planks", 0.3));
        this.spire(cx, y0 + 34, 0, 2, "spruce_fence", "lantern[hanging=false]");
    }

    /** Sakura: a four-tier pagoda on the east shelf with dark tiled eaves and a golden spire. */
    private void pagoda() {
        int cx = (int)Math.round(this.a + 15);
        int y = 15;
        int half = 7;
        for (int tier = 0; tier < 4; tier++) {
            int h = tier == 0 ? 6 : 4;
            this.fill(cx - half, y, -half, cx + half, y + h - 1, half, of("white_terracotta"));
            for (int[] c : new int[][]{{-half, -half}, {-half, half}, {half, -half}, {half, half}, {0, -half}, {0, half}, {-half, 0}, {half, 0}}) {
                for (int k = 0; k < h; k++) {
                    this.set(cx + c[0], y + k, c[1], "red_concrete");
                }
            }

            for (Face f : new Face[]{new Face(cx - half, 0, 1, 0), new Face(cx + half, 0, -1, 0), new Face(cx, -half, 0, 1), new Face(cx, half, 0, -1)}) {
                if (tier == 0) {
                    this.arch(f, 0, y, 3, 4, false, 1, "dark_oak_planks", "red_concrete");
                }

                for (int u : new int[]{-half / 2 - 1, half / 2 + 1}) {
                    f.set(u, y + h - 2, 0, "dark_oak_trapdoor[facing=" + f.front() + ",half=top,open=true]");
                }
            }

            // eaves: a wide dark roof with upturned corners
            int e = half + 2;
            for (int x = -e; x <= e; x++) {
                for (int z = -e; z <= e; z++) {
                    boolean edge = Math.abs(x) == e || Math.abs(z) == e;
                    this.set(cx + x, y + h, z, edge ? "deepslate_tile_slab[type=bottom]" : "deepslate_tiles");
                    if (Math.abs(x) <= e - 2 && Math.abs(z) <= e - 2) {
                        this.set(cx + x, y + h + 1, z, "deepslate_tile_slab[type=bottom]");
                    }
                }
            }

            for (int[] c : new int[][]{{-e, -e}, {-e, e}, {e, -e}, {e, e}}) {
                this.set(cx + c[0], y + h + 1, c[1], "deepslate_tile_wall");
                this.set(cx + c[0], y + h + 2, c[1], "lantern[hanging=false]");
            }

            y += h + 2;
            half -= 2;
        }

        this.spire(cx, y, 0, 4, "gold_block", "lightning_rod[facing=up]");
    }

    /** Sakura: a red torii gate on the west shelf with stone lanterns. */
    private void torii() {
        int cx = -(int)Math.round(this.a + 10);
        int y0 = 4;
        for (int side = -1; side <= 1; side += 2) {
            for (int y = y0; y <= y0 + 9; y++) {
                this.set(cx, y, side * 5, "red_concrete");
            }

            this.set(cx, y0, side * 5, "polished_blackstone");
            this.lantern(cx + 2, y0, side * 8, "stone_brick_wall", 2, "lantern[hanging=false]");
        }

        for (int z = -7; z <= 7; z++) {
            this.set(cx, y0 + 7, z, "red_concrete");
            this.set(cx, y0 + 10, z, Math.abs(z) == 7 ? "blackstone_slab[type=bottom]" : "polished_blackstone");
            if (Math.abs(z) <= 6) {
                this.set(cx, y0 + 11, z, "polished_blackstone_slab[type=bottom]");
            }
        }

        this.set(cx, y0 + 8, 0, "red_concrete");
        this.set(cx, y0 + 9, 0, "red_concrete");
    }

    /** Ember: a great red wyvern on a rock on the east shelf, wings spread, neck curved toward the floor. */
    private void wyvern() {
        int cx = (int)Math.round(this.a + 15);
        int y0 = 17;
        Mat scales = this.mixOf(431L, "red_nether_bricks", "red_terracotta", 0.35, "nether_bricks", 0.1);
        Mat belly = this.mixOf(433L, "orange_terracotta", "red_sandstone", 0.3);
        // rock it perches on
        this.ellipsoid(cx + 2, y0 - 1, 0, 6, 3, 5, this.mixOf(435L, "calcite", "diorite", 0.4));
        // body and legs
        this.ellipsoid(cx + 2, y0 + 6, 0, 6.0, 3.6, 3.4, scales);
        this.ellipsoid(cx + 0.5, y0 + 5, 0, 4.0, 2.4, 2.4, belly);
        for (int side = -1; side <= 1; side += 2) {
            this.line(cx + 4, y0 + 4, side * 2.5, cx + 3, y0 + 1, side * 3.5, "red_nether_bricks");
            this.line(cx - 1, y0 + 4, side * 2.5, cx - 2, y0 + 1, side * 3.0, "red_nether_bricks");
            this.set(cx - 3, y0 + 1, side * 3, "bone_block[axis=x]");
            this.set(cx + 2, y0 + 1, side * 4, "bone_block[axis=x]");
        }

        // neck and head, toward the floor (west)
        double px = cx - 2;
        double py = y0 + 8;
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            double nx = cx - 2 - t * 6;
            double ny = y0 + 8 + Math.sin(t * Math.PI * 0.8) * 7;
            this.ellipsoid(nx, ny, 0, 1.6 - t * 0.4, 1.6 - t * 0.4, 1.5 - t * 0.4, scales);
            px = nx;
            py = ny;
        }

        int hx2 = (int)Math.round(px) - 2;
        int hy = (int)Math.round(py);
        this.ellipsoid(hx2, hy, 0, 2.6, 1.4, 1.5, scales);
        this.ellipsoid(hx2 - 2, hy - 1, 0, 1.6, 0.8, 1.0, scales);
        this.set(hx2 - 4, hy - 2, 0, "red_nether_bricks");
        this.set(hx2, hy, -2, "yellow_concrete");
        this.set(hx2, hy, 2, "yellow_concrete");
        for (int side = -1; side <= 1; side += 2) {
            this.line(hx2 + 1, hy + 1, side, hx2 + 4, hy + 3, side * 2, "bone_block[axis=y]");
        }

        // wings: bones spreading out and back, red membranes between them
        for (int side = -1; side <= 1; side += 2) {
            double[] shoulder = {cx + 1, y0 + 9, side * 3.0};
            double[][] tips = {{cx - 3, y0 + 22, side * 13.0}, {cx + 3, y0 + 20, side * 16.0}, {cx + 9, y0 + 14, side * 15.0}, {cx + 10, y0 + 8, side * 10.0}};
            for (int i = 0; i < tips.length; i++) {
                this.line(shoulder[0], shoulder[1], shoulder[2], tips[i][0], tips[i][1], tips[i][2], i == 0 ? "red_nether_bricks" : "nether_bricks");
                if (i + 1 < tips.length) {
                    // fill the membrane triangle shoulder - tip i - tip i+1
                    for (double u = 0; u <= 1.0; u += 0.04) {
                        for (double v = 0; v <= 1.0 - u; v += 0.04) {
                            double wx = shoulder[0] + (tips[i][0] - shoulder[0]) * u + (tips[i + 1][0] - shoulder[0]) * v;
                            double wy = shoulder[1] + (tips[i][1] - shoulder[1]) * u + (tips[i + 1][1] - shoulder[1]) * v;
                            double wz = shoulder[2] + (tips[i][2] - shoulder[2]) * u + (tips[i + 1][2] - shoulder[2]) * v;
                            int bx = (int)Math.round(wx);
                            int by = (int)Math.round(wy);
                            int bz = (int)Math.round(wz);
                            if (this.empty(bx, by, bz)) {
                                this.set(bx, by, bz, u + v > 0.92 ? "red_terracotta" : "red_concrete");
                            }
                        }
                    }
                }
            }
        }

        // tail curling behind
        for (int i = 0; i <= 18; i++) {
            double t = i / 18.0;
            double tx = cx + 7 + t * 8;
            double tz = Math.sin(t * Math.PI * 1.3) * 5;
            double ty = y0 + 5 - t * 4;
            this.ellipsoid(tx, ty, tz, 1.4 - t, 1.3 - t * 0.9, 1.4 - t, scales);
        }
    }

    /** Ember: an aqueduct of pale arches crossing the cliffs on the north side. */
    private void aqueduct() {
        int z0 = -(int)Math.round(this.b) - 16;
        int topY = 22;
        Mat st = this.mixOf(441L, "smooth_sandstone", "cut_sandstone", 0.3, "calcite", 0.15);
        for (int x = -this.inner + 4; x <= this.inner - 4; x++) {
            int local = Math.floorMod(x, 7);
            for (int dz = -1; dz <= 1; dz++) {
                int z = z0 + dz;
                for (int y = 1; y <= topY; y++) {
                    boolean pier = local <= 1;
                    boolean upper = y > 12;
                    double lu = local - 4.0;
                    boolean open = !pier && (inArch(lu, y, 0, 2.2, 1, 10, false) || upper && inArch(lu, y, 0, 2.2, 13, topY - 3, false));
                    if (!open) {
                        this.set(x, y, z, st.at(x, y, z));
                    } else if (y > 3) {
                        this.set(x, y, z, null);
                    }
                }

                this.set(x, topY + 1, z, dz == 0 ? "light_blue_stained_glass" : "smooth_sandstone_slab[type=bottom]");
            }
        }

        this.set(-this.inner + 4, topY + 1, z0, "smooth_sandstone");
        this.set(this.inner - 4, topY + 1, z0, "smooth_sandstone");
    }

    // ================================================================== GOTHIC (harbour town)

    private Mat dark;
    private Mat creamWall;
    private Mat copper;
    private static final String COPPER_STAIRS = "waxed_oxidized_cut_copper";

    private void gothic() {
        this.dark = this.mixOf(601L, "polished_deepslate", "deepslate_bricks", 0.3, "deepslate_tiles", 0.1);
        this.creamWall = this.mixOf(603L, "smooth_sandstone", "cut_sandstone", 0.25, "sandstone", 0.06);
        this.copper = this.mixOf(605L, "waxed_oxidized_cut_copper", "waxed_oxidized_copper", 0.25, "waxed_weathered_cut_copper", 0.08);
        // orange panel wall between dark pillars, and a stone quay behind it
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.0 || d >= 5.0) {
                    continue;
                }

                double s = this.along(x, z);
                boolean pil = Math.floorMod((int)Math.round(s), 6) == 0;
                if (d < 1.5) {
                    for (int y = 1; y <= 3; y++) {
                        this.set(x, y, z, pil ? this.dark.at(x, y, z) : (y == 3 ? "terracotta" : "orange_terracotta"));
                    }

                    this.set(x, 4, z, pil ? "polished_andesite" : "polished_andesite_slab[type=bottom]");
                    if (pil && d < 0.8) {
                        this.set(x, 5, z, "soul_lantern[hanging=false]");
                    }
                } else {
                    this.set(x, 1, z, "stone");
                    this.set(x, 2, z, "stone");
                    this.set(x, 3, z, Math.floorMod((int)Math.round(s), 4) == 0 ? "polished_andesite" : "stone_bricks");
                }
            }
        }

        int front = 5;
        Face east = new Face(this.hx + front, 0, 1, 0);
        Face west = new Face(-this.hx - front, 0, -1, 0);
        Face south = new Face(0, this.hz + front, 0, 1);
        Face north = new Face(0, -this.hz - front, 0, -1);
        int lx = this.hx + front;
        int lz = this.hz + front;
        this.townRow(north, -lx - 6, lx + 6, 601);
        this.townRow(south, -lx - 6, lx + 6, 602);
        this.townRow(east, -lz, -15, 603);
        this.townRow(east, 15, lz, 604);
        this.townRow(west, -lz, -11, 605);
        this.townRow(west, 11, lz, 606);
        this.cathedral(east);
        this.seaGate(west);
        this.hills(front + 16, 4, "grass_block", "dirt");
        this.grove(front + 14, 0.6, 5);
        this.grove(front + 14, 0.3, 0);
    }

    /** A row of tall narrow gothic town houses along a face, with arch bridges between some of them. */
    private void townRow(Face f, int u0, int u1, long salt) {
        java.util.Random r = new java.util.Random(this.seed ^ salt);
        int u = u0;
        int prevTop = -1;
        while (u <= u1) {
            int w = 6 + r.nextInt(3);
            if (u + w - 1 > u1) {
                w = u1 - u + 1;
            }

            if (w < 4) {
                break;
            }

            int h = 15 + r.nextInt(4) * 3;
            int depth = 10 + r.nextInt(3);
            boolean creamy = r.nextBoolean();
            this.townHouse(f, u, u + w - 1, h, depth, creamy, r);
            if (prevTop > 0 && r.nextInt(3) == 0) {
                // an arch bridge over the gap to the previous house
                int by = Math.min(prevTop, h) - 5;
                for (int du = -1; du <= 0; du++) {
                    for (int y = by; y <= by + 2; y++) {
                        f.set(u + du, y, 2, this.dark.at(0, y, 0));
                        f.set(u + du, y, 3, this.dark.at(0, y, 0));
                    }
                }
            }

            prevTop = h;
            u += w + (r.nextInt(4) == 0 ? 1 : 0);
        }
    }

    private void townHouse(Face f, int u0, int u1, int h, int depth, boolean creamy, java.util.Random r) {
        Mat wall = creamy ? this.creamWall : this.mixOf(611L, "tuff_bricks", "polished_tuff", 0.3, "stone_bricks", 0.1);
        f.box(u0, 1, 0, u1, h, depth, wall);
        // dark corner pilasters and floor cornices
        for (int y = 1; y <= h; y++) {
            f.set(u0, y, -1, this.dark.at(0, y, 0));
            f.set(u1, y, -1, this.dark.at(0, y, 0));
        }

        for (int u = u0; u <= u1; u++) {
            f.set(u, 1, 0, this.dark.at(0, 1, 0));
            f.set(u, 6, -1, stairs("polished_deepslate", f.back(), true));
            f.set(u, h, -1, stairs("deepslate_brick", f.back(), true));
        }

        int mid = (u0 + u1) / 2;
        boolean even = (u1 - u0) % 2 == 1;
        double uc = even ? mid + 0.5 : mid;
        // ground floor: a pointed doorway or shop arch
        this.arch(f, uc, 2, even ? 2 : 3, 4, true, 1, "dark_oak_planks", "polished_deepslate");
        // upper floors: tall purple lancets
        for (int y = 8; y + 5 < h; y += 7) {
            if (u1 - u0 >= 6) {
                this.window(f, u0 + 2, y, 1, 5, true, "purple_stained_glass_pane", "black_concrete", "polished_deepslate", "polished_deepslate", 1);
                this.window(f, u1 - 2, y, 1, 5, true, "purple_stained_glass_pane", "black_concrete", "polished_deepslate", "polished_deepslate", 1);
            } else {
                this.window(f, uc, y, even ? 2 : 1, 5, true, "purple_stained_glass_pane", "black_concrete", "polished_deepslate", "polished_deepslate", 1);
            }
        }

        // steep copper roof with its gable to the front
        int ax = Math.min(f.x(u0, 0), f.x(u1, depth));
        int bx = Math.max(f.x(u0, 0), f.x(u1, depth));
        int az = Math.min(f.z(u0, 0), f.z(u1, depth));
        int bz = Math.max(f.z(u0, 0), f.z(u1, depth));
        int top = this.gableRoof(ax, az, bx, bz, h + 1, f.rx == 0, COPPER_STAIRS, this.copper, wall);
        int top2 = this.gableRoof(ax, az, bx, bz, h + 2, f.rx == 0, COPPER_STAIRS, this.copper, wall);
        // the front gable edge in dark stone
        for (int y = Math.max(top, top2); y >= h + 1; y--) {
            for (int u = u0; u <= u1; u++) {
                String s = f.get(u, y, 0);
                if (s != null) {
                    f.set(u, y, 0, wall.at(0, y, 0));
                    if (f.empty(u, y + 1, 0)) {
                        f.set(u, y + 1, 0, slab("deepslate_brick", false));
                    }
                }
            }
        }

        if (r.nextInt(3) == 0) {
            this.spire(f.x(mid, 1), Math.max(top, top2) + 2, f.z(mid, 1), 2, "deepslate_brick_wall", "lightning_rod[facing=up]");
        }
    }

    /** The cathedral: twin towers with copper spires, a deep portal, a purple rose window and a gallery of arches. */
    private void cathedral(Face f) {
        int ch = 30;
        f.box(-14, 1, 0, 14, ch, 28, this.creamWall);
        // dark buttress lines
        for (int u : new int[]{-14, -6, 6, 14}) {
            for (int y = 1; y <= ch; y++) {
                f.set(u, y, -1, this.dark.at(0, y, 0));
                if (y < ch - 6) {
                    f.set(u, y, -2, this.dark.at(0, y, 0));
                }
            }

            f.set(u, ch - 6, -2, stairs("polished_deepslate", f.back(), false));
        }

        // the portal: three nested pointed arches
        for (int k = 0; k < 3; k++) {
            this.arch(f.moved(0, k), 0, 2, 9 - k * 2, 12 - k, true, 1, null, k == 0 ? "polished_deepslate" : null);
        }

        Face door = f.moved(0, 3);
        for (int u = -2; u <= 2; u++) {
            for (int y = 2; y <= 10; y++) {
                if (inArch(u, y, 0, 2.0, 2, 10, true)) {
                    door.set(u, y, 0, y > 7 ? "purple_stained_glass_pane" : "dark_oak_planks");
                    door.set(u, y, 1, "black_concrete");
                }
            }
        }

        for (int u = -5; u <= 5; u++) {
            f.set(u, 1, -1, stairs("polished_deepslate", f.back(), false));
        }

        // gallery of small arches over the portal
        for (int u = -5; u <= 5; u += 2) {
            this.window(f, u, 16, 1, 3, true, "black_concrete", "black_concrete", "polished_deepslate", null, 1);
        }

        // rose window
        double rc = 24.0;
        for (int u = -6; u <= 6; u++) {
            for (int y = 17; y <= 31; y++) {
                double dd = Math.hypot(u, y - rc);
                if (dd < 4.6) {
                    f.set(u, y, 0, null);
                    boolean spoke = Math.abs(u) == Math.abs((int)Math.round(y - rc)) || u == 0 || Math.round(y - rc) == 0;
                    f.set(u, y, 1, dd < 1.2 ? "magenta_stained_glass_pane" : (spoke ? "iron_bars" : "purple_stained_glass_pane"));
                    f.set(u, y, 2, "black_concrete");
                } else if (dd < 5.6) {
                    f.set(u, y, 0, "polished_deepslate");
                }
            }
        }

        // the nave roof and its gable
        int ax = Math.min(f.x(-14, 0), f.x(14, 28));
        int bx = Math.max(f.x(-14, 0), f.x(14, 28));
        int az = Math.min(f.z(-14, 0), f.z(14, 28));
        int bz = Math.max(f.z(-14, 0), f.z(14, 28));
        int top = this.gableRoof(ax, az, bx, bz, ch + 1, f.rx == 0, COPPER_STAIRS, this.copper, this.creamWall);
        for (int y = top; y >= ch + 1; y--) {
            for (int u = -14; u <= 14; u++) {
                if (f.get(u, y, 0) != null) {
                    f.set(u, y, 0, this.creamWall.at(0, y, 0));
                    if (f.empty(u, y + 1, 0)) {
                        f.set(u, y + 1, 0, slab("deepslate_brick", false));
                    }
                }
            }
        }

        this.spire(f.x(0, 0), top + 2, f.z(0, 0), 3, "deepslate_brick_wall", "lightning_rod[facing=up]");
        // twin towers with copper spires
        for (int side = -1; side <= 1; side += 2) {
            int u0 = side < 0 ? -21 : 15;
            int u1 = side < 0 ? -15 : 21;
            int th = 42;
            f.box(u0, 1, -2, u1, th, 8, this.creamWall);
            Face tf = f.moved(0, -2);
            for (int y = 1; y <= th; y++) {
                tf.set(u0, y, -1, this.dark.at(0, y, 0));
                tf.set(u1, y, -1, this.dark.at(0, y, 0));
            }

            int uc = (u0 + u1) / 2;
            for (int y : new int[]{4, 14, 24, 33}) {
                this.window(tf, uc, y, 3, 7, true, "purple_stained_glass_pane", "black_concrete", "polished_deepslate", "polished_deepslate", 1);
            }

            for (int u = u0; u <= u1; u++) {
                tf.set(u, th, -1, stairs("deepslate_brick", tf.back(), true));
                tf.set(u, th + 1, 0, Math.floorMod(u, 2) == 0 ? "deepslate_brick_wall" : "polished_deepslate");
            }

            int ax2 = Math.min(f.x(u0, -2), f.x(u1, 8));
            int bx2 = Math.max(f.x(u0, -2), f.x(u1, 8));
            int az2 = Math.min(f.z(u0, -2), f.z(u1, 8));
            int bz2 = Math.max(f.z(u0, -2), f.z(u1, 8));
            int cxt = (ax2 + bx2) / 2;
            int czt = (az2 + bz2) / 2;
            int pt = th + 1;
            for (int k = 0; k < 16; k++) {
                double rr = 3.6 * (1.0 - k / 16.0);
                this.ringAt(cxt, czt, 0, rr, pt + k, this.copper);
            }

            this.spire(cxt, pt + 16, czt, 3, "deepslate_brick_wall", "lightning_rod[facing=up]");
            for (int[] c : new int[][]{{ax2, az2}, {ax2, bz2}, {bx2, az2}, {bx2, bz2}}) {
                this.spire(c[0], th + 2, c[1], 3, "deepslate_brick_wall", "soul_lantern[hanging=false]");
            }
        }
    }

    /** The sea gate at the west end: a tall gate with a portcullis between two octagonal towers with copper cones. */
    private void seaGate(Face f) {
        int gh = 24;
        f.box(-10, 1, -1, 10, gh, 10, this.dark);
        Face g = f.moved(0, -1);
        for (int u = -10; u <= 10; u++) {
            for (int y = 1; y <= gh; y++) {
                if (Math.abs(u) <= 7 && y >= 3 && y <= gh - 3 && Math.floorMod(u, 3) != 0) {
                    g.set(u, y, 0, this.creamWall.at(0, y, 0));
                }
            }

            g.set(u, gh, -1, stairs("deepslate_brick", g.back(), true));
            g.set(u, gh + 1, 0, Math.floorMod(u, 2) == 0 ? "deepslate_brick_wall" : "polished_deepslate");
        }

        this.arch(g, 0, 2, 7, 13, true, 3, "black_concrete", "polished_deepslate");
        for (int u = -3; u <= 3; u++) {
            for (int y = 7; y <= 14; y++) {
                if (inArch(u, y, 0, 3.0, 2, 14, true)) {
                    g.set(u, y, 1, "iron_bars");
                }
            }
        }

        this.window(g, 0, 17, 3, 5, true, "purple_stained_glass_pane", "black_concrete", "polished_deepslate", "polished_deepslate", 1);
        for (int side = -1; side <= 1; side += 2) {
            int tx = g.x(side * 13, 2);
            int tz = g.z(side * 13, 2);
            this.cyl(tx, tz, 4.2, 1, 32, this.creamWall);
            for (int k = 0; k < 8; k++) {
                double t = k * Math.PI / 4.0;
                for (int y = 1; y <= 32; y++) {
                    this.set(tx + (int)Math.round(Math.cos(t) * 4.2), y, tz + (int)Math.round(Math.sin(t) * 4.2), this.dark.at(0, y, 0));
                }
            }

            for (int y = 6; y < 30; y += 8) {
                this.set(tx + (int)Math.round(-f.bx * 4.2), y, tz + (int)Math.round(-f.bz * 4.2), "purple_stained_glass");
                this.set(tx + (int)Math.round(-f.bx * 4.2), y + 1, tz + (int)Math.round(-f.bz * 4.2), "purple_stained_glass");
            }

            this.ringAt(tx, tz, 3.5, 5.2, 32, of("polished_deepslate"));
            this.coneRoof(tx, tz, 5.0, 33, 12, this.copper);
            this.spire(tx, 46, tz, 2, "deepslate_brick_wall", "lightning_rod[facing=up]");
        }
    }
    // ================================================================== shared helpers for the 3.11.0 arenas

    /** A giant jungle tree: 2x2 trunk with buttress roots, a wide layered canopy and hanging vines. */
    private void jungleGiant(int x, int y, int z, int h) {
        for (int k = 0; k < h; k++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    this.set(x + dx, y + k, z + dz, log("jungle_log", 'y'));
                }
            }
        }

        for (int[] r : new int[][]{{-1, 0}, {2, 1}, {0, -1}, {1, 2}}) {
            for (int k = 0; k < 3; k++) {
                this.set(x + r[0], y + k - 1, z + r[1], log("jungle_log", 'y'));
            }
        }

        this.blob(x, y + h + 1, z, 7.5, leaves("jungle"));
        this.blob(x + 4, y + h - 3, z - 3, 5.0, leaves("jungle"));
        this.blob(x - 4, y + h - 4, z + 3, 5.0, leaves("jungle"));
        this.blob(x + 1, y + h - 6, z + 5, 4.0, leaves("jungle"));
        for (int i = 0; i < 18; i++) {
            int vx = x + this.rnd.nextInt(15) - 7;
            int vz = z + this.rnd.nextInt(15) - 7;
            for (int vy = y + h + 2; vy > y + h - 10; vy--) {
                String here = this.get(vx, vy, vz);
                if (here != null && here.startsWith("jungle_leaves") && this.empty(vx, vy - 1, vz)) {
                    int len = 3 + this.rnd.nextInt(7);
                    for (int k = 1; k <= len && this.empty(vx, vy - k, vz); k++) {
                        String face = this.get(vx + 1, vy - k + 1, vz) != null ? "east" : (this.get(vx - 1, vy - k + 1, vz) != null ? "west" : "north");
                        this.set(vx, vy - k, vz, "vine[" + face + "=true]");
                    }

                    break;
                }
            }
        }
    }

    /** A beach umbrella: a pole and a striped round canopy. */
    private void umbrella(int x, int y, int z, String color) {
        for (int k = 0; k < 3; k++) {
            this.set(x, y + k, z, "bamboo_fence");
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 2.4) {
                    boolean stripe = Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0), 2) == 0;
                    this.set(x + dx, y + 3, z + dz, d < 1.2 ? color + "_wool" : (stripe ? color + "_carpet" : "white_carpet"));
                }
            }
        }

        this.set(x, y + 4, z, color + "_wool");
    }

    /** A hut on stilts with bamboo walls, a thatched (hay) roof and a little porch. */
    private void stiltHut(int x, int y, int z, int w, int d, String facing) {
        int floor = y + 3;
        for (int dx = -w; dx <= w; dx++) {
            for (int dz = -d; dz <= d; dz++) {
                this.set(x + dx, floor, z + dz, "spruce_planks");
                if ((Math.abs(dx) == w || Math.abs(dz) == d) && (Math.abs(dx) == w && Math.abs(dz) == d || dx == 0 || dz == 0)) {
                    for (int k = y; k < floor; k++) {
                        this.set(x + dx, k, z + dz, log("stripped_jungle_log", 'y'));
                    }
                }
            }
        }

        for (int dx = -w + 1; dx <= w - 1; dx++) {
            for (int dz = -d + 1; dz <= d - 1; dz++) {
                if (Math.abs(dx) == w - 1 || Math.abs(dz) == d - 1) {
                    for (int k = 1; k <= 3; k++) {
                        boolean corner = Math.abs(dx) == w - 1 && Math.abs(dz) == d - 1;
                        this.set(x + dx, floor + k, z + dz, corner ? log("stripped_jungle_log", 'y') : (k == 2 && (dx == 0 || dz == 0) ? "air" : "bamboo_mosaic"));
                    }
                }
            }
        }

        this.pyramidRoof(x - w, z - d, x + w, z + d, floor + 4, null, this.mixOf(711L, "hay_block", "bamboo_block", 0.15));
        int[] f = switch (facing) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "east" -> new int[]{1, 0};
            default -> new int[]{-1, 0};
        };
        for (int k = 1; k <= 3; k++) {
            this.set(x + f[0] * (w + k), floor - k + 1, z + f[1] * (d + k), stairs("spruce", opposite(facing), false));
        }

        this.set(x + f[0] * (w - 1), floor + 2, z + f[1] * (d - 1), "air");
        this.set(x + f[0] * (w - 1), floor + 1, z + f[1] * (d - 1), "air");
        this.set(x + f[0] * w, floor + 4, z + f[1] * d, "lantern[hanging=true]");
    }

    /** A red and white striped lighthouse on a rock, with a glowing lantern room and a gallery. */
    private void lighthouse(int x, int y, int z) {
        this.ellipsoid(x, y - 1, z, 7, 4, 7, this.mixOf(721L, "stone", "andesite", 0.3, "mossy_cobblestone", 0.2));
        int top = y + 26;
        for (int k = y; k <= top; k++) {
            double r = 3.6 - (k - y) * 0.035;
            String band = ((k - y) / 4) % 2 == 0 ? "red_concrete" : "white_concrete";
            this.ringAt(x, z, 0, r, k, of(band));
        }

        for (int k = y + 4; k < top - 2; k += 6) {
            this.set(x - 3, k, z, "glass");
            this.set(x - 3, k + 1, z, "glass");
        }

        this.ringAt(x, z, 0, 4.2, top + 1, of("polished_andesite"));
        this.ringAt(x, z, 3.6, 4.2, top + 2, of("iron_bars"));
        this.ringAt(x, z, 0, 2.2, top + 2, of("sea_lantern"));
        this.ringAt(x, z, 2.0, 2.6, top + 3, of("light_blue_stained_glass"));
        this.ringAt(x, z, 0, 1.6, top + 3, of("sea_lantern"));
        this.ringAt(x, z, 2.0, 2.6, top + 4, of("light_blue_stained_glass"));
        this.coneRoof(x, z, 3.0, top + 5, 4, of("red_concrete"));
        this.set(x, top + 10, z, "lightning_rod[facing=up]");
    }

    private void obelisk(int x, int y, int z, int h) {
        for (int k = 0; k < h; k++) {
            int half = k < 2 ? 2 : 1;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    if (k >= 2 && (Math.abs(dx) == 1 && Math.abs(dz) == 1) && k > h / 2) {
                        continue;
                    }

                    this.set(x + dx, y + k, z + dz, k < 2 ? "smooth_sandstone" : (k % 5 == 3 ? "chiseled_sandstone" : "cut_sandstone"));
                }
            }
        }

        this.set(x, y + h, z, "gold_block");
        this.set(x, y + h + 1, z, "lightning_rod[facing=up]");
    }

    // ================================================================== TROPICAL BEACH

    private void beach() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.0) {
                    continue;
                }

                double t = Math.atan2(z / this.b, x / this.a);
                boolean east = Math.abs(t) < 0.62;
                boolean west = Math.abs(Math.abs(t) - Math.PI) < 0.62;
                double s = this.along(x, z);
                if (d < 1.5) {
                    double local = s - Math.round(s / 5.0) * 5.0;
                    if (Math.abs(local) < 0.6) {
                        for (int y = 1; y <= 3; y++) {
                            this.set(x, y, z, log("stripped_jungle_log", 'y'));
                        }

                        this.set(x, 4, z, d < 0.8 ? "lantern[hanging=false]" : "bamboo_block[axis=y]");
                    } else {
                        this.set(x, 1, z, "bamboo_mosaic");
                        this.set(x, 2, z, "bamboo_fence");
                    }
                } else if (d >= 8.4 && d < 9.0 && !east && !west) {
                    this.set(x, 0, z, "white_concrete_powder");
                } else if (d >= 9.0 && d < 23.0 && !east && !west) {
                    // the lagoon: clear blue glass over pale sand - it looks like water but it is solid ground
                    double depth = Math.min(1.0, (d - 9.0) / 6.0);
                    this.set(x, 0, z, depth > 0.6 ? "light_blue_stained_glass" : "cyan_stained_glass");
                    this.set(x, -1, z, depth > 0.6 ? "prismarine" : "sand");
                } else if (d >= 23.0 && d < 24.0 && !east && !west) {
                    this.set(x, 0, z, "white_concrete_powder");
                }
            }
        }

        // umbrellas and loungers on the beach ring
        int n = (int)Math.round(Math.PI * (this.a + this.b + 10) / 13.0);
        String[] colors = {"red", "orange", "yellow", "lime", "light_blue", "pink", "magenta"};
        for (int k = 0; k < n; k++) {
            double t = Math.PI * 2 * (k + 0.5) / n;
            int ux = (int)Math.round(Math.cos(t) * (this.a + 5));
            int uz = (int)Math.round(Math.sin(t) * (this.b + 5));
            this.umbrella(ux, 1, uz, colors[k % colors.length]);
            int lx = (int)Math.round(Math.cos(t + 0.06) * (this.a + 6.5));
            int lz = (int)Math.round(Math.sin(t + 0.06) * (this.b + 6.5));
            String face = this.outward(lx, lz);
            this.set(lx, 1, lz, stairs("birch", face, false));
            this.set(lx - (face.equals("east") ? 1 : face.equals("west") ? -1 : 0), 1, lz - (face.equals("south") ? 1 : face.equals("north") ? -1 : 0), "birch_slab[type=bottom]");
        }

        // huts on stilts over the lagoon
        for (double t : new double[]{1.1, 1.9, -1.1, -1.9}) {
            int hx2 = (int)Math.round(Math.cos(t) * (this.a + 15));
            int hz2 = (int)Math.round(Math.sin(t) * (this.b + 15));
            this.stiltHut(hx2, 0, hz2, 3, 3, hz2 > 0 ? "north" : "south");
            // a boardwalk back to the beach
            for (int k = 0; k <= 7; k++) {
                int bx = (int)Math.round(Math.cos(t) * (this.a + 8 + k));
                int bz = (int)Math.round(Math.sin(t) * (this.b + 8 + k));
                this.set(bx, 1, bz, "spruce_slab[type=bottom]");
                this.set(bx + 1, 1, bz, "spruce_slab[type=bottom]");
            }
        }

        // the lighthouse on the rocks at the east end, the tiki bar at the west end
        this.lighthouse((int)Math.round(this.a + 17), 1, 0);
        this.tikiBar(-(int)Math.round(this.a + 11), 0);
        // sand islands and palms beyond the lagoon
        this.grove(3.5, 0.45, 2);
        this.grove(24.0, 0.7, 2);
    }

    /** A tiki bar: a round counter, bamboo posts and a big thatched roof, with lanterns and a sign. */
    private void tikiBar(int x, int z) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 5.2) {
                    this.set(x + dx, 0, z + dz, "bamboo_planks");
                }

                if (d > 3.4 && d < 4.4 && !(dx > 2 && Math.abs(dz) <= 1)) {
                    this.set(x + dx, 1, z + dz, "bamboo_block[axis=y]");
                    this.set(x + dx, 2, z + dz, "bamboo_slab[type=bottom]");
                }
            }
        }

        for (int[] c : new int[][]{{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
            for (int y = 1; y <= 5; y++) {
                this.set(x + c[0], y, z + c[1], log("stripped_jungle_log", 'y'));
            }

            this.set(x + c[0], 6, z + c[1], "lantern[hanging=false]");
        }

        this.coneRoof(x, z, 7.2, 6, 5, this.mixOf(731L, "hay_block", "bamboo_block", 0.2));
        this.set(x, 1, z, "barrel[facing=up]");
        this.set(x, 2, z, "barrel[facing=up]");
        this.set(x + 1, 2, z + 1, "flower_pot");
        this.set(x - 1, 2, z - 1, "potted_bamboo");
    }

    // ================================================================== JUNGLE TEMPLE

    private void temple() {
        Mat moss = this.mixOf(801L, "mossy_stone_bricks", "stone_bricks", 0.3, "cracked_stone_bricks", 0.12, "mossy_cobblestone", 0.15);
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.0 || d >= 11.0) {
                    continue;
                }

                String out = this.outward(x, z);
                double s = this.along(x, z);
                if (d < 1.0) {
                    this.set(x, 1, z, stairs("mossy_stone_brick", out, false));
                } else if (d < 4.0) {
                    this.set(x, 1, z, moss.at(x, 1, z));
                    this.set(x, 2, z, d < 2.0 ? stairs("mossy_stone_brick", out, false) : moss.at(x, 2, z));
                    if (d >= 2.0 && Math.floorMod((int)Math.round(s), 6) == 0) {
                        for (int y = 3; y <= 5; y++) {
                            this.set(x, y, z, y == 4 ? "chiseled_stone_bricks" : moss.at(x, y, z));
                        }

                        this.set(x, 6, z, "lantern[hanging=false]");
                    } else if (d >= 3.0 && hash3(this.seed, x, 3, z) < 0.25) {
                        this.set(x, 3, z, "moss_carpet");
                    }
                } else if (d < 10.0) {
                    // ruined walls: tall and broken, mossy, with vines down their faces
                    double r = noise(this.seed + 811L, s * 0.35, d);
                    int h = (int)Math.round(5 + r * 11);
                    if (r < 0.22) {
                        h = 2;
                    }

                    for (int y = 1; y <= h; y++) {
                        this.set(x, y, z, d < 5.0 && y % 4 == 2 && Math.floorMod((int)Math.round(s), 4) == 1 ? "chiseled_stone_bricks" : moss.at(x, y, z));
                    }

                    if (h > 3 && d < 5.0 && hash3(this.seed + 3, x, h, z) < 0.35) {
                        int len = 2 + (int)(hash3(this.seed + 4, x, 0, z) * (h - 2));
                        for (int k = 0; k < len; k++) {
                            int vx = x;
                            int vz = z;
                            switch (out) {
                                case "east" -> vx = x - 1;
                                case "west" -> vx = x + 1;
                                case "south" -> vz = z - 1;
                                default -> vz = z + 1;
                            }

                            if (this.empty(vx, h - k, vz) && this.sd(vx, vz) >= 4.0 - 1.5) {
                                this.set(vx, h - k, vz, "vine[" + out + "=true]");
                            }
                        }
                    }

                    if (hash3(this.seed + 5, x, h, z) < 0.3) {
                        this.set(x, h + 1, z, hash3(this.seed + 6, x, h, z) < 0.5 ? "moss_carpet" : "fern");
                    }
                }
            }
        }

        this.stepPyramid((int)Math.round(this.hx + 22.0), 0);
        this.stoneHead(-(int)Math.round(this.hx + 15.0), 0);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                this.jungleGiant(sx * (this.hx + 12), 1, sz * (this.hz + 12), 18);
            }

            this.jungleGiant(sx * 6, 1, sx * (this.hz + 15), 16);
        }

        this.hills(11.0, 6, "grass_block", "dirt");
        this.grove(11.0, 0.9, 6);
    }

    /** A Mayan step pyramid facing the floor: six terraces, a wide stairway up the front and a temple on top. */
    private void stepPyramid(int cx, int cz) {
        Mat moss = this.mixOf(821L, "mossy_stone_bricks", "stone_bricks", 0.35, "mossy_cobblestone", 0.15, "cracked_stone_bricks", 0.08);
        int half = 15;
        int y = 1;
        for (int step = 0; step < 6; step++) {
            int hlf = half - step * 2;
            for (int x = cx - hlf; x <= cx + hlf; x++) {
                for (int z = cz - hlf; z <= cz + hlf; z++) {
                    for (int k = 0; k < 4; k++) {
                        boolean edge = Math.abs(x - cx) == hlf || Math.abs(z - cz) == hlf;
                        this.set(x, y + k, z, edge && k == 3 ? "chiseled_stone_bricks" : moss.at(x, y + k, z));
                    }
                }
            }

            y += 4;
        }

        // the stairway on the west face
        for (int k = 0; k < y - 1; k++) {
            int x = cx - half - 1 + k / 2;
            for (int z = cz - 3; z <= cz + 3; z++) {
                this.set(x, k + 1, z, Math.abs(z - cz) == 3 ? moss.at(x, k + 1, z) : stairs("mossy_stone_brick", "east", false));
                for (int f = 1; f <= k; f++) {
                    if (this.empty(x, f, z)) {
                        this.set(x, f, z, moss.at(x, f, z));
                    }
                }
            }
        }

        // temple on top
        int ty = y;
        for (int x = cx - 4; x <= cx + 4; x++) {
            for (int z = cz - 4; z <= cz + 4; z++) {
                for (int k = 0; k <= 6; k++) {
                    boolean wall = Math.abs(x - cx) == 4 || Math.abs(z - cz) == 4;
                    if (wall || k == 6) {
                        this.set(x, ty + k, z, k == 5 ? "chiseled_stone_bricks" : moss.at(x, ty + k, z));
                    }
                }
            }
        }

        for (int k = 0; k < 4; k++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                this.set(cx - 4, ty + k, z, null);
            }
        }

        this.set(cx, ty, cz, "gold_block");
        this.set(cx, ty + 1, cz, "gold_block");
        this.set(cx, ty + 2, cz, "carved_pumpkin[facing=west]");
        // roof comb with carvings
        for (int z = cz - 3; z <= cz + 3; z++) {
            for (int k = 7; k <= 10; k++) {
                if (k < 10 || Math.abs(z - cz) <= 1) {
                    this.set(cx, ty + k, z, (k + z) % 2 == 0 ? "chiseled_stone_bricks" : moss.at(cx, ty + k, z));
                }
            }
        }

        this.set(cx - 5, ty + 2, cz - 2, "lantern[hanging=false]");
        this.set(cx - 5, ty + 2, cz + 2, "lantern[hanging=false]");
    }

    /** A huge carved stone head with a helmet, half sunk in the jungle, looking at the floor. */
    private void stoneHead(int cx, int cz) {
        Mat st = this.mixOf(831L, "stone", "andesite", 0.3, "mossy_cobblestone", 0.15, "tuff", 0.1);
        this.ellipsoid(cx, 8, cz, 6.5, 9, 7, st);
        this.ellipsoid(cx, 15, cz, 7.2, 3.6, 7.6, this.mixOf(833L, "mossy_stone_bricks", "stone_bricks", 0.3));
        int fx = cx + 6;
        for (int dz = -4; dz <= 4; dz++) {
            for (int y = 3; y <= 13; y++) {
                if (!this.empty(fx, y, cz + dz)) {
                    continue;
                }

                if (Math.abs(dz) <= 5 && y <= 12) {
                    this.set(fx, y, cz + dz, st.at(fx, y, cz + dz));
                }
            }
        }

        for (int side = -1; side <= 1; side += 2) {
            this.set(fx + 1, 10, cz + side * 2, "black_concrete");
            this.set(fx + 1, 10, cz + side * 3, "black_concrete");
            this.set(fx + 1, 11, cz + side * 2, "polished_andesite");
            this.set(fx + 1, 11, cz + side * 3, "polished_andesite");
            this.ellipsoid(cx, 9, cz + side * 7.5, 1.8, 2.6, 1.2, st);
        }

        for (int y = 6; y <= 9; y++) {
            this.set(fx + 1, y, cz, st.at(fx, y, cz));
        }

        this.set(fx + 2, 6, cz, st.at(fx, 6, cz));
        for (int dz = -2; dz <= 2; dz++) {
            this.set(fx + 1, 4, cz + dz, "polished_andesite");
            this.set(fx + 1, 3, cz + dz, "andesite");
        }

        for (int i = 0; i < 25; i++) {
            int vx = cx + this.rnd.nextInt(13) - 6;
            int vz = cz + this.rnd.nextInt(15) - 7;
            for (int vy = 19; vy > 2; vy--) {
                if (!this.empty(vx, vy, vz) && this.empty(vx, vy + 1, vz)) {
                    this.set(vx, vy + 1, vz, this.rnd.nextBoolean() ? "moss_carpet" : "fern");
                    break;
                }
            }
        }
    }

    // ================================================================== VOLCANO FORGE

    private void volcano() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.0 || d >= 6.0) {
                    continue;
                }

                double s = this.along(x, z);
                double local = s - Math.round(s / 6.0) * 6.0;
                if (d < 1.6) {
                    if (Math.abs(local) < 1.0) {
                        for (int y = 1; y <= 4; y++) {
                            this.set(x, y, z, "polished_basalt[axis=y]");
                        }

                        this.set(x, 5, z, "polished_blackstone");
                        if (Math.abs(local) < 0.5 && d < 0.8) {
                            this.set(x, 6, z, "soul_lantern[hanging=false]");
                        }
                    } else {
                        for (int y = 1; y <= 3; y++) {
                            this.set(x, y, z, y == 2 && Math.floorMod((int)Math.round(s), 3) == 0 ? "gilded_blackstone" : "polished_blackstone_bricks");
                        }

                        this.set(x, 4, z, "polished_blackstone_slab[type=bottom]");
                    }
                } else {
                    for (int y = 1; y <= 2; y++) {
                        this.set(x, y, z, "blackstone");
                    }

                    this.set(x, 3, z, Math.abs(local) < 0.6 && d > 4.5 ? "shroomlight" : "polished_blackstone");
                }
            }
        }

        this.terrain(20);
        this.volcanoCone((int)Math.round(this.a + 24), 0);
        this.forge(-(int)Math.round(this.a + 13), 0);
        // basalt columns and glowing lava streaks on the slopes
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                if (this.sd(x, z) < 7.0) {
                    continue;
                }

                int h = this.groundTop(x, z);
                if (h < 4) {
                    continue;
                }

                double c = noise(this.seed + 901L, x * 0.9, z * 0.9);
                if (Math.abs(c - 0.5) < 0.03) {
                    this.set(x, h, z, hash3(this.seed, x, h, z) < 0.5 ? "shroomlight" : "orange_concrete");
                } else if (hash3(this.seed + 9, x, 0, z) < 0.006) {
                    int ph = 3 + (int)(hash3(this.seed + 10, x, 0, z) * 8);
                    for (int k = 1; k <= ph; k++) {
                        this.set(x, h + k, z, "basalt[axis=y]");
                    }
                }
            }
        }
    }

    /** The volcano: a steep dark cone with a glowing crater lake, lava streams down its sides and smoke on top. */
    private void volcanoCone(int cx, int cz) {
        int peak = 62;
        double base = 30;
        for (int x = cx - 32; x <= cx + 32; x++) {
            for (int z = cz - 32; z <= cz + 32; z++) {
                double r = Math.hypot(x - cx, z - cz) * (0.9 + 0.2 * noise(this.seed + 911L, x * 1.3, z * 1.3));
                if (r > base) {
                    continue;
                }

                int h = (int)Math.round(peak * Math.pow(1.0 - r / base, 1.35));
                double rr = Math.hypot(x - cx, z - cz);
                if (rr < 6.5) {
                    h = peak - 8;
                }

                for (int y = 1; y <= h; y++) {
                    if (this.empty(x, y, z) || y > this.groundTop(x, z) - 1) {
                        double v = noise(this.seed + 913L, x * 1.5 + y, z * 1.5 - y);
                        this.set(x, y, z, v > 0.7 ? "basalt[axis=y]" : (v < 0.25 ? "tuff" : (v < 0.4 ? "smooth_basalt" : "blackstone")));
                    }
                }

                if (rr < 6.5) {
                    this.set(x, h, z, rr < 5.5 ? (hash3(this.seed, x, h, z) < 0.4 ? "shroomlight" : "orange_concrete") : "magma_block");
                }

                if (rr >= 6.5 && rr < 8.0 && hash3(this.seed + 11, x, 0, z) < 0.18) {
                    this.set(x, h + 1, z, "campfire[lit=true]");
                }
            }
        }

        // lava streams down the slopes
        for (double t : new double[]{Math.PI, Math.PI * 0.72, Math.PI * 1.28, Math.PI * 0.4}) {
            for (double r = 6.5; r < base - 1; r += 0.5) {
                double wig = Math.sin(r * 0.4 + t * 3) * 1.6;
                int x = cx + (int)Math.round(Math.cos(t) * r - Math.sin(t) * wig);
                int z = cz + (int)Math.round(Math.sin(t) * r + Math.cos(t) * wig);
                int h = this.groundTop(x, z);
                if (h > 1) {
                    this.set(x, h, z, hash3(this.seed + 13, x, h, z) < 0.45 ? "shroomlight" : "orange_concrete");
                    this.set(x + 1, h, z, "magma_block");
                }
            }
        }
    }

    /** The dwarven forge hall: blackstone walls, a glowing arch full of furnaces, anvils and two smoking chimneys. */
    private void forge(int cx, int cz) {
        Mat wall = this.mixOf(921L, "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", 0.2, "blackstone", 0.15);
        Face f = new Face(cx + 7, cz, -1, 0);
        f.box(-11, 1, 0, 11, 14, 14, wall);
        for (int u = -11; u <= 11; u++) {
            f.set(u, 14, -1, stairs("polished_blackstone_brick", f.back(), true));
            f.set(u, 15, 0, Math.floorMod(u, 2) == 0 ? "polished_blackstone_brick_wall" : "polished_blackstone");
            if (Math.floorMod(u, 4) == 0) {
                for (int y = 1; y <= 13; y++) {
                    f.set(u, y, -1, y % 4 == 2 ? "gilded_blackstone" : "polished_blackstone");
                }
            }
        }

        this.arch(f, 0, 1, 7, 9, false, 4, null, "gilded_blackstone");
        Face back = f.moved(0, 4);
        for (int u = -3; u <= 3; u++) {
            for (int y = 1; y <= 3; y++) {
                back.set(u, y, 0, y == 2 ? "blast_furnace[facing=" + f.front() + ",lit=true]" : "polished_blackstone_bricks");
            }

            back.set(u, 4, 0, "shroomlight");
        }

        f.set(-4, 1, -2, "anvil[facing=north]");
        f.set(4, 1, -2, "anvil[facing=south]");
        f.set(-6, 1, -2, "smithing_table");
        f.set(6, 1, -2, "grindstone[face=floor,facing=north]");
        for (int u : new int[]{-8, 8}) {
            this.window(f, u, 6, 1, 4, true, "orange_stained_glass_pane", "shroomlight", "gilded_blackstone", null, 1);
            for (int y = 15; y <= 24; y++) {
                for (int dw = 9; dw <= 10; dw++) {
                    for (int du = u - 1; du <= u; du++) {
                        f.set(du, y, dw, "nether_bricks");
                    }
                }
            }

            f.set(u, 25, 9, "campfire[lit=true]");
        }

        int ax = Math.min(f.x(-12, 1), f.x(12, 15));
        int bx = Math.max(f.x(-12, 1), f.x(12, 15));
        int az = Math.min(f.z(-12, 1), f.z(12, 15));
        int bz = Math.max(f.z(-12, 1), f.z(12, 15));
        this.gableRoof(ax, az, bx, bz, 15, f.rx == 0, "nether_brick", of("nether_bricks"), wall);
    }

    // ================================================================== PIRATE COVE

    private void cove() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.0 || d >= 1.6) {
                    continue;
                }

                double s = this.along(x, z);
                int h = 3 + (int)Math.floorMod((int)Math.round(s * 1.7), 2);
                for (int y = 1; y <= h; y++) {
                    this.set(x, y, z, log("spruce_log", 'y'));
                }

                this.set(x, h + 1, z, "spruce_fence");
                if (Math.floorMod((int)Math.round(s), 9) == 0 && d < 0.8) {
                    this.set(x, h + 2, z, "lantern[hanging=false]");
                }
            }
        }

        // crates and barrels along the outside of the palisade
        int n = (int)Math.round(Math.PI * (this.a + this.b + 6) / 7.0);
        for (int k = 0; k < n; k++) {
            double t = Math.PI * 2 * k / n;
            if (Math.abs(Math.cos(t)) > 0.85 && Math.cos(t) > 0) {
                continue;
            }

            int cx = (int)Math.round(Math.cos(t) * (this.a + 3));
            int cz = (int)Math.round(Math.sin(t) * (this.b + 3));
            this.set(cx, 1, cz, k % 3 == 0 ? "barrel[facing=up]" : "spruce_planks");
            if (k % 2 == 0) {
                this.set(cx, 2, cz, k % 4 == 0 ? "barrel[facing=up]" : "chest[facing=north]");
            }
        }

        this.terrain(34);
        // the east end opens onto the beach and the sea, where the ship lies
        int sx = (int)Math.round(this.a + 22);
        for (int x = (int)Math.round(this.a + 4); x <= this.inner; x++) {
            for (int z = -22; z <= 22; z++) {
                double open = Math.abs(z) / 22.0;
                if (open > 1.0 - (x - this.a - 4) * 0.0 && Math.abs(z) > 20 - (x - this.a) * 0.2) {
                    continue;
                }

                for (int y = 1; y <= 60; y++) {
                    String b = this.get(x, y, z);
                    if (b != null && !b.startsWith("spruce") && !b.startsWith("barrel") && !b.startsWith("chest") && !b.startsWith("lantern")) {
                        this.set(x, y, z, null);
                    }
                }

                this.set(x, 0, z, x > this.a + 12 ? (x > this.a + 15 ? "light_blue_stained_glass" : "cyan_stained_glass") : "sand");
                this.set(x, -1, z, x > this.a + 12 ? "prismarine" : "sand");
            }
        }

        this.ship(sx, 0);
        // a dock from the beach toward the ship
        for (int x = (int)Math.round(this.a + 6); x <= sx - 10; x++) {
            for (int z = 11; z <= 13; z++) {
                this.set(x, 1, z, "spruce_slab[type=top]");
            }

            if (x % 4 == 0) {
                this.set(x, 0, 11, log("spruce_log", 'y'));
                this.set(x, 0, 13, log("spruce_log", 'y'));
                this.set(x, 2, 11, "spruce_fence");
                this.set(x, 2, 13, "spruce_fence");
            }
        }

        // treasure in a cave mouth on the west side
        int tx = -(int)Math.round(this.a + 9);
        for (int x = tx - 4; x <= tx + 3; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 1; y <= 6; y++) {
                    if (Math.hypot((x - tx) * 0.9, z) < 4.6 && y <= 6 - Math.abs(z) / 2) {
                        this.set(x, y, z, null);
                    }
                }

                this.set(x, 0, z, "sand");
            }
        }

        for (int[] g : new int[][]{{0, 0}, {-1, 1}, {-1, -1}, {-2, 0}, {0, 2}, {0, -2}}) {
            this.set(tx + g[0], 1, g[1], "gold_block");
        }

        this.set(tx - 1, 2, 0, "gold_block");
        this.set(tx + 1, 1, 2, "chest[facing=east]");
        this.set(tx + 1, 1, -2, "chest[facing=east]");
        this.set(tx - 2, 1, 2, "barrel[facing=up]");
        this.set(tx + 2, 4, 0, "lantern[hanging=true]");
        this.set(tx - 3, 1, -2, "skeleton_skull[rotation=4]");
        this.grove(8.0, 0.35, 2);
    }

    /** A pirate ship, bow toward the floor: a curved hull, two masts with full sails, a crow's nest and a skull flag. */
    private void ship(int cx, int cz) {
        int len = 13;
        for (int dx = -len; dx <= len; dx++) {
            double t = (double)dx / len;
            double halfW = 5.6 * Math.sqrt(Math.max(0.0, 1.0 - Math.pow(Math.max(0.0, -t) * 1.0, 2) * 0.95)) * (t > 0.7 ? 1.0 - (t - 0.7) * 0.6 : 1.0);
            int keel = (int)Math.round(1 + Math.abs(t) * Math.abs(t) * 3);
            int deck = 7 + (t > 0.55 ? 2 : 0) + (t < -0.8 ? 1 : 0);
            for (int dz = -6; dz <= 6; dz++) {
                double ax = Math.abs(dz);
                if (ax > halfW + 0.3) {
                    continue;
                }

                for (int y = keel; y <= deck; y++) {
                    boolean shell = ax > halfW - 1.0 || y == keel;
                    if (shell) {
                        this.set(cx + dx, y, cz + dz, y == deck - 2 ? "stripped_spruce_wood[axis=y]" : (y == deck - 4 && dx % 3 == 0 && ax > halfW - 1 ? "black_concrete" : "dark_oak_planks"));
                    } else if (y < deck) {
                        this.set(cx + dx, y, cz + dz, null);
                    }
                }

                this.set(cx + dx, deck, cz + dz, "spruce_planks");
                if (ax > halfW - 1.0) {
                    this.set(cx + dx, deck + 1, cz + dz, "dark_oak_fence");
                }
            }
        }

        // bowsprit
        for (int k = 1; k <= 7; k++) {
            this.set(cx - len - k, 8 + k / 3, cz, log("spruce_log", 'x'));
        }

        // masts, sails, crow's nest, flag
        for (int[] m : new int[][]{{-4, 26}, {5, 22}}) {
            int mx = cx + m[0];
            for (int y = 8; y <= m[1]; y++) {
                this.set(mx, y, cz, log("spruce_log", 'y'));
            }

            for (int row = 0; row < 2; row++) {
                int y0 = 11 + row * 7;
                int half = row == 0 ? 6 : 5;
                for (int z = -half; z <= half; z++) {
                    this.set(mx, y0 + 6, cz + z, log("spruce_log", 'z'));
                    for (int y = y0; y <= y0 + 5; y++) {
                        double bulge = Math.sin((y - y0) / 5.0 * Math.PI) * (1.0 - Math.pow((double)z / (half + 1), 2)) * 1.6;
                        this.set(mx - 1 - (int)Math.round(bulge), y, cz + z, (row == 0 && y == y0 + 3 && Math.abs(z) <= 1) ? "black_wool" : "white_wool");
                    }
                }
            }

            if (m[0] < 0) {
                this.ringAt(mx, cz, 0, 1.6, m[1] - 2, of("spruce_slab[type=bottom]"));
                for (int z = 1; z <= 4; z++) {
                    for (int y = m[1] - 3; y <= m[1]; y++) {
                        boolean skull = (y == m[1] - 1 && (z == 2 || z == 3)) || (y == m[1] - 2 && z == 2);
                        this.set(mx, y, cz + z, skull ? "white_wool" : "black_wool");
                    }
                }
            }
        }

        // stern cabin
        int sx = cx + len - 3;
        for (int dx = 0; dx <= 3; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = 10; y <= 12; y++) {
                    if (dx == 0 || Math.abs(dz) == 4 || y == 12) {
                        this.set(sx + dx, y, cz + dz, y == 12 ? "dark_oak_slab[type=bottom]" : (y == 11 && dx == 0 && Math.abs(dz) == 2 ? "orange_stained_glass" : "dark_oak_planks"));
                    }
                }
            }
        }

        this.set(sx - 1, 11, cz, "lantern[hanging=false]");
    }

    // ================================================================== PHARAOH'S TOMB

    private void tomb() {
        Mat sand = this.mixOf(1001L, "cut_sandstone", "smooth_sandstone", 0.3, "sandstone", 0.1);
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
                if (d < 0.0 || d >= 10.0) {
                    continue;
                }

                String out = this.outward(x, z);
                double s = this.along(x, z);
                if (d < 1.0) {
                    this.set(x, 1, z, stairs("smooth_sandstone", out, false));
                } else if (d < 2.0) {
                    this.set(x, 1, z, "smooth_sandstone");
                    this.set(x, 2, z, stairs("smooth_sandstone", out, false));
                } else if (Math.abs(z) <= 12 && Math.abs(x) > this.hx) {
                    // the colonnade opens at both ends onto the pylon gate (east) and the sphinx (west)
                    for (int y = 1; y <= 2; y++) {
                        this.set(x, y, z, "smooth_sandstone");
                    }
                } else {
                    // the colonnade: a raised floor, painted columns, a hieroglyph wall behind, a roof
                    for (int y = 1; y <= 2; y++) {
                        this.set(x, y, z, "smooth_sandstone");
                    }

                    int col = Math.floorMod((int)Math.round(s), 5);
                    if (d >= 3.0 && d < 5.0 && col <= 1) {
                        for (int y = 3; y <= 12; y++) {
                            this.set(x, y, z, y == 11 ? "blue_terracotta" : (y == 12 ? "orange_terracotta" : (y % 4 == 0 ? "chiseled_sandstone" : "cut_sandstone")));
                        }
                    }

                    if (d >= 2.4 && d < 5.6) {
                        this.set(x, 13, z, d < 3.0 ? stairs("smooth_sandstone", opposite(out), true) : "smooth_sandstone");
                        this.set(x, 14, z, d < 3.0 ? "blue_terracotta" : "cut_sandstone");
                        this.set(x, 15, z, "smooth_sandstone_slab[type=bottom]");
                    }

                    if (d >= 8.0) {
                        for (int y = 3; y <= 15; y++) {
                            double g = hash3(this.seed + 7, (int)Math.round(s), y, 0);
                            String glyph = y >= 5 && y <= 12 && d < 9.0
                                ? (g < 0.18 ? "chiseled_sandstone" : (g < 0.26 ? "blue_terracotta" : (g < 0.3 ? "orange_terracotta" : (g < 0.33 ? "gold_block" : sand.at(x, y, z)))))
                                : sand.at(x, y, z);
                            this.set(x, y, z, glyph);
                        }
                    }

                    if (d >= 5.6 && d < 8.0) {
                        this.set(x, 15, z, "smooth_sandstone_slab[type=bottom]");
                        this.set(x, 14, z, "cut_sandstone");
                    }
                }
            }
        }

        this.pylonAndPyramid((int)Math.round(this.hx + 4.0), 0);
        this.sphinx(-(int)Math.round(this.hx + 16.0), 0);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                this.obelisk(sx * (this.hx + 13), 1, sz * (this.hz + 14), 22);
            }
        }

        this.hills(12.0, 5, "sand", "sandstone");
        this.grove(12.0, 0.45, 2);
    }

    /** A pylon gate (two sloped towers with banners) in front of a great stepped pyramid with a golden top. */
    private void pylonAndPyramid(int gx, int cz) {
        Mat sand = this.mixOf(1011L, "cut_sandstone", "smooth_sandstone", 0.35, "sandstone", 0.08);
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y <= 22; y++) {
                int inset = y / 6;
                for (int x = gx; x <= gx + 7 - inset; x++) {
                    for (int z = side * 4; Math.abs(z) <= 13 - inset; z += side) {
                        this.set(x, y, z, sand.at(x, y, z));
                    }
                }
            }

            for (int y = 20; y <= 22; y++) {
                for (int z = side * 4; Math.abs(z) <= 10; z += side) {
                    this.set(gx - 1, y, z, y == 21 ? "blue_terracotta" : "orange_terracotta");
                }
            }

            Face f = new Face(gx, side * 8, 1, 0);
            this.cloth(f, 0, 18, 3, 12, "blue_wool", "yellow_wool", "yellow_wool");
        }

        for (int z = -3; z <= 3; z++) {
            for (int y = 13; y <= 16; y++) {
                this.set(gx, y, z, y == 16 ? "gold_block" : sand.at(gx, y, z));
                this.set(gx + 1, y, z, sand.at(gx, y, z));
            }
        }

        // the great pyramid behind: steep (two blocks up for every block in), golden on top
        int px = gx + 28;
        int half = 24;
        for (int k = 0; k < half; k++) {
            for (int dy = 0; dy < 2; dy++) {
                int y = 1 + k * 2 + dy;
                String edge = dy == 1 ? "smooth_sandstone_slab[type=bottom]" : null;
                for (int x = px - half + k; x <= px + half - k; x++) {
                    for (int z = cz - half + k; z <= cz + half - k; z++) {
                        boolean rim = x == px - half + k || x == px + half - k || z == cz - half + k || z == cz + half - k;
                        String b = k >= half - 3 ? "gold_block" : (hash3(this.seed, x, y, z) < 0.1 ? "sandstone" : "smooth_sandstone");
                        this.set(x, y, z, rim && edge != null && k < half - 3 ? edge : b);
                    }
                }
            }
        }
    }

    /** A sphinx lying on a plinth, looking at the floor: lion body and paws, a striped headdress, a face. */
    private void sphinx(int cx, int cz) {
        Mat st = this.mixOf(1021L, "sandstone", "smooth_sandstone", 0.35, "cut_sandstone", 0.15);
        this.fill(cx - 13, 1, cz - 7, cx + 8, 2, cz + 7, of("cut_sandstone"));
        this.ellipsoid(cx - 3, 6, cz, 10, 4.5, 5, st);
        for (int side = -1; side <= 1; side += 2) {
            this.fill(cx + 4, 3, cz + side * 2 - 1, cx + 12, 4, cz + side * 2 + (side > 0 ? 1 : 1) - 0, st);
        }

        // head with nemes stripes
        int hx2 = cx + 6;
        this.ellipsoid(hx2, 12, cz, 3.4, 4.2, 3.6, st);
        for (int y = 9; y <= 16; y++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dx = -3; dx <= 1; dx++) {
                    double e = Math.pow((dx + 1) / 3.0, 2) + Math.pow(dz / 5.2, 2) + Math.pow((y - 12.5) / 4.6, 2);
                    if (e <= 1.0 && (Math.abs(dz) >= 3 || y >= 15) && this.empty(hx2 + dx, y, cz + dz) || e <= 1.0 && (Math.abs(dz) >= 3 || y >= 15)) {
                        this.set(hx2 + dx, y, cz + dz, (y % 2 == 0) ? "blue_terracotta" : "yellow_terracotta");
                    }
                }
            }
        }

        this.set(hx2 + 4, 13, cz - 1, "black_concrete");
        this.set(hx2 + 4, 13, cz + 1, "black_concrete");
        this.set(hx2 + 4, 11, cz, "smooth_sandstone");
        this.set(hx2 + 5, 11, cz, "smooth_sandstone");
        this.set(hx2 + 4, 10, cz, "orange_terracotta");
        for (int y = 7; y <= 9; y++) {
            this.set(hx2 + 3, y, cz, "blue_terracotta");
        }
    }
}
