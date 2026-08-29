package top.boluofan.musictv.player.settings;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SleepTimerContractInstrumentedTest {
    @Test
    public void stateBundleRoundTrips() {
        SleepTimerContract.State original = new SleepTimerContract.State(
                SleepTimerContract.MODE_SONGS, 10, 7);

        SleepTimerContract.State decoded = SleepTimerContract.decode(
                SleepTimerContract.encode(original));

        assertEquals(original.mode, decoded.mode);
        assertEquals(original.value, decoded.value);
        assertEquals(original.remaining, decoded.remaining);
    }
}
