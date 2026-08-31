package top.boluofan.musictv.catalog.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import org.junit.Test;
import top.boluofan.musictv.catalog.model.MusicInfo;

public class DirectCatalogMappingInstrumentedTest {
    @Test
    public void mapsKuwoFixtureAndDropsDuplicateIdentity() throws Exception {
        List<MusicInfo> songs = DirectCatalogFixtureHarness.mapSongs("kw", "direct_kuwo_songs.json");

        assertEquals(1, songs.size());
        MusicInfo song = songs.get(0);
        DirectCatalogFixtureHarness.assertSong(song, "kw", "228908", "晴天 & Live", "周杰伦、阿信", "04:29");
        assertEquals("叶惠美", song.getAlbumName());
        assertEquals("1001", song.getAlbumId());
        assertEquals("https://img.example/star/albumcover/1000/cover.jpg", song.getPicUrl());
        DirectCatalogFixtureHarness.assertQuality(song, "128k", "4.10 MB", null);
        DirectCatalogFixtureHarness.assertQuality(song, "320k", "10.20 MB", null);
        DirectCatalogFixtureHarness.assertQuality(song, "flac", "30.30 MB", null);
    }

    @Test
    public void mapsKugouFixture() throws Exception {
        MusicInfo song = DirectCatalogFixtureHarness.onlySong("kg", "direct_kugou_songs.json");

        DirectCatalogFixtureHarness.assertSong(song, "kg", "7654321", "夜曲", "周杰伦", "04:29");
        assertEquals(Long.valueOf(269), song.getRawInterval());
        assertEquals("hash-128", song.getHash());
        assertEquals("https://img.example/400/cover.jpg", song.getPicUrl());
        DirectCatalogFixtureHarness.assertQuality(song, "128k", "1.00 MB", "hash-128");
        DirectCatalogFixtureHarness.assertQuality(song, "320k", "2.00 MB", "hash-320");
        DirectCatalogFixtureHarness.assertQuality(song, "flac", "3.00 MB", "hash-flac");
    }

    @Test
    public void mapsTencentFixture() throws Exception {
        MusicInfo song = DirectCatalogFixtureHarness.onlySong("tx", "direct_tencent_songs.json");

        DirectCatalogFixtureHarness.assertSong(song, "tx", "3003", "稻香", "周杰伦、杨瑞代", "03:43");
        assertEquals("0039MnYb0qxYhV", song.getSongmid());
        assertEquals("000bviBl4FjTpO", song.getAlbumMid());
        assertEquals("media-mid", song.getStrMediaMid());
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R500x500M000000bviBl4FjTpO.jpg",
                song.getPicUrl());
        DirectCatalogFixtureHarness.assertQuality(song, "flac24bit", "4.00 MB", null);
    }

    @Test
    public void mapsMiguFixture() throws Exception {
        MusicInfo song = DirectCatalogFixtureHarness.onlySong("mg", "direct_migu_songs.json");

        DirectCatalogFixtureHarness.assertSong(song, "mg", "5005", "七里香", "周杰伦、钟兴民", "01:02");
        assertEquals("七里香", song.getAlbumName());
        assertEquals("6006", song.getAlbumId());
        assertEquals("copyright-5005", song.getCopyrightId());
        assertEquals("https://lyrics.example/main.lrc", song.getLrcUrl());
        assertEquals("https://lyrics.example/romanized.lrc", song.getMrcUrl());
        assertEquals("https://lyrics.example/translated.lrc", song.getTrcUrl());
        assertEquals("https://d.musicapp.migu.cn/cover/5005.jpg", song.getPicUrl());
        DirectCatalogFixtureHarness.assertQuality(song, "flac24bit", "4.00 MB", null);
    }

}
