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
        Module tp = Module.get("tabping");
        if (VortexMods.config.enabled(tp)) {
            int ms = entry.getLatency();
            net.minecraft.util.Formatting col = !VortexMods.config.bool(tp, "color") ? net.minecraft.util.Formatting.GRAY
                    : ms < 80 ? net.minecraft.util.Formatting.GREEN : ms < 150 ? net.minecraft.util.Formatting.YELLOW
                    : ms < 250 ? net.minecraft.util.Formatting.GOLD : net.minecraft.util.Formatting.RED;
            cir.setReturnValue(cir.getReturnValue().copy().append(Text.literal(" " + ms + "ms").formatted(col)));
        }
        // Nick Hider: your own row shows the other name.
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (VortexMods.on("nickhider") && mc.player != null && mc.player.getUuid().equals(Compat.profileId(entry))) {
            cir.setReturnValue(Text.literal(VortexMods.nick()));
        }
        Module m = Module.get("badge");
        if (!VortexMods.config.enabled(m) || !VortexMods.config.bool(m, "tab")) return;
        if (VortexPlayers.isVortex(Compat.profileId(entry))) cir.setReturnValue(VortexPlayers.badge(cir.getReturnValue()));
    }
}
