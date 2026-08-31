package top.boluofan.musictv.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class LxServerPreferences {
    private static final String PREFS_NAME = "LxMusicPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_TOKEN = "x-user-token";

    private LxServerPreferences() {
    }

    public static String getServerUrl(Context context) {
        return preferences(context).getString(KEY_SERVER_URL, "");
    }

    public static String getUsername(Context context) {
        return preferences(context).getString(KEY_USERNAME, "");
    }

    public static String getPassword(Context context) {
        return preferences(context).getString(KEY_PASSWORD, "");
    }

    public static String getToken(Context context) {
        return preferences(context).getString(KEY_TOKEN, "");
    }

    public static void saveToken(Context context, String token) {
        preferences(context).edit().putString(KEY_TOKEN, token == null ? "" : token).apply();
    }

    public static void saveConfig(Context context, String serverUrl, String username,
                                  String password, String token) {
        boolean saved = preferences(context).edit()
                .putString(KEY_SERVER_URL, serverUrl)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_TOKEN, token)
                .commit();
        if (!saved) throw new IllegalStateException("Failed to persist LXserver configuration");
    }

    public static void clearUserInfo(Context context) {
        AppSettingsPreferences.initialize(context);
        preferences(context).edit()
                .remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
                .remove(KEY_TOKEN)
                .apply();
    }

    public static boolean isLoggedIn(Context context) {
        return !getUsername(context).isEmpty() && !getPassword(context).isEmpty();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
