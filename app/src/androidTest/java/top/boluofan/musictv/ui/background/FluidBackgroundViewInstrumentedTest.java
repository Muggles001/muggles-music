package top.boluofan.musictv.ui.background;

import static org.junit.Assert.assertEquals;

import android.graphics.Rect;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import top.boluofan.musictv.R;
import top.boluofan.musictv.settings.AppSettingsPreferences;
import top.boluofan.musictv.ui.settings.connection.ConfigActivity;

@RunWith(AndroidJUnit4.class)
public class FluidBackgroundViewInstrumentedTest {
    @Test
    public void motionProfilesCapTheActualSurfaceBuffer() {
        AppSettingsPreferences settings = new AppSettingsPreferences(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        AppSettingsPreferences.BrowseMotion original = settings.getBrowseMotion();
        try {
            assertSurfaceSize(settings, AppSettingsPreferences.BrowseMotion.STANDARD);
            assertSurfaceSize(settings, AppSettingsPreferences.BrowseMotion.REDUCED);
        } finally {
            settings.setBrowseMotion(original);
        }
    }

    private static void assertSurfaceSize(
            AppSettingsPreferences settings, AppSettingsPreferences.BrowseMotion motion) {
        settings.setBrowseMotion(motion);
        try (ActivityScenario<ConfigActivity> scenario = ActivityScenario.launch(ConfigActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<FluidBackgroundView> viewReference = new AtomicReference<>();
            AtomicInteger viewWidth = new AtomicInteger();
            AtomicInteger viewHeight = new AtomicInteger();
            scenario.onActivity(activity -> {
                FluidBackgroundView view = activity.findViewById(R.id.fluidBackground);
                viewReference.set(view);
                viewWidth.set(view.getWidth());
                viewHeight.set(view.getHeight());
            });

            FluidBackgroundPolicy.Profile profile = FluidBackgroundPolicy.profileFor(motion);
            FluidRendererPlan.RenderSize expected = FluidRendererPlan.cappedRenderSize(
                    viewWidth.get(),
                    viewHeight.get(),
                    profile.surfaceTargetMaxWidth,
                    profile.surfaceTargetMaxHeight);
            Rect frame = awaitSurfaceFrame(viewReference.get(), expected);
            assertEquals(expected.width, frame.width());
            assertEquals(expected.height, frame.height());
        }
    }

    private static Rect awaitSurfaceFrame(
            FluidBackgroundView view, FluidRendererPlan.RenderSize expected) {
        long deadline = SystemClock.elapsedRealtime() + 5_000L;
        Rect frame;
        do {
            frame = new Rect(view.getHolder().getSurfaceFrame());
            if (frame.width() == expected.width && frame.height() == expected.height) return frame;
            SystemClock.sleep(25L);
        } while (SystemClock.elapsedRealtime() < deadline);
        return frame;
    }
}
