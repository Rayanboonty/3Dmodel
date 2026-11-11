package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.render.WorldRenderer;

/** Block Outline: your own colour for the box around the block you look at. */
/** Block Outline (Minecraft 1.21.9+: the outline comes from a render state). */
@Mixin(WorldRenderer.class)
public class OutlineMixin {
    @ModifyArg(method = "renderTargetBlockOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;I)V"),
            index = 6)
    private int vortex$outlineColor(int color) {
        return VortexMods.outlineColor(color);
    }
}
