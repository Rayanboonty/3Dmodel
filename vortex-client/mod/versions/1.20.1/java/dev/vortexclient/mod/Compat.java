package dev.vortexclient.mod;

import net.minecraft.SharedConstants;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;

/** Compat for Minecraft 1.20 to 1.20.4 (see the shared Compat). */
public final class Compat {
    private Compat() {}

    public static boolean isValidChar(char c) {
        return SharedConstants.isValidChar(c);
    }

    public static String packId(ResourcePackProfile p) {
        return p.getName();
    }

    /** Potion Effects: green for good effects, red for bad. */
    public static boolean beneficial(StatusEffectInstance e) {
        return e.getEffectType().isBeneficial();
    }

    public static Text effectName(StatusEffectInstance e) {
        return e.getEffectType().getName();
    }

    public static boolean debugShown() {
        return net.minecraft.client.MinecraftClient.getInstance().options.debugEnabled;
    }

    public static String durationText(StatusEffectInstance e) {
        return net.minecraft.entity.effect.StatusEffectUtil.getDurationText(e, 1.0f).getString();
    }

    public static String gameVersionName() {
        return net.minecraft.SharedConstants.getGameVersion().getName();
    }

    public static net.minecraft.client.option.KeyBinding keyBinding(String id, int key) {
        return new net.minecraft.client.option.KeyBinding(id, net.minecraft.client.util.InputUtil.Type.KEYSYM, key, "category.vortex");
    }

    public static boolean keyMatches(net.minecraft.client.option.KeyBinding k, int key, int scancode) {
        return k.matchesKey(key, scancode);
    }

    public static boolean isClient(net.minecraft.world.World w) {
        return w.isClient;
    }

    public static net.minecraft.util.math.Vec3d pos(net.minecraft.entity.Entity e) {
        return e.getPos();
    }

    /** A Tab list entry's player id. */
    public static java.util.UUID profileId(net.minecraft.client.network.PlayerListEntry e) {
        return e.getProfile().getId();
    }

    /** The Fast / Fancy / Fabulous option (FPS Boost), or null where the game has none. */
    public static net.minecraft.client.option.SimpleOption<?> graphicsOption(net.minecraft.client.option.GameOptions o) {
        return o.getGraphicsMode();
    }

    /** The value of that option that draws fastest. */
    public static final String GRAPHICS_FAST = "FAST";
}
