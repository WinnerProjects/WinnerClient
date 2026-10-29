package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;

public class Timer extends Module {
    private final Setting.NumberSetting speed = addNum("Speed", "Timer speed multiplier", 1.5, 0.5, 10.0, 0.1);
    private final Setting.BoolSetting onGroundOnly = addBool("OnGroundOnly", "Only speed when on ground", false);

    public Timer() {
        super("Timer", "Speeds up client tick rate (requires mixins)", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
    }

    @Override
    public void onDisable() {
    }
}
