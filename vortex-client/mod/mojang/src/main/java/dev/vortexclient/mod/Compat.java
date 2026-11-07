package dev.vortexclient.mod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Small game calls that are spelled differently between Minecraft versions
 * (see Gfx for drawing). This one is for 1.21.9 and later; older versions
 * have their own in versions/<mc>/java.
 */
public final class Compat {
    private Compat() {}

    public static boolean isValidChar(char c) {
        return StringUtil.isAllowedChatCharacter(c);
    }

    public static String packId(Pack p) {
        return p.getId();
    }

    public static Component effectName(MobEffectInstance e) {
        return e.getEffect().value().getDisplayName();
    }

    public static boolean debugShown() {
        return net.minecraft.client.Minecraft.getInstance().getDebugOverlay().showDebugScreen();
    }

    /** The effect's remaining time, at the world's tick rate. */
    public static String durationText(MobEffectInstance e) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        float rate = mc.level != null ? mc.level.tickRateManager().tickrate() : 20f;
        return net.minecraft.world.effect.MobEffectUtil.formatDuration(e, 1.0f, rate).getString();
    }

    public static String gameVersionName() {
        return net.minecraft.SharedConstants.getCurrentVersion().name();
    }

    private static net.minecraft.client.KeyMapping.Category category;

    public static net.minecraft.client.KeyMapping keyBinding(String id, int key) {
        if (category == null) category = net.minecraft.client.KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("vortex", "main"));
        return new net.minecraft.client.KeyMapping(id, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, key, category);
    }

    public static boolean keyMatches(net.minecraft.client.KeyMapping k, int key, int scancode) {
        return k.matches(new net.minecraft.client.input.KeyEvent(key, scancode, 0));
    }

    public static boolean isClient(net.minecraft.world.level.Level w) {
        return w.isClientSide();
    }

    public static net.minecraft.world.phys.Vec3 pos(net.minecraft.world.entity.Entity e) {
        return e.position();
    }
}
