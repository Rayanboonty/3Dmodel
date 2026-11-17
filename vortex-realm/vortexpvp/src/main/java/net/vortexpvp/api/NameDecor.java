package net.vortexpvp.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * Extra bits add-ons show right after a player's name in chat and in the tab list (a rank badge, a clan tag, a
 * title...). Each provider returns the component to append (with its own leading space) or an empty one.
 */
public final class NameDecor {
    public static final List<Function<UUID, Component>> CHAT = new CopyOnWriteArrayList<>();
    public static final List<Function<UUID, Component>> TAB = new CopyOnWriteArrayList<>();

    private NameDecor() {
    }

    public static Component chat(UUID u) {
        return join(CHAT, u);
    }

    public static Component tab(UUID u) {
        return join(TAB, u);
    }

    private static Component join(List<Function<UUID, Component>> list, UUID u) {
        Component out = Component.empty();
        for (Function<UUID, Component> f : list) {
            try {
                Component c = f.apply(u);
                if (c != null) {
                    out = out.append(c);
                }
            } catch (RuntimeException var5) {
            }
        }

        return out;
    }
}
