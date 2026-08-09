package top.boluofan.musictv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses standard LRC and LX/KRC-style millisecond lyric timelines. */
public final class LyricParser {
    private static final Pattern OFFSET_PATTERN = Pattern.compile("(?i)\\[offset:([+-]?\\d+)]");
    private static final Pattern LRC_TIME_PATTERN = Pattern.compile(
            "\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]"
    );
    private static final Pattern MILLISECOND_LINE_PATTERN = Pattern.compile("^\\s*\\[(-?\\d+),\\d+]\\s*");
    private static final Pattern WORD_TIME_PATTERN = Pattern.compile("<-?\\d+,-?\\d+>");

    private LyricParser() {
    }

    public static List<Line> parse(String rawLyrics) {
        List<Line> result = new ArrayList<>();
        if (rawLyrics == null || rawLyrics.trim().isEmpty()) return result;

        long offsetMs = parseOffset(rawLyrics);
        String[] rawLines = rawLyrics.replace("\r", "").split("\n");
        for (String rawLine : rawLines) {
            parseLine(rawLine, offsetMs, result);
        }
        result.sort(Comparator.comparingLong(line -> line.timeMs));
        return result;
    }

    private static long parseOffset(String rawLyrics) {
        Matcher matcher = OFFSET_PATTERN.matcher(rawLyrics);
        if (!matcher.find()) return 0L;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static void parseLine(String rawLine, long offsetMs, List<Line> result) {
        if (!rawLine.isEmpty() && rawLine.charAt(0) == '\uFEFF') rawLine = rawLine.substring(1);
        Matcher lrcMatcher = LRC_TIME_PATTERN.matcher(rawLine);
        List<Long> timestamps = new ArrayList<>();
        int textStart = 0;
        while (lrcMatcher.find()) {
            timestamps.add(parseLrcTimestamp(lrcMatcher, offsetMs));
            textStart = lrcMatcher.end();
        }

        if (!timestamps.isEmpty()) {
            String text = cleanText(rawLine.substring(textStart));
            if (!text.isEmpty()) {
                for (long timestamp : timestamps) {
                    result.add(new Line(timestamp, text, rawLine));
                }
            }
            return;
        }

        Matcher millisecondMatcher = MILLISECOND_LINE_PATTERN.matcher(rawLine);
        if (!millisecondMatcher.find()) return;
        try {
            long timestamp = Math.max(0L, Long.parseLong(millisecondMatcher.group(1)) + offsetMs);
            String text = cleanText(rawLine.substring(millisecondMatcher.end()));
            if (!text.isEmpty()) result.add(new Line(timestamp, text, rawLine));
        } catch (NumberFormatException ignored) {
            // Ignore malformed timing lines while preserving the rest of the lyric.
        }
    }

    private static long parseLrcTimestamp(Matcher matcher, long offsetMs) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long fractionMs = 0L;
        if (fraction != null) {
            if (fraction.length() == 1) fractionMs = Long.parseLong(fraction) * 100L;
            else if (fraction.length() == 2) fractionMs = Long.parseLong(fraction) * 10L;
            else fractionMs = Long.parseLong(fraction.substring(0, 3));
        }
        return Math.max(0L, (minutes * 60L + seconds) * 1000L + fractionMs + offsetMs);
    }

    private static String cleanText(String text) {
        return WORD_TIME_PATTERN.matcher(text).replaceAll("").trim();
    }

    public static final class Line {
        public final long timeMs;
        public final String text;
        public final String rawLine;

        Line(long timeMs, String text, String rawLine) {
            this.timeMs = timeMs;
            this.text = text;
            this.rawLine = rawLine;
        }
    }
}
