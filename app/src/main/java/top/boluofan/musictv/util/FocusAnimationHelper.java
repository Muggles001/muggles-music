package top.boluofan.musictv.util;

import android.view.View;
import android.view.KeyEvent;
import android.view.animation.DecelerateInterpolator;

public class FocusAnimationHelper {

    public static void applyFocusAnimation(View view) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                animateFocusIn(v);
            } else {
                animateFocusOut(v);
            }
        });
    }

    public static void animateFocusIn(View view) {
        view.animate().cancel();
        view.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationZ(1.5f)
                .setDuration(180L)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    public static void animateFocusOut(View view) {
        view.animate().cancel();
        view.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationZ(0f)
                .setDuration(150L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public static void requestFocusAndAnimate(View view) {
        if (view.requestFocus()) {
            animateFocusIn(view);
        }
    }

    /** Keep a control focused when its click redraws surrounding content. */
    public static void keepFocusAfterClick(View view) {
        view.post(() -> {
            if (canRestoreClickedFocus(view)) {
                view.requestFocus();
            }
        });
    }

    /** Playback may refresh its row after the click callback returns. */
    public static void keepFocusAfterPlayback(View view) {
        keepFocusAfterClick(view);
        view.postDelayed(() -> {
            if (canRestoreClickedFocus(view)) {
                view.requestFocus();
            }
        }, 320L);
    }

    /** Never let a delayed playback redraw override a newer remote action. */
    private static boolean canRestoreClickedFocus(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.isShown()
                || !view.isEnabled() || !view.isFocusable()) {
            return false;
        }
        View currentFocus = view.getRootView().findFocus();
        return currentFocus == null || currentFocus == view;
    }

    /** A pager is a vertical endpoint, never a route back to the app rail. */
    public static void blockDownFocusEscape(View... controls) {
        View.OnKeyListener listener = (view, keyCode, event) -> event.getAction() == KeyEvent.ACTION_DOWN
                && keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
        for (View control : controls) {
            if (control != null) control.setOnKeyListener(listener);
        }
    }
}
