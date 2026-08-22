package top.boluofan.musictv;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

public class FloatingPlayerWindow {
    private static final String TAG = "FloatingPlayerWindow";

    private final Activity activity;
    private final Context context;
    private final View floatingView;
    private final CardView cvCover;
    private final ImageView ivCover;
    private final TextView tvTitle;
    private final View container;

    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private Player.Listener playerListener;
    private ObjectAnimator fadeAnim;
    private Handler fadeHandler;
    private Runnable fadeOutRunnable;
    private boolean isConnected = false;
    private boolean isReleased = false;
    private boolean isFadedOut = false;
    private boolean isFocused = false;
    /** The view that handed focus to the floating player, used for a clean
     *  return path when the user presses up/left. */
    private View focusReturnView;
    private final int collapsedWidth;
    private final int expandedWidth;

    public FloatingPlayerWindow(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
        collapsedWidth = dimension(R.dimen.lx_floating_player_collapsed_width);
        expandedWidth = dimension(R.dimen.lx_floating_player_expanded_width);

        LayoutInflater inflater = LayoutInflater.from(activity);
        floatingView = inflater.inflate(R.layout.layout_floating_player, null);

        container = floatingView.findViewById(R.id.floatingPlayerContainer);
        cvCover = floatingView.findViewById(R.id.cvFloatingCover);
        ivCover = floatingView.findViewById(R.id.ivFloatingCover);
        tvTitle = floatingView.findViewById(R.id.tvFloatingTitle);

        setupContainer();
        setupListeners();
    }

    private void setupContainer() {
        ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();
        
        View existingContainer = rootView.findViewById(R.id.floatingPlayerContainer);
        if (existingContainer != null && existingContainer.getParent() != null) {
            ((ViewGroup) existingContainer.getParent()).removeView(existingContainer);
        }
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                collapsedWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = dimension(R.dimen.lx_floating_player_margin);
        params.setMargins(0, 0, margin, margin);
        params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        
        container.setLayoutParams(params);
        
        if (container.getParent() == null) {
            rootView.addView(container);
        }
        
        // This view is added to the decor window after the page content. It
        // must still participate in the TV focus tree, but it should never
        // steal focus merely because playback starts.
        container.setFocusable(true);
        container.setFocusableInTouchMode(true);
        container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        
        fadeHandler = new Handler(Looper.getMainLooper());
        fadeOutRunnable = this::fadeOut;
    }

    private void setupListeners() {
        container.setOnClickListener(v -> openPlayer());

        container.setOnFocusChangeListener((v, hasFocus) -> {
            isFocused = hasFocus;
            if (hasFocus) {
                tvTitle.setSelected(true);
                expandPlayer();
            } else {
                tvTitle.setSelected(false);
                collapsePlayer();
            }
        });

        container.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || 
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    openPlayer();
                    return true;
                }
            }
            return false;
        });
    }

    private void openPlayer() {
        context.startActivity(new Intent(context, PlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void expandPlayer() {
        fadeHandler.removeCallbacks(fadeOutRunnable);
        tvTitle.animate().cancel();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        if (params.width != expandedWidth) {
            params.width = expandedWidth;
            container.setLayoutParams(params);
        }
        tvTitle.setAlpha(0f);
        tvTitle.animate().alpha(1f)
                .setDuration(context.getResources().getInteger(R.integer.lx_motion_focus)).start();
    }

    private void collapsePlayer() {
        tvTitle.animate().cancel();
        tvTitle.animate().alpha(0f)
                .setDuration(context.getResources().getInteger(R.integer.lx_motion_press))
                .withEndAction(() -> {
            if (!isFocused && container.getParent() != null) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
                params.width = collapsedWidth;
                container.setLayoutParams(params);
            }
        }).start();
    }

    private int dimension(int resourceId) {
        return activity.getResources().getDimensionPixelSize(resourceId);
    }

    private void fadeIn() {
        if (fadeAnim != null && fadeAnim.isRunning()) {
            fadeAnim.cancel();
        }
        
        fadeHandler.removeCallbacks(fadeOutRunnable);
        
        if (!isFadedOut && container.getAlpha() >= 1.0f) {
            return;
        }
        
        isFadedOut = false;
        
        container.setVisibility(View.VISIBLE);
        fadeAnim = ObjectAnimator.ofFloat(container, "alpha", container.getAlpha(), 1.0f);
        fadeAnim.setDuration(context.getResources().getInteger(R.integer.lx_motion_panel));
        fadeAnim.start();
    }

    private void fadeOut() {
        if (fadeAnim != null && fadeAnim.isRunning()) {
            fadeAnim.cancel();
        }
        
        if (isFadedOut || container.getAlpha() <= 0.0f) {
            return;
        }
        
        isFadedOut = true;
        
        fadeAnim = ObjectAnimator.ofFloat(container, "alpha", container.getAlpha(), 0.0f);
        fadeAnim.setDuration(context.getResources().getInteger(R.integer.lx_motion_panel));
        fadeAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (isFadedOut) {
                    container.setVisibility(View.GONE);
                }
            }
        });
        fadeAnim.start();
    }

    public void connectToService() {
        if (isConnected || isReleased) return;
        
        SessionToken sessionToken = new SessionToken(context, 
                new ComponentName(context, MusicService.class));
        
        final ListenableFuture<MediaController> pendingController =
                new MediaController.Builder(context, sessionToken).buildAsync();
        controllerFuture = pendingController;
        pendingController.addListener(() -> {
            try {
                MediaController resolvedController = pendingController.get();
                if (isReleased || activity.isFinishing() || activity.isDestroyed()) {
                    MediaController.releaseFuture(pendingController);
                    return;
                }
                player = resolvedController;
                isConnected = true;
                playerListener = new Player.Listener() {
                    @Override
                    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                        updateUI();
                    }

                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) {
                            updateUI();
                        }
                    }
                };
                player.addListener(playerListener);
                updateUI();
            } catch (Exception e) {
                Log.e(TAG, "Failed to get MediaController: " + e.getMessage());
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(activity));
    }

    public void showIfPlaying() {
        if (player == null || player.getMediaItemCount() == 0) {
            hide();
            return;
        }

        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem == null) {
            hide();
            return;
        }

        updateUI();
    }

    public void updateUI() {
        if (player == null || player.getMediaItemCount() == 0) {
            hide();
            return;
        }

        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem == null) {
            hide();
            return;
        }

        activity.runOnUiThread(() -> {
            MediaMetadata metadata = currentItem.mediaMetadata;
            if (metadata != null) {
                CharSequence title = metadata.title;
                tvTitle.setText(title != null ? title.toString() : "");

                Uri artworkUri = metadata.artworkUri;
                if (artworkUri != null) {
                    Glide.with(context)
                            .load(artworkUri)
                            .placeholder(R.drawable.ic_album_placeholder)
                            .centerCrop()
                            .into(ivCover);
                } else {
                    ivCover.setImageResource(R.drawable.ic_album_placeholder);
                }
            }

            container.setVisibility(View.VISIBLE);
            container.setAlpha(1.0f);
            isFadedOut = false;
        });
    }

    public void hide() {
        if (container != null) {
            activity.runOnUiThread(() -> {
                fadeHandler.removeCallbacks(fadeOutRunnable);
                if (fadeAnim != null) {
                    fadeAnim.cancel();
                }
                container.setVisibility(View.GONE);
                container.setAlpha(1.0f);
                isFadedOut = false;
            });
        }
    }

    public void release() {
        if (isReleased) return;
        isReleased = true;
        isFocused = false;
        tvTitle.animate().cancel();
        if (fadeAnim != null) {
            fadeAnim.cancel();
            fadeAnim = null;
        }
        if (fadeHandler != null) {
            fadeHandler.removeCallbacks(fadeOutRunnable);
            fadeHandler = null;
        }
        if (player != null && playerListener != null) {
            player.removeListener(playerListener);
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        
        if (container != null) {
            container.setOnFocusChangeListener(null);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
            if (params != null) {
                params.width = collapsedWidth;
                container.setLayoutParams(params);
            }
            tvTitle.setSelected(false);
            
            ViewGroup parent = (ViewGroup) container.getParent();
            if (parent != null) {
                parent.removeView(container);
            }
        }
        
        isConnected = false;
        player = null;
        playerListener = null;
    }
    
    public View getContainer() {
        return container;
    }
    
    public boolean requestFocus() {
        if (container != null && container.getVisibility() == View.VISIBLE) {
            return container.requestFocus();
        }
        return false;
    }

    /** Enter the overlay from an explicitly configured row edge. */
    public boolean requestFocusFrom(View source) {
        if (source == null || container == null || container.getVisibility() != View.VISIBLE
                || container.getAlpha() < 0.1f) {
            return false;
        }
        focusReturnView = source;
        return container.requestFocus();
    }

    /**
     * The floating player is an overlay, not another row in the content list.
     * Only an explicit right press from a small lower-right control enters it;
     * down presses always remain available for ordinary song-list navigation.
     * Up/left returns to the view that started the jump.
     */
    public boolean handleDirectionalKey(int keyCode, View currentFocus) {
        if (container == null || container.getVisibility() != View.VISIBLE
                || container.getAlpha() < 0.1f) {
            return false;
        }

        if (currentFocus == container) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                if (focusReturnView != null && focusReturnView.isShown()
                        && focusReturnView.isFocusable()) {
                    focusReturnView.requestFocus();
                    return true;
                }
            }
            return false;
        }

        if (currentFocus == null) return false;

        // Song rows explicitly mark their rightmost action with the overlay
        // id. This gives the remote a deterministic edge transition even when
        // the row is not close enough to the visual overlay for geometry-based
        // detection.
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                && currentFocus.getNextFocusRightId() == R.id.floatingPlayerContainer) {
            return requestFocusFrom(currentFocus);
        }

        // Song controls already define an exact left-to-right graph. Applying
        // the lower-right geometry shortcut inside a row can skip its remaining
        // actions when larger TV focus targets happen to overlap that zone.
        if (isInsideSongRow(currentFocus)) return false;

        int[] decorLocation = new int[2];
        int[] focusLocation = new int[2];
        View decor = activity.getWindow().getDecorView();
        decor.getLocationOnScreen(decorLocation);
        currentFocus.getLocationOnScreen(focusLocation);
        int focusBottom = focusLocation[1] - decorLocation[1] + currentFocus.getHeight();
        int focusRight = focusLocation[0] - decorLocation[0] + currentFocus.getWidth();
        int decorHeight = decor.getHeight();
        int decorWidth = decor.getWidth();
        boolean nearBottom = focusBottom >= decorHeight
                - dimension(R.dimen.lx_floating_entry_bottom);
        boolean nearRight = focusRight >= decorWidth
                - dimension(R.dimen.lx_floating_player_expanded_width);

        boolean rightPressFromBroadRow = keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                && currentFocus instanceof ViewGroup
                && currentFocus.getWidth() >= Math.round(decorWidth * 0.65f);
        boolean shouldEnter = !rightPressFromBroadRow
                && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                && nearBottom && nearRight;

        if (shouldEnter) {
            focusReturnView = currentFocus;
            return requestFocus();
        }
        return false;
    }

    private boolean isInsideSongRow(View view) {
        View current = view;
        while (current != null) {
            if (current.getId() == R.id.item_song_root) return true;
            if (!(current.getParent() instanceof View)) return false;
            current = (View) current.getParent();
        }
        return false;
    }

    public boolean handleLeftKey(View currentFocus) {
        return handleDirectionalKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT, currentFocus);
    }
}
