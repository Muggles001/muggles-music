package top.boluofan.musictv.player.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SleepTimerStateTest {
    @Test
    public void minuteTimerRoundsDisplayedRemainderUpAndExpiresOnce() {
        SleepTimerState state = new SleepTimerState();
        state.startMinutes(30, 1_000L);

        assertEquals(30, state.getRemaining(1_001L));
        assertEquals(30, state.getConfiguredValue());
        assertFalse(state.isExpired(1_800_999L));
        assertTrue(state.isExpired(1_801_000L));
        assertFalse(state.isExpired(1_801_001L));
        assertEquals(SleepTimerState.Mode.OFF, state.getMode());
    }

    @Test
    public void songTimerOnlyChangesWhenNaturalCompletionIsReported() {
        SleepTimerState state = new SleepTimerState();
        state.startSongs(3);

        assertFalse(state.onNaturalCompletion());
        assertEquals(2, state.getRemaining(0L));
        assertEquals(3, state.getConfiguredValue());
        assertFalse(state.onNaturalCompletion());
        assertTrue(state.onNaturalCompletion());
        assertEquals(SleepTimerState.Mode.OFF, state.getMode());
    }
}
