package top.boluofan.musictv.util;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import top.boluofan.musictv.R;

public class FocusAnimationHelper {

    private static AnimatorSet currentAnimatorSet;

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
        if (currentAnimatorSet != null) {
            currentAnimatorSet.cancel();
        }

        view.setScaleX(1.05f);
        view.setScaleY(1.05f);
        view.setElevation(8f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.05f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.05f);

        currentAnimatorSet = new AnimatorSet();
        currentAnimatorSet.playTogether(scaleX, scaleY);
        currentAnimatorSet.setDuration(120);
        currentAnimatorSet.setInterpolator(new DecelerateInterpolator());
        currentAnimatorSet.start();
    }

    public static void animateFocusOut(View view) {
        if (currentAnimatorSet != null) {
            currentAnimatorSet.cancel();
        }

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", view.getScaleX(), 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", view.getScaleY(), 1.0f);

        currentAnimatorSet = new AnimatorSet();
        currentAnimatorSet.playTogether(scaleX, scaleY);
        currentAnimatorSet.setDuration(100);
        currentAnimatorSet.setInterpolator(new AccelerateInterpolator());
        currentAnimatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setElevation(0f);
            }
        });
        currentAnimatorSet.start();
    }

    public static void requestFocusAndAnimate(View view) {
        if (view.requestFocus()) {
            animateFocusIn(view);
        }
    }
}
