package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.VortexStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;

/** Vortex-style buttons in Minecraft's own menus. */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void vortex$style(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        PressableWidget self = (PressableWidget) (Object) this;
        if (!VortexStyle.styles(self)) return;
        VortexStyle.button(context, self);
        ci.cancel();
    }
}
