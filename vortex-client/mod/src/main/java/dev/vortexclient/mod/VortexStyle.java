package dev.vortexclient.mod;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** The Vortex look in Minecraft's own menus: buttons and the main menu art. */
public final class VortexStyle {
    private VortexStyle() {}

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("vortex.menu", "true"));
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

    private static Art bg, logo;
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
        logo = load("vortex.menuLogo", "logo");
    }

    public static Art background() {
        ensure();
        return bg;
    }

    public static Art logo() {
        ensure();
        return logo;
    }
}
