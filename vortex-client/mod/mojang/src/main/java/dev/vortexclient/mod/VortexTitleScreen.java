package dev.vortexclient.mod;

import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Vortex's main menu: the launcher's background and logo, with glass buttons. */
public class VortexTitleScreen extends ScreenBase {
    private long openedAt;
    private int logoBottom;

    public VortexTitleScreen() {
        super(Component.translatable("narrator.screen.title"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        openedAt = Util.getMillis();
        int bw = 204, bh = 22, gap = 6;
        int x = width / 2 - bw / 2;
        logoBottom = logoTop() + logoHeight();
        int y = logoBottom + 12;
        addRenderableWidget(Button.builder(Component.translatable("menu.singleplayer"), b -> minecraft.setScreen(new SelectWorldScreen(this)))
                .bounds(x, y, bw, bh).build());
        y += bh + gap;
        Button multi = Button.builder(Component.translatable("menu.multiplayer"),
                b -> minecraft.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)))
                .bounds(x, y, bw, bh).build();
        multi.active = minecraft.allowsMultiplayer();
        addRenderableWidget(multi);
        y += bh + gap;
        int half = (bw - gap) / 2;
        Button realms = Button.builder(Component.translatable("menu.online"), b -> minecraft.setScreen(new RealmsMainScreen(this)))
                .bounds(x, y, half, bh).build();
        realms.active = minecraft.allowsMultiplayer();
        addRenderableWidget(realms);
        addRenderableWidget(Button.builder(Component.literal("Vortex Mods"), b -> minecraft.setScreen(new ModMenuScreen(this)))
                .bounds(x + half + gap, y, bw - half - gap, bh).build());
        y += bh + gap + 6;
        addRenderableWidget(Button.builder(Component.translatable("menu.options"), b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)))
                .bounds(x, y, half, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("menu.quit"), b -> minecraft.stop())
                .bounds(x + half + gap, y, bw - half - gap, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("options.language"), b -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())))
                .bounds(8, 8, 86, 18).build());
    }

    private int logoTop() {
        return Math.max(12, height / 9);
    }

    private int logoWidth() {
        return Math.min(width * 3 / 5, 300);
    }

    /** Logo height: as big as fits above the buttons (4 rows) and the footer. */
    private int logoHeight() {
        VortexStyle.Art logo = VortexStyle.logo();
        int room = height - logoTop() - 12 - (22 * 4 + 6 * 3 + 6) - 36;
        if (logo == null) return Math.max(20, Math.min(40, room));
        return Math.max(24, Math.min(Math.min(height / 3, room), logoWidth() * logo.h() / Math.max(1, logo.w())));
    }

    @Override
    protected void drawBackdrop(GuiGraphics c, int mouseX, int mouseY, float delta) {
        VortexStyle.Art bg = VortexStyle.background();
        if (bg != null) {
            // Cover the screen, keeping the picture's shape; drift a little with the mouse.
            float s = Math.max(width / (float) bg.w(), height / (float) bg.h()) * 1.04f;
            int dw = Math.round(bg.w() * s), dh = Math.round(bg.h() * s);
            int ox = (width - dw) / 2 - Math.round((mouseX / (float) Math.max(1, width) - 0.5f) * width * 0.02f);
            int oy = (height - dh) / 2;
            Gfx.texture(c, bg.id(), ox, oy, dw, dh, bg.w(), bg.h());
        } else {
            vanillaBackdrop(c, mouseX, mouseY, delta);
        }
        c.fillGradient(0, 0, width, height / 3, 0x55000000, 0x00000000);
        c.fillGradient(0, height / 2, width, height, 0x00000000, 0xB0050410);

        float t = Math.min(1f, (Util.getMillis() - openedAt) / 600f);
        float ease = 1f - (float) Math.pow(1 - t, 3);
        int lift = Math.round((1 - ease) * 12);
        VortexStyle.Art logo = VortexStyle.logo();
        if (logo != null) {
            int lw = logoWidth(), lh = logoHeight();
            lw = lh * logo.w() / Math.max(1, logo.h());
            Gfx.texture(c, logo.id(), (width - lw) / 2, logoTop() - lift, lw, lh, logo.w(), logo.h());
        } else {
            Gfx.push(c);
            Gfx.translate(c, width / 2f, logoTop() - lift);
            Gfx.scale(c, 3f, 3f);
            Ui.centered(c, "VORTEX", 0, 0, VortexMods.accent());
            Gfx.pop(c);
            Ui.centered(c, "CLIENT", width / 2, logoTop() + 28 - lift, 0xFFFFFFFF);
        }
    }

    @Override
    public void render(GuiGraphics c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        String version = "Vortex Client  ·  Minecraft " + Compat.gameVersionName();
        c.drawString(font, version, 6, height - 12, 0xB0FFFFFF);
        String copy = "Copyright Mojang AB. Do not distribute!";
        c.drawString(font, copy, width - font.width(copy) - 6, height - 12, 0x80FFFFFF);
        if (minecraft != null) {
            String name = minecraft.getUser().getName();
            int nw = font.width(name) + 20;
            Ui.round(c, width - nw - 8, 8, nw, 18, 0xC0151922);
            Ui.outline(c, width - nw - 8, 8, nw, 18, 0x38FFFFFF);
            c.fill(width - nw, 15, width - nw + 4, 19, 0xFF86DC5C);
            c.drawString(font, name, width - nw + 8, 13, 0xFFFFFFFF, false);
        }
    }
}
