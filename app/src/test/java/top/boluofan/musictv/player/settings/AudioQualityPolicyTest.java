package top.boluofan.musictv.player.settings;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class AudioQualityPolicyTest {
    @Test
    public void losslessFallsBackThroughSupportedLowerQualities() {
        assertEquals("320k", AudioQualityPolicy.choose(
                "flac", Arrays.asList("128k", "320k")));
        assertEquals("128k", AudioQualityPolicy.choose(
                "flac", Arrays.asList("128k")));
    }

    @Test
    public void mediumQualityDoesNotUpgrade() {
        assertEquals("128k", AudioQualityPolicy.choose(
                "320k", Arrays.asList("flac", "128k")));
    }

    @Test
    public void lowestQualityUsesFirstDeclaredPlayableTypeWhenAbsent() {
        assertEquals("flac", AudioQualityPolicy.choose(
                "128k", Arrays.asList("flac", "320k")));
    }
}
