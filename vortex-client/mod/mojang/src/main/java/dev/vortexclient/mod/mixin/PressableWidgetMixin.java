package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.VortexStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;

/** Vortex-style buttons in every menu. */
@Mixin(AbstractButton.class)
public abstract class PressableWidgetMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void vortex$style(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!VortexStyle.enabled()) return;
        VortexStyle.button(context, (AbstractButton) (Object) this);
        ci.cancel();
    }
}
