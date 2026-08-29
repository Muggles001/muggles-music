package top.boluofan.musictv.player.visual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WaveformProgressViewTest {
    @Test
    public void seekPositionIsClampedToPlayableRange() {
        assertEquals(0L, WaveformProgressView.clampSeekPosition(-1L, 60_000L));
        assertEquals(20_000L, WaveformProgressView.clampSeekPosition(20_000L, 60_000L));
        assertEquals(60_000L, WaveformProgressView.clampSeekPosition(90_000L, 60_000L));
        assertEquals(0L, WaveformProgressView.clampSeekPosition(20_000L, 0L));
    }

    @Test
    public void timeLabelsUseCompactPlayerFormat() {
        char[] buffer = new char[16];

        int currentLength = WaveformProgressView.writeTime(buffer, 794L, false);
        assertEquals("13:14", new String(buffer, 0, currentLength));

        int remainingLength = WaveformProgressView.writeTime(buffer, 52L, true);
        assertEquals("-0:52", new String(buffer, 0, remainingLength));

        int hourLength = WaveformProgressView.writeTime(buffer, 3_723L, false);
        assertEquals("1:02:03", new String(buffer, 0, hourLength));
    }

    @Test
    public void dpadSeekTargetsSaturateWithoutOverflow() {
        assertEquals(0L, WaveformProgressView.seekBackwardTarget(2_000L, 10_000L));
        assertEquals(15_000L, WaveformProgressView.seekForwardTarget(10_000L, 15_000L, 10_000L));
        assertEquals(
                Long.MAX_VALUE,
                WaveformProgressView.seekForwardTarget(Long.MAX_VALUE - 1L, Long.MAX_VALUE, 10_000L)
        );
    }

    @Test
    public void playingProgressInterpolatesBetweenAuthoritativeUpdates() {
        assertEquals(
                12_016L,
                WaveformProgressView.interpolatedPosition(12_000L, 60_000L, 16L, true)
        );
        assertEquals(
                12_000L,
                WaveformProgressView.interpolatedPosition(12_000L, 60_000L, 16L, false)
        );
        assertEquals(
                60_000L,
                WaveformProgressView.interpolatedPosition(59_990L, 60_000L, 16L, true)
        );
    }

    @Test
    public void spectrumMovesSmoothlyTowardLatestFrame() {
        float first = WaveformProgressView.interpolateSpectrum(0f, 1f, 16L);
        float second = WaveformProgressView.interpolateSpectrum(first, 1f, 16L);
        float oneLongFrame = WaveformProgressView.interpolateSpectrum(0f, 1f, 32L);

        assertTrue(first > 0f && first < 1f);
        assertTrue(second > first && second < 1f);
        assertEquals(oneLongFrame, second, 0.0001f);
    }
}
