package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.attribute.EntityAttributes;

public class Flight extends Module {
    private final Setting.NumberSetting speed      = addNum("Speed", "Flight speed", 0.8, 0.2, 5.0, 0.1);
    private final Setting.NumberSetting upSpeed     = addNum("Up Speed", "Upward speed", 0.4, 0.1, 2.0, 0.05);
    private final Setting.ModeSetting mode          = addMode("Mode", "Flight mode", "Motion", "Motion", "Packet", "Vanilla");
    private final Setting.BoolSetting antiKick      = addBool("AntiKick", "Prevent fly kick", true);
    private final Setting.BoolSetting noBob         = addBool("NoBob", "Remove view bobbing", false);
    private int antiKickTimer;

    public Flight() {
        super("Flight", "Allows you to fly in survival", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        antiKickTimer = 0;
        if (mc.player != null && noBob.getValue()) {
            mc.options.getBobView().setValue(false);
        }
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().allowFlying = false;
        float spd = (float)(double)speed.getValue();

        String m = mode.getValue();
        Vec3d vel = mc.player.getVelocity();
        var input = mc.player.input.getMovementInput();

        if (input.x != 0 || input.y != 0) {
            double yaw = Math.toRadians(mc.player.getYaw());
            double forward = input.x;
            double strafe = input.y;
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            double vx = (forward * cos - strafe * sin) * spd;
            double vz = (forward * sin + strafe * cos) * spd;
            vel = new Vec3d(vx, vel.y, vz);
        } else {
            vel = new Vec3d(0, vel.y, 0);
        }

        if (mc.player.input.playerInput.jump()) {
            vel = vel.add(0, upSpeed.getValue(), 0);
        } else if (mc.options.sneakKey.isPressed()) {
            vel = vel.add(0, -upSpeed.getValue(), 0);
        } else if (input.x == 0 && input.y == 0) {
            vel = new Vec3d(vel.x, 0, vel.z);
        }

        switch (m) {
            case "Motion":
                mc.player.setVelocity(vel);
                applyAntiKick(mc, 0.01);
                break;
            case "Packet":
                mc.player.setVelocity(vel);
                applyAntiKick(mc, -0.04);
                sendPacketPos(mc);
                break;
            case "Vanilla":
                mc.player.getAbilities().flying = true;
                mc.player.getAbilities().allowFlying = true;
                mc.player.getAbilities().setFlySpeed(spd * 0.08f);
                break;
        }
    }

    private void applyAntiKick(MinecraftClient mc, double offset) {
        if (!antiKick.getValue()) return;
        antiKickTimer++;
        if (antiKickTimer >= 20) {
            antiKickTimer = 0;
            mc.player.setVelocity(mc.player.getVelocity().add(0, offset, 0));
        }
    }

    private void sendPacketPos(MinecraftClient mc) {
        Vec3d pos = mc.player.getEntityPos();
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(new Vec3d(pos.x, pos.y - 0.05, pos.z), false, false));
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(new Vec3d(pos.x, pos.y + 0.05, pos.z), false, false));
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().setFlySpeed(0.05f);
            mc.player.setVelocity(new Vec3d(0, -0.02, 0));
            if (noBob.getValue()) mc.options.getBobView().setValue(true);
        }
    }
}