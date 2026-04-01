package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.util.FocusAnimationHelper;
import top.boluofan.musictv.ui.SongSquareFragment;

public class MainActivity extends AppCompatActivity {
    private FloatingPlayerWindow floatingPlayerWindow;
    private LinearLayout tabLibrary;
    private LinearLayout tabSearch;
    private LinearLayout tabSongSquare;
    private LinearLayout tabRanking;
    private LinearLayout tabSettings;
    
    private ImageButton btnLibrary;
    private ImageButton btnSearch;
    private ImageButton btnSongSquare;
    private ImageButton btnRanking;
    private ImageButton btnSettings;
    
    private TextView tvLibrary;
    private TextView tvSearch;
    private TextView tvSongSquare;
    private TextView tvRanking;
    private TextView tvSettings;

    private int currentSelectedTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String serverUrl = LxRetrofitClient.getServerUrl(this);
        if (serverUrl == null || serverUrl.isEmpty()) {
            startActivity(new Intent(this, top.boluofan.musictv.ConfigActivity.class));
            finish();
            return;
        }
        
        setContentView(R.layout.activity_main);
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        initViews();
        setupListeners();
        
        showSongSquare();
    }

    private void initViews() {
        tabLibrary = findViewById(R.id.tabLibrary);
        tabSearch = findViewById(R.id.tabSearch);
        tabSongSquare = findViewById(R.id.tabSongSquare);
        tabRanking = findViewById(R.id.tabRanking);
        tabSettings = findViewById(R.id.tabSettings);
        
        btnLibrary = findViewById(R.id.btnLibrary);
        btnSearch = findViewById(R.id.btnSearch);
        btnSongSquare = findViewById(R.id.btnSongSquare);
        btnRanking = findViewById(R.id.btnRanking);
        btnSettings = findViewById(R.id.btnSettings);
        
        tvLibrary = findViewById(R.id.tvLibrary);
        tvSearch = findViewById(R.id.tvSearch);
        tvSongSquare = findViewById(R.id.tvSongSquare);
        tvRanking = findViewById(R.id.tvRanking);
        tvSettings = findViewById(R.id.tvSettings);
        
        if (!LxRetrofitClient.isLoggedIn(this)) {
            tabLibrary.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        tabLibrary.setOnClickListener(v -> showLibrary());
        tabSearch.setOnClickListener(v -> showSearch());
        tabSongSquare.setOnClickListener(v -> showSongSquare());
        tabRanking.setOnClickListener(v -> showRanking());
        tabSettings.setOnClickListener(v -> showSettings());

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (hasFocus) {
                FocusAnimationHelper.animateFocusIn(v);
            } else {
                FocusAnimationHelper.animateFocusOut(v);
            }
        };

        tabLibrary.setOnFocusChangeListener(focusListener);
        tabSearch.setOnFocusChangeListener(focusListener);
        tabSongSquare.setOnFocusChangeListener(focusListener);
        tabRanking.setOnFocusChangeListener(focusListener);
        tabSettings.setOnFocusChangeListener(focusListener);
    }

    private void clearSelection() {
        btnLibrary.setAlpha(0.5f);
        btnSearch.setAlpha(0.5f);
        btnSongSquare.setAlpha(0.5f);
        btnRanking.setAlpha(0.5f);
        btnSettings.setAlpha(0.5f);
        
        tvLibrary.setTextColor(0xFF9CA3AF);
        tvSearch.setTextColor(0xFF9CA3AF);
        tvSongSquare.setTextColor(0xFF9CA3AF);
        tvRanking.setTextColor(0xFF9CA3AF);
        tvSettings.setTextColor(0xFF9CA3AF);
    }

    private void updateTabSelection(int tabIndex) {
        clearSelection();
        boolean isLoggedIn = LxRetrofitClient.isLoggedIn(this);
        
        if (isLoggedIn) {
            switch (tabIndex) {
                case 0:
                    btnSearch.setAlpha(1.0f);
                    tvSearch.setTextColor(0xFFFFFFFF);
                    break;
                case 1:
                    btnSongSquare.setAlpha(1.0f);
                    tvSongSquare.setTextColor(0xFFFFFFFF);
                    break;
                case 2:
                    btnRanking.setAlpha(1.0f);
                    tvRanking.setTextColor(0xFFFFFFFF);
                    break;
                case 3:
                    btnLibrary.setAlpha(1.0f);
                    tvLibrary.setTextColor(0xFFFFFFFF);
                    break;
                case 4:
                    btnSettings.setAlpha(1.0f);
                    tvSettings.setTextColor(0xFFFFFFFF);
                    break;
            }
        } else {
            switch (tabIndex) {
                case 0:
                    btnSearch.setAlpha(1.0f);
                    tvSearch.setTextColor(0xFFFFFFFF);
                    break;
                case 1:
                    btnSongSquare.setAlpha(1.0f);
                    tvSongSquare.setTextColor(0xFFFFFFFF);
                    break;
                case 2:
                    btnRanking.setAlpha(1.0f);
                    tvRanking.setTextColor(0xFFFFFFFF);
                    break;
                case 3:
                    btnSettings.setAlpha(1.0f);
                    tvSettings.setTextColor(0xFFFFFFFF);
                    break;
            }
        }
        currentSelectedTab = tabIndex;
    }

    private void showLibrary() {
        if (!LxRetrofitClient.isLoggedIn(this)) return;
        
        updateTabSelection(3);
        
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragmentContainer, new LibraryFragment())
            .commit();
    }

    private void showSearch() {
        updateTabSelection(0);
        
        startActivity(new Intent(this, SearchActivity.class));
    }

    private void showSongSquare() {
        updateTabSelection(1);
        
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragmentContainer, new SongSquareFragment())
            .commit();
    }

    private void showRanking() {
        updateTabSelection(2);
        
        startActivity(new Intent(this, RankingActivity.class));
    }

    private void showSettings() {
        boolean isLoggedIn = LxRetrofitClient.isLoggedIn(this);
        updateTabSelection(isLoggedIn ? 4 : 3);
        
        startActivity(new Intent(this, SettingsActivity.class));
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
