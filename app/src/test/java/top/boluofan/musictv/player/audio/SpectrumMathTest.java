package top.boluofan.musictv.player.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpectrumMathTest {
    @Test
    public void analyzePlacesToneInExpectedLogarithmicBand() {
        int sampleRate = 48000;
        float[] samples = new float[1024];
        for (int sample = 0; sample < samples.length; sample++) {
            samples[sample] = (float) Math.sin(2.0 * Math.PI * 440.0 * sample / sampleRate);
        }
        float[] coefficients = new float[24];
        float[] previous = new float[24];
        float[] output = new float[24];
        SpectrumMath.prepareCoefficients(sampleRate, coefficients);

        float level = SpectrumMath.analyze(samples, samples.length, coefficients, previous, output);

        int strongestBand = 0;
        for (int band = 1; band < output.length; band++) {
            if (output[band] > output[strongestBand]) strongestBand = band;
        }
        assertTrue(strongestBand >= 7 && strongestBand <= 10);
        assertTrue(level > 0.5f);
        assertTrue(level <= 1f);
    }

    @Test
    public void analyzeReleasesBandsGraduallyAfterSilence() {
        float[] samples = new float[1024];
        for (int sample = 0; sample < samples.length; sample++) samples[sample] = 0.8f;
        float[] coefficients = new float[24];
        float[] previous = new float[24];
        float[] output = new float[24];
        SpectrumMath.prepareCoefficients(48000, coefficients);
        SpectrumMath.analyze(samples, samples.length, coefficients, previous, output);
        float active = output[0];

        for (int sample = 0; sample < samples.length; sample++) samples[sample] = 0f;
        float silentLevel = SpectrumMath.analyze(samples, samples.length, coefficients,
                previous, output);

        assertEquals(0f, silentLevel, 0.0001f);
        assertTrue(output[0] > 0f);
        assertTrue(output[0] < active);
    }
}
