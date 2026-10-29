package com.winnerclient.fabric.module.world;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class Nuker extends Module {
    private final Setting.NumberSetting range = addNum("Range", "Nuker range", 4.0, 1, 6, 0.5);

    public Nuker() {
        super("Nuker", "Auto break blocks around you", Category.WORLD);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.interactionManager.isBreakingBlock()) return;

        double r = range.getValue();
        BlockPos center = BlockPos.ofFloored(mc.player.getEntityPos());

        for (int x = -(int)r; x <= (int)r; x++) {
            for (int y = -(int)r; y <= (int)r; y++) {
                for (int z = -(int)r; z <= (int)r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    double dist = Math.sqrt(center.getSquaredDistance(pos));
                    if (dist > r) continue;
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    mc.interactionManager.attackBlock(pos, mc.player.getHorizontalFacing());
                    mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    return;
                }
            }
        }
    }
}
