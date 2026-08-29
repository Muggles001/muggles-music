package top.boluofan.musictv.player.visual;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import top.boluofan.musictv.player.audio.PlaybackSpectrumBus;

public final class AsciiRibbonView extends View {
    private static final long PARTICLE_FRAME_NANOS = 33_333_333L;
    private static final long LIVE_SPECTRUM_WINDOW_NANOS = 750_000_000L;
    private static final int ATLAS_SLOT_SIZE = 48;
    private static final int ATLAS_COLUMNS = 3;
    private static final float COMPAT_RENDER_SCALE = 0.5f;

    private final Paint vertexPaint = new Paint();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    @Nullable private AsciiRibbonField workerField;
    private final float[] spectrum = new float[PlaybackSpectrumBus.BAND_COUNT];
    private final int[] bucketColors = new int[AsciiRibbonField.BUCKET_COUNT];
    private final PlaybackSpectrumBus.Snapshot spectrumSnapshot =
            new PlaybackSpectrumBus.Snapshot();
    private final Bitmap glyphAtlas;
    private final Runnable particleTicker = this::runParticleTick;

    private PlaybackSpectrumBus spectrumSource = PlaybackSpectrumBus.getShared();
    @Nullable private ExecutorService renderExecutor;
    @Nullable private GlyphVertexBatch displayedBatch;
    @Nullable private GlyphVertexBatch workerBatch;
    private Bitmap compatibilityBitmap;
    private Canvas compatibilityCanvas;
    private int ambientColor = Color.rgb(132, 94, 78);
    private boolean playing;
    private boolean animationEnabled = true;
    private boolean attached;
    private float cellWidth;
    private float cellHeight;
    private float animationSeconds;
    private float motion;
    private float level;
    private long previousFrameNanos;
    private long previousParticleFrameNanos;
    private long manualSpectrumTimestampNanos;
    private int targetColumns;
    private int targetRows;
    private int renderGeneration;
    private boolean renderInFlight;
    private boolean particleTickScheduled;

    public AsciiRibbonView(Context context) {
        this(context, null);
    }

    public AsciiRibbonView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AsciiRibbonView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        glyphAtlas = createGlyphAtlas();
        vertexPaint.setShader(new BitmapShader(glyphAtlas,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        vertexPaint.setFilterBitmap(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        updateBucketColors();
    }

    public void setPlaying(boolean isPlaying) {
        if (playing == isPlaying) return;
        playing = isPlaying;
        if (playing && motion < 0.08f) motion = 0.08f;
        previousFrameNanos = 0L;
        previousParticleFrameNanos = 0L;
        scheduleParticleTick(0L);
    }

    public void setAnimationEnabled(boolean enabled) {
        if (animationEnabled == enabled) return;
        animationEnabled = enabled;
        previousFrameNanos = 0L;
        previousParticleFrameNanos = 0L;
        if (enabled) scheduleParticleTick(0L);
    }

    public void setAmbientColor(int color) {
        if (ambientColor == color) return;
        ambientColor = color;
        updateBucketColors();
        previousParticleFrameNanos = 0L;
        scheduleParticleTick(0L);
    }

    public void setSpectrum(float[] bands, float overallLevel) {
        spectrumSource = null;
        if (bands != null) {
            int copied = Math.min(bands.length, spectrum.length);
            System.arraycopy(bands, 0, spectrum, 0, copied);
            for (int band = copied; band < spectrum.length; band++) spectrum[band] = 0f;
        }
        level = clamp01(overallLevel);
        manualSpectrumTimestampNanos = System.nanoTime();
        previousParticleFrameNanos = 0L;
        scheduleParticleTick(0L);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        workerField = new AsciiRibbonField();
        renderExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ascii-ribbon-renderer");
            thread.setDaemon(true);
            return thread;
        });
        previousFrameNanos = 0L;
        previousParticleFrameNanos = 0L;
        scheduleParticleTick(0L);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        renderGeneration++;
        renderInFlight = false;
        workerField = null;
        particleTickScheduled = false;
        removeCallbacks(particleTicker);
        ExecutorService executor = renderExecutor;
        renderExecutor = null;
        if (executor != null) executor.shutdownNow();
        previousFrameNanos = 0L;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        targetColumns = AsciiRibbonField.recommendedColumnCount(width);
        cellWidth = width / (float) targetColumns;
        targetRows = Math.max(48, Math.round(height / cellWidth));
        cellHeight = height / (float) targetRows;
        renderGeneration++;
        renderInFlight = false;
        previousParticleFrameNanos = 0L;
        displayedBatch = null;
        workerBatch = new GlyphVertexBatch(
                targetColumns * targetRows,
                ATLAS_SLOT_SIZE,
                ATLAS_COLUMNS
        );

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (compatibilityBitmap != null) compatibilityBitmap.recycle();
            int bitmapWidth = Math.max(1, Math.round(width * COMPAT_RENDER_SCALE));
            int bitmapHeight = Math.max(1, Math.round(height * COMPAT_RENDER_SCALE));
            compatibilityBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                    Bitmap.Config.ARGB_8888);
            compatibilityCanvas = new Canvas(compatibilityBitmap);
        }
        scheduleParticleTick(0L);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!animationEnabled || targetColumns == 0) return;

        boolean compatibilityPath = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && canvas.isHardwareAccelerated();
        GlyphVertexBatch batch = displayedBatch;
        if (batch != null && compatibilityPath) {
            compatibilityCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            int compatibilitySave = compatibilityCanvas.save();
            compatibilityCanvas.scale(COMPAT_RENDER_SCALE, COMPAT_RENDER_SCALE);
            drawBatch(compatibilityCanvas, batch);
            compatibilityCanvas.restoreToCount(compatibilitySave);
            int saveCount = canvas.save();
            canvas.scale(getWidth() / (float) compatibilityBitmap.getWidth(),
                    getHeight() / (float) compatibilityBitmap.getHeight());
            canvas.drawBitmap(compatibilityBitmap, 0f, 0f, bitmapPaint);
            canvas.restoreToCount(saveCount);
        } else if (batch != null) {
            drawBatch(canvas, batch);
        }
    }

    private void runParticleTick() {
        particleTickScheduled = false;
        if (!attached || !animationEnabled || targetColumns == 0 || workerBatch == null) return;
        long now = System.nanoTime();
        float deltaSeconds = previousFrameNanos == 0L
                ? 0f : Math.min(0.1f, (now - previousFrameNanos) / 1_000_000_000f);
        previousFrameNanos = now;
        float targetMotion = playing ? 1f : 0f;
        float response = playing ? 0.18f : 0.07f;
        motion += (targetMotion - motion) * response;
        animationSeconds += deltaSeconds * (0.16f + motion * 0.84f);
        boolean hasLiveSpectrum = readSpectrum(now);
        if (!hasLiveSpectrum) generateFallbackSpectrum();
        requestParticleFrame(now);
        if (playing || motion > 0.015f || renderInFlight) {
            scheduleParticleTick(33L);
        }
    }

    private void scheduleParticleTick(long delayMillis) {
        if (!attached || !animationEnabled || particleTickScheduled) return;
        particleTickScheduled = true;
        if (delayMillis == 0L) postOnAnimation(particleTicker);
        else postDelayed(particleTicker, delayMillis);
    }

    private void requestParticleFrame(long now) {
        ExecutorService executor = renderExecutor;
        AsciiRibbonField field = workerField;
        GlyphVertexBatch batch = workerBatch;
        if (executor == null || field == null || batch == null || renderInFlight
                || previousParticleFrameNanos != 0L
                && now - previousParticleFrameNanos < PARTICLE_FRAME_NANOS) {
            return;
        }
        renderInFlight = true;
        previousParticleFrameNanos = now;
        int generation = renderGeneration;
        int columns = targetColumns;
        int rows = targetRows;
        float frameCellWidth = cellWidth;
        float frameCellHeight = cellHeight;
        float[] frameSpectrum = spectrum.clone();
        int[] frameColors = bucketColors.clone();
        float frameTime = animationSeconds;
        float frameLevel = level;
        float frameMotion = 0.2f + motion * 0.8f;
        try {
            executor.execute(() -> {
                if (field.getColumns() != columns || field.getRows() != rows) {
                    field.resize(columns, rows);
                }
                field.render(frameTime, frameSpectrum, frameLevel, frameMotion);
                buildVertexBatch(batch, field, frameCellWidth, frameCellHeight,
                        frameColors);
                post(() -> finishParticleFrame(generation, batch));
            });
        } catch (RejectedExecutionException ignored) {
            renderInFlight = false;
        }
    }

    private void finishParticleFrame(int generation, GlyphVertexBatch completedBatch) {
        if (generation != renderGeneration) return;
        renderInFlight = false;
        if (!attached) return;
        GlyphVertexBatch previousBatch = displayedBatch;
        displayedBatch = completedBatch;
        workerBatch = previousBatch != null ? previousBatch : new GlyphVertexBatch(
                targetColumns * targetRows,
                ATLAS_SLOT_SIZE,
                ATLAS_COLUMNS
        );
        postInvalidateOnAnimation();
    }

    private static void buildVertexBatch(
            GlyphVertexBatch vertexBatch,
            AsciiRibbonField field,
            float cellWidth,
            float cellHeight,
            int[] bucketColors
    ) {
        vertexBatch.reset();
        int columns = field.getColumns();
        for (int bucket = 0; bucket < AsciiRibbonField.BUCKET_COUNT; bucket++) {
            char[] characters = field.getBucketCharacters(bucket);
            int bucketOffset = field.getBucketOffset(bucket);
            float widthFraction = AsciiRibbonField.glyphWidthFraction(bucket);
            float heightFraction = AsciiRibbonField.glyphHeightFraction(bucket);
            for (int row = 0; row < field.getRows(); row++) {
                int first = field.getRowFirstColumn(bucket, row);
                int last = field.getRowLastColumn(bucket, row);
                if (last < first) continue;
                int rowOffset = bucketOffset + row * columns;
                for (int column = first; column <= last; column++) {
                    char glyph = characters[rowOffset + column];
                    if (glyph != ' ') {
                        int cell = row * columns + column;
                        float depthScale = field.getCellGlyphScale(cell);
                        vertexBatch.add(glyph, column, row, cellWidth, cellHeight,
                                widthFraction * depthScale, heightFraction * depthScale,
                                bucketColors[bucket], field.getCellAlpha(cell));
                    }
                }
            }
        }
    }

    private void drawBatch(Canvas canvas, GlyphVertexBatch vertexBatch) {
        int vertexValueCount = vertexBatch.getVertexValueCount();
        if (vertexValueCount == 0) return;
        // API 29 is the first release that hardware-accelerates drawVertices; vertexCount is
        // the number of float values, not the number of logical vertices.
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, vertexValueCount,
                vertexBatch.getVertices(), 0,
                vertexBatch.getTextureCoordinates(), 0,
                vertexBatch.getColors(), 0,
                null, 0, 0, vertexPaint);
    }

    private boolean readSpectrum(long now) {
        if (spectrumSource != null) {
            spectrumSource.copyLatest(spectrum, spectrumSnapshot);
            if (spectrumSnapshot.timestampNanos > 0L
                    && now - spectrumSnapshot.timestampNanos <= LIVE_SPECTRUM_WINDOW_NANOS) {
                level = spectrumSnapshot.level;
                return true;
            }
            return false;
        }
        return manualSpectrumTimestampNanos > 0L
                && now - manualSpectrumTimestampNanos <= LIVE_SPECTRUM_WINDOW_NANOS;
    }

    private void generateFallbackSpectrum() {
        float time = animationSeconds;
        float fallbackLevel = playing ? 0.2f : 0.06f * motion;
        for (int band = 0; band < spectrum.length; band++) {
            float envelope = 1f - band / (float) (spectrum.length * 1.35f);
            float wave = 0.5f + 0.5f * (float) Math.sin(time * 2.1f + band * 0.73f);
            float target = fallbackLevel * envelope * (0.42f + wave * 0.58f);
            spectrum[band] += (target - spectrum[band]) * 0.08f;
        }
        level += (fallbackLevel - level) * 0.08f;
    }

    private Bitmap createGlyphAtlas() {
        int rows = (GlyphVertexBatch.glyphCountInAtlas() + ATLAS_COLUMNS - 1) / ATLAS_COLUMNS;
        Bitmap atlas = Bitmap.createBitmap(ATLAS_COLUMNS * ATLAS_SLOT_SIZE,
                rows * ATLAS_SLOT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas atlasCanvas = new Canvas(atlas);
        Paint atlasPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        atlasPaint.setColor(Color.WHITE);
        atlasPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        atlasPaint.setTextAlign(Paint.Align.CENTER);
        atlasPaint.setTextSize(44f);
        atlasPaint.setTextScaleX(1.3f);
        Paint.FontMetrics metrics = atlasPaint.getFontMetrics();
        float baselineOffset = -(metrics.ascent + metrics.descent) * 0.5f;
        for (int index = 0; index < GlyphVertexBatch.glyphCountInAtlas(); index++) {
            int column = index % ATLAS_COLUMNS;
            int row = index / ATLAS_COLUMNS;
            atlasCanvas.drawText(String.valueOf(GlyphVertexBatch.glyphAt(index)),
                    (column + 0.5f) * ATLAS_SLOT_SIZE,
                    (row + 0.5f) * ATLAS_SLOT_SIZE + baselineOffset, atlasPaint);
        }
        return atlas;
    }

    private void updateBucketColors() {
        int red = Color.red(ambientColor);
        int green = Color.green(ambientColor);
        int blue = Color.blue(ambientColor);
        bucketColors[AsciiRibbonField.BUCKET_DIM] = blend(red, green, blue, 45);
        bucketColors[AsciiRibbonField.BUCKET_MID] = blend(red, green, blue, 72);
        bucketColors[AsciiRibbonField.BUCKET_BRIGHT] = blend(red, green, blue, 92);
    }

    private static int blend(int red, int green, int blue, int whitePercent) {
        int colorPercent = 100 - whitePercent;
        return Color.rgb((red * colorPercent + 255 * whitePercent) / 100,
                (green * colorPercent + 255 * whitePercent) / 100,
                (blue * colorPercent + 255 * whitePercent) / 100);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }
}
