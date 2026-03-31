package top.boluofan.musictv.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.cardview.widget.CardView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.util.concurrent.ListenableFuture;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
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
import android.content.ComponentName;
import android.net.Uri;

public class LibraryFragment extends Fragment {
    private static final String TAG = "LibraryFragment";
    private RecyclerView rvPlaylists;
    private RecyclerView rvSongs;
    private TextView tvCurrentTitle;
    private TextView tvCurrentArtist;
    private ImageView ivCurrentCover;
    private CardView cvCurrentCover;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private View layoutPlayer;
    private View btnOpenPlayer;
    private TextView tvSongCount;
    private TextView tvPlaylistTitle;
    private TextView tabAllSongs;
    private TextView tabLoveList;
    private View btnSettings;
    
    private PlaylistAdapter playlistAdapter;
    private LxMusicAdapter songAdapter;
    
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private ObjectAnimator rotateAnim;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };
    
    private ListData listData;
    private Playlist currentPlaylist;
    private boolean showingAllSongs = false;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupRecyclerViews();
        setupListeners(view);
        
        loadUserData();
    }
    
    private void initViews(View view) {
        rvPlaylists = view.findViewById(R.id.rvPlaylists);
        rvSongs = view.findViewById(R.id.rvSongs);
        tvCurrentTitle = view.findViewById(R.id.tvCurrentTitle);
        tvCurrentArtist = view.findViewById(R.id.tvCurrentArtist);
        ivCurrentCover = view.findViewById(R.id.ivCurrentCover);
        cvCurrentCover = view.findViewById(R.id.cvCurrentCover);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        btnNext = view.findViewById(R.id.btnNext);
        layoutPlayer = view.findViewById(R.id.layoutPlayer);
        btnOpenPlayer = view.findViewById(R.id.btnOpenPlayer);
        tvSongCount = view.findViewById(R.id.tvSongCount);
        tvPlaylistTitle = view.findViewById(R.id.tvPlaylistTitle);
        tabAllSongs = view.findViewById(R.id.tabAllSongs);
        tabLoveList = view.findViewById(R.id.tabLoveList);
        btnSettings = view.findViewById(R.id.btnSettings);
        
        if (cvCurrentCover != null) {
            cvCurrentCover.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            rotateAnim = ObjectAnimator.ofFloat(cvCurrentCover, "rotation", 0f, 360f);
            rotateAnim.setDuration(10000);
            rotateAnim.setInterpolator(new LinearInterpolator());
            rotateAnim.setRepeatCount(ObjectAnimator.INFINITE);
            rotateAnim.setRepeatMode(ObjectAnimator.RESTART);
        }
    }
    
    private void setupRecyclerViews() {
        playlistAdapter = new PlaylistAdapter();
        rvPlaylists.setAdapter(playlistAdapter);
        rvPlaylists.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        songAdapter = new LxMusicAdapter();
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
    }
    
    private void setupListeners(View view) {
        btnPlayPause.setOnClickListener(v -> {
            if (player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
            }
        });
        
        btnNext.setOnClickListener(v -> {
            if (player != null) player.seekToNext();
        });
        
        if (btnOpenPlayer != null) {
            btnOpenPlayer.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), top.boluofan.musictv.PlayerActivity.class));
            });
        } else {
            layoutPlayer.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), top.boluofan.musictv.PlayerActivity.class));
            });
        }
        
        playlistAdapter.setOnItemClickListener(playlistName -> {
            loadPlaylistSongs(playlistName);
        });
        
        songAdapter.setOnItemClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnPlayClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnFullscreenClickListener((song, position) -> {
            Intent intent = new Intent(requireContext(), top.boluofan.musictv.PlayerActivity.class);
            intent.putExtra("song", song.getName());
            intent.putExtra("artist", song.getSinger());
            intent.putExtra("source", song.getSource());
            intent.putExtra("songmid", song.getSongmid());
            startActivity(intent);
        });
        
        tabAllSongs.setOnClickListener(v -> {
            showingAllSongs = false;
            tabAllSongs.setBackgroundColor(0xFF374151);
            tabLoveList.setBackgroundColor(0x00000000);
            if (listData != null && listData.getUserList() != null && !listData.getUserList().isEmpty()) {
                currentPlaylist = listData.getUserList().get(0);
                updateSongList(currentPlaylist);
            }
        });
        
        tabLoveList.setOnClickListener(v -> {
            showingAllSongs = true;
            tabLoveList.setBackgroundColor(0xFF374151);
            tabAllSongs.setBackgroundColor(0x00000000);
            if (listData != null && listData.getLoveList() != null) {
                Playlist lovePlaylist = new Playlist();
                lovePlaylist.setName("我的收藏");
                lovePlaylist.setSongs(listData.getLoveList());
                currentPlaylist = lovePlaylist;
                updateSongList(lovePlaylist);
            }
        });
        
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), top.boluofan.musictv.ConfigActivity.class));
        });
    }
    
    private void loadUserData() {
        if (!LxRetrofitClient.isLoggedIn(requireContext())) {
            Toast.makeText(requireContext(), "未登录，仅显示公共功能", Toast.LENGTH_SHORT).show();
            tabLoveList.setVisibility(View.GONE);
            tabAllSongs.setVisibility(View.GONE);
            return;
        }
        
        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());
        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        
        apiService.getUserList(username, password).enqueue(new Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> call, Response<ListData> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listData = response.body();
                    updatePlaylistList();
                }
            }
            
            @Override
            public void onFailure(Call<ListData> call, Throwable t) {
                Toast.makeText(requireContext(), "加载用户数据失败", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updatePlaylistList() {
        if (listData == null || listData.getUserList() == null) return;
        
        java.util.HashMap<String, java.util.List<String>> data = new java.util.HashMap<>();
        for (Playlist p : listData.getUserList()) {
            java.util.List<String> songs = new ArrayList<>();
            if (p.getSongs() != null) {
                for (MusicInfo m : p.getSongs()) {
                    songs.add(m.getName());
                }
            }
            data.put(p.getName(), songs);
        }
        playlistAdapter.setData(data);
        
        if (!listData.getUserList().isEmpty()) {
            currentPlaylist = listData.getUserList().get(0);
            updateSongList(currentPlaylist);
        }
    }
    
    private void loadPlaylistSongs(String playlistName) {
        if (listData == null || listData.getUserList() == null) return;
        
        for (Playlist p : listData.getUserList()) {
            if (p.getName().equals(playlistName)) {
                currentPlaylist = p;
                updateSongList(p);
                break;
            }
        }
    }
    
    private void updateSongList(Playlist playlist) {
        if (playlist == null || playlist.getSongs() == null) return;
        
        tvPlaylistTitle.setText(playlist.getName());
        tvSongCount.setText(playlist.getSongCount() + " 首");
        songAdapter.setSongs(playlist.getSongs());
    }
    
    private void playSongAtIndex(int position) {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null) return;
        
        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo song : currentPlaylist.getSongs()) {
            mediaItems.add(createMediaItem(song));
        }
        
        if (player != null) {
            player.setMediaItems(mediaItems, position, 0);
            player.prepare();
            player.play();
            songAdapter.setPlayingIndex(position);
        }
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
    
    @Override
    public void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(requireContext(), new ComponentName(requireContext(), MusicService.class));
        controllerFuture = new MediaController.Builder(requireContext(), sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                player.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                        if (rotateAnim != null) {
                            if (isPlaying) {
                                if (rotateAnim.isPaused()) rotateAnim.resume();
                                else if (!rotateAnim.isRunning()) rotateAnim.start();
                            } else {
                                rotateAnim.pause();
                            }
                        }
                    }
                    
                    @Override
                    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                        updateMiniPlayer(mediaItem);
                    }
                });
                handler.post(progressUpdater);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(requireContext()));
    }
    
    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(progressUpdater);
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
    }
    
    private void updateProgress() {
        if (player == null) return;
        
        long current = player.getCurrentPosition();
        long duration = player.getDuration();
        
        if (duration > 0) {
            tvCurrentArtist.setText(formatTime(current) + " / " + formatTime(duration));
        } else {
            tvCurrentArtist.setText("--:-- / --:--");
        }
    }
    
    private String formatTime(long millis) {
        if (millis <= 0) return "00:00";
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private void updateMiniPlayer(MediaItem mediaItem) {
        if (mediaItem == null) return;
        
        tvCurrentTitle.setText(mediaItem.mediaMetadata.title);
        tvCurrentArtist.setText(mediaItem.mediaMetadata.artist);
        
        if (mediaItem.mediaMetadata.artworkUri != null) {
            Glide.with(this).load(mediaItem.mediaMetadata.artworkUri)
                    .placeholder(R.drawable.ic_cover_placeholder)
                    .into(ivCurrentCover);
        }
        
        if (player != null && player.isPlaying() && rotateAnim != null) {
            if (rotateAnim.isPaused()) rotateAnim.resume();
            else if (!rotateAnim.isRunning()) rotateAnim.start();
        }
    }
}
