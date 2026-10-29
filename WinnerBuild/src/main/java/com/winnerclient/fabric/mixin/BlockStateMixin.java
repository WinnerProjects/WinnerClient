package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.render.XRay;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class BlockStateMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void onGetRenderType(CallbackInfoReturnable<BlockRenderType> ci) {
        if (!XRay.isActive()) return;
        BlockState self = (BlockState) (Object) this;
        if (!XRay.isVisible(self.getBlock())) {
            ci.setReturnValue(BlockRenderType.INVISIBLE);
        }
    }

    @Inject(method = "isOpaqueFullCube", at = @At("HEAD"), cancellable = true)
    private void onIsOpaqueFullCube(CallbackInfoReturnable<Boolean> ci) {
        if (!XRay.isActive()) return;
        BlockState self = (BlockState) (Object) this;
        if (!XRay.isVisible(self.getBlock())) {
            ci.setReturnValue(false);
        }
    }
}
