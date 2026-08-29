package top.boluofan.musictv.player.visual;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import top.boluofan.musictv.player.lyrics.LyricTimeline;

public final class SpatialLyricsView extends View {
    private static final long TRANSITION_DURATION_MS = 450L;
    private static final int MAX_VISIBLE_DISTANCE = 3;
    private static final int CACHE_DISTANCE = 3;
    private static final int MAX_LAYOUT_LINES = 2;
    private static final float DEFAULT_FOCUS_FRACTION = 0.50f;
    private static final float TEXT_SIZE_PX_1080 = 54f;
    private static final float MULTILINE_SPACING_PX_1080 = 8f;
    private static final float TAIL_FADE_PX_1080 = 32f;
    private static final float PROJECTION_CENTER_Y_OFFSET_PX_1080 = 72f;

    private final TextPaint lyricPaint = new TextPaint(PaintFlags.VALUE);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix projectionMatrix = new Matrix();
    private final SpatialLyricsGeometry.Pose pose = new SpatialLyricsGeometry.Pose();
    private final float[] sourceQuad = new float[8];
    private final float[] projectedQuad = new float[8];
    private final int[] viewLocation = new int[2];
    private final int[] rootLocation = new int[2];
    private final LinearInterpolator linearInterpolator = new LinearInterpolator();

    private List<LyricTimeline.Line> lyrics = Collections.emptyList();
    private LayoutEntry[] layoutEntries = new LayoutEntry[0];
    private float[] blockHeights = new float[0];
    @Nullable private ValueAnimator transitionAnimator;
    private float focusFraction = DEFAULT_FOCUS_FRACTION;
    private float displayedIndex = -1f;
    private float animationStartIndex;
    private float animationTargetIndex;
    private int currentIndex = -1;
    private int layoutWidth;
    private float projectionCenterX;
    private float projectionCenterY;
    private boolean hostResumed = true;
    private boolean attached;

    public SpatialLyricsView(Context context) {
        this(context, null);
    }

    public SpatialLyricsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpatialLyricsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        configurePaints();
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setLyrics(@Nullable List<LyricTimeline.Line> lyrics) {
        cancelTransition();
        recycleCachedBitmaps();
        this.lyrics = lyrics == null
                ? Collections.<LyricTimeline.Line>emptyList()
                : new ArrayList<>(lyrics);
        currentIndex = -1;
        displayedIndex = -1f;
        rebuildLayouts();
        invalidate();
    }

    public void setCurrentIndex(int index, boolean animate) {
        int normalizedIndex = index >= 0 && index < lyrics.size() ? index : -1;
        if (normalizedIndex == currentIndex && !isTransitionRunning()) return;

        float transitionStart = displayedIndex;
        cancelTransition();
        currentIndex = normalizedIndex;
        boolean canAnimate = animate
                && attached
                && hostResumed
                && transitionStart >= 0f
                && normalizedIndex >= 0
                && Math.abs(normalizedIndex - transitionStart) <= CACHE_DISTANCE;
        if (!canAnimate) {
            displayedIndex = normalizedIndex;
            prepareBitmaps(normalizedIndex, normalizedIndex);
            invalidate();
            return;
        }
        startTransition(transitionStart, normalizedIndex);
    }

    public void setFocusFraction(float focusFraction) {
        this.focusFraction = Math.max(0f, Math.min(1f, focusFraction));
        invalidate();
    }

    public void onHostResume() {
        hostResumed = true;
        resumeTransitionIfNeeded();
    }

    public void onHostPause() {
        hostResumed = false;
        cancelTransition();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        resumeTransitionIfNeeded();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        cancelTransition();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        int availableWidth = Math.max(1, width - getPaddingLeft() - getPaddingRight());
        layoutWidth = Math.max(1, Math.round(availableWidth * 0.84f));
        rebuildLayouts();
        prepareBitmaps(displayedIndex, currentIndex);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layoutEntries.length == 0 || displayedIndex < 0f) return;

        updateProjectionCenter();
        SpatialLyricsGeometry.compute(
                0f,
                getWidth(),
                getHeight(),
                projectionCenterX,
                projectionCenterY,
                focusFraction,
                pose
        );
        updateProjectionMatrix();
        int checkpoint = canvas.save();
        canvas.concat(projectionMatrix);
        int nearestIndex = Math.round(displayedIndex);
        int start = Math.max(0, nearestIndex - MAX_VISIBLE_DISTANCE);
        int end = Math.min(layoutEntries.length - 1, nearestIndex + MAX_VISIBLE_DISTANCE);
        for (int index = start; index <= end; index++) {
            if (index != nearestIndex) drawLine(canvas, index);
        }
        if (nearestIndex >= start && nearestIndex <= end) {
            drawLine(canvas, nearestIndex);
        }
        canvas.restoreToCount(checkpoint);
    }

    private void configurePaints() {
        lyricPaint.setColor(Color.WHITE);
        lyricPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        lyricPaint.setLetterSpacing(0.02f);
    }

    private void rebuildLayouts() {
        recycleCachedBitmaps();
        if (layoutWidth <= 0 || lyrics.isEmpty()) {
            layoutEntries = new LayoutEntry[0];
            blockHeights = new float[0];
            return;
        }

        LayoutEntry[] rebuiltEntries = new LayoutEntry[lyrics.size()];
        float[] rebuiltHeights = new float[lyrics.size()];
        for (int index = 0; index < lyrics.size(); index++) {
            String sourceText = lyrics.get(index).text == null ? "" : lyrics.get(index).text;
            String primaryText = SpatialLyricsGeometry.primaryText(sourceText);
            String translationText = SpatialLyricsGeometry.translationText(sourceText);
            LayoutEntry entry = new LayoutEntry(
                    primaryText.length() == 0 ? null : primaryText,
                    translationText.length() == 0 ? null : translationText,
                    linePaint()
            );
            rebuiltEntries[index] = entry;
            rebuiltHeights[index] = entry.blockHeight;
        }
        layoutEntries = rebuiltEntries;
        blockHeights = rebuiltHeights;
    }

    private TextPaint linePaint() {
        TextPaint paint = new TextPaint(lyricPaint);
        float viewportScale = Math.max(0.5f, getHeight() / 1080f);
        paint.setTextSize(TEXT_SIZE_PX_1080 * viewportScale);
        return paint;
    }

    private void prepareBitmaps(float fromIndex, float toIndex) {
        if (layoutEntries.length == 0 || fromIndex < 0f || toIndex < 0f) return;
        int keepStart = Math.max(
                0,
                (int) Math.floor(Math.min(fromIndex, toIndex)) - CACHE_DISTANCE
        );
        int keepEnd = Math.min(
                layoutEntries.length - 1,
                (int) Math.ceil(Math.max(fromIndex, toIndex)) + CACHE_DISTANCE
        );
        for (int index = 0; index < layoutEntries.length; index++) {
            LayoutEntry entry = layoutEntries[index];
            if (index < keepStart || index > keepEnd) {
                entry.recycleBitmap();
            } else {
                entry.ensureBitmap();
            }
        }
    }

    @SuppressLint("WrongConstant")
    private StaticLayout createTextLayout(
            String text,
            TextPaint paint,
            int maxLines,
            float lineSpacing
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, layoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                    .setLineSpacing(lineSpacing, 1f)
                    .setMaxLines(maxLines)
                    .build();
        }
        return new StaticLayout(
                text,
                paint,
                layoutWidth,
                Layout.Alignment.ALIGN_CENTER,
                1f,
                lineSpacing,
                false
        );
    }

    private Bitmap renderEntry(LayoutEntry entry) {
        Bitmap bitmap = Bitmap.createBitmap(
                entry.blockWidth,
                entry.blockHeight,
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        if (entry.primaryLayout == null) {
            if (entry.primaryText != null) {
                canvas.drawText(
                        entry.primaryText,
                        (entry.blockWidth - entry.primaryNaturalWidth) * 0.5f,
                        -entry.metrics.ascent,
                        entry.paint
                );
            }
        } else {
            entry.primaryLayout.draw(canvas);
            if (entry.primaryTruncated) {
                int lastLine = entry.primaryLineCount - 1;
                float lineEnd = Math.min(
                        entry.blockWidth,
                        entry.primaryLayout.getLineRight(lastLine)
                );
                applyTailFade(
                        canvas,
                        lineEnd,
                        entry.primaryLayout.getLineTop(lastLine),
                        entry.primaryLayout.getLineBottom(lastLine)
                );
            }
        }
        if (entry.translationLayout != null) {
            int checkpoint = canvas.save();
            canvas.translate(0f, entry.translationTop);
            entry.translationLayout.draw(canvas);
            if (entry.translationTruncated) {
                int lastLine = entry.translationLineCount - 1;
                float lineEnd = Math.min(
                        entry.blockWidth,
                        entry.translationLayout.getLineRight(lastLine)
                );
                applyTailFade(
                        canvas,
                        lineEnd,
                        entry.translationLayout.getLineTop(lastLine),
                        entry.translationLayout.getLineBottom(lastLine)
                );
            }
            canvas.restoreToCount(checkpoint);
        }
        return bitmap;
    }

    private void applyTailFade(Canvas canvas, float lineEnd, float top, float bottom) {
        float viewportScale = Math.max(0.5f, getHeight() / 1080f);
        float fadeWidth = Math.min(lineEnd, TAIL_FADE_PX_1080 * viewportScale);
        Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fadePaint.setShader(new LinearGradient(
                lineEnd - fadeWidth,
                0f,
                lineEnd,
                0f,
                Color.WHITE,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawRect(lineEnd - fadeWidth, top, lineEnd, bottom, fadePaint);
        fadePaint.setXfermode(null);
    }

    private void drawLine(Canvas canvas, int index) {
        LayoutEntry entry = layoutEntries[index];
        float distance = index - displayedIndex;
        Bitmap bitmap = entry.bitmap;
        if (bitmap == null || bitmap.isRecycled()) return;

        SpatialLyricsGeometry.compute(
                distance,
                getWidth(),
                getHeight(),
                projectionCenterX,
                projectionCenterY,
                focusFraction,
                pose
        );
        pose.screenY = SpatialLyricsGeometry.blockCenterY(
                index,
                displayedIndex,
                getHeight() * focusFraction,
                getHeight(),
                blockHeights
        );
        drawPlaneLine(
                canvas,
                bitmap,
                Math.round(255f * pose.alpha),
                SpatialLyricsGeometry.activeOverlayAlpha(distance)
        );
    }

    private void drawPlaneLine(
            Canvas canvas,
            Bitmap bitmap,
            int alpha,
            int overlayAlpha
    ) {
        bitmapPaint.setAlpha(alpha);
        float left = pose.screenX - bitmap.getWidth() * 0.5f;
        float top = pose.screenY - bitmap.getHeight() * 0.5f;
        canvas.drawBitmap(
                bitmap,
                left,
                top,
                bitmapPaint
        );
        if (overlayAlpha > 0) {
            bitmapPaint.setAlpha(overlayAlpha);
            canvas.drawBitmap(bitmap, left, top, bitmapPaint);
        }
    }

    private void updateProjectionMatrix() {
        float width = getWidth();
        float height = getHeight();
        sourceQuad[0] = 0f;
        sourceQuad[1] = 0f;
        sourceQuad[2] = width;
        sourceQuad[3] = 0f;
        sourceQuad[4] = width;
        sourceQuad[5] = height;
        sourceQuad[6] = 0f;
        sourceQuad[7] = height;
        SpatialLyricsGeometry.populateProjectedPlaneQuad(
                pose,
                width,
                height,
                projectedQuad
        );
        projectionMatrix.reset();
        projectionMatrix.setPolyToPoly(
                sourceQuad,
                0,
                projectedQuad,
                0,
                4
        );
    }

    private void updateProjectionCenter() {
        View root = getRootView();
        if (root == null) {
            projectionCenterX = getWidth();
            projectionCenterY = getHeight() * 0.5f
                    + PROJECTION_CENTER_Y_OFFSET_PX_1080 * getHeight() / 1080f;
            return;
        }
        getLocationInWindow(viewLocation);
        root.getLocationInWindow(rootLocation);
        projectionCenterX = rootLocation[0] + root.getWidth() - viewLocation[0];
        projectionCenterY = rootLocation[1]
                + root.getHeight() * 0.5f
                + PROJECTION_CENTER_Y_OFFSET_PX_1080 * root.getHeight() / 1080f
                - viewLocation[1];
    }

    private void startTransition(float startIndex, float targetIndex) {
        prepareBitmaps(startIndex, targetIndex);
        animationStartIndex = startIndex;
        animationTargetIndex = targetIndex;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        transitionAnimator = animator;
        animator.setDuration(TRANSITION_DURATION_MS);
        animator.setInterpolator(linearInterpolator);
        animator.addUpdateListener(animation -> {
            displayedIndex = SpatialLyricsGeometry.interpolateIndex(
                    animationStartIndex,
                    animationTargetIndex,
                    animation.getAnimatedFraction()
            );
            invalidate();
        });
        animator.start();
    }

    private void resumeTransitionIfNeeded() {
        if (!attached || !hostResumed || currentIndex < 0) return;
        if (displayedIndex < 0f) {
            displayedIndex = currentIndex;
            prepareBitmaps(currentIndex, currentIndex);
            invalidate();
        } else if (Math.abs(displayedIndex - currentIndex) > 0.001f) {
            startTransition(displayedIndex, currentIndex);
        }
    }

    private boolean isTransitionRunning() {
        return transitionAnimator != null && transitionAnimator.isRunning();
    }

    private void cancelTransition() {
        ValueAnimator animator = transitionAnimator;
        transitionAnimator = null;
        if (animator != null) animator.cancel();
    }

    private void recycleCachedBitmaps() {
        for (LayoutEntry entry : layoutEntries) {
            if (entry != null) entry.recycleBitmap();
        }
    }

    private final class LayoutEntry {
        @Nullable final String primaryText;
        @Nullable final String translationText;
        final TextPaint paint;
        final Paint.FontMetricsInt metrics;
        @Nullable final StaticLayout primaryLayout;
        @Nullable final StaticLayout translationLayout;
        final int primaryLineCount;
        final int translationLineCount;
        final int primaryHeight;
        final int translationTop;
        final int blockWidth;
        final int blockHeight;
        final float primaryNaturalWidth;
        final boolean primaryTruncated;
        final boolean translationTruncated;
        @Nullable Bitmap bitmap;

        LayoutEntry(
                @Nullable String primaryText,
                @Nullable String translationText,
                TextPaint paint
        ) {
            this.primaryText = primaryText;
            this.translationText = translationText;
            this.paint = paint;
            metrics = paint.getFontMetricsInt();
            int lineHeight = Math.max(1, metrics.descent - metrics.ascent);
            if (primaryText == null && translationText == null) {
                primaryLayout = null;
                translationLayout = null;
                primaryLineCount = 1;
                translationLineCount = 0;
                primaryHeight = lineHeight;
                translationTop = lineHeight;
                blockWidth = 1;
                blockHeight = lineHeight;
                primaryNaturalWidth = 0f;
                primaryTruncated = false;
                translationTruncated = false;
                return;
            }

            float lineSpacing = MULTILINE_SPACING_PX_1080
                    * Math.max(0.5f, getHeight() / 1080f);
            primaryNaturalWidth = primaryText == null ? 0f : paint.measureText(primaryText);
            if (primaryText == null) {
                primaryLayout = null;
                primaryLineCount = 0;
                primaryHeight = 0;
                primaryTruncated = false;
            } else if (primaryNaturalWidth <= layoutWidth) {
                primaryLayout = null;
                primaryLineCount = 1;
                primaryHeight = lineHeight;
                primaryTruncated = false;
            } else {
                StaticLayout layout = createTextLayout(
                        primaryText, paint, MAX_LAYOUT_LINES, lineSpacing);
                primaryLayout = layout;
                primaryLineCount = Math.max(
                        1, Math.min(MAX_LAYOUT_LINES, layout.getLineCount()));
                primaryHeight = Math.max(
                        1, layout.getLineBottom(primaryLineCount - 1));
                primaryTruncated = primaryNaturalWidth > layoutWidth * MAX_LAYOUT_LINES
                        || layout.getLineCount() > primaryLineCount
                        || layout.getLineEnd(primaryLineCount - 1) < primaryText.length()
                        || layout.getLineWidth(primaryLineCount - 1) > layoutWidth;
            }

            if (translationText == null) {
                translationLayout = null;
                translationLineCount = 0;
                translationTop = primaryHeight;
                translationTruncated = false;
                blockHeight = primaryHeight;
            } else {
                StaticLayout layout = createTextLayout(
                        translationText, paint, MAX_LAYOUT_LINES, lineSpacing);
                translationLayout = layout;
                translationLineCount = Math.max(
                        1, Math.min(MAX_LAYOUT_LINES, layout.getLineCount()));
                translationTop = primaryHeight
                        + (primaryText == null ? 0 : Math.round(lineSpacing));
                blockHeight = translationTop + Math.max(
                        1, layout.getLineBottom(translationLineCount - 1));
                float naturalTranslationWidth = paint.measureText(translationText);
                translationTruncated = naturalTranslationWidth > layoutWidth * MAX_LAYOUT_LINES
                        || layout.getLineCount() > translationLineCount
                        || layout.getLineEnd(translationLineCount - 1) < translationText.length()
                        || layout.getLineWidth(translationLineCount - 1) > layoutWidth;
            }
            blockWidth = primaryLayout != null || translationLayout != null
                    ? layoutWidth
                    : Math.max(1, (int) Math.ceil(primaryNaturalWidth));
        }

        void ensureBitmap() {
            if (primaryText == null && translationText == null) return;
            if (bitmap == null || bitmap.isRecycled()) bitmap = renderEntry(this);
        }

        void recycleBitmap() {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = null;
        }
    }

    private static final class PaintFlags {
        static final int VALUE = Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG;

        private PaintFlags() {
        }
    }
}
