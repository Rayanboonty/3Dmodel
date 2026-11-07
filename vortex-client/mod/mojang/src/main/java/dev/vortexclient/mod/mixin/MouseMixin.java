package dev.vortexclient.mod.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.Stats;
import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

/** Click counting and scroll zoom (Minecraft 1.21.9+: mouse buttons arrive as MouseInput). */
@Mixin(MouseHandler.class)
public class MouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void vortex$countClicks(long window, net.minecraft.client.input.MouseButtonInfo input, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (action == GLFW.GLFW_PRESS && mc.screen == null && window == mc.getWindow().handle()) Stats.click(input.button());
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void vortex$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && vertical != 0 && VortexMods.scrollZooms()) {
            VortexMods.scrollZoom(vertical);
            ci.cancel();
        }
    }
}
