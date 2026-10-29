package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;

public class RenderDistanceBypass extends Module {
    private State state = State.IDLE;
    private int ticksElapsed;

    public RenderDistanceBypass() {
        super("RenderDistanceBypass", "Automatically sets render/simulation distance and sends packets to force server-side reload.", Category.MISC);
    }

    private final Setting.IntSetting triggerY = addInt("TriggerY", "Y level to trigger bypass", 5, -64, 320);
    private final Setting.IntSetting lowRender = addInt("LowRender", "Low render distance", 2, 2, 16);
    private final Setting.IntSetting highRender = addInt("HighRender", "High render distance", 16, 2, 32);
    private final Setting.IntSetting lowSim = addInt("LowSim", "Low simulation distance", 2, 2, 16);
    private final Setting.IntSetting highSim = addInt("HighSim", "High simulation distance", 16, 2, 32);
    private final Setting.IntSetting lowDelay = addInt("LowDelay", "Delay ticks on low", 20, 5, 100);
    private final Setting.IntSetting highDelay = addInt("HighDelay", "Delay ticks on high", 40, 5, 200);

    private enum State { IDLE, LOW_SET, HIGH_SET, DONE }

    @Override
    protected void onEnable() {
        state = State.IDLE;
        ticksElapsed = 0;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        switch (state) {
            case IDLE:
                if (mc.player.getY() < triggerY.getValue()) {
                    mc.options.getViewDistance().setValue(lowRender.getValue());
                    mc.options.getSimulationDistance().setValue(lowSim.getValue());
                    state = State.LOW_SET;
                    ticksElapsed = 0;
                }
                break;
            case LOW_SET:
                ticksElapsed++;
                if (ticksElapsed >= lowDelay.getValue()) {
                    mc.options.getViewDistance().setValue(highRender.getValue());
                    mc.options.getSimulationDistance().setValue(highSim.getValue());
                    state = State.HIGH_SET;
                    ticksElapsed = 0;
                }
                break;
            case HIGH_SET:
                ticksElapsed++;
                if (ticksElapsed >= highDelay.getValue()) {
                    state = State.DONE;
                    toggle();
                }
                break;
            case DONE:
                break;
        }
    }
}
