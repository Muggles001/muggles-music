package top.boluofan.musictv.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import java.util.concurrent.TimeUnit;
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
        String baseUrl = prefs.getString(KEY_SERVER_URL, "");

        if (baseUrl.isEmpty()) {
            baseUrl = "http://localhost:9527/";
        }

        if (!baseUrl.startsWith("http")) {
            baseUrl = "https://" + baseUrl;
        }

        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        if (retrofit != null && baseUrl.equals(currentBaseUrl)) {
            return retrofit;
        }

        currentBaseUrl = baseUrl;

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging);

        retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(builder.build())
                .build();

        return retrofit;
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

    public static String getServerUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, "");
    }

    public static void saveConfig(Context context, String serverUrl, String username, String password, String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_SERVER_URL, serverUrl)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_TOKEN, token)
                .apply();
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
