package top.boluofan.musictv.ui.player;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import top.boluofan.musictv.R;
import top.boluofan.musictv.catalog.direct.netease.SongScraper;
import top.boluofan.musictv.player.control.PlayerControlsStateMachine;
import top.boluofan.musictv.player.lyrics.LyricRequestCoordinator;
import top.boluofan.musictv.player.lyrics.LyricCatalogActions;
import top.boluofan.musictv.player.lyrics.LyricPlaybackState;
import top.boluofan.musictv.player.lyrics.LyricParser;
import top.boluofan.musictv.player.options.PlayerOptionActions;
import top.boluofan.musictv.player.settings.PlayerPreferences;
import top.boluofan.musictv.player.session.MediaControllerConnection;
import top.boluofan.musictv.player.visual.AsciiRibbonView;
import top.boluofan.musictv.player.visual.SpatialLyricsView;
import top.boluofan.musictv.player.visual.VinylTurntableView;
import top.boluofan.musictv.player.visual.WaveformProgressView;
import top.boluofan.musictv.settings.AppSettingsPreferences;
import top.boluofan.musictv.settings.LyricTimingPreferences;

public class PlayerActivity extends AppCompatActivity {
    private static final String TAG = "PlayerActivity";
    private static final float METADATA_TEXT_REDUCTION_PX = 18f;
    private ImageView ivBlurBackground;
    private AsciiRibbonView asciiRibbonView;
    private VinylTurntableView vinylTurntableView;
    private WaveformProgressView waveformProgressView;
    private SpatialLyricsView spatialLyricsView;
    private TextView tvBigTitle;
    private TextView tvBigArtist;
    private TextView tvNoLyrics;
    private TextView tvSeekProgress, tvPlaylistName, tvPlaylistCount;
    private android.widget.LinearLayout layoutSeekProgress;
    private ImageButton btnRepeat, btnPrev, btnPlayPause, btnNext, btnScrape, btnQueue;
    private ImageButton btnOptionMenuBack;
    private android.view.View layoutDrawer;
    private android.view.ViewGroup layoutMainContent;
    private RecyclerView rvDrawerSongs;
    private DrawerSongAdapter drawerSongAdapter;
    private android.view.View layoutOptionMenu;
    private RecyclerView rvOptions;
    private PlayerOptionAdapter optionAdapter;
    private MediaController player;
    private MediaControllerConnection controllerConnection;
    private Player.Listener playerListener;
    private LyricPlaybackState lyricPlaybackState;
    private SongScraper songScraper;
    private String currentScrapingId = "";
    private String currentSongSource = "mg";
    private String currentSongMid = "";
    private boolean hasArtwork = false;
    private PlayerArtworkLoader artworkLoader;
    private LyricRequestCoordinator lyricRequestCoordinator;
    private LyricCatalogActions lyricCatalogActions;
    private AppSettingsPreferences settingsPreferences;
    private LyricTimingPreferences lyricTimingPreferences;
    private PlayerPreferences playerPreferences;
    private boolean activityResumed;
    private long playbackErrorGeneration;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updatePlaybackClock();
            // Keep the lyric clock alive while the player screen exists. MediaController's
            // isPlaying flag can briefly toggle during buffering even though playback resumes.
            handler.postDelayed(this, 100);
        }
    };

    private android.view.View layoutBottomControls;
    private PlayerControlsStateMachine controlsStateMachine;
    private float lastPointerY = Float.NaN;
    // Remote capture long press / double click
    private long lastLeftClickTime = 0;
    private long lastRightClickTime = 0;
    private static final long DOUBLE_CLICK_INTERVAL = 300;
    private boolean isSeeking = false;
    private final Runnable seekForwardRunnable = new Runnable() {
        @Override public void run() {
            if (player != null) {
                player.seekForward();
                updateTooltip(true);
                handler.postDelayed(this, 300);
            }
        }
    };
    private final Runnable seekBackwardRunnable = new Runnable() {
        @Override public void run() {
            if (player != null) {
                player.seekBack();
                updateTooltip(false);
                handler.postDelayed(this, 300);
            }
        }
    };

    private void updateTooltip(boolean isForward) {
        if (player == null) return;
        handler.removeCallbacks(showShortcutHintRunnable); // Cancel hint if seeking starts
        handler.removeCallbacks(hideSeekHintRunnable);
        long current = player.getCurrentPosition();
        long duration = player.getDuration();
        String prefix = isForward ? "▶▶ " : "◀◀ ";
        tvSeekProgress.setText(prefix + formatTime(current) + " / " + formatTime(duration));
        layoutSeekProgress.setVisibility(View.VISIBLE);
    }

    private final Runnable hideSeekHintRunnable = () -> {
        layoutSeekProgress.setVisibility(View.GONE);
    };

    private final Runnable showShortcutHintRunnable = () -> {
        if (!isSeeking) { // Only show if not currently seeking
            tvSeekProgress.setText("长按「左」「右」键快进快退，双击「左」「右」键切换歌曲");
            layoutSeekProgress.setVisibility(View.VISIBLE);
            handler.postDelayed(hideSeekHintRunnable, 2000);
        }
    };

    private void showShortcutHint() {
        handler.removeCallbacks(showShortcutHintRunnable);
        handler.removeCallbacks(hideSeekHintRunnable);
        // Delay until a long press has had enough time to suppress the tap hint.
        handler.postDelayed(showShortcutHintRunnable, 500);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        settingsPreferences = new AppSettingsPreferences(this);
        lyricTimingPreferences = new LyricTimingPreferences(this);
        playerPreferences = new PlayerPreferences(this);

        ivBlurBackground = findViewById(R.id.ivBlurBackground);
        asciiRibbonView = findViewById(R.id.asciiRibbonView);
        vinylTurntableView = findViewById(R.id.vinylTurntableView);
        waveformProgressView = findViewById(R.id.waveformProgressView);
        tvBigTitle = findViewById(R.id.tvBigTitle);
        tvBigArtist = findViewById(R.id.tvBigArtist);
        reduceMetadataTextSize(tvBigTitle);
        reduceMetadataTextSize(tvBigArtist);
        spatialLyricsView = findViewById(R.id.spatialLyricsView);
        tvNoLyrics = findViewById(R.id.tvNoLyrics);
        tvSeekProgress = findViewById(R.id.tvSeekProgress);
        tvPlaylistName = findViewById(R.id.tvPlaylistName);
        tvPlaylistCount = findViewById(R.id.tvPlaylistCount);
        layoutSeekProgress = findViewById(R.id.layoutSeekProgress);
        artworkLoader = new PlayerArtworkLoader(
                this, ivBlurBackground, vinylTurntableView, asciiRibbonView);

        btnRepeat = findViewById(R.id.btnRepeat);
        btnPrev = findViewById(R.id.btnPrev);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnScrape = findViewById(R.id.btnScrape);
        btnQueue = findViewById(R.id.btnQueue);
        layoutDrawer = findViewById(R.id.layoutDrawer);
        layoutMainContent = findViewById(R.id.layoutMainContent);
        layoutBottomControls = findViewById(R.id.layoutBottomControls);
        layoutOptionMenu = findViewById(R.id.layoutOptionMenu);
        btnOptionMenuBack = findViewById(R.id.btnOptionMenuBack);
        rvOptions = findViewById(R.id.rvOptions);
        
        rvDrawerSongs = findViewById(R.id.rvDrawerSongs);

        controlsStateMachine = new PlayerControlsStateMachine(
                layoutBottomControls,
                handler,
                state -> {
                    if (state == PlayerControlsStateMachine.State.SHOWING) {
                        updateProgress();
                        waveformProgressView.setAnimationEnabled(true);
                        btnPlayPause.post(() -> btnPlayPause.requestFocus());
                    } else if (state == PlayerControlsStateMachine.State.HIDDEN) {
                        waveformProgressView.setAnimationEnabled(false);
                        layoutMainContent.post(() -> layoutMainContent.requestFocus());
                    }
                }
        );
        waveformProgressView.setAnimationEnabled(false);
        
        lyricPlaybackState = createLyricPlaybackState();
        spatialLyricsView.setFocusFraction(0.50f);

        // Option Menu Setup
        rvOptions.setLayoutManager(new LinearLayoutManager(this));
        List<String> options = PlayerOptionActions.labels();
        
        optionAdapter = new PlayerOptionAdapter(options, (option, position) -> {
            handleOptionClick(option);
        });
        optionAdapter.setOptionDisabled(PlayerOptionActions.SAVE_LYRICS_LABEL, true);
        rvOptions.setAdapter(optionAdapter);
        btnOptionMenuBack.setOnClickListener(v -> toggleOptionMenu(false));
        btnOptionMenuBack.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                focusFirstOption();
                return true;
            }
            return false;
        });
        layoutOptionMenu.findViewById(R.id.viewOptionMenuDim).setOnClickListener(v -> toggleOptionMenu(false));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (closeTopPlayerLayer()) return;
                finish();
            }
        });

        // Sidebar Drawer Setup
        rvDrawerSongs.setLayoutManager(new LinearLayoutManager(this));
        drawerSongAdapter = new DrawerSongAdapter(this);
        rvDrawerSongs.setAdapter(drawerSongAdapter);
        drawerSongAdapter.setOnItemClickListener((song, position) -> {
            if (player != null) {
                player.seekToDefaultPosition(position);
                player.play();
                toggleDrawer(false);
            }
        });
        
        lyricCatalogActions = new LyricCatalogActions(this);

        // Enable marquee
        tvBigTitle.setSelected(true);
        tvBigArtist.setSelected(true);

        setupControls();
        setupWaveformProgress();
        layoutMainContent.post(() -> layoutMainContent.requestFocus());

        songScraper = new SongScraper();
        lyricRequestCoordinator = createLyricRequestCoordinator();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (controllerConnection == null) {
            controllerConnection = new MediaControllerConnection(
                    this,
                    () -> !isFinishing() && !isDestroyed(),
                    resolvedController -> {
                        player = resolvedController;
                        setupPlayer();
                    },
                    error -> Log.e(TAG, "Failed to connect media controller", error));
        }
        controllerConnection.connect();
    }

    private void setupControls() {
        btnPlayPause.setOnClickListener(v -> {
            if (player == null) return;
            if (player.isPlaying()) player.pause();
            else player.play();
            resetControlsTimer();
        });

        btnPrev.setOnClickListener(v -> {
            skipToPrevious();
            resetControlsTimer();
        });

        btnNext.setOnClickListener(v -> {
            if (player != null) {
                player.seekToNext();
                resetControlsTimer();
            }
        });

        btnScrape.setOnClickListener(v -> {
            toggleOptionMenu(true);
            resetControlsTimer();
        });

        btnQueue.setOnClickListener(v -> {
            toggleDrawer(true);
            resetControlsTimer();
        });
        
        layoutDrawer.setOnClickListener(v -> toggleDrawer(false));

        btnRepeat.setOnClickListener(v -> {
            if (player == null) return;
            
            // Current State
            int repeatMode = player.getRepeatMode();
            boolean isShuffle = player.getShuffleModeEnabled();

            // Next State Logic: All(Loop) -> Shuffle(All+S) -> One(1) -> All(Loop)...
            if (isShuffle) {
                // Currently Shuffle -> Switch to Single Loop (One)
                player.setShuffleModeEnabled(false);
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
                Toast.makeText(this, "已切换为单曲循环", Toast.LENGTH_SHORT).show();
            } else if (repeatMode == Player.REPEAT_MODE_ONE) {
                // Currently Single Loop -> Switch to List Loop (All)
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                Toast.makeText(this, "已切换为列表循环", Toast.LENGTH_SHORT).show();
            } else {
                // Currently List Loop (or somehow Off) -> Switch to Shuffle
                player.setRepeatMode(Player.REPEAT_MODE_ALL); 
                player.setShuffleModeEnabled(true);
                Toast.makeText(this, "已切换为随机播放", Toast.LENGTH_SHORT).show();
            }
            updateControlsUI();
            resetControlsTimer();
        });
    }

    private void toggleDrawer(boolean show) {
        if (show) {
            layoutDrawer.setVisibility(View.VISIBLE);
            // Lock focus to the drawer by blocking background interaction
            if (layoutMainContent != null) {
                layoutMainContent.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
            // Prepare data from current player queue
            if (player != null) {
                List<top.boluofan.musictv.ui.player.DrawerSongAdapter.DrawerSongItem> queue = new ArrayList<>();
                for (int i = 0; i < player.getMediaItemCount(); i++) {
                    androidx.media3.common.MediaItem item = player.getMediaItemAt(i);
                    String title = item.mediaMetadata.title != null ? item.mediaMetadata.title.toString() : "";
                    String artist = item.mediaMetadata.artist != null ? item.mediaMetadata.artist.toString() : "";
                    String coverUrl = item.mediaMetadata.artworkUri != null ? item.mediaMetadata.artworkUri.toString() : "";
                    
                    queue.add(new top.boluofan.musictv.ui.player.DrawerSongAdapter.DrawerSongItem(
                            title, artist, coverUrl));
                }
                drawerSongAdapter.setSongs(queue);
                drawerSongAdapter.setPlayingIndex(player.getCurrentMediaItemIndex());
                drawerSongAdapter.setPlayerPlaying(player.isPlaying());
            }

            // Show current playlist name from prefs
            String playlistName = playerPreferences.getLastPlaylistName();
            if (tvPlaylistName != null) {
                tvPlaylistName.setText(playlistName.isEmpty() ? "" : "[" + playlistName + "]");
            }
            if (tvPlaylistCount != null && player != null) {
                tvPlaylistCount.setText(player.getMediaItemCount() + "首");
            }

            rvDrawerSongs.post(() -> {
                if (player != null) {
                    int currentIndex = player.getCurrentMediaItemIndex();
                    rvDrawerSongs.scrollToPosition(currentIndex);
                    
                    // Delay slightly to ensure RecyclerView has bound the view for requestFocus to succeed
                    rvDrawerSongs.postDelayed(() -> {
                        RecyclerView.ViewHolder vh = rvDrawerSongs.findViewHolderForAdapterPosition(currentIndex);
                        if (vh != null && vh.itemView != null) {
                            vh.itemView.requestFocus();
                        } else {
                            rvDrawerSongs.requestFocus();
                        }
                    }, 50);
                }
            });
        } else {
            layoutDrawer.setVisibility(View.GONE);
            // Restore background interaction when drawer is closed
            if (layoutMainContent != null) {
                layoutMainContent.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            btnQueue.requestFocus();
        }
    }

    private boolean closeTopPlayerLayer() {
        if (layoutOptionMenu.getVisibility() == View.VISIBLE) {
            toggleOptionMenu(false);
            return true;
        } else if (layoutDrawer.getVisibility() == View.VISIBLE) {
            toggleDrawer(false);
            resetControlsTimer();
            return true;
        } else if (isControlsVisible()) {
            hideBottomControls();
            return true;
        }
        return false;
    }

    private boolean isControlsVisible() {
        return controlsStateMachine != null && controlsStateMachine.isDisplayed();
    }

    private void hideBottomControls() {
        if (controlsStateMachine != null) controlsStateMachine.hideNow();
    }

    private void resetControlsTimer() {
        if (controlsStateMachine != null && controlsStateMachine.isDisplayed()) {
            controlsStateMachine.onInteraction();
        }
    }

    private void reduceMetadataTextSize(TextView textView) {
        textView.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                textView.getTextSize() - METADATA_TEXT_REDUCTION_PX
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (layoutDrawer.getVisibility() == View.VISIBLE || layoutOptionMenu.getVisibility() == View.VISIBLE) {
            return super.dispatchKeyEvent(event);
        }

        if (action == android.view.KeyEvent.ACTION_DOWN) {
            if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                toggleOptionMenu(true);
                return true;
            }
            if (controlsStateMachine != null && controlsStateMachine.onRevealKey(keyCode)) {
                return true;
            }
            resetControlsTimer();
            if (!isControlsVisible()) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (event.getRepeatCount() == 0) {
                        long now = System.currentTimeMillis();
                        if (now - lastLeftClickTime < DOUBLE_CLICK_INTERVAL) {
                            handler.removeCallbacks(showShortcutHintRunnable);
                            handler.removeCallbacks(hideSeekHintRunnable);
                            layoutSeekProgress.setVisibility(View.GONE);
                            skipToPrevious();
                            lastLeftClickTime = 0;
                        } else {
                            lastLeftClickTime = now;
                            showShortcutHint();
                        }
                    } else {
                        // Any repeat means the user is holding the key
                        handler.removeCallbacks(showShortcutHintRunnable);
                        handler.removeCallbacks(hideSeekHintRunnable);
                        if (!isSeeking) {
                            layoutSeekProgress.setVisibility(View.GONE);
                        }
                        if (event.getRepeatCount() > 2) {
                            if (!isSeeking) {
                                isSeeking = true;
                                handler.post(seekBackwardRunnable);
                            }
                        }
                    }
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.getRepeatCount() == 0) {
                        long now = System.currentTimeMillis();
                        if (now - lastRightClickTime < DOUBLE_CLICK_INTERVAL) {
                            handler.removeCallbacks(showShortcutHintRunnable);
                            handler.removeCallbacks(hideSeekHintRunnable);
                            layoutSeekProgress.setVisibility(View.GONE);
                            if (player != null) player.seekToNext();
                            lastRightClickTime = 0;
                        } else {
                            lastRightClickTime = now;
                            showShortcutHint();
                        }
                    } else {
                        // Any repeat means the user is holding the key
                        handler.removeCallbacks(showShortcutHintRunnable);
                        handler.removeCallbacks(hideSeekHintRunnable);
                        if (!isSeeking) {
                            layoutSeekProgress.setVisibility(View.GONE);
                        }
                        if (event.getRepeatCount() > 2) {
                            if (!isSeeking) {
                                isSeeking = true;
                                handler.post(seekForwardRunnable);
                            }
                        }
                    }
                    return true;
                }
            } else {
                // Controls visible
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    // If focus is already on bottom row, hide
                    View focused = getCurrentFocus();
                    if (focused == btnPlayPause || focused == btnPrev || focused == btnNext || focused == btnScrape || focused == btnRepeat || focused == btnQueue) {
                        hideBottomControls();
                        return true;
                    }
                }
            }
        } else if (action == android.view.KeyEvent.ACTION_UP) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (isSeeking) {
                    handler.removeCallbacks(showShortcutHintRunnable);
                    isSeeking = false;
                    handler.removeCallbacks(seekForwardRunnable);
                    handler.removeCallbacks(seekBackwardRunnable);
                    layoutSeekProgress.setVisibility(View.GONE);
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        trackPointerMotion(event);
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        trackPointerMotion(event);
        return super.dispatchTouchEvent(event);
    }

    private void trackPointerMotion(MotionEvent event) {
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_HOVER_MOVE) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_HOVER_ENTER) {
                lastPointerY = event.getY();
            }
            return;
        }
        float y = event.getY();
        if (!Float.isNaN(lastPointerY)) {
            float deltaY = y - lastPointerY;
            if (deltaY >= dp(4) && controlsStateMachine != null) {
                controlsStateMachine.onPointerMoved(deltaY);
            }
        }
        lastPointerY = y;
    }

    private void skipToPrevious() {
        if (player == null) return;
        // If the track has played for a while, seekToPrevious normally just restarts the current song.
        // We want to force it to ALWAYS skip to the previous song.
        // So if we are past the threshold (typically 3s), we seek to 0 first, then call seekToPrevious.
        if (player.getCurrentPosition() > player.getMaxSeekToPreviousPosition()) {
            player.seekTo(0);
        }
        player.seekToPrevious();
    }
    
    private void setupWaveformProgress() {
        waveformProgressView.setOnSeekListener(positionMs -> {
            if (player == null) return;
            player.seekTo(positionMs);
            updateProgress();
            resetControlsTimer();
        });
        waveformProgressView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                updateProgress();
                resetControlsTimer();
            }
        });
    }

    private void updateControlsUI() {
        if (player == null) return;
        boolean playing = player.isPlaying();
        btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        asciiRibbonView.setPlaying(playing);
        vinylTurntableView.setPlaying(playing);
        waveformProgressView.setPlaying(playing);

        // Mode Display
        int repeatMode = player.getRepeatMode();
        boolean isShuffle = player.getShuffleModeEnabled();

        if (isShuffle) {
            btnRepeat.setImageResource(R.drawable.ic_shuffle);
            btnRepeat.setAlpha(1.0f);
        } else if (repeatMode == Player.REPEAT_MODE_ONE) {
            btnRepeat.setImageResource(R.drawable.ic_repeat_one);
            btnRepeat.setAlpha(1.0f);
        } else if (repeatMode == Player.REPEAT_MODE_ALL) {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setAlpha(1.0f);
        } else {
            // Fallback (Off) visually mapped to List Loop as it's the enforced default
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setAlpha(1.0f);
        }
        
        if (layoutDrawer.getVisibility() == View.VISIBLE) {
            drawerSongAdapter.setPlayingIndex(player.getCurrentMediaItemIndex());
            drawerSongAdapter.setPlayerPlaying(player.isPlaying());
        }
    }

    private void setupPlayer() {
        if (player == null) return;
        if (playerListener != null) {
            player.removeListener(playerListener);
        }
        playerListener = new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                playbackErrorGeneration++;
                updateMetadata(mediaItem);
            }
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                startProgressUpdater();
                updateControlsUI();
                updateKeepScreenOn();
            }
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateKeepScreenOn();
            }
            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                updateKeepScreenOn();
            }
            @Override
            public void onRepeatModeChanged(int repeatMode) {
                updateControlsUI();
            }
            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
                updateControlsUI();
            }
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                String errorMsg = error.getErrorCodeName() + " - " + error.getMessage();
                Throwable cause = error.getCause();
                int depth = 0;
                while (cause != null && depth < 4) {
                    String causeMessage = cause.getMessage();
                    errorMsg += "\nCause: " + (causeMessage != null ? causeMessage : cause.toString());
                    cause = cause.getCause();
                    depth++;
                }
                Log.e(TAG, "Player Error: " + errorMsg);
                final String finalErrorMessage = errorMsg;
                long generation = ++playbackErrorGeneration;
                handler.postDelayed(() -> {
                    if (generation != playbackErrorGeneration || player == null
                            || player.getPlayerError() == null || !activityResumed) {
                        return;
                    }
                    showPlaybackError(finalErrorMessage);
                }, 600L);
            }
        };
        player.addListener(playerListener);
        
        // Enforce Default Mode: List Loop (REPEAT_MODE_ALL)
        if (player.getRepeatMode() == Player.REPEAT_MODE_OFF && !player.getShuffleModeEnabled()) {
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
        }
        
        if (player.getCurrentMediaItem() != null) {
            updateMetadata(player.getCurrentMediaItem());
        }
        startProgressUpdater();
        updateControlsUI();
    }

    private void showPlaybackError(String errorMessage) {
        String versionName = "unknown";
        try {
            versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
        } catch (Exception ignored) {
        }
        final String fullMessage = "版本: " + versionName + "\n\n" + errorMessage;
        runOnUiThread(() -> new AlertDialog.Builder(PlayerActivity.this)
                .setTitle("播放失败（完整错误）")
                .setMessage(fullMessage)
                .setNeutralButton("复制错误", (dialog, which) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("muggles-music playback error", fullMessage));
                        Toast.makeText(PlayerActivity.this, "完整错误已复制", Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton("关闭", null)
                .show());
    }

    private void updateMetadata(MediaItem mediaItem) {
        if (mediaItem == null) {
            if (lyricRequestCoordinator != null) lyricRequestCoordinator.cancel();
            return;
        }
        
        CharSequence titleSeq = mediaItem.mediaMetadata.title;
        String title = titleSeq != null ? titleSeq.toString() : null;
        String originalName = null;
        String lyrics = null;
        currentSongSource = "mg";
        currentSongMid = mediaItem.mediaId;

        if (mediaItem.mediaMetadata.extras != null) {
            originalName = mediaItem.mediaMetadata.extras.getString("original_name");
            lyrics = mediaItem.mediaMetadata.extras.getString("lyrics");
            currentSongSource = mediaItem.mediaMetadata.extras.getString("source", currentSongSource);
            currentSongMid = mediaItem.mediaMetadata.extras.getString("songmid", currentSongMid);
        }
        if (currentSongSource == null || currentSongSource.trim().isEmpty()) currentSongSource = "mg";
        if (currentSongMid == null || currentSongMid.trim().isEmpty()) currentSongMid = mediaItem.mediaId;
        loadLyricTimingAdjustment();
        
        String savedName = playerPreferences.getCurrentSongName();
        
        Log.d(TAG, "updateMetadata: title=" + title + ", lrc=" + (lyrics != null ? "yes" : "no"));
        
        String queryName = originalName != null ? originalName : (savedName != null ? savedName : title);
        
        if (queryName != null && !queryName.equals(savedName)) {
            playerPreferences.setCurrentSongName(queryName);
        }
        
        if (title != null) {
            tvBigTitle.setText(title);
            tvBigArtist.setText(mediaItem.mediaMetadata.artist != null ? mediaItem.mediaMetadata.artist : "");
        } else if (queryName != null) {
            tvBigTitle.setText(queryName);
            tvBigArtist.setText("加载中...");
        }

        // 1. 处理歌词状态
        boolean isCurrentlyScraping = mediaItem.mediaId.equals(currentScrapingId);
        if (lyrics != null && !lyrics.isEmpty()) {
            lyricRequestCoordinator.cancel();
            String lyricArtist = extractArtistFromLyrics(lyrics);
            if (lyricArtist != null && !lyricArtist.isEmpty()) {
                tvBigArtist.setText(lyricArtist);
            }
            parseLyrics(lyrics);
        } else if (!isCurrentlyScraping) {
            // 只有当不在抓取中时，才显示初始的“加载”状态
            showNoLyrics("加载歌词...");
        }

        // 2. 处理封面状态
        hasArtwork = false;
        if (mediaItem.mediaMetadata.artworkData != null) {
            byte[] data = mediaItem.mediaMetadata.artworkData;
            artworkLoader.loadMetadata(data);
            hasArtwork = true;
        } else if (mediaItem.mediaMetadata.artworkUri != null) {
            String uri = mediaItem.mediaMetadata.artworkUri.toString();
            Log.d(TAG, "Loading artwork from metadata: " + uri);
            artworkLoader.loadMetadata(uri);
            hasArtwork = true;
        } else {
            // 如果当前在抓取中，不要轻易重置图片，避免闪烁
            if (!isCurrentlyScraping) {
                artworkLoader.clear();
            } else {
                artworkLoader.prepareScrapedFallback();
            }
        }

        // 3. 触发抓取补全逻辑：只要缺歌词，就去尝试获取
        // 封面如果已存在，则不刮削
        if (lyrics == null || lyrics.isEmpty()) {
            // 只有当歌曲 ID 变化时（或者之前没记录到 ID）才重新触发
            if (!isCurrentlyScraping) {
                fetchMusicInfoForScraping(queryName, false);
            }
        } else {
            // 如果歌词已获取到，重置状态
            currentScrapingId = "";
        }
    }

    private void fetchMusicInfoForScraping(String songName, boolean forceScrape) {
        if (songName == null || songName.isEmpty()) {
            lyricRequestCoordinator.cancel();
            return;
        }
        
        MediaItem current = player != null ? player.getCurrentMediaItem() : null;
        if (current != null) {
            String targetId = current.mediaId + (forceScrape ? "_forced" : "");
            if (targetId.equals(currentScrapingId)) return; // 已经在刮削了
            currentScrapingId = targetId;
        }
        
        String source = currentSongSource != null ? currentSongSource : "mg";
        String songmid = currentSongMid != null ? currentSongMid : "";

        Bundle extras = current != null && current.mediaMetadata.extras != null
                ? current.mediaMetadata.extras
                : Bundle.EMPTY;
        String singer = extras.getString("singer", "");
        if (singer.isEmpty() && current != null && current.mediaMetadata.artist != null) {
            singer = current.mediaMetadata.artist.toString();
        }

        Map<String, String> params = new HashMap<>();
        putLyricParam(params, "source", source);
        putLyricParam(params, "songmid", songmid);
        putLyricParam(params, "songId", extras.getString("song_id", ""));
        putLyricParam(params, "name", extras.getString("original_name", songName));
        putLyricParam(params, "singer", singer);
        putLyricParam(params, "hash", extras.getString("hash", ""));
        putLyricParam(params, "interval", extras.getString("interval", ""));
        putLyricParam(params, "copyrightId", extras.getString("copyrightId", ""));
        putLyricParam(params, "albumId", extras.getString("albumId", ""));
        putLyricParam(params, "lrcUrl", extras.getString("lrcUrl", ""));
        putLyricParam(params, "mrcUrl", extras.getString("mrcUrl", ""));
        putLyricParam(params, "trcUrl", extras.getString("trcUrl", ""));
        putLyricParam(params, "quality", settingsPreferences
                .getPreferredQuality().getStorageValue());
        String artistHint = PlayerOptionActions.editableArtist(tvBigArtist.getText().toString());
        lyricRequestCoordinator.load(new LyricRequestCoordinator.Request(
                source,
                songmid,
                current != null ? current.mediaId : "",
                songName,
                artistHint,
                params,
                forceScrape
        ));
    }

    private void putLyricParam(Map<String, String> params, String key, String value) {
        if (value != null && !value.trim().isEmpty()) params.put(key, value);
    }

    private LyricRequestCoordinator createLyricRequestCoordinator() {
        return new LyricRequestCoordinator(
                (request, callback) -> {
                    return lyricCatalogActions.request(
                            request.getParams(), new LyricCatalogActions.Callback() {
                        @Override
                        public void onSuccess(String primary, String lxlyric,
                                              String translated) {
                            callback.onSuccess(primary, lxlyric, translated);
                        }

                        @Override
                        public void onFailure(String message) {
                            Log.e(TAG, "获取歌词失败: " + message);
                            callback.onFailure(message);
                        }
                    });
                },
                (request, callback) -> {
                    if (!isCurrentLyricRequest(request)) return;
                    songScraper.scrape(
                            request.getSongName(), request.getArtist(),
                            new SongScraper.ScrapeCallback() {
                                @Override
                                public void onSuccess(String artist, String picUrl, String lyrics) {
                                    callback.onSuccess(artist, picUrl, lyrics);
                                }

                                @Override
                                public void onError(String message) {
                                    callback.onFailure(message);
                                }
                            });
                },
                (delayMs, runnable) -> {
                    handler.postDelayed(runnable, delayMs);
                    return () -> handler.removeCallbacks(runnable);
                },
                LyricParser::chooseMoreComplete,
                new LyricRequestCoordinator.Listener() {
                    @Override
                    public void onLoading(LyricRequestCoordinator.Request request) {
                        if (optionAdapter != null) {
                            optionAdapter.setOptionDisabled(
                                    PlayerOptionActions.SAVE_LYRICS_LABEL, true);
                        }
                        Log.d(TAG, "Triggering initial scraper check for: "
                                + request.getSongName() + " (force="
                                + request.isForceExternal() + ")");
                        showNoLyrics("正在获取歌词...");
                    }

                    @Override
                    public void onServerLyrics(LyricRequestCoordinator.Request request,
                                               String primary, String translated) {
                        if (!isCurrentLyricRequest(request)) return;
                        parseLyrics(primary, translated);
                    }

                    @Override
                    public void onExternalResult(LyricRequestCoordinator.Request request,
                                                 String artist, String picUrl, String lyrics) {
                        if (!isCurrentLyricRequest(request)) return;
                        showExternalLyricResult(artist, picUrl, lyrics);
                    }

                    @Override
                    public void onTimeout(LyricRequestCoordinator.Request request) {
                        if (!isCurrentLyricRequest(request)) return;
                        Toast.makeText(PlayerActivity.this,
                                "刮削超时", Toast.LENGTH_SHORT).show();
                        stopScrapeAnimation();
                        if (lyricPlaybackState.getLines().isEmpty()) {
                            showNoLyrics("暂无歌词 (请求超时)");
                        }
                    }

                    @Override
                    public void onError(LyricRequestCoordinator.Request request, String message) {
                        if (!isCurrentLyricRequest(request)) return;
                        Log.e(TAG, "第三方刮削出错: " + message);
                        Toast.makeText(PlayerActivity.this,
                                "刮削失败：" + message, Toast.LENGTH_SHORT).show();
                        stopScrapeAnimation();
                        showNoLyrics("暂无歌词");
                    }
                });
    }

    private boolean isCurrentLyricRequest(LyricRequestCoordinator.Request request) {
        if (player == null || player.getCurrentMediaItem() == null) return false;
        MediaItem current = player.getCurrentMediaItem();
        Bundle extras = current.mediaMetadata.extras;
        String source = extras != null ? extras.getString("source", "") : "";
        String songmid = extras != null ? extras.getString("songmid", "") : "";
        if (songmid.isEmpty()) songmid = current.mediaId;
        return request.getSource().equals(source) && request.getSongmid().equals(songmid);
    }

    private LyricPlaybackState createLyricPlaybackState() {
        return new LyricPlaybackState(
                (primary, translated) -> {
                    List<LyricPlaybackState.ParsedLine> parsed = new ArrayList<>();
                    for (LyricParser.Line line : LyricParser.parse(primary, translated)) {
                        parsed.add(new LyricPlaybackState.ParsedLine(
                                line.timeMs, line.text, line.rawLine));
                    }
                    return parsed;
                },
                new LyricPlaybackState.TimingStore() {
                    @Override
                    public long get(String source, String songmid) {
                        return lyricTimingPreferences.getAdjustmentMs(source, songmid);
                    }

                    @Override
                    public void set(String source, String songmid, long offsetMs) {
                        lyricTimingPreferences.setAdjustmentMs(source, songmid, offsetMs);
                    }
                });
    }

    private void showExternalLyricResult(String artist, String picUrl, String lyrics) {
        boolean hasCover = picUrl != null && !picUrl.isEmpty();
        boolean hasLyrics = lyrics != null && !lyrics.isEmpty();
        String resultMessage;
        if (hasCover && hasLyrics) resultMessage = "刮削成功：封面+歌词";
        else if (hasCover) resultMessage = "刮削成功：封面";
        else if (hasLyrics) resultMessage = "刮削成功：歌词";
        else resultMessage = "刮削结果：无有效信息";
        Toast.makeText(this, resultMessage, Toast.LENGTH_SHORT).show();
        stopScrapeAnimation();

        if (artist != null && !artist.isEmpty()) tvBigArtist.setText(artist);
        if (!hasArtwork && hasCover) {
            artworkLoader.loadScraped(picUrl);
        }
        if (hasLyrics) {
            parseLyrics(lyrics);
        } else {
            showNoLyrics("暂无当前歌曲歌词");
        }
    }
    
    private void stopScrapeAnimation() {
        if (btnScrape != null) {
            ObjectAnimator animator = (ObjectAnimator) btnScrape.getTag();
            if (animator != null) {
                animator.cancel();
                animator.end();
                btnScrape.setRotation(0f);
                btnScrape.setTag(null);
            }
        }
    }


    private void showNoLyrics(String msg) {
        spatialLyricsView.setVisibility(View.GONE);
        tvNoLyrics.setVisibility(View.VISIBLE);
        tvNoLyrics.setText(msg);
    }

    private void parseLyrics(String lrc) {
        parseLyrics(lrc, "");
    }

    private void parseLyrics(String lrc, String translatedLrc) {
        LyricPlaybackState.Presentation presentation = lyricPlaybackState.setLyrics(
                lrc, translatedLrc, settingsPreferences.isLyricTranslationEnabled());
        Log.d(TAG, "Parsing lyrics length: " + presentation.getText().length()
                + ", lines: " + presentation.getLines().size());
        if (presentation.getKind() == LyricPlaybackState.Kind.UNTIMED) {
            showNoLyrics(presentation.getText());
            tvNoLyrics.setMaxLines(20);
            tvNoLyrics.setEllipsize(android.text.TextUtils.TruncateAt.END);
            spatialLyricsView.setLyrics(presentation.getLines());
            return;
        }
        if (presentation.getKind() == LyricPlaybackState.Kind.EMPTY) {
            showNoLyrics("没有歌词");
            spatialLyricsView.setLyrics(presentation.getLines());
            return;
        }
        spatialLyricsView.setVisibility(View.VISIBLE);
        tvNoLyrics.setVisibility(View.GONE);
        spatialLyricsView.setLyrics(presentation.getLines());
        long currentPosition = player != null ? player.getCurrentPosition() : 0L;
        updateLyric(currentPosition);
    }

    private String extractArtistFromLyrics(String lyrics) {
        if (lyrics == null || lyrics.isEmpty()) return null;
        try (BufferedReader reader = new BufferedReader(new StringReader(lyrics))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.toLowerCase().startsWith("[ar:")) {
                    int closing = line.indexOf(']');
                    if (closing > 4) {
                        return line.substring(4, closing).trim();
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private void startProgressUpdater() {
        handler.removeCallbacks(progressUpdater);
        handler.post(progressUpdater);
    }
    
    private void stopProgressUpdater() {
        handler.removeCallbacks(progressUpdater);
    }

    private void updateProgress() {
        if (player == null) return;
        long current = player.getCurrentPosition();
        long duration = player.getDuration();
        waveformProgressView.setProgress(current, duration > 0L ? duration : 0L);
        updateLyric(current);
    }

    private void updatePlaybackClock() {
        if (player == null) return;
        long current = player.getCurrentPosition();
        long duration = player.getDuration();
        if (player.isPlaying() && isControlsVisible()) {
            waveformProgressView.setProgress(current, duration > 0L ? duration : 0L);
        }
        updateLyric(current);
    }

    private void updateLyric(long currentPos) {
        LyricPlaybackState.PositionUpdate update = lyricPlaybackState.updatePosition(currentPos);
        if (update.isChanged()) {
            spatialLyricsView.setCurrentIndex(
                    update.getActiveIndex(), update.shouldAnimate());
        }
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    private void toggleOptionMenu(boolean show) {
        if (show) {
            layoutOptionMenu.setVisibility(View.VISIBLE);
            if (layoutMainContent != null) {
                layoutMainContent.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
            rvOptions.postDelayed(this::focusFirstOption, 100);
        } else {
            layoutOptionMenu.setVisibility(View.GONE);
            if (layoutMainContent != null) {
                layoutMainContent.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            if (controlsStateMachine != null) controlsStateMachine.reveal();
            btnScrape.post(btnScrape::requestFocus);
        }
    }

    private void focusFirstOption() {
        RecyclerView.ViewHolder first = rvOptions.findViewHolderForAdapterPosition(0);
        if (first != null) {
            first.itemView.requestFocus();
        } else {
            rvOptions.requestFocus();
        }
    }

    private void handleOptionClick(String option) {
        toggleOptionMenu(false);
        MediaItem current = player != null ? player.getCurrentMediaItem() : null;
        if (current == null) return;
        PlayerOptionActions.Action action = PlayerOptionActions.actionFor(option);
        if (action == null) return;
        
        final String mediaId = current.mediaId; // 原始文件名
        String songTitle = current.mediaMetadata.title != null ? current.mediaMetadata.title.toString() : mediaId;

        switch (action) {
            case EDIT_ARTIST:
                String currentArtist = PlayerOptionActions.editableArtist(
                        tvBigArtist.getText().toString());
                showEditDialog("修改歌手名", currentArtist, newArtist -> updateSongTag(mediaId, "artist", newArtist));
                break;
            case FORCE_SCRAPE:
                currentScrapingId = ""; 
                fetchMusicInfoForScraping(songTitle, true);
                break;
            case ADVANCE_LYRICS:
                adjustLyricTiming(250L);
                break;
            case DELAY_LYRICS:
                adjustLyricTiming(-250L);
                break;
            case RESET_LYRIC_TIMING:
                lyricPlaybackState.resetTiming();
                updateLyricNow();
                Toast.makeText(this, "已重置当前歌曲的歌词时间", Toast.LENGTH_SHORT).show();
                break;
            case SAVE_LYRICS:
                Toast.makeText(this, "当前版本不支持写回歌词或封面", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void adjustLyricTiming(long deltaMs) {
        long offsetMs = lyricPlaybackState.adjustTiming(deltaMs);
        updateLyricNow();
        if (offsetMs == 0L) {
            Toast.makeText(this, "当前歌曲已恢复原始歌词时间", Toast.LENGTH_SHORT).show();
            return;
        }
        String direction = offsetMs >= 0 ? "提前" : "延后";
        String seconds = String.format(java.util.Locale.US, "%.2f", Math.abs(offsetMs) / 1000f);
        Toast.makeText(this, "当前歌曲歌词已" + direction + " " + seconds + " 秒", Toast.LENGTH_SHORT).show();
    }

    private void updateLyricNow() {
        if (player != null) {
            updateLyric(player.getCurrentPosition());
        }
    }

    private void loadLyricTimingAdjustment() {
        lyricPlaybackState.onTrack(currentSongSource, currentSongMid);
    }

    private interface OnSubmitListener {
        void onSubmit(String value);
    }

    private void showEditDialog(String title, String defaultValue, OnSubmitListener listener) {
        android.widget.EditText editText = new android.widget.EditText(this);
        editText.setText(defaultValue);
        if (defaultValue != null) editText.setSelection(defaultValue.length());

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = editText.getText().toString().trim();
                    if (!value.isEmpty()) {
                        listener.onSubmit(value);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateSongTag(String fileName, String key, String value) {
        Toast.makeText(PlayerActivity.this, "lxserver 不支持此功能", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        activityResumed = false;
        playbackErrorGeneration++;
        updateKeepScreenOn();
        if (controlsStateMachine != null) controlsStateMachine.hideNow();
        asciiRibbonView.setPlaying(false);
        asciiRibbonView.setAnimationEnabled(false);
        vinylTurntableView.setPlaying(false);
        vinylTurntableView.onHostPause();
        spatialLyricsView.onHostPause();
        waveformProgressView.setAnimationEnabled(false);
        handler.removeCallbacks(showShortcutHintRunnable);
        handler.removeCallbacks(hideSeekHintRunnable);
        handler.removeCallbacks(seekForwardRunnable);
        handler.removeCallbacks(seekBackwardRunnable);
        isSeeking = false;
        layoutSeekProgress.setVisibility(View.GONE);
        if (lyricRequestCoordinator != null) lyricRequestCoordinator.cancel();
        currentScrapingId = "";
        stopScrapeAnimation();
        if (player != null && playerListener != null) {
            player.removeListener(playerListener);
        }
        playerListener = null;
        player = null;
        if (controllerConnection != null) controllerConnection.disconnect();
        artworkLoader.clear();
        stopProgressUpdater();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        boolean showTranslation = settingsPreferences.isLyricTranslationEnabled();
        if (showTranslation != lyricPlaybackState.isTranslationEnabled()
                && !lyricPlaybackState.getPrimaryLyrics().isEmpty()) {
            parseLyrics(lyricPlaybackState.getPrimaryLyrics(),
                    lyricPlaybackState.getTranslatedLyrics());
        }
        updateKeepScreenOn();
        asciiRibbonView.setAnimationEnabled(true);
        vinylTurntableView.onHostResume();
        spatialLyricsView.onHostResume();
        if (!isControlsVisible()) layoutMainContent.post(() -> layoutMainContent.requestFocus());
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        updateKeepScreenOn();
        super.onPause();
    }

    private void updateKeepScreenOn() {
        boolean playbackActive = player != null && (player.isPlaying()
                || (player.getPlaybackState() == Player.STATE_BUFFERING
                && player.getPlayWhenReady()));
        boolean keepScreenOn = activityResumed
                && settingsPreferences != null
                && settingsPreferences.isKeepScreenOnEnabled()
                && playbackActive;
        if (keepScreenOn) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onDestroy() {
        if (controlsStateMachine != null) controlsStateMachine.dispose();
        if (artworkLoader != null) artworkLoader.close();
        super.onDestroy();
    }
}
