package dev.vortexclient.mod;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
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
            MinecraftClient.getInstance().getTextureManager().registerTexture(SKIN, Gfx.newTexture(img));
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

    private static final Identifier CUSTOM_CAPE = Identifier.of("vortex", "cape/custom");
    private static int customCape; // 0 unknown, 1 loaded, -1 failed

    private static Identifier cape() {
        String c = System.getProperty("vortex.cape", "none");
        if ("custom".equals(c)) return customCape() ? CUSTOM_CAPE : null;
        return CAPES.contains(c) ? Identifier.of("vortex", "textures/cape/" + c + ".png") : null;
    }

    /** A cape from your collection (OptiFine, LabyMod, a file...), saved by the launcher as a 64×32 layout. */
    private static boolean customCape() {
        if (customCape == 0) {
            customCape = -1;
            String path = System.getProperty("vortex.capeFile");
            if (path != null && !path.isBlank()) {
                try (InputStream in = Files.newInputStream(Path.of(path))) {
                    NativeImage img = NativeImage.read(in);
                    MinecraftClient.getInstance().getTextureManager().registerTexture(CUSTOM_CAPE, Gfx.newTexture(img));
                    customCape = 1;
                } catch (Exception e) {
                    VortexMods.LOG.warn("Could not load the cape from {}", path, e);
                }
            }
        }
        return customCape == 1;
    }

    /** The local player's textures with the Vortex skin and cape, or null to keep vanilla's (1.21.9+). */
    public static SkinTextures apply(SkinTextures orig) {
        if (!loaded) load();
        Identifier cape = cape();
        if (!hasSkin && cape == null) return null;
        PlayerSkinType model = hasSkin
                ? (Boolean.getBoolean("vortex.skinSlim") ? PlayerSkinType.SLIM : PlayerSkinType.WIDE)
                : orig.model();
        AssetInfo.TextureAsset body = hasSkin ? new AssetInfo.TextureAssetInfo(SKIN, SKIN) : orig.body();
        AssetInfo.TextureAsset capeAsset = cape != null ? new AssetInfo.TextureAssetInfo(cape, cape) : orig.cape();
        return new SkinTextures(body, capeAsset, orig.elytra(), model, orig.secure());
    }
}
