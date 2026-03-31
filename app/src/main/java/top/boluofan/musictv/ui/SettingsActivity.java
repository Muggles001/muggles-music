package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import top.boluofan.musictv.ConfigActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.LxRetrofitClient;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        initViews();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        
        LinearLayout layoutServerConfig = findViewById(R.id.layoutServerConfig);
        layoutServerConfig.setOnClickListener(v -> {
            startActivity(new Intent(this, ConfigActivity.class));
        });
        
        LinearLayout layoutLogout = findViewById(R.id.layoutLogout);
        layoutLogout.setOnClickListener(v -> {
            LxRetrofitClient.clearConfig(this);
            Toast.makeText(this, "配置已清除", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        
        TextView tvServerUrl = findViewById(R.id.tvServerUrl);
        tvServerUrl.setText(LxRetrofitClient.getServerUrl(this));
        
        TextView tvUsername = findViewById(R.id.tvUsername);
        String username = LxRetrofitClient.getUsername(this);
        tvUsername.setText(username.isEmpty() ? "未登录" : username);
    }
}
