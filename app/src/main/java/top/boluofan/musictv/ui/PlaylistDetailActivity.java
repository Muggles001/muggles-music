package top.boluofan.musictv.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import android.net.Uri;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PlaylistDetailActivity extends AppCompatActivity {
    private static final String TAG = "PlaylistDetailActivity";
    
    private ImageButton btnBack;
    private TextView tvTitle;
    private ImageView ivCover;
    private TextView tvPlaylistName;
    private TextView tvPlaylistInfo;
    private TextView tvPlaylistSource;
    private TextView tvPlaylistPlayCount;
    private TextView tvPlaylistCreateTime;
    private TextView tvPlaylistDesc;
    private ImageButton btnPlayAll;
    private ImageButton btnShuffle;
    private ImageButton btnFavorite;
    private RecyclerView rvSongs;
    private ProgressBar loadingProgress;
    
    private String playlistId;
    private String playlistName;
    private String playlistSource;
    private String playlistCover;
    
    private LxMusicAdapter songAdapter;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private List<MusicInfo> songs = new ArrayList<>();
    
    private final String[] SOURCES = {"mg", "kw", "kg", "tx", "wy"};
    private final String[] SOURCE_NAMES = {"咪咕", "酷我", "酷狗", "QQ音乐", "网易云"};
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);
        
        initViews();
        setupListeners();
        loadIntentData();
        loadPlaylistDetail();
    }
    
    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        ivCover = findViewById(R.id.ivCover);
        tvPlaylistName = findViewById(R.id.tvPlaylistName);
        tvPlaylistInfo = findViewById(R.id.tvPlaylistInfo);
        tvPlaylistSource = findViewById(R.id.tvPlaylistSource);
        tvPlaylistPlayCount = findViewById(R.id.tvPlaylistPlayCount);
        tvPlaylistCreateTime = findViewById(R.id.tvPlaylistCreateTime);
        tvPlaylistDesc = findViewById(R.id.tvPlaylistDesc);
        btnPlayAll = findViewById(R.id.btnPlayAll);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnFavorite = findViewById(R.id.btnFavorite);
        rvSongs = findViewById(R.id.rvSongs);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        songAdapter = new LxMusicAdapter();
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
    }
    
    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        
        btnPlayAll.setOnClickListener(v -> playAll(false));
        btnShuffle.setOnClickListener(v -> playAll(true));
        
        btnFavorite.setOnClickListener(v -> {
            Toast.makeText(this, "收藏功能开发中", Toast.LENGTH_SHORT).show();
        });
        
        songAdapter.setOnItemClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnPlayClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnFullscreenClickListener((song, position) -> {
            playSongAtIndex(position);
            startActivity(new Intent(this, top.boluofan.musictv.PlayerActivity.class));
        });
    }
    
    private void loadIntentData() {
        playlistId = getIntent().getStringExtra("playlist_id");
        playlistName = getIntent().getStringExtra("playlist_name");
        playlistSource = getIntent().getStringExtra("playlist_source");
        playlistCover = getIntent().getStringExtra("playlist_cover");
        
        tvTitle.setText(playlistName != null ? playlistName : "歌单详情");
        tvPlaylistName.setText(playlistName);
        
        if (playlistCover != null && !playlistCover.isEmpty()) {
            Glide.with(this).load(playlistCover)
                    .placeholder(R.drawable.ic_cover_placeholder)
                    .into(ivCover);
        }
        
        String sourceName = getSourceName(playlistSource);
        tvPlaylistSource.setText(sourceName);
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
        if (playlistId == null || playlistSource == null) {
            return;
        }
        
        showLoading(true);
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getPlaylistDetail(playlistSource, playlistId, 1).enqueue(new Callback<Playlist>() {
            @Override
            public void onResponse(Call<Playlist> call, Response<Playlist> response) {
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
                showLoading(false);
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateUI(Playlist playlist) {
        tvPlaylistInfo.setText(playlist.getSongCount() + " 首歌曲");
        
        String creator = playlist.getCreator();
        if (creator != null && !creator.isEmpty()) {
            tvPlaylistSource.setText("来源: " + creator);
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
        
        if (playlist.getDesc() != null && !playlist.getDesc().isEmpty()) {
            tvPlaylistDesc.setText(playlist.getDesc());
            tvPlaylistDesc.setVisibility(View.VISIBLE);
        } else {
            tvPlaylistDesc.setVisibility(View.GONE);
        }
        
        String coverUrl = playlist.getPicUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            Glide.with(this).load(coverUrl)
                    .placeholder(R.drawable.ic_cover_placeholder)
                    .into(ivCover);
        }
        
        if (playlist.getSongs() != null) {
            songs = playlist.getSongs();
            songAdapter.setSongs(songs);
        }
    }
    
    private void playAll(boolean shuffle) {
        if (songs.isEmpty()) {
            Toast.makeText(this, "没有可播放的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (player == null) return;
        
        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo song : songs) {
            mediaItems.add(createMediaItem(song));
        }
        
        int startIndex = shuffle ? (int) (Math.random() * songs.size()) : 0;
        
        player.setMediaItems(mediaItems, startIndex, 0);
        player.prepare();
        player.play();
        
        Toast.makeText(this, shuffle ? "随机播放" : "播放全部", Toast.LENGTH_SHORT).show();
    }
    
    private void playSongAtIndex(int position) {
        if (songs.isEmpty() || player == null) return;
        
        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo song : songs) {
            mediaItems.add(createMediaItem(song));
        }
        
        player.setMediaItems(mediaItems, position, 0);
        player.prepare();
        player.play();
        songAdapter.setPlayingIndex(position);
    }
    
    private MediaItem createMediaItem(MusicInfo song) {
        Bundle extras = new Bundle();
        extras.putString("song_id", song.getId());
        extras.putString("source", song.getSource());
        extras.putString("songmid", song.getSongmid());
        extras.putString("pic_url", song.getPicUrl());
        extras.putString("original_name", song.getName());
        
        Uri artworkUri = song.getPicUrl() != null ? Uri.parse(song.getPicUrl()) : null;
        Uri resolveUri = MusicService.buildResolveUri(song.getSource(), song.getSongmid(), song.getName());
        
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(song.getName())
                .setArtist(song.getSinger())
                .setAlbumTitle(song.getAlbumName())
                .setExtras(extras);
        
        if (artworkUri != null) {
            metadataBuilder.setArtworkUri(artworkUri);
        }
        
        return new MediaItem.Builder()
                .setMediaId(song.getSongmid())
                .setUri(resolveUri)
                .setMediaMetadata(metadataBuilder.build())
                .build();
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
}
