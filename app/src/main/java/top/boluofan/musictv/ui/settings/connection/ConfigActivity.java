package top.boluofan.musictv.ui.settings.connection;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import top.boluofan.musictv.R;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.lxserver.LxServerConnectionActions;
import top.boluofan.musictv.player.service.MusicService;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;
import top.boluofan.musictv.remote.LocalIpv4Address;
import top.boluofan.musictv.remote.RemoteWebContract;
import top.boluofan.musictv.remote.login.LoginWebServer;
import top.boluofan.musictv.remote.login.LoginRemoteSession;
import top.boluofan.musictv.remote.sourceimport.SourceImportWebServer;
import top.boluofan.musictv.remote.sourceimport.SourceImportRemoteSession;
import top.boluofan.musictv.source.ImportedSource;
import top.boluofan.musictv.source.SourceImportActions;
import top.boluofan.musictv.ui.background.FluidBackgroundView;
import top.boluofan.musictv.ui.common.DialogHelper;

public class ConfigActivity extends AppCompatActivity {
    public static final String EXTRA_INITIAL_MODE = "initial_backend_mode";
    private static final String TAG = "ConfigActivity";
    private LoginRemoteSession loginRemoteSession;
    private SourceImportRemoteSession sourceImportSession;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private SourceImportActions sourceImportActions;
    private boolean isQrMode = false;
    private BackendMode selectedMode = BackendMode.NONE;
    
    private EditText etUrl;
    private EditText etUsername;
    private EditText etPassword;
    private EditText etToken;
    private Button btnConnect;
    private View layoutManual;
    private View layoutQr;
    private View layoutDirect;
    private Button btnToggleMode;
    private Button btnModeDirect;
    private Button btnModeServer;
    private Button btnImportSource;
    private Button btnRollbackSource;
    private EditText etSourceUrl;
    private TextView tvSourceStatus;
    private FluidBackgroundView fluidBackground;
    private BackendMode initialBackendMode = BackendMode.DIRECT_SOURCE;
    private LxServerConnectionActions serverConnectionActions;
    private LxServerConnectionActions.Operation activeConnectOperation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        serverConnectionActions = new LxServerConnectionActions(this);
        sourceImportActions = new SourceImportActions(this);

        etUrl = findViewById(R.id.etUrl);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etToken = findViewById(R.id.etToken);
        btnConnect = findViewById(R.id.btnConnect);
        ImageView ivQrCode = findViewById(R.id.ivQrCode);
        TextView tvIpAddress = findViewById(R.id.tvIpAddress);
        layoutManual = findViewById(R.id.layoutManual);
        layoutQr = findViewById(R.id.layoutQr);
        layoutDirect = findViewById(R.id.layoutDirect);
        btnToggleMode = findViewById(R.id.btnToggleMode);
        btnModeDirect = findViewById(R.id.btnModeDirect);
        btnModeServer = findViewById(R.id.btnModeServer);
        btnImportSource = findViewById(R.id.btnImportSource);
        btnRollbackSource = findViewById(R.id.btnRollbackSource);
        etSourceUrl = findViewById(R.id.etSourceUrl);
        tvSourceStatus = findViewById(R.id.tvSourceStatus);
        fluidBackground = findViewById(R.id.fluidBackground);
        installFocusBoundaryHandlers();

        etUrl.setText("");
        
        String serverUrlFromSettings = getIntent().getStringExtra("server_url");
        String usernameFromSettings = getIntent().getStringExtra("username");
        
        if (serverUrlFromSettings != null && !serverUrlFromSettings.isEmpty()) {
            etUrl.setText(serverUrlFromSettings);
        }
        
        if (usernameFromSettings != null && !usernameFromSettings.isEmpty()) {
            etUsername.setText(usernameFromSettings);
        }
        
        if (LxRetrofitClient.isLoggedIn(this)) {
            String savedPassword = LxRetrofitClient.getPassword(this);
            if (!savedPassword.isEmpty()) {
                etPassword.setText(savedPassword);
            }
        }

        String savedToken = LxRetrofitClient.getToken(this);
        if (!savedToken.isEmpty()) {
            etToken.setText(savedToken);
        }

        ImportedSource activeSource = sourceImportActions.getActiveSource();
        if (activeSource != null) {
            etSourceUrl.setText(activeSource.importUrl);
            tvSourceStatus.setText(activeSource.metadata.name + " · "
                    + (activeSource.metadata.version.isEmpty() ? "版本未知" : activeSource.metadata.version));
        }
        updateRollbackVisibility();

        btnModeDirect.setOnClickListener(v -> {
            cancelServerConnection();
            selectBackendMode(BackendMode.DIRECT_SOURCE);
        });
        btnModeServer.setOnClickListener(v -> selectBackendMode(BackendMode.LXSERVER));
        BackendMode requestedMode = requestedInitialMode();
        initialBackendMode = requestedMode;
        if (requestedMode == BackendMode.LXSERVER) selectBackendMode(BackendMode.LXSERVER);
        else selectBackendMode(BackendMode.DIRECT_SOURCE);
        View initialFocus = requestedMode == BackendMode.LXSERVER ? btnModeServer : btnModeDirect;
        initialFocus.post(initialFocus::requestFocus);

        btnToggleMode.setOnClickListener(v -> {
            if (selectedMode == BackendMode.DIRECT_SOURCE) {
                showSourceQrCodeDialog();
                return;
            }
            isQrMode = !isQrMode;
            if (isQrMode) {
                btnToggleMode.setText("返回手动输入");
                layoutManual.setVisibility(View.GONE);
                layoutQr.setVisibility(View.VISIBLE);
                setServerFormFocusable(false);
                showQrCodeDialog();
            } else {
                leaveQrMode();
                stopLoginSession();
            }
        });

        View.OnClickListener clickToShowKeyboard = v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(v, InputMethodManager.SHOW_FORCED);
            }
        };

        etUrl.setOnClickListener(clickToShowKeyboard);
        etUsername.setOnClickListener(clickToShowKeyboard);
        etPassword.setOnClickListener(clickToShowKeyboard);
        etToken.setOnClickListener(clickToShowKeyboard);
        etSourceUrl.setOnClickListener(clickToShowKeyboard);

        btnImportSource.setOnClickListener(v -> importDirectSource());
        btnRollbackSource.setOnClickListener(v -> rollbackSource());

        btnConnect.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

            String urlRaw = etUrl.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String token = etToken.getText().toString().trim();

            if (urlRaw.isEmpty()) {
                Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
                return;
            }

            String finalUrl = LxRetrofitClient.normalizeServerUrl(urlRaw);
            if (finalUrl == null) {
                Toast.makeText(this, "服务器地址格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }

            connectToServer(finalUrl, username, password, token);
        });
    }

    private BackendMode requestedInitialMode() {
        String requested = getIntent().getStringExtra(EXTRA_INITIAL_MODE);
        if (requested != null) {
            try {
                BackendMode mode = BackendMode.valueOf(requested);
                if (mode != BackendMode.NONE) return mode;
            } catch (IllegalArgumentException ignored) {
            }
        }
        BackendMode saved = BackendPreferences.getMode(this);
        return saved == BackendMode.LXSERVER ? BackendMode.LXSERVER : BackendMode.DIRECT_SOURCE;
    }

    private void connectToServer(String url, String username, String password, String token) {
        cancelServerConnection();
        btnConnect.setEnabled(false);
        btnConnect.setText("正在验证服务器...");
        activeConnectOperation = serverConnectionActions.connect(
                url, username, password, token, new LxServerConnectionActions.Callback() {
            @Override
            public void onSuccess(String resolvedToken, String message) {
                activeConnectOperation = null;
                if (!isActivityUsable()) return;
                completeServerConnection(url, username, password, resolvedToken, message);
            }

            @Override
            public void onFailure(String message) {
                activeConnectOperation = null;
                showServerConnectionError(message);
            }
        });
    }

    private void completeServerConnection(String url, String username, String password,
                                          String token, String message) {
        try {
            LxRetrofitClient.saveConfig(this, url, username, password, token);
            BackendPreferences.setMode(this, BackendMode.LXSERVER);
        } catch (RuntimeException error) {
            showServerConnectionError(error.getMessage());
            return;
        }
        btnConnect.setEnabled(true);
        btnConnect.setText("连　接");
        clearPlaybackQueue();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, top.boluofan.musictv.ui.main.MainActivity.class));
        finish();
    }

    private void showServerConnectionError(String message) {
        if (!isActivityUsable()) return;
        btnConnect.setEnabled(true);
        btnConnect.setText("重新连接");
        Toast.makeText(this, "连接失败：" + (message == null ? "未知错误" : message),
                Toast.LENGTH_LONG).show();
    }

    private void cancelServerConnection() {
        LxServerConnectionActions.Operation operation = activeConnectOperation;
        activeConnectOperation = null;
        if (operation != null) operation.cancel();
        if (btnConnect != null) {
            btnConnect.setEnabled(true);
            btnConnect.setText("连　接");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && getCurrentFocus() == null) {
            View initialFocus = initialBackendMode == BackendMode.LXSERVER
                    ? btnModeServer : btnModeDirect;
            initialFocus.setFocusableInTouchMode(true);
            initialFocus.requestFocus();
        }
    }

    private void selectBackendMode(BackendMode mode) {
        selectedMode = mode;
        initialBackendMode = mode;
        isQrMode = false;
        boolean direct = mode == BackendMode.DIRECT_SOURCE;
        layoutDirect.setVisibility(direct ? View.VISIBLE : View.GONE);
        layoutManual.setVisibility(direct ? View.GONE : View.VISIBLE);
        layoutQr.setVisibility(View.GONE);
        setServerFormFocusable(!direct);
        btnToggleMode.setVisibility(View.VISIBLE);
        btnToggleMode.setText(direct ? "扫码导入音源" : "扫码配置");
        btnModeDirect.setSelected(direct);
        btnModeServer.setSelected(!direct);
        updateVisibleFocusGraph();
    }

    private void setServerFormFocusable(boolean focusable) {
        View[] views = {etUrl, etUsername, etPassword, etToken, btnConnect};
        for (View view : views) {
            view.setFocusable(focusable);
            if (view instanceof EditText) view.setFocusableInTouchMode(focusable);
        }
    }

    private void leaveQrMode() {
        isQrMode = false;
        btnToggleMode.setText("扫码配置");
        layoutManual.setVisibility(View.VISIBLE);
        layoutQr.setVisibility(View.GONE);
        setServerFormFocusable(true);
        updateVisibleFocusGraph();
    }

    private void updateVisibleFocusGraph() {
        boolean direct = selectedMode == BackendMode.DIRECT_SOURCE;
        int selectedModeId = direct ? R.id.btnModeDirect : R.id.btnModeServer;
        int firstFieldId = direct ? R.id.etSourceUrl : R.id.etUrl;
        btnToggleMode.setNextFocusRightId(selectedModeId);
        btnModeDirect.setNextFocusDownId(firstFieldId);
        btnModeServer.setNextFocusDownId(firstFieldId);

        if (direct) {
            etSourceUrl.setNextFocusUpId(R.id.btnModeDirect);
            etSourceUrl.setNextFocusDownId(R.id.btnImportSource);
            btnImportSource.setNextFocusUpId(R.id.etSourceUrl);
            btnImportSource.setNextFocusDownId(btnRollbackSource.getVisibility() == View.VISIBLE
                    ? R.id.btnRollbackSource : R.id.btnImportSource);
            btnRollbackSource.setNextFocusUpId(R.id.btnImportSource);
            btnRollbackSource.setNextFocusDownId(R.id.btnRollbackSource);
        } else if (!isQrMode) {
            etUrl.setNextFocusUpId(R.id.btnModeServer);
            etUrl.setNextFocusDownId(R.id.etUsername);
            etUsername.setNextFocusUpId(R.id.etUrl);
            etUsername.setNextFocusDownId(R.id.etPassword);
            etPassword.setNextFocusUpId(R.id.etUsername);
            etPassword.setNextFocusDownId(R.id.etToken);
            etToken.setNextFocusUpId(R.id.etPassword);
            etToken.setNextFocusDownId(R.id.btnConnect);
            btnConnect.setNextFocusUpId(R.id.etToken);
            btnConnect.setNextFocusDownId(R.id.btnConnect);
        }
    }

    private void installFocusBoundaryHandlers() {
        bindVerticalFocus(etSourceUrl, R.id.btnModeDirect, R.id.btnImportSource);
        bindVerticalFocus(etUrl, R.id.btnModeServer, R.id.etUsername);
        bindVerticalFocus(etUsername, R.id.etUrl, R.id.etPassword);
        bindVerticalFocus(etPassword, R.id.etUsername, R.id.etToken);
        bindVerticalFocus(etToken, R.id.etPassword, R.id.btnConnect);
        btnImportSource.setOnKeyListener((view, keyCode, event) ->
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && btnRollbackSource.getVisibility() != View.VISIBLE);
        btnRollbackSource.setOnKeyListener((view, keyCode, event) ->
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        && event.getAction() == KeyEvent.ACTION_DOWN);
        btnConnect.setOnKeyListener((view, keyCode, event) ->
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        && event.getAction() == KeyEvent.ACTION_DOWN);
    }

    private void bindVerticalFocus(EditText field, int upId, int downId) {
        field.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int targetId;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) targetId = upId;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) targetId = downId;
            else return false;
            View target = findViewById(targetId);
            return target != null && target.isShown() && target.isEnabled()
                    && target.requestFocus();
        });
    }

    private void showSourceQrCodeDialog() {
        String ipAddress = LocalIpv4Address.find();
        if (ipAddress == null) {
            Toast.makeText(this, "无法获取局域网地址，请检查网络", Toast.LENGTH_SHORT).show();
            return;
        }
        stopLoginSession();
        stopSourceImportSession();
        String importUrl = RemoteWebContract.url(
                ipAddress, RemoteWebContract.SOURCE_IMPORT_PATH);
        AlertDialog dialog = DialogHelper.showQrCodeDialog(
                this,
                "扫码导入 LX 音源",
                "在手机页面粘贴落雪兼容音源脚本地址",
                importUrl,
                "访问地址: " + importUrl
        );
        SourceImportRemoteSession session = new SourceImportRemoteSession(listener ->
                new SourceImportWebServer(RemoteWebContract.PORT, listener::onSourceUrl));
        sourceImportSession = session;
        try {
            session.start(sourceUrl -> mainHandler.post(() -> {
                if (!isActivityUsable() || sourceImportSession != session) return;
                sourceImportSession = null;
                dialog.dismiss();
                etSourceUrl.setText(sourceUrl);
                btnImportSource.performClick();
            }));
        } catch (IOException error) {
            stopSourceImportSession(session);
            Toast.makeText(this, "扫码服务启动失败: " + error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        dialog.setOnDismissListener(ignored -> stopSourceImportSession(session));
        dialog.show();
    }

    private void stopSourceImportSession(SourceImportRemoteSession session) {
        if (sourceImportSession != session) return;
        session.close();
        sourceImportSession = null;
    }

    private void stopSourceImportSession() {
        if (sourceImportSession == null) return;
        sourceImportSession.close();
        sourceImportSession = null;
    }

    private void importDirectSource() {
        String url = etSourceUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入 LX 自定义音源脚本地址", Toast.LENGTH_SHORT).show();
            return;
        }
        btnImportSource.setEnabled(false);
        btnImportSource.setText("正在下载并检测...");
        tvSourceStatus.setText("正在验证脚本格式和运行能力");
        sourceImportActions.importSource(url, new SourceImportActions.Callback() {
            @Override
            public void onSuccess(ImportedSource downloaded,
                                  com.google.gson.JsonObject capabilities) {
                mainHandler.post(() -> {
                    if (!isActivityUsable()) return;
                    BackendPreferences.setMode(ConfigActivity.this, BackendMode.DIRECT_SOURCE);
                    clearPlaybackQueue();
                    btnImportSource.setEnabled(true);
                    btnImportSource.setText("下载、检测并启用");
                    tvSourceStatus.setText(downloaded.metadata.name + " · 已启用 · 支持 "
                            + capabilities.keySet().toString());
                    updateRollbackVisibility();
                    Toast.makeText(ConfigActivity.this, "音源已启用", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(ConfigActivity.this,
                            top.boluofan.musictv.ui.main.MainActivity.class));
                    finish();
                });
            }

            @Override
            public void onFailure(String error) {
                showSourceImportError(error);
            }
        });
    }

    private void rollbackSource() {
        ImportedSource restored = sourceImportActions.rollback();
        if (restored == null) {
            Toast.makeText(this, "没有可用的音源版本可回滚", Toast.LENGTH_SHORT).show();
            return;
        }
        BackendPreferences.setMode(this, BackendMode.DIRECT_SOURCE);
        clearPlaybackQueue();
        etSourceUrl.setText(restored.importUrl);
        tvSourceStatus.setText(restored.metadata.name + " · 已回滚到 "
                + (restored.metadata.version.isEmpty() ? "上一版" : restored.metadata.version));
        updateRollbackVisibility();
        Toast.makeText(this, "已回滚，下一次播放将使用此版本", Toast.LENGTH_SHORT).show();
    }

    private void updateRollbackVisibility() {
        btnRollbackSource.setVisibility(sourceImportActions.hasPrevious()
                ? View.VISIBLE : View.GONE);
        updateVisibleFocusGraph();
    }

    private void clearPlaybackQueue() {
        SessionToken token = new SessionToken(this, new ComponentName(this, MusicService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(this, token).buildAsync();
        future.addListener(() -> {
            try {
                MediaController controller = future.get();
                controller.stop();
                controller.clearMediaItems();
            } catch (Exception ignored) {
            } finally {
                MediaController.releaseFuture(future);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void showSourceImportError(String error) {
        mainHandler.post(() -> {
            if (!isActivityUsable()) return;
            btnImportSource.setEnabled(true);
            btnImportSource.setText("下载、检测并启用");
            tvSourceStatus.setText("检测失败：" + (error == null ? "未知错误" : error));
        });
    }

    private void showQrCodeDialog() {
        String ipAddress = LocalIpv4Address.find();
        if (ipAddress == null) {
            Toast.makeText(this, "无法获取局域网地址，请检查网络", Toast.LENGTH_SHORT).show();
            leaveQrMode();
            return;
        }

        stopSourceImportSession();
        stopLoginSession();
        String loginUrl = RemoteWebContract.url(
                ipAddress, RemoteWebContract.SERVER_CONFIG_PATH);
        
        AlertDialog qrDialog = DialogHelper.showQrCodeDialog(
            this,
            "扫码配置服务器",
            "在手机浏览器访问地址后填写配置信息",
            loginUrl,
            "访问管理: " + loginUrl
        );
        
        String savedUrl = LxRetrofitClient.getServerUrl(this);
        String savedUsername = LxRetrofitClient.getUsername(this);
        String savedPassword = LxRetrofitClient.getPassword(this);
        String savedToken = LxRetrofitClient.getToken(this);

        LoginRemoteSession session = new LoginRemoteSession(listener ->
                new LoginWebServer(RemoteWebContract.PORT, listener::onLogin,
                        savedUrl, savedUsername, savedPassword, savedToken));
        loginRemoteSession = session;
        qrDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "关闭", (dialog, which) -> {
            stopLoginSession(session);
            leaveQrMode();
        });

        try {
            session.start((url, username, password, token) ->
                    mainHandler.post(() -> {
                        if (!isActivityUsable() || loginRemoteSession != session) return;
                        loginRemoteSession = null;
                        qrDialog.dismiss();
                        String mergedUrl = url != null && !url.isEmpty() ? url : savedUrl;
                        String mergedUsername = username != null && !username.isEmpty()
                                ? username : savedUsername;
                        String mergedPassword = password != null && !password.isEmpty()
                                ? password : savedPassword;
                        String mergedToken = token != null && !token.isEmpty()
                                ? token : savedToken;

                        etUrl.setText(mergedUrl);
                        etUsername.setText(mergedUsername);
                        etPassword.setText(mergedPassword);
                        etToken.setText(mergedToken);
                        Toast.makeText(this, "收到推送信息，正在登录...",
                                Toast.LENGTH_SHORT).show();
                        btnConnect.performClick();
                    }));
        } catch (IOException e) {
            stopLoginSession(session);
            Toast.makeText(this, "服务启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            leaveQrMode();
            return;
        }
        
        qrDialog.setOnDismissListener(d -> {
            stopLoginSession(session);
            if (selectedMode == BackendMode.LXSERVER) leaveQrMode();
        });
        
        qrDialog.show();
    }

    private void stopLoginSession(LoginRemoteSession session) {
        if (loginRemoteSession != session) return;
        session.close();
        loginRemoteSession = null;
    }

    private void stopLoginSession() {
        if (loginRemoteSession == null) return;
        loginRemoteSession.close();
        loginRemoteSession = null;
    }

    private boolean isActivityUsable() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fluidBackground != null) fluidBackground.resumeRendering();
    }

    @Override
    protected void onPause() {
        if (fluidBackground != null) fluidBackground.pauseRendering();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        sourceImportActions.close();
        cancelServerConnection();
        stopLoginSession();
        stopSourceImportSession();
        super.onDestroy();
    }
}
