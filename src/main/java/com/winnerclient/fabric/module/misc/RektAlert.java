package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class RektAlert extends Module {
    private final Setting.BoolSetting soundAlert = addBool(
        "SoundAlert",
        "Play a sound when a change is detected.",
        true
    );

    private final Setting.BoolSetting chatAlert = addBool(
        "ChatAlert",
        "Print an alert when a change is detected.",
        true
    );

    private final Setting.NumberSetting soundVolume = addNum(
        "Volume",
        "Alert sound volume.",
        0.5,
        0.1,
        1.0,
        0.1
    );

    private final Setting.IntSetting cooldownTicks = addInt(
        "Cooldown",
        "Minimum ticks between alerts.",
        20,
        5,
        100
    );

    private long lastAlertTick;
    private long currentTick;
    private long lastSeenSequence;

    public RektAlert() {
        super(
            "RektAlert",
            "Plays a sound and sends a chat alert for RektMovement detections.",
            Category.MISC
        );
    }

    @Override
    protected void onEnable() {
        currentTick = 0;
        lastAlertTick = 0;

        RektMovement movement = getMovement();
        lastSeenSequence = movement == null
            ? 0
            : movement.getDetectionSequence();
    }

    @Override
    public void onDisconnect() {
        currentTick = 0;
        lastAlertTick = 0;

        RektMovement movement = getMovement();
        lastSeenSequence = movement == null
            ? 0
            : movement.getDetectionSequence();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        currentTick++;

        RektMovement movement = getMovement();
        if (movement == null || !movement.isEnabled()) return;

        long sequence = movement.getDetectionSequence();
        if (sequence == lastSeenSequence) return;

        // Like the original module, changes during cooldown are not queued.
        lastSeenSequence = sequence;

        if (currentTick - lastAlertTick < cooldownTicks.getValue()) return;

        DetectedMovement latest = movement.getLatestDetection();
        if (latest == null) return;

        lastAlertTick = currentTick;

        if (soundAlert.getValue()) {
            mc.getSoundManager().play(
                PositionedSoundInstance.master(
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                    1.0f,
                    soundVolume.getValue().floatValue()
                )
            );
        }

        if (chatAlert.getValue()) {
            String message = String.format(
                "§c[RektAlert]§r %s (%d,%d,%d): %s",
                latest.type,
                latest.pos.getX(),
                latest.pos.getY(),
                latest.pos.getZ(),
                latest.description
            );

            mc.player.sendMessage(Text.literal(message), false);
        }
    }

    private RektMovement getMovement() {
        Module module = ModuleManager.getModule("RektMovement");
        return module instanceof RektMovement
            ? (RektMovement) module
            : null;
    }
}
