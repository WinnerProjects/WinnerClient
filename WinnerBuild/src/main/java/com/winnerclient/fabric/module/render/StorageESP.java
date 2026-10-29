package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.mixin.GameRendererAccessor;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.entity.*;
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

import java.util.ArrayList;
import java.util.List;

public class StorageESP extends Module {
    private final Setting.BoolSetting chests = addBool("Chests", "Show chests", true);
    private final Setting.BoolSetting barrels = addBool("Barrels", "Show barrels", true);
    private final Setting.BoolSetting shulkers = addBool("Shulkers", "Show shulkers", true);
    private final Setting.BoolSetting furnaces = addBool("Furnaces", "Show furnaces/smokers/blast furnaces", false);
    private final Setting.BoolSetting redstone = addBool("Redstone", "Show hoppers/droppers/dispensers/comparators", false);
    private final Setting.BoolSetting miscStorage = addBool("MiscStorage", "Show brewing stands/beacons/etc", false);
    private final Setting.ColorSetting color = addColor("Color", "ESP color", 0xCCFFAA00, true);
    private final Setting.NumberSetting fillPercent = addNum("Fill", "Fill opacity percent", 0.0, 0.0, 100.0, 1.0);
    private final Setting.BoolSetting showId = addBool("ShowID", "Show block type name above storage", false);
    private final Setting.NumberSetting loadY = addNum("LoadY", "Y level to load void chunks", -30, -128.0, -1.0, 1.0);
    private final Setting.BoolSetting tracers = addBool("Tracers", "Draw lines to storage blocks", true);
    private final Setting.ColorSetting tracerColor = addColor("TracerColor", "Tracer line color", 0xCCFFAA00, true);
    private final Setting.NumberSetting tracerSize = addNum("TracerSize", "Tracer line thickness", 2.0, 0.1, 8.0, 0.1);

    private final List<int[]> screenTracers = new ArrayList<>();
    private final List<int[]> offscreenIndicators = new ArrayList<>();
    private final List<Object[]> idLabels = new ArrayList<>();
    private long lastVoidLoadTime;

    public StorageESP() {
        super("StorageESP", "See storage containers through walls", Category.RENDER);
    }

    public Double pollVoidLoad() {
        if (!isEnabled()) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.getY() <= -10) return null;
        long now = System.currentTimeMillis();
        if (now - lastVoidLoadTime < 2000) return null;
        lastVoidLoadTime = now;
        return loadY.getValue();
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        screenTracers.clear();
        offscreenIndicators.clear();
        idLabels.clear();

        var camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        int viewDist = mc.options.getViewDistance().getValue();
        int camChunkX = BlockPos.ofFloored(camPos).getX() >> 4;
        int camChunkZ = BlockPos.ofFloored(camPos).getZ() >> 4;

        int boxColor = color.getValue();
        int fillAlpha = (int)(fillPercent.getValue() * 2.55);
        WorldRenderer wr = context.worldRenderer();
        if (wr == null) return;

        float camYaw = camera.getYaw();
        float camPitch = camera.getPitch();
        float pitchRad = camPitch * MathHelper.RADIANS_PER_DEGREE;
        float yawRad = camYaw * MathHelper.RADIANS_PER_DEGREE;
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRad) * MathHelper.cos(pitchRad), -MathHelper.sin(pitchRad), MathHelper.cos(yawRad) * MathHelper.cos(pitchRad));
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d localUp = right.crossProduct(forward).normalize();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        float fovDeg = ((GameRendererAccessor) mc.gameRenderer).invokeGetFov(camera, mc.getRenderTickCounter().getDynamicDeltaTicks(), true);
        double vFovRad = Math.toRadians(fovDeg);
        double fovScaleV = Math.tan(vFovRad / 2);
        double fovScaleH = fovScaleV * w / h;
        boolean hasTracers = tracers.getValue();

        for (int cx = camChunkX - viewDist; cx <= camChunkX + viewDist; cx++) {
            for (int cz = camChunkZ - viewDist; cz <= camChunkZ + viewDist; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be == null || be.isRemoved()) continue;
                    BlockPos pos = be.getPos();
                    if (pos == null) continue;
                    if (pos.getSquaredDistance(camPos.x, camPos.y, camPos.z) > 1048576) continue;

                    boolean match = (chests.getValue() && (be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity || be instanceof EnderChestBlockEntity))
                        || (barrels.getValue() && be instanceof BarrelBlockEntity)
                        || (shulkers.getValue() && be instanceof ShulkerBoxBlockEntity)
                        || (furnaces.getValue() && (be instanceof FurnaceBlockEntity || be instanceof SmokerBlockEntity || be instanceof BlastFurnaceBlockEntity))
                        || (redstone.getValue() && (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity || be instanceof DropperBlockEntity || be instanceof ComparatorBlockEntity || be instanceof CrafterBlockEntity))
                        || (miscStorage.getValue() && (be instanceof BrewingStandBlockEntity || be instanceof BeaconBlockEntity));
                    if (!match) continue;

                    if (showId.getValue()) {
                        Vec3d worldPos = Vec3d.ofCenter(pos);
                        int[] sp = projectToScreen(worldPos, camPos, forward, right, localUp, fovScaleH, fovScaleV, w, h);
                        if (sp != null) {
                            idLabels.add(new Object[]{sp[0], sp[1], blockEntityName(be)});
                        }
                    }

                    Box bb = new Box(pos);

                    try (var scope = wr.startDrawingGizmos()) {
                        if (fillAlpha > 0) {
                            int fillColor = (boxColor & 0x00FFFFFF) | (Math.min(255, fillAlpha) << 24);
                            GizmoDrawing.box(bb, DrawStyle.filled(fillColor)).ignoreOcclusion();
                        }
                        GizmoDrawing.box(bb, DrawStyle.stroked(boxColor, 2.0f)).ignoreOcclusion();
                    }

                    if (hasTracers) {
                        Vec3d worldPos = Vec3d.ofCenter(pos);
                        int[] sp = projectToScreen(worldPos, camPos, forward, right, localUp, fovScaleH, fovScaleV, w, h);
                        if (sp != null) {
                            Vec3d rel = worldPos.subtract(camPos);
                            double df = rel.dotProduct(forward);
                            if (df > 0.01) {
                                screenTracers.add(sp);
                            } else {
                                offscreenIndicators.add(sp);
                            }
                        }
                    }
                }
            }
        }
    }

    private static int[] projectToScreen(Vec3d worldPos, Vec3d camPos, Vec3d forward, Vec3d right, Vec3d up,
                                          double fovScaleH, double fovScaleV, int w, int h) {
        Vec3d rel = worldPos.subtract(camPos);
        double df = rel.dotProduct(forward);
        double cx = w / 2.0;
        double cy = h / 2.0;

        if (df <= 0) {
            Vec3d dir = rel.normalize();
            double hAngle = Math.atan2(dir.dotProduct(right), dir.dotProduct(forward));
            double halfHFov = Math.atan(fovScaleH);
            double sx = cx + (hAngle / halfHFov) * (w / 2.0);
            int ix = (int)Math.round(sx);
            if (ix < 0) ix = 0;
            if (ix > w) ix = w;
            return new int[]{ix, h};
        }

        double absDf = Math.max(df, 0.01);
        double dr = rel.dotProduct(right);
        double du = rel.dotProduct(up);
        double sx = (dr / (absDf * fovScaleH)) * (w / 2.0) + w / 2.0;
        double sy = (-du / (absDf * fovScaleV)) * (h / 2.0) + h / 2.0;
        double dx = sx - cx;
        double dy = sy - cy;
        if (dx == 0 && dy == 0) return null;
        if (sx < 0 || sx > w || sy < 0 || sy > h) {
            double absDx = Math.abs(dx);
            double absDy = Math.abs(dy);
            if (absDx * h > absDy * w) {
                sx = dx > 0 ? w : 0;
                sy = cy + dy * (sx - cx) / dx;
            } else {
                sy = dy > 0 ? h : 0;
                sx = cx + dx * (sy - cy) / dy;
            }
        }
        return new int[]{(int)Math.round(sx), (int)Math.round(sy)};
    }

    private static String blockEntityName(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return "Chest";
        if (be instanceof TrappedChestBlockEntity) return "Trapped Chest";
        if (be instanceof EnderChestBlockEntity) return "Ender Chest";
        if (be instanceof BarrelBlockEntity) return "Barrel";
        if (be instanceof ShulkerBoxBlockEntity) return "Shulker";
        if (be instanceof FurnaceBlockEntity) return "Furnace";
        if (be instanceof SmokerBlockEntity) return "Smoker";
        if (be instanceof BlastFurnaceBlockEntity) return "Blast Furnace";
        if (be instanceof HopperBlockEntity) return "Hopper";
        if (be instanceof DispenserBlockEntity) return "Dispenser";
        if (be instanceof DropperBlockEntity) return "Dropper";
        if (be instanceof ComparatorBlockEntity) return "Comparator";
        if (be instanceof CrafterBlockEntity) return "Crafter";
        if (be instanceof BrewingStandBlockEntity) return "Brewing Stand";
        if (be instanceof BeaconBlockEntity) return "Beacon";
        return be.getClass().getSimpleName();
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        boolean hasTracers = tracers.getValue();
        boolean hasIds = showId.getValue();

        if (!hasTracers && !hasIds) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int cx = sw / 2;
        int cy = sh / 2;
        int tc = tracerColor.getValue();
        int thick = Math.max(1, (int)Math.round(tracerSize.getValue()));
        var matrices = context.getMatrices();

        for (int[] sp : screenTracers) {
            int dx = sp[0] - cx;
            int dy = sp[1] - cy;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 0.5) continue;
            matrices.pushMatrix();
            matrices.translate(cx, cy);
            matrices.rotate((float)Math.atan2(dy, dx));
            context.fill(0, -thick / 2, (int)Math.round(len), -thick / 2 + thick, tc);
            matrices.popMatrix();
        }

        for (int[] sp : offscreenIndicators) {
            int x = sp[0];
            int y = sp[1];
            int dx = x - cx;
            int dy = y - cy;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 0.5) continue;
            matrices.pushMatrix();
            matrices.translate(cx, cy);
            matrices.rotate((float)Math.atan2(dy, dx));
            context.fill(0, -thick / 2, (int)Math.round(len), -thick / 2 + thick, tc);
            matrices.popMatrix();
            context.fill(x - 3, y - 3, x + 3, y + 3, tc);
        }

        if (hasIds) {
            int labelColor = color.getValue() & 0x00FFFFFF | 0xFF000000;
            for (Object[] label : idLabels) {
                int lx = (int) label[0];
                int ly = (int) label[1] - 20;
                String name = (String) label[2];
                context.drawText(mc.textRenderer, name, lx - mc.textRenderer.getWidth(name) / 2, ly, labelColor, true);
            }
        }
    }
}
