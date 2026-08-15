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
            if (view.isShown() && view.isEnabled() && view.isFocusable()) {
                view.requestFocus();
            }
        });
    }

    /** Playback may refresh its row after the click callback returns. */
    public static void keepFocusAfterPlayback(View view) {
        keepFocusAfterClick(view);
        view.postDelayed(() -> {
            if (view.isShown() && view.isEnabled() && view.isFocusable()) {
                view.requestFocus();
            }
        }, 320L);
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
