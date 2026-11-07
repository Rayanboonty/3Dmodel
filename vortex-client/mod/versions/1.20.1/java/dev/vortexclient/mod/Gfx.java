package dev.vortexclient.mod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;

/** Drawing calls for Minecraft 1.20 to 1.21.1 (see the shared Gfx). */
public final class Gfx {
    private Gfx() {}

    public static void texture(DrawContext c, Identifier id, int x, int y, int w, int h, int texW, int texH) {
        RenderSystem.enableBlend();
        c.drawTexture(id, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        RenderSystem.disableBlend();
    }

    public static void sprite(DrawContext c, Sprite sprite, int x, int y, int w, int h) {
        c.drawSprite(x, y, 0, w, h, sprite);
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
        c.drawItemInSlot(MinecraftClient.getInstance().textRenderer, st, x, y);
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
