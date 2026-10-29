package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

public class BlockOverlay extends Module {
    private final Setting.ColorSetting color = addColor("Color", "Overlay color", 0x506B5CE7, true);

    public BlockOverlay() {
        super("BlockOverlay", "Show block selection overlay", Category.RENDER);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        WorldRenderer wr = context.worldRenderer();
        if (wr == null) return;

        try (var scope = wr.startDrawingGizmos()) {
            int c = color.getValue();
            var style = DrawStyle.filledAndStroked(c, 2.0f, (c & 0x00FFFFFF) | 0xFF000000);
            BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
            BlockState state = mc.world.getBlockState(hit.getBlockPos());
            if (!state.isAir()) {
                GizmoDrawing.box(new Box(hit.getBlockPos()), style);
            }
        }
    }
}
