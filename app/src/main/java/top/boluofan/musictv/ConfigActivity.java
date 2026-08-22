package top.boluofan.musictv;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.LoginResponse;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.source.ImportedSource;
import top.boluofan.musictv.source.SourceRuntimeEngine;
import top.boluofan.musictv.source.SourceRuntimeManager;
import top.boluofan.musictv.source.SourceScriptImporter;
import top.boluofan.musictv.source.SourceScriptStore;
import top.boluofan.musictv.ui.LibraryActivity;
import top.boluofan.musictv.util.DialogHelper;

public class ConfigActivity extends AppCompatActivity {
    private static final String TAG = "ConfigActivity";
    private LoginWebServer webServer;
    private SourceImportWebServer sourceWebServer;
    private static final int SERVER_PORT = 8088;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
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
    private BackendMode initialBackendMode = BackendMode.DIRECT_SOURCE;
    private retrofit2.Call<?> activeConnectCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

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

        ImportedSource activeSource = SourceRuntimeManager.get(this).getActiveSource();
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
        BackendMode savedMode = BackendPreferences.getMode(this);
        initialBackendMode = savedMode;
        if (savedMode == BackendMode.LXSERVER) selectBackendMode(BackendMode.LXSERVER);
        else selectBackendMode(BackendMode.DIRECT_SOURCE);
        View initialFocus = savedMode == BackendMode.LXSERVER ? btnModeServer : btnModeDirect;
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

                etUrl.setFocusable(false);
                etUsername.setFocusable(false);
                etPassword.setFocusable(false);
                etToken.setFocusable(false);
                btnConnect.setFocusable(false);

                showQrCodeDialog();
            } else {
                btnToggleMode.setText("扫码配置");
                layoutManual.setVisibility(View.VISIBLE);
                layoutQr.setVisibility(View.GONE);

                etUrl.setFocusable(true);
                etUrl.setFocusableInTouchMode(true);
                etUsername.setFocusable(true);
                etUsername.setFocusableInTouchMode(true);
                etPassword.setFocusable(true);
                etPassword.setFocusableInTouchMode(true);
                etToken.setFocusable(true);
                etToken.setFocusableInTouchMode(true);
                btnConnect.setFocusable(true);

                if (webServer != null) {
                    webServer.stop();
                    webServer = null;
                }
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

    private void connectToServer(String url, String username, String password, String token) {
        btnConnect.setEnabled(false);
        btnConnect.setText("正在验证服务器...");
        LxApiService apiService;
        try {
            apiService = LxRetrofitClient.createApiService(this, url);
        } catch (RuntimeException error) {
            showServerConnectionError(error.getMessage());
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            retrofit2.Call<okhttp3.ResponseBody> call = apiService.getSongListTags("wy");
            activeConnectCall = call;
            call.enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> request,
                                       retrofit2.Response<okhttp3.ResponseBody> response) {
                    if (!acceptServerResponse(request)) return;
                    if (!response.isSuccessful()) {
                        showServerConnectionError("服务器返回 HTTP " + response.code());
                        return;
                    }
                    completeServerConnection(url, username, password, token,
                            "服务器连接成功，将使用公共功能");
                }

                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> request, Throwable error) {
                    if (!acceptServerResponse(request)) return;
                    showServerConnectionError(serverFailureMessage(error));
                }
            });
            return;
        }

        java.util.HashMap<String, String> body = new java.util.HashMap<>();
        body.put("username", username);
        body.put("password", password);
        retrofit2.Call<LoginResponse> call = apiService.loginUser(body);
        activeConnectCall = call;
        call.enqueue(new retrofit2.Callback<LoginResponse>() {
            @Override
            public void onResponse(retrofit2.Call<LoginResponse> request,
                                   retrofit2.Response<LoginResponse> response) {
                if (!acceptServerResponse(request)) return;
                LoginResponse result = response.body();
                if (!response.isSuccessful()) {
                    showServerConnectionError("服务器返回 HTTP " + response.code());
                    return;
                }
                if (result == null || !result.isSuccess()) {
                    String message = result == null ? null : result.getMessage();
                    showServerConnectionError(message == null || message.trim().isEmpty()
                            ? "用户名或密码错误" : message);
                    return;
                }
                completeServerConnection(url, username, password, result.getToken(), "登录成功");
            }

            @Override
            public void onFailure(retrofit2.Call<LoginResponse> request, Throwable error) {
                if (!acceptServerResponse(request)) return;
                showServerConnectionError(serverFailureMessage(error));
            }
        });
    }

    private boolean acceptServerResponse(retrofit2.Call<?> call) {
        if (call != activeConnectCall || !isActivityUsable()) return false;
        activeConnectCall = null;
        return true;
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
        startActivity(new Intent(this, top.boluofan.musictv.ui.MainActivity.class));
        finish();
    }

    private void showServerConnectionError(String message) {
        activeConnectCall = null;
        if (!isActivityUsable()) return;
        btnConnect.setEnabled(true);
        btnConnect.setText("重新连接");
        Toast.makeText(this, "连接失败：" + (message == null ? "未知错误" : message),
                Toast.LENGTH_LONG).show();
    }

    private static String serverFailureMessage(Throwable error) {
        if (error instanceof java.io.InterruptedIOException) return "连接超时，请检查地址和网络";
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "无法访问服务器" : message;
    }

    private void cancelServerConnection() {
        retrofit2.Call<?> call = activeConnectCall;
        activeConnectCall = null;
        if (call != null) call.cancel();
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
        boolean direct = mode == BackendMode.DIRECT_SOURCE;
        layoutDirect.setVisibility(direct ? View.VISIBLE : View.GONE);
        layoutManual.setVisibility(direct ? View.GONE : View.VISIBLE);
        layoutQr.setVisibility(View.GONE);
        btnToggleMode.setVisibility(View.VISIBLE);
        btnToggleMode.setText(direct ? "扫码导入音源" : "扫码配置");
        btnModeDirect.setBackgroundResource(direct ? R.drawable.bg_btn_primary_tv : R.drawable.bg_btn_secondary);
        btnModeServer.setBackgroundResource(direct ? R.drawable.bg_btn_secondary : R.drawable.bg_btn_primary_tv);
        btnModeDirect.setTextColor(ContextCompat.getColorStateList(this,
                direct ? R.color.selector_primary_button_text : R.color.lx_text_primary));
        btnModeServer.setTextColor(ContextCompat.getColorStateList(this,
                direct ? R.color.lx_text_primary : R.color.selector_primary_button_text));
        if (direct) {
            etSourceUrl.setNextFocusLeftId(R.id.btnToggleMode);
            btnImportSource.setNextFocusLeftId(R.id.btnToggleMode);
        }
    }

    private void showSourceQrCodeDialog() {
        String ipAddress = getIPAddress();
        if (ipAddress == null) {
            Toast.makeText(this, "无法获取局域网地址，请检查网络", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sourceWebServer != null) sourceWebServer.stop();
        String importUrl = "http://" + ipAddress + ":" + SERVER_PORT;
        AlertDialog dialog = DialogHelper.showQrCodeDialog(
                this,
                "扫码导入 LX 音源",
                "在手机页面粘贴落雪兼容音源脚本地址",
                importUrl,
                "访问地址: " + importUrl
        );
        sourceWebServer = new SourceImportWebServer(SERVER_PORT, sourceUrl ->
                mainHandler.post(() -> {
                    if (!isActivityUsable()) return;
                    dialog.dismiss();
                    etSourceUrl.setText(sourceUrl);
                    btnImportSource.performClick();
                }));
        try {
            sourceWebServer.start();
        } catch (IOException error) {
            sourceWebServer = null;
            Toast.makeText(this, "扫码服务启动失败: " + error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        dialog.setOnDismissListener(ignored -> {
            if (sourceWebServer != null) {
                sourceWebServer.stop();
                sourceWebServer = null;
            }
        });
        dialog.show();
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
        importExecutor.execute(() -> {
            try {
                ImportedSource downloaded = new SourceScriptImporter().download(url);
                SourceRuntimeManager.get(this).activate(downloaded, new SourceRuntimeEngine.LoadCallback() {
                    @Override
                    public void onLoaded(com.google.gson.JsonObject capabilities) {
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
                                    top.boluofan.musictv.ui.MainActivity.class));
                            finish();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        showSourceImportError(error);
                    }
                });
            } catch (Exception error) {
                showSourceImportError(error.getMessage());
            }
        });
    }

    private void rollbackSource() {
        SourceScriptStore store = new SourceScriptStore(this);
        if (!store.rollback()) {
            Toast.makeText(this, "没有可回滚的音源版本", Toast.LENGTH_SHORT).show();
            return;
        }
        SourceRuntimeManager.get(this).invalidate();
        ImportedSource restored = store.getActive();
        if (restored == null) {
            showSourceImportError("上一版音源文件不存在");
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
        btnRollbackSource.setVisibility(new SourceScriptStore(this).hasPrevious()
                ? View.VISIBLE : View.GONE);
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
        String ipAddress = getIPAddress();
        if (ipAddress == null) {
            Toast.makeText(this, "无法获取局域网地址，请检查网络", Toast.LENGTH_SHORT).show();
            return;
        }

        String loginUrl = "http://" + ipAddress + ":" + SERVER_PORT;
        
        AlertDialog qrDialog = DialogHelper.showQrCodeDialog(
            this,
            "扫码配置服务器",
            "在手机浏览器访问地址后填写配置信息",
            loginUrl,
            "访问管理: " + loginUrl
        );
        
        qrDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "关闭", (d, which) -> {
            if (webServer != null) {
                webServer.stop();
                webServer = null;
            }
            isQrMode = false;
            btnToggleMode.setText("扫码配置");
            layoutManual.setVisibility(View.VISIBLE);
            layoutQr.setVisibility(View.GONE);
            
            etUrl.setFocusable(true);
            etUsername.setFocusable(true);
            etPassword.setFocusable(true);
            btnConnect.setFocusable(true);
        });
        
        String savedUrl = LxRetrofitClient.getServerUrl(this);
        String savedUsername = LxRetrofitClient.getUsername(this);
        String savedPassword = LxRetrofitClient.getPassword(this);
        String savedToken = LxRetrofitClient.getToken(this);

        webServer = new LoginWebServer(this, SERVER_PORT, (url, username, password, token) -> {
            mainHandler.post(() -> {
                if (!isActivityUsable()) return;
                qrDialog.dismiss();
                // 合并：如果推送的值为空，保留原有值
                String mergedUrl = (url != null && !url.isEmpty()) ? url : savedUrl;
                String mergedUsername = (username != null && !username.isEmpty()) ? username : savedUsername;
                String mergedPassword = (password != null && !password.isEmpty()) ? password : savedPassword;
                String mergedToken = (token != null && !token.isEmpty()) ? token : savedToken;

                etUrl.setText(mergedUrl);
                etUsername.setText(mergedUsername);
                etPassword.setText(mergedPassword);
                etToken.setText(mergedToken);
                Toast.makeText(this, "收到推送信息，正在登录...", Toast.LENGTH_SHORT).show();
                btnConnect.performClick();
            });
        }, savedUrl, savedUsername, savedPassword, savedToken);

        try {
            webServer.start();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "服务启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        qrDialog.setOnDismissListener(d -> {
            if (webServer != null) {
                webServer.stop();
                webServer = null;
            }
        });
        
        qrDialog.show();
    }

    private void startLoginWebServer(TextView tvIp, ImageView ivQr, EditText etUrl, EditText etUsername, EditText etPassword, Button btnConnect) {
        String ipAddress = getIPAddress();
        if (ipAddress == null) {
            tvIp.setText("无法获取局域网地址，请检查网络");
            return;
        }

        String loginUrl = "http://" + ipAddress + ":" + SERVER_PORT;
        tvIp.setText("访问管理: " + loginUrl);

        generateQrCode(loginUrl, ivQr);

        webServer = new LoginWebServer(this, SERVER_PORT, (url, username, password, token) -> {
            mainHandler.post(() -> {
                if (!isActivityUsable()) return;
                etUrl.setText(url);
                etUsername.setText(username);
                etPassword.setText(password);
                etToken.setText(token);
                Toast.makeText(this, "收到推送信息，正在登录...", Toast.LENGTH_SHORT).show();
                btnConnect.performClick();
            });
        });

        try {
            webServer.start();
        } catch (IOException e) {
            e.printStackTrace();
            tvIp.setText("服务启动失败: " + e.getMessage());
        }
    }

    private void generateQrCode(String text, ImageView imageView) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            imageView.setImageBitmap(bmp);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private String getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) return sAddr;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private boolean isActivityUsable() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        importExecutor.shutdownNow();
        if (activeConnectCall != null) {
            activeConnectCall.cancel();
            activeConnectCall = null;
        }
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        if (sourceWebServer != null) {
            sourceWebServer.stop();
            sourceWebServer = null;
        }
        super.onDestroy();
    }
}
