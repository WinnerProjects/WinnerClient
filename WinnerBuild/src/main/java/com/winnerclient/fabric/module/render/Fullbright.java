package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.module.Module;
import net.minecraft.client.MinecraftClient;

public class Fullbright extends Module {
    private double oldGamma;

    public Fullbright() {
        super("Fullbright", "Night vision effect", Category.RENDER);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;
        oldGamma = mc.options.getGamma().getValue();
        mc.options.getGamma().setValue(1.0);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.getGamma().setValue(oldGamma);
        }
    }
}
