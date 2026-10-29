package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.mixin.GameRendererAccessor;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.ArrayList;
import java.util.List;

public class ESP extends Module {
    private final Setting.BoolSetting players = addBool("Players", "Show players", true);
    private final Setting.BoolSetting mobs = addBool("Mobs", "Show mobs", false);
    private final Setting.ColorSetting color = addColor("Color", "ESP color", 0xFF00FF00, true);
    private final Setting.NumberSetting fillPercent = addNum("Fill", "Fill opacity percent", 0.0, 0.0, 100.0, 1.0);
    private final Setting.BoolSetting tracers = addBool("Tracers", "Draw lines to targets", true);
    private final Setting.ColorSetting tracerColor = addColor("TracerColor", "Tracer line color", 0xFF00FF00, true);
    private final Setting.NumberSetting tracerSize = addNum("TracerSize", "Tracer line thickness", 2.0, 0.1, 8.0, 0.1);

    private final List<int[]> screenTracers = new ArrayList<>();
    private final List<int[]> offscreenIndicators = new ArrayList<>();

    public ESP() {
        super("ESP", "See entity hitboxes through walls", Category.RENDER);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!players.getValue() && !mobs.getValue()) return;

        screenTracers.clear();
        offscreenIndicators.clear();

        int boxColor = color.getValue();
        int fillAlpha = (int)(fillPercent.getValue() * 2.55);
        WorldRenderer wr = context.worldRenderer();
        boolean hasTracers = tracers.getValue();

        var camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
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

        for (var entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.isInvisible()) continue;
            boolean isTarget = (players.getValue() && entity instanceof PlayerEntity)
                || (mobs.getValue() && entity instanceof LivingEntity && !(entity instanceof PlayerEntity));
            if (!isTarget) continue;

            if (wr != null) {
                Box bb = entity.getBoundingBox();
                try (var scope = wr.startDrawingGizmos()) {
                    if (fillAlpha > 0) {
                        int fillColor = (boxColor & 0x00FFFFFF) | (Math.min(255, fillAlpha) << 24);
                        GizmoDrawing.box(bb, DrawStyle.filled(fillColor)).ignoreOcclusion();
                    }
                    GizmoDrawing.box(bb, DrawStyle.stroked(boxColor, 2.0f)).ignoreOcclusion();
                }
            }

            if (hasTracers) {
                int[] sp = projectToScreen(entity.getEyePos(), camPos, forward, right, localUp, fovScaleH, fovScaleV, w, h);
                if (sp != null) {
                    Vec3d rel = entity.getEyePos().subtract(camPos);
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

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int cx = sw / 2;
        int cy = sh / 2;
        int tc = tracerColor.getValue();
        int thick = Math.max(1, (int)Math.round(tracerSize.getValue()));
        var matrices = context.getMatrices();

        if (tracers.getValue()) {
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
        } else {
            for (int[] sp : offscreenIndicators) {
                int x = sp[0];
                int y = sp[1];
                context.fill(x - 3, y - 3, x + 3, y + 3, tc);
            }
        }
    }
}
