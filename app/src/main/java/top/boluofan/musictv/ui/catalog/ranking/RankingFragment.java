package top.boluofan.musictv.ui.catalog.ranking;

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
import top.boluofan.musictv.player.queue.PlaybackQueue;
import top.boluofan.musictv.ui.player.PlayerActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.ui.common.DialogHelper;
import top.boluofan.musictv.ui.common.player.FloatingPlayerWindow;
import top.boluofan.musictv.ui.main.MainActivity;
import top.boluofan.musictv.catalog.RankingCatalogActions;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.MusicApiProvider;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;
import top.boluofan.musictv.catalog.direct.DirectSourcePlatforms;
import top.boluofan.musictv.library.PlaylistActions;
import top.boluofan.musictv.player.session.MediaControllerConnection;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.RankingBoard;
import top.boluofan.musictv.ui.common.songlist.LxMusicAdapter;
import top.boluofan.musictv.ui.catalog.common.MusicSourceStripAdapter;
import top.boluofan.musictv.ui.common.FocusAnimationHelper;
import top.boluofan.musictv.util.PageWindow;
import top.boluofan.musictv.ui.common.RecyclerFocus;
import top.boluofan.musictv.ui.common.ViewHierarchy;
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

    private String[] SOURCES = {"tx", "mg", "kw", "kg", "wy"};
    private String[] SOURCE_NAMES = {"QQ音乐", "咪咕", "酷我", "酷狗", "网易云"};

    private List<RankingBoard> boards = new ArrayList<>();
    private List<MusicInfo> songs = new ArrayList<>();

    private LxMusicAdapter songAdapter;
    private MusicSourceStripAdapter sourceAdapter;
    private MediaController player;
    private MediaControllerConnection controllerConnection;
    private Player.Listener playerListener;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable positionUpdater;
    private static final int SONGS_PER_PAGE = 8;
    private int currentSongPage = 0;
    private long boardRequestGeneration = 0L;
    private long songRequestGeneration = 0L;
    private RankingCatalogActions rankingActions;
    private RankingCatalogActions.Operation boardOperation;
    private RankingCatalogActions.Operation songOperation;
    private PlaylistActions.Operation playlistChoicesOperation;
    private PlaylistActions.Operation addSongOperation;
    private PlaylistActions.Operation collectPlaylistOperation;

    private boolean isPageUsable() {
        return isAdded() && rootView != null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ranking, container, false);
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
        rankingActions = new RankingCatalogActions(MusicApiProvider.get(requireContext()));
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
                RankingBoard board = boards.get(position);
                holder.tv.setText(board.getName());
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
            startActivity(new Intent(requireContext(), top.boluofan.musictv.ui.player.PlayerActivity.class));
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
            Intent intent = new Intent(requireContext(), top.boluofan.musictv.ui.settings.connection.ConfigActivity.class);
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
        final RankingBoard boardSnapshot = currentBoardIndex >= 0 && currentBoardIndex < boards.size()
                ? boards.get(currentBoardIndex) : null;
        final String boardNameSnapshot = boardSnapshot != null
                && boardSnapshot.getName() != null && !boardSnapshot.getName().isEmpty()
                ? boardSnapshot.getName()
                : SOURCE_NAMES[Math.max(0, Math.min(sourceIndexSnapshot, SOURCE_NAMES.length - 1))] + "排行榜";
        final List<MusicInfo> songsSnapshot = new ArrayList<>(songs);

        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        PlaylistActions actions = new PlaylistActions(
                MusicApiProvider.get(requireContext()), username, password, token);

        btnFavorite.setEnabled(false);
        collectPlaylistOperation = actions.collectPlaylist(
                boardNameSnapshot, sourceSnapshot, boardIdSnapshot, songsSnapshot,
                new PlaylistActions.CollectCallback() {
            @Override
            public void onCreated() {
                if (!isPageUsable()) return;
                btnFavorite.setEnabled(true);
                Toast.makeText(requireContext(), "收藏成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onOverwriteRequired(
                    PlaylistActions.OverwriteConfirmation confirmation) {
                if (!isPageUsable()) return;
                btnFavorite.setEnabled(true);
                DialogHelper.showOverwriteConfirmDialog(
                        requireContext(), boardNameSnapshot, new DialogHelper.IDialogCallback() {
                            @Override
                            public void onConfirm() {
                                if (!isPageUsable()) return;
                                btnFavorite.setEnabled(false);
                                confirmation.confirm();
                            }

                            @Override
                            public void onCancel() {
                            }
                        });
            }

            @Override
            public void onOverwritten() {
                if (!isPageUsable()) return;
                btnFavorite.setEnabled(true);
                Toast.makeText(requireContext(), "覆盖成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(PlaylistActions.Failure failure) {
                if (!isPageUsable()) return;
                btnFavorite.setEnabled(true);
                String message;
                switch (failure.getKind()) {
                    case READ_RESPONSE:
                        message = "获取歌单失败";
                        break;
                    case TRANSPORT:
                        message = playlistFailureMessage(failure, "网络错误");
                        break;
                    case WRITE_RESPONSE:
                    default:
                        message = "收藏失败";
                        break;
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void collectSingleSong(MusicInfo song) {
        if (!LxRetrofitClient.isLoggedIn(requireContext()) && !BackendPreferences.usesLocalLibrary(requireContext())) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(requireContext(), top.boluofan.musictv.ui.settings.connection.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(requireContext()));
            startActivity(intent);
            return;
        }

        String username = LxRetrofitClient.getUsername(requireContext());
        String password = LxRetrofitClient.getPassword(requireContext());
        String token = LxRetrofitClient.getToken(requireContext());
        PlaylistActions actions = new PlaylistActions(
                MusicApiProvider.get(requireContext()), username, password, token);

        playlistChoicesOperation = actions.loadChoices(new PlaylistActions.Callback<List<String>>() {
            @Override
            public void onSuccess(List<String> playlistNames) {
                if (!isPageUsable()) return;
                DialogHelper.showPlaylistPickerDialog(requireContext(), "选择歌单",
                        playlistNames.toArray(new String[0]), (dialog, which) -> {
                    if (!isPageUsable() || which < 0 || which >= playlistNames.size()) return;
                    addSongOperation = actions.addSong(
                            playlistNames.get(which), song,
                            new PlaylistActions.Callback<PlaylistActions.AddResult>() {
                                @Override
                                public void onSuccess(PlaylistActions.AddResult result) {
                                    if (!isPageUsable()) return;
                                    Toast.makeText(requireContext(),
                                            "已添加到「" + result.getPlaylistName() + "」",
                                            Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure(PlaylistActions.Failure failure) {
                                    showAddSongFailure(failure);
                                }
                            });
                });
            }

            @Override
            public void onFailure(PlaylistActions.Failure failure) {
                if (!isPageUsable()) return;
                String message = failure.getKind() == PlaylistActions.Failure.Kind.NO_PLAYLISTS
                        ? "暂无歌单，请先在歌单库创建歌单"
                        : playlistFailureMessage(failure, "获取歌单失败");
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddSongFailure(PlaylistActions.Failure failure) {
        if (!isPageUsable()) return;
        String message;
        switch (failure.getKind()) {
            case READ_RESPONSE:
                message = "获取歌单失败";
                break;
            case DUPLICATE_SONG:
                message = "歌曲已存在于此歌单";
                break;
            case WRITE_RESPONSE:
                message = "添加失败";
                break;
            case TRANSPORT:
                message = playlistFailureMessage(failure, "网络错误");
                break;
            case NO_PLAYLISTS:
            case TARGET_NOT_FOUND:
            default:
                message = "歌单不存在";
                break;
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private static String playlistFailureMessage(PlaylistActions.Failure failure,
                                                 String fallback) {
        Throwable cause = failure.getCause();
        return failure.getKind() == PlaylistActions.Failure.Kind.TRANSPORT
                ? "网络错误: " + (cause != null ? cause.getMessage() : null)
                : fallback;
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
        currentBoardId = getBangId(boards.get(position).getId());
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
        if (boardOperation != null) boardOperation.cancel();
        if (songOperation != null) songOperation.cancel();
        showLoading(true);

        boardOperation = rankingActions.loadBoards(
                sourceSnapshot, new RankingCatalogActions.Callback<List<RankingBoard>>() {
            @Override
            public void onSuccess(List<RankingBoard> loadedBoards) {
                if (!isCurrentBoardRequest(requestGeneration, sourceSnapshot)) return;
                boardOperation = null;
                showLoading(false);
                boards.clear();
                boards.addAll(loadedBoards);
                if (rvBoards.getAdapter() != null) {
                    rvBoards.getAdapter().notifyDataSetChanged();
                }
                if (!boards.isEmpty()) {
                    // Loading a source must not steal focus from the source chip.
                    selectBoard(0, false);
                }
            }

            @Override
            public void onFailure(RankingCatalogActions.Failure failure) {
                if (!isCurrentBoardRequest(requestGeneration, sourceSnapshot)) return;
                boardOperation = null;
                showLoading(false);
                showRankingFailure(failure);
            }
        });
    }

    private void loadSongs() {
        if (currentBoardId.isEmpty()) return;

        final long requestGeneration = ++songRequestGeneration;
        final String sourceSnapshot = currentSource;
        final String boardIdSnapshot = currentBoardId;

        if (songOperation != null) songOperation.cancel();
        showLoading(true);

        songOperation = rankingActions.loadSongs(
                sourceSnapshot, boardIdSnapshot,
                new RankingCatalogActions.Callback<List<MusicInfo>>() {
            @Override
            public void onSuccess(List<MusicInfo> loadedSongs) {
                if (!isCurrentSongRequest(requestGeneration, sourceSnapshot, boardIdSnapshot)) return;
                songOperation = null;
                showLoading(false);
                if (!isCurrentSongRequest(
                        requestGeneration, sourceSnapshot, boardIdSnapshot)) return;
                songs.clear();
                songs.addAll(loadedSongs);
                currentSongPage = 0;
                updateSongPage();
            }

            @Override
            public void onFailure(RankingCatalogActions.Failure failure) {
                if (!isCurrentSongRequest(requestGeneration, sourceSnapshot, boardIdSnapshot)) return;
                songOperation = null;
                showLoading(false);
                showRankingFailure(failure);
            }
        });
    }

    private void showRankingFailure(RankingCatalogActions.Failure failure) {
        String message;
        if (failure.getKind() == RankingCatalogActions.Failure.Kind.TRANSPORT) {
            Throwable cause = failure.getCause();
            message = "网络错误: " + (cause == null ? null : cause.getMessage());
        } else if (failure.getKind() == RankingCatalogActions.Failure.Kind.PARSE) {
            Throwable cause = failure.getCause();
            message = "解析失败: " + (cause == null ? null : cause.getMessage());
        } else {
            message = "加载失败";
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
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

    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
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
                        setupPlayerListener();
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
        boardRequestGeneration++;
        songRequestGeneration++;
        if (boardOperation != null) boardOperation.cancel();
        if (songOperation != null) songOperation.cancel();
        if (playlistChoicesOperation != null) playlistChoicesOperation.cancel();
        if (addSongOperation != null) addSongOperation.cancel();
        if (collectPlaylistOperation != null) collectPlaylistOperation.cancel();
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

        if (playerListener != null) player.removeListener(playerListener);
        playerListener = new Player.Listener() {
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
        };
        player.addListener(playerListener);
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
