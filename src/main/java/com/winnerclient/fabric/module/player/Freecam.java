package com.winnerclient.fabric.module.player;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Freecam extends Module {
    private final Setting.NumberSetting speed = addNum(
        "Speed",
        "Camera movement speed",
        1.0,
        0.1,
        5.0,
        0.1
    );

    private final Setting.BoolSetting staySneaking = addBool(
        "Stay Sneaking",
        "Keep the player sneaking if they were sneaking when Freecam was enabled",
        true
    );

    private final Setting.BoolSetting staticView = addBool(
        "Static View",
        "Disable view bobbing and FOV effects while Freecam is active",
        true
    );

    private final Setting.BoolSetting reloadChunks = addBool(
        "Reload Chunks",
        "Reload chunk rendering when Freecam is toggled",
        true
    );

    private static final PlayerInput EMPTY_INPUT =
        new PlayerInput(false, false, false, false, false, false, false);

    private static PlayerInput cameraInput = EMPTY_INPUT;

    // These are camera coordinates, including eye height.
    private static Vec3d pos = Vec3d.ZERO;
    private static Vec3d prevPos = Vec3d.ZERO;

    private static float yaw;
    private static float pitch;
    private static float lastYaw;
    private static float lastPitch;

    private static boolean active;
    private static boolean keepSneaking;

    private Perspective previousPerspective;
    private ClientPlayerEntity playerAtEnable;
    private ClientWorld worldAtEnable;

    private boolean changedViewSettings;
    private double previousFovScale;
    private boolean previousBobView;

    public Freecam() {
        super(
            "Freecam",
            "Move the camera independently of the player",
            Category.PLAYER
        );
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean shouldKeepSneaking() {
        return active && keepSneaking;
    }

    public static void captureInput(PlayerInput input) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!active || mc.currentScreen != null) {
            cameraInput = EMPTY_INPUT;
            return;
        }

        cameraInput = input;
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
        return MathHelper.lerp(tickDelta, lastYaw, yaw);
    }

    public static float getPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, lastPitch, pitch);
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
            setEnabled(false);
            return;
        }

        // Freelook also controls the camera, so turn it off first.
        Module freelook = ModuleManager.getModule("Freelook");
        if (freelook != null && freelook.isEnabled()) {
            freelook.setEnabled(false);
        }

        playerAtEnable = mc.player;
        worldAtEnable = mc.world;
        previousPerspective = mc.options.getPerspective();

        var camera = mc.gameRenderer.getCamera();

        pos = camera.getCameraPos();
        prevPos = pos;

        yaw = camera.getYaw();
        pitch = camera.getPitch();
        lastYaw = yaw;
        lastPitch = pitch;

        keepSneaking =
            staySneaking.getValue()
                && !mc.player.getAbilities().flying
                && mc.options.sneakKey.isPressed();

        cameraInput = EMPTY_INPUT;

        changedViewSettings = staticView.getValue();
        if (changedViewSettings) {
            previousFovScale = mc.options.getFovEffectScale().getValue();
            previousBobView = mc.options.getBobView().getValue();

            mc.options.getFovEffectScale().setValue(0.0);
            mc.options.getBobView().setValue(false);
        }

        active = true;
        mc.options.setPerspective(Perspective.FIRST_PERSON);

        if (reloadChunks.getValue()) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();

        boolean wasActive = active;
        active = false;
        keepSneaking = false;
        cameraInput = EMPTY_INPUT;

        playerAtEnable = null;
        worldAtEnable = null;

        if (previousPerspective != null) {
            mc.options.setPerspective(previousPerspective);
            previousPerspective = null;
        }

        if (changedViewSettings) {
            mc.options.getFovEffectScale().setValue(previousFovScale);
            mc.options.getBobView().setValue(previousBobView);
            changedViewSettings = false;
        }

        if (wasActive && reloadChunks.getValue() && mc.world != null) {
            mc.execute(mc.worldRenderer::reload);
        }
    }

    @Override
    public void onDisconnect() {
        if (isEnabled()) {
            setEnabled(false);
        }
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        // Stop Freecam on respawn or dimension/world changes.
        if (mc.player == null
            || mc.world == null
            || mc.player != playerAtEnable
            || mc.world != worldAtEnable) {
            setEnabled(false);
            return;
        }

        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }

        PlayerInput input =
            mc.currentScreen == null ? cameraInput : EMPTY_INPUT;

        Vec3d forward = Vec3d.fromPolar(0, yaw);
        Vec3d right = Vec3d.fromPolar(0, yaw + 90);

        double multiplier = input.sprint() ? 1.0 : 0.5;
        double movementSpeed = speed.getValue() * multiplier;

        double velX = 0;
        double velY = 0;
        double velZ = 0;

        if (input.forward()) {
            velX += forward.x * movementSpeed;
            velZ += forward.z * movementSpeed;
        }

        if (input.backward()) {
            velX -= forward.x * movementSpeed;
            velZ -= forward.z * movementSpeed;
        }

        if (input.right()) {
            velX += right.x * movementSpeed;
            velZ += right.z * movementSpeed;
        }

        if (input.left()) {
            velX -= right.x * movementSpeed;
            velZ -= right.z * movementSpeed;
        }

        boolean forwardOrBackward =
            input.forward() || input.backward();

        boolean leftOrRight =
            input.left() || input.right();

        if (forwardOrBackward && leftOrRight) {
            double diagonal = 1.0 / Math.sqrt(2.0);
            velX *= diagonal;
            velZ *= diagonal;
        }

        if (input.jump()) {
            velY += movementSpeed;
        }

        if (input.sneak()) {
            velY -= movementSpeed;
        }

        prevPos = pos;
        pos = pos.add(velX, velY, velZ);
    }
}
