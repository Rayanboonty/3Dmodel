package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.VortexStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;

/** Vortex-style buttons in every menu (Minecraft 1.20.1: renderButton). */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {
    @Inject(method = "renderButton", at = @At("HEAD"), cancellable = true)
    private void vortex$style(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!VortexStyle.enabled()) return;
        VortexStyle.button(context, (PressableWidget) (Object) this);
        ci.cancel();
    }
}
