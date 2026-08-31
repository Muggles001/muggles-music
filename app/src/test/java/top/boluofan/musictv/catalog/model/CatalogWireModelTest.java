package top.boluofan.musictv.catalog.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.Test;
import top.boluofan.musictv.ui.presentation.PlaylistPlayCountFormatter;

public class CatalogWireModelTest {
    private final Gson gson = new Gson();

    @Test
    public void playlistUsesNestedInfoBeforeTopLevelAliases() {
        Playlist playlist = gson.fromJson("{"
                + "\"id\":\"p1\",\"name\":\"top\",\"picUrl\":\"top.jpg\","
                + "\"creator\":\"top-author\",\"desc\":\"top-desc\","
                + "\"time\":\"top-time\",\"playCount\":12345,"
                + "\"info\":{\"name\":\"nested\",\"img\":\"nested.jpg\","
                + "\"author\":\"nested-author\",\"desc\":\"nested-desc\","
                + "\"time\":\"nested-time\",\"play_count\":\"nested-count\"}}",
                Playlist.class);

        assertEquals("nested.jpg", playlist.getPicUrl());
        assertEquals("nested", playlist.getName());
        assertEquals("nested-author", playlist.getCreator());
        assertEquals("nested-desc", playlist.getDesc());
        assertEquals("nested-time", playlist.getTime());
        assertEquals("nested-count", PlaylistPlayCountFormatter.format(playlist));
    }

    @Test
    public void musicInfoFallsBackFromEmptyTopLevelFieldsToNestedMeta() {
        MusicInfo song = gson.fromJson("{"
                + "\"id\":\"fallback-id\",\"songmid\":\"\",\"songId\":\"\","
                + "\"img\":\"\",\"albumName\":\"\","
                + "\"meta\":{\"songId\":\"nested-id\",\"picUrl\":\"nested.jpg\","
                + "\"albumName\":\"nested-album\"}}", MusicInfo.class);

        assertEquals("nested-id", song.getSongmid());
        assertEquals("nested.jpg", song.getPicUrl());
        assertEquals("nested-album", song.getAlbumName());
    }

    @Test
    public void playlistFallsBackAcrossTotalSongsCoverAndPlayCount() {
        Playlist playlist = gson.fromJson("{\"total\":\"2\",\"play_count\":\"raw\","
                + "\"list\":[{\"img\":\"first.jpg\"},{\"img\":\"second.jpg\"}]}",
                Playlist.class);

        assertEquals(2, playlist.getSongCount());
        assertEquals("first.jpg", playlist.getPicUrl());
        assertEquals("raw", PlaylistPlayCountFormatter.format(playlist));

        Playlist invalidTotal = gson.fromJson("{\"total\":\"bad\",\"list\":[{}]}",
                Playlist.class);
        assertEquals(0, invalidTotal.getSongCount());
    }

    @Test
    public void listDataPreservesAllWireCollections() {
        ListData data = gson.fromJson("{"
                + "\"defaultList\":[{\"id\":\"d\"}],"
                + "\"loveList\":[{\"id\":\"l\"}],"
                + "\"userList\":[{\"id\":\"u\"}],"
                + "\"tempList\":[{\"id\":\"t\"}]}", ListData.class);

        assertEquals("d", data.getDefaultList().get(0).getId());
        assertEquals("l", data.getLoveList().get(0).getId());
        assertEquals("u", data.getUserList().get(0).getId());
        assertEquals("t", data.getTempList().get(0).getId());
    }

    @Test
    public void loginAndMusicUrlKeepWireFieldsAndValidity() {
        LoginResponse login = gson.fromJson("{\"success\":true,\"message\":\"ok\","
                + "\"token\":\"token\",\"username\":\"user\"}", LoginResponse.class);
        assertTrue(login.isSuccess());
        assertEquals("ok", login.getMessage());
        assertEquals("token", login.getToken());
        assertEquals("user", login.getUsername());

        MusicUrlResponse url = gson.fromJson(
                "{\"url\":\"https://example.test/song\",\"type\":\"flac\","
                        + "\"source\":\"tx\"}", MusicUrlResponse.class);
        assertTrue(url.isValid());
        assertEquals("flac", url.getType());
        assertEquals("tx", url.getSource());
        assertFalse(gson.fromJson("{\"url\":\"\"}", MusicUrlResponse.class).isValid());
        assertNull(gson.fromJson("{}", MusicUrlResponse.class).getUrl());
    }
}
