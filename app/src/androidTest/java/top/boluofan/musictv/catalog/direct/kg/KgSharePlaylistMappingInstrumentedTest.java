package top.boluofan.musictv.catalog.direct.kg;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;

@RunWith(AndroidJUnit4.class)
public class KgSharePlaylistMappingInstrumentedTest {
    @Test
    public void mapsMobileGcidOutputIntoPlayableSongs() throws Exception {
        String html = "<script> window.$output = {\"info\":{"
                + "\"listinfo\":{\"name\":\"我的歌单1\",\"pic\":\"cover.jpg\","
                + "\"intro\":\"说明\",\"list_create_username\":\"用户\"},"
                + "\"songs\":[{\"audio_id\":40949274,"
                + "\"name\":\"李常超（Lao乾妈） - 盗墓笔记·十年人间\","
                + "\"singerinfo\":[{\"name\":\"李常超（Lao乾妈）\"}],"
                + "\"hash\":\"HASH128\",\"size\":4438958,\"timelen\":277394,"
                + "\"cover\":\"song-{size}.jpg\",\"remark\":\"专辑\","
                + "\"relate_goods\":[{\"bitrate\":320,\"hash\":\"HASH320\","
                + "\"size\":11097048}] }]}}; </script>";

        Playlist playlist = KgCatalog.playlistFromShareHtml(
                "gcid_a1b2c3d4e5f678", html);

        assertEquals("我的歌单1", playlist.getName());
        assertEquals("用户", playlist.getCreator());
        assertEquals(1, playlist.getSongs().size());
        MusicInfo song = playlist.getSongs().get(0);
        assertEquals("40949274", song.getSongmid());
        assertEquals("盗墓笔记·十年人间", song.getName());
        assertEquals("李常超（Lao乾妈）", song.getSinger());
        assertEquals("HASH128", song.getHash());
        assertEquals("04:37", song.getInterval());
        assertEquals(2, song.getTypes().size());
    }
}
