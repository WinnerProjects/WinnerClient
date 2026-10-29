package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.CameraOverriddenEntity;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public class Freelook extends Module {
    private final Setting.NumberSetting cameraDistance = addNum("Distance", "Third-person camera distance", 20.0, 1.0, 50.0, 0.5);
    private final Setting.BoolSetting noClip = addBool("NoClip", "Camera can go through walls", false);

    private Perspective prevPerspective;

    public Freelook() {
        super("Freelook", "Look around freely while your body stays still", Category.RENDER);
        setHoldToUse(true);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player instanceof CameraOverriddenEntity coe) {
            coe.freelook$setCameraPitch(mc.player.getPitch());
            coe.freelook$setCameraYaw(mc.player.getYaw());
            coe.freelook$setSavedYaw(mc.player.getYaw());
            coe.freelook$setSavedPitch(mc.player.getPitch());
        }
        prevPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player instanceof CameraOverriddenEntity coe) {
            mc.player.setYaw(coe.freelook$getSavedYaw());
            mc.player.setPitch(coe.freelook$getSavedPitch());
        }
        if (prevPerspective != null) {
            mc.options.setPerspective(prevPerspective);
            prevPerspective = null;
        }
    }

    public static boolean isActive() {
        Module m = ModuleManager.getModule("Freelook");
        return m != null && m.isEnabled();
    }

    public static double getCameraDistance() {
        Freelook fl = (Freelook) ModuleManager.getModule("Freelook");
        return fl != null ? fl.cameraDistance.getValue() : 10.0;
    }

    public static boolean hasNoClip() {
        Freelook fl = (Freelook) ModuleManager.getModule("Freelook");
        return fl != null && fl.noClip.getValue();
    }
}
