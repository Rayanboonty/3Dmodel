package dev.vortexclient.mod.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.Stats;
import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;

/** Click counting and scroll zoom (Minecraft 1.21.9+: mouse buttons arrive as MouseInput). */
@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void vortex$countClicks(long window, net.minecraft.client.input.MouseInput input, int action, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (action == GLFW.GLFW_PRESS && mc.currentScreen == null && window == mc.getWindow().getHandle()) Stats.click(input.button());
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void vortex$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null && vertical != 0 && VortexMods.scrollZooms()) {
            VortexMods.scrollZoom(vertical);
            ci.cancel();
        }
    }
}
