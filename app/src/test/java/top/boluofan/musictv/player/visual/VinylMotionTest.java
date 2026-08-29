package top.boluofan.musictv.player.visual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VinylMotionTest {
    @Test
    public void playingCompletesOneRevolutionEveryTwentySeconds() {
        VinylMotion motion = new VinylMotion();
        motion.setPlaying(true);

        motion.advance(10_000L);
        assertEquals(180f, motion.getAngleDegrees(), 0.001f);

        motion.advance(10_000L);
        assertEquals(0f, motion.getAngleDegrees(), 0.001f);
        assertEquals(
                VinylMotion.PLAYBACK_DEGREES_PER_MILLISECOND,
                motion.getAngularVelocity(),
                0f
        );
    }

    @Test
    public void pausingDeceleratesThenStopsWithoutFurtherRotation() {
        VinylMotion motion = new VinylMotion();
        motion.setPlaying(true);
        motion.advance(1_000L);
        motion.setPlaying(false);

        float angleAtPause = motion.getAngleDegrees();
        motion.advance(360L);
        assertTrue(motion.getAngleDegrees() > angleAtPause);
        assertTrue(motion.getAngularVelocity() > 0f);

        motion.advance(360L);
        float stoppedAngle = motion.getAngleDegrees();
        assertEquals(0f, motion.getAngularVelocity(), 0f);
        motion.advance(5_000L);
        assertEquals(stoppedAngle, motion.getAngleDegrees(), 0f);
        assertFalse(motion.isAnimating());
    }

    @Test
    public void tonearmTravelsBetweenRestAndTrack() {
        VinylMotion motion = new VinylMotion();
        motion.setPlaying(true);
        motion.advance(460L);

        assertEquals(1f, motion.getTonearmProgress(), 0f);

        motion.setPlaying(false);
        motion.advance(460L);
        motion.advance(720L);

        assertEquals(0f, motion.getTonearmProgress(), 0f);
        assertFalse(motion.isAnimating());
    }
}
