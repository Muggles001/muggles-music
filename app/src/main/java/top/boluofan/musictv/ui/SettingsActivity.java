package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import top.boluofan.musictv.ConfigActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxRetrofitClient;

public class SettingsActivity extends AppCompatActivity {
    private FloatingPlayerWindow floatingPlayerWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        initViews();
    }

    private static final String EXTRA_SERVER_URL = "server_url";
    
    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        
        String serverUrl = LxRetrofitClient.getServerUrl(this);
        
        LinearLayout layoutServerConfig = findViewById(R.id.layoutServerConfig);
        layoutServerConfig.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConfigActivity.class);
            intent.putExtra(EXTRA_SERVER_URL, serverUrl);
            startActivity(intent);
        });

        LinearLayout layoutUserInfo = findViewById(R.id.layoutUserInfo);
        String username = LxRetrofitClient.getUsername(this);
        layoutUserInfo.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConfigActivity.class);
            intent.putExtra(EXTRA_SERVER_URL, serverUrl);
            startActivity(intent);
        });
        
        LinearLayout layoutLogout = findViewById(R.id.layoutLogout);
        layoutLogout.setOnClickListener(v -> {
            LxRetrofitClient.clearConfig(this);
            Toast.makeText(this, "配置已清除", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        
        TextView tvServerUrl = findViewById(R.id.tvServerUrl);
        tvServerUrl.setText(serverUrl);
        
        TextView tvUsername = findViewById(R.id.tvUsername);
        tvUsername.setText(username.isEmpty() ? "未登录" : username);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null && floatingPlayerWindow != null) {
                if (floatingPlayerWindow.handleLeftKey(currentFocus)) {
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
