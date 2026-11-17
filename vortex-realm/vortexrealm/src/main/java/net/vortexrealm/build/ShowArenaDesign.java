package net.vortexrealm.build;

/**
 * Show arenas: an oval sand (or packed earth) floor with a wall, stands and a ring of decorated buildings around it,
 * like the big practice-server arenas. Three looks:
 * <ul>
 * <li>Desert Palace - red, cream and gold walls, blue pillars, orange onion domes and a palace with golden gates;</li>
 * <li>Olympus - tiered sandstone seating, a ring of tall arches with red banners and a giant warrior statue;</li>
 * <li>Timber Fort - packed-earth floor, stone walls, timber-frame houses with red roofs, a striped tent gatehouse and
 * a rock outcrop with pines.</li>
 * </ul>
 * Everything stands on solid ground down to a bedrock floor under every column.
 */
public final class ShowArenaDesign extends MegaVoxels implements ArenaVoxels {
    public final ShowArenaDesign.Style style;
    /** Half-axes of the oval fighting floor. */
    public final double a;
    public final double b;
    private final int wallH;
    private final int tiers;
    /** Signed distance where the stands end and the buildings start. */
    private final int s0;
    /** Height of the ground behind the stands. */
    private final int g0;
    private final boolean ffa;
    private final boolean barriers;
    private final int depth;
    private final long seed;

    public ShowArenaDesign(ShowArenaDesign.Style style, int size, long seed, int cx, int floorY, int cz, int depth, boolean ffa, boolean barriers) {
        super(cx, floorY, cz, Math.max(60, (size - 1) / 2), -depth, 72, seed);
        this.style = style;
        this.seed = seed;
        this.depth = depth;
        this.ffa = ffa;
        this.barriers = barriers;
        this.a = Math.max(26.0, Math.min(64.0, this.R * 0.56));
        this.b = this.a * 0.76;
        this.wallH = switch (style) {
            case PALACE -> 5;
            case OLYMPUS -> 4;
            case TIMBER_FORT, MOLE_MINES -> 6;
        };
        this.tiers = switch (style) {
            case PALACE -> 5;
            case OLYMPUS -> 8;
            case TIMBER_FORT, MOLE_MINES -> 2;
        };
        this.s0 = 2 + this.tiers;
        this.g0 = this.wallH + this.tiers;
        this.base();
        this.wallAndStands();
        switch (style) {
            case PALACE -> this.palace();
            case OLYMPUS -> this.olympus();
            case TIMBER_FORT, MOLE_MINES -> this.fort();
        }

        if (barriers) {
            this.barrierRing();
        }

        this.finish();
    }

    @Override
    public String name() {
        return this.style.display;
    }

    // ------------------------------------------------------------------ geometry

    /** Signed distance (blocks) from the edge of the oval floor; negative inside. */
    public double sd(double x, double z) {
        double e = Math.sqrt(x * x / (this.a * this.a) + z * z / (this.b * this.b));
        if (e < 1.0E-6) {
            return -Math.min(this.a, this.b);
        } else {
            double g = Math.sqrt(x * x / (this.a * this.a * this.a * this.a) + z * z / (this.b * this.b * this.b * this.b)) / e;
            return (e - 1.0) / g;
        }
    }

    /** A point on the oval {@code k} blocks outside the floor edge, at parameter angle {@code t} (radians). */
    private int[] onRing(double k, double t) {
        return new int[]{(int)Math.round(Math.cos(t) * (this.a + k)), (int)Math.round(Math.sin(t) * (this.b + k))};
    }

    /** How many evenly spaced spots fit around the ring {@code k} blocks out, about {@code spacing} apart. */
    private int count(double k, double spacing) {
        double A = this.a + k;
        double B = this.b + k;
        double perimeter = Math.PI * (3.0 * (A + B) - Math.sqrt((3.0 * A + B) * (A + 3.0 * B)));
        return Math.max(6, (int)Math.round(perimeter / spacing));
    }

    /** Angle in degrees of a point (0 = +x, 90 = +z) for keeping sectors free. */
    private static double deg(double x, double z) {
        double d = Math.toDegrees(Math.atan2(z, x));
        return d < 0.0 ? d + 360.0 : d;
    }

    /** True for the north sector (toward -z) where the big feature building stands. */
    private boolean north(double x, double z, double halfWidth) {
        return z < 0.0 && Math.abs(x) <= halfWidth;
    }

    public int spawnOffset() {
        return (int)Math.round(this.a * 0.6);
    }

    /** FFA spawn ring: {x, y, z} offsets on the floor. */
    @Override
    public int[] ffaSpot(int i, int n) {
        double t = Math.PI * 2.0 * i / n;
        return new int[]{(int)Math.round(Math.cos(t) * this.a * 0.62), 1, (int)Math.round(Math.sin(t) * this.b * 0.62)};
    }

    @Override
    public int[] spawn(int side) {
        int sp = this.spawnOffset();
        return new int[]{side == 0 ? -sp : sp, 1, 0};
    }

    // ------------------------------------------------------------------ ground

    private String floorBlock(int x, int z) {
        double n = noise(this.seed + 11L, x * 1.9, z * 1.9);
        double m = noise(this.seed + 23L, x * 0.7, z * 0.7);
        return switch (this.style) {
            case PALACE -> n > 0.78 ? "smooth_sandstone" : (n < 0.16 ? "sandstone" : "sand");
            case OLYMPUS -> n > 0.8 ? "sandstone" : "sand";
            case TIMBER_FORT, MOLE_MINES -> n > 0.7 ? "coarse_dirt" : (m < 0.22 ? "mud_bricks" : (n < 0.18 ? "dirt" : "packed_mud"));
        };
    }

    private String groundBlock(int x, int z) {
        double n = noise(this.seed + 31L, x * 1.3, z * 1.3);
        return switch (this.style) {
            case PALACE -> n > 0.82 ? "sandstone" : "sand";
            case OLYMPUS -> n > 0.86 ? "coarse_dirt" : "grass_block";
            case TIMBER_FORT, MOLE_MINES -> n > 0.8 ? "podzol" : (n < 0.12 ? "coarse_dirt" : "grass_block");
        };
    }

    private String subBlock() {
        return switch (this.style) {
            case PALACE, OLYMPUS -> "sandstone";
            case TIMBER_FORT, MOLE_MINES -> "dirt";
        };
    }

    /** Solid ground under every column of the footprint, bedrock at the bottom. */
    private void base() {
        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = this.sd(x, z);
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

                this.set(x, 0, z, d < 0.0 ? this.floorBlock(x, z) : this.groundBlock(x, z));
            }
        }
    }

    // ------------------------------------------------------------------ wall and stands

    private String wallBlock(int x, int y, int z) {
        double n = noise(this.seed + 51L, x * 3.1 + y, z * 3.1 - y);
        return switch (this.style) {
            case PALACE -> y <= this.wallH - 2 ? "red_terracotta" : (y == this.wallH - 1 ? "yellow_terracotta" : "cut_sandstone");
            case OLYMPUS -> y == this.wallH ? "stone_bricks" : (n > 0.8 ? "andesite" : "polished_andesite");
            case TIMBER_FORT, MOLE_MINES -> n > 0.82 ? "mossy_stone_bricks" : (n < 0.14 ? "cracked_stone_bricks" : "stone_bricks");
        };
    }

    private String standMaterial(int t) {
        return switch (this.style) {
            case PALACE -> "smooth_sandstone";
            case OLYMPUS -> t % 2 == 0 ? "sandstone" : "smooth_sandstone";
            case TIMBER_FORT, MOLE_MINES -> "stone_brick";
        };
    }

    private String standSupport() {
        return switch (this.style) {
            case PALACE, OLYMPUS -> "sandstone";
            case TIMBER_FORT, MOLE_MINES -> "stone_bricks";
        };
    }

    private void wallAndStands() {
        int reach = (int)Math.ceil(this.a) + this.s0 + 2;

        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double d = this.sd(x, z);
                if (!(d < 0.0) && !(d >= this.s0)) {
                    if (d < 2.0) {
                        for (int y = 1; y <= this.wallH; y++) {
                            this.set(x, y, z, this.wallBlock(x, y, z));
                        }

                        if (this.style == ShowArenaDesign.Style.OLYMPUS && Math.floorMod((int)Math.round(deg(x, z) * 2.0), 18) == 0) {
                            this.set(x, this.wallH - 1, z, "chiseled_stone_bricks");
                        }
                    } else {
                        int t = (int)(d - 2.0);
                        int top = this.wallH + 1 + t;

                        for (int y = 1; y < top; y++) {
                            this.set(x, y, z, this.standSupport());
                        }

                        String mat = this.standMaterial(t);
                        boolean aisle = this.style == ShowArenaDesign.Style.OLYMPUS && Math.floorMod((int)Math.round(deg(x, z)), 30) < 2;
                        String f = facing(x / (this.a * this.a), z / (this.b * this.b));
                        this.set(x, top, z, aisle ? stairs("smooth_quartz", f, false) : stairs(mat, f, false));
                    }
                }
            }
        }

        // the ground behind the stands is raised to the top of the stands
        int far = (int)Math.ceil(this.a) + this.s0 + 40;

        for (int x = -far; x <= far; x++) {
            for (int z = -far; z <= far; z++) {
                double d = this.sd(x, z);
                if (d >= this.s0 && d < this.s0 + 6) {
                    for (int y = 1; y <= this.g0; y++) {
                        this.set(x, y, z, y == this.g0 ? this.terrace() : this.standSupport());
                    }
                } else if (d >= this.s0 + 6 && d < this.s0 + 6 + this.g0) {
                    // a slope down to the ground outside
                    int h = (int)Math.round(this.g0 - (d - this.s0 - 6));

                    for (int y = 1; y <= h; y++) {
                        this.set(x, y, z, y == h ? this.groundBlock(x, z) : (y >= h - 2 ? this.subBlock() : "stone"));
                    }
                }
            }
        }
    }

    private String terrace() {
        return switch (this.style) {
            case PALACE -> "smooth_sandstone";
            case OLYMPUS -> "smooth_sandstone";
            case TIMBER_FORT, MOLE_MINES -> "spruce_planks";
        };
    }

    /** Invisible wall above the arena wall so nobody climbs out onto the stands. */
    private void barrierRing() {
        int reach = (int)Math.ceil(this.a) + 3;

        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double d = this.sd(x, z);
                if (d >= 0.0 && d < 1.2) {
                    for (int y = this.wallH + 1; y <= this.wallH + 26; y++) {
                        if (this.empty(x, y, z)) {
                            this.set(x, y, z, "barrier");
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ shared pieces

    /** An onion dome standing on (x, y, z): bulges past its base, then narrows to a golden point. */
    private void onionDome(int x, int y, int z, double r, String mat) {
        int h = (int)Math.round(r * 2.4);

        for (int dy = 0; dy <= h; dy++) {
            double t = (double)dy / h;
            double rr = t < 0.35 ? r * (0.85 + 0.15 * Math.sin(t / 0.35 * Math.PI / 2.0)) : r * Math.pow(Math.cos((t - 0.35) / 0.65 * Math.PI / 2.0), 1.6);
            this.disc(x, z, y + dy, Math.max(0.4, rr), mat);
        }

        this.ring(x, z, y, r * 0.8, r * 0.9, "yellow_terracotta");
        this.set(x, y + h + 1, z, "gold_block");
        this.set(x, y + h + 2, z, "end_rod[facing=up]");
    }

    private void palm(int x, int y, int z) {
        int h = 6 + this.rnd.nextInt(4);
        double lean = this.rnd.nextDouble() * Math.PI * 2.0;
        int tx = x;
        int tz = z;

        for (int i = 0; i < h; i++) {
            if (i > 2 && i % 3 == 0) {
                tx += (int)Math.round(Math.cos(lean));
                tz += (int)Math.round(Math.sin(lean));
            }

            this.set(tx, y + i, tz, log("jungle_log", 'y'));
        }

        int top = y + h;
        this.set(tx, top, tz, leaves("jungle"));

        for (int dir = 0; dir < 8; dir++) {
            double ang = dir * Math.PI / 4.0;

            for (int k = 1; k <= 4; k++) {
                int lx = tx + (int)Math.round(Math.cos(ang) * k);
                int lz = tz + (int)Math.round(Math.sin(ang) * k);
                this.setIfEmpty(lx, top - (k >= 3 ? 1 : 0) - (k == 4 ? 1 : 0), lz, leaves("jungle"));
            }
        }
    }

    /** Trees on the open ground outside the buildings. */
    private void forest(double from, double chance, int kind) {
        for (int x = -this.R + 3; x <= this.R - 3; x += 2) {
            for (int z = -this.R + 3; z <= this.R - 3; z += 2) {
                double d = this.sd(x, z);
                if (d > from && this.rnd.nextDouble() < chance && this.empty(x, 1, z) && !this.empty(x, 0, z)) {
                    switch (kind) {
                        case 0 -> this.palm(x, 1, z);
                        case 1 -> {
                            if (this.rnd.nextBoolean()) {
                                this.tree(x, 1, z, "oak_log", "oak", 4 + this.rnd.nextInt(3), 2.8 + this.rnd.nextDouble());
                            } else {
                                this.conifer(x, 1, z, "spruce_log", "spruce", 7 + this.rnd.nextInt(5));
                            }
                        }
                        default -> this.conifer(x, 1, z, "spruce_log", "spruce", 8 + this.rnd.nextInt(7));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Desert Palace

    private void palace() {
        int g = this.g0;
        // colonnade: blue pillars on the inner edge, a roof, and a red and cream outer wall with towers
        int reach = (int)Math.ceil(this.a) + this.s0 + 8;

        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double d = this.sd(x, z);
                if (d >= this.s0 && d < this.s0 + 6) {
                    double rel = d - this.s0;
                    double arc = deg(x, z) * (this.a + this.s0) * Math.PI / 180.0;
                    int panel = Math.floorMod((int)Math.floor(arc), 7);
                    if (rel >= 5.0) {
                        // outer wall, seen from the arena behind the pillars
                        for (int y = 1; y <= g + 13; y++) {
                            String s;
                            if (y <= g) {
                                s = "smooth_sandstone";
                            } else if (y <= g + 2) {
                                s = "red_terracotta";
                            } else if (y == g + 3 || y == g + 10) {
                                s = "yellow_terracotta";
                            } else if (y < g + 10) {
                                s = panel >= 2 && panel <= 4 ? (y >= g + 5 && y <= g + 7 && panel == 3 ? "light_blue_stained_glass_pane" : "red_terracotta") : "cut_sandstone";
                            } else {
                                s = "smooth_sandstone";
                            }

                            this.set(x, y, z, s);
                        }

                        if (panel % 2 == 0) {
                            this.set(x, g + 14, z, "smooth_sandstone");
                        }
                    } else if (rel >= 4.0) {
                        this.set(x, g + 9, z, "smooth_sandstone");
                    } else {
                        this.set(x, g + 9, z, rel < 1.0 ? "cut_sandstone" : "smooth_sandstone_slab[type=top]");
                        if (rel < 1.0) {
                            this.set(x, g + 10, z, "sandstone_wall");
                            this.set(x, g + 8, z, "cut_sandstone");
                        }
                    }
                }
            }
        }

        int n = this.count(this.s0 + 0.5, 4.5);

        for (int i = 0; i < n; i++) {
            int[] p = this.onRing(this.s0 + 0.5, Math.PI * 2.0 * i / n);
            if (!this.north(p[0], p[1], 26.0)) {
                for (int y = g + 1; y <= g + 7; y++) {
                    this.set(p[0], y, p[1], y == g + 7 ? "chiseled_sandstone" : "light_blue_terracotta");
                }
            }
        }

        // towers with onion domes
        int towers = Math.max(8, this.count(this.s0 + 3.0, 34.0));

        for (int i = 0; i < towers; i++) {
            double t = Math.PI * 2.0 * (i + 0.5) / towers;
            int[] p = this.onRing(this.s0 + 3.0, t);
            if (!this.north(p[0], p[1], 34.0)) {
                this.tower(p[0], p[1], g + 18, 3, 3.6);
            }
        }

        this.palaceHall(g);
        this.gate(g, 1);
        // palms and a few dunes outside
        this.forest(this.s0 + 8 + this.g0, 0.025, 0);
    }

    private void tower(int x, int z, int top, int h, double dome) {
        for (int y = 1; y <= top; y++) {
            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
                    boolean corner = Math.abs(dx) == h && Math.abs(dz) == h;
                    String s = corner ? "chiseled_sandstone" : (y % 9 == 0 ? "yellow_terracotta" : (y % 9 == 1 || y % 9 == 8 ? "red_terracotta" : "cut_sandstone"));
                    if (edge && !corner && (y % 9 >= 3 && y % 9 <= 5) && (dx == 0 || dz == 0) && y > this.g0) {
                        s = "light_blue_stained_glass_pane";
                    }

                    if (edge || y == top || y <= this.g0) {
                        this.set(x + dx, y, z + dz, s);
                    }
                }
            }
        }

        // a small balcony ring and the dome
        for (int dx = -h - 1; dx <= h + 1; dx++) {
            for (int dz = -h - 1; dz <= h + 1; dz++) {
                if (Math.abs(dx) == h + 1 || Math.abs(dz) == h + 1) {
                    this.set(x + dx, top, z + dz, "smooth_sandstone_slab[type=top]");
                    this.set(x + dx, top + 1, z + dz, "sandstone_wall");
                }
            }
        }

        this.onionDome(x, z, top + 1, dome);
    }

    private void onionDome(int x, int z, int y, double r) {
        this.onionDome(x, y, z, r, "orange_terracotta");
    }

    /** The palace on the north side: golden gates, blue pillars, a balcony, minarets and a big dome. */
    private void palaceHall(int g) {
        int front = (int)Math.round(-(this.b + this.s0 + 1.0));
        int back = front - 18;
        int hw = 22;
        // clear what the colonnade put there
        this.box(-hw - 6, g + 1, back - 4, hw + 6, 59, front + 6, null);

        for (int x = -hw; x <= hw; x++) {
            for (int z = back; z <= front; z++) {
                for (int y = 1; y <= g; y++) {
                    this.set(x, y, z, "smooth_sandstone");
                }
            }
        }

        int top = g + 18;

        for (int y = g + 1; y <= top; y++) {
            String band = y <= g + 3 ? "red_terracotta" : (y == g + 4 || y == g + 15 ? "yellow_terracotta" : "cut_sandstone");
            this.walls(-hw, y, back, hw, y, front, band);
        }

        this.box(-hw, top + 1, back, hw, top + 1, front, "smooth_sandstone");

        for (int x = -hw; x <= hw; x += 2) {
            this.set(x, top + 2, front, "smooth_sandstone");
            this.set(x, top + 2, back, "smooth_sandstone");
        }

        // windows
        for (int x = -hw + 3; x <= hw - 3; x += 5) {
            if (Math.abs(x) > 5) {
                this.box(x, g + 7, front, x + 1, g + 10, front, "light_blue_stained_glass_pane");
                this.box(x, g + 7, back, x + 1, g + 10, back, "light_blue_stained_glass_pane");
            }
        }

        // the great golden gate with an arch
        for (int x = -4; x <= 4; x++) {
            int archTop = g + 9 + (int)Math.round(Math.sqrt(Math.max(0, 16 - x * x)) * 0.8);

            for (int y = g + 1; y <= archTop; y++) {
                this.set(x, y, front, null);
                this.set(x, y, front - 1, Math.abs(x) <= 3 ? "gold_block" : "cut_sandstone");
            }

            this.set(x, archTop + 1, front, "chiseled_sandstone");
        }

        for (int side = -1; side <= 1; side += 2) {
            int dx = side * 12;

            for (int x = dx - 2; x <= dx + 2; x++) {
                for (int y = g + 1; y <= g + 6; y++) {
                    this.set(x, y, front, null);
                    this.set(x, y, front - 1, Math.abs(x - dx) <= 1 ? "gold_block" : "cut_sandstone");
                }

                this.set(x, g + 7, front, "chiseled_sandstone");
            }
        }

        // blue pillars in front
        for (int px : new int[]{-18, -8, 8, 18}) {
            for (int y = g + 1; y <= g + 12; y++) {
                this.set(px, y, front + 2, y == g + 12 ? "chiseled_sandstone" : "light_blue_terracotta");
            }
        }

        // balcony over the gate
        this.box(-9, g + 13, front + 1, 9, g + 13, front + 3, "smooth_sandstone_slab[type=top]");

        for (int x = -9; x <= 9; x++) {
            this.set(x, g + 14, front + 3, "sandstone_wall");
        }

        this.box(-2, g + 14, front, 2, g + 16, front, "light_blue_stained_glass_pane");
        // upper storey and the big dome
        int up = top + 1;

        for (int y = up + 1; y <= up + 7; y++) {
            this.walls(-12, y, back + 3, 12, y, front - 3, y == up + 7 ? "yellow_terracotta" : "cut_sandstone");
        }

        for (int x = -10; x <= 10; x += 4) {
            this.box(x, up + 3, front - 3, x + 1, up + 5, front - 3, "light_blue_stained_glass_pane");
        }

        this.box(-12, up + 8, back + 3, 12, up + 8, front - 3, "smooth_sandstone");
        this.onionDome(0, up + 9, (back + front) / 2, 7.5, "orange_terracotta");

        for (int side = -1; side <= 1; side += 2) {
            int mx = side * (hw + 3);
            int mz = front - 4;
            this.cylinder(mx, mz, 1, g + 30, 2.6, "cut_sandstone", false);

            for (int y = g + 4; y <= g + 30; y += 6) {
                this.ring(mx, mz, y, 1.6, 2.7, "yellow_terracotta");
            }

            this.disc(mx, mz, g + 24, 3.8, "smooth_sandstone_slab[type=top]");
            this.ring(mx, mz, g + 25, 3.2, 3.9, "sandstone_wall");
            this.onionDome(mx, g + 31, mz, 3.0, "orange_terracotta");
        }

        // corner domes on the roof
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int z : new int[]{front - 2, back + 2}) {
                this.onionDome(sx * (hw - 2), top + 2, z, 2.6, "orange_terracotta");
            }
        }
    }

    /** A decorative gate in the wall on the south side (not a way out: the doors are blocks). */
    private void gate(int g, int dir) {
        int z = (int)Math.round(this.b + 1.0) * dir;

        for (int x = -4; x <= 4; x++) {
            for (int y = 1; y <= this.wallH + 4; y++) {
                boolean door = Math.abs(x) <= 2 && y <= this.wallH + 1;
                this.set(x, y, z, door ? "gold_block" : (y > this.wallH + 2 ? "chiseled_sandstone" : "cut_sandstone"));
            }
        }

        for (int x = -6; x <= 6; x += 12) {
            for (int y = 1; y <= this.wallH + 8; y++) {
                this.set(x, y, z, "light_blue_terracotta");
            }

            this.onionDome(x, this.wallH + 9, z, 1.8, "orange_terracotta");
        }
    }

    // ------------------------------------------------------------------ Olympus

    private void olympus() {
        int g = this.g0;
        double k = this.s0 + 1.5;
        int n = this.count(k, 10.0);
        int[][] cols = new int[n][];

        for (int i = 0; i < n; i++) {
            cols[i] = this.onRing(k, Math.PI * 2.0 * i / n);
        }

        // entablature: a continuous band on top of the columns
        int reach = (int)Math.ceil(this.a) + this.s0 + 5;

        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double d = this.sd(x, z);
                if (d >= this.s0 && d < this.s0 + 3.2 && !this.north(x, z, 12.0)) {
                    this.set(x, g + 17, z, "smooth_quartz");
                    this.set(x, g + 18, z, d < this.s0 + 0.8 || d >= this.s0 + 2.4 ? "quartz_slab[type=bottom]" : "smooth_quartz");
                }
            }
        }

        for (int i = 0; i < n; i++) {
            int[] p = cols[i];
            int[] q = cols[(i + 1) % n];
            if (!this.north(p[0], p[1], 12.0)) {
                // column 2x2 with base and capital
                this.box(p[0] - 1, g + 1, p[1] - 1, p[0] + 1, g + 1, p[1] + 1, "smooth_quartz");

                for (int y = g + 2; y <= g + 15; y++) {
                    this.box(p[0], y, p[1], p[0] + 1, y, p[1] + 1, "quartz_pillar[axis=y]");
                }

                this.box(p[0] - 1, g + 16, p[1] - 1, p[0] + 2, g + 16, p[1] + 2, "chiseled_quartz_block");
            }

            if (!this.north(p[0], p[1], 12.0) && !this.north(q[0], q[1], 12.0)) {
                // a round arch between this column and the next
                for (double t = 0.0; t <= 1.0; t += 0.02) {
                    int ax = (int)Math.round(p[0] + (q[0] - p[0]) * t + 0.5);
                    int az = (int)Math.round(p[1] + (q[1] - p[1]) * t + 0.5);
                    int ay = g + 11 + (int)Math.round(Math.sin(Math.PI * t) * 5.0);

                    for (int y = ay; y <= g + 16; y++) {
                        this.set(ax, y, az, "smooth_sandstone");
                    }
                }

                // red banner on every other arch, hanging on the arena side
                if (i % 2 == 0) {
                    this.banner((p[0] + q[0]) / 2.0, (p[1] + q[1]) / 2.0, g + 15);
                }
            }
        }

        // stone walkway behind the columns
        this.statue(g);
        this.forest(this.s0 + 6 + this.g0, 0.03, 1);
    }

    /** A red banner (3 wide, 7 tall) with a yellow hem and a white emblem, hanging toward the arena. */
    private void banner(double mx, double mz, int topY) {
        double len = Math.sqrt(mx * mx / (this.a * this.a) + mz * mz / (this.b * this.b));
        double nx = mx / len;
        double nz = mz / len;
        // one block in front of the arch, toward the centre
        double fx = mx - nx / Math.max(1.0, Math.abs(nx) + Math.abs(nz)) * 1.2;
        double fz = mz - nz / Math.max(1.0, Math.abs(nx) + Math.abs(nz)) * 1.2;
        double tx = -nz;
        double tz = nx;
        double tl = Math.sqrt(tx * tx + tz * tz);
        tx /= tl;
        tz /= tl;

        for (int w = -1; w <= 1; w++) {
            for (int h = 0; h < 7; h++) {
                int x = (int)Math.round(fx + tx * w);
                int z = (int)Math.round(fz + tz * w);
                boolean emblem = (h == 1 || h == 4) && w == 0 || (h == 2 || h == 3) && w != 0;
                String s = h == 6 ? "yellow_wool" : (emblem ? "white_wool" : "red_wool");
                this.set(x, topY - h, z, s);
            }
        }
    }

    /** The giant warrior statue behind the north stands, facing the arena: spear raised, round shield. */
    private void statue(int g) {
        int sz = (int)Math.round(-(this.b + this.s0 + 9.0));
        int y0 = g;
        // plinth
        this.box(-8, 1, sz - 7, 8, y0 + 3, sz + 7, "stone_bricks");
        this.box(-9, y0 + 3, sz - 8, 9, y0 + 3, sz + 8, "chiseled_stone_bricks");
        this.box(-7, y0 + 4, sz - 6, 7, y0 + 4, sz + 6, "polished_andesite");
        int f = y0 + 5;
        // legs
        this.box(-4, f, sz - 2, -2, f + 10, sz + 1, "andesite");
        this.box(2, f, sz - 2, 4, f + 10, sz + 1, "andesite");
        this.box(-4, f, sz - 2, -2, f + 2, sz + 2, "polished_andesite");
        this.box(2, f, sz - 2, 4, f + 2, sz + 2, "polished_andesite");
        // kilt and torso
        this.box(-5, f + 9, sz - 3, 5, f + 12, sz + 2, "stone");
        this.box(-5, f + 13, sz - 3, 5, f + 13, sz + 2, "polished_andesite");
        this.box(-5, f + 14, sz - 3, 5, f + 22, sz + 2, "stone");
        this.box(-4, f + 15, sz + 2, 4, f + 21, sz + 2, "polished_andesite");
        // shoulders and arms
        this.box(-8, f + 20, sz - 2, -6, f + 22, sz + 1, "polished_andesite");
        this.box(6, f + 20, sz - 2, 8, f + 22, sz + 1, "polished_andesite");
        // right arm raised with the spear
        this.box(-8, f + 22, sz - 1, -6, f + 30, sz + 1, "andesite");

        for (int y = f + 6; y <= f + 44; y++) {
            this.set(-7, y, sz + 2, "stone_brick_wall");
        }

        this.cone(-7, sz + 2, f + 45, f + 49, 1.6, "iron_block");
        // left arm down holding the round shield
        this.box(6, f + 12, sz - 1, 8, f + 19, sz + 1, "andesite");

        for (int dx = -7; dx <= 7; dx++) {
            for (int dy = -7; dy <= 7; dy++) {
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r <= 7.0) {
                    String s = r > 5.8 ? "red_terracotta" : (r < 1.8 ? "red_nether_bricks" : (r > 3.6 && r < 4.6 ? "red_terracotta" : "stone"));
                    this.set(13 + dx, f + 15 + dy, sz + 3, s);
                    this.set(13 + dx, f + 15 + dy, sz + 2, "stone");
                }
            }
        }

        // head with a crested helmet
        this.box(-2, f + 23, sz - 2, 2, f + 27, sz + 2, "andesite");
        this.box(-3, f + 25, sz - 3, 3, f + 28, sz + 2, "polished_andesite");
        this.box(-1, f + 24, sz + 2, 1, f + 25, sz + 3, "polished_andesite");

        for (int z = sz - 5; z <= sz + 3; z++) {
            int h = 2 - Math.abs(z - (sz - 1)) / 3;
            this.box(0, f + 29, z, 0, f + 29 + Math.max(0, h), z, "red_terracotta");
        }
    }

    // ------------------------------------------------------------------ Timber Fort

    private void fort() {
        int g = this.g0;
        // walkway and railing on the wall, posts with lanterns
        int reach = (int)Math.ceil(this.a) + 4;

        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double d = this.sd(x, z);
                if (d >= 0.0 && d < 2.0) {
                    this.set(x, this.wallH, z, "spruce_planks");
                    if (d < 1.0) {
                        this.set(x, this.wallH + 1, z, "spruce_fence");
                    }
                }
            }
        }

        int posts = this.count(1.0, 7.0);

        for (int i = 0; i < posts; i++) {
            int[] p = this.onRing(0.5, Math.PI * 2.0 * i / posts);

            for (int y = 1; y <= this.wallH + 1; y++) {
                this.set(p[0], y, p[1], log("spruce_log", 'y'));
            }

            if (i % 2 == 0) {
                this.set(p[0], this.wallH + 2, p[1], "lantern");
            }
        }

        // a covered timber gallery behind the stands, with posts, railing and flower boxes
        int far = (int)Math.ceil(this.a) + this.s0 + 4;

        for (int x = -far; x <= far; x++) {
            for (int z = -far; z <= far; z++) {
                double d = this.sd(x, z);
                if (d >= this.s0 && d < this.s0 + 3.0 && !this.north(x, z, 24.0)) {
                    double arc = deg(x, z) * (this.a + this.s0) * Math.PI / 180.0;
                    boolean post = Math.floorMod((int)Math.floor(arc), 5) == 0;
                    double rel = d - this.s0;
                    if (rel < 1.0) {
                        if (post) {
                            for (int y = g + 1; y <= g + 4; y++) {
                                this.set(x, y, z, "stripped_dark_oak_log[axis=y]");
                            }
                        } else {
                            this.set(x, g + 1, z, "spruce_fence");
                            if (Math.floorMod((int)Math.floor(arc), 5) == 2) {
                                this.set(x, g + 4, z, "spruce_leaves[persistent=true]");
                            }
                        }
                    }

                    this.set(x, g + 5, z, rel < 1.0 ? "dark_oak_planks" : "spruce_slab[type=bottom]");
                }
            }
        }

        // houses all around, a gatehouse with a striped tent in the north and a rock outcrop in the south-east
        double k = this.s0 + 8.0;
        int n = this.count(k, 15.0);

        for (int i = 0; i < n; i++) {
            double t = Math.PI * 2.0 * (i + 0.5) / n;
            int[] p = this.onRing(k, t);
            double dg = deg(p[0], p[1]);
            if (!this.north(p[0], p[1], 30.0) && !(dg > 20.0 && dg < 70.0)) {
                int w = 5 + (i % 3 == 0 ? 1 : 0);
                this.house(p[0], p[1], w, g, 9 + (i % 2) * 3);
            }
        }

        this.gatehouse(g);
        this.rock(g);
        this.forest(this.s0 + 8 + this.g0, 0.045, 2);
    }

    /** A timber-frame house with a red roof and a balcony facing the arena. */
    private void house(int x, int z, int h, int g, int height) {
        int top = g + height;
        String frame = "stripped_dark_oak_log[axis=y]";

        for (int y = 1; y <= g; y++) {
            this.box(x - h, y, z - h, x + h, y, z + h, "stone_bricks");
        }

        for (int y = g + 1; y <= top; y++) {
            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
                    if (edge) {
                        boolean corner = Math.abs(dx) == h && Math.abs(dz) == h;
                        boolean beam = y == g + 1 || y == top || y == g + height / 2 + 1;
                        boolean window = !beam && (y - g) % 4 >= 2 && (y - g) % 4 <= 3 && (Math.abs(dx) == 1 || Math.abs(dz) == 1);
                        String s = corner || (dx == 0 || dz == 0) && !window ? frame : (beam ? "dark_oak_planks" : (window ? "glass_pane" : "white_terracotta"));
                        this.set(x + dx, y, z + dz, s);
                    } else {
                        this.set(x + dx, y, z + dz, null);
                    }
                }
            }
        }

        this.box(x - h + 1, g + height / 2 + 1, z - h + 1, x + h - 1, g + height / 2 + 1, z + h - 1, "spruce_planks");
        // hipped red roof
        for (int i = 0; i <= h + 1; i++) {
            int r = h + 1 - i;
            String s = i == 0 ? "red_terracotta" : (r <= 1 ? "red_terracotta" : "red_wool");

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r || Math.abs(dz) == r || i == h + 1) {
                        this.set(x + dx, top + 1 + i, z + dz, s);
                    }
                }
            }
        }

        this.box(x + h - 1, top + 2, z - h + 1, x + h - 1, top + h + 2, z - h + 1, "cobblestone");
        // balcony toward the arena
        double nx = -x;
        double nz = -z;
        String f = facing(nx, nz);
        int bx = f.equals("east") ? x + h + 1 : (f.equals("west") ? x - h - 1 : x);
        int bz = f.equals("south") ? z + h + 1 : (f.equals("north") ? z - h - 1 : z);

        for (int w = -2; w <= 2; w++) {
            int ex = f.equals("east") || f.equals("west") ? bx : bx + w;
            int ez = f.equals("east") || f.equals("west") ? bz + w : bz;
            this.set(ex, g + height / 2 + 1, ez, "spruce_slab[type=top]");
            this.set(ex, g + height / 2 + 2, ez, "spruce_fence");
        }

        this.set(bx, g + height / 2 + 3, bz, "lantern");
    }

    /** The north gatehouse: stone base, timber hall and a big red and white striped tent roof. */
    private void gatehouse(int g) {
        int front = (int)Math.round(-(this.b + this.s0 + 1.0));
        int back = front - 16;
        int hw = 18;
        this.box(-hw - 4, g + 1, back - 4, hw + 4, 59, front + 4, null);

        for (int y = 1; y <= g + 3; y++) {
            this.box(-hw, y, back, hw, y, front, y == g + 3 ? "stone_brick_slab[type=top]" : "stone_bricks");
        }

        int top = g + 14;

        for (int y = g + 4; y <= top; y++) {
            for (int x = -hw; x <= hw; x++) {
                String s = Math.floorMod(x, 6) == 0 || y == g + 4 || y == top || y == g + 9 ? "stripped_dark_oak_log[axis=y]" : "white_terracotta";
                this.set(x, y, front, s);
                this.set(x, y, back, s);
            }

            for (int z = back; z <= front; z++) {
                String s = Math.floorMod(z, 6) == 0 || y == g + 4 || y == top || y == g + 9 ? "stripped_dark_oak_log[axis=y]" : "white_terracotta";
                this.set(-hw, y, z, s);
                this.set(hw, y, z, s);
            }
        }

        // big arched gate doors
        for (int x = -4; x <= 4; x++) {
            int archTop = g + 9 + (int)Math.round(Math.sqrt(Math.max(0, 16 - x * x)) * 0.6);

            for (int y = g + 4; y <= archTop; y++) {
                this.set(x, y, front, "dark_oak_planks");
            }

            this.set(x, archTop + 1, front, "stone_bricks");
        }

        this.box(-hw, top + 1, back, hw, top + 1, front, "spruce_planks");
        // striped tent
        int cz = (front + back) / 2;
        double rx = hw + 1.0;
        double rz = (front - back) / 2.0 + 2.0;
        int ry = 13;

        for (int y = 0; y <= ry; y++) {
            double f = Math.sqrt(Math.max(0.0, 1.0 - (double)(y * y) / (ry * ry)));

            for (int x = (int)-Math.ceil(rx); x <= Math.ceil(rx); x++) {
                for (int z = (int)-Math.ceil(rz); z <= Math.ceil(rz); z++) {
                    double e = Math.sqrt(x * x / (rx * rx) + z * z / (rz * rz));
                    if (e <= f && e > f - 0.22) {
                        int stripe = Math.floorMod((int)Math.floor(deg(x, z) / 15.0), 2);
                        this.set(x, top + 2 + y, cz + z, stripe == 0 ? "red_wool" : "white_wool");
                    }
                }
            }
        }

        this.set(0, top + ry + 3, cz, "gold_block");
        this.set(0, top + ry + 4, cz, "end_rod[facing=up]");

        // timber struts from the corners up to the tent
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz2 = -1; sz2 <= 1; sz2 += 2) {
                this.line(sx * hw, top + 1, cz + sz2 * (rz - 1.0), sx * hw * 0.55, top + 9, cz + sz2 * rz * 0.5, "dark_oak_log[axis=y]");
            }
        }

        // banners
        for (int x : new int[]{-12, 12}) {
            for (int h = 0; h < 6; h++) {
                this.set(x, top - 1 - h, front + 1, h == 5 ? "yellow_wool" : "red_wool");
            }
        }
    }

    /** A rock outcrop with pines in the south-east, behind the houses. */
    private void rock(int g) {
        double t = Math.toRadians(45.0);
        int[] p = this.onRing(this.s0 + 14.0, t);
        long s = this.seed + 77L;

        for (int x = -16; x <= 16; x++) {
            for (int z = -14; z <= 14; z++) {
                double e = Math.sqrt(x * x / 256.0 + z * z / 196.0);
                double h = (1.0 - e) * (g + 16) + (noise(s, x, z) - 0.5) * 6.0;

                for (int y = 1; y <= h; y++) {
                    double v = noise(s + 3L, (p[0] + x) * 2.0 + y, (p[1] + z) * 2.0);
                    String b = y >= h - 1 && v > 0.45 ? "moss_block" : (v > 0.72 ? "andesite" : (v < 0.2 ? "mossy_cobblestone" : "stone"));
                    this.set(p[0] + x, y, p[1] + z, b);
                }

                if (h > g && this.rnd.nextDouble() < 0.05) {
                    this.conifer(p[0] + x, (int)Math.floor(h) + 1, p[1] + z, "spruce_log", "spruce", 7 + this.rnd.nextInt(4));
                }
            }
        }
    }

    public static enum Style {
        PALACE("Desert Palace"),
        OLYMPUS("Olympus"),
        TIMBER_FORT("Timber Fort"),
        MOLE_MINES("Mole Hollow");

        public final String display;

        private Style(String display) {
            this.display = display;
        }
    }
}
