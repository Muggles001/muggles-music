package top.boluofan.musictv.ui.library;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.boluofan.musictv.R;
import top.boluofan.musictv.catalog.MusicApiProvider;
import top.boluofan.musictv.catalog.PlaylistLinkResolver;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.library.PlaylistImportActions;
import top.boluofan.musictv.library.PlaylistImportPreview;
import top.boluofan.musictv.remote.LocalIpv4Address;
import top.boluofan.musictv.remote.RemoteWebContract;
import top.boluofan.musictv.remote.playlistimport.PlaylistImportRemoteSession;
import top.boluofan.musictv.remote.playlistimport.PlaylistImportWebServer;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;
import top.boluofan.musictv.ui.background.FluidBackgroundView;
import top.boluofan.musictv.ui.common.DialogHelper;
import top.boluofan.musictv.ui.common.FocusAnimationHelper;

public final class PlaylistImportActivity extends AppCompatActivity {
    public static final String EXTRA_PLAYLIST_ID = "playlist_import_id";
    private static final String[] SOURCES = {"auto", "wy", "tx", "kw", "kg", "mg"};
    private static final int[] SOURCE_BUTTONS = {
            R.id.btnSourceAuto, R.id.btnSourceWy, R.id.btnSourceTx,
            R.id.btnSourceKw, R.id.btnSourceKg, R.id.btnSourceMg
    };

    private FluidBackgroundView fluidBackground;
    private EditText etLink;
    private ImageButton btnBack;
    private ImageButton btnClear;
    private Button btnResolve;
    private ImageButton btnScan;
    private ProgressBar progress;
    private TextView tvState;
    private View previewPanel;
    private ImageView ivCover;
    private TextView tvName;
    private TextView tvMeta;
    private TextView tvDescription;
    private Button btnConfirm;
    private Button btnCancel;
    private Button[] sourceButtons;
    private String sourceHint = "auto";
    private int selectedSourceIndex;
    private boolean keyboardRequested;
    private PlaylistImportPreview preview;
    private PlaylistImportActions actions;
    private PlaylistImportActions.Operation prepareOperation;
    private PlaylistImportActions.Operation commitOperation;
    private PlaylistImportRemoteSession remoteSession;
    private AlertDialog qrDialog;
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_import);
        bindViews();
        if (!configureActions()) return;
        configureSources();
        configureListeners();
        selectSource(0);
        sourceButtons[0].requestFocus();
    }

    private void bindViews() {
        fluidBackground = findViewById(R.id.fluidBackground);
        etLink = findViewById(R.id.etPlaylistLink);
        btnBack = findViewById(R.id.btnImportBack);
        btnClear = findViewById(R.id.btnImportClear);
        btnResolve = findViewById(R.id.btnImportResolve);
        btnScan = findViewById(R.id.btnImportScan);
        etLink.setShowSoftInputOnFocus(false);
        progress = findViewById(R.id.importProgress);
        tvState = findViewById(R.id.tvImportState);
        previewPanel = findViewById(R.id.layoutImportPreview);
        ivCover = findViewById(R.id.ivImportCover);
        tvName = findViewById(R.id.tvImportName);
        tvMeta = findViewById(R.id.tvImportMeta);
        tvDescription = findViewById(R.id.tvImportDescription);
        btnConfirm = findViewById(R.id.btnImportConfirm);
        btnCancel = findViewById(R.id.btnImportCancel);
        sourceButtons = new Button[SOURCE_BUTTONS.length];
        for (int i = 0; i < SOURCE_BUTTONS.length; i++) {
            sourceButtons[i] = findViewById(SOURCE_BUTTONS[i]);
            FocusAnimationHelper.applyFocusAnimation(sourceButtons[i]);
        }
        FocusAnimationHelper.applyFocusAnimation(btnBack, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnClear, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnResolve);
        FocusAnimationHelper.applyFocusAnimation(btnScan, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnConfirm);
        FocusAnimationHelper.applyFocusAnimation(btnCancel);
    }

    private boolean configureActions() {
        if (BackendPreferences.getMode(this) == BackendMode.NONE) {
            Toast.makeText(this, R.string.playlist_import_no_backend, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        actions = new PlaylistImportActions(
                MusicApiProvider.get(this),
                LxRetrofitClient.getUsername(this),
                LxRetrofitClient.getPassword(this),
                LxRetrofitClient.getToken(this),
                new PlaylistLinkResolver(), background, this::runOnUiThread);
        return true;
    }

    private void configureSources() {
        for (int i = 1; i < sourceButtons.length; i++) {
            sourceButtons[i].setEnabled(true);
            sourceButtons[i].setFocusable(true);
            sourceButtons[i].setAlpha(1f);
        }
        List<Integer> enabledIndexes = new ArrayList<>();
        enabledIndexes.add(0);
        for (int i = 1; i < sourceButtons.length; i++) {
            if (sourceButtons[i].isEnabled()) enabledIndexes.add(i);
        }
        for (int i = 0; i < enabledIndexes.size(); i++) {
            int index = enabledIndexes.get(i);
            sourceButtons[index].setNextFocusLeftId(i == 0
                    ? R.id.btnImportBack : SOURCE_BUTTONS[enabledIndexes.get(i - 1)]);
            sourceButtons[index].setNextFocusRightId(i + 1 < enabledIndexes.size()
                    ? SOURCE_BUTTONS[enabledIndexes.get(i + 1)] : SOURCE_BUTTONS[index]);
        }
    }

    private void configureListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnClear.setOnClickListener(v -> {
            etLink.setText("");
            clearPreview();
            etLink.requestFocus();
        });
        btnResolve.setOnClickListener(v -> resolveInput());
        btnScan.setOnClickListener(v -> showQrInput());
        btnCancel.setOnClickListener(v -> {
            clearPreview();
            etLink.requestFocus();
        });
        btnConfirm.setOnClickListener(v -> commitPreview());
        View.OnKeyListener previewDown = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN
                    || keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (previewPanel.isShown() && btnConfirm.isEnabled()) {
                return btnConfirm.requestFocus();
            }
            return true;
        };
        btnClear.setOnKeyListener(previewDown);
        btnResolve.setOnKeyListener(previewDown);
        btnScan.setOnKeyListener(previewDown);
        etLink.setOnClickListener(v -> showKeyboard());
        etLink.setOnEditorActionListener((v, actionId, event) -> {
            resolveInput();
            return true;
        });
        for (int i = 0; i < sourceButtons.length; i++) {
            final int index = i;
            sourceButtons[i].setOnClickListener(v -> selectSource(index));
        }
    }

    private void selectSource(int index) {
        selectedSourceIndex = index;
        sourceHint = SOURCES[index];
        for (int i = 0; i < sourceButtons.length; i++) {
            sourceButtons[i].setSelected(i == index);
        }
        clearPreview();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && etLink != null && etLink.hasFocus()) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (!keyboardRequested) showKeyboard();
                return true;
            }
            if (moveFocusFromInput(keyCode)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean moveFocusFromInput(int keyCode) {
        int targetId = inputFocusTarget(keyCode, previewPanel.isShown(),
                SOURCE_BUTTONS[selectedSourceIndex]);
        if (targetId == View.NO_ID) return false;
        View target = findViewById(targetId);
        if (target == null || !target.isShown() || !target.isEnabled()) return false;
        closeKeyboardForFocusMove();
        return target.requestFocus();
    }

    static int inputFocusTarget(int keyCode, boolean previewVisible,
                                int selectedSourceButtonId) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return R.id.btnImportBack;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return R.id.btnImportClear;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return selectedSourceButtonId;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return previewVisible ? R.id.btnImportConfirm : R.id.btnImportResolve;
        }
        return View.NO_ID;
    }

    private void showKeyboard() {
        keyboardRequested = true;
        etLink.setShowSoftInputOnFocus(true);
        etLink.requestFocus();
        etLink.post(() -> {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(etLink, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void resolveInput() {
        String input = etLink.getText().toString().trim();
        if (input.isEmpty()) {
            setState(getString(R.string.playlist_import_error_empty));
            etLink.requestFocus();
            return;
        }
        hideKeyboard();
        if (prepareOperation != null) prepareOperation.cancel();
        clearPreview();
        setBusy(true, getString(R.string.playlist_import_resolving));
        prepareOperation = actions.prepare(input, sourceHint,
                new PlaylistImportActions.Callback<PlaylistImportPreview>() {
                    @Override
                    public void onSuccess(PlaylistImportPreview value) {
                        if (!isUiAvailable()) return;
                        prepareOperation = null;
                        preview = value;
                        renderPreview(value);
                    }

                    @Override
                    public void onFailure(PlaylistImportActions.Failure failure) {
                        if (!isUiAvailable()) return;
                        prepareOperation = null;
                        setBusy(false, errorText(failure));
                        btnResolve.requestFocus();
                    }
                });
    }

    private void renderPreview(PlaylistImportPreview value) {
        setBusy(false, value.isRefresh()
                ? getString(R.string.playlist_import_refresh_ready)
                : getString(R.string.playlist_import_create_ready));
        previewPanel.setVisibility(View.VISIBLE);
        tvName.setText(value.getName());
        tvMeta.setText(getString(R.string.playlist_import_preview_meta,
                sourceName(value.getReference().getSource()), value.getOriginalCount(),
                value.getImportableCount(), value.getSkippedCount()));
        tvDescription.setText(value.getDescription() == null
                ? getString(R.string.playlist_import_no_description) : value.getDescription());
        Glide.with(this).load(value.getCoverUrl())
                .placeholder(R.drawable.ic_cover_placeholder)
                .error(R.drawable.ic_cover_placeholder)
                .into(ivCover);
        btnConfirm.setText(value.isRefresh()
                ? R.string.playlist_import_refresh : R.string.playlist_import_confirm);
        btnConfirm.requestFocus();
    }

    private void commitPreview() {
        if (preview == null || commitOperation != null) return;
        setBusy(true, getString(R.string.playlist_import_saving));
        btnConfirm.setEnabled(false);
        commitOperation = actions.commit(preview,
                new PlaylistImportActions.Callback<PlaylistImportActions.ImportResult>() {
                    @Override
                    public void onSuccess(PlaylistImportActions.ImportResult result) {
                        if (!isUiAvailable()) return;
                        commitOperation = null;
                        Intent data = new Intent();
                        data.putExtra(EXTRA_PLAYLIST_ID, result.getPlaylistId());
                        setResult(Activity.RESULT_OK, data);
                        Toast.makeText(PlaylistImportActivity.this,
                                getString(R.string.playlist_import_success,
                                        result.getImportedCount(), result.getSkippedCount()),
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onFailure(PlaylistImportActions.Failure failure) {
                        if (!isUiAvailable()) return;
                        commitOperation = null;
                        btnConfirm.setEnabled(true);
                        setBusy(false, errorText(failure));
                        btnConfirm.requestFocus();
                    }
                });
    }

    private void showQrInput() {
        String address = LocalIpv4Address.find();
        if (address == null) {
            setState(getString(R.string.playlist_import_no_lan));
            return;
        }
        closeRemoteSession();
        String token = randomToken();
        String url = RemoteWebContract.url(address, RemoteWebContract.PLAYLIST_IMPORT_PATH)
                + "?token=" + token;
        PlaylistImportRemoteSession session = new PlaylistImportRemoteSession(listener ->
                new PlaylistImportWebServer(
                        RemoteWebContract.PORT, token, listener::onPlaylistLink));
        remoteSession = session;
        qrDialog = DialogHelper.showQrCodeDialog(this,
                getString(R.string.playlist_import_scan_title),
                getString(R.string.playlist_import_scan_hint), url, url);
        qrDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                getString(R.string.playlist_import_close),
                (dialog, which) -> closeRemoteSession());
        qrDialog.setOnDismissListener(dialog -> closeRemoteSession());
        try {
            session.start(link -> runOnUiThread(() -> {
                if (!isUiAvailable() || remoteSession != session) return;
                remoteSession = null;
                if (qrDialog != null) qrDialog.dismiss();
                qrDialog = null;
                etLink.setText(link);
                resolveInput();
            }));
        } catch (IOException error) {
            closeRemoteSession();
            setState(getString(R.string.playlist_import_qr_failed));
            return;
        }
        qrDialog.show();
    }

    private void clearPreview() {
        preview = null;
        previewPanel.setVisibility(View.GONE);
        btnConfirm.setEnabled(true);
        setState(getString(R.string.playlist_import_ready));
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnResolve.setEnabled(!busy);
        btnScan.setEnabled(!busy);
        btnClear.setEnabled(!busy);
        for (Button sourceButton : sourceButtons) {
            if (sourceButton.getAlpha() > 0.5f) sourceButton.setEnabled(!busy);
        }
        setState(message);
    }

    private void setState(String message) {
        tvState.setText(message);
    }

    private String errorText(PlaylistImportActions.Failure failure) {
        switch (failure.getKind()) {
            case INVALID_INPUT:
                return getString(R.string.playlist_import_error_invalid);
            case NO_IMPORTABLE_SONGS:
                return getString(R.string.playlist_import_error_empty_playlist);
            case INCOMPLETE_PLAYLIST:
                return getString(R.string.playlist_import_error_incomplete);
            case READ_RESPONSE:
            case WRITE_RESPONSE:
                return getString(R.string.playlist_import_error_save);
            case DETAIL_RESPONSE:
            case TRANSPORT:
            default:
                return getString(R.string.playlist_import_error_network);
        }
    }

    private String sourceName(String source) {
        if ("wy".equals(source)) return getString(R.string.playlist_import_platform_wy);
        if ("tx".equals(source)) return getString(R.string.playlist_import_platform_tx);
        if ("kw".equals(source)) return getString(R.string.playlist_import_platform_kw);
        if ("kg".equals(source)) return getString(R.string.playlist_import_platform_kg);
        if ("mg".equals(source)) return getString(R.string.playlist_import_platform_mg);
        return source;
    }

    private static String randomToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder token = new StringBuilder(32);
        for (byte value : bytes) token.append(String.format("%02x", value & 0xff));
        return token.toString();
    }

    private void hideKeyboard() {
        closeKeyboardForFocusMove();
        etLink.clearFocus();
    }

    private void closeKeyboardForFocusMove() {
        keyboardRequested = false;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(etLink.getWindowToken(), 0);
        etLink.setShowSoftInputOnFocus(false);
    }

    private void closeRemoteSession() {
        PlaylistImportRemoteSession session = remoteSession;
        remoteSession = null;
        if (session != null) session.close();
    }

    private boolean isUiAvailable() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    public void onBackPressed() {
        if (keyboardRequested) {
            hideKeyboard();
            btnResolve.requestFocus();
            return;
        }
        if (qrDialog != null && qrDialog.isShowing()) {
            qrDialog.dismiss();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fluidBackground.resumeRendering();
    }

    @Override
    protected void onPause() {
        fluidBackground.pauseRendering();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        closeRemoteSession();
        if (prepareOperation != null) prepareOperation.cancel();
        if (commitOperation != null) commitOperation.cancel();
        background.shutdownNow();
        super.onDestroy();
    }
}
