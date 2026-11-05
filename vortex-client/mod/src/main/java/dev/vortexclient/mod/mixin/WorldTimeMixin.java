package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.world.ClientWorld;

/** Time Changer: the time of day you see. */
@Mixin(ClientWorld.Properties.class)
public class WorldTimeMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void vortex$time(CallbackInfoReturnable<Long> cir) {
        long t = VortexMods.timeOverride();
        if (t >= 0) cir.setReturnValue(t);
    }
}
