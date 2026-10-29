package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class Swim extends Module {
    private final Setting.NumberSetting speed = addNum("Speed", "Swim speed multiplier", 1.5, 0.5, 3.0, 0.1);
    private final Setting.BoolSetting autoSurface = addBool("AutoSurface", "Automatically swim to surface", false);

    public Swim() {
        super("Swim", "Auto swim / fast water movement", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!mc.player.isTouchingWater() && !mc.player.isInLava()) return;

        float spd = (float)(double)speed.getValue();
        Vec3d vel = mc.player.getVelocity();

        if (mc.player.input.getMovementInput().x > 0 || mc.player.input.getMovementInput().y != 0) {
            double yaw = Math.toRadians(mc.player.getYaw());
            double forward = mc.player.input.getMovementInput().x * spd * 0.13;
            double sideways = mc.player.input.getMovementInput().y * spd * 0.13;
            vel = new Vec3d(
                forward * Math.sin(-yaw) + sideways * Math.cos(yaw),
                vel.y,
                forward * Math.cos(yaw) - sideways * Math.sin(-yaw));
        }

        if (mc.player.input.playerInput.jump()) {
            vel = vel.add(0, 0.15 * spd, 0);
        } else if (mc.options.sneakKey.isPressed()) {
            vel = vel.add(0, -0.15 * spd, 0);
        } else if (autoSurface.getValue() && mc.player.isTouchingWater() && !mc.options.sneakKey.isPressed()) {
            vel = vel.add(0, 0.05, 0);
        }

        mc.player.setVelocity(vel);
    }
}
