package top.boluofan.musictv.settings;

import android.content.Context;

public final class LxServerAccountActions {
    private LxServerAccountActions() {
    }

    public static void signOut(Context context) {
        LxServerPreferences.clearUserInfo(context);
    }
}
