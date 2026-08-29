package top.boluofan.musictv.player.visual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpatialLyricsGeometryTest {
    private static final float VIEWPORT_WIDTH = 960f;
    private static final float VIEWPORT_HEIGHT = 1080f;
    private static final float PROJECTION_CENTER_X = 1920f;
    private static final float PROJECTION_CENTER_Y = 612f;
    private static final float BITMAP_WIDTH = 800f;
    private static final float BITMAP_HEIGHT = 64f;

    @Test public void activeLineKeepsItsPositionAndOpacity() {
        SpatialLyricsGeometry.Pose active = pose(0f);

        assertEquals(508.8f, active.screenX, 0.1f);
        assertEquals(540f, active.screenY, 0.1f);
        assertEquals(PROJECTION_CENTER_X, active.projectionCenterX, 0f);
        assertEquals(PROJECTION_CENTER_Y, active.projectionCenterY, 0f);
        assertEquals(0.4780f, active.planeXScale, 0.0005f);
        assertEquals(1f, active.alpha, 0f);
    }

    @Test public void slotsKeepCompressedSpacingAndOpacityHierarchy() {
        SpatialLyricsGeometry.Pose d1 = pose(1f);
        SpatialLyricsGeometry.Pose d2 = pose(2f);
        SpatialLyricsGeometry.Pose d3 = pose(3f);
        SpatialLyricsGeometry.Pose negative = pose(-3f);
        SpatialLyricsGeometry.Pose half = pose(1.5f);

        assertEquals(680f, d1.screenY, 0.1f);
        assertEquals(816f, d2.screenY, 0.1f);
        assertEquals(948f, d3.screenY, 0.1f);
        assertEquals(132f, negative.screenY, 0.1f);
        assertTrue(d2.screenY - d1.screenY < d1.screenY - 540f);
        assertTrue(d3.screenY - d2.screenY < d2.screenY - d1.screenY);
        assertTrue(half.screenY > d1.screenY);
        assertTrue(half.screenY < d2.screenY);
        assertEquals(0.72f, d1.alpha, 0.001f);
        assertEquals(0.50f, d2.alpha, 0.001f);
        assertEquals(0.30f, d3.alpha, 0.001f);
        assertEquals(0.86f, pose(0.5f).alpha, 0.001f);
        assertEquals(508.8f, d1.screenX, 0.1f);
        assertEquals(508.8f, d2.screenX, 0.1f);
        assertEquals(508.8f, d3.screenX, 0.1f);
    }

    @Test public void tallBlocksKeepFortyEightPixelGapAndInterpolateAcrossAnchors() {
        float[] blockHeights = {128f, 160f, 144f, 176f};
        float focusY = 540f;
        float expectedStep = blockHeights[0] * 0.5f
                + blockHeights[1] * 0.5f
                + 48f;

        float firstAtStart = blockCenterY(0, 0f, focusY, blockHeights);
        float secondAtStart = blockCenterY(1, 0f, focusY, blockHeights);
        assertEquals(focusY, firstAtStart, 0.01f);
        assertEquals(expectedStep, secondAtStart - firstAtStart, 0.01f);
        assertEquals(
                48f,
                secondAtStart - blockHeights[1] * 0.5f
                        - (firstAtStart + blockHeights[0] * 0.5f),
                0.01f
        );

        float firstAtHalf = blockCenterY(0, 0.5f, focusY, blockHeights);
        float secondAtHalf = blockCenterY(1, 0.5f, focusY, blockHeights);
        assertEquals(focusY - expectedStep * 0.5f, firstAtHalf, 0.01f);
        assertEquals(focusY + expectedStep * 0.5f, secondAtHalf, 0.01f);

        float beforeBoundary = blockCenterY(2, 0.999f, focusY, blockHeights);
        float afterBoundary = blockCenterY(2, 1.001f, focusY, blockHeights);
        assertTrue(Math.abs(afterBoundary - beforeBoundary) < 1f);
    }

    @Test public void mixedBlockHeightsKeepOuterGapClearlyLargerThanInnerSpacing() {
        float[] referenceHeights = {64f, 160f, 224f, 64f};
        float[] viewportHeights = {720f, 1080f, 2160f};
        float[] displayedIndices = {0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f, 1.5f, 2f};

        for (float viewportHeight : viewportHeights) {
            float scale = viewportHeight / VIEWPORT_HEIGHT;
            float[] blockHeights = new float[referenceHeights.length];
            for (int index = 0; index < blockHeights.length; index++) {
                blockHeights[index] = referenceHeights[index] * scale;
            }
            float minimumOuterGap = 48f * scale;
            float innerLineExtra = 8f * scale;
            assertTrue(minimumOuterGap >= innerLineExtra * 6f);

            for (float displayedIndex : displayedIndices) {
                for (int upper = 0; upper < blockHeights.length - 1; upper++) {
                    float gap = edgeGap(
                            upper,
                            displayedIndex,
                            viewportHeight * 0.5f,
                            viewportHeight,
                            blockHeights
                    );
                    assertTrue(
                            "viewport=" + viewportHeight + ", displayed=" + displayedIndex
                                    + ", upper=" + upper + ", gap=" + gap,
                            gap + 0.01f >= minimumOuterGap
                    );
                }
            }
        }
    }

    @Test public void animationTranslatesEveryBlockWithoutChangingAdjacentGaps() {
        float[] blockHeights = {64f, 160f, 224f, 64f};
        float[] expectedGaps = new float[blockHeights.length - 1];
        for (int upper = 0; upper < expectedGaps.length; upper++) {
            expectedGaps[upper] = edgeGap(upper, 0f, 540f, VIEWPORT_HEIGHT, blockHeights);
        }

        float[] displayedIndices = {0f, 0.125f, 0.5f, 0.875f, 0.999f, 1f, 1.001f, 1.5f, 2f};
        for (float displayedIndex : displayedIndices) {
            for (int upper = 0; upper < expectedGaps.length; upper++) {
                assertEquals(
                        "displayed=" + displayedIndex + ", upper=" + upper,
                        expectedGaps[upper],
                        edgeGap(upper, displayedIndex, 540f, VIEWPORT_HEIGHT, blockHeights),
                        0.01f
                );
            }
        }
    }

    @Test public void activeOverlayIsBoundedAndContinuous() {
        assertEquals(40, SpatialLyricsGeometry.activeOverlayAlpha(0f));
        assertEquals(30, SpatialLyricsGeometry.activeOverlayAlpha(0.25f));
        assertEquals(20, SpatialLyricsGeometry.activeOverlayAlpha(0.5f));
        assertEquals(10, SpatialLyricsGeometry.activeOverlayAlpha(-0.75f));
        assertEquals(0, SpatialLyricsGeometry.activeOverlayAlpha(1f));
        assertEquals(0, SpatialLyricsGeometry.activeOverlayAlpha(3f));
    }

    @Test public void projectedBaselinesShareOneShallowVanishingPoint() {
        float[] vanishingPoint = horizontalVanishingPoint(quad(0f));
        float activeAngle = baselineAngleDegrees(quad(0f));
        float nextAngle = baselineAngleDegrees(quad(1f));

        float[] planeVanishingPoint = horizontalVanishingPoint(planeQuad());
        assertEquals(3264.4f, vanishingPoint[0], 2f);
        assertEquals(PROJECTION_CENTER_Y, vanishingPoint[1], 0.2f);
        assertEquals(vanishingPoint[0], planeVanishingPoint[0], 2f);
        assertEquals(vanishingPoint[1], planeVanishingPoint[1], 0.2f);
        assertEquals(1.50f, activeAngle, 0.08f);
        assertEquals(-1.41f, nextAngle, 0.08f);

        for (int distance = -3; distance <= 3; distance++) {
            float[] quad = quad(distance);
            float[] lineVanishingPoint = horizontalVanishingPoint(quad);
            assertEquals(
                    "vanishing X, distance=" + distance,
                    vanishingPoint[0],
                    lineVanishingPoint[0],
                    2f
            );
            assertEquals(
                    "vanishing Y, distance=" + distance,
                    vanishingPoint[1],
                    lineVanishingPoint[1],
                    0.2f
            );
            assertEquals(
                    "top edge, distance=" + distance,
                    vanishingPoint[1],
                    edgeYAtX(quad[0], quad[1], quad[2], quad[3], vanishingPoint[0]),
                    0.2f
            );
            assertEquals(
                    "bottom edge, distance=" + distance,
                    vanishingPoint[1],
                    edgeYAtX(quad[6], quad[7], quad[4], quad[5], vanishingPoint[0]),
                    0.2f
            );
        }
    }

    @Test public void sharedPlaneKeepsSideDepthAndLineLengthStableAcrossSlots() {
        float minimumBaselineLength = Float.POSITIVE_INFINITY;
        float maximumBaselineLength = 0f;
        float expectedHeightRatio = -1f;

        for (int distance = -3; distance <= 3; distance++) {
            float[] quad = quad(distance);
            float leftHeight = length(quad[0], quad[1], quad[6], quad[7]);
            float rightHeight = length(quad[2], quad[3], quad[4], quad[5]);
            float heightRatio = rightHeight / leftHeight;
            float baselineLength = projectedCenterlineLength(quad);

            assertEquals("distance=" + distance, 0.751f, heightRatio, 0.01f);
            if (expectedHeightRatio < 0f) expectedHeightRatio = heightRatio;
            assertEquals("distance=" + distance, expectedHeightRatio, heightRatio, 0.003f);
            if (distance == 0) {
                assertEquals(BITMAP_WIDTH, baselineLength, BITMAP_WIDTH * 0.005f);
            }
            minimumBaselineLength = Math.min(minimumBaselineLength, baselineLength);
            maximumBaselineLength = Math.max(maximumBaselineLength, baselineLength);
        }

        assertTrue(
                "minimum=" + minimumBaselineLength + ", maximum=" + maximumBaselineLength,
                maximumBaselineLength - minimumBaselineLength <= maximumBaselineLength * 0.03f
        );
    }

    @Test public void primaryTextAndEasingRemainStable() {
        assertEquals("", SpatialLyricsGeometry.primaryText(null));
        assertEquals("main", SpatialLyricsGeometry.primaryText("main\ntranslation"));
        assertEquals("single line", SpatialLyricsGeometry.primaryText("single line"));
        assertEquals(0f, SpatialLyricsGeometry.interpolateIndex(0f, 1f, -1f), 0.001f);
        assertEquals(1f, SpatialLyricsGeometry.interpolateIndex(0f, 1f, 1f), 0.001f);
        assertTrue(SpatialLyricsGeometry.interpolateIndex(0f, 1f, 0.25f) > 0.25f);
        assertEquals(1f, SpatialLyricsGeometry.interpolateIndex(0f, 1f, 2f), 0.001f);
    }

    private static SpatialLyricsGeometry.Pose pose(float distance) {
        SpatialLyricsGeometry.Pose pose = new SpatialLyricsGeometry.Pose();
        SpatialLyricsGeometry.compute(
                distance,
                VIEWPORT_WIDTH,
                VIEWPORT_HEIGHT,
                PROJECTION_CENTER_X,
                PROJECTION_CENTER_Y,
                0.50f,
                pose
        );
        return pose;
    }

    private static float blockCenterY(
            int targetIndex,
            float displayedIndex,
            float focusY,
            float[] blockHeights
    ) {
        return SpatialLyricsGeometry.blockCenterY(
                targetIndex,
                displayedIndex,
                focusY,
                VIEWPORT_HEIGHT,
                blockHeights
        );
    }

    private static float edgeGap(
            int upperIndex,
            float displayedIndex,
            float focusY,
            float viewportHeight,
            float[] blockHeights
    ) {
        float upperCenter = SpatialLyricsGeometry.blockCenterY(
                upperIndex, displayedIndex, focusY, viewportHeight, blockHeights);
        float lowerCenter = SpatialLyricsGeometry.blockCenterY(
                upperIndex + 1, displayedIndex, focusY, viewportHeight, blockHeights);
        return lowerCenter - blockHeights[upperIndex + 1] * 0.5f
                - (upperCenter + blockHeights[upperIndex] * 0.5f);
    }

    private static float[] quad(float distance) {
        float[] quad = new float[8];
        SpatialLyricsGeometry.populateProjectedQuad(
                pose(distance),
                BITMAP_WIDTH,
                BITMAP_HEIGHT,
                quad
        );
        return quad;
    }

    private static float[] planeQuad() {
        float[] quad = new float[8];
        SpatialLyricsGeometry.populateProjectedPlaneQuad(
                pose(0f),
                VIEWPORT_WIDTH,
                VIEWPORT_HEIGHT,
                quad
        );
        return quad;
    }

    private static float baselineAngleDegrees(float[] quad) {
        float[] center = diagonalIntersection(quad);
        float[] vanishingPoint = horizontalVanishingPoint(quad);
        return (float) Math.toDegrees(Math.atan2(
                vanishingPoint[1] - center[1],
                vanishingPoint[0] - center[0]
        ));
    }

    private static float edgeYAtX(float x1, float y1, float x2, float y2, float x) {
        return y1 + (x - x1) * (y2 - y1) / (x2 - x1);
    }

    private static float[] diagonalIntersection(float[] quad) {
        return lineIntersection(
                quad[0], quad[1], quad[4], quad[5],
                quad[2], quad[3], quad[6], quad[7]
        );
    }

    private static float[] horizontalVanishingPoint(float[] quad) {
        return lineIntersection(
                quad[0], quad[1], quad[2], quad[3],
                quad[6], quad[7], quad[4], quad[5]
        );
    }

    private static float projectedCenterlineLength(float[] quad) {
        float[] center = diagonalIntersection(quad);
        float[] vanishingPoint = horizontalVanishingPoint(quad);
        float[] left = lineIntersection(
                center[0], center[1], vanishingPoint[0], vanishingPoint[1],
                quad[0], quad[1], quad[6], quad[7]
        );
        float[] right = lineIntersection(
                center[0], center[1], vanishingPoint[0], vanishingPoint[1],
                quad[2], quad[3], quad[4], quad[5]
        );
        return length(left[0], left[1], right[0], right[1]);
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

    private static float length(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }
}
