package top.boluofan.musictv.player.audio;

final class SpectrumMath {
    private static final double LOW_FREQUENCY_HZ = 55.0;
    private static final double HIGH_FREQUENCY_HZ = 12000.0;
    private static final double LOG_NORMALIZER = Math.log1p(24.0);

    private SpectrumMath() {
    }

    static void prepareCoefficients(int sampleRate, float[] coefficients) {
        double maximum = Math.min(HIGH_FREQUENCY_HZ, sampleRate * 0.45);
        double ratio = maximum / LOW_FREQUENCY_HZ;
        int denominator = Math.max(1, coefficients.length - 1);
        for (int band = 0; band < coefficients.length; band++) {
            double frequency = LOW_FREQUENCY_HZ * Math.pow(ratio, band / (double) denominator);
            coefficients[band] = (float) (2.0 * Math.cos(2.0 * Math.PI * frequency / sampleRate));
        }
    }

    static float analyze(float[] samples, int sampleCount, float[] coefficients,
            float[] previousBands, float[] outputBands) {
        double squareSum = 0.0;
        for (int sample = 0; sample < sampleCount; sample++) {
            float value = samples[sample];
            squareSum += value * value;
        }

        for (int band = 0; band < coefficients.length; band++) {
            float coefficient = coefficients[band];
            float first = 0f;
            float second = 0f;
            for (int sample = 0; sample < sampleCount; sample++) {
                float next = samples[sample] + coefficient * first - second;
                second = first;
                first = next;
            }
            double power = first * first + second * second - coefficient * first * second;
            double amplitude = Math.sqrt(Math.max(0.0, power)) / sampleCount;
            float normalized = (float) Math.min(1.0, Math.log1p(amplitude * 24.0) / LOG_NORMALIZER);
            float previous = previousBands[band];
            float response = normalized > previous ? 0.5f : 0.12f;
            float smoothed = previous + (normalized - previous) * response;
            outputBands[band] = smoothed;
            previousBands[band] = smoothed;
        }

        return (float) Math.min(1.0, Math.sqrt(squareSum / sampleCount) * 2.5);
    }
}
