package net.vortexpvp.pack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;

public final class Glyphs {
    private static final Key ICONS = Key.key("vortex", "icons");
    private static final Key RANK = Key.key("vortex", "rank");
    private static final Map<String, Integer> FLAG_INDEX = new HashMap<>();
    /** Server logo (tab header). */
    static final int LOGO = 61186;
    /** Spaces of 1, 2, 4 .. 128 px. */
    static final int SPACE = 63489;
    /** Negative spaces of 1, 2, 4 .. 128 px. */
    static final int BACK = 63505;

    private Glyphs() {
    }

    static void flagOrder(List<String> codes) {
        FLAG_INDEX.clear();

        for (int i = 0; i < codes.size(); i++) {
            FLAG_INDEX.put(codes.get(i), i);
        }
    }

    private static Component glyph(Key font, int cp, boolean shadow) {
        Component c = ((TextComponent)Component.text(new String(Character.toChars(cp))).font(font)).color(NamedTextColor.WHITE);
        return shadow ? c : c.shadowColor(ShadowColor.none());
    }

    public static Component flag(String code) {
        Integer i = FLAG_INDEX.get(code);
        return i == null ? null : glyph(ICONS, 57344 + i, false);
    }

    /** The VR logo; it hangs about 28 px below its line, so follow it with three empty lines. */
    public static Component logo() {
        return glyph(ICONS, LOGO, false);
    }

    /** An exact horizontal gap of {@code px} pixels (negative moves back). Needs the pack. */
    public static Component space(int px) {
        StringBuilder sb = new StringBuilder();
        int base = px < 0 ? BACK : SPACE;
        int n = Math.min(1020, Math.abs(px));

        for (int bit = 7; bit >= 0; bit--) {
            while (n >= 1 << bit) {
                sb.appendCodePoint(base + bit);
                n -= 1 << bit;
            }
        }

        return sb.isEmpty() ? Component.empty() : Component.text(sb.toString()).font(ICONS);
    }

    public static Component play() {
        return glyph(ICONS, 61185, false);
    }

    public static Component seal() {
        return glyph(ICONS, 61184, false);
    }

    public static Component rankFrame(int tier, int glow, int frame, int shine) {
        StringBuilder sb = new StringBuilder();
        if (glow >= 0) {
            sb.appendCodePoint(57376 + tier * 3 + Math.min(2, glow));
        } else {
            sb.appendCodePoint(61443);
        }

        sb.appendCodePoint(61440);
        if (frame >= 0) {
            int f = shine >= 0 ? 17 + Math.min(7, shine) : Math.min(16, frame);
            sb.appendCodePoint(57600 + tier * 32 + f);
        } else {
            sb.appendCodePoint(61444);
        }

        sb.appendCodePoint(61442);
        return ((TextComponent)((TextComponent)Component.text(sb.toString()).font(RANK)).color(NamedTextColor.WHITE)).shadowColor(ShadowColor.none());
    }
}
