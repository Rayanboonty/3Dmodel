package dev.vortexclient.mod;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.LockButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** The Vortex look in Minecraft's own menus: buttons and the main menu art. */
public final class VortexStyle {
    private VortexStyle() {}

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("vortex.menu", "true"));
    }

    /**
     * Only Minecraft's own text buttons get the Vortex look. Other mods'
     * buttons (item lists, tabs, tiny arrows), icon buttons (recipe book,
     * lock, book pages), checkboxes and buttons too small for a label keep
     * the look they were made with.
     */
    public static boolean styles(PressableWidget w) {
        if (!enabled()) return false;
        if (!w.getClass().getName().startsWith("net.minecraft.")) return false;
        if (!(w instanceof ButtonWidget) && !(w instanceof CyclingButtonWidget)) return false;
        if (w instanceof TexturedButtonWidget || w instanceof LockButtonWidget || w instanceof PageTurnWidget) return false;
        if (w.getWidth() < 30 || w.getHeight() < 14) return false;
        return !w.getMessage().getString().isEmpty();
    }

    /** A dark glass button with an accent edge on hover. */
    public static void button(DrawContext c, PressableWidget w) {
        int x = w.getX(), y = w.getY(), wd = w.getWidth(), h = w.getHeight();
        boolean active = w.active;
        boolean hot = active && w.isSelected();
        int accent = VortexMods.accent();
        int bg = !active ? 0x80101318 : hot ? 0xE0262C38 : 0xC0151922;
        int r = Math.min(7, h / 2 - 1);
        // Rounded glass; the accent lights the edge and the bottom on hover.
        Ui.glass(c, x, y, wd, h, r, bg);
        if (hot) {
            Ui.roundedOutline(c, x, y, wd, h, r, Ui.alpha(accent, 0.9f));
            c.fillGradient(x + 2, y + h / 2, x + wd - 2, y + h - 1, 0x00000000, Ui.alpha(accent, 0.22f));
        }
        int tc = !active ? 0xFF6E747E : hot ? 0xFFFFFFFF : 0xFFE4E8EE;
        Gfx.buttonLabel(c, w, tc);
    }

    // ------------------------------------------------------------ menu art --

    public record Art(Identifier id, int w, int h) {}

    private static Art bg;
    private static boolean loaded;

    private static Art load(String prop, String name) {
        String path = System.getProperty(prop);
        if (path == null || path.isBlank()) return null;
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            NativeImage img = NativeImage.read(in);
            Identifier id = Identifier.of("vortex", "menu/" + name);
            int w = img.getWidth(), h = img.getHeight();
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, Gfx.newTexture(img));
            return new Art(id, w, h);
        } catch (Exception e) {
            VortexMods.LOG.warn("Could not load the Vortex menu image {}", path, e);
            return null;
        }
    }

    private static void ensure() {
        if (loaded) return;
        loaded = true;
        bg = load("vortex.menuBg", "background");
    }

    public static Art background() {
        ensure();
        return bg;
    }

    /** The VC logo, bundled with the mod so every launcher (and Android) shows the same one. */
    private static final Art LOGO = new Art(Identifier.of("vortex", "textures/gui/logo.png"), 354, 252);
    private static final Art LOGO_SMALL = new Art(Identifier.of("vortex", "textures/gui/logo_small.png"), 135, 96);

    public static Art logo() {
        return LOGO;
    }

    /** A pre-shrunk copy for small spots like the mod menu header. */
    public static Art logoSmall() {
        return LOGO_SMALL;
    }
}
