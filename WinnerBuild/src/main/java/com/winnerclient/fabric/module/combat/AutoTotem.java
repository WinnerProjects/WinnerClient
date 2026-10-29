package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {
    private final Setting.NumberSetting healthThreshold = addNum("HealthThreshold", "Health to swap at", 20.0, 1.0, 20.0, 1.0);
    private final Setting.BoolSetting always = addBool("Always", "Always keep totem in offhand", true);

    public AutoTotem() {
        super("AutoTotem", "Auto-equip Totem of Undying", Category.COMBAT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        float health = mc.player.getHealth();
        if (!always.getValue() && health > healthThreshold.getValue().floatValue()) return;

        ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.isOf(Items.TOTEM_OF_UNDYING)) return;

        int invSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().main.get(i);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                invSlot = i;
                break;
            }
        }
        if (invSlot == -1) return;

        int screenSlot = invSlot < 9 ? invSlot + 36 : invSlot;

        var handler = mc.player.currentScreenHandler;
        mc.interactionManager.clickSlot(handler.syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
    }
}
