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

        /*
         * If no screen is open and the player inventory handler is active,
         * let the server clean up the crafting grid.
         *
         * If a screen is open, don't force-close a handler underneath it.
         * When the player inventory is closed normally after disabling
         * XCarry, its close packet will be sent normally.
         */
        if (mc.currentScreen == null
                && mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
            mc.getNetworkHandler().sendPacket(
                new CloseHandledScreenC2SPacket(
                    mc.player.playerScreenHandler.syncId
                )
            );
        }
    }
}
