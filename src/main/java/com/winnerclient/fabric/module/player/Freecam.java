package com.winnerclient.fabric.module.player;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Freecam extends Module {
    private final Setting.NumberSetting speed = addNum(
        "Speed",
        "Freecam speed",
        1.0,
        0.1,
        5.0,
        0.1
    );

    private final Setting.BoolSetting antiFreecamBypass = addBool(
        "AntiFreecam Bypass",
        "Visually fill connected air pockets smaller than 6 blocks",
        false
    );

    private static final PlayerInput EMPTY_INPUT = new PlayerInput(
        false,
        false,
        false,
        false,
        false,
        false,
        false
    );

    private static Vec3d pos;
    private static Vec3d prevPos;

    private static float yaw;
    private static float pitch;
    private static float lastYaw;
    private static float lastPitch;

    private static volatile boolean active;
    private static volatile boolean bypassActive;

    private static PlayerInput cameraInput = EMPTY_INPUT;
    private static boolean keepSneaking;

    private Vec3d returnPos;
    private float returnYaw;
    private float returnPitch;

    public Freecam() {
        super(
            "Freecam",
            "Camera moves freely, player stays behind",
            Category.PLAYER
        );
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isAntiFreecamBypassActive() {
        return active && bypassActive;
    }

    public static void captureInput(PlayerInput input) {
        cameraInput = input != null ? input : EMPTY_INPUT;
    }

    public static boolean shouldKeepSneaking() {
        return active && keepSneaking;
    }

    public static double getX(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPos.x, pos.x);
    }

    public static double getY(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPos.y, pos.y);
    }

    public static double getZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPos.z, pos.z);
    }

    public static float getYaw(float tickDelta) {
        return (float) MathHelper.lerp(tickDelta, lastYaw, yaw);
    }

    public static float getPitch(float tickDelta) {
        return (float) MathHelper.lerp(tickDelta, lastPitch, pitch);
    }

    public static void changeLookDirection(double deltaX, double deltaY) {
        lastYaw = yaw;
        lastPitch = pitch;

        yaw += (float) deltaX;
        pitch = MathHelper.clamp(
            pitch + (float) deltaY,
            -90.0f,
            90.0f
        );
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) {
            active = false;
            bypassActive = false;
            cameraInput = EMPTY_INPUT;
            return;
        }

        returnPos = mc.player.getEntityPos();
        returnYaw = mc.player.getYaw();
        returnPitch = mc.player.getPitch();

        var camera = mc.gameRenderer.getCamera();

        pos = camera.getCameraPos();
        prevPos = pos;

        yaw = camera.getYaw();
        pitch = camera.getPitch();
        lastYaw = yaw;
        lastPitch = pitch;

        keepSneaking = mc.player.isSneaking();
        cameraInput = EMPTY_INPUT;

        mc.player.setNoGravity(true);
        mc.player.setVelocity(Vec3d.ZERO);

        bypassActive = antiFreecamBypass.getValue();
        active = true;

        if (bypassActive) {
            reloadRenderer();
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();

        boolean wasBypassActive = bypassActive;

        active = false;
        bypassActive = false;
        cameraInput = EMPTY_INPUT;
        keepSneaking = false;

        if (mc.player != null) {
            mc.player.setNoGravity(false);
            mc.player.setVelocity(Vec3d.ZERO);

            if (returnPos != null) {
                mc.player.setPos(
                    returnPos.x,
                    returnPos.y,
                    returnPos.z
                );

                mc.player.setYaw(returnYaw);
                mc.player.setPitch(returnPitch);
                mc.player.setHeadYaw(returnYaw);
            }
        }

        returnPos = null;

        if (wasBypassActive) {
            reloadRenderer();
        }
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!active || mc.player == null || mc.world == null || pos == null) {
            return;
        }

        boolean requestedBypass = antiFreecamBypass.getValue();

        if (requestedBypass != bypassActive) {
            bypassActive = requestedBypass;
            reloadRenderer();
        }

        PlayerInput input = cameraInput;

        double spd = speed.getValue();
        double mult = input.sprint() ? 1.0 : 0.5;

        Vec3d forward = Vec3d.fromPolar(0, yaw).normalize();

        // yaw - 90 points to the player's right.
        Vec3d right = Vec3d.fromPolar(0, yaw - 90).normalize();

        double velX = 0.0;
        double velY = 0.0;
        double velZ = 0.0;

        if (input.forward()) {
            velX += forward.x * mult * spd;
            velZ += forward.z * mult * spd;
        }

        if (input.backward()) {
            velX -= forward.x * mult * spd;
            velZ -= forward.z * mult * spd;
        }

        if (input.right()) {
            velX += right.x * mult * spd;
            velZ += right.z * mult * spd;
        }

        if (input.left()) {
            velX -= right.x * mult * spd;
            velZ -= right.z * mult * spd;
        }

        if ((input.forward() || input.backward())
            && (input.right() || input.left())) {
            double diagonal = 1.0 / Math.sqrt(2.0);
            velX *= diagonal;
            velZ *= diagonal;
        }

        if (input.jump()) {
            velY += mult * spd;
        }

        if (input.sneak()) {
            velY -= mult * spd;
        }

        prevPos = pos;
        pos = pos.add(velX, velY, velZ);
    }

    private static void reloadRenderer() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }
}
