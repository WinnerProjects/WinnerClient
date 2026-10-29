package com.winnerclient.fabric.module.player;

import com.winnerclient.fabric.module.Module;
import net.minecraft.client.MinecraftClient;

public class Sneak extends Module {
    public Sneak() {
        super("Sneak", "Auto sneak", Category.PLAYER);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.options.sneakKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.options.sneakKey.setPressed(false);
        }
    }
}
