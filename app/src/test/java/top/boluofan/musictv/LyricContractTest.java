package top.boluofan.musictv;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import java.util.List;
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

    @Test
    public void lyricParser_supportsMixedCreditsAndMillisecondTimeline() {
        String lyric = "[00:00.00]作词：某某\n"
                + "[00:01.20]编曲：某某\n"
                + "[15440,3530]<0,300>第<300,300>一<600,300>句\n"
                + "[19000,2800]<0,300>第<300,300>二<600,300>句";

        List<LyricParser.Line> lines = LyricParser.parse(lyric);

        assertEquals(4, lines.size());
        assertEquals(15440L, lines.get(2).timeMs);
        assertEquals("第一句", lines.get(2).text);
        assertEquals(19000L, lines.get(3).timeMs);
        assertEquals("第二句", lines.get(3).text);
    }

    @Test
    public void lyricParser_sortsUnorderedLinesAndExpandsRepeatedTimestamps() {
        String lyric = "[00:05.50]后一句\n[00:01.2][00:03.250]重复句";

        List<LyricParser.Line> lines = LyricParser.parse(lyric);

        assertEquals(3, lines.size());
        assertEquals(1200L, lines.get(0).timeMs);
        assertEquals(3250L, lines.get(1).timeMs);
        assertEquals(5500L, lines.get(2).timeMs);
    }

    @Test
    public void lyricParser_appliesOffset() {
        List<LyricParser.Line> lines = LyricParser.parse("[offset:-500]\n[00:02.00]歌词");

        assertEquals(1, lines.size());
        assertEquals(1500L, lines.get(0).timeMs);
    }

    @Test
    public void lyricParser_acceptsBomAndWhitespaceBeforeMillisecondTimeline() {
        List<LyricParser.Line> lines = LyricParser.parse(
                "\uFEFF  [15440,3530]<0,300>带<300,300>空<600,300>格"
        );

        assertEquals(1, lines.size());
        assertEquals(15440L, lines.get(0).timeMs);
        assertEquals("带空格", lines.get(0).text);
    }
}
