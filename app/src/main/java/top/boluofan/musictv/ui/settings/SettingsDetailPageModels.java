package top.boluofan.musictv.ui.settings;

import android.content.Context;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import top.boluofan.musictv.player.settings.SleepTimerContract;
import top.boluofan.musictv.R;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;
import top.boluofan.musictv.settings.AppSettingsPreferences;
import top.boluofan.musictv.settings.SettingsItem;
import top.boluofan.musictv.source.ImportedSource;
import top.boluofan.musictv.source.SourceScriptStore;

public final class SettingsDetailPageModels {
    private final Context context;
    private final AppSettingsPreferences settings;

    public SettingsDetailPageModels(Context context, AppSettingsPreferences settings) {
        this.context = context;
        this.settings = settings;
    }

    public List<SettingsItem> rowsFor(
            String detailType, String sleepMode, int sleepValue, int sleepRemaining,
            long cacheBytes) {
        switch (detailType) {
            case SettingsDetailActivity.TYPE_CONNECTION:
                return connectionRows();
            case SettingsDetailActivity.TYPE_QUALITY:
                return qualityRows();
            case SettingsDetailActivity.TYPE_SLEEP_TIMER:
                return sleepTimerRows(sleepMode, sleepValue, sleepRemaining);
            case SettingsDetailActivity.TYPE_BROWSE_MOTION:
                return motionRows();
            case SettingsDetailActivity.TYPE_HELP:
                return helpRows();
            case SettingsDetailActivity.TYPE_CACHE:
                return cacheRows(cacheBytes);
            case SettingsDetailActivity.TYPE_ABOUT:
            default:
                return aboutRows();
        }
    }

    public int titleFor(String detailType) {
        switch (detailType) {
            case SettingsDetailActivity.TYPE_CONNECTION:
                return R.string.settings_connection_title;
            case SettingsDetailActivity.TYPE_QUALITY:
                return R.string.settings_quality_title;
            case SettingsDetailActivity.TYPE_SLEEP_TIMER:
                return R.string.settings_sleep_timer_title;
            case SettingsDetailActivity.TYPE_BROWSE_MOTION:
                return R.string.settings_browse_motion_title;
            case SettingsDetailActivity.TYPE_HELP:
                return R.string.settings_operation_guide_title;
            case SettingsDetailActivity.TYPE_CACHE:
                return R.string.settings_cache_title;
            case SettingsDetailActivity.TYPE_ABOUT:
            default:
                return R.string.settings_about_title;
        }
    }

    private List<SettingsItem> connectionRows() {
        List<SettingsItem> rows = new ArrayList<>();
        BackendMode mode = BackendPreferences.getMode(context);
        rows.add(SettingsItem.section("connection_status_section",
                context.getString(R.string.settings_connection_status_section)));
        rows.add(SettingsItem.action("connection_mode",
                context.getString(R.string.settings_connection_mode), connectionModeLabel(mode), false));

        if (mode == BackendMode.DIRECT_SOURCE) addDirectSourceStatus(rows);
        else if (mode == BackendMode.LXSERVER) addLxserverStatus(rows);

        rows.add(SettingsItem.section("connection_manage_section",
                context.getString(R.string.settings_connection_manage_section)));
        rows.add(SettingsItem.route("configure_connection",
                context.getString(manageConnectionTitle(mode)),
                context.getString(manageConnectionSummary(mode)), null));
        if (mode == BackendMode.DIRECT_SOURCE) {
            SourceScriptStore store = new SourceScriptStore(context);
            rows.add(SettingsItem.action("source_rollback",
                    context.getString(R.string.settings_source_rollback),
                    context.getString(R.string.settings_source_rollback_summary), store.hasPrevious()));
        }
        if (mode != BackendMode.NONE) {
            rows.add(SettingsItem.route("switch_connection",
                    context.getString(R.string.settings_switch_connection),
                    context.getString(R.string.settings_switch_connection_summary), null));
            rows.add(SettingsItem.section("connection_data_section",
                    context.getString(R.string.settings_connection_data_section)));
            addConnectionDataAction(rows, mode);
        }
        return rows;
    }

    private void addDirectSourceStatus(List<SettingsItem> rows) {
        ImportedSource source = new SourceScriptStore(context).getActive();
        rows.add(SettingsItem.action("source_name", context.getString(R.string.settings_source_name),
                source == null ? context.getString(R.string.settings_not_configured)
                        : source.metadata.name,
                false));
        if (source != null) {
            rows.add(SettingsItem.action("source_version",
                    context.getString(R.string.settings_source_version),
                    emptyFallback(source.metadata.version), false));
            rows.add(SettingsItem.action("source_author",
                    context.getString(R.string.settings_source_author),
                    emptyFallback(source.metadata.author), false));
            rows.add(SettingsItem.action("source_description",
                    context.getString(R.string.settings_source_description),
                    emptyFallback(source.metadata.description), false));
            rows.add(SettingsItem.action("source_homepage",
                    context.getString(R.string.settings_source_homepage),
                    emptyFallback(source.metadata.homepage), false));
            rows.add(SettingsItem.action("source_import_url",
                    context.getString(R.string.settings_source_import_url),
                    emptyFallback(source.importUrl), false));
            rows.add(SettingsItem.action("source_capabilities",
                    context.getString(R.string.settings_source_capabilities),
                    sourceCapabilities(source), false));
            rows.add(SettingsItem.action("source_digest",
                    context.getString(R.string.settings_source_digest),
                    shortDigest(source.metadata.sha256), false));
        }
    }

    private void addLxserverStatus(List<SettingsItem> rows) {
        rows.add(SettingsItem.action("server_url", context.getString(R.string.settings_server_url),
                emptyFallback(LxRetrofitClient.getServerUrl(context)), false));
        rows.add(SettingsItem.action("server_user", context.getString(R.string.settings_server_user),
                emptyFallback(LxRetrofitClient.getUsername(context)), false));
        rows.add(SettingsItem.action("server_auth", context.getString(R.string.settings_server_auth),
                LxRetrofitClient.isLoggedIn(context)
                        ? context.getString(R.string.settings_server_signed_in)
                        : context.getString(R.string.settings_server_signed_out), false));
    }

    private void addConnectionDataAction(List<SettingsItem> rows, BackendMode mode) {
        if (mode == BackendMode.DIRECT_SOURCE) {
            rows.add(SettingsItem.danger("remove_source",
                    context.getString(R.string.settings_remove_source),
                    context.getString(R.string.settings_remove_source_summary),
                    new SourceScriptStore(context).getActive() != null));
        } else if (mode == BackendMode.LXSERVER) {
            rows.add(SettingsItem.danger("server_sign_out",
                    context.getString(R.string.settings_server_sign_out),
                    context.getString(R.string.settings_server_sign_out_summary),
                    LxRetrofitClient.isLoggedIn(context)));
        }
    }

    private String connectionModeLabel(BackendMode mode) {
        if (mode == BackendMode.DIRECT_SOURCE) {
            return context.getString(R.string.settings_mode_direct);
        }
        if (mode == BackendMode.LXSERVER) {
            return context.getString(R.string.settings_mode_lxserver);
        }
        return context.getString(R.string.settings_not_configured);
    }

    static int manageConnectionTitle(BackendMode mode) {
        if (mode == BackendMode.DIRECT_SOURCE) return R.string.settings_manage_source;
        if (mode == BackendMode.LXSERVER) return R.string.settings_manage_server;
        return R.string.settings_configure_connection;
    }

    static int manageConnectionSummary(BackendMode mode) {
        if (mode == BackendMode.DIRECT_SOURCE) return R.string.settings_manage_source_summary;
        if (mode == BackendMode.LXSERVER) return R.string.settings_manage_server_summary;
        return R.string.settings_configure_connection_summary;
    }

    private String sourceCapabilities(ImportedSource source) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry
                : source.capabilities.entrySet()) {
            if (!entry.getValue().isJsonNull()) names.add(entry.getKey());
        }
        return names.isEmpty() ? context.getString(R.string.settings_not_available)
                : TextUtils.join(" · ", names);
    }

    private String shortDigest(String digest) {
        if (digest == null || digest.isEmpty()) {
            return context.getString(R.string.settings_not_available);
        }
        return digest.substring(0, Math.min(16, digest.length()));
    }

    private List<SettingsItem> qualityRows() {
        List<SettingsItem> rows = new ArrayList<>();
        rows.add(SettingsItem.section("quality_section",
                context.getString(R.string.settings_quality_section)));
        rows.add(SettingsItem.segmented("quality_choice",
                context.getString(R.string.settings_quality_title),
                context.getString(R.string.settings_quality_detail_summary),
                qualityIndex(settings.getPreferredQuality()),
                context.getString(R.string.settings_quality_128),
                context.getString(R.string.settings_quality_320),
                context.getString(R.string.settings_quality_lossless)));
        return rows;
    }

    private List<SettingsItem> sleepTimerRows(
            String sleepMode, int sleepValue, int sleepRemaining) {
        List<SettingsItem> rows = new ArrayList<>();
        rows.add(SettingsItem.section("sleep_section",
                context.getString(R.string.settings_sleep_timer_title)));
        rows.add(SettingsItem.action("sleep_state",
                context.getString(R.string.settings_sleep_timer_state),
                sleepTimerSummary(sleepMode, sleepRemaining), false));
        rows.add(SettingsItem.segmented("sleep_minutes",
                context.getString(R.string.settings_sleep_minutes),
                context.getString(R.string.settings_sleep_minutes_summary),
                sleepMinuteIndex(sleepMode, sleepValue),
                context.getString(R.string.settings_off),
                context.getString(R.string.settings_sleep_30_minutes),
                context.getString(R.string.settings_sleep_60_minutes),
                context.getString(R.string.settings_sleep_90_minutes)));
        rows.add(SettingsItem.segmented("sleep_songs",
                context.getString(R.string.settings_sleep_songs),
                context.getString(R.string.settings_sleep_songs_summary),
                sleepSongIndex(sleepMode, sleepValue),
                context.getString(R.string.settings_sleep_current_song),
                context.getString(R.string.settings_sleep_3_songs),
                context.getString(R.string.settings_sleep_5_songs),
                context.getString(R.string.settings_sleep_10_songs)));
        return rows;
    }

    private String sleepTimerSummary(String sleepMode, int sleepRemaining) {
        if (SleepTimerContract.MODE_MINUTES.equals(sleepMode)) {
            return context.getString(R.string.settings_sleep_remaining_minutes, sleepRemaining);
        }
        if (SleepTimerContract.MODE_SONGS.equals(sleepMode)) {
            return context.getResources().getQuantityString(
                    R.plurals.settings_sleep_remaining_songs, sleepRemaining, sleepRemaining);
        }
        return context.getString(R.string.settings_off);
    }

    private List<SettingsItem> motionRows() {
        List<SettingsItem> rows = new ArrayList<>();
        rows.add(SettingsItem.section("motion_section",
                context.getString(R.string.settings_browse_motion_title)));
        rows.add(SettingsItem.segmented("motion_choice",
                context.getString(R.string.settings_browse_motion_title),
                context.getString(R.string.settings_motion_detail_summary),
                motionIndex(settings.getBrowseMotion()),
                context.getString(R.string.settings_motion_standard),
                context.getString(R.string.settings_motion_reduced),
                context.getString(R.string.settings_motion_still)));
        return rows;
    }

    private List<SettingsItem> helpRows() {
        List<SettingsItem> rows = new ArrayList<>();
        rows.add(SettingsItem.section("help_navigation_section",
                context.getString(R.string.settings_help_navigation_section)));
        rows.add(SettingsItem.info("help_navigation",
                context.getString(R.string.settings_help_navigation_title),
                context.getString(R.string.settings_help_navigation_body)));
        rows.add(SettingsItem.section("help_playback_section",
                context.getString(R.string.settings_help_playback_section)));
        rows.add(SettingsItem.info("help_playback",
                context.getString(R.string.settings_help_playback_title),
                context.getString(R.string.settings_help_playback_body)));
        rows.add(SettingsItem.info("help_queue",
                context.getString(R.string.settings_help_queue_title),
                context.getString(R.string.settings_help_queue_body)));
        rows.add(SettingsItem.section("help_connection_section",
                context.getString(R.string.settings_help_connection_section)));
        rows.add(SettingsItem.info("help_direct_source",
                context.getString(R.string.settings_help_direct_source_title),
                context.getString(R.string.settings_help_direct_source_body)));
        rows.add(SettingsItem.info("help_lxserver",
                context.getString(R.string.settings_help_lxserver_title),
                context.getString(R.string.settings_help_lxserver_body)));
        return rows;
    }

    private List<SettingsItem> cacheRows(long cacheBytes) {
        List<SettingsItem> rows = new ArrayList<>();
        rows.add(SettingsItem.section("cache_section", context.getString(R.string.settings_cache_title)));
        rows.add(SettingsItem.action("cache_size", context.getString(R.string.settings_cache_size),
                cacheBytes < 0 ? context.getString(R.string.settings_calculating)
                        : formatBytes(cacheBytes),
                false));
        rows.add(SettingsItem.action("clear_cache", context.getString(R.string.settings_clear_cache),
                context.getString(R.string.settings_clear_cache_summary), cacheBytes > 0));
        return rows;
    }

    private List<SettingsItem> aboutRows() {
        List<SettingsItem> rows = new ArrayList<>();
        rows.add(SettingsItem.section("about_app_section",
                context.getString(R.string.settings_about_title)));
        rows.add(SettingsItem.info("about_version", context.getString(R.string.settings_version),
                appVersionName()));
        rows.add(SettingsItem.info("about_package", context.getString(R.string.settings_package),
                context.getPackageName()));
        rows.add(SettingsItem.section("about_open_source_section",
                context.getString(R.string.settings_open_source_section)));
        rows.add(SettingsItem.info("about_project", context.getString(R.string.app_name),
                context.getString(R.string.settings_project_attribution)));
        rows.add(SettingsItem.info("about_project_url",
                context.getString(R.string.settings_project_url),
                context.getString(R.string.settings_project_url_value)));
        rows.add(SettingsItem.info("about_upstream", context.getString(R.string.settings_upstream),
                context.getString(R.string.settings_upstream_attribution)));
        rows.add(SettingsItem.info("about_license", context.getString(R.string.settings_license),
                context.getString(R.string.settings_license_value)));
        return rows;
    }

    static int qualityIndex(AppSettingsPreferences.AudioQuality quality) {
        switch (quality) {
            case QUALITY_128K:
                return 0;
            case LOSSLESS:
                return 2;
            case QUALITY_320K:
            default:
                return 1;
        }
    }

    static int motionIndex(AppSettingsPreferences.BrowseMotion motion) {
        switch (motion) {
            case REDUCED:
                return 1;
            case STILL:
                return 2;
            case STANDARD:
            default:
                return 0;
        }
    }

    static int sleepMinuteIndex(String sleepMode, int sleepValue) {
        if (SleepTimerContract.MODE_OFF.equals(sleepMode)) return 0;
        if (!SleepTimerContract.MODE_MINUTES.equals(sleepMode)) return -1;
        if (sleepValue == 30) return 1;
        if (sleepValue == 60) return 2;
        if (sleepValue == 90) return 3;
        return -1;
    }

    static int sleepSongIndex(String sleepMode, int sleepValue) {
        if (!SleepTimerContract.MODE_SONGS.equals(sleepMode)) return -1;
        if (sleepValue == 1) return 0;
        if (sleepValue == 3) return 1;
        if (sleepValue == 5) return 2;
        if (sleepValue == 10) return 3;
        return -1;
    }

    private String emptyFallback(String value) {
        return value == null || value.trim().isEmpty()
                ? context.getString(R.string.settings_not_available) : value;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return context.getString(R.string.settings_bytes, bytes);
        double kib = bytes / 1024d;
        if (kib < 1024d) return String.format(Locale.getDefault(), "%.1f KB", kib);
        return String.format(Locale.getDefault(), "%.1f MB", kib / 1024d);
    }

    private String appVersionName() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return context.getString(R.string.settings_not_available);
        }
    }
}
