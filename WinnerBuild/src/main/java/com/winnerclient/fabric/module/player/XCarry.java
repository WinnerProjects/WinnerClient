package com.winnerclient.fabric.module.player;

import com.winnerclient.fabric.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class XCarry extends Module {

    public XCarry() {
        super("XCarry", "Carry items in crafting slots", Category.PLAYER);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return;

        int[] craftingSlots = {1, 2, 3, 4};
        for (int slot : craftingSlots) {
            if (!mc.player.currentScreenHandler.getSlot(slot).getStack().isEmpty()) {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    slot, 0, SlotActionType.QUICK_MOVE, mc.player);
            }
        }
    }
}
