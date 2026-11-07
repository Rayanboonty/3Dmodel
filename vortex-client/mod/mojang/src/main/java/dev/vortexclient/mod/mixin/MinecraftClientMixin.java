package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.vortexclient.mod.VortexStyle;
import dev.vortexclient.mod.VortexTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Show the Vortex main menu wherever Minecraft would show its title screen. */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen vortex$menu(Screen screen) {
        if (screen instanceof TitleScreen && VortexStyle.enabled()) return new VortexTitleScreen();
        return screen;
    }
}
