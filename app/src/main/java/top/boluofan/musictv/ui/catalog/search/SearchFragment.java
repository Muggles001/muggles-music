package top.boluofan.musictv.ui.catalog.search;

import android.content.Intent;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.player.queue.PlaybackQueue;
import top.boluofan.musictv.R;
import top.boluofan.musictv.ui.common.player.FloatingPlayerWindow;
import top.boluofan.musictv.ui.main.MainActivity;
import top.boluofan.musictv.ui.player.PlayerActivity;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.MusicApiProvider;
import top.boluofan.musictv.catalog.SearchCatalogActions;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;
import top.boluofan.musictv.catalog.direct.DirectSourcePlatforms;
import top.boluofan.musictv.library.PlaylistActions;
import top.boluofan.musictv.player.session.MediaControllerConnection;
import top.boluofan.musictv.remote.LocalIpv4Address;
import top.boluofan.musictv.remote.search.SearchRemoteSession;
import top.boluofan.musictv.remote.search.SearchWebServer;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.ui.common.songlist.LxMusicAdapter;
import top.boluofan.musictv.ui.catalog.common.MusicSourceStripAdapter;
import top.boluofan.musictv.ui.common.DialogHelper;
import top.boluofan.musictv.ui.common.FocusAnimationHelper;
import top.boluofan.musictv.util.PageWindow;
import top.boluofan.musictv.ui.common.RecyclerFocus;
import top.boluofan.musictv.ui.common.ViewHierarchy;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import java.util.Collections;

public class SearchFragment extends Fragment implements MainActivity.PrimaryPageKeyHandler {
    private static final String TAG = "SearchFragment";
    private View rootView;

    private EditText etSearch;
    private Button btnSearch;
    private ImageButton btnClear;
    private RecyclerView rvSourceList;
    private RecyclerView rvHotSearch;
    private RecyclerView rvSearchResults;
    private LxMusicAdapter songAdapter;
    private MusicSourceStripAdapter sourceAdapter;
    private ProgressBar loadingProgress;
    private TextView tvTitle;
    private TextView tvSearchSubtitle;
    private TextView tvNoResults;
    private TextView tvResultCount;
    private TextView tvHotSearchTitle;
    private View layoutSearchActions;
    private View layoutSearchPager;
    private ImageButton btnSearchPlayAll;
    private ImageButton btnSearchPlayOrderToggle;
    private ImageButton btnSearchPrevPage;
    private ImageButton btnSearchNextPage;
    private TextView tvSearchPageNumber;

    private MediaController player;
    private MediaControllerConnection controllerConnection;
    private Player.Listener playerListener;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable positionUpdater;

    private String currentSource = "all";
    private int currentSourceIndex = 0;
    private int currentPage = 1;
    private boolean hasMore = true;
    private String lastKeyword = "";
    private List<MusicInfo> allResults = new ArrayList<>();
    private List<String> hotSearchWords = new ArrayList<>();
    private boolean shuffleEnabled = false;
    private static final int SEARCH_SONGS_PER_PAGE = 8;
    private int currentResultPage = 0;
    private int playingGlobalIndex = -1;
    private int searchGeneration = 0;
    private int hotSearchGeneration = 0;
    private SearchCatalogActions searchCatalogActions;
    private SearchCatalogActions.Operation hotSearchOperation;
    private final List<SearchCatalogActions.Operation> searchOperations = new ArrayList<>();

    private String[] SOURCES = {"all", "kw", "kg", "tx", "wy", "mg"};
    private String[] SOURCE_NAMES = {"聚合搜索", "酷我", "酷狗", "QQ音乐", "网易云", "咪咕"};

    private String[] ALL_SOURCES = {"kw", "kg", "tx", "wy", "mg"};
    private String[] ALL_SOURCE_NAMES = {"酷我", "酷狗", "QQ音乐", "网易云", "咪咕"};

    private static final int SEARCH_SERVER_PORT = 8089;
    private SearchRemoteSession searchRemoteSession;
    private ImageButton btnScan;
    private RecyclerView.Adapter<?> hotSearchAdapter;

    private CustomKeyboardPopup customKeyboardPopup;
    private boolean isKeyboardVisible = false;
    private PlaylistActions.Operation playlistChoicesOperation;
    private PlaylistActions.Operation addSongOperation;

    private boolean isPageUsable() {
        return isAdded() && rootView != null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (BackendPreferences.getMode(requireContext()) == BackendMode.DIRECT_SOURCE) {
            DirectSourcePlatforms.Snapshot platforms =
                    DirectSourcePlatforms.snapshot(requireContext());
            ALL_SOURCES = platforms.codes();
            ALL_SOURCE_NAMES = platforms.names();
            SOURCES = new String[ALL_SOURCES.length + 1];
            SOURCE_NAMES = new String[ALL_SOURCE_NAMES.length + 1];
            SOURCES[0] = "all";
            SOURCE_NAMES[0] = "聚合搜索";
            System.arraycopy(ALL_SOURCES, 0, SOURCES, 1, ALL_SOURCES.length);
            System.arraycopy(ALL_SOURCE_NAMES, 0, SOURCE_NAMES, 1, ALL_SOURCE_NAMES.length);
        }
        // MainActivity owns the single full-window gradient. Keeping the
        // fragment transparent prevents the background from restarting at the
        // content rail boundary.
        view.setBackgroundResource(0);
        rootView = view;
        searchCatalogActions = new SearchCatalogActions(MusicApiProvider.get(requireContext()));
        initViews();
        setupRecyclerViews();
        setupListeners();
        setupCustomKeyboard();
        updateResults();
    }

    private void initViews() {
        etSearch = rootView.findViewById(R.id.etSearch);
        btnSearch = rootView.findViewById(R.id.btnSearch);
        btnClear = rootView.findViewById(R.id.btnClear);
        btnScan = rootView.findViewById(R.id.btnScan);
        rvSourceList = rootView.findViewById(R.id.rvSourceList);
        rvHotSearch = rootView.findViewById(R.id.rvHotSearch);
        rvSearchResults = rootView.findViewById(R.id.rvSearchResults);
        loadingProgress = rootView.findViewById(R.id.loadingProgress);
        tvTitle = rootView.findViewById(R.id.tvTitle);
        tvSearchSubtitle = rootView.findViewById(R.id.tvSearchSubtitle);
        tvNoResults = rootView.findViewById(R.id.tvNoResults);
        tvResultCount = rootView.findViewById(R.id.tvResultCount);
        tvHotSearchTitle = rootView.findViewById(R.id.tvHotSearchTitle);
        layoutSearchActions = rootView.findViewById(R.id.layoutSearchActions);
        layoutSearchPager = rootView.findViewById(R.id.layoutSearchPager);
        btnSearchPlayAll = rootView.findViewById(R.id.btnSearchPlayAll);
        btnSearchPlayOrderToggle = rootView.findViewById(R.id.btnSearchPlayOrderToggle);
        btnSearchPrevPage = rootView.findViewById(R.id.btnSearchPrevPage);
        btnSearchNextPage = rootView.findViewById(R.id.btnSearchNextPage);
        tvSearchPageNumber = rootView.findViewById(R.id.tvSearchPageNumber);

        FocusAnimationHelper.applyFocusAnimation(btnClear, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnSearch, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnScan, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnSearchPlayAll, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(
                btnSearchPlayOrderToggle, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnSearchPrevPage, FocusAnimationHelper.Profile.ICON);
        FocusAnimationHelper.applyFocusAnimation(btnSearchNextPage, FocusAnimationHelper.Profile.ICON);

        songAdapter = new LxMusicAdapter();
        songAdapter.setNextFocusDownId(R.id.btnSearchNextPage);
        rvSearchResults.setAdapter(songAdapter);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));

        ImageButton btnBack = rootView.findViewById(R.id.btnBack);
        btnBack.setVisibility(View.GONE);

        // 禁用系统键盘弹出，由自定义键盘处理输入
        etSearch.setShowSoftInputOnFocus(false);
    }

    private void setupRecyclerViews() {
        rvSourceList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));

        sourceAdapter = new MusicSourceStripAdapter(SOURCE_NAMES, currentSourceIndex,
                this::selectSource, (position, keyCode) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && position == 0) {
                return focusPrimaryRail();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && position == SOURCES.length - 1) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && allResults.isEmpty()) {
                return hotSearchWords.isEmpty() || focusHotSearch(0);
            }
            return false;
        });
        sourceAdapter.setOnItemBindListener((itemView, position) -> itemView.setNextFocusDownId(
                allResults.isEmpty() ? View.NO_ID : R.id.btnSearchPlayAll));
        rvSourceList.setAdapter(sourceAdapter);

        rvHotSearch.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvHotSearch.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.left = dp(3);
                outRect.right = dp(3);
            }
        });
        hotSearchAdapter = new androidx.recyclerview.widget.RecyclerView.Adapter<HotSearchViewHolder>() {
            @NonNull
            @Override
            public HotSearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                tv.setPadding(dp(16), dp(6), dp(16), dp(6));
                tv.setTextSize(14);
                tv.setTextColor(getResources().getColorStateList(R.color.selector_action_content));
                tv.setBackgroundResource(R.drawable.bg_tab_selected);
                tv.setFocusable(true);
                tv.setClickable(true);
                return new HotSearchViewHolder(tv);
            }

            @Override
            public void onBindViewHolder(@NonNull HotSearchViewHolder holder, int position) {
                String hotWord = hotSearchWords.size() > position ? hotSearchWords.get(position) : "";
                holder.tv.setText(hotWord);
                holder.tv.setNextFocusDownId(allResults.isEmpty() ? View.NO_ID : R.id.btnSearchPlayAll);
                holder.tv.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    int currentPosition = holder.getAdapterPosition();
                    if (currentPosition == RecyclerView.NO_POSITION) return true;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        return sourceAdapter.requestFocusAt(currentSourceIndex);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        return currentPosition == 0
                                ? focusPrimaryRail() : focusHotSearch(currentPosition - 1);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            && currentPosition < getItemCount() - 1) {
                        return focusHotSearch(currentPosition + 1);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
                    return false;
                });
                holder.tv.setOnClickListener(v -> {
                    hideCustomKeyboard();
                    etSearch.setText(hotWord);
                    search(hotWord);
                });
            }

            @Override
            public int getItemCount() {
                return Math.min(hotSearchWords.size(), 10);
            }
        };
        rvHotSearch.setAdapter(hotSearchAdapter);

        songAdapter.setOnItemClickListener((song, position) -> {
            playSong(song);
        });

        songAdapter.setOnPlayClickListener((song, position) -> {
            playSong(song);
        });

        songAdapter.setOnFullscreenClickListener((song, position) -> {
            if (!playSong(song)) return;
            startActivity(new Intent(requireContext(), top.boluofan.musictv.ui.player.PlayerActivity.class));
        });

        songAdapter.setOnFavClickListener((song, position) -> {
            collectSingleSong(song);
        });
        songAdapter.setNextFocusLeftId(R.id.tabSearch);
        songAdapter.setOnFirstItemUpListener(() -> {
            return btnSearchPlayAll != null && btnSearchPlayAll.isShown()
                    && btnSearchPlayAll.requestFocus();
        });
        rvSearchResults.setPreserveFocusAfterLayout(true);

        View.OnKeyListener actionNavigation = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return requestFirstResultFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return focusSearchHeader();
            }
            return false;
        };
        btnSearchPlayAll.setOnKeyListener(actionNavigation);
        btnSearchPlayOrderToggle.setOnKeyListener(actionNavigation);
        btnSearchPlayAll.setOnClickListener(v -> {
            playAllResults();
            FocusAnimationHelper.keepFocusAfterPlayback(v);
        });
        btnSearchPlayOrderToggle.setOnClickListener(v -> {
            shuffleEnabled = !shuffleEnabled;
            updatePlaybackModeButton();
            if (player != null) player.setShuffleModeEnabled(shuffleEnabled);
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        btnSearchPrevPage.setOnClickListener(v -> {
            changeResultPage(-1);
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        btnSearchNextPage.setOnClickListener(v -> {
            changeResultPage(1);
            FocusAnimationHelper.keepFocusAfterClick(v);
        });
        View.OnKeyListener pagerNavigation = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return requestLastResultFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            return false;
        };
        btnSearchPrevPage.setOnKeyListener(pagerNavigation);
        btnSearchNextPage.setOnKeyListener(pagerNavigation);
        updatePlaybackModeButton();
    }

    private boolean requestFirstResultFocus() {
        if (rvSearchResults == null || songAdapter.getItemCount() == 0) return true;
        rvSearchResults.setVisibility(View.VISIBLE);
        return songAdapter.requestFocusAt(rvSearchResults, 0);
    }

    private boolean requestLastResultFocus() {
        if (rvSearchResults == null || songAdapter.getItemCount() == 0) return true;
        int position = songAdapter.getItemCount() - 1;
        return songAdapter.requestFocusAt(rvSearchResults, position);
    }

    private boolean focusSearchHeader() {
        if (rvHotSearch != null && rvHotSearch.getVisibility() == View.VISIBLE
                && rvHotSearch.getChildCount() > 0) {
            return rvHotSearch.getChildAt(0).requestFocus();
        }
        return rvSourceList != null && rvSourceList.getChildCount() > 0
                && rvSourceList.getChildAt(0).requestFocus();
    }

    private void updateSearchFocusPath(boolean hasSearchResults) {
        int downId = hasSearchResults ? R.id.btnSearchPlayAll : View.NO_ID;
        if (rvSourceList != null) {
            for (int i = 0; i < rvSourceList.getChildCount(); i++) {
                rvSourceList.getChildAt(i).setNextFocusDownId(downId);
            }
        }
        if (rvHotSearch != null) {
            for (int i = 0; i < rvHotSearch.getChildCount(); i++) {
                rvHotSearch.getChildAt(i).setNextFocusDownId(downId);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void selectSource(int position) {
        if (position < 0 || position >= SOURCES.length) return;

        View focusedBeforeUpdate = getActivity() != null
                ? getActivity().getCurrentFocus() : null;
        boolean retainSourceFocus = ViewHierarchy.contains(rvSourceList, focusedBeforeUpdate);

        currentSourceIndex = position;
        currentSource = SOURCES[position];

        sourceAdapter.setSelectedPosition(position);

        if (retainSourceFocus) rvSourceList.post(() -> {
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

        loadHotSearch();
    }

    private boolean focusHotSearch(int position) {
        if (rvHotSearch == null || position < 0 || hotSearchAdapter == null
                || position >= hotSearchAdapter.getItemCount()) {
            return false;
        }
        return RecyclerFocus.requestPosition(rvHotSearch, position);
    }

    private boolean focusPrimaryRail() {
        if (getActivity() == null) return false;
        View tabSearch = getActivity().findViewById(R.id.tabSearch);
        return tabSearch != null && tabSearch.requestFocus();
    }

    private void loadHotSearch() {
        final int generation = ++hotSearchGeneration;
        final String source = currentSource;
        if (hotSearchOperation != null) hotSearchOperation.cancel();
        if ("all".equals(source)) {
            hotSearchWords.clear();
            if (hotSearchAdapter != null) {
                hotSearchAdapter.notifyDataSetChanged();
            }
            requireActivity().runOnUiThread(() -> updateResults());
            return;
        }

        hotSearchOperation = searchCatalogActions.loadHotSearch(
                source, new SearchCatalogActions.Callback<List<String>>() {
            @Override
            public void onSuccess(List<String> words) {
                if (!isPageUsable() || generation != hotSearchGeneration
                        || !source.equals(currentSource)) return;
                hotSearchOperation = null;
                hotSearchWords.clear();
                hotSearchWords.addAll(words);
                if (hotSearchAdapter != null) {
                    hotSearchAdapter.notifyDataSetChanged();
                }
                requireActivity().runOnUiThread(() -> updateResults());
            }

            @Override
            public void onFailure(SearchCatalogActions.Failure failure) {
                if (!isPageUsable() || generation != hotSearchGeneration
                        || !source.equals(currentSource)) return;
                hotSearchOperation = null;
            }
        });
    }

    private void setupListeners() {
        btnSearch.setOnClickListener(v -> {
            String keyword = etSearch.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(requireContext(), "请输入搜索关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            hideCustomKeyboard();
            search(keyword);
        });

        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            lastKeyword = "";
            allResults.clear();
            updateResults();
        });

        etSearch.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            // An EditText normally consumes directional keys to move its text
            // cursor. On TV those keys belong to the page focus graph.
            if (!isKeyboardVisible) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    return focusPrimaryRail();
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return btnClear.requestFocus();
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return sourceAdapter.requestFocusAt(0);
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    showCustomKeyboard();
                    return true;
                }
            }
            return false;
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (!isKeyboardVisible) {
                    showCustomKeyboard();
                } else {
                    String keyword = etSearch.getText().toString().trim();
                    if (!keyword.isEmpty()) {
                        hideCustomKeyboard();
                        search(keyword);
                    }
                }
                return true;
            }
            return false;
        });

        btnScan.setOnClickListener(v -> showScanSearchDialog());
    }

    private void setupCustomKeyboard() {
        customKeyboardPopup = new CustomKeyboardPopup(requireActivity());
        customKeyboardPopup.setSource(currentSource);
        customKeyboardPopup.setOnSearchListener(new CustomKeyboardPopup.OnSearchListener() {
            @Override
            public void onSearch(String keyword) {
                search(keyword);
            }

            @Override
            public void onInputChanged(String text) {
                etSearch.setText(text);
                etSearch.setSelection(text.length());
            }
        });
    }

    private void showCustomKeyboard() {
        if (customKeyboardPopup == null) {
            customKeyboardPopup = new CustomKeyboardPopup(requireActivity());
            customKeyboardPopup.setOnSearchListener(new CustomKeyboardPopup.OnSearchListener() {
                @Override
                public void onSearch(String keyword) {
                    search(keyword);
                }

                @Override
                public void onInputChanged(String text) {
                    etSearch.setText(text);
                    etSearch.setSelection(text.length());
                }
            });
        }
        if (customKeyboardPopup.isShowing()) {
            return;
        }
        isKeyboardVisible = true;

        // 阻止系统输入法管理器干扰
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && etSearch != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }

        customKeyboardPopup.setSource(currentSource);
        customKeyboardPopup.show(etSearch.getText().toString());
    }

    private void hideCustomKeyboard() {
        boolean wasKeyboardVisible = isKeyboardVisible
                || (customKeyboardPopup != null && customKeyboardPopup.isShowing());
        isKeyboardVisible = false;
        if (customKeyboardPopup != null) {
            customKeyboardPopup.dismiss();
        }
        if (!wasKeyboardVisible) return;
        // 清除 EditText 的焦点，防止 InputMethodManager 继续尝试管理它
        if (etSearch != null) {
            etSearch.clearFocus();
        }
        // 一级页没有返回按钮；关闭键盘后将焦点还给搜索框。
        if (etSearch != null) {
            etSearch.post(() -> {
                if (!isPageUsable() || getActivity() == null) return;
                View currentFocus = getActivity().getCurrentFocus();
                if (currentFocus == null || currentFocus == etSearch) {
                    etSearch.requestFocus();
                }
            });
        }
    }

    private void showScanSearchDialog() {
        String ipAddress = LocalIpv4Address.find();
        if (ipAddress == null) {
            Toast.makeText(requireContext(), "无法获取局域网地址", Toast.LENGTH_SHORT).show();
            return;
        }

        String searchUrl = "http://" + ipAddress + ":" + SEARCH_SERVER_PORT;
        stopSearchSession();

        AlertDialog qrDialog = DialogHelper.showQrCodeDialog(
            requireActivity(),
            "扫码搜索",
            "在手机浏览器访问地址后搜索歌曲",
            searchUrl,
            searchUrl
        );

        SearchRemoteSession session = new SearchRemoteSession(listener ->
                new SearchWebServer(SEARCH_SERVER_PORT, listener::onSearch));
        searchRemoteSession = session;
        qrDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "关闭",
                (dialog, which) -> stopSearchSession(session));

        try {
            session.start((keyword, source) ->
                    mainHandler.post(() -> {
                        if (!isPageUsable() || searchRemoteSession != session) return;
                        searchRemoteSession = null;
                        qrDialog.dismiss();

                        if (source != null && !source.isEmpty()) {
                            int sourceIdx = -1;
                            for (int i = 0; i < SOURCES.length; i++) {
                                if (SOURCES[i].equals(source)) {
                                    sourceIdx = i;
                                    break;
                                }
                            }
                            if (sourceIdx >= 0) {
                                selectSource(sourceIdx);
                            }
                        }

                        etSearch.setText(keyword);
                        search(keyword);
                        Toast.makeText(requireContext(),
                                "收到推送的搜索: " + keyword,
                                Toast.LENGTH_SHORT).show();
                    }));
        } catch (Exception e) {
            stopSearchSession(session);
            Toast.makeText(requireContext(), "服务启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        qrDialog.setOnDismissListener(dialog -> stopSearchSession(session));

        qrDialog.show();
    }

    private void stopSearchSession(SearchRemoteSession session) {
        if (searchRemoteSession != session) return;
        session.close();
        searchRemoteSession = null;
    }

    private void stopSearchSession() {
        if (searchRemoteSession == null) return;
        searchRemoteSession.close();
        searchRemoteSession = null;
    }

    private void search(String keyword) {
        hideCustomKeyboard();
        final int generation = ++searchGeneration;
        cancelSearchOperations();
        lastKeyword = keyword;
        currentPage = 1;
        currentResultPage = 0;
        playingGlobalIndex = -1;
        songAdapter.setPlayingIndex(-1);
        hasMore = true;
        allResults.clear();
        showLoading(true);

        if ("all".equals(currentSource)) {
            searchAllSources(keyword, generation);
        } else {
            searchSingleSource(keyword, currentSource, generation);
        }
    }

    private void searchAllSources(String keyword, int generation) {
        List<MusicInfo>[] results = new List[ALL_SOURCES.length];
        int[] completed = new int[1];
        for (int i = 0; i < ALL_SOURCES.length; i++) {
            final int index = i;
            final String source = ALL_SOURCES[index];

            SearchCatalogActions.Operation operation = searchCatalogActions.search(
                    keyword, source, 1, 30,
                    new SearchCatalogActions.Callback<List<MusicInfo>>() {
                @Override
                public void onSuccess(List<MusicInfo> loadedSongs) {
                    if (!isPageUsable() || generation != searchGeneration) return;
                    synchronized (completed) {
                        results[index] = loadedSongs;
                        completed[0]++;

                        if (completed[0] == ALL_SOURCES.length) {
                            mergeAllResults(results, generation);
                        }
                    }
                }

                @Override
                public void onFailure(SearchCatalogActions.Failure failure) {
                    if (!isPageUsable() || generation != searchGeneration) return;
                    synchronized (completed) {
                        results[index] = new ArrayList<>();
                        completed[0]++;

                        if (completed[0] == ALL_SOURCES.length) {
                            mergeAllResults(results, generation);
                        }
                    }
                }
            });
            searchOperations.add(operation);
        }
    }

    private void mergeAllResults(List<MusicInfo>[] results, int generation) {
        if (!isPageUsable() || generation != searchGeneration) return;
        allResults.clear();
        for (List<MusicInfo> list : results) {
            if (list != null) {
                for (MusicInfo song : list) {
                    allResults.add(song);
                }
            }
        }
        hasMore = false;
        searchOperations.clear();
        showLoading(false);
        updateResults();
    }

    private void searchSingleSource(String keyword, String source, int generation) {
        SearchCatalogActions.Operation operation = searchCatalogActions.search(
                keyword, source, currentPage, 30,
                new SearchCatalogActions.Callback<List<MusicInfo>>() {
            @Override
            public void onSuccess(List<MusicInfo> result) {
                if (!isPageUsable() || generation != searchGeneration
                        || !source.equals(currentSource)) return;
                searchOperations.clear();
                showLoading(false);
                allResults.addAll(result);
                hasMore = result.size() >= 30;
                updateResults();
            }

            @Override
            public void onFailure(SearchCatalogActions.Failure failure) {
                if (!isPageUsable() || generation != searchGeneration
                        || !source.equals(currentSource)) return;
                searchOperations.clear();
                showLoading(false);
                if (failure.getKind() == SearchCatalogActions.Failure.Kind.TRANSPORT) {
                    Throwable cause = failure.getCause();
                    Toast.makeText(requireContext(), "网络错误: "
                                    + (cause == null ? null : cause.getMessage()),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "搜索失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        searchOperations.add(operation);
    }

    private void cancelSearchOperations() {
        for (SearchCatalogActions.Operation operation : searchOperations) {
            operation.cancel();
        }
        searchOperations.clear();
    }

    private void updateResults() {
        boolean hasHotSearch = !hotSearchWords.isEmpty();
        boolean hasSearchResults = !allResults.isEmpty();
        updateSearchFocusPath(hasSearchResults);

        tvTitle.setVisibility(View.VISIBLE);
        tvSearchSubtitle.setVisibility(View.VISIBLE);
        boolean showDiscovery = hasHotSearch && !hasSearchResults;
        tvHotSearchTitle.setVisibility(showDiscovery ? View.VISIBLE : View.GONE);
        rvHotSearch.setVisibility(showDiscovery ? View.VISIBLE : View.GONE);

        if (hasSearchResults) {
            tvResultCount.setVisibility(View.VISIBLE);
            tvResultCount.setText(getString(R.string.search_result_count, allResults.size()));
            layoutSearchActions.setVisibility(View.VISIBLE);
            layoutSearchPager.setVisibility(View.VISIBLE);
            rvSearchResults.setVisibility(View.VISIBLE);
            tvNoResults.setVisibility(View.GONE);
        } else {
            tvResultCount.setVisibility(View.GONE);
            layoutSearchActions.setVisibility(View.GONE);
            layoutSearchPager.setVisibility(View.GONE);
            rvSearchResults.setVisibility(View.GONE);
            if (!hasHotSearch) {
                tvNoResults.setVisibility(View.VISIBLE);
            } else {
                tvNoResults.setVisibility(View.GONE);
            }
        }

        if (hasSearchResults) {
            updateResultPageUi();
        } else {
            songAdapter.setSongs(new ArrayList<>());
            songAdapter.setIndexOffset(0);
        }
    }

    private void updateResultPageUi() {
        PageWindow<MusicInfo> page = PageWindow.of(allResults, currentResultPage, SEARCH_SONGS_PER_PAGE);
        currentResultPage = page.getPageIndex();
        songAdapter.setIndexOffset(page.getStartIndex());
        songAdapter.setSongs(page.getItems());
        tvSearchPageNumber.setText(String.valueOf(currentResultPage + 1));
        boolean hasPrevious = page.hasPrevious();
        boolean hasNext = page.hasNext();
        btnSearchPrevPage.setEnabled(hasPrevious);
        btnSearchNextPage.setEnabled(hasNext);
        btnSearchPrevPage.setAlpha(hasPrevious ? 1f : 0.35f);
        btnSearchNextPage.setAlpha(hasNext ? 1f : 0.35f);
        updateVisiblePlayingIndex();
    }

    private void changeResultPage(int delta) {
        int nextPage = PageWindow.of(allResults, currentResultPage, SEARCH_SONGS_PER_PAGE).pageAfter(delta);
        if (nextPage == currentResultPage) return;
        currentResultPage = nextPage;
        updateResultPageUi();
        rvSearchResults.scrollToPosition(0);
        requestFirstResultFocus();
    }

    private void updateVisiblePlayingIndex() {
        int globalIndex = playingGlobalIndex;
        int pageStart = currentResultPage * SEARCH_SONGS_PER_PAGE;
        int pageEnd = pageStart + songAdapter.getItemCount();
        if (globalIndex >= pageStart && globalIndex < pageEnd) {
            songAdapter.setPlayingIndex(globalIndex - pageStart);
        } else {
            songAdapter.setPlayingIndex(-1);
        }
    }

    private boolean playSong(MusicInfo song) {
        if (player == null) {
            Toast.makeText(requireContext(), "播放器未初始化", Toast.LENGTH_SHORT).show();
            return false;
        }

        int index = allResults.indexOf(song);
        boolean started;
        if (index >= 0) {
            started = playResultAtIndex(index);
        } else {
            PlaybackQueue queue = PlaybackQueue.from(Collections.singletonList(song));
            if (queue.isEmpty()) {
                Toast.makeText(requireContext(), "该歌曲缺少播放信息", Toast.LENGTH_SHORT).show();
                return false;
            }
            player.setMediaItem(queue.getMediaItems().get(0));
            player.setShuffleModeEnabled(shuffleEnabled);
            player.prepare();
            player.play();
            started = true;
        }

        if (started) {
            Toast.makeText(requireContext(), "正在播放: " + song.getName(), Toast.LENGTH_SHORT).show();
        }
        return started;
    }

    private void playAllResults() {
        if (allResults.isEmpty() || player == null) return;
        playResultAtIndex(0);
    }

    private boolean playResultAtIndex(int index) {
        if (player == null || index < 0 || index >= allResults.size()) return false;
        PlaybackQueue queue = PlaybackQueue.from(allResults);
        if (!queue.playSourceIndex(player, index, shuffleEnabled)) {
            Toast.makeText(requireContext(), "该歌曲缺少播放信息", Toast.LENGTH_SHORT).show();
            return false;
        }
        playingGlobalIndex = index;
        int pageStart = currentResultPage * SEARCH_SONGS_PER_PAGE;
        int pageEnd = pageStart + songAdapter.getItemCount();
        songAdapter.setPlayingIndex(index >= pageStart && index < pageEnd ? index - pageStart : -1);
        return true;
    }

    private void updatePlaybackModeButton() {
        if (btnSearchPlayOrderToggle == null) return;
        btnSearchPlayOrderToggle.setImageResource(shuffleEnabled
                ? R.drawable.ic_shuffle : R.drawable.ic_repeat);
        btnSearchPlayOrderToggle.setContentDescription(shuffleEnabled
                ? "切换为顺序播放" : "切换为随机播放");
        btnSearchPlayOrderToggle.setSelected(shuffleEnabled);
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
                        player.setShuffleModeEnabled(shuffleEnabled);
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
        if (positionUpdater != null) {
            mainHandler.removeCallbacks(positionUpdater);
        }
        super.onPause();
    }

    private void setupPlayerListener() {
        if (player == null) return;

        if (playerListener != null) player.removeListener(playerListener);
        playerListener = new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                songAdapter.setPlayerPlaying(isPlaying);
                songAdapter.restorePendingPlaybackFocus();
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (mediaItem == null || mediaItem.mediaId == null) return;
                for (int i = 0; i < allResults.size(); i++) {
                    if (mediaItem.mediaId.equals(allResults.get(i).getSongmid())) {
                        playingGlobalIndex = i;
                        int pageStart = currentResultPage * SEARCH_SONGS_PER_PAGE;
                        int pageEnd = pageStart + songAdapter.getItemCount();
                        songAdapter.setPlayingIndex(i >= pageStart && i < pageEnd ? i - pageStart : -1);
                        break;
                    }
                }
            }
        };
        player.addListener(playerListener);
    }

    private static class HotSearchViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        HotSearchViewHolder(TextView tv) {
            super(tv);
            this.tv = tv;
            FocusAnimationHelper.applyFocusAnimation(tv, FocusAnimationHelper.Profile.RAIL);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isKeyboardVisible) {
                hideCustomKeyboard();
                return true;
            }
        }

        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                && !isKeyboardVisible) {
            View currentFocus = requireActivity().getCurrentFocus();
            if (currentFocus == etSearch) {
                showCustomKeyboard();
                return true;
            }
        }

        View currentFocus = requireActivity().getCurrentFocus();
        if ((keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                && rvSearchResults != null && songAdapter != null
                && (ViewHierarchy.contains(rvSearchResults, currentFocus) || currentFocus == rvSearchResults
                || (currentFocus == null && songAdapter.hasFocusHistory()))) {
            return songAdapter.handleVerticalKey(currentFocus != null ? currentFocus : rvSearchResults, keyCode);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                && (currentFocus == btnSearchPrevPage || currentFocus == btnSearchNextPage)) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (currentFocus != null) {
                if (currentFocus.getParent() == rvSourceList) {
                    if (!"all".equals(currentSource) && hotSearchWords.isEmpty()) {
                        return true;
                    }
                } else if (currentFocus.getParent() == rvHotSearch) {
                    if (!"all".equals(currentSource) && allResults.isEmpty()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public void onDestroyView() {
        searchGeneration++;
        hotSearchGeneration++;
        cancelSearchOperations();
        if (hotSearchOperation != null) hotSearchOperation.cancel();
        if (playlistChoicesOperation != null) playlistChoicesOperation.cancel();
        if (addSongOperation != null) addSongOperation.cancel();
        mainHandler.removeCallbacksAndMessages(null);
        stopSearchSession();
        if (customKeyboardPopup != null) {
            customKeyboardPopup.dismiss();
            customKeyboardPopup = null;
        }
        rootView = null;
        super.onDestroyView();
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
}
