package top.boluofan.musictv.backend;

import android.content.Context;
import android.content.SharedPreferences;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.source.SourceScriptStore;

public final class BackendPreferences {
    private static final String PREFS = "MugglesBackendPrefs";
    private static final String KEY_MODE = "mode";

    private BackendPreferences() {}

    public static BackendMode getMode(Context context) {
        String saved = preferences(context).getString(KEY_MODE, "");
        if (!saved.isEmpty()) {
            try {
                BackendMode mode = BackendMode.valueOf(saved);
                if (isConfigured(context, mode)) return mode;
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (isConfigured(context, BackendMode.LXSERVER)) return BackendMode.LXSERVER;
        if (isConfigured(context, BackendMode.DIRECT_SOURCE)) return BackendMode.DIRECT_SOURCE;
        return BackendMode.NONE;
    }

    public static void setMode(Context context, BackendMode mode) {
        if (mode == null || mode == BackendMode.NONE) {
            preferences(context).edit().remove(KEY_MODE).apply();
            return;
        }
        if (!isConfigured(context, mode)) {
            throw new IllegalStateException("所选模式尚未配置");
        }
        preferences(context).edit().putString(KEY_MODE, mode.name()).apply();
    }

    public static boolean isConfigured(Context context, BackendMode mode) {
        if (mode == BackendMode.LXSERVER) {
            return LxRetrofitClient.normalizeServerUrl(
                    LxRetrofitClient.getServerUrl(context)) != null;
        }
        if (mode == BackendMode.DIRECT_SOURCE) {
            return new SourceScriptStore(context).getActive() != null;
        }
        return false;
    }

    public static boolean usesLocalLibrary(Context context) {
        return getMode(context) == BackendMode.DIRECT_SOURCE;
    }

    public static void clearMode(Context context) {
        preferences(context).edit().remove(KEY_MODE).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
