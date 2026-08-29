package top.boluofan.musictv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.source.SourceRuntimeManager;
import top.boluofan.musictv.source.SourceScriptStore;

@RunWith(AndroidJUnit4.class)
public class ConfigFocusInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clearConfiguration();
    }

    @After
    public void tearDown() {
        clearConfiguration();
    }

    @Test
    public void directModeMovesThroughVisibleFieldsAndStopsAtImport() {
        try (ActivityScenario<ConfigActivity> scenario = launchConfig()) {
            assertFocused(scenario, R.id.btnModeDirect);

            pressDown();
            assertFocused(scenario, R.id.etSourceUrl);

            pressDown();
            assertFocused(scenario, R.id.btnImportSource);

            pressDown();
            assertFocused(scenario, R.id.btnImportSource);
        }
    }

    @Test
    public void directModeIncludesVisibleRollbackAndStopsThere() {
        context.getSharedPreferences("MugglesSourcePrefs", Context.MODE_PRIVATE)
                .edit().putString("previous_source", "focus-fixture").commit();

        try (ActivityScenario<ConfigActivity> scenario = launchConfig()) {
            scenario.onActivity(activity -> assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.btnRollbackSource).getVisibility()));
            assertFocused(scenario, R.id.btnModeDirect);

            pressDown();
            assertFocused(scenario, R.id.etSourceUrl);
            pressDown();
            assertFocused(scenario, R.id.btnImportSource);
            pressDown();
            assertFocused(scenario, R.id.btnRollbackSource);
            pressDown();
            assertFocused(scenario, R.id.btnRollbackSource);
        }
    }

    @Test
    public void serverModeMovesThroughEveryVisibleFieldAndStopsAtConnect() {
        try (ActivityScenario<ConfigActivity> scenario = launchConfig()) {
            scenario.onActivity(activity -> {
                View serverMode = activity.findViewById(R.id.btnModeServer);
                serverMode.performClick();
                serverMode.requestFocusFromTouch();
            });
            waitForIdle();
            assertFocused(scenario, R.id.btnModeServer);

            int[] expected = {
                    R.id.etUrl,
                    R.id.etUsername,
                    R.id.etPassword,
                    R.id.etToken,
                    R.id.btnConnect
            };
            for (int viewId : expected) {
                pressDown();
                assertFocused(scenario, viewId);
            }

            pressDown();
            assertFocused(scenario, R.id.btnConnect);
        }
    }

    private ActivityScenario<ConfigActivity> launchConfig() {
        ActivityScenario<ConfigActivity> scenario = ActivityScenario.launch(
                new Intent(context, ConfigActivity.class));
        waitForIdle();
        return scenario;
    }

    private void pressDown() {
        InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        waitForIdle();
    }

    private void assertFocused(ActivityScenario<ConfigActivity> scenario, int expectedId) {
        scenario.onActivity(activity -> assertSame(
                activity.findViewById(expectedId), activity.getCurrentFocus()));
    }

    private void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void clearConfiguration() {
        context.getSharedPreferences("LxMusicPrefs", Context.MODE_PRIVATE).edit().clear().commit();
        BackendPreferences.clearMode(context);
        new SourceScriptStore(context).clear();
        SourceRuntimeManager.get(context).invalidate();
    }
}
