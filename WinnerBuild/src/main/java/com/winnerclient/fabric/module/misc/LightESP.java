package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LightESP extends Module {
    private final Map<BlockPos, Integer> lightCache = new ConcurrentHashMap<>();

    public LightESP() {
        super("LightESP", "Detect light-emitting blocks (torches, glowstone, lanterns, etc.) in loaded chunks.", Category.MISC);
    }

    private final Setting.IntSetting chunkRadius = addInt("ChunkRadius", "Scan radius in chunks", 4, 1, 16);
    private final Setting.IntSetting minLightLevel = addInt("MinLightLevel", "Minimum light level to detect", 7, 0, 15);
    private final Setting.IntSetting minY = addInt("MinY", "Minimum Y", -64, -64, 320);
    private final Setting.IntSetting maxY = addInt("MaxY", "Maximum Y", 320, -64, 320);
    private final Setting.NumberSetting maxDist = addNum("MaxDist", "Max render distance", 80.0, 10.0, 500.0, 5.0);
    private final Setting.BoolSetting filterNatural = addBool("FilterNatural", "Hide natural light sources (lava, magma)", true);
    private final Setting.BoolSetting showTorches = addBool("Torches", "Show torches/lanterns", true);
    private final Setting.BoolSetting showGlowstone = addBool("Glowstone", "Show glowstone/sea lanterns", true);
    private final Setting.BoolSetting showRedstone = addBool("RedstoneLamps", "Show redstone lamps/torches", true);
    private final Setting.BoolSetting showBeacons = addBool("Beacons", "Show beacons/conduits", true);
    private final Setting.BoolSetting thermalColors = addBool("ThermalColors", "Color blocks by light level", true);
    private final Setting.BoolSetting useFilled = addBool("Filled", "Draw filled boxes", true);
    private final Setting.ColorSetting lineColorSetting = addColor("LineColor", "Outline color", 0xCCFFFF55, true);
    private final Setting.NumberSetting lineWidth = addNum("LineWidth", "Outline width", 1.5, 0.5, 6.0, 0.5);

    private int tickCounter;

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int radius = chunkRadius.getValue();
        int px = BlockPos.ofFloored(playerPos).getX() >> 4;
        int pz = BlockPos.ofFloored(playerPos).getZ() >> 4;

        for (int cx = px - radius; cx <= px + radius; cx++) {
            for (int cz = pz - radius; cz <= pz + radius; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                int bx = cx << 4;
                int bz = cz << 4;
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        for (int y = Math.max(minY.getValue(), -64); y <= Math.min(maxY.getValue(), 320); y++) {
                            BlockPos pos = new BlockPos(bx + dx, y, bz + dz);
                            var state = mc.world.getBlockState(pos);
                            int light = state.getLuminance();
                            if (light < minLightLevel.getValue()) continue;
                            if (filterNatural.getValue()) {
                                var block = state.getBlock();
                                if (block == Blocks.LAVA || block == Blocks.MAGMA_BLOCK
                                    || block == Blocks.FIRE || block == Blocks.SOUL_FIRE
                                    || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) continue;
                            }
                            if (!passesFilters(state.getBlock())) continue;
                            lightCache.put(pos, light);
                        }
                    }
                }
            }
        }

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        double maxDistSq = maxDist.getValue() * maxDist.getValue();
        lightCache.keySet().removeIf(pos ->
            pos.getSquaredDistance(camPos.x, camPos.y, camPos.z) > maxDistSq);
    }

    private boolean passesFilters(Block block) {
        if (showTorches.getValue() && (
            block == Blocks.TORCH || block == Blocks.SOUL_TORCH || block == Blocks.LANTERN
            || block == Blocks.SOUL_LANTERN || block == Blocks.END_ROD
            || block == Blocks.CANDLE || block == Blocks.SEA_PICKLE)) return true;
        if (showGlowstone.getValue() && (
            block == Blocks.GLOWSTONE || block == Blocks.SEA_LANTERN
            || block == Blocks.SHROOMLIGHT || block == Blocks.JACK_O_LANTERN)) return true;
        if (showRedstone.getValue() && (
            block == Blocks.REDSTONE_LAMP || block == Blocks.REDSTONE_TORCH
            || block == Blocks.REDSTONE_BLOCK)) return true;
        if (showBeacons.getValue() && (
            block == Blocks.BEACON || block == Blocks.CONDUIT)) return true;
        return false;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        var camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        WorldRenderer wr = context.worldRenderer();
        if (wr == null) return;

        double maxDistSq = maxDist.getValue() * maxDist.getValue();
        double lw = lineWidth.getValue();
        boolean fill = useFilled.getValue();
        boolean thermal = thermalColors.getValue();
        int lc = lineColorSetting.getValue();
        int fillAlpha = 60;

        for (Map.Entry<BlockPos, Integer> entry : lightCache.entrySet()) {
            BlockPos pos = entry.getKey();
            int light = entry.getValue();
            if (pos.getSquaredDistance(camPos.x, camPos.y, camPos.z) > maxDistSq) continue;

            int color;
            if (thermal) {
                float hue = 0.15f * (1.0f - light / 15.0f);
                int rgb = java.awt.Color.HSBtoRGB(hue, 0.9f, 1.0f);
                color = (Math.min(255, fillAlpha) << 24) | (rgb & 0x00FFFFFF);
            } else {
                color = lc;
            }

            Box bb = new Box(pos);
            try (var scope = wr.startDrawingGizmos()) {
                if (fill) {
                    int fillColor = thermal ? color : ((lc & 0x00FFFFFF) | (fillAlpha << 24));
                    GizmoDrawing.box(bb, DrawStyle.filled(fillColor)).ignoreOcclusion();
                }
                GizmoDrawing.box(bb, DrawStyle.stroked(thermal ? color : lc, (float) lw)).ignoreOcclusion();
            }
        }
    }
}
