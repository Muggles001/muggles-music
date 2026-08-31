package top.boluofan.musictv.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.util.concurrent.ListenableFuture;
import com.bumptech.glide.Glide;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import top.boluofan.musictv.R;
import top.boluofan.musictv.settings.AppCacheManager;
import top.boluofan.musictv.settings.AppSettingsPreferences;
import top.boluofan.musictv.settings.LxServerAccountActions;
import top.boluofan.musictv.source.SourceSettingsMutations;
import top.boluofan.musictv.settings.SettingsItem;
import top.boluofan.musictv.player.session.MediaControllerConnection;
import top.boluofan.musictv.player.settings.SleepTimerContract;
import top.boluofan.musictv.source.SourceRuntimeManager;
import top.boluofan.musictv.ui.settings.SettingsAdapter;
import top.boluofan.musictv.ui.background.FluidBackgroundView;
import top.boluofan.musictv.ui.common.DialogHelper;
import top.boluofan.musictv.ui.common.FocusAnimationHelper;

public final class SettingsDetailActivity extends AppCompatActivity
        implements SettingsAdapter.Listener {
    public static final String TYPE_CONNECTION = "connection";
    public static final String TYPE_QUALITY = "quality";
    public static final String TYPE_SLEEP_TIMER = "sleep_timer";
    public static final String TYPE_BROWSE_MOTION = "browse_motion";
    public static final String TYPE_HELP = "help";
    public static final String TYPE_CACHE = "cache";
    public static final String TYPE_ABOUT = "about";

    private static final String EXTRA_DETAIL_TYPE = "settings_detail_type";
    private final ExecutorService cacheExecutor = Executors.newSingleThreadExecutor();
    private RecyclerView list;
    private SettingsAdapter adapter;
    private AppSettingsPreferences settings;
    private SettingsDetailPageModels pageModels;
    private FluidBackgroundView fluidBackground;
    private String detailType;
    private long cacheBytes = -1L;
    private MediaControllerConnection controllerConnection;
    private MediaController mediaController;
    private String sleepMode = SleepTimerContract.MODE_OFF;
    private int sleepValue;
    private int sleepRemaining;

    public static Intent intent(Context context, String detailType) {
        return new Intent(context, SettingsDetailActivity.class)
                .putExtra(EXTRA_DETAIL_TYPE, detailType);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        detailType = getIntent().getStringExtra(EXTRA_DETAIL_TYPE);
        if (detailType == null) detailType = TYPE_ABOUT;
        setContentView(R.layout.activity_settings_detail);
        settings = new AppSettingsPreferences(this);
        pageModels = new SettingsDetailPageModels(this, settings);
        fluidBackground = findViewById(R.id.fluidBackground);

        TextView title = findViewById(R.id.tvTitle);
        title.setText(pageModels.titleFor(detailType));
        ImageButton back = findViewById(R.id.btnBack);
        back.setOnClickListener(view -> finish());
        FocusAnimationHelper.applyFocusAnimation(back, FocusAnimationHelper.Profile.ICON);

        list = findViewById(R.id.rvSettingsDetail);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setItemAnimator(null);
        adapter = new SettingsAdapter(this);
        adapter.setTopBoundaryFocusTarget(back);
        list.setAdapter(adapter);
        back.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN
                    || (keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                    && keyCode != KeyEvent.KEYCODE_DPAD_DOWN)) {
                return false;
            }
            adapter.requestFirstInteractiveFocus(list);
            return true;
        });
        renderRows(null);
        if (TYPE_CACHE.equals(detailType)) measureCache();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fluidBackground != null) fluidBackground.resumeRendering();
        if (adapter != null) renderRows(null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (TYPE_SLEEP_TIMER.equals(detailType)) connectSleepController();
    }

    @Override
    protected void onStop() {
        if (controllerConnection != null) controllerConnection.disconnect();
        mediaController = null;
        super.onStop();
    }

    @Override
    protected void onPause() {
        if (fluidBackground != null) fluidBackground.pauseRendering();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cacheExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onItemActivated(SettingsItem item) {
        switch (item.key) {
            case "configure_connection":
                launchConnectionSetup(false);
                break;
            case "switch_connection":
                launchConnectionSetup(true);
                break;
            case "source_rollback":
                rollbackSource(item.key);
                break;
            case "remove_source":
                confirmRemoveSource(item.key);
                break;
            case "server_sign_out":
                confirmServerSignOut(item.key);
                break;
            case "clear_cache":
                confirmClearCache(item.key);
                break;
            default:
                break;
        }
    }

    @Override
    public void onToggleChanged(SettingsItem item, boolean checked) {
    }

    @Override
    public void onSegmentChanged(SettingsItem item, int selectedIndex) {
        if ("quality_choice".equals(item.key)) {
            AppSettingsPreferences.AudioQuality[] values = {
                    AppSettingsPreferences.AudioQuality.QUALITY_128K,
                    AppSettingsPreferences.AudioQuality.QUALITY_320K,
                    AppSettingsPreferences.AudioQuality.LOSSLESS
            };
            if (selectedIndex >= 0 && selectedIndex < values.length) {
                settings.setPreferredQuality(values[selectedIndex]);
            }
        } else if ("motion_choice".equals(item.key)) {
            AppSettingsPreferences.BrowseMotion[] values = {
                    AppSettingsPreferences.BrowseMotion.STANDARD,
                    AppSettingsPreferences.BrowseMotion.REDUCED,
                    AppSettingsPreferences.BrowseMotion.STILL
            };
            if (selectedIndex >= 0 && selectedIndex < values.length) {
                settings.setBrowseMotion(values[selectedIndex]);
            }
        } else if ("sleep_minutes".equals(item.key)) {
            int[] values = {0, 30, 60, 90};
            if (selectedIndex >= 0 && selectedIndex < values.length) {
                setSleepTimer(values[selectedIndex] == 0
                        ? SleepTimerContract.MODE_OFF : SleepTimerContract.MODE_MINUTES,
                        values[selectedIndex]);
            }
            return;
        } else if ("sleep_songs".equals(item.key)) {
            int[] values = {1, 3, 5, 10};
            if (selectedIndex >= 0 && selectedIndex < values.length) {
                setSleepTimer(SleepTimerContract.MODE_SONGS, values[selectedIndex]);
            }
            return;
        }
        renderRows(item.key);
    }

    @Override
    public boolean onMoveLeft(SettingsItem item) {
        return findViewById(R.id.btnBack).requestFocus();
    }

    private void renderRows(@Nullable String focusKey) {
        adapter.submitList(pageModels.rowsFor(
                detailType, sleepMode, sleepValue, sleepRemaining, cacheBytes));
        if (focusKey != null) adapter.requestFocusByKey(list, focusKey);
        else adapter.requestFirstInteractiveFocus(list);
    }

    private void launchConnectionSetup(boolean switchMode) {
        startActivity(ConnectionSettingsActions.createSetupIntent(this, switchMode));
    }

    private void rollbackSource(String focusKey) {
        if (!SourceSettingsMutations.rollback(this)) {
            Toast.makeText(this, R.string.settings_source_rollback_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.settings_source_rollback_done, Toast.LENGTH_SHORT).show();
        renderRows(focusKey);
    }

    private void confirmRemoveSource(String focusKey) {
        DialogHelper.showConfirmDialog(this, getString(R.string.settings_remove_source),
                getString(R.string.settings_remove_source_dialog),
                getString(R.string.settings_remove), getString(R.string.settings_cancel),
                new DialogHelper.IDialogCallback() {
                    @Override
                    public void onConfirm() {
                        if (!SourceSettingsMutations.remove(
                                SettingsDetailActivity.this)) return;
                        launchConnectionSetup(false);
                        finish();
                    }

                    @Override
                    public void onCancel() {
                        adapter.requestFocusByKey(list, focusKey);
                    }
                });
    }

    private void confirmServerSignOut(String focusKey) {
        DialogHelper.showConfirmDialog(this, getString(R.string.settings_server_sign_out),
                getString(R.string.settings_server_sign_out_dialog),
                getString(R.string.settings_sign_out), getString(R.string.settings_cancel),
                new DialogHelper.IDialogCallback() {
                    @Override
                    public void onConfirm() {
                        LxServerAccountActions.signOut(SettingsDetailActivity.this);
                        Toast.makeText(SettingsDetailActivity.this,
                                R.string.settings_server_signed_out, Toast.LENGTH_SHORT).show();
                        renderRows(focusKey);
                    }

                    @Override
                    public void onCancel() {
                        adapter.requestFocusByKey(list, focusKey);
                    }
                });
    }

    private void connectSleepController() {
        if (controllerConnection == null) {
            controllerConnection = new MediaControllerConnection(
                    this,
                    () -> !isFinishing() && !isDestroyed(),
                    resolved -> {
                        mediaController = resolved;
                        requestSleepTimerState();
                    },
                    error -> Toast.makeText(this, R.string.settings_sleep_unavailable,
                            Toast.LENGTH_SHORT).show());
        }
        controllerConnection.connect();
    }

    private void requestSleepTimerState() {
        sendSleepCommand(SleepTimerContract.COMMAND_GET, Bundle.EMPTY, null);
    }

    private void setSleepTimer(String mode, int value) {
        Bundle args = SleepTimerContract.encode(new SleepTimerContract.State(mode, value, 0));
        sendSleepCommand(SleepTimerContract.COMMAND_SET, args, mode);
    }

    private void sendSleepCommand(String action, Bundle args, @Nullable String optimisticMode) {
        if (mediaController == null) {
            Toast.makeText(this, R.string.settings_sleep_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        ListenableFuture<SessionResult> future = mediaController.sendCustomCommand(
                new SessionCommand(action, Bundle.EMPTY), args);
        future.addListener(() -> {
            try {
                SessionResult result = future.get();
                if (result.resultCode != SessionResult.RESULT_SUCCESS) throw new IllegalStateException();
                applySleepState(result.extras);
            } catch (Exception error) {
                Toast.makeText(this, R.string.settings_sleep_update_failed, Toast.LENGTH_SHORT).show();
                if (optimisticMode != null) requestSleepTimerState();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void applySleepState(Bundle state) {
        SleepTimerContract.State decoded = SleepTimerContract.decode(state);
        sleepMode = decoded.mode;
        sleepValue = decoded.value;
        sleepRemaining = decoded.remaining;
        renderRows(null);
    }

    private void measureCache() {
        cacheExecutor.execute(() -> {
            long measured = AppCacheManager.measure(
                    SourceRuntimeManager.temporaryDirectory(this), Glide.getPhotoCacheDir(this));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                cacheBytes = measured;
                renderRows("clear_cache");
            });
        });
    }

    private void confirmClearCache(String focusKey) {
        DialogHelper.showConfirmDialog(this, getString(R.string.settings_clear_cache_dialog_title),
                getString(R.string.settings_clear_cache_dialog_message),
                getString(R.string.settings_clear), getString(R.string.settings_cancel),
                new DialogHelper.IDialogCallback() {
                    @Override
                    public void onConfirm() {
                        clearCache(focusKey);
                    }

                    @Override
                    public void onCancel() {
                        adapter.requestFocusByKey(list, focusKey);
                    }
                });
    }

    private void clearCache(String focusKey) {
        cacheExecutor.execute(() -> {
            File runtimeCache = SourceRuntimeManager.temporaryDirectory(this);
            File imageCache = Glide.getPhotoCacheDir(this);
            SourceRuntimeManager.get(this).clearTemporaryFiles();
            Glide.get(getApplicationContext()).clearDiskCache();
            long remaining = AppCacheManager.measure(runtimeCache, imageCache);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                cacheBytes = remaining;
                Toast.makeText(this, remaining == 0L
                        ? R.string.settings_cache_cleared : R.string.settings_cache_clear_incomplete,
                        Toast.LENGTH_SHORT).show();
                renderRows(focusKey);
            });
        });
    }

}
