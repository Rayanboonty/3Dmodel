package dev.vortexclient.mod;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;

/**
 * Small game calls that are spelled differently between Minecraft versions
 * (see Gfx for drawing). This one is for 1.21.6 and later; older versions
 * have their own in versions/<mc>/java.
 */
public final class Compat {
    private Compat() {}

    public static boolean isValidChar(char c) {
        return StringHelper.isValidChar(c);
    }

    public static String packId(ResourcePackProfile p) {
        return p.getId();
    }

    public static Text effectName(StatusEffectInstance e) {
        return e.getEffectType().value().getName();
    }

    public static boolean debugShown() {
        return net.minecraft.client.MinecraftClient.getInstance().getDebugHud().shouldShowDebugHud();
    }

    /** The effect's remaining time, at the world's tick rate. */
    public static String durationText(StatusEffectInstance e) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        float rate = mc.world != null ? mc.world.getTickManager().getTickRate() : 20f;
        return net.minecraft.entity.effect.StatusEffectUtil.getDurationText(e, 1.0f, rate).getString();
    }

    public static String gameVersionName() {
        return net.minecraft.SharedConstants.getGameVersion().name();
    }
}
