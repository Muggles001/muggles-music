package top.boluofan.musictv.player.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AudioQualityPolicy {
    private static final String QUALITY_128K = "128k";
    private static final String QUALITY_320K = "320k";
    private static final String QUALITY_FLAC = "flac";

    private AudioQualityPolicy() {
    }

    public static String choose(String preferredQuality, Collection<String> declaredQualities) {
        String preferred = normalizePreferred(preferredQuality);
        if (declaredQualities == null || declaredQualities.isEmpty()) return preferred;

        Set<String> declared = new LinkedHashSet<>();
        for (String quality : declaredQualities) {
            String normalized = normalizeDeclared(quality);
            if (normalized != null) declared.add(normalized);
        }
        if (declared.isEmpty()) return preferred;

        for (String candidate : fallbackOrder(preferred)) {
            if (declared.contains(candidate)) return candidate;
        }
        return declared.iterator().next();
    }

    public static List<String> fallbackOrder(String preferredQuality) {
        String preferred = normalizePreferred(preferredQuality);
        List<String> order = new ArrayList<>();
        if (QUALITY_FLAC.equals(preferred)) {
            order.add(QUALITY_FLAC);
            order.add(QUALITY_320K);
            order.add(QUALITY_128K);
        } else if (QUALITY_320K.equals(preferred)) {
            order.add(QUALITY_320K);
            order.add(QUALITY_128K);
        } else {
            order.add(QUALITY_128K);
        }
        return Collections.unmodifiableList(order);
    }

    private static String normalizePreferred(String quality) {
        String normalized = normalizeDeclared(quality);
        if (QUALITY_FLAC.equals(normalized) || QUALITY_320K.equals(normalized)
                || QUALITY_128K.equals(normalized)) {
            return normalized;
        }
        return QUALITY_320K;
    }

    private static String normalizeDeclared(String quality) {
        if (quality == null) return null;
        String normalized = quality.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) return null;
        if (normalized.equals("lossless")) return QUALITY_FLAC;
        if (normalized.equals("320") || normalized.equals("320000")) return QUALITY_320K;
        if (normalized.equals("128") || normalized.equals("128000")) return QUALITY_128K;
        return normalized;
    }
}
