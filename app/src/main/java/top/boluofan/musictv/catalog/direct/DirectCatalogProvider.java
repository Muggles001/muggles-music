package top.boluofan.musictv.catalog.direct;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import top.boluofan.musictv.catalog.model.LyricInfo;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;
import top.boluofan.musictv.catalog.direct.DirectHttpTransport;
import top.boluofan.musictv.catalog.direct.DirectPlatformCatalog;
import top.boluofan.musictv.catalog.direct.kg.KgCatalog;
import top.boluofan.musictv.catalog.direct.kw.KwCatalog;
import top.boluofan.musictv.catalog.direct.mg.MgCatalog;
import top.boluofan.musictv.catalog.direct.tx.TxCatalog;

final class DirectCatalogProvider {
    static final String[] PLATFORM_ORDER = {"mg", "kw", "kg", "tx", "wy"};

    private static final Map<String, String[][]> BOARDS = new LinkedHashMap<>();
    static {
        BOARDS.put("mg", new String[][]{
                {"mg__27553319", "新歌榜"}, {"mg__27186466", "热歌榜"},
                {"mg__27553408", "原创榜"}, {"mg__75959118", "音乐风向榜"},
                {"mg__76557036", "彩铃分贝榜"}, {"mg__76557745", "会员臻爱榜"},
                {"mg__23189800", "港台榜"}, {"mg__23189399", "内地榜"},
                {"mg__19190036", "欧美榜"}, {"mg__83176390", "国风金曲榜"}
        });
        BOARDS.put("kw", new String[][]{
                {"kw__93", "飙升榜"}, {"kw__17", "新歌榜"}, {"kw__16", "热歌榜"},
                {"kw__158", "抖音热歌榜"}, {"kw__284", "热评榜"},
                {"kw__290", "ACG新歌榜"}, {"kw__187", "流行趋势榜"},
                {"kw__26", "经典怀旧榜"}, {"kw__104", "华语榜"}, {"kw__22", "欧美榜"}
        });
        BOARDS.put("kg", new String[][]{
                {"kg__8888", "TOP500"}, {"kg__6666", "飙升榜"},
                {"kg__59703", "流行音乐榜"}, {"kg__52144", "抖音热歌榜"},
                {"kg__24971", "DJ热歌榜"}, {"kg__23784", "网络红歌榜"},
                {"kg__31308", "内地榜"}, {"kg__33160", "电音榜"},
                {"kg__31310", "欧美榜"}, {"kg__33162", "ACG新歌榜"}
        });
        BOARDS.put("tx", new String[][]{
                {"tx__4", "流行指数榜"}, {"tx__26", "热歌榜"}, {"tx__27", "新歌榜"},
                {"tx__62", "飙升榜"}, {"tx__58", "说唱榜"}, {"tx__57", "电音榜"},
                {"tx__28", "网络歌曲榜"}, {"tx__5", "内地榜"},
                {"tx__3", "欧美榜"}, {"tx__16", "韩国榜"}
        });
    }

    private final Map<String, DirectPlatformCatalog> catalogs = new LinkedHashMap<>();

    DirectCatalogProvider() {
        this(new DirectHttpTransport());
    }

    DirectCatalogProvider(DirectHttpTransport transport) {
        catalogs.put("kw", new KwCatalog(transport));
        catalogs.put("kg", new KgCatalog(transport));
        catalogs.put("tx", new TxCatalog(transport));
        catalogs.put("mg", new MgCatalog(transport));
    }

    List<MusicInfo> search(String source, String keyword, int page, int limit) throws IOException {
        DirectPlatformCatalog catalog = catalogs.get(source);
        return catalog == null ? Collections.emptyList() : catalog.search(keyword, page, limit);
    }

    JsonObject playlistList(String source, String tagId, String sortId, int page)
            throws IOException {
        DirectPlatformCatalog catalog = catalogs.get(source);
        return catalog == null ? emptyListResult(page, 30)
                : catalog.playlistList(tagId, sortId, page);
    }

    Playlist playlistDetail(String source, String id, int page) throws IOException {
        DirectPlatformCatalog catalog = catalogs.get(source);
        return catalog == null ? new Playlist() : catalog.playlistDetail(id, page);
    }

    JsonArray boards(String source) {
        JsonArray result = new JsonArray();
        String[][] boards = BOARDS.get(source);
        if (boards == null) return result;
        for (String[] board : boards) {
            JsonObject item = new JsonObject();
            item.addProperty("id", board[0]);
            item.addProperty("name", board[1]);
            result.add(item);
        }
        return result;
    }

    List<MusicInfo> boardSongs(String source, String rawId, int page) throws IOException {
        DirectPlatformCatalog catalog = catalogs.get(source);
        if (catalog == null) return Collections.emptyList();
        String id = rawId == null ? "" : rawId.replace(source + "__", "");
        return catalog.boardSongs(id, page);
    }

    LyricInfo lyric(String source, Map<String, String> params) throws IOException {
        DirectPlatformCatalog catalog = catalogs.get(source);
        return catalog == null ? new LyricInfo() : catalog.lyric(params);
    }

    private static JsonObject emptyListResult(int page, int limit) {
        JsonObject result = new JsonObject();
        result.add("list", new JsonArray());
        result.addProperty("total", 0);
        result.addProperty("limit", limit);
        result.addProperty("page", page);
        return result;
    }
}
