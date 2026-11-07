package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/** Own Nametag: your name above your head in third person. */
@Mixin(LivingEntityRenderer.class)
public class NametagMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void vortex$ownNametag(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && VortexMods.on("nametag") && !mc.options.getCameraType().isFirstPerson() && Minecraft.renderNames()) {
            cir.setReturnValue(true);
        }
    }
}
