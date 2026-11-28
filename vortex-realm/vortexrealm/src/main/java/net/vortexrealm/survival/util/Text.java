package net.vortexrealm.survival.util;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.Builder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class Text {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final int SHADOW_GOLD = -12637696;
    public static final int SHADOW_GREEN = -15779313;
    public static final int SHADOW_RED = -12973558;

    private Text() {
    }

    public static Component mm(String s) {
        return MM.deserialize(s == null ? "" : s);
    }

    public static Component mm(String s, TagResolver... resolvers) {
        return MM.deserialize(s == null ? "" : s, resolvers);
    }

    public static Component item(String s, TagResolver... resolvers) {
        return noItalic(mm(s, resolvers));
    }

    public static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE);
    }

    public static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);

        for (String line : lines) {
            out.add(item(line));
        }

        return out;
    }

    public static List<Component> lore(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());

        for (String line : lines) {
            out.add(item(line));
        }

        return out;
    }

    public static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String esc(String s) {
        return MM.escapeTags(s == null ? "" : s);
    }

    public static Component shadow(Component c, int argb) {
        return c.shadowColor(ShadowColor.shadowColor(argb));
    }

    public static TagResolver vars(Object... pairs) {
        if (pairs != null && pairs.length != 0) {
            Builder b = TagResolver.builder();

            for (int i = 0; i + 1 < pairs.length; i += 2) {
                String key = String.valueOf(pairs[i]);
                Object value = pairs[i + 1];
                if (value instanceof Component comp) {
                    b.resolver(Placeholder.component(key, comp));
                } else {
                    b.resolver(Placeholder.unparsed(key, String.valueOf(value)));
                }
            }

            return b.build();
        } else {
            return TagResolver.empty();
        }
    }

    public static String gradientBold(String from, String to, String text) {
        return "<b><gradient:" + from + ":" + to + ">" + text + "</gradient></b>";
    }
}
