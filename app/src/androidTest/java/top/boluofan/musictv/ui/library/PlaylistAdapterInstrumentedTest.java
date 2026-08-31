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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            Map<String, List<String>> playlists = new LinkedHashMap<>();
            for (int i = 1; i <= 11; i++) {
                List<String> songs = new ArrayList<>();
                songs.add("song-" + i);
                playlists.put("playlist-" + i, songs);
            }
            AtomicReference<String> clicked = new AtomicReference<>();
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
            assertEquals("playlist-11", clicked.get());
            assertEquals(View.VISIBLE, holder.itemView.getVisibility());
        });
    }

}
