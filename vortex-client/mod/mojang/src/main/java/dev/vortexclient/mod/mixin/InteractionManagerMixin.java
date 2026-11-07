package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vortexclient.mod.Stats;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

@Mixin(MultiPlayerGameMode.class)
public class InteractionManagerMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void vortex$attack(Player player, Entity target, CallbackInfo ci) {
        Stats.attacked(player, target);
    }
}
