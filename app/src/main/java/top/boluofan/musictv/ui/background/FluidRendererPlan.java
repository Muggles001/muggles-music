package top.boluofan.musictv.ui.background;

final class FluidRendererPlan {
    static final int PARTICLE_VEC4_COUNT = 3;
    static final int PARTICLE_FLOAT_COUNT = PARTICLE_VEC4_COUNT * 4;
    static final int PARTICLE_STRIDE_BYTES = PARTICLE_FLOAT_COUNT * Float.BYTES;

    private static final float[] PLUME_START = {0.02f, 0.70f};
    private static final float[] PLUME_CONTROL_A = {0.22f, 0.86f};
    private static final float[] PLUME_CONTROL_B = {0.58f, 0.26f};
    private static final float[] PLUME_END = {0.98f, 0.58f};

    private FluidRendererPlan() {
    }

    static float[] createInitialParticles(int particleCount) {
        if (particleCount <= 0) throw new IllegalArgumentException("Particle count must be positive");
        float[] particles = new float[particleCount * PARTICLE_FLOAT_COUNT];
        for (int particle = 0; particle < particleCount; particle++) {
            int offset = particle * PARTICLE_FLOAT_COUNT;
            float seed = unitHash(particle, 0x4f1bbcdc);
            float pathPosition = (particle + unitHash(particle, 0x94d049bb)) / particleCount;
            float[] center = cubicBezier(pathPosition);
            float[] tangent = cubicBezierTangent(pathPosition);
            float tangentLength = (float) Math.hypot(tangent[0], tangent[1]);
            float normalX = -tangent[1] / tangentLength;
            float normalY = tangent[0] / tangentLength;
            float lateralSample = unitHash(particle, 0x369dea0f)
                    + unitHash(particle, 0x7f4a7c15)
                    + unitHash(particle, 0x6a09e667)
                    + unitHash(particle, 0xbb67ae85) - 2f;
            float plumeWidth = 0.13f - 0.055f * pathPosition
                    + 0.035f * Math.abs(pathPosition - 0.58f);
            float lateralOffset = lateralSample * plumeWidth;
            float longitudinalOffset = (unitHash(particle, 0x3c6ef372) - 0.5f) * 0.012f;
            particles[offset] = clamp01(center[0] + normalX * lateralOffset
                    + tangent[0] / tangentLength * longitudinalOffset);
            particles[offset + 1] = clamp01(center[1] + normalY * lateralOffset
                    + tangent[1] / tangentLength * longitudinalOffset);
            particles[offset + 2] = 0f;
            particles[offset + 3] = 0f;
            particles[offset + 4] = 0.24f + unitHash(particle, 0xa54ff53a) * 0.48f;
            particles[offset + 5] = 0f;
            particles[offset + 6] = 0f;
            particles[offset + 7] = 18f + unitHash(particle, 0x510e527f) * 16f;
            particles[offset + 8] = seed;
            particles[offset + 9] = 0f;
            particles[offset + 10] = 0f;
            particles[offset + 11] = 0f;
        }
        return particles;
    }

    private static float[] cubicBezier(float t) {
        float inverse = 1f - t;
        float startWeight = inverse * inverse * inverse;
        float controlAWeight = 3f * inverse * inverse * t;
        float controlBWeight = 3f * inverse * t * t;
        float endWeight = t * t * t;
        return new float[]{
                PLUME_START[0] * startWeight + PLUME_CONTROL_A[0] * controlAWeight
                        + PLUME_CONTROL_B[0] * controlBWeight + PLUME_END[0] * endWeight,
                PLUME_START[1] * startWeight + PLUME_CONTROL_A[1] * controlAWeight
                        + PLUME_CONTROL_B[1] * controlBWeight + PLUME_END[1] * endWeight
        };
    }

    private static float[] cubicBezierTangent(float t) {
        float inverse = 1f - t;
        return new float[]{
                3f * inverse * inverse * (PLUME_CONTROL_A[0] - PLUME_START[0])
                        + 6f * inverse * t * (PLUME_CONTROL_B[0] - PLUME_CONTROL_A[0])
                        + 3f * t * t * (PLUME_END[0] - PLUME_CONTROL_B[0]),
                3f * inverse * inverse * (PLUME_CONTROL_A[1] - PLUME_START[1])
                        + 6f * inverse * t * (PLUME_CONTROL_B[1] - PLUME_CONTROL_A[1])
                        + 3f * t * t * (PLUME_END[1] - PLUME_CONTROL_B[1])
        };
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static int fixedStepPassCount(int pressureIterations) {
        if (pressureIterations <= 0) {
            throw new IllegalArgumentException("Pressure iteration count must be positive");
        }
        return pressureIterations + 7;
    }

    static int bloomDimension(int surfaceDimension, int divisor) {
        if (surfaceDimension <= 0 || divisor <= 0) {
            throw new IllegalArgumentException("Bloom dimensions must be positive");
        }
        return Math.max(1, (surfaceDimension + divisor - 1) / divisor);
    }

    private static float unitHash(int value, int salt) {
        int mixed = value ^ salt;
        mixed ^= mixed >>> 16;
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        mixed ^= mixed >>> 16;
        return ((mixed & 0x00ffffff) + 0.5f) / 16_777_216f;
    }
}
