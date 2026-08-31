package top.boluofan.musictv.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
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
import top.boluofan.musictv.R;

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
        assertColorClose(ContextCompat.getColor(context, R.color.lx_bg_focus),
                centerColor(context, R.drawable.selector_selectable_surface, SELECTED_FOCUSED));
        assertColorClose(ContextCompat.getColor(context, R.color.lx_bg_focus),
                centerColor(context, R.drawable.selector_selectable_surface, FOCUSED));
        assertColorClose(ContextCompat.getColor(context, R.color.lx_brand_soft),
                centerColor(context, R.drawable.selector_selectable_surface, SELECTED));
        assertNotEquals(Color.WHITE,
                centerColor(context, R.drawable.selector_selectable_surface, FOCUSED));
        assertNotEquals(Color.WHITE,
                centerColor(context, R.drawable.selector_selectable_surface, SELECTED_FOCUSED));
    }

    @Test
    public void selectableContentUsesDarkInkOnMintStates() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ColorStateList colors = ContextCompat.getColorStateList(
                context, R.color.selector_selectable_content);
        int primary = ContextCompat.getColor(context, R.color.lx_text_primary);
        assertEquals(primary,
                colors.getColorForState(SELECTED_FOCUSED, colors.getDefaultColor()));
        assertEquals(primary,
                colors.getColorForState(FOCUSED, colors.getDefaultColor()));
        assertEquals(primary,
                colors.getColorForState(SELECTED, colors.getDefaultColor()));
        assertNotEquals(Color.WHITE,
                colors.getColorForState(FOCUSED, colors.getDefaultColor()));

        ColorStateList navColors = ContextCompat.getColorStateList(
                context, R.color.selector_nav_content);
        assertEquals(primary,
                navColors.getColorForState(SELECTED_FOCUSED, navColors.getDefaultColor()));
        assertEquals(primary,
                navColors.getColorForState(FOCUSED, navColors.getDefaultColor()));
        assertEquals(primary,
                navColors.getColorForState(SELECTED, navColors.getDefaultColor()));
    }

    @Test
    public void primaryActionDoesNotTurnWhiteOnFocus() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertColorClose(ContextCompat.getColor(context, R.color.lx_brand),
                centerColor(context, R.drawable.bg_btn_primary, FOCUSED));
        assertNotEquals(Color.WHITE, centerColor(context, R.drawable.bg_btn_primary, FOCUSED));

        ColorStateList content = ContextCompat.getColorStateList(
                context, R.color.selector_primary_action_content);
        assertEquals(ContextCompat.getColor(context, R.color.lx_text_primary),
                content.getColorForState(FOCUSED, content.getDefaultColor()));
    }

    @Test
    public void focusRingUsesTwoDpOpticalEdge() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        float density = context.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(2f * density),
                context.getResources().getDimensionPixelSize(R.dimen.lx_stroke_focus));
    }

    @Test
    public void glassTiersIncreaseOpacityWithoutBecomingOpaque() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int clear = ContextCompat.getColor(context, R.color.lx_glass_clear);
        int regular = ContextCompat.getColor(context, R.color.lx_glass_regular);
        int strong = ContextCompat.getColor(context, R.color.lx_glass_strong);

        assertTrue(Color.alpha(clear) < Color.alpha(regular));
        assertTrue(Color.alpha(regular) < Color.alpha(strong));
        assertTrue(Color.alpha(clear) >= 61 && Color.alpha(clear) <= 71);
        assertTrue(Color.alpha(regular) >= 77 && Color.alpha(regular) <= 87);
        assertTrue(Color.alpha(strong) >= 97 && Color.alpha(strong) <= 107);
    }

    @Test
    public void glassSurfacesKeepBackgroundVisibleBelowRestrainedTopLight() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int navTop = colorAt(context, R.drawable.bg_nav_rail, 120, 4);
        int contentTop = colorAt(context, R.drawable.bg_content_panel, 120, 4);
        int floatingTop = colorAt(context, R.drawable.bg_floating_glass, 120, 4);
        int navBottom = colorAt(context, R.drawable.bg_nav_rail, 120, 88);
        int contentBottom = colorAt(context, R.drawable.bg_content_panel, 120, 88);
        int floatingBottom = colorAt(context, R.drawable.bg_floating_glass, 120, 88);

        assertTrue(Color.alpha(navBottom) > Color.alpha(contentBottom));
        assertTrue(Color.alpha(contentBottom) > Color.alpha(floatingBottom));
        assertTrue(Color.alpha(navBottom) <= 112);
        assertTrue(Color.alpha(contentBottom) <= 96);
        assertTrue(Color.alpha(floatingBottom) <= 80);
        assertRestrainedTopLight(navTop, navBottom);
        assertRestrainedTopLight(contentTop, contentBottom);
        assertRestrainedTopLight(floatingTop, floatingBottom);
        int edge = ContextCompat.getColor(context, R.color.lx_glass_edge);
        assertTrue(Color.alpha(edge) >= 191 && Color.alpha(edge) <= 217);
    }

    @Test
    public void sourceSelectorUsesTransparentDefaultAndMintSelection() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int defaultColor = centerColor(context, R.drawable.selector_source_item, new int[0]);
        int selectedColor = centerColor(context, R.drawable.selector_source_item, SELECTED);
        int focusedColor = centerColor(context, R.drawable.selector_source_item, FOCUSED);
        int selectedFocusedColor = centerColor(
                context, R.drawable.selector_source_item, SELECTED_FOCUSED);

        assertEquals(0, Color.alpha(defaultColor));
        assertColorClose(ContextCompat.getColor(context, R.color.lx_brand_soft), selectedColor);
        assertColorClose(ContextCompat.getColor(context, R.color.lx_bg_focus), focusedColor);
        assertColorClose(ContextCompat.getColor(context, R.color.lx_bg_focus),
                selectedFocusedColor);
        assertNotEquals(Color.WHITE, selectedColor);

        ColorStateList content = ContextCompat.getColorStateList(
                context, R.color.selector_source_item_content);
        int primary = ContextCompat.getColor(context, R.color.lx_text_primary);
        assertEquals(primary, content.getColorForState(SELECTED, content.getDefaultColor()));
        assertEquals(primary, content.getColorForState(FOCUSED, content.getDefaultColor()));
        assertEquals(primary,
                content.getColorForState(SELECTED_FOCUSED, content.getDefaultColor()));
    }

    @Test
    public void ordinaryFocusSurfacesUseOneMintFill() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int expected = ContextCompat.getColor(context, R.color.lx_bg_focus);
        int[] drawables = {
                R.drawable.selector_selectable_surface,
                R.drawable.selector_source_item,
                R.drawable.bg_input_field,
                R.drawable.bg_btn_secondary,
                R.drawable.selector_menu_item,
                R.drawable.bg_tab_selected
        };
        for (int drawable : drawables) {
            assertColorClose(expected, centerColor(context, drawable, FOCUSED));
        }
    }

    @Test
    public void contentContainerLeavesFluidUncovered() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(R.layout.activity_main, null, false);
            assertNull(root.findViewById(R.id.fragmentContainer).getBackground());
        });
    }

    @Test
    public void sourceItemHidesLegacyRadioWithoutLeavingSpacing() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(R.layout.item_source, null, false);
            View radio = root.findViewById(R.id.ivRadio);
            View label = root.findViewById(R.id.tvSourceName);

            assertEquals(View.GONE, radio.getVisibility());
            assertEquals(0, radio.getLayoutParams().width);
            assertEquals(0, radio.getLayoutParams().height);
            assertEquals(0, ((android.view.ViewGroup.MarginLayoutParams)
                    label.getLayoutParams()).getMarginStart());
        });
    }

    @Test
    public void compactSearchResultKeepsOneCompleteSongRow() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(R.layout.fragment_search, null, false);
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
        return colorAt(context, drawableId, state, 120, 48);
    }

    private int colorAt(Context context, int drawableId, int x, int y) {
        return colorAt(context, drawableId, new int[0], x, y);
    }

    private int colorAt(Context context, int drawableId, int[] state, int x, int y) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId).mutate();
        drawable.setState(state);
        Bitmap bitmap = Bitmap.createBitmap(240, 96, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        return bitmap.getPixel(x, y);
    }

    private void assertColorClose(int expected, int actual) {
        assertTrue("expected=" + Integer.toHexString(expected)
                        + ", actual=" + Integer.toHexString(actual),
                Math.abs(Color.alpha(expected) - Color.alpha(actual)) <= 1
                        && Math.abs(Color.red(expected) - Color.red(actual)) <= 3
                        && Math.abs(Color.green(expected) - Color.green(actual)) <= 3
                        && Math.abs(Color.blue(expected) - Color.blue(actual)) <= 3);
    }

    private void assertRestrainedTopLight(int top, int bottom) {
        assertTrue(Color.alpha(top) > Color.alpha(bottom));
        assertTrue(Color.alpha(top) - Color.alpha(bottom) <= 36);
        assertTrue(Color.alpha(top) <= 140);
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
