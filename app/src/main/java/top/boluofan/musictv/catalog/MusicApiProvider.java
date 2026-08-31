package top.boluofan.musictv.catalog;

import android.content.Context;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.direct.DirectLxApiService;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;

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
