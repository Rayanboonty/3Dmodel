package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.Crosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;

/** Custom crosshair and Hide Scoreboard (Minecraft 1.20 to 1.20.4). */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void vortex$crosshair(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (Crosshair.draw(context)) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void vortex$hideScoreboard(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (dev.vortexclient.mod.VortexMods.on("scoreboard")) ci.cancel();
    }
}
