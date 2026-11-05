package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;

/** Own Nametag: your name above your head in third person. */
@Mixin(LivingEntityRenderer.class)
public class NametagMixin {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void vortex$ownNametag(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == mc.player && VortexMods.on("nametag") && !mc.options.getPerspective().isFirstPerson() && MinecraftClient.isHudEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
