package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.renderer.LevelRenderer;

/** Block Outline: your own colour for the box around the block you look at. */
/** Block Outline (Minecraft 1.21.9+: the outline comes from a render state). */
@Mixin(LevelRenderer.class)
public class OutlineMixin {
    @ModifyArg(method = "renderBlockOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDLnet/minecraft/client/renderer/state/BlockOutlineRenderState;IF)V"),
            index = 6)
    private int vortex$outlineColor(int color) {
        return VortexMods.outlineColor(color);
    }
}
