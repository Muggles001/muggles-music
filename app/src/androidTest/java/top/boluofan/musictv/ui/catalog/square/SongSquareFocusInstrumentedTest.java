package top.boluofan.musictv.ui.catalog.square;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import top.boluofan.musictv.ui.settings.connection.ConfigActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.catalog.PlaylistCatalogActions;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.model.Playlist;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;
import top.boluofan.musictv.ui.catalog.square.SquarePlaylistAdapter;

@RunWith(AndroidJUnit4.class)
public class SongSquareFocusInstrumentedTest {
    private Context context;
    private boolean temporaryBackend;

    @Before
    public void configureBackend() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        temporaryBackend = BackendPreferences.getMode(context) == BackendMode.NONE;
        if (temporaryBackend) {
            LxRetrofitClient.saveConfig(context, "http://127.0.0.1:9", "", "", "");
            BackendPreferences.setMode(context, BackendMode.LXSERVER);
        }
    }

    @After
    public void clearBackend() {
        if (temporaryBackend) {
            BackendPreferences.clearMode(context);
            context.getSharedPreferences("LxMusicPrefs", Context.MODE_PRIVATE)
                    .edit().clear().commit();
        }
    }

    @Test
    public void sourceAndFirstRowMoveByGridColumnWithoutDefaultSelection() throws Exception {
        try (Harness harness = launchHarness(12)) {
            harness.scenario.onActivity(activity -> {
                RecyclerView.ViewHolder source = harness.sources()
                        .findViewHolderForAdapterPosition(0);
                assertNotNull(source);
                assertTrue(source.itemView.requestFocusFromTouch());
                assertTrue(harness.fragment.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_DOWN, downEvent()));
                assertFocusedPosition(harness.playlists(), activity.getCurrentFocus(), 0);

                assertTrue(harness.fragment.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_DOWN, downEvent()));
                assertFocusedPosition(harness.playlists(), activity.getCurrentFocus(), 6);

                RecyclerView.ViewHolder second = harness.playlists()
                        .findViewHolderForAdapterPosition(1);
                assertNotNull(second);
                assertTrue(second.itemView.requestFocusFromTouch());
                assertTrue(harness.fragment.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_DOWN, downEvent()));
                assertFocusedPosition(harness.playlists(), activity.getCurrentFocus(), 7);

                RecyclerView.ViewHolder first = harness.playlists()
                        .findViewHolderForAdapterPosition(0);
                assertNotNull(first);
                assertFalse(first.itemView.isSelected());
            });
        }
    }

    @Test
    public void lastAndShortRowsRouteDownToPagerWhileEmptyGridKeepsSourceFocus()
            throws Exception {
        try (Harness harness = launchHarness(5)) {
            harness.scenario.onActivity(activity -> {
                View nextPage = activity.findViewById(R.id.btnNextPage);
                nextPage.setEnabled(true);
                RecyclerView.ViewHolder first = harness.playlists()
                        .findViewHolderForAdapterPosition(0);
                assertNotNull(first);
                assertTrue(first.itemView.requestFocusFromTouch());
                assertTrue(harness.fragment.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_DOWN, downEvent()));
                assertSame(nextPage, activity.getCurrentFocus());

                setPlaylists(harness.fragment, new ArrayList<>());
                RecyclerView.ViewHolder source = harness.sources()
                        .findViewHolderForAdapterPosition(0);
                assertNotNull(source);
                assertTrue(source.itemView.requestFocusFromTouch());
                assertTrue(harness.fragment.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_DOWN, downEvent()));
                assertSame(source.itemView, activity.getCurrentFocus());
            });
        }
    }

    @Test
    public void downScrollsAndFocusesAnUnattachedTargetRow() throws Exception {
        try (Harness harness = launchHarness(12)) {
            harness.scenario.onActivity(activity -> {
                RecyclerView list = harness.playlists();
                RecyclerView.ViewHolder first = list.findViewHolderForAdapterPosition(0);
                assertNotNull(first);
                list.getLayoutParams().height = first.itemView.getHeight() + list.getPaddingTop();
                list.requestLayout();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            harness.scenario.onActivity(activity -> {
                RecyclerView list = harness.playlists();
                RecyclerView.ViewHolder first = list.findViewHolderForAdapterPosition(0);
                assertNotNull(first);
                assertTrue(first.itemView.requestFocusFromTouch());
                assertNull(list.findViewHolderForAdapterPosition(6));
                assertTrue(harness.fragment.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_DOWN, downEvent()));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            harness.scenario.onActivity(activity ->
                    assertFocusedPosition(harness.playlists(), activity.getCurrentFocus(), 6));
        }
    }

    @Test
    public void horizontalMovementStaysInsideEachGridRow() throws Exception {
        try (Harness harness = launchHarness(12)) {
            harness.scenario.onActivity(activity -> {
                RecyclerView.ViewHolder first = harness.playlists()
                        .findViewHolderForAdapterPosition(0);
                assertNotNull(first);
                assertTrue(first.itemView.requestFocusFromTouch());
                assertTrue(harness.fragment.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_RIGHT, downEvent()));
                assertFocusedPosition(harness.playlists(), activity.getCurrentFocus(), 1);

                assertTrue(harness.fragment.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_LEFT, downEvent()));
                assertFocusedPosition(harness.playlists(), activity.getCurrentFocus(), 0);

                RecyclerView.ViewHolder rightEdge = harness.playlists()
                        .findViewHolderForAdapterPosition(5);
                assertNotNull(rightEdge);
                assertTrue(rightEdge.itemView.requestFocusFromTouch());
                assertTrue(harness.fragment.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_RIGHT, downEvent()));
                assertFocusedPosition(harness.playlists(), activity.getCurrentFocus(), 5);
            });
        }
    }

    private Harness launchHarness(int itemCount) throws Exception {
        AtomicReference<SongSquareFragment> fragmentRef = new AtomicReference<>();
        ActivityScenario<ConfigActivity> scenario = ActivityScenario.launch(
                new Intent(context, ConfigActivity.class));
        scenario.onActivity(activity -> {
            FrameLayout host = new FrameLayout(activity);
            host.setId(View.generateViewId());
            activity.setContentView(host);
            SongSquareFragment fragment = new SongSquareFragment();
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(host.getId(), fragment)
                    .commitNow();
            fragmentRef.set(fragment);
            cancelPageOperation(fragment);
            setPlaylists(fragment, playlists(itemCount));
            setField(fragment, "navigationReady", true);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return new Harness(scenario, fragmentRef.get());
    }

    private static void setPlaylists(SongSquareFragment fragment, List<Playlist> values) {
        List<Playlist> playlists = field(fragment, "playlists");
        playlists.clear();
        playlists.addAll(values);
        SquarePlaylistAdapter adapter = field(fragment, "playlistAdapter");
        adapter.setData(playlists);
        RecyclerView list = field(fragment, "rvPlaylists");
        list.scrollToPosition(0);
    }

    private static void cancelPageOperation(SongSquareFragment fragment) {
        PlaylistCatalogActions.Operation operation = field(fragment, "activePageOperation");
        if (operation != null) operation.cancel();
        setField(fragment, "activePageOperation", null);
    }

    private static List<Playlist> playlists(int count) {
        List<Playlist> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Playlist playlist = new Playlist();
            playlist.setId("focus-" + i);
            playlist.setName("Focus Playlist " + i);
            playlist.setSource("tx");
            values.add(playlist);
        }
        return values;
    }

    private static KeyEvent downEvent() {
        return new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    private static void assertFocusedPosition(
            RecyclerView recyclerView, View focus, int expectedPosition) {
        RecyclerView.ViewHolder holder = recyclerView.findContainingViewHolder(focus);
        assertNotNull(holder);
        assertEquals(expectedPosition, holder.getAdapterPosition());
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(owner);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void setField(Object owner, String name, Object value) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static final class Harness implements AutoCloseable {
        final ActivityScenario<ConfigActivity> scenario;
        final SongSquareFragment fragment;

        Harness(ActivityScenario<ConfigActivity> scenario, SongSquareFragment fragment) {
            this.scenario = scenario;
            this.fragment = fragment;
        }

        RecyclerView sources() {
            return field(fragment, "rvSourceList");
        }

        RecyclerView playlists() {
            return field(fragment, "rvPlaylists");
        }

        @Override
        public void close() {
            scenario.close();
        }
    }
}
