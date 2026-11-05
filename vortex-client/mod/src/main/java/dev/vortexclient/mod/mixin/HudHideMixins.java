package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;

/** Hide Boss Bars. */
@Mixin(BossBarHud.class)
public class HudHideMixins {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void vortex$hideBossBars(DrawContext context, CallbackInfo ci) {
        if (VortexMods.on("bossbar")) ci.cancel();
    }
}
