package dev.vortexclient.mod;

import com.mojang.blaze3d.systems.RenderSystem;
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
}
