package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.mixin.GameRendererAccessor;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketESP extends Module {
    private final Map<BlockPos, Detection> detections = new ConcurrentHashMap<>();
    private long lastClearTime;

    public PacketESP() {
        super("PacketESP", "Advanced packet-based ESP for blocks and entities. Scans loaded chunks for chests, spawners, deepslate, pistons, heads, signs and more.", Category.MISC);
    }

    private final Setting.BoolSetting showChest = addBool("Chest", "Show chests", true);
    private final Setting.BoolSetting showTrappedChest = addBool("TrappedChest", "Show trapped chests", true);
    private final Setting.BoolSetting showEnderChest = addBool("EnderChest", "Show ender chests", true);
    private final Setting.BoolSetting showBarrel = addBool("Barrel", "Show barrels", true);
    private final Setting.BoolSetting showShulker = addBool("Shulker", "Show shulker boxes", true);
    private final Setting.BoolSetting showSpawner = addBool("Spawner", "Show mob spawners", true);
    private final Setting.BoolSetting showPiston = addBool("Piston", "Show pistons", true);
    private final Setting.BoolSetting showObserver = addBool("Observer", "Show observers", true);
    private final Setting.BoolSetting showHopper = addBool("Hopper", "Show hoppers/droppers/dispensers", true);
    private final Setting.BoolSetting showSign = addBool("Sign", "Show signs", true);
    private final Setting.BoolSetting showHead = addBool("Head", "Show heads/skulls", true);
    private final Setting.BoolSetting showDroppedItem = addBool("DroppedItem", "Show dropped items", false);
    private final Setting.BoolSetting showDeepslate = addBool("Deepslate", "Show rotated deepslate (player-placed)", true);
    private final Setting.BoolSetting showPlayer = addBool("Player", "Show players", false);
    private final Setting.BoolSetting showMob = addBool("Mob", "Show mobs", false);
    private final Setting.BoolSetting showFurnace = addBool("Furnace", "Show furnaces/smokers/blast furnaces", false);
    private final Setting.BoolSetting showCrafting = addBool("Crafting", "Show crafting tables/stonecutters/etc", false);
    private final Setting.BoolSetting showAnvil = addBool("Anvil", "Show anvils", false);
    private final Setting.BoolSetting showBrewing = addBool("Brewing", "Show brewing stands", false);
    private final Setting.BoolSetting showBed = addBool("Bed", "Show beds", false);

    private final Setting.ColorSetting chestColor = addColor("ChestColor", "Chest color", 0xCCFFAA00, true);
    private final Setting.ColorSetting spawnerColor = addColor("SpawnerColor", "Spawner color", 0xCCFF5555, true);
    private final Setting.ColorSetting pistonColor = addColor("PistonColor", "Piston color", 0xCC55FFFF, true);
    private final Setting.ColorSetting deepslateColor = addColor("DeepslateColor", "Deepslate color", 0xCC888888, true);
    private final Setting.ColorSetting headColor = addColor("HeadColor", "Head/skull color", 0xCCFFAA00, true);
    private final Setting.ColorSetting signColor = addColor("SignColor", "Sign color", 0xCCFFFF55, true);
    private final Setting.ColorSetting defaultColor = addColor("DefaultColor", "Default block color", 0xCCFFAA00, true);

    private final Setting.NumberSetting fillPercent = addNum("Fill", "Fill opacity percent", 30.0, 0.0, 100.0, 1.0);
    private final Setting.NumberSetting lineWidth = addNum("LineWidth", "Outline width", 2.0, 0.5, 6.0, 0.5);
    private final Setting.BoolSetting tracers = addBool("Tracers", "Draw tracers to detections", true);
    private final Setting.ColorSetting tracerColor = addColor("TracerColor", "Tracer color", 0xCCFFFFFF, true);
    private final Setting.NumberSetting maxDist = addNum("MaxDist", "Maximum render distance", 100.0, 10.0, 500.0, 5.0);
    private final Setting.IntSetting minY = addInt("MinY", "Minimum Y level", -64, -64, 320);
    private final Setting.IntSetting maxYLimit = addInt("MaxY", "Maximum Y level", 320, -64, 320);
    private final Setting.BoolSetting showLabels = addBool("Labels", "Show distance labels", true);
    private final Setting.IntSetting autoClear = addInt("AutoClear", "Auto-clear after seconds (0=off)", 0, 0, 300);
    private final Setting.BoolSetting clearOnDisable = addBool("ClearOnDisable", "Clear detections when disabled", true);
    private final Setting.BoolSetting scanOnRender = addBool("ScanOnRender", "Scan chunks each render frame", true);

    private final List<int[]> screenTracers = new ArrayList<>();

    private static class Detection {
        double x, y, z;
        String label;
        int color;
        long lastSeen;
        Detection(double x, double y, double z, String label, int color) {
            this.x = x; this.y = y; this.z = z;
            this.label = label; this.color = color;
            this.lastSeen = System.currentTimeMillis();
        }
    }

    @Override
    protected void onDisable() {
        if (clearOnDisable.getValue()) detections.clear();
    }

    @Override
    public void onTick() {
        int ac = autoClear.getValue();
        if (ac > 0) {
            long now = System.currentTimeMillis();
            long threshold = now - ac * 1000L;
            detections.values().removeIf(d -> d.lastSeen < threshold);
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        screenTracers.clear();

        var camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        int viewDist = mc.options.getViewDistance().getValue();
        int camChunkX = BlockPos.ofFloored(camPos).getX() >> 4;
        int camChunkZ = BlockPos.ofFloored(camPos).getZ() >> 4;
        WorldRenderer wr = context.worldRenderer();
        if (wr == null) return;

        double maxDistSq = maxDist.getValue() * maxDist.getValue();
        float camYaw = camera.getYaw();
        float camPitch = camera.getPitch();
        float pitchRad = camPitch * MathHelper.RADIANS_PER_DEGREE;
        float yawRad = camYaw * MathHelper.RADIANS_PER_DEGREE;
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRad) * MathHelper.cos(pitchRad), -MathHelper.sin(pitchRad), MathHelper.cos(yawRad) * MathHelper.cos(pitchRad));
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.crossProduct(forward).normalize();

        boolean hasTracers = tracers.getValue();
        boolean hasLabels = showLabels.getValue();
        double fillAlpha = fillPercent.getValue() / 100.0;
        double lw = lineWidth.getValue();

        if (scanOnRender.getValue()) {
            for (int cx = camChunkX - viewDist; cx <= camChunkX + viewDist; cx++) {
                for (int cz = camChunkZ - viewDist; cz <= camChunkZ + viewDist; cz++) {
                    WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be == null || be.isRemoved()) continue;
                        BlockPos pos = be.getPos();
                        if (pos.getSquaredDistance(camPos.x, camPos.y, camPos.z) > maxDistSq) continue;
                        int y = pos.getY();
                        if (y < minY.getValue() || y > maxYLimit.getValue()) continue;
                        int color = detectBlockEntity(be);
                        if (color == 0) {
                            color = detectBlockState(mc.world.getBlockState(pos));
                        }
                        if (color != 0) {
                            detections.put(pos, new Detection(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, labelFor(be), color));
                        }
                    }
                }
            }
        }

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        GameRendererAccessor gra = (GameRendererAccessor) mc.gameRenderer;
        float fovDeg = gra.invokeGetFov(camera, mc.getRenderTickCounter().getDynamicDeltaTicks(), true);
        double vFovRad = Math.toRadians(fovDeg);
        double fovScaleV = Math.tan(vFovRad / 2);
        double fovScaleH = fovScaleV * w / h;

        for (Map.Entry<BlockPos, Detection> entry : detections.entrySet()) {
            Detection d = entry.getValue();
            Vec3d pos = new Vec3d(d.x, d.y, d.z);
            if (pos.squaredDistanceTo(camPos) > maxDistSq) continue;
            Box bb = new Box(d.x - 0.5, d.y - 0.5, d.z - 0.5, d.x + 0.5, d.y + 0.5, d.z + 0.5);
            try (var scope = wr.startDrawingGizmos()) {
                if (fillAlpha > 0) {
                    int fillColor = (d.color & 0x00FFFFFF) | ((int) (Math.min(255, fillAlpha * 255)) << 24);
                    GizmoDrawing.box(bb, DrawStyle.filled(fillColor)).ignoreOcclusion();
                }
                GizmoDrawing.box(bb, DrawStyle.stroked(d.color, (float) lw)).ignoreOcclusion();
            }
            if (hasTracers) {
                Vec3d rel = pos.subtract(camPos);
                double df = rel.dotProduct(forward);
                if (df > 0.01) {
                    double dr = rel.dotProduct(right);
                    double du = rel.dotProduct(up);
                    double absDf = Math.max(df, 0.01);
                    double sx = (dr / (absDf * fovScaleH)) * (w / 2.0) + w / 2.0;
                    double sy = (-du / (absDf * fovScaleV)) * (h / 2.0) + h / 2.0;
                    screenTracers.add(new int[]{(int) Math.round(sx), (int) Math.round(sy)});
                }
            }
        }
    }

    private int detectBlockEntity(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return showChest.getValue() ? chestColor.getValue() : 0;
        if (be instanceof TrappedChestBlockEntity) return showTrappedChest.getValue() ? chestColor.getValue() : 0;
        if (be instanceof EnderChestBlockEntity) return showEnderChest.getValue() ? chestColor.getValue() : 0;
        if (be instanceof BarrelBlockEntity) return showBarrel.getValue() ? chestColor.getValue() : 0;
        if (be instanceof ShulkerBoxBlockEntity) return showShulker.getValue() ? chestColor.getValue() : 0;
        if (be instanceof MobSpawnerBlockEntity) return showSpawner.getValue() ? spawnerColor.getValue() : 0;
        if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity || be instanceof DropperBlockEntity)
            return showHopper.getValue() ? defaultColor.getValue() : 0;
        if (be instanceof FurnaceBlockEntity || be instanceof SmokerBlockEntity || be instanceof BlastFurnaceBlockEntity)
            return showFurnace.getValue() ? defaultColor.getValue() : 0;
        if (be instanceof SignBlockEntity || be instanceof HangingSignBlockEntity)
            return showSign.getValue() ? signColor.getValue() : 0;
        if (be instanceof SkullBlockEntity) return showHead.getValue() ? headColor.getValue() : 0;
        if (be instanceof BrewingStandBlockEntity) return showBrewing.getValue() ? defaultColor.getValue() : 0;
        if (be instanceof ComparatorBlockEntity) return showHopper.getValue() ? defaultColor.getValue() : 0;
        if (be instanceof BedBlockEntity) return showBed.getValue() ? defaultColor.getValue() : 0;
        return 0;
    }

    private int detectBlockState(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof PistonBlock || block instanceof PistonHeadBlock)
            return showPiston.getValue() ? pistonColor.getValue() : 0;
        if (block instanceof ObserverBlock) return showObserver.getValue() ? defaultColor.getValue() : 0;
        if (block instanceof CraftingTableBlock || block instanceof StonecutterBlock
            || block instanceof GrindstoneBlock || block instanceof LoomBlock
            || block instanceof CartographyTableBlock || block instanceof SmithingTableBlock)
            return showCrafting.getValue() ? defaultColor.getValue() : 0;
        if (block instanceof AnvilBlock) return showAnvil.getValue() ? defaultColor.getValue() : 0;
        if (block instanceof BedBlock) return showBed.getValue() ? defaultColor.getValue() : 0;
        if (showDeepslate.getValue() && isRotatedDeepslate(state)) return deepslateColor.getValue();
        return 0;
    }

    private boolean isRotatedDeepslate(BlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.DEEPSLATE && block != Blocks.COBBLED_DEEPSLATE) return false;
        if (!(block instanceof PillarBlock)) return false;
        var axis = state.get(PillarBlock.AXIS);
        return axis != Direction.Axis.Y;
    }

    private String labelFor(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return "Chest";
        if (be instanceof TrappedChestBlockEntity) return "Trapped Chest";
        if (be instanceof EnderChestBlockEntity) return "Ender Chest";
        if (be instanceof BarrelBlockEntity) return "Barrel";
        if (be instanceof ShulkerBoxBlockEntity) return "Shulker";
        if (be instanceof MobSpawnerBlockEntity) return "Spawner";
        if (be instanceof HopperBlockEntity) return "Hopper";
        if (be instanceof DispenserBlockEntity) return "Dispenser";
        if (be instanceof DropperBlockEntity) return "Dropper";
        if (be instanceof FurnaceBlockEntity) return "Furnace";
        if (be instanceof SmokerBlockEntity) return "Smoker";
        if (be instanceof BlastFurnaceBlockEntity) return "Blast Furnace";
        if (be instanceof SignBlockEntity) return "Sign";
        if (be instanceof SkullBlockEntity) return "Head";
        if (be instanceof BrewingStandBlockEntity) return "Brewing Stand";
        return "";
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !tracers.getValue()) return;
        if (screenTracers.isEmpty()) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int cx = w / 2;
        int cy = h / 2;
        int tc = tracerColor.getValue();
        int thick = Math.max(1, (int) Math.round(lineWidth.getValue()));
        var matrices = context.getMatrices();

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
