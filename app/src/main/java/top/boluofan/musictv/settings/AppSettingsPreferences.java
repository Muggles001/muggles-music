package top.boluofan.musictv.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettingsPreferences {
    private static final String PREFS_NAME = "MugglesAppSettings";
    private static final String LEGACY_PREFS_NAME = "LxMusicPrefs";
    private static final String LEGACY_KEY_QUALITY = "quality";
    private static final String LEGACY_KEY_BACKGROUND_PLAY = "background_play";

    static final int CURRENT_SCHEMA_VERSION = 1;
    static final String KEY_SCHEMA_VERSION = "schema_version";
    static final String KEY_PREFERRED_QUALITY = "preferred_quality";
    static final String KEY_BACKGROUND_PLAYBACK = "background_playback";
    static final String KEY_SKIP_FAILED_TRACKS = "skip_failed_tracks";
    static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    static final String KEY_LYRIC_TRANSLATION = "lyric_translation";
    static final String KEY_BROWSE_MOTION = "browse_motion";

    private final SharedPreferences preferences;

    public AppSettingsPreferences(Context context) {
        this(context.getApplicationContext().getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE));
    }

    AppSettingsPreferences(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public static boolean initialize(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences legacy = appContext.getSharedPreferences(
                LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences settings = appContext.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        return migrateLegacy(legacy, settings);
    }

    static boolean migrateLegacy(SharedPreferences legacy, SharedPreferences settings) {
        if (isMigrationComplete(legacy, settings)) return true;

        AudioQuality quality = settings.contains(KEY_PREFERRED_QUALITY)
                ? readQuality(settings, KEY_PREFERRED_QUALITY)
                : readQuality(legacy, LEGACY_KEY_QUALITY);
        boolean backgroundPlayback = settings.contains(KEY_BACKGROUND_PLAYBACK)
                ? readBoolean(settings, KEY_BACKGROUND_PLAYBACK, true)
                : readBoolean(legacy, LEGACY_KEY_BACKGROUND_PLAY, true);
        BrowseMotion browseMotion = readBrowseMotion(settings, KEY_BROWSE_MOTION);

        boolean committed = settings.edit()
                .putString(KEY_PREFERRED_QUALITY, quality.storageValue)
                .putBoolean(KEY_BACKGROUND_PLAYBACK, backgroundPlayback)
                .putBoolean(KEY_SKIP_FAILED_TRACKS,
                        readBoolean(settings, KEY_SKIP_FAILED_TRACKS, true))
                .putBoolean(KEY_KEEP_SCREEN_ON,
                        readBoolean(settings, KEY_KEEP_SCREEN_ON, true))
                .putBoolean(KEY_LYRIC_TRANSLATION,
                        readBoolean(settings, KEY_LYRIC_TRANSLATION, true))
                .putString(KEY_BROWSE_MOTION, browseMotion.name())
                .commit();
        if (!committed) return false;

        if (!settings.edit()
                .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                .commit()) {
            return false;
        }

        return legacy.edit()
                .remove(LEGACY_KEY_QUALITY)
                .remove(LEGACY_KEY_BACKGROUND_PLAY)
                .commit();
    }

    private static boolean isMigrationComplete(
            SharedPreferences legacy, SharedPreferences settings) {
        try {
            // Schema is committed before legacy cleanup, so both states must be complete.
            return settings.getInt(KEY_SCHEMA_VERSION, 0) >= CURRENT_SCHEMA_VERSION
                    && !legacy.contains(LEGACY_KEY_QUALITY)
                    && !legacy.contains(LEGACY_KEY_BACKGROUND_PLAY);
        } catch (ClassCastException ignored) {
            return false;
        }
    }

    public AudioQuality getPreferredQuality() {
        return readQuality(preferences, KEY_PREFERRED_QUALITY);
    }

    public void setPreferredQuality(AudioQuality quality) {
        AudioQuality value = quality == null ? AudioQuality.QUALITY_320K : quality;
        preferences.edit().putString(KEY_PREFERRED_QUALITY, value.storageValue).apply();
    }

    public boolean isBackgroundPlaybackEnabled() {
        return readBoolean(preferences, KEY_BACKGROUND_PLAYBACK, true);
    }

    public void setBackgroundPlaybackEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_BACKGROUND_PLAYBACK, enabled).apply();
    }

    public boolean isSkipFailedTracksEnabled() {
        return readBoolean(preferences, KEY_SKIP_FAILED_TRACKS, true);
    }

    public void setSkipFailedTracksEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_SKIP_FAILED_TRACKS, enabled).apply();
    }

    public boolean isKeepScreenOnEnabled() {
        return readBoolean(preferences, KEY_KEEP_SCREEN_ON, true);
    }

    public void setKeepScreenOnEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply();
    }

    public boolean isLyricTranslationEnabled() {
        return readBoolean(preferences, KEY_LYRIC_TRANSLATION, true);
    }

    public void setLyricTranslationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_LYRIC_TRANSLATION, enabled).apply();
    }

    public BrowseMotion getBrowseMotion() {
        return readBrowseMotion(preferences, KEY_BROWSE_MOTION);
    }

    public void setBrowseMotion(BrowseMotion motion) {
        BrowseMotion value = motion == null ? BrowseMotion.STANDARD : motion;
        preferences.edit().putString(KEY_BROWSE_MOTION, value.name()).apply();
    }

    private static AudioQuality readQuality(SharedPreferences preferences, String key) {
        try {
            return AudioQuality.fromStorageValue(preferences.getString(key, null));
        } catch (ClassCastException ignored) {
            return AudioQuality.QUALITY_320K;
        }
    }

    private static BrowseMotion readBrowseMotion(SharedPreferences preferences, String key) {
        try {
            String value = preferences.getString(key, null);
            return value == null ? BrowseMotion.STANDARD : BrowseMotion.valueOf(value);
        } catch (ClassCastException | IllegalArgumentException ignored) {
            return BrowseMotion.STANDARD;
        }
    }

    private static boolean readBoolean(
            SharedPreferences preferences, String key, boolean defaultValue) {
        try {
            return preferences.getBoolean(key, defaultValue);
        } catch (ClassCastException ignored) {
            return defaultValue;
        }
    }

    public enum AudioQuality {
        QUALITY_128K("128k"),
        QUALITY_320K("320k"),
        LOSSLESS("flac");

        private final String storageValue;

        AudioQuality(String storageValue) {
            this.storageValue = storageValue;
        }

        public String getStorageValue() {
            return storageValue;
        }

        public static AudioQuality fromStorageValue(String value) {
            for (AudioQuality quality : values()) {
                if (quality.storageValue.equals(value)) return quality;
            }
            return QUALITY_320K;
        }
    }

    public enum BrowseMotion {
        STANDARD,
        REDUCED,
        STILL
    }
}
