package top.boluofan.musictv.ui.background;

final class FluidSimulationClock {
    private static final long UNSET_WALL_TIME = Long.MIN_VALUE;

    private final long stepNanos;
    private final int maxSteps;
    private long wallTimeNanos = UNSET_WALL_TIME;
    private long accumulatedNanos;
    private long simulationTimeNanos;

    FluidSimulationClock(long stepNanos, int maxSteps) {
        if (stepNanos <= 0 || maxSteps <= 0) {
            throw new IllegalArgumentException("Simulation step and limit must be positive");
        }
        this.stepNanos = stepNanos;
        this.maxSteps = maxSteps;
    }

    void resetWallTime(long nowNanos) {
        wallTimeNanos = nowNanos;
        accumulatedNanos = 0;
    }

    int advance(long nowNanos) {
        if (wallTimeNanos == UNSET_WALL_TIME) {
            resetWallTime(nowNanos);
            return 0;
        }

        long elapsedNanos = nowNanos - wallTimeNanos;
        wallTimeNanos = nowNanos;
        if (elapsedNanos < 0) {
            accumulatedNanos = 0;
            return 0;
        }
        if (elapsedNanos == 0) return 0;

        long maximumElapsedNanos = stepNanos * maxSteps;
        accumulatedNanos += Math.min(elapsedNanos, maximumElapsedNanos);
        int stepCount = (int) Math.min(accumulatedNanos / stepNanos, maxSteps);
        accumulatedNanos -= stepCount * stepNanos;
        simulationTimeNanos += stepCount * stepNanos;
        return stepCount;
    }

    float stepSeconds() {
        return stepNanos / 1_000_000_000f;
    }

    double simulationTimeSeconds() {
        return simulationTimeNanos / 1_000_000_000.0;
    }
}
