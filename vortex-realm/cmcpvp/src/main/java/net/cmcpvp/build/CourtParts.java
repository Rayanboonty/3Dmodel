package net.cmcpvp.build;

/**
 * Building parts for the court arenas ({@link CourtArenaDesign}): textured walls, building faces with arched and
 * gothic windows, pilasters, cornices and battlements, round and square towers, cone, pyramid, gable and onion roofs,
 * banners and cloths, lamps and trees. Everything is written in local coordinates (floor at y = 0).
 */
abstract class CourtParts extends MegaVoxels {
    protected CourtParts(int cx, int floorY, int cz, int radius, int yLo, int yHi, long seed) {
        super(cx, floorY, cz, radius, yLo, yHi, seed);
    }

    /** A block choice that may depend on the position (textured walls). */
    @FunctionalInterface
    protected interface Mat {
        String at(int x, int y, int z);
    }

    protected static Mat of(String b) {
        return (x, y, z) -> b;
    }

    /** A noisy mix: the first block mostly, the others in patches (weights add up to 1 or less; the rest = first). */
    protected Mat mixOf(long salt, String first, Object... rest) {
        return (x, y, z) -> {
            double n = hash3(salt, x, y, z);
            double acc = 0.0;
            for (int i = 0; i + 1 < rest.length; i += 2) {
                acc += ((Number)rest[i + 1]).doubleValue();
                if (n < acc) {
                    return (String)rest[i];
                }
            }

            return first;
        };
    }

    protected static double hash3(long salt, int x, int y, int z) {
        long h = salt * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L + z * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11 & 0xFFFFFL) / (double)0xFFFFF;
    }

    protected static String dir(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        } else {
            return dz > 0 ? "south" : "north";
        }
    }

    protected static String stairsOf(String mat, String facing, boolean top) {
        return stairs(mat, facing, top);
    }

    // ------------------------------------------------------------------ building faces

    /**
     * A building face: its front plane passes through (cx, cz); (bx, bz) points into the building; u runs to the right
     * as seen from the front, w into the building (w &lt; 0 sticks out of the face), y is the height.
     */
    protected final class Face {
        final int cx;
        final int cz;
        final int bx;
        final int bz;
        final int rx;
        final int rz;

        Face(int cx, int cz, int bx, int bz) {
            this.cx = cx;
            this.cz = cz;
            this.bx = bx;
            this.bz = bz;
            this.rx = -bz;
            this.rz = bx;
        }

        int x(int u, int w) {
            return this.cx + u * this.rx + w * this.bx;
        }

        int z(int u, int w) {
            return this.cz + u * this.rz + w * this.bz;
        }

        void set(int u, int y, int w, String b) {
            CourtParts.this.set(this.x(u, w), y, this.z(u, w), b);
        }

        void setIfEmpty(int u, int y, int w, String b) {
            CourtParts.this.setIfEmpty(this.x(u, w), y, this.z(u, w), b);
        }

        String get(int u, int y, int w) {
            return CourtParts.this.get(this.x(u, w), y, this.z(u, w));
        }

        boolean empty(int u, int y, int w) {
            return CourtParts.this.empty(this.x(u, w), y, this.z(u, w));
        }

        void box(int u0, int y0, int w0, int u1, int y1, int w1, Mat m) {
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) {
                for (int w = Math.min(w0, w1); w <= Math.max(w0, w1); w++) {
                    for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                        int x = this.x(u, w);
                        int z = this.z(u, w);
                        CourtParts.this.set(x, y, z, m == null ? null : m.at(x, y, z));
                    }
                }
            }
        }

        void box(int u0, int y0, int w0, int u1, int y1, int w1, String b) {
            this.box(u0, y0, w0, u1, y1, w1, b == null ? null : of(b));
        }

        /** Facing toward the front (out of the building). */
        String front() {
            return dir(-this.bx, -this.bz);
        }

        /** Facing into the building. */
        String back() {
            return dir(this.bx, this.bz);
        }

        String right() {
            return dir(this.rx, this.rz);
        }

        String left() {
            return dir(-this.rx, -this.rz);
        }

        Face moved(int du, int dw) {
            return new Face(this.x(du, dw), this.z(du, dw), this.bx, this.bz);
        }

        /** The same plane seen from the side: the face turned 90 degrees to the right around (u, w). */
        Face turnedRight(int u, int w) {
            return new Face(this.x(u, w), this.z(u, w), this.rx, this.rz);
        }
    }

    /** True inside an arch opening: u from uc-hw to uc+hw, y from y0 to y1, rounded (or pointed) at the top. */
    protected static boolean inArch(double u, int y, double uc, double hw, int y0, int y1, boolean pointed) {
        if (y < y0 || y > y1 || Math.abs(u - uc) > hw + 0.01) {
            return false;
        }

        double rise = pointed ? Math.max(2.0, hw * 1.7) : Math.max(1.0, hw);
        double ya = y1 - rise;
        if (y <= ya) {
            return true;
        }

        double t = (y - ya) / (rise + 0.6);
        double half = pointed ? hw * (1.0 - Math.pow(t, 1.5)) + 0.25 : Math.sqrt(Math.max(0.0, 1.0 - t * t)) * hw + 0.35;
        return Math.abs(u - uc) <= half;
    }

    /**
     * A window in a face: the opening is cut from the front plane, the glass sits {@code depth} blocks in with a dark
     * back behind it, a frame goes round it, a sill (stairs) sticks out under it and a hood or keystone above.
     */
    protected void window(Face f, double uc, int y0, int width, int height, boolean pointed, String glass, String back, String frame, String sillMat, int depth) {
        double hw = (width - 1) / 2.0;
        int y1 = y0 + height - 1;
        int u0 = (int)Math.floor(uc - hw) - 1;
        int u1 = (int)Math.ceil(uc + hw) + 1;
        for (int u = u0; u <= u1; u++) {
            for (int y = y0 - 1; y <= y1 + 1; y++) {
                boolean in = inArch(u, y, uc, hw, y0, y1, pointed);
                if (in) {
                    for (int w = 0; w < depth; w++) {
                        f.set(u, y, w, null);
                    }

                    f.set(u, y, depth, glass);
                    f.set(u, y, depth + 1, back);
                } else if (frame != null && (inArch(u - 1, y, uc, hw, y0, y1, pointed) || inArch(u + 1, y, uc, hw, y0, y1, pointed)
                    || inArch(u, y - 1, uc, hw, y0, y1, pointed) || inArch(u, y + 1, uc, hw, y0, y1, pointed))) {
                    f.set(u, y, 0, frame);
                }
            }
        }

        if (sillMat != null) {
            for (int u = (int)Math.round(uc - hw); u <= (int)Math.round(uc + hw); u++) {
                f.set(u, y0 - 1, -1, stairs(sillMat, f.back(), true));
            }
        }

        if (frame != null) {
            f.set((int)Math.round(uc), y1 + 1, -1, frame);
        }
    }

    /** An open arch (passage or niche): cut {@code depth} deep, the back filled with {@code fill} (null = open). */
    protected void arch(Face f, double uc, int y0, int width, int height, boolean pointed, int depth, String fill, String frame) {
        double hw = (width - 1) / 2.0;
        int y1 = y0 + height - 1;
        for (int u = (int)Math.floor(uc - hw) - 1; u <= (int)Math.ceil(uc + hw) + 1; u++) {
            for (int y = y0; y <= y1 + 1; y++) {
                if (inArch(u, y, uc, hw, y0, y1, pointed)) {
                    for (int w = 0; w < depth; w++) {
                        f.set(u, y, w, null);
                    }

                    if (fill != null) {
                        f.set(u, y, depth, fill);
                    }
                } else if (frame != null && (inArch(u - 1, y, uc, hw, y0, y1, pointed) || inArch(u + 1, y, uc, hw, y0, y1, pointed)
                    || inArch(u, y - 1, uc, hw, y0, y1, pointed))) {
                    f.set(u, y, 0, frame);
                    f.set(u, y, -1, frame);
                }
            }
        }
    }

    /** A pilaster sticking one block out of the face, with a base and a capital. */
    protected void pilaster(Face f, int u, int y0, int y1, String shaft, String base, String capStairsMat) {
        for (int y = y0; y <= y1; y++) {
            f.set(u, y, -1, y == y0 && base != null ? base : shaft);
        }

        if (capStairsMat != null) {
            f.set(u, y1 + 1, -1, stairs(capStairsMat, f.back(), false));
        }
    }

    /** A cornice: a row of upside-down stairs sticking out of the face from u0 to u1. */
    protected void cornice(Face f, int u0, int u1, int y, String stairMat, int out) {
        for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) {
            for (int w = -out; w < 0; w++) {
                f.set(u, y, w, stairs(stairMat, f.back(), true));
            }
        }
    }

    /** Battlements on top of a wall from u0 to u1: merlons two wide with one-block gaps, on the front row. */
    protected void merlons(Face f, int u0, int u1, int y, Mat m, int phase) {
        for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) {
            if (Math.floorMod(u + phase, 3) != 2) {
                f.set(u, y, 0, m.at(f.x(u, 0), y, f.z(u, 0)));
            }
        }
    }

    /**
     * A hanging cloth (wool) on a face: width wide, from yTop down len blocks with a pointed end, hung from a dark rod;
     * a trim row at the top and an emblem (a small diamond) a third of the way down.
     */
    protected void cloth(Face f, int uc, int yTop, int width, int len, String main, String trim, String emblem) {
        int hw = width / 2;
        int ey = len / 3 + 1;
        for (int du = -hw; du <= hw; du++) {
            int tip = hw - Math.abs(du);
            for (int k = 0; k < len + tip; k++) {
                String b = k == 0 && trim != null ? trim : main;
                if (emblem != null && (du == 0 && Math.abs(k - ey) <= 1 || Math.abs(du) == 1 && k == ey && width >= 3)) {
                    b = emblem;
                }

                if (trim != null && k == len + tip - 1 && du == 0) {
                    b = trim;
                }

                f.set(uc + du, yTop - k, -1, b);
            }
        }

        for (int du = -hw - 1; du <= hw + 1; du++) {
            f.set(uc + du, yTop + 1, -1, "dark_oak_fence");
        }
    }

    // ------------------------------------------------------------------ shapes

    protected void fill(int x0, int y0, int z0, int x1, int y1, int z1, Mat m) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    this.set(x, y, z, m == null ? null : m.at(x, y, z));
                }
            }
        }
    }

    protected void cyl(double ox, double oz, double r, int y0, int y1, Mat m) {
        int n = (int)Math.ceil(r) + 1;
        for (int x = -n; x <= n; x++) {
            for (int z = -n; z <= n; z++) {
                if (x * x + z * z <= r * r + 0.3) {
                    int wx = (int)Math.round(ox) + x;
                    int wz = (int)Math.round(oz) + z;
                    for (int y = y0; y <= y1; y++) {
                        this.set(wx, y, wz, m.at(wx, y, wz));
                    }
                }
            }
        }
    }

    protected void ringAt(double ox, double oz, double rIn, double rOut, int y, Mat m) {
        int n = (int)Math.ceil(rOut) + 1;
        for (int x = -n; x <= n; x++) {
            for (int z = -n; z <= n; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= rOut + 0.2 && d >= rIn - 0.2) {
                    int wx = (int)Math.round(ox) + x;
                    int wz = (int)Math.round(oz) + z;
                    this.set(wx, y, wz, m.at(wx, y, wz));
                }
            }
        }
    }

    /** A cone roof standing on a round tower: radius r at y0, a point h higher, with a finial. */
    protected void coneRoof(double ox, double oz, double r, int y0, int h, Mat m) {
        for (int k = 0; k <= h; k++) {
            double rr = r * (1.0 - (double)k / (h + 1));
            int n = (int)Math.ceil(rr) + 1;
            for (int x = -n; x <= n; x++) {
                for (int z = -n; z <= n; z++) {
                    if (x * x + z * z <= rr * rr + 0.4) {
                        int wx = (int)Math.round(ox) + x;
                        int wz = (int)Math.round(oz) + z;
                        this.set(wx, y0 + k, wz, m.at(wx, y0 + k, wz));
                    }
                }
            }
        }
    }

    /** A stepped pyramid roof over x0..x1 / z0..z1 from y0: stairs on the edges, solid inside. Returns the top y. */
    protected int pyramidRoof(int x0, int z0, int x1, int z1, int y0, String stairMat, Mat body) {
        int k = 0;
        while (x0 + k <= x1 - k && z0 + k <= z1 - k) {
            int y = y0 + k;
            int ax = x0 + k;
            int bx = x1 - k;
            int az = z0 + k;
            int bz = z1 - k;
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) {
                    boolean ex = x == ax || x == bx;
                    boolean ez = z == az || z == bz;
                    if (ax == bx || az == bz) {
                        this.set(x, y, z, body.at(x, y, z));
                    } else if (ex && ez) {
                        this.set(x, y, z, body.at(x, y, z));
                    } else if (ex && stairMat != null) {
                        this.set(x, y, z, stairs(stairMat, x == ax ? "east" : "west", false));
                    } else if (ez && stairMat != null) {
                        this.set(x, y, z, stairs(stairMat, z == az ? "south" : "north", false));
                    } else {
                        this.set(x, y, z, body.at(x, y, z));
                    }
                }
            }

            k++;
        }

        return y0 + k - 1;
    }

    /** A gable roof over x0..x1 / z0..z1 from y0, ridge along x (alongX) or along z; the gable ends filled with gableMat. */
    protected int gableRoof(int x0, int z0, int x1, int z1, int y0, boolean alongX, String stairMat, Mat body, Mat gable) {
        int lo = alongX ? z0 : x0;
        int hi = alongX ? z1 : x1;
        int a0 = alongX ? x0 : z0;
        int a1 = alongX ? x1 : z1;
        int k = 0;
        int top = y0;
        while (lo + k <= hi - k) {
            int y = y0 + k;
            top = y;
            for (int a = a0; a <= a1; a++) {
                for (int c = lo + k; c <= hi - k; c++) {
                    int x = alongX ? a : c;
                    int z = alongX ? c : a;
                    boolean low = c == lo + k;
                    boolean high = c == hi - k;
                    boolean end = a == a0 || a == a1;
                    String b;
                    if (low && high || stairMat == null && (low || high)) {
                        b = body.at(x, y, z);
                    } else if (low) {
                        b = stairs(stairMat, alongX ? "south" : "east", false);
                    } else if (high) {
                        b = stairs(stairMat, alongX ? "north" : "west", false);
                    } else {
                        b = end && gable != null ? gable.at(x, y, z) : body.at(x, y, z);
                    }

                    this.set(x, y, z, b);
                }
            }

            k++;
        }

        return top;
    }

    /** An onion dome standing on (x, y, z): bulges past its base, narrows to a point with a golden finial. */
    protected void onion(double x, int y, double z, double r, Mat m, String trim) {
        int h = (int)Math.round(r * 2.3);
        for (int dy = 0; dy <= h; dy++) {
            double t = (double)dy / h;
            double rr = t < 0.38 ? r * (0.86 + 0.24 * Math.sin(t / 0.38 * Math.PI / 2.0)) : r * 1.1 * Math.pow(Math.cos((t - 0.38) / 0.62 * Math.PI / 2.0), 1.5);
            int n = (int)Math.ceil(rr) + 1;
            for (int dx = -n; dx <= n; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    if (dx * dx + dz * dz <= Math.max(0.25, rr * rr) + 0.2) {
                        int wx = (int)Math.round(x) + dx;
                        int wz = (int)Math.round(z) + dz;
                        this.set(wx, y + dy, wz, m.at(wx, y + dy, wz));
                    }
                }
            }
        }

        if (trim != null) {
            this.ringAt(x, z, r * 0.7, r * 0.95, y, of(trim));
        }

        this.set((int)Math.round(x), y + h + 1, (int)Math.round(z), "gold_block");
        this.set((int)Math.round(x), y + h + 2, (int)Math.round(z), "lightning_rod[facing=up]");
    }

    /** A thin spire: a wall post with a finial. */
    protected void spire(int x, int y, int z, int h, String post, String tip) {
        for (int k = 0; k < h; k++) {
            this.set(x, y + k, z, post);
        }

        this.set(x, y + h, z, tip);
    }

    // ------------------------------------------------------------------ nature

    protected void palmTree(int x, int y, int z) {
        int h = 7 + this.rnd.nextInt(5);
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
            double ang = dir * Math.PI / 4.0 + 0.2;
            for (int k = 1; k <= 5; k++) {
                int lx = tx + (int)Math.round(Math.cos(ang) * k);
                int lz = tz + (int)Math.round(Math.sin(ang) * k);
                int ly = top - (k >= 3 ? 1 : 0) - (k >= 5 ? 1 : 0);
                this.setIfEmpty(lx, ly, lz, leaves("jungle"));
            }
        }
    }

    /** A round broadleaf tree with a few side branches. */
    /**
     * A broadleaf tree the way builders shape them (1.0.15): a slightly leaning trunk with a flared foot, three to five
     * boughs climbing out at different heights, and a crown of several soft, uneven leaf clusters (no single ball).
     */
    protected void broadleaf(int x, int y, int z, String logType, String leafType, int trunk, double crown) {
        double lean = hash3(901L, x, y, z) * Math.PI * 2.0;
        double tx = x;
        double tz = z;
        int height = Math.max(4, trunk + (int)Math.round(crown * 0.6));
        for (int i = 0; i < height; i++) {
            if (i > 1) {
                tx += Math.cos(lean) * 0.13;
                tz += Math.sin(lean) * 0.13;
            }

            this.set((int)Math.round(tx), y + i, (int)Math.round(tz), log(logType, 'y'));
        }

        if (height >= 7) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (hash3(902L, x + d[0], y, z + d[1]) < 0.6) {
                    this.set(x + d[0], y, z + d[1], log(logType, 'y'));
                }
            }
        }

        int top = y + height;
        int boughs = 3 + (int)(hash3(903L, x, y, z) * 2.99);
        for (int b = 0; b < boughs; b++) {
            double a = (b + hash3(905L, x, b, z) * 0.7) * Math.PI * 2.0 / boughs;
            double len = crown * (0.75 + hash3(907L, x, b, z) * 0.5);
            int by = top - 2 - (int)(hash3(909L, x, b, z) * Math.min(4, height / 2.0));
            double ex = tx + Math.cos(a) * len;
            double ez = tz + Math.sin(a) * len;
            double ey = by + len * 0.5;
            this.line(tx, by, tz, ex, ey, ez, log(logType, Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? 'x' : 'z'));
            this.leafCluster(ex, ey + 0.8, ez, crown * 0.8 + 0.6, leafType);
        }

        this.leafCluster(tx, top + 1.2, tz, crown * 1.05, leafType);
    }

    /** A soft cluster of leaves: squashed, with a ragged edge. */
    protected void leafCluster(double ox, double oy, double oz, double r, String leafType) {
        int n = (int)Math.ceil(r) + 1;
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    int x = (int)Math.round(ox) + dx;
                    int y = (int)Math.round(oy) + dy;
                    int z = (int)Math.round(oz) + dz;
                    double d = Math.sqrt(dx * dx + dy * dy * 2.1 + dz * dz);
                    if (d <= r - hash3(911L, x, y, z) * 1.0) {
                        this.setIfEmpty(x, y, z, leaves(leafType));
                    }
                }
            }
        }
    }

    protected void blob(int x, int y, int z, double r, String b) {
        int n = (int)Math.ceil(r);
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.7 + dz * dz);
                    if (d <= r - this.rnd.nextDouble() * 0.7) {
                        this.setIfEmpty(x + dx, y + dy, z + dz, b);
                    }
                }
            }
        }
    }

    /** A bare dead tree: a crooked trunk and a few forked branches. */
    protected void deadTree(int x, int y, int z, String logType, int h) {
        int tx = x;
        int tz = z;
        for (int i = 0; i < h; i++) {
            this.set(tx, y + i, tz, log(logType, 'y'));
            if (i > 2 && this.rnd.nextInt(4) == 0) {
                tx += this.rnd.nextInt(3) - 1;
                tz += this.rnd.nextInt(3) - 1;
                this.set(tx, y + i, tz, log(logType, 'y'));
            }
        }

        int n = 3 + this.rnd.nextInt(3);
        for (int b = 0; b < n; b++) {
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            int sy = y + h / 2 + this.rnd.nextInt(Math.max(1, h / 2));
            double len = 2.5 + this.rnd.nextDouble() * 3.5;
            int ex = tx + (int)Math.round(Math.cos(a) * len);
            int ez = tz + (int)Math.round(Math.sin(a) * len);
            int ey = sy + 1 + this.rnd.nextInt(3);
            this.line(tx, sy, tz, ex, ey, ez, log(logType, Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? 'x' : 'z'));
            if (this.rnd.nextBoolean()) {
                this.line(ex, ey, ez, ex + (int)Math.round(Math.cos(a + 0.8) * 2), ey + 2, ez + (int)Math.round(Math.sin(a + 0.8) * 2), log(logType, 'y'));
            }
        }
    }

    /** A cherry tree (1.0.15): a twisting trunk, spreading boughs and wide, flat clouds of blossom. */
    protected void cherryTree(int x, int y, int z) {
        int height = 5 + (int)(hash3(921L, x, y, z) * 3.0);
        double a = hash3(923L, x, y, z) * Math.PI * 2.0;
        double tx = x;
        double tz = z;
        for (int i = 0; i < height; i++) {
            tx += Math.cos(a + i * 0.5) * 0.33;
            tz += Math.sin(a + i * 0.5) * 0.33;
            this.set((int)Math.round(tx), y + i, (int)Math.round(tz), log("cherry_log", 'y'));
        }

        int top = y + height;
        for (int b = 0; b < 4; b++) {
            double ba = a + b * Math.PI / 2.0 + hash3(925L, x, b, z) * 0.8;
            double len = 3.0 + hash3(927L, x, b, z) * 2.5;
            double ex = tx + Math.cos(ba) * len;
            double ez = tz + Math.sin(ba) * len;
            double ey = top - 1 + len * 0.3;
            this.line(tx, top - 2, tz, ex, ey, ez, log("cherry_log", Math.abs(Math.cos(ba)) > Math.abs(Math.sin(ba)) ? 'x' : 'z'));
            this.ellipsoid(ex, ey + 1.0, ez, 3.0, 1.4, 3.0, leaves("cherry"));
        }

        this.ellipsoid(tx, top + 1.3, tz, 3.6, 1.6, 3.6, leaves("cherry"));
    }

    /** A spruce (1.0.15): tiers of uneven width (wide, narrow, wide...), a pointed top, snow on its branches when snowy. */
    protected void spruce(int x, int y, int z, int h, boolean snow) {
        for (int i = 0; i < h; i++) {
            this.set(x, y + i, z, log("spruce_log", 'y'));
        }

        for (int i = 2; i <= h + 1; i++) {
            double t = (double)(i - 2) / Math.max(1, h - 1);
            double r = (1.0 - t) * (1.8 + h * 0.17);
            if (i % 3 == 1) {
                r *= 0.55;
            }

            int ry = (int)Math.ceil(r);
            for (int dx = -ry; dx <= ry; dx++) {
                for (int dz = -ry; dz <= ry; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if ((dx != 0 || dz != 0) && d <= r + 0.3 - hash3(931L, x + dx, y + i, z + dz) * 0.7) {
                        this.setIfEmpty(x + dx, y + i, z + dz, leaves("spruce"));
                        if (snow && this.empty(x + dx, y + i + 1, z + dz) && hash3(933L, x + dx, y + i, z + dz) < 0.65) {
                            this.set(x + dx, y + i + 1, z + dz, "snow[layers=2]");
                        }
                    }
                }
            }
        }

        this.set(x, y + h + 1, z, leaves("spruce"));
        this.set(x, y + h + 2, z, snow ? "snow[layers=3]" : leaves("spruce"));
    }

    /**
     * A gable roof over x0..x1 / z0..z1 from y0 (ridge along x, or along z), {@code over} blocks of eave; the gable ends
     * are walled with {@code body}. Returns the y above the ridge.
     */
    protected int gableRoof(int x0, int x1, int z0, int z1, int y0, String stairMat, String body, boolean alongX, int over) {
        int a0 = alongX ? z0 - over : x0 - over;
        int a1 = alongX ? z1 + over : x1 + over;
        int b0 = alongX ? x0 - over : z0 - over;
        int b1 = alongX ? x1 + over : z1 + over;
        int k = 0;
        int y = y0;
        while (a0 + k <= a1 - k) {
            for (int b = b0; b <= b1; b++) {
                for (int a = a0 + k; a <= a1 - k; a++) {
                    boolean lo = a == a0 + k;
                    boolean hi = a == a1 - k;
                    String blk;
                    if (lo && hi) {
                        blk = slab(stairMat, false);
                    } else if (lo) {
                        blk = stairs(stairMat, alongX ? "south" : "east", false);
                    } else if (hi) {
                        blk = stairs(stairMat, alongX ? "north" : "west", false);
                    } else if (b == b0 + over || b == b1 - over) {
                        blk = body;
                    } else {
                        continue;
                    }

                    if (alongX) {
                        this.set(b, y, a, blk);
                    } else {
                        this.set(a, y, b, blk);
                    }
                }
            }

            k++;
            y++;
        }

        return y;
    }

    protected void lantern(int x, int y, int z, String post, int h, String light) {
        for (int k = 0; k < h; k++) {
            this.set(x, y + k, z, post);
        }

        this.set(x, y + h, z, light);
    }
}
