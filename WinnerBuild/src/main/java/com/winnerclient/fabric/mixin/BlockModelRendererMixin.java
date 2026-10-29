package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.render.XRay;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V", at = @At("HEAD"), cancellable = true)
    private void onRender(BlockRenderView world, List<?> models, BlockState state, BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer, boolean cull, int overlay, CallbackInfo ci) {
        if (!XRay.isVisible(state.getBlock())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSmooth", at = @At("HEAD"), cancellable = true)
    private void onRenderSmooth(BlockRenderView world, List<?> models, BlockState state, BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer, boolean cull, int overlay, CallbackInfo ci) {
        if (!XRay.isVisible(state.getBlock())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFlat", at = @At("HEAD"), cancellable = true)
    private void onRenderFlat(BlockRenderView world, List<?> models, BlockState state, BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer, boolean cull, int overlay, CallbackInfo ci) {
        if (!XRay.isVisible(state.getBlock())) {
            ci.cancel();
        }
    }
}
