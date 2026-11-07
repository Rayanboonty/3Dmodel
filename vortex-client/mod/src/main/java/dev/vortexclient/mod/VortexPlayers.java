package dev.vortexclient.mod;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Who in this world plays with Vortex (from the server), and the badge itself. */
public final class VortexPlayers {
    private VortexPlayers() {}

    /** The VC logo as a font glyph (assets/minecraft/font/default.json). */
    public static final String GLYPH = "";

    private static final Set<UUID> OTHERS = ConcurrentHashMap.newKeySet();

    public static void set(Collection<UUID> ids) {
        OTHERS.clear();
        OTHERS.addAll(ids);
        VortexMods.LOG.info("Vortex players in this world: {}", ids.size());
    }

    public static void clear() {
        OTHERS.clear();
    }

    public static int count() {
        return OTHERS.size();
    }

    public static boolean isVortex(UUID id) {
        if (id == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = Module.get("badge");
        boolean self = mc.player != null && id.equals(mc.player.getUuid());
        if (self) return VortexMods.config.bool(m, "self");
        return OTHERS.contains(id);
    }

    /** The name with the VC badge in front. */
    public static Text badge(Text name) {
        MutableText out = Text.literal(GLYPH + " ");
        return out.append(name);
    }
}
