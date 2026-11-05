package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.render.StorageESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // XCarry: suppress only the player's own inventory close packet.
        if (packet instanceof CloseHandledScreenC2SPacket closePacket) {
            Module xCarry = ModuleManager.getModule("XCarry");

            if (xCarry != null
                    && xCarry.isEnabled()
                    && mc.player != null
                    && closePacket.getSyncId()
                        == mc.player.playerScreenHandler.syncId) {
                ci.cancel();
                return;
            }
        }

        // Existing StorageESP movement-packet logic.
        if (!(packet instanceof PlayerMoveC2SPacket)) return;

        StorageESP esp = (StorageESP) ModuleManager.getModule("StorageESP");
        if (esp == null) return;

        Double voidY = esp.pollVoidLoad();
        if (voidY == null) return;
        if (mc.player == null) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        boolean onGround = mc.player.isOnGround();

        ci.cancel();
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.Full(
                px, voidY, pz, yaw, pitch, onGround, false
            )
        );
    }
}
