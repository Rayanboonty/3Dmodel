package dev.vortexclient.mod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Small drawing helpers shared by the HUD and the menus. */
public final class Ui {
    private Ui() {}

    public static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** A filled rectangle with its corners cut by one pixel. */
    public static void round(DrawContext c, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        c.fill(x + 1, y, x + w - 1, y + h, color);
        c.fill(x, y + 1, x + 1, y + h - 1, color);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** A one pixel outline with cut corners. */
    public static void outline(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 1, y, x + w - 1, y + 1, color);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        c.fill(x, y + 1, x + 1, y + h - 1, color);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static int alpha(int color, float a) {
        int al = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return r;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(font(), s, x, y, color, false);
    }

    public static void centered(DrawContext c, String s, int cx, int y, int color) {
        c.drawText(font(), s, cx - font().getWidth(s) / 2, y, color, false);
    }

    public static int width(String s) {
        return font().getWidth(s);
    }

    // ------------------------------------------------------- the new look --
    // Rounded glass like the launcher: a translucent body, a soft light from
    // the top and a thin bright rim. Built from fills, so every version draws
    // it the same way.

    /** How far a row of a rounded corner is inset (r = corner radius). */
    private static int inset(int row, int r) {
        double d = r - row - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * (double) r - d * d)));
    }

    /** A filled rectangle with round corners of radius r. */
    public static void rounded(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r <= 1) {
            round(c, x, y, w, h, color);
            return;
        }
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int in = inset(i, r);
            c.fill(x + in, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
    }

    /** A one pixel outline with round corners of radius r. */
    public static void roundedOutline(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r <= 1) {
            outline(c, x, y, w, h, color);
            return;
        }
        c.fill(x + r, y, x + w - r, y + 1, color);
        c.fill(x + r, y + h - 1, x + w - r, y + h, color);
        c.fill(x, y + r, x + 1, y + h - r, color);
        c.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int in = inset(i, r);
            // Each corner row runs from its own inset to the row above's.
            int end = i == 0 ? r : Math.max(in + 1, inset(i - 1, r));
            c.fill(x + in, y + i, x + end, y + i + 1, color);
            c.fill(x + w - end, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + end, y + h - i, color);
            c.fill(x + w - end, y + h - 1 - i, x + w - in, y + h - i, color);
        }
    }

    /** A glass panel: translucent body, light from the top, bright rim. */
    public static void glass(DrawContext c, int x, int y, int w, int h, int r, int body) {
        rounded(c, x, y, w, h, r, body);
        int sheen = Math.min(h / 2, 24);
        if (sheen > 2) c.fillGradient(x + r / 2, y + 1, x + w - r / 2, y + sheen, 0x14FFFFFF, 0x00FFFFFF);
        roundedOutline(c, x, y, w, h, r, 0x2CFFFFFF);
        // A brighter line along the top edge, like light catching glass.
        c.fill(x + r, y, x + w - r, y + 1, 0x55FFFFFF);
    }

    /** An iOS-style switch (t = 0 off .. 1 on), w x h, knob sliding across. */
    public static void toggle(DrawContext c, int x, int y, int w, int h, float t, int accent) {
        int track = mix(0xFF3A3F4A, accent, t);
        rounded(c, x, y, w, h, h / 2, track);
        int k = h - 4;
        int kx = x + 2 + Math.round(t * (w - 4 - k));
        rounded(c, kx, y + 2, k, k, k / 2, 0xFFFFFFFF);
    }

    /** Eases a value toward a target, independent of frame rate. */
    public static float approach(float value, float target, float perSecond, float dtSeconds) {
        float k = 1f - (float) Math.exp(-perSecond * dtSeconds);
        return value + (target - value) * k;
    }
}
