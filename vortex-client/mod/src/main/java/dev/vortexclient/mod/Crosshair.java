package dev.vortexclient.mod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** The Crosshair module: draws its own crosshair instead of vanilla's. */
public final class Crosshair {
    private Crosshair() {}

    /** Returns true when the vanilla crosshair should be skipped. */
    public static boolean draw(DrawContext c) {
        Module m = Module.get("crosshair");
        if (!VortexMods.config.enabled(m)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (Compat.debugShown()) return false;
        if (!mc.options.getPerspective().isFirstPerson()) return true;
        drawAt(c, c.getScaledWindowWidth() / 2, c.getScaledWindowHeight() / 2, false);
        return true;
    }

    /** The crosshair centred on (cx, cy); preview = the options page. */
    public static void drawAt(DrawContext c, int cx, int cy, boolean preview) {
        Module m = Module.get("crosshair");
        Config cfg = VortexMods.config;
        int size = (int) cfg.num(m, "size"), gap = (int) cfg.num(m, "gap"), t = (int) cfg.num(m, "thick");
        int color = switch (cfg.str(m, "color")) {
            case "Accent" -> VortexMods.accent();
            case "Green" -> 0xFF55FF55;
            case "Red" -> 0xFFFF5555;
            case "Aqua" -> 0xFF55FFFF;
            case "Yellow" -> 0xFFFFFF55;
            case "Pink" -> 0xFFFF77CC;
            case "Rainbow" -> Hud.rainbow(0);
            default -> 0xFFFFFFFF;
        };
        // A hit just landed: a short red flash.
        if (cfg.bool(m, "hitcolor") && System.currentTimeMillis() - Stats.lastHitAt < 180) color = 0xFFFF3B3B;
        // Spread when moving: the gap opens with your speed.
        if (cfg.bool(m, "dynamic")) gap += (int) Math.min(6, Math.round(Stats.speed / 1.5));
        boolean outline = cfg.bool(m, "outline");
        String style = cfg.str(m, "style");
        int lo = t / 2, hi = t - lo;
        switch (style) {
            case "Dot" -> rect(c, cx - lo - 1, cy - lo - 1, cx + hi + 1, cy + hi + 1, color, outline);
            case "Circle" -> {
                int r = Math.max(2, size);
                for (int a = 0; a < 360; a += 10) {
                    int px = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * r);
                    int py = cy + (int) Math.round(Math.sin(Math.toRadians(a)) * r);
                    rect(c, px - lo, py - lo, px + hi, py + hi, color, outline);
                }
                rect(c, cx - 1, cy - 1, cx + 1, cy + 1, color, outline);
            }
            case "Plus" -> {
                rect(c, cx - size, cy - lo, cx + size + 1, cy + hi, color, outline);
                rect(c, cx - lo, cy - size, cx + hi, cy + size + 1, color, outline);
            }
            case "T" -> {
                rect(c, cx - gap - size, cy - lo, cx - gap, cy + hi, color, outline);
                rect(c, cx + gap + 1, cy - lo, cx + gap + size + 1, cy + hi, color, outline);
                rect(c, cx - lo, cy + gap + 1, cx + hi, cy + gap + size + 1, color, outline);
            }
            case "X" -> {
                for (int i = gap; i < gap + size; i++) {
                    rect(c, cx - i - 1, cy - i - 1, cx - i - 1 + t, cy - i - 1 + t, color, outline);
                    rect(c, cx + i, cy - i - 1, cx + i + t, cy - i - 1 + t, color, outline);
                    rect(c, cx - i - 1, cy + i, cx - i - 1 + t, cy + i + t, color, outline);
                    rect(c, cx + i, cy + i, cx + i + t, cy + i + t, color, outline);
                }
            }
            case "Square" -> {
                int r = Math.max(2, size);
                rect(c, cx - r, cy - r, cx + r + 1, cy - r + t, color, outline);
                rect(c, cx - r, cy + r + 1 - t, cx + r + 1, cy + r + 1, color, outline);
                rect(c, cx - r, cy - r, cx - r + t, cy + r + 1, color, outline);
                rect(c, cx + r + 1 - t, cy - r, cx + r + 1, cy + r + 1, color, outline);
            }
            case "Arrow" -> {
                for (int i = 0; i < size; i++) {
                    rect(c, cx - i - 1, cy + gap + i, cx - i - 1 + t, cy + gap + i + t, color, outline);
                    rect(c, cx + i, cy + gap + i, cx + i + t, cy + gap + i + t, color, outline);
                }
            }
            default -> {
                rect(c, cx - gap - size, cy - lo, cx - gap, cy + hi, color, outline);
                rect(c, cx + gap + 1, cy - lo, cx + gap + size + 1, cy + hi, color, outline);
                rect(c, cx - lo, cy - gap - size, cx + hi, cy - gap, color, outline);
                rect(c, cx - lo, cy + gap + 1, cx + hi, cy + gap + size + 1, color, outline);
            }
        }
        if (cfg.bool(m, "center") && !style.equals("Dot")) rect(c, cx - lo, cy - lo, cx + hi, cy + hi, color, outline);
    }

    private static void rect(DrawContext c, int x1, int y1, int x2, int y2, int color, boolean outline) {
        if (outline) c.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xC0000000);
        c.fill(x1, y1, x2, y2, color);
    }
}
