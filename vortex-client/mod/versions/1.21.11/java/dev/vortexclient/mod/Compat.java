package dev.vortexclient.mod;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;

/**
 * Small game calls that are spelled differently between Minecraft versions
 * (see Gfx for drawing). This one is for 1.21.9 and later; older versions
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

    private static net.minecraft.client.option.KeyBinding.Category category;

    public static net.minecraft.client.option.KeyBinding keyBinding(String id, int key) {
        if (category == null) category = net.minecraft.client.option.KeyBinding.Category.create(net.minecraft.util.Identifier.of("vortex", "main"));
        return new net.minecraft.client.option.KeyBinding(id, net.minecraft.client.util.InputUtil.Type.KEYSYM, key, category);
    }

    public static boolean keyMatches(net.minecraft.client.option.KeyBinding k, int key, int scancode) {
        return k.matchesKey(new net.minecraft.client.input.KeyInput(key, scancode, 0));
    }

    public static boolean isClient(net.minecraft.world.World w) {
        return w.isClient();
    }

    public static net.minecraft.util.math.Vec3d pos(net.minecraft.entity.Entity e) {
        return e.getEntityPos();
    }
}
