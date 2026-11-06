package dev.vortexclient.mod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Screen base for Minecraft 1.20.2 to 1.20.4. Screen with the version-specific bits Vortex screens need: they draw
 * their background in drawBackdrop and take the mouse wheel in onScroll,
 * and this class hooks those into the version's own Screen methods.
 */
public abstract class ScreenBase extends Screen {
    protected ScreenBase(Text title) {
        super(title);
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        drawBackdrop(c, mouseX, mouseY, delta);
    }

    /** The screen's background; Minecraft's own unless overridden. */
    protected void drawBackdrop(DrawContext c, int mouseX, int mouseY, float delta) {
        super.renderBackground(c, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        return onScroll(mx, my, horizontal, vertical) || super.mouseScrolled(mx, my, horizontal, vertical);
    }

    /** The mouse wheel; true when handled. */
    protected boolean onScroll(double mx, double my, double horizontal, double vertical) {
        return false;
    }

    /** Minecraft's own menu backdrop (the panorama), for when there's no picture. */
    protected void vanillaBackdrop(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fillGradient(0, 0, width, height, 0xFF20304A, 0xFF101018);
    }
}
