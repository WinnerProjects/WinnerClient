package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SeedChunkFinder extends Module {
    private final Map<ChunkPos, DetectionType> modifiedChunks = new ConcurrentHashMap<>();

    public SeedChunkFinder() {
        super("SeedChunkFinder", "Marks chunks modified by players (horizontal deepslate etc.) with colored beacon lines.", Category.MISC);
    }

    private enum DetectionType { DEEPSLATE, ILLEGAL_BLOCK }

    private final Setting.StringSetting worldSeed = addString("WorldSeed", "World seed (stored for reference)", "");
    private final Setting.BoolSetting detectIllegal = addBool("DetectIllegal", "Detect unnatural blocks underground (cobblestone, wood, torches)", true);
    private final Setting.IntSetting renderRadius = addInt("RenderRadius", "Grid render radius", 64, 16, 256);
    private final Setting.IntSetting renderY = addInt("RenderY", "Y level for ESP render", 0, -64, 320);
    private final Setting.BoolSetting filledEsp = addBool("Filled", "Draw filled chunk boxes", true);
    private final Setting.ColorSetting espColor = addColor("ESPColor", "ESP color", 0xCCFF5555, true);
    private final Setting.NumberSetting lineWidth = addNum("LineWidth", "Outline width", 2.0, 0.5, 6.0, 0.5);

    private int tickCounter;

    @Override
    protected void onDisable() {
        modifiedChunks.clear();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        tickCounter++;
        if (tickCounter % 40 != 0) return;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int px = BlockPos.ofFloored(playerPos).getX() >> 4;
        int pz = BlockPos.ofFloored(playerPos).getZ() >> 4;
        int radius = 8;

        for (int cx = px - radius; cx <= px + radius; cx++) {
            for (int cz = pz - radius; cz <= pz + radius; cz++) {
                ChunkPos cpos = new ChunkPos(cx, cz);
                if (modifiedChunks.containsKey(cpos)) continue;

                WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                int bx = cx << 4;
                int bz = cz << 4;

                outer:
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        for (int y = mc.world.getBottomY(); y < 60; y++) {
                            BlockPos pos = new BlockPos(bx + dx, y, bz + dz);
                            BlockState state = mc.world.getBlockState(pos);
                            if (isRotatedDeepslate(state)) {
                                modifiedChunks.put(cpos, DetectionType.DEEPSLATE);
                                break outer;
                            }
                            if (detectIllegal.getValue() && isIllegalBlock(state.getBlock(), y)) {
                                modifiedChunks.put(cpos, DetectionType.ILLEGAL_BLOCK);
                                break outer;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isRotatedDeepslate(BlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.DEEPSLATE && block != Blocks.COBBLED_DEEPSLATE) return false;
        if (!(block instanceof PillarBlock)) return false;
        Direction.Axis axis = state.get(PillarBlock.AXIS);
        return axis != Direction.Axis.Y;
    }

    private boolean isIllegalBlock(Block block, int y) {
        if (y > 20) return false;
        return block == Blocks.COBBLESTONE || block == Blocks.OAK_PLANKS
            || block == Blocks.OAK_LOG || block == Blocks.TORCH
            || block == Blocks.LADDER || block == Blocks.CRAFTING_TABLE
            || block == Blocks.FURNACE || block == Blocks.CHEST;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        WorldRenderer wr = context.worldRenderer();
        if (wr == null) return;

        int ry = renderY.getValue();
        int color = espColor.getValue();
        double lw = lineWidth.getValue();
        boolean fill = filledEsp.getValue();

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int pp = BlockPos.ofFloored(playerPos).getX() >> 4;
        int qq = BlockPos.ofFloored(playerPos).getZ() >> 4;
        int rr = renderRadius.getValue() >> 4;

        for (Map.Entry<ChunkPos, DetectionType> entry : modifiedChunks.entrySet()) {
            ChunkPos cp = entry.getKey();
            if (Math.abs(cp.x - pp) > rr || Math.abs(cp.z - qq) > rr) continue;

            int bx = cp.getStartX();
            int bz = cp.getStartZ();
            Box box = new Box(bx, ry, bz, bx + 16, ry + 1, bz + 16);

            try (var scope = wr.startDrawingGizmos()) {
                if (fill) {
                    GizmoDrawing.box(box, DrawStyle.filled(color)).ignoreOcclusion();
                }
                GizmoDrawing.box(box, DrawStyle.stroked(color, (float) lw)).ignoreOcclusion();
            }
        }
    }
}
