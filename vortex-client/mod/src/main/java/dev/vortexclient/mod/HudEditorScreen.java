package dev.vortexclient.mod;

import java.util.Map;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Drag HUD elements around. They snap to the centre lines and edges. */
public class HudEditorScreen extends ScreenBase {
    private final Screen parent;
    private Module dragging;
    private double grabX, grabY;
    private boolean snapX, snapY;
    private static final int SNAP = 4;

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
        // Centre guides.
        c.fill(width / 2, 0, width / 2 + 1, height, snapX && dragging != null ? Ui.alpha(accent, 0.9f) : 0x22FFFFFF);
        c.fill(0, height / 2, width, height / 2 + 1, snapY && dragging != null ? Ui.alpha(accent, 0.9f) : 0x22FFFFFF);

        Map<Module, Hud.Rect> layout = Hud.layout(width, height);
        for (Map.Entry<Module, Hud.Rect> e : layout.entrySet()) {
            Hud.Rect r = e.getValue();
            boolean hot = e.getKey() == dragging || (dragging == null && r.contains(mouseX, mouseY));
            Hud.drawAt(c, e.getKey(), r.x(), r.y(), r.scale());
            Ui.outline(c, r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, hot ? accent : 0x66FFFFFF);
            if (hot) {
                String n = e.getKey().name;
                int ty = r.y() > 12 ? r.y() - 11 : r.y() + r.h() + 3;
                Ui.round(c, r.x() - 1, ty - 1, Ui.width(n) + 6, 10, 0xD0101318);
                Ui.text(c, n, r.x() + 2, ty, 0xFFFFFFFF);
            }
        }

        String hint = "Drag to move.  Right click resets one.  Esc to finish.";
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
    public void close() {
        VortexMods.config.save();
        if (client != null) client.setScreen(parent);
    }
}
