package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.misc.ChunkFinder;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ChunkFinderWorldMixin {
    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void winnerclient$chunkFinderBlockChanged(
        BlockPos pos,
        BlockState state,
        int flags,
        int maxUpdateDepth,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()) return;

        var module = ModuleManager.getModule("ChunkFinder");

        if (module instanceof ChunkFinder finder) {
            finder.changed(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }
}
