package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

public class HoleESP extends Module {
    private final Setting.NumberSetting range = addNum("Range", "Hole search range", 6, 2, 16, 1);
    private final Setting.ColorSetting color = addColor("Color", "Hole outline color", 0xFF00FF00, true);

    public HoleESP() {
        super("HoleESP", "See bedrock holes", Category.RENDER);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        WorldRenderer wr = context.worldRenderer();
        if (wr == null) return;

        try (var scope = wr.startDrawingGizmos()) {
            int c = color.getValue();
            var style = DrawStyle.stroked(c, 1.5f);
            BlockPos playerPos = mc.player.getBlockPos();
            int r = (int)(double)range.getValue();

            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, -1, z);
                    if (isHole(mc, pos)) {
                        GizmoDrawing.box(new Box(pos), style);
                    }
                }
            }
        }
    }

    private boolean isHole(MinecraftClient mc, BlockPos pos) {
        BlockState feet = mc.world.getBlockState(pos.up());
        BlockState head = mc.world.getBlockState(pos.up(2));
        if (!feet.isAir() || !head.isAir()) return false;
        BlockState north = mc.world.getBlockState(pos.north());
        BlockState south = mc.world.getBlockState(pos.south());
        BlockState east = mc.world.getBlockState(pos.east());
        BlockState west = mc.world.getBlockState(pos.west());
        BlockState floor = mc.world.getBlockState(pos);
        return floor.isOf(Blocks.BEDROCK) && north.isOf(Blocks.BEDROCK) && south.isOf(Blocks.BEDROCK)
            && east.isOf(Blocks.BEDROCK) && west.isOf(Blocks.BEDROCK);
    }
}
