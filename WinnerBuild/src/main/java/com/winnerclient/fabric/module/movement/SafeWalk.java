package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import net.minecraft.client.MinecraftClient;

public class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", "Prevents falling off edges", Category.MOVEMENT);
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
