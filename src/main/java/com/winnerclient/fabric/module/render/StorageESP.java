package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.mixin.GameRendererAccessor;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import com.winnerclient.fabric.render.StorageShaderPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.block.entity.ComparatorBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * WinnerClient's standalone storage ESP implementation.
 *
 * <p>This ports the useful, renderer-independent parts of Meteor's StorageESP:
 * per-storage colors, opened-container state, hiding/recoloring opened blocks,
 * fading near the camera, shape selection, double-chest geometry and tracers.
 * It uses WinnerClient's existing GizmoDrawing world hook and never depends on
 * Meteor classes or a Meteor runtime.</p>
 *
 * <p>The Shader mode is implemented by the standalone
 * {@link com.winnerclient.fabric.render.StorageShaderPipeline}: it renders
 * storage boxes to a mask framebuffer and applies an outline fragment shader.
 * It intentionally outlines ESP geometry boxes, not full block-entity model
 * silhouettes; that is the remaining difference from Meteor's
 * SimpleBlockRenderer-based capture path.</p>
 */
public final class StorageESP extends Module {
    private static final int MAX_DISTANCE_SQUARED = 1_048_576; // 1024 blocks.

    private final Setting.ModeSetting mode = addMode(
        "Mode", "Rendering mode; Shader uses the standalone framebuffer outline pipeline", "Box",
        "Box", "Shader"
    );
    private final Setting.IntSetting shaderWidth = addInt(
        "ShaderWidth", "Shader outline radius in pixels", 1, 1, 10
    );
    private final Setting.NumberSetting glowMultiplier = addNum(
        "GlowMultiplier", "Shader outline glow multiplier", 3.5, 0.0, 10.0, 0.1
    );

    // Original WinnerClient filters, retained for config/API compatibility.
    private final Setting.BoolSetting chests = addBool(
        "Chests", "Show normal, trapped, and ender chests", true
    );
    private final Setting.BoolSetting barrels = addBool(
        "Barrels", "Show barrels", true
    );
    private final Setting.BoolSetting shulkers = addBool(
        "Shulkers", "Show shulker boxes", true
    );
    private final Setting.BoolSetting furnaces = addBool(
        "Furnaces", "Show furnaces, smokers, and blast furnaces", false
    );
    private final Setting.BoolSetting redstone = addBool(
        "Redstone", "Show hoppers, droppers, dispensers, comparators, and crafters", false
    );
    private final Setting.BoolSetting miscStorage = addBool(
        "MiscStorage", "Show brewing stands, beacons, pots, and bookshelves", false
    );

    // Original generic settings retained; the new category colors below are used
    // for rendering. Color remains useful for labels and old config compatibility.
    private final Setting.ColorSetting color = addColor(
        "Color", "Legacy/default ESP color and label color", 0xCCFFAA00, true
    );
    private final Setting.NumberSetting fillPercent = addNum(
        "Fill", "Box fill opacity percentage", 0.0, 0.0, 100.0, 1.0
    );
    private final Setting.NumberSetting lineWidth = addNum(
        "LineWidth", "Box outline width", 2.0, 0.5, 8.0, 0.5
    );
    private final Setting.BoolSetting showId = addBool(
        "ShowID", "Show the storage type above each block", false
    );
    private final Setting.IntSetting chunkRadius = addInt(
        "ChunkRadius", "Loaded chunk radius to inspect", 8, 1, 16
    );
    private final Setting.BoolSetting tracers = addBool(
        "Tracers", "Draw lines to storage blocks", true
    );
    private final Setting.ColorSetting tracerColor = addColor(
        "TracerColor", "Tracer and off-screen indicator color", 0xCCFFAA00, true
    );
    private final Setting.NumberSetting tracerSize = addNum(
        "TracerSize", "Tracer line thickness", 2.0, 0.1, 8.0, 0.1
    );

    // Meteor parity: shape mode and opened rendering.
    private final Setting.ModeSetting shapeMode = addMode(
        "ShapeMode", "Which parts of each box to render", "Both",
        "Lines", "Sides", "Both"
    );
    private final Setting.NumberSetting fadeDistance = addNum(
        "FadeDistance", "Fade alpha within this distance", 6.0, 0.0, 32.0, 0.5
    );
    private final Setting.BoolSetting hideOpened = addBool(
        "HideOpened", "Hide containers recorded as opened", false
    );
    private final Setting.ColorSetting openedColor = addColor(
        "OpenedColor", "Optional color for opened containers; alpha zero keeps their normal color", 0x00CB5ACB, true
    );

    // Meteor-style per-storage colors (ARGB, including outline alpha).
    private final Setting.ColorSetting chestColor = addColor(
        "ChestColor", "Normal chest color", 0xFFFFA000, true
    );
    private final Setting.ColorSetting trappedChestColor = addColor(
        "TrappedChestColor", "Trapped chest color", 0xFFFF0000, true
    );
    private final Setting.ColorSetting barrelColor = addColor(
        "BarrelColor", "Barrel color", 0xFFFFA000, true
    );
    private final Setting.ColorSetting shulkerColor = addColor(
        "ShulkerColor", "Shulker box color", 0xFFFFA000, true
    );
    private final Setting.ColorSetting enderChestColor = addColor(
        "EnderChestColor", "Ender chest color", 0xFF7800FF, true
    );
    private final Setting.ColorSetting otherColor = addColor(
        "OtherColor", "Furnace, brewing, redstone and other storage color", 0xFF8C8C8C, true
    );

    /** Positions of containers interacted with while this module is active. */
    private static final int MAX_INTERACTED_BLOCKS = 8192;
    private final Set<BlockPos> interactedBlocks = new LinkedHashSet<>();
    private Object trackedWorld;

    private final List<ScreenPoint> screenTracers = new ArrayList<>();
    private final List<ScreenPoint> offscreenIndicators = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<StorageShaderPipeline.BoxColor> shaderBoxes = new ArrayList<>();

    public StorageESP() {
        super("StorageESP", "See storage containers through walls", Category.RENDER);
    }

    /** Compatibility ABI for older network mixins; intentionally never rewrites packets. */
    public Double pollVoidLoad() {
        return null;
    }

    /**
     * Called by ClientPlayerInteractionManagerMixin at interactBlock HEAD.
     * The position and its double-chest partner are recorded as opened.
     */
    public void markInteracted(BlockPos clickedPos) {
        if (!isEnabled() || clickedPos == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (trackedWorld != mc.world) {
            interactedBlocks.clear();
            trackedWorld = mc.world;
        }

        BlockPos pos = clickedPos.toImmutable();
        var blockEntity = mc.world.getBlockEntity(pos);
        // Match Meteor's InteractBlockEvent behavior: record attempted
        // interactions only for positions that currently have a block entity.
        // Ordinary block clicks must not grow this cache.
        if (blockEntity == null) return;

        addInteracted(pos);
        var state = blockEntity.getCachedState();
        if (state.contains(ChestBlock.CHEST_TYPE) && state.get(ChestBlock.CHEST_TYPE) != net.minecraft.block.enums.ChestType.SINGLE) {
            Direction facing = ChestBlock.getFacing(state);
            net.minecraft.block.enums.ChestType type = state.get(ChestBlock.CHEST_TYPE);
            Direction towardOther = type == net.minecraft.block.enums.ChestType.LEFT
                ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
            addInteracted(pos.offset(towardOther).toImmutable());
        }
    }

    /** Clears opened state, useful for a UI button or diagnostics. */
    public void clearInteracted() {
        interactedBlocks.clear();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (trackedWorld != mc.world) {
            interactedBlocks.clear();
            trackedWorld = mc.world;
            return;
        }

        // A chunk unload must release its remembered positions. This keeps
        // long sessions bounded even when the player visits many regions.
        for (Iterator<BlockPos> it = interactedBlocks.iterator(); it.hasNext();) {
            BlockPos pos = it.next();
            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) it.remove();
        }
    }

    private void addInteracted(BlockPos pos) {
        interactedBlocks.remove(pos);
        interactedBlocks.add(pos);
        while (interactedBlocks.size() > MAX_INTERACTED_BLOCKS) {
            Iterator<BlockPos> it = interactedBlocks.iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
    }

    @Override
    public void onEnable() {
        clearInteracted();
        trackedWorld = MinecraftClient.getInstance().world;
    }

    @Override
    public void onDisable() {
        clearInteracted();
        trackedWorld = null;
        clearScreenState();
    }

    @Override
    public void onDisconnect() {
        clearInteracted();
        trackedWorld = null;
        clearScreenState();
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        clearScreenState();

        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;
        if (trackedWorld != mc.world) {
            // Never carry opened state from one server/world into another.
            interactedBlocks.clear();
            trackedWorld = mc.world;
        }

        WorldRenderer worldRenderer = context.worldRenderer();
        if (worldRenderer == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        BlockPos cameraBlock = BlockPos.ofFloored(cameraPos);
        int cameraChunkX = cameraBlock.getX() >> 4;
        int cameraChunkZ = cameraBlock.getZ() >> 4;
        int radius = chunkRadius.getValue();

        Projection frameProjection = (tracers.getValue() || showId.getValue())
            ? projection(mc, camera) : null;

        try (var ignored = worldRenderer.startDrawingGizmos()) {
            for (int chunkX = cameraChunkX - radius; chunkX <= cameraChunkX + radius; chunkX++) {
                for (int chunkZ = cameraChunkZ - radius; chunkZ <= cameraChunkZ + radius; chunkZ++) {
                    WorldChunk chunk = mc.world.getChunkManager().getChunk(
                        chunkX, chunkZ, ChunkStatus.FULL, false
                    );
                    if (chunk == null) continue;

                    for (var blockEntity : chunk.getBlockEntities().values()) {
                        if (blockEntity == null || blockEntity.isRemoved()) continue;
                        BlockPos pos = blockEntity.getPos();
                        if (pos == null || pos.getSquaredDistance(cameraPos.x, cameraPos.y, cameraPos.z) > MAX_DISTANCE_SQUARED) continue;
                        if (!isSelected(blockEntity)) continue;

                        boolean opened = interactedBlocks.contains(pos);
                        if (opened && hideOpened.getValue()) continue;

                        int baseColor = storageColor(blockEntity);
                        if (opened && ((openedColor.getValue() >>> 24) & 0xFF) != 0) {
                            baseColor = openedColor.getValue();
                        }

                        double fade = fadeFactor(pos, cameraPos);
                        if (fade < 0.075) continue;

                        int outlineColor = withAlpha(baseColor, (int) Math.round(alpha(baseColor) * fade));
                        int fillAlpha = (int) Math.round(
                            clamp(fillPercent.getValue(), 0.0, 100.0) * 2.55 * fade
                        );
                        int sideColor = (baseColor & 0x00FFFFFF) | (MathHelper.clamp(fillAlpha, 0, 255) << 24);

                        Box storageBox = storageBox(blockEntity);
                        if ("Shader".equalsIgnoreCase(mode.getValue())) {
                            shaderBoxes.add(new StorageShaderPipeline.BoxColor(storageBox, outlineColor));
                        } else {
                            renderBox(blockEntity, sideColor, outlineColor);
                        }

                        Vec3d center = Vec3d.ofCenter(pos);
                        if (showId.getValue()) labels.add(new Label(center, blockEntityName(blockEntity), baseColor));
                        if (tracers.getValue() && frameProjection != null) {
                            addScreenPoint(center, cameraPos, frameProjection);
                        }
                    }
                }
            }
        }

        // Shader compositing is intentionally deferred to END_MAIN by EventBus.
        // The main pass must be complete before its framebuffer is sampled.
    }

    /** Called from WorldRenderEvents.END_MAIN after the world main pass is complete. */
    public void onShaderRender() {
        if (!"Shader".equalsIgnoreCase(mode.getValue()) || !StorageShaderPipeline.isAvailable() || shaderBoxes.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        StorageShaderPipeline.renderBoxes(
            shaderBoxes,
            ((GameRendererAccessor) mc.gameRenderer).invokeGetProjectionMatrix(
                mc.getRenderTickCounter().getTickProgress(false)
            ),
            RenderSystem.getModelViewMatrix(),
            camera.getCameraPos(),
            shaderWidth.getValue(), (float) (fillPercent.getValue() / 100.0),
            "Lines".equalsIgnoreCase(shapeMode.getValue()) ? 0 :
                "Sides".equalsIgnoreCase(shapeMode.getValue()) ? 1 : 2,
            (float) glowMultiplier.getValue()
        );
        shaderBoxes.clear();
    }

    private void renderBox(net.minecraft.block.entity.BlockEntity blockEntity, int sideColor, int lineColor) {
        Box box = storageBox(blockEntity);
        String mode = shapeMode.getValue();
        if ("Sides".equalsIgnoreCase(mode) || "Both".equalsIgnoreCase(mode)) {
            GizmoDrawing.box(box, DrawStyle.filled(sideColor)).ignoreOcclusion();
        }
        if ("Lines".equalsIgnoreCase(mode) || "Both".equalsIgnoreCase(mode)) {
            GizmoDrawing.box(box, DrawStyle.stroked(lineColor, (float) clamp(lineWidth.getValue(), 0.5, 8.0))).ignoreOcclusion();
        }
    }

    /** Meteor-compatible chest geometry: trim outer sides but leave the seam closed. */
    private static Box storageBox(net.minecraft.block.entity.BlockEntity blockEntity) {
        BlockPos pos = blockEntity.getPos();
        double x1 = pos.getX();
        double y1 = pos.getY();
        double z1 = pos.getZ();
        double x2 = x1 + 1.0;
        double y2 = y1 + 1.0;
        double z2 = z1 + 1.0;

        boolean chestLike = blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity;
        if (!chestLike) return new Box(x1, y1, z1, x2, y2, z2);

        final double inset = 1.0 / 16.0;
        Direction partner = null;
        if (blockEntity instanceof ChestBlockEntity chest) {
            var state = chest.getCachedState();
            if (state.contains(ChestBlock.CHEST_TYPE) && state.get(ChestBlock.CHEST_TYPE) != net.minecraft.block.enums.ChestType.SINGLE) {
                Direction facing = ChestBlock.getFacing(state);
                partner = state.get(ChestBlock.CHEST_TYPE) == net.minecraft.block.enums.ChestType.LEFT
                    ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
            }
        }

        // Vanilla chest model is inset on external sides and two pixels shorter.
        if (partner != Direction.WEST) x1 += inset;
        if (partner != Direction.NORTH) z1 += inset;
        if (partner != Direction.EAST) x2 -= inset;
        if (partner != Direction.SOUTH) z2 -= inset;
        y2 -= inset * 2.0;
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    private double fadeFactor(BlockPos pos, Vec3d cameraPos) {
        double fadeDistanceValue = fadeDistance.getValue();
        if (fadeDistanceValue <= 0.0) return 1.0;
        double squared = pos.getSquaredDistance(cameraPos.x, cameraPos.y, cameraPos.z);
        double maxSquared = fadeDistanceValue * fadeDistanceValue;
        // Matches Meteor's current behavior: alpha falls linearly in squared distance.
        return squared <= maxSquared ? squared / maxSquared : 1.0;
    }

    private int storageColor(net.minecraft.block.entity.BlockEntity blockEntity) {
        // Trapped chest must be tested before ChestBlockEntity. If an old config
        // changed only the original generic Color setting, honor it while the
        // corresponding new per-storage setting is still at its default.
        if (blockEntity instanceof TrappedChestBlockEntity) return legacyAware(trappedChestColor, 0xFFFF0000);
        if (blockEntity instanceof ChestBlockEntity) return legacyAware(chestColor, 0xFFFFA000);
        if (blockEntity instanceof BarrelBlockEntity) return legacyAware(barrelColor, 0xFFFFA000);
        if (blockEntity instanceof ShulkerBoxBlockEntity) return legacyAware(shulkerColor, 0xFFFFA000);
        if (blockEntity instanceof EnderChestBlockEntity) return legacyAware(enderChestColor, 0xFF7800FF);
        return legacyAware(otherColor, 0xFF8C8C8C);
    }

    private int legacyAware(Setting.ColorSetting specific, int specificDefault) {
        int generic = color.getValue();
        if (generic != 0xCCFFAA00 && specific.getValue() == specificDefault) return generic;
        return specific.getValue();
    }

    private boolean isSelected(net.minecraft.block.entity.BlockEntity blockEntity) {
        if (blockEntity instanceof TrappedChestBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity) return chests.getValue();
        if (blockEntity instanceof BarrelBlockEntity) return barrels.getValue();
        if (blockEntity instanceof ShulkerBoxBlockEntity) return shulkers.getValue();
        if (blockEntity instanceof FurnaceBlockEntity || blockEntity instanceof SmokerBlockEntity || blockEntity instanceof BlastFurnaceBlockEntity) return furnaces.getValue();
        if (blockEntity instanceof HopperBlockEntity || blockEntity instanceof DropperBlockEntity || blockEntity instanceof DispenserBlockEntity || blockEntity instanceof ComparatorBlockEntity || blockEntity instanceof CrafterBlockEntity) return redstone.getValue();
        return blockEntity instanceof BrewingStandBlockEntity || blockEntity instanceof BeaconBlockEntity || blockEntity instanceof DecoratedPotBlockEntity || blockEntity instanceof ChiseledBookshelfBlockEntity ? miscStorage.getValue() : false;
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!tracers.getValue() && !showId.getValue()) return;

        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        if (width <= 0 || height <= 0) return;
        int centerX = width / 2;
        int centerY = height / 2;
        int tracerArgb = tracerColor.getValue();
        int thickness = Math.max(1, (int) Math.round(tracerSize.getValue()));
        var matrices = context.getMatrices();

        if (tracers.getValue()) {
            drawTracers(context, matrices, screenTracers, centerX, centerY, tracerArgb, thickness);
            drawTracers(context, matrices, offscreenIndicators, centerX, centerY, tracerArgb, thickness);
            for (ScreenPoint point : offscreenIndicators) context.fill(point.x() - 3, point.y() - 3, point.x() + 3, point.y() + 3, tracerArgb);
        }

        if (showId.getValue()) {
            Projection currentProjection = projection(mc, mc.gameRenderer.getCamera());
            if (currentProjection == null) return;
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
            for (Label label : labels) {
                ScreenPoint point = project(label.position(), cameraPos, currentProjection);
                if (point == null || point.behind()) continue;
                String text = label.text();
                int labelColor = withAlpha(label.color(), 255);
                context.drawText(mc.textRenderer, text, point.x() - mc.textRenderer.getWidth(text) / 2, point.y() - 20, labelColor, true);
            }
        }
    }

    private static void drawTracers(DrawContext context, org.joml.Matrix3x2fStack matrices, List<ScreenPoint> points, int centerX, int centerY, int argb, int thickness) {
        for (ScreenPoint point : points) {
            int dx = point.x() - centerX;
            int dy = point.y() - centerY;
            double length = Math.sqrt((double) dx * dx + (double) dy * dy);
            if (length < 0.5) continue;
            matrices.pushMatrix();
            matrices.translate(centerX, centerY);
            matrices.rotate((float) Math.atan2(dy, dx));
            context.fill(0, -thickness / 2, (int) Math.round(length), -thickness / 2 + thickness, argb);
            matrices.popMatrix();
        }
    }

    private Projection projection(MinecraftClient mc, Camera camera) {
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        if (width <= 0 || height <= 0) return null;
        float fov = ((GameRendererAccessor) mc.gameRenderer).invokeGetFov(camera, mc.getRenderTickCounter().getTickProgress(false), true);
        if (!(fov > 0.0f && fov < 180.0f)) return null;
        float pitch = camera.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        float yaw = camera.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d forward = new Vec3d(-MathHelper.sin(yaw) * MathHelper.cos(pitch), -MathHelper.sin(pitch), MathHelper.cos(yaw) * MathHelper.cos(pitch));
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1.0E-8) return null;
        right = right.normalize();
        Vec3d up = right.crossProduct(forward).normalize();
        double verticalScale = Math.tan(Math.toRadians(fov) / 2.0);
        double horizontalScale = verticalScale * width / (double) height;
        return new Projection(forward, right, up, verticalScale, horizontalScale, width, height);
    }

    private static void addScreenPoint(List<ScreenPoint> visible, List<ScreenPoint> hidden, Vec3d position, Vec3d cameraPos, Projection projection) {
        ScreenPoint point = project(position, cameraPos, projection);
        if (point == null) return;
        (point.behind() ? hidden : visible).add(point);
    }

    private void addScreenPoint(Vec3d position, Vec3d cameraPos, Projection projection) {
        addScreenPoint(screenTracers, offscreenIndicators, position, cameraPos, projection);
    }

    private static ScreenPoint project(Vec3d position, Vec3d cameraPos, Projection p) {
        Vec3d relative = position.subtract(cameraPos);
        double forwardDistance = relative.dotProduct(p.forward());
        int centerX = p.width() / 2;
        int centerY = p.height() / 2;
        if (forwardDistance <= 0.01) {
            Vec3d direction = relative.lengthSquared() < 1.0E-8 ? p.forward().multiply(-1) : relative.normalize();
            double angle = Math.atan2(direction.dotProduct(p.right()), direction.dotProduct(p.forward()));
            double x = centerX + (angle / Math.atan(p.horizontalScale())) * centerX;
            return new ScreenPoint(MathHelper.clamp((int) Math.round(x), 0, p.width()), p.height(), true);
        }
        double screenX = centerX + (relative.dotProduct(p.right()) / (forwardDistance * p.horizontalScale())) * centerX;
        double screenY = centerY - (relative.dotProduct(p.up()) / (forwardDistance * p.verticalScale())) * centerY;
        if (screenX >= 0 && screenX <= p.width() && screenY >= 0 && screenY <= p.height()) return new ScreenPoint((int) Math.round(screenX), (int) Math.round(screenY), false);
        double dx = screenX - centerX;
        double dy = screenY - centerY;
        double scaleX = dx == 0 ? Double.POSITIVE_INFINITY : ((dx > 0 ? p.width() - centerX : -centerX) / dx);
        double scaleY = dy == 0 ? Double.POSITIVE_INFINITY : ((dy > 0 ? p.height() - centerY : -centerY) / dy);
        double scale = Math.min(Math.abs(scaleX), Math.abs(scaleY));
        if (!Double.isFinite(scale) || scale <= 0) return null;
        return new ScreenPoint(MathHelper.clamp((int) Math.round(centerX + dx * scale), 0, p.width()), MathHelper.clamp((int) Math.round(centerY + dy * scale), 0, p.height()), true);
    }

    private static String blockEntityName(net.minecraft.block.entity.BlockEntity blockEntity) {
        if (blockEntity instanceof TrappedChestBlockEntity) return "Trapped Chest";
        if (blockEntity instanceof ChestBlockEntity) return "Chest";
        if (blockEntity instanceof EnderChestBlockEntity) return "Ender Chest";
        if (blockEntity instanceof BarrelBlockEntity) return "Barrel";
        if (blockEntity instanceof ShulkerBoxBlockEntity) return "Shulker";
        if (blockEntity instanceof FurnaceBlockEntity) return "Furnace";
        if (blockEntity instanceof SmokerBlockEntity) return "Smoker";
        if (blockEntity instanceof BlastFurnaceBlockEntity) return "Blast Furnace";
        if (blockEntity instanceof HopperBlockEntity) return "Hopper";
        if (blockEntity instanceof DispenserBlockEntity) return "Dispenser";
        if (blockEntity instanceof DropperBlockEntity) return "Dropper";
        if (blockEntity instanceof ComparatorBlockEntity) return "Comparator";
        if (blockEntity instanceof CrafterBlockEntity) return "Crafter";
        if (blockEntity instanceof BrewingStandBlockEntity) return "Brewing Stand";
        if (blockEntity instanceof BeaconBlockEntity) return "Beacon";
        if (blockEntity instanceof DecoratedPotBlockEntity) return "Decorated Pot";
        if (blockEntity instanceof ChiseledBookshelfBlockEntity) return "Chiseled Bookshelf";
        return blockEntity.getClass().getSimpleName();
    }

    private static int alpha(int argb) { return (argb >>> 24) & 0xFF; }
    private static int withAlpha(int argb, int alpha) { return (argb & 0x00FFFFFF) | (MathHelper.clamp(alpha, 0, 255) << 24); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private void clearScreenState() { screenTracers.clear(); offscreenIndicators.clear(); labels.clear(); shaderBoxes.clear(); }

    private record Projection(Vec3d forward, Vec3d right, Vec3d up, double verticalScale, double horizontalScale, int width, int height) {}
    private record ScreenPoint(int x, int y, boolean behind) {}
    private record Label(Vec3d position, String text, int color) {}
}
