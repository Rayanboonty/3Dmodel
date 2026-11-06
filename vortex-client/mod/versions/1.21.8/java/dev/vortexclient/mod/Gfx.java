package dev.vortexclient.mod;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;

/** Drawing calls for Minecraft 1.21.6 and later (see the shared Gfx). */
public final class Gfx {
    private Gfx() {}

    public static void texture(DrawContext c, Identifier id, int x, int y, int w, int h, int texW, int texH) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH);
    }

    public static void sprite(DrawContext c, Sprite sprite, int x, int y, int w, int h) {
        c.drawSpriteStretched(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
    }

    public static void push(DrawContext c) {
        c.getMatrices().pushMatrix();
    }

    public static void pop(DrawContext c) {
        c.getMatrices().popMatrix();
    }

    public static void translate(DrawContext c, float x, float y) {
        c.getMatrices().translate(x, y);
    }

    public static void scale(DrawContext c, float x, float y) {
        c.getMatrices().scale(x, y);
    }

    public static NativeImageBackedTexture newTexture(NativeImage img) {
        return new NativeImageBackedTexture(() -> "vortex", img);
    }

    public static void effectIcon(DrawContext c, StatusEffectInstance e, int x, int y, int size) {
        c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(e.getEffectType()), x, y, size, size);
    }

    /** A button's own label, centred, in the given colour. */
    public static void buttonLabel(DrawContext c, net.minecraft.client.gui.widget.PressableWidget w, int color) {
        w.drawMessage(c, net.minecraft.client.MinecraftClient.getInstance().textRenderer, color);
    }
}
