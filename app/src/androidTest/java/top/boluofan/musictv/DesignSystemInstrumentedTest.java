package top.boluofan.musictv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DesignSystemInstrumentedTest {
    private static final int[] SELECTED_FOCUSED = {
            android.R.attr.state_enabled,
            android.R.attr.state_selected,
            android.R.attr.state_focused
    };
    private static final int[] FOCUSED = {
            android.R.attr.state_enabled,
            android.R.attr.state_focused
    };
    private static final int[] SELECTED = {
            android.R.attr.state_enabled,
            android.R.attr.state_selected
    };

    @Test
    public void selectableSurfaceFollowsTvStateMatrix() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertColorClose(ContextCompat.getColor(context, R.color.lx_selected_focused_fill),
                centerColor(context, R.drawable.selector_selectable_surface, SELECTED_FOCUSED));
        assertColorClose(ContextCompat.getColor(context, R.color.lx_bg_focus),
                centerColor(context, R.drawable.selector_selectable_surface, FOCUSED));
        assertColorClose(ContextCompat.getColor(context, R.color.lx_brand_soft),
                centerColor(context, R.drawable.selector_selectable_surface, SELECTED));
        assertNotEquals(Color.WHITE,
                centerColor(context, R.drawable.selector_selectable_surface, FOCUSED));
    }

    @Test
    public void selectableContentOnlyInvertsForSelectedFocus() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ColorStateList colors = ContextCompat.getColorStateList(
                context, R.color.selector_selectable_content);
        assertEquals(ContextCompat.getColor(context, R.color.lx_brand_pressed),
                colors.getColorForState(SELECTED_FOCUSED, colors.getDefaultColor()));
        assertEquals(ContextCompat.getColor(context, R.color.lx_brand_pressed),
                colors.getColorForState(FOCUSED, colors.getDefaultColor()));
        assertEquals(ContextCompat.getColor(context, R.color.lx_brand),
                colors.getColorForState(SELECTED, colors.getDefaultColor()));
        assertNotEquals(Color.WHITE,
                colors.getColorForState(FOCUSED, colors.getDefaultColor()));
    }

    @Test
    public void primaryActionDoesNotTurnWhiteOnFocus() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertColorClose(ContextCompat.getColor(context, R.color.lx_brand),
                centerColor(context, R.drawable.bg_btn_primary, FOCUSED));
        assertNotEquals(Color.WHITE, centerColor(context, R.drawable.bg_btn_primary, FOCUSED));
    }

    @Test
    public void focusRingUsesThreeDpToken() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        float density = context.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(3f * density),
                context.getResources().getDimensionPixelSize(R.dimen.lx_stroke_focus));
    }

    @Test
    public void compactSearchResultKeepsOneCompleteSongRow() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(R.layout.activity_search, null, false);
            root.findViewById(R.id.tvTitle).setVisibility(View.GONE);
            root.findViewById(R.id.tvSearchSubtitle).setVisibility(View.GONE);
            root.findViewById(R.id.tvHotSearchTitle).setVisibility(View.GONE);
            root.findViewById(R.id.rvHotSearch).setVisibility(View.GONE);
            root.findViewById(R.id.layoutSearchActions).setVisibility(View.VISIBLE);
            root.findViewById(R.id.layoutSearchPager).setVisibility(View.VISIBLE);
            RecyclerView results = root.findViewById(R.id.rvSearchResults);
            results.setVisibility(View.VISIBLE);

            int width = dp(context, 960);
            int height = dp(context, 436);
            root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);

            int requiredHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.lx_list_row_height) + results.getPaddingTop() + results.getPaddingBottom();
            assertTrue("resultsHeight=" + results.getMeasuredHeight()
                            + ", requiredHeight=" + requiredHeight,
                    results.getMeasuredHeight() >= requiredHeight);
        });
    }

    private int centerColor(Context context, int drawableId, int[] state) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId).mutate();
        drawable.setState(state);
        Bitmap bitmap = Bitmap.createBitmap(240, 96, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        return bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
    }

    private void assertColorClose(int expected, int actual) {
        assertTrue("expected=" + Integer.toHexString(expected)
                        + ", actual=" + Integer.toHexString(actual),
                Math.abs(Color.alpha(expected) - Color.alpha(actual)) <= 1
                        && Math.abs(Color.red(expected) - Color.red(actual)) <= 3
                        && Math.abs(Color.green(expected) - Color.green(actual)) <= 3
                        && Math.abs(Color.blue(expected) - Color.blue(actual)) <= 3);
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
