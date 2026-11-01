package com.winnerclient.fabric.module.misc;

import net.minecraft.util.math.BlockPos;

public class DetectedMovement {
    public final BlockPos pos;
    public final String type;
    public final String description;
    public final long detectTime;
    public final int durationMs;
    public final boolean distant;

    public DetectedMovement(
        BlockPos pos,
        String type,
        String description,
        long detectTime,
        int durationMs
    ) {
        this(pos, type, description, detectTime, durationMs, false);
    }

    public DetectedMovement(
        BlockPos pos,
        String type,
        String description,
        long detectTime,
        int durationMs,
        boolean distant
    ) {
        this.pos = pos;
        this.type = type;
        this.description = description;
        this.detectTime = detectTime;
        this.durationMs = durationMs;
        this.distant = distant;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - detectTime > durationMs;
    }

    public long ageMs() {
        return System.currentTimeMillis() - detectTime;
    }
}
