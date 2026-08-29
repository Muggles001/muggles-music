package top.boluofan.musictv.player.visual;

final class VinylMotion {
    static final float PLAYBACK_DEGREES_PER_MILLISECOND = 360f / 20_000f;
    private static final float STOP_DURATION_MILLIS = 720f;
    private static final float TONEARM_TRAVEL_MILLIS = 460f;
    private static final float STOP_ACCELERATION =
            PLAYBACK_DEGREES_PER_MILLISECOND / STOP_DURATION_MILLIS;

    private boolean playing;
    private double angleDegrees;
    private float angularVelocity;
    private float tonearmProgress;

    void setPlaying(boolean playing) {
        this.playing = playing;
        if (playing) angularVelocity = PLAYBACK_DEGREES_PER_MILLISECOND;
    }

    void advance(long elapsedMillis) {
        if (elapsedMillis <= 0L) return;
        if (playing) {
            angularVelocity = PLAYBACK_DEGREES_PER_MILLISECOND;
            angleDegrees += 360.0 * elapsedMillis / 20_000.0;
        } else if (angularVelocity > 0f) {
            float stopTime = angularVelocity / STOP_ACCELERATION;
            float movingTime = Math.min(elapsedMillis, stopTime);
            float nextVelocity = Math.max(0f, angularVelocity - STOP_ACCELERATION * movingTime);
            angleDegrees += (angularVelocity + nextVelocity) * 0.5f * movingTime;
            angularVelocity = nextVelocity;
        }

        float targetTonearmProgress = playing ? 1f : 0f;
        float tonearmStep = elapsedMillis / TONEARM_TRAVEL_MILLIS;
        if (tonearmProgress < targetTonearmProgress) {
            tonearmProgress = Math.min(targetTonearmProgress, tonearmProgress + tonearmStep);
        } else if (tonearmProgress > targetTonearmProgress) {
            tonearmProgress = Math.max(targetTonearmProgress, tonearmProgress - tonearmStep);
        }

        angleDegrees %= 360f;
        if (angleDegrees < 0f) angleDegrees += 360f;
    }

    boolean isAnimating() {
        float tonearmTarget = playing ? 1f : 0f;
        return playing || angularVelocity > 0f || tonearmProgress != tonearmTarget;
    }

    float getAngleDegrees() {
        return (float) angleDegrees;
    }

    float getAngularVelocity() {
        return angularVelocity;
    }

    float getTonearmProgress() {
        return tonearmProgress;
    }
}
