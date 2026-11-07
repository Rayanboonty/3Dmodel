package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

/** Chat Timestamps. */
@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component vortex$timestamp(Component message) {
        return VortexMods.stampChat(message);
    }
}
