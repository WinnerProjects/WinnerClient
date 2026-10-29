package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.Hand;

public class TriggerBot extends Module {
    private final Setting.NumberSetting cps = addNum("CPS", "Clicks per second", 12.0, 1.0, 20.0, 0.5);
    private final Setting.BoolSetting blockHit = addBool("BlockHit", "Attack on block hit", false);
    private final Setting.BoolSetting cooldownSync = addBool("CooldownSync", "Wait for full cooldown", false);
    private int tickCounter;

    public TriggerBot() {
        super("TriggerBot", "Auto attack when cursor over entity", Category.COMBAT);
    }

    @Override
    public void onEnable() { tickCounter = 0; }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.crosshairTarget == null || mc.interactionManager == null) return;

        tickCounter++;
        int delay = Math.max(1, (int)(20.0 / cps.getValue()));
        if (tickCounter % delay != 0) return;

        float threshold = cooldownSync.getValue() ? 0.99f : 0.85f;
        if (mc.player.getAttackCooldownProgress(0) < threshold) return;

        if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
            Entity e = entityHit.getEntity();
            if (e != null && e.isAttackable()) {
                mc.interactionManager.attackEntity(mc.player, e);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}