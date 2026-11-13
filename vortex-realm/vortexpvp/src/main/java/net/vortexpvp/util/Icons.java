package net.vortexpvp.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import org.bukkit.entity.Player;

public final class Icons {
    private static final Map<UUID, Component> HEADS = new ConcurrentHashMap<>();

    private Icons() {
    }

    public static Component sprite(String ref) {
        if (ref != null && !ref.isBlank() && Cfg.bool(Opt.SPRITE_ICONS)) {
            String atlas = "items";
            String path = ref;
            int i = ref.indexOf(58);
            if (i > 0) {
                atlas = ref.substring(0, i);
                path = ref.substring(i + 1);
            }

            try {
                return Component.object(ObjectContents.sprite(Key.key("minecraft", atlas), Key.key("minecraft", path)));
            } catch (RuntimeException var5) {
                return null;
            }
        } else {
            return null;
        }
    }

    public static Component spriteOr(String ref, String fallbackMini) {
        Component c = sprite(ref);
        return c != null ? c : Text.mm(fallbackMini);
    }

    public static Component head(Player p) {
        return !Cfg.bool(Opt.SPRITE_ICONS)
            ? Text.mm("<gray>■")
            : HEADS.computeIfAbsent(p.getUniqueId(), u -> Component.object(ObjectContents.playerHead().skin(p.getPlayerProfile()).hat(true).build()));
    }

    public static void forget(UUID uuid) {
        HEADS.remove(uuid);
    }
}
