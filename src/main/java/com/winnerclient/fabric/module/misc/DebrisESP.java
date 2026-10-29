package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DebrisESP extends Module {
    private final Map<BlockPos, long[]> detections = new ConcurrentHashMap<>();
    private final Set<Long> scannedChunks = new HashSet<>();
    private int tickCounter;

    public DebrisESP() {
        super("DebrisESP", "Detect ancient debris blocks by scanning loaded chunks.", Category.MISC);
    }

    private final Setting.ColorSetting debrisColor = addColor("Color", "Debris highlight color", 0xCCFF5555, true);
    private final Setting.NumberSetting fillPercent = addNum("Fill", "Fill opacity percent", 30.0, 0.0, 100.0, 1.0);
    private final Setting.NumberSetting lineWidth = addNum("LineWidth", "Outline width", 2.0, 0.5, 6.0, 0.5);
    private final Setting.BoolSetting tracers = addBool("Tracers", "Draw tracers", true);
    private final Setting.ColorSetting tracerColor = addColor("TracerColor", "Tracer color", 0xCCFF5555, true);
    private final Setting.IntSetting scanRadius = addInt("ScanRadius", "Scan radius in chunks", 4, 1, 16);
    private final Setting.IntSetting minY = addInt("MinY", "Minimum Y", 8, -64, 320);
    private final Setting.IntSetting maxY = addInt("MaxY", "Maximum Y", 120, -64, 320);
    private final Setting.NumberSetting maxDist = addNum("MaxDist", "Max render distance", 100.0, 10.0, 500.0, 5.0);
    private final Setting.BoolSetting clearOnDisable = addBool("ClearOnDisable", "Clear on disable", true);

    private final List<int[]> screenTracers = new ArrayList<>();

    @Override
    protected void onDisable() {
        if (clearOnDisable.getValue()) { detections.clear(); scannedChunks.clear(); }
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        tickCounter++;
        if (tickCounter % 10 != 0) return;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int radius = scanRadius.getValue();
        int px = BlockPos.ofFloored(playerPos).getX() >> 4;
        int pz = BlockPos.ofFloored(playerPos).getZ() >> 4;

        scannedChunks.removeIf(key -> {
            int cx = (int) (long) key;
            int cz = (int) (key >> 32);
            return Math.abs(cx - px) > radius + 2 || Math.abs(cz - pz) > radius + 2;
        });

        for (int cx = px - radius; cx <= px + radius; cx++) {
            for (int cz = pz - radius; cz <= pz + radius; cz++) {
                long chunkKey = (long) cz << 32 | (cx & 0xFFFFFFFFL);
                if (scannedChunks.contains(chunkKey)) continue;

                WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                scannedChunks.add(chunkKey);
                int bx = cx << 4;
                int bz = cz << 4;
                int minYVal = Math.max(minY.getValue(), -64);
                int maxYVal = Math.min(maxY.getValue(), 320);

                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        for (int y = minYVal; y <= maxYVal; y++) {
                            BlockPos pos = new BlockPos(bx + dx, y, bz + dz);
                            if (mc.world.getBlockState(pos).isOf(Blocks.ANCIENT_DEBRIS)) {
                                detections.put(pos, new long[]{pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis()});
                            }
                        }
                    }
                }
                return;
            }
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        screenTracers.clear();

        var camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        WorldRenderer wr = context.worldRenderer();
        if (wr == null) return;

        double maxDistSq = maxDist.getValue() * maxDist.getValue();
        double fillAlpha = fillPercent.getValue() / 100.0;
        double lw = lineWidth.getValue();
        int dc = debrisColor.getValue();

        for (Map.Entry<BlockPos, long[]> entry : detections.entrySet()) {
            long[] data = entry.getValue();
            double dx = data[0];
            double dy = data[1];
            double dz = data[2];
            Vec3d pos = new Vec3d(dx + 0.5, dy + 0.5, dz + 0.5);
            if (pos.squaredDistanceTo(camPos) > maxDistSq) continue;

            Box bb = new Box(dx, dy, dz, dx + 1, dy + 1, dz + 1);
            try (var scope = wr.startDrawingGizmos()) {
                if (fillAlpha > 0) {
                    int fillColor = (dc & 0x00FFFFFF) | ((int) (Math.min(255, fillAlpha * 255)) << 24);
                    GizmoDrawing.box(bb, DrawStyle.filled(fillColor)).ignoreOcclusion();
                }
                GizmoDrawing.box(bb, DrawStyle.stroked(dc, (float) lw)).ignoreOcclusion();
            }
        }
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !tracers.getValue()) return;
        if (detections.isEmpty()) return;

        var camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        float camYaw = camera.getYaw();
        float camPitch = camera.getPitch();
        float pitchRad = camPitch * MathHelper.RADIANS_PER_DEGREE;
        float yawRad = camYaw * MathHelper.RADIANS_PER_DEGREE;
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRad) * MathHelper.cos(pitchRad), -MathHelper.sin(pitchRad), MathHelper.cos(yawRad) * MathHelper.cos(pitchRad));
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.crossProduct(forward).normalize();

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int cx = w / 2;
        int cy = h / 2;
        int tc = tracerColor.getValue();
        int thick = Math.max(1, (int) Math.round(lineWidth.getValue()));
        double maxDistSq = maxDist.getValue() * maxDist.getValue();
        com.winnerclient.fabric.mixin.GameRendererAccessor gra = (com.winnerclient.fabric.mixin.GameRendererAccessor) mc.gameRenderer;
        float fovDeg = gra.invokeGetFov(camera, mc.getRenderTickCounter().getDynamicDeltaTicks(), true);
        double vFovRad = Math.toRadians(fovDeg);
        double fovScaleV = Math.tan(vFovRad / 2);
        double fovScaleH = fovScaleV * w / h;

        var matrices = context.getMatrices();
        screenTracers.clear();

        for (long[] data : detections.values()) {
            Vec3d wp = new Vec3d(data[0] + 0.5, data[1] + 0.5, data[2] + 0.5);
            if (wp.squaredDistanceTo(camPos) > maxDistSq) continue;
            Vec3d rel = wp.subtract(camPos);
            double df = rel.dotProduct(forward);
            if (df <= 0.01) continue;
            double dr = rel.dotProduct(right);
            double du = rel.dotProduct(up);
            double absDf = Math.max(df, 0.01);
            double sx = (dr / (absDf * fovScaleH)) * (w / 2.0) + w / 2.0;
            double sy = (-du / (absDf * fovScaleV)) * (h / 2.0) + h / 2.0;
            if (sx < 0 || sx > w || sy < 0 || sy > h) continue;
            screenTracers.add(new int[]{(int) Math.round(sx), (int) Math.round(sy)});
        }

        for (int[] sp : screenTracers) {
            int dx = sp[0] - cx;
            int dy = sp[1] - cy;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 0.5) continue;
            matrices.pushMatrix();
            matrices.translate(cx, cy);
            matrices.rotate((float) Math.atan2(dy, dx));
            context.fill(0, -thick / 2, (int) Math.round(len), -thick / 2 + thick, tc);
            matrices.popMatrix();
        }
    }
}
