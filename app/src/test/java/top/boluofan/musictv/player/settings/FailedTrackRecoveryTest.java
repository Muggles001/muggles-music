package top.boluofan.musictv.player.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FailedTrackRecoveryTest {
    @Test
    public void advancesAcrossUntriedItemsAndStopsAfterTraversalFails() {
        FailedTrackRecovery recovery = new FailedTrackRecovery();

        assertEquals(1, recovery.recordFailureAndFindNext(0, 3));
        assertEquals(2, recovery.recordFailureAndFindNext(1, 3));
        assertEquals(FailedTrackRecovery.INDEX_UNSET,
                recovery.recordFailureAndFindNext(2, 3));
    }

    @Test
    public void successfulPlaybackStartsAFreshTraversal() {
        FailedTrackRecovery recovery = new FailedTrackRecovery();
        recovery.recordFailureAndFindNext(0, 2);

        recovery.reset();

        assertEquals(0, recovery.failedCount());
        assertEquals(0, recovery.recordFailureAndFindNext(1, 2));
    }
}
