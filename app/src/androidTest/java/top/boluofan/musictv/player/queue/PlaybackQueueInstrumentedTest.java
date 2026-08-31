package top.boluofan.musictv.player.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import androidx.media3.common.MediaItem;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import top.boluofan.musictv.player.queue.PlaybackQueue;
import top.boluofan.musictv.catalog.model.MusicInfo;

@RunWith(AndroidJUnit4.class)
public class PlaybackQueueInstrumentedTest {
    @Test
    public void filtersMalformedEntriesAndPreservesSourceIndexMapping() {
        MusicInfo first = song("first-id", "First", "kw", "first-mid");
        MusicInfo missingSource = song("missing-source", "Missing source", null, "bad-mid");
        MusicInfo blankSongmid = song(null, "Blank songmid", "kg", "   ");
        MusicInfo last = song("last-id", "Last", "tx", "last-mid");

        PlaybackQueue queue = PlaybackQueue.from(Arrays.asList(
                null, first, missingSource, blankSongmid, last));

        assertEquals(2, queue.size());
        assertEquals(0, queue.queueIndexForSourceIndex(1));
        assertEquals(1, queue.queueIndexForSourceIndex(4));
        assertEquals(-1, queue.queueIndexForSourceIndex(0));
        assertEquals(-1, queue.queueIndexForSourceIndex(2));
        assertEquals(-1, queue.queueIndexForSourceIndex(-1));
        assertEquals(-1, queue.queueIndexForSourceIndex(5));

        List<MediaItem> items = queue.getMediaItems();
        assertEquals("first-mid", items.get(0).mediaId);
        assertEquals("last-mid", items.get(1).mediaId);
    }

    @Test
    public void mediaItemMapsIdentityAndPlaybackExtras() {
        MusicInfo song = song("catalog-id", "落雪", "mg", "playback-mid");
        song.setSinger("歌手");
        song.setAlbumName("专辑");
        song.setPicUrl("https://example.com/cover.jpg");

        MediaItem item = PlaybackQueue.createMediaItem(song);

        assertEquals("playback-mid", item.mediaId);
        assertEquals("落雪", item.mediaMetadata.title.toString());
        assertEquals("歌手", item.mediaMetadata.artist.toString());
        assertEquals("专辑", item.mediaMetadata.albumTitle.toString());
        assertEquals("https://example.com/cover.jpg", item.mediaMetadata.artworkUri.toString());
        assertEquals("mg", item.localConfiguration.uri.getQueryParameter("source"));
        assertEquals("playback-mid", item.localConfiguration.uri.getQueryParameter("songmid"));
        assertEquals("落雪", item.localConfiguration.uri.getQueryParameter("name"));

        Bundle extras = item.mediaMetadata.extras;
        assertEquals("catalog-id", extras.getString("song_id"));
        assertEquals("mg", extras.getString("source"));
        assertEquals("playback-mid", extras.getString("songmid"));
        assertEquals("落雪", extras.getString("original_name"));
        assertEquals("歌手", extras.getString("singer"));
        assertEquals(new HashSet<>(Arrays.asList(
                        "song_id", "source", "songmid", "pic_url", "original_name", "singer",
                        "hash", "interval", "copyrightId", "albumId", "lrcUrl", "mrcUrl",
                        "trcUrl")),
                extras.keySet());
    }

    @Test
    public void emptyAndInvalidInputsProduceNoPlayableItems() {
        PlaybackQueue nullQueue = PlaybackQueue.from(null);
        PlaybackQueue emptyQueue = PlaybackQueue.from(Collections.emptyList());

        assertTrue(nullQueue.isEmpty());
        assertTrue(emptyQueue.isEmpty());
        assertEquals(-1, nullQueue.queueIndexForSourceIndex(0));
        assertNull(PlaybackQueue.createMediaItem(null));
        assertNull(PlaybackQueue.createMediaItem(song("id", "Name", "  ", "mid")));
        assertNull(PlaybackQueue.createMediaItem(song(null, "Name", "kw", "  ")));
        assertFalse(emptyQueue.getMediaItems().iterator().hasNext());
    }

    private static MusicInfo song(String id, String name, String source, String songmid) {
        MusicInfo song = new MusicInfo();
        song.setId(id);
        song.setName(name);
        song.setSource(source);
        song.setSongmid(songmid);
        return song;
    }
}
