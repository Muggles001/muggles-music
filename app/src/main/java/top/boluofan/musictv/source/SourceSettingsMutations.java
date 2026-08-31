package top.boluofan.musictv.source;

import android.content.Context;
import top.boluofan.musictv.settings.BackendPreferences;

public final class SourceSettingsMutations {
    private SourceSettingsMutations() {
    }

    public static boolean rollback(Context context) {
        return SourceRuntimeManager.get(context).rollbackActive() != null;
    }

    public static boolean remove(Context context) {
        if (!new SourceScriptStore(context).clear()) return false;
        SourceRuntimeManager.get(context).invalidate();
        BackendPreferences.clearMode(context);
        return true;
    }
}
