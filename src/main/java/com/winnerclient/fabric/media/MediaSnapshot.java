package com.winnerclient.fabric.media;

public record MediaSnapshot(
        String key,
        String title,
        String artist,
        String album,
        byte[] artwork,
        boolean playing,
        long positionMs,
        long durationMs,
        long sampledAtNanos,
        boolean previous,
        boolean toggle,
        boolean next,
        boolean seek,
        String status
) {
    public static MediaSnapshot empty(String status) {
        return new MediaSnapshot(
                "", "", "", "", null,
                false, -1, -1,
                System.nanoTime(),
                false, false, false, false,
                status
        );
    }

    public long estimatedPosition() {
        if (positionMs < 0) {
            return -1;
        }

        long elapsed = playing
                ? Math.max(
                        0,
                        (System.nanoTime() - sampledAtNanos) / 1_000_000L
                )
                : 0;

        long position = positionMs + elapsed;

        return durationMs > 0
                ? Math.min(position, durationMs)
                : position;
    }
}
