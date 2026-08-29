package top.boluofan.musictv.ui.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import top.boluofan.musictv.R;
import top.boluofan.musictv.api.model.Playlist;

@RunWith(AndroidJUnit4.class)
public class SquarePlaylistLayoutInstrumentedTest {

    @Test
    public void playlistCardKeepsSquareCoverAndStableMetadataSlot() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SquarePlaylistAdapter adapter = new SquarePlaylistAdapter();
            Playlist playlist = new Playlist();
            playlist.setName("很长的歌单标题用于验证两行排版不会挤压作者与歌曲数量元数据槽");
            adapter.setData(Collections.singletonList(playlist));

            RecyclerView parent = new RecyclerView(context);
            parent.setLayoutManager(new LinearLayoutManager(context));
            SquarePlaylistAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
            adapter.onBindViewHolder(holder, 0);
            View item = holder.itemView;
            int width = dp(context, 180);
            item.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            item.layout(0, 0, width, item.getMeasuredHeight());

            View cover = item.findViewById(R.id.coverContainer);
            View metadata = item.findViewById(R.id.metadataSlot);
            TextView title = item.findViewById(R.id.tvName);
            TextView creator = item.findViewById(R.id.tvCreator);

            assertEquals(cover.getMeasuredWidth(), cover.getMeasuredHeight());
            assertEquals(dp(context, 54), metadata.getMeasuredHeight());
            assertEquals(2, title.getMaxLines());
            assertEquals(TextUtils.TruncateAt.END, title.getEllipsize());
            assertEquals(View.VISIBLE, creator.getVisibility());
            assertNotNull(item.getOnFocusChangeListener());

            item.setScaleX(1.04f);
            item.setScaleY(1.04f);
            item.setTranslationZ(dp(context, 8));
            adapter.onViewRecycled(holder);
            assertEquals(1f, item.getScaleX(), 0f);
            assertEquals(1f, item.getScaleY(), 0f);
            assertEquals(0f, item.getTranslationZ(), 0f);
        });
    }

    @Test
    public void dataCreatesOnlyPlaylistCardsAndKeepsClickCallback() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SquarePlaylistAdapter adapter = new SquarePlaylistAdapter();
            Playlist playlist = new Playlist();
            playlist.setName("测试歌单");
            AtomicReference<Playlist> clicked = new AtomicReference<>();
            adapter.setOnItemClickListener(clicked::set);
            adapter.setData(Collections.singletonList(playlist));

            RecyclerView parent = new RecyclerView(context);
            parent.setLayoutManager(new LinearLayoutManager(context));
            parent.setAdapter(adapter);
            layoutRecycler(parent, context);
            SquarePlaylistAdapter.ViewHolder holder =
                    (SquarePlaylistAdapter.ViewHolder) parent
                            .findViewHolderForAdapterPosition(0);

            assertEquals(1, adapter.getItemCount());
            assertEquals(0, adapter.getItemViewType(0));
            assertNotNull(holder);
            holder.itemView.performClick();
            assertEquals(playlist, clicked.get());
        });
    }

    private static void layoutRecycler(RecyclerView recyclerView, Context context) {
        recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(dp(context, 960), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(context, 540), View.MeasureSpec.EXACTLY));
        recyclerView.layout(0, 0, recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
