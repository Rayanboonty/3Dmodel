package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.render.WorldRenderer;

/** Block Outline: your own colour for the box around the block you look at. */
@Mixin(WorldRenderer.class)
public class OutlineMixin {
    @ModifyArg(method = "renderTargetBlockOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;DDDLnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)V"),
            index = 8)
    private int vortex$outlineColor(int color) {
        return VortexMods.outlineColor(color);
    }
}
