package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void vortex$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) cir.setReturnValue(VortexMods.applyZoom(cir.getReturnValue()));
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void vortex$noHurtCam(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (VortexMods.noHurtCam()) ci.cancel();
    }
}
