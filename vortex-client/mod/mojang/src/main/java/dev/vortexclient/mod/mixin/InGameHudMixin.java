package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.Crosshair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/** Custom crosshair. */
@Mixin(Gui.class)
public class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void vortex$crosshair(GuiGraphics context, DeltaTracker tick, CallbackInfo ci) {
        if (Crosshair.draw(context)) ci.cancel();
    }

    /** Hide Scoreboard. */
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void vortex$hideScoreboard(GuiGraphics context, DeltaTracker tick, CallbackInfo ci) {
        if (dev.vortexclient.mod.VortexMods.on("scoreboard")) ci.cancel();
    }
}
