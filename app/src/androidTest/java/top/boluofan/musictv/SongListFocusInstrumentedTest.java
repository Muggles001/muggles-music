package top.boluofan.musictv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;

@RunWith(AndroidJUnit4.class)
public class SongListFocusInstrumentedTest {
    @Test
    public void downKeepsFocusInsideListWhenNextRowIsNotAttached() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BackendPreferences.clearMode(context);
        AtomicReference<RecyclerView> listRef = new AtomicReference<>();
        AtomicReference<ConfigActivity> activityRef = new AtomicReference<>();

        try (ActivityScenario<ConfigActivity> scenario = ActivityScenario.launch(
                new Intent(context, ConfigActivity.class))) {
            scenario.onActivity(activity -> {
                activityRef.set(activity);
                FrameLayout host = new FrameLayout(activity);
                RecyclerView list = new RecyclerView(activity);
                list.setId(R.id.rvSongs);
                list.setFocusable(true);
                list.setFocusableInTouchMode(true);
                list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                list.setLayoutManager(new LinearLayoutManager(activity));
                LxMusicAdapter adapter = new LxMusicAdapter();
                adapter.setSongs(songs(8));
                list.setAdapter(adapter);
                host.addView(list, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(activity, 72)));
                activity.setContentView(host);
                listRef.set(list);
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                RecyclerView.ViewHolder first = listRef.get()
                        .findViewHolderForAdapterPosition(0);
                assertNotNull(first);
                assertTrue(first.itemView.requestFocusFromTouch());
            });

            for (int expected = 1; expected <= 6; expected++) {
                InstrumentationRegistry.getInstrumentation()
                        .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                Thread.sleep(100L);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                int expectedPosition = expected;
                scenario.onActivity(activity -> {
                    RecyclerView list = listRef.get();
                    View focused = activityRef.get().getCurrentFocus();
                    assertTrue(isWithinView(focused, list));
                    RecyclerView.ViewHolder holder = list.findContainingViewHolder(focused);
                    assertNotNull(holder);
                    assertEquals(expectedPosition, holder.getAdapterPosition());
                });
            }
            assertActionColumnMovesDown(scenario, listRef, activityRef, R.id.btnItemPlay);
            assertActionColumnMovesDown(scenario, listRef, activityRef, R.id.btnItemFullscreen);
            assertActionColumnMovesDown(scenario, listRef, activityRef, R.id.btnItemFav);
        } finally {
            BackendPreferences.clearMode(context);
        }
    }

    @Test
    public void rightEntersFloatingPlayerOnlyFromRightmostSongAction() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BackendPreferences.clearMode(context);
        AtomicReference<FloatingPlayerWindow> floatingRef = new AtomicReference<>();

        try (ActivityScenario<ConfigActivity> scenario = ActivityScenario.launch(
                new Intent(context, ConfigActivity.class))) {
            scenario.onActivity(activity -> {
                FrameLayout host = new FrameLayout(activity);
                RecyclerView list = new RecyclerView(activity);
                list.setFocusable(true);
                list.setFocusableInTouchMode(true);
                list.setLayoutManager(new LinearLayoutManager(activity));
                LxMusicAdapter adapter = new LxMusicAdapter();
                adapter.setSongs(songs(1));
                list.setAdapter(adapter);
                FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 72));
                listParams.gravity = android.view.Gravity.BOTTOM;
                host.addView(list, listParams);
                activity.setContentView(host);

                FloatingPlayerWindow floating = new FloatingPlayerWindow(activity);
                floating.getContainer().setVisibility(View.VISIBLE);
                floating.getContainer().setAlpha(1f);
                floatingRef.set(floating);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(android.R.id.content)
                        .findViewById(R.id.rvSongs);
                if (list == null) {
                    ViewGroup content = activity.findViewById(android.R.id.content);
                    list = findRecyclerView(content);
                }
                assertNotNull(list);
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
                assertNotNull(holder);
                View play = holder.itemView.findViewById(R.id.btnItemPlay);
                View fullscreen = holder.itemView.findViewById(R.id.btnItemFullscreen);
                View favorite = holder.itemView.findViewById(R.id.btnItemFav);
                FloatingPlayerWindow floating = floatingRef.get();

                assertEquals(R.id.btnItemFullscreen, play.getNextFocusRightId());
                assertEquals(R.id.btnItemFav, fullscreen.getNextFocusRightId());
                assertEquals(R.id.floatingPlayerContainer, favorite.getNextFocusRightId());
                assertFalse(floating.handleDirectionalKey(KeyEvent.KEYCODE_DPAD_RIGHT, play));
                assertFalse(floating.handleDirectionalKey(KeyEvent.KEYCODE_DPAD_RIGHT, fullscreen));
                assertTrue(floating.handleDirectionalKey(KeyEvent.KEYCODE_DPAD_RIGHT, favorite));
                assertEquals(floating.getContainer(), activity.getCurrentFocus());
            });
        } finally {
            if (floatingRef.get() != null) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(
                        () -> floatingRef.get().release());
            }
            BackendPreferences.clearMode(context);
        }
    }

    private void assertActionColumnMovesDown(ActivityScenario<ConfigActivity> scenario,
                                             AtomicReference<RecyclerView> listRef,
                                             AtomicReference<ConfigActivity> activityRef,
                                             int controlId) throws Exception {
        scenario.onActivity(activity -> {
            RecyclerView list = listRef.get();
            list.scrollToPosition(0);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            RecyclerView.ViewHolder first = listRef.get().findViewHolderForAdapterPosition(0);
            assertNotNull(first);
            View control = first.itemView.findViewById(controlId);
            assertNotNull(control);
            assertTrue(control.requestFocusFromTouch());
        });
        for (int expected = 1; expected <= 3; expected++) {
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            Thread.sleep(100L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            int expectedPosition = expected;
            scenario.onActivity(activity -> {
                RecyclerView list = listRef.get();
                View focused = activityRef.get().getCurrentFocus();
                assertTrue(isWithinView(focused, list));
                assertEquals(controlId, focused.getId());
                RecyclerView.ViewHolder holder = list.findContainingViewHolder(focused);
                assertNotNull(holder);
                assertEquals(expectedPosition, holder.getAdapterPosition());
            });
        }
    }

    private List<MusicInfo> songs(int count) {
        List<MusicInfo> songs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MusicInfo song = new MusicInfo();
            song.setId("focus-" + i);
            song.setName("Focus Song " + i);
            song.setSinger("Muggles");
            song.setSource("tx");
            songs.add(song);
        }
        return songs;
    }

    private boolean isWithinView(View child, View ancestor) {
        View current = child;
        while (current != null) {
            if (current == ancestor) return true;
            if (!(current.getParent() instanceof View)) return false;
            current = (View) current.getParent();
        }
        return false;
    }

    private RecyclerView findRecyclerView(View view) {
        if (view instanceof RecyclerView) return (RecyclerView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            RecyclerView result = findRecyclerView(group.getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
