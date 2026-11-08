package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.combat.SpinBot;
import com.winnerclient.fabric.module.render.StorageESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @ModifyVariable(
            method = "sendPacket",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Packet<?> winnerclient$applySpinRotation(Packet<?> packet) {
        if (!(packet instanceof PlayerMoveC2SPacket move)) {
            return packet;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) {
            return packet;
        }

        var module = ModuleManager.getModule("SpinBot");

        if (!(module instanceof SpinBot spin)
                || !spin.isReadyFor(mc.player)) {
            return packet;
        }

        if (move.changesPosition()) {
            return new PlayerMoveC2SPacket.Full(
                    move.getX(mc.player.getX()),
                    move.getY(mc.player.getY()),
                    move.getZ(mc.player.getZ()),
                    spin.getSpinYaw(),
                    spin.getSpinPitch(),
                    move.isOnGround(),
                    move.horizontalCollision()
            );
        }

        return new PlayerMoveC2SPacket.LookAndOnGround(
                spin.getSpinYaw(),
                spin.getSpinPitch(),
                move.isOnGround(),
                move.horizontalCollision()
        );
    }

    @Inject(
            at = @At("HEAD"),
            method = "sendPacket",
            cancellable = true
    )
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof PlayerMoveC2SPacket)) {
            return;
        }

        StorageESP esp = (StorageESP) ModuleManager.getModule("StorageESP");

        if (esp == null) {
            return;
        }

        Double voidY = esp.pollVoidLoad();

        if (voidY == null) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {
            return;
        }

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        boolean onGround = mc.player.isOnGround();

        ci.cancel();

        mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.Full(
                        px,
                        voidY,
                        pz,
                        yaw,
                        pitch,
                        onGround,
                        false
                )
        );
    }
}
