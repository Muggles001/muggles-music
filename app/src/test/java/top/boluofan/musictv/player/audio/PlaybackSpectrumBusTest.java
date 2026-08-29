package top.boluofan.musictv.player.audio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackSpectrumBusTest {
    @Test
    public void publishCopiesBandsAndUpdatesSnapshot() {
        PlaybackSpectrumBus bus = new PlaybackSpectrumBus();
        float[] source = new float[PlaybackSpectrumBus.BAND_COUNT];
        for (int index = 0; index < source.length; index++) source[index] = index / 24f;
        float[] destination = new float[PlaybackSpectrumBus.BAND_COUNT];
        PlaybackSpectrumBus.Snapshot snapshot = new PlaybackSpectrumBus.Snapshot();

        bus.publish(source, 1.4f, 42L);
        int copied = bus.copyLatest(destination, snapshot);

        assertEquals(PlaybackSpectrumBus.BAND_COUNT, copied);
        for (int index = 0; index < destination.length; index++) {
            assertEquals(source[index], destination[index], 0.000_001f);
        }
        assertEquals(1L, snapshot.generation);
        assertEquals(42L, snapshot.timestampNanos);
        assertEquals(1f, snapshot.level, 0.000_001f);
    }

    @Test
    public void clearPublishesANewerZeroSnapshot() {
        PlaybackSpectrumBus bus = new PlaybackSpectrumBus();
        float[] source = new float[PlaybackSpectrumBus.BAND_COUNT];
        java.util.Arrays.fill(source, 0.5f);
        bus.publish(source, 0.5f, 99L);

        bus.clear();

        float[] destination = new float[PlaybackSpectrumBus.BAND_COUNT];
        PlaybackSpectrumBus.Snapshot snapshot = new PlaybackSpectrumBus.Snapshot();
        bus.copyLatest(destination, snapshot);
        for (float band : destination) assertEquals(0f, band, 0.000_001f);
        assertEquals(2L, snapshot.generation);
        assertEquals(0L, snapshot.timestampNanos);
        assertEquals(0f, snapshot.level, 0.000_001f);
    }
}
