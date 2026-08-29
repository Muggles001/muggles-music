package top.boluofan.musictv.ui.background;

import android.app.ActivityManager;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import top.boluofan.musictv.settings.AppSettingsPreferences;

public final class FluidBackgroundView extends GLSurfaceView {
    private static final String TAG = "FluidBackground";

    private FluidBackgroundRenderer renderer;
    private boolean surfaceInitialized;
    private boolean rendererAvailable;
    private boolean surfaceResumed;
    private boolean hostResumed;
    private AppSettingsPreferences.BrowseMotion activeMotion;
    private final Handler frameHandler = new Handler(Looper.getMainLooper());
    private AppSettingsPreferences settingsPreferences;
    private final Runnable reducedFrame = new Runnable() {
        @Override
        public void run() {
            if (!hostResumed || !surfaceInitialized
                    || activeMotion != AppSettingsPreferences.BrowseMotion.REDUCED) {
                return;
            }
            requestRender();
            frameHandler.postDelayed(this, 33L);
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
            setRenderMode(motion == AppSettingsPreferences.BrowseMotion.REDUCED
                    ? RENDERMODE_WHEN_DIRTY : RENDERMODE_CONTINUOUSLY);
            surfaceInitialized = true;
            rendererAvailable = true;
        } catch (RuntimeException error) {
            Log.w(TAG, "OpenGL background unavailable; using static mint fallback", error);
            setVisibility(View.INVISIBLE);
        }
    }

    public void resumeRendering() {
        hostResumed = true;
        AppSettingsPreferences.BrowseMotion motion = currentMotion();
        activeMotion = motion;
        if (motion == AppSettingsPreferences.BrowseMotion.STILL) {
            frameHandler.removeCallbacks(reducedFrame);
            pauseSurface();
            setVisibility(View.INVISIBLE);
            return;
        }
        if (!surfaceInitialized) initializeRenderer(getContext(), motion);
        if (!surfaceInitialized) return;
        setVisibility(rendererAvailable ? View.VISIBLE : View.INVISIBLE);
        if (!surfaceResumed) {
            onResume();
            surfaceResumed = true;
        }
        if (rendererAvailable) queueEvent(renderer::resetWallClock);
        applyRenderCadence(motion);
    }

    public void pauseRendering() {
        hostResumed = false;
        frameHandler.removeCallbacks(reducedFrame);
        pauseSurface();
    }

    private void applyRenderCadence(AppSettingsPreferences.BrowseMotion motion) {
        frameHandler.removeCallbacks(reducedFrame);
        if (motion == AppSettingsPreferences.BrowseMotion.REDUCED) {
            setRenderMode(RENDERMODE_WHEN_DIRTY);
            frameHandler.post(reducedFrame);
        } else {
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }
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
            frameHandler.removeCallbacks(reducedFrame);
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
