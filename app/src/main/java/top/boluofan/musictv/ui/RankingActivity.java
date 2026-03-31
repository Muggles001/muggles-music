package top.boluofan.musictv.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.PlayerActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public class RankingActivity extends AppCompatActivity {
    private static final String TAG = "RankingActivity";
    
    private RecyclerView rvSourceList;
    private RecyclerView rvBoards;
    private RecyclerView rvSongs;
    private ImageButton btnBack;
    private ImageButton btnPlayAll;
    private ImageButton btnShuffle;
    private ImageButton btnFavorite;
    private ProgressBar loadingProgress;
    
    private View layoutMiniPlayer;
    private ImageView ivCurrentCover;
    private TextView tvCurrentTitle;
    private TextView tvCurrentArtist;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private View btnOpenPlayer;
    
    private String currentSource = "tx";
    private int currentSourceIndex = 0;
    private String currentBoardId = "";
    private int currentBoardIndex = 0;
    
    private final String[] SOURCES = {"tx", "mg", "kw", "kg", "wy"};
    private final String[] SOURCE_NAMES = {"QQ音乐", "咪咕", "酷我", "酷狗", "网易云"};
    
    private List<BoardInfo> boards = new ArrayList<>();
    private List<MusicInfo> songs = new ArrayList<>();
    
    private LxMusicAdapter songAdapter;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable positionUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);
        
        initViews();
        setupRecyclerViews();
        setupListeners();
        setupMiniPlayer();
        
        loadBoards();
    }

    private void initViews() {
        rvSourceList = findViewById(R.id.rvSourceList);
        rvBoards = findViewById(R.id.rvBoards);
        rvSongs = findViewById(R.id.rvSongs);
        btnBack = findViewById(R.id.btnBack);
        btnPlayAll = findViewById(R.id.btnPlayAll);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnFavorite = findViewById(R.id.btnFavorite);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerViews() {
        rvSourceList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
        rvSourceList.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<SourceViewHolder>() {
            @NonNull
            @Override
            public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                tv.setPadding(32, 12, 32, 12);
                tv.setTextSize(14);
                tv.setTextColor(getResources().getColorStateList(R.color.white));
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setFocusable(true);
                tv.setClickable(true);
                return new SourceViewHolder(tv);
            }
            
            @Override
            public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
                holder.tv.setText(SOURCE_NAMES[position]);
                holder.tv.setBackgroundResource(R.drawable.selector_source_item);
                
                holder.tv.setOnClickListener(v -> selectSource(position));
            }
            
            @Override
            public int getItemCount() {
                return SOURCES.length;
            }
        });
        
        rvBoards.setLayoutManager(new LinearLayoutManager(this));
        rvBoards.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<BoardViewHolder>() {
            @NonNull
            @Override
            public BoardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = getLayoutInflater().inflate(R.layout.item_ranking_board, parent, false);
                return new BoardViewHolder(view);
            }
            
            @Override
            public void onBindViewHolder(@NonNull BoardViewHolder holder, int position) {
                BoardInfo board = boards.get(position);
                holder.tv.setText(board.name);
                holder.tv.setTag(position);
            }
            
            @Override
            public int getItemCount() {
                return boards.size();
            }
        });
        
        songAdapter = new LxMusicAdapter();
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        
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
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > 0) {
                rvSourceList.getChildAt(0).requestFocus();
            }
        });
    }
    
    private void setupListeners() {
        btnPlayAll.setOnClickListener(v -> playAll(false));
        btnShuffle.setOnClickListener(v -> playAll(true));
        btnFavorite.setOnClickListener(v -> {
            Toast.makeText(this, "收藏功能开发中", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void setupMiniPlayer() {
        layoutMiniPlayer = findViewById(R.id.layoutMiniPlayer);
        ivCurrentCover = findViewById(R.id.ivCurrentCover);
        tvCurrentTitle = findViewById(R.id.tvCurrentTitle);
        tvCurrentArtist = findViewById(R.id.tvCurrentArtist);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnOpenPlayer = findViewById(R.id.btnOpenPlayer);
        
        btnPlayPause.setOnClickListener(v -> {
            if (player != null) {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
            }
        });
        
        btnNext.setOnClickListener(v -> {
            if (player != null) {
                player.seekToNext();
            }
        });
        
        btnOpenPlayer.setOnClickListener(v -> {
            startActivity(new Intent(this, PlayerActivity.class));
        });
    }
    
    private void updateMiniPlayerVisibility() {
        boolean hasMedia = player != null && player.getMediaItemCount() > 0;
        layoutMiniPlayer.setVisibility(hasMedia ? View.VISIBLE : View.GONE);
    }
    
    private void updateMiniPlayerInfo() {
        if (player == null || player.getMediaItemCount() == 0) {
            return;
        }
        
        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem != null && currentItem.mediaMetadata != null) {
            MediaMetadata metadata = currentItem.mediaMetadata;
            CharSequence title = metadata.title;
            CharSequence artist = metadata.artist;
            
            tvCurrentTitle.setText(title != null ? title.toString() : "未知歌曲");
            tvCurrentArtist.setText(artist != null ? artist.toString() : "未知歌手");
            
            Uri artworkUri = metadata.artworkUri;
            if (artworkUri != null) {
                Glide.with(this)
                        .load(artworkUri)
                        .placeholder(R.drawable.ic_cover_placeholder)
                        .into(ivCurrentCover);
            } else {
                ivCurrentCover.setImageResource(R.drawable.ic_cover_placeholder);
            }
        } else {
            tvCurrentTitle.setText("未知歌曲");
            tvCurrentArtist.setText("未知歌手");
            ivCurrentCover.setImageResource(R.drawable.ic_cover_placeholder);
        }
        
        btnPlayPause.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }
    
    private void selectSource(int position) {
        if (position < 0 || position >= SOURCES.length) return;
        
        currentSourceIndex = position;
        currentSource = SOURCES[position];
        currentBoardId = "";
        currentBoardIndex = 0;
        boards.clear();
        songs.clear();
        
        if (rvBoards.getAdapter() != null) {
            rvBoards.getAdapter().notifyDataSetChanged();
        }
        if (songAdapter != null) {
            songAdapter.notifyDataSetChanged();
        }
        
        if (rvSourceList.getAdapter() != null) {
            rvSourceList.getAdapter().notifyDataSetChanged();
        }
        
        loadBoards();
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > position) {
                View itemView = rvSourceList.getChildAt(position);
                if (itemView != null) {
                    itemView.requestFocus();
                }
            }
        });
    }
    
    private void selectBoard(int position) {
        if (position < 0 || position >= boards.size()) return;
        
        currentBoardIndex = position;
        currentBoardId = getBangId(boards.get(position).id);
        
        if (rvBoards.getAdapter() != null) {
            rvBoards.getAdapter().notifyDataSetChanged();
        }
        
        loadSongs();
        
        rvBoards.post(() -> {
            if (rvBoards.getChildCount() > position) {
                View itemView = rvBoards.getChildAt(position);
                if (itemView != null) {
                    itemView.requestFocus();
                }
            }
        });
    }
    
    private String getBangId(String fullId) {
        if (fullId == null) return "";
        int index = fullId.indexOf("__");
        if (index >= 0 && index + 2 < fullId.length()) {
            return fullId.substring(index + 2);
        }
        return fullId;
    }
    
    private void loadBoards() {
        showLoading(true);
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getLeaderboardBoards(currentSource).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        Gson gson = new Gson();
                        JsonObject root = gson.fromJson(bodyStr, JsonObject.class);
                        JsonArray list = root.getAsJsonArray("list");
                        
                        boards.clear();
                        if (list != null) {
                            for (int i = 0; i < list.size(); i++) {
                                JsonObject item = list.get(i).getAsJsonObject();
                                BoardInfo board = new BoardInfo();
                                board.id = item.get("id").getAsString();
                                board.name = item.get("name").getAsString();
                                boards.add(board);
                            }
                        }
                        
                        if (rvBoards.getAdapter() != null) {
                            rvBoards.getAdapter().notifyDataSetChanged();
                        }
                        
                        rvBoards.post(() -> {
                            if (rvBoards.getChildCount() > 0) {
                                rvBoards.getChildAt(0).requestFocus();
                            } else {
                                rvBoards.requestFocus();
                            }
                        });
                        
                        if (!boards.isEmpty()) {
                            selectBoard(0);
                        }
                    } catch (Exception e) {
                        Toast.makeText(RankingActivity.this, "解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(RankingActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                showLoading(false);
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadSongs() {
        if (currentBoardId.isEmpty()) return;
        
        showLoading(true);
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getLeaderboardList(currentSource, currentBoardId, 1).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        Gson gson = new Gson();
                        JsonObject root = gson.fromJson(bodyStr, JsonObject.class);
                        JsonArray list = root.getAsJsonArray("list");
                        
                        songs.clear();
                        if (list != null) {
                            for (int i = 0; i < list.size(); i++) {
                                JsonObject item = list.get(i).getAsJsonObject();
                                MusicInfo music = new MusicInfo();
                                music.setId(item.has("id") ? item.get("id").getAsString() : "");
                                music.setName(item.has("name") ? item.get("name").getAsString() : "");
                                music.setSinger(item.has("singer") ? item.get("singer").getAsString() : "");
                                music.setSource(currentSource);
                                music.setSongmid(item.has("songmid") ? item.get("songmid").getAsString() : "");
                                music.setPicUrl(item.has("img") ? item.get("img").getAsString() : 
                                    (item.has("picUrl") ? item.get("picUrl").getAsString() : ""));
                                music.setAlbumName(item.has("album") ? item.get("album").getAsString() : "");
                                songs.add(music);
                            }
                        }
                        
                        songAdapter.setSongs(songs);
                    } catch (Exception e) {
                        Toast.makeText(RankingActivity.this, "解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(RankingActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                showLoading(false);
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
        
        updateMiniPlayerVisibility();
        
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
        
        updateMiniPlayerVisibility();
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
                updateMiniPlayerVisibility();
                updateMiniPlayerInfo();
                setupPlayerListener();
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
        if (positionUpdater != null) {
            mainHandler.removeCallbacks(positionUpdater);
        }
    }
    
    private void setupPlayerListener() {
        if (player == null) return;
        
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateMiniPlayerVisibility();
                updateMiniPlayerInfo();
            }
            
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateMiniPlayerInfo();
                updateMiniPlayerVisibility();
            }
            
            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                updateMiniPlayerInfo();
                updateMiniPlayerVisibility();
            }
        });
        
        positionUpdater = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isPlaying()) {
                    updateMiniPlayerInfo();
                }
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.post(positionUpdater);
    }
    
    private static class BoardInfo {
        String id;
        String name;
    }
    
    private static class SourceViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        SourceViewHolder(TextView tv) { super(tv); this.tv = tv; }
    }
    
    private class BoardViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        BoardViewHolder(View view) { 
            super(view); 
            tv = view.findViewById(R.id.tvBoardName);
            tv.setOnClickListener(v -> {
                int position = (int) tv.getTag();
                RankingActivity.this.selectBoard(position);
            });
        }
    }
}
