package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class RedstoneDebug extends Module {
    private static final long DEDUP_MS = 30_000L;
    private static final int MAX_RECENT = 10;
    private static final int MAX_REPORTED_POSITIONS = 8192;
    private static final int MAX_FLAGGED_CHUNKS = 1024;
    private static final int MAX_NBT_ENTRIES = 4096;

    private static final Set<Block> FARM_BLOCKS = Set.of(
        Blocks.KELP,
        Blocks.KELP_PLANT,
        Blocks.BAMBOO,
        Blocks.BAMBOO_SAPLING,
        Blocks.SUGAR_CANE
    );

    private static final Set<Block> REDSTONE_BLOCKS = Set.of(
        Blocks.REDSTONE_WIRE,
        Blocks.REPEATER,
        Blocks.COMPARATOR,
        Blocks.REDSTONE_TORCH,
        Blocks.REDSTONE_WALL_TORCH,
        Blocks.REDSTONE_LAMP,
        Blocks.LEVER,
        Blocks.OBSERVER,
        Blocks.NOTE_BLOCK,
        Blocks.DAYLIGHT_DETECTOR,
        Blocks.TARGET,
        Blocks.DISPENSER,
        Blocks.DROPPER,
        Blocks.HOPPER,
        Blocks.PISTON,
        Blocks.STICKY_PISTON,
        Blocks.POWERED_RAIL,
        Blocks.DETECTOR_RAIL,
        Blocks.ACTIVATOR_RAIL,
        Blocks.SCULK_SENSOR,
        Blocks.CALIBRATED_SCULK_SENSOR,
        Blocks.SCULK_SHRIEKER
    );

    private final Setting.BoolSetting detectPistons = addBool(
        "DetectPistons",
        "Detect piston state updates.",
        true
    );

    private final Setting.BoolSetting detectFarmBreaks = addBool(
        "DetectFarmBreaks",
        "Detect kelp, bamboo and sugar cane breaking.",
        true
    );

    private final Setting.BoolSetting detectNbt = addBool(
        "DetectNBT",
        "Detect changed block-entity NBT packets.",
        true
    );

    private final Setting.BoolSetting detectRedstone = addBool(
        "DetectRedstone",
        "Detect redstone block updates.",
        true
    );

    private final Setting.BoolSetting detectObservers = addBool(
        "DetectObservers",
        "Detect observer updates.",
        true
    );

    private final Setting.BoolSetting detectNoteBlocks = addBool(
        "DetectNoteBlocks",
        "Detect note block updates.",
        true
    );

    private final Setting.BoolSetting scanUnderground = addBool(
        "ScanUnderground",
        "Scan loaded chunks below Y -20 for redstone components.",
        true
    );

    private final Setting.BoolSetting logToChat = addBool(
        "LogToChat",
        "Print detections in chat.",
        true
    );

    private final Setting.BoolSetting logToHud = addBool(
        "LogToHUD",
        "Show recent detections on screen.",
        true
    );

    private final Setting.IntSetting hudLines = addInt(
        "HUDLines",
        "Maximum recent HUD entries.",
        6,
        1,
        MAX_RECENT
    );

    private final Setting.ColorSetting chunkColor = addColor(
        "ChunkColor",
        "Flagged chunk fill color.",
        0x50FF6400,
        true
    );

    private final Setting.ColorSetting chunkLineColor = addColor(
        "ChunkLineColor",
        "Flagged chunk outline color.",
        0xFFFF6400,
        true
    );

    private final Setting.BoolSetting tracer = addBool(
        "Tracer",
        "Draw lines to flagged chunks.",
        true
    );

    private final Setting.ColorSetting tracerColor = addColor(
        "TracerColor",
        "Tracer line color.",
        0x96FF6400,
        true
    );

    private final Setting.ModeSetting tracerOrigin = addMode(
        "TracerOrigin",
        "Where tracer lines begin.",
        "Camera",
        "Camera",
        "Player"
    );

    /*
     * All collections are accessed from the client thread.
     * Chunk keys pack X into the high 32 bits and Z into the low 32 bits.
     */
    private final Map<BlockPos, Long> lastReported = new LinkedHashMap<>();
    private final Map<BlockPos, NbtCompound> previousNbt = new LinkedHashMap<>();
    private final Map<Long, String> flaggedChunks = new LinkedHashMap<>();
    private final Map<Long, Set<BlockPos>> undergroundCache = new HashMap<>();
    private final Set<Long> scannedChunks = new HashSet<>();
    private final Deque<String> recent = new ArrayDeque<>();

    private ClientWorld currentWorld;
    private int scanCursor;

    public RedstoneDebug() {
        super(
            "RedstoneDebug",
            "Detects redstone activity, farm breaks and underground components.",
            Category.MISC
        );
    }

    @Override
    protected void onEnable() {
        clearData();
        currentWorld = MinecraftClient.getInstance().world;
    }

    @Override
    protected void onDisable() {
        clearData();
        currentWorld = null;
    }

    @Override
    public void onDisconnect() {
        clearData();
        currentWorld = null;
    }

    private void clearData() {
        lastReported.clear();
        previousNbt.clear();
        flaggedChunks.clear();
        undergroundCache.clear();
        scannedChunks.clear();
        recent.clear();
        scanCursor = 0;
    }

    private boolean prepareWorld() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isEnabled() || mc.player == null || mc.world == null) {
            return false;
        }

        if (currentWorld != mc.world) {
            clearData();
            currentWorld = mc.world;
        }

        return true;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    private static int chunkX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkZ(long key) {
        return (int) key;
    }

    /*
     * Called before the server's block update is applied.
     */
    public void onBlockUpdate(BlockPos position, BlockState newState) {
        if (!prepareWorld()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        BlockPos pos = position.toImmutable();

        BlockState oldState = mc.world.getBlockState(pos);
        Block oldBlock = oldState.getBlock();
        Block newBlock = newState.getBlock();

        updateUndergroundCache(pos, newBlock);

        if (detectPistons.getValue()
            && (newBlock == Blocks.PISTON || newBlock == Blocks.STICKY_PISTON)) {
            report(pos, "PISTON", newState.toString());
            return;
        }

        if (detectFarmBreaks.getValue()
            && FARM_BLOCKS.contains(oldBlock)
            && newState.isAir()) {
            report(pos, "FARM", oldBlock.getName().getString() + " broken");
            return;
        }

        /*
         * The original bytecode also reports certain special block changes
         * under its NBT category. This is separate from an actual NBT packet.
         */
        if (detectNbt.getValue() && oldBlock != newBlock && !newState.isAir()) {
            String id = Registries.BLOCK.getId(newBlock).getPath();

            if (id.contains("spawner")
                || id.contains("command_block")
                || id.contains("structure_block")) {
                report(pos, "NBT", newBlock.getName().getString() + " appeared");
            }
        }

        if (!detectRedstone.getValue()) return;

        if (newBlock == Blocks.OBSERVER && !detectObservers.getValue()) {
            return;
        }

        if (newBlock == Blocks.NOTE_BLOCK && !detectNoteBlocks.getValue()) {
            return;
        }

        if (REDSTONE_BLOCKS.contains(newBlock)) {
            String type = newBlock == Blocks.OBSERVER ? "OBSERVER" : "REDSTONE";
            report(pos, type, newState.toString());
        }
    }

    /*
     * Tracks actual block-entity update packets. The first packet establishes
     * a baseline; later packets report only when the NBT differs.
     */
    public void onBlockEntityUpdate(BlockPos position, NbtCompound nbt) {
        if (!prepareWorld() || !detectNbt.getValue() || nbt == null) return;

        BlockPos pos = position.toImmutable();
        NbtCompound oldNbt = previousNbt.put(pos, nbt.copy());

        while (previousNbt.size() > MAX_NBT_ENTRIES) {
            BlockPos oldest = previousNbt.keySet().iterator().next();
            previousNbt.remove(oldest);
        }

        if (oldNbt != null && !oldNbt.equals(nbt)) {
            report(pos, "NBT", "Block-entity data changed");
        }
    }

    private void updateUndergroundCache(BlockPos pos, Block block) {
        if (pos.getY() >= -20) return;

        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> blocks = undergroundCache.get(key);

        if (REDSTONE_BLOCKS.contains(block)) {
            if (blocks == null) {
                blocks = new HashSet<>();
                undergroundCache.put(key, blocks);
            }

            blocks.add(pos.toImmutable());
            flagChunk(key, "Underground redstone");
        } else if (blocks != null) {
            blocks.remove(pos);
        }
    }

    private void report(BlockPos position, String type, String description) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        BlockPos pos = position.toImmutable();
        long now = System.currentTimeMillis();

        Long last = lastReported.get(pos);
        if (last != null && now - last < DEDUP_MS) return;

        lastReported.remove(pos);
        lastReported.put(pos, now);

        while (lastReported.size() > MAX_REPORTED_POSITIONS) {
            BlockPos oldest = lastReported.keySet().iterator().next();
            lastReported.remove(oldest);
        }

        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        flagChunk(key, type);

        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dy = mc.player.getY() - (pos.getY() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

        String line = "[" + type + "] "
            + description
            + " @ "
            + pos.getX() + ", "
            + pos.getY() + ", "
            + pos.getZ()
            + " (" + distance + "m)";

        recent.addFirst(line);

        while (recent.size() > MAX_RECENT) {
            recent.removeLast();
        }

        if (logToChat.getValue()) {
            mc.player.sendMessage(
                Text.literal("[RedstoneDebug] " + line),
                false
            );
        }
    }

    private void flagChunk(long key, String reason) {
        if (flaggedChunks.containsKey(key)) return;

        flaggedChunks.put(key, reason);

        while (flaggedChunks.size() > MAX_FLAGGED_CHUNKS) {
            Long oldest = flaggedChunks.keySet().iterator().next();
            flaggedChunks.remove(oldest);
        }
    }

    @Override
    public void onTick() {
        if (!prepareWorld() || !scanUnderground.getValue()) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        int playerChunkX = mc.player.getBlockPos().getX() >> 4;
        int playerChunkZ = mc.player.getBlockPos().getZ() >> 4;
        int radius = mc.options.getViewDistance().getValue();
        int width = radius * 2 + 1;
        int total = width * width;

        /*
         * Scan no more than one loaded, previously unscanned chunk per tick.
         * This is intentionally slower than scanning every chunk at once.
         */
        for (int attempts = 0; attempts < total; attempts++) {
            int index = Math.floorMod(scanCursor++, total);
            int cx = playerChunkX - radius + index / width;
            int cz = playerChunkZ - radius + index % width;
            long key = chunkKey(cx, cz);

            if (scannedChunks.contains(key)) continue;

            WorldChunk chunk = mc.world.getChunkManager().getChunk(
                cx,
                cz,
                ChunkStatus.FULL,
                false
            );

            if (chunk == null) continue;

            scanChunk(chunk, cx, cz);
            break;
        }

        /*
         * Allow chunks to be scanned again after moving away and returning.
         * Flagged chunks stay visible until the module is disabled or the
         * world changes.
         */
        int keepRadius = radius + 2;

        scannedChunks.removeIf(key ->
            Math.abs(chunkX(key) - playerChunkX) > keepRadius
                || Math.abs(chunkZ(key) - playerChunkZ) > keepRadius
        );

        undergroundCache.keySet().removeIf(key ->
            Math.abs(chunkX(key) - playerChunkX) > keepRadius
                || Math.abs(chunkZ(key) - playerChunkZ) > keepRadius
        );
    }

    private void scanChunk(WorldChunk chunk, int chunkX, int chunkZ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        long key = chunkKey(chunkX, chunkZ);
        Set<BlockPos> found = new HashSet<>();

        int minY = mc.world.getBottomY();
        int maxY = Math.min(-20, mc.world.getTopYInclusive() + 1);

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set((chunkX << 4) + x, y, (chunkZ << 4) + z);

                    if (REDSTONE_BLOCKS.contains(
                        chunk.getBlockState(mutable).getBlock()
                    )) {
                        found.add(mutable.toImmutable());
                    }
                }
            }
        }

        scannedChunks.add(key);
        undergroundCache.put(key, found);

        if (!found.isEmpty()) {
            boolean wasFlagged = flaggedChunks.containsKey(key);
            flagChunk(key, "Underground redstone: " + found.size());

            if (!wasFlagged && logToChat.getValue() && mc.player != null) {
                mc.player.sendMessage(
                    Text.literal(
                        "[RedstoneDebug] Underground redstone in chunk "
                            + chunkX + ", " + chunkZ
                            + " (" + found.size() + " blocks)"
                    ),
                    false
                );
            }
        }
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        if (!logToHud.getValue()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        int x = 4;
        int y = 60;

        context.drawText(
            mc.textRenderer,
            "[Redstone Debug] Activity Analysis",
            x,
            y,
            0xFFFFAA55,
            true
        );

        y += 11;

        if (recent.isEmpty()) {
            context.drawText(
                mc.textRenderer,
                "No movements detected...",
                x,
                y,
                0xFFAAAAAA,
                true
            );
            return;
        }

        int count = 0;

        for (String line : recent) {
            if (count++ >= hudLines.getValue()) break;

            context.drawText(
                mc.textRenderer,
                line,
                x,
                y,
                0xFFFFFFFF,
                true
            );

            y += 10;
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) return;

        WorldRenderer renderer = context.worldRenderer();
        if (renderer == null) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        Vec3d origin = tracerOrigin.getValue().equals("Player")
            ? new Vec3d(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ()
            )
            : cameraPos;

        try (var scope = renderer.startDrawingGizmos()) {
            for (long key : flaggedChunks.keySet()) {
                int x = chunkX(key) << 4;
                int z = chunkZ(key) << 4;

                double y = mc.world.getTopY(
                    Heightmap.Type.WORLD_SURFACE,
                    x + 8,
                    z + 8
                );

                Box box = new Box(
                    x,
                    y - 0.5,
                    z,
                    x + 16,
                    y + 0.5,
                    z + 16
                );

                GizmoDrawing.box(
                    box,
                    DrawStyle.filled(chunkColor.getValue())
                ).ignoreOcclusion();

                GizmoDrawing.box(
                    box,
                    DrawStyle.stroked(chunkLineColor.getValue(), 1.5f)
                ).ignoreOcclusion();

                if (tracer.getValue()) {
                    Vec3d target = new Vec3d(x + 8.0, y, z + 8.0);

                    GizmoDrawing.line(
                        origin,
                        target,
                        tracerColor.getValue(),
                        1.5f
                    ).ignoreOcclusion();
                }
            }
        }
    }
    }
