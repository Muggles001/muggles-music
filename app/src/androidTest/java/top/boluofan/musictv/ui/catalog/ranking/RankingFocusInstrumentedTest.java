package top.boluofan.musictv.ui.catalog.ranking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.ui.settings.connection.ConfigActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.model.RankingBoard;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;

@RunWith(AndroidJUnit4.class)
public class RankingFocusInstrumentedTest {
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
    public void boardStripMovesExplicitlyAndConsumesItsRightEdge() throws Exception {
        AtomicReference<RankingFragment> fragmentRef = new AtomicReference<>();
        try (ActivityScenario<ConfigActivity> scenario = ActivityScenario.launch(
                new Intent(context, ConfigActivity.class))) {
            scenario.onActivity(activity -> {
                FrameLayout host = new FrameLayout(activity);
                host.setId(View.generateViewId());
                activity.setContentView(host);
                RankingFragment fragment = new RankingFragment();
                activity.getSupportFragmentManager().beginTransaction()
                        .replace(host.getId(), fragment)
                        .commitNow();
                fragmentRef.set(fragment);
                setField(fragment, "boardRequestGeneration", 1000L);
                List<Object> boards = field(fragment, "boards");
                boards.clear();
                boards.add(board("new", "新歌榜"));
                boards.add(board("hot", "热歌榜"));
                boards.add(board("original", "原创榜"));
                RecyclerView boardList = field(fragment, "rvBoards");
                assertNotNull(boardList.getAdapter());
                boardList.getAdapter().notifyDataSetChanged();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                RecyclerView boardList = activity.findViewById(R.id.rvBoards);
                RecyclerView.ViewHolder first = boardList.findViewHolderForAdapterPosition(0);
                assertNotNull(first);
                assertTrue(first.itemView.requestFocusFromTouch());
            });

            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity ->
                    assertFocusedBoard(activity.findViewById(R.id.rvBoards),
                            activity.getCurrentFocus(), 1));

            scenario.onActivity(activity -> {
                RecyclerView boardList = activity.findViewById(R.id.rvBoards);
                RecyclerView.ViewHolder last = boardList.findViewHolderForAdapterPosition(2);
                assertNotNull(last);
                assertTrue(last.itemView.requestFocusFromTouch());
            });
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity ->
                    assertFocusedBoard(activity.findViewById(R.id.rvBoards),
                            activity.getCurrentFocus(), 2));
        }
    }

    private static Object board(String id, String name) {
        return new RankingBoard(id, name);
    }

    private static void assertFocusedBoard(RecyclerView list, View focus, int position) {
        RecyclerView.ViewHolder holder = list.findContainingViewHolder(focus);
        assertNotNull(holder);
        assertEquals(position, holder.getAdapterPosition());
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
}
