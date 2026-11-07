package dev.vortexclient.mod;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;

/** Drawing calls for Minecraft 1.21.9 and later (see the shared Gfx). */
public final class Gfx {
    private Gfx() {}

    public static void texture(GuiGraphics c, Identifier id, int x, int y, int w, int h, int texW, int texH) {
        c.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH);
    }

    public static void sprite(GuiGraphics c, TextureAtlasSprite sprite, int x, int y, int w, int h) {
        c.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
    }

    public static void push(GuiGraphics c) {
        c.pose().pushMatrix();
    }

    public static void pop(GuiGraphics c) {
        c.pose().popMatrix();
    }

    public static void translate(GuiGraphics c, float x, float y) {
        c.pose().translate(x, y);
    }

    public static void scale(GuiGraphics c, float x, float y) {
        c.pose().scale(x, y);
    }

    public static DynamicTexture newTexture(NativeImage img) {
        return new DynamicTexture(() -> "vortex", img);
    }

    public static void effectIcon(GuiGraphics c, MobEffectInstance e, int x, int y, int size) {
        c.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(e.getEffect()), x, y, size, size);
    }

    public static void buttonLabel(GuiGraphics c, net.minecraft.client.gui.components.AbstractButton w, int color) {
        var tr = net.minecraft.client.Minecraft.getInstance().font;
        c.drawCenteredString(tr, w.getMessage(), w.getX() + w.getWidth() / 2, w.getY() + (w.getHeight() - 8) / 2, color);
    }
}
