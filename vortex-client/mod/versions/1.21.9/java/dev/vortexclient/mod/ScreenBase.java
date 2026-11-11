package dev.vortexclient.mod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;

/** Screen base for Minecraft 1.21.9 and later (input arrives as records). */
public abstract class ScreenBase extends Screen {
    protected ScreenBase(Text title) {
        super(title);
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        drawBackdrop(c, mouseX, mouseY, delta);
    }

    protected void drawBackdrop(DrawContext c, int mouseX, int mouseY, float delta) {
        super.renderBackground(c, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        return onScroll(mx, my, horizontal, vertical) || super.mouseScrolled(mx, my, horizontal, vertical);
    }

    protected boolean onScroll(double mx, double my, double horizontal, double vertical) {
        return false;
    }

    protected void vanillaBackdrop(DrawContext c, int mouseX, int mouseY, float delta) {
        renderPanoramaBackground(c, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return onMouseDown(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(Click click) {
        return onMouseUp(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        return onMouseDrag(click.x(), click.y(), click.button(), dx, dy);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return onKey(input.key(), input.scancode(), input.modifiers());
    }

    @Override
    public boolean charTyped(CharInput input) {
        int cp = input.codepoint();
        return Character.isBmpCodePoint(cp) ? onChar((char) cp, input.modifiers()) : super.charTyped(input);
    }

    protected boolean onMouseDown(double mx, double my, int button) {
        return super.mouseClicked(new Click(mx, my, new MouseInput(button, 0)), false);
    }

    protected boolean onMouseUp(double mx, double my, int button) {
        return super.mouseReleased(new Click(mx, my, new MouseInput(button, 0)));
    }

    protected boolean onMouseDrag(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(new Click(mx, my, new MouseInput(button, 0)), dx, dy);
    }

    protected boolean onKey(int key, int scancode, int modifiers) {
        return super.keyPressed(new KeyInput(key, scancode, modifiers));
    }

    protected boolean onChar(char chr, int modifiers) {
        return super.charTyped(new CharInput(chr, modifiers));
    }
}
