package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private TextView tvSourceTitle;
    private ProgressBar loadingProgress;
    private ImageButton btnBack;
    
    private List<String> sourceList = new ArrayList<>();
    private List<Playlist> playlists = new ArrayList<>();
    private String currentSource = "mg";
    private int currentSourceIndex = 0;
    
    private final String[] SOURCES = {"mg", "kw", "kg", "tx", "wy"};
    private final String[] SOURCE_NAMES = {"咪咕", "酷我", "酷狗", "QQ音乐", "网易云"};
    
    private SquarePlaylistAdapter playlistAdapter;
    private int currentPage = 1;
    private boolean hasMore = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_square);
        
        initViews();
        setupRecyclerViews();
        setupListeners();
        
        loadSources();
    }

    private void initViews() {
        rvSourceList = findViewById(R.id.rvSourceList);
        rvPlaylists = findViewById(R.id.rvPlaylists);
        tvSourceTitle = findViewById(R.id.tvSourceTitle);
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
        rvPlaylists.setLayoutManager(new GridLayoutManager(this, 5));
        
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
        playlists.clear();
        
        if (playlistAdapter != null) {
            playlistAdapter.notifyDataSetChanged();
        }

        for (int i = 0; i < rvSourceList.getChildCount(); i++) {
            rvSourceList.getChildAt(i).setSelected(i == position);
        }

        tvSourceTitle.setText(SOURCE_NAMES[position] + " - 热门歌单");
        
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
        showLoading(true);
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getSongListList(currentSource, "", "hot", currentPage).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        SongListResult result = gson.fromJson(bodyStr, SongListResult.class);
                        if (result != null && result.getList() != null) {
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
                showLoading(false);
                Toast.makeText(SongSquareActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
}
