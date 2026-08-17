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
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.SearchWebServer;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.PlayerActivity;
import android.view.KeyEvent;
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
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private static final String TAG = "SearchActivity";
    
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
    private FloatingPlayerWindow floatingPlayerWindow;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        initViews();
        setupRecyclerViews();
        setupListeners();
        setupCustomKeyboard();
        updateResults();
    }

    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        btnSearch = findViewById(R.id.btnSearch);
        btnClear = findViewById(R.id.btnClear);
        btnScan = findViewById(R.id.btnScan);
        rvSourceList = findViewById(R.id.rvSourceList);
        rvHotSearch = findViewById(R.id.rvHotSearch);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        loadingProgress = findViewById(R.id.loadingProgress);
        tvNoResults = findViewById(R.id.tvNoResults);
        tvResultCount = findViewById(R.id.tvResultCount);
        tvHotSearchTitle = findViewById(R.id.tvHotSearchTitle);
        layoutSearchPager = findViewById(R.id.layoutSearchPager);
        btnSearchPrevPage = findViewById(R.id.btnSearchPrevPage);
        btnSearchNextPage = findViewById(R.id.btnSearchNextPage);
        tvSearchPageNumber = findViewById(R.id.tvSearchPageNumber);
        layoutSearchActions = findViewById(R.id.layoutSearchActions);
        btnSearchPlayAll = findViewById(R.id.btnSearchPlayAll);
        btnSearchPlayOrderToggle = findViewById(R.id.btnSearchPlayOrderToggle);
        
        songAdapter = new LxMusicAdapter();
        songAdapter.setNextFocusDownId(R.id.btnSearchNextPage);
        rvSearchResults.setAdapter(songAdapter);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            if (isKeyboardVisible) {
                hideCustomKeyboard();
            } else {
                finish();
            }
        });

        // 禁用系统键盘弹出，由自定义键盘处理输入
        etSearch.setShowSoftInputOnFocus(false);
    }

    private void setupRecyclerViews() {
        rvSourceList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
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
            }
            
            @Override
            public int getItemCount() {
                return SOURCES.length;
            }
        });
        
        rvHotSearch.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvHotSearch.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.left = 2;
                outRect.right = 2;
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
                tv.setPadding(18, 6, 18, 6);
                tv.setTextSize(12);
                tv.setTextColor(getResources().getColorStateList(R.color.white));
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
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > 0) {
                rvSourceList.getChildAt(0).requestFocus();
            }
        });
        
        songAdapter.setOnItemClickListener((song, position) -> {
            playSong(song);
        });
        
        songAdapter.setOnPlayClickListener((song, position) -> {
            playSong(song);
        });
        
        songAdapter.setOnFullscreenClickListener((song, position) -> {
            playSong(song);
            startActivity(new Intent(this, top.boluofan.musictv.PlayerActivity.class));
        });

        songAdapter.setOnFavClickListener((song, position) -> {
            collectSingleSong(song);
        });
        songAdapter.setOnFirstItemUpListener(() -> btnSearchPlayAll.isShown()
                && btnSearchPlayAll.requestFocus());

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
    
    private void selectSource(int position) {
        if (position < 0 || position >= SOURCES.length) return;
        
        currentSourceIndex = position;
        currentSource = SOURCES[position];
        
        if (rvSourceList.getAdapter() != null) {
            rvSourceList.getAdapter().notifyDataSetChanged();
        }
        
        rvSourceList.post(() -> {
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
    
    private void loadHotSearch() {
        String source = currentSource;
        if ("all".equals(source)) {
            hotSearchWords.clear();
            if (hotSearchAdapter != null) {
                hotSearchAdapter.notifyDataSetChanged();
            }
            runOnUiThread(() -> updateResults());
            return;
        }
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getHotSearch(source).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
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
                        runOnUiThread(() -> updateResults());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }
    
    private void setupListeners() {
        btnSearch.setOnClickListener(v -> {
            String keyword = etSearch.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
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
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (!isKeyboardVisible) {
                        showCustomKeyboard();
                        return true;
                    }
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
        customKeyboardPopup = new CustomKeyboardPopup(this);
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
            customKeyboardPopup = new CustomKeyboardPopup(this);
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
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && etSearch != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }

        customKeyboardPopup.setSource(currentSource);
        customKeyboardPopup.show(etSearch.getText().toString());
    }
    
    private void hideCustomKeyboard() {
        isKeyboardVisible = false;
        if (customKeyboardPopup != null) {
            customKeyboardPopup.dismiss();
        }
        // 清除 EditText 的焦点，防止 InputMethodManager 继续尝试管理它
        if (etSearch != null) {
            etSearch.clearFocus();
        }
        // 使用 post 确保焦点转移在 UI 线程正常执行
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.post(() -> btnBack.requestFocus());
        }
    }
    
    private void showScanSearchDialog() {
        String ipAddress = getIPAddress();
        if (ipAddress == null) {
            Toast.makeText(this, "无法获取局域网地址", Toast.LENGTH_SHORT).show();
            return;
        }

        String searchUrl = "http://" + ipAddress + ":" + SEARCH_SERVER_PORT;
        
        AlertDialog qrDialog = DialogHelper.showQrCodeDialog(
            this,
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
        
        searchWebServer = new SearchWebServer(this, SEARCH_SERVER_PORT, (keyword, source) -> {
            mainHandler.post(() -> {
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
                Toast.makeText(this, "收到推送的搜索: " + keyword, Toast.LENGTH_SHORT).show();
            });
        });
        
        try {
            searchWebServer.start();
        } catch (Exception e) {
            Toast.makeText(this, "服务启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        lastKeyword = keyword;
        currentPage = 1;
        currentResultPage = 0;
        playingGlobalIndex = -1;
        songAdapter.setPlayingIndex(-1);
        hasMore = true;
        allResults.clear();
        showLoading(true);
        
        if ("all".equals(currentSource)) {
            searchAllSources(keyword);
        } else {
            searchSingleSource(keyword, currentSource);
        }
    }
    
    private void searchAllSources(String keyword) {
        ExecutorService executor = Executors.newFixedThreadPool(ALL_SOURCES.length);
        List<MusicInfo>[] results = new List[ALL_SOURCES.length];
        int[] completed = new int[1];
        
        for (int i = 0; i < ALL_SOURCES.length; i++) {
            final int index = i;
            final String source = ALL_SOURCES[index];
            
            executor.submit(() -> {
                LxApiService apiService = LxRetrofitClient.getApiService(SearchActivity.this);
                apiService.searchMusic(keyword, source, 1, 30).enqueue(new Callback<List<MusicInfo>>() {
                    @Override
                    public void onResponse(Call<List<MusicInfo>> call, Response<List<MusicInfo>> response) {
                        synchronized (completed) {
                            if (response.isSuccessful() && response.body() != null) {
                                results[index] = response.body();
                            } else {
                                results[index] = new ArrayList<>();
                            }
                            completed[0]++;
                            
                            if (completed[0] == ALL_SOURCES.length) {
                                runOnUiThread(() -> {
                                    mergeAllResults(results);
                                });
                            }
                        }
                    }
                    
                    @Override
                    public void onFailure(Call<List<MusicInfo>> call, Throwable t) {
                        synchronized (completed) {
                            results[index] = new ArrayList<>();
                            completed[0]++;
                            
                            if (completed[0] == ALL_SOURCES.length) {
                                runOnUiThread(() -> {
                                    mergeAllResults(results);
                                });
                            }
                        }
                    }
                });
            });
        }
    }
    
    private void mergeAllResults(List<MusicInfo>[] results) {
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
    
    private void searchSingleSource(String keyword, String source) {
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.searchMusic(keyword, source, currentPage, 30).enqueue(new Callback<List<MusicInfo>>() {
            @Override
            public void onResponse(Call<List<MusicInfo>> call, Response<List<MusicInfo>> response) {
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
                    Toast.makeText(SearchActivity.this, "搜索失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<MusicInfo>> call, Throwable t) {
                showLoading(false);
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
        int pageStart = currentResultPage * SEARCH_SONGS_PER_PAGE;
        int pageEnd = pageStart + songAdapter.getItemCount();
        if (playingGlobalIndex >= pageStart && playingGlobalIndex < pageEnd) {
            songAdapter.setPlayingIndex(playingGlobalIndex - pageStart);
        } else {
            songAdapter.setPlayingIndex(-1);
        }
    }

    private void playSong(MusicInfo song) {
        if (player == null) {
            Toast.makeText(this, "播放器未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int index = allResults.indexOf(song);
        if (index >= 0) playResultAtIndex(index);
        else {
            player.setMediaItem(createMediaItem(song));
            player.setShuffleModeEnabled(shuffleEnabled);
            player.prepare();
            player.play();
        }
        
        Toast.makeText(this, "正在播放: " + song.getName(), Toast.LENGTH_SHORT).show();
    }

    private void playAllResults() {
        if (allResults.isEmpty() || player == null) return;
        playResultAtIndex(0);
    }

    private void playResultAtIndex(int index) {
        if (player == null || index < 0 || index >= allResults.size()) return;
        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo result : allResults) {
            mediaItems.add(createMediaItem(result));
        }
        player.setMediaItems(mediaItems, index, 0);
        player.setShuffleModeEnabled(shuffleEnabled);
        player.prepare();
        player.play();
        playingGlobalIndex = index;
        int pageStart = currentResultPage * SEARCH_SONGS_PER_PAGE;
        int pageEnd = pageStart + songAdapter.getItemCount();
        songAdapter.setPlayingIndex(index >= pageStart && index < pageEnd ? index - pageStart : -1);
    }

    private void updatePlaybackModeButton() {
        if (btnSearchPlayOrderToggle == null) return;
        btnSearchPlayOrderToggle.setImageResource(shuffleEnabled
                ? R.drawable.ic_shuffle : R.drawable.ic_repeat);
        btnSearchPlayOrderToggle.setContentDescription(shuffleEnabled
                ? "切换为顺序播放" : "切换为随机播放");
        btnSearchPlayOrderToggle.setSelected(shuffleEnabled);
    }

    private MediaItem createMediaItem(MusicInfo song) {
        Bundle extras = song.toPlaybackExtras();
        
        Uri artworkUri = null;
        String coverUrl = song.getPicUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            artworkUri = Uri.parse(coverUrl);
        }
        
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

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                player.setShuffleModeEnabled(shuffleEnabled);
                setupPlayerListener();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
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
        
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && !isKeyboardVisible) {
            View currentFocus = getCurrentFocus();
            if (currentFocus == etSearch || currentFocus == btnSearch || currentFocus == btnClear) {
                showCustomKeyboard();
                return true;
            }
            if (currentFocus != null) {
                showCustomKeyboard();
                return true;
            }
        }

        View currentFocus = getCurrentFocus();
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
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (floatingPlayerWindow != null
                    && floatingPlayerWindow.handleDirectionalKey(keyCode, currentFocus)) {
                return true;
            }
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
        
        return super.onKeyDown(keyCode, event);
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
    protected void onDestroy() {
        super.onDestroy();
        if (floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
        if (searchWebServer != null) {
            searchWebServer.stop();
            searchWebServer = null;
        }
    }

    private void collectSingleSong(MusicInfo song) {
        if (!LxRetrofitClient.isLoggedIn(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, top.boluofan.musictv.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(this));
            startActivity(intent);
            return;
        }

        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getApiService(this);

        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(SearchActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null || userPlaylists.isEmpty()) {
                    Toast.makeText(SearchActivity.this, "暂无歌单，请先在歌单库创建歌单", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[userPlaylists.size()];
                for (int i = 0; i < userPlaylists.size(); i++) {
                    playlistNames[i] = userPlaylists.get(i).getName();
                }

                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(SearchActivity.this, "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
                    fetchAndAddSongToPlaylist(userPlaylists.get(which).getName(), finalSong);
                });
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addSongToPlaylist(top.boluofan.musictv.api.model.ListData listData, top.boluofan.musictv.api.model.Playlist playlist, MusicInfo song) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getApiService(this);

        List<MusicInfo> songList = playlist.getSongs();
        if (songList == null) {
            songList = new ArrayList<>();
        }

        for (MusicInfo m : songList) {
            if (m.getName().equals(song.getName()) && m.getSource().equals(song.getSource())) {
                Toast.makeText(this, "歌曲已存在于此歌单", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        songList.add(0, song);
        playlist.setSongs(songList);
        playlist.setSongCount(songList.size());

        apiService.updateUserList(username, password, token, listData).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(SearchActivity.this, "已添加到「" + playlist.getName() + "」", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SearchActivity.this, "添加失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAndAddSongToPlaylist(String playlistName, MusicInfo song) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getApiService(this);

        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(SearchActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null) {
                    Toast.makeText(SearchActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(SearchActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                addSongToPlaylist(listData, targetPlaylist, song);
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
