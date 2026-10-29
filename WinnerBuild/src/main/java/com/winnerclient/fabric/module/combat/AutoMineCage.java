package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.*;
import net.minecraft.util.math.Direction;

import java.util.*;

public class AutoMineCage extends Module {
    private final Setting.NumberSetting range = addNum("Range", "Target range", 6.0, 2.0, 10.0, 0.5);
    private final Setting.BoolSetting silentRotate = addBool("SilentRotate", "Silent rotations", true);

    private BlockPos currentTarget;
    private int tickCounter;

    public AutoMineCage() {
        super("AutoMineCage", "Break cage blocks around trapped targets", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        currentTarget = null;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        PlayerEntity target = findTarget(mc);
        if (target == null) {
            currentTarget = null;
            return;
        }

        BlockPos targetPos = BlockPos.ofFloored(target.getEntityPos());
        BlockPos[] cagePositions = getCagePositions(targetPos);

        BlockPos toBreak = null;
        for (BlockPos pos : cagePositions) {
            if (mc.world.getBlockState(pos).isAir()) continue;
            double d = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (d > range.getValue() * range.getValue()) continue;
            if (toBreak == null || d < mc.player.squaredDistanceTo(Vec3d.ofCenter(toBreak))) {
                toBreak = pos;
            }
        }

        if (toBreak == null) {
            currentTarget = null;
            return;
        }

        if (!toBreak.equals(currentTarget)) {
            currentTarget = toBreak;
            tickCounter = 0;
        }

        if (silentRotate.getValue()) {
            lookAt(Vec3d.ofCenter(toBreak));
        }

        tickCounter++;
        int slot = mc.player.getInventory().selectedSlot;
        Direction dir = Direction.DOWN;
        Vec3d center = Vec3d.ofCenter(toBreak);
        Vec3d hitPos = new Vec3d(
                center.x + dir.getOffsetX() * 0.5,
                center.y + dir.getOffsetY() * 0.5,
                center.z + dir.getOffsetZ() * 0.5
        );

        if (tickCounter == 1) {
            mc.player.networkHandler.sendPacket(
                    new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, toBreak, dir, slot));
        }
        mc.player.networkHandler.sendPacket(
                new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, toBreak, dir, slot));

        if (mc.world.getBlockState(toBreak).isAir()) {
            currentTarget = null;
        }
    }

    private BlockPos[] getCagePositions(BlockPos target) {
        int y = target.getY();
        int x = target.getX();
        int z = target.getZ();
        return new BlockPos[]{
                new BlockPos(x + 1, y, z),
                new BlockPos(x - 1, y, z),
                new BlockPos(x, y, z + 1),
                new BlockPos(x, y, z - 1)
        };
    }

    private PlayerEntity findTarget(MinecraftClient mc) {
        double r = range.getValue();
        return mc.world.getEntitiesByClass(PlayerEntity.class,
                mc.player.getBoundingBox().expand(r),
                e -> e != mc.player && e.isAlive()
                        && !e.isCreative() && !e.isSpectator()
                        && !mc.player.isTeammate(e))
                .stream()
                .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
                .orElse(null);
    }

    private void lookAt(Vec3d target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d eyes = mc.player.getEyePos();
        Vec3d diff = target.subtract(eyes);
        double yaw = Math.atan2(-diff.x, diff.z) * 180.0 / Math.PI;
        double pitch = -Math.asin(diff.y / diff.length()) * 180.0 / Math.PI;
        mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround((float) yaw, (float) pitch, mc.player.isOnGround(), true));
    }
}
