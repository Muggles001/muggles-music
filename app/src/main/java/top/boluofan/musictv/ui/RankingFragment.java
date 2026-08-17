package top.boluofan.musictv.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import top.boluofan.musictv.util.DialogHelper;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import top.boluofan.musictv.util.FocusAnimationHelper;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

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

    private final String[] SOURCES = {"tx", "mg", "kw", "kg", "wy"};
    private final String[] SOURCE_NAMES = {"QQ音乐", "咪咕", "酷我", "酷狗", "网易云"};

    private List<BoardInfo> boards = new ArrayList<>();
    private List<MusicInfo> songs = new ArrayList<>();

    private LxMusicAdapter songAdapter;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable positionUpdater;
    private static final int SONGS_PER_PAGE = 8;
    private int currentSongPage = 0;

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
    }

    private void setupRecyclerViews() {
        rvSourceList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));

        rvSourceList.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<SourceViewHolder>() {
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
                holder.itemView.setSelected(position == currentSourceIndex);

                holder.itemView.setOnClickListener(v -> selectSource(position));
                holder.itemView.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    int currentPosition = holder.getAdapterPosition();
                    if (currentPosition == RecyclerView.NO_POSITION) return true;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentPosition == 0) {
                        return focusPrimaryRail();
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentPosition > 0) {
                        return focusRecyclerChild(rvSourceList, currentPosition - 1);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            && currentPosition == SOURCES.length - 1) {
                        return btnPlayAll != null && btnPlayAll.requestFocus();
                    }
                    return false;
                });
            }

            @Override
            public int getItemCount() {
                return SOURCES.length;
            }
        });

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
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            && currentPosition == boards.size() - 1) {
                        return true;
                    }
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
            playSongAtIndex(position);
            startActivity(new Intent(requireContext(), top.boluofan.musictv.PlayerActivity.class));
        });

        songAdapter.setOnFavClickListener((song, position) -> {
            collectSingleSong(song);
        });

        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > 0) {
                rvSourceList.getChildAt(0).requestFocus();
            }
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
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null) return holder.itemView.requestFocus();
        recyclerView.scrollToPosition(position);
        recyclerView.post(() -> {
            RecyclerView.ViewHolder target = recyclerView.findViewHolderForAdapterPosition(position);
            if (target != null) target.itemView.requestFocus();
        });
        return true;
    }

    private boolean focusFirstSong() {
        if (rvSongs == null || songAdapter == null || songAdapter.getItemCount() == 0) {
            return true;
        }
        RecyclerView.ViewHolder holder = rvSongs.findViewHolderForAdapterPosition(0);
        if (holder != null) return holder.itemView.requestFocus();
        rvSongs.scrollToPosition(0);
        rvSongs.post(() -> {
            RecyclerView.ViewHolder target = rvSongs.findViewHolderForAdapterPosition(0);
            if (target != null) target.itemView.requestFocus();
        });
        return true;
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
        if (!LxRetrofitClient.isLoggedIn(requireContext())) {
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

        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());

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

                String boardName = currentBoardId.isEmpty() ? SOURCE_NAMES[currentSourceIndex] + "排行榜" : boards.get(currentBoardIndex).name;

                top.boluofan.musictv.api.model.Playlist existingPlaylist = null;
                if (listData.getUserList() != null) {
                    for (top.boluofan.musictv.api.model.Playlist p : listData.getUserList()) {
                        if (boardName.equals(p.getName())) {
                            existingPlaylist = p;
                            break;
                        }
                    }
                }

                if (existingPlaylist != null) {
                    final top.boluofan.musictv.api.model.ListData finalListData = listData;
                    final top.boluofan.musictv.api.model.Playlist finalExistingPlaylist = existingPlaylist;
                    final String finalBoardName = boardName;
                    android.content.Context ctx = requireContext();
                    DialogHelper.showOverwriteConfirmDialog(ctx, boardName, new DialogHelper.IDialogCallback() {
                        @Override
                        public void onConfirm() {
                            doCollectPlaylist(finalListData, finalExistingPlaylist, finalBoardName);
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
                } else {
                    doCollectPlaylist(listData, null, boardName);
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

    private void doCollectPlaylist(top.boluofan.musictv.api.model.ListData listData, top.boluofan.musictv.api.model.Playlist existingPlaylist, String boardName) {
        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());

        top.boluofan.musictv.api.model.Playlist newPlaylist;
        if (existingPlaylist != null) {
            newPlaylist = existingPlaylist;
            newPlaylist.setSongs(new ArrayList<>(songs));
            newPlaylist.setSongCount(songs.size());
            newPlaylist.setSource(currentSource);
            newPlaylist.setSourceListId(currentBoardId);
        } else {
            newPlaylist = new top.boluofan.musictv.api.model.Playlist();
            newPlaylist.setId("playlist_" + System.currentTimeMillis());
            newPlaylist.setName(boardName);
            newPlaylist.setSource(currentSource);
            newPlaylist.setSourceListId(currentBoardId);
            newPlaylist.setSongs(new ArrayList<>(songs));
            newPlaylist.setSongCount(songs.size());

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
        if (!LxRetrofitClient.isLoggedIn(requireContext())) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(requireContext(), top.boluofan.musictv.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(requireContext()));
            startActivity(intent);
            return;
        }

        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());

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

                final int songIndex = songs.indexOf(song);
                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(requireContext(), "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
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
        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());

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
        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());

        List<MusicInfo> songList = playlist.getSongs();
        if (songList == null) {
            songList = new ArrayList<>();
        }

        for (MusicInfo m : songList) {
            if (m.getName().equals(song.getName()) && m.getSource().equals(song.getSource())) {
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
        showLoading(true);

        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());
        apiService.getLeaderboardBoards(currentSource).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isPageUsable()) return;
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
                if (!isPageUsable()) return;
                showLoading(false);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSongs() {
        if (currentBoardId.isEmpty()) return;

        showLoading(true);

        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());
        apiService.getLeaderboardList(currentSource, currentBoardId, 1).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isPageUsable()) return;
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
                                MusicInfo music = gson.fromJson(item, MusicInfo.class);
                                if (music.getSource() == null || music.getSource().isEmpty()) {
                                    music.setSource(currentSource);
                                }
                                songs.add(music);
                            }
                        }

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
                if (!isPageUsable()) return;
                showLoading(false);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void playAll(boolean shuffle) {
        if (songs.isEmpty()) {
            Toast.makeText(requireContext(), "没有可播放的歌曲", Toast.LENGTH_SHORT).show();
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

        Toast.makeText(requireContext(), shuffle ? "随机播放" : "播放全部", Toast.LENGTH_SHORT).show();
    }

    private void playSongAtIndex(int position) {
        if (songs.isEmpty() || player == null) return;

        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo song : songs) {
            mediaItems.add(createMediaItem(song));
        }

        int globalPosition = currentSongPage * SONGS_PER_PAGE + position;
        if (globalPosition < 0 || globalPosition >= mediaItems.size()) return;
        player.setMediaItems(mediaItems, globalPosition, 0);
        player.prepare();
        player.play();
        songAdapter.setPlayingIndex(position);
    }

    private int getSongPageCount() {
        return Math.max(1, (songs.size() + SONGS_PER_PAGE - 1) / SONGS_PER_PAGE);
    }

    private void updateSongPage() {
        int from = Math.min(currentSongPage * SONGS_PER_PAGE, songs.size());
        int to = Math.min(from + SONGS_PER_PAGE, songs.size());
        List<MusicInfo> pageSongs = new ArrayList<>();
        if (from < to) pageSongs.addAll(songs.subList(from, to));
        songAdapter.setIndexOffset(currentSongPage * SONGS_PER_PAGE);
        songAdapter.setSongs(pageSongs);
        songAdapter.setPlayingIndex(-1);
        tvPageNumber.setText(String.valueOf(currentSongPage + 1));
        boolean hasPrevious = currentSongPage > 0;
        boolean hasNext = currentSongPage + 1 < getSongPageCount();
        btnPrevPage.setEnabled(hasPrevious);
        btnNextPage.setEnabled(hasNext);
        btnPrevPage.setAlpha(hasPrevious ? 1f : 0.35f);
        btnNextPage.setAlpha(hasNext ? 1f : 0.35f);
        requestFirstSongFocus();
    }

    private void changeSongPage(int delta) {
        int nextPage = Math.max(0, Math.min(currentSongPage + delta, getSongPageCount() - 1));
        if (nextPage == currentSongPage) return;
        currentSongPage = nextPage;
        updateSongPage();
        rvSongs.scrollToPosition(0);
    }

    private void requestFirstSongFocus() {
        if (rvSongs == null || songAdapter == null || songAdapter.getItemCount() == 0) return;
        rvSongs.scrollToPosition(0);
        rvSongs.post(() -> {
            RecyclerView.ViewHolder holder = rvSongs.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private MediaItem createMediaItem(MusicInfo song) {
        Bundle extras = song.toPlaybackExtras();

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
    public void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(requireContext(), new ComponentName(requireContext(), MusicService.class));
        controllerFuture = new MediaController.Builder(requireContext(), sessionToken).buildAsync();
        final ListenableFuture<MediaController> pendingController = controllerFuture;
        controllerFuture.addListener(() -> {
            try {
                MediaController resolved = pendingController.get();
                if (!isAdded() || controllerFuture != pendingController) {
                    MediaController.releaseFuture(pendingController);
                    return;
                }
                player = resolved;
                setupPlayerListener();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(requireContext()));
    }

    @Override
    public void onStop() {
        super.onStop();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        player = null;
    }

    @Override
    public void onDestroyView() {
        rootView = null;
        super.onDestroyView();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        View currentFocus = getActivity() != null ? getActivity().getCurrentFocus() : null;
        if ((keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                && rvSongs != null && songAdapter != null
                && (isWithinView(currentFocus, rvSongs) || currentFocus == rvSongs
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

    private boolean isWithinView(View child, View ancestor) {
        if (child == null || ancestor == null) return false;
        View current = child;
        while (current != null) {
            if (current == ancestor) return true;
            if (!(current.getParent() instanceof View)) return false;
            current = (View) current.getParent();
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

    private static class SourceViewHolder extends RecyclerView.ViewHolder {
        TextView tvSourceName;
        ImageView ivRadio;
        SourceViewHolder(View view) {
            super(view);
            tvSourceName = view.findViewById(R.id.tvSourceName);
            ivRadio = view.findViewById(R.id.ivRadio);
        }
    }

    private class BoardViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        BoardViewHolder(View view) {
            super(view);
            tv = view.findViewById(R.id.tvBoardName);
            tv.setOnClickListener(v -> {
                int position = (int) tv.getTag();
                RankingFragment.this.selectBoard(position);
            });
        }
    }
}
