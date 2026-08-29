package top.boluofan.musictv.player.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LyricTimeline {
    public static final class Line {
        public final long timeMs;
        public final String text;
        public final String rawLine;

        public Line(long timeMs, String text) {
            this(timeMs, text, "");
        }

        public Line(long timeMs, String text, String rawLine) {
            this.timeMs = timeMs;
            this.text = text == null ? "" : text;
            this.rawLine = rawLine == null ? "" : rawLine;
        }
    }

    private List<Line> lines = Collections.emptyList();
    private int currentIndex = -1;

    public void setLines(List<Line> sourceLines) {
        if (sourceLines == null || sourceLines.isEmpty()) {
            lines = Collections.emptyList();
        } else {
            List<Line> padded = new ArrayList<>(sourceLines.size() + 2);
            padded.add(new Line(-1L, ""));
            padded.addAll(sourceLines);
            padded.add(new Line(Long.MAX_VALUE, ""));
            lines = Collections.unmodifiableList(padded);
        }
        currentIndex = -1;
    }

    public List<Line> getLines() {
        return lines;
    }

    public int size() {
        return lines.size();
    }

    public int findActiveIndex(long positionMs) {
        if (lines.size() < 3) return -1;
        int low = 1;
        int high = lines.size() - 2;
        int activeIndex = -1;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            if (positionMs >= lines.get(middle).timeMs) {
                activeIndex = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return activeIndex;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }
}
