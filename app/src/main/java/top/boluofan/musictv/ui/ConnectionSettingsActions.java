package top.boluofan.musictv.ui;

import android.content.Context;
import android.content.Intent;
import top.boluofan.musictv.ConfigActivity;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.source.SourceRuntimeManager;
import top.boluofan.musictv.source.SourceScriptStore;

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

    static boolean rollbackSource(Context context) {
        return SourceRuntimeManager.get(context).rollbackActive() != null;
    }

    static boolean removeSource(Context context) {
        if (!new SourceScriptStore(context).clear()) return false;
        SourceRuntimeManager.get(context).invalidate();
        BackendPreferences.clearMode(context);
        return true;
    }

    static void signOutServer(Context context) {
        LxRetrofitClient.clearUserInfo(context);
    }
}
