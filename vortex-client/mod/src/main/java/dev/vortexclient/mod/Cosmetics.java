package dev.vortexclient.mod;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

/** Your skin (for offline accounts) and Vortex cape. Only you see them. */
public final class Cosmetics {
    private Cosmetics() {}

    private static final Identifier SKIN = Identifier.of("vortex", "skin/local");
    private static final Set<String> CAPES = Set.of("vortex", "diamond", "ember", "emerald", "amethyst");
    private static boolean loaded;
    private static boolean hasSkin;

    private static void load() {
        loaded = true;
        String path = System.getProperty("vortex.skin");
        if (path == null || path.isBlank()) return;
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            NativeImage img = NativeImage.read(in);
            if (img.getWidth() == 64 && img.getHeight() == 32) img = legacy(img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(SKIN, new NativeImageBackedTexture(img));
            hasSkin = true;
        } catch (Exception e) {
            VortexMods.LOG.warn("Could not load the Vortex skin from {}", path, e);
        }
    }

    /** Old 64×32 skins: copy the right arm and leg to the left, like vanilla. */
    private static NativeImage legacy(NativeImage old) {
        NativeImage img = new NativeImage(64, 64, true);
        img.copyFrom(old);
        old.close();
        img.fillRect(0, 32, 64, 32, 0);
        img.copyRect(4, 16, 16, 32, 4, 4, true, false);
        img.copyRect(8, 16, 16, 32, 4, 4, true, false);
        img.copyRect(0, 20, 24, 32, 4, 12, true, false);
        img.copyRect(4, 20, 16, 32, 4, 12, true, false);
        img.copyRect(8, 20, 8, 32, 4, 12, true, false);
        img.copyRect(12, 20, 16, 32, 4, 12, true, false);
        img.copyRect(44, 16, -8, 32, 4, 4, true, false);
        img.copyRect(48, 16, -8, 32, 4, 4, true, false);
        img.copyRect(40, 20, 0, 32, 4, 12, true, false);
        img.copyRect(44, 20, -8, 32, 4, 12, true, false);
        img.copyRect(48, 20, -16, 32, 4, 12, true, false);
        img.copyRect(52, 20, -8, 32, 4, 12, true, false);
        return img;
    }

    private static Identifier cape() {
        String c = System.getProperty("vortex.cape", "none");
        return CAPES.contains(c) ? Identifier.of("vortex", "textures/cape/" + c + ".png") : null;
    }

    /** The local player's textures with the Vortex skin and cape, or null to keep vanilla's. */
    public static SkinTextures apply(SkinTextures orig) {
        if (!loaded) load();
        Identifier cape = cape();
        if (!hasSkin && cape == null) return null;
        SkinTextures.Model model = hasSkin
                ? (Boolean.getBoolean("vortex.skinSlim") ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE)
                : orig.model();
        return new SkinTextures(hasSkin ? SKIN : orig.texture(), orig.textureUrl(),
                cape != null ? cape : orig.capeTexture(), orig.elytraTexture(), model, orig.secure());
    }
}
