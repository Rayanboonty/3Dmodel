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
