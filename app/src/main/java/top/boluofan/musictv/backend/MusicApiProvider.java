package top.boluofan.musictv.backend;

import android.content.Context;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;

public final class MusicApiProvider {
    private static volatile DirectLxApiService directService;

    private MusicApiProvider() {}

    public static LxApiService get(Context context) {
        if (BackendPreferences.getMode(context) == BackendMode.DIRECT_SOURCE) {
            if (directService == null) {
                synchronized (MusicApiProvider.class) {
                    if (directService == null) {
                        directService = new DirectLxApiService(context.getApplicationContext());
                    }
                }
            }
            return directService;
        }
        return LxRetrofitClient.getApiService(context);
    }
}
