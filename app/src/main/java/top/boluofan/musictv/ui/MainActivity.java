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
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import android.content.ComponentName;
import android.util.Log;
import androidx.recyclerview.widget.RecyclerView;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.util.FocusAnimationHelper;
import top.boluofan.musictv.ui.SongSquareFragment;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
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
    
    private long lastBackPressTime = 0;
    
    private TextView tvLibrary;
    private TextView tvSearch;
    private TextView tvSongSquare;
    private TextView tvRanking;
    private TextView tvSettings;

    private int currentSelectedTab = 0;
    
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;

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

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
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
        
        startActivity(new Intent(this, LibraryActivity.class));
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
        Log.d(TAG, "onKeyDown: keyCode=" + keyCode);
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            View currentFocus = getCurrentFocus();
            Log.d(TAG, "LEFT key, currentFocus=" + currentFocus);
            if (currentFocus != null && floatingPlayerWindow != null) {
                if (floatingPlayerWindow.handleLeftKey(currentFocus)) {
                    return true;
                }
            }

            if (currentFocus != null && currentFocus.getId() == R.id.tabSearch) {
                View fragmentView = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer).getView();
                if (fragmentView != null) {
                    RecyclerView rvSourceList = fragmentView.findViewById(R.id.rvSourceList);
                    if (rvSourceList != null) {
                        RecyclerView.LayoutManager lm = rvSourceList.getLayoutManager();
                        if (lm != null) {
                            View firstSource = lm.findViewByPosition(0);
                            if (firstSource != null) {
                                firstSource.setFocusable(true);
                                return firstSource.requestFocus();
                            }
                        }
                    }
                }
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBackPressTime < 2000) {
                boolean backgroundPlay = LxRetrofitClient.getBackgroundPlay(this);
                if (!backgroundPlay && player != null) {
                    player.stop();
                    player.clearMediaItems();
                }
                if (!backgroundPlay && floatingPlayerWindow != null) {
                    floatingPlayerWindow.release();
                    floatingPlayerWindow = null;
                }
                finish();
            } else {
                Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                lastBackPressTime = currentTime;
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
