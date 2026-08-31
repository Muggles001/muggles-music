package top.boluofan.musictv.testutil;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public final class LyricBitmapFixture {
    private LyricBitmapFixture() {
    }

    public static int maxAlphaNear(
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

    public static int countOpaqueBandsNearX(Bitmap bitmap, int centerX, int halfWidth) {
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

    public static int verticalGapBetween(int[] upper, int[] lower) {
        return lower[1] - upper[3] - 1;
    }

    public static int opaqueHeightInRect(
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

    public static int[] opaqueBounds(Bitmap bitmap) {
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

    public static int[] opaqueBoundsInVerticalWindow(
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

    public static int[] opaqueBoundsInYRange(Bitmap bitmap, int startY, int endY) {
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

    public static int boundsHeight(int[] bounds) {
        return bounds[3] < bounds[1] ? 0 : bounds[3] - bounds[1] + 1;
    }
}
