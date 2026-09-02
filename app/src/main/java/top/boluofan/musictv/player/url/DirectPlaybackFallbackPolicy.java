package top.boluofan.musictv.player.url;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import top.boluofan.musictv.catalog.model.MusicInfo;

public final class DirectPlaybackFallbackPolicy {
    private static final int MIN_MATCH_SCORE = 85;

    private DirectPlaybackFallbackPolicy() {
    }

    public static List<String> fallbackSources(String originalSource, String[] supportedSources) {
        List<String> ordered = new ArrayList<>();
        if (supportedSources == null) return ordered;
        for (String source : supportedSources) {
            if (hasText(source) && !source.equals(originalSource) && !ordered.contains(source)) {
                ordered.add(source);
            }
        }
        return ordered;
    }

    public static MusicInfo bestMatch(MusicInfo original, List<MusicInfo> candidates) {
        if (original == null || candidates == null) return null;
        MusicInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MusicInfo candidate : candidates) {
            int score = score(original, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= MIN_MATCH_SCORE ? best : null;
    }

    static int score(MusicInfo original, MusicInfo candidate) {
        if (candidate == null || !hasText(candidate.getSource())
                || !hasText(candidate.getSongmid())) return Integer.MIN_VALUE;
        String originalTitle = normalize(original.getName());
        String candidateTitle = normalize(candidate.getName());
        if (originalTitle.isEmpty() || candidateTitle.isEmpty()) return Integer.MIN_VALUE;

        int score;
        if (originalTitle.equals(candidateTitle)) {
            score = 100;
        } else if (originalTitle.contains(candidateTitle) || candidateTitle.contains(originalTitle)) {
            score = 72;
        } else {
            return Integer.MIN_VALUE;
        }

        String originalSinger = primarySinger(original.getSinger());
        String candidateSinger = primarySinger(candidate.getSinger());
        if (!originalSinger.isEmpty() && !candidateSinger.isEmpty()) {
            if (originalSinger.equals(candidateSinger)) {
                score += 30;
            } else if (originalSinger.contains(candidateSinger)
                    || candidateSinger.contains(originalSinger)) {
                score += 18;
            } else {
                return Integer.MIN_VALUE;
            }
        }

        int originalSeconds = durationSeconds(original.getInterval());
        int candidateSeconds = durationSeconds(candidate.getInterval());
        if (originalSeconds > 0 && candidateSeconds > 0) {
            int difference = Math.abs(originalSeconds - candidateSeconds);
            if (difference <= 3) score += 20;
            else if (difference <= 8) score += 10;
            else if (difference > 20) score -= 35;
        }
        return score;
    }

    private static String primarySinger(String value) {
        if (!hasText(value)) return "";
        String[] singers = value.split("[、,，/&\\s]+", 2);
        return normalize(singers.length == 0 ? value : singers[0]);
    }

    private static String normalize(String value) {
        if (!hasText(value)) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char character = lower.charAt(i);
            if (Character.isLetterOrDigit(character)) normalized.append(character);
        }
        return normalized.toString();
    }

    private static int durationSeconds(String interval) {
        if (!hasText(interval)) return 0;
        String[] parts = interval.trim().split(":");
        try {
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
            return Integer.parseInt(interval.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
