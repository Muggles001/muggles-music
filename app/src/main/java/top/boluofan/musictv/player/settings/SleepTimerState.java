package top.boluofan.musictv.player.settings;

public final class SleepTimerState {
    public enum Mode {
        OFF,
        MINUTES,
        SONGS
    }

    private Mode mode = Mode.OFF;
    private long deadlineElapsedMs;
    private int remainingSongs;
    private int configuredValue;

    public void disable() {
        mode = Mode.OFF;
        deadlineElapsedMs = 0L;
        remainingSongs = 0;
        configuredValue = 0;
    }

    public void startMinutes(int minutes, long nowElapsedMs) {
        if (minutes <= 0) {
            disable();
            return;
        }
        mode = Mode.MINUTES;
        configuredValue = minutes;
        deadlineElapsedMs = nowElapsedMs + minutes * 60_000L;
        remainingSongs = 0;
    }

    public void startSongs(int songs) {
        if (songs <= 0) {
            disable();
            return;
        }
        mode = Mode.SONGS;
        configuredValue = songs;
        remainingSongs = songs;
        deadlineElapsedMs = 0L;
    }

    public boolean onNaturalCompletion() {
        if (mode != Mode.SONGS) return false;
        remainingSongs--;
        if (remainingSongs > 0) return false;
        disable();
        return true;
    }

    public boolean isExpired(long nowElapsedMs) {
        if (mode != Mode.MINUTES || nowElapsedMs < deadlineElapsedMs) return false;
        disable();
        return true;
    }

    public Mode getMode() {
        return mode;
    }

    public int getRemaining(long nowElapsedMs) {
        if (mode == Mode.SONGS) return remainingSongs;
        if (mode == Mode.MINUTES) {
            long remainingMs = Math.max(0L, deadlineElapsedMs - nowElapsedMs);
            return (int) ((remainingMs + 59_999L) / 60_000L);
        }
        return 0;
    }

    public int getConfiguredValue() {
        return configuredValue;
    }
}
