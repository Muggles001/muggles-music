package top.boluofan.musictv.player.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LyricPlaybackStateTest {
    private FakeParser parser;
    private FakeTimingStore timingStore;
    private LyricPlaybackState state;

    @Before
    public void setUp() {
        parser = new FakeParser();
        timingStore = new FakeTimingStore();
        state = new LyricPlaybackState(parser, timingStore);
    }

    @Test
    public void timedLyricsMapIntoPaddedTimelineAndPreserveRawLines() {
        parser.result = Arrays.asList(
                line(1_000L, "First", "[00:01]First"),
                line(2_000L, "Second", "[00:02]Second"));

        LyricPlaybackState.Presentation presentation =
                state.setLyrics("primary", "translated", true);

        assertEquals(LyricPlaybackState.Kind.TIMED, presentation.getKind());
        assertEquals("", presentation.getText());
        assertSame(state.getLines(), presentation.getLines());
        assertEquals(4, presentation.getLines().size());
        assertEquals(-1L, presentation.getLines().get(0).timeMs);
        assertEquals("[00:01]First", presentation.getLines().get(1).rawLine);
        assertEquals("[00:02]Second", presentation.getLines().get(2).rawLine);
        assertEquals(Long.MAX_VALUE, presentation.getLines().get(3).timeMs);
    }

    @Test
    public void untimedAndEmptyLyricsClearTimeline() {
        parser.result = Collections.emptyList();

        LyricPlaybackState.Presentation untimed = state.setLyrics("Plain text", "", false);
        assertEquals(LyricPlaybackState.Kind.UNTIMED, untimed.getKind());
        assertEquals("Plain text", untimed.getText());
        assertTrue(untimed.getLines().isEmpty());

        LyricPlaybackState.Presentation empty = state.setLyrics("  ", "Translation", true);
        assertEquals(LyricPlaybackState.Kind.EMPTY, empty.getKind());
        assertEquals("", empty.getText());
        assertTrue(empty.getLines().isEmpty());
    }

    @Test
    public void translationToggleReparsesSavedRawSnapshot() {
        parser.result = Collections.singletonList(line(1_000L, "Line", "raw"));
        state.setLyrics("Primary", "Translated", false);
        assertEquals("", parser.lastTranslated);

        state.setTranslationEnabled(true);
        assertEquals("Primary", parser.lastPrimary);
        assertEquals("Translated", parser.lastTranslated);
        assertEquals("Primary", state.getPrimaryLyrics());
        assertEquals("Translated", state.getTranslatedLyrics());

        state.setTranslationEnabled(false);
        assertEquals("", parser.lastTranslated);
        assertFalse(state.isTranslationEnabled());
    }

    @Test
    public void replacingLyricsResetsActiveTimelineIndex() {
        parser.result = Collections.singletonList(line(1_000L, "First", "raw-1"));
        state.setLyrics("one", "", false);
        assertTrue(state.updatePosition(1_000L).isChanged());

        parser.result = Collections.singletonList(line(2_000L, "Second", "raw-2"));
        state.setLyrics("two", "", false);

        LyricPlaybackState.PositionUpdate update = state.updatePosition(1_999L);
        assertFalse(update.isChanged());
        assertEquals(-1, update.getActiveIndex());
    }

    @Test
    public void rewindingBeforeFirstLineKeepsCurrentVisualIndex() {
        parser.result = Collections.singletonList(line(1_000L, "First", "raw"));
        state.setLyrics("one", "", false);
        assertTrue(state.updatePosition(1_000L).isChanged());

        LyricPlaybackState.PositionUpdate update = state.updatePosition(0L);

        assertFalse(update.isChanged());
        assertEquals(1, update.getActiveIndex());
        assertFalse(state.updatePosition(1_000L).isChanged());
    }

    @Test
    public void positionUsesOffsetAndOnlyChangesWhenActiveLineChanges() {
        timingStore.values.put("kw:mid", 500L);
        state.onTrack("kw", "mid");
        parser.result = Arrays.asList(
                line(1_000L, "One", "one"),
                line(2_000L, "Two", "two"),
                line(3_000L, "Three", "three"),
                line(7_000L, "Four", "four"));
        state.setLyrics("timed", "", false);

        LyricPlaybackState.PositionUpdate first = state.updatePosition(500L);
        LyricPlaybackState.PositionUpdate unchanged = state.updatePosition(700L);
        LyricPlaybackState.PositionUpdate nearby = state.updatePosition(1_500L);
        LyricPlaybackState.PositionUpdate distant = state.updatePosition(6_500L);

        assertTrue(first.isChanged());
        assertEquals(1, first.getActiveIndex());
        assertFalse(first.shouldAnimate());
        assertFalse(unchanged.isChanged());
        assertEquals(1, unchanged.getActiveIndex());
        assertTrue(nearby.isChanged());
        assertEquals(2, nearby.getActiveIndex());
        assertTrue(nearby.shouldAnimate());
        assertTrue(distant.isChanged());
        assertEquals(4, distant.getActiveIndex());
        assertTrue(distant.shouldAnimate());
    }

    @Test
    public void jumpsLargerThanTwoRowsDoNotAnimate() {
        parser.result = Arrays.asList(
                line(1_000L, "One", "one"),
                line(2_000L, "Two", "two"),
                line(3_000L, "Three", "three"),
                line(7_000L, "Four", "four"));
        state.setLyrics("timed", "", false);
        state.updatePosition(1_000L);

        LyricPlaybackState.PositionUpdate jump = state.updatePosition(7_000L);

        assertTrue(jump.isChanged());
        assertEquals(4, jump.getActiveIndex());
        assertFalse(jump.shouldAnimate());
    }

    @Test
    public void negativeAdjustedPositionClampsToZero() {
        timingStore.values.put("kw:mid", -500L);
        state.onTrack("kw", "mid");
        parser.result = Collections.singletonList(line(1L, "Line", "raw"));
        state.setLyrics("timed", "", false);

        LyricPlaybackState.PositionUpdate update = state.updatePosition(100L);

        assertFalse(update.isChanged());
        assertEquals(-1, update.getActiveIndex());
        assertEquals(0L, update.getAdjustedPositionMs());
    }

    @Test
    public void timingAdjustmentsAccumulateClampPersistAndReset() {
        state.onTrack("tx", "song");

        assertEquals(250L, state.adjustTiming(250L));
        assertEquals(500L, state.adjustTiming(250L));
        assertEquals(5_000L, state.adjustTiming(10_000L));
        assertEquals(-1_000L, state.adjustTiming(-6_000L));
        assertEquals(-5_000L, state.adjustTiming(-20_000L));
        assertEquals(-5_000L, state.getOffsetMs());
        assertEquals(-5_000L, (long) timingStore.values.get("tx:song"));

        state.resetTiming();
        assertEquals(0L, state.getOffsetMs());
        assertEquals(0L, (long) timingStore.values.get("tx:song"));
    }

    @Test
    public void trackOffsetsRemainIsolatedBySourceAndSongmid() {
        timingStore.values.put("kw:same", 250L);
        timingStore.values.put("tx:same", -750L);

        state.onTrack("kw", "same");
        assertEquals(250L, state.getOffsetMs());
        state.onTrack("tx", "same");
        assertEquals(-750L, state.getOffsetMs());
        state.onTrack(null, null);
        state.adjustTiming(250L);

        assertEquals(250L, (long) timingStore.values.get(":"));
    }

    @Test
    public void exposedLinesRetainOnlyParserRawLinesForSaving() {
        parser.result = Arrays.asList(
                line(1_000L, "Primary\nTranslation", "[00:01]Primary"),
                line(2_000L, "Second", "[00:02]Second"));

        state.setLyrics("primary", "translated", true);

        List<String> saved = new ArrayList<>();
        for (LyricTimeline.Line line : state.getLines()) {
            if (!line.rawLine.isEmpty()) saved.add(line.rawLine);
        }
        assertEquals(Arrays.asList("[00:01]Primary", "[00:02]Second"), saved);
    }

    private static LyricPlaybackState.ParsedLine line(long timeMs, String text, String raw) {
        return new LyricPlaybackState.ParsedLine(timeMs, text, raw);
    }

    private static final class FakeParser implements LyricPlaybackState.Parser {
        List<LyricPlaybackState.ParsedLine> result = Collections.emptyList();
        String lastPrimary;
        String lastTranslated;

        @Override
        public List<LyricPlaybackState.ParsedLine> parse(String primary, String translated) {
            lastPrimary = primary;
            lastTranslated = translated;
            return result;
        }
    }

    private static final class FakeTimingStore implements LyricPlaybackState.TimingStore {
        final Map<String, Long> values = new HashMap<>();

        @Override
        public long get(String source, String songmid) {
            Long value = values.get(key(source, songmid));
            return value == null ? 0L : value;
        }

        @Override
        public void set(String source, String songmid, long offsetMs) {
            values.put(key(source, songmid), offsetMs);
        }

        private static String key(String source, String songmid) {
            return (source == null ? "" : source) + ":" + (songmid == null ? "" : songmid);
        }
    }
}
