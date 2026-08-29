package top.boluofan.musictv;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.player.visual.SpatialLyricsGeometry;
import top.boluofan.musictv.player.visual.SpatialLyricsView;
import top.boluofan.musictv.player.lyrics.LyricTimeline;

@RunWith(AndroidJUnit4.class)
public class LyricVisualInstrumentedTest {
    private static final int SCENE_WIDTH = 1_920;
    private static final int VIEW_WIDTH = 960;
    private static final int VIEW_HEIGHT = 1_080;
    private static final float PROJECTION_CENTER_X = 1_920f;
    private static final float PROJECTION_CENTER_Y = 612f;

    @Test
    public void spatialViewRendersSevenProjectedRowsWithDepthOpacity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SpatialLyricsView lyricsView = new SpatialLyricsView(context);
            List<LyricTimeline.Line> lyrics = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                lyrics.add(new LyricTimeline.Line(index * 1_000L, "██████████████"));
            }
            lyricsView.setLyrics(lyrics);
            lyricsView.setFocusFraction(0.50f);
            lyricsView.setCurrentIndex(3, false);

            layoutInScene(context, lyricsView);
            Bitmap frame = Bitmap.createBitmap(
                    VIEW_WIDTH,
                    VIEW_HEIGHT,
                    Bitmap.Config.ARGB_8888
            );
            lyricsView.draw(new Canvas(frame));

            SpatialLyricsGeometry.Pose active = poseAt(0f, VIEW_WIDTH, VIEW_HEIGHT);
            SpatialLyricsGeometry.Pose firstDepth = poseAt(1f, VIEW_WIDTH, VIEW_HEIGHT);
            SpatialLyricsGeometry.Pose secondDepth = poseAt(2f, VIEW_WIDTH, VIEW_HEIGHT);
            int activeAlpha = maxAlphaNear(frame, active.screenX, active.screenY, 12, 14);
            int firstDepthAlpha = maxAlphaNear(
                    frame,
                    firstDepth.screenX,
                    firstDepth.screenY,
                    12,
                    14
            );
            int secondDepthAlpha = maxAlphaNear(
                    frame,
                    secondDepth.screenX,
                    secondDepth.screenY,
                    12,
                    14
            );

            assertTrue(activeAlpha > firstDepthAlpha);
            assertTrue(firstDepthAlpha > secondDepthAlpha);
            assertTrue(secondDepthAlpha > 0);
            assertEquals(7, countOpaqueBandsNearX(frame, Math.round(active.screenX), 6));
        });
    }

    @Test
    public void repeatedGlyphLinesKeepStraightEdgesOnTheSharedPlane() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SpatialLyricsView lyricsView = new SpatialLyricsView(context);
            List<LyricTimeline.Line> lyrics = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                lyrics.add(new LyricTimeline.Line(index * 1_000L, "██████████████"));
            }
            lyricsView.setLyrics(lyrics);
            lyricsView.setFocusFraction(0.50f);
            lyricsView.setCurrentIndex(3, false);

            layoutInScene(context, lyricsView);
            Bitmap frame = Bitmap.createBitmap(
                    VIEW_WIDTH,
                    VIEW_HEIGHT,
                    Bitmap.Config.ARGB_8888
            );
            lyricsView.draw(new Canvas(frame));
            float[] vanishingPoint = horizontalVanishingPoint();

            for (int distance = -3; distance <= 3; distance++) {
                float[] lineQuad = projectedLineQuad(distance, 800f, 64f);
                EdgeFit topEdge = fitEdge(frame, lineQuad, 46, true);
                EdgeFit bottomEdge = fitEdge(frame, lineQuad, 46, false);
                String label = "distance=" + distance + ", ";
                assertTrue(label + edgeSummary(topEdge), topEdge.samples > 100);
                assertTrue(label + edgeSummary(bottomEdge), bottomEdge.samples > 100);
                assertEquals(
                        label + edgeSummary(topEdge),
                        vanishingPoint[1],
                        topEdge.valueAt(vanishingPoint[0]),
                        24f
                );
                assertEquals(
                        label + edgeSummary(bottomEdge),
                        vanishingPoint[1],
                        bottomEdge.valueAt(vanishingPoint[0]),
                        24f
                );
                assertStraight(label, topEdge);
                assertStraight(label, bottomEdge);
            }
        });
    }

    @Test
    public void shortPrimaryAndLongTranslationRenderAsThreePhysicalRows() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SpatialLyricsView lyricsView = new SpatialLyricsView(context);
            lyricsView.setLyrics(Collections.singletonList(new LyricTimeline.Line(
                    0L,
                    "████████\n" + repeatToken("████", 40)
            )));
            lyricsView.setCurrentIndex(0, false);

            layoutInScene(context, lyricsView);
            Bitmap frame = Bitmap.createBitmap(
                    VIEW_WIDTH,
                    VIEW_HEIGHT,
                    Bitmap.Config.ARGB_8888
            );
            lyricsView.draw(new Canvas(frame));

            int[] bounds = opaqueBounds(frame);
            assertTrue(bounds[0] >= 0);
            assertTrue(bounds[1] >= 0);
            assertTrue(bounds[2] < VIEW_WIDTH);
            assertTrue(bounds[3] < VIEW_HEIGHT);
            assertTrue(bounds[2] - bounds[0] > VIEW_WIDTH / 2);
            assertTrue(bounds[3] - bounds[1] > 0);
            assertEquals(3, physicalLineCount(lyricsView));
        });
    }

    @Test
    public void wrappedPrimaryAndLongTranslationRenderAsFourPhysicalRows() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SpatialLyricsView lyricsView = new SpatialLyricsView(context);
            lyricsView.setLyrics(Collections.singletonList(new LyricTimeline.Line(
                    0L,
                    repeatToken("████", 40) + "\n" + repeatToken("████", 40)
            )));
            lyricsView.setCurrentIndex(0, false);

            layoutInScene(context, lyricsView);
            Bitmap frame = Bitmap.createBitmap(
                    VIEW_WIDTH,
                    VIEW_HEIGHT,
                    Bitmap.Config.ARGB_8888
            );
            lyricsView.draw(new Canvas(frame));

            assertEquals(4, physicalLineCount(lyricsView));
        });
    }

    @Test
    public void extremePrimaryTextUsesTwoRowsWithoutEllipsis() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SpatialLyricsView shortView = new SpatialLyricsView(context);
            shortView.setLyrics(Collections.singletonList(new LyricTimeline.Line(
                    0L,
                    "████████"
            )));
            shortView.setCurrentIndex(0, false);
            layoutInScene(context, shortView);
            Bitmap shortFrame = Bitmap.createBitmap(
                    VIEW_WIDTH,
                    VIEW_HEIGHT,
                    Bitmap.Config.ARGB_8888
            );
            shortView.draw(new Canvas(shortFrame));

            SpatialLyricsView lyricsView = new SpatialLyricsView(context);
            lyricsView.setLyrics(Collections.singletonList(new LyricTimeline.Line(
                    0L,
                    repeatToken("████ ", 80)
            )));
            lyricsView.setCurrentIndex(0, false);

            layoutInScene(context, lyricsView);
            Bitmap frame = Bitmap.createBitmap(
                    VIEW_WIDTH,
                    VIEW_HEIGHT,
                    Bitmap.Config.ARGB_8888
            );
            lyricsView.draw(new Canvas(frame));

            int[] shortSlice = opaqueBoundsInVerticalWindow(
                    shortFrame, 509, 8, 0, VIEW_HEIGHT - 1
            );
            int[] longSlice = opaqueBoundsInVerticalWindow(frame, 509, 8, 0, VIEW_HEIGHT - 1);
            int[] longBounds = opaqueBounds(frame);
            int lowerHalfTop = (longBounds[1] + longBounds[3]) / 2;
            int[] secondLineBounds = opaqueBoundsInYRange(
                    frame,
                    lowerHalfTop,
                    longBounds[3]
            );
            int tailInkHeight = opaqueHeightInRect(
                    frame,
                    Math.max(secondLineBounds[0], secondLineBounds[2] - 24),
                    secondLineBounds[2],
                    lowerHalfTop,
                    longBounds[3]
            );

            assertTrue(boundsHeight(longSlice) > boundsHeight(shortSlice) * 1.5f);
            assertTrue(tailInkHeight > boundsHeight(shortSlice) * 0.5f);
        });
    }

    @Test
    public void consecutiveTwoRowBlocksKeepVisibleSeparation() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SpatialLyricsView lyricsView = new SpatialLyricsView(context);
            String twoRowText = repeatToken("████ ", 26);
            List<LyricTimeline.Line> lyrics = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                lyrics.add(new LyricTimeline.Line(index * 1_000L, twoRowText));
            }
            lyricsView.setLyrics(lyrics);
            lyricsView.setCurrentIndex(1, false);

            layoutInScene(context, lyricsView);
            Bitmap frame = Bitmap.createBitmap(
                    VIEW_WIDTH,
                    VIEW_HEIGHT,
                    Bitmap.Config.ARGB_8888
            );
            lyricsView.draw(new Canvas(frame));

            float blockHeight = twoRowBlockHeight(twoRowText);
            float[] blockHeights = {blockHeight, blockHeight, blockHeight};
            int[][] blockBounds = new int[3][];
            int maximumInnerGap = 0;
            for (int index = 0; index < blockBounds.length; index++) {
                float centerY = SpatialLyricsGeometry.blockCenterY(
                        index,
                        1f,
                        VIEW_HEIGHT * 0.5f,
                        VIEW_HEIGHT,
                        blockHeights
                );
                int top = Math.round(centerY - blockHeight * 0.5f - 4f);
                int middle = Math.round(centerY);
                int bottom = Math.round(centerY + blockHeight * 0.5f + 4f);
                blockBounds[index] = opaqueBoundsInVerticalWindow(
                        frame, 509, 8, top, bottom);
                int[] upperLine = opaqueBoundsInVerticalWindow(
                        frame, 509, 8, top, middle - 1);
                int[] lowerLine = opaqueBoundsInVerticalWindow(
                        frame, 509, 8, middle, bottom);
                maximumInnerGap = Math.max(
                        maximumInnerGap, verticalGapBetween(upperLine, lowerLine));
            }

            int firstOuterGap = verticalGapBetween(blockBounds[0], blockBounds[1]);
            int secondOuterGap = verticalGapBetween(blockBounds[1], blockBounds[2]);

            assertTrue(firstOuterGap >= 40);
            assertTrue(secondOuterGap >= 40);
            assertTrue(firstOuterGap >= maximumInnerGap + 24);
            assertTrue(secondOuterGap >= maximumInnerGap + 24);
        });
    }

    @Test
    public void activeOverlayRaisesContrastWithoutChangingGlyphBounds() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SpatialLyricsView lyricsView = new SpatialLyricsView(context);
            String text = "████████████";
            lyricsView.setLyrics(java.util.Arrays.asList(
                    new LyricTimeline.Line(0L, text),
                    new LyricTimeline.Line(1_000L, text)
            ));
            lyricsView.setCurrentIndex(0, false);

            layoutInScene(context, lyricsView);
            Bitmap frame = Bitmap.createBitmap(
                    VIEW_WIDTH,
                    VIEW_HEIGHT,
                    Bitmap.Config.ARGB_8888
            );
            lyricsView.draw(new Canvas(frame));

            int[] activeBounds = opaqueBoundsNearLine(
                    frame,
                    projectedLineQuad(0f, 700f, 64f),
                    44
            );
            int[] nextBounds = opaqueBoundsNearLine(
                    frame,
                    projectedLineQuad(1f, 700f, 64f),
                    44
            );
            SpatialLyricsGeometry.Pose active = poseAt(0f, VIEW_WIDTH, VIEW_HEIGHT);
            SpatialLyricsGeometry.Pose next = poseAt(1f, VIEW_WIDTH, VIEW_HEIGHT);
            int activeAlpha = maxAlphaNear(frame, active.screenX, active.screenY, 12, 18);
            int nextAlpha = maxAlphaNear(frame, next.screenX, next.screenY, 12, 18);

            assertEquals(activeBounds[2] - activeBounds[0], nextBounds[2] - nextBounds[0], 2);
            assertEquals(activeBounds[3] - activeBounds[1], nextBounds[3] - nextBounds[1], 2);
            assertTrue("active=" + activeAlpha + ", next=" + nextAlpha,
                    activeAlpha - nextAlpha >= 50);
        });
    }

    private static SpatialLyricsGeometry.Pose poseAt(float distance, int width, int height) {
        SpatialLyricsGeometry.Pose pose = new SpatialLyricsGeometry.Pose();
        SpatialLyricsGeometry.compute(
                distance,
                width,
                height,
                PROJECTION_CENTER_X,
                PROJECTION_CENTER_Y,
                0.50f,
                pose
        );
        return pose;
    }

    private static int physicalLineCount(SpatialLyricsView lyricsView) {
        try {
            java.lang.reflect.Field entriesField = SpatialLyricsView.class
                    .getDeclaredField("layoutEntries");
            entriesField.setAccessible(true);
            Object[] entries = (Object[]) entriesField.get(lyricsView);
            Object entry = entries[0];
            java.lang.reflect.Field primaryCount = entry.getClass()
                    .getDeclaredField("primaryLineCount");
            java.lang.reflect.Field translationCount = entry.getClass()
                    .getDeclaredField("translationLineCount");
            primaryCount.setAccessible(true);
            translationCount.setAccessible(true);
            return primaryCount.getInt(entry) + translationCount.getInt(entry);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void layoutInScene(Context context, SpatialLyricsView lyricsView) {
        FrameLayout scene = new FrameLayout(context);
        scene.addView(lyricsView, new FrameLayout.LayoutParams(VIEW_WIDTH, VIEW_HEIGHT));
        scene.measure(
                View.MeasureSpec.makeMeasureSpec(SCENE_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY)
        );
        scene.layout(0, 0, SCENE_WIDTH, VIEW_HEIGHT);
    }

    private static float[] horizontalVanishingPoint() {
        float[] quad = new float[8];
        SpatialLyricsGeometry.populateProjectedPlaneQuad(
                poseAt(0f, VIEW_WIDTH, VIEW_HEIGHT),
                VIEW_WIDTH,
                VIEW_HEIGHT,
                quad
        );
        return lineIntersection(
                quad[0], quad[1], quad[2], quad[3],
                quad[6], quad[7], quad[4], quad[5]
        );
    }

    private static float[] projectedLineQuad(float distance, float width, float height) {
        float[] quad = new float[8];
        SpatialLyricsGeometry.populateProjectedQuad(
                poseAt(distance, VIEW_WIDTH, VIEW_HEIGHT),
                width,
                height,
                quad
        );
        return quad;
    }

    private static float[] lineIntersection(
            float firstX,
            float firstY,
            float firstEndX,
            float firstEndY,
            float secondX,
            float secondY,
            float secondEndX,
            float secondEndY
    ) {
        float firstDeltaX = firstEndX - firstX;
        float firstDeltaY = firstEndY - firstY;
        float secondDeltaX = secondEndX - secondX;
        float secondDeltaY = secondEndY - secondY;
        float denominator = firstDeltaX * secondDeltaY - firstDeltaY * secondDeltaX;
        float parameter = ((secondX - firstX) * secondDeltaY
                - (secondY - firstY) * secondDeltaX) / denominator;
        return new float[]{
                firstX + parameter * firstDeltaX,
                firstY + parameter * firstDeltaY
        };
    }

    private static int maxAlphaNear(
            Bitmap bitmap,
            float centerX,
            float centerY,
            int halfWidth,
            int halfHeight
    ) {
        int maximum = 0;
        int left = Math.max(0, Math.round(centerX) - halfWidth);
        int right = Math.min(bitmap.getWidth() - 1, Math.round(centerX) + halfWidth);
        int top = Math.max(0, Math.round(centerY) - halfHeight);
        int bottom = Math.min(bitmap.getHeight() - 1, Math.round(centerY) + halfHeight);
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                maximum = Math.max(maximum, Color.alpha(bitmap.getPixel(x, y)));
            }
        }
        return maximum;
    }

    private static int countOpaqueBandsNearX(Bitmap bitmap, int centerX, int halfWidth) {
        return opaqueBandsNearX(bitmap, centerX, halfWidth).size();
    }

    private static List<int[]> opaqueBandsNearX(Bitmap bitmap, int centerX, int halfWidth) {
        List<int[]> bands = new ArrayList<>();
        boolean insideBand = false;
        int bandTop = -1;
        int left = Math.max(0, centerX - halfWidth);
        int right = Math.min(bitmap.getWidth() - 1, centerX + halfWidth);
        for (int y = 0; y < bitmap.getHeight(); y++) {
            boolean rowHasInk = false;
            for (int x = left; x <= right; x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    rowHasInk = true;
                    break;
                }
            }
            if (rowHasInk && !insideBand) bandTop = y;
            if (!rowHasInk && insideBand) bands.add(new int[]{bandTop, y - 1});
            insideBand = rowHasInk;
        }
        if (insideBand) bands.add(new int[]{bandTop, bitmap.getHeight() - 1});
        return bands;
    }

    private static int verticalGapBetween(int[] upper, int[] lower) {
        return lower[1] - upper[3] - 1;
    }

    private static int maxAlphaInRect(
            Bitmap bitmap,
            int left,
            int right,
            int top,
            int bottom
    ) {
        int maximum = 0;
        for (int y = Math.max(0, top); y <= Math.min(bitmap.getHeight() - 1, bottom); y++) {
            for (int x = Math.max(0, left); x <= Math.min(bitmap.getWidth() - 1, right); x++) {
                maximum = Math.max(maximum, Color.alpha(bitmap.getPixel(x, y)));
            }
        }
        return maximum;
    }

    private static int opaqueHeightInRect(
            Bitmap bitmap,
            int left,
            int right,
            int top,
            int bottom
    ) {
        int inkTop = bitmap.getHeight();
        int inkBottom = -1;
        for (int y = Math.max(0, top); y <= Math.min(bitmap.getHeight() - 1, bottom); y++) {
            for (int x = Math.max(0, left); x <= Math.min(bitmap.getWidth() - 1, right); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 24) continue;
                inkTop = Math.min(inkTop, y);
                inkBottom = Math.max(inkBottom, y);
            }
        }
        return inkBottom < inkTop ? 0 : inkBottom - inkTop + 1;
    }

    private static int countOpaqueRowBands(Bitmap bitmap) {
        int bands = 0;
        boolean insideBand = false;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            boolean rowHasInk = false;
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    rowHasInk = true;
                    break;
                }
            }
            if (rowHasInk && !insideBand) bands++;
            insideBand = rowHasInk;
        }
        return bands;
    }

    private static int[] opaqueBounds(Bitmap bitmap) {
        int left = bitmap.getWidth();
        int top = bitmap.getHeight();
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 24) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return new int[]{left, top, right, bottom};
    }

    private static int[] opaqueBoundsInVerticalWindow(
            Bitmap bitmap,
            int centerX,
            int halfWidth,
            int startY,
            int endY
    ) {
        int left = bitmap.getWidth();
        int top = bitmap.getHeight();
        int right = -1;
        int bottom = -1;
        int startX = Math.max(0, centerX - halfWidth);
        int endX = Math.min(bitmap.getWidth() - 1, centerX + halfWidth);
        for (int y = Math.max(0, startY); y <= Math.min(bitmap.getHeight() - 1, endY); y++) {
            for (int x = startX; x <= endX; x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 24) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return new int[]{left, top, right, bottom};
    }

    private static int[] opaqueBoundsInYRange(Bitmap bitmap, int startY, int endY) {
        int left = bitmap.getWidth();
        int top = bitmap.getHeight();
        int right = -1;
        int bottom = -1;
        for (int y = Math.max(0, startY); y <= Math.min(bitmap.getHeight() - 1, endY); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 24) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return new int[]{left, top, right, bottom};
    }

    private static int boundsHeight(int[] bounds) {
        return bounds[3] < bounds[1] ? 0 : bounds[3] - bounds[1] + 1;
    }

    @SuppressWarnings("deprecation")
    private static float twoRowBlockHeight(String text) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setLetterSpacing(0.02f);
        paint.setTextSize(54f);
        int width = Math.round(VIEW_WIDTH * 0.84f);
        StaticLayout layout;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                    .setLineSpacing(8f, 1f)
                    .setMaxLines(2)
                    .build();
        } else {
            layout = new StaticLayout(
                    text,
                    paint,
                    width,
                    Layout.Alignment.ALIGN_CENTER,
                    1f,
                    8f,
                    false
            );
        }
        return layout.getLineBottom(Math.min(1, layout.getLineCount() - 1));
    }

    private static int[] opaqueBoundsNearLine(
            Bitmap bitmap,
            float[] lineQuad,
            int halfThickness
    ) {
        float[] center = diagonalIntersection(lineQuad);
        float[] vanishingPoint = horizontalVanishingPoint();
        int left = bitmap.getWidth();
        int top = bitmap.getHeight();
        int right = -1;
        int bottom = -1;
        int startX = Math.max(0, (int) Math.floor(minimumX(lineQuad)));
        int endX = Math.min(bitmap.getWidth() - 1, (int) Math.ceil(maximumX(lineQuad)));
        for (int x = startX; x <= endX; x++) {
            int centerY = Math.round(edgeYAtX(
                    center[0], center[1], vanishingPoint[0], vanishingPoint[1], x
            ));
            int startY = Math.max(0, centerY - halfThickness);
            int endY = Math.min(bitmap.getHeight() - 1, centerY + halfThickness);
            for (int y = startY; y <= endY; y++) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 24) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return new int[]{left, top, right, bottom};
    }

    private static String repeatToken(String token, int count) {
        StringBuilder text = new StringBuilder(token.length() * count);
        for (int index = 0; index < count; index++) text.append(token);
        return text.toString();
    }

    private static EdgeFit fitEdge(
            Bitmap bitmap,
            float[] lineQuad,
            int halfThickness,
            boolean topEdge
    ) {
        float[] center = diagonalIntersection(lineQuad);
        float[] vanishingPoint = horizontalVanishingPoint();
        int left = Math.max(0, (int) Math.floor(minimumX(lineQuad)));
        int right = Math.min(bitmap.getWidth() - 1, (int) Math.ceil(maximumX(lineQuad)));
        List<Float> xs = new ArrayList<>();
        List<Float> ys = new ArrayList<>();
        for (int x = left; x <= right; x++) {
            int centerY = Math.round(edgeYAtX(
                    center[0], center[1], vanishingPoint[0], vanishingPoint[1], x
            ));
            int top = Math.max(0, centerY - halfThickness);
            int bottom = Math.min(bitmap.getHeight() - 1, centerY + halfThickness);
            int edgeY = topEdge ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            for (int y = top; y <= bottom; y++) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 24) continue;
                edgeY = topEdge ? Math.min(edgeY, y) : Math.max(edgeY, y);
            }
            if (edgeY == Integer.MAX_VALUE || edgeY == Integer.MIN_VALUE) continue;
            xs.add((float) x);
            ys.add((float) edgeY);
        }
        return fitLine(xs, ys);
    }

    private static float[] diagonalIntersection(float[] quad) {
        return lineIntersection(
                quad[0], quad[1], quad[4], quad[5],
                quad[2], quad[3], quad[6], quad[7]
        );
    }

    private static float edgeYAtX(float x1, float y1, float x2, float y2, float x) {
        return y1 + (x - x1) * (y2 - y1) / (x2 - x1);
    }

    private static float minimumX(float[] quad) {
        return Math.min(Math.min(quad[0], quad[2]), Math.min(quad[4], quad[6]));
    }

    private static float maximumX(float[] quad) {
        return Math.max(Math.max(quad[0], quad[2]), Math.max(quad[4], quad[6]));
    }

    private static EdgeFit fitLine(List<Float> independent, List<Float> dependent) {
        float meanX = mean(independent);
        float meanY = mean(dependent);
        float numerator = 0f;
        float denominator = 0f;
        for (int index = 0; index < independent.size(); index++) {
            float centeredX = independent.get(index) - meanX;
            numerator += centeredX * (dependent.get(index) - meanY);
            denominator += centeredX * centeredX;
        }
        float slope = denominator == 0f ? 0f : numerator / denominator;
        float intercept = meanY - slope * meanX;
        List<Float> residuals = new ArrayList<>();
        for (int index = 0; index < independent.size(); index++) {
            float predictedY = slope * independent.get(index) + intercept;
            float residual = Math.abs(dependent.get(index) - predictedY);
            residuals.add(residual);
        }
        Collections.sort(residuals);
        int percentile95Index = Math.round((residuals.size() - 1) * 0.95f);
        float trimmedSquaredResidualTotal = 0f;
        for (int index = 0; index <= percentile95Index; index++) {
            float residual = residuals.get(index);
            trimmedSquaredResidualTotal += residual * residual;
        }
        float trimmedRootMeanSquareResidual = (float) Math.sqrt(
                trimmedSquaredResidualTotal / (percentile95Index + 1)
        );
        return new EdgeFit(
                independent.size(),
                slope,
                intercept,
                trimmedRootMeanSquareResidual,
                residuals.get(percentile95Index)
        );
    }

    private static float mean(List<Float> values) {
        float total = 0f;
        for (float value : values) total += value;
        return values.isEmpty() ? 0f : total / values.size();
    }

    private static void assertStraight(String label, EdgeFit edge) {
        assertTrue(
                label + edgeSummary(edge),
                edge.trimmedRootMeanSquareResidual <= 1.0f
        );
        assertTrue(
                label + edgeSummary(edge),
                edge.percentile95Residual <= 1.5f
        );
    }

    private static String edgeSummary(EdgeFit edge) {
        return "slope=" + edge.slope
                + ", trimmedRms=" + edge.trimmedRootMeanSquareResidual
                + ", p95=" + edge.percentile95Residual;
    }

    private static final class EdgeFit {
        final int samples;
        final float slope;
        final float intercept;
        final float trimmedRootMeanSquareResidual;
        final float percentile95Residual;

        EdgeFit(
                int samples,
                float slope,
                float intercept,
                float trimmedRootMeanSquareResidual,
                float percentile95Residual
        ) {
            this.samples = samples;
            this.slope = slope;
            this.intercept = intercept;
            this.trimmedRootMeanSquareResidual = trimmedRootMeanSquareResidual;
            this.percentile95Residual = percentile95Residual;
        }

        float valueAt(float independent) {
            return slope * independent + intercept;
        }
    }
}
