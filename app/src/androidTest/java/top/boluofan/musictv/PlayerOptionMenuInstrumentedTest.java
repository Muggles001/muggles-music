package top.boluofan.musictv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;

@RunWith(AndroidJUnit4.class)
public class PlayerOptionMenuInstrumentedTest {
    private Context context;

    @Before
    public void configureBackend() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LxRetrofitClient.saveConfig(context, "http://127.0.0.1:9", "", "", "");
        BackendPreferences.setMode(context, BackendMode.LXSERVER);
    }

    @After
    public void clearBackend() {
        BackendPreferences.clearMode(context);
        context.getSharedPreferences("LxMusicPrefs", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void songInfoButtonOpensExistingMenuAndBackRestoresItsFocus() {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class))) {
            scenario.onActivity(activity -> activity.findViewById(R.id.btnScrape).performClick());
            SystemClock.sleep(150L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.layoutOptionMenu).getVisibility());
                RecyclerView options = activity.findViewById(R.id.rvOptions);
                RecyclerView.ViewHolder scrape = options.findViewHolderForAdapterPosition(1);
                assertNotNull(scrape);
                TextView label = scrape.itemView.findViewById(R.id.tvOptionName);
                assertEquals("重新刮削歌词", label.getText().toString());
                activity.getOnBackPressedDispatcher().onBackPressed();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                View songInfo = activity.findViewById(R.id.btnScrape);
                assertEquals(View.GONE,
                        activity.findViewById(R.id.layoutOptionMenu).getVisibility());
                assertSame(songInfo, activity.getCurrentFocus());
            });
        }
    }

    @Test
    public void menuBackButtonAndFirstOptionFormAVerticalFocusPath() {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class))) {
            scenario.onActivity(activity -> activity.findViewById(R.id.btnScrape).performClick());
            SystemClock.sleep(150L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                RecyclerView options = activity.findViewById(R.id.rvOptions);
                View firstOption = options.findViewHolderForAdapterPosition(0).itemView;
                View back = activity.findViewById(R.id.btnOptionMenuBack);
                assertSame(firstOption, activity.getCurrentFocus());

                activity.dispatchKeyEvent(new KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_UP
                ));
                assertSame(back, activity.getCurrentFocus());

                activity.dispatchKeyEvent(new KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_DOWN
                ));
                assertSame(firstOption, activity.getCurrentFocus());

                back.performClick();
                assertEquals(View.GONE,
                        activity.findViewById(R.id.layoutOptionMenu).getVisibility());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> assertSame(
                    activity.findViewById(R.id.btnScrape),
                    activity.getCurrentFocus()
            ));
        }
    }

    @Test
    public void remoteBackClosesMenuWithoutFinishingPlayer() {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class))) {
            scenario.onActivity(activity -> activity.findViewById(R.id.btnScrape).performClick());
            SystemClock.sleep(150L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                assertEquals(View.GONE,
                        activity.findViewById(R.id.layoutOptionMenu).getVisibility());
                assertFalse(activity.isFinishing());
                assertSame(activity.findViewById(R.id.btnScrape), activity.getCurrentFocus());
            });
        }
    }

    @Test
    public void backClosesDrawerThenControlsBeforeFinishingPlayer() {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class))) {
            scenario.onActivity(activity -> {
                activity.dispatchKeyEvent(new KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_DOWN
                ));
                activity.findViewById(R.id.btnQueue).performClick();
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.layoutDrawer).getVisibility());

                activity.getOnBackPressedDispatcher().onBackPressed();
                assertEquals(View.GONE,
                        activity.findViewById(R.id.layoutDrawer).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.layoutBottomControls).getVisibility());
                assertFalse(activity.isFinishing());

                activity.getOnBackPressedDispatcher().onBackPressed();
                assertFalse(activity.isFinishing());
            });
            SystemClock.sleep(350L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                assertEquals(View.INVISIBLE,
                        activity.findViewById(R.id.layoutBottomControls).getVisibility());
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertTrue(activity.isFinishing());
            });
        }
    }

    @Test
    public void menuKeyRemainsAWorkingShortcut() {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class))) {
            scenario.onActivity(activity -> activity.dispatchKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.layoutOptionMenu).getVisibility()));
        }
    }
}
