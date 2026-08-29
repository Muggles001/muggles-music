package top.boluofan.musictv;

import android.animation.ObjectAnimator;
import android.app.Activity;
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

import com.bumptech.glide.Glide;
import top.boluofan.musictv.player.session.MediaControllerConnection;

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
    private MediaControllerConnection controllerConnection;
    private Player.Listener playerListener;
    private ObjectAnimator fadeAnim;
    private Handler fadeHandler;
    private Runnable fadeOutRunnable;
    private boolean isConnected = false;
    private boolean isReleased = false;
    private boolean isFadedOut = false;
    private boolean isFocused = false;
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
        
        setPassiveFocusEnabled(false);
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
                setPassiveFocusEnabled(false);
                focusReturnView = null;
            }
        });

        container.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || 
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    openPlayer();
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                        || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                        || keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    returnFocusToSource();
                    return true;
                }
            }
            return false;
        });
    }

    private void setPassiveFocusEnabled(boolean enabled) {
        container.setFocusable(enabled);
        container.setFocusableInTouchMode(enabled);
    }

    private void returnFocusToSource() {
        View target = focusReturnView;
        if (target != null && target.isAttachedToWindow() && target.isShown()
                && target.isEnabled() && target.isFocusable()) {
            target.requestFocus();
        }
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
                    if (container.hasFocus()) returnFocusToSource();
                    container.setVisibility(View.GONE);
                    setPassiveFocusEnabled(false);
                    focusReturnView = null;
                }
            }
        });
        fadeAnim.start();
    }

    public void connectToService() {
        if (isConnected || isReleased) return;
        if (controllerConnection == null) {
            controllerConnection = new MediaControllerConnection(
                    context,
                    () -> !isReleased && !activity.isFinishing() && !activity.isDestroyed(),
                    resolvedController -> {
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
                    },
                    error -> Log.e(TAG, "Failed to get MediaController: " + error.getMessage()));
        }
        controllerConnection.connect();
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
                if (container.hasFocus()) returnFocusToSource();
                container.setVisibility(View.GONE);
                container.setAlpha(1.0f);
                isFadedOut = false;
                setPassiveFocusEnabled(false);
                focusReturnView = null;
            });
        }
    }

    public void release() {
        if (isReleased) return;
        isReleased = true;
        isFocused = false;
        setPassiveFocusEnabled(false);
        focusReturnView = null;
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
        if (controllerConnection != null) controllerConnection.disconnect();
        
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
    
    private boolean requestOverlayFocus() {
        if (container != null && container.getVisibility() == View.VISIBLE) {
            setPassiveFocusEnabled(true);
            boolean focused = container.requestFocus();
            if (!focused) setPassiveFocusEnabled(false);
            return focused;
        }
        return false;
    }

    public boolean requestFocusFrom(View source) {
        if (source == null || container == null || container.getVisibility() != View.VISIBLE
                || container.getAlpha() < 0.1f
                || source.getNextFocusRightId() != R.id.floatingPlayerContainer) {
            return false;
        }
        focusReturnView = source;
        if (requestOverlayFocus()) return true;
        focusReturnView = null;
        return false;
    }

    /**
     * The floating player is an overlay, not another row in the content list.
     * Only an explicit right-focus edge enters this decor overlay. Keeping it
     * out of passive FocusFinder results prevents unrelated page controls from
     * jumping into a view that was attached after the page hierarchy.
     */
    public boolean handleDirectionalKey(int keyCode, View currentFocus) {
        if (container == null || container.getVisibility() != View.VISIBLE
                || container.getAlpha() < 0.1f) {
            return false;
        }

        if (currentFocus == container) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                returnFocusToSource();
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return true;
            }
            return false;
        }

        if (currentFocus == null) return false;

        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                && currentFocus.getNextFocusRightId() == R.id.floatingPlayerContainer) {
            requestFocusFrom(currentFocus);
            return true;
        }
        return false;
    }

    public boolean handleLeftKey(View currentFocus) {
        return handleDirectionalKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT, currentFocus);
    }
}
