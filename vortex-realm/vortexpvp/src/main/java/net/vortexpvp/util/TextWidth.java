package net.vortexpvp.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class TextWidth {
    private static final int[] ASCII = new int[128];

    private TextWidth() {
    }

    private static void set(String chars, int w) {
        for (char c : chars.toCharArray()) {
            ASCII[c] = w;
        }
    }

    private static int advance(int cp) {
        if (cp < 128) {
            return cp < 32 ? 0 : ASCII[cp];
        } else if (cp < 592) {
            return 6;
        } else {
            return cp >= 57344 && cp <= 63743 ? 12 : 8;
        }
    }

    public static int width(Component c) {
        int[] state = new int[]{0, 0};
        walk(c, false, state);
        return Math.max(state[0], state[1]);
    }

    public static int widest(Collection<? extends Component> list) {
        int w = 0;

        for (Component c : list) {
            w = Math.max(w, width(c));
        }

        return w;
    }

    public static int fit(int w, int min, int max) {
        return Math.max(min, Math.min(max, w + 4));
    }

    private static void walk(Component c, boolean bold, int[] st) {
        State s = c.style().decoration(TextDecoration.BOLD);
        boolean b = s == State.TRUE || s == State.NOT_SET && bold;
        if (c instanceof TextComponent t) {
            add(t.content(), b, st);
        } else if (c instanceof ObjectComponent) {
            st[0] += 9;
        } else {
            add(PlainTextComponentSerializer.plainText().serialize(c.children(List.of())), b, st);
        }

        for (Component child : c.children()) {
            walk(child, b, st);
        }
    }

    private static void add(String text, boolean bold, int[] st) {
        int i = 0;

        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 10) {
                st[1] = Math.max(st[1], st[0]);
                st[0] = 0;
            } else {
                st[0] += advance(cp) + (bold ? 1 : 0);
            }
        }
    }

    static {
        Arrays.fill(ASCII, 6);
        set(" ", 4);
        set("!',.:;i|", 2);
        set("`l", 3);
        set("\"()*I[]t{}", 4);
        set("<>fk", 5);
        set("@~", 7);
    }
}
