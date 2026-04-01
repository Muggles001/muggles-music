package top.boluofan.musictv;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
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
    private ObjectAnimator rotateAnim;
    private boolean isPlaying = false;
    private boolean isConnected = false;

    public FloatingPlayerWindow(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();

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
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(12, 120, 0, 0);
        
        container.setLayoutParams(params);
        
        if (container.getParent() == null) {
            rootView.addView(container);
        }
        
        container.setFocusable(true);
        
        cvCover.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        rotateAnim = ObjectAnimator.ofFloat(cvCover, "rotation", 0f, 360f);
        rotateAnim.setDuration(10000);
        rotateAnim.setInterpolator(new LinearInterpolator());
        rotateAnim.setRepeatCount(ObjectAnimator.INFINITE);
        rotateAnim.setRepeatMode(ObjectAnimator.RESTART);
    }

    private void setupListeners() {
        container.setOnClickListener(v -> openPlayer());

        container.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                container.setBackgroundResource(R.drawable.bg_floating_player_focused);
                tvTitle.setSelected(true);
            } else {
                container.setBackgroundResource(R.drawable.bg_floating_player);
                tvTitle.setSelected(false);
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

    public void connectToService() {
        if (isConnected) return;
        
        SessionToken sessionToken = new SessionToken(context, 
                new ComponentName(context, MusicService.class));
        
        controllerFuture = new MediaController.Builder(context, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                isConnected = true;
                playerListener = new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean playing) {
                        isPlaying = playing;
                        updatePlayPauseButton();
                    }

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
        }, MoreExecutors.directExecutor());
    }

    private void updatePlayPauseButton() {
        activity.runOnUiThread(() -> {
            if (rotateAnim != null) {
                if (isPlaying) {
                    if (rotateAnim.isPaused()) rotateAnim.resume();
                    else if (!rotateAnim.isRunning()) rotateAnim.start();
                } else {
                    rotateAnim.pause();
                }
            }
        });
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
            isPlaying = player.isPlaying();
            
            if (rotateAnim != null) {
                if (isPlaying) {
                    if (rotateAnim.isPaused()) rotateAnim.resume();
                    else if (!rotateAnim.isRunning()) rotateAnim.start();
                } else {
                    rotateAnim.pause();
                }
            }

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
        });
    }

    public void hide() {
        if (container != null) {
            activity.runOnUiThread(() -> container.setVisibility(View.GONE));
        }
    }

    public void release() {
        if (rotateAnim != null) {
            rotateAnim.cancel();
            rotateAnim = null;
        }
        if (player != null && playerListener != null) {
            player.removeListener(playerListener);
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        isConnected = false;
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
    
    public boolean handleLeftKey(View currentFocus) {
        if (container == null || container.getVisibility() != View.VISIBLE) {
            return false;
        }
        
        if (currentFocus == null) {
            return requestFocus();
        }
        
        int[] location = new int[2];
        currentFocus.getLocationOnScreen(location);
        
        if (location[0] <= 60) {
            return requestFocus();
        }
        
        return false;
    }
}
