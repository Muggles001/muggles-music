package top.boluofan.musictv.catalog.direct;

import android.content.Context;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.source.ImportedSource;
import top.boluofan.musictv.source.SourceScriptStore;

public final class DirectSourcePlatforms {
    private static final String[] CODES = {"mg", "kw", "kg", "tx", "wy"};
    private static final String[] NAMES = {"咪咕", "酷我", "酷狗", "QQ音乐", "网易云"};
    private static final Snapshot EMPTY = new Snapshot(new String[0], new String[0]);

    private DirectSourcePlatforms() {}

    public static Snapshot snapshot(Context context) {
        ImportedSource active = new SourceScriptStore(context).getActive();
        return active == null ? EMPTY : snapshot(active.capabilities);
    }

    static Snapshot snapshot(JsonObject capabilities) {
        if (capabilities == null) return EMPTY;
        List<String> codes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < CODES.length; i++) {
            if (!capabilities.has(CODES[i])) continue;
            codes.add(CODES[i]);
            names.add(NAMES[i]);
        }
        return new Snapshot(codes.toArray(new String[0]), names.toArray(new String[0]));
    }

    public static final class Snapshot {
        private final String[] codes;
        private final String[] names;

        private Snapshot(String[] codes, String[] names) {
            this.codes = codes;
            this.names = names;
        }

        public String[] codes() {
            return codes.clone();
        }

        public String[] names() {
            return names.clone();
        }
    }
}
