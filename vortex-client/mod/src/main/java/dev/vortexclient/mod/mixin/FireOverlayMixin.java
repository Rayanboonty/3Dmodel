package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;

/** Low Fire: the burning overlay sits lower on the screen. */
@Mixin(InGameOverlayRenderer.class)
public class FireOverlayMixin {
    @ModifyArg(method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"), index = 1, require = 0)
    private static float vortex$lowFire(float y) {
        return VortexMods.lowFire() ? y - 0.32f : y;
    }
}
