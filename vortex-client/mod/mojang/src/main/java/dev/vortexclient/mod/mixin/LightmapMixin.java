package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;

@Mixin(LightTexture.class)
public class LightmapMixin {
    /** Fullbright: the lightmap sees a very high gamma; the saved option is untouched. */
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"), require = 0)
    private Object vortex$gamma(OptionInstance<?> option) {
        Object value = option.get();
        if (VortexMods.fullbright() && option == Minecraft.getInstance().options.gamma()) return 15.0;
        return value;
    }
}
