package top.boluofan.musictv.player.url;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.junit.Test;

import top.boluofan.musictv.catalog.model.MusicInfo;

public class DirectPlaybackFallbackPolicyTest {
    @Test
    public void keepsDeclaredFallbackOrderAndExcludesOriginalPlatform() {
        assertEquals(Arrays.asList("mg", "kw", "tx"),
                DirectPlaybackFallbackPolicy.fallbackSources(
                        "wy", new String[]{"mg", "kw", "wy", "tx"}));
    }

    @Test
    public void choosesMatchingTitleSingerAndDuration() {
        MusicInfo original = song("夜空中最亮的星", "逃跑计划", "04:12", "wy", "1");
        MusicInfo wrongSinger = song("夜空中最亮的星", "其他歌手", "04:12", "kw", "2");
        MusicInfo correct = song("夜空中最亮的星", "逃跑计划", "04:10", "kw", "3");

        assertEquals(correct, DirectPlaybackFallbackPolicy.bestMatch(
                original, Arrays.asList(wrongSinger, correct)));
    }

    @Test
    public void rejectsDifferentRecordingWithLowConfidence() {
        MusicInfo original = song("同名歌曲", "歌手甲", "03:10", "wy", "1");
        MusicInfo candidate = song("同名歌曲 Live", "歌手乙", "05:40", "tx", "2");

        assertNull(DirectPlaybackFallbackPolicy.bestMatch(
                original, java.util.Collections.singletonList(candidate)));
    }

    private static MusicInfo song(String name, String singer, String interval,
                                  String source, String id) {
        MusicInfo song = new MusicInfo();
        song.setName(name);
        song.setSinger(singer);
        song.setInterval(interval);
        song.setSource(source);
        song.setSongmid(id);
        return song;
    }
}
