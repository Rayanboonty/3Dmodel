package dev.vortexclient.mod;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.resources.Identifier;

/** The Vortex look in Minecraft's own menus: buttons and the main menu art. */
public final class VortexStyle {
    private VortexStyle() {}

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("vortex.menu", "true"));
    }

    /** A dark glass button with an accent edge on hover. */
    public static void button(GuiGraphics c, AbstractButton w) {
        int x = w.getX(), y = w.getY(), wd = w.getWidth(), h = w.getHeight();
        boolean active = w.active;
        boolean hot = active && w.isHoveredOrFocused();
        int accent = VortexMods.accent();
        int bg = !active ? 0x90101318 : hot ? 0xE8262C38 : 0xD0151922;
        Ui.round(c, x, y, wd, h, bg);
        Ui.outline(c, x, y, wd, h, hot ? accent : 0x38FFFFFF);
        c.fill(x + 2, y + 1, x + wd - 2, y + 2, hot ? 0x30FFFFFF : 0x14FFFFFF);
        if (hot) c.fill(x + 3, y + h - 2, x + wd - 3, y + h - 1, Ui.alpha(accent, 0.85f));
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
            Identifier id = Identifier.fromNamespaceAndPath("vortex", "menu/" + name);
            int w = img.getWidth(), h = img.getHeight();
            Minecraft.getInstance().getTextureManager().register(id, Gfx.newTexture(img));
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
