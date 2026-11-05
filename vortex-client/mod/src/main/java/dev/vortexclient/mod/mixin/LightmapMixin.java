package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;

@Mixin(LightmapTextureManager.class)
public class LightmapMixin {
    /** Fullbright: the lightmap sees a very high gamma; the saved option is untouched. */
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"), require = 0)
    private Object vortex$gamma(SimpleOption<?> option) {
        Object value = option.getValue();
        if (VortexMods.fullbright() && option == MinecraftClient.getInstance().options.getGamma()) return 15.0;
        return value;
    }
}
