package top.boluofan.musictv.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.util.Base64;
import android.util.Log;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LxRetrofitClient {
    private static final String TAG = "LxRetrofitClient";
    private static final String PREFS_NAME = "LxMusicPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_TOKEN = "x-user-token";
    private static final String KEY_QUALITY = "quality";
    private static final String KEY_ADMIN_PASSWORD = "admin_password";
    private static final String KEY_BACKGROUND_PLAY = "background_play";

    private static Retrofit retrofit = null;
    private static String currentBaseUrl = null;

    public static final String QUALITY_FLAC = "flac";
    public static final String QUALITY_320K = "320k";
    public static final String QUALITY_128K = "128k";

    public static Retrofit getClient(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedBaseUrl = prefs.getString(KEY_SERVER_URL, "");
        String baseUrl = normalizeServerUrl(savedBaseUrl);
        if (baseUrl == null) {
            throw new IllegalStateException("LXserver address is missing or invalid");
        }

        if (retrofit != null && baseUrl.equals(currentBaseUrl)) {
            return retrofit;
        }

        currentBaseUrl = baseUrl;
        retrofit = createRetrofit(context, baseUrl);
        return retrofit;
    }

    public static LxApiService createApiService(Context context, String rawUrl) {
        String baseUrl = normalizeServerUrl(rawUrl);
        if (baseUrl == null) throw new IllegalArgumentException("LXserver address is invalid");
        return createRetrofit(context, baseUrl).create(LxApiService.class);
    }

    private static Retrofit createRetrofit(Context context, String baseUrl) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // BODY logging buffers and prints complete playlist/lyric responses,
        // which is expensive on low-memory TVs and can expose account data.
        boolean isDebuggable = (context.getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        logging.setLevel(isDebuggable
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .callTimeout(25, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging);

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(builder.build())
                .build();
    }

    public static LxApiService getApiService(Context context) {
        return getClient(context).create(LxApiService.class);
    }

    public static String getUsername(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USERNAME, "");
    }

    public static String getPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PASSWORD, "");
    }

    public static String getToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TOKEN, "");
    }

    public static void saveToken(Context context, String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_TOKEN, token == null ? "" : token).apply();
    }

    public static String getServerUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, "");
    }

    public static String getPureServerUrl(Context context) {
        String baseUrl = normalizeServerUrl(getServerUrl(context));
        if (baseUrl == null) return null;
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    /** Returns a Retrofit-compatible HTTP(S) base URL, or null when invalid. */
    public static String normalizeServerUrl(String rawUrl) {
        if (rawUrl == null) return null;
        String candidate = rawUrl.trim();
        if (candidate.isEmpty()) return null;
        boolean hasHttpScheme = candidate.regionMatches(true, 0, "http://", 0, 7)
                || candidate.regionMatches(true, 0, "https://", 0, 8);
        if (!hasHttpScheme) {
            candidate = "https://" + candidate;
        }
        HttpUrl parsed = HttpUrl.parse(candidate);
        if (parsed == null || parsed.host().isEmpty()) return null;

        HttpUrl.Builder builder = parsed.newBuilder().query(null).fragment(null);
        if (!parsed.encodedPath().endsWith("/")) builder.addPathSegment("");
        return builder.build().toString();
    }

    public static void saveConfig(Context context, String serverUrl, String username, String password, String token) {
        String normalizedServerUrl = normalizeServerUrl(serverUrl);
        if (normalizedServerUrl == null) {
            throw new IllegalArgumentException("LXserver address is missing or invalid");
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean saved = prefs.edit()
                .putString(KEY_SERVER_URL, normalizedServerUrl)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_TOKEN, token)
                .commit();
        if (!saved) {
            throw new IllegalStateException("Failed to persist LXserver configuration");
        }
        resetClient();
    }

    public static void clearConfig(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        resetClient();
    }

    public static void clearUserInfo(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply();
    }

    public static boolean isLoggedIn(Context context) {
        String username = getUsername(context);
        String password = getPassword(context);
        return !username.isEmpty() && !password.isEmpty();
    }

    public static String getQuality(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_QUALITY, QUALITY_320K);
    }

    public static void setQuality(Context context, String quality) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_QUALITY, quality).apply();
    }

    public static boolean getBackgroundPlay(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_BACKGROUND_PLAY, true);
    }

    public static void setBackgroundPlay(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_BACKGROUND_PLAY, enabled).apply();
    }

    public static void resetClient() {
        retrofit = null;
        currentBaseUrl = null;
    }

    public static String getBasicAuthHeader() {
        Context context = top.boluofan.musictv.MusicTvApp.getInstance();
        if (context == null) return null;
        String username = getUsername(context);
        String password = getPassword(context);
        if (username.isEmpty() || password.isEmpty()) return null;
        String credentials = username + ":" + password;
        return "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
    }

    public static String getAdminAuth(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ADMIN_PASSWORD, null);
    }

    public static void setAdminPassword(Context context, String password) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ADMIN_PASSWORD, password).apply();
    }
}
