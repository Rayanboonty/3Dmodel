package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;

/** Zoom and No Hurt Cam (Minecraft 1.20 to 1.21.1: getFov returns a double). */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void vortex$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (changingFov) cir.setReturnValue((double) VortexMods.applyZoom(cir.getReturnValue().floatValue()));
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void vortex$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (VortexMods.noHurtCam()) ci.cancel();
    }
}
