package top.boluofan.musictv.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import top.boluofan.musictv.test.InMemorySharedPreferences;

public class AppSettingsPreferencesTest {
    @Test
    public void defaultsAndTypedValuesAreStable() {
        InMemorySharedPreferences store = new InMemorySharedPreferences();
        AppSettingsPreferences settings = new AppSettingsPreferences(store);

        assertEquals(AppSettingsPreferences.AudioQuality.QUALITY_320K,
                settings.getPreferredQuality());
        assertTrue(settings.isBackgroundPlaybackEnabled());
        assertTrue(settings.isSkipFailedTracksEnabled());
        assertTrue(settings.isKeepScreenOnEnabled());
        assertTrue(settings.isLyricTranslationEnabled());
        assertEquals(AppSettingsPreferences.BrowseMotion.STANDARD, settings.getBrowseMotion());

        settings.setPreferredQuality(AppSettingsPreferences.AudioQuality.LOSSLESS);
        settings.setBackgroundPlaybackEnabled(false);
        settings.setSkipFailedTracksEnabled(false);
        settings.setKeepScreenOnEnabled(false);
        settings.setLyricTranslationEnabled(false);
        settings.setBrowseMotion(AppSettingsPreferences.BrowseMotion.STILL);

        assertEquals(AppSettingsPreferences.AudioQuality.LOSSLESS, settings.getPreferredQuality());
        assertFalse(settings.isBackgroundPlaybackEnabled());
        assertFalse(settings.isSkipFailedTracksEnabled());
        assertFalse(settings.isKeepScreenOnEnabled());
        assertFalse(settings.isLyricTranslationEnabled());
        assertEquals(AppSettingsPreferences.BrowseMotion.STILL, settings.getBrowseMotion());
    }

    @Test
    public void invalidStoredEnumsFallBackToApprovedDefaults() {
        InMemorySharedPreferences store = new InMemorySharedPreferences()
                .putInitial(AppSettingsPreferences.KEY_PREFERRED_QUALITY, "hires")
                .putInitial(AppSettingsPreferences.KEY_BROWSE_MOTION, "FAST");
        AppSettingsPreferences settings = new AppSettingsPreferences(store);

        assertEquals(AppSettingsPreferences.AudioQuality.QUALITY_320K,
                settings.getPreferredQuality());
        assertEquals(AppSettingsPreferences.BrowseMotion.STANDARD, settings.getBrowseMotion());
    }

    @Test
    public void migrationCopiesLegacyValuesThenRemovesOnlyMigratedKeys() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences()
                .putInitial("quality", "flac")
                .putInitial("background_play", false)
                .putInitial("server_url", "https://example.test/")
                .putInitial("username", "listener");
        InMemorySharedPreferences store = new InMemorySharedPreferences();

        assertTrue(AppSettingsPreferences.migrateLegacy(legacy, store));

        AppSettingsPreferences settings = new AppSettingsPreferences(store);
        assertEquals(AppSettingsPreferences.AudioQuality.LOSSLESS,
                settings.getPreferredQuality());
        assertFalse(settings.isBackgroundPlaybackEnabled());
        assertFalse(legacy.contains("quality"));
        assertFalse(legacy.contains("background_play"));
        assertEquals("https://example.test/", legacy.getString("server_url", ""));
        assertEquals("listener", legacy.getString("username", ""));
        assertEquals(AppSettingsPreferences.CURRENT_SCHEMA_VERSION,
                store.getInt(AppSettingsPreferences.KEY_SCHEMA_VERSION, 0));
    }

    @Test
    public void firstInstallPersistsDefaultsAndCurrentSchema() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences();
        InMemorySharedPreferences store = new InMemorySharedPreferences();

        assertTrue(AppSettingsPreferences.migrateLegacy(legacy, store));

        assertEquals("320k", store.getString(
                AppSettingsPreferences.KEY_PREFERRED_QUALITY, null));
        assertTrue(store.getBoolean(
                AppSettingsPreferences.KEY_BACKGROUND_PLAYBACK, false));
        assertTrue(store.getBoolean(
                AppSettingsPreferences.KEY_SKIP_FAILED_TRACKS, false));
        assertTrue(store.getBoolean(
                AppSettingsPreferences.KEY_KEEP_SCREEN_ON, false));
        assertTrue(store.getBoolean(
                AppSettingsPreferences.KEY_LYRIC_TRANSLATION, false));
        assertEquals(AppSettingsPreferences.BrowseMotion.STANDARD.name(),
                store.getString(AppSettingsPreferences.KEY_BROWSE_MOTION, null));
        assertEquals(AppSettingsPreferences.CURRENT_SCHEMA_VERSION,
                store.getInt(AppSettingsPreferences.KEY_SCHEMA_VERSION, 0));
    }

    @Test
    public void completedMigrationReturnsWithoutWritingAgain() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences();
        InMemorySharedPreferences store = new InMemorySharedPreferences()
                .putInitial(AppSettingsPreferences.KEY_SCHEMA_VERSION,
                        AppSettingsPreferences.CURRENT_SCHEMA_VERSION)
                .putInitial(AppSettingsPreferences.KEY_PREFERRED_QUALITY, "flac")
                .putInitial(AppSettingsPreferences.KEY_BACKGROUND_PLAYBACK, false);
        store.failNextCommit();

        assertTrue(AppSettingsPreferences.migrateLegacy(legacy, store));
        assertEquals("flac", store.getString(
                AppSettingsPreferences.KEY_PREFERRED_QUALITY, null));
        assertFalse(store.getBoolean(
                AppSettingsPreferences.KEY_BACKGROUND_PLAYBACK, true));
        assertFalse(store.edit().commit());
    }

    @Test
    public void migrationIsIdempotentAndNeverOverwritesNewValues() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences()
                .putInitial("quality", "128k")
                .putInitial("background_play", false);
        InMemorySharedPreferences store = new InMemorySharedPreferences()
                .putInitial(AppSettingsPreferences.KEY_PREFERRED_QUALITY, "flac")
                .putInitial(AppSettingsPreferences.KEY_BACKGROUND_PLAYBACK, true);

        assertTrue(AppSettingsPreferences.migrateLegacy(legacy, store));
        legacy.putInitial("quality", "128k");
        legacy.putInitial("background_play", false);
        assertTrue(AppSettingsPreferences.migrateLegacy(legacy, store));

        AppSettingsPreferences settings = new AppSettingsPreferences(store);
        assertEquals(AppSettingsPreferences.AudioQuality.LOSSLESS,
                settings.getPreferredQuality());
        assertTrue(settings.isBackgroundPlaybackEnabled());
    }

    @Test
    public void interruptedMigrationKeepsLegacyValuesAndCanRetry() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences()
                .putInitial("quality", "128k")
                .putInitial("background_play", false);
        InMemorySharedPreferences store = new InMemorySharedPreferences();
        store.failNextCommit();

        assertFalse(AppSettingsPreferences.migrateLegacy(legacy, store));
        assertTrue(legacy.contains("quality"));
        assertTrue(legacy.contains("background_play"));
        assertFalse(store.contains(AppSettingsPreferences.KEY_SCHEMA_VERSION));

        assertTrue(AppSettingsPreferences.migrateLegacy(legacy, store));
        assertEquals(AppSettingsPreferences.AudioQuality.QUALITY_128K,
                new AppSettingsPreferences(store).getPreferredQuality());
        assertFalse(legacy.contains("quality"));
        assertFalse(legacy.contains("background_play"));
    }

    @Test
    public void schemaWriteFailureLeavesLegacyValuesForRetry() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences()
                .putInitial("quality", "flac")
                .putInitial("background_play", false);
        InMemorySharedPreferences store = new InMemorySharedPreferences();
        store.failCommitNumber(2);

        assertFalse(AppSettingsPreferences.migrateLegacy(legacy, store));
        assertTrue(legacy.contains("quality"));
        assertTrue(legacy.contains("background_play"));
        assertFalse(store.contains(AppSettingsPreferences.KEY_SCHEMA_VERSION));

        assertTrue(AppSettingsPreferences.migrateLegacy(legacy, store));
        assertEquals(AppSettingsPreferences.AudioQuality.LOSSLESS,
                new AppSettingsPreferences(store).getPreferredQuality());
        assertFalse(legacy.contains("quality"));
        assertFalse(legacy.contains("background_play"));
    }

    @Test
    public void legacyCleanupFailureRetriesWithoutOverwritingNewValues() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences()
                .putInitial("quality", "128k")
                .putInitial("background_play", false);
        InMemorySharedPreferences store = new InMemorySharedPreferences()
                .putInitial(AppSettingsPreferences.KEY_PREFERRED_QUALITY, "flac")
                .putInitial(AppSettingsPreferences.KEY_BACKGROUND_PLAYBACK, true);
        legacy.failNextCommit();

        assertFalse(AppSettingsPreferences.migrateLegacy(legacy, store));
        assertEquals(AppSettingsPreferences.CURRENT_SCHEMA_VERSION,
                store.getInt(AppSettingsPreferences.KEY_SCHEMA_VERSION, 0));
        assertTrue(legacy.contains("quality"));
        assertTrue(legacy.contains("background_play"));

        assertTrue(AppSettingsPreferences.migrateLegacy(legacy, store));
        assertEquals(AppSettingsPreferences.AudioQuality.LOSSLESS,
                new AppSettingsPreferences(store).getPreferredQuality());
        assertTrue(new AppSettingsPreferences(store).isBackgroundPlaybackEnabled());
        assertFalse(legacy.contains("quality"));
        assertFalse(legacy.contains("background_play"));
    }

    @Test
    public void invalidSchemaTypeIsRepairedAndMigrationConverges() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences()
                .putInitial("quality", "128k")
                .putInitial("background_play", false);
        InMemorySharedPreferences store = new InMemorySharedPreferences()
                .putInitial(AppSettingsPreferences.KEY_SCHEMA_VERSION, "invalid")
                .putInitial(AppSettingsPreferences.KEY_PREFERRED_QUALITY, "flac")
                .putInitial(AppSettingsPreferences.KEY_BACKGROUND_PLAYBACK, true);

        assertTrue(AppSettingsPreferences.migrateLegacy(legacy, store));

        assertEquals(AppSettingsPreferences.CURRENT_SCHEMA_VERSION,
                store.getInt(AppSettingsPreferences.KEY_SCHEMA_VERSION, 0));
        assertEquals(AppSettingsPreferences.AudioQuality.LOSSLESS,
                new AppSettingsPreferences(store).getPreferredQuality());
        assertTrue(new AppSettingsPreferences(store).isBackgroundPlaybackEnabled());
        assertFalse(legacy.contains("quality"));
        assertFalse(legacy.contains("background_play"));
    }
}
