package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class TriggerBot extends Module {

    private final Setting.NumberSetting cps = addNum(
        "CPS", "Maximum clicks per second", 12.0, 1.0, 20.0, 0.5
    );

    private final Setting.BoolSetting blockHit = addBool(
        "BlockHit", "Also click when looking at a block", false
    );

    private final Setting.BoolSetting cooldownSync = addBool(
        "CooldownSync", "Wait for full cooldown", false
    );

    private long lastClickNanos;

    public TriggerBot() {
        super(
            "TriggerBot",
            "Automatically press attack when looking at a target",
            Category.COMBAT
        );
    }

    @Override
    protected void onEnable() {
        lastClickNanos = 0L;
    }

    @Override
    protected void onDisable() {
        lastClickNanos = 0L;
    }

    /**
     * Called from MinecraftClientMixin immediately before Minecraft
     * handles its normal input events.
     */
    public void queueAttackInput() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isEnabled()
                || mc.player == null
                || mc.world == null
                || mc.interactionManager == null
                || mc.currentScreen != null
                || !mc.isWindowFocused()
                || mc.crosshairTarget == null
                || mc.options.attackKey.isUnbound()) {
            return;
        }

        HitResult target = mc.crosshairTarget;
        boolean validTarget = false;

        if (target instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            validTarget = entity != null && entity.isAttackable();
        } else if (blockHit.getValue()
                && target.getType() == HitResult.Type.BLOCK) {
            validTarget = true;
        }

        if (!validTarget) return;

        float threshold = cooldownSync.getValue() ? 0.99f : 0.85f;
        if (mc.player.getAttackCooldownProgress(0.0f) < threshold) return;

        long now = System.nanoTime();
        long intervalNanos = (long) (1_000_000_000.0 / cps.getValue());

        if (lastClickNanos != 0L
                && now - lastClickNanos < intervalNanos) {
            return;
        }

        /*
         * Queue a press for the currently configured attack key.
         * Minecraft processes it through its usual attack-input path.
         */
        KeyBinding.onKeyPressed(
            KeyBindingHelper.getBoundKeyOf(mc.options.attackKey)
        );

        lastClickNanos = now;
    }
}
