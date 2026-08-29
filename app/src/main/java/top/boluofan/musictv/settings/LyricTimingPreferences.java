package top.boluofan.musictv.settings;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

public final class LyricTimingPreferences {
    private static final String PREFS_NAME = "LyricTimingPrefs";
    private static final String KEY_PREFIX = "lyric_timing_";

    private final SharedPreferences preferences;

    public LyricTimingPreferences(Context context) {
        this(context.getApplicationContext().getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE));
    }

    LyricTimingPreferences(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public long getAdjustmentMs(String source, String songId) {
        try {
            return preferences.getLong(songKey(source, songId), 0L);
        } catch (ClassCastException ignored) {
            return 0L;
        }
    }

    public void setAdjustmentMs(String source, String songId, long adjustmentMs) {
        SharedPreferences.Editor editor = preferences.edit();
        String key = songKey(source, songId);
        if (adjustmentMs == 0L) {
            editor.remove(key);
        } else {
            editor.putLong(key, adjustmentMs);
        }
        editor.apply();
    }

    public int count() {
        int count = 0;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(KEY_PREFIX)
                    && entry.getValue() instanceof Long
                    && (Long) entry.getValue() != 0L) {
                count++;
            }
        }
        return count;
    }

    public void clearAll() {
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX)) editor.remove(key);
        }
        editor.apply();
    }

    private static String songKey(String source, String songId) {
        String safeSource = source == null ? "" : source;
        String safeSongId = songId == null ? "" : songId;
        return KEY_PREFIX + safeSource + "_" + safeSongId;
    }
}
