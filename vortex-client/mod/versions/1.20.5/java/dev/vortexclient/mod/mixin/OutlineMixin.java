package dev.vortexclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import dev.vortexclient.mod.VortexMods;
import net.minecraft.client.render.WorldRenderer;

/**
 * Block Outline (Minecraft 1.20 to 1.21.1): the outline's colour is passed
 * as four floats to drawCuboidShapeOutline inside drawBlockOutline.
 */
@Mixin(WorldRenderer.class)
public class OutlineMixin {
    @ModifyArgs(method = "drawBlockOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;drawCuboidShapeOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/util/shape/VoxelShape;DDDFFFF)V"))
    private void vortex$outlineColor(Args args) {
        int vanilla = 0x66000000;
        int c = VortexMods.outlineColor(vanilla);
        if (c == vanilla) return;
        args.set(6, ((c >> 16) & 0xFF) / 255f);
        args.set(7, ((c >> 8) & 0xFF) / 255f);
        args.set(8, (c & 0xFF) / 255f);
        args.set(9, ((c >>> 24) & 0xFF) / 255f);
    }
}
