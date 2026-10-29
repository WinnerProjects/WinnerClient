package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.entity.*;
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

public class PacketStorageFinder extends Module {
    private final Map<BlockPos, TargetType> detections = new ConcurrentHashMap<>();
    private final List<Box> clusterBoxes = new ArrayList<>();

    public PacketStorageFinder() {
        super("PacketStorageFinder", "Highlights clusters of storage blocks detected via chunk scanning.", Category.MISC);
    }

    private enum TargetType { CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL, HOPPER, SHULKER }

    private final Setting.IntSetting minBlocks = addInt("MinBlocks", "Minimum blocks in cluster", 3, 1, 50);
    private final Setting.IntSetting clusterRadius = addInt("ClusterRadius", "Chunk radius for clustering", 1, 0, 5);
    private final Setting.IntSetting maxY = addInt("MaxY", "Maximum Y", 320, -64, 320);
    private final Setting.IntSetting renderY = addInt("RenderY", "Y level for chunk render", 0, -64, 320);
    private final Setting.NumberSetting renderHeight = addNum("RenderHeight", "Height of cluster boxes", 2.0, 0.5, 10.0, 0.5);
    private final Setting.NumberSetting maxDist = addNum("MaxDist", "Max render distance", 100.0, 10.0, 500.0, 5.0);
    private final Setting.ColorSetting boxColor = addColor("BoxColor", "Cluster box fill color", 0x44FF5500, true);
    private final Setting.ColorSetting lineColor = addColor("LineColor", "Cluster outline color", 0xCCFF5500, true);
    private final Setting.NumberSetting lineWidth = addNum("LineWidth", "Outline width", 2.0, 0.5, 6.0, 0.5);

    @Override
    protected void onDisable() {
        detections.clear();
        clusterBoxes.clear();
    }

    private TargetType getTargetType(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return TargetType.CHEST;
        if (be instanceof TrappedChestBlockEntity) return TargetType.TRAPPED_CHEST;
        if (be instanceof EnderChestBlockEntity) return TargetType.ENDER_CHEST;
        if (be instanceof BarrelBlockEntity) return TargetType.BARREL;
        if (be instanceof HopperBlockEntity) return TargetType.HOPPER;
        if (be instanceof ShulkerBoxBlockEntity) return TargetType.SHULKER;
        return null;
    }

    private void updateClusters() {
        clusterBoxes.clear();
        if (detections.isEmpty()) return;

        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos pos : detections.keySet()) {
            if (visited.contains(pos)) continue;

            Set<BlockPos> cluster = new HashSet<>();
            Queue<BlockPos> queue = new LinkedList<>();
            queue.add(pos);
            visited.add(pos);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                cluster.add(current);
                int range = clusterRadius.getValue() * 16;
                for (BlockPos other : detections.keySet()) {
                    if (visited.contains(other)) continue;
                    if (Math.abs(other.getX() - current.getX()) <= range
                        && Math.abs(other.getZ() - current.getZ()) <= range) {
                        visited.add(other);
                        queue.add(other);
                    }
                }
            }

            if (cluster.size() >= minBlocks.getValue()) {
                int minX = cluster.stream().mapToInt(BlockPos::getX).min().orElse(0);
                int maxX = cluster.stream().mapToInt(BlockPos::getX).max().orElse(0);
                int minZ = cluster.stream().mapToInt(BlockPos::getZ).min().orElse(0);
                int maxZ = cluster.stream().mapToInt(BlockPos::getZ).max().orElse(0);
                double ry = renderY.getValue();
                double rh = renderHeight.getValue();
                clusterBoxes.add(new Box(minX, ry, minZ, maxX + 1, ry + rh, maxZ + 1));
            }
        }
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        detections.clear();
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int viewDist = mc.options.getViewDistance().getValue();
        int camChunkX = BlockPos.ofFloored(playerPos).getX() >> 4;
        int camChunkZ = BlockPos.ofFloored(playerPos).getZ() >> 4;
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        double maxDistSq = maxDist.getValue() * maxDist.getValue();

        for (int cx = camChunkX - viewDist; cx <= camChunkX + viewDist; cx++) {
            for (int cz = camChunkZ - viewDist; cz <= camChunkZ + viewDist; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be == null || be.isRemoved()) continue;
                    BlockPos pos = be.getPos();
                    if (pos.getY() > maxY.getValue()) continue;
                    if (pos.getSquaredDistance(camPos.x, camPos.y, camPos.z) > maxDistSq) continue;
                    if (getTargetType(be) != null) {
                        detections.put(pos, getTargetType(be));
                    }
                }
            }
        }
        updateClusters();
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        WorldRenderer wr = context.worldRenderer();
        if (wr == null) return;

        int bc = boxColor.getValue();
        int lc = lineColor.getValue();
        double lw = lineWidth.getValue();

        for (Box box : clusterBoxes) {
            try (var scope = wr.startDrawingGizmos()) {
                int fillColor = (bc & 0x00FFFFFF) | (Math.min(255, (bc >> 24) & 0xFF) << 24);
                GizmoDrawing.box(box, DrawStyle.filled(fillColor)).ignoreOcclusion();
                GizmoDrawing.box(box, DrawStyle.stroked(lc, (float) lw)).ignoreOcclusion();
            }
        }
    }
}
