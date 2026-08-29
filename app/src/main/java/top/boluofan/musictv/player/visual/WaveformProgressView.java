package top.boluofan.musictv.player.visual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;

import top.boluofan.musictv.player.audio.PlaybackSpectrumBus;

public final class WaveformProgressView extends View {
    public interface OnSeekListener {
        void onSeekRequested(long positionMs);
    }

    private static final int MAX_SPECTRUM_BINS = 128;
    private static final int MAX_BARS = 96;
    private static final long DEFAULT_SEEK_STEP_MS = 10_000L;
    private static final float SPECTRUM_RESPONSE_TIME_MS = 40f;

    private final Paint playedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint remainingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] spectrum = new float[MAX_SPECTRUM_BINS];
    private final float[] targetSpectrum = new float[MAX_SPECTRUM_BINS];
    private final float[] fallbackHeights = new float[MAX_BARS];
    private final char[] currentTimeChars = new char[24];
    private final char[] remainingTimeChars = new char[25];
    private final PlaybackSpectrumBus.Snapshot spectrumSnapshot =
            new PlaybackSpectrumBus.Snapshot();

    private final float density;
    private final float barWidth;
    private final float barGap;
    private final float minBarHeight;
    private final float cornerRadius;
    private final float horizontalPadding;
    private final float verticalPadding;
    private final float timeGap;
    private final float focusStrokeWidth;

    private int spectrumCount;
    private int barCount;
    private long positionMs;
    private long durationMs;
    private long progressAnchorUptimeMs;
    private long previousFrameUptimeMs;
    private long displayedPositionSecond = Long.MIN_VALUE;
    private long displayedRemainingSecond = Long.MIN_VALUE;
    private int currentTimeLength;
    private int remainingTimeLength;
    private OnSeekListener onSeekListener;
    private boolean playing;
    private boolean animationEnabled;
    private PlaybackSpectrumBus spectrumSource = PlaybackSpectrumBus.getShared();
    private long displayedSpectrumGeneration = Long.MIN_VALUE;

    public WaveformProgressView(Context context) {
        this(context, null);
    }

    public WaveformProgressView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveformProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        barWidth = dp(4f);
        barGap = dp(3f);
        minBarHeight = dp(3f);
        cornerRadius = dp(2f);
        horizontalPadding = dp(14f);
        verticalPadding = dp(10f);
        timeGap = dp(8f);
        focusStrokeWidth = dp(2f);

        playedPaint.setColor(Color.WHITE);
        remainingPaint.setColor(0x66FFFFFF);
        textPaint.setColor(0xE6FFFFFF);
        textPaint.setTextSize(sp(13f));
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        focusPaint.setColor(0xCCFFFFFF);
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(focusStrokeWidth);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setProgress(0L, 0L);
    }

    public void setOnSeekListener(OnSeekListener listener) {
        onSeekListener = listener;
    }

    public void setProgress(long positionMs, long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        this.positionMs = clampSeekPosition(positionMs, this.durationMs);
        progressAnchorUptimeMs = SystemClock.uptimeMillis();
        updateTimeLabels(this.positionMs);
        invalidate();
    }

    public void setPlaying(boolean playing) {
        if (this.playing == playing) return;
        long now = SystemClock.uptimeMillis();
        positionMs = interpolatedPosition(
                positionMs,
                durationMs,
                now - progressAnchorUptimeMs,
                this.playing
        );
        progressAnchorUptimeMs = now;
        this.playing = playing;
        previousFrameUptimeMs = 0L;
        postInvalidateOnAnimation();
    }

    public void setAnimationEnabled(boolean enabled) {
        if (animationEnabled == enabled) return;
        animationEnabled = enabled;
        previousFrameUptimeMs = 0L;
        if (enabled) postInvalidateOnAnimation();
    }

    public long getPositionMs() {
        return interpolatedPosition(
                positionMs,
                durationMs,
                SystemClock.uptimeMillis() - progressAnchorUptimeMs,
                playing
        );
    }

    public void setSpectrum(float[] values) {
        setSpectrum(values, values == null ? 0 : values.length);
    }

    public void setSpectrum(float[] values, int count) {
        spectrumSource = null;
        if (values == null || count <= 0) {
            spectrumCount = 0;
            java.util.Arrays.fill(spectrum, 0f);
            java.util.Arrays.fill(targetSpectrum, 0f);
            invalidate();
            return;
        }
        int copiedCount = Math.min(Math.min(count, values.length), MAX_SPECTRUM_BINS);
        boolean seedSpectrum = spectrumCount == 0;
        for (int index = 0; index < copiedCount; index++) {
            float target = clampUnit(values[index]);
            targetSpectrum[index] = target;
            if (seedSpectrum) spectrum[index] = target;
        }
        spectrumCount = copiedCount;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(dp(520f));
        int desiredHeight = Math.round(dp(82f));
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float availableWidth = Math.max(0f, width - getPaddingLeft() - getPaddingRight() - 2f * horizontalPadding);
        barCount = Math.min(MAX_BARS, Math.max(1, (int) ((availableWidth + barGap) / (barWidth + barGap))));
        for (int index = 0; index < barCount; index++) {
            float waveA = (float) Math.sin(index * 0.47f) * 0.18f;
            float waveB = (float) Math.sin(index * 0.13f + 1.7f) * 0.14f;
            fallbackHeights[index] = clampUnit(0.28f + waveA + waveB);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long frameTimeMs = SystemClock.uptimeMillis();
        long framePositionMs = interpolatedPosition(
                positionMs,
                durationMs,
                frameTimeMs - progressAnchorUptimeMs,
                playing
        );
        updateTimeLabels(framePositionMs);
        readLatestSpectrum();
        long frameDeltaMs = previousFrameUptimeMs == 0L
                ? 16L : Math.max(0L, Math.min(100L, frameTimeMs - previousFrameUptimeMs));
        previousFrameUptimeMs = frameTimeMs;
        if (animationEnabled && playing) advanceSpectrumFrame(frameDeltaMs);
        else if (!playing) snapSpectrumToTarget();
        float left = getPaddingLeft() + horizontalPadding;
        float right = getWidth() - getPaddingRight() - horizontalPadding;
        float top = getPaddingTop() + verticalPadding;
        float textBaseline = getHeight() - getPaddingBottom() - verticalPadding;
        float waveformBottom = textBaseline - textPaint.getTextSize() - timeGap;
        float waveformHeight = Math.max(minBarHeight, waveformBottom - top);
        float progressFraction = durationMs > 0L
                ? (float) framePositionMs / (float) durationMs : 0f;
        float progressX = left + (right - left) * progressFraction;

        for (int index = 0; index < barCount; index++) {
            float normalizedHeight = spectrumCount > 0
                    ? spectrum[(index * spectrumCount) / barCount]
                    : fallbackHeights[index];
            float height = minBarHeight + normalizedHeight * (waveformHeight - minBarHeight);
            float barLeft = left + index * (barWidth + barGap);
            if (barLeft + barWidth > right) {
                break;
            }
            canvas.drawRoundRect(
                    barLeft,
                    waveformBottom - height,
                    barLeft + barWidth,
                    waveformBottom,
                    cornerRadius,
                    cornerRadius,
                    barLeft + barWidth * 0.5f <= progressX ? playedPaint : remainingPaint
            );
        }

        canvas.drawText(currentTimeChars, 0, currentTimeLength, left, textBaseline, textPaint);
        float remainingWidth = textPaint.measureText(remainingTimeChars, 0, remainingTimeLength);
        canvas.drawText(
                remainingTimeChars,
                0,
                remainingTimeLength,
                right - remainingWidth,
                textBaseline,
                textPaint
        );

        if (isFocused()) {
            float halfStroke = focusStrokeWidth * 0.5f;
            canvas.drawRoundRect(
                    halfStroke,
                    halfStroke,
                    getWidth() - halfStroke,
                    getHeight() - halfStroke,
                    dp(12f),
                    dp(12f),
                    focusPaint
            );
        }
        if (animationEnabled && playing) postInvalidateOnAnimation();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        long currentPositionMs = getPositionMs();
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            requestSeek(seekBackwardTarget(currentPositionMs, DEFAULT_SEEK_STEP_MS));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            requestSeek(seekForwardTarget(
                    currentPositionMs, durationMs, DEFAULT_SEEK_STEP_MS));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    static long clampSeekPosition(long requestedPositionMs, long durationMs) {
        long sanitizedDuration = Math.max(0L, durationMs);
        if (requestedPositionMs <= 0L || sanitizedDuration == 0L) {
            return 0L;
        }
        return Math.min(requestedPositionMs, sanitizedDuration);
    }

    static long seekBackwardTarget(long positionMs, long stepMs) {
        return positionMs <= stepMs ? 0L : positionMs - stepMs;
    }

    static long seekForwardTarget(long positionMs, long durationMs, long stepMs) {
        long clampedPosition = clampSeekPosition(positionMs, durationMs);
        long remaining = Math.max(0L, durationMs - clampedPosition);
        return stepMs >= remaining ? durationMs : clampedPosition + stepMs;
    }

    static long interpolatedPosition(
            long anchorPositionMs,
            long durationMs,
            long elapsedMs,
            boolean playing
    ) {
        long clampedAnchor = clampSeekPosition(anchorPositionMs, durationMs);
        if (!playing || elapsedMs <= 0L) return clampedAnchor;
        long remaining = Math.max(0L, durationMs - clampedAnchor);
        return elapsedMs >= remaining ? durationMs : clampedAnchor + elapsedMs;
    }

    static float interpolateSpectrum(float current, float target, long frameDeltaMs) {
        float response = 1f - (float) Math.exp(
                -Math.max(0L, frameDeltaMs) / SPECTRUM_RESPONSE_TIME_MS
        );
        return current + (target - current) * response;
    }

    static int writeTime(char[] target, long totalSeconds, boolean negativePrefix) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainingSeconds = seconds % 60L;
        int index = 0;
        if (negativePrefix) {
            target[index++] = '-';
        }
        if (hours > 0L) {
            index = writeNumber(target, index, hours);
            target[index++] = ':';
            target[index++] = (char) ('0' + minutes / 10L);
            target[index++] = (char) ('0' + minutes % 10L);
        } else {
            index = writeNumber(target, index, minutes);
        }
        target[index++] = ':';
        target[index++] = (char) ('0' + remainingSeconds / 10L);
        target[index++] = (char) ('0' + remainingSeconds % 10L);
        return index;
    }

    private static int writeNumber(char[] target, int offset, long value) {
        int digits = decimalDigits(value);
        int end = offset + digits;
        for (int index = end - 1; index >= offset; index--) {
            target[index] = (char) ('0' + value % 10L);
            value /= 10L;
        }
        return end;
    }

    private static int decimalDigits(long value) {
        int digits = 1;
        while (value >= 10L) {
            value /= 10L;
            digits++;
        }
        return digits;
    }

    private void requestSeek(long requestedPositionMs) {
        if (durationMs <= 0L) {
            return;
        }
        long targetPositionMs = clampSeekPosition(requestedPositionMs, durationMs);
        setProgress(targetPositionMs, durationMs);
        if (onSeekListener != null) {
            onSeekListener.onSeekRequested(targetPositionMs);
        }
    }

    private void readLatestSpectrum() {
        if (spectrumSource == null) return;
        spectrumSource.copyLatest(targetSpectrum, spectrumSnapshot);
        if (displayedSpectrumGeneration == spectrumSnapshot.generation) return;
        displayedSpectrumGeneration = spectrumSnapshot.generation;
        for (int index = 0; index < PlaybackSpectrumBus.BAND_COUNT; index++) {
            targetSpectrum[index] = clampUnit(targetSpectrum[index]);
        }
        if (spectrumCount == 0) {
            spectrumCount = PlaybackSpectrumBus.BAND_COUNT;
            snapSpectrumToTarget();
        }
    }

    private void advanceSpectrumFrame(long frameDeltaMs) {
        for (int index = 0; index < spectrumCount; index++) {
            spectrum[index] = interpolateSpectrum(
                    spectrum[index],
                    targetSpectrum[index],
                    frameDeltaMs
            );
        }
    }

    private void snapSpectrumToTarget() {
        System.arraycopy(targetSpectrum, 0, spectrum, 0, spectrumCount);
    }

    private void updateTimeLabels(long displayedPositionMs) {
        long positionSecond = displayedPositionMs / 1_000L;
        long remainingSecond = Math.max(0L, durationMs - displayedPositionMs) / 1_000L;
        if (positionSecond != displayedPositionSecond) {
            currentTimeLength = writeTime(currentTimeChars, positionSecond, false);
            displayedPositionSecond = positionSecond;
        }
        if (remainingSecond != displayedRemainingSecond) {
            remainingTimeLength = writeTime(remainingTimeChars, remainingSecond, true);
            displayedRemainingSecond = remainingSecond;
        }
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private static float clampUnit(float value) {
        if (Float.isNaN(value) || value <= 0f) {
            return 0f;
        }
        return Math.min(1f, value);
    }
}
