package net.vortexpvp.fx.anim;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;

/** Small shared animation helpers: easing curves, colour maths and the "light sweep" text effect. */
public final class Anim {
    private Anim() {
    }

    public static double clamp01(double t) {
        return t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
    }

    public static double easeOutCubic(double t) {
        t = clamp01(t);
        double u = 1.0 - t;
        return 1.0 - u * u * u;
    }

    public static double easeInCubic(double t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static double easeInOutCubic(double t) {
        t = clamp01(t);
        return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
    }

    public static double easeInOutSine(double t) {
        t = clamp01(t);
        return -(Math.cos(Math.PI * t) - 1.0) / 2.0;
    }

    /** Overshoots a little and settles (pop-ins). */
    public static double easeOutBack(double t) {
        t = clamp01(t);
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(t - 1.0, 3.0) + c1 * Math.pow(t - 1.0, 2.0);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static int mix(int a, int b, double t) {
        t = clamp01(t);
        int r = (int)Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (int)Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (int)Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    public static int parseRgb(String hex, int fallback) {
        if (hex == null) {
            return fallback;
        } else {
            String h = hex.trim();
            if (h.startsWith("<") && h.endsWith(">")) {
                h = h.substring(1, h.length() - 1);
            }

            if (h.startsWith("#")) {
                h = h.substring(1);
            }

            try {
                return h.length() == 6 ? Integer.parseInt(h, 16) : fallback;
            } catch (NumberFormatException var4) {
                return fallback;
            }
        }
    }

    /**
     * The moving light + shadow over a name, in the name's own colour: a bright band (the colour mixed toward white)
     * sweeps left to right with a darker band trailing it. {@code phase} 0..1 = band position across the name (values
     * outside 0..1 leave the name in its plain colour, which gives a pause between sweeps).
     */
    public static Component sweep(String text, int baseRgb, double phase, boolean bold) {
        int hi = mix(baseRgb, 0xFFFFFF, 0.72);
        int lo = mix(baseRgb, 0x000000, 0.5);
        int n = text.length();
        double pos = -3.0 + phase * (n + 6.0);
        TextComponent.Builder out = Component.text();

        for (int i = 0; i < n; i++) {
            double d = i - pos;
            double light = Math.exp(-d * d / 2.4);
            double shadow = Math.exp(-(d + 2.8) * (d + 2.8) / 1.8) * 0.85;
            int c = mix(mix(baseRgb, lo, shadow), hi, light);
            out.append(Component.text(String.valueOf(text.charAt(i)), TextColor.color(c)));
        }

        Component built = out.build();
        return bold ? built.decoration(TextDecoration.BOLD, true) : built;
    }

    /** Sweep phase for tick {@code t}: one pass every {@code period} ticks, travelling for {@code travel} of them. */
    public static double sweepPhase(int t, int period, int travel, int offset) {
        int k = Math.floorMod(t + offset, period);
        return k >= travel ? 2.0 : (double)k / travel;
    }

    /** Teleport with client-side smoothing (duration clamped to what the client accepts). */
    public static void glide(Display d, Location to, int ticks) {
        if (d != null && d.isValid()) {
            d.setTeleportDuration(Math.max(0, Math.min(59, ticks)));
            d.teleport(to);
        }
    }

    /** Interpolated pose change starting now. */
    public static void pose(Display d, Transformation t, int ticks) {
        if (d != null && d.isValid()) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(Math.max(0, ticks));
            d.setTransformation(t);
        }
    }
}
