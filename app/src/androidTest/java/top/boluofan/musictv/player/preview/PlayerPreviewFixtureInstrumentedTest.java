package top.boluofan.musictv.player.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.player.lyrics.LyricParser;
import top.boluofan.musictv.player.visual.VinylTurntableView;

@RunWith(AndroidJUnit4.class)
public class PlayerPreviewFixtureInstrumentedTest {
    @Test
    public void fixtureLyricsParseIntoStableBilingualTimeline() {
        List<LyricParser.Line> lyrics = PlayerPreviewFixture.parseLyrics();

        assertEquals("Land, Sea and Sky", PlayerPreviewFixture.SONG_TITLE);
        assertEquals("Midnight Signal", PlayerPreviewFixture.ARTIST);
        assertEquals(28_000L, PlayerPreviewFixture.DURATION_MS);
        assertEquals(7, lyrics.size());
        assertEquals(0L, lyrics.get(0).timeMs);
        assertEquals(
                "Midnight wakes the silent wire\n午夜唤醒沉默的线路",
                lyrics.get(0).text
        );
        assertEquals(8_000L, lyrics.get(2).timeMs);
        assertEquals(
                "On the land, the sea, the sky\n在陆地、海洋与天空之上",
                lyrics.get(2).text
        );
        for (int index = 1; index < lyrics.size(); index++) {
            assertTrue(lyrics.get(index).timeMs > lyrics.get(index - 1).timeMs);
        }
    }

    @Test
    public void fixtureArtworkIsSquareAndCanRenderThroughTurntable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            VinylTurntableView turntable = new VinylTurntableView(context);
            Bitmap artwork = PlayerPreviewFixture.applyArtwork(turntable, 512);

            assertEquals(512, artwork.getWidth());
            assertEquals(512, artwork.getHeight());
            assertEquals(Bitmap.Config.ARGB_8888, artwork.getConfig());

            int viewSize = 720;
            turntable.measure(
                    View.MeasureSpec.makeMeasureSpec(viewSize, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(viewSize, View.MeasureSpec.EXACTLY)
            );
            turntable.layout(0, 0, viewSize, viewSize);
            turntable.setPlaying(true);
            Bitmap rendered = Bitmap.createBitmap(viewSize, viewSize, Bitmap.Config.ARGB_8888);
            turntable.draw(new Canvas(rendered));

            assertTrue(Color.alpha(rendered.getPixel(320, 380)) > 0);
            turntable.setPlaying(false);
        });
    }
}
