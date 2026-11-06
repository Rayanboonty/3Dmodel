package dev.vortexclient.mod;

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
}
