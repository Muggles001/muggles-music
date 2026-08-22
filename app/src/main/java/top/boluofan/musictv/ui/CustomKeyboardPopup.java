package top.boluofan.musictv.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.backend.MusicApiProvider;

public class CustomKeyboardPopup {

    private static final int MODE_PINYIN = 0;
    private static final int MODE_ABC = 1;
    private static final int MODE_123 = 2;

    private PopupWindow popupWindow;
    private Activity activity;
    private Context context;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private View popupView;
    private StringBuilder inputText = new StringBuilder();
    private LinearLayout layoutTipSuggestions;
    private RecyclerView rvTipSuggestions;
    private int keyboardMode = MODE_PINYIN;

    private List<String> tipWords = new ArrayList<>();
    private Runnable tipSearchRunnable;
    private String currentSource = "kw";
    private OnSearchListener searchListener;
    private boolean isPreparingToShow = false;
    private Runnable pendingShowRunnable;

    public interface OnSearchListener {
        void onSearch(String keyword);
        void onInputChanged(String text);
    }

    public CustomKeyboardPopup(Activity activity) {
        this.activity = activity;
        this.context = activity;
    }

    public void setOnSearchListener(OnSearchListener listener) {
        this.searchListener = listener;
    }

    public void setSource(String source) {
        this.currentSource = "all".equals(source) ? "kw" : source;
    }

    public void show(String initialText) {
        if (popupWindow != null && popupWindow.isShowing()) {
            return;
        }
        
        if (isPreparingToShow) {
            return;
        }
        isPreparingToShow = true;

        inputText.setLength(0);
        if (initialText != null) {
            inputText.append(initialText);
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        popupView = inflater.inflate(R.layout.view_keyboard_popup, null);

        layoutTipSuggestions = popupView.findViewById(R.id.layoutTipSuggestions);
        rvTipSuggestions = popupView.findViewById(R.id.rvTipSuggestions);
        rvTipSuggestions.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));

        setupModeButtons();
        setupTipCloseButton();
        buildKeyboardRows();

        int width = WindowManager.LayoutParams.MATCH_PARENT;
        int height = WindowManager.LayoutParams.WRAP_CONTENT;

        popupWindow = new PopupWindow(popupView, width, height, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setClippingEnabled(false);

        pendingShowRunnable = () -> {
            isPreparingToShow = false;
            if (activity == null || activity.isFinishing()) {
                return;
            }
            View decorView = activity.getWindow().getDecorView();
            popupWindow.showAtLocation(decorView, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
            requestKeyboardFocus();
        };
        mainHandler.postDelayed(pendingShowRunnable, 100);
    }

    public void dismiss() {
        if (pendingShowRunnable != null) {
            mainHandler.removeCallbacks(pendingShowRunnable);
            pendingShowRunnable = null;
        }
        isPreparingToShow = false;
        if (popupWindow != null) {
            popupWindow.dismiss();
            popupWindow = null;
        }
        hideTipSuggestions();
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    private void setupModeButtons() {
        Button btnModePinYin = popupView.findViewById(R.id.btnModePinYin);
        Button btnModeAbc = popupView.findViewById(R.id.btnModeAbc);
        Button btnMode123 = popupView.findViewById(R.id.btnMode123);

        configureModeButton(btnModePinYin);
        configureModeButton(btnModeAbc);
        configureModeButton(btnMode123);

        btnModePinYin.setNextFocusRightId(R.id.btnModeAbc);
        btnModeAbc.setNextFocusLeftId(R.id.btnModePinYin);
        btnModeAbc.setNextFocusRightId(R.id.btnMode123);
        btnMode123.setNextFocusLeftId(R.id.btnModeAbc);

        btnModePinYin.setOnClickListener(v -> {
            keyboardMode = MODE_PINYIN;
            updateKeyboardMode();
        });

        btnModeAbc.setOnClickListener(v -> {
            keyboardMode = MODE_ABC;
            updateKeyboardMode();
        });

        btnMode123.setOnClickListener(v -> {
            keyboardMode = MODE_123;
            updateKeyboardMode();
        });

        updateKeyboardModeButtons(btnModePinYin, btnModeAbc, btnMode123);
    }

    private void configureModeButton(Button button) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setTextColor(modeTextColors());
        button.setSelected(false);
    }

    private ColorStateList modeTextColors() {
        int primary = context.getResources().getColor(R.color.lx_text_primary);
        return new ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_focused },
                        new int[] { android.R.attr.state_selected },
                        new int[] {}
                },
                new int[] { Color.WHITE, Color.WHITE, primary });
    }

    private ColorStateList keyTextColors() {
        int primary = context.getResources().getColor(R.color.lx_text_primary);
        return new ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_focused },
                        new int[] { android.R.attr.state_pressed },
                        new int[] {}
                },
                new int[] { Color.WHITE, Color.WHITE, primary });
    }

    private void setupTipCloseButton() {
        View btnCloseTips = popupView.findViewById(R.id.btnCloseTips);
        btnCloseTips.setOnClickListener(v -> {
            hideTipSuggestions();
            inputText.setLength(0);
            if (searchListener != null) {
                searchListener.onInputChanged("");
            }
        });
    }

    private void buildKeyboardRows() {
        LinearLayout layoutRow1 = popupView.findViewById(R.id.layoutRow1);
        LinearLayout layoutRow2 = popupView.findViewById(R.id.layoutRow2);
        LinearLayout layoutRow3 = popupView.findViewById(R.id.layoutRow3);
        LinearLayout layoutRow4 = popupView.findViewById(R.id.layoutRow4);

        layoutRow1.removeAllViews();
        layoutRow2.removeAllViews();
        layoutRow3.removeAllViews();
        layoutRow4.removeAllViews();

        if (keyboardMode == MODE_PINYIN) {
            String[] row1 = {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"};
            String[] row2 = {"A", "S", "D", "F", "G", "H", "J", "K", "L"};
            String[] row3 = {"Z", "X", "C", "V", "B", "N", "M"};

            for (String key : row1) addKeyView(layoutRow1, key);
            for (String key : row2) addKeyView(layoutRow2, key);
            addKeyView(layoutRow3, "←");
            for (String key : row3) addKeyView(layoutRow3, key);
            addKeyView(layoutRow3, "×");

            addActionKey(layoutRow4, "清空", 2);
            addActionKey(layoutRow4, "空格", 4);
            addActionKey(layoutRow4, "搜索", 1);
        } else if (keyboardMode == MODE_ABC) {
            String[] row1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
            String[] row2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
            String[] row3 = {"z", "x", "c", "v", "b", "n", "m"};

            for (String key : row1) addKeyView(layoutRow1, key);
            for (String key : row2) addKeyView(layoutRow2, key);
            addKeyView(layoutRow3, "←");
            for (String key : row3) addKeyView(layoutRow3, key);
            addKeyView(layoutRow3, "×");

            addActionKey(layoutRow4, "清空", 2);
            addActionKey(layoutRow4, "空格", 4);
            addActionKey(layoutRow4, "搜索", 1);
        } else if (keyboardMode == MODE_123) {
            String[] row1 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
            String[] row2 = {"-", "/", ":", ";", "(", ")", "$", "&", "@", "\""};
            String[] row3 = {".", ",", "?", "!", "'", "+", "=", "#"};

            for (String key : row1) addKeyView(layoutRow1, key);
            for (String key : row2) addKeyView(layoutRow2, key);
            addKeyView(layoutRow3, "←");
            for (String key : row3) addKeyView(layoutRow3, key);
            addKeyView(layoutRow3, "×");

            addActionKey(layoutRow4, "清空", 2);
            addActionKey(layoutRow4, "空格", 4);
            addActionKey(layoutRow4, "搜索", 1);
        }
    }

    private void addKeyView(LinearLayout parent, String key) {
        TextView tv = new TextView(context);
        tv.setText(key);
        tv.setTextSize(18);
        tv.setTextColor(keyTextColors());
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.bg_keyboard_key);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 80, 1.0f);
        params.setMarginStart(4);
        params.setMarginEnd(4);
        tv.setLayoutParams(params);
        tv.setFocusable(true);
        tv.setClickable(true);

        tv.setOnClickListener(v -> onKeyPressed(key));
        tv.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                tv.setBackgroundResource(R.drawable.bg_keyboard_key_action);
            } else {
                tv.setBackgroundResource(R.drawable.bg_keyboard_key);
            }
        });

        parent.addView(tv);
    }

    private void addActionKey(LinearLayout parent, String text, int weight) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTextColor(Color.WHITE);
        btn.setAllCaps(false);
        btn.setBackgroundResource(R.drawable.bg_keyboard_key_action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 80, weight);
        params.setMarginStart(4);
        params.setMarginEnd(4);
        btn.setLayoutParams(params);
        btn.setFocusable(true);

        btn.setOnClickListener(v -> {
            if ("清空".equals(text)) {
                inputText.setLength(0);
                hideTipSuggestions();
                if (searchListener != null) {
                    searchListener.onInputChanged("");
                }
            } else if ("空格".equals(text)) {
                onKeyPressed(" ");
            } else if ("搜索".equals(text)) {
                String keyword = inputText.toString().trim();
                if (!keyword.isEmpty()) {
                    dismiss();
                    if (searchListener != null) {
                        searchListener.onSearch(keyword);
                    }
                }
            }
        });

        parent.addView(btn);
    }

    private void onKeyPressed(String key) {
        if ("←".equals(key)) {
            if (inputText.length() > 0) {
                inputText.setLength(inputText.length() - 1);
            }
        } else if ("×".equals(key)) {
            inputText.setLength(0);
        } else {
            inputText.append(key);
        }

        if (searchListener != null) {
            searchListener.onInputChanged(inputText.toString());
        }
        triggerTipSearch();
    }

    private void updateKeyboardMode() {
        buildKeyboardRows();
        Button btnModePinYin = popupView.findViewById(R.id.btnModePinYin);
        Button btnModeAbc = popupView.findViewById(R.id.btnModeAbc);
        Button btnMode123 = popupView.findViewById(R.id.btnMode123);

        updateKeyboardModeButtons(btnModePinYin, btnModeAbc, btnMode123);
    }

    private void updateKeyboardModeButtons(Button btnModePinYin, Button btnModeAbc, Button btnMode123) {
        if (btnModePinYin == null || btnModeAbc == null || btnMode123 == null) return;
        btnModePinYin.setTextColor(modeTextColors());
        btnModeAbc.setTextColor(modeTextColors());
        btnMode123.setTextColor(modeTextColors());
        btnModePinYin.setSelected(keyboardMode == MODE_PINYIN);
        btnModeAbc.setSelected(keyboardMode == MODE_ABC);
        btnMode123.setSelected(keyboardMode == MODE_123);
        btnModePinYin.setBackgroundResource(keyboardMode == MODE_PINYIN ? R.drawable.bg_keyboard_key_action : R.drawable.bg_keyboard_key);
        btnModeAbc.setBackgroundResource(keyboardMode == MODE_ABC ? R.drawable.bg_keyboard_key_action : R.drawable.bg_keyboard_key);
        btnMode123.setBackgroundResource(keyboardMode == MODE_123 ? R.drawable.bg_keyboard_key_action : R.drawable.bg_keyboard_key);
    }

    private void requestKeyboardFocus() {
        LinearLayout layoutRow1 = popupView.findViewById(R.id.layoutRow1);
        if (layoutRow1 != null && layoutRow1.getChildCount() > 0) {
            layoutRow1.getChildAt(0).requestFocus();
        }
    }

    private void triggerTipSearch() {
        if (tipSearchRunnable != null) {
            mainHandler.removeCallbacks(tipSearchRunnable);
        }
        String text = inputText.toString();
        tipSearchRunnable = () -> {
            if (text.length() >= 1) {
                loadTipSearch(text);
            } else {
                hideTipSuggestions();
            }
        };
        mainHandler.postDelayed(tipSearchRunnable, 300);
    }

    private void loadTipSearch(String keyword) {
        LxApiService apiService = MusicApiProvider.get(context);
        apiService.tipSearch(keyword, currentSource).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.JsonArray arr = new com.google.gson.Gson().fromJson(bodyStr, com.google.gson.JsonArray.class);
                        tipWords.clear();
                        if (arr != null) {
                            for (int i = 0; i < arr.size() && i < 8; i++) {
                                tipWords.add(arr.get(i).getAsString());
                            }
                        }
                        if (!tipWords.isEmpty()) {
                            mainHandler.post(() -> showTipSuggestions());
                        } else {
                            mainHandler.post(() -> hideTipSuggestions());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        mainHandler.post(() -> hideTipSuggestions());
                    }
                } else {
                    mainHandler.post(() -> hideTipSuggestions());
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                mainHandler.post(() -> hideTipSuggestions());
            }
        });
    }

    private void showTipSuggestions() {
        if (tipWords.isEmpty() || popupView == null) return;
        layoutTipSuggestions.setVisibility(View.VISIBLE);

        rvTipSuggestions.setAdapter(new RecyclerView.Adapter<TipViewHolder>() {
            @NonNull
            @Override
            public TipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                tv.setPadding(24, 0, 24, 0);
                tv.setTextSize(13);
                tv.setTextColor(keyTextColors());
                tv.setBackgroundResource(R.drawable.bg_keyboard_key);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                tv.setFocusable(true);
                tv.setClickable(true);
                return new TipViewHolder(tv);
            }

            @Override
            public void onBindViewHolder(@NonNull TipViewHolder holder, int position) {
                String word = tipWords.get(position);
                holder.tv.setText(word);
                holder.tv.setOnClickListener(v -> {
                    inputText.setLength(0);
                    inputText.append(word);
                    if (searchListener != null) {
                        searchListener.onInputChanged(word);
                    }
                    hideTipSuggestions();
                    dismiss();
                    if (searchListener != null) {
                        searchListener.onSearch(word);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return tipWords.size();
            }
        });
    }

    private void hideTipSuggestions() {
        if (layoutTipSuggestions != null) {
            layoutTipSuggestions.setVisibility(View.GONE);
        }
        tipWords.clear();
    }

    private static class TipViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        TipViewHolder(TextView tv) { super(tv); this.tv = tv; }
    }
}
