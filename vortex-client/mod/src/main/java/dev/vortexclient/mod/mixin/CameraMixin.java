package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.render.Camera;

/** Freelook: the camera uses its own angles while the player keeps theirs. */
@Mixin(Camera.class)
public class CameraMixin {
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"), require = 0)
    private void vortex$freelook(Args args) {
        if (!VortexMods.freelook) return;
        args.set(0, VortexMods.freeYaw);
        args.set(1, VortexMods.freePitch);
    }
}
