package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;

/** Own Nametag (Minecraft 1.20 to 1.21.1: hasLabel takes just the entity). */
@Mixin(LivingEntityRenderer.class)
public class NametagMixin {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void vortex$ownNametag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == mc.player && VortexMods.on("nametag") && !mc.options.getPerspective().isFirstPerson() && MinecraftClient.isHudEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
