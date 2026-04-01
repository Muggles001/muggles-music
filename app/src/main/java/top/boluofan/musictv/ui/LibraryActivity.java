package top.boluofan.musictv.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.ListData;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import top.boluofan.musictv.PlaylistAdapter;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;

public class LibraryActivity extends AppCompatActivity {
    private static final String TAG = "LibraryActivity";
    
    private RecyclerView rvPlaylists;
    private RecyclerView rvSongs;
    private PlaylistAdapter playlistAdapter;
    private LxMusicAdapter songAdapter;
    
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private FloatingPlayerWindow floatingPlayerWindow;
    
    private TextView tvPlaylistTitle;
    private TextView tvSongCount;
    private ImageButton btnSettings;
    private ImageButton btnBack;
    
    private ListData listData;
    private Playlist currentPlaylist;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        handler = new Handler(Looper.getMainLooper());
        initViews();
        setupRecyclerViews();
        setupListeners();
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        loadUserData();
    }

    private void initViews() {
        rvPlaylists = findViewById(R.id.rvPlaylists);
        rvSongs = findViewById(R.id.rvSongs);
        tvPlaylistTitle = findViewById(R.id.tvPlaylistTitle);
        tvSongCount = findViewById(R.id.tvSongCount);
        btnSettings = findViewById(R.id.btnSettings);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupRecyclerViews() {
        playlistAdapter = new PlaylistAdapter();
        rvPlaylists.setAdapter(playlistAdapter);
        rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
        
        songAdapter = new LxMusicAdapter();
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> showSettingsMenu());
        
        playlistAdapter.setOnItemClickListener(playlistName -> {
            loadPlaylistSongs(playlistName);
        });
        
        songAdapter.setOnItemClickListener((song, position) -> {
            playSongAtIndex(position);
        });
    }

    private void loadUserData() {
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, top.boluofan.musictv.ConfigActivity.class));
            finish();
            return;
        }
        
        apiService.getUserList(username, password).enqueue(new Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> call, Response<ListData> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listData = response.body();
                    updatePlaylistList();
                } else {
                    Toast.makeText(LibraryActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ListData> call, Throwable t) {
                Toast.makeText(LibraryActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updatePlaylistList() {
        if (listData == null) return;
        
        java.util.Map<String, List<String>> playlistData = new java.util.HashMap<>();
        
        Playlist defaultPlaylist = listData.getDefaultPlaylist();
        if (defaultPlaylist != null && defaultPlaylist.getSongs() != null && !defaultPlaylist.getSongs().isEmpty()) {
            List<String> songNames = new ArrayList<>();
            for (MusicInfo song : defaultPlaylist.getSongs()) {
                songNames.add(song.getName());
            }
            playlistData.put(defaultPlaylist.getName(), songNames);
        }
        
        Playlist lovePlaylist = listData.getLovePlaylist();
        if (lovePlaylist != null && lovePlaylist.getSongs() != null && !lovePlaylist.getSongs().isEmpty()) {
            List<String> songNames = new ArrayList<>();
            for (MusicInfo song : lovePlaylist.getSongs()) {
                songNames.add(song.getName());
            }
            playlistData.put(lovePlaylist.getName(), songNames);
        }
        
        if (listData.getUserList() != null) {
            for (Playlist playlist : listData.getUserList()) {
                List<String> songNames = new ArrayList<>();
                if (playlist.getSongs() != null) {
                    for (MusicInfo song : playlist.getSongs()) {
                        songNames.add(song.getName());
                    }
                }
                playlistData.put(playlist.getName(), songNames);
            }
        }
        
        playlistAdapter.setData(playlistData);
        
        if (!playlistData.isEmpty()) {
            String firstKey = playlistData.keySet().iterator().next();
            loadPlaylistSongs(firstKey);
        }
    }

    private void updateSongList() {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null) {
            songAdapter.setSongs(null);
            tvSongCount.setText("0 首歌曲");
            return;
        }
        
        songAdapter.setSongs(currentPlaylist.getSongs());
        tvPlaylistTitle.setText(currentPlaylist.getName());
        tvSongCount.setText(currentPlaylist.getSongCount() + " 首歌曲");
    }

    private void loadPlaylistSongs(String playlistName) {
        if (listData == null) return;
        
        Playlist targetPlaylist = null;
        
        Playlist defaultPlaylist = listData.getDefaultPlaylist();
        if (defaultPlaylist != null && defaultPlaylist.getName().equals(playlistName)) {
            targetPlaylist = defaultPlaylist;
        }
        
        if (targetPlaylist == null) {
            Playlist lovePlaylist = listData.getLovePlaylist();
            if (lovePlaylist != null && lovePlaylist.getName().equals(playlistName)) {
                targetPlaylist = lovePlaylist;
            }
        }
        
        if (targetPlaylist == null && listData.getUserList() != null) {
            for (Playlist playlist : listData.getUserList()) {
                if (playlist.getName().equals(playlistName)) {
                    targetPlaylist = playlist;
                    break;
                }
            }
        }
        
        if (targetPlaylist != null) {
            currentPlaylist = targetPlaylist;
            updateSongList();
        }
    }

    private void playSongAtIndex(int index) {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null || player == null) return;
        if (index < 0 || index >= currentPlaylist.getSongs().size()) return;
        
        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo song : currentPlaylist.getSongs()) {
            MediaItem item = createMediaItem(song);
            mediaItems.add(item);
        }
        
        player.setMediaItems(mediaItems, index, 0);
        player.prepare();
        player.play();
        
        songAdapter.setPlayingIndex(index);
    }

    private MediaItem createMediaItem(MusicInfo song) {
        Bundle extras = new Bundle();
        extras.putString("song_id", song.getId());
        extras.putString("source", song.getSource());
        extras.putString("songmid", song.getSongmid());
        extras.putString("pic_url", song.getPicUrl());
        extras.putString("original_name", song.getName());
        
        Uri artworkUri = null;
        String coverUrl = song.getPicUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            artworkUri = Uri.parse(coverUrl);
        }
        
        Uri resolveUri = MusicService.buildResolveUri(song.getSource(), song.getSongmid(), song.getName());
        
        MediaItem.Builder builder = new MediaItem.Builder()
                .setMediaId(song.getSongmid())
                .setUri(resolveUri);
        
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(song.getName())
                .setArtist(song.getSinger())
                .setAlbumTitle(song.getAlbumName())
                .setExtras(extras);
        
        if (artworkUri != null) {
            metadataBuilder.setArtworkUri(artworkUri);
        }
        
        builder.setMediaMetadata(metadataBuilder.build());
        
        return builder.build();
    }

    private void showSettingsMenu() {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, btnSettings);
        popup.getMenu().add("刷新列表");
        popup.getMenu().add("搜索音乐");
        popup.getMenu().add("退出登录");
        popup.setOnMenuItemClickListener(item -> {
            String title = (String) item.getTitle();
            if ("刷新列表".equals(title)) {
                loadUserData();
                return true;
            } else if ("搜索音乐".equals(title)) {
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            } else if ("退出登录".equals(title)) {
                logout();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void logout() {
        if (player != null) player.stop();
        LxRetrofitClient.clearConfig(this);
        Intent intent = new Intent(this, top.boluofan.musictv.ConfigActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                player.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        runOnUiThread(() -> {
                            songAdapter.setPlayerPlaying(isPlaying);
                        });
                    }
                });
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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBackPressTime < 2000) {
                finish();
            } else {
                Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                lastBackPressTime = currentTime;
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private long lastBackPressTime = 0;
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
    }
}
