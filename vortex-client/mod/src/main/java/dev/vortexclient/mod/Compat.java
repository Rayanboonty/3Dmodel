package dev.vortexclient.mod;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;

/**
 * Small game calls that are spelled differently between Minecraft versions
 * (see Gfx for drawing). This one is for 1.20.5 and later; older versions
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

    /** Potion Effects: green for good effects, red for bad. */
    public static boolean beneficial(StatusEffectInstance e) {
        return e.getEffectType().value().isBeneficial();
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

    /** Hitboxes module (the F3+B boxes). */
    public static void setHitboxes(net.minecraft.client.MinecraftClient mc, boolean on) {
        if (mc.getEntityRenderDispatcher() != null) mc.getEntityRenderDispatcher().setRenderHitboxes(on);
    }
    /** How far an item's cooldown has to go (0 = ready). */
    public static float cooldown(net.minecraft.entity.player.PlayerEntity p, net.minecraft.item.Item item) {
        return p.getItemCooldownManager().getCooldownProgress(new net.minecraft.item.ItemStack(item), 0f);
    }
}
