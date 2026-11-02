package com.winnerclient.fabric.module.player;

import com.winnerclient.fabric.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

public class XCarry extends Module {
    public XCarry() {
        super(
            "XCarry",
            "Carry items in the player crafting slots",
            Category.PLAYER
        );
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.getNetworkHandler() == null) return;

        // Let the server close the player inventory normally.
        mc.getNetworkHandler().sendPacket(
            new CloseHandledScreenC2SPacket(
                mc.player.playerScreenHandler.syncId
            )
        );
    }
}
