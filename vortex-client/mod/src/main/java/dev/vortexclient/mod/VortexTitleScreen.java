package dev.vortexclient.mod;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Vortex's main menu: the launcher's background and logo, with glass buttons. */
public class VortexTitleScreen extends ScreenBase {
    private long openedAt;
    private int logoBottom;

    public VortexTitleScreen() {
        super(Text.translatable("narrator.screen.title"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        openedAt = Util.getMeasuringTimeMs();
        int bw = 204, bh = 22, gap = 6;
        int x = width / 2 - bw / 2;
        logoBottom = logoTop() + logoHeight();
        int y = logoBottom + 12;
        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.singleplayer"), b -> client.setScreen(new SelectWorldScreen(this)))
                .dimensions(x, y, bw, bh).build());
        y += bh + gap;
        ButtonWidget multi = ButtonWidget.builder(Text.translatable("menu.multiplayer"),
                b -> client.setScreen(client.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this)))
                .dimensions(x, y, bw, bh).build();
        multi.active = client.isMultiplayerEnabled();
        addDrawableChild(multi);
        y += bh + gap;
        int half = (bw - gap) / 2;
        ButtonWidget realms = ButtonWidget.builder(Text.translatable("menu.online"), b -> client.setScreen(new RealmsMainScreen(this)))
                .dimensions(x, y, half, bh).build();
        realms.active = client.isMultiplayerEnabled();
        addDrawableChild(realms);
        addDrawableChild(ButtonWidget.builder(Text.literal("Vortex Mods"), b -> client.setScreen(new ModMenuScreen(this)))
                .dimensions(x + half + gap, y, bw - half - gap, bh).build());
        y += bh + gap + 6;
        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.options"), b -> client.setScreen(new OptionsScreen(this, client.options)))
                .dimensions(x, y, half, bh).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.quit"), b -> client.scheduleStop())
                .dimensions(x + half + gap, y, bw - half - gap, bh).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("options.language"), b -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager())))
                .dimensions(8, 8, 86, 18).build());
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
        return Math.max(24, Math.min(Math.min(Math.min(height / 4, 96), room), logoWidth() * logo.h() / Math.max(1, logo.w())));
    }

    @Override
    protected void drawBackdrop(DrawContext c, int mouseX, int mouseY, float delta) {
        // "moving": Minecraft's turning 3D world, graded like a shader (warm
        // light from the sun side, deeper shadows, a soft vignette).
        boolean moving = !"picture".equals(System.getProperty("vortex.menuStyle", "moving"));
        VortexStyle.Art bg = moving ? null : VortexStyle.background();
        if (moving) {
            vanillaBackdrop(c, mouseX, mouseY, delta);
            shaderGrade(c);
        } else if (bg != null) {
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

        float t = Math.min(1f, (Util.getMeasuringTimeMs() - openedAt) / 600f);
        float ease = 1f - (float) Math.pow(1 - t, 3);
        int lift = Math.round((1 - ease) * 12);
        VortexStyle.Art logo = VortexStyle.logo();
        int lh = logoHeight(), lw = lh * logo.w() / Math.max(1, logo.h());
        Gfx.texture(c, logo.id(), (width - lw) / 2, logoTop() - lift, lw, lh, logo.w(), logo.h());
    }

    /** A cinematic grade over the panorama: sun glow, cool shadows, vignette. */
    private void shaderGrade(DrawContext c) {
        float t = (Util.getMeasuringTimeMs() % 24000L) / 24000f;
        int glow = (int) (0x5A + 0x14 * Math.sin(t * Math.PI * 2));
        c.fillGradient(0, 0, width, height * 2 / 3, (glow << 24) | 0xFFA94D, 0x00FFA94D);
        c.fillGradient(0, 0, width, height / 5, 0x30FFE2B0, 0x00FFE2B0);
        c.fillGradient(0, height / 3, width, height, 0x00140E2A, 0x902A1240);
        int steps = 12, band = Math.max(1, width / 7 / steps);
        for (int i = 0; i < steps; i++) {
            int a = (int) (0x70 * (1 - i / (float) steps) * (1 - i / (float) steps));
            c.fill(i * band, 0, (i + 1) * band, height, a << 24);
            c.fill(width - (i + 1) * band, 0, width - i * band, height, a << 24);
        }
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        String version = "Vortex Client  ·  Minecraft " + Compat.gameVersionName();
        c.drawTextWithShadow(textRenderer, version, 6, height - 12, 0xB0FFFFFF);
        String copy = "Copyright Mojang AB. Do not distribute!";
        c.drawTextWithShadow(textRenderer, copy, width - textRenderer.getWidth(copy) - 6, height - 12, 0x80FFFFFF);
        if (client != null) {
            String name = client.getSession().getUsername();
            int nw = textRenderer.getWidth(name) + 20;
            Ui.round(c, width - nw - 8, 8, nw, 18, 0xC0151922);
            Ui.outline(c, width - nw - 8, 8, nw, 18, 0x38FFFFFF);
            c.fill(width - nw, 15, width - nw + 4, 19, 0xFF86DC5C);
            c.drawText(textRenderer, name, width - nw + 8, 13, 0xFFFFFFFF, false);
        }
    }
}
