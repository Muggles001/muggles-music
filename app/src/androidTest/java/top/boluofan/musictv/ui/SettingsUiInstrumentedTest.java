package top.boluofan.musictv.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.ConfigActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.settings.SettingsItem;
import top.boluofan.musictv.source.SourceScriptStore;
import top.boluofan.musictv.ui.adapter.SettingsAdapter;
import top.boluofan.musictv.ui.settings.SettingsRowView;

@RunWith(AndroidJUnit4.class)
public class SettingsUiInstrumentedTest {
    @Test
    public void rootUsesTransparentRecyclerSettingsSurface() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(R.layout.activity_settings, null, false);
            assertTrue(root.getBackground() instanceof ColorDrawable);
            assertEquals(Color.TRANSPARENT, ((ColorDrawable) root.getBackground()).getColor());
            View list = root.findViewById(R.id.layoutServerConfig);
            assertTrue(list instanceof RecyclerView);
        });
    }

    @Test
    public void toggleRowExposesCheckedSemantics() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SettingsRowView row = (SettingsRowView) LayoutInflater.from(context)
                    .inflate(R.layout.item_settings_row, null, false);
            row.setCheckable(true);
            row.setChecked(true);
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
            row.onInitializeAccessibilityNodeInfo(info);
            assertTrue(info.isCheckable());
            assertTrue(info.isChecked());
            assertEquals(android.widget.Switch.class.getName(), info.getClassName());
            info.recycle();
        });
    }

    @Test
    public void infoRowIsFocusableReadOnlyAndWrapsWithoutEllipsis() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SettingsRowView row = (SettingsRowView) LayoutInflater.from(context)
                    .inflate(R.layout.item_settings_info, null, false);
            TextView body = row.findViewById(R.id.tvSettingsInfoBody);
            body.setText("这是用于验证只读说明能够自然换行并完整显示的较长文本，"
                    + "它不应该被压缩成单行，也不应该显示省略号。");
            int width = dp(context, 360);
            row.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            row.layout(0, 0, width, row.getMeasuredHeight());

            assertTrue(row.isFocusable());
            assertFalse(row.isClickable());
            assertTrue(body.getLineCount() > 1);
            assertEquals(null, body.getEllipsize());
            assertTrue(row.getMeasuredHeight()
                    > context.getResources().getDimensionPixelSize(R.dimen.lx_settings_row_height));

            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
            row.onInitializeAccessibilityNodeInfo(info);
            assertFalse(info.isClickable());
            assertFalse(info.getActionList().contains(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK));
            info.recycle();
        });
    }

    @Test
    public void helpAndAboutRowsNavigateScrollAndKeepLastItemVisible() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertReadOnlyDetailNavigation(context, SettingsDetailActivity.TYPE_HELP,
                new String[]{"help_navigation", "help_playback", "help_queue",
                        "help_direct_source", "help_lxserver"});
        assertReadOnlyDetailNavigation(context, SettingsDetailActivity.TYPE_ABOUT,
                new String[]{"about_version", "about_package", "about_project",
                        "about_project_url", "about_upstream", "about_license"});
    }

    @Test
    public void detailStartsOnInteractiveRowAndDoesNotWrapAtEnd() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = SettingsDetailActivity.intent(context, SettingsDetailActivity.TYPE_QUALITY);
        try (ActivityScenario<SettingsDetailActivity> scenario = ActivityScenario.launch(intent)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.rvSettingsDetail);
                View focused = activity.getCurrentFocus();
                assertTrue(focused != null);
                assertEquals(1, list.getChildAdapterPosition(focused));
                focused.dispatchKeyEvent(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN));
                assertSame(focused, activity.getCurrentFocus());
            });
        }
    }

    @Test
    public void detailBackAndRowsShareOneShell() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = SettingsDetailActivity.intent(context, SettingsDetailActivity.TYPE_QUALITY);
        try (ActivityScenario<SettingsDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                View back = activity.findViewById(R.id.btnBack);
                RecyclerView list = activity.findViewById(R.id.rvSettingsDetail);
                assertTrue(back.isFocusable());
                assertSame(list.getParent(), back.getParent().getParent());
                assertTrue(list.getAdapter() instanceof SettingsAdapter);
                SettingsAdapter adapter = (SettingsAdapter) list.getAdapter();
                assertEquals(SettingsItem.Type.SECTION, adapter.getItems().get(0).type);
                assertEquals(SettingsItem.Type.SEGMENTED, adapter.getItems().get(1).type);
            });
        }
    }

    @Test
    public void detailFirstRowAndBackButtonHaveBidirectionalFocus() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = SettingsDetailActivity.intent(context, SettingsDetailActivity.TYPE_QUALITY);
        try (ActivityScenario<SettingsDetailActivity> scenario = ActivityScenario.launch(intent)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> dispatchDown(activity.getCurrentFocus(), KeyEvent.KEYCODE_DPAD_UP));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertSame(
                    activity.findViewById(R.id.btnBack), activity.getCurrentFocus()));

            scenario.onActivity(activity -> dispatchDown(activity.getCurrentFocus(), KeyEvent.KEYCODE_DPAD_DOWN));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertEquals(1, focusedAdapterPosition(activity)));

            scenario.onActivity(activity -> dispatchDown(activity.getCurrentFocus(), KeyEvent.KEYCODE_DPAD_UP));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> dispatchDown(activity.getCurrentFocus(), KeyEvent.KEYCODE_DPAD_RIGHT));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertEquals(1, focusedAdapterPosition(activity)));
        }
    }

    @Test
    public void detailNavigationSkipsSectionsAndDisabledRowsWithoutWrapping() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BackendPreferences.clearMode(context);
        clearLxserverConfig(context);
        new SourceScriptStore(context).clear();
        Intent intent = SettingsDetailActivity.intent(context, SettingsDetailActivity.TYPE_CONNECTION);
        try (ActivityScenario<SettingsDetailActivity> scenario = ActivityScenario.launch(intent)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.rvSettingsDetail);
                SettingsAdapter adapter = (SettingsAdapter) list.getAdapter();
                assertEquals("configure_connection",
                        adapter.getItems().get(focusedAdapterPosition(activity)).key);
                View lastInteractive = activity.getCurrentFocus();
                dispatchDown(lastInteractive, KeyEvent.KEYCODE_DPAD_DOWN);
                assertSame(lastInteractive, activity.getCurrentFocus());
            });
        }
    }

    @Test
    public void connectionDetailUsesOneModeAwareStructure() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BackendPreferences.clearMode(context);
        clearLxserverConfig(context);
        new SourceScriptStore(context).clear();
        LxRetrofitClient.saveConfig(context, "http://127.0.0.1:19000", "tester", "secret", "token");
        BackendPreferences.setMode(context, BackendMode.LXSERVER);

        Intent intent = SettingsDetailActivity.intent(
                context, SettingsDetailActivity.TYPE_CONNECTION);
        try (ActivityScenario<SettingsDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals(context.getString(R.string.settings_connection_title),
                        ((TextView) activity.findViewById(R.id.tvTitle)).getText().toString());
                RecyclerView list = activity.findViewById(R.id.rvSettingsDetail);
                SettingsAdapter adapter = (SettingsAdapter) list.getAdapter();
                assertRowOrder(adapter, "connection_mode", "server_url", "server_user",
                        "server_auth", "configure_connection", "switch_connection",
                        "server_sign_out");
                assertFalse(hasKey(adapter, "source_name"));
            });
        } finally {
            BackendPreferences.clearMode(context);
            clearLxserverConfig(context);
        }
    }

    @Test
    public void connectionSetupHonorsRequestedInitialMode() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, ConfigActivity.class)
                .putExtra(ConfigActivity.EXTRA_INITIAL_MODE, BackendMode.LXSERVER.name());
        try (ActivityScenario<ConfigActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.btnModeServer).isSelected());
                assertFalse(activity.findViewById(R.id.btnModeDirect).isSelected());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.layoutManual).getVisibility());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.layoutDirect).getVisibility());
            });
        }
    }

    @Test
    public void connectionSetupActionPreservesSavedServerFields() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clearLxserverConfig(context);
        LxRetrofitClient.saveConfig(
                context, "http://127.0.0.1:19000", "tester", "secret", "token");
        BackendPreferences.setMode(context, BackendMode.LXSERVER);

        try {
            Intent intent = ConnectionSettingsActions.createSetupIntent(context, true);

            assertEquals(BackendMode.DIRECT_SOURCE.name(),
                    intent.getStringExtra(ConfigActivity.EXTRA_INITIAL_MODE));
            assertEquals("http://127.0.0.1:19000/", intent.getStringExtra("server_url"));
            assertEquals("tester", intent.getStringExtra("username"));
        } finally {
            BackendPreferences.clearMode(context);
            clearLxserverConfig(context);
        }
    }

    @Test
    public void serverSignOutActionPreservesServerUrl() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clearLxserverConfig(context);
        LxRetrofitClient.saveConfig(
                context, "http://127.0.0.1:19000", "tester", "secret", "token");

        try {
            ConnectionSettingsActions.signOutServer(context);

            assertEquals("http://127.0.0.1:19000/", LxRetrofitClient.getServerUrl(context));
            assertEquals("", LxRetrofitClient.getUsername(context));
            assertEquals("", LxRetrofitClient.getPassword(context));
            assertEquals("", LxRetrofitClient.getToken(context));
        } finally {
            clearLxserverConfig(context);
        }
    }

    private static void clearLxserverConfig(Context context) {
        context.getSharedPreferences("LxMusicPrefs", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private static void assertRowOrder(SettingsAdapter adapter, String... keys) {
        int previous = -1;
        for (String key : keys) {
            int current = indexOfKey(adapter, key);
            assertTrue(key + " is missing", current >= 0);
            assertTrue(key + " is out of order", current > previous);
            previous = current;
        }
    }

    private static boolean hasKey(SettingsAdapter adapter, String key) {
        return indexOfKey(adapter, key) >= 0;
    }

    private static int indexOfKey(SettingsAdapter adapter, String key) {
        for (int index = 0; index < adapter.getItems().size(); index++) {
            if (key.equals(adapter.getItems().get(index).key)) return index;
        }
        return -1;
    }

    private static void assertReadOnlyDetailNavigation(
            Context context, String detailType, String[] expectedKeys) {
        Intent intent = SettingsDetailActivity.intent(context, detailType);
        try (ActivityScenario<SettingsDetailActivity> scenario = ActivityScenario.launch(intent)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.rvSettingsDetail);
                list.getLayoutParams().height = dp(context, 280);
                list.requestLayout();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertFocusedKey(scenario, expectedKeys[0]);
            for (int index = 1; index < expectedKeys.length; index++) {
                scenario.onActivity(activity -> dispatchDown(
                        activity.getCurrentFocus(), KeyEvent.KEYCODE_DPAD_DOWN));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertFocusedKey(scenario, expectedKeys[index]);
            }
            waitForRecyclerIdle(scenario);

            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.rvSettingsDetail);
                RecyclerView.ViewHolder holder = list.findContainingViewHolder(
                        activity.getCurrentFocus());
                assertTrue(holder != null);
                Rect listBounds = new Rect();
                Rect rowBounds = new Rect();
                assertTrue(list.getGlobalVisibleRect(listBounds));
                assertTrue(holder.itemView.getGlobalVisibleRect(rowBounds));
                assertTrue(rowBounds.top >= listBounds.top);
                assertTrue(rowBounds.bottom <= listBounds.bottom);
                assertEquals(holder.itemView.getHeight(), rowBounds.height());
                assertTrue(list.computeVerticalScrollOffset() > 0);

                View last = activity.getCurrentFocus();
                dispatchDown(last, KeyEvent.KEYCODE_DPAD_DOWN);
                assertSame(last, activity.getCurrentFocus());
            });
        }
    }

    private static void assertFocusedKey(
            ActivityScenario<SettingsDetailActivity> scenario, String expectedKey) {
        scenario.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.rvSettingsDetail);
            SettingsAdapter adapter = (SettingsAdapter) list.getAdapter();
            assertEquals(expectedKey,
                    adapter.getItems().get(focusedAdapterPosition(activity)).key);
        });
    }

    private static void waitForRecyclerIdle(
            ActivityScenario<SettingsDetailActivity> scenario) {
        for (int attempt = 0; attempt < 20; attempt++) {
            AtomicBoolean idle = new AtomicBoolean();
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.rvSettingsDetail);
                idle.set(list.getScrollState() == RecyclerView.SCROLL_STATE_IDLE);
            });
            if (idle.get()) return;
            SystemClock.sleep(50L);
        }
    }

    private static int focusedAdapterPosition(SettingsDetailActivity activity) {
        RecyclerView list = activity.findViewById(R.id.rvSettingsDetail);
        RecyclerView.ViewHolder holder = list.findContainingViewHolder(activity.getCurrentFocus());
        return holder == null ? RecyclerView.NO_POSITION : holder.getBindingAdapterPosition();
    }

    private static void dispatchDown(View view, int keyCode) {
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

}
