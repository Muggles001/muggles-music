package top.boluofan.musictv.player.visual;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

public final class VinylTurntableView extends View {
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artworkPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint labelFallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stylusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF recordBounds = new RectF();
    private final RectF stylusBounds = new RectF();
    private final Matrix artworkMatrix = new Matrix();
    private final VinylMotion motion = new VinylMotion();

    @Nullable private Bitmap artwork;
    @Nullable private BitmapShader artworkShader;
    private float density;
    private float recordCenterX;
    private float recordCenterY;
    private float recordRadius;
    private float artworkRadius;
    private boolean playing;
    private boolean hostResumed = true;
    private boolean attached;
    private long previousFrameTime;

    public VinylTurntableView(Context context) {
        this(context, null);
    }

    public VinylTurntableView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VinylTurntableView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        configurePaints();
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setArtwork(@Nullable Bitmap artwork) {
        if (this.artwork == artwork) return;
        this.artwork = artwork;
        artworkShader = artwork == null
                ? null
                : new BitmapShader(artwork, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        artworkPaint.setShader(artworkShader);
        updateArtworkMatrix();
        invalidate();
    }

    public void setArtworkDrawable(@Nullable Drawable drawable) {
        if (drawable == null) {
            setArtwork(null);
            return;
        }
        if (drawable instanceof BitmapDrawable) {
            setArtwork(((BitmapDrawable) drawable).getBitmap());
            return;
        }

        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 512;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 512;
        Bitmap renderedArtwork = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(renderedArtwork);
        Rect previousBounds = drawable.getBounds();
        int previousLeft = previousBounds.left;
        int previousTop = previousBounds.top;
        int previousRight = previousBounds.right;
        int previousBottom = previousBounds.bottom;
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        drawable.setBounds(previousLeft, previousTop, previousRight, previousBottom);
        setArtwork(renderedArtwork);
    }

    public void setPlaying(boolean playing) {
        if (this.playing == playing) return;
        this.playing = playing;
        motion.setPlaying(playing);
        previousFrameTime = 0L;
        scheduleFrame();
    }

    public void onHostResume() {
        hostResumed = true;
        previousFrameTime = 0L;
        scheduleFrame();
    }

    public void onHostPause() {
        hostResumed = false;
        previousFrameTime = 0L;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        previousFrameTime = 0L;
        scheduleFrame();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        previousFrameTime = 0L;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float contentWidth = width - getPaddingLeft() - getPaddingRight();
        float contentHeight = height - getPaddingTop() - getPaddingBottom();
        recordRadius = Math.max(0f, Math.min(contentHeight * 0.42f, contentWidth * 0.36f));
        recordCenterX = getPaddingLeft() + contentWidth * 0.44f;
        recordCenterY = getPaddingTop() + contentHeight * 0.55f;
        artworkRadius = recordRadius * 0.65f;
        recordBounds.set(
                recordCenterX - recordRadius,
                recordCenterY - recordRadius,
                recordCenterX + recordRadius,
                recordCenterY + recordRadius
        );
        recordBounds.inset(recordRadius * 0.075f, recordRadius * 0.075f);
        shadowPaint.setShader(new RadialGradient(
                recordCenterX,
                recordCenterY + 12f * density,
                recordRadius * 1.14f,
                new int[]{0x94000000, 0x5C000000, 0x00000000},
                new float[]{0f, 0.78f, 1f},
                Shader.TileMode.CLAMP
        ));
        recordPaint.setShader(new RadialGradient(
                recordCenterX,
                recordCenterY,
                recordRadius,
                new int[]{0xFF36383C, 0xFF17191C, 0xFF08090B},
                new float[]{0f, 0.68f, 1f},
                Shader.TileMode.CLAMP
        ));
        updateArtworkMatrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (recordRadius <= 0f) return;

        long frameTime = SystemClock.uptimeMillis();
        if (previousFrameTime != 0L) motion.advance(frameTime - previousFrameTime);
        previousFrameTime = frameTime;

        canvas.drawCircle(
                recordCenterX,
                recordCenterY + 12f * density,
                recordRadius * 1.14f,
                shadowPaint
        );

        int recordSave = canvas.save();
        canvas.rotate(motion.getAngleDegrees(), recordCenterX, recordCenterY);
        canvas.drawCircle(recordCenterX, recordCenterY, recordRadius, recordPaint);
        drawGrooves(canvas);
        drawArtwork(canvas);
        canvas.restoreToCount(recordSave);

        drawHub(canvas);
        drawTonearm(canvas);

        if (motion.isAnimating()) scheduleFrame();
        else previousFrameTime = 0L;
    }

    private void configurePaints() {
        groovePaint.setStyle(Paint.Style.STROKE);
        groovePaint.setStrokeWidth(Math.max(0.7f, density * 0.55f));
        groovePaint.setColor(Color.WHITE);

        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(Math.max(1f, density * 0.8f));
        highlightPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setColor(0x26FFFFFF);

        labelFallbackPaint.setColor(0xFF4A252B);
        hubPaint.setColor(0xFFD6D7D8);

        armShadowPaint.setStyle(Paint.Style.STROKE);
        armShadowPaint.setStrokeCap(Paint.Cap.ROUND);
        armShadowPaint.setStrokeWidth(11f * density);
        armShadowPaint.setColor(0x62000000);

        armPaint.setStyle(Paint.Style.STROKE);
        armPaint.setStrokeCap(Paint.Cap.ROUND);
        armPaint.setStrokeWidth(7f * density);
        armPaint.setColor(0xFFD4D4D3);

        armHighlightPaint.setStyle(Paint.Style.STROKE);
        armHighlightPaint.setStrokeCap(Paint.Cap.ROUND);
        armHighlightPaint.setStrokeWidth(2f * density);
        armHighlightPaint.setColor(0xFFFFFFFF);

        stylusPaint.setColor(0xFF1A1B1D);
    }

    private void drawGrooves(Canvas canvas) {
        for (int index = 0; index < 36; index++) {
            float ratio = 0.37f + index * 0.0172f;
            groovePaint.setAlpha(index % 5 == 0 ? 46 : 20);
            canvas.drawCircle(recordCenterX, recordCenterY, recordRadius * ratio, groovePaint);
        }

        canvas.drawArc(recordBounds, 198f, 66f, false, highlightPaint);
        canvas.drawArc(recordBounds, 18f, 44f, false, highlightPaint);
    }

    private void drawArtwork(Canvas canvas) {
        if (artworkShader == null) {
            canvas.drawCircle(recordCenterX, recordCenterY, artworkRadius, labelFallbackPaint);
        } else {
            canvas.drawCircle(recordCenterX, recordCenterY, artworkRadius, artworkPaint);
        }
    }

    private void drawHub(Canvas canvas) {
        float hubRadius = recordRadius * 0.035f;
        canvas.drawCircle(recordCenterX, recordCenterY, hubRadius, hubPaint);
        groovePaint.setColor(0x8A000000);
        groovePaint.setAlpha(255);
        groovePaint.setStrokeWidth(Math.max(0.7f, density * 0.55f));
        canvas.drawCircle(recordCenterX, recordCenterY, hubRadius, groovePaint);
        groovePaint.setColor(Color.WHITE);
    }

    private void drawTonearm(Canvas canvas) {
        float pivotX = recordCenterX + recordRadius * 0.78f;
        float pivotY = recordCenterY - recordRadius * 0.86f;
        float armAngle = -27f + motion.getTonearmProgress() * 27f;
        float jointX = pivotX - recordRadius * 0.10f;
        float jointY = pivotY + recordRadius * 0.47f;
        float tipX = pivotX - recordRadius * 0.34f;
        float tipY = pivotY + recordRadius * 0.82f;

        int armSave = canvas.save();
        canvas.rotate(armAngle, pivotX, pivotY);
        canvas.drawLine(
                pivotX + 4f * density,
                pivotY + 6f * density,
                jointX + 4f * density,
                jointY + 6f * density,
                armShadowPaint
        );
        canvas.drawLine(
                jointX + 4f * density,
                jointY + 6f * density,
                tipX + 4f * density,
                tipY + 6f * density,
                armShadowPaint
        );
        canvas.drawLine(pivotX, pivotY, jointX, jointY, armPaint);
        canvas.drawLine(jointX, jointY, tipX, tipY, armPaint);
        canvas.drawLine(
                pivotX - density,
                pivotY,
                jointX - density,
                jointY,
                armHighlightPaint
        );
        canvas.drawLine(
                jointX - density,
                jointY,
                tipX - density,
                tipY,
                armHighlightPaint
        );
        stylusBounds.set(
                tipX - 8f * density,
                tipY - 3f * density,
                tipX + 9f * density,
                tipY + 12f * density
        );
        canvas.drawRoundRect(stylusBounds, 2f * density, 2f * density, stylusPaint);
        canvas.restoreToCount(armSave);

        canvas.drawCircle(pivotX, pivotY, 17f * density, armShadowPaint);
        canvas.drawCircle(pivotX, pivotY, 13f * density, hubPaint);
        canvas.drawCircle(pivotX, pivotY, 5f * density, stylusPaint);
    }

    private void updateArtworkMatrix() {
        if (artworkShader == null || artwork == null || artworkRadius <= 0f) return;
        float diameter = artworkRadius * 2f;
        float scale = Math.max(diameter / artwork.getWidth(), diameter / artwork.getHeight());
        artworkMatrix.reset();
        artworkMatrix.setScale(scale, scale);
        artworkMatrix.postTranslate(
                recordCenterX - artwork.getWidth() * scale * 0.5f,
                recordCenterY - artwork.getHeight() * scale * 0.5f
        );
        artworkShader.setLocalMatrix(artworkMatrix);
    }

    private void scheduleFrame() {
        if (!attached || !hostResumed) return;
        postInvalidateOnAnimation();
    }
}
