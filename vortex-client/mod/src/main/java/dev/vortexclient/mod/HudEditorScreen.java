package dev.vortexclient.mod;

import java.util.Map;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Drag HUD elements around. They snap to the centre lines and edges. */
public class HudEditorScreen extends ScreenBase {
    private final Screen parent;
    private Module dragging;
    /** The element clicked last: arrow keys move it, Delete resets it. */
    private Module selected;
    private double grabX, grabY;
    private boolean snapX, snapY;
    private static boolean grid;
    private static final int SNAP = 4, GRID = 10;
    /** Edges of other elements the dragged one lined up with (for guides). */
    private int guideX = -1, guideY = -1;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void drawBackdrop(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0x30000000);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        Hud.tickFrame();
        int accent = VortexMods.accent();
        if (grid) {
            for (int gx = 0; gx < width; gx += GRID) c.fill(gx, 0, gx + 1, height, 0x14FFFFFF);
            for (int gy = 0; gy < height; gy += GRID) c.fill(0, gy, width, gy + 1, 0x14FFFFFF);
        }
        // Centre guides.
        c.fill(width / 2, 0, width / 2 + 1, height, snapX && dragging != null ? Ui.alpha(accent, 0.9f) : 0x22FFFFFF);
        c.fill(0, height / 2, width, height / 2 + 1, snapY && dragging != null ? Ui.alpha(accent, 0.9f) : 0x22FFFFFF);
        if (dragging != null && guideX >= 0) c.fill(guideX, 0, guideX + 1, height, Ui.alpha(accent, 0.6f));
        if (dragging != null && guideY >= 0) c.fill(0, guideY, width, guideY + 1, Ui.alpha(accent, 0.6f));

        Map<Module, Hud.Rect> layout = Hud.layout(width, height);
        for (Map.Entry<Module, Hud.Rect> e : layout.entrySet()) {
            Hud.Rect r = e.getValue();
            boolean hot = e.getKey() == dragging || (dragging == null && r.contains(mouseX, mouseY));
            Hud.drawAt(c, e.getKey(), r.x(), r.y(), r.scale());
            Ui.outline(c, r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, hot || e.getKey() == selected ? accent : 0x66FFFFFF);
            if (hot) {
                String n = e.getKey().name + "  " + r.x() + ", " + r.y() + "  " + Math.round(VortexMods.config.num(e.getKey(), "scale") * 100) + "%";
                int ty = r.y() > 12 ? r.y() - 11 : r.y() + r.h() + 3;
                Ui.round(c, r.x() - 1, ty - 1, Ui.width(n) + 6, 10, 0xD0101318);
                Ui.text(c, n, r.x() + 2, ty, 0xFFFFFFFF);
            }
        }

        String hint = "Drag to move.  Scroll to resize.  Arrows nudge.  G grid" + (grid ? " (on)" : "") + ".  R resets all.  Esc to finish.";
        int hw = Ui.width(hint) + 16;
        Ui.round(c, (width - hw) / 2, height - 26, hw, 16, 0xD0101318);
        Ui.centered(c, hint, width / 2, height - 22, 0xFFCFD5DE);
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        for (Map.Entry<Module, Hud.Rect> e : Hud.layout(width, height).entrySet()) {
            Hud.Rect r = e.getValue();
            if (!r.contains(mx, my)) continue;
            if (button == 1) {
                Config.ModState s = VortexMods.config.state(e.getKey());
                s.x = -1;
                s.y = -1;
                VortexMods.config.save();
                return true;
            }
            dragging = e.getKey();
            selected = e.getKey();
            grabX = mx - r.x();
            grabY = my - r.y();
            return true;
        }
        return super.onMouseDown(mx, my, button);
    }

    @Override
    protected boolean onMouseDrag(double mx, double my, int button, double dx, double dy) {
        if (dragging == null) return false;
        Hud.Rect r = Hud.layout(width, height).get(dragging);
        if (r == null) return false;
        double x = mx - grabX, y = my - grabY;
        double cx = x + r.w() / 2.0, cy = y + r.h() / 2.0;
        snapX = Math.abs(cx - width / 2.0) < SNAP;
        snapY = Math.abs(cy - height / 2.0) < SNAP;
        if (snapX) cx = width / 2.0;
        if (snapY) cy = height / 2.0;
        // Line up with the edges of other elements.
        guideX = -1;
        guideY = -1;
        for (Map.Entry<Module, Hud.Rect> o : Hud.layout(width, height).entrySet()) {
            if (o.getKey() == dragging) continue;
            Hud.Rect q = o.getValue();
            double left = cx - r.w() / 2.0, top = cy - r.h() / 2.0;
            if (Math.abs(left - q.x()) < SNAP) { cx = q.x() + r.w() / 2.0; guideX = q.x(); }
            else if (Math.abs(left + r.w() - (q.x() + q.w())) < SNAP) { cx = q.x() + q.w() - r.w() / 2.0; guideX = q.x() + q.w(); }
            if (Math.abs(top - (q.y() + q.h() + 3)) < SNAP) { cy = q.y() + q.h() + 3 + r.h() / 2.0; guideY = q.y() + q.h() + 3; }
            else if (Math.abs(top - q.y()) < SNAP) { cy = q.y() + r.h() / 2.0; guideY = q.y(); }
        }
        if (grid) {
            cx = Math.round((cx - r.w() / 2.0) / GRID) * GRID + r.w() / 2.0;
            cy = Math.round((cy - r.h() / 2.0) / GRID) * GRID + r.h() / 2.0;
        }
        // Edges snap to a 2px margin.
        if (Math.abs(x - 2) < SNAP) cx = 2 + r.w() / 2.0;
        if (Math.abs(x + r.w() - (width - 2)) < SNAP) cx = width - 2 - r.w() / 2.0;
        if (Math.abs(y - 2) < SNAP) cy = 2 + r.h() / 2.0;
        if (Math.abs(y + r.h() - (height - 2)) < SNAP) cy = height - 2 - r.h() / 2.0;
        Config.ModState s = VortexMods.config.state(dragging);
        s.x = Math.max(0, Math.min(1, cx / width));
        s.y = Math.max(0, Math.min(1, cy / height));
        return true;
    }

    @Override
    protected boolean onMouseUp(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            VortexMods.config.save();
            return true;
        }
        return super.onMouseUp(mx, my, button);
    }

    @Override
    protected boolean onScroll(double mx, double my, double horizontal, double vertical) {
        for (Map.Entry<Module, Hud.Rect> e : Hud.layout(width, height).entrySet()) {
            if (!e.getValue().contains(mx, my)) continue;
            Module m = e.getKey();
            double sc = VortexMods.config.num(m, "scale") + (vertical > 0 ? 0.1 : -0.1);
            VortexMods.config.set(m, "scale", Math.round(Math.max(0.5, Math.min(2.0, sc)) * 10) / 10.0);
            VortexMods.config.save();
            return true;
        }
        return super.onScroll(mx, my, horizontal, vertical);
    }

    @Override
    protected boolean onKey(int key, int scancode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_G) {
            grid = !grid;
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            for (Module m : Module.ALL) {
                Config.ModState s = VortexMods.config.state(m);
                s.x = -1;
                s.y = -1;
            }
            VortexMods.config.save();
            return true;
        }
        if (selected != null) {
            int step = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            int dx = key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT ? -step : key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT ? step : 0;
            int dy = key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -step : key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? step : 0;
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                Config.ModState s = VortexMods.config.state(selected);
                s.x = -1;
                s.y = -1;
                VortexMods.config.save();
                return true;
            }
            if (dx != 0 || dy != 0) {
                Hud.Rect r = Hud.layout(width, height).get(selected);
                if (r != null) {
                    Config.ModState s = VortexMods.config.state(selected);
                    double cx = r.x() + r.w() / 2.0 + dx, cy = r.y() + r.h() / 2.0 + dy;
                    s.x = Math.max(0, Math.min(1, cx / width));
                    s.y = Math.max(0, Math.min(1, cy / height));
                    VortexMods.config.save();
                }
                return true;
            }
        }
        return super.onKey(key, scancode, modifiers);
    }

    @Override
    public void close() {
        VortexMods.config.save();
        if (client != null) client.setScreen(parent);
    }
}
