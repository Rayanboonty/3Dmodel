package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.vortexclient.mod.Compat;
import dev.vortexclient.mod.Module;
import dev.vortexclient.mod.VortexMods;
import dev.vortexclient.mod.VortexPlayers;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

/** The VC badge in front of Vortex players' names in the Tab list. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void vortex$badge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Module m = Module.get("badge");
        if (!VortexMods.config.enabled(m) || !VortexMods.config.bool(m, "tab")) return;
        if (VortexPlayers.isVortex(Compat.profileId(entry))) cir.setReturnValue(VortexPlayers.badge(cir.getReturnValue()));
    }
}
