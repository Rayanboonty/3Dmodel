package net.vortexrealm.build;

/**
 * "Castle Market" hub in the MCPVP lobby style, without houses: a fountain plaza in the middle, four lamp-lit avenues,
 * a market street of striped-awning stalls under coloured bunting (east and west), four small castles on the
 * diagonals (one per game mode, each with a gate, corner towers, a keep and its own roof colour), stone arches at the
 * ends of the avenues, gardens, hedges and trees. NPC spots: one at each castle gate and four around the fountain.
 */
public final class CastleMarketDesign extends Voxels {
    /** Distance of the castles from the centre, on both axes. */
    private final int castleAt;
    private final int plaza;

    public CastleMarketDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -40, 40, seed);
        this.castleAt = (int)Math.round(radius * 0.46);
        this.plaza = 17;
        this.island(0.0, 0, 0.0, this.R - 4, "grass_block", "stone", seed ^ 0x5EED);
        this.avenues();
        this.plaza();
        this.fountain();
        this.market();
        String[][] castles = new String[][]{
            {"red_terracotta", "red_wool"}, {"blue_terracotta", "blue_wool"}, {"green_terracotta", "lime_wool"}, {"orange_terracotta", "yellow_wool"}
        };
        int i = 0;

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                this.castle(sx * this.castleAt, sz * this.castleAt, castles[i][0], castles[i][1]);
                i++;
            }
        }

        this.arches();
        this.gardens();
        this.spawn = new double[]{0.5, 1.0, this.plaza + 6.5, 180.0};

        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            this.npc((int)Math.round(Math.cos(a) * 13.0), 1.0, (int)Math.round(Math.sin(a) * 13.0));
        }

        this.finish();
    }

    @Override
    public String name() {
        return "castle_market";
    }

    private static int hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7FFFFFFF;
    }

    /** Worn medieval paving. */
    private static String paving(int x, int z) {
        int h = hash(x, z, 1) % 100;
        return h < 58 ? "stone_bricks" : (h < 82 ? "polished_andesite" : (h < 91 ? "cracked_stone_bricks" : "mossy_stone_bricks"));
    }

    private boolean ground(int x, int z) {
        return "grass_block".equals(this.get(x, 0, z));
    }

    private void pave(int x, int z, String b) {
        if (this.get(x, 0, z) != null) {
            this.set(x, 0, z, b);
            this.set(x, 1, z, null);
        }
    }

    // ------------------------------------------------------------------ avenues

    private void avenues() {
        int end = this.R - 9;

        for (int d = this.plaza - 1; d <= end; d++) {
            for (int w = -3; w <= 3; w++) {
                String b = Math.abs(w) == 3 ? (hash(d, w, 2) % 3 == 0 ? "andesite" : "cobblestone") : paving(d, w);
                this.pave(d, w, b);
                this.pave(-d, w, b);
                this.pave(w, d, b);
                this.pave(w, -d, b);
            }
        }

        // lamp posts along all four avenues
        for (int d = this.plaza + 4; d <= end - 4; d += 10) {
            for (int s = -1; s <= 1; s += 2) {
                this.lamp(d, s * 5);
                this.lamp(-d, s * 5);
                this.lamp(s * 5, d);
                this.lamp(s * 5, -d);
            }
        }
    }

    private void lamp(int x, int z) {
        if (this.get(x, 0, z) != null) {
            this.set(x, 0, z, "polished_andesite");
            this.lampPost(x, 1, z, "stone_brick_wall", "dark_oak_fence", "lantern", 5);
        }
    }

    // ------------------------------------------------------------------ plaza and fountain

    private void plaza() {
        for (int x = -this.plaza; x <= this.plaza; x++) {
            for (int z = -this.plaza; z <= this.plaza; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (!(d > this.plaza + 0.4)) {
                    double a = Math.atan2(z, x);
                    int sector = (int)Math.floor((a + Math.PI) / (Math.PI / 8));
                    String b;
                    if (d >= this.plaza - 0.6) {
                        b = "chiseled_stone_bricks";
                    } else if (Math.abs(d - 12.0) < 0.6) {
                        b = "polished_diorite";
                    } else if (d < 9.5) {
                        b = "smooth_stone";
                    } else {
                        b = sector % 2 == 0 ? "polished_andesite" : "stone_bricks";
                    }

                    this.pave(x, z, b);
                }
            }
        }

        // benches between the avenues, facing the fountain
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;

            for (int t = -2; t <= 2; t++) {
                double px = Math.cos(a) * 15.5 - Math.sin(a) * t;
                double pz = Math.sin(a) * 15.5 + Math.cos(a) * t;
                int x = (int)Math.round(px);
                int z = (int)Math.round(pz);
                // the back of the bench is away from the fountain, so people sit facing it
                this.set(x, 1, z, stairs("spruce", facing(Math.cos(a), Math.sin(a)), false));
            }

            int fx = (int)Math.round(Math.cos(a) * 16.5);
            int fz = (int)Math.round(Math.sin(a) * 16.5);
            this.set(fx, 1, fz, "flowering_azalea_leaves[persistent=true]");
        }
    }

    private void fountain() {
        // basin: water at floor level inside a two-high rim
        this.disc(0.0, 0.0, -1, 7.0, "stone_bricks");
        this.disc(0.0, 0.0, 0, 6.2, "water");
        this.ring(0.0, 0.0, 0, 6.3, 7.4, "stone_bricks");
        this.ring(0.0, 0.0, 1, 6.3, 7.4, "stone_brick_slab[type=bottom]");

        for (int[] c : new int[][]{{7, 0}, {-7, 0}, {0, 7}, {0, -7}}) {
            this.set(c[0], 1, c[1], "chiseled_stone_bricks");
            this.set(c[0], 2, c[1], "lantern");
        }

        // pillar, upper bowl (water held by a full rim) and a lit crown
        this.cylinder(0.0, 0.0, 0, 3, 1.4, "quartz_pillar", false);
        this.disc(0.0, 0.0, 4, 3.3, "smooth_quartz");
        this.ring(0.0, 0.0, 5, 2.5, 3.3, "quartz_bricks");
        this.disc(0.0, 0.0, 5, 2.3, "water");
        this.set(0, 5, 0, "quartz_pillar");
        this.set(0, 6, 0, "quartz_pillar");
        this.set(0, 7, 0, "chiseled_quartz_block");
        this.set(0, 8, 0, "sea_lantern");
        this.set(0, 9, 0, "end_rod[facing=up]");

        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            this.set(c[0], 7, c[1], "quartz_slab[type=bottom]");
        }

        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            this.set((int)Math.round(Math.cos(a) * 4.4), 1, (int)Math.round(Math.sin(a) * 4.4), "lily_pad");
        }
    }

    // ------------------------------------------------------------------ market street

    private void market() {
        String[][] stripes = new String[][]{
            {"red_wool", "white_wool"}, {"blue_wool", "white_wool"}, {"lime_wool", "white_wool"}, {"yellow_wool", "white_wool"}, {"purple_wool", "white_wool"},
            {"orange_wool", "white_wool"}
        };
        int n = 0;

        int last = this.R - 13;

        for (int s = -1; s <= 1; s += 2) {
            for (int x = this.plaza + 6; x <= this.R - 20; x += 9) {
                if (Math.abs(x - this.castleAt) >= 8) {
                    // not where the castle paths cross the street
                    for (int side = -1; side <= 1; side += 2) {
                        String[] st = stripes[n++ % stripes.length];
                        // the stall faces the avenue (toward z = 0)
                        this.stall(s * x, side * 8, 0, -side, st[0], st[1], n);
                    }
                }
            }

            // bunting from lamp post to lamp post: across the street and along both sides, sagging in the middle
            for (int d = this.plaza + 4; d <= last; d += 10) {
                this.bunting(s * d, -5, s * d, 5);
                if (d + 10 <= last) {
                    for (int side = -1; side <= 1; side += 2) {
                        this.bunting(s * d, side * 5, s * (d + 10), side * 5);
                    }
                }
            }
        }
    }

    private static final String[] BUNTING = new String[]{
        "red_stained_glass_pane", "yellow_stained_glass_pane", "blue_stained_glass_pane", "lime_stained_glass_pane", "orange_stained_glass_pane",
        "magenta_stained_glass_pane"
    };

    private void bunting(int x0, int z0, int x1, int z1) {
        double len = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        int steps = (int)Math.ceil(len);

        for (int i = 0; i <= steps; i++) {
            double t = (double)i / Math.max(1, steps);
            int x = (int)Math.round(x0 + (x1 - x0) * t);
            int z = (int)Math.round(z0 + (z1 - z0) * t);
            int y = 6 - (int)Math.round(Math.sin(t * Math.PI) * (len > 12.0 ? 1.5 : 1.0));
            if (this.empty(x, y, z)) {
                this.set(x, y, z, BUNTING[i % BUNTING.length]);
            }
        }
    }

    /**
     * A market stall: counter at the front, posts, a striped awning with a front valance and goods. {@code fx,fz} is
     * the direction the stall faces (toward the street).
     */
    private void stall(int sx, int sz, int fx, int fz, String a, String b, int kind) {
        int ax = -fz;
        int az = fx;
        String face = facing(fx, fz);

        for (int u = -2; u <= 2; u++) {
            for (int v = -1; v <= 3; v++) {
                int x = sx + ax * u - fx * v;
                int z = sz + az * u - fz * v;
                if (v >= 0) {
                    this.set(x, 0, z, "spruce_planks");

                    for (int y = 1; y <= 4; y++) {
                        this.set(x, y, z, null);
                    }
                }

                // awning: stripes run front to back; the front row hangs one lower (valance)
                String wool = Math.floorMod(u, 2) == 0 ? a : b;
                this.set(x, v == -1 ? 4 : 5, z, wool);
            }
        }

        for (int u = -2; u <= 2; u += 4) {
            for (int v = 0; v <= 3; v += 3) {
                for (int y = 1; y <= 4; y++) {
                    this.set(sx + ax * u - fx * v, y, sz + az * u - fz * v, "spruce_fence");
                }
            }
        }

        // counter and goods
        String[] goods = switch (kind % 5) {
            case 0 -> new String[]{"melon", "pumpkin", "hay_block[axis=y]"};
            case 1 -> new String[]{"bookshelf", "lectern[facing=" + face + "]", "potted_red_tulip"};
            case 2 -> new String[]{"anvil[facing=" + face + "]", "smithing_table", "grindstone[face=floor,facing=" + face + "]"};
            case 3 -> new String[]{"brewing_stand", "cauldron", "potted_blue_orchid"};
            default -> new String[]{"barrel[facing=up]", "chest[facing=" + face + "]", "potted_cornflower"};
        };

        for (int u = -1; u <= 1; u++) {
            int x = sx + ax * u;
            int z = sz + az * u;
            this.set(x, 1, z, u == 0 ? "stripped_spruce_wood[axis=y]" : "barrel[facing=up]");
            this.set(x, 2, z, goods[u + 1]);
        }

        for (int u = -1; u <= 1; u++) {
            int x = sx + ax * u - fx * 3;
            int z = sz + az * u - fz * 3;
            this.set(x, 1, z, u == 0 ? "crafting_table" : "barrel[facing=up]");
            this.set(x, 2, z, u == 0 ? "lantern" : "spruce_trapdoor[half=bottom,facing=" + face + "]");
        }
    }

    // ------------------------------------------------------------------ castles

    private String wallBlock(int x, int y, int z) {
        int h = hash(x * 3 + y, z * 5 - y, 7) % 100;
        return y <= 1 ? "cobblestone" : (h < 70 ? "stone_bricks" : (h < 85 ? "mossy_stone_bricks" : "cracked_stone_bricks"));
    }

    private void castle(int ox, int oz, String roof, String banner) {
        int half = 8;
        int wallH = 7;
        // the gate faces the market street (z = 0)
        int gz = oz > 0 ? -1 : 1;

        for (int dx = -half - 3; dx <= half + 3; dx++) {
            for (int dz = -half - 3; dz <= half + 3; dz++) {
                for (int y = 1; y <= 4; y++) {
                    this.set(ox + dx, y, oz + dz, null);
                }

                int m = Math.max(Math.abs(dx), Math.abs(dz));
                if (m <= half) {
                    this.set(ox + dx, 0, oz + dz, (dx + dz & 1) == 0 ? "polished_andesite" : "andesite");
                } else if (this.get(ox + dx, 0, oz + dz) != null) {
                    this.set(ox + dx, 0, oz + dz, "gravel");
                }
            }
        }

        // curtain wall, two thick, with a walkway and crenellations
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                if (m >= half - 1) {
                    for (int y = 1; y <= wallH; y++) {
                        this.set(ox + dx, y, oz + dz, this.wallBlock(ox + dx, y, oz + dz));
                    }

                    if (m == half && (dx + dz & 1) == 0) {
                        this.set(ox + dx, wallH + 1, oz + dz, "stone_bricks");
                    }
                }
            }
        }

        // gate: an arched opening, a raised portcullis and lanterns
        int wz = oz + gz * half;

        for (int dx = -1; dx <= 1; dx++) {
            for (int k = 0; k <= 1; k++) {
                for (int y = 1; y <= (dx == 0 ? 4 : 3); y++) {
                    this.set(ox + dx, y, wz - gz * k, null);
                }

                this.set(ox + dx, 0, wz - gz * k, "stone_bricks");
            }

        }

        this.set(ox, 5, wz, "chiseled_stone_bricks");

        this.set(ox - 2, 3, wz + gz, "lantern");
        this.set(ox + 2, 3, wz + gz, "lantern");
        this.set(ox - 2, 2, wz + gz, "stone_brick_wall");
        this.set(ox + 2, 2, wz + gz, "stone_brick_wall");
        this.set(ox - 2, 1, wz + gz, "stone_brick_wall");
        this.set(ox + 2, 1, wz + gz, "stone_brick_wall");

        // gatehouse towers and banners either side
        for (int s = -1; s <= 1; s += 2) {
            this.tower(ox + s * 3, wz, 1.4, 10, roof, banner);

            for (int y = 5; y <= 7; y++) {
                this.set(ox + s * 6, y, wz + gz, banner);
            }
        }

        // corner towers
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                this.tower(ox + sx * half, oz + sz * half, 3.0, 12, roof, banner);
            }
        }

        // the keep at the back of the courtyard
        int kx = ox;
        int kz = oz - gz * 3;
        int kh = 14;

        for (int y = 1; y <= kh; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    this.set(kx + dx, y, kz + dz, wall ? this.wallBlock(kx + dx, y, kz + dz) : (y == 7 ? "spruce_planks" : null));
                }
            }
        }

        for (int y = 3; y <= kh - 2; y += 5) {
            for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                this.set(kx + d[0], y, kz + d[1], "glass_pane");
                this.set(kx + d[0], y + 1, kz + d[1], "glass_pane");
            }
        }

        for (int y = 1; y <= 3; y++) {
            this.set(kx, y, kz + gz * 3, y == 3 ? "stone_bricks" : null);
        }

        this.set(kx, 1, kz + gz * 3, null);
        this.set(kx, 2, kz + gz * 3, null);

        for (int k = 0; k <= 4; k++) {
            int y = kh + 1 + k;
            int h = 4 - k;

            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == h || k == 4) {
                        this.set(kx + dx, y, kz + dz, roof);
                    }
                }
            }
        }

        for (int y = kh + 6; y <= kh + 9; y++) {
            this.set(kx, y, kz, "spruce_fence");
        }

        this.box(kx + 1, kh + 8, kz, kx + 3, kh + 9, kz, banner);
        // courtyard: a well and two trees
        this.ring(ox + 4, oz + gz * 3, 1, 0.6, 1.6, "cobblestone");
        this.set(ox + 4, 0, oz + gz * 3, "water");
        this.tree(ox - 5, 1, oz + gz * 4, "oak_log", "oak", 4, 2.4);
        // path from the gate to the street
        int from = wz + gz * 2;
        int to = gz < 0 ? 4 : -4;

        for (int z = Math.min(from, to); z <= Math.max(from, to); z++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Math.abs(z) > 3) {
                    this.pave(ox + dx, z, Math.abs(dx) == 2 ? "cobblestone" : paving(ox + dx, z));
                }
            }
        }

        this.npc(ox, 1.0, wz + gz * 4);
    }

    private void tower(int x, int z, double r, int h, String roof, String banner) {
        this.cylinder(x, z, 1, h, r, "stone_bricks", false);
        this.cylinder(x, z, 2, h - 1, r - 1.0, "air", false);

        for (int y = 3; y < h; y += 4) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                this.set(x + d[0] * (int)Math.round(r), y, z + d[1] * (int)Math.round(r), "glass_pane");
            }
        }

        this.ring(x, z, h + 1, r - 0.4, r + 0.8, "stone_brick_slab[type=bottom]");
        this.cone(x, z, h + 1, h + 4 + (int)Math.round(r * 2.0), r + 0.5, roof);
        int top = h + 5 + (int)Math.round(r * 2.0);
        this.set(x, top, z, "spruce_fence");
        this.set(x, top + 1, z, "spruce_fence");
        this.set(x + 1, top + 1, z, banner);
        this.set(x + 1, top, z, banner);
    }

    // ------------------------------------------------------------------ arches and gardens

    private void arches() {
        int d = this.R - 14;

        for (int k = 0; k < 4; k++) {
            int ux = k == 0 ? 1 : (k == 1 ? -1 : 0);
            int uz = k == 2 ? 1 : (k == 3 ? -1 : 0);
            int px = -uz;
            int pz = ux;

            for (int s = -1; s <= 1; s += 2) {
                for (int t = 0; t <= 1; t++) {
                    for (int y = 1; y <= 8; y++) {
                        int x = ux * d + px * s * (5 + t);
                        int z = uz * d + pz * s * (5 + t);
                        this.set(x, y, z, y == 8 ? "chiseled_stone_bricks" : this.wallBlock(x, y, z));
                    }
                }
            }

            for (int w = -6; w <= 6; w++) {
                for (int y = 8; y <= 9; y++) {
                    this.set(ux * d + px * w, y, uz * d + pz * w, y == 9 && (w & 1) == 0 ? "stone_brick_slab[type=bottom]" : "stone_bricks");
                }
            }

            this.set(ux * d + px * -3, 7, uz * d + pz * -3, "lantern[hanging=true]");
            this.set(ux * d + px * 3, 7, uz * d + pz * 3, "lantern[hanging=true]");
        }
    }

    private boolean clear(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!this.ground(x + dx, z + dz)) {
                    return false;
                }

                for (int y = 1; y <= 3; y++) {
                    if (!this.empty(x + dx, y, z + dz)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void gardens() {
        // hedges and flower beds along the north and south avenues
        for (int d = this.plaza + 3; d <= this.R - 18; d++) {
            for (int s = -1; s <= 1; s += 2) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    int z = dir * d;
                    if (this.ground(s * 7, z)) {
                        this.set(s * 7, 1, z, (d / 5 & 1) == 0 ? "azalea_leaves[persistent=true]" : "flowering_azalea_leaves[persistent=true]");
                    }

                    if (this.ground(s * 6, z) && this.empty(s * 6, 1, z)) {
                        String[] f = new String[]{"poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "azure_bluet"};
                        this.set(s * 6, 1, z, f[hash(s, d, 3) % f.length]);
                    }
                }
            }
        }

        // trees beyond the plaza, away from paths and buildings
        String[][] kinds = new String[][]{{"oak_log", "oak"}, {"birch_log", "birch"}, {"cherry_log", "cherry"}, {"spruce_log", "spruce"}};

        for (int i = 0, placed = 0; i < 1200 && placed < 70; i++) {
            int x = this.rnd.nextInt(this.R * 2) - this.R;
            int z = this.rnd.nextInt(this.R * 2) - this.R;
            double d = Math.sqrt(x * x + z * z);
            if (d > this.plaza + 8 && d < this.R - 10 && this.clear(x, z, 3)) {
                String[] k = kinds[this.rnd.nextInt(kinds.length)];
                if (k[1].equals("spruce")) {
                    this.conifer(x, 1, z, k[0], k[1], 7 + this.rnd.nextInt(4));
                } else {
                    this.tree(x, 1, z, k[0], k[1], 4 + this.rnd.nextInt(3), 2.6 + this.rnd.nextDouble() * 0.8);
                }

                placed++;
            }
        }

        // grass and flowers
        String[] flowers = new String[]{"poppy", "dandelion", "oxeye_daisy", "cornflower", "short_grass", "short_grass", "short_grass", "fern"};

        for (int i = 0; i < 2600; i++) {
            int x = this.rnd.nextInt(this.R * 2) - this.R;
            int z = this.rnd.nextInt(this.R * 2) - this.R;
            if (this.ground(x, z) && this.empty(x, 1, z) && Math.sqrt(x * x + z * z) > this.plaza + 1) {
                this.set(x, 1, z, flowers[this.rnd.nextInt(flowers.length)]);
            }
        }
    }
}
