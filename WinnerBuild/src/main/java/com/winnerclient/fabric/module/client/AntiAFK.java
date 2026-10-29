package com.winnerclient.fabric.module.client;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class AntiAFK extends Module {
    private final Setting.BoolSetting rotate = addBool("Rotate", "Rotate to prevent AFK", true);
    private final Setting.BoolSetting move = addBool("Move", "Slight movement", false);
    private int tick;

    public AntiAFK() {
        super("AntiAFK", "Prevent being kicked for idling", Category.CLIENT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        tick++;
        if (tick < 80) return;
        tick = 0;

        if (rotate.getValue()) {
            mc.player.setYaw(mc.player.getYaw() + 10);
        }
        if (move.getValue()) {
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x + 0.01, vel.y, vel.z + 0.01);
        }
    }
}
