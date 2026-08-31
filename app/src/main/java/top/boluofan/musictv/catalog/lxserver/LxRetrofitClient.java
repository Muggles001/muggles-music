package top.boluofan.musictv.catalog.lxserver;

import android.content.Context;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.lxserver.LxServerTransport;
import top.boluofan.musictv.settings.LxServerPreferences;

public final class LxRetrofitClient {
    private LxRetrofitClient() {
    }

    public static LxApiService createApiService(Context context, String rawUrl) {
        return LxServerTransport.createApiService(context, rawUrl);
    }

    public static LxApiService getApiService(Context context) {
        return LxServerTransport.getApiService(context,
                LxServerPreferences.getServerUrl(context));
    }

    public static String getUsername(Context context) {
        return LxServerPreferences.getUsername(context);
    }

    public static String getPassword(Context context) {
        return LxServerPreferences.getPassword(context);
    }

    public static String getToken(Context context) {
        return LxServerPreferences.getToken(context);
    }

    public static void saveToken(Context context, String token) {
        LxServerPreferences.saveToken(context, token);
    }

    public static String getServerUrl(Context context) {
        return LxServerPreferences.getServerUrl(context);
    }

    public static String getPureServerUrl(Context context) {
        String baseUrl = normalizeServerUrl(getServerUrl(context));
        if (baseUrl == null) return null;
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return baseUrl;
    }

    public static String normalizeServerUrl(String rawUrl) {
        return LxServerTransport.normalizeServerUrl(rawUrl);
    }

    public static void saveConfig(Context context, String serverUrl, String username,
                                  String password, String token) {
        String normalizedServerUrl = normalizeServerUrl(serverUrl);
        if (normalizedServerUrl == null) {
            throw new IllegalArgumentException("LXserver address is missing or invalid");
        }
        LxServerPreferences.saveConfig(context, normalizedServerUrl, username, password, token);
        LxServerTransport.resetClient();
    }

    public static void clearUserInfo(Context context) {
        LxServerPreferences.clearUserInfo(context);
    }

    public static boolean isLoggedIn(Context context) {
        return LxServerPreferences.isLoggedIn(context);
    }
}
