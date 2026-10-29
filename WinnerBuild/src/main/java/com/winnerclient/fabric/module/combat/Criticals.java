package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class Criticals extends Module {
    private final Setting.ModeSetting mode = addMode("Mode", "Critical mode", "Packet", "Packet", "MiniJump", "Verus");

    public Criticals() {
        super("Criticals", "Every hit becomes a critical hit", Category.COMBAT);
    }

    public void doCritical() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isInLava()) return;

        String m = mode.getValue();
        switch (m) {
            case "Packet":
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    new Vec3d(mc.player.getX(), mc.player.getY() + 0.0625, mc.player.getZ()), false, false));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), false, false));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    new Vec3d(mc.player.getX(), mc.player.getY() + 0.0125, mc.player.getZ()), false, false));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), false, false));
                break;
            case "MiniJump":
                mc.player.addVelocity(0, 0.11, 0);
                break;
            case "Verus":
                double[] offsets = {0.0625, 0.0, 1.0E-4, 0.0};
                boolean onGround = false;
                for (double off : offsets) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        new Vec3d(mc.player.getX(), mc.player.getY() + off, mc.player.getZ()), onGround, false));
                    onGround = !onGround;
                }
                break;
        }
    }
}