package top.boluofan.musictv.player.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PlayerControlsStateMachineTest {
    @Test
    public void defaultsHiddenAndHidesAfterInteractionTimeout() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeAnimationDriver animations = new FakeAnimationDriver();
        PlayerControlsStateMachine machine = createMachine(scheduler, animations);

        assertEquals(PlayerControlsStateMachine.State.HIDDEN, machine.getState());
        assertTrue(animations.snappedHidden);

        machine.onInteraction();
        assertEquals(PlayerControlsStateMachine.State.SHOWING, machine.getState());
        animations.finishLatest();
        assertEquals(PlayerControlsStateMachine.State.VISIBLE, machine.getState());

        scheduler.advanceBy(4_499L);
        assertEquals(PlayerControlsStateMachine.State.VISIBLE, machine.getState());
        scheduler.advanceBy(1L);
        assertEquals(PlayerControlsStateMachine.State.HIDING, machine.getState());
        animations.finishLatest();
        assertEquals(PlayerControlsStateMachine.State.HIDDEN, machine.getState());
    }

    @Test
    public void repeatedInteractionReplacesHideDeadline() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeAnimationDriver animations = new FakeAnimationDriver();
        PlayerControlsStateMachine machine = createMachine(scheduler, animations);

        machine.reveal();
        animations.finishLatest();
        scheduler.advanceBy(4_000L);
        machine.onInteraction();
        scheduler.advanceBy(4_000L);

        assertEquals(PlayerControlsStateMachine.State.VISIBLE, machine.getState());
        scheduler.advanceBy(500L);
        assertEquals(PlayerControlsStateMachine.State.HIDING, machine.getState());
    }

    @Test
    public void reversingAnimationIgnoresCancelledCompletion() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeAnimationDriver animations = new FakeAnimationDriver();
        PlayerControlsStateMachine machine = createMachine(scheduler, animations);

        machine.reveal();
        PendingAnimation showing = animations.latest();
        machine.hideNow();
        PendingAnimation hiding = animations.latest();
        machine.reveal();
        PendingAnimation resumedShowing = animations.latest();

        showing.finish();
        hiding.finish();
        assertEquals(PlayerControlsStateMachine.State.SHOWING, machine.getState());
        resumedShowing.finish();
        assertEquals(PlayerControlsStateMachine.State.VISIBLE, machine.getState());
        assertEquals(2, animations.cancelCount);
    }

    @Test
    public void revealKeysConsumeOnlyWhenControlsWereNotDisplayed() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeAnimationDriver animations = new FakeAnimationDriver();
        PlayerControlsStateMachine machine = createMachine(scheduler, animations);

        assertTrue(machine.onRevealKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN));
        assertFalse(machine.onRevealKey(android.view.KeyEvent.KEYCODE_ENTER));
        assertFalse(machine.onRevealKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT));
    }

    @Test
    public void disposeCancelsPendingWorkAndCompletion() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeAnimationDriver animations = new FakeAnimationDriver();
        PlayerControlsStateMachine machine = createMachine(scheduler, animations);

        machine.reveal();
        PendingAnimation pending = animations.latest();
        machine.dispose();
        pending.finish();
        scheduler.advanceBy(5_000L);

        assertEquals(PlayerControlsStateMachine.State.SHOWING, machine.getState());
        assertEquals(1, animations.cancelCount);
    }

    private static PlayerControlsStateMachine createMachine(
            FakeScheduler scheduler,
            FakeAnimationDriver animations
    ) {
        return new PlayerControlsStateMachine(scheduler, animations, null, 4_500L, 280L);
    }

    private static final class FakeScheduler implements PlayerControlsStateMachine.Scheduler {
        private final List<ScheduledTask> tasks = new ArrayList<>();
        private long nowMs;

        @Override
        public PlayerControlsStateMachine.Cancellable schedule(Runnable runnable, long delayMs) {
            ScheduledTask task = new ScheduledTask(runnable, nowMs + delayMs);
            tasks.add(task);
            return () -> task.cancelled = true;
        }

        private void advanceBy(long elapsedMs) {
            nowMs += elapsedMs;
            boolean ranTask;
            do {
                ranTask = false;
                for (ScheduledTask task : tasks) {
                    if (!task.cancelled && !task.finished && task.deadlineMs <= nowMs) {
                        task.finished = true;
                        task.runnable.run();
                        ranTask = true;
                    }
                }
            } while (ranTask);
        }
    }

    private static final class ScheduledTask {
        private final Runnable runnable;
        private final long deadlineMs;
        private boolean cancelled;
        private boolean finished;

        private ScheduledTask(Runnable runnable, long deadlineMs) {
            this.runnable = runnable;
            this.deadlineMs = deadlineMs;
        }
    }

    private static final class FakeAnimationDriver implements PlayerControlsStateMachine.AnimationDriver {
        private final List<PendingAnimation> animations = new ArrayList<>();
        private boolean snappedHidden;
        private int cancelCount;

        @Override
        public void snapHidden() {
            snappedHidden = true;
        }

        @Override
        public void animateTo(
                boolean visible,
                long durationMs,
                long generation,
                PlayerControlsStateMachine.Completion completion
        ) {
            animations.add(new PendingAnimation(generation, completion));
        }

        @Override
        public void cancel() {
            if (!animations.isEmpty() && !latest().finished) {
                cancelCount++;
            }
        }

        private PendingAnimation latest() {
            return animations.get(animations.size() - 1);
        }

        private void finishLatest() {
            latest().finish();
        }
    }

    private static final class PendingAnimation {
        private final long generation;
        private final PlayerControlsStateMachine.Completion completion;
        private boolean finished;

        private PendingAnimation(long generation, PlayerControlsStateMachine.Completion completion) {
            this.generation = generation;
            this.completion = completion;
        }

        private void finish() {
            if (!finished) {
                finished = true;
                completion.onComplete(generation);
            }
        }
    }
}
