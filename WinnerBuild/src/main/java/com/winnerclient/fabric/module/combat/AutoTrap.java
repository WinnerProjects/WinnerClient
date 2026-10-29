package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoTrap extends Module {
    private final Setting.NumberSetting range = addNum("Range", "Target range", 6.0, 2.0, 10.0, 0.5);
    private final Setting.NumberSetting blocksPerTick = addNum("BlocksPerTick", "Blocks placed per tick", 2.0, 1.0, 8.0, 1.0);
    private final Setting.BoolSetting silentRotate = addBool("SilentRotate", "Silent rotations", true);

    public AutoTrap() {
        super("AutoTrap", "Automatically trap nearby players with obsidian", Category.COMBAT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        PlayerEntity target = findTarget(mc);
        if (target == null) return;

        BlockPos targetPos = BlockPos.ofFloored(target.getEntityPos());
        List<BlockPos> trapPositions = getTrapPositions(targetPos);
        int placed = 0;
        int maxPlaced = blocksPerTick.getValue().intValue();

        for (BlockPos pos : trapPositions) {
            if (placed >= maxPlaced) break;
            if (!mc.world.getBlockState(pos).isAir()) continue;

            int slot = findObsidianSlot(mc.player);
            if (slot == -1) return;

            Vec3d placeCenter = Vec3d.ofCenter(pos);
            double dist = mc.player.squaredDistanceTo(placeCenter);
            if (dist > range.getValue() * range.getValue()) continue;

            if (tryPlaceBlock(mc, pos, slot)) {
                placed++;
            }
        }
    }

    private PlayerEntity findTarget(MinecraftClient mc) {
        double r = range.getValue();
        List<Entity> entities = mc.world.getEntitiesByClass(Entity.class,
                mc.player.getBoundingBox().expand(r),
                e -> e != mc.player && e instanceof PlayerEntity p && p.isAlive()
                        && !p.isCreative() && !p.isSpectator()
                        && !mc.player.isTeammate(p));

        if (entities.isEmpty()) return null;

        entities.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
        return (PlayerEntity) entities.get(0);
    }

    private List<BlockPos> getTrapPositions(BlockPos target) {
        List<BlockPos> positions = new ArrayList<>();
        int y = target.getY();
        int x = target.getX();
        int z = target.getZ();

        // Surround at head level (y+2) - leave feet + body open for crystal
        positions.add(new BlockPos(x + 1, y + 2, z));
        positions.add(new BlockPos(x - 1, y + 2, z));
        positions.add(new BlockPos(x, y + 2, z + 1));
        positions.add(new BlockPos(x, y + 2, z - 1));

        // Cap above head (y+3)
        positions.add(new BlockPos(x + 1, y + 3, z));
        positions.add(new BlockPos(x - 1, y + 3, z));
        positions.add(new BlockPos(x, y + 3, z + 1));
        positions.add(new BlockPos(x, y + 3, z - 1));
        positions.add(new BlockPos(x, y + 3, z));

        return positions;
    }

    private boolean tryPlaceBlock(MinecraftClient mc, BlockPos pos, int slot) {
        useSlotSilent(mc, slot);

        Vec3d center = Vec3d.ofCenter(pos);
        if (silentRotate.getValue()) {
            lookAt(center);
        }

        Direction placeDir = getPlaceDirection(mc, pos);
        if (placeDir == null) return false;

        BlockPos neighbor = pos.offset(placeDir.getOpposite());
        Vec3d hitPos = Vec3d.ofCenter(neighbor);

        if (silentRotate.getValue()) {
            lookAt(hitPos);
        }

        BlockHitResult hit = new BlockHitResult(hitPos, placeDir, neighbor, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private Direction getPlaceDirection(MinecraftClient mc, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            if (!mc.world.getBlockState(neighbor).isAir()) {
                return dir.getOpposite();
            }
        }
        return Direction.UP;
    }

    private boolean hasLineOfSight(MinecraftClient mc, BlockPos pos) {
        var result = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(),
                Vec3d.ofCenter(pos),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player));
        return result.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    private void useSlotSilent(MinecraftClient mc, int slot) {
        if (slot < 0 || slot > 8) return;
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        if (player.getInventory().selectedSlot == slot) return;
        int prev = player.getInventory().selectedSlot;
        player.getInventory().selectedSlot = slot;
        player.networkHandler.sendPacket(
                new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        player.getInventory().selectedSlot = prev;
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

    private int findObsidianSlot(ClientPlayerEntity player) {
        if (player.getMainHandStack().getItem() == Blocks.OBSIDIAN.asItem()) return player.getInventory().selectedSlot;
        if (player.getOffHandStack().getItem() == Blocks.OBSIDIAN.asItem()) return -2;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() == Blocks.OBSIDIAN.asItem()) return i;
        }
        return -1;
    }
}
