package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.settings.AppSettingsPreferences;
import top.boluofan.musictv.settings.LyricTimingPreferences;
import top.boluofan.musictv.settings.SettingsItem;
import top.boluofan.musictv.player.session.MediaControllerConnection;
import top.boluofan.musictv.player.settings.SleepTimerContract;
import top.boluofan.musictv.source.ImportedSource;
import top.boluofan.musictv.source.SourceScriptStore;
import top.boluofan.musictv.ui.adapter.SettingsAdapter;
import top.boluofan.musictv.util.DialogHelper;

public class SettingsFragment extends Fragment implements MainActivity.PrimaryPageKeyHandler,
        SettingsAdapter.Listener {
    private RecyclerView settingsList;
    private SettingsAdapter adapter;
    private AppSettingsPreferences settings;
    private LyricTimingPreferences lyricTiming;
    private String restoreFocusKey;
    private MediaControllerConnection controllerConnection;
    private MediaController mediaController;
    private String sleepMode = SleepTimerContract.MODE_OFF;
    private int sleepRemaining;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundResource(0);
        settings = new AppSettingsPreferences(requireContext());
        lyricTiming = new LyricTimingPreferences(requireContext());
        settingsList = view.findViewById(R.id.layoutServerConfig);
        settingsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        settingsList.setItemAnimator(null);
        adapter = new SettingsAdapter(this);
        settingsList.setAdapter(adapter);
        renderRows();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter == null) return;
        renderRows();
        if (restoreFocusKey != null) {
            adapter.requestFocusByKey(settingsList, restoreFocusKey);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        connectSleepController();
    }

    @Override
    public void onStop() {
        if (controllerConnection != null) controllerConnection.disconnect();
        mediaController = null;
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        settingsList = null;
        adapter = null;
        super.onDestroyView();
    }

    @Override
    public void onItemActivated(SettingsItem item) {
        switch (item.key) {
            case "connection":
                if (BackendPreferences.getMode(requireContext()) == BackendMode.NONE) {
                    restoreFocusKey = item.key;
                    startActivity(new Intent(requireContext(), top.boluofan.musictv.ConfigActivity.class));
                } else {
                    openDetail(SettingsDetailActivity.TYPE_CONNECTION, item.key);
                }
                break;
            case "quality":
                openDetail(SettingsDetailActivity.TYPE_QUALITY, item.key);
                break;
            case "sleep_timer":
                openDetail(SettingsDetailActivity.TYPE_SLEEP_TIMER, item.key);
                break;
            case "browse_motion":
                openDetail(SettingsDetailActivity.TYPE_BROWSE_MOTION, item.key);
                break;
            case "operation_guide":
                openDetail(SettingsDetailActivity.TYPE_HELP, item.key);
                break;
            case "cache":
                openDetail(SettingsDetailActivity.TYPE_CACHE, item.key);
                break;
            case "about":
                openDetail(SettingsDetailActivity.TYPE_ABOUT, item.key);
                break;
            case "clear_lyric_timing":
                confirmClearLyricTiming(item.key);
                break;
            default:
                break;
        }
    }

    @Override
    public void onToggleChanged(SettingsItem item, boolean checked) {
        switch (item.key) {
            case "background_playback":
                settings.setBackgroundPlaybackEnabled(checked);
                break;
            case "skip_failed_tracks":
                settings.setSkipFailedTracksEnabled(checked);
                break;
            case "keep_screen_on":
                settings.setKeepScreenOnEnabled(checked);
                break;
            case "lyric_translation":
                settings.setLyricTranslationEnabled(checked);
                break;
            default:
                return;
        }
        restoreFocusKey = item.key;
        renderRows();
        adapter.requestFocusByKey(settingsList, item.key);
    }

    @Override
    public void onSegmentChanged(SettingsItem item, int selectedIndex) {
    }

    @Override
    public boolean onMoveLeft(SettingsItem item) {
        View settingsRailItem = requireActivity().findViewById(R.id.tabSettings);
        return settingsRailItem != null && settingsRailItem.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    private void renderRows() {
        adapter.submitList(buildRows());
    }

    private List<SettingsItem> buildRows() {
        List<SettingsItem> rows = new ArrayList<>();
        rows.add(SettingsItem.section("section_connection", getString(R.string.settings_section_connection)));
        rows.add(SettingsItem.route("connection", getString(R.string.settings_connection_title),
                connectionSummary(), connectionModeLabel()));

        rows.add(SettingsItem.section("section_playback", getString(R.string.settings_section_playback)));
        rows.add(SettingsItem.route("quality", getString(R.string.settings_quality_title),
                getString(R.string.settings_quality_summary), qualityLabel()));
        rows.add(SettingsItem.toggle("background_playback",
                getString(R.string.settings_background_playback_title),
                getString(R.string.settings_background_playback_summary),
                settings.isBackgroundPlaybackEnabled()));
        rows.add(SettingsItem.toggle("skip_failed_tracks",
                getString(R.string.settings_skip_failed_title),
                getString(R.string.settings_skip_failed_summary),
                settings.isSkipFailedTracksEnabled()));
        rows.add(SettingsItem.toggle("keep_screen_on",
                getString(R.string.settings_keep_screen_on_title),
                getString(R.string.settings_keep_screen_on_summary),
                settings.isKeepScreenOnEnabled()));
        rows.add(SettingsItem.route("sleep_timer", getString(R.string.settings_sleep_timer_title),
                getString(R.string.settings_sleep_timer_summary), sleepTimerSummary()));

        rows.add(SettingsItem.section("section_lyrics", getString(R.string.settings_section_lyrics)));
        rows.add(SettingsItem.toggle("lyric_translation",
                getString(R.string.settings_lyric_translation_title),
                getString(R.string.settings_lyric_translation_summary),
                settings.isLyricTranslationEnabled()));
        int timingCount = lyricTiming.count();
        rows.add(SettingsItem.action("clear_lyric_timing",
                getString(R.string.settings_clear_lyric_timing_title),
                timingCount == 0
                        ? getString(R.string.settings_no_lyric_timing)
                        : getResources().getQuantityString(
                                R.plurals.settings_lyric_timing_count, timingCount, timingCount),
                timingCount > 0));
        rows.add(SettingsItem.route("browse_motion", getString(R.string.settings_browse_motion_title),
                getString(R.string.settings_browse_motion_summary), browseMotionLabel()));

        rows.add(SettingsItem.section("section_help", getString(R.string.settings_section_help)));
        rows.add(SettingsItem.route("operation_guide", getString(R.string.settings_operation_guide_title),
                getString(R.string.settings_operation_guide_summary), null));

        rows.add(SettingsItem.section("section_storage", getString(R.string.settings_section_storage)));
        rows.add(SettingsItem.route("cache", getString(R.string.settings_cache_title),
                getString(R.string.settings_cache_summary), null));
        rows.add(SettingsItem.route("about", getString(R.string.settings_about_title),
                getString(R.string.settings_about_summary), null));
        return rows;
    }

    private String connectionModeLabel() {
        BackendMode mode = BackendPreferences.getMode(requireContext());
        if (mode == BackendMode.DIRECT_SOURCE) return getString(R.string.settings_mode_direct);
        if (mode == BackendMode.LXSERVER) return getString(R.string.settings_mode_lxserver);
        return getString(R.string.settings_not_configured);
    }

    private String connectionSummary() {
        BackendMode mode = BackendPreferences.getMode(requireContext());
        if (mode == BackendMode.DIRECT_SOURCE) {
            ImportedSource source = new SourceScriptStore(requireContext()).getActive();
            if (source == null) return getString(R.string.settings_not_configured);
            String version = source.metadata.version == null ? "" : source.metadata.version.trim();
            return version.isEmpty() ? source.metadata.name
                    : getString(R.string.settings_source_name_version, source.metadata.name, version);
        }
        if (mode == BackendMode.LXSERVER) {
            String serverUrl = LxRetrofitClient.getServerUrl(requireContext());
            String username = LxRetrofitClient.getUsername(requireContext());
            if (serverUrl.isEmpty()) return getString(R.string.settings_not_configured);
            return username.isEmpty() ? serverUrl
                    : getString(R.string.settings_server_user_summary, serverUrl, username);
        }
        return getString(R.string.settings_choose_connection_summary);
    }

    private String qualityLabel() {
        switch (settings.getPreferredQuality()) {
            case QUALITY_128K:
                return getString(R.string.settings_quality_128);
            case LOSSLESS:
                return getString(R.string.settings_quality_lossless);
            case QUALITY_320K:
            default:
                return getString(R.string.settings_quality_320);
        }
    }

    private String browseMotionLabel() {
        switch (settings.getBrowseMotion()) {
            case REDUCED:
                return getString(R.string.settings_motion_reduced);
            case STILL:
                return getString(R.string.settings_motion_still);
            case STANDARD:
            default:
                return getString(R.string.settings_motion_standard);
        }
    }

    private void openDetail(String detailType, String sourceKey) {
        restoreFocusKey = sourceKey;
        startActivity(SettingsDetailActivity.intent(requireContext(), detailType));
    }

    private void confirmClearLyricTiming(String sourceKey) {
        restoreFocusKey = sourceKey;
        DialogHelper.showConfirmDialog(requireContext(),
                getString(R.string.settings_clear_lyric_timing_dialog_title),
                getString(R.string.settings_clear_lyric_timing_dialog_message),
                getString(R.string.settings_clear), getString(R.string.settings_cancel),
                new DialogHelper.IDialogCallback() {
                    @Override
                    public void onConfirm() {
                        lyricTiming.clearAll();
                        renderRows();
                        adapter.requestFocusByKey(settingsList, sourceKey);
                    }

                    @Override
                    public void onCancel() {
                        adapter.requestFocusByKey(settingsList, sourceKey);
                    }
                });
    }

    private void connectSleepController() {
        if (controllerConnection == null) {
            controllerConnection = new MediaControllerConnection(
                    requireContext(),
                    this::isAdded,
                    resolved -> {
                        mediaController = resolved;
                        ListenableFuture<SessionResult> state = resolved.sendCustomCommand(
                                new SessionCommand(SleepTimerContract.COMMAND_GET, Bundle.EMPTY),
                                Bundle.EMPTY);
                        state.addListener(() -> {
                            try {
                                applySleepState(state.get().extras);
                            } catch (Exception ignored) {
                            }
                        }, ContextCompat.getMainExecutor(requireContext()));
                    },
                    error -> { });
        }
        controllerConnection.connect();
    }

    private void applySleepState(Bundle state) {
        SleepTimerContract.State decoded = SleepTimerContract.decode(state);
        sleepMode = decoded.mode;
        sleepRemaining = decoded.remaining;
        if (adapter != null) renderRows();
    }

    private String sleepTimerSummary() {
        if (SleepTimerContract.MODE_MINUTES.equals(sleepMode)) {
            return getString(R.string.settings_sleep_remaining_minutes, sleepRemaining);
        }
        if (SleepTimerContract.MODE_SONGS.equals(sleepMode)) {
            return getResources().getQuantityString(
                    R.plurals.settings_sleep_remaining_songs, sleepRemaining, sleepRemaining);
        }
        return getString(R.string.settings_off);
    }
}
