package top.boluofan.musictv;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import org.junit.Test;
import top.boluofan.musictv.api.model.LyricInfo;
import top.boluofan.musictv.api.model.MusicInfo;

public class LyricContractTest {
    private final Gson gson = new Gson();

    @Test
    public void lyricInfo_acceptsLrcAlias() {
        LyricInfo info = gson.fromJson("{\"lrc\":\"[00:01.00]第一句\"}", LyricInfo.class);

        assertEquals("[00:01.00]第一句", info.getBestLyric());
    }

    @Test
    public void lyricInfo_fallsBackToKaraokeLyricAndRemovesWordTiming() {
        LyricInfo info = gson.fromJson(
                "{\"klyric\":\"[00:02.00]<-20,300>你<300,-10>好\"}",
                LyricInfo.class
        );

        assertEquals("[00:02.00]你好", info.getBestLyric());
    }

    @Test
    public void lyricInfo_fallsBackToTranslatedLyric() {
        LyricInfo info = gson.fromJson("{\"tlyric\":\"[00:03.00]Translated\"}", LyricInfo.class);

        assertEquals("[00:03.00]Translated", info.getBestLyric());
    }

    @Test
    public void musicInfo_readsNestedLyricFieldsWithoutLosingNumericSongId() {
        MusicInfo info = gson.fromJson(
                "{\"meta\":{\"source\":\"mg\",\"songId\":123456789012345678," +
                        "\"copyrightId\":\"cp-1\",\"lyricUrl\":\"https://example/lrc\"," +
                        "\"mrcurl\":\"https://example/mrc\",\"trcUrl\":\"https://example/trc\"}}",
                MusicInfo.class
        );

        assertEquals("mg", info.getSource());
        assertEquals("123456789012345678", info.getSongmid());
        assertEquals("cp-1", info.getCopyrightId());
        assertEquals("https://example/lrc", info.getLrcUrl());
        assertEquals("https://example/mrc", info.getMrcUrl());
        assertEquals("https://example/trc", info.getTrcUrl());
    }

    @Test
    public void musicInfo_prefersQqSongmidRegardlessOfJsonFieldOrder() {
        MusicInfo numericFirst = gson.fromJson(
                "{\"songId\":123456789,\"songmid\":\"0039MnYb0qxYhV\"}",
                MusicInfo.class
        );
        MusicInfo midFirst = gson.fromJson(
                "{\"songmid\":\"0039MnYb0qxYhV\",\"songId\":123456789}",
                MusicInfo.class
        );

        assertEquals("0039MnYb0qxYhV", numericFirst.getSongmid());
        assertEquals("0039MnYb0qxYhV", midFirst.getSongmid());
    }
}
