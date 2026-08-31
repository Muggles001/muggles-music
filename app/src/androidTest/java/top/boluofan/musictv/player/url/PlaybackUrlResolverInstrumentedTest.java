package top.boluofan.musictv.player.url;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;
import org.junit.runner.RunWith;

import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.player.url.PlaybackUrlResolver;

@RunWith(AndroidJUnit4.class)
public class PlaybackUrlResolverInstrumentedTest {
    @Test
    public void resolvePlaceholderRoundTripsReservedAndUnicodeValues() {
        MusicInfo song = new MusicInfo();
        song.setSource("kw&local=1");
        song.setSongmid("id/with?query#fragment");
        song.setName("落雪 + live & acoustic");

        Uri placeholder = PlaybackUrlResolver.buildResolveUri(song);

        assertEquals(PlaybackUrlResolver.RESOLVE_SCHEME, placeholder.getScheme());
        assertEquals(PlaybackUrlResolver.RESOLVE_HOST, placeholder.getHost());
        assertEquals(song.getSource(), placeholder.getQueryParameter("source"));
        assertEquals(song.getSongmid(), placeholder.getQueryParameter("songmid"));
        assertEquals(song.getName(), placeholder.getQueryParameter("name"));
        JsonObject payload = new JsonParser()
                .parse(placeholder.getQueryParameter("payload"))
                .getAsJsonObject();
        assertEquals(song.getSongmid(), payload.get("songmid").getAsString());
        assertTrue(placeholder.getEncodedQuery().contains("%26"));
        assertTrue(placeholder.getEncodedQuery().contains("%23"));
    }
}
