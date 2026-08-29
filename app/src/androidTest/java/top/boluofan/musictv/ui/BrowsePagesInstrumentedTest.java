package top.boluofan.musictv.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.R;

@RunWith(AndroidJUnit4.class)
public class BrowsePagesInstrumentedTest {
    @Test
    public void searchStateChangeKeepsHeaderAndInputAnchorsStable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(R.layout.activity_search, null, false);
            View title = root.findViewById(R.id.tvTitle);
            View search = root.findViewById(R.id.layoutSearch);
            View hotTitle = root.findViewById(R.id.tvHotSearchTitle);
            View hotList = root.findViewById(R.id.rvHotSearch);
            View actions = root.findViewById(R.id.layoutSearchActions);
            View pager = root.findViewById(R.id.layoutSearchPager);
            View results = root.findViewById(R.id.rvSearchResults);

            actions.setVisibility(View.GONE);
            pager.setVisibility(View.GONE);
            results.setVisibility(View.GONE);
            hotTitle.setVisibility(View.VISIBLE);
            hotList.setVisibility(View.VISIBLE);
            layout(root, context);
            int titleTop = title.getTop();
            int searchTop = search.getTop();

            hotTitle.setVisibility(View.GONE);
            hotList.setVisibility(View.GONE);
            actions.setVisibility(View.VISIBLE);
            pager.setVisibility(View.VISIBLE);
            results.setVisibility(View.VISIBLE);
            layout(root, context);

            assertEquals(View.VISIBLE, title.getVisibility());
            assertEquals(titleTop, title.getTop());
            assertEquals(searchTop, search.getTop());
            assertEquals(R.id.rvSourceList, root.findViewById(R.id.btnSearch).getNextFocusDownId());
        });
    }

    @Test
    public void searchPlaybackActionsHaveAnExplicitHorizontalChain() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(R.layout.activity_search, null, false);
            View playAll = root.findViewById(R.id.btnSearchPlayAll);
            View playOrder = root.findViewById(R.id.btnSearchPlayOrderToggle);

            assertEquals(playOrder.getId(), playAll.getNextFocusRightId());
            assertEquals(playAll.getId(), playOrder.getNextFocusLeftId());
            assertEquals(playOrder.getId(), playOrder.getNextFocusRightId());
        });
    }

    @Test
    public void libraryOverviewAndDetailShareContentAnchor() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(R.layout.fragment_library, null, false);
            View playlists = root.findViewById(R.id.rvPlaylists);
            View songs = root.findViewById(R.id.rvSongs);
            songs.setVisibility(View.VISIBLE);
            layout(root, context);

            assertEquals(playlists.getTop(), songs.getTop());
            assertEquals(View.VISIBLE, root.findViewById(R.id.libraryActionBar).getVisibility());
            assertTransparentRoot(root);
        });
    }

    @Test
    public void songMetadataStaysInsideFixedHeightRow() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View row = LayoutInflater.from(context).inflate(R.layout.item_song, null, false);
            int width = dp(context, 960);
            row.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

            assertEquals(context.getResources().getDimensionPixelSize(R.dimen.lx_list_row_height),
                    row.getMeasuredHeight());
            View artist = row.findViewById(R.id.tvArtist);
            View source = row.findViewById(R.id.tvSource);
            assertSame(artist.getParent(), source.getParent());
            assertEquals(R.id.btnItemPlay, row.getNextFocusRightId());
        });
    }

    @Test
    public void fragmentPagesLeaveTheActivityBackgroundVisible() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertTransparentRoot(LayoutInflater.from(context)
                    .inflate(R.layout.activity_search, null, false));
            assertTransparentRoot(LayoutInflater.from(context)
                    .inflate(R.layout.activity_ranking, null, false));
            assertTransparentRoot(LayoutInflater.from(context)
                    .inflate(R.layout.fragment_library, null, false));
            assertTransparentRoot(LayoutInflater.from(context)
                    .inflate(R.layout.fragment_song_square, null, false));
            assertTransparentRoot(LayoutInflater.from(context)
                    .inflate(R.layout.activity_settings, null, false));
        });
    }

    @Test
    public void invalidPlaylistKeepsOnlyRecoveryControlsFocusable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<PlaylistDetailActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlaylistDetailActivity.class))) {
            scenario.onActivity(activity -> {
                View state = activity.findViewById(R.id.tvPlaylistState);
                View progress = activity.findViewById(R.id.loadingProgress);
                assertEquals(View.VISIBLE, state.getVisibility());
                assertSame(progress.getParent(), state.getParent());
                assertFalse(activity.findViewById(R.id.btnPlayAll).isFocusable());
                assertFalse(activity.findViewById(R.id.btnShuffle).isFocusable());
                assertFalse(activity.findViewById(R.id.btnFavorite).isFocusable());
                assertFalse(activity.findViewById(R.id.btnPlaylistDetailPrevPage).isFocusable());
                assertFalse(activity.findViewById(R.id.btnPlaylistDetailNextPage).isFocusable());
                assertTrue(activity.findViewById(R.id.btnBack).isFocusable());
            });
        }
    }

    private static void assertTransparentRoot(View root) {
        assertTrue(root.getBackground() instanceof ColorDrawable);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) root.getBackground()).getColor());
    }

    private static void layout(View root, Context context) {
        int width = dp(context, 1280);
        int height = dp(context, 720);
        root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
