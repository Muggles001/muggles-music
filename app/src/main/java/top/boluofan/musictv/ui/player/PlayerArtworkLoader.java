package top.boluofan.musictv.ui.player;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import jp.wasabeef.glide.transformations.BlurTransformation;
import top.boluofan.musictv.R;
import top.boluofan.musictv.player.artwork.ArtworkRequestState;
import top.boluofan.musictv.player.visual.AsciiRibbonView;
import top.boluofan.musictv.player.visual.VinylTurntableView;

final class PlayerArtworkLoader implements AutoCloseable {
    private static final int PRIMARY_SIZE = 720;
    private static final int BLURRED_WIDTH = 640;
    private static final int BLURRED_HEIGHT = 360;
    private static final int DEFAULT_AMBIENT_COLOR = 0xFF845F52;

    private final Activity activity;
    private final ImageView backgroundView;
    private final VinylTurntableView turntableView;
    private final AsciiRibbonView ribbonView;
    private final ArtworkRequestState requestState = new ArtworkRequestState();
    private ArtworkRequestState.Binding binding;
    private CustomTarget<Bitmap> artworkTarget;
    private CustomTarget<Bitmap> blurredArtworkTarget;

    PlayerArtworkLoader(Activity activity, ImageView backgroundView,
                        VinylTurntableView turntableView, AsciiRibbonView ribbonView) {
        this.activity = activity;
        this.backgroundView = backgroundView;
        this.turntableView = turntableView;
        this.ribbonView = ribbonView;
    }

    void loadMetadata(Object artwork) {
        if (artwork == null || !isHostUsable()) return;
        binding = requestState.begin(true);
        load(artwork, binding, true);
    }

    void prepareScrapedFallback() {
        binding = requestState.begin(false);
    }

    void loadScraped(Object artwork) {
        if (artwork == null || !isHostUsable()) return;
        if (binding == null || !requestState.isCurrent(binding)) {
            binding = requestState.begin(false);
        }
        load(artwork, binding, false);
    }

    void clear() {
        requestState.clear();
        binding = null;
        if (activity.isDestroyed()) {
            artworkTarget = null;
            blurredArtworkTarget = null;
            return;
        }
        Glide.with(activity).clear(artworkTarget);
        Glide.with(activity).clear(blurredArtworkTarget);
        Glide.with(activity).clear(backgroundView);
        turntableView.setArtworkDrawable(
                ContextCompat.getDrawable(activity, R.drawable.ic_album_placeholder));
        ribbonView.setAmbientColor(DEFAULT_AMBIENT_COLOR);
        backgroundView.setImageResource(android.R.color.black);
    }

    @Override
    public void close() {
        clear();
        requestState.close();
    }

    private void load(Object artwork, ArtworkRequestState.Binding requestBinding,
                      boolean showPrimaryPlaceholder) {
        if (showPrimaryPlaceholder) {
            turntableView.setArtworkDrawable(
                    ContextCompat.getDrawable(activity, R.drawable.ic_album_placeholder));
            ribbonView.setAmbientColor(DEFAULT_AMBIENT_COLOR);
        }
        Glide.with(activity).clear(artworkTarget);
        Glide.with(activity).clear(blurredArtworkTarget);
        artworkTarget = createArtworkTarget(requestBinding);
        blurredArtworkTarget = createBlurredArtworkTarget(requestBinding);

        Glide.with(activity)
                .asBitmap()
                .load(artwork)
                .override(PRIMARY_SIZE, PRIMARY_SIZE)
                .centerCrop()
                .error(R.drawable.ic_album_placeholder)
                .into(artworkTarget);
        Glide.with(activity).clear(backgroundView);
        Glide.with(activity)
                .asBitmap()
                .load(artwork)
                .override(BLURRED_WIDTH, BLURRED_HEIGHT)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .transform(new CenterCrop(), new BlurTransformation(20, 3))
                .error(android.R.color.black)
                .into(blurredArtworkTarget);
    }

    private CustomTarget<Bitmap> createArtworkTarget(
            ArtworkRequestState.Binding requestBinding) {
        return new CustomTarget<Bitmap>(PRIMARY_SIZE, PRIMARY_SIZE) {
            @Override
            public void onResourceReady(Bitmap resource,
                                        @Nullable Transition<? super Bitmap> transition) {
                if (!isCurrent(requestBinding)) return;
                turntableView.setArtwork(resource);
                Palette.from(resource).resizeBitmapArea(112 * 112).generate(palette -> {
                    if (palette == null || !isCurrent(requestBinding)) return;
                    int dominant = palette.getVibrantColor(
                            palette.getMutedColor(palette.getDominantColor(DEFAULT_AMBIENT_COLOR)));
                    ribbonView.setAmbientColor(dominant);
                });
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                if (!requestState.isCurrent(requestBinding)) return;
                turntableView.setArtworkDrawable(
                        ContextCompat.getDrawable(activity, R.drawable.ic_album_placeholder));
                ribbonView.setAmbientColor(DEFAULT_AMBIENT_COLOR);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                onLoadCleared(errorDrawable);
            }
        };
    }

    private CustomTarget<Bitmap> createBlurredArtworkTarget(
            ArtworkRequestState.Binding requestBinding) {
        return new CustomTarget<Bitmap>(BLURRED_WIDTH, BLURRED_HEIGHT) {
            @Override
            public void onResourceReady(Bitmap resource,
                                        @Nullable Transition<? super Bitmap> transition) {
                if (isCurrent(requestBinding)) backgroundView.setImageBitmap(resource);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                if (requestState.isCurrent(requestBinding)) {
                    backgroundView.setImageResource(android.R.color.black);
                }
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                onLoadCleared(errorDrawable);
            }
        };
    }

    private boolean isCurrent(ArtworkRequestState.Binding requestBinding) {
        return requestState.isCurrent(requestBinding) && isHostUsable();
    }

    private boolean isHostUsable() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }
}
