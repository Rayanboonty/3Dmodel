package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.Crosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

/** Custom crosshair. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void vortex$crosshair(DrawContext context, RenderTickCounter tick, CallbackInfo ci) {
        if (Crosshair.draw(context)) ci.cancel();
    }

    /** Hide Scoreboard. */
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void vortex$hideScoreboard(DrawContext context, RenderTickCounter tick, CallbackInfo ci) {
        if (dev.vortexclient.mod.VortexMods.on("scoreboard")) ci.cancel();
    }
}
