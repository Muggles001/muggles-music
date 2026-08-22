package top.boluofan.musictv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LyricVisualInstrumentedTest {
    @Test
    public void activeLyricKeepsStraightSidesWithoutHorizontalClipping() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            FrameLayout parent = new FrameLayout(context);
            LyricAdapter adapter = new LyricAdapter();
            adapter.setLyrics(Collections.singletonList(new LyricAdapter.LyricLine(
                    0L,
                    "I want to break free from your lies\n我要从你的谎言中挣脱")));
            adapter.setCurrentIndex(1);

            LyricAdapter.LyricViewHolder holder = adapter.onCreateViewHolder(parent, 0);
            adapter.onBindViewHolder(holder, 1);
            int itemWidth = 1200;
            holder.itemView.measure(
                    View.MeasureSpec.makeMeasureSpec(itemWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            holder.itemView.layout(0, 0, itemWidth, holder.itemView.getMeasuredHeight());

            int horizontalPadding = context.getResources().getDimensionPixelSize(
                    R.dimen.player_lyric_horizontal_padding);
            assertEquals(1f, holder.itemView.getScaleX(), 0f);
            assertEquals(1f, holder.itemView.getScaleY(), 0f);
            assertEquals(horizontalPadding, holder.itemView.getPaddingLeft());
            assertEquals(horizontalPadding, holder.itemView.getPaddingRight());

            TextView lyric = holder.itemView.findViewById(R.id.tvLyric);
            assertTrue(lyric.getMeasuredWidth() <= itemWidth - horizontalPadding * 2);

            Drawable background = holder.itemView.getBackground();
            assertTrue(background instanceof SoftRoundedBackgroundDrawable);
            int radius = context.getResources().getDimensionPixelSize(
                    R.dimen.player_cover_corner_radius);
            int height = radius * 3;
            int expectedExtent = Math.min(radius, Math.round(
                    height * SoftRoundedBackgroundDrawable.MAX_CORNER_HEIGHT_FRACTION));
            Bitmap bitmap = Bitmap.createBitmap(itemWidth, height, Bitmap.Config.ARGB_8888);
            background.setBounds(0, 0, itemWidth, height);
            background.draw(new Canvas(bitmap));
            int firstFilledPixel = -1;
            for (int x = 0; x < itemWidth; x++) {
                if ((bitmap.getPixel(x, 0) >>> 24) >= 6) {
                    firstFilledPixel = x;
                    break;
                }
            }
            assertTrue("firstFilledPixel=" + firstFilledPixel
                            + ", expectedExtent=" + expectedExtent,
                    firstFilledPixel >= Math.round(expectedExtent * 0.85f)
                            && firstFilledPixel <= expectedExtent + 1);
            assertTrue(height - expectedExtent * 2 >= Math.round(height * 0.5f));
        });
    }
}
