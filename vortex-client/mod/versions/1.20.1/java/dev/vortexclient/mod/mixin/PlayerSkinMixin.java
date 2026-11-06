package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.vortexclient.mod.Cosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

/** Your skin and cape (Minecraft 1.20.1: three separate getters). */
@Mixin(AbstractClientPlayerEntity.class)
public class PlayerSkinMixin {
    private boolean vortex$me() {
        return (Object) this == MinecraftClient.getInstance().player;
    }

    @Inject(method = "getSkinTexture", at = @At("RETURN"), cancellable = true)
    private void vortex$skin(CallbackInfoReturnable<Identifier> cir) {
        if (vortex$me()) cir.setReturnValue(Cosmetics.skin(cir.getReturnValue()));
    }

    @Inject(method = "getCapeTexture", at = @At("RETURN"), cancellable = true)
    private void vortex$cape(CallbackInfoReturnable<Identifier> cir) {
        if (vortex$me()) cir.setReturnValue(Cosmetics.cape(cir.getReturnValue()));
    }

    @Inject(method = "canRenderCapeTexture", at = @At("RETURN"), cancellable = true)
    private void vortex$hasCape(CallbackInfoReturnable<Boolean> cir) {
        if (vortex$me() && Cosmetics.cape(null) != null) cir.setReturnValue(true);
    }

    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
    private void vortex$model(CallbackInfoReturnable<String> cir) {
        if (vortex$me()) cir.setReturnValue(Cosmetics.model(cir.getReturnValue()));
    }
}
