package com.winnerclient.fabric.module.client;

import com.winnerclient.fabric.SelfDestructScreen;
import com.winnerclient.fabric.module.Module;
import net.minecraft.client.MinecraftClient;

public class SelfDestruct extends Module {
    public SelfDestruct() {
        super("SelfDestruct", "Close WinnerClient modules gracefully", Category.CLIENT);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new SelfDestructScreen());
        setEnabled(false);
    }
}
