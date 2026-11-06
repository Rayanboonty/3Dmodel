package dev.vortexclient.mod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Screen base for Minecraft 1.20.1: screens draw their own background
 * before their widgets, and the mouse wheel has one axis.
 */
public abstract class ScreenBase extends Screen {
    protected ScreenBase(Text title) {
        super(title);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        drawBackdrop(c, mouseX, mouseY, delta);
        super.render(c, mouseX, mouseY, delta);
    }

    protected void drawBackdrop(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return onScroll(mx, my, 0, amount) || super.mouseScrolled(mx, my, amount);
    }

    protected boolean onScroll(double mx, double my, double horizontal, double vertical) {
        return false;
    }

    protected void vanillaBackdrop(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fillGradient(0, 0, width, height, 0xFF20304A, 0xFF101018);
    }
}
