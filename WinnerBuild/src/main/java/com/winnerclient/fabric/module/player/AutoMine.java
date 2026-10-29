package com.winnerclient.fabric.module.player;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class AutoMine extends Module {
    private final Setting.NumberSetting range = addNum("Range", "Mine range", 4.5, 1, 6, 0.5);
    private BlockPos target;

    public AutoMine() {
        super("AutoMine", "Auto mine blocks in front of you", Category.PLAYER);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (mc.interactionManager.isBreakingBlock() || mc.player.isUsingItem()) return;

        double r = range.getValue();
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);

        for (double d = 0; d < r; d += 0.5) {
            Vec3d check = eyePos.add(look.multiply(d));
            BlockPos pos = BlockPos.ofFloored(check);
            if (!mc.world.getBlockState(pos).isAir()) {
                target = pos;
                break;
            }
        }

        if (target != null) {
            mc.interactionManager.attackBlock(target, mc.player.getHorizontalFacing());
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            target = null;
        }
    }
}
