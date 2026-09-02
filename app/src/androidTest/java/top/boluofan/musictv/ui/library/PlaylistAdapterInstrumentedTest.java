package top.boluofan.musictv.ui.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.R;
import top.boluofan.musictv.testutil.RecyclerViewLayoutFixture;

@RunWith(AndroidJUnit4.class)
public class PlaylistAdapterInstrumentedTest {
    @Test
    public void paginationBindingAndClickBehaviorRemainStable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            PlaylistAdapter adapter = new PlaylistAdapter();
            List<LibraryPlaylistRow> playlists = new ArrayList<>();
            for (int i = 1; i <= 11; i++) {
                playlists.add(new LibraryPlaylistRow(
                        "id-" + i, "playlist-" + i, 1));
            }
            AtomicReference<LibraryPlaylistRow> clicked = new AtomicReference<>();
            adapter.setOnItemClickListener(clicked::set);
            adapter.setData(playlists);
            adapter.setPageSize(10);

            assertEquals(2, adapter.getPageCount());
            assertEquals(10, adapter.getItemCount());

            adapter.setPage(1);
            assertEquals(1, adapter.getCurrentPage());
            assertEquals(1, adapter.getItemCount());

            RecyclerView parent = new RecyclerView(context);
            parent.setLayoutManager(new LinearLayoutManager(context));
            parent.setAdapter(adapter);
            RecyclerViewLayoutFixture.layout(parent, context);
            PlaylistAdapter.ViewHolder holder = (PlaylistAdapter.ViewHolder)
                    parent.findViewHolderForAdapterPosition(0);

            assertNotNull(holder);
            assertEquals("11", ((TextView) holder.itemView.findViewById(R.id.tvIndex)).getText());
            assertEquals(R.id.btnLibraryNextPage, holder.itemView.getNextFocusDownId());
            holder.itemView.performClick();
            assertEquals("id-11", clicked.get().getPlaylistId());
            assertEquals(View.VISIBLE, holder.itemView.getVisibility());
        });
    }

    @Test
    public void sameNamePlaylistsRemainDistinctAndEmitStableIdentity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            PlaylistAdapter adapter = new PlaylistAdapter();
            List<LibraryPlaylistRow> rows = new ArrayList<>();
            rows.add(new LibraryPlaylistRow("first", "同名歌单", 2));
            rows.add(new LibraryPlaylistRow("second", "同名歌单", 3));
            AtomicReference<String> clickedId = new AtomicReference<>();
            adapter.setOnItemClickListener(row -> clickedId.set(row.getPlaylistId()));
            adapter.setData(rows);

            RecyclerView parent = new RecyclerView(context);
            parent.setLayoutManager(new LinearLayoutManager(context));
            parent.setAdapter(adapter);
            RecyclerViewLayoutFixture.layout(parent, context);

            assertEquals(2, adapter.getItemCount());
            PlaylistAdapter.ViewHolder second = (PlaylistAdapter.ViewHolder)
                    parent.findViewHolderForAdapterPosition(1);
            assertNotNull(second);
            second.itemView.performClick();
            assertEquals("second", clickedId.get());
        });
    }

    @Test
    public void managementActionsAppearOnlyInManagementModeAndKeepStableIdentity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            PlaylistAdapter adapter = new PlaylistAdapter();
            LibraryPlaylistRow row = new LibraryPlaylistRow("stable-id", "Managed", 4);
            AtomicReference<String> renamed = new AtomicReference<>();
            AtomicReference<String> deleted = new AtomicReference<>();
            adapter.setData(java.util.Collections.singletonList(row));
            adapter.setOnManageClickListener(new PlaylistAdapter.OnManageClickListener() {
                @Override
                public void onRename(LibraryPlaylistRow playlist) {
                    renamed.set(playlist.getPlaylistId());
                }

                @Override
                public void onDelete(LibraryPlaylistRow playlist) {
                    deleted.set(playlist.getPlaylistId());
                }
            });
            RecyclerView parent = new RecyclerView(context);
            parent.setLayoutManager(new LinearLayoutManager(context));
            parent.setAdapter(adapter);
            RecyclerViewLayoutFixture.layout(parent, context);
            PlaylistAdapter.ViewHolder holder = (PlaylistAdapter.ViewHolder)
                    parent.findViewHolderForAdapterPosition(0);

            assertNotNull(holder);
            assertEquals(View.GONE, holder.btnRename.getVisibility());
            assertEquals(View.GONE, holder.btnDelete.getVisibility());

            adapter.setManagementMode(true);
            RecyclerViewLayoutFixture.layout(parent, context);
            holder = (PlaylistAdapter.ViewHolder) parent.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            assertEquals(View.VISIBLE, holder.btnRename.getVisibility());
            assertEquals(View.VISIBLE, holder.btnDelete.getVisibility());
            assertEquals(R.id.btnPlaylistRename, holder.itemView.getNextFocusRightId());
            holder.btnRename.performClick();
            holder.btnDelete.performClick();
            assertEquals("stable-id", renamed.get());
            assertEquals("stable-id", deleted.get());
        });
    }

}
