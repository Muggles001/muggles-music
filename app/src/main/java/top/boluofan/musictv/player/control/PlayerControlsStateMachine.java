package top.boluofan.musictv.player.control;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public final class PlayerControlsStateMachine {
    public static final long DEFAULT_HIDE_DELAY_MS = 4_500L;
    public static final long DEFAULT_ANIMATION_DURATION_MS = 280L;

    public enum State {
        HIDDEN,
        SHOWING,
        VISIBLE,
        HIDING
    }

    public interface Listener {
        void onStateChanged(State state);
    }

    public interface Scheduler {
        Cancellable schedule(Runnable runnable, long delayMs);
    }

    public interface Cancellable {
        void cancel();
    }

    public interface AnimationDriver {
        void snapHidden();

        void animateTo(boolean visible, long durationMs, long generation, Completion completion);

        void cancel();
    }

    public interface Completion {
        void onComplete(long generation);
    }

    private final Scheduler scheduler;
    private final AnimationDriver animationDriver;
    private final Listener listener;
    private final long hideDelayMs;
    private final long animationDurationMs;

    private State state = State.HIDDEN;
    private Cancellable pendingHide;
    private long animationGeneration;
    private boolean disposed;

    public PlayerControlsStateMachine(View controlsView, Handler handler) {
        this(controlsView, handler, null);
    }

    public PlayerControlsStateMachine(View controlsView, Handler handler, Listener listener) {
        this(
                new HandlerScheduler(handler),
                new ViewAnimationDriver(controlsView),
                listener,
                DEFAULT_HIDE_DELAY_MS,
                DEFAULT_ANIMATION_DURATION_MS
        );
    }

    public PlayerControlsStateMachine(
            Scheduler scheduler,
            AnimationDriver animationDriver,
            Listener listener,
            long hideDelayMs,
            long animationDurationMs
    ) {
        if (scheduler == null || animationDriver == null) {
            throw new IllegalArgumentException("scheduler and animationDriver are required");
        }
        if (hideDelayMs < 0L || animationDurationMs < 0L) {
            throw new IllegalArgumentException("durations must not be negative");
        }
        this.scheduler = scheduler;
        this.animationDriver = animationDriver;
        this.listener = listener;
        this.hideDelayMs = hideDelayMs;
        this.animationDurationMs = animationDurationMs;
        animationDriver.snapHidden();
    }

    public State getState() {
        return state;
    }

    public boolean isDisplayed() {
        return state == State.SHOWING || state == State.VISIBLE;
    }

    public void reveal() {
        if (disposed) {
            return;
        }
        if (state == State.HIDDEN || state == State.HIDING) {
            startAnimation(true);
        }
        scheduleHide();
    }

    public void onInteraction() {
        reveal();
    }

    public boolean onRevealKey(int keyCode) {
        if (!isRevealKey(keyCode)) {
            return false;
        }
        boolean consume = !isDisplayed();
        reveal();
        return consume;
    }

    public boolean onPointerMoved(float deltaY) {
        if (deltaY <= 0f) {
            return false;
        }
        reveal();
        return true;
    }

    public void hideNow() {
        if (disposed || state == State.HIDDEN || state == State.HIDING) {
            return;
        }
        cancelPendingHide();
        startAnimation(false);
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        cancelPendingHide();
        animationGeneration++;
        animationDriver.cancel();
    }

    public static boolean isRevealKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void startAnimation(boolean visible) {
        animationDriver.cancel();
        long generation = ++animationGeneration;
        setState(visible ? State.SHOWING : State.HIDING);
        animationDriver.animateTo(
                visible,
                animationDurationMs,
                generation,
                completedGeneration -> finishAnimation(visible, completedGeneration)
        );
    }

    private void finishAnimation(boolean visible, long completedGeneration) {
        if (disposed || completedGeneration != animationGeneration) {
            return;
        }
        State expectedState = visible ? State.SHOWING : State.HIDING;
        if (state != expectedState) {
            return;
        }
        setState(visible ? State.VISIBLE : State.HIDDEN);
    }

    private void scheduleHide() {
        cancelPendingHide();
        pendingHide = scheduler.schedule(() -> {
            pendingHide = null;
            hideNow();
        }, hideDelayMs);
    }

    private void cancelPendingHide() {
        if (pendingHide != null) {
            pendingHide.cancel();
            pendingHide = null;
        }
    }

    private void setState(State newState) {
        if (state == newState) {
            return;
        }
        state = newState;
        if (listener != null) {
            listener.onStateChanged(newState);
        }
    }

    private static final class HandlerScheduler implements Scheduler {
        private final Handler handler;

        private HandlerScheduler(Handler handler) {
            if (handler == null) {
                throw new IllegalArgumentException("handler is required");
            }
            this.handler = handler;
        }

        @Override
        public Cancellable schedule(Runnable runnable, long delayMs) {
            handler.postDelayed(runnable, delayMs);
            return () -> handler.removeCallbacks(runnable);
        }
    }

    private static final class ViewAnimationDriver implements AnimationDriver {
        private static final float FALLBACK_HIDDEN_DISTANCE_DP = 96f;

        private final View view;
        private final DecelerateInterpolator interpolator = new DecelerateInterpolator();
        private AnimatorSet animator;

        private ViewAnimationDriver(View view) {
            if (view == null) {
                throw new IllegalArgumentException("controlsView is required");
            }
            this.view = view;
        }

        @Override
        public void snapHidden() {
            cancel();
            view.setAlpha(0f);
            view.setTranslationY(hiddenDistance());
            view.setVisibility(View.INVISIBLE);
        }

        @Override
        public void animateTo(boolean visible, long durationMs, long generation, Completion completion) {
            if (visible) {
                view.setVisibility(View.VISIBLE);
                if (view.getAlpha() == 0f && view.getTranslationY() == 0f) {
                    view.setTranslationY(hiddenDistance());
                }
            }

            ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, visible ? 1f : 0f);
            ObjectAnimator translation = ObjectAnimator.ofFloat(
                    view,
                    View.TRANSLATION_Y,
                    visible ? 0f : hiddenDistance()
            );
            AnimatorSet nextAnimator = new AnimatorSet();
            nextAnimator.playTogether(alpha, translation);
            nextAnimator.setDuration(durationMs);
            nextAnimator.setInterpolator(interpolator);
            nextAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animator == animation) {
                        animator = null;
                    }
                    if (!cancelled) {
                        if (!visible) {
                            view.setVisibility(View.INVISIBLE);
                        }
                        completion.onComplete(generation);
                    }
                }
            });
            animator = nextAnimator;
            nextAnimator.start();
        }

        @Override
        public void cancel() {
            if (animator != null) {
                AnimatorSet activeAnimator = animator;
                animator = null;
                activeAnimator.cancel();
            }
        }

        private float hiddenDistance() {
            int height = view.getHeight();
            if (height > 0) {
                return height;
            }
            return FALLBACK_HIDDEN_DISTANCE_DP * view.getResources().getDisplayMetrics().density;
        }
    }
}
