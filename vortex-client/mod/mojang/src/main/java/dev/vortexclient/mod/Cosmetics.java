package dev.vortexclient.mod;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Your skin (for offline accounts) and Vortex cape. Only you see them. */
public final class Cosmetics {
    private Cosmetics() {}

    private static final Identifier SKIN = Identifier.fromNamespaceAndPath("vortex", "skin/local");
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
            Minecraft.getInstance().getTextureManager().register(SKIN, Gfx.newTexture(img));
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
        return CAPES.contains(c) ? Identifier.fromNamespaceAndPath("vortex", "textures/cape/" + c + ".png") : null;
    }

    /** The local player's textures with the Vortex skin and cape, or null to keep vanilla's (1.21.9+). */
    public static PlayerSkin apply(PlayerSkin orig) {
        if (!loaded) load();
        Identifier cape = cape();
        if (!hasSkin && cape == null) return null;
        PlayerModelType model = hasSkin
                ? (Boolean.getBoolean("vortex.skinSlim") ? PlayerModelType.SLIM : PlayerModelType.WIDE)
                : orig.model();
        ClientAsset.Texture body = hasSkin ? new ClientAsset.ResourceTexture(SKIN, SKIN) : orig.body();
        ClientAsset.Texture capeAsset = cape != null ? new ClientAsset.ResourceTexture(cape, cape) : orig.cape();
        return new PlayerSkin(body, capeAsset, orig.elytra(), model, orig.secure());
    }
}
