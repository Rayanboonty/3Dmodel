package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;

/** Hide Boss Bars. */
@Mixin(BossHealthOverlay.class)
public class HudHideMixins {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void vortex$hideBossBars(GuiGraphics context, CallbackInfo ci) {
        if (VortexMods.on("bossbar")) ci.cancel();
    }
}
