package top.boluofan.musictv.ui.settings;

import android.content.Context;
import android.content.Intent;
import top.boluofan.musictv.ui.settings.connection.ConfigActivity;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;

final class ConnectionSettingsActions {
    private ConnectionSettingsActions() {}

    static Intent createSetupIntent(Context context, boolean switchMode) {
        BackendMode selectedMode = selectedMode(BackendPreferences.getMode(context), switchMode);
        return new Intent(context, ConfigActivity.class)
                .putExtra(ConfigActivity.EXTRA_INITIAL_MODE, selectedMode.name())
                .putExtra("server_url", LxRetrofitClient.getServerUrl(context))
                .putExtra("username", LxRetrofitClient.getUsername(context));
    }

    static BackendMode selectedMode(BackendMode currentMode, boolean switchMode) {
        if (!switchMode) return currentMode;
        return currentMode == BackendMode.LXSERVER
                ? BackendMode.DIRECT_SOURCE : BackendMode.LXSERVER;
    }

}
