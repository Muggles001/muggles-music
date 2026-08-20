package top.boluofan.musictv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses standard LRC and LX/KRC-style millisecond lyric timelines. */
public final class LyricParser {
    private static final long TRANSLATION_MATCH_TOLERANCE_MS = 1200L;
    private static final Pattern OFFSET_PATTERN = Pattern.compile("(?i)\\[offset:([+-]?\\d+)]");
    private static final Pattern LRC_TIME_PATTERN = Pattern.compile(
            "\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]"
    );
    private static final Pattern MILLISECOND_LINE_PATTERN = Pattern.compile("^\\s*\\[(-?\\d+),\\d+]\\s*");
    private static final Pattern WORD_TIME_PATTERN = Pattern.compile("<-?\\d+,-?\\d+>");

    private LyricParser() {
    }

    public static List<Line> parse(String rawLyrics) {
        return parseSingle(rawLyrics);
    }

    public static String chooseMoreComplete(String preferredLyrics, String alternativeLyrics) {
        if (preferredLyrics == null || preferredLyrics.trim().isEmpty()) {
            return alternativeLyrics == null ? "" : alternativeLyrics;
        }
        if (alternativeLyrics == null || alternativeLyrics.trim().isEmpty()) return preferredLyrics;
        return parseSingle(alternativeLyrics).size() > parseSingle(preferredLyrics).size()
                ? alternativeLyrics
                : preferredLyrics;
    }

    public static List<Line> parse(String rawLyrics, String translatedLyrics) {
        List<Line> primary = parseSingle(rawLyrics);
        List<Line> translated = parseSingle(translatedLyrics);
        if (primary.isEmpty()) return translated;
        if (translated.isEmpty()) return primary;

        List<TranslationCandidate> candidates = new ArrayList<>();
        for (int primaryIndex = 0; primaryIndex < primary.size(); primaryIndex++) {
            for (int translationIndex = 0; translationIndex < translated.size(); translationIndex++) {
                long distance = Math.abs(
                        translated.get(translationIndex).timeMs - primary.get(primaryIndex).timeMs
                );
                if (distance <= TRANSLATION_MATCH_TOLERANCE_MS) {
                    candidates.add(new TranslationCandidate(primaryIndex, translationIndex, distance));
                }
                if (translated.get(translationIndex).timeMs
                        > primary.get(primaryIndex).timeMs + TRANSLATION_MATCH_TOLERANCE_MS) {
                    break;
                }
            }
        }
        // List.sort and Comparator.comparingLong are only available from API 24.
        // The app supports Android 5.0 (API 21), so use the legacy collection
        // helper to keep opening the lyric screen safe on older TVs.
        Collections.sort(candidates, new Comparator<TranslationCandidate>() {
            @Override
            public int compare(TranslationCandidate left, TranslationCandidate right) {
                return left.distanceMs < right.distanceMs ? -1
                        : (left.distanceMs == right.distanceMs ? 0 : 1);
            }
        });

        int[] translationForPrimary = new int[primary.size()];
        for (int i = 0; i < translationForPrimary.length; i++) translationForPrimary[i] = -1;
        boolean[] usedTranslations = new boolean[translated.size()];
        for (TranslationCandidate candidate : candidates) {
            if (translationForPrimary[candidate.primaryIndex] >= 0
                    || usedTranslations[candidate.translationIndex]) {
                continue;
            }
            translationForPrimary[candidate.primaryIndex] = candidate.translationIndex;
            usedTranslations[candidate.translationIndex] = true;
        }

        List<Line> merged = new ArrayList<>(primary.size());
        for (int primaryIndex = 0; primaryIndex < primary.size(); primaryIndex++) {
            Line primaryLine = primary.get(primaryIndex);
            String displayText = primaryLine.text;
            int bestIndex = translationForPrimary[primaryIndex];
            if (bestIndex >= 0) {
                String translatedText = translated.get(bestIndex).text;
                if (!translatedText.isEmpty() && !translatedText.equals(primaryLine.text)) {
                    displayText += "\n" + translatedText;
                }
            }
            merged.add(new Line(primaryLine.timeMs, displayText, primaryLine.rawLine));
        }
        return merged;
    }

    private static final class TranslationCandidate {
        final int primaryIndex;
        final int translationIndex;
        final long distanceMs;

        TranslationCandidate(int primaryIndex, int translationIndex, long distanceMs) {
            this.primaryIndex = primaryIndex;
            this.translationIndex = translationIndex;
            this.distanceMs = distanceMs;
        }
    }

    private static List<Line> parseSingle(String rawLyrics) {
        List<Line> result = new ArrayList<>();
        if (rawLyrics == null || rawLyrics.trim().isEmpty()) return result;

        long offsetMs = parseOffset(rawLyrics);
        String[] rawLines = rawLyrics.replace("\r", "").split("\n");
        for (String rawLine : rawLines) {
            parseLine(rawLine, offsetMs, result);
        }
        Collections.sort(result, new Comparator<Line>() {
            @Override
            public int compare(Line left, Line right) {
                return left.timeMs < right.timeMs ? -1
                        : (left.timeMs == right.timeMs ? 0 : 1);
            }
        });
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
            long timestamp = Math.max(0L, Long.parseLong(millisecondMatcher.group(1)) - offsetMs);
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
        // Match LX Web LinePlayer semantics: a positive [offset] advances the lyric clock,
        // which is equivalent to moving every lyric timestamp earlier.
        return Math.max(0L, (minutes * 60L + seconds) * 1000L + fractionMs - offsetMs);
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
