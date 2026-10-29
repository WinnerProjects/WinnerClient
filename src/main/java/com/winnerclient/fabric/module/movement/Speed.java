package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class Speed extends Module {
    private final Setting.NumberSetting speed   = addNum("Speed", "Movement speed multiplier", 1.3, 0.5, 2.5, 0.05);
    private final Setting.ModeSetting mode       = addMode("Mode", "Speed mode", "Strafe", "Strafe", "BHop", "MiniHop", "Ground");
    private final Setting.BoolSetting jump       = addBool("AutoJump", "Auto jump while sprinting", false);
    private int groundTicks;
    private double moveSpeed;

    public Speed() {
        super("Speed", "Increases movement speed with AC bypass", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        groundTicks = 0;
        moveSpeed = 0;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        String m = mode.getValue();
        Vec3d vel = mc.player.getVelocity();
        float mult = (float)(double)speed.getValue();

        if (m.equals("Ground") && mc.player.isOnGround() && hasInput(mc)) {
            mc.player.setVelocity(vel.x * mult, vel.y, vel.z * mult);
            if (jump.getValue() && groundTicks % 3 == 0) {
                mc.player.jump();
            }
            groundTicks++;
            return;
        }

        if (!hasInput(mc)) return;

        var input = mc.player.input.getMovementInput();
        double yaw = Math.toRadians(mc.player.getYaw());
        double forward = input.y;
        double strafe = input.x;
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        float base = mult * 0.2f;

        double vx = forward * cos - strafe * sin;
        double vz = forward * sin + strafe * cos;

        double airMult = 0.8;
        switch (m) {
            case "Strafe" -> {
                if (mc.player.isOnGround()) {
                    mc.player.setVelocity(vx * base, vel.y, vz * base);
                    if (jump.getValue()) mc.player.jump();
                } else {
                    mc.player.setVelocity(vx * base * airMult, vel.y, vz * base * airMult);
                }
            }
            case "BHop" -> {
                if (mc.player.isOnGround()) {
                    mc.player.jump();
                    mc.player.setVelocity(vx * base * 1.2f, vel.y, vz * base * 1.2f);
                } else {
                    mc.player.setVelocity(vx * base * airMult, vel.y, vz * base * airMult);
                }
            }
            case "MiniHop" -> {
                if (mc.player.isOnGround()) {
                    mc.player.addVelocity(0, 0.06, 0);
                    mc.player.setVelocity(vx * base, vel.y, vz * base);
                } else {
                    mc.player.setVelocity(vx * base * airMult, vel.y, vz * base * airMult);
                }
            }
        }
    }

    private boolean hasInput(MinecraftClient mc) {
        var input = mc.player.input.getMovementInput();
        return input.x != 0 || input.y != 0;
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x * 0.3, vel.y, vel.z * 0.3);
        }
    }
}
