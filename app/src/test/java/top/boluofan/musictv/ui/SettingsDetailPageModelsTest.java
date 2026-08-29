package top.boluofan.musictv.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import top.boluofan.musictv.R;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.player.settings.SleepTimerContract;
import top.boluofan.musictv.settings.AppSettingsPreferences;

public class SettingsDetailPageModelsTest {
    @Test
    public void connectionManagementCopyMatchesEveryBackendMode() {
        assertEquals(R.string.settings_configure_connection,
                SettingsDetailPageModels.manageConnectionTitle(BackendMode.NONE));
        assertEquals(R.string.settings_configure_connection_summary,
                SettingsDetailPageModels.manageConnectionSummary(BackendMode.NONE));

        assertEquals(R.string.settings_manage_source,
                SettingsDetailPageModels.manageConnectionTitle(BackendMode.DIRECT_SOURCE));
        assertEquals(R.string.settings_manage_source_summary,
                SettingsDetailPageModels.manageConnectionSummary(BackendMode.DIRECT_SOURCE));

        assertEquals(R.string.settings_manage_server,
                SettingsDetailPageModels.manageConnectionTitle(BackendMode.LXSERVER));
        assertEquals(R.string.settings_manage_server_summary,
                SettingsDetailPageModels.manageConnectionSummary(BackendMode.LXSERVER));
    }

    @Test
    public void preferenceIndexesMatchSegmentOrder() {
        assertEquals(0, SettingsDetailPageModels.qualityIndex(
                AppSettingsPreferences.AudioQuality.QUALITY_128K));
        assertEquals(1, SettingsDetailPageModels.qualityIndex(
                AppSettingsPreferences.AudioQuality.QUALITY_320K));
        assertEquals(2, SettingsDetailPageModels.qualityIndex(
                AppSettingsPreferences.AudioQuality.LOSSLESS));

        assertEquals(0, SettingsDetailPageModels.motionIndex(
                AppSettingsPreferences.BrowseMotion.STANDARD));
        assertEquals(1, SettingsDetailPageModels.motionIndex(
                AppSettingsPreferences.BrowseMotion.REDUCED));
        assertEquals(2, SettingsDetailPageModels.motionIndex(
                AppSettingsPreferences.BrowseMotion.STILL));
    }

    @Test
    public void sleepIndexesMatchSegmentOrder() {
        assertEquals(0, SettingsDetailPageModels.sleepMinuteIndex(
                SleepTimerContract.MODE_OFF, 0));
        assertEquals(1, SettingsDetailPageModels.sleepMinuteIndex(
                SleepTimerContract.MODE_MINUTES, 30));
        assertEquals(2, SettingsDetailPageModels.sleepMinuteIndex(
                SleepTimerContract.MODE_MINUTES, 60));
        assertEquals(3, SettingsDetailPageModels.sleepMinuteIndex(
                SleepTimerContract.MODE_MINUTES, 90));
        assertEquals(-1, SettingsDetailPageModels.sleepMinuteIndex(
                SleepTimerContract.MODE_SONGS, 3));

        assertEquals(0, SettingsDetailPageModels.sleepSongIndex(
                SleepTimerContract.MODE_SONGS, 1));
        assertEquals(1, SettingsDetailPageModels.sleepSongIndex(
                SleepTimerContract.MODE_SONGS, 3));
        assertEquals(2, SettingsDetailPageModels.sleepSongIndex(
                SleepTimerContract.MODE_SONGS, 5));
        assertEquals(3, SettingsDetailPageModels.sleepSongIndex(
                SleepTimerContract.MODE_SONGS, 10));
        assertEquals(-1, SettingsDetailPageModels.sleepSongIndex(
                SleepTimerContract.MODE_MINUTES, 30));
    }
}
