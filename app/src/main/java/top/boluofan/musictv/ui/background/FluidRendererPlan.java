package top.boluofan.musictv.ui.background;

final class FluidRendererPlan {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long UNSET_FRAME_TIME = Long.MIN_VALUE;
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

    static int flowStepPassCount(int pressureIterations) {
        if (pressureIterations <= 0) {
            throw new IllegalArgumentException("Pressure iteration count must be positive");
        }
        return pressureIterations + 3;
    }

    static int fastStepPassCount() {
        return 4;
    }

    static int surfacePassCount() {
        return 1;
    }

    static int twoFastStepPassCount(int pressureIterations) {
        return flowStepPassCount(pressureIterations)
                + 2 * (fastStepPassCount() + surfacePassCount());
    }

    static SimulationFormatPlan simulationFormatPlan(
            boolean rg16fRenderable, boolean r16fRenderable) {
        return new SimulationFormatPlan(rg16fRenderable, r16fRenderable);
    }

    static RenderSize cappedRenderSize(
            int surfaceWidth,
            int surfaceHeight,
            int maxWidth,
            int maxHeight) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("Render dimensions must be positive");
        }
        double scale = Math.min(1.0, Math.min(
                maxWidth / (double) surfaceWidth,
                maxHeight / (double) surfaceHeight));
        return new RenderSize(
                Math.max(1, (int) Math.round(surfaceWidth * scale)),
                Math.max(1, (int) Math.round(surfaceHeight * scale)));
    }

    static final class FrameCadence {
        private final long intervalNanos;
        private final long timestampToleranceNanos;
        private long previousFrameNanos = UNSET_FRAME_TIME;
        private long accumulatedNanos;

        FrameCadence(int framesPerSecond) {
            if (framesPerSecond <= 0 || framesPerSecond > NANOS_PER_SECOND) {
                throw new IllegalArgumentException("Frame rate must be positive");
            }
            intervalNanos = NANOS_PER_SECOND / framesPerSecond;
            // Vsync timestamps truncate fractional nanoseconds on common 60 Hz displays.
            timestampToleranceNanos = Math.min(1_000_000L, intervalNanos / 20L);
        }

        boolean shouldRender(long frameTimeNanos) {
            if (previousFrameNanos == UNSET_FRAME_TIME) {
                previousFrameNanos = frameTimeNanos;
                return true;
            }
            long elapsedNanos = frameTimeNanos - previousFrameNanos;
            previousFrameNanos = frameTimeNanos;
            if (elapsedNanos < 0L) {
                accumulatedNanos = 0L;
                return false;
            }
            if (elapsedNanos == 0L) return false;

            if (elapsedNanos >= intervalNanos * 2L) {
                accumulatedNanos = intervalNanos;
            } else {
                accumulatedNanos += elapsedNanos;
            }
            if (accumulatedNanos + timestampToleranceNanos < intervalNanos) return false;
            accumulatedNanos = Math.max(0L, accumulatedNanos - intervalNanos);
            return true;
        }
    }

    static final class FlowCadence {
        private final int divisor;
        private int stepsUntilFlow;

        FlowCadence(int divisor) {
            if (divisor <= 0) throw new IllegalArgumentException("Flow divisor must be positive");
            this.divisor = divisor;
        }

        boolean shouldRunFlowStep() {
            if (stepsUntilFlow > 0) {
                stepsUntilFlow--;
                return false;
            }
            stepsUntilFlow = divisor - 1;
            return true;
        }

        void reset() {
            stepsUntilFlow = 0;
        }
    }

    static final class RenderSize {
        final int width;
        final int height;

        RenderSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    static final class SimulationFormatPlan {
        final boolean narrowVelocity;
        final boolean narrowScalar;

        SimulationFormatPlan(boolean narrowVelocity, boolean narrowScalar) {
            this.narrowVelocity = narrowVelocity;
            this.narrowScalar = narrowScalar;
        }
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
