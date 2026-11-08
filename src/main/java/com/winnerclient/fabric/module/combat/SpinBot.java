package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class SpinBot extends Module {

    private final Setting.NumberSetting speed = addNum(
            "Speed",
            "Rotation speed in degrees per tick",
            30.0, 1.0, 180.0, 1.0
    );

    private final Setting.ModeSetting pitchMode = addMode(
            "Pitch",
            "Pitch rotation mode",
            "None",
            "None", "Random", "Spin"
    );

    private final Setting.BoolSetting bypass = addBool(
            "Bypass",
            "Add small variations to rotation speed",
            true
    );

    private final Random random = new Random();

    private ClientPlayerEntity owner;
    private float spinYaw;
    private float spinPitch;
    private float pitchJitter;

    public SpinBot() {
        super(
                "SpinBot",
                "Player model spins while the camera stays unaffected",
                Category.COMBAT
        );
    }

    @Override
    public void onEnable() {
        owner = null;
        initializePlayer();
    }

    @Override
    public void onDisable() {
        owner = null;
    }

    @Override
    public void onDisconnect() {
        owner = null;
    }

    private void initializePlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {
            owner = null;
            return;
        }

        owner = mc.player;
        spinYaw = mc.player.getYaw();
        spinPitch = mc.player.getPitch();
        pitchJitter = spinPitch;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) {
            owner = null;
            return;
        }

        if (owner != mc.player) {
            initializePlayer();
        }

        float speedValue = speed.getValue().floatValue();

        if (bypass.getValue()) {
            speedValue += (random.nextFloat() - 0.5f) * 4.0f;
        }

        spinYaw = MathHelper.wrapDegrees(spinYaw + speedValue);

        switch (pitchMode.getValue()) {
            case "Random" -> {
                pitchJitter += random.nextFloat() * 6.0f - 3.0f;
                pitchJitter = MathHelper.clamp(
                        pitchJitter,
                        -90.0f,
                        90.0f
                );
                spinPitch = pitchJitter;
            }

            case "Spin" -> {
                spinPitch += speedValue * 0.3f;

                if (spinPitch > 90.0f) {
                    spinPitch = -90.0f;
                } else if (spinPitch < -90.0f) {
                    spinPitch = 90.0f;
                }
            }

            default -> spinPitch = mc.player.getPitch();
        }

        mc.player.setBodyYaw(spinYaw);
        mc.player.setHeadYaw(spinYaw);

        // Ekstra hareket paketi gönderilmez.
        // Rotasyon mevcut hareket paketine mixin tarafından uygulanır.
    }

    public boolean isReadyFor(ClientPlayerEntity player) {
        return isEnabled() && player != null && owner == player;
    }

    public float getSpinYaw() {
        return spinYaw;
    }

    public float getSpinPitch() {
        return spinPitch;
    }
}
