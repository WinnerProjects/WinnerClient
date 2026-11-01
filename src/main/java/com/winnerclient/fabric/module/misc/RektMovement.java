package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class RektMovement extends Module {
    private static final int MAX_BLOCKS = 1500;

    private final Setting.IntSetting radius = addInt(
        "Radius",
        "Block scan radius.",
        6,
        2,
        12
    );

    private final Setting.IntSetting scanInterval = addInt(
        "ScanInterval",
        "Number of ticks between scans.",
        10,
        5,
        40
    );

    private final Setting.IntSetting displayDuration = addInt(
        "DisplayDuration",
        "How long detections are retained, in milliseconds.",
        4000,
        500,
        10000
    );

    private final Setting.BoolSetting showCoords = addBool(
        "ShowCoords",
        "Include coordinates in movement chat messages.",
        true
    );

    private final Setting.BoolSetting chatLog = addBool(
        "ChatLog",
        "Print each detection to chat.",
        false
    );

    public final List<DetectedMovement> detections = new CopyOnWriteArrayList<>();

    private final Map<BlockPos, BlockState> prevBlocks = new HashMap<>();

    private ClientWorld snapshotWorld;
    private int tickCounter;

    // Monotonically increasing so RektAlert can detect new entries even when
    // older entries expire at the same time.
    private long detectionSequence;
    private DetectedMovement latestDetection;

    public RektMovement() {
        super(
            "RektMovement",
            "Detects nearby block state changes.",
            Category.MISC
        );
    }

    @Override
    protected void onEnable() {
        clearSnapshot();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && mc.player != null) {
            snapshotWorld = mc.world;
            snapshotBlocks(mc);
        }
    }

    @Override
    protected void onDisable() {
        clearSnapshot();
    }

    @Override
    public void onDisconnect() {
        clearSnapshot();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        // Start a fresh baseline after joining or changing dimensions.
        if (snapshotWorld != mc.world) {
            clearSnapshot();
            snapshotWorld = mc.world;
            snapshotBlocks(mc);
            return;
        }

        tickCounter++;
        detections.removeIf(DetectedMovement::isExpired);

        if (tickCounter >= Math.max(1, scanInterval.getValue())) {
            tickCounter = 0;
            scanForChanges(mc);
            snapshotBlocks(mc);
        }
    }

    public long getDetectionSequence() {
        return detectionSequence;
    }

    public DetectedMovement getLatestDetection() {
        return latestDetection;
    }

    private void clearSnapshot() {
        prevBlocks.clear();
        detections.clear();
        latestDetection = null;
        snapshotWorld = null;
        tickCounter = 0;
    }

    private void snapshotBlocks(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;

        BlockPos center = BlockPos.ofFloored(mc.player.getEntityPos());
        int px = center.getX();
        int py = center.getY();
        int pz = center.getZ();
        int r = radius.getValue();

        int minY = Math.max(mc.world.getBottomY(), py - r);
        int maxY = Math.min(mc.world.getTopYInclusive(), py + r);

        prevBlocks.clear();
        int count = 0;

        outer:
        for (int x = px - r; x <= px + r; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = pz - r; z <= pz + r; z++) {
                    if (count >= MAX_BLOCKS) break outer;

                    BlockPos pos = new BlockPos(x, y, z);
                    prevBlocks.put(pos, mc.world.getBlockState(pos));
                    count++;
                }
            }
        }
    }

    private void scanForChanges(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;

        List<DetectedMovement> newDetections = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockState> entry : prevBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState oldState = entry.getValue();
            BlockState newState = mc.world.getBlockState(pos);

            if (newState.equals(oldState)) continue;

            String type = "Block Change";
            String description = oldState.getBlock().getName().getString()
                + " -> "
                + newState.getBlock().getName().getString();

            DetectedMovement detection = new DetectedMovement(
                pos,
                type,
                description,
                System.currentTimeMillis(),
                displayDuration.getValue()
            );

            newDetections.add(detection);
            latestDetection = detection;
            detectionSequence++;

            if (chatLog.getValue()) {
                String message;

                if (showCoords.getValue()) {
                    message = String.format(
                        "[RektMovement] %s (%d,%d,%d): %s",
                        type,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        description
                    );
                } else {
                    message = "[RektMovement] " + type + ": " + description;
                }

                mc.player.sendMessage(Text.literal(message), false);
            }
        }

        detections.addAll(newDetections);
    }
}
