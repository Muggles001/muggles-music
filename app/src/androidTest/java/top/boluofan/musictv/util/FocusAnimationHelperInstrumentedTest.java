package top.boluofan.musictv.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FocusAnimationHelperInstrumentedTest {

    @Test
    public void onlyCardProfileChangesLayoutScale() {
        Resources resources = context().getResources();

        assertEquals(1.04f, FocusAnimationHelper.focusedScale(
                resources, FocusAnimationHelper.Profile.CARD), 0.001f);
        assertEquals(1f, FocusAnimationHelper.focusedScale(
                resources, FocusAnimationHelper.Profile.ROW), 0.001f);
        assertEquals(1f, FocusAnimationHelper.focusedScale(
                resources, FocusAnimationHelper.Profile.RAIL), 0.001f);
        assertEquals(1f, FocusAnimationHelper.focusedScale(
                resources, FocusAnimationHelper.Profile.ICON), 0.001f);
    }

    @Test
    public void focusProfilesStayWithinTvMotionBudget() {
        Resources resources = context().getResources();

        for (FocusAnimationHelper.Profile profile : FocusAnimationHelper.Profile.values()) {
            int duration = FocusAnimationHelper.focusDuration(resources, profile);
            assertTrue(profile + " duration=" + duration, duration >= 160 && duration <= 220);
        }
    }

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
