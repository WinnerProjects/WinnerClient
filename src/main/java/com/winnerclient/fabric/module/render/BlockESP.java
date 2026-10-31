package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.ConfigManager;
import com.winnerclient.fabric.mixin.GameRendererAccessor;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockESP extends Module {
    private static final int BLOCKS_PER_TICK = 4096;
    private static final int MAX_RESULTS = 1500;

    private final Setting.StringSetting blocks = addString(
        "Blocks",
        "Blocks to highlight",
        "minecraft:diamond_ore,minecraft:deepslate_diamond_ore"
    );

    private final Setting.IntSetting radius = addInt(
        "Radius",
        "Horizontal scan radius in blocks",
        24, 4, 48
    );

    private final Setting.IntSetting verticalRange = addInt(
        "VerticalRange",
        "Vertical scan radius in blocks",
        16, 4, 48
    );

    private final Setting.ColorSetting color = addColor(
        "Color",
        "Box color",
        0xCCA632FF,
        true
    );

    private final Setting.NumberSetting fill = addNum(
        "Fill",
        "Box fill opacity percentage",
        15.0, 0.0, 100.0, 1.0
    );

    private final Setting.NumberSetting lineWidth = addNum(
        "LineWidth",
        "Box and tracer line width",
        2.0, 0.5, 6.0, 0.5
    );

    private final Setting.BoolSetting tracers = addBool(
        "Tracers",
        "Draw lines to selected blocks",
        false
    );

    private final Setting.ColorSetting tracerColor = addColor(
        "TracerColor",
        "Tracer color",
        0xCCA632FF,
        true
    );

    private final Set<String> selectedIds = new HashSet<>();
    private final List<BlockPos> found = new ArrayList<>();
    private final List<BlockPos> pending = new ArrayList<>();

    private String cachedSelection = "";
    private Object scannedWorld;

    private int originX;
    private int originY;
    private int originZ;
    private int scanRadius;
    private int scanVertical;
    private int cursor;
    private boolean scanning;

    public BlockESP() {
        super(
            "BlockESP",
            "Highlight selected blocks through walls.",
            Category.RENDER
        );
    }

    public Set<String> getSelectedIds() {
        syncSelection();
        return Collections.unmodifiableSet(new HashSet<>(selectedIds));
    }

    public boolean isSelected(Block block) {
        syncSelection();
        return selectedIds.contains(Registries.BLOCK.getId(block).toString());
    }

    public void toggleBlock(Block block) {
        syncSelection();

        String id = Registries.BLOCK.getId(block).toString();

        if (!selectedIds.add(id)) {
            selectedIds.remove(id);
        }

        List<String> sorted = new ArrayList<>(selectedIds);
        Collections.sort(sorted);

        blocks.setValue(String.join(",", sorted));

        // Force the cached IDs and results to refresh.
        cachedSelection = null;
        syncSelection();
        ConfigManager.save();
    }

    private void syncSelection() {
        String value = blocks.getValue();

        if (value == null) {
            value = "";
        }

        if (value.equals(cachedSelection)) {
            return;
        }

        cachedSelection = value;
        selectedIds.clear();

        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .forEach(selectedIds::add);

        invalidateScan();
    }

    private void invalidateScan() {
        scanning = false;
        cursor = 0;
        pending.clear();
        found.clear();
    }

    @Override
    public void onDisable() {
        invalidateScan();
        scannedWorld = null;
    }

    @Override
    public void onDisconnect() {
        invalidateScan();
        scannedWorld = null;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.player == null) {
            return;
        }

        syncSelection();

        if (scannedWorld != mc.world) {
            scannedWorld = mc.world;
            invalidateScan();
        }

        if (selectedIds.isEmpty()) {
            invalidateScan();
            return;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        int r = radius.getValue();
        int vr = verticalRange.getValue();

        boolean movedFar =
            Math.abs(playerPos.getX() - originX) > 8 ||
            Math.abs(playerPos.getY() - originY) > 8 ||
            Math.abs(playerPos.getZ() - originZ) > 8;

        if (
            !scanning ||
            movedFar ||
            r != scanRadius ||
            vr != scanVertical
        ) {
            originX = playerPos.getX();
            originY = playerPos.getY();
            originZ = playerPos.getZ();

            scanRadius = r;
            scanVertical = vr;

            cursor = 0;
            pending.clear();
            scanning = true;
        }

        int width = scanRadius * 2 + 1;
        int height = scanVertical * 2 + 1;
        int total = width * width * height;

        int bottomY = mc.world.getBottomY();
        int topY = mc.world.getTopYInclusive();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;
        WorldChunk chunk = null;

        for (
            int count = 0;
            count < BLOCKS_PER_TICK && cursor < total;
            count++
        ) {
            int index = cursor++;

            int localX = index % width;
            int localZ = (index / width) % width;
            int localY = index / (width * width);

            int x = originX - scanRadius + localX;
            int y = originY - scanVertical + localY;
            int z = originZ - scanRadius + localZ;

            if (y < bottomY || y > topY) {
                continue;
            }

            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
                cachedChunkX = chunkX;
                cachedChunkZ = chunkZ;

                chunk = mc.world.getChunkManager().getChunk(
                    chunkX,
                    chunkZ,
                    ChunkStatus.FULL,
                    false
                );
            }

            if (chunk == null) {
                continue;
            }

            pos.set(x, y, z);

            Block block = chunk.getBlockState(pos).getBlock();
            String id = Registries.BLOCK.getId(block).toString();

            if (
                selectedIds.contains(id) &&
                pending.size() < MAX_RESULTS
            ) {
                pending.add(pos.toImmutable());
            }
        }

        if (cursor >= total) {
            found.clear();
            found.addAll(pending);
            pending.clear();
            scanning = false;
        }
    }

    private boolean stillSelected(MinecraftClient mc, BlockPos pos) {
        if (mc.world == null) {
            return false;
        }

        WorldChunk chunk = mc.world.getChunkManager().getChunk(
            pos.getX() >> 4,
            pos.getZ() >> 4,
            ChunkStatus.FULL,
            false
        );

        if (chunk == null) {
            return false;
        }

        Block block = chunk.getBlockState(pos).getBlock();
        String id = Registries.BLOCK.getId(block).toString();

        return selectedIds.contains(id);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.player == null || found.isEmpty()) {
            return;
        }

        var worldRenderer = context.worldRenderer();

        if (worldRenderer == null) {
            return;
        }

        int outlineColor = color.getValue();

        int fillAlpha = (int) Math.round(
            Math.max(0.0, Math.min(100.0, fill.getValue())) * 2.55
        );

        int fillColor =
            (outlineColor & 0x00FFFFFF) | (fillAlpha << 24);

        float width = lineWidth.getValue().floatValue();

        try (var scope = worldRenderer.startDrawingGizmos()) {
            for (BlockPos pos : found) {
                if (!stillSelected(mc, pos)) {
                    continue;
                }

                Box box = new Box(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1,
                    pos.getY() + 1,
                    pos.getZ() + 1
                );

                if (fillAlpha > 0) {
                    GizmoDrawing.box(
                        box,
                        DrawStyle.filled(fillColor)
                    ).ignoreOcclusion();
                }

                GizmoDrawing.box(
                    box,
                    DrawStyle.stroked(outlineColor, width)
                ).ignoreOcclusion();
            }
        }
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (
            mc.world == null ||
            mc.player == null ||
            found.isEmpty() ||
            !tracers.getValue()
        ) {
            return;
        }

        var camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();

        float pitch = camera.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        float yaw = camera.getYaw() * MathHelper.RADIANS_PER_DEGREE;

        Vec3d forward = new Vec3d(
            -MathHelper.sin(yaw) * MathHelper.cos(pitch),
            -MathHelper.sin(pitch),
            MathHelper.cos(yaw) * MathHelper.cos(pitch)
        );

        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.crossProduct(forward).normalize();

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        GameRendererAccessor renderer =
            (GameRendererAccessor) mc.gameRenderer;

        float fov = renderer.invokeGetFov(
            camera,
            mc.getRenderTickCounter().getDynamicDeltaTicks(),
            true
        );

        double verticalScale = Math.tan(Math.toRadians(fov) / 2.0);
        double horizontalScale =
            verticalScale * screenWidth / (double) screenHeight;

        if (verticalScale <= 0 || horizontalScale <= 0) {
            return;
        }

        int lineColor = tracerColor.getValue();
        int thickness = Math.max(
            1,
            (int) Math.round(lineWidth.getValue())
        );

        var matrices = context.getMatrices();

        for (BlockPos pos : found) {
            if (!stillSelected(mc, pos)) {
                continue;
            }

            Vec3d blockCenter = new Vec3d(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            Vec3d relative = blockCenter.subtract(cameraPos);
            double forwardDistance = relative.dotProduct(forward);

            if (forwardDistance <= 0.01) {
                continue;
            }

            double rightDistance = relative.dotProduct(right);
            double upDistance = relative.dotProduct(up);

            double screenX =
                (rightDistance / (forwardDistance * horizontalScale))
                * centerX + centerX;

            double screenY =
                (-upDistance / (forwardDistance * verticalScale))
                * centerY + centerY;

            if (
                screenX < 0 || screenX > screenWidth ||
                screenY < 0 || screenY > screenHeight
            ) {
                continue;
            }

            double dx = screenX - centerX;
            double dy = screenY - centerY;
            double length = Math.sqrt(dx * dx + dy * dy);

            if (length < 0.5) {
                continue;
            }

            matrices.pushMatrix();
            matrices.translate(centerX, centerY);
            matrices.rotate((float) Math.atan2(dy, dx));

            context.fill(
                0,
                -thickness / 2,
                (int) Math.round(length),
                -thickness / 2 + thickness,
                lineColor
            );

            matrices.popMatrix();
        }
    }
}
