package top.boluofan.musictv.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import top.boluofan.musictv.test.InMemorySharedPreferences;

public class LyricTimingPreferencesTest {
    @Test
    public void adjustmentsUseExistingKeysAndZeroRemovesTheEntry() {
        InMemorySharedPreferences store = new InMemorySharedPreferences();
        LyricTimingPreferences preferences = new LyricTimingPreferences(store);

        preferences.setAdjustmentMs("kw", "123", 500L);

        assertEquals(500L, preferences.getAdjustmentMs("kw", "123"));
        assertEquals(1, preferences.count());
        assertTrue(store.contains("lyric_timing_kw_123"));

        preferences.setAdjustmentMs("kw", "123", 0L);

        assertEquals(0L, preferences.getAdjustmentMs("kw", "123"));
        assertEquals(0, preferences.count());
    }

    @Test
    public void countAndClearIgnoreUnrelatedEntries() {
        InMemorySharedPreferences store = new InMemorySharedPreferences()
                .putInitial("unrelated", 3L)
                .putInitial("lyric_timing_tx_1", 250L)
                .putInitial("lyric_timing_tx_2", -250L);
        LyricTimingPreferences preferences = new LyricTimingPreferences(store);

        assertEquals(2, preferences.count());
        preferences.clearAll();

        assertEquals(0, preferences.count());
        assertTrue(store.contains("unrelated"));
    }

    @Test
    public void negativeAdjustmentsRemainIsolatedBySourceAndSong() {
        InMemorySharedPreferences store = new InMemorySharedPreferences();
        LyricTimingPreferences preferences = new LyricTimingPreferences(store);

        preferences.setAdjustmentMs("tx", "same-id", -750L);
        preferences.setAdjustmentMs("kw", "same-id", 250L);

        assertEquals(-750L, preferences.getAdjustmentMs("tx", "same-id"));
        assertEquals(250L, preferences.getAdjustmentMs("kw", "same-id"));
        assertEquals(0L, preferences.getAdjustmentMs("tx", "other-id"));
    }
}
