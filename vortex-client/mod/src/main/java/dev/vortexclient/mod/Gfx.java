package dev.vortexclient.mod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;

/**
 * Drawing calls whose signatures change between Minecraft versions. The
 * shared code uses these; versions/<mc>/java has a Gfx for each version
 * that draws differently. This one is for 1.21.2 to 1.21.5.
 */
public final class Gfx {
    private Gfx() {}

    /** A whole texture of texW x texH stretched over x, y, w, h. */
    public static void texture(DrawContext c, Identifier id, int x, int y, int w, int h, int texW, int texH) {
        c.drawTexture(RenderLayer::getGuiTextured, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH);
    }

    /** A sprite (like a status effect icon) stretched over x, y, w, h. */
    public static void sprite(DrawContext c, Sprite sprite, int x, int y, int w, int h) {
        c.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, x, y, w, h);
    }

    public static void push(DrawContext c) {
        c.getMatrices().push();
    }

    public static void pop(DrawContext c) {
        c.getMatrices().pop();
    }

    public static void translate(DrawContext c, float x, float y) {
        c.getMatrices().translate(x, y, 0);
    }

    public static void scale(DrawContext c, float x, float y) {
        c.getMatrices().scale(x, y, 1f);
    }

    public static NativeImageBackedTexture newTexture(NativeImage img) {
        return new NativeImageBackedTexture(img);
    }

    /** A status effect's icon, size x size. */
    /** The count and durability bar Minecraft draws over an item in a slot. */
    public static void itemOverlay(DrawContext c, net.minecraft.item.ItemStack st, int x, int y) {
        c.drawStackOverlay(MinecraftClient.getInstance().textRenderer, st, x, y);
    }

    public static void effectIcon(DrawContext c, StatusEffectInstance e, int x, int y, int size) {
        Sprite sprite = MinecraftClient.getInstance().getStatusEffectSpriteManager().getSprite(e.getEffectType());
        sprite(c, sprite, x, y, size, size);
    }

    /** A button's own label, centred, in the given colour. */
    public static void buttonLabel(DrawContext c, net.minecraft.client.gui.widget.PressableWidget w, int color) {
        w.drawMessage(c, net.minecraft.client.MinecraftClient.getInstance().textRenderer, color);
    }
}
