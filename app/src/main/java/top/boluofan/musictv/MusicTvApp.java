package top.boluofan.musictv;

import android.app.Application;
import top.boluofan.musictv.settings.AppSettingsPreferences;

public class MusicTvApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppSettingsPreferences.initialize(this);
    }
}
