package top.boluofan.musictv.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import top.boluofan.musictv.settings.AppSettingsPreferences;

public class LxRetrofitClient {
    private static final String PREFS_NAME = "LxMusicPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_TOKEN = "x-user-token";

    private static Retrofit retrofit = null;
    private static String currentBaseUrl = null;

    private static Retrofit getClient(Context context) {
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

    public static void clearUserInfo(Context context) {
        AppSettingsPreferences.initialize(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .remove(KEY_TOKEN)
            .apply();
    }

    public static boolean isLoggedIn(Context context) {
        String username = getUsername(context);
        String password = getPassword(context);
        return !username.isEmpty() && !password.isEmpty();
    }

    private static void resetClient() {
        retrofit = null;
        currentBaseUrl = null;
    }

}
