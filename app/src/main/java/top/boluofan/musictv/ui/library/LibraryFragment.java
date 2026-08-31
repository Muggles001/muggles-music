package top.boluofan.musictv.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.session.MediaController;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.player.queue.PlaybackQueue;
import top.boluofan.musictv.R;
import top.boluofan.musictv.ui.main.MainActivity;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.MusicApiProvider;
import top.boluofan.musictv.settings.BackendPreferences;
import top.boluofan.musictv.player.session.MediaControllerConnection;
import top.boluofan.musictv.catalog.model.ListData;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;
import top.boluofan.musictv.library.PlaylistActions;
import top.boluofan.musictv.ui.common.songlist.LxMusicAdapter;
import top.boluofan.musictv.ui.library.PlaylistAdapter;
import top.boluofan.musictv.ui.common.FocusAnimationHelper;
import top.boluofan.musictv.util.PageWindow;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import android.net.Uri;

public class LibraryFragment extends Fragment implements MainActivity.PrimaryPageKeyHandler {
    private static final String TAG = "LibraryFragment";
    private RecyclerView rvPlaylists;
    private RecyclerView rvSongs;
    private TextView tvSongCount;
    private TextView tvPlaylistTitle;
    private TextView tvLibraryModeLabel;
    private TextView tabAllSongs;
    private TextView tabLoveList;
    private ImageButton btnBackToPlaylists;
    private ImageButton btnPrevPage;
    private ImageButton btnNextPage;
    private ImageButton btnPlayAllSongs;
    private ImageButton btnPlayOrderToggle;
    private TextView tvPageNumber;
    private View libraryPagerBar;
    private View libraryActionBar;
    
    private PlaylistAdapter playlistAdapter;
    private LinearLayoutManager playlistLayoutManager;
    private LxMusicAdapter songAdapter;
    
    private MediaController player;
    private MediaControllerConnection controllerConnection;
    private Player.Listener playerListener;
    
    private ListData listData;
    private Playlist currentPlaylist;
    private boolean showingAllSongs = false;
    private boolean showingPlaylistDetail = false;
    private boolean shuffleEnabled = false;
    private static final int PLAYLISTS_PER_PAGE = 10;
    private static final int SONGS_PER_PAGE = 8;
    private int currentSongPage = 0;
    private int songFocusRequestGeneration = 0;
    private PlaylistActions.Operation loadUserOperation;

    private boolean isPageUsable() {
        return isAdded() && getView() != null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && isPagerFocused()) {
            // The page controls are the end of this vertical focus path. Keep
            // a held Down key from wrapping into the primary navigation rail.
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && rvSongs != null
                && rvSongs.getVisibility() == View.VISIBLE) {
            showPlaylistOverview();
            return true;
        }
        return false;
    }

    private boolean isPagerFocused() {
        if (getActivity() == null) return false;
        View currentFocus = getActivity().getCurrentFocus();
        return currentFocus == btnPrevPage || currentFocus == btnNextPage;
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundResource(0);
        
        initViews(view);
        setupRecyclerViews();
        setupListeners(view);
        
        loadUserData();
    }
    
    private void initViews(View view) {
        rvPlaylists = view.findViewById(R.id.rvPlaylists);
        rvSongs = view.findViewById(R.id.rvSongs);
        tvSongCount = view.findViewById(R.id.tvSongCount);
        tvPlaylistTitle = view.findViewById(R.id.tvPlaylistTitle);
        tvLibraryModeLabel = view.findViewById(R.id.tvLibraryModeLabel);
        tabAllSongs = view.findViewById(R.id.tabAllSongs);
        tabLoveList = view.findViewById(R.id.tabLoveList);
        btnBackToPlaylists = view.findViewById(R.id.btnBackToPlaylists);
        btnPrevPage = view.findViewById(R.id.btnLibraryPrevPage);
        btnNextPage = view.findViewById(R.id.btnLibraryNextPage);
        btnPlayAllSongs = view.findViewById(R.id.btnPlayAllSongs);
        btnPlayOrderToggle = view.findViewById(R.id.btnPlayOrderToggle);
        tvPageNumber = view.findViewById(R.id.tvLibraryPageNumber);
        libraryPagerBar = view.findViewById(R.id.libraryPagerBar);
        libraryActionBar = view.findViewById(R.id.libraryActionBar);
        FocusAnimationHelper.applyFocusAnimation(
                btnBackToPlaylists, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnPlayAllSongs, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(
                btnPlayOrderToggle, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnPrevPage, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnNextPage, FocusAnimationHelper.Profile.ICON);
    }
    
    private void setupRecyclerViews() {
        playlistAdapter = new PlaylistAdapter();
        playlistAdapter.setPageSize(PLAYLISTS_PER_PAGE);
        playlistAdapter.setNextFocusLeftId(R.id.tabLibrary);
        rvPlaylists.setAdapter(playlistAdapter);
        // Match the song list: one full-width row per playlist, which leaves
        // room for long names and gives the remote a predictable down path.
        playlistLayoutManager = new LinearLayoutManager(requireContext());
        rvPlaylists.setLayoutManager(playlistLayoutManager);
        
        songAdapter = new LxMusicAdapter();
        songAdapter.setNextFocusDownId(R.id.btnLibraryNextPage);
        songAdapter.setNextFocusLeftId(R.id.tabLibrary);
        songAdapter.setOnFirstItemUpListener(() -> {
            if (btnPlayAllSongs != null && btnPlayAllSongs.isShown()) {
                return btnPlayAllSongs.requestFocus();
            }
            return tabAllSongs != null && tabAllSongs.requestFocus();
        });
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSongs.setPreserveFocusAfterLayout(true);
    }
    
    private void setupListeners(View view) {
        playlistAdapter.setOnItemClickListener(playlistName -> {
            loadPlaylistSongs(playlistName);
        });

        FocusAnimationHelper.blockDownFocusEscape(btnPrevPage, btnNextPage);

        btnBackToPlaylists.setOnClickListener(v -> {
            showPlaylistOverview();
            rvPlaylists.postDelayed(this::requestFirstPlaylistFocus, 220L);
        });
        btnBackToPlaylists.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return btnPlayAllSongs != null && btnPlayAllSongs.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && showingPlaylistDetail) {
                // This is the top of the detail-page focus graph. Repeated
                // Up must not fall through to the main navigation rail.
                return true;
            }
            return false;
        });
        btnPrevPage.setOnClickListener(v -> {
            if (showingPlaylistDetail) {
                changeSongPage(-1);
            } else {
                playlistAdapter.setPage(playlistAdapter.getCurrentPage() - 1);
                updateLibraryPageUi();
            }
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        btnNextPage.setOnClickListener(v -> {
            if (showingPlaylistDetail) {
                changeSongPage(1);
            } else {
                playlistAdapter.setPage(playlistAdapter.getCurrentPage() + 1);
                updateLibraryPageUi();
            }
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        btnPlayAllSongs.setOnClickListener(v -> {
            playAllSongs();
            FocusAnimationHelper.keepFocusAfterPlayback(v);
        });
        btnPlayOrderToggle.setOnClickListener(v -> {
            shuffleEnabled = !shuffleEnabled;
            updatePlaybackModeButton();
            if (player != null) player.setShuffleModeEnabled(shuffleEnabled);
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        View.OnKeyListener actionDownToSongs = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                requestFirstSongFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (showingPlaylistDetail && btnBackToPlaylists.isShown()) {
                    return btnBackToPlaylists.requestFocus();
                }
                return true;
            }
            return false;
        };
        btnPlayAllSongs.setOnKeyListener(actionDownToSongs);
        btnPlayOrderToggle.setOnKeyListener(actionDownToSongs);
        
        songAdapter.setOnItemClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnPlayClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnFullscreenClickListener((song, position) -> {
            if (!playSongAtIndex(position)) return;
            Intent intent = new Intent(requireContext(), top.boluofan.musictv.ui.player.PlayerActivity.class);
            intent.putExtra("song", song.getName());
            intent.putExtra("artist", song.getSinger());
            intent.putExtra("source", song.getSource());
            intent.putExtra("songmid", song.getSongmid());
            startActivity(intent);
        });
        
        tabAllSongs.setOnClickListener(v -> {
            showingAllSongs = false;
            tabAllSongs.setSelected(true);
            tabLoveList.setSelected(false);
            if (listData != null && listData.getUserList() != null && !listData.getUserList().isEmpty()) {
                currentPlaylist = listData.getUserList().get(0);
                showPlaylistDetail(currentPlaylist, false);
            }
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        
        tabLoveList.setOnClickListener(v -> {
            showingAllSongs = true;
            tabLoveList.setSelected(true);
            tabAllSongs.setSelected(false);
            if (listData != null && listData.getLoveList() != null) {
                Playlist lovePlaylist = new Playlist();
                lovePlaylist.setName("我的收藏");
                lovePlaylist.setSongs(listData.getLoveList());
                currentPlaylist = lovePlaylist;
                showPlaylistDetail(lovePlaylist, false);
            }
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        
    }
    
    private void loadUserData() {
        if (!LxRetrofitClient.isLoggedIn(requireContext()) && !BackendPreferences.usesLocalLibrary(requireContext())) {
            Toast.makeText(requireContext(), "未登录，仅显示公共功能", Toast.LENGTH_SHORT).show();
            tabLoveList.setVisibility(View.GONE);
            tabAllSongs.setVisibility(View.GONE);
            return;
        }
        
        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());

        if (loadUserOperation != null) loadUserOperation.cancel();
        PlaylistActions actions = new PlaylistActions(
                MusicApiProvider.get(requireContext()), username, password, token);
        loadUserOperation = actions.loadData(new PlaylistActions.Callback<ListData>() {
            @Override
            public void onSuccess(ListData data) {
                if (!isPageUsable()) return;
                loadUserOperation = null;
                listData = data;
                updatePlaylistList();
            }

            @Override
            public void onFailure(PlaylistActions.Failure failure) {
                if (!isPageUsable()) return;
                loadUserOperation = null;
                if (failure.getKind() == PlaylistActions.Failure.Kind.TRANSPORT) {
                    Toast.makeText(requireContext(),
                            "加载用户数据失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    
    private void updatePlaylistList() {
        if (listData == null || listData.getUserList() == null) return;
        
        java.util.LinkedHashMap<String, java.util.List<String>> data = new java.util.LinkedHashMap<>();
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
        currentPlaylist = null;
        showPlaylistOverview();
    }
    
    private void loadPlaylistSongs(String playlistName) {
        if (listData == null || listData.getUserList() == null) return;
        
        for (Playlist p : listData.getUserList()) {
            if (java.util.Objects.equals(p.getName(), playlistName)) {
                // Reuse the public playlist-detail screen so a playlist from
                // "我的歌单" has the exact same layout and navigation model as
                // one opened from 歌单广场.
                Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
                intent.putExtra(PlaylistDetailActivity.EXTRA_LOCAL_PLAYLIST_ID, p.getId());
                intent.putExtra(PlaylistDetailActivity.EXTRA_LOCAL_PLAYLIST_NAME, p.getName());
                startActivity(intent);
                break;
            }
        }
    }

    private void showPlaylistOverview() {
        int distance = transitionDistance();
        songFocusRequestGeneration++;
        showingPlaylistDetail = false;
        rvSongs.animate().cancel();
        libraryActionBar.animate().cancel();
        rvPlaylists.animate().cancel();
        rvPlaylists.setVisibility(View.VISIBLE);
        rvPlaylists.setAlpha(0f);
        rvPlaylists.setTranslationX(-distance);
        rvPlaylists.animate().alpha(1f).translationX(0f)
                .setDuration(getResources().getInteger(R.integer.lx_motion_panel)).start();

        rvSongs.animate().alpha(0f).translationX(distance)
                .setDuration(getResources().getInteger(R.integer.lx_motion_focus))
                .withEndAction(() -> {
                    rvSongs.setVisibility(View.GONE);
                    rvSongs.setTranslationX(0f);
                }).start();
        libraryActionBar.setVisibility(View.VISIBLE);
        libraryActionBar.setAlpha(1f);
        libraryActionBar.setTranslationX(0f);
        tvLibraryModeLabel.setText("歌单列表");
        libraryPagerBar.setVisibility(View.VISIBLE);
        setSongListConstraints(false);
        configurePagerFocus(false);
        configureDetailHeaderFocus(false);
        btnBackToPlaylists.setVisibility(View.GONE);
        btnPlayAllSongs.setVisibility(View.GONE);
        btnPlayOrderToggle.setVisibility(View.GONE);
        tvPlaylistTitle.setText("歌单列表");
        int count = listData != null && listData.getUserList() != null
                ? listData.getUserList().size() : 0;
        tvSongCount.setText(count + " 个");
        updateLibraryPageUi();
    }

    private void showPlaylistDetail(Playlist playlist) {
        showPlaylistDetail(playlist, true);
    }

    private void showPlaylistDetail(Playlist playlist, boolean focusFirstSong) {
        if (playlist == null) return;
        int distance = transitionDistance();
        currentPlaylist = playlist;
        showingPlaylistDetail = true;
        libraryPagerBar.setVisibility(View.VISIBLE);
        setSongListConstraints(true);
        configurePagerFocus(true);
        configureDetailHeaderFocus(true);
        btnBackToPlaylists.setVisibility(View.VISIBLE);
        btnPlayAllSongs.setVisibility(View.VISIBLE);
        btnPlayOrderToggle.setVisibility(View.VISIBLE);
        updatePlaybackModeButton();
        updateSongList(playlist);

        rvSongs.animate().cancel();
        libraryActionBar.animate().cancel();
        libraryActionBar.setVisibility(View.VISIBLE);
        libraryActionBar.setAlpha(1f);
        libraryActionBar.setTranslationX(0f);
        tvLibraryModeLabel.setText("播放控制");
        // A playlist row is about to be hidden. Hand focus to a visible
        // detail control first so Android cannot fall back to another rail tab.
        if (focusFirstSong) btnPlayAllSongs.requestFocus();
        rvPlaylists.setVisibility(View.GONE);
        rvSongs.setVisibility(View.VISIBLE);
        rvSongs.setAlpha(0f);
        rvSongs.setTranslationX(distance);
        rvSongs.animate().alpha(1f).translationX(0f)
                .setDuration(getResources().getInteger(R.integer.lx_motion_panel)).start();
        if (focusFirstSong) {
            rvSongs.postDelayed(this::requestFirstSongFocus,
                    getResources().getInteger(R.integer.lx_motion_panel));
        }
    }

    private void requestFirstPlaylistFocus() {
        if (rvPlaylists == null || playlistAdapter == null || playlistAdapter.getItemCount() == 0) {
            return;
        }
        rvPlaylists.scrollToPosition(0);
        rvPlaylists.post(() -> {
            RecyclerView.ViewHolder holder = rvPlaylists.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void requestFirstSongFocus() {
        int generation = ++songFocusRequestGeneration;
        if (rvSongs == null || songAdapter == null || songAdapter.getItemCount() == 0) {
            return;
        }
        if (!isPageUsable() || !showingPlaylistDetail || !rvSongs.isShown()
                || generation != songFocusRequestGeneration) {
            return;
        }
        songAdapter.requestFocusAt(rvSongs, 0);
    }

    private int transitionDistance() {
        return Math.round(28f * getResources().getDisplayMetrics().density);
    }

    private void configurePagerFocus(boolean songPage) {
        int upId = songPage ? R.id.rvSongs : R.id.rvPlaylists;
        btnPrevPage.setNextFocusUpId(upId);
        btnNextPage.setNextFocusUpId(upId);
    }

    private void configureDetailHeaderFocus(boolean detail) {
        if (detail) {
            btnBackToPlaylists.setNextFocusRightId(R.id.btnPlayAllSongs);
            btnBackToPlaylists.setNextFocusDownId(R.id.btnPlayAllSongs);
            btnPlayAllSongs.setNextFocusLeftId(R.id.btnBackToPlaylists);
        } else {
            btnPlayAllSongs.setNextFocusLeftId(R.id.tabLibrary);
        }
    }

    private void setSongListConstraints(boolean detail) {
        if (rvSongs == null) return;
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) rvSongs.getLayoutParams();
        if (detail) {
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
            params.bottomToTop = R.id.libraryPagerBar;
        } else {
            params.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        }
        rvSongs.setLayoutParams(params);
    }

    private void updateLibraryPageUi() {
        if (playlistAdapter == null || tvPageNumber == null) return;
        int page = playlistAdapter.getCurrentPage();
        int pageCount = playlistAdapter.getPageCount();
        if (showingPlaylistDetail) {
            PageWindow<MusicInfo> songPage = buildSongPage();
            page = songPage.getPageIndex();
            pageCount = songPage.getPageCount();
        }
        tvPageNumber.setText(String.valueOf(page + 1));
        boolean hasPrevious = page > 0;
        boolean hasNext = page + 1 < pageCount;
        btnPrevPage.setEnabled(hasPrevious);
        btnNextPage.setEnabled(hasNext);
        btnPrevPage.setAlpha(hasPrevious ? 1f : 0.35f);
        btnNextPage.setAlpha(hasNext ? 1f : 0.35f);
    }

    private void updatePlaybackModeButton() {
        if (btnPlayOrderToggle == null) return;
        btnPlayOrderToggle.setImageResource(shuffleEnabled
                ? R.drawable.ic_shuffle : R.drawable.ic_repeat);
        btnPlayOrderToggle.setContentDescription(shuffleEnabled
                ? "切换为顺序播放" : "切换为随机播放");
        btnPlayOrderToggle.setSelected(shuffleEnabled);
    }
    
    private void updateSongList(Playlist playlist) {
        if (playlist == null || playlist.getSongs() == null) return;
        
        tvPlaylistTitle.setText(playlist.getName());
        currentSongPage = 0;
        updateSongPage();
    }

    private PageWindow<MusicInfo> buildSongPage() {
        List<MusicInfo> songs = currentPlaylist != null ? currentPlaylist.getSongs() : null;
        return PageWindow.of(songs != null ? songs : java.util.Collections.emptyList(),
                currentSongPage, SONGS_PER_PAGE);
    }

    private void updateSongPage() {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null) return;
        List<MusicInfo> songs = currentPlaylist.getSongs();
        PageWindow<MusicInfo> page = PageWindow.of(songs, currentSongPage, SONGS_PER_PAGE);
        currentSongPage = page.getPageIndex();
        songAdapter.setIndexOffset(page.getStartIndex());
        songAdapter.setSongs(page.getItems());
        songAdapter.setPlayingIndex(-1);
        tvSongCount.setText(songs.size() + " 首");
        updateLibraryPageUi();
    }

    private void changeSongPage(int delta) {
        int nextPage = buildSongPage().pageAfter(delta);
        if (nextPage == currentSongPage) return;
        currentSongPage = nextPage;
        updateSongPage();
        rvSongs.scrollToPosition(0);
        requestFirstSongFocus();
    }

    private void playAllSongs() {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null
                || currentPlaylist.getSongs().isEmpty()) return;
        if (currentSongPage != 0) {
            currentSongPage = 0;
            updateSongPage();
        }
        playSongAtGlobalIndex(0);
    }
    
    private boolean playSongAtIndex(int position) {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null) return false;
        int globalPosition = currentSongPage * SONGS_PER_PAGE + position;
        return playSongAtGlobalIndex(globalPosition);
    }

    private boolean playSongAtGlobalIndex(int globalPosition) {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null || player == null) {
            return false;
        }
        PlaybackQueue queue = PlaybackQueue.from(currentPlaylist.getSongs());
        if (!queue.playSourceIndex(player, globalPosition, shuffleEnabled)) {
            Toast.makeText(requireContext(), "该歌曲缺少播放信息", Toast.LENGTH_SHORT).show();
            return false;
        }
        int pageStart = currentSongPage * SONGS_PER_PAGE;
        int pageEnd = pageStart + songAdapter.getItemCount();
        songAdapter.setPlayingIndex(globalPosition >= pageStart && globalPosition < pageEnd
                ? globalPosition - pageStart : -1);
        return true;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (controllerConnection == null) {
            controllerConnection = new MediaControllerConnection(
                    requireContext(),
                    this::isResumed,
                    resolved -> {
                        player = resolved;
                        if (playerListener != null) player.removeListener(playerListener);
                        playerListener = new Player.Listener() {
                            @Override
                            public void onIsPlayingChanged(boolean isPlaying) {
                                songAdapter.setPlayerPlaying(isPlaying);
                                songAdapter.restorePendingPlaybackFocus();
                            }
                        };
                        player.addListener(playerListener);
                    },
                    Throwable::printStackTrace);
        }
        controllerConnection.connect();
    }
    
    @Override
    public void onPause() {
        if (player != null && playerListener != null) {
            player.removeListener(playerListener);
        }
        playerListener = null;
        if (controllerConnection != null) controllerConnection.disconnect();
        player = null;
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        songFocusRequestGeneration++;
        if (loadUserOperation != null) loadUserOperation.cancel();
        super.onDestroyView();
    }
}
