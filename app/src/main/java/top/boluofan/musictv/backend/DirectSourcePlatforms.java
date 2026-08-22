package top.boluofan.musictv.backend;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.source.ImportedSource;
import top.boluofan.musictv.source.SourceScriptStore;

public final class DirectSourcePlatforms {
    public static final String[] CODES = {"mg", "kw", "kg", "tx", "wy"};
    public static final String[] NAMES = {"咪咕", "酷我", "酷狗", "QQ音乐", "网易云"};

    private DirectSourcePlatforms() {}

    public static String[] codes(Context context) {
        ImportedSource active = new SourceScriptStore(context).getActive();
        if (active == null) return new String[0];
        List<String> result = new ArrayList<>();
        for (String code : CODES) {
            if (active.capabilities.has(code)) result.add(code);
        }
        return result.toArray(new String[0]);
    }

    public static String[] names(Context context) {
        ImportedSource active = new SourceScriptStore(context).getActive();
        if (active == null) return new String[0];
        List<String> result = new ArrayList<>();
        for (int i = 0; i < CODES.length; i++) {
            if (active.capabilities.has(CODES[i])) result.add(NAMES[i]);
        }
        return result.toArray(new String[0]);
    }
}
