package top.boluofan.musictv.player.lyrics;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class LyricTimelineTest {
    @Test
    public void padsTimedLinesForSpatialDepthAndResetsCurrentIndex() {
        LyricTimeline timeline = new LyricTimeline();
        timeline.setLines(Collections.singletonList(new LyricTimeline.Line(1_000L, "Line")));
        timeline.setCurrentIndex(1);

        assertEquals(3, timeline.size());
        assertEquals(-1L, timeline.getLines().get(0).timeMs);
        assertEquals(1_000L, timeline.getLines().get(1).timeMs);
        assertEquals(Long.MAX_VALUE, timeline.getLines().get(2).timeMs);

        timeline.setLines(Collections.emptyList());
        assertEquals(0, timeline.size());
        assertEquals(-1, timeline.getCurrentIndex());
    }

    @Test
    public void findsActiveLineAcrossTimelineBoundaries() {
        LyricTimeline timeline = new LyricTimeline();
        timeline.setLines(Arrays.asList(
                new LyricTimeline.Line(1_000L, "First"),
                new LyricTimeline.Line(2_000L, "Second"),
                new LyricTimeline.Line(4_000L, "Third")
        ));

        assertEquals(-1, timeline.findActiveIndex(999L));
        assertEquals(1, timeline.findActiveIndex(1_000L));
        assertEquals(1, timeline.findActiveIndex(1_999L));
        assertEquals(2, timeline.findActiveIndex(2_000L));
        assertEquals(3, timeline.findActiveIndex(4_000L));
        assertEquals(3, timeline.findActiveIndex(Long.MAX_VALUE));
    }

    @Test
    public void duplicateTimestampsSelectLastMatchingLine() {
        LyricTimeline timeline = new LyricTimeline();
        timeline.setLines(Arrays.asList(
                new LyricTimeline.Line(1_000L, "First"),
                new LyricTimeline.Line(1_000L, "Second"),
                new LyricTimeline.Line(2_000L, "Third")
        ));

        assertEquals(2, timeline.findActiveIndex(1_000L));
        assertEquals(2, timeline.findActiveIndex(1_999L));
    }

    @Test
    public void emptyTimelineHasNoActiveLine() {
        LyricTimeline timeline = new LyricTimeline();

        assertEquals(-1, timeline.findActiveIndex(0L));
        timeline.setLines(Collections.emptyList());
        assertEquals(-1, timeline.findActiveIndex(Long.MAX_VALUE));
    }
}
