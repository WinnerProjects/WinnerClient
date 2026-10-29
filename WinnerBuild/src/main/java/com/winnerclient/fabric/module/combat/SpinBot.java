package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class SpinBot extends Module {
    private final Setting.NumberSetting speed = addNum("Speed", "Rotation speed (degrees per tick)", 30.0, 1.0, 180.0, 1.0);
    private final Setting.ModeSetting pitchMode = addMode("Pitch", "Pitch rotation mode", "None", "None", "Random", "Spin");
    private final Setting.BoolSetting bypass = addBool("Bypass", "Anti-cheat bypass", true);

    private final java.util.Random random = new java.util.Random();
    private float spinYaw;
    private float spinPitch;
    private float pitchJitter;
    private int tickCounter;

    public SpinBot() {
        super("SpinBot", "Player model spins, camera stays unaffected", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            spinYaw = mc.player.getYaw();
            spinPitch = mc.player.getPitch();
        }
        pitchJitter = 0;
        tickCounter = 0;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        float speedVal = speed.getValue().floatValue();
        boolean bypassOn = bypass.getValue();

        if (bypassOn) {
            tickCounter++;
            speedVal += (random.nextFloat() - 0.5f) * 4;
        }

        spinYaw += speedVal;

        switch (pitchMode.getValue()) {
            case "Random":
                pitchJitter += random.nextFloat() * 6 - 3;
                pitchJitter = Math.max(-90, Math.min(90, pitchJitter));
                spinPitch = pitchJitter;
                break;
            case "Spin":
                spinPitch += speedVal * 0.3f;
                if (spinPitch > 90) spinPitch = -90;
                if (spinPitch < -90) spinPitch = 90;
                break;
            default:
                spinPitch = mc.player.getPitch();
                break;
        }

        mc.player.setBodyYaw(spinYaw);
        mc.player.setHeadYaw(spinYaw);

        if (!bypassOn || tickCounter % 2 == 0) {
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(spinYaw, spinPitch, mc.player.isOnGround(), true)
            );
        }
    }
}
