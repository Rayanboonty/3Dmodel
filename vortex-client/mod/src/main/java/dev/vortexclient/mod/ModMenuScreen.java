package dev.vortexclient.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.util.StringHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** The Vortex mod menu (Right Shift): a grid of cards, each with an options page. */
public class ModMenuScreen extends ScreenBase {
    private interface Action {
        void run(double mx, double my);
    }

    private record Hit(int x, int y, int w, int h, Action action) {}

    private static final int BG = 0xD80C0F15;
    private static final int CARD = 0xC8171B23;
    private static final int CARD_HOT = 0xE0202630;
    private static final int LINE = 0x30FFFFFF;
    private static final int TEXT = 0xFFF2F4F7;
    private static final int DIM = 0xFF8D96A5;
    private static final int CONTROL = 0xFF242A33;
    private static final int ON_BG = 0xFF1C5229;
    private static final int ON_TEXT = 0xFF86E596;

    private static final String[] CATEGORIES = {"All", "HUD", "PvP", "Utility", "Favorites"};

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();
    private String category = "All";
    private String search = "";
    private boolean searchFocused;
    private float scroll, scrollTarget, maxScroll;
    private float optScroll, optScrollTarget, optMax;
    private int listTop, listBottom;
    private Module selected;
    private Module.Option draggingSlider;
    private int sliderX, sliderW;
    private long openedAt;
    private long lastFrame;
    private float dt;
    private String notice;
    private long noticeAt;

    public ModMenuScreen(Screen parent) {
        super(Text.literal("Vortex Mods"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        lastFrame = System.nanoTime();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private float anim(String key, float target, float speed) {
        float v = Ui.approach(anim.getOrDefault(key, target), target, speed, dt);
        anim.put(key, v);
        return v;
    }

    private void hit(int x, int y, int w, int h, Action a) {
        hits.add(new Hit(x, y, w, h, a));
    }

    /** A hit inside the scrolling options list, clipped to what's visible. */
    private void listHit(int x, int y, int w, int h, Action a) {
        int top = Math.max(y, listTop), bottom = Math.min(y + h, listBottom);
        if (bottom > top) hits.add(new Hit(x, top, w, bottom - top, a));
    }

    // ------------------------------------------------------------- render --

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        Hud.tickFrame();
        hits.clear();

        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 220f);
        float ease = 1f - (float) Math.pow(1 - open, 3);
        int lift = Math.round((1 - ease) * 10);
        int accent = VortexMods.accent();

        int pw = Math.min(width - 24, 520);
        int px = (width - pw) / 2;
        boolean inWorld = client != null && client.world != null;

        // The dock: Mods, Edit HUD, Close.
        int dockH = 24, dockY = height - dockH - 6 + lift;
        String[] labels = {"MODS", "EDIT HUD", "CLOSE"};
        int[] bw = new int[labels.length];
        int dockW = 6;
        for (int i = 0; i < labels.length; i++) {
            bw[i] = Ui.width(labels[i]) + 30;
            dockW += bw[i] + 2;
        }
        int dockX = (width - dockW) / 2;
        Ui.glass(c, dockX, dockY, dockW, dockH, 11, 0xD0101319);
        int bx = dockX + 4;
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            boolean on = i == 0;
            boolean hot = Ui.inside(mx, my, bx, dockY + 3, bw[i], dockH - 6);
            if (on) Ui.rounded(c, bx, dockY + 3, bw[i], dockH - 6, 8, accent);
            else if (hot) Ui.rounded(c, bx, dockY + 3, bw[i], dockH - 6, 8, i == 2 ? 0x60FF4A55 : 0x26FFFFFF);
            int fg = on ? 0xFF16110A : (i == 2 && hot ? 0xFFFFD0D3 : TEXT);
            dockIcon(c, i, bx + 9, dockY + 8, fg);
            Ui.text(c, labels[i], bx + 21, dockY + 8, fg);
            hit(bx, dockY + 3, bw[i], dockH - 6, (x, y) -> {
                if (which == 0) selected = null;
                else if (which == 1) {
                    if (inWorld) client.setScreen(new HudEditorScreen(this));
                    else flash("Join a world to move HUD elements");
                } else close();
            });
            bx += bw[i] + 2;
        }

        int py = 8 + lift;
        int ph = dockY - 6 - py;
        Ui.glass(c, px, py, pw, ph, 10, BG);

        if (selected == null) renderGrid(c, mx, my, px, py, pw, ph, accent);
        else renderOptions(c, mx, my, px, py, pw, ph, accent);

        if (notice != null && System.currentTimeMillis() - noticeAt < 2200) {
            int nw = Ui.width(notice) + 20;
            int ny = dockY - 24;
            Ui.glass(c, (width - nw) / 2, ny, nw, 18, 9, 0xF0201820);
            Ui.roundedOutline(c, (width - nw) / 2, ny, nw, 18, 9, Ui.alpha(accent, 0.8f));
            Ui.centered(c, notice, width / 2, ny + 5, TEXT);
        }
    }

    private void flash(String text) {
        notice = text;
        noticeAt = System.currentTimeMillis();
    }

    /** Dock icons: a grid (Mods), arrows (Edit HUD), a cross (Close). */
    private void dockIcon(DrawContext c, int which, int x, int y, int fg) {
        if (which == 0) {
            for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) c.fill(x + i * 4, y + j * 4, x + 3 + i * 4, y + 3 + j * 4, fg);
        } else if (which == 1) {
            c.fill(x + 3, y - 1, x + 4, y + 8, fg);
            c.fill(x - 1, y + 3, x + 8, y + 4, fg);
            c.fill(x + 2, y, x + 5, y + 1, fg);
            c.fill(x + 2, y + 6, x + 5, y + 7, fg);
            c.fill(x, y + 2, x + 1, y + 5, fg);
            c.fill(x + 6, y + 2, x + 7, y + 5, fg);
        } else {
            for (int i = 0; i < 7; i++) {
                c.fill(x + i, y + i, x + i + 1, y + i + 1, fg);
                c.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, fg);
            }
        }
    }

    /** A top-bar pill; returns its right edge. */
    private int pill(DrawContext c, int mx, int my, int x, int y, String label, boolean active, int accent, Runnable onClick) {
        int w = Ui.width(label) + 30, h = 20;
        boolean hot = Ui.inside(mx, my, x, y, w, h);
        boolean on = active;
        int bg = on ? accent : (hot ? 0xE0262C36 : 0xC0161A21);
        Ui.round(c, x, y, w, h, bg);
        if (!on) Ui.outline(c, x, y, w, h, LINE);
        int fg = on ? 0xFF16110A : TEXT;
        // Icon: a 2×2 grid for MODS, arrows for EDIT HUD.
        if (label.equals("MODS")) {
            for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) c.fill(x + 8 + i * 4, y + 6 + j * 4, x + 11 + i * 4, y + 9 + j * 4, fg);
        } else {
            c.fill(x + 11, y + 5, x + 12, y + 15, fg);
            c.fill(x + 7, y + 10, x + 16, y + 11, fg);
            c.fill(x + 10, y + 6, x + 13, y + 7, fg);
            c.fill(x + 10, y + 13, x + 13, y + 14, fg);
            c.fill(x + 8, y + 9, x + 9, y + 12, fg);
            c.fill(x + 14, y + 9, x + 15, y + 12, fg);
        }
        Ui.text(c, label, x + 21, y + 6, fg);
        hit(x, y, w, h, (a, b) -> onClick.run());
        return x + w;
    }

    private List<Module> visible() {
        List<Module> out = new ArrayList<>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        for (Module m : Module.ALL) {
            boolean cat = switch (category) {
                case "HUD" -> m.category == Module.Category.HUD;
                case "PvP" -> m.category == Module.Category.PVP;
                case "Utility" -> m.category == Module.Category.UTILITY;
                case "Favorites" -> VortexMods.config.favorites.contains(m.id);
                default -> true;
            };
            if (cat && (q.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q))) out.add(m);
        }
        return out;
    }

    private void renderGrid(DrawContext c, int mx, int my, int px, int py, int pw, int ph, int accent) {
        int pad = 10;
        // Title, then the category tabs in a segmented track.
        VortexStyle.Art logo = VortexStyle.logoSmall();
        int lh = 16, lw = lh * logo.w() / logo.h();
        Gfx.texture(c, logo.id(), px + pad, py + 4, lw, lh, logo.w(), logo.h());
        int x = px + pad + lw + 12, y = py + 6;
        int trackW = 4;
        for (String cat : CATEGORIES) trackW += Ui.width(cat) + 18;
        Ui.rounded(c, x - 2, y - 2, trackW, 18, 9, 0x50000000);
        for (String cat : CATEGORIES) {
            int w = Ui.width(cat) + 18;
            boolean on = cat.equals(category);
            boolean hot = Ui.inside(mx, my, x, y, w, 14);
            if (on) Ui.rounded(c, x, y, w, 14, 7, accent);
            else if (hot) Ui.rounded(c, x, y, w, 14, 7, 0x22FFFFFF);
            Ui.centered(c, cat, x + w / 2, y + 3, on ? 0xFF16110A : (hot ? TEXT : DIM));
            hit(x, y, w, 14, (a, b) -> {
                category = cat;
                scrollTarget = 0;
            });
            x += w;
        }
        y = py + 6;
        // Search box.
        int sw = Math.min(130, px + pw - pad - x - 8);
        if (sw > 50) {
            int sx = px + pw - pad - sw;
            boolean hot = Ui.inside(mx, my, sx, y, sw, 16);
            Ui.rounded(c, sx, y - 2, sw, 18, 9, hot || searchFocused ? 0x70000000 : 0x50000000);
            if (searchFocused) Ui.roundedOutline(c, sx, y - 2, sw, 18, 9, Ui.alpha(accent, 0.8f));
            // Magnifier.
            Ui.outline(c, sx + 6, y + 4, 6, 6, DIM);
            c.fill(sx + 11, y + 10, sx + 13, y + 12, DIM);
            String shown = search.isEmpty() && !searchFocused ? "Search" : search;
            boolean caret = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
            Ui.text(c, shown + (caret ? "_" : ""), sx + 17, y + 4, search.isEmpty() ? DIM : TEXT);
            hit(sx, y, sw, 16, (a, b) -> searchFocused = true);
        }

        // Cards.
        int gx = px + pad, gy = y + 16 + pad, gw = pw - pad * 2, gh = py + ph - pad - gy;
        int cols = gw >= 430 ? 4 : gw >= 300 ? 3 : 2;
        int gap = 7;
        int cw = (gw - gap * (cols - 1) - 4) / cols, ch = 78;
        List<Module> list = visible();
        int rows = (list.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (ch + gap) - gap - gh);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll = Ui.approach(scroll, scrollTarget, 18f, dt);

        if (list.isEmpty()) {
            Ui.centered(c, category.equals("Favorites") ? "Tap the heart on a card to add it here." : "Nothing matches \"" + search + "\".", gx + gw / 2, gy + 30, DIM);
        }

        c.enableScissor(gx, gy, gx + gw, gy + gh);
        boolean inGrid = Ui.inside(mx, my, gx, gy, gw, gh);
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int cx = gx + (i % cols) * (cw + gap);
            int cy = gy + (i / cols) * (ch + gap) - Math.round(scroll);
            if (cy + ch < gy || cy > gy + gh) continue;
            card(c, mx, my, inGrid, m, cx, cy, cw, ch, accent);
        }
        c.disableScissor();

        if (maxScroll > 0) {
            int track = gh;
            int thumb = Math.max(20, track * gh / (gh + Math.round(maxScroll)));
            int ty = gy + Math.round((track - thumb) * (scroll / maxScroll));
            c.fill(gx + gw - 2, gy, gx + gw, gy + gh, 0x22FFFFFF);
            c.fill(gx + gw - 2, ty, gx + gw, ty + thumb, Ui.alpha(accent, 0.8f));
        }
    }

    private void card(DrawContext c, int mx, int my, boolean inGrid, Module m, int x, int y, int w, int h, int accent) {
        boolean on = VortexMods.config.enabled(m);
        boolean hot = inGrid && Ui.inside(mx, my, x, y, w, h);
        float hv = anim("card:" + m.id, hot ? 1 : 0, 16f);
        float onT = anim("on:" + m.id, on ? 1 : 0, 14f);
        Ui.glass(c, x, y, w, h, 7, Ui.mix(CARD, CARD_HOT, hv));
        if (onT > 0.01f) c.fillGradient(x + 2, y + h / 2, x + w - 2, y + h - 2, 0x00000000, Ui.alpha(accent, 0.16f * onT));
        Ui.roundedOutline(c, x, y, w, h, 7, Ui.mix(LINE, Ui.alpha(accent, 0.75f), Math.max(hv * 0.8f, onT * 0.55f)));
        boolean hasOptions = !m.options.isEmpty();
        // Clicking the card body opens its options; the controls on top win.
        if (inGrid && hasOptions) hit(x, y, w, h - 22, (a, b) -> openOptions(m));

        String name = m.name;
        int maxName = w - 26;
        while (Ui.width(name) > maxName && name.length() > 3) name = name.substring(0, name.length() - 2);
        if (!name.equals(m.name)) name = name + "…";
        Ui.text(c, name, x + 7, y + 7, on ? TEXT : DIM);

        boolean fav = VortexMods.config.favorites.contains(m.id);
        int hx = x + w - 15, hy = y + 5;
        boolean heartHot = hot && Ui.inside(mx, my, hx - 2, hy - 1, 13, 12);
        Ui.round(c, hx - 2, hy - 1, 13, 12, heartHot ? 0xFF2A303A : 0x00000000);
        Ui.text(c, "❤", hx, hy + 1, fav ? 0xFFFF5470 : (heartHot ? 0xFF8890A0 : 0xFF4A5260));
        if (inGrid) hit(hx - 2, hy - 1, 13, 12, (a, b) -> {
            if (fav) VortexMods.config.favorites.remove(m.id);
            else VortexMods.config.favorites.add(m.id);
            VortexMods.config.save();
        });

        // Icon, drawn at 2× and lifted a little on hover.
        int iy = y + 19 - Math.round(hv * 2);
        Gfx.push(c);
        Gfx.translate(c, x + w / 2f - 16, iy);
        Gfx.scale(c, 2f, 2f);
        c.drawItem(new ItemStack(m.icon), 0, 0);
        Gfx.pop(c);

        // Bottom row: options on the left, a switch on the right.
        int by = y + h - 19;
        if (hasOptions) {
            boolean gHot = inGrid && Ui.inside(mx, my, x + 6, by, 16, 14);
            Ui.rounded(c, x + 6, by, 16, 14, 7, gHot ? 0x40FFFFFF : 0x1EFFFFFF);
            gear(c, x + 14, by + 7, gHot ? TEXT : DIM);
            if (inGrid) hit(x + 6, by, 16, 14, (a, b) -> openOptions(m));
        }
        int swW = 24, swH = 13, swX = x + w - 7 - swW, swY = by + 1;
        boolean bHot = inGrid && Ui.inside(mx, my, swX - 26, by - 1, swW + 26, 16);
        Ui.text(c, on ? "ON" : "OFF", swX - Ui.width(on ? "ON" : "OFF") - 5, swY + 3, Ui.mix(DIM, TEXT, onT));
        Ui.toggle(c, swX, swY, swW, swH, onT, accent);
        if (bHot) Ui.roundedOutline(c, swX - 1, swY - 1, swW + 2, swH + 2, 7, 0x40FFFFFF);
        if (inGrid) {
            hit(swX - 26, by - 1, swW + 26, 16, (a, b) -> {
                VortexMods.config.state(m).enabled = !on;
                VortexMods.config.save();
                VortexMods.applyToggles();
            });
        }
    }

    private void gear(DrawContext c, int cx, int cy, int color) {
        // Ring with eight teeth.
        Ui.outline(c, cx - 3, cy - 3, 7, 7, color);
        c.fill(cx - 1, cy - 5, cx + 2, cy - 3, color);
        c.fill(cx - 1, cy + 4, cx + 2, cy + 6, color);
        c.fill(cx - 5, cy - 1, cx - 3, cy + 2, color);
        c.fill(cx + 4, cy - 1, cx + 6, cy + 2, color);
        c.fill(cx - 4, cy - 4, cx - 2, cy - 2, color);
        c.fill(cx + 3, cy - 4, cx + 5, cy - 2, color);
        c.fill(cx - 4, cy + 3, cx - 2, cy + 5, color);
        c.fill(cx + 3, cy + 3, cx + 5, cy + 5, color);
    }

    private void openOptions(Module m) {
        selected = m;
        optScroll = optScrollTarget = 0;
        searchFocused = false;
    }

    // ------------------------------------------------------------ options --

    private void renderOptions(DrawContext c, int mx, int my, int px, int py, int pw, int ph, int accent) {
        Module m = selected;
        int pad = 8;
        int x = px + pad, y = py + pad;
        // Back.
        boolean backHot = Ui.inside(mx, my, x, y, 18, 18);
        Ui.rounded(c, x, y, 18, 18, 9, backHot ? 0x40FFFFFF : 0x1EFFFFFF);
        for (int i = 0; i < 4; i++) {
            c.fill(x + 7 + i, y + 9 - i, x + 8 + i, y + 10 - i, TEXT);
            c.fill(x + 7 + i, y + 9 + i, x + 8 + i, y + 10 + i, TEXT);
        }
        hit(x, y, 18, 18, (a, b) -> selected = null);
        Ui.text(c, m.name, x + 26, y + 1, TEXT);
        String desc = m.description;
        int maxDesc = pw - pad * 2 - 26 - 60;
        while (Ui.width(desc) > maxDesc && desc.length() > 4) desc = desc.substring(0, desc.length() - 2);
        if (!desc.equals(m.description)) desc += "…";
        Ui.text(c, desc, x + 26, y + 11, DIM);

        int rw = Ui.width("Reset") + 16;
        int rx = px + pw - pad - rw;
        boolean resetHot = Ui.inside(mx, my, rx, y + 1, rw, 16);
        Ui.rounded(c, rx, y + 1, rw, 16, 8, resetHot ? 0x40FFFFFF : 0x1EFFFFFF);
        Ui.centered(c, "Reset", rx + rw / 2, y + 5, resetHot ? TEXT : DIM);
        hit(rx, y + 1, rw, 16, (a, b) -> {
            VortexMods.config.reset(m);
            VortexMods.config.save();
            VortexMods.applyToggles();
        });

        int top = y + 26;
        c.fill(px + pad, top - 4, px + pw - pad, top - 3, LINE);
        boolean preview = m.hud && Hud.has(m);
        int listW = preview ? (pw - pad * 3) * 52 / 100 : pw - pad * 2;
        int lx = px + pad, right = lx + listW;

        listTop = top;
        listBottom = py + ph - pad;
        optMax = Math.max(0, (m.options.size() + 1) * 19 + 2 - (listBottom - listTop));
        optScrollTarget = Math.max(0, Math.min(optMax, optScrollTarget));
        optScroll = Ui.approach(optScroll, optScrollTarget, 18f, dt);
        c.enableScissor(lx - 3, listTop, right + 3, listBottom);
        int rowY = top + 2 - Math.round(optScroll);
        rowY = toggleRow(c, mx, my, lx, right, rowY, "Enabled", VortexMods.config.enabled(m), "enabled:" + m.id, accent, v -> {
            VortexMods.config.state(m).enabled = v;
            VortexMods.applyToggles();
        });
        for (Module.Option o : m.options) {
            switch (o.type()) {
                case BOOL -> rowY = toggleRow(c, mx, my, lx, right, rowY, o.label(), VortexMods.config.bool(m, o.key()), m.id + ":" + o.key(), accent,
                        v -> VortexMods.config.set(m, o.key(), v));
                case CHOICE -> rowY = choiceRow(c, mx, my, lx, right, rowY, m, o, accent);
                case SLIDER -> rowY = sliderRow(c, mx, my, lx, right, rowY, m, o, accent);
            }
        }
        c.disableScissor();
        if (optMax > 0) {
            int track = listBottom - listTop;
            int thumb = Math.max(16, track * track / (track + Math.round(optMax)));
            int ty = listTop + Math.round((track - thumb) * (optScroll / optMax));
            c.fill(right + 4, listTop, right + 6, listBottom, 0x22FFFFFF);
            c.fill(right + 4, ty, right + 6, ty + thumb, Ui.alpha(accent, 0.8f));
        }

        if (preview) {
            int bx = right + pad, bw = px + pw - pad - bx, by = top, bh = py + ph - pad - top;
            // A little sky-and-grass backdrop, like a screenshot of the game.
            c.fillGradient(bx, by, bx + bw, by + bh, 0xFF7FA6D9, 0xFFB9D1EA);
            int ground = by + bh * 2 / 3;
            c.fill(bx, ground, bx + bw, ground + 3, 0xFF6DAA3C);
            c.fill(bx, ground + 3, bx + bw, by + bh, 0xFF7A5537);
            for (int i = 0; i < bw; i += 6) c.fill(bx + i, ground + 3 + (i * 7 % 5), bx + i + 2, ground + 5 + (i * 7 % 5), 0xFF6A472D);
            Ui.roundedOutline(c, bx, by, bw, bh, 4, LINE);
            int[] s = Hud.size(m);
            float scale = (float) VortexMods.config.num(m, "scale");
            float fit = Math.min(scale, Math.min((bw - 16f) / Math.max(1, s[0]), (bh - 16f) / Math.max(1, s[1])));
            int ew = Math.round(s[0] * fit), eh = Math.round(s[1] * fit);
            c.enableScissor(bx, by, bx + bw, by + bh);
            Hud.drawAt(c, m, bx + (bw - ew) / 2, by + (bh - eh) / 2, fit);
            c.disableScissor();
            Ui.round(c, bx + 4, by + 4, Ui.width("Preview") + 8, 11, 0xA0000000);
            Ui.text(c, "Preview", bx + 8, by + 6, 0xFFFFFFFF);
        }
    }

    private int toggleRow(DrawContext c, int mx, int my, int x, int right, int y, String label, boolean value, String key, int accent, java.util.function.Consumer<Boolean> set) {
        boolean hot = Ui.inside(mx, my, x, y, right - x, 18) && my >= listTop && my < listBottom;
        if (hot) Ui.rounded(c, x - 2, y, right - x + 4, 18, 6, 0x12FFFFFF);
        Ui.text(c, label, x + 2, y + 5, TEXT);
        float t = anim("t:" + key, value ? 1 : 0, 16f);
        Ui.toggle(c, right - 26, y + 3, 24, 13, t, accent);
        listHit(x, y, right - x, 18, (a, b) -> {
            set.accept(!value);
            VortexMods.config.save();
        });
        return y + 19;
    }

    private int choiceRow(DrawContext c, int mx, int my, int x, int right, int y, Module m, Module.Option o, int accent) {
        Ui.text(c, o.label(), x + 2, y + 5, TEXT);
        String current = VortexMods.config.str(m, o.key());
        int total = 0;
        for (String ch : o.choices()) total += Ui.width(ch) + 13;
        int room = right - 2 - (x + 2 + Ui.width(o.label()) + 10);
        if (total > room) {
            // Not enough room for every choice: one pill that cycles.
            int w = 0;
            for (String ch : o.choices()) w = Math.max(w, Ui.width(ch));
            w += 28;
            final int cw = w;
            final int cx = right - 2 - w;
            boolean hot = Ui.inside(mx, my, cx, y + 2, w, 14) && my >= listTop && my < listBottom;
            Ui.rounded(c, cx, y + 2, w, 14, 7, hot ? 0x40FFFFFF : 0x1EFFFFFF);
            Ui.text(c, "<", cx + 5, y + 5, DIM);
            Ui.text(c, ">", cx + w - 10, y + 5, DIM);
            Ui.centered(c, current, cx + w / 2, y + 5, accent);
            listHit(cx, y + 2, w, 14, (a, b) -> {
                int i = o.choices().indexOf(current);
                int n = o.choices().size();
                int next = a < cx + cw / 2.0 ? (i - 1 + n) % n : (i + 1) % n;
                VortexMods.config.set(m, o.key(), o.choices().get(next));
                VortexMods.config.save();
            });
            return y + 19;
        }
        int cx = right - 2;
        for (int i = o.choices().size() - 1; i >= 0; i--) {
            String ch = o.choices().get(i);
            int w = Ui.width(ch) + 10;
            cx -= w;
            boolean on = ch.equals(current);
            boolean hot = Ui.inside(mx, my, cx, y + 2, w, 14) && my >= listTop && my < listBottom;
            Ui.rounded(c, cx, y + 2, w, 14, 7, on ? accent : (hot ? 0x40FFFFFF : 0x1EFFFFFF));
            Ui.centered(c, ch, cx + w / 2, y + 5, on ? 0xFF16110A : (hot ? TEXT : DIM));
            listHit(cx, y + 2, w, 14, (a, b) -> {
                VortexMods.config.set(m, o.key(), ch);
                VortexMods.config.save();
            });
            cx -= 3;
        }
        return y + 19;
    }

    private int sliderRow(DrawContext c, int mx, int my, int x, int right, int y, Module m, Module.Option o, int accent) {
        Ui.text(c, o.label(), x + 2, y + 5, TEXT);
        double v = VortexMods.config.num(m, o.key());
        int tw = 84, tx = right - tw - 4, ty = y + 9;
        String label = o.step() < 1 ? String.format(Locale.ROOT, "%.1f", v) : String.valueOf(Math.round(v));
        if (o.key().equals("level")) label += "×";
        int vw = Math.max(22, Ui.width(label) + 8);
        Ui.rounded(c, tx - vw - 8, y + 3, vw, 12, 6, 0x1EFFFFFF);
        Ui.centered(c, label, tx - vw - 8 + vw / 2, y + 5, TEXT);
        float f = (float) ((v - o.min()) / (o.max() - o.min()));
        Ui.rounded(c, tx, ty - 1, tw, 4, 2, 0xFF39404B);
        Ui.rounded(c, tx, ty - 1, Math.max(4, Math.round(f * tw)), 4, 2, accent);
        int kx = tx + Math.round(f * tw) - 4;
        boolean hot = (Ui.inside(mx, my, tx - 4, y, tw + 8, 18) && my >= listTop && my < listBottom) || draggingSlider == o;
        Ui.rounded(c, kx, ty - 4, 9, 9, 4, 0xFFFFFFFF);
        if (hot) Ui.roundedOutline(c, kx - 1, ty - 5, 11, 11, 5, Ui.alpha(accent, 0.9f));
        listHit(tx - 4, y, tw + 8, 18, (a, b) -> {
            draggingSlider = o;
            sliderX = tx;
            sliderW = tw;
            setSlider(a);
        });
        return y + 19;
    }

    private void setSlider(double mx) {
        Module.Option o = draggingSlider;
        if (o == null || selected == null) return;
        double f = Math.max(0, Math.min(1, (mx - sliderX) / sliderW));
        double v = o.min() + f * (o.max() - o.min());
        v = Math.round(v / o.step()) * o.step();
        VortexMods.config.set(selected, o.key(), Math.round(v * 100) / 100.0);
    }

    // -------------------------------------------------------------- input --

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (button != 0) return super.onMouseDown(mx, my, button);
        boolean wasFocused = searchFocused;
        searchFocused = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Ui.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                h.action().run(mx, my);
                return true;
            }
        }
        if (wasFocused) return true;
        return super.onMouseDown(mx, my, button);
    }

    @Override
    protected boolean onMouseDrag(double mx, double my, int button, double dx, double dy) {
        if (draggingSlider != null) {
            setSlider(mx);
            return true;
        }
        return super.onMouseDrag(mx, my, button, dx, dy);
    }

    @Override
    protected boolean onMouseUp(double mx, double my, int button) {
        if (draggingSlider != null) {
            draggingSlider = null;
            VortexMods.config.save();
            return true;
        }
        return super.onMouseUp(mx, my, button);
    }

    @Override
    protected boolean onScroll(double mx, double my, double horizontal, double vertical) {
        if (selected != null) optScrollTarget -= (float) vertical * 24f;
        else scrollTarget -= (float) vertical * 30f;
        return true;
    }

    @Override
    protected boolean onKey(int key, int scan, int mods) {
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                scrollTarget = 0;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && selected != null) {
            selected = null;
            return true;
        }
        if (VortexMods.menuKey != null && Compat.keyMatches(VortexMods.menuKey, key, scan) && !searchFocused) {
            close();
            return true;
        }
        return super.onKey(key, scan, mods);
    }

    @Override
    protected boolean onChar(char chr, int mods) {
        if (selected == null && Compat.isValidChar(chr) && search.length() < 24) {
            searchFocused = true;
            search += chr;
            scrollTarget = 0;
            return true;
        }
        return super.onChar(chr, mods);
    }

    @Override
    public void close() {
        VortexMods.config.save();
        if (client != null) client.setScreen(parent);
    }
}
