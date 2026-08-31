package top.boluofan.musictv.ui.background;

import android.app.ActivityManager;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;

import top.boluofan.musictv.settings.AppSettingsPreferences;

public final class FluidBackgroundView extends GLSurfaceView {
    private static final String TAG = "FluidBackground";
    private static final long STILL_MOTION_POLL_MILLIS = 250L;

    private FluidBackgroundRenderer renderer;
    private boolean surfaceInitialized;
    private boolean rendererAvailable;
    private boolean surfaceResumed;
    private boolean hostResumed;
    private AppSettingsPreferences.BrowseMotion activeMotion;
    private final Handler frameHandler = new Handler(Looper.getMainLooper());
    private AppSettingsPreferences settingsPreferences;
    private FluidRendererPlan.FrameCadence frameCadence;
    private boolean frameCallbackPosted;
    private int surfaceBufferWidth;
    private int surfaceBufferHeight;
    private final Choreographer.FrameCallback frameCallback = this::onDisplayFrame;
    private final Runnable stillMotionPoll = new Runnable() {
        @Override
        public void run() {
            if (!hostResumed || activeMotion != AppSettingsPreferences.BrowseMotion.STILL) return;
            AppSettingsPreferences.BrowseMotion motion = currentMotion();
            if (motion != activeMotion) {
                applyMotion(motion);
            } else {
                frameHandler.postDelayed(this, STILL_MOTION_POLL_MILLIS);
            }
        }
    };

    public FluidBackgroundView(Context context) {
        this(context, null);
    }

    public FluidBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        settingsPreferences = new AppSettingsPreferences(context);
        AppSettingsPreferences.BrowseMotion motion = currentMotion();
        activeMotion = motion;
        if (motion == AppSettingsPreferences.BrowseMotion.STILL) {
            setVisibility(View.INVISIBLE);
            return;
        }
        initializeRenderer(context, motion);
    }

    private void initializeRenderer(
            Context context, AppSettingsPreferences.BrowseMotion motion) {
        if (surfaceInitialized) return;
        if (!supportsGles3(context)) {
            setVisibility(View.INVISIBLE);
            return;
        }
        try {
            setEGLContextClientVersion(3);
            setEGLConfigChooser(8, 8, 8, 0, 0, 0);
            setPreserveEGLContextOnPause(true);
            renderer = new FluidBackgroundRenderer(
                    context.getApplicationContext(), FluidBackgroundPolicy.profileFor(motion),
                    this::handleRendererUnavailable);
            setRenderer(renderer);
            setRenderMode(RENDERMODE_WHEN_DIRTY);
            surfaceInitialized = true;
            rendererAvailable = true;
        } catch (RuntimeException error) {
            Log.w(TAG, "OpenGL background unavailable; using static mint fallback", error);
            setVisibility(View.INVISIBLE);
        }
    }

    public void resumeRendering() {
        hostResumed = true;
        applyMotion(currentMotion());
    }

    private void applyMotion(AppSettingsPreferences.BrowseMotion motion) {
        stopFrameScheduling();
        activeMotion = motion;
        if (motion == AppSettingsPreferences.BrowseMotion.STILL) {
            pauseSurface();
            setVisibility(View.INVISIBLE);
            // Static mode has no frame callbacks to observe a later preference change.
            if (hostResumed) frameHandler.postDelayed(stillMotionPoll, STILL_MOTION_POLL_MILLIS);
            return;
        }
        if (!surfaceInitialized) initializeRenderer(getContext(), motion);
        if (!surfaceInitialized) return;
        FluidBackgroundPolicy.Profile profile = FluidBackgroundPolicy.profileFor(motion);
        updateSurfaceBufferSize(profile, getWidth(), getHeight());
        setVisibility(rendererAvailable ? View.VISIBLE : View.INVISIBLE);
        if (!surfaceResumed) {
            onResume();
            surfaceResumed = true;
        }
        if (!rendererAvailable) return;
        frameCadence = new FluidRendererPlan.FrameCadence(profile.framesPerSecond);
        queueEvent(() -> renderer.applyProfile(profile));
        scheduleDisplayFrame();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (!surfaceInitialized
                || activeMotion == AppSettingsPreferences.BrowseMotion.STILL) return;
        updateSurfaceBufferSize(
                FluidBackgroundPolicy.profileFor(activeMotion), width, height);
    }

    private void updateSurfaceBufferSize(
            FluidBackgroundPolicy.Profile profile, int width, int height) {
        if (width <= 0 || height <= 0) return;
        FluidRendererPlan.RenderSize size = FluidRendererPlan.cappedRenderSize(
                width,
                height,
                profile.surfaceTargetMaxWidth,
                profile.surfaceTargetMaxHeight);
        if (surfaceBufferWidth == size.width && surfaceBufferHeight == size.height) return;
        surfaceBufferWidth = size.width;
        surfaceBufferHeight = size.height;
        getHolder().setFixedSize(size.width, size.height);
    }

    public void pauseRendering() {
        hostResumed = false;
        stopFrameScheduling();
        pauseSurface();
    }

    private void onDisplayFrame(long frameTimeNanos) {
        frameCallbackPosted = false;
        if (!hostResumed || !surfaceInitialized || !rendererAvailable
                || activeMotion == AppSettingsPreferences.BrowseMotion.STILL) return;

        AppSettingsPreferences.BrowseMotion motion = currentMotion();
        if (motion != activeMotion) {
            applyMotion(motion);
            return;
        }
        if (frameCadence != null && frameCadence.shouldRender(frameTimeNanos)) requestRender();
        scheduleDisplayFrame();
    }

    private void scheduleDisplayFrame() {
        if (frameCallbackPosted || !hostResumed || !surfaceInitialized || !rendererAvailable
                || activeMotion == AppSettingsPreferences.BrowseMotion.STILL) return;
        frameCallbackPosted = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void stopFrameScheduling() {
        frameHandler.removeCallbacks(stillMotionPoll);
        if (!frameCallbackPosted) return;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameCallbackPosted = false;
    }

    private AppSettingsPreferences.BrowseMotion currentMotion() {
        return settingsPreferences.getBrowseMotion();
    }

    private void pauseSurface() {
        if (!surfaceInitialized || !surfaceResumed) return;
        onPause();
        surfaceResumed = false;
    }

    private void handleRendererUnavailable() {
        post(() -> {
            rendererAvailable = false;
            stopFrameScheduling();
            setVisibility(View.INVISIBLE);
        });
    }

    private static boolean supportsGles3(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return manager != null
                && FluidBackgroundPolicy.supportsGlesVersion(
                        manager.getDeviceConfigurationInfo().reqGlEsVersion);
    }
}
