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

    /** Eases a value toward a target, independent of frame rate. */
    public static float approach(float value, float target, float perSecond, float dtSeconds) {
        float k = 1f - (float) Math.exp(-perSecond * dtSeconds);
        return value + (target - value) * k;
    }
}
