package dev.vortexclient.mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/** Screen base for Minecraft 1.21.9 and later (input arrives as records). */
public abstract class ScreenBase extends Screen {
    protected ScreenBase(Component title) {
        super(title);
    }

    @Override
    public void renderBackground(GuiGraphics c, int mouseX, int mouseY, float delta) {
        drawBackdrop(c, mouseX, mouseY, delta);
    }

    protected void drawBackdrop(GuiGraphics c, int mouseX, int mouseY, float delta) {
        super.renderBackground(c, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        return onScroll(mx, my, horizontal, vertical) || super.mouseScrolled(mx, my, horizontal, vertical);
    }

    protected boolean onScroll(double mx, double my, double horizontal, double vertical) {
        return false;
    }

    protected void vanillaBackdrop(GuiGraphics c, int mouseX, int mouseY, float delta) {
        renderPanorama(c, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return onMouseDown(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return onMouseUp(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        return onMouseDrag(click.x(), click.y(), click.button(), dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return onKey(input.key(), input.scancode(), input.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        int cp = input.codepoint();
        return Character.isBmpCodePoint(cp) ? onChar((char) cp, input.modifiers()) : super.charTyped(input);
    }

    protected boolean onMouseDown(double mx, double my, int button) {
        return super.mouseClicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(button, 0)), false);
    }

    protected boolean onMouseUp(double mx, double my, int button) {
        return super.mouseReleased(new MouseButtonEvent(mx, my, new MouseButtonInfo(button, 0)));
    }

    protected boolean onMouseDrag(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(new MouseButtonEvent(mx, my, new MouseButtonInfo(button, 0)), dx, dy);
    }

    protected boolean onKey(int key, int scancode, int modifiers) {
        return super.keyPressed(new KeyEvent(key, scancode, modifiers));
    }

    protected boolean onChar(char chr, int modifiers) {
        return super.charTyped(new CharacterEvent(chr, modifiers));
    }
}
