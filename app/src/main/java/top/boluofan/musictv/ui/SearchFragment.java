package top.boluofan.musictv.ui;

import android.content.Intent;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.widget.ImageView;
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
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.PlaybackQueue;
import top.boluofan.musictv.R;
import top.boluofan.musictv.SearchWebServer;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.PlayerActivity;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import top.boluofan.musictv.util.DialogHelper;
import top.boluofan.musictv.util.FocusAnimationHelper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Objects;

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
    private ProgressBar loadingProgress;
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
    private ListenableFuture<MediaController> controllerFuture;
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

    private final String[] SOURCES = {"all", "kw", "kg", "tx", "wy", "mg"};
    private final String[] SOURCE_NAMES = {"聚合搜索", "酷我", "酷狗", "QQ音乐", "网易云", "咪咕"};

    private final String[] ALL_SOURCES = {"kw", "kg", "tx", "wy", "mg"};
    private final String[] ALL_SOURCE_NAMES = {"酷我", "酷狗", "QQ音乐", "网易云", "咪咕"};

    private static final int SEARCH_SERVER_PORT = 8089;
    private SearchWebServer searchWebServer;
    private ImageButton btnScan;
    private RecyclerView.Adapter<?> hotSearchAdapter;

    private CustomKeyboardPopup customKeyboardPopup;
    private boolean isKeyboardVisible = false;

    private boolean isPageUsable() {
        return isAdded() && rootView != null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // MainActivity owns the single full-window gradient. Keeping the
        // fragment transparent prevents the background from restarting at the
        // content rail boundary.
        view.setBackgroundResource(0);
        rootView = view;
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
                holder.itemView.setNextFocusDownId(allResults.isEmpty() ? View.NO_ID : R.id.btnSearchPlayAll);

                holder.itemView.setOnClickListener(v -> selectSource(position));
                holder.itemView.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    int currentPosition = holder.getAdapterPosition();
                    if (currentPosition == RecyclerView.NO_POSITION) return true;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentPosition == 0) {
                        return focusPrimaryRail();
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentPosition > 0) {
                        return focusSource(currentPosition - 1);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            && currentPosition == SOURCES.length - 1) {
                        return true;
                    }
                    return false;
                });
            }

            @Override
            public int getItemCount() {
                return SOURCES.length;
            }
        });

        rvHotSearch.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvHotSearch.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.left = dp(2);
                outRect.right = dp(2);
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
                tv.setPadding(dp(18), dp(6), dp(18), dp(6));
                tv.setTextSize(12);
                tv.setTextColor(getResources().getColorStateList(R.color.lx_brand));
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
            startActivity(new Intent(requireContext(), top.boluofan.musictv.PlayerActivity.class));
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
                requestFirstResultFocus();
                return true;
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
                requestLastResultFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            return false;
        };
        btnSearchPrevPage.setOnKeyListener(pagerNavigation);
        btnSearchNextPage.setOnKeyListener(pagerNavigation);
        updatePlaybackModeButton();
    }

    private void requestFirstResultFocus() {
        if (rvSearchResults == null || allResults.isEmpty()) return;
        rvSearchResults.setVisibility(View.VISIBLE);
        rvSearchResults.scrollToPosition(0);
        rvSearchResults.post(() -> {
            RecyclerView.ViewHolder holder = rvSearchResults.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void requestLastResultFocus() {
        if (rvSearchResults == null || songAdapter.getItemCount() == 0) return;
        int position = songAdapter.getItemCount() - 1;
        rvSearchResults.scrollToPosition(position);
        rvSearchResults.post(() -> {
            RecyclerView.ViewHolder holder = rvSearchResults.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.itemView.requestFocus();
        });
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
        boolean retainSourceFocus = isWithinView(focusedBeforeUpdate, rvSourceList);

        currentSourceIndex = position;
        currentSource = SOURCES[position];

        if (rvSourceList.getAdapter() != null) {
            rvSourceList.getAdapter().notifyDataSetChanged();
        }

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

    private boolean focusSource(int position) {
        if (rvSourceList == null || position < 0) return false;
        RecyclerView.ViewHolder holder = rvSourceList.findViewHolderForAdapterPosition(position);
        if (holder != null) return holder.itemView.requestFocus();
        rvSourceList.scrollToPosition(position);
        rvSourceList.post(() -> {
            RecyclerView.ViewHolder target = rvSourceList.findViewHolderForAdapterPosition(position);
            if (target != null) target.itemView.requestFocus();
        });
        return true;
    }

    private boolean focusPrimaryRail() {
        if (getActivity() == null) return false;
        View tabSearch = getActivity().findViewById(R.id.tabSearch);
        return tabSearch != null && tabSearch.requestFocus();
    }

    private void loadHotSearch() {
        final int generation = ++hotSearchGeneration;
        final String source = currentSource;
        if ("all".equals(source)) {
            hotSearchWords.clear();
            if (hotSearchAdapter != null) {
                hotSearchAdapter.notifyDataSetChanged();
            }
            requireActivity().runOnUiThread(() -> updateResults());
            return;
        }

        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());
        apiService.getHotSearch(source).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isPageUsable() || generation != hotSearchGeneration
                        || !source.equals(currentSource)) return;
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(bodyStr, com.google.gson.JsonObject.class);
                        com.google.gson.JsonArray arr = obj.getAsJsonArray("list");
                        hotSearchWords.clear();
                        if (arr != null) {
                            for (int i = 0; i < arr.size() && i < 10; i++) {
                                hotSearchWords.add(arr.get(i).getAsString());
                            }
                        }
                        if (hotSearchAdapter != null) {
                            hotSearchAdapter.notifyDataSetChanged();
                        }
                        requireActivity().runOnUiThread(() -> updateResults());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                if (!isPageUsable() || generation != hotSearchGeneration
                        || !source.equals(currentSource)) return;
                t.printStackTrace();
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
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusSource(0);
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
        String ipAddress = getIPAddress();
        if (ipAddress == null) {
            Toast.makeText(requireContext(), "无法获取局域网地址", Toast.LENGTH_SHORT).show();
            return;
        }

        String searchUrl = "http://" + ipAddress + ":" + SEARCH_SERVER_PORT;

        AlertDialog qrDialog = DialogHelper.showQrCodeDialog(
            requireActivity(),
            "扫码搜索",
            "在手机浏览器访问地址后搜索歌曲",
            searchUrl,
            searchUrl
        );

        qrDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "关闭", (d, which) -> {
            if (searchWebServer != null) {
                searchWebServer.stop();
                searchWebServer = null;
            }
        });

        searchWebServer = new SearchWebServer(requireContext(), SEARCH_SERVER_PORT, (keyword, source) -> {
            mainHandler.post(() -> {
                if (!isPageUsable()) return;
                qrDialog.dismiss();
                if (searchWebServer != null) {
                    searchWebServer.stop();
                    searchWebServer = null;
                }

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
                Toast.makeText(requireContext(), "收到推送的搜索: " + keyword, Toast.LENGTH_SHORT).show();
            });
        });

        try {
            searchWebServer.start();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "服务启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        qrDialog.setOnDismissListener(d -> {
            if (searchWebServer != null) {
                searchWebServer.stop();
                searchWebServer = null;
            }
        });

        qrDialog.show();
    }

    private void generateQrCode(String text, ImageView imageView) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            imageView.setImageBitmap(bmp);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private String getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) return sAddr;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private void setupMiniPlayer() {
    }

    private void updateMiniPlayerVisibility() {
    }

    private void updateMiniPlayerInfo() {
    }

    private void search(String keyword) {
        hideCustomKeyboard();
        final int generation = ++searchGeneration;
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
        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());

        for (int i = 0; i < ALL_SOURCES.length; i++) {
            final int index = i;
            final String source = ALL_SOURCES[index];

            // Retrofit.enqueue is already asynchronous. Wrapping it in a new
            // fixed thread pool on every search left five core threads alive
            // indefinitely and could eventually exhaust memory.
            apiService.searchMusic(keyword, source, 1, 30).enqueue(new Callback<List<MusicInfo>>() {
                @Override
                public void onResponse(Call<List<MusicInfo>> call, Response<List<MusicInfo>> response) {
                    if (!isPageUsable() || generation != searchGeneration) return;
                    synchronized (completed) {
                        if (response.isSuccessful() && response.body() != null) {
                            results[index] = response.body();
                        } else {
                            results[index] = new ArrayList<>();
                        }
                        completed[0]++;

                        if (completed[0] == ALL_SOURCES.length) {
                            mergeAllResults(results, generation);
                        }
                    }
                }

                @Override
                public void onFailure(Call<List<MusicInfo>> call, Throwable t) {
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
        }
    }

    private void mergeAllResults(List<MusicInfo>[] results, int generation) {
        if (!isPageUsable() || generation != searchGeneration) return;
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

    private void searchSingleSource(String keyword, String source, int generation) {
        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());
        apiService.searchMusic(keyword, source, currentPage, 30).enqueue(new Callback<List<MusicInfo>>() {
            @Override
            public void onResponse(Call<List<MusicInfo>> call, Response<List<MusicInfo>> response) {
                if (!isPageUsable() || generation != searchGeneration
                        || !source.equals(currentSource)) return;
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
                    Toast.makeText(requireContext(), "搜索失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<MusicInfo>> call, Throwable t) {
                if (!isPageUsable() || generation != searchGeneration
                        || !source.equals(currentSource)) return;
                showLoading(false);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateResults() {
        boolean hasHotSearch = !hotSearchWords.isEmpty();
        boolean hasSearchResults = !allResults.isEmpty();
        updateSearchFocusPath(hasSearchResults);

        tvHotSearchTitle.setVisibility(hasHotSearch ? View.VISIBLE : View.GONE);
        rvHotSearch.setVisibility(hasHotSearch ? View.VISIBLE : View.GONE);

        if (hasSearchResults) {
            tvResultCount.setVisibility(View.VISIBLE);
            tvResultCount.setText("共 " + allResults.size() + " 首");
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

    private int getResultPageCount() {
        return Math.max(1, (allResults.size() + SEARCH_SONGS_PER_PAGE - 1) / SEARCH_SONGS_PER_PAGE);
    }

    private void updateResultPageUi() {
        int pageCount = getResultPageCount();
        currentResultPage = Math.max(0, Math.min(currentResultPage, pageCount - 1));
        int start = currentResultPage * SEARCH_SONGS_PER_PAGE;
        int end = Math.min(start + SEARCH_SONGS_PER_PAGE, allResults.size());
        List<MusicInfo> pageSongs = new ArrayList<>(allResults.subList(start, end));
        songAdapter.setIndexOffset(start);
        songAdapter.setSongs(pageSongs);
        tvSearchPageNumber.setText(String.valueOf(currentResultPage + 1));
        boolean hasPrevious = currentResultPage > 0;
        boolean hasNext = currentResultPage + 1 < pageCount;
        btnSearchPrevPage.setEnabled(hasPrevious);
        btnSearchNextPage.setEnabled(hasNext);
        btnSearchPrevPage.setAlpha(hasPrevious ? 1f : 0.35f);
        btnSearchNextPage.setAlpha(hasNext ? 1f : 0.35f);
        updateVisiblePlayingIndex();
    }

    private void changeResultPage(int delta) {
        int nextPage = Math.max(0, Math.min(currentResultPage + delta, getResultPageCount() - 1));
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
        int queueIndex = queue.queueIndexForSourceIndex(index);
        if (queueIndex < 0) {
            Toast.makeText(requireContext(), "该歌曲缺少播放信息", Toast.LENGTH_SHORT).show();
            return false;
        }
        player.setMediaItems(queue.getMediaItems(), queueIndex, 0);
        player.setShuffleModeEnabled(shuffleEnabled);
        player.prepare();
        player.play();
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

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && requireActivity().getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(requireActivity().getCurrentFocus().getWindowToken(), 0);
        }
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
                player.setShuffleModeEnabled(shuffleEnabled);
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
        if (positionUpdater != null) {
            mainHandler.removeCallbacks(positionUpdater);
        }
    }

    private void setupPlayerListener() {
        if (player == null) return;

        player.addListener(new Player.Listener() {
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
        });
    }

    private static class SourceViewHolder extends RecyclerView.ViewHolder {
        ImageView ivRadio;
        TextView tvSourceName;

        SourceViewHolder(View itemView) {
            super(itemView);
            ivRadio = itemView.findViewById(R.id.ivRadio);
            tvSourceName = itemView.findViewById(R.id.tvSourceName);
        }
    }

    private static class HotSearchViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        HotSearchViewHolder(TextView tv) { super(tv); this.tv = tv; }
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
                && (isWithinView(currentFocus, rvSearchResults) || currentFocus == rvSearchResults
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

    @Override
    public void onDestroyView() {
        searchGeneration++;
        hotSearchGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        if (searchWebServer != null) {
            searchWebServer.stop();
            searchWebServer = null;
        }
        if (customKeyboardPopup != null) {
            customKeyboardPopup.dismiss();
            customKeyboardPopup = null;
        }
        rootView = null;
        super.onDestroyView();
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
}
