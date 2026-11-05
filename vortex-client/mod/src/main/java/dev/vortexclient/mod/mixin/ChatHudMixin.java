package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;

/** Chat Timestamps. */
@Mixin(ChatHud.class)
public class ChatHudMixin {
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Text vortex$timestamp(Text message) {
        return VortexMods.stampChat(message);
    }
}
