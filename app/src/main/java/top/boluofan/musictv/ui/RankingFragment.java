package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.cardview.widget.CardView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.PlaybackQueue;
import top.boluofan.musictv.PlayerActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.util.DialogHelper;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.backend.MusicApiProvider;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.backend.DirectSourcePlatforms;
import top.boluofan.musictv.player.session.MediaControllerConnection;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import top.boluofan.musictv.ui.adapter.MusicSourceStripAdapter;
import top.boluofan.musictv.util.FocusAnimationHelper;
import top.boluofan.musictv.util.PageWindow;
import top.boluofan.musictv.util.RecyclerFocus;
import top.boluofan.musictv.util.ViewHierarchy;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RankingFragment extends Fragment implements MainActivity.PrimaryPageKeyHandler {
    private static final String TAG = "RankingFragment";
    private View rootView;

    private RecyclerView rvSourceList;
    private RecyclerView rvBoards;
    private RecyclerView rvSongs;
    private ImageButton btnBack;
    private ImageButton btnPlayAll;
    private ImageButton btnShuffle;
    private ImageButton btnFavorite;
    private ImageButton btnPrevPage;
    private ImageButton btnNextPage;
    private TextView tvPageNumber;
    private ProgressBar loadingProgress;

    private String currentSource = "tx";
    private int currentSourceIndex = 0;
    private String currentBoardId = "";
    private int currentBoardIndex = 0;

    private String[] SOURCES = {"tx", "mg", "kw", "kg", "wy"};
    private String[] SOURCE_NAMES = {"QQ音乐", "咪咕", "酷我", "酷狗", "网易云"};

    private List<BoardInfo> boards = new ArrayList<>();
    private List<MusicInfo> songs = new ArrayList<>();

    private LxMusicAdapter songAdapter;
    private MusicSourceStripAdapter sourceAdapter;
    private MediaController player;
    private MediaControllerConnection controllerConnection;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable positionUpdater;
    private static final int SONGS_PER_PAGE = 8;
    private int currentSongPage = 0;
    private long boardRequestGeneration = 0L;
    private long songRequestGeneration = 0L;

    private boolean isPageUsable() {
        return isAdded() && rootView != null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_ranking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (BackendPreferences.getMode(requireContext()) == BackendMode.DIRECT_SOURCE) {
            DirectSourcePlatforms.Snapshot platforms =
                    DirectSourcePlatforms.snapshot(requireContext());
            SOURCES = platforms.codes();
            SOURCE_NAMES = platforms.names();
            currentSource = SOURCES.length == 0 ? "wy" : SOURCES[0];
            currentSourceIndex = 0;
        }
        view.setBackgroundResource(0);
        rootView = view;
        initViews();
        setupRecyclerViews();
        setupListeners();

        loadBoards();
    }

    private void initViews() {
        rvSourceList = rootView.findViewById(R.id.rvSourceList);
        rvBoards = rootView.findViewById(R.id.rvBoards);
        rvSongs = rootView.findViewById(R.id.rvSongs);
        btnBack = rootView.findViewById(R.id.btnBack);
        btnPlayAll = rootView.findViewById(R.id.btnPlayAll);
        btnShuffle = rootView.findViewById(R.id.btnShuffle);
        btnFavorite = rootView.findViewById(R.id.btnFavorite);
        btnPrevPage = rootView.findViewById(R.id.btnRankingPrevPage);
        btnNextPage = rootView.findViewById(R.id.btnRankingNextPage);
        tvPageNumber = rootView.findViewById(R.id.tvRankingPageNumber);
        loadingProgress = rootView.findViewById(R.id.loadingProgress);
        btnBack.setVisibility(View.GONE);
        FocusAnimationHelper.applyFocusAnimation(btnPlayAll, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnShuffle, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnFavorite, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnPrevPage, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnNextPage, FocusAnimationHelper.Profile.ICON);
    }

    private void setupRecyclerViews() {
        rvSourceList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));

        sourceAdapter = new MusicSourceStripAdapter(SOURCE_NAMES, currentSourceIndex,
                this::selectSource, (position, keyCode) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && position == 0) {
                return focusPrimaryRail();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && position == SOURCES.length - 1) {
                return btnPlayAll != null && btnPlayAll.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusCurrentBoard();
            return false;
        });
        rvSourceList.setAdapter(sourceAdapter);

        rvBoards.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
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
                // RecyclerView reuses off-screen board views. Reapply the
                // active-board state here rather than relying on a later pass
                // over only the currently attached children.
                holder.itemView.setSelected(position == currentBoardIndex);
                holder.itemView.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    int currentPosition = holder.getAdapterPosition();
                    if (currentPosition == RecyclerView.NO_POSITION) return true;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentPosition == 0) {
                        return focusPrimaryRail();
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentPosition > 0) {
                        return focusRecyclerChild(rvBoards, currentPosition - 1);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return focusFirstSong();
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        return focusRecyclerChild(rvSourceList, currentSourceIndex);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            && currentPosition < boards.size() - 1) {
                        return focusRecyclerChild(rvBoards, currentPosition + 1);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
                    return false;
                });
            }

            @Override
            public int getItemCount() {
                return boards.size();
            }
        });

        // Do not reclaim focus when the user moves down into the song list.
        // The old sidebar implementation did that on every focus loss, which
        // made the second column trap a TV remote. Selection is restored only
        // when the board strip is entered again.
        rvBoards.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && boards.size() > 0) {
                rvBoards.post(() -> {
                    View focused = rvBoards.findFocus();
                    if (focused == null && currentBoardIndex < rvBoards.getChildCount()) {
                        rvBoards.getChildAt(Math.max(0, currentBoardIndex)).requestFocus();
                    }
                });
            }
        });

        rvSongs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && boards.size() > 0 && currentBoardIndex >= 0 && currentBoardIndex < boards.size()) {
                rvBoards.post(() -> {
                    for (int i = 0; i < rvBoards.getChildCount(); i++) {
                        rvBoards.getChildAt(i).setSelected(i == currentBoardIndex);
                    }
                });
            }
        });

        songAdapter = new LxMusicAdapter();
        songAdapter.setNextFocusDownId(R.id.btnRankingNextPage);
        songAdapter.setNextFocusLeftId(R.id.tabRanking);
        songAdapter.setOnFirstItemUpListener(this::focusCurrentBoard);
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSongs.setPreserveFocusAfterLayout(true);

        songAdapter.setOnItemClickListener((song, position) -> {
            playSongAtIndex(position);
        });

        songAdapter.setOnPlayClickListener((song, position) -> {
            playSongAtIndex(position);
        });

        songAdapter.setOnFullscreenClickListener((song, position) -> {
            if (!playSongAtIndex(position)) return;
            startActivity(new Intent(requireContext(), top.boluofan.musictv.PlayerActivity.class));
        });

        songAdapter.setOnFavClickListener((song, position) -> {
            collectSingleSong(song);
        });

    }

    private void setupListeners() {
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
        btnPrevPage.setOnClickListener(v -> {
            changeSongPage(-1);
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        btnNextPage.setOnClickListener(v -> {
            changeSongPage(1);
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        View.OnKeyListener actionNavigation = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusFirstSong();
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v == btnPlayAll) {
                return focusRecyclerChild(rvSourceList, SOURCES.length - 1);
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v == btnFavorite) return true;
            return false;
        };
        btnPlayAll.setOnKeyListener(actionNavigation);
        btnShuffle.setOnKeyListener(actionNavigation);
        btnFavorite.setOnKeyListener(actionNavigation);
    }

    private boolean focusRecyclerChild(RecyclerView recyclerView, int position) {
        if (recyclerView == null || position < 0) return false;
        return RecyclerFocus.requestPosition(recyclerView, position);
    }

    private boolean focusFirstSong() {
        if (rvSongs == null || songAdapter == null || songAdapter.getItemCount() == 0) {
            return true;
        }
        return songAdapter.requestFocusAt(rvSongs, 0);
    }

    private boolean focusCurrentBoard() {
        return focusRecyclerChild(rvBoards, currentBoardIndex);
    }

    private boolean focusPrimaryRail() {
        if (getActivity() == null) return false;
        View tabRanking = getActivity().findViewById(R.id.tabRanking);
        return tabRanking != null && tabRanking.requestFocus();
    }

    private void collectPlaylist() {
        if (!LxRetrofitClient.isLoggedIn(requireContext()) && !BackendPreferences.usesLocalLibrary(requireContext())) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(requireContext(), top.boluofan.musictv.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(requireContext()));
            startActivity(intent);
            return;
        }

        if (songs.isEmpty()) {
            Toast.makeText(requireContext(), "歌单为空，无法收藏", Toast.LENGTH_SHORT).show();
            return;
        }

        // Keep the exact board the user confirmed. The leaderboard can change
        // while the user-list request is in flight, so callbacks must never
        // read the mutable board index or song collection again.
        final String sourceSnapshot = currentSource;
        final String boardIdSnapshot = currentBoardId;
        final int sourceIndexSnapshot = currentSourceIndex;
        final BoardInfo boardSnapshot = currentBoardIndex >= 0 && currentBoardIndex < boards.size()
                ? boards.get(currentBoardIndex) : null;
        final String boardNameSnapshot = boardSnapshot != null
                && boardSnapshot.name != null && !boardSnapshot.name.isEmpty()
                ? boardSnapshot.name
                : SOURCE_NAMES[Math.max(0, Math.min(sourceIndexSnapshot, SOURCE_NAMES.length - 1))] + "排行榜";
        final List<MusicInfo> songsSnapshot = new ArrayList<>(songs);

        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        LxApiService apiService = MusicApiProvider.get(requireContext());

        btnFavorite.setEnabled(false);

        apiService.getUserList(username, password,token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!isPageUsable()) return;
                btnFavorite.setEnabled(true);

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(requireContext(), "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();

                top.boluofan.musictv.api.model.Playlist existingPlaylist = null;
                if (listData.getUserList() != null) {
                    for (top.boluofan.musictv.api.model.Playlist p : listData.getUserList()) {
                        if (boardNameSnapshot.equals(p.getName())) {
                            existingPlaylist = p;
                            break;
                        }
                    }
                }

                if (existingPlaylist != null) {
                    final top.boluofan.musictv.api.model.ListData finalListData = listData;
                    final top.boluofan.musictv.api.model.Playlist finalExistingPlaylist = existingPlaylist;
                    android.content.Context ctx = requireContext();
                    DialogHelper.showOverwriteConfirmDialog(ctx, boardNameSnapshot, new DialogHelper.IDialogCallback() {
                        @Override
                        public void onConfirm() {
                            if (!isPageUsable()) return;
                            doCollectPlaylist(finalListData, finalExistingPlaylist, boardNameSnapshot,
                                    sourceSnapshot, boardIdSnapshot, songsSnapshot);
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
                } else {
                    doCollectPlaylist(listData, null, boardNameSnapshot,
                            sourceSnapshot, boardIdSnapshot, songsSnapshot);
                }
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                if (!isPageUsable()) return;
                btnFavorite.setEnabled(true);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doCollectPlaylist(top.boluofan.musictv.api.model.ListData listData,
                                   top.boluofan.musictv.api.model.Playlist existingPlaylist,
                                   String boardName, String sourceSnapshot,
                                   String boardIdSnapshot, List<MusicInfo> songsSnapshot) {
        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        LxApiService apiService = MusicApiProvider.get(requireContext());

        top.boluofan.musictv.api.model.Playlist newPlaylist;
        if (existingPlaylist != null) {
            newPlaylist = existingPlaylist;
            newPlaylist.setSongs(new ArrayList<>(songsSnapshot));
            newPlaylist.setSongCount(songsSnapshot.size());
            newPlaylist.setSource(sourceSnapshot);
            newPlaylist.setSourceListId(boardIdSnapshot);
        } else {
            newPlaylist = new top.boluofan.musictv.api.model.Playlist();
            newPlaylist.setId("playlist_" + System.currentTimeMillis());
            newPlaylist.setName(boardName);
            newPlaylist.setSource(sourceSnapshot);
            newPlaylist.setSourceListId(boardIdSnapshot);
            newPlaylist.setSongs(new ArrayList<>(songsSnapshot));
            newPlaylist.setSongCount(songsSnapshot.size());

            if (listData.getUserList() == null) {
                listData.setUserList(new ArrayList<>());
            }
            listData.getUserList().add(newPlaylist);
        }

        btnFavorite.setEnabled(false);
        apiService.updateUserList(username, password, token, listData).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isPageUsable()) return;
                btnFavorite.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), existingPlaylist != null ? "覆盖成功" : "收藏成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "收藏失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                if (!isPageUsable()) return;
                btnFavorite.setEnabled(true);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void collectSingleSong(MusicInfo song) {
        if (!LxRetrofitClient.isLoggedIn(requireContext()) && !BackendPreferences.usesLocalLibrary(requireContext())) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(requireContext(), top.boluofan.musictv.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(requireContext()));
            startActivity(intent);
            return;
        }

        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        LxApiService apiService = MusicApiProvider.get(requireContext());

        apiService.getUserList(username, password,token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!isPageUsable()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(requireContext(), "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null || userPlaylists.isEmpty()) {
                    Toast.makeText(requireContext(), "暂无歌单，请先在歌单库创建歌单", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[userPlaylists.size()];
                for (int i = 0; i < userPlaylists.size(); i++) {
                    playlistNames[i] = userPlaylists.get(i).getName();
                }

                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(requireContext(), "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
                    if (!isPageUsable() || which < 0 || which >= userPlaylists.size()) return;
                    fetchAndAddSongToPlaylist(userPlaylists.get(which).getName(), finalSong);
                });
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                if (!isPageUsable()) return;
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAndAddSongToPlaylist(String playlistName, MusicInfo song) {
        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        LxApiService apiService = MusicApiProvider.get(requireContext());

        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!isPageUsable()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(requireContext(), "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null) {
                    Toast.makeText(requireContext(), "歌单不存在", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(requireContext(), "歌单不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                addSongToPlaylist(listData, targetPlaylist, song);
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                if (!isPageUsable()) return;
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addSongToPlaylist(top.boluofan.musictv.api.model.ListData listData, top.boluofan.musictv.api.model.Playlist playlist, MusicInfo song) {
        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        LxApiService apiService = MusicApiProvider.get(requireContext());

        List<MusicInfo> songList = playlist.getSongs();
        if (songList == null) {
            songList = new ArrayList<>();
        }

        for (MusicInfo m : songList) {
            if (Objects.equals(m.getName(), song.getName())
                    && Objects.equals(m.getSource(), song.getSource())) {
                Toast.makeText(requireContext(), "歌曲已存在于此歌单", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        songList.add(0, song);
        playlist.setSongs(songList);
        playlist.setSongCount(songList.size());

        apiService.updateUserList(username, password, token, listData).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isPageUsable()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), "已添加到「" + playlist.getName() + "」", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "添加失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                if (!isPageUsable()) return;
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void selectSource(int position) {
        if (position < 0 || position >= SOURCES.length) return;

        View focusedBeforeUpdate = getActivity() != null
                ? getActivity().getCurrentFocus() : null;
        boolean retainSourceFocus = ViewHierarchy.contains(rvSourceList, focusedBeforeUpdate);

        currentSourceIndex = position;
        currentSource = SOURCES[position];
        currentBoardId = "";
        currentBoardIndex = 0;
        currentSongPage = 0;
        boards.clear();
        songs.clear();

        if (rvBoards.getAdapter() != null) {
            rvBoards.getAdapter().notifyDataSetChanged();
        }
        if (songAdapter != null) {
            songAdapter.notifyDataSetChanged();
        }

        sourceAdapter.setSelectedPosition(position);

        loadBoards();

        if (retainSourceFocus) rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > position) {
                View itemView = rvSourceList.getChildAt(position);
                if (itemView != null) {
                    itemView.requestFocus();
                }
            }
        });
    }

    private void selectBoard(int position) {
        selectBoard(position, true);
    }

    private void selectBoard(int position, boolean retainBoardFocus) {
        if (position < 0 || position >= boards.size()) return;

        currentBoardIndex = position;
        currentBoardId = getBangId(boards.get(position).id);
        currentSongPage = 0;

        if (rvBoards.getAdapter() != null) {
            rvBoards.getAdapter().notifyDataSetChanged();
        }

        loadSongs();

        rvBoards.post(() -> {
            for (int i = 0; i < rvBoards.getChildCount(); i++) {
                rvBoards.getChildAt(i).setSelected(i == position);
            }
            if (retainBoardFocus) focusRecyclerChild(rvBoards, position);
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
        final long requestGeneration = ++boardRequestGeneration;
        final String sourceSnapshot = currentSource;
        // A source reload invalidates any song response belonging to its old
        // board set, even before the new board response arrives.
        songRequestGeneration++;
        showLoading(true);

        LxApiService apiService = MusicApiProvider.get(requireContext());
        apiService.getLeaderboardBoards(sourceSnapshot).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isCurrentBoardRequest(requestGeneration, sourceSnapshot)) return;
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

                        if (!boards.isEmpty()) {
                            // Loading a source must not steal focus from the
                            // source chip the user just confirmed.
                            selectBoard(0, false);
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                if (!isCurrentBoardRequest(requestGeneration, sourceSnapshot)) return;
                showLoading(false);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSongs() {
        if (currentBoardId.isEmpty()) return;

        final long requestGeneration = ++songRequestGeneration;
        final String sourceSnapshot = currentSource;
        final String boardIdSnapshot = currentBoardId;

        showLoading(true);

        LxApiService apiService = MusicApiProvider.get(requireContext());
        apiService.getLeaderboardList(sourceSnapshot, boardIdSnapshot, 1).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isCurrentSongRequest(requestGeneration, sourceSnapshot, boardIdSnapshot)) return;
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        Gson gson = new Gson();
                        JsonObject root = gson.fromJson(bodyStr, JsonObject.class);
                        JsonArray list = root.getAsJsonArray("list");

                        List<MusicInfo> loadedSongs = new ArrayList<>();
                        if (list != null) {
                            for (int i = 0; i < list.size(); i++) {
                                JsonObject item = list.get(i).getAsJsonObject();
                                MusicInfo music = gson.fromJson(item, MusicInfo.class);
                                if (music.getSource() == null || music.getSource().isEmpty()) {
                                    music.setSource(sourceSnapshot);
                                }
                                loadedSongs.add(music);
                            }
                        }

                        // Parsing is synchronous, but a source/board selection
                        // can still invalidate this response before publishing.
                        if (!isCurrentSongRequest(requestGeneration, sourceSnapshot, boardIdSnapshot)) return;
                        songs.clear();
                        songs.addAll(loadedSongs);
                        currentSongPage = 0;
                        updateSongPage();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                if (!isCurrentSongRequest(requestGeneration, sourceSnapshot, boardIdSnapshot)) return;
                showLoading(false);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isCurrentBoardRequest(long requestGeneration, String sourceSnapshot) {
        return isPageUsable()
                && requestGeneration == boardRequestGeneration
                && sourceSnapshot.equals(currentSource);
    }

    private boolean isCurrentSongRequest(long requestGeneration, String sourceSnapshot,
                                         String boardIdSnapshot) {
        return isPageUsable()
                && requestGeneration == songRequestGeneration
                && sourceSnapshot.equals(currentSource)
                && boardIdSnapshot.equals(currentBoardId);
    }

    private void playAll(boolean shuffle) {
        if (songs.isEmpty()) {
            Toast.makeText(requireContext(), "没有可播放的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }

        if (player == null) return;

        PlaybackQueue queue = PlaybackQueue.from(songs);
        if (queue.isEmpty()) {
            Toast.makeText(requireContext(), "歌曲缺少可用的播放信息", Toast.LENGTH_SHORT).show();
            return;
        }

        int startIndex = shuffle ? (int) (Math.random() * queue.size()) : 0;

        queue.play(player, startIndex);

        Toast.makeText(requireContext(), shuffle ? "随机播放" : "播放全部", Toast.LENGTH_SHORT).show();
    }

    private boolean playSongAtIndex(int position) {
        if (songs.isEmpty() || player == null) return false;

        int globalPosition = currentSongPage * SONGS_PER_PAGE + position;
        PlaybackQueue queue = PlaybackQueue.from(songs);
        if (!queue.playSourceIndex(player, globalPosition)) {
            Toast.makeText(requireContext(), "该歌曲缺少播放信息", Toast.LENGTH_SHORT).show();
            return false;
        }
        songAdapter.setPlayingIndex(position);
        return true;
    }

    private void updateSongPage() {
        PageWindow<MusicInfo> page = PageWindow.of(songs, currentSongPage, SONGS_PER_PAGE);
        currentSongPage = page.getPageIndex();
        songAdapter.setIndexOffset(page.getStartIndex());
        songAdapter.setSongs(page.getItems());
        songAdapter.setPlayingIndex(-1);
        tvPageNumber.setText(String.valueOf(currentSongPage + 1));
        boolean hasPrevious = page.hasPrevious();
        boolean hasNext = page.hasNext();
        btnPrevPage.setEnabled(hasPrevious);
        btnNextPage.setEnabled(hasNext);
        btnPrevPage.setAlpha(hasPrevious ? 1f : 0.35f);
        btnNextPage.setAlpha(hasNext ? 1f : 0.35f);
    }

    private void changeSongPage(int delta) {
        int nextPage = PageWindow.of(songs, currentSongPage, SONGS_PER_PAGE).pageAfter(delta);
        if (nextPage == currentSongPage) return;
        currentSongPage = nextPage;
        updateSongPage();
        rvSongs.scrollToPosition(0);
    }

    private void requestFirstSongFocus() {
        if (rvSongs == null || songAdapter == null || songAdapter.getItemCount() == 0) return;
        songAdapter.requestFocusAt(rvSongs, 0);
    }

    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (controllerConnection == null) {
            controllerConnection = new MediaControllerConnection(
                    requireContext(),
                    this::isAdded,
                    resolved -> {
                        player = resolved;
                        setupPlayerListener();
                    },
                    Throwable::printStackTrace);
        }
        controllerConnection.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (controllerConnection != null) controllerConnection.disconnect();
        player = null;
    }

    @Override
    public void onDestroyView() {
        boardRequestGeneration++;
        songRequestGeneration++;
        rootView = null;
        super.onDestroyView();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        View currentFocus = getActivity() != null ? getActivity().getCurrentFocus() : null;
        if ((keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                && rvSongs != null && songAdapter != null
                && (ViewHierarchy.contains(rvSongs, currentFocus) || currentFocus == rvSongs
                || (currentFocus == null && songAdapter.hasFocusHistory()))) {
            return songAdapter.handleVerticalKey(currentFocus != null ? currentFocus : rvSongs, keyCode);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && isPagerFocused()) {
            // Do not let a repeated Down at the song-list boundary fall back
            // to the left primary rail.
            return true;
        }
        return false;
    }

    private boolean isPagerFocused() {
        if (getActivity() == null) return false;
        View currentFocus = getActivity().getCurrentFocus();
        return currentFocus == btnPrevPage || currentFocus == btnNextPage;
    }

    private void setupPlayerListener() {
        if (player == null) return;

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                songAdapter.notifyDataSetChanged();
                songAdapter.restorePendingPlaybackFocus();
            }

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
        });
    }

    private static class BoardInfo {
        String id;
        String name;
    }

    private class BoardViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        BoardViewHolder(View view) {
            super(view);
            FocusAnimationHelper.applyFocusAnimation(
                    view, FocusAnimationHelper.Profile.RAIL);
            tv = view.findViewById(R.id.tvBoardName);
            tv.setOnClickListener(v -> {
                int position = (int) tv.getTag();
                RankingFragment.this.selectBoard(position);
            });
        }
    }
}
