package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Scaffold extends Module {
    private final Setting.BoolSetting tower = addBool("Tower", "Auto tower when holding down", true);
    private final Setting.BoolSetting swing = addBool("Swing", "Swing hand when placing", true);
    private final Setting.ModeSetting acMode = addMode("ACMode", "Anti-cheat bypass mode", "None", "None", "Rotate");
    private int lastSlot = -1;
    private float prevYaw, prevPitch;

    public Scaffold() {
        super("Scaffold", "Auto place blocks under you", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        ClientPlayerEntity p = mc.player;
        BlockPos below = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        if (!mc.world.getBlockState(below).isAir()) return;

        int slot = getBlockSlot(mc);
        if (slot == -1) return;

        String mode = acMode.getValue();
        if (mode.equals("Rotate")) {
            prevYaw = p.getYaw();
            prevPitch = p.getPitch();
            Vec3d eyePos = p.getEyePos();
            Vec3d targetPos = Vec3d.ofCenter(below);
            Vec3d diff = targetPos.subtract(eyePos);
            float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
            float pitch = (float) Math.toDegrees(-Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
            p.setYaw(yaw);
            p.setPitch(pitch);
            p.setHeadYaw(yaw);
        }

        int prevSlot = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);

        Vec3d hitPos = Vec3d.ofCenter(below);
        Direction side = Direction.UP;
        BlockHitResult hit = new BlockHitResult(hitPos, side, below, false);

        if (swing.getValue()) p.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);

        p.getInventory().setSelectedSlot(prevSlot);

        if (mode.equals("Rotate")) {
            p.setYaw(prevYaw);
            p.setPitch(prevPitch);
            p.setHeadYaw(prevYaw);
        }
    }

    private int getBlockSlot(MinecraftClient mc) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && lastSlot != -1) {
            mc.player.getInventory().setSelectedSlot(lastSlot);
            lastSlot = -1;
        }
    }
}
