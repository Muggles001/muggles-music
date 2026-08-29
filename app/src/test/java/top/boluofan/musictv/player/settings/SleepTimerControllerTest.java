package top.boluofan.musictv.player.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SleepTimerControllerTest {
    @Test
    public void replacementAndOffCancelScheduledExpiry() {
        Fixture fixture = new Fixture();

        assertTrue(fixture.controller.set(SleepTimerContract.MODE_MINUTES, 30));
        assertEquals(1_800_000L, fixture.scheduler.delayMs);

        assertTrue(fixture.controller.set(SleepTimerContract.MODE_MINUTES, 60));
        assertEquals(3_600_000L, fixture.scheduler.delayMs);
        assertEquals(2, fixture.scheduler.removeCount);

        assertTrue(fixture.controller.set(SleepTimerContract.MODE_OFF, 0));
        assertNull(fixture.scheduler.pending);
        assertEquals(SleepTimerContract.MODE_OFF, fixture.controller.snapshot().mode);
        assertEquals(3, fixture.listener.published.size());
    }

    @Test
    public void songTimerHasNoElapsedScheduleAndOnlyCountsNaturalCompletions() {
        Fixture fixture = new Fixture();

        assertTrue(fixture.controller.set(SleepTimerContract.MODE_SONGS, 3));
        assertNull(fixture.scheduler.pending);
        fixture.controller.onElapsed();
        assertEquals(0, fixture.listener.pauseCount);

        fixture.controller.onNaturalCompletion();
        assertEquals(2, fixture.controller.snapshot().remaining);
        assertEquals(0, fixture.listener.pauseCount);
        assertEquals(1, fixture.listener.published.size());
    }

    @Test
    public void elapsedTimerExpiresOnlyOnce() {
        Fixture fixture = new Fixture();
        fixture.clock.nowMs = 1_000L;
        fixture.controller.set(SleepTimerContract.MODE_MINUTES, 30);
        fixture.clock.nowMs = 1_801_000L;

        fixture.scheduler.runPending();
        fixture.controller.onElapsed();

        assertEquals(1, fixture.listener.pauseCount);
        assertEquals(2, fixture.listener.published.size());
        assertEquals(SleepTimerContract.MODE_OFF,
                fixture.listener.published.get(1).mode);
    }

    @Test
    public void closeCancelsPendingWorkAndRejectsFurtherChanges() {
        Fixture fixture = new Fixture();
        fixture.controller.set(SleepTimerContract.MODE_MINUTES, 30);

        fixture.controller.close();
        fixture.clock.nowMs = 2_000_000L;
        fixture.controller.onElapsed();

        assertNull(fixture.scheduler.pending);
        assertEquals(0, fixture.listener.pauseCount);
        assertEquals(1, fixture.listener.published.size());
        assertFalse(fixture.controller.set(SleepTimerContract.MODE_MINUTES, 60));
    }

    @Test
    public void unsupportedValuesDoNotReplaceCurrentTimer() {
        Fixture fixture = new Fixture();
        fixture.controller.set(SleepTimerContract.MODE_SONGS, 5);

        assertFalse(fixture.controller.set(SleepTimerContract.MODE_MINUTES, 15));
        assertFalse(fixture.controller.set(SleepTimerContract.MODE_SONGS, 2));

        SleepTimerContract.State state = fixture.controller.snapshot();
        assertEquals(SleepTimerContract.MODE_SONGS, state.mode);
        assertEquals(5, state.remaining);
        assertEquals(1, fixture.listener.published.size());
    }

    private static final class Fixture {
        final FakeClock clock = new FakeClock();
        final FakeScheduler scheduler = new FakeScheduler();
        final RecordingListener listener = new RecordingListener();
        final SleepTimerController controller =
                new SleepTimerController(clock, scheduler, listener);
    }

    private static final class FakeClock implements SleepTimerController.Clock {
        long nowMs;

        @Override
        public long elapsedRealtime() {
            return nowMs;
        }
    }

    private static final class FakeScheduler implements SleepTimerController.Scheduler {
        Runnable pending;
        long delayMs;
        int removeCount;

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            pending = task;
            this.delayMs = delayMs;
        }

        @Override
        public void removeCallbacks(Runnable task) {
            removeCount++;
            if (pending == task) pending = null;
        }

        void runPending() {
            Runnable task = pending;
            pending = null;
            if (task != null) task.run();
        }
    }

    private static final class RecordingListener implements SleepTimerController.Listener {
        int pauseCount;
        final List<SleepTimerContract.State> published = new ArrayList<>();

        @Override
        public void pausePlayback() {
            pauseCount++;
        }

        @Override
        public void publish(SleepTimerContract.State state) {
            published.add(state);
        }
    }
}
