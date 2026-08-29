package top.boluofan.musictv.player.visual;

public final class SpatialLyricsGeometry {
    private static final float REFERENCE_HEIGHT_PX = 1080f;
    private static final float PERSPECTIVE_DISTANCE_PX = 1350f;
    private static final float SIDE_DEPTH_PX = 384f;
    private static final float SIDE_DEPTH_REFERENCE_WIDTH_PX = 800f;
    private static final float ACTIVE_CENTER_X = 0.53f;
    private static final float MINIMUM_BLOCK_GAP_PX = 48f;
    private static final int ACTIVE_OVERLAY_ALPHA = 40;
    private static final float[] SCREEN_SPACING_PX = {140f, 136f, 132f};

    private SpatialLyricsGeometry() {
    }

    public static void compute(
            float distance,
            float viewportWidth,
            float viewportHeight,
            float projectionCenterX,
            float projectionCenterY,
            float focusFraction,
            Pose output
    ) {
        float referenceScale = viewportHeight / REFERENCE_HEIGHT_PX;
        float absoluteDistance = Math.abs(distance);
        output.alpha = alphaForDistance(absoluteDistance);
        output.screenX = viewportWidth * ACTIVE_CENTER_X;
        output.screenY = viewportHeight * focusFraction
                + signedScreenOffset(distance) * referenceScale;
        output.projectionCenterX = projectionCenterX;
        output.projectionCenterY = projectionCenterY;
        output.perspectiveDistance = PERSPECTIVE_DISTANCE_PX * referenceScale;
        output.sideDepth = SIDE_DEPTH_PX * referenceScale;
        output.sideDepthReferenceWidth = SIDE_DEPTH_REFERENCE_WIDTH_PX * referenceScale;
        float depthPerPixel = output.sideDepth / output.sideDepthReferenceWidth;
        float halfReferenceDepth = output.sideDepth * 0.5f;
        // Keep a typical lyric at its natural width after increasing plane depth.
        float referenceWidthCorrection = halfReferenceDepth * halfReferenceDepth
                / (output.perspectiveDistance * output.perspectiveDistance);
        output.planeXScale = 1f
                - (projectionCenterX - output.screenX)
                * depthPerPixel
                / output.perspectiveDistance
                - referenceWidthCorrection;
    }

    public static void populateProjectedQuad(
            Pose pose,
            float bitmapWidth,
            float bitmapHeight,
            float[] output
    ) {
        float left = pose.screenX - bitmapWidth * 0.5f;
        float top = pose.screenY - bitmapHeight * 0.5f;
        projectPlanePoint(pose, left, top, output, 0);
        projectPlanePoint(pose, left + bitmapWidth, top, output, 2);
        projectPlanePoint(
                pose,
                left + bitmapWidth,
                top + bitmapHeight,
                output,
                4
        );
        projectPlanePoint(pose, left, top + bitmapHeight, output, 6);
    }

    public static void populateProjectedPlaneQuad(
            Pose pose,
            float planeWidth,
            float planeHeight,
            float[] output
    ) {
        projectPlanePoint(pose, 0f, 0f, output, 0);
        projectPlanePoint(pose, planeWidth, 0f, output, 2);
        projectPlanePoint(pose, planeWidth, planeHeight, output, 4);
        projectPlanePoint(pose, 0f, planeHeight, output, 6);
    }

    public static String primaryText(String text) {
        if (text == null) return "";
        int lineBreak = text.indexOf('\n');
        return lineBreak < 0 ? text : text.substring(0, lineBreak);
    }

    public static String translationText(String text) {
        if (text == null) return "";
        int lineBreak = text.indexOf('\n');
        return lineBreak < 0 || lineBreak == text.length() - 1
                ? ""
                : text.substring(lineBreak + 1).replace('\n', ' ');
    }

    public static float interpolateIndex(float start, float end, float progress) {
        float easedProgress = cubicBezierEase(clamp(progress, 0f, 1f));
        return start + (end - start) * easedProgress;
    }

    public static float alphaForDistance(float absoluteDistance) {
        if (absoluteDistance <= 0f) return 1f;
        if (absoluteDistance >= 4f) return 0.12f;
        if (absoluteDistance <= 1f) return lerp(1f, 0.72f, absoluteDistance);
        if (absoluteDistance <= 2f) return lerp(0.72f, 0.50f, absoluteDistance - 1f);
        if (absoluteDistance <= 3f) return lerp(0.50f, 0.30f, absoluteDistance - 2f);
        return lerp(0.30f, 0.12f, absoluteDistance - 3f);
    }

    public static float screenSpacingForStep(int step, float viewportHeight) {
        int index = Math.max(0, Math.min(step, SCREEN_SPACING_PX.length - 1));
        return SCREEN_SPACING_PX[index] * viewportHeight / REFERENCE_HEIGHT_PX;
    }

    public static float blockCenterY(
            int targetIndex,
            float displayedIndex,
            float focusY,
            float viewportHeight,
            float[] blockHeights
    ) {
        float clampedIndex = clamp(displayedIndex, 0f, blockHeights.length - 1f);
        int lowerAnchor = (int) Math.floor(clampedIndex);
        int upperAnchor = Math.min(lowerAnchor + 1, blockHeights.length - 1);
        float progress = clampedIndex - lowerAnchor;
        float lowerCenter = blockCenterFromAnchor(
                targetIndex,
                lowerAnchor,
                focusY,
                viewportHeight,
                blockHeights
        );
        float upperCenter = blockCenterFromAnchor(
                targetIndex,
                upperAnchor,
                focusY,
                viewportHeight,
                blockHeights
        );
        return lerp(lowerCenter, upperCenter, progress);
    }

    public static int activeOverlayAlpha(float distance) {
        float emphasis = Math.max(0f, 1f - Math.abs(distance));
        return Math.round(ACTIVE_OVERLAY_ALPHA * emphasis);
    }

    private static float cubicBezierEase(float progress) {
        float lower = 0f;
        float upper = 1f;
        float parameter = progress;
        for (int iteration = 0; iteration < 14; iteration++) {
            parameter = (lower + upper) * 0.5f;
            float projectedProgress = cubicBezier(parameter, 0.16f, 0.30f);
            if (projectedProgress < progress) lower = parameter;
            else upper = parameter;
        }
        return cubicBezier(parameter, 1f, 1f);
    }

    private static float cubicBezier(float parameter, float firstControl, float secondControl) {
        float inverse = 1f - parameter;
        return 3f * inverse * inverse * parameter * firstControl
                + 3f * inverse * parameter * parameter * secondControl
                + parameter * parameter * parameter;
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float blockCenterFromAnchor(
            int targetIndex,
            int anchorIndex,
            float focusY,
            float viewportHeight,
            float[] blockHeights
    ) {
        float center = focusY;
        if (targetIndex > anchorIndex) {
            for (int index = anchorIndex + 1; index <= targetIndex; index++) {
                center += blockSpacing(
                        index - anchorIndex - 1,
                        blockHeights[index - 1],
                        blockHeights[index],
                        viewportHeight
                );
            }
        } else {
            for (int index = anchorIndex - 1; index >= targetIndex; index--) {
                center -= blockSpacing(
                        anchorIndex - index - 1,
                        blockHeights[index],
                        blockHeights[index + 1],
                        viewportHeight
                );
            }
        }
        return center;
    }

    private static float blockSpacing(
            int step,
            float firstHeight,
            float secondHeight,
            float viewportHeight
    ) {
        float minimumGap = MINIMUM_BLOCK_GAP_PX * viewportHeight / REFERENCE_HEIGHT_PX;
        float contentSpacing = (firstHeight + secondHeight) * 0.5f + minimumGap;
        return Math.max(screenSpacingForStep(step, viewportHeight), contentSpacing);
    }

    private static void projectPlanePoint(
            Pose pose,
            float sourceX,
            float sourceY,
            float[] output,
            int outputOffset
    ) {
        float localX = sourceX - pose.screenX;
        float depth = -localX * pose.sideDepth / pose.sideDepthReferenceWidth;
        float projection = pose.perspectiveDistance / (pose.perspectiveDistance - depth);
        float worldX = pose.screenX + localX * pose.planeXScale;
        output[outputOffset] = pose.projectionCenterX
                + (worldX - pose.projectionCenterX) * projection;
        output[outputOffset + 1] = pose.projectionCenterY
                + (sourceY - pose.projectionCenterY) * projection;
    }

    private static float signedScreenOffset(float distance) {
        float absoluteDistance = Math.abs(distance);
        int wholeSteps = Math.min((int) absoluteDistance, SCREEN_SPACING_PX.length);
        float offset = 0f;
        for (int index = 0; index < wholeSteps; index++) {
            offset += SCREEN_SPACING_PX[index];
        }
        if (wholeSteps < SCREEN_SPACING_PX.length) {
            offset += (absoluteDistance - wholeSteps) * SCREEN_SPACING_PX[wholeSteps];
        } else if (absoluteDistance > SCREEN_SPACING_PX.length) {
            offset += (absoluteDistance - SCREEN_SPACING_PX.length)
                    * SCREEN_SPACING_PX[SCREEN_SPACING_PX.length - 1];
        }
        return Math.copySign(offset, distance);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Pose {
        public float screenX;
        public float screenY;
        public float projectionCenterX;
        public float projectionCenterY;
        public float perspectiveDistance;
        public float sideDepth;
        public float sideDepthReferenceWidth;
        public float planeXScale;
        public float alpha;
    }
}
