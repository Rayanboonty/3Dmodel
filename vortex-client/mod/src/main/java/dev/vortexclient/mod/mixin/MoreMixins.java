package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

/** Clear Weather: no rain or thunder in the world you see. */
@Mixin(World.class)
public class MoreMixins {
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void vortex$noRain(float delta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientWorld && VortexMods.clearWeather()) cir.setReturnValue(0f);
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void vortex$noThunder(float delta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientWorld && VortexMods.clearWeather()) cir.setReturnValue(0f);
    }
}
