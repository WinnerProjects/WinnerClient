package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class Phase extends Module {
    public Phase() {
        super("Phase", "Phase through blocks", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        mc.player.noClip = true;
        mc.player.fallDistance = 0;

        boolean insideBlock = mc.world.getBlockState(mc.player.getBlockPos())
                .shouldSuffocate(mc.world, mc.player.getBlockPos());

        Vec3d vel = mc.player.getVelocity();

        if (insideBlock) {
            var input = mc.player.input.getMovementInput();
            if (input.x != 0 || input.y != 0) {
                double yaw = Math.toRadians(mc.player.getYaw());
                double sin = Math.sin(yaw);
                double cos = Math.cos(yaw);
                double vx = input.y * cos - input.x * sin;
                double vz = input.y * sin + input.x * cos;
                mc.player.setVelocity(vx * 0.3, vel.y, vz * 0.3);
            }
        }

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isOnGround(), false));
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.noClip = false;
        }
    }
}
