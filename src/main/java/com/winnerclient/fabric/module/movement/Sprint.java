package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;

public class Sprint extends Module {
    private final Setting.BoolSetting omni    = addBool("OmniSprint", "Sprint in all directions", false);
    private final Setting.BoolSetting onWalk  = addBool("AlwaysOn", "Always sprinting", true);

    public Sprint() {
        super("Sprint", "Automatically keeps sprint enabled", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        boolean moving = mc.player.input.getMovementInput().x > 0 || (omni.getValue() &&
            (mc.player.input.getMovementInput().x != 0 || mc.player.input.getMovementInput().y != 0));

        if (mc.player.hasVehicle() || mc.player.isTouchingWaterOrRain() || mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS))
            return;

        if (mc.player.getHungerManager().getFoodLevel() <= 6) return;

        if (onWalk.getValue() || moving) {
            mc.player.setSprinting(true);
        }
    }
}