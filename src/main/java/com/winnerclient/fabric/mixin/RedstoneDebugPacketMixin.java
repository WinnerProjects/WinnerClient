package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.misc.RedstoneDebug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class RedstoneDebugPacketMixin {
    @Unique
    private RedstoneDebug winnerclient$getRedstoneDebug() {
        MinecraftClient mc = MinecraftClient.getInstance();

        /*
         * Do not read the world or modify the module's collections from the
         * network thread. The handler is expected to run again on the client
         * thread when Minecraft schedules the packet.
         */
        if (!mc.isOnThread()) return null;

        Module module = ModuleManager.getModule("RedstoneDebug");

        if (module instanceof RedstoneDebug debug && debug.isEnabled()) {
            return debug;
        }

        return null;
    }

    @Inject(method = "onBlockUpdate", at = @At("HEAD"))
    private void winnerclient$redstoneBlockUpdate(
        BlockUpdateS2CPacket packet,
        CallbackInfo ci
    ) {
        RedstoneDebug debug = winnerclient$getRedstoneDebug();

        if (debug != null) {
            debug.onBlockUpdate(packet.getPos(), packet.getState());
        }
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"))
    private void winnerclient$redstoneChunkDelta(
        ChunkDeltaUpdateS2CPacket packet,
        CallbackInfo ci
    ) {
        RedstoneDebug debug = winnerclient$getRedstoneDebug();

        if (debug != null) {
            packet.visitUpdates(debug::onBlockUpdate);
        }
    }

    @Inject(method = "onBlockEntityUpdate", at = @At("HEAD"))
    private void winnerclient$redstoneBlockEntityUpdate(
        BlockEntityUpdateS2CPacket packet,
        CallbackInfo ci
    ) {
        RedstoneDebug debug = winnerclient$getRedstoneDebug();

        if (debug != null) {
            debug.onBlockEntityUpdate(packet.getPos(), packet.getNbt());
        }
    }
}
