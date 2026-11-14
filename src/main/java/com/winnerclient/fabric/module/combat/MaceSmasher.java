package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

/** Automatically attacks nearby targets with a mace after the configured fall height. */
public class MaceSmasher extends Module {
    private final Setting.NumberSetting height = addNum("Height", "Minimum downward fall distance before attacking", 4.0, 1.0, 30.0, 0.5);
    private final Setting.BoolSetting ignoreWindBurst = addBool("IgnoreWindBurst", "Do not attack while being launched upward by Wind Burst", true);
    private final Setting.NumberSetting aps = addNum("APS", "Attacks per second", 2.0, 0.5, 10.0, 0.5);
    private final Setting.BoolSetting silentSwitch = addBool("SilentSwitch", "Spoof the mace hotbar slot without changing the visible held item", true);
    private final Setting.NumberSetting range = addNum("Range", "Maximum target distance", 4.5, 2.0, 6.0, 0.1);
    private final Setting.BoolSetting players = addBool("Players", "Target players", true);
    private final Setting.BoolSetting hostile = addBool("Hostile", "Target hostile mobs", false);
    private final Setting.BoolSetting passive = addBool("Passive", "Target passive mobs", false);

    private long lastAttackNanos;

    public MaceSmasher() {
        super("MaceSmasher", "Automatically mace-smashes nearby targets", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        lastAttackNanos = 0L;
    }

    @Override
    protected void onDisable() {
        lastAttackNanos = 0L;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;

        // A mace smash must happen while descending. This also prevents Wind Burst's
        // upward launch from being mistaken for usable falling height.
        if (ignoreWindBurst.getValue() && mc.player.getVelocity().y > 0.0) return;
        if (mc.player.fallDistance < height.getValue()) return;

        long now = System.nanoTime();
        long interval = (long) (1_000_000_000.0 / aps.getValue());
        if (lastAttackNanos != 0L && now - lastAttackNanos < interval) return;
        if (mc.player.getAttackCooldownProgress(0.0f) < 0.90f) return;

        int maceSlot = findMaceHotbarSlot(mc);
        if (maceSlot < 0) return;

        Entity target = findTarget(mc);
        if (target == null) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        try {
            if (silentSwitch.getValue() && oldSlot != maceSlot) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
            } else if (!silentSwitch.getValue() && oldSlot != maceSlot) {
                mc.player.getInventory().setSelectedSlot(maceSlot);
            }

            // No client-side crosshair/raycast check is performed. The server still
            // enforces its own reach and combat rules; this cannot override server anti-cheat.
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            lastAttackNanos = now;
        } finally {
            if (oldSlot != maceSlot) {
                if (silentSwitch.getValue()) {
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
                } else {
                    mc.player.getInventory().setSelectedSlot(oldSlot);
                }
            }
        }
    }

    private int findMaceHotbarSlot(MinecraftClient mc) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(Items.MACE)) return slot;
        }
        return -1;
    }

    private Entity findTarget(MinecraftClient mc) {
        Box box = mc.player.getBoundingBox().expand(range.getValue());
        List<Entity> candidates = mc.world.getEntitiesByClass(Entity.class, box,
                entity -> entity != mc.player && entity instanceof LivingEntity living && living.isAlive() && living.isAttackable());

        candidates.removeIf(entity -> {
            if (entity instanceof PlayerEntity player) {
                return !players.getValue() || player.isCreative() || player.isSpectator() || mc.player.isTeammate(player);
            }
            String group = entity.getType().getSpawnGroup().getName();
            if (group.equals("monster")) return !hostile.getValue();
            if (group.equals("creature") || group.equals("ambient") || group.equals("water_creature")) return !passive.getValue();
            return true;
        });

        return candidates.stream().min(Comparator.comparingDouble(mc.player::squaredDistanceTo)).orElse(null);
    }
}
