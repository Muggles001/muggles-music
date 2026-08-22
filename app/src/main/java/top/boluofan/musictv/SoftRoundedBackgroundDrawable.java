package top.boluofan.musictv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A low-cost rounded rectangle whose cubic corners meet every straight edge tangentially. */
public final class SoftRoundedBackgroundDrawable extends Drawable {
    // Cubic Bezier approximation of a quarter circle. Unlike sampled superellipse points,
    // this gives the corner and straight edge exactly the same tangent at their join.
    private static final float QUARTER_CIRCLE_CONTROL = 0.55228475f;
    static final float MAX_CORNER_HEIGHT_FRACTION = 0.22f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float requestedExtent;

    public SoftRoundedBackgroundDrawable(int color, float extentPx) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        requestedExtent = extentPx;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        rebuildPath(bounds);
    }

    private void rebuildPath(Rect bounds) {
        path.reset();
        float left = bounds.left;
        float top = bounds.top;
        float right = bounds.right;
        float bottom = bounds.bottom;
        // Half-height corners turn short lyric rows into pills. This ratio keeps the
        // cover's rounded-corner-to-straight-edge balance while preserving tangency.
        float extent = Math.min(requestedExtent,
                Math.min((right - left) / 2f,
                        (bottom - top) * MAX_CORNER_HEIGHT_FRACTION));
        float control = extent * QUARTER_CIRCLE_CONTROL;

        path.moveTo(left + extent, top);
        path.lineTo(right - extent, top);
        path.cubicTo(right - extent + control, top,
                right, top + extent - control,
                right, top + extent);
        path.lineTo(right, bottom - extent);
        path.cubicTo(right, bottom - extent + control,
                right - extent + control, bottom,
                right - extent, bottom);
        path.lineTo(left + extent, bottom);
        path.cubicTo(left + extent - control, bottom,
                left, bottom - extent + control,
                left, bottom - extent);
        path.lineTo(left, top + extent);
        path.cubicTo(left, top + extent - control,
                left + extent - control, top,
                left + extent, top);
        path.close();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
