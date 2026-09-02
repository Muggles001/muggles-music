package top.boluofan.musictv.ui.library;

import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import top.boluofan.musictv.library.PlaylistMutations;
import top.boluofan.musictv.ui.common.songlist.LxMusicAdapter;
import top.boluofan.musictv.ui.library.PlaylistAdapter;
import top.boluofan.musictv.ui.common.FocusAnimationHelper;
import top.boluofan.musictv.ui.common.DialogHelper;
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
    private TextView tvLibraryModeLabel;
    private TextView tabAllSongs;
    private TextView tabLoveList;
    private ImageButton btnBackToPlaylists;
    private ImageButton btnPrevPage;
    private ImageButton btnNextPage;
    private ImageButton btnPlayAllSongs;
    private ImageButton btnPlayOrderToggle;
    private View btnImportPlaylist;
    private Button btnManagePlaylists;
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
    private boolean showingFavorites;
    private boolean showingPlaylistDetail = false;
    private boolean managingPlaylists;
    private boolean shuffleEnabled = false;
    private static final int PLAYLISTS_PER_PAGE = 10;
    private static final int SONGS_PER_PAGE = 8;
    private int currentSongPage = 0;
    private int songFocusRequestGeneration = 0;
    private PlaylistActions.Operation loadUserOperation;
    private PlaylistActions.Operation libraryMutationOperation;
    private PlaylistActions playlistActions;
    private ActivityResultLauncher<Intent> playlistImportLauncher;
    private String pendingImportedPlaylistId;
    private String pendingPlaylistFocusId;

    private boolean isPageUsable() {
        return isAdded() && getView() != null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && managingPlaylists) {
            setManagingPlaylists(false);
            btnManagePlaylists.requestFocus();
            return true;
        }
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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistImportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        if (btnImportPlaylist != null) btnImportPlaylist.requestFocus();
                        return;
                    }
                    pendingImportedPlaylistId = result.getData().getStringExtra(
                            PlaylistImportActivity.EXTRA_PLAYLIST_ID);
                    if (isPageUsable()) loadUserData();
                });
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
        btnImportPlaylist = view.findViewById(R.id.btnImportPlaylist);
        btnManagePlaylists = view.findViewById(R.id.btnManagePlaylists);
        FocusAnimationHelper.applyFocusAnimation(
                btnBackToPlaylists, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnPlayAllSongs, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(
                btnPlayOrderToggle, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnPrevPage, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnNextPage, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnImportPlaylist);
        FocusAnimationHelper.applyFocusAnimation(btnManagePlaylists);
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
        playlistAdapter.setOnItemClickListener(row -> loadPlaylistSongs(row.getPlaylistId()));
        playlistAdapter.setOnManageClickListener(new PlaylistAdapter.OnManageClickListener() {
            @Override
            public void onRename(LibraryPlaylistRow playlist) {
                showRenamePlaylistDialog(playlist);
            }

            @Override
            public void onDelete(LibraryPlaylistRow playlist) {
                confirmDeletePlaylist(playlist);
            }
        });

        FocusAnimationHelper.blockDownFocusEscape(btnPrevPage, btnNextPage);

        btnImportPlaylist.setOnClickListener(v -> {
            if (!LxRetrofitClient.isLoggedIn(requireContext())
                    && !BackendPreferences.usesLocalLibrary(requireContext())) {
                Toast.makeText(requireContext(), "请先配置连接或音源", Toast.LENGTH_SHORT).show();
                return;
            }
            playlistImportLauncher.launch(
                    new Intent(requireContext(), PlaylistImportActivity.class));
        });
        btnImportPlaylist.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (playlistAdapter.getItemCount() == 0) return true;
                requestFirstPlaylistFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            return false;
        });
        btnManagePlaylists.setOnClickListener(v -> {
            setManagingPlaylists(!managingPlaylists);
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        btnManagePlaylists.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (playlistAdapter.getItemCount() > 0) requestFirstPlaylistFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            return false;
        });

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

        songAdapter.setOnFavClickListener(this::toggleFavorite);
        
        tabAllSongs.setOnClickListener(v -> {
            showingFavorites = false;
            setManagingPlaylists(false);
            tabAllSongs.setSelected(true);
            tabLoveList.setSelected(false);
            showPlaylistOverview();
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        
        tabLoveList.setOnClickListener(v -> {
            showingFavorites = true;
            setManagingPlaylists(false);
            tabLoveList.setSelected(true);
            tabAllSongs.setSelected(false);
            if (listData != null) {
                Playlist lovePlaylist = new Playlist();
                lovePlaylist.setName(getString(R.string.library_favorites));
                lovePlaylist.setSongs(listData.getLoveList() == null
                        ? new ArrayList<>() : listData.getLoveList());
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
        playlistActions = new PlaylistActions(
                MusicApiProvider.get(requireContext()), username, password, token);
        loadUserOperation = playlistActions.loadData(new PlaylistActions.Callback<ListData>() {
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
        if (listData == null) return;
        
        List<LibraryPlaylistRow> rows = new ArrayList<>();
        if (listData.getUserList() != null) {
            for (Playlist p : listData.getUserList()) {
                if (p != null) rows.add(LibraryPlaylistRow.from(p));
            }
        }
        playlistAdapter.setData(rows);
        playlistAdapter.setManagementMode(managingPlaylists);
        songAdapter.setFavoriteSongs(listData.getLoveList());
        currentPlaylist = null;
        showPlaylistOverview();
        restoreImportedPlaylistFocus();
        restorePendingPlaylistFocus();
    }

    private void restoreImportedPlaylistFocus() {
        String playlistId = pendingImportedPlaylistId;
        pendingImportedPlaylistId = null;
        if (playlistId == null) return;
        int globalPosition = playlistAdapter.findGlobalPositionById(playlistId);
        if (globalPosition == RecyclerView.NO_POSITION) {
            btnImportPlaylist.post(btnImportPlaylist::requestFocus);
            return;
        }
        int page = globalPosition / playlistAdapter.getPageSize();
        int localPosition = globalPosition % playlistAdapter.getPageSize();
        playlistAdapter.setPage(page);
        updateLibraryPageUi();
        rvPlaylists.scrollToPosition(localPosition);
        rvPlaylists.post(() -> {
            RecyclerView.ViewHolder holder =
                    rvPlaylists.findViewHolderForAdapterPosition(localPosition);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void restorePendingPlaylistFocus() {
        String playlistId = pendingPlaylistFocusId;
        pendingPlaylistFocusId = null;
        if (playlistId == null) return;
        focusPlaylistById(playlistId);
    }

    private void focusPlaylistById(String playlistId) {
        int globalPosition = playlistAdapter.findGlobalPositionById(playlistId);
        if (globalPosition == RecyclerView.NO_POSITION) {
            btnManagePlaylists.post(btnManagePlaylists::requestFocus);
            return;
        }
        int page = globalPosition / playlistAdapter.getPageSize();
        int localPosition = globalPosition % playlistAdapter.getPageSize();
        playlistAdapter.setPage(page);
        updateLibraryPageUi();
        rvPlaylists.scrollToPosition(localPosition);
        rvPlaylists.post(() -> {
            RecyclerView.ViewHolder holder =
                    rvPlaylists.findViewHolderForAdapterPosition(localPosition);
            if (holder != null) holder.itemView.requestFocus();
        });
    }
    
    private void loadPlaylistSongs(String playlistId) {
        if (listData == null || listData.getUserList() == null) return;
        
        for (Playlist p : listData.getUserList()) {
            if (p != null && java.util.Objects.equals(p.getId(), playlistId)) {
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

    private void setManagingPlaylists(boolean managing) {
        managingPlaylists = managing && !showingPlaylistDetail;
        if (playlistAdapter != null) {
            playlistAdapter.setManagementMode(managingPlaylists);
        }
        if (btnManagePlaylists == null || btnImportPlaylist == null) return;
        btnManagePlaylists.setText(managingPlaylists
                ? R.string.library_manage_done : R.string.library_manage);
        btnImportPlaylist.setVisibility(managingPlaylists ? View.GONE : View.VISIBLE);
        btnManagePlaylists.setNextFocusLeftId(managingPlaylists
                ? R.id.tabLibrary : R.id.btnImportPlaylist);
        if (tvLibraryModeLabel != null && !showingPlaylistDetail) {
            tvLibraryModeLabel.setText(managingPlaylists
                    ? R.string.library_management_mode : R.string.library_all_playlists_title);
        }
    }

    private void confirmDeletePlaylist(LibraryPlaylistRow row) {
        DialogHelper.showDangerConfirmDialog(requireContext(),
                getString(R.string.library_delete_title),
                getString(R.string.library_delete_message, row.getName()),
                getString(R.string.library_delete_playlist),
                getString(R.string.library_cancel),
                new DialogHelper.IDialogCallback() {
                    @Override
                    public void onConfirm() {
                        deletePlaylist(row);
                    }

                    @Override
                    public void onCancel() {
                        focusPlaylistById(row.getPlaylistId());
                    }
                });
    }

    private void deletePlaylist(LibraryPlaylistRow row) {
        if (playlistActions == null || libraryMutationOperation != null) return;
        pendingPlaylistFocusId = adjacentPlaylistId(row.getPlaylistId());
        libraryMutationOperation = playlistActions.deletePlaylist(row.getPlaylistId(),
                new PlaylistActions.Callback<PlaylistActions.PlaylistChangeResult>() {
                    @Override
                    public void onSuccess(PlaylistActions.PlaylistChangeResult value) {
                        if (!isPageUsable()) return;
                        libraryMutationOperation = null;
                        Toast.makeText(requireContext(), R.string.library_delete_success,
                                Toast.LENGTH_SHORT).show();
                        loadUserData();
                    }

                    @Override
                    public void onFailure(PlaylistActions.Failure failure) {
                        if (!isPageUsable()) return;
                        libraryMutationOperation = null;
                        pendingPlaylistFocusId = null;
                        showLibraryMutationFailure(failure);
                        focusPlaylistById(row.getPlaylistId());
                    }
                });
    }

    private String adjacentPlaylistId(String deletingId) {
        List<Playlist> playlists = listData == null ? null : listData.getUserList();
        if (playlists == null) return null;
        for (int i = 0; i < playlists.size(); i++) {
            Playlist playlist = playlists.get(i);
            if (playlist == null || !java.util.Objects.equals(deletingId, playlist.getId())) {
                continue;
            }
            if (i + 1 < playlists.size() && playlists.get(i + 1) != null) {
                return playlists.get(i + 1).getId();
            }
            if (i > 0 && playlists.get(i - 1) != null) {
                return playlists.get(i - 1).getId();
            }
            return null;
        }
        return null;
    }

    private void showRenamePlaylistDialog(LibraryPlaylistRow row) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setText(row.getName());
        input.setSelection(input.length());
        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(
                        requireContext(), R.style.CustomAlertDialog)
                        .setTitle(R.string.library_rename_title)
                        .setView(input)
                        .setPositiveButton(R.string.library_save, null)
                        .setNegativeButton(R.string.library_cancel,
                                (ignored, which) -> focusPlaylistById(row.getPlaylistId()))
                        .create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) return;
                        dialog.dismiss();
                        renamePlaylist(row, name);
                    });
            input.requestFocus();
        });
        dialog.show();
    }

    private void renamePlaylist(LibraryPlaylistRow row, String name) {
        if (playlistActions == null || libraryMutationOperation != null) return;
        pendingPlaylistFocusId = row.getPlaylistId();
        libraryMutationOperation = playlistActions.renamePlaylist(
                row.getPlaylistId(), name,
                new PlaylistActions.Callback<PlaylistActions.PlaylistChangeResult>() {
                    @Override
                    public void onSuccess(PlaylistActions.PlaylistChangeResult value) {
                        if (!isPageUsable()) return;
                        libraryMutationOperation = null;
                        Toast.makeText(requireContext(), R.string.library_rename_success,
                                Toast.LENGTH_SHORT).show();
                        loadUserData();
                    }

                    @Override
                    public void onFailure(PlaylistActions.Failure failure) {
                        if (!isPageUsable()) return;
                        libraryMutationOperation = null;
                        pendingPlaylistFocusId = null;
                        showLibraryMutationFailure(failure);
                        focusPlaylistById(row.getPlaylistId());
                    }
                });
    }

    private void toggleFavorite(MusicInfo song, int position) {
        if (playlistActions == null || libraryMutationOperation != null) return;
        libraryMutationOperation = playlistActions.toggleFavorite(song,
                new PlaylistActions.Callback<PlaylistActions.FavoriteResult>() {
                    @Override
                    public void onSuccess(PlaylistActions.FavoriteResult result) {
                        if (!isPageUsable()) return;
                        libraryMutationOperation = null;
                        boolean current = listData != null && PlaylistMutations.isFavorite(
                                listData.getLoveList(), song);
                        if (listData != null && current != result.isFavorite()) {
                            PlaylistMutations.toggleFavorite(listData, song);
                        }
                        songAdapter.setFavoriteSongs(
                                listData == null ? null : listData.getLoveList());
                        if (showingFavorites && currentPlaylist != null) {
                            updateSongPage();
                        }
                        Toast.makeText(requireContext(), result.isFavorite()
                                        ? R.string.library_favorite_added
                                        : R.string.library_favorite_removed,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(PlaylistActions.Failure failure) {
                        if (!isPageUsable()) return;
                        libraryMutationOperation = null;
                        showLibraryMutationFailure(failure);
                    }
                });
    }

    private void showLibraryMutationFailure(PlaylistActions.Failure failure) {
        int message = failure.getKind() == PlaylistActions.Failure.Kind.DUPLICATE_NAME
                ? R.string.library_duplicate_name : R.string.library_operation_failed;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void showPlaylistOverview() {
        int distance = transitionDistance();
        songFocusRequestGeneration++;
        showingPlaylistDetail = false;
        showingFavorites = false;
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
        tvLibraryModeLabel.setText(managingPlaylists
                ? R.string.library_management_mode : R.string.library_all_playlists_title);
        libraryPagerBar.setVisibility(View.VISIBLE);
        setSongListConstraints(false);
        configurePagerFocus(false);
        configureDetailHeaderFocus(false);
        btnBackToPlaylists.setVisibility(View.GONE);
        btnImportPlaylist.setVisibility(managingPlaylists ? View.GONE : View.VISIBLE);
        btnManagePlaylists.setVisibility(View.VISIBLE);
        btnPlayAllSongs.setVisibility(View.GONE);
        btnPlayOrderToggle.setVisibility(View.GONE);
        tabAllSongs.setSelected(true);
        tabLoveList.setSelected(false);
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
        btnImportPlaylist.setVisibility(View.GONE);
        btnManagePlaylists.setVisibility(View.GONE);
        btnPlayAllSongs.setVisibility(View.VISIBLE);
        btnPlayOrderToggle.setVisibility(View.VISIBLE);
        updatePlaybackModeButton();
        songAdapter.setFavoriteSongs(listData == null ? null : listData.getLoveList());
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
        if (libraryMutationOperation != null) libraryMutationOperation.cancel();
        super.onDestroyView();
    }
}
