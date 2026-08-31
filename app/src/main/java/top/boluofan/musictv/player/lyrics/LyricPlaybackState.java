package top.boluofan.musictv.player.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class LyricPlaybackState {
    private static final long MIN_OFFSET_MS = -5_000L;
    private static final long MAX_OFFSET_MS = 5_000L;

    public interface Parser {
        List<ParsedLine> parse(String primary, String translated);
    }

    public interface TimingStore {
        long get(String source, String songmid);
        void set(String source, String songmid, long offsetMs);
    }

    public enum Kind { TIMED, UNTIMED, EMPTY }

    public static final class ParsedLine {
        private final long timeMs;
        private final String text;
        private final String rawLine;

        public ParsedLine(long timeMs, String text, String rawLine) {
            this.timeMs = timeMs;
            this.text = value(text);
            this.rawLine = value(rawLine);
        }

        public long getTimeMs() { return timeMs; }
        public String getText() { return text; }
        public String getRawLine() { return rawLine; }
    }

    public static final class Presentation {
        private final Kind kind;
        private final List<LyricTimeline.Line> lines;
        private final String text;

        private Presentation(Kind kind, List<LyricTimeline.Line> lines, String text) {
            this.kind = kind;
            this.lines = lines;
            this.text = text;
        }

        public Kind getKind() { return kind; }
        public List<LyricTimeline.Line> getLines() { return lines; }
        public String getText() { return text; }
    }

    public static final class PositionUpdate {
        private final boolean changed;
        private final int activeIndex;
        private final boolean animate;
        private final long adjustedPositionMs;

        private PositionUpdate(boolean changed, int activeIndex, boolean animate,
                               long adjustedPositionMs) {
            this.changed = changed;
            this.activeIndex = activeIndex;
            this.animate = animate;
            this.adjustedPositionMs = adjustedPositionMs;
        }

        public boolean isChanged() { return changed; }
        public int getActiveIndex() { return activeIndex; }
        public boolean shouldAnimate() { return animate; }
        public long getAdjustedPositionMs() { return adjustedPositionMs; }
    }

    private final Parser parser;
    private final TimingStore timingStore;
    private final LyricTimeline timeline = new LyricTimeline();
    private String source;
    private String songmid;
    private String primaryLyrics = "";
    private String translatedLyrics = "";
    private boolean translationEnabled;
    private long offsetMs;

    public LyricPlaybackState(Parser parser, TimingStore timingStore) {
        this.parser = Objects.requireNonNull(parser);
        this.timingStore = Objects.requireNonNull(timingStore);
    }

    public void onTrack(String source, String songmid) {
        this.source = source;
        this.songmid = songmid;
        offsetMs = clamp(timingStore.get(source, songmid));
    }

    public Presentation setLyrics(String primary, String translated,
                                  boolean translationEnabled) {
        primaryLyrics = value(primary);
        translatedLyrics = value(translated);
        this.translationEnabled = translationEnabled;
        return rebuildPresentation();
    }

    public Presentation setTranslationEnabled(boolean enabled) {
        translationEnabled = enabled;
        return rebuildPresentation();
    }

    public PositionUpdate updatePosition(long playerPositionMs) {
        long adjusted = adjustedPosition(playerPositionMs);
        int activeIndex = timeline.findActiveIndex(adjusted);
        int previousIndex = timeline.getCurrentIndex();
        if (activeIndex == -1) {
            return new PositionUpdate(false, previousIndex, false, adjusted);
        }
        if (activeIndex == previousIndex) {
            return new PositionUpdate(false, activeIndex, false, adjusted);
        }
        boolean animate = previousIndex >= 0 && activeIndex >= 0
                && Math.abs(activeIndex - previousIndex) <= 2;
        timeline.setCurrentIndex(activeIndex);
        return new PositionUpdate(true, activeIndex, animate, adjusted);
    }

    public long adjustTiming(long deltaMs) {
        offsetMs = clamp(saturatedAdd(offsetMs, deltaMs));
        timingStore.set(source, songmid, offsetMs);
        return offsetMs;
    }

    public void resetTiming() {
        offsetMs = 0L;
        timingStore.set(source, songmid, 0L);
    }

    public long getOffsetMs() { return offsetMs; }
    public String getPrimaryLyrics() { return primaryLyrics; }
    public String getTranslatedLyrics() { return translatedLyrics; }
    public boolean isTranslationEnabled() { return translationEnabled; }
    public List<LyricTimeline.Line> getLines() { return timeline.getLines(); }

    private Presentation rebuildPresentation() {
        String visibleTranslation = translationEnabled ? translatedLyrics : "";
        List<ParsedLine> parsed = parser.parse(primaryLyrics, visibleTranslation);
        List<LyricTimeline.Line> lines = new ArrayList<>();
        if (parsed != null) {
            for (ParsedLine line : parsed) {
                if (line == null) continue;
                lines.add(new LyricTimeline.Line(
                        line.getTimeMs(), line.getText(), line.getRawLine()));
            }
        }
        timeline.setLines(lines);
        if (!lines.isEmpty()) {
            return new Presentation(Kind.TIMED, timeline.getLines(), "");
        }
        if (!primaryLyrics.trim().isEmpty()) {
            return new Presentation(Kind.UNTIMED, timeline.getLines(), primaryLyrics);
        }
        return new Presentation(Kind.EMPTY, timeline.getLines(), "");
    }

    private long adjustedPosition(long playerPositionMs) {
        if (offsetMs > 0L && playerPositionMs > Long.MAX_VALUE - offsetMs) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, playerPositionMs + offsetMs);
    }

    private static long clamp(long offsetMs) {
        return Math.max(MIN_OFFSET_MS, Math.min(MAX_OFFSET_MS, offsetMs));
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
