package top.boluofan.musictv.ui.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.core.view.ViewCompat;

import org.junit.Test;
import org.junit.runner.RunWith;

import top.boluofan.musictv.R;

@RunWith(AndroidJUnit4.class)
public class PlaylistImportLayoutInstrumentedTest {
    @Test
    public void importControlsExposeBoundedTvFocusGraph() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(
                    R.layout.activity_playlist_import, null, false);
            View back = root.findViewById(R.id.btnImportBack);
            View auto = root.findViewById(R.id.btnSourceAuto);
            View input = root.findViewById(R.id.etPlaylistLink);
            View clear = root.findViewById(R.id.btnImportClear);
            View scan = root.findViewById(R.id.btnImportScan);
            View cancel = root.findViewById(R.id.btnImportCancel);
            View confirm = root.findViewById(R.id.btnImportConfirm);

            assertTrue(back.isFocusable());
            assertEquals(R.id.btnSourceAuto, back.getNextFocusRightId());
            assertEquals(R.id.etPlaylistLink, auto.getNextFocusDownId());
            assertEquals(R.id.btnImportClear, input.getNextFocusRightId());
            assertEquals(R.id.btnImportResolve, input.getNextFocusDownId());
            assertEquals(R.id.etPlaylistLink, clear.getNextFocusLeftId());
            assertEquals(R.id.btnImportResolve, clear.getNextFocusRightId());
            assertEquals(R.id.btnImportClear, clear.getNextFocusDownId());
            assertEquals(R.id.btnSourceAuto, clear.getNextFocusUpId());
            assertEquals(R.id.btnImportScan, scan.getNextFocusRightId());
            assertEquals(R.id.btnImportConfirm, confirm.getNextFocusRightId());
            assertEquals(R.id.btnImportCancel, cancel.getNextFocusDownId());
            assertEquals(R.id.btnImportBack, PlaylistImportActivity.inputFocusTarget(
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT, false, R.id.btnSourceTx));
            assertEquals(R.id.btnImportClear, PlaylistImportActivity.inputFocusTarget(
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT, false, R.id.btnSourceTx));
            assertEquals(R.id.btnSourceTx, PlaylistImportActivity.inputFocusTarget(
                    android.view.KeyEvent.KEYCODE_DPAD_UP, false, R.id.btnSourceTx));
            assertEquals(R.id.btnImportResolve, PlaylistImportActivity.inputFocusTarget(
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN, false, R.id.btnSourceTx));
            assertEquals(R.id.btnImportConfirm, PlaylistImportActivity.inputFocusTarget(
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN, true, R.id.btnSourceTx));
            assertCapsuleCorners(context, cancel);
            assertCapsuleCorners(context, confirm);
            assertFalse(root.findViewById(R.id.tvImportState).isFocusable());
            assertTrue(input.isFocusable());
            ViewGroup content = root.findViewById(R.id.importContent);
            assertTrue(content.getChildAt(0) instanceof HorizontalScrollView);
        });
    }

    private static void assertCapsuleCorners(Context context, View view) {
        int width = context.getResources().getDimensionPixelSize(R.dimen.lx_button_short_width);
        int height = context.getResources().getDimensionPixelSize(R.dimen.lx_control_standard);
        int inset = Math.round(6 * context.getResources().getDisplayMetrics().density);
        assertNull(ViewCompat.getBackgroundTintList(view));
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));

        assertEquals(0, Color.alpha(bitmap.getPixel(inset, inset)));
        assertTrue(Color.alpha(bitmap.getPixel(width / 2, height / 2)) > 0);
    }
}
