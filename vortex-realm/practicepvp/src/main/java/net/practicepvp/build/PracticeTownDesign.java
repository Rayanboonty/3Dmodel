package net.practicepvp.build;

/**
 * "Practice Town": a chill, lived-in hub in the MCPVP / Club PvP style. A cobbled plaza with a tiered fountain and a sword
 * statue, four lamp-lit streets, a market street with striped stalls and bunting, and a building for every mode, each
 * with its NPC at the door: Duels Hall, Ranked Arena (a small colosseum), FFA Pit, Leaderboard Hall (podium and framed
 * boards), Kit Workshop, Events Stage, Shop and a Spectate Tower. Around it: gardens, a pond with a bridge, a parkour
 * course, a training yard, groves, meadows and soft hills at the edge, so the town never looks flat.
 */
public final class PracticeTownDesign extends MegaVoxels {
    private static final String PAVE = "polished_andesite";
    private static final String PAVE2 = "stone_bricks";
    private static final String TRIM = "chiseled_stone_bricks";
    private static final String[] AWNINGS = new String[]{"red", "blue", "yellow", "lime", "orange", "light_blue", "magenta", "cyan"};
    private static final String[] BUNTING = new String[]{"red", "yellow", "blue", "white", "lime", "orange"};

    public PracticeTownDesign(int cx, int floorY, int cz, int radius, long seed) {
        super(cx, floorY, cz, radius, -40, 52, seed);
        this.islandBase(this.R - 4, "grass_block", 34, seed ^ 0x7A11L);
        this.streets();
        this.plaza();
        this.duelsHall(0, -66);
        this.rankedArena(66, 0);
        this.ffaPit(-64, 0);
        this.leaderboardHall(46, -48);
        this.kitWorkshop(-48, -48);
        this.eventsStage(46, 50);
        this.shop(-48, 48);
        this.spectateTower(0, 76);
        this.market();
        this.pond(-20, 66);
        this.trainingYard(-78, -30);
        this.parkour(0, -98);
        this.hills();
        this.nature();
        // spawn: south of the fountain, looking north up the main street
        this.spawn = new double[]{0.5, 1.0, 26.5, 180.0};
        this.feature("deco:crown", 0, 15.5, 0, 0, 30);
        this.feature("holo:welcome:FRAME", 0, 2.2, 15, 0, 30);
        this.feature("holo:tiers:HOLO", 18, 2.2, 8, 0, 26);
        this.feature("holo:discord:MARQUEE", -18, 2.2, 8, 0, 26);
        this.finish();
    }

    @Override
    public String name() {
        return "Practice Town";
    }

    // ------------------------------------------------------------------ ground

    private void pave(int x, int z, String b) {
        if (!this.empty(x, 0, z)) {
            this.set(x, 0, z, b);
            this.set(x, 1, z, null);
        }
    }

    private String paving(int x, int z) {
        double n = noise(31L, x * 0.7, z * 0.7);
        return n > 0.72 ? "cobblestone" : (n < 0.18 ? "andesite" : ((x * 7 + z * 3 & 7) == 0 ? PAVE2 : PAVE));
    }

    private void street(double x0, double z0, double x1, double z1, int w) {
        double len = dist(x1 - x0, z1 - z0);
        double ux = (x1 - x0) / len;
        double uz = (z1 - z0) / len;
        double half = w / 2.0;

        for (double t = 0.0; t <= len; t += 0.5) {
            for (double s = -half - 1.0; s <= half + 1.0; s += 0.5) {
                int x = (int)Math.round(x0 + ux * t - uz * s);
                int z = (int)Math.round(z0 + uz * t + ux * s);
                this.pave(x, z, Math.abs(s) > half - 0.2 ? "stone_bricks" : this.paving(x, z));
            }
        }

        for (double t = 7.0; t < len - 3.0; t += 13.0) {
            for (int side = -1; side <= 1; side += 2) {
                int x = (int)Math.round(x0 + ux * t - uz * (half + 2.0) * side);
                int z = (int)Math.round(z0 + uz * t + ux * (half + 2.0) * side);
                if (!this.empty(x, 0, z) && this.empty(x, 1, z)) {
                    this.lampPost(x, 1, z, "stone_brick_wall", "dark_oak_fence", "lantern", 5);
                }

                // a bench between the lamps, facing the street, with a flower pot at its end
                int bx = (int)Math.round(x0 + ux * (t + 6.0) - uz * (half + 2.0) * side);
                int bz = (int)Math.round(z0 + uz * (t + 6.0) + ux * (half + 2.0) * side);
                if (t + 8.0 < len && !this.empty(bx, 0, bz) && this.empty(bx, 1, bz) && this.empty(bx + 1, 1, bz) && this.empty(bx, 1, bz + 1)) {
                    boolean alongX = Math.abs(uz) < Math.abs(ux);
                    this.bench(bx, bz, facing(-uz * side, ux * side), alongX);
                    this.set(alongX ? bx + 2 : bx, 1, alongX ? bz : bz + 2, "potted_red_tulip");
                }
            }
        }
    }

    private void streets() {
        // the main street north to the Duels Hall, south to the arch and the Spectate Tower, east and west to the arenas
        this.street(0, -20, 0, -54, 7);
        this.street(0, 20, 0, 70, 7);
        this.street(20, 0, 48, 0, 7);
        this.street(-20, 0, -50, 0, 7);
        // diagonal lanes to the corner buildings
        for (int[] d : new int[][]{{1, -1}, {-1, -1}, {1, 1}, {-1, 1}}) {
            this.street(d[0] * 14, d[1] * 14, d[0] * 38, d[1] * 40, 5);
        }

        // the welcome arch over the south street
        this.arch(0, 34, 11, 10, "stone_bricks", TRIM, false);

        for (int s = -4; s <= 4; s++) {
            this.set(s, 10, 34, s % 2 == 0 ? "red_wool" : "white_wool");
        }

        this.set(-6, 11, 34, "lantern");
        this.set(6, 11, 34, "lantern");
    }

    // ------------------------------------------------------------------ plaza

    private void plaza() {
        int r = 20;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = dist(x, z);
                if (d <= r) {
                    String b = d > r - 1.2 ? TRIM : ((int)(d + 0.5) % 4 == 0 ? PAVE2 : (Math.abs(x) == Math.abs(z) && d > 9.0 ? "polished_diorite" : PAVE));
                    this.pave(x, z, b);
                }
            }
        }

        // the fountain: a wide basin, a middle tier and a sword planted in the top
        this.fountain(0, 0, 9, "stone_bricks", "quartz_pillar");
        this.ring(0, 0, 2, 4.5, 5.5, "stone_brick_slab[type=bottom]");
        this.disc(0, 0, 1, 4.6, "water");
        this.ring(0, 0, 1, 4.6, 5.5, "stone_bricks");
        this.giantSwordAt(0, 6, 0, 9, "iron_block", "light_gray_concrete", "gold_block", "dark_oak_log", true);
        this.set(0, 5, 0, "sea_lantern");

        // four flower beds with low hedges between the streets, benches facing the fountain
        for (int q = 0; q < 4; q++) {
            double a = 45.0 + q * 90.0;

            for (double rr = 13.0; rr <= 17.0; rr += 0.5) {
                for (double da = -14.0; da <= 14.0; da += 1.5) {
                    int x = px(rr, a + da);
                    int z = pz(rr, a + da);
                    boolean edge = rr < 13.6 || rr > 16.4 || Math.abs(da) > 12.5;
                    this.set(x, 0, z, edge ? "moss_block" : "grass_block");
                    if (edge) {
                        this.set(x, 1, z, "oak_leaves[persistent=true]");
                    } else if (this.rnd.nextDouble() < 0.6) {
                        String[] fl = new String[]{"poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "azure_bluet", "red_tulip", "pink_tulip"};
                        this.set(x, 1, z, fl[this.rnd.nextInt(fl.length)]);
                    }
                }
            }

            int bx = px(11.0, a);
            int bz = pz(11.0, a);
            this.bench(bx, bz, facing(bx, bz), Math.abs(bx) >= Math.abs(bz) ? false : true);
        }

        for (int q = 0; q < 8; q++) {
            double a = q * 45.0 + 22.5;
            this.lampPost(px(18.5, a), 1, pz(18.5, a), "stone_brick_wall", "dark_oak_fence", "lantern", 4);
        }
    }

    // ------------------------------------------------------------------ buildings

    /**
     * A timber-framed building: stone base, walls with log posts, windows, a door on {@code door} (N, S, E, W), a gabled
     * roof along the long side with an overhang, and a plank floor inside.
     */
    private void building(int x0, int z0, int x1, int z1, int h, String wall, String log, String roof, char door) {
        this.box(x0, 0, z0, x1, 0, z1, "stone_bricks");
        this.box(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, "spruce_planks");
        this.walls(x0, 1, z0, x1, 1, z1, "stone_bricks");
        this.walls(x0, 2, z0, x1, h, z1, wall);
        this.box(x0 + 1, 1, z0 + 1, x1 - 1, h, z1 - 1, null);

        for (int x = x0; x <= x1; x += 4) {
            for (int y = 1; y <= h; y++) {
                this.set(x, y, z0, log(log, 'y'));
                this.set(x, y, z1, log(log, 'y'));
            }
        }

        for (int z = z0; z <= z1; z += 4) {
            for (int y = 1; y <= h; y++) {
                this.set(x0, y, z, log(log, 'y'));
                this.set(x1, y, z, log(log, 'y'));
            }
        }

        for (int y = 1; y <= h; y++) {
            this.set(x1, y, z1, log(log, 'y'));
            this.set(x0, y, z1, log(log, 'y'));
            this.set(x1, y, z0, log(log, 'y'));
        }

        // windows: two-high panes between the posts
        for (int x = x0 + 2; x < x1 - 1; x += 4) {
            for (int y = 3; y <= Math.min(4, h - 1); y++) {
                this.set(x, y, z0, "glass_pane");
                this.set(x, y, z1, "glass_pane");
            }
        }

        for (int z = z0 + 2; z < z1 - 1; z += 4) {
            for (int y = 3; y <= Math.min(4, h - 1); y++) {
                this.set(x0, y, z, "glass_pane");
                this.set(x1, y, z, "glass_pane");
            }
        }

        // the door: 3 wide, 4 high, with a lantern either side
        int mx = (x0 + x1) / 2;
        int mz = (z0 + z1) / 2;

        for (int s = -1; s <= 1; s++) {
            for (int y = 1; y <= 4; y++) {
                switch (door) {
                    case 'N' -> this.set(mx + s, y, z0, null);
                    case 'S' -> this.set(mx + s, y, z1, null);
                    case 'E' -> this.set(x1, y, mz + s, null);
                    default -> this.set(x0, y, mz + s, null);
                }
            }
        }

        switch (door) {
            case 'N' -> {
                this.set(mx - 2, 4, z0 - 1, "lantern");
                this.set(mx + 2, 4, z0 - 1, "lantern");
            }
            case 'S' -> {
                this.set(mx - 2, 4, z1 + 1, "lantern");
                this.set(mx + 2, 4, z1 + 1, "lantern");
            }
            case 'E' -> {
                this.set(x1 + 1, 4, mz - 2, "lantern");
                this.set(x1 + 1, 4, mz + 2, "lantern");
            }
            default -> {
                this.set(x0 - 1, 4, mz - 2, "lantern");
                this.set(x0 - 1, 4, mz + 2, "lantern");
            }
        }

        this.roof(x0, z0, x1, z1, h, roof, wall);

        // a chandelier
        int ch = h + (Math.min(x1 - x0, z1 - z0) / 2) - 1;
        this.set(mx, ch, mz, "iron_chain");
        this.set(mx, ch - 1, mz, "iron_chain");
        this.set(mx, ch - 2, mz, "lantern[hanging=true]");
    }

    /** A gabled roof of stairs along the longer side, one block of overhang all round, gable ends filled. */
    private void roof(int x0, int z0, int x1, int z1, int h, String mat, String gable) {
        boolean alongX = x1 - x0 >= z1 - z0;
        int a0 = alongX ? z0 : x0;
        int a1 = alongX ? z1 : x1;
        int b0 = (alongX ? x0 : z0) - 1;
        int b1 = (alongX ? x1 : z1) + 1;
        int layers = (a1 - a0) / 2 + 1;

        for (int i = 0; i <= layers; i++) {
            int lo = a0 - 1 + i;
            int hi = a1 + 1 - i;
            int y = h + 1 + i;
            if (lo > hi) {
                break;
            }

            for (int b = b0; b <= b1; b++) {
                if (lo == hi) {
                    this.set(alongX ? b : lo, y, alongX ? lo : b, slab(mat, false));
                } else {
                    this.set(alongX ? b : lo, y, alongX ? lo : b, stairs(mat, alongX ? "south" : "east", false));
                    this.set(alongX ? b : hi, y, alongX ? hi : b, stairs(mat, alongX ? "north" : "west", false));
                }
            }

            // gable ends
            for (int a = lo + 1; a < hi; a++) {
                if (y > h + 1) {
                    this.set(alongX ? b0 + 1 : a, y - 1, alongX ? a : b0 + 1, gable);
                    this.set(alongX ? b1 - 1 : a, y - 1, alongX ? a : b1 - 1, gable);
                }
            }
        }
    }

    private void wallBanner(int x, int y, int z, String color, String face) {
        this.set(x, y, z, color + "_wall_banner[facing=" + face + "]");
    }

    private void duelsHall(int x, int z) {
        int x0 = x - 13;
        int x1 = x + 13;
        int z0 = z - 11;
        int z1 = z + 11;
        this.building(x0, z0, x1, z1, 9, "white_terracotta", "spruce_log", "dark_oak", 'S');
        // a red carpet aisle to a raised stage with two sparring rings
        for (int zz = z0 + 1; zz < z1; zz++) {
            this.set(x, 1, zz, "red_carpet");
            this.set(x - 1, 1, zz, "red_carpet");
            this.set(x + 1, 1, zz, "red_carpet");
        }

        for (int side = -1; side <= 1; side += 2) {
            int rx = x + side * 7;
            int rz = z - 2;
            this.ring(rx, rz, 1, 3.4, 4.2, "spruce_fence");
            this.disc(rx, rz, 0, 3.4, "smooth_stone");
            this.set(rx, 1, rz, "target");
        }

        this.box(x - 4, 1, z0 + 1, x + 4, 1, z0 + 3, "spruce_planks");

        for (int xx = x - 3; xx <= x + 3; xx += 2) {
            this.set(xx, 2, z0 + 1, "barrel[facing=up]");
        }

        for (int xx = x0 + 3; xx <= x1 - 3; xx += 6) {
            this.wallBanner(xx, 7, z1 + 1, xx % 12 == 0 ? "red" : "blue", "south");
            this.wallBanner(xx, 6, z0 + 1, "red", "south");
        }

        // the sign over the door: two crossed swords in gold
        this.set(x - 1, 7, z1 + 1, "gold_block");
        this.set(x, 8, z1 + 1, "gold_block");
        this.set(x + 1, 7, z1 + 1, "gold_block");
        this.npc(x, 1.0, z1 + 3);
    }

    private void rankedArena(int ox, int oz) {
        int r = 16;
        this.disc(ox, oz, 0, r + 1, "stone_bricks");
        this.disc(ox, oz, 0, r - 4, "sand");
        this.ring(ox, oz, 0, r - 4.5, r - 3.5, "smooth_sandstone");
        // the outer wall, then three stepped rows of seats facing the sand
        this.cylinder(ox, oz, 1, 8, r, "stone_bricks", true);

        for (int k = 0; k < 3; k++) {
            double rr = r - 3.5 + k;

            for (double a = 0.0; a < 360.0; a += 2.0) {
                int x = ox + px(rr, a);
                int z = oz + pz(rr, a);

                for (int y = 1; y <= k; y++) {
                    this.set(x, y, z, "stone_bricks");
                }

                this.set(x, k + 1, z, stairs("stone_brick", facing(x - ox, z - oz), false));
            }
        }

        for (int a = 0; a < 360; a += 20) {
            int x = ox + px(r, a);
            int z = oz + pz(r, a);
            for (int y = 3; y <= 5; y++) {
                this.set(x, y, z, a % 40 == 0 ? "chiseled_stone_bricks" : "iron_bars");
            }

            this.set(x, 9, z, "gold_block");
            if (a % 60 == 0) {
                this.set(x, 10, z, "lantern");
            }
        }

        this.ring(ox, oz, 8, r - 0.5, r + 0.5, "smooth_quartz");
        // the gate faces the plaza (west), 5 wide
        for (int s = -2; s <= 2; s++) {
            for (int y = 1; y <= 6; y++) {
                for (int d = 0; d <= 4; d++) {
                    this.set(ox - r + d, y, oz + s, null);
                }
            }

            this.set(ox - r, 7, oz + s, "gold_block");
        }

        this.wallBanner(ox - r - 1, 6, oz - 3, "yellow", "west");
        this.wallBanner(ox - r - 1, 6, oz + 3, "yellow", "west");
        this.set(ox, 1, oz, "gold_block");
        this.npc(ox - r - 3, 1.0, oz);
    }

    private void ffaPit(int ox, int oz) {
        int r = 12;
        // sunken: the floor is three blocks down, rimmed by stone and a fence with a way in from the street
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = dist(x, z);
                if (d <= r - 1) {
                    for (int y = -2; y <= 2; y++) {
                        this.set(ox + x, y, oz + z, null);
                    }

                    this.set(ox + x, -3, oz + z, noise(77L, x, z) > 0.55 ? "coarse_dirt" : "sand");
                } else if (d <= r) {
                    for (int y = -3; y <= 0; y++) {
                        this.set(ox + x, y, oz + z, y == 0 ? "polished_andesite" : "stone_bricks");
                    }

                    this.set(ox + x, 1, oz + z, "spruce_fence");
                }
            }
        }

        for (int s = -1; s <= 1; s++) {
            this.set(ox + r, 1, oz + s, null);

            for (int k = 0; k < 3; k++) {
                this.set(ox + r - 1 - k, -k, oz + s, stairs("stone_brick", "east", false));
            }
        }

        for (int a = 0; a < 360; a += 45) {
            this.set(ox + px(r - 2, a), -2, oz + pz(r - 2, a), "hay_block");
        }

        this.set(ox, -2, oz, "lodestone");
        this.npc(ox + r + 3, 1.0, oz);
    }

    private void leaderboardHall(int ox, int oz) {
        // an open pavilion: quartz columns, a gold-trimmed roof, a checkered floor; open to the south
        int x0 = ox - 10;
        int x1 = ox + 10;
        int z0 = oz - 8;
        int z1 = oz + 6;

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                this.set(x, 0, z, (x + z & 1) == 0 ? "polished_diorite" : "smooth_quartz");
                this.set(x, 1, z, null);
            }
        }

        for (int x = x0; x <= x1; x += 4) {
            for (int z : new int[]{z0, z1}) {
                for (int y = 1; y <= 7; y++) {
                    this.set(x, y, z, "quartz_pillar");
                }
            }
        }

        for (int z = z0; z <= z1; z += 7) {
            for (int x : new int[]{x0, x1}) {
                for (int y = 1; y <= 7; y++) {
                    this.set(x, y, z, "quartz_pillar");
                }
            }
        }

        this.box(x0 - 1, 8, z0 - 1, x1 + 1, 8, z1 + 1, "smooth_quartz");
        this.walls(x0 - 1, 9, z0 - 1, x1 + 1, 9, z1 + 1, "gold_block");
        this.box(x0, 9, z0, x1, 9, z1, "smooth_quartz_slab[type=bottom]");
        // back wall with banners
        this.box(x0, 1, z0, x1, 7, z0, "white_concrete");

        for (int x = x0 + 2; x <= x1 - 2; x += 4) {
            this.wallBanner(x, 6, z0 + 1, x % 8 == 0 ? "yellow" : "light_blue", "south");
        }

        for (int x = x0 + 1; x < x1; x++) {
            this.set(x, 7, z0 + 1, "lantern[hanging=true]");
            x += 3;
        }

        // the boards: the podium in the middle, framed boards either side, all facing the street
        this.feature("lb:overall:PODIUM:ELO", ox, 1.0, oz - 3, ox, oz + 40);
        this.feature("lb:overall:FRAME:WINS", ox - 7, 1.0, oz - 5, ox - 4, oz + 40);
        this.feature("lb:overall:FRAME:WINRATE", ox + 7, 1.0, oz - 5, ox + 4, oz + 40);
        this.npc(ox, 1.0, z1 + 3);
    }

    private void kitWorkshop(int ox, int oz) {
        int x0 = ox - 9;
        int x1 = ox + 9;
        int z0 = oz - 7;
        int z1 = oz + 7;
        this.building(x0, z0, x1, z1, 7, "spruce_planks", "dark_oak_log", "spruce", 'S');
        String[] work = new String[]{"anvil[facing=north]", "smithing_table", "grindstone[face=floor,facing=north]", "blast_furnace[facing=south]", "crafting_table", "fletching_table",
            "barrel[facing=up]", "lectern[facing=south]"};

        for (int i = 0; i < work.length; i++) {
            this.set(x0 + 2 + i * 2, 1, z0 + 1, work[i]);
        }

        for (int z = z0 + 2; z <= z1 - 3; z += 2) {
            this.set(x0 + 1, 1, z, "barrel[facing=east]");
            this.set(x1 - 1, 1, z, "chest[facing=west]");
        }

        this.set(ox - 3, 1, oz + 2, "anvil[facing=east]");
        this.set(ox + 3, 1, oz + 2, "smithing_table");
        this.npc(ox, 1.0, z1 + 3);
    }

    private void eventsStage(int ox, int oz) {
        // a wooden stage on the north side with a striped backdrop, and curved seating rows facing it
        int sz = oz - 8;
        this.box(ox - 8, 1, sz - 3, ox + 8, 2, sz + 2, "spruce_planks");
        this.box(ox - 8, 3, sz - 3, ox + 8, 9, sz - 3, "white_wool");

        for (int x = ox - 8; x <= ox + 8; x += 2) {
            this.box(x, 3, sz - 3, x, 9, sz - 3, "red_wool");
        }

        for (int x = ox - 7; x <= ox + 7; x += 7) {
            this.set(x, 3, sz + 2, "lantern");
        }

        for (int s = -1; s <= 1; s++) {
            this.set(ox + s, 1, sz + 3, stairs("spruce", "north", false));
        }

        for (int k = 0; k < 5; k++) {
            double rr = 7.0 + k * 2.0;

            for (double a = 30.0; a <= 150.0; a += 3.0) {
                int x = ox + px(rr, a);
                int z = sz + pz(rr, a);
                for (int y = 1; y <= k; y++) {
                    this.set(x, y, z, "stone_bricks");
                }

                this.set(x, k + 1, z, stairs("spruce", facing(x - ox, z - sz), false));
            }
        }

        this.npc(ox - 12, 1.0, sz + 4);
    }

    private void shop(int ox, int oz) {
        int x0 = ox - 8;
        int x1 = ox + 8;
        int z0 = oz - 6;
        int z1 = oz + 6;
        this.building(x0, z0, x1, z1, 7, "light_gray_terracotta", "stripped_spruce_log", "spruce", 'N');
        // a striped awning over the front and a counter inside
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            this.set(x, 5, z0 - 1, (x & 1) == 0 ? "red_wool" : "white_wool");
            this.set(x, 4, z0 - 2, ((x & 1) == 0 ? "red" : "white") + "_carpet");
        }

        for (int x = x0 + 2; x <= x1 - 2; x++) {
            this.set(x, 1, oz, "spruce_slab[type=top]");
        }

        for (int x = x0 + 1; x <= x1 - 1; x += 2) {
            this.set(x, 1, z1 - 1, "barrel[facing=up]");
            this.set(x, 2, z1 - 1, (x & 2) == 0 ? "chest[facing=north]" : "decorated_pot");
        }

        this.npc(ox, 1.0, z0 - 4);
    }

    private void spectateTower(int ox, int oz) {
        this.disc(ox, oz, 0, 8, "stone_bricks");
        this.towerAt(ox, 0, oz, 6, 26, "stone_bricks", "polished_andesite", "dark_oak_planks", "sea_lantern");
        // a doorway toward the town and a viewing balcony
        for (int s = -1; s <= 1; s++) {
            for (int y = 1; y <= 3; y++) {
                this.set(ox + s, y, oz - 6, null);
            }
        }

        this.ring(ox, oz, 20, 6.0, 8.0, "spruce_planks");
        this.ring(ox, oz, 21, 7.4, 8.0, "spruce_fence");
        this.npc(ox, 1.0, oz - 9);
    }

    // ------------------------------------------------------------------ market street

    private void stall(int x, int z, String color, boolean north) {
        int f = north ? -1 : 1;

        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            for (int y = 1; y <= 3; y++) {
                this.set(x + c[0], y, z + c[1], "spruce_fence");
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                this.set(x + dx, 4, z + dz, (dx & 1) == 0 ? color + "_wool" : "white_wool");
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            this.set(x + dx, 1, z + f, "spruce_slab[type=top]");
        }

        this.set(x, 1, z - f, "barrel[facing=up]");
        this.set(x - 1, 1, z - f, this.rnd.nextBoolean() ? "melon" : "pumpkin");
        this.set(x + 1, 1, z - f, "hay_block");
        this.set(x, 3, z, "lantern[hanging=true]");
    }

    private void market() {
        int i = 0;

        for (int side = -1; side <= 1; side += 2) {
            for (int x = 24; x <= 44; x += 7) {
                for (int row = -1; row <= 1; row += 2) {
                    this.stall(side * x, row * 8, AWNINGS[i++ % AWNINGS.length], row > 0);
                }
            }

            // bunting across the street between the stalls
            for (int x = 22; x <= 46; x += 8) {
                for (int z = -6; z <= 6; z++) {
                    this.set(side * x, 6 + (Math.abs(z) > 4 ? 0 : (Math.abs(z) > 2 ? -1 : -1)), z, BUNTING[(z + 6 + x) % BUNTING.length] + "_carpet");
                }

                for (int y = 1; y <= 6; y++) {
                    this.set(side * x, y, -7, "spruce_fence");
                    this.set(side * x, y, 7, "spruce_fence");
                }
            }
        }
    }

    // ------------------------------------------------------------------ nature and extras

    private void pond(int ox, int oz) {
        double r = 8.0;

        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                double d = dist(x, z * 1.3);
                if (d <= r) {
                    this.set(ox + x, 0, oz + z, "water");
                    this.set(ox + x, -1, oz + z, d < r - 2 ? "water" : "clay");
                    this.set(ox + x, -2, oz + z, "sand");
                    if (this.rnd.nextDouble() < 0.06 && d < r - 1) {
                        this.set(ox + x, 1, oz + z, "lily_pad");
                    }
                } else if (d <= r + 1.2) {
                    this.set(ox + x, 0, oz + z, this.rnd.nextBoolean() ? "sand" : "gravel");
                    if (this.rnd.nextDouble() < 0.25) {
                        this.set(ox + x, 1, oz + z, this.rnd.nextBoolean() ? "sugar_cane" : "short_grass");
                    }
                }
            }
        }

        this.bridge(ox - 11, 1, oz, ox + 11, 1, oz, "spruce_planks", "spruce_fence");
        this.bench(ox, oz - 9, "south", true);
    }

    private void trainingYard(int ox, int oz) {
        for (int x = -7; x <= 7; x++) {
            for (int z = -6; z <= 6; z++) {
                boolean edge = Math.abs(x) == 7 || Math.abs(z) == 6;
                this.pave(ox + x, oz + z, "coarse_dirt");
                if (edge && !(x > 5 && Math.abs(z) <= 1)) {
                    this.set(ox + x, 1, oz + z, "oak_fence");
                }
            }
        }

        for (int x = -4; x <= 4; x += 4) {
            for (int z = -3; z <= 3; z += 3) {
                this.set(ox + x, 1, oz + z, "hay_block");
                this.set(ox + x, 2, oz + z, "hay_block");
                this.set(ox + x, 3, oz + z, "carved_pumpkin[facing=east]");
            }
        }

        for (int z = -5; z <= 5; z += 2) {
            this.set(ox - 6, 2, oz + z, "target");
        }
    }

    private void parkour(int ox, int oz) {
        // a loop of jumps rising behind the Duels Hall, from a start pad to a gold finish
        this.box(ox - 2, 1, oz - 2, ox + 2, 1, oz + 2, "polished_andesite");
        String[] blocks = new String[]{"lime_concrete", "light_blue_concrete", "yellow_concrete", "orange_concrete", "magenta_concrete", "cyan_concrete"};
        double a = 0.0;
        double x = ox + 3;
        double z = oz;
        int y = 1;

        for (int i = 0; i < 22; i++) {
            a += 0.33;
            x += Math.cos(a) * 3.0;
            z += Math.sin(a) * 2.2;
            if (i % 2 == 0 && y < 12) {
                y++;
            }

            this.set((int)Math.round(x), y, (int)Math.round(z), blocks[i % blocks.length]);
            if (i % 5 == 4) {
                this.set((int)Math.round(x) + 1, y, (int)Math.round(z), blocks[i % blocks.length]);
            }
        }

        this.box((int)Math.round(x) - 1, y + 1, (int)Math.round(z) - 1, (int)Math.round(x) + 1, y + 1, (int)Math.round(z) + 1, "gold_block");
        this.set((int)Math.round(x), y + 2, (int)Math.round(z), "light_weighted_pressure_plate");
    }

    /** Soft hills around the edge, so the town sits in a green bowl instead of on a flat plate. */
    private void hills() {
        long seed = 911L;

        for (int x = -this.R; x <= this.R; x++) {
            for (int z = -this.R; z <= this.R; z++) {
                double d = dist(x, z) / this.R;
                if (d > 0.8 && "grass_block".equals(this.get(x, 0, z)) && this.empty(x, 1, z)) {
                    double f = Math.min(1.0, (d - 0.8) / 0.12);
                    int h = (int)Math.round(f * (2.0 + 6.0 * noise(seed, x * 0.06, z * 0.06)) * (d > 0.93 ? (1.0 - d) / 0.07 + 0.2 : 1.0));

                    for (int y = 0; y < h; y++) {
                        this.set(x, y, z, "dirt");
                    }

                    if (h > 0) {
                        this.set(x, h, z, "grass_block");
                    }
                }
            }
        }
    }

    private void nature() {
        // trees along the streets, groves on the hills, meadows and hedges in the open spaces
        for (int t = 28; t <= 64; t += 9) {
            for (int[] dir : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                for (int side = -1; side <= 1; side += 2) {
                    int x = dir[0] * t + (dir[0] == 0 ? side * 8 : 0);
                    int z = dir[1] * t + (dir[1] == 0 ? side * 8 : 0);
                    if ("grass_block".equals(this.get(x, 0, z)) && this.empty(x, 1, z) && this.empty(x, 4, z)) {
                        boolean birch = (t / 9 & 1) == 0;
                        this.tree(x, 1, z, birch ? "birch_log" : "oak_log", birch ? "birch" : "oak", 4 + this.rnd.nextInt(2), 2.6);
                    }
                }
            }
        }

        for (int a = 0; a < 360; a += 24) {
            double rr = this.R * 0.86;
            int x = px(rr, a);
            int z = pz(rr, a);
            int top = 0;

            while (top < 10 && !this.empty(x, top + 1, z)) {
                top++;
            }

            this.groveAt(x, top, z, 9.0, 4, a % 48 == 0 ? "spruce_log" : "oak_log", a % 48 == 0 ? "spruce" : "oak");
        }

        for (int[] m : new int[][]{{28, 28}, {-28, 28}, {28, -28}, {-28, -28}, {70, 30}, {-72, 28}, {72, -30}, {-24, -76}, {26, -78}, {30, 76}, {-46, 76}}) {
            this.meadow(m[0], m[1], 9.0, 0.35);
        }

        this.grove(-30, -80, 10.0, 5, "dark_oak_log", "dark_oak");
        this.grove(30, 80, 9.0, 4, "cherry_log", "cherry");
    }
}
