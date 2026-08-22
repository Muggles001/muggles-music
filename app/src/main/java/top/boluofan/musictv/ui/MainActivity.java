package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.FocusFinder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import android.content.ComponentName;
import android.util.Log;
import androidx.recyclerview.widget.RecyclerView;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.util.FocusAnimationHelper;
import top.boluofan.musictv.ui.SongSquareFragment;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String STATE_SELECTED_PAGE = "selected_primary_page";
    public static final int PAGE_SEARCH = 0;
    public static final int PAGE_SONG_SQUARE = 1;
    public static final int PAGE_RANKING = 2;
    public static final int PAGE_LIBRARY = 3;
    public static final int PAGE_SETTINGS = 4;

    public interface PrimaryPageKeyHandler {
        boolean onKeyDown(int keyCode, KeyEvent event);
    }
    private FloatingPlayerWindow floatingPlayerWindow;
    private LinearLayout tabLibrary;
    private LinearLayout tabSearch;
    private LinearLayout tabSongSquare;
    private LinearLayout tabRanking;
    private LinearLayout tabSettings;
    
    private ImageButton btnLibrary;
    private ImageButton btnSearch;
    private ImageButton btnSongSquare;
    private ImageButton btnRanking;
    private ImageButton btnSettings;
    
    private long lastBackPressTime = 0;
    
    private TextView tvLibrary;
    private TextView tvSearch;
    private TextView tvSongSquare;
    private TextView tvRanking;
    private TextView tvSettings;

    private int currentSelectedTab = -1;
    private boolean pageTransitionInProgress = false;
    private int pageTransitionGeneration = 0;
    private View fragmentContainer;
    private View lastContentFocus;
    private int lastContentFocusPage = -1;
    private ViewTreeObserver.OnGlobalFocusChangeListener contentFocusTracker;
    
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        BackendMode backendMode = BackendPreferences.getMode(this);
        if (backendMode == BackendMode.NONE) {
            startActivity(new Intent(this, top.boluofan.musictv.ConfigActivity.class));
            finish();
            return;
        }
        
        setContentView(R.layout.activity_main);
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        initViews();
        setupListeners();
        
        int initialPage = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_SELECTED_PAGE, PAGE_SONG_SQUARE)
                : PAGE_SONG_SQUARE;
        if (initialPage == PAGE_LIBRARY && !isLibraryAvailable()) {
            initialPage = PAGE_SONG_SQUARE;
        }
        selectPrimaryPage(initialPage, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        final ListenableFuture<MediaController> pendingController =
                new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture = pendingController;
        pendingController.addListener(() -> {
            try {
                MediaController resolvedController = pendingController.get();
                if (isFinishing() || isDestroyed() || controllerFuture != pendingController) {
                    MediaController.releaseFuture(pendingController);
                    return;
                }
                player = resolvedController;
            } catch (Exception e) {
                Log.e(TAG, "Failed to connect media controller", e);
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        player = null;
    }

    private void initViews() {
        fragmentContainer = findViewById(R.id.fragmentContainer);
        contentFocusTracker = (oldFocus, newFocus) -> {
            if (newFocus != null && isWithinView(newFocus, fragmentContainer)) {
                lastContentFocus = newFocus;
                lastContentFocusPage = currentSelectedTab;
            }
        };
        fragmentContainer.getViewTreeObserver()
                .addOnGlobalFocusChangeListener(contentFocusTracker);
        tabLibrary = findViewById(R.id.tabLibrary);
        tabSearch = findViewById(R.id.tabSearch);
        tabSongSquare = findViewById(R.id.tabSongSquare);
        tabRanking = findViewById(R.id.tabRanking);
        tabSettings = findViewById(R.id.tabSettings);
        
        btnLibrary = findViewById(R.id.btnLibrary);
        btnSearch = findViewById(R.id.btnSearch);
        btnSongSquare = findViewById(R.id.btnSongSquare);
        btnRanking = findViewById(R.id.btnRanking);
        btnSettings = findViewById(R.id.btnSettings);
        
        tvLibrary = findViewById(R.id.tvLibrary);
        tvSearch = findViewById(R.id.tvSearch);
        tvSongSquare = findViewById(R.id.tvSongSquare);
        tvRanking = findViewById(R.id.tvRanking);
        tvSettings = findViewById(R.id.tvSettings);
        
        if (!isLibraryAvailable()) {
            tabLibrary.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        bindPrimaryTab(tabSearch, PAGE_SEARCH);
        bindPrimaryTab(tabSongSquare, PAGE_SONG_SQUARE);
        bindPrimaryTab(tabRanking, PAGE_RANKING);
        bindPrimaryTab(tabLibrary, PAGE_LIBRARY);
        bindPrimaryTab(tabSettings, PAGE_SETTINGS);
    }

    private boolean isLibraryAvailable() {
        return BackendPreferences.usesLocalLibrary(this) || LxRetrofitClient.isLoggedIn(this);
    }

    private void bindPrimaryTab(View tab, int page) {
        tab.setOnClickListener(v -> selectPrimaryPage(page, true));
        tab.setOnFocusChangeListener((v, hasFocus) -> {
            TextView label = getTabLabel(page);
            if (label != null) label.setActivated(hasFocus);
            if (hasFocus) {
                FocusAnimationHelper.animateFocusIn(v);
                selectPrimaryPage(page, true);
            } else {
                FocusAnimationHelper.animateFocusOut(v);
            }
        });
    }

    private void clearSelection() {
        btnLibrary.setAlpha(0.5f);
        btnSearch.setAlpha(0.5f);
        btnSongSquare.setAlpha(0.5f);
        btnRanking.setAlpha(0.5f);
        btnSettings.setAlpha(0.5f);
        btnLibrary.setSelected(false);
        btnSearch.setSelected(false);
        btnSongSquare.setSelected(false);
        btnRanking.setSelected(false);
        btnSettings.setSelected(false);
        
        tvLibrary.setTextColor(ContextCompat.getColorStateList(this, R.color.selector_nav_tint));
        tvSearch.setTextColor(ContextCompat.getColorStateList(this, R.color.selector_nav_tint));
        tvSongSquare.setTextColor(ContextCompat.getColorStateList(this, R.color.selector_nav_tint));
        tvRanking.setTextColor(ContextCompat.getColorStateList(this, R.color.selector_nav_tint));
        tvSettings.setTextColor(ContextCompat.getColorStateList(this, R.color.selector_nav_tint));
        tvLibrary.setSelected(false);
        tvSearch.setSelected(false);
        tvSongSquare.setSelected(false);
        tvRanking.setSelected(false);
        tvSettings.setSelected(false);

        tabLibrary.setSelected(false);
        tabSearch.setSelected(false);
        tabSongSquare.setSelected(false);
        tabRanking.setSelected(false);
        tabSettings.setSelected(false);
    }

    private void updateTabSelection(int tabIndex) {
        clearSelection();
        switch (tabIndex) {
            case PAGE_SEARCH:
                btnSearch.setAlpha(1.0f);
                btnSearch.setSelected(true);
                tabSearch.setSelected(true);
                tvSearch.setSelected(true);
                break;
            case PAGE_SONG_SQUARE:
                btnSongSquare.setAlpha(1.0f);
                btnSongSquare.setSelected(true);
                tabSongSquare.setSelected(true);
                tvSongSquare.setSelected(true);
                break;
            case PAGE_RANKING:
                btnRanking.setAlpha(1.0f);
                btnRanking.setSelected(true);
                tabRanking.setSelected(true);
                tvRanking.setSelected(true);
                break;
            case PAGE_LIBRARY:
                btnLibrary.setAlpha(1.0f);
                btnLibrary.setSelected(true);
                tabLibrary.setSelected(true);
                tvLibrary.setSelected(true);
                break;
            case PAGE_SETTINGS:
                btnSettings.setAlpha(1.0f);
                btnSettings.setSelected(true);
                tabSettings.setSelected(true);
                tvSettings.setSelected(true);
                break;
        }
        currentSelectedTab = tabIndex;
    }

    private TextView getTabLabel(int page) {
        switch (page) {
            case PAGE_SEARCH: return tvSearch;
            case PAGE_SONG_SQUARE: return tvSongSquare;
            case PAGE_RANKING: return tvRanking;
            case PAGE_LIBRARY: return tvLibrary;
            case PAGE_SETTINGS: return tvSettings;
            default: return null;
        }
    }

    public void selectPrimaryPage(int page, boolean animate) {
        if (page == PAGE_LIBRARY && !isLibraryAvailable()) return;
        if (page < PAGE_SEARCH || page > PAGE_SETTINGS) return;

        Fragment current = currentSelectedTab >= 0
                ? getSupportFragmentManager().findFragmentByTag("primary_page_" + currentSelectedTab)
                : null;
        Fragment next = getSupportFragmentManager().findFragmentByTag("primary_page_" + page);
        if (currentSelectedTab == page && next != null && !next.isHidden()) return;

        int previousPage = currentSelectedTab;
        if (next == null) next = createPrimaryFragment(page);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true);
        if (animate && previousPage >= 0) {
            if (navigationOrder(page) > navigationOrder(previousPage)) {
                transaction.setCustomAnimations(
                        R.anim.page_enter_from_right,
                        R.anim.page_exit_to_left
                );
            } else {
                transaction.setCustomAnimations(
                        R.anim.page_enter_from_left,
                        R.anim.page_exit_to_right
                );
            }
        }

        final int transitionGeneration = ++pageTransitionGeneration;
        pageTransitionInProgress = true;
        updateTabSelection(page);
        if (current != null && current != next) {
            transaction.hide(current);
            transaction.setMaxLifecycle(current, Lifecycle.State.STARTED);
        }
        if (next.isAdded()) {
            transaction.show(next);
        } else {
            transaction.add(R.id.fragmentContainer, next, "primary_page_" + page);
        }
        transaction.setMaxLifecycle(next, Lifecycle.State.RESUMED);
        transaction.commit();
        fragmentContainer.postDelayed(() -> {
            if (transitionGeneration == pageTransitionGeneration) {
                pageTransitionInProgress = false;
            }
        }, 300L);
    }

    /** The visual rail is ordered Song Square, Search, Ranking, Library, Settings. */
    private int navigationOrder(int page) {
        switch (page) {
            case PAGE_SONG_SQUARE: return 0;
            case PAGE_SEARCH: return 1;
            case PAGE_RANKING: return 2;
            case PAGE_LIBRARY: return 3;
            case PAGE_SETTINGS: return 4;
            default: return page;
        }
    }

    private Fragment createPrimaryFragment(int page) {
        switch (page) {
            case PAGE_SEARCH:
                return new SearchFragment();
            case PAGE_RANKING:
                return new RankingFragment();
            case PAGE_LIBRARY:
                return new LibraryFragment();
            case PAGE_SETTINGS:
                return new SettingsFragment();
            case PAGE_SONG_SQUARE:
            default:
                return new SongSquareFragment();
        }
    }

    /**
     * Primary-page RecyclerViews deliberately keep their containers out of
     * the focus chain. Sending the rail directly to a visible child avoids
     * the extra DPAD_RIGHT press that a container focus target requires.
     */
    private boolean moveFocusFromRailToPage(Fragment pageFragment) {
        if (pageFragment == null || pageFragment.getView() == null) return false;

        View root = pageFragment.getView();
        View target;
        switch (currentSelectedTab) {
            case PAGE_SONG_SQUARE:
                target = root.findViewById(R.id.rvSourceList);
                break;
            case PAGE_SEARCH:
                target = root.findViewById(R.id.etSearch);
                break;
            case PAGE_RANKING:
                target = root.findViewById(R.id.rvBoards);
                return requestPageTargetFocus(target)
                        || requestPageTargetFocus(root.findViewById(R.id.rvSourceList));
            case PAGE_LIBRARY:
                target = root.findViewById(R.id.tabAllSongs);
                break;
            case PAGE_SETTINGS:
                target = root.findViewById(R.id.layoutServerConfig);
                break;
            default:
                return false;
        }
        return requestPageTargetFocus(target);
    }

    private boolean requestPageTargetFocus(View target) {
        if (target == null || !target.isShown() || !target.isEnabled()) return false;
        if (target instanceof RecyclerView) {
            RecyclerView list = (RecyclerView) target;
            if (list.getChildCount() > 0) {
                return list.getChildAt(0).requestFocus();
            }
            // A source or ranking strip can finish binding one frame after the
            // page transition. Consume this key and deliver the focus as soon
            // as its first actual item exists.
            list.post(() -> {
                if (list.isShown() && list.getChildCount() > 0) {
                    list.getChildAt(0).requestFocus();
                }
            });
            return true;
        }
        if (target.isFocusable()) return target.requestFocus();
        return requestFirstFocusableDescendant(target);
    }

    private boolean requestFirstFocusableDescendant(View view) {
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!child.isShown() || !child.isEnabled()) continue;
            if (child instanceof RecyclerView) {
                if (requestPageTargetFocus(child)) return true;
            } else if (child.isFocusable() && child.requestFocus()) {
                return true;
            } else if (requestFirstFocusableDescendant(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRailItem(View view) {
        return view == tabSongSquare || view == tabSearch || view == tabRanking
                || view == tabLibrary || view == tabSettings;
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

    /**
     * Android's geometric focus search can choose the first item in the left
     * rail when content is loading or a list is being rebound. A vertical key
     * must never change the primary page; the rail is entered explicitly from
     * the left edge with DPAD_LEFT.
     */
    private boolean wouldLeakVerticalFocusToRail(int keyCode, View currentFocus) {
        if ((keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN)
                || !isWithinView(currentFocus, fragmentContainer)) {
            return false;
        }
        View contentRoot = findViewById(android.R.id.content);
        if (!(contentRoot instanceof ViewGroup)) return false;
        int direction = keyCode == KeyEvent.KEYCODE_DPAD_UP
                ? View.FOCUS_UP : View.FOCUS_DOWN;
        View next = FocusFinder.getInstance().findNextFocus(
                (ViewGroup) contentRoot,
                currentFocus,
                direction
        );
        // No valid content target means this is a vertical boundary. Keep the
        // current control focused instead of allowing framework wraparound.
        return next == null || isRailItem(next);
    }

    /**
     * RecyclerView can briefly clear window focus while rebinding a row. If a
     * repeated vertical key reaches the Activity during that frame, Android's
     * default search starts at the decor root and commonly selects the first
     * primary-rail item. Restore the last focus from this page when it is still
     * valid; otherwise consume the key until the content finishes laying out.
     */
    private boolean handleMissingContentVerticalFocus(int keyCode, View currentFocus) {
        if ((keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN)
                || currentFocus != null) {
            return false;
        }

        final View target = lastContentFocus;
        final int targetPage = lastContentFocusPage;
        if (isRestorableContentFocus(target, targetPage) && !target.requestFocus()) {
            fragmentContainer.post(() -> {
                if (getCurrentFocus() == null
                        && isRestorableContentFocus(target, targetPage)) {
                    target.requestFocus();
                }
            });
        }
        return true;
    }

    private boolean isRestorableContentFocus(View target, int targetPage) {
        return target != null
                && targetPage == currentSelectedTab
                && target.isAttachedToWindow()
                && target.isShown()
                && target.isEnabled()
                && target.isFocusable()
                && isWithinView(target, fragmentContainer);
    }

    private void moveFocusFromRailWhenReady() {
        fragmentContainer.postDelayed(() -> {
            if (!isRailItem(getCurrentFocus())) return;
            Fragment readyFragment = getSupportFragmentManager()
                    .findFragmentByTag("primary_page_" + currentSelectedTab);
            moveFocusFromRailToPage(readyFragment);
        }, 80L);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_PAGE, currentSelectedTab);
        super.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onDestroy() {
        if (fragmentContainer != null && contentFocusTracker != null) {
            ViewTreeObserver observer = fragmentContainer.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalFocusChangeListener(contentFocusTracker);
            }
            contentFocusTracker = null;
        }
        super.onDestroy();
        if (floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Fragment currentFragment = getSupportFragmentManager()
                .findFragmentByTag("primary_page_" + currentSelectedTab);
        View currentFocus = getCurrentFocus();
        boolean directionalKey = keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isRailItem(currentFocus)) {
            if (currentFragment != null && !pageTransitionInProgress
                    && moveFocusFromRailToPage(currentFragment)) {
                return true;
            }
            // The rail focus itself triggered a page transaction. Preserve a
            // single right press while the fragment finishes attaching.
            moveFocusFromRailWhenReady();
            return true;
        }
        if (directionalKey && (currentFragment == null || pageTransitionInProgress)) {
            return true;
        }
        if (handleMissingContentVerticalFocus(keyCode, currentFocus)) {
            return true;
        }

        // Give the floating player the first chance at edge navigation. Page
        // fragments may intentionally consume down/left at their own edges;
        // the player should still be reachable from the bottom-right edge.
        if (directionalKey && floatingPlayerWindow != null
                && floatingPlayerWindow.handleDirectionalKey(keyCode, getCurrentFocus())) {
            return true;
        }

        if (currentFragment instanceof PrimaryPageKeyHandler
                && ((PrimaryPageKeyHandler) currentFragment).onKeyDown(keyCode, event)) {
            return true;
        }
        if (currentFragment instanceof SongSquareFragment) {
            SongSquareFragment songSquareFragment = (SongSquareFragment) currentFragment;
            if (songSquareFragment.onKeyDown(keyCode, event)) {
                return true;
            }
        }

        if (wouldLeakVerticalFocusToRail(keyCode, currentFocus)) {
            return true;
        }
        
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBackPressTime < 2000) {
                boolean backgroundPlay = LxRetrofitClient.getBackgroundPlay(this);
                if (!backgroundPlay && player != null) {
                    player.stop();
                    player.clearMediaItems();
                }
                if (!backgroundPlay && floatingPlayerWindow != null) {
                    floatingPlayerWindow.release();
                    floatingPlayerWindow = null;
                }
                finish();
            } else {
                Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                lastBackPressTime = currentTime;
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
