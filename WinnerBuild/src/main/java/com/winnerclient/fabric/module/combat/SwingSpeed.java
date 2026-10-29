package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class SwingSpeed extends Module {
    private final Setting.NumberSetting multiplier = addNum("Multiplier", "Attack speed multiplier", 1.0, 0.1, 10.0, 0.1);
    private static double activeMultiplier = 1.0;

    public SwingSpeed() {
        super("SwingSpeed", "Modify hand swing speed", Category.COMBAT);
    }

    public static double getMultiplier() {
        return activeMultiplier;
    }

    @Override
    public void onEnable() {
        activeMultiplier = multiplier.getValue();
    }

    @Override
    public void onDisable() {
        activeMultiplier = 1.0;
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        activeMultiplier = multiplier.getValue();
    }
}
