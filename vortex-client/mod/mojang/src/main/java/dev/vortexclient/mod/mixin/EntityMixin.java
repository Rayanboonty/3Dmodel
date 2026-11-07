package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Freelook: mouse movement turns the camera, not the player. */
@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void vortex$freelook(double dx, double dy, CallbackInfo ci) {
        if (!VortexMods.freelook || (Object) this != Minecraft.getInstance().player) return;
        VortexMods.freeYaw += (float) dx * 0.15f;
        VortexMods.freePitch = Math.max(-90f, Math.min(90f, VortexMods.freePitch + (float) dy * 0.15f));
        ci.cancel();
    }
}
