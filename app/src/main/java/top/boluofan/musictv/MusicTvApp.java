package top.boluofan.musictv;

import android.app.Application;
import android.content.Context;
import top.boluofan.musictv.settings.AppSettingsPreferences;

public class MusicTvApp extends Application {
    private static MusicTvApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        AppSettingsPreferences.initialize(this);
        instance = this;
    }

    public static MusicTvApp getInstance() {
        return instance;
    }

    public static Context getAppContext() {
        return instance;
    }
}
