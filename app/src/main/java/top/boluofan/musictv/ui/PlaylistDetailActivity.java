package top.boluofan.musictv.ui;

import android.content.ComponentName;
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
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.PlayerActivity;
import top.boluofan.musictv.PlaybackQueue;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.backend.MusicApiProvider;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import top.boluofan.musictv.util.DialogHelper;
import top.boluofan.musictv.util.FocusAnimationHelper;
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
    private RecyclerView rvSongs;
    private ProgressBar loadingProgress;
    
    private String playlistId;
    private String playlistName;
    private String playlistSource;
    private String playlistCover;
    private boolean isLocalPlaylist;
    
    private LxMusicAdapter songAdapter;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private Player.Listener playerListener;
    private FloatingPlayerWindow floatingPlayerWindow;
    private List<MusicInfo> songs = new ArrayList<>();
    private static final int SONGS_PER_PAGE = 8;
    private int currentPage = 0;
    
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
        rvSongs = findViewById(R.id.rvSongs);
        loadingProgress = findViewById(R.id.loadingProgress);
        
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
                startActivity(new Intent(this, top.boluofan.musictv.PlayerActivity.class));
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
            Intent intent = new Intent(this, top.boluofan.musictv.ConfigActivity.class);
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
        LxApiService apiService = MusicApiProvider.get(this);
        
        btnFavorite.setEnabled(false);
        
        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!isActivityUsable()) return;
                btnFavorite.setEnabled(true);
                
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(PlaylistDetailActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                top.boluofan.musictv.api.model.ListData listData = response.body();
                
                top.boluofan.musictv.api.model.Playlist existingPlaylist = null;
                if (listData.getUserList() != null) {
                    for (top.boluofan.musictv.api.model.Playlist p : listData.getUserList()) {
                        if (playlistName.equals(p.getName())) {
                            existingPlaylist = p;
                            break;
                        }
                    }
                }
                
                if (existingPlaylist != null) {
                    final top.boluofan.musictv.api.model.ListData finalListData = listData;
                    final top.boluofan.musictv.api.model.Playlist finalExistingPlaylist = existingPlaylist;
                    android.content.Context ctx = PlaylistDetailActivity.this;
                    DialogHelper.showOverwriteConfirmDialog(ctx, playlistName, new DialogHelper.IDialogCallback() {
                        @Override
                        public void onConfirm() {
                            if (!isActivityUsable()) return;
                            doCollectPlaylist(finalListData, finalExistingPlaylist);
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
                } else {
                    doCollectPlaylist(listData, null);
                }
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                if (!isActivityUsable()) return;
                btnFavorite.setEnabled(true);
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void doCollectPlaylist(top.boluofan.musictv.api.model.ListData listData, top.boluofan.musictv.api.model.Playlist existingPlaylist) {
        if (!isActivityUsable()) return;
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = MusicApiProvider.get(this);
        
        top.boluofan.musictv.api.model.Playlist newPlaylist;
        if (existingPlaylist != null) {
            newPlaylist = existingPlaylist;
            newPlaylist.setSongs(new ArrayList<>(songs));
            newPlaylist.setSongCount(songs.size());
            newPlaylist.setSource(playlistSource);
            newPlaylist.setSourceListId(playlistId);
        } else {
            newPlaylist = new top.boluofan.musictv.api.model.Playlist();
            newPlaylist.setId("playlist_" + System.currentTimeMillis());
            newPlaylist.setName(playlistName);
            newPlaylist.setSource(playlistSource);
            newPlaylist.setSourceListId(playlistId);
            newPlaylist.setSongs(new ArrayList<>(songs));
            newPlaylist.setSongCount(songs.size());
            
            if (listData.getUserList() == null) {
                listData.setUserList(new ArrayList<>());
            }
            listData.getUserList().add(newPlaylist);
        }
        
        btnFavorite.setEnabled(false);
        apiService.updateUserList(username, password, token, listData).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isActivityUsable()) return;
                btnFavorite.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(PlaylistDetailActivity.this, existingPlaylist != null ? "覆盖成功" : "收藏成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(PlaylistDetailActivity.this, "收藏失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                if (!isActivityUsable()) return;
                btnFavorite.setEnabled(true);
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void collectSingleSong(MusicInfo song) {
        if (!LxRetrofitClient.isLoggedIn(this) && !BackendPreferences.usesLocalLibrary(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, top.boluofan.musictv.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(this));
            startActivity(intent);
            return;
        }

        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = MusicApiProvider.get(this);

        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!isActivityUsable()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(PlaylistDetailActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null || userPlaylists.isEmpty()) {
                    Toast.makeText(PlaylistDetailActivity.this, "暂无歌单，请先在歌单库创建歌单", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[userPlaylists.size()];
                for (int i = 0; i < userPlaylists.size(); i++) {
                    playlistNames[i] = userPlaylists.get(i).getName();
                }

                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(PlaylistDetailActivity.this, "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
                    if (!isActivityUsable() || which < 0 || which >= userPlaylists.size()) return;
                    fetchAndAddSongToPlaylist(userPlaylists.get(which).getName(), finalSong);
                });
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                if (!isActivityUsable()) return;
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addSongToPlaylist(top.boluofan.musictv.api.model.ListData listData, top.boluofan.musictv.api.model.Playlist playlist, MusicInfo song) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = MusicApiProvider.get(this);

        List<MusicInfo> songList = playlist.getSongs();
        if (songList == null) {
            songList = new ArrayList<>();
        }

        for (MusicInfo m : songList) {
            if (m != null && song != null
                    && Objects.equals(m.getName(), song.getName())
                    && Objects.equals(m.getSource(), song.getSource())) {
                Toast.makeText(this, "歌曲已存在于此歌单", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        songList.add(0, song);
        playlist.setSongs(songList);
        playlist.setSongCount(songList.size());

        apiService.updateUserList(username, password, token, listData).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isActivityUsable()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(PlaylistDetailActivity.this, "已添加到「" + playlist.getName() + "」", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(PlaylistDetailActivity.this, "添加失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                if (!isActivityUsable()) return;
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAndAddSongToPlaylist(String playlistName, MusicInfo song) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = MusicApiProvider.get(this);

        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!isActivityUsable()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(PlaylistDetailActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null) {
                    Toast.makeText(PlaylistDetailActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.Playlist targetPlaylist = null;
                for (top.boluofan.musictv.api.model.Playlist p : userPlaylists) {
                    if (playlistName.equals(p.getName())) {
                        targetPlaylist = p;
                        break;
                    }
                }

                if (targetPlaylist == null) {
                    Toast.makeText(PlaylistDetailActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                addSongToPlaylist(listData, targetPlaylist, song);
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                if (!isActivityUsable()) return;
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
            return;
        }
        
        showLoading(true);
        
        LxApiService apiService = MusicApiProvider.get(this);
        apiService.getPlaylistDetail(playlistSource, playlistId, 1).enqueue(new Callback<Playlist>() {
            @Override
            public void onResponse(Call<Playlist> call, Response<Playlist> response) {
                if (!isActivityUsable()) return;
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    Playlist playlist = response.body();
                    updateUI(playlist);
                } else {
                    Toast.makeText(PlaylistDetailActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Playlist> call, Throwable t) {
                if (!isActivityUsable()) return;
                showLoading(false);
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        MusicApiProvider.get(this).getUserList(username, password, token)
                .enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
                    @Override
                    public void onResponse(
                            Call<top.boluofan.musictv.api.model.ListData> call,
                            Response<top.boluofan.musictv.api.model.ListData> response) {
                        if (!isActivityUsable()) return;
                        showLoading(false);
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getUserList() == null) {
                            Toast.makeText(PlaylistDetailActivity.this,
                                    "加载歌单失败", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Playlist localPlaylist = findLocalPlaylist(response.body().getUserList());
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
                    public void onFailure(
                            Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                        if (!isActivityUsable()) return;
                        showLoading(false);
                        Toast.makeText(PlaylistDetailActivity.this,
                                "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
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
        
        String playCountText = playlist.getFormattedPlayCount();
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

    private int getPageCount() {
        return Math.max(1, (songs.size() + SONGS_PER_PAGE - 1) / SONGS_PER_PAGE);
    }

    private void updateSongPage() {
        int from = Math.min(currentPage * SONGS_PER_PAGE, songs.size());
        int to = Math.min(from + SONGS_PER_PAGE, songs.size());
        List<MusicInfo> pageSongs = new ArrayList<>();
        if (from < to) pageSongs.addAll(songs.subList(from, to));
        songAdapter.setIndexOffset(currentPage * SONGS_PER_PAGE);
        songAdapter.setSongs(pageSongs);
        songAdapter.setPlayingIndex(-1);
        tvPageNumber.setText(String.valueOf(currentPage + 1));
        boolean hasPrevious = currentPage > 0;
        boolean hasNext = currentPage + 1 < getPageCount();
        btnPrevPage.setEnabled(hasPrevious);
        btnNextPage.setEnabled(hasNext);
        btnPrevPage.setAlpha(hasPrevious ? 1f : 0.35f);
        btnNextPage.setAlpha(hasNext ? 1f : 0.35f);
        requestFirstSongFocus();
    }

    private void changePage(int delta) {
        int nextPage = Math.max(0, Math.min(currentPage + delta, getPageCount() - 1));
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

        player.setMediaItems(queue.getMediaItems(), startIndex, 0);
        player.prepare();
        player.play();
        
        Toast.makeText(this, shuffle ? "随机播放" : "播放全部", Toast.LENGTH_SHORT).show();
    }
    
    private boolean playSongAtIndex(int position) {
        if (songs.isEmpty() || player == null) return false;
        int globalPosition = currentPage * SONGS_PER_PAGE + position;
        PlaybackQueue queue = PlaybackQueue.from(songs);
        int queueIndex = queue.queueIndexForSourceIndex(globalPosition);
        if (queueIndex < 0) {
            Toast.makeText(this, "该歌曲缺少播放信息", Toast.LENGTH_SHORT).show();
            return false;
        }
        player.setMediaItems(queue.getMediaItems(), queueIndex, 0);
        player.prepare();
        player.play();
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
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        final ListenableFuture<MediaController> pendingController =
                new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture = pendingController;
        pendingController.addListener(() -> {
            try {
                MediaController resolvedController = pendingController.get();
                if (isFinishing() || isDestroyed() || controllerFuture != pendingController) {
                    MediaController.releaseFuture(pendingController);
                    return;
                }
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
            } catch (Exception e) {
                Log.e(TAG, "Failed to connect media controller", e);
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (player != null && playerListener != null) {
            player.removeListener(playerListener);
        }
        playerListener = null;
        player = null;
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
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
