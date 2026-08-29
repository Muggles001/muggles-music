package top.boluofan.musictv.player.settings;

import android.os.Handler;
import android.os.SystemClock;

public final class SleepTimerController {
    public interface Listener {
        void pausePlayback();

        void publish(SleepTimerContract.State state);
    }

    interface Clock {
        long elapsedRealtime();
    }

    interface Scheduler {
        void postDelayed(Runnable task, long delayMs);

        void removeCallbacks(Runnable task);
    }

    private final SleepTimerState state = new SleepTimerState();
    private final Clock clock;
    private final Scheduler scheduler;
    private final Listener listener;
    private final Runnable elapsedExpiry = this::onElapsed;
    private boolean closed;

    public SleepTimerController(Handler handler, Listener listener) {
        this(SystemClock::elapsedRealtime, new HandlerScheduler(handler), listener);
    }

    SleepTimerController(Clock clock, Scheduler scheduler, Listener listener) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.listener = listener;
    }

    public boolean set(String mode, int value) {
        if (closed) return false;
        if (SleepTimerContract.MODE_OFF.equals(mode)) {
            scheduler.removeCallbacks(elapsedExpiry);
            state.disable();
        } else if (SleepTimerContract.MODE_MINUTES.equals(mode) && isAllowedMinutes(value)) {
            scheduler.removeCallbacks(elapsedExpiry);
            state.startMinutes(value, clock.elapsedRealtime());
            scheduler.postDelayed(elapsedExpiry, value * 60_000L);
        } else if (SleepTimerContract.MODE_SONGS.equals(mode) && isAllowedSongs(value)) {
            scheduler.removeCallbacks(elapsedExpiry);
            state.startSongs(value);
        } else {
            return false;
        }
        listener.publish(snapshot());
        return true;
    }

    public SleepTimerContract.State snapshot() {
        SleepTimerState.Mode mode = state.getMode();
        String modeValue = mode == SleepTimerState.Mode.MINUTES
                ? SleepTimerContract.MODE_MINUTES
                : mode == SleepTimerState.Mode.SONGS
                        ? SleepTimerContract.MODE_SONGS : SleepTimerContract.MODE_OFF;
        return new SleepTimerContract.State(
                modeValue, state.getConfiguredValue(), state.getRemaining(clock.elapsedRealtime()));
    }

    public void onElapsed() {
        if (closed || !state.isExpired(clock.elapsedRealtime())) return;
        listener.pausePlayback();
        listener.publish(snapshot());
    }

    public void onNaturalCompletion() {
        if (closed || !state.onNaturalCompletion()) return;
        listener.pausePlayback();
        listener.publish(snapshot());
    }

    public void close() {
        if (closed) return;
        closed = true;
        scheduler.removeCallbacks(elapsedExpiry);
    }

    private static boolean isAllowedMinutes(int value) {
        return value == 30 || value == 60 || value == 90;
    }

    private static boolean isAllowedSongs(int value) {
        return value == 1 || value == 3 || value == 5 || value == 10;
    }

    private static final class HandlerScheduler implements Scheduler {
        private final Handler handler;

        HandlerScheduler(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            handler.postDelayed(task, delayMs);
        }

        @Override
        public void removeCallbacks(Runnable task) {
            handler.removeCallbacks(task);
        }
    }
}
