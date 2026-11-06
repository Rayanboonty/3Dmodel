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

    // Input arrives here in every version's shape and goes on to these.
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return onMouseDown(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return onMouseUp(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return onMouseDrag(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return onKey(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return onChar(chr, modifiers);
    }

    protected boolean onMouseDown(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button);
    }

    protected boolean onMouseUp(double mx, double my, int button) {
        return super.mouseReleased(mx, my, button);
    }

    protected boolean onMouseDrag(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    protected boolean onKey(int key, int scancode, int modifiers) {
        return super.keyPressed(key, scancode, modifiers);
    }

    protected boolean onChar(char chr, int modifiers) {
        return super.charTyped(chr, modifiers);
    }
}
