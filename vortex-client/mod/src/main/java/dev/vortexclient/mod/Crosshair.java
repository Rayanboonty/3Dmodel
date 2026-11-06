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
        Config cfg = VortexMods.config;
        int cx = c.getScaledWindowWidth() / 2, cy = c.getScaledWindowHeight() / 2;
        int size = (int) cfg.num(m, "size"), gap = (int) cfg.num(m, "gap"), t = (int) cfg.num(m, "thick");
        int color = switch (cfg.str(m, "color")) {
            case "Accent" -> VortexMods.accent();
            case "Green" -> 0xFF55FF55;
            case "Red" -> 0xFFFF5555;
            case "Aqua" -> 0xFF55FFFF;
            default -> 0xFFFFFFFF;
        };
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
            default -> {
                rect(c, cx - gap - size, cy - lo, cx - gap, cy + hi, color, outline);
                rect(c, cx + gap + 1, cy - lo, cx + gap + size + 1, cy + hi, color, outline);
                rect(c, cx - lo, cy - gap - size, cx + hi, cy - gap, color, outline);
                rect(c, cx - lo, cy + gap + 1, cx + hi, cy + gap + size + 1, color, outline);
            }
        }
        return true;
    }

    private static void rect(DrawContext c, int x1, int y1, int x2, int y2, int color, boolean outline) {
        if (outline) c.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xC0000000);
        c.fill(x1, y1, x2, y2, color);
    }
}
