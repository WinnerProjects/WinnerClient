package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class Step extends Module {
    private final Setting.NumberSetting height = addNum("Height", "Step height", 1.5, 0.5, 10.0, 0.5);
    private final Setting.ModeSetting mode = addMode("Mode", "Step mode", "Motion", "Motion", "Packet", "NCP");

    public Step() {
        super("Step", "Automatically steps up full blocks", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isInLava()) return;

        float h = (float)(double)height.getValue();
        String m = mode.getValue();

        switch (m) {
            case "Motion":
                if (mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue() != h) {
                    mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(h);
                }
                break;
            case "Packet":
                if (isCollidingHorizontally(mc) && mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue() < 0.6) {
                    mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(1.0);
                }
                doPacketStep(mc, h);
                break;
            case "NCP":
                doNCPStep(mc, h);
                break;
        }
    }

    private boolean isCollidingHorizontally(MinecraftClient mc) {
        net.minecraft.util.math.Box box = mc.player.getBoundingBox().expand(0.1, 0, 0.1).offset(0, 1, 0);
        return !mc.world.isSpaceEmpty(mc.player, box);
    }

    private void doPacketStep(MinecraftClient mc, float h) {
        if (!isCollidingHorizontally(mc)) return;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        double[] yOffsets;
        if (h <= 1.0) yOffsets = new double[]{0.42, 0.75};
        else if (h <= 1.5) yOffsets = new double[]{0.42, 0.75, 0.63, 0.58, 0.44, 0.30, 0.15};
        else yOffsets = new double[]{0.42, 0.75, 0.63, 0.58, 0.44, 0.30, 0.15, 0.1};
        for (double off : yOffsets) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(new Vec3d(x, y + off, z), false, false));
        }
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(new Vec3d(x, y + h, z), false, false));
    }

    private void doNCPStep(MinecraftClient mc, float h) {
        if (!isCollidingHorizontally(mc)) return;
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + 0.42, mc.player.getZ());
        mc.player.setVelocity(mc.player.getVelocity().add(0, 0.42, 0));
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(0.6);
        }
    }
}