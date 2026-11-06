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

    public static Text effectName(StatusEffectInstance e) {
        return e.getEffectType().getName();
    }

    public static boolean debugShown() {
        return net.minecraft.client.MinecraftClient.getInstance().options.debugEnabled;
    }

    public static String durationText(StatusEffectInstance e) {
        return net.minecraft.entity.effect.StatusEffectUtil.getDurationText(e, 1.0f).getString();
    }
}
