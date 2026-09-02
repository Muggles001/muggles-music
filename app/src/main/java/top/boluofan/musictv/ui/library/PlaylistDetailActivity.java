package top.boluofan.musictv.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.ui.player.PlayerActivity;
import top.boluofan.musictv.player.queue.PlaybackQueue;
import top.boluofan.musictv.R;
import top.boluofan.musictv.ui.common.player.FloatingPlayerWindow;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.MusicApiProvider;
import top.boluofan.musictv.settings.BackendPreferences;
import top.boluofan.musictv.library.PlaylistActions;
import top.boluofan.musictv.player.session.MediaControllerConnection;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;
import top.boluofan.musictv.ui.presentation.PlaylistPlayCountFormatter;
import top.boluofan.musictv.ui.common.songlist.LxMusicAdapter;
import top.boluofan.musictv.ui.background.FluidBackgroundView;
import top.boluofan.musictv.ui.common.DialogHelper;
import top.boluofan.musictv.ui.common.FocusAnimationHelper;
import top.boluofan.musictv.util.PageWindow;
import android.net.Uri;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PlaylistDetailActivity extends AppCompatActivity {
    private static final String TAG = "PlaylistDetailActivity";
    public static final String EXTRA_LOCAL_PLAYLIST_ID = "local_playlist_id";
    public static final String EXTRA_LOCAL_PLAYLIST_NAME = "local_playlist_name";
    
    private ImageButton btnBack;
    private TextView tvTitle;
    private ImageView ivCover;
    private TextView tvPlaylistName;
    private TextView tvPlaylistInfo;
    private TextView tvPlaylistDesc;
    private TextView tvPlaylistSource;
    private TextView tvPlaylistPlayCount;
    private TextView tvPlaylistCreateTime;
    private ImageButton btnPlayAll;
    private ImageButton btnShuffle;
    private ImageButton btnFavorite;
    private ImageButton btnPrevPage;
    private ImageButton btnNextPage;
    private TextView tvPageNumber;
    private TextView tvPlaylistState;
    private RecyclerView rvSongs;
    private ProgressBar loadingProgress;
    private FluidBackgroundView fluidBackground;
    
    private String playlistId;
    private String playlistName;
    private String playlistSource;
    private String playlistCover;
    private boolean isLocalPlaylist;
    
    private LxMusicAdapter songAdapter;
    private MediaController player;
    private MediaControllerConnection controllerConnection;
    private Player.Listener playerListener;
    private FloatingPlayerWindow floatingPlayerWindow;
    private List<MusicInfo> songs = new ArrayList<>();
    private static final int SONGS_PER_PAGE = 8;
    private int currentPage = 0;
    private PlaylistActions.Operation collectPlaylistOperation;
    private PlaylistActions.Operation favoriteOperation;
    private PlaylistActions.Operation playlistLoadOperation;
    
    private final String[] SOURCES = {"mg", "kw", "kg", "tx", "wy"};
    private final String[] SOURCE_NAMES = {"咪咕", "酷我", "酷狗", "QQ音乐", "网易云"};
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);
        
        initViews();
        setupListeners();
        loadIntentData();
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        loadPlaylistDetail();
    }
    
    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        ivCover = findViewById(R.id.ivCover);
        tvPlaylistName = findViewById(R.id.tvPlaylistName);
        tvPlaylistInfo = findViewById(R.id.tvPlaylistInfo);
        tvPlaylistDesc = findViewById(R.id.tvPlaylistDesc);
        tvPlaylistSource = findViewById(R.id.tvPlaylistSource);
        tvPlaylistPlayCount = findViewById(R.id.tvPlaylistPlayCount);
        tvPlaylistCreateTime = findViewById(R.id.tvPlaylistCreateTime);
        btnPlayAll = findViewById(R.id.btnPlayAll);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnPrevPage = findViewById(R.id.btnPlaylistDetailPrevPage);
        btnNextPage = findViewById(R.id.btnPlaylistDetailNextPage);
        tvPageNumber = findViewById(R.id.tvPlaylistDetailPageNumber);
        tvPlaylistState = findViewById(R.id.tvPlaylistState);
        rvSongs = findViewById(R.id.rvSongs);
        loadingProgress = findViewById(R.id.loadingProgress);
        fluidBackground = findViewById(R.id.fluidBackground);

        FocusAnimationHelper.applyFocusAnimation(btnBack, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnPlayAll, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnShuffle, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnFavorite, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnPrevPage, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnNextPage, FocusAnimationHelper.Profile.ICON);
        
        songAdapter = new LxMusicAdapter();
        songAdapter.setNextFocusDownId(R.id.btnPlaylistDetailNextPage);
        songAdapter.setOnFirstItemUpListener(() -> btnPlayAll != null && btnPlayAll.requestFocus());
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSongs.setPreserveFocusAfterLayout(true);
    }
    
    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnBack.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return btnPlayAll.requestFocus();
            }
            return false;
        });
        
        FocusAnimationHelper.blockDownFocusEscape(btnPrevPage, btnNextPage);
        btnPlayAll.setOnClickListener(v -> {
            playAll(false);
            FocusAnimationHelper.keepFocusAfterPlayback(v);
        });
        btnShuffle.setOnClickListener(v -> {
            playAll(true);
            FocusAnimationHelper.keepFocusAfterPlayback(v);
        });
        btnFavorite.setOnClickListener(v -> {
            collectPlaylist();
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        View.OnKeyListener headerDownToSongs = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return focusFirstSong();
            }
            return false;
        };
        btnPlayAll.setOnKeyListener(headerDownToSongs);
        btnShuffle.setOnKeyListener(headerDownToSongs);
        btnFavorite.setOnKeyListener(headerDownToSongs);
        btnPrevPage.setOnClickListener(v -> {
            changePage(-1);
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        btnNextPage.setOnClickListener(v -> {
            changePage(1);
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        
        songAdapter.setOnItemClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnPlayClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnFullscreenClickListener((song, position) -> {
            if (playSongAtIndex(position)) {
                startActivity(new Intent(this, top.boluofan.musictv.ui.player.PlayerActivity.class));
            }
        });

        songAdapter.setOnFavClickListener((song, position) -> {
            collectSingleSong(song);
        });
    }

    private boolean focusFirstSong() {
        if (rvSongs == null || songAdapter == null || songAdapter.getItemCount() == 0) {
            return true;
        }
        return songAdapter.requestFocusAt(rvSongs, 0);
    }
    
    private void collectPlaylist() {
        if (isLocalPlaylist) {
            Toast.makeText(this, "该歌单已在我的歌单", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LxRetrofitClient.isLoggedIn(this) && !BackendPreferences.usesLocalLibrary(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, top.boluofan.musictv.ui.settings.connection.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(this));
            startActivity(intent);
            return;
        }
        
        if (songs.isEmpty()) {
            Toast.makeText(this, "歌单为空，无法收藏", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        PlaylistActions actions = new PlaylistActions(
                MusicApiProvider.get(this), username, password, token);
        String nameSnapshot = playlistName;
        String sourceSnapshot = playlistSource;
        String idSnapshot = playlistId;
        List<MusicInfo> songsSnapshot = new ArrayList<>(songs);

        btnFavorite.setEnabled(false);
        collectPlaylistOperation = actions.collectPlaylist(
                nameSnapshot, sourceSnapshot, idSnapshot, songsSnapshot,
                new PlaylistActions.CollectCallback() {
            @Override
            public void onCreated() {
                if (!isActivityUsable()) return;
                btnFavorite.setEnabled(true);
                Toast.makeText(PlaylistDetailActivity.this,
                        "收藏成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onOverwriteRequired(
                    PlaylistActions.OverwriteConfirmation confirmation) {
                if (!isActivityUsable()) return;
                btnFavorite.setEnabled(true);
                DialogHelper.showOverwriteConfirmDialog(
                        PlaylistDetailActivity.this, nameSnapshot,
                        new DialogHelper.IDialogCallback() {
                        @Override
                        public void onConfirm() {
                            if (!isActivityUsable()) return;
                            btnFavorite.setEnabled(false);
                            confirmation.confirm();
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
            }

            @Override
            public void onOverwritten() {
                if (!isActivityUsable()) return;
                btnFavorite.setEnabled(true);
                Toast.makeText(PlaylistDetailActivity.this,
                        "覆盖成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(PlaylistActions.Failure failure) {
                if (!isActivityUsable()) return;
                btnFavorite.setEnabled(true);
                String message;
                switch (failure.getKind()) {
                    case READ_RESPONSE:
                        message = "获取歌单失败";
                        break;
                    case TRANSPORT:
                        message = playlistFailureMessage(failure, "网络错误");
                        break;
                    case WRITE_RESPONSE:
                    default:
                        message = "收藏失败";
                        break;
                }
                Toast.makeText(PlaylistDetailActivity.this,
                        message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void collectSingleSong(MusicInfo song) {
        if (!LxRetrofitClient.isLoggedIn(this) && !BackendPreferences.usesLocalLibrary(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, top.boluofan.musictv.ui.settings.connection.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(this));
            startActivity(intent);
            return;
        }

        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        PlaylistActions actions = new PlaylistActions(
                MusicApiProvider.get(this), username, password, token);

        if (favoriteOperation != null) favoriteOperation.cancel();
        favoriteOperation = actions.toggleFavorite(song,
                new PlaylistActions.Callback<PlaylistActions.FavoriteResult>() {
            @Override
            public void onSuccess(PlaylistActions.FavoriteResult result) {
                if (!isActivityUsable()) return;
                favoriteOperation = null;
                songAdapter.setFavoriteState(song, result.isFavorite());
                Toast.makeText(PlaylistDetailActivity.this, result.isFavorite()
                                ? R.string.library_favorite_added
                                : R.string.library_favorite_removed,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(PlaylistActions.Failure failure) {
                if (!isActivityUsable()) return;
                favoriteOperation = null;
                Toast.makeText(PlaylistDetailActivity.this,
                        R.string.library_operation_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String playlistFailureMessage(PlaylistActions.Failure failure,
                                                 String fallback) {
        Throwable cause = failure.getCause();
        return failure.getKind() == PlaylistActions.Failure.Kind.TRANSPORT
                ? "网络错误: " + (cause != null ? cause.getMessage() : null)
                : fallback;
    }
    
    private void loadIntentData() {
        playlistId = getIntent().getStringExtra("playlist_id");
        playlistName = getIntent().getStringExtra("playlist_name");
        playlistSource = getIntent().getStringExtra("playlist_source");
        playlistCover = getIntent().getStringExtra("playlist_cover");
        isLocalPlaylist = getIntent().hasExtra(EXTRA_LOCAL_PLAYLIST_ID);
        if (isLocalPlaylist) {
            playlistId = getIntent().getStringExtra(EXTRA_LOCAL_PLAYLIST_ID);
            playlistName = getIntent().getStringExtra(EXTRA_LOCAL_PLAYLIST_NAME);
            playlistSource = null;
            playlistCover = null;
        }
        
        tvTitle.setText("歌单详情");
        tvPlaylistName.setText(playlistName);
        
        if (playlistCover != null && !playlistCover.isEmpty()) {
            Glide.with(this).load(playlistCover)
                    .placeholder(R.drawable.ic_cover_placeholder)
                    .into(ivCover);
        }
        
        tvPlaylistSource.setText(isLocalPlaylist ? "我的歌单" : getSourceName(playlistSource));
    }
    
    private String getSourceName(String source) {
        if (source == null) return "来源: 未知";
        for (int i = 0; i < SOURCES.length; i++) {
            if (source.equals(SOURCES[i])) {
                return "来源: " + SOURCE_NAMES[i];
            }
        }
        return "来源: " + source;
    }
    
    private void loadPlaylistDetail() {
        if (isLocalPlaylist) {
            loadLocalPlaylist();
            return;
        }
        if (playlistId == null || playlistSource == null) {
            showContentError("歌单信息不完整，无法加载");
            return;
        }
        
        showLoading(true);
        
        if (playlistLoadOperation != null) playlistLoadOperation.cancel();
        playlistLoadOperation = playlistActions().loadDetail(
                playlistSource, playlistId, new PlaylistActions.Callback<Playlist>() {
            @Override
            public void onSuccess(Playlist playlist) {
                if (!isActivityUsable()) return;
                playlistLoadOperation = null;
                showLoading(false);
                updateUI(playlist);
            }

            @Override
            public void onFailure(PlaylistActions.Failure failure) {
                if (!isActivityUsable()) return;
                playlistLoadOperation = null;
                showLoading(false);
                if (failure.getKind() == PlaylistActions.Failure.Kind.TRANSPORT) {
                    Throwable cause = failure.getCause();
                    showContentError("网络连接失败，请稍后重试");
                    Toast.makeText(PlaylistDetailActivity.this, "网络错误: "
                                    + (cause == null ? null : cause.getMessage()),
                            Toast.LENGTH_SHORT).show();
                } else {
                    showContentError("歌单加载失败，请稍后重试");
                    Toast.makeText(PlaylistDetailActivity.this,
                            "加载失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadLocalPlaylist() {
        if (!LxRetrofitClient.isLoggedIn(this) && !BackendPreferences.usesLocalLibrary(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        showLoading(true);
        if (playlistLoadOperation != null) playlistLoadOperation.cancel();
        playlistLoadOperation = playlistActions().loadData(
                new PlaylistActions.Callback<top.boluofan.musictv.catalog.model.ListData>() {
                    @Override
                    public void onSuccess(top.boluofan.musictv.catalog.model.ListData data) {
                        if (!isActivityUsable()) return;
                        playlistLoadOperation = null;
                        showLoading(false);
                        if (data.getUserList() == null) {
                            showContentError("歌单加载失败，请稍后重试");
                            Toast.makeText(PlaylistDetailActivity.this,
                                    "加载歌单失败", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Playlist localPlaylist = findLocalPlaylist(data.getUserList());
                        if (localPlaylist == null) {
                            Toast.makeText(PlaylistDetailActivity.this,
                                    "歌单不存在或已被删除", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        playlistName = localPlaylist.getName();
                        updateUI(localPlaylist);
                    }

                    @Override
                    public void onFailure(PlaylistActions.Failure failure) {
                        if (!isActivityUsable()) return;
                        playlistLoadOperation = null;
                        showLoading(false);
                        if (failure.getKind() == PlaylistActions.Failure.Kind.TRANSPORT) {
                            Throwable cause = failure.getCause();
                            showContentError("网络连接失败，请稍后重试");
                            Toast.makeText(PlaylistDetailActivity.this,
                                    "网络错误: " + (cause == null ? null : cause.getMessage()),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            showContentError("歌单加载失败，请稍后重试");
                            Toast.makeText(PlaylistDetailActivity.this,
                                    "加载歌单失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private PlaylistActions playlistActions() {
        return new PlaylistActions(
                MusicApiProvider.get(this),
                LxRetrofitClient.getUsername(this),
                LxRetrofitClient.getPassword(this),
                LxRetrofitClient.getToken(this));
    }

    private boolean isActivityUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private Playlist findLocalPlaylist(List<Playlist> playlists) {
        for (Playlist playlist : playlists) {
            if (playlistId != null && playlistId.equals(playlist.getId())) {
                return playlist;
            }
        }
        // Old server data can lack an id. The name remains a compatibility
        // fallback for those existing playlists.
        for (Playlist playlist : playlists) {
            if (playlistName != null && playlistName.equals(playlist.getName())) {
                return playlist;
            }
        }
        return null;
    }
    
    private void updateUI(Playlist playlist) {
        playlistName = playlist.getName();
        tvPlaylistName.setText(playlistName);
        tvPlaylistInfo.setText(playlist.getSongCount() + " 首歌曲");
        
        String creator = playlist.getCreator();
        if (creator != null && !creator.isEmpty()) {
            tvPlaylistSource.setText("来源: " + creator);
        }
        if (isLocalPlaylist) {
            tvPlaylistSource.setText("我的歌单");
        }
        
        String desc = playlist.getDesc();
        if (desc != null && !desc.isEmpty()) {
            tvPlaylistDesc.setText(desc);
            tvPlaylistDesc.setVisibility(View.VISIBLE);
        } else {
            tvPlaylistDesc.setVisibility(View.GONE);
        }
        
        String playCountText = PlaylistPlayCountFormatter.format(playlist);
        if (playCountText != null && !playCountText.isEmpty()) {
            tvPlaylistPlayCount.setText("播放: " + playCountText);
            tvPlaylistPlayCount.setVisibility(View.VISIBLE);
        } else {
            tvPlaylistPlayCount.setVisibility(View.GONE);
        }
        
        String createTime = playlist.getTime();
        if (createTime != null && !createTime.isEmpty()) {
            tvPlaylistCreateTime.setText("创建时间: " + createTime);
            tvPlaylistCreateTime.setVisibility(View.VISIBLE);
        } else if (playlist.getCreateTime() != null && playlist.getCreateTime() > 0) {
            String formattedTime = formatTime(playlist.getCreateTime());
            tvPlaylistCreateTime.setText("创建时间: " + formattedTime);
            tvPlaylistCreateTime.setVisibility(View.VISIBLE);
        } else {
            tvPlaylistCreateTime.setVisibility(View.GONE);
        }
        
        String coverUrl = playlist.getPicUrl();
        if (coverUrl != null && !coverUrl.isEmpty() && !isFinishing() && !isDestroyed()) {
            Glide.with(this).load(coverUrl)
                    .placeholder(R.drawable.ic_cover_placeholder)
                    .into(ivCover);
        }
        
        songs = playlist.getSongs() != null ? playlist.getSongs() : new ArrayList<>();
        currentPage = 0;
        updateSongPage();
    }

    private void updateSongPage() {
        PageWindow<MusicInfo> page = PageWindow.of(songs, currentPage, SONGS_PER_PAGE);
        currentPage = page.getPageIndex();
        songAdapter.setIndexOffset(page.getStartIndex());
        songAdapter.setSongs(page.getItems());
        songAdapter.setPlayingIndex(-1);
        tvPageNumber.setText(String.valueOf(currentPage + 1));
        boolean hasPrevious = page.hasPrevious();
        boolean hasNext = page.hasNext();
        btnPrevPage.setEnabled(hasPrevious);
        btnNextPage.setEnabled(hasNext);
        btnPrevPage.setAlpha(hasPrevious ? 1f : 0.35f);
        btnNextPage.setAlpha(hasNext ? 1f : 0.35f);
        updateContentState();
        requestFirstSongFocus();
    }

    private void changePage(int delta) {
        int nextPage = PageWindow.of(songs, currentPage, SONGS_PER_PAGE).pageAfter(delta);
        if (nextPage == currentPage) return;
        currentPage = nextPage;
        updateSongPage();
        rvSongs.scrollToPosition(0);
    }

    private void requestFirstSongFocus() {
        if (rvSongs == null || songAdapter == null || songAdapter.getItemCount() == 0) return;
        songAdapter.requestFocusAt(rvSongs, 0);
    }
    
    private void playAll(boolean shuffle) {
        if (songs.isEmpty()) {
            Toast.makeText(this, "没有可播放的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (player == null) return;
        
        PlaybackQueue queue = PlaybackQueue.from(songs);
        if (queue.isEmpty()) {
            Toast.makeText(this, "歌曲缺少可用的播放信息", Toast.LENGTH_SHORT).show();
            return;
        }

        int startIndex = shuffle ? (int) (Math.random() * queue.size()) : 0;

        queue.play(player, startIndex);
        
        Toast.makeText(this, shuffle ? "随机播放" : "播放全部", Toast.LENGTH_SHORT).show();
    }
    
    private boolean playSongAtIndex(int position) {
        if (songs.isEmpty() || player == null) return false;
        int globalPosition = currentPage * SONGS_PER_PAGE + position;
        PlaybackQueue queue = PlaybackQueue.from(songs);
        if (!queue.playSourceIndex(player, globalPosition)) {
            Toast.makeText(this, "该歌曲缺少播放信息", Toast.LENGTH_SHORT).show();
            return false;
        }
        songAdapter.setPlayingIndex(position);
        return true;
    }
    
    private String formatTime(Long timestamp) {
        if (timestamp == null || timestamp <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            updateContentControls(false);
            rvSongs.setVisibility(View.INVISIBLE);
            tvPlaylistState.setText("正在加载歌单…");
            tvPlaylistState.setVisibility(View.VISIBLE);
        } else {
            updateContentState();
        }
    }

    private void updateContentState() {
        if (songs.isEmpty()) {
            updateContentControls(false);
            rvSongs.setVisibility(View.INVISIBLE);
            tvPlaylistState.setText("这个歌单还没有歌曲");
            tvPlaylistState.setVisibility(View.VISIBLE);
            return;
        }
        updateContentControls(true);
        tvPlaylistState.setVisibility(View.GONE);
        rvSongs.setVisibility(View.VISIBLE);
    }

    private void showContentError(String message) {
        loadingProgress.setVisibility(View.GONE);
        updateContentControls(false);
        rvSongs.setVisibility(View.INVISIBLE);
        tvPlaylistState.setText(message);
        tvPlaylistState.setVisibility(View.VISIBLE);
    }

    private void updateContentControls(boolean hasContent) {
        boolean moveFocusToBack = !hasContent && (btnPlayAll.hasFocus() || btnShuffle.hasFocus()
                || btnFavorite.hasFocus() || btnPrevPage.hasFocus() || btnNextPage.hasFocus());
        btnPlayAll.setEnabled(hasContent);
        btnPlayAll.setFocusable(hasContent);
        btnShuffle.setEnabled(hasContent);
        btnShuffle.setFocusable(hasContent);
        btnFavorite.setFocusable(hasContent && btnFavorite.isEnabled());
        if (!hasContent) {
            btnPrevPage.setEnabled(false);
            btnNextPage.setEnabled(false);
            btnPrevPage.setAlpha(0.35f);
            btnNextPage.setAlpha(0.35f);
        }
        btnPrevPage.setFocusable(hasContent && btnPrevPage.isEnabled());
        btnNextPage.setFocusable(hasContent && btnNextPage.isEnabled());
        if (moveFocusToBack) btnBack.requestFocus();
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
    protected void onStart() {
        super.onStart();
        if (controllerConnection == null) {
            controllerConnection = new MediaControllerConnection(
                    this,
                    () -> !isFinishing() && !isDestroyed(),
                    resolvedController -> {
                        player = resolvedController;
                        playerListener = new Player.Listener() {
                            @Override
                            public void onIsPlayingChanged(boolean isPlaying) {
                                songAdapter.setPlayerPlaying(isPlaying);
                                songAdapter.restorePendingPlaybackFocus();
                            }

                            @Override
                            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                                songAdapter.notifyDataSetChanged();
                                songAdapter.restorePendingPlaybackFocus();
                            }
                        };
                        player.addListener(playerListener);
                    },
                    error -> Log.e(TAG, "Failed to connect media controller", error));
        }
        controllerConnection.connect();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (player != null && playerListener != null) {
            player.removeListener(playerListener);
        }
        playerListener = null;
        player = null;
        if (controllerConnection != null) controllerConnection.disconnect();
    }
    
    @Override
    protected void onDestroy() {
        if (collectPlaylistOperation != null) collectPlaylistOperation.cancel();
        if (favoriteOperation != null) favoriteOperation.cancel();
        if (playlistLoadOperation != null) playlistLoadOperation.cancel();
        super.onDestroy();
        if (floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            View currentFocus = getCurrentFocus();
            if (currentFocus == btnPrevPage || currentFocus == btnNextPage) {
                // Keep pagination as the vertical endpoint. The detail page
                // has no navigation rail, so repeated Down should not jump to
                // the back button either.
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            View currentFocus = getCurrentFocus();
            if (floatingPlayerWindow != null
                    && floatingPlayerWindow.handleDirectionalKey(keyCode, currentFocus)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
