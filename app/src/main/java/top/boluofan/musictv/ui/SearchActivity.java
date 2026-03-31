package top.boluofan.musictv.ui;

import android.content.Intent;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.PlayerActivity;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

public class SearchActivity extends AppCompatActivity {
    private static final String TAG = "SearchActivity";
    
    private EditText etSearch;
    private Button btnSearch;
    private RecyclerView rvSourceList;
    private RecyclerView rvHotSearch;
    private RecyclerView rvSearchResults;
    private LxMusicAdapter songAdapter;
    private ProgressBar loadingProgress;
    private TextView tvNoResults;
    private TextView tvResultCount;
    private TextView tvHotSearchTitle;
    
    private View layoutMiniPlayer;
    private ImageView ivCurrentCover;
    private TextView tvCurrentTitle;
    private TextView tvCurrentArtist;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private View btnOpenPlayer;
    
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable positionUpdater;
    
    private String currentSource = "all";
    private int currentSourceIndex = 0;
    private int currentPage = 1;
    private boolean hasMore = true;
    private String lastKeyword = "";
    private List<MusicInfo> allResults = new ArrayList<>();
    
    private final String[] SOURCES = {"all", "kw", "kg", "tx", "wy", "mg"};
    private final String[] SOURCE_NAMES = {"全部", "酷我", "酷狗", "QQ音乐", "网易云", "咪咕"};
    
    private final String[] ALL_SOURCES = {"kw", "kg", "tx", "wy", "mg"};
    private final String[] ALL_SOURCE_NAMES = {"酷我", "酷狗", "QQ音乐", "网易云", "咪咕"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        initViews();
        setupRecyclerViews();
        setupListeners();
        setupMiniPlayer();
    }

    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        btnSearch = findViewById(R.id.btnSearch);
        rvSourceList = findViewById(R.id.rvSourceList);
        rvHotSearch = findViewById(R.id.rvHotSearch);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        loadingProgress = findViewById(R.id.loadingProgress);
        tvNoResults = findViewById(R.id.tvNoResults);
        tvResultCount = findViewById(R.id.tvResultCount);
        tvHotSearchTitle = findViewById(R.id.tvHotSearchTitle);
        
        songAdapter = new LxMusicAdapter();
        rvSearchResults.setAdapter(songAdapter);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        
        ImageButton btnBack = findViewById(R.id.btnBack);
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
        
        rvHotSearch.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvHotSearch.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<HotSearchViewHolder>() {
            @NonNull
            @Override
            public HotSearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                tv.setPadding(24, 12, 24, 12);
                tv.setTextSize(14);
                tv.setTextColor(getResources().getColorStateList(R.color.white));
                tv.setBackgroundResource(R.drawable.bg_tab_selected);
                tv.setFocusable(true);
                tv.setClickable(true);
                return new HotSearchViewHolder(tv);
            }
            
            @Override
            public void onBindViewHolder(@NonNull HotSearchViewHolder holder, int position) {
                String hotWord = "热门搜索" + (position + 1);
                holder.tv.setText(hotWord);
                holder.tv.setOnClickListener(v -> {
                    etSearch.setText(hotWord);
                    search(hotWord);
                });
            }
            
            @Override
            public int getItemCount() {
                return 5;
            }
        });
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > 0) {
                rvSourceList.getChildAt(0).requestFocus();
            }
        });
        
        songAdapter.setOnItemClickListener((song, position) -> {
            playSong(song);
        });
        
        songAdapter.setOnPlayClickListener((song, position) -> {
            playSong(song);
        });
        
        songAdapter.setOnFullscreenClickListener((song, position) -> {
            Intent intent = new Intent(this, top.boluofan.musictv.PlayerActivity.class);
            intent.putExtra("song", song.getName());
            intent.putExtra("artist", song.getSinger());
            intent.putExtra("source", song.getSource());
            intent.putExtra("songmid", song.getSongmid());
            startActivity(intent);
        });
    }
    
    private void selectSource(int position) {
        if (position < 0 || position >= SOURCES.length) return;
        
        currentSourceIndex = position;
        currentSource = SOURCES[position];
        
        if (rvSourceList.getAdapter() != null) {
            rvSourceList.getAdapter().notifyDataSetChanged();
        }
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > position) {
                View itemView = rvSourceList.getChildAt(position);
                if (itemView != null) {
                    itemView.requestFocus();
                }
            }
        });
        
        if (!lastKeyword.isEmpty()) {
            search(lastKeyword);
        }
    }
    
    private void setupListeners() {
        btnSearch.setOnClickListener(v -> {
            String keyword = etSearch.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            hideKeyboard();
            search(keyword);
        });
        
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                btnSearch.performClick();
                return true;
            }
            return false;
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
        if (currentItem != null) {
            CharSequence title = currentItem.mediaMetadata.title;
            CharSequence artist = currentItem.mediaMetadata.artist;
            
            tvCurrentTitle.setText(title != null ? title.toString() : "未知歌曲");
            tvCurrentArtist.setText(artist != null ? artist.toString() : "未知歌手");
            
            Uri artworkUri = currentItem.mediaMetadata.artworkUri;
            if (artworkUri != null) {
                Glide.with(this)
                        .load(artworkUri)
                        .placeholder(R.drawable.ic_cover_placeholder)
                        .into(ivCurrentCover);
            } else {
                ivCurrentCover.setImageResource(R.drawable.ic_cover_placeholder);
            }
        }
        
        btnPlayPause.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void search(String keyword) {
        lastKeyword = keyword;
        currentPage = 1;
        hasMore = true;
        allResults.clear();
        showLoading(true);
        
        if ("all".equals(currentSource)) {
            searchAllSources(keyword);
        } else {
            searchSingleSource(keyword, currentSource);
        }
    }
    
    private void searchAllSources(String keyword) {
        ExecutorService executor = Executors.newFixedThreadPool(ALL_SOURCES.length);
        List<MusicInfo>[] results = new List[ALL_SOURCES.length];
        int[] completed = new int[1];
        
        for (int i = 0; i < ALL_SOURCES.length; i++) {
            final int index = i;
            final String source = ALL_SOURCES[index];
            
            executor.submit(() -> {
                LxApiService apiService = LxRetrofitClient.getApiService(SearchActivity.this);
                apiService.searchMusic(keyword, source, 1, 30).enqueue(new Callback<List<MusicInfo>>() {
                    @Override
                    public void onResponse(Call<List<MusicInfo>> call, Response<List<MusicInfo>> response) {
                        synchronized (completed) {
                            if (response.isSuccessful() && response.body() != null) {
                                results[index] = response.body();
                            } else {
                                results[index] = new ArrayList<>();
                            }
                            completed[0]++;
                            
                            if (completed[0] == ALL_SOURCES.length) {
                                runOnUiThread(() -> {
                                    mergeAllResults(results);
                                });
                            }
                        }
                    }
                    
                    @Override
                    public void onFailure(Call<List<MusicInfo>> call, Throwable t) {
                        synchronized (completed) {
                            results[index] = new ArrayList<>();
                            completed[0]++;
                            
                            if (completed[0] == ALL_SOURCES.length) {
                                runOnUiThread(() -> {
                                    mergeAllResults(results);
                                });
                            }
                        }
                    }
                });
            });
        }
    }
    
    private void mergeAllResults(List<MusicInfo>[] results) {
        allResults.clear();
        for (List<MusicInfo> list : results) {
            if (list != null) {
                for (MusicInfo song : list) {
                    song.setSearchSource(getSourceName(song.getSource()));
                    allResults.add(song);
                }
            }
        }
        hasMore = false;
        showLoading(false);
        updateResults();
    }
    
    private String getSourceName(String source) {
        for (int i = 0; i < ALL_SOURCES.length; i++) {
            if (ALL_SOURCES[i].equals(source)) {
                return ALL_SOURCE_NAMES[i];
            }
        }
        return source;
    }
    
    private void searchSingleSource(String keyword, String source) {
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.searchMusic(keyword, source, currentPage, 30).enqueue(new Callback<List<MusicInfo>>() {
            @Override
            public void onResponse(Call<List<MusicInfo>> call, Response<List<MusicInfo>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<MusicInfo> result = response.body();
                    allResults.addAll(result);
                    hasMore = result.size() >= 30;
                    
                    String sourceName = "";
                    for (int i = 0; i < SOURCES.length; i++) {
                        if (SOURCES[i].equals(source)) {
                            sourceName = SOURCE_NAMES[i];
                            break;
                        }
                    }
                    for (MusicInfo song : result) {
                        song.setSearchSource(sourceName);
                    }
                    
                    updateResults();
                } else {
                    Toast.makeText(SearchActivity.this, "搜索失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<MusicInfo>> call, Throwable t) {
                showLoading(false);
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateResults() {
        if (allResults.isEmpty()) {
            tvNoResults.setVisibility(View.VISIBLE);
            rvSearchResults.setVisibility(View.GONE);
        } else {
            tvNoResults.setVisibility(View.GONE);
            rvSearchResults.setVisibility(View.VISIBLE);
            tvResultCount.setText("共 " + allResults.size() + " 首");
        }
        
        songAdapter.setSongs(allResults);
    }

    private void playSong(MusicInfo song) {
        if (player == null) {
            Toast.makeText(this, "播放器未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        
        MediaItem mediaItem = createMediaItem(song);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
        
        int index = allResults.indexOf(song);
        if (index >= 0) {
            songAdapter.setPlayingIndex(index);
        }
        
        updateMiniPlayerVisibility();
        
        Toast.makeText(this, "正在播放: " + song.getName(), Toast.LENGTH_SHORT).show();
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

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
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
                songAdapter.setPlayerPlaying(isPlaying);
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
    
    private static class SourceViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        SourceViewHolder(TextView tv) { super(tv); this.tv = tv; }
    }
    
    private static class HotSearchViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        HotSearchViewHolder(TextView tv) { super(tv); this.tv = tv; }
    }
}
