package top.boluofan.musictv.api.model;

public class SourceInfo {
    private final String id;
    private final String name;
    private final String displayName;

    public static final SourceInfo KW = new SourceInfo("kw", "kuwo", "酷我音乐");
    public static final SourceInfo KG = new SourceInfo("kg", "kugou", "酷狗音乐");
    public static final SourceInfo TX = new SourceInfo("tx", "tencent", "QQ音乐");
    public static final SourceInfo WY = new SourceInfo("wy", "wy", "网易云");
    public static final SourceInfo MG = new SourceInfo("mg", "migu", "咪咕音乐");
    public static final SourceInfo ALL = new SourceInfo("all", "all", "全部");

    private static final SourceInfo[] SOURCES = {ALL, KW, KG, TX, WY, MG};

    public SourceInfo(String id, String name, String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SourceInfo[] getAllSources() {
        return SOURCES;
    }

    public static SourceInfo getSourceById(String id) {
        if (id == null) return ALL;
        for (SourceInfo source : SOURCES) {
            if (source.id.equals(id)) {
                return source;
            }
        }
        return ALL;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
