package top.boluofan.musictv.player.audio;

import java.util.Arrays;

public final class PlaybackSpectrumBus {
    public static final int BAND_COUNT = 24;

    public static final class Snapshot {
        public long generation;
        public long timestampNanos;
        public float level;
    }

    private static final PlaybackSpectrumBus SHARED = new PlaybackSpectrumBus();

    private final float[] bands = new float[BAND_COUNT];
    private long generation;
    private long timestampNanos;
    private float level;

    public static PlaybackSpectrumBus getShared() {
        return SHARED;
    }

    public synchronized int copyLatest(float[] destination, Snapshot snapshot) {
        if (destination == null) return 0;
        int copied = Math.min(destination.length, BAND_COUNT);
        System.arraycopy(bands, 0, destination, 0, copied);
        if (snapshot != null) {
            snapshot.generation = generation;
            snapshot.timestampNanos = timestampNanos;
            snapshot.level = level;
        }
        return copied;
    }

    synchronized void publish(float[] sourceBands, float sourceLevel, long sourceTimestampNanos) {
        System.arraycopy(sourceBands, 0, bands, 0, BAND_COUNT);
        level = clamp01(sourceLevel);
        timestampNanos = sourceTimestampNanos;
        generation++;
    }

    public synchronized void clear() {
        Arrays.fill(bands, 0f);
        level = 0f;
        timestampNanos = 0L;
        generation++;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }
}
