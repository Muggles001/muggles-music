package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.ui.adapter.SquarePlaylistAdapter;

import java.util.ArrayList;
import java.util.List;

public class SongSquareActivity extends AppCompatActivity {
    private static final String TAG = "SongSquareActivity";
    
    private RecyclerView rvSourceList;
    private RecyclerView rvPlaylists;
    private ProgressBar loadingProgress;
    private ImageButton btnBack;
    
    private List<String> sourceList = new ArrayList<>();
    private List<Playlist> playlists = new ArrayList<>();
    private String currentSource = "mg";
    private int currentSourceIndex = 0;
    
    private final String[] SOURCES = {"mg", "kw", "kg", "tx", "wy"};
    private final String[] SOURCE_NAMES = {"咪咕", "酷我", "酷狗", "QQ音乐", "网易云"};
    
    private SquarePlaylistAdapter playlistAdapter;
    private FloatingPlayerWindow floatingPlayerWindow;
    private int currentPage = 1;
    private boolean hasMore = true;
    private boolean isLoading = false;
    private boolean isLoadingMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_square);
        
        initViews();
        setupRecyclerViews();
        setupListeners();
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        loadSources();
    }

    private void initViews() {
        rvSourceList = findViewById(R.id.rvSourceList);
        rvPlaylists = findViewById(R.id.rvPlaylists);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerViews() {
        rvSourceList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
        androidx.recyclerview.widget.RecyclerView.Adapter<SourceViewHolder> sourceAdapter = 
                new androidx.recyclerview.widget.RecyclerView.Adapter<SourceViewHolder>() {
            @NonNull
            @Override
            public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source, parent, false);
                return new SourceViewHolder(view);
            }
            
            @Override
            public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
                holder.tvSourceName.setText(SOURCE_NAMES[position]);
                holder.ivRadio.setImageResource(position == currentSourceIndex ? R.drawable.radio_checked : R.drawable.radio_unchecked);
                
                holder.itemView.setOnClickListener(v -> selectSource(position));
            }
            
            @Override
            public int getItemCount() {
                return SOURCES.length;
            }
        };
        
        rvSourceList.setAdapter(sourceAdapter);
        
        playlistAdapter = new SquarePlaylistAdapter();
        rvPlaylists.setAdapter(playlistAdapter);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 5);
        rvPlaylists.setLayoutManager(gridLayoutManager);
        
        rvPlaylists.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null || isLoadingMore || !hasMore) return;
                
                int spanCount = gridLayoutManager.getSpanCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
                
                if (dy <= 0) return;
                
                int recyclerHeight = recyclerView.getHeight();
                int itemHeight = recyclerHeight / spanCount;
                if (itemHeight <= 0) return;
                
                int visibleRows = recyclerHeight / itemHeight;
                int thresholdRow = Math.max(1, visibleRows - 1);
                int thresholdPosition = totalItemCount - (thresholdRow * spanCount);
                
                if (firstVisiblePosition >= thresholdPosition - spanCount) {
                    loadMorePlaylists();
                }
            }
        });
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > 0) {
                rvSourceList.getChildAt(0).setSelected(true);
                rvSourceList.getChildAt(0).requestFocus();
            }
        });
        
        playlistAdapter.setOnItemClickListener(playlist -> {
            Intent intent = new Intent(this, PlaylistDetailActivity.class);
            intent.putExtra("playlist_id", playlist.getId());
            intent.putExtra("playlist_name", playlist.getName());
            intent.putExtra("playlist_source", playlist.getSource());
            intent.putExtra("playlist_cover", playlist.getCoverUrl());
            startActivity(intent);
        });
    }

    private void setupListeners() {
    }
    
    private void selectSource(int position) {
        if (position < 0 || position >= SOURCES.length) return;
        
        currentSourceIndex = position;
        String newSource = SOURCES[position];
        
        currentSource = newSource;
        currentPage = 1;
        hasMore = true;
        playlists.clear();
        
        if (playlistAdapter != null) {
            playlistAdapter.notifyDataSetChanged();
        }

        for (int i = 0; i < rvSourceList.getChildCount(); i++) {
            rvSourceList.getChildAt(i).setSelected(i == position);
        }
        
        loadPlaylists();
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > position) {
                View itemView = rvSourceList.getChildAt(position);
                if (itemView != null) {
                    itemView.requestFocus();
                }
            }
        });
    }
    
    private void loadSources() {
        selectSource(0);
    }

    private void loadPlaylists() {
        if (isLoading) return;
        isLoading = true;
        showLoading(true);
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getSongListList(currentSource, "", "hot", currentPage).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                isLoading = false;
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        SongListResult result = gson.fromJson(bodyStr, SongListResult.class);
                        if (result != null && result.getList() != null) {
                            if (currentPage == 1) {
                                playlists.clear();
                            }
                            hasMore = result.getList().size() >= 20;
                            playlists.addAll(result.getList());
                            updatePlaylistList();
                        }
                    } catch (Exception e) {
                        Toast.makeText(SongSquareActivity.this, "解析失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(SongSquareActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
                
                rvSourceList.post(() -> {
                    if (rvSourceList.getChildCount() > currentSourceIndex) {
                        View itemView = rvSourceList.getChildAt(currentSourceIndex);
                        if (itemView != null && itemView.isFocusable()) {
                            itemView.requestFocus();
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                isLoading = false;
                showLoading(false);
                Toast.makeText(SongSquareActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadMorePlaylists() {
        if (isLoadingMore || !hasMore) return;
        isLoadingMore = true;
        currentPage++;
        
        playlistAdapter.setShowFooter(true);
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getSongListList(currentSource, "", "hot", currentPage).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                isLoadingMore = false;
                playlistAdapter.setShowFooter(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        SongListResult result = gson.fromJson(bodyStr, SongListResult.class);
                        if (result != null && result.getList() != null) {
                            hasMore = result.getList().size() >= 20;
                            playlists.addAll(result.getList());
                            updatePlaylistList();
                        }
                    } catch (Exception e) {
                        currentPage--;
                        Toast.makeText(SongSquareActivity.this, "解析失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    currentPage--;
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                isLoadingMore = false;
                playlistAdapter.setShowFooter(false);
                currentPage--;
            }
        });
    }

    private void updatePlaylistList() {
        playlistAdapter.setData(playlists);
    }

    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    
    private static class SongListResult {
        private List<Playlist> list;
        
        public List<Playlist> getList() { return list; }
    }
    
    private static class SourceViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        TextView tvSourceName;
        ImageView ivRadio;
        SourceViewHolder(View view) {
            super(view);
            tvSourceName = view.findViewById(R.id.tvSourceName);
            ivRadio = view.findViewById(R.id.ivRadio);
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
