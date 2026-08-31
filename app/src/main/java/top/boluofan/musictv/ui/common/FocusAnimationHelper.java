package top.boluofan.musictv.ui.common;

import android.content.res.Resources;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import top.boluofan.musictv.R;

public class FocusAnimationHelper {

    public enum Profile {
        CARD(R.integer.lx_focus_scale_card_milli, R.dimen.lx_elevation_focus_card,
                R.integer.lx_motion_focus_card),
        ROW(R.integer.lx_focus_scale_row_milli, R.dimen.lx_elevation_focus_row,
                R.integer.lx_motion_focus_row),
        RAIL(R.integer.lx_focus_scale_rail_milli, R.dimen.lx_elevation_focus_rail,
                R.integer.lx_motion_focus_rail),
        ICON(R.integer.lx_focus_scale_icon_milli, R.dimen.lx_elevation_focus_icon,
                R.integer.lx_motion_focus_icon);

        private final int scaleResource;
        private final int elevationResource;
        private final int durationResource;

        Profile(int scaleResource, int elevationResource, int durationResource) {
            this.scaleResource = scaleResource;
            this.elevationResource = elevationResource;
            this.durationResource = durationResource;
        }
    }

    public static void applyFocusAnimation(View view) {
        applyFocusAnimation(view, Profile.ROW);
    }

    public static void applyFocusAnimation(View view, Profile profile) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                animateFocusIn(v, profile);
            } else {
                animateFocusOut(v, profile);
            }
        });
    }

    public static void animateFocusIn(View view) {
        animateFocusIn(view, Profile.RAIL);
    }

    public static void animateFocusIn(View view, Profile profile) {
        Resources resources = view.getResources();
        view.animate().cancel();
        ViewPropertyAnimator animator = view.animate()
                .scaleX(focusedScale(resources, profile))
                .scaleY(focusedScale(resources, profile))
                .translationZ(resources.getDimension(profile.elevationResource))
                .setDuration(resources.getInteger(profile.durationResource))
                .setInterpolator(new DecelerateInterpolator(1.8f));
        if (profile == Profile.CARD) animator.withLayer();
        animator.start();
    }

    public static void animateFocusOut(View view) {
        animateFocusOut(view, Profile.RAIL);
    }

    public static void animateFocusOut(View view, Profile profile) {
        view.animate().cancel();
        ViewPropertyAnimator animator = view.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationZ(0f)
                .setDuration(view.getResources().getInteger(R.integer.lx_motion_press))
                .setInterpolator(new DecelerateInterpolator());
        if (profile == Profile.CARD) animator.withLayer();
        animator.start();
    }

    public static void requestFocusAndAnimate(View view) {
        requestFocusAndAnimate(view, Profile.ROW);
    }

    public static void requestFocusAndAnimate(View view, Profile profile) {
        if (view.requestFocus()) {
            animateFocusIn(view, profile);
        }
    }

    static float focusedScale(Resources resources, Profile profile) {
        return resources.getInteger(profile.scaleResource) / 1000f;
    }

    static int focusDuration(Resources resources, Profile profile) {
        return resources.getInteger(profile.durationResource);
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
