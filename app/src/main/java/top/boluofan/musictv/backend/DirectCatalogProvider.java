package top.boluofan.musictv.backend;

import android.text.Html;
import android.util.Base64;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import top.boluofan.musictv.api.model.LyricInfo;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.Playlist;

final class DirectCatalogProvider {
    static final String[] PLATFORM_ORDER = {"mg", "kw", "kg", "tx", "wy"};
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final Pattern KUWO_QUALITY = Pattern.compile(
            "bitrate:(\\d+),format:[^,;]+,size:([^;]+)");
    private static final Pattern KUGOU_EMBEDDED_LIST = Pattern.compile(
            "global\\.data\\s*=\\s*(\\[[\\s\\S]*?]);");

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

    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(25, TimeUnit.SECONDS)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    List<MusicInfo> search(String source, String keyword, int page, int limit) throws IOException {
        switch (source) {
            case "kw": return searchKuwo(keyword, page, limit);
            case "kg": return searchKugou(keyword, page, limit);
            case "tx": return searchTencent(keyword, page, limit);
            case "mg": return searchMigu(keyword, page, limit);
            default: return Collections.emptyList();
        }
    }

    JsonObject playlistList(String source, String tagId, String sortId, int page) throws IOException {
        switch (source) {
            case "kw": return kuwoPlaylistList(tagId, sortId, page);
            case "kg": return kugouPlaylistList(tagId, sortId, page);
            case "tx": return tencentPlaylistList(tagId, sortId, page);
            case "mg": return miguPlaylistList(tagId, page);
            default: return emptyListResult(page, 30);
        }
    }

    Playlist playlistDetail(String source, String id, int page) throws IOException {
        switch (source) {
            case "kw": return kuwoPlaylistDetail(id, page);
            case "kg": return kugouPlaylistDetail(id);
            case "tx": return tencentPlaylistDetail(id);
            case "mg": return miguPlaylistDetail(id, page);
            default: return new Playlist();
        }
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
        String id = rawId == null ? "" : rawId.replace(source + "__", "");
        switch (source) {
            case "kw": return kuwoBoardSongs(id, page);
            case "kg": return kugouBoardSongs(id, page);
            case "tx": return tencentBoardSongs(id);
            case "mg": return miguBoardSongs(id);
            default: return Collections.emptyList();
        }
    }

    LyricInfo lyric(String source, Map<String, String> params) throws IOException {
        switch (source) {
            case "kw": return kuwoLyric(params.get("songmid"));
            case "kg": return kugouLyric(params);
            case "tx": return tencentLyric(params.get("songmid"));
            case "mg": return miguLyric(params);
            default: return new LyricInfo();
        }
    }

    private List<MusicInfo> searchKuwo(String keyword, int page, int limit) throws IOException {
        String url = "https://search.kuwo.cn/r.s?client=kt&all=" + encode(keyword)
                + "&pn=" + Math.max(0, page - 1) + "&rn=" + limit
                + "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1"
                + "&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012"
                + "&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1";
        return mapSongs(array(getJson(url), "abslist"), "kw");
    }

    private List<MusicInfo> searchKugou(String keyword, int page, int limit) throws IOException {
        String url = "https://songsearch.kugou.com/song_search_v2?keyword=" + encode(keyword)
                + "&page=" + page + "&pagesize=" + limit
                + "&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1"
                + "&privilege_filter=0&area_code=1";
        return mapSongs(array(object(getJson(url), "data"), "lists"), "kg");
    }

    private List<MusicInfo> searchTencent(String keyword, int page, int limit) throws IOException {
        JsonObject comm = new JsonObject();
        comm.addProperty("ct", "11");
        comm.addProperty("cv", "14090508");
        comm.addProperty("v", "14090508");
        comm.addProperty("tmeAppID", "qqmusic");
        comm.addProperty("phonetype", "EBG-AN10");
        comm.addProperty("deviceScore", "553.47");
        comm.addProperty("devicelevel", "50");
        comm.addProperty("newdevicelevel", "20");
        comm.addProperty("rom", "HuaWei/EMOTION/EmotionUI_14.2.0");
        comm.addProperty("os_ver", "12");
        for (String key : new String[]{"OpenUDID", "OpenUDID2", "QIMEI36", "udid", "chid",
                "aid", "oaid", "taid", "tid", "wid", "uid", "sid"}) comm.addProperty(key, "0");
        comm.addProperty("modeSwitch", "6");
        comm.addProperty("teenMode", "0");
        comm.addProperty("ui_mode", "2");
        comm.addProperty("nettype", "1020");
        comm.addProperty("v4ip", "");

        JsonObject param = new JsonObject();
        param.addProperty("search_type", 0);
        param.addProperty("query", keyword);
        param.addProperty("page_num", page);
        param.addProperty("num_per_page", limit);
        param.addProperty("highlight", 0);
        param.addProperty("nqc_flag", 0);
        param.addProperty("multi_zhida", 0);
        param.addProperty("cat", 2);
        param.addProperty("grp", 1);
        param.addProperty("sin", 0);
        param.addProperty("sem", 0);
        JsonObject req = new JsonObject();
        req.addProperty("module", "music.search.SearchCgiService");
        req.addProperty("method", "DoSearchForQQMusicMobile");
        req.add("param", param);
        JsonObject body = new JsonObject();
        body.add("comm", comm);
        body.add("req", req);
        JsonObject root = postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body,
                Collections.singletonMap("User-Agent", "QQMusic 14090508(android 12)"));
        JsonObject data = object(object(root, "req"), "data");
        return mapSongs(array(object(data, "body"), "item_song"), "tx");
    }

    private List<MusicInfo> searchMigu(String keyword, int page, int limit) throws IOException {
        String searchSwitch = "{\"song\":1,\"album\":0,\"singer\":0,\"tagSong\":0,"
                + "\"mvSong\":0,\"songlist\":0,\"bestShow\":0}";
        String url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do"
                + "?isCopyright=1&isCorrect=1&pageNo=" + page + "&pageSize=" + limit
                + "&searchSwitch=" + encode(searchSwitch) + "&sort=0&text=" + encode(keyword);
        JsonArray nested = array(object(getJson(url), "songResultData"), "resultList");
        JsonArray flattened = new JsonArray();
        for (JsonElement group : nested) {
            if (!group.isJsonArray()) continue;
            for (JsonElement song : group.getAsJsonArray()) flattened.add(song);
        }
        return mapSongs(flattened, "mg");
    }

    private JsonObject kuwoPlaylistList(String tagId, String sortId, int page) throws IOException {
        String url;
        if (tagId == null || tagId.isEmpty()) {
            url = "https://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList"
                    + "?loginUid=0&loginSid=0&appUid=76039576&pn=" + page
                    + "&rn=36&order=" + (sortId == null || sortId.isEmpty() ? "hot" : sortId);
        } else {
            String id = tagId.contains("-") ? tagId.substring(0, tagId.indexOf('-')) : tagId;
            url = "https://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList"
                    + "?loginUid=0&loginSid=0&appUid=76039576&pn=" + page
                    + "&id=" + encode(id) + "&rn=36";
        }
        JsonObject data = object(getJson(url), "data");
        return playlistResult(mapPlaylists(array(data, "data"), "kw"),
                integer(data, "total", 0), 36, page);
    }

    private JsonObject kugouPlaylistList(String tagId, String sortId, int page) throws IOException {
        String order = sortId == null || sortId.isEmpty() || "hot".equals(sortId) ? "5" : sortId;
        String url = "http://www2.kugou.kugou.com/yueku/v9/special/getSpecial"
                + "?is_ajax=1&cdn=cdn&t=" + encode(order) + "&c="
                + encode(tagId == null ? "" : tagId) + "&p=" + page;
        JsonObject root = getJson(url);
        JsonArray list = mapPlaylists(array(root, "special_db"), "kg");
        return playlistResult(list, integer(object(root, "data"), "total", page * 20 + 20), 20, page);
    }

    private JsonObject tencentPlaylistList(String tagId, String sortId, int page) throws IOException {
        int order = parseInt(sortId, 5);
        JsonObject param = new JsonObject();
        param.addProperty("id", 10000000);
        param.addProperty("sin", 36 * Math.max(0, page - 1));
        param.addProperty("size", 36);
        param.addProperty("order", order);
        param.addProperty("cur_page", page);
        JsonObject playlist = new JsonObject();
        playlist.addProperty("method", "get_playlist_by_tag");
        playlist.addProperty("module", "playlist.PlayListPlazaServer");
        playlist.add("param", param);
        JsonObject comm = new JsonObject();
        comm.addProperty("cv", 1602);
        comm.addProperty("ct", 20);
        JsonObject data = new JsonObject();
        data.add("comm", comm);
        data.add("playlist", playlist);
        String url = "https://u.y.qq.com/cgi-bin/musicu.fcg?loginUin=0&hostUin=0"
                + "&format=json&inCharset=utf-8&outCharset=utf-8&notice=0"
                + "&platform=wk_v15.json&needNewCode=0&data=" + encode(GSON.toJson(data));
        JsonObject result = object(object(getJson(url), "playlist"), "data");
        return playlistResult(mapPlaylists(array(result, "v_playlist"), "tx"),
                integer(result, "total", 0), 36, page);
    }

    private JsonObject miguPlaylistList(String tagId, int page) throws IOException {
        String url = tagId == null || tagId.isEmpty()
                ? "https://app.c.nf.migu.cn/pc/bmw/page-data/playlist-square-recommend/v1.0"
                    + "?templateVersion=2&pageNo=" + page
                : "https://app.c.nf.migu.cn/pc/v1.0/template/musiclistplaza-listbytag/release"
                    + "?pageNumber=" + page + "&templateVersion=2&tagId=" + encode(tagId);
        JsonArray found = new JsonArray();
        collectMiguPlaylists(object(getJson(url), "data"), found, new LinkedHashSet<>());
        return playlistResult(found, page * 30 + (found.size() >= 30 ? 30 : 0), 30, page);
    }

    private Playlist kuwoPlaylistDetail(String rawId, int page) throws IOException {
        String id = normalizeNumericId(rawId);
        String url = "https://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=" + encode(id)
                + "&pn=" + Math.max(0, page - 1)
                + "&rn=1000&encode=utf8&keyset=pl2012&identity=kuwo"
                + "&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1";
        JsonObject root = getJson(url);
        Playlist playlist = playlistInfo(id, "kw", string(root, "title", "酷我歌单"),
                string(root, "pic", ""), string(root, "info", ""),
                string(root, "uname", ""), integer(root, "total", 0));
        playlist.setSongs(mapSongs(array(root, "musiclist"), "kw"));
        return playlist;
    }

    private Playlist kugouPlaylistDetail(String rawId) throws IOException {
        String id = normalizeNumericId(rawId);
        String html = getText("http://www2.kugou.kugou.com/yueku/v9/special/single/"
                + encode(id) + "-5-9999.html", Collections.emptyMap());
        Matcher matcher = KUGOU_EMBEDDED_LIST.matcher(html);
        if (!matcher.find()) throw new IOException("酷狗歌单详情解析失败");
        JsonElement parsed;
        try {
            parsed = new JsonParser().parse(matcher.group(1));
        } catch (Exception error) {
            throw new IOException("酷狗歌单歌曲数据无效", error);
        }
        JsonArray songs = parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        Playlist playlist = playlistInfo(id, "kg", "酷狗歌单", "", "", "", songs.size());
        playlist.setSongs(mapSongs(songs, "kg"));
        return playlist;
    }

    private Playlist tencentPlaylistDetail(String rawId) throws IOException {
        String id = normalizeNumericId(rawId);
        String url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg"
                + "?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=" + encode(id)
                + "&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8"
                + "&notice=0&platform=yqq.json&needNewCode=0";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Origin", "https://y.qq.com");
        headers.put("Referer", "https://y.qq.com/n/yqq/playsquare/" + id + ".html");
        JsonArray cdlist = array(getJson(url, headers), "cdlist");
        JsonObject detail = cdlist.size() > 0 && cdlist.get(0).isJsonObject()
                ? cdlist.get(0).getAsJsonObject() : new JsonObject();
        JsonArray songs = array(detail, "songlist");
        Playlist playlist = playlistInfo(id, "tx", string(detail, "dissname", "QQ音乐歌单"),
                string(detail, "logo", ""), decode(string(detail, "desc", "")),
                string(detail, "nickname", ""), songs.size());
        playlist.setSongs(mapSongs(songs, "tx"));
        return playlist;
    }

    private Playlist miguPlaylistDetail(String rawId, int page) throws IOException {
        String id = normalizeNumericId(rawId);
        Map<String, String> headers = miguHeaders();
        String songsUrl = "https://app.c.nf.migu.cn/MIGUM3.0/resource/playlist/song/v2.0"
                + "?pageNo=" + page + "&pageSize=1000&playlistId=" + encode(id);
        JsonObject songData = object(getJson(songsUrl, headers), "data");
        String infoUrl = "https://c.musicapp.migu.cn/MIGUM3.0/resource/playlist/v2.0"
                + "?playlistId=" + encode(id);
        JsonObject info = object(getJson(infoUrl, headers), "data");
        Playlist playlist = playlistInfo(id, "mg", string(info, "title", "咪咕歌单"),
                string(object(info, "imgItem"), "img", ""), string(info, "summary", ""),
                string(info, "ownerName", ""), integer(songData, "totalCount", 0));
        playlist.setSongs(mapSongs(array(songData, "songList"), "mg"));
        return playlist;
    }

    private List<MusicInfo> kuwoBoardSongs(String id, int page) throws IOException {
        String url = "https://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn="
                + Math.max(0, page - 1) + "&rn=100&type=bang&data=content&id=" + encode(id)
                + "&show_copyright_off=0&pcmp4=1&isbang=1";
        return mapSongs(array(getJson(url), "musiclist"), "kw");
    }

    private List<MusicInfo> kugouBoardSongs(String id, int page) throws IOException {
        String url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108"
                + "&ranktype=1&plat=0&pagesize=100&area_code=1&page=" + page
                + "&rankid=" + encode(id) + "&with_res_tag=0&show_portrait_mv=1";
        return mapSongs(array(object(getJson(url), "data"), "info"), "kg");
    }

    private List<MusicInfo> tencentBoardSongs(String id) throws IOException {
        String url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?topid=" + encode(id)
                + "&page=detail&type=top&song_begin=0&song_num=300&g_tk=5381&uin=0"
                + "&format=json&inCharset=utf-8&outCharset=utf-8&notice=0"
                + "&platform=h5&needNewCode=1";
        Map<String, String> headers = Collections.singletonMap("Referer", "https://y.qq.com/");
        JsonArray wrapped = array(getJson(url, headers), "songlist");
        JsonArray songs = new JsonArray();
        for (JsonElement item : wrapped) {
            if (!item.isJsonObject()) continue;
            JsonObject data = object(item.getAsJsonObject(), "data");
            if (!data.entrySet().isEmpty()) songs.add(data);
        }
        return mapSongs(songs, "tx");
    }

    private List<MusicInfo> miguBoardSongs(String id) throws IOException {
        String url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/querycontentbyId.do"
                + "?columnId=" + encode(id) + "&needAll=0";
        JsonArray contents = array(object(getJson(url), "columnInfo"), "contents");
        JsonArray songs = new JsonArray();
        for (JsonElement item : contents) {
            if (!item.isJsonObject()) continue;
            JsonObject raw = item.getAsJsonObject();
            JsonObject info = raw.has("objectInfo") && raw.get("objectInfo").isJsonObject()
                    ? raw.getAsJsonObject("objectInfo") : raw;
            songs.add(info);
        }
        return mapSongs(songs, "mg");
    }

    private LyricInfo kuwoLyric(String songId) throws IOException {
        Map<String, String> headers = Collections.singletonMap("User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
                        + " Chrome/120.0.0.0 Mobile Safari/537.36");
        JsonObject data = object(getJson("https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId="
                + encode(songId), headers), "data");
        JsonArray lines = array(data, "lrclist");
        StringBuilder lyric = new StringBuilder();
        for (JsonElement element : lines) {
            if (!element.isJsonObject()) continue;
            JsonObject line = element.getAsJsonObject();
            double seconds = doubleValue(line, "time", 0);
            lyric.append(timeTag(seconds)).append(string(line, "lineLyric", "")).append('\n');
        }
        LyricInfo result = new LyricInfo();
        result.setLyric(lyric.toString());
        return result;
    }

    private LyricInfo kugouLyric(Map<String, String> params) throws IOException {
        String duration = params.get("interval");
        int millis = intervalMillis(duration);
        String url = "http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword="
                + encode(params.get("name")) + "&hash=" + encode(params.get("hash"))
                + "&timelength=" + millis + "&lrctxt=1";
        JsonArray candidates = array(getJson(url), "candidates");
        if (candidates.size() == 0 || !candidates.get(0).isJsonObject()) return new LyricInfo();
        JsonObject candidate = candidates.get(0).getAsJsonObject();
        String download = "http://lyrics.kugou.com/download?ver=1&client=pc&id="
                + encode(string(candidate, "id", "")) + "&accesskey="
                + encode(string(candidate, "accesskey", "")) + "&fmt=lrc&charset=utf8";
        JsonObject root = getJson(download);
        String content = string(root, "content", "");
        LyricInfo result = new LyricInfo();
        if (!content.isEmpty()) {
            result.setLyric(new String(Base64.decode(content, Base64.DEFAULT), StandardCharsets.UTF_8));
        }
        return result;
    }

    private LyricInfo tencentLyric(String songId) throws IOException {
        String url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                + encode(songId) + "&g_tk=5381&loginUin=0&hostUin=0&format=json"
                + "&inCharset=utf8&outCharset=utf-8&platform=yqq";
        JsonObject root = getJson(url, Collections.singletonMap(
                "Referer", "https://y.qq.com/portal/player.html"));
        LyricInfo result = new LyricInfo();
        result.setLyric(decodeBase64(string(root, "lyric", "")));
        result.setTlyric(decodeBase64(string(root, "trans", "")));
        result.setRlyric(decodeBase64(string(root, "roma", "")));
        return result;
    }

    private LyricInfo miguLyric(Map<String, String> params) throws IOException {
        LyricInfo result = new LyricInfo();
        String lrcUrl = params.get("lrcUrl");
        if (lrcUrl != null && !lrcUrl.isEmpty()) {
            result.setLyric(getText(lrcUrl, miguHeaders()));
        }
        String trcUrl = params.get("trcUrl");
        if (trcUrl != null && !trcUrl.isEmpty()) {
            result.setTlyric(getText(trcUrl, miguHeaders()));
        }
        return result;
    }

    private List<MusicInfo> mapSongs(JsonArray raw, String source) {
        List<MusicInfo> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject mapped;
            switch (source) {
                case "kw": mapped = mapKuwoSong(element.getAsJsonObject()); break;
                case "kg": mapped = mapKugouSong(element.getAsJsonObject()); break;
                case "tx": mapped = mapTencentSong(element.getAsJsonObject()); break;
                case "mg": mapped = mapMiguSong(element.getAsJsonObject()); break;
                default: continue;
            }
            String id = string(mapped, "songmid", "");
            if (id.isEmpty() || !ids.add(source + ':' + id)) continue;
            result.add(GSON.fromJson(mapped, MusicInfo.class));
        }
        return result;
    }

    private JsonObject mapKuwoSong(JsonObject raw) {
        JsonObject song = baseSong("kw",
                first(raw, "MUSICRID", "id", "songmid").replace("MUSIC_", ""),
                decode(first(raw, "SONGNAME", "name")),
                decode(first(raw, "ARTIST", "artist")).replace("&", "、"));
        song.addProperty("albumName", decode(first(raw, "ALBUM", "album")));
        song.addProperty("albumId", first(raw, "ALBUMID", "albumid", "albumId"));
        song.addProperty("img", normalizeKuwoPic(first(raw, "prob_albumpic", "albumpic", "pic")));
        long duration = longFirst(raw, "DURATION", "duration", "song_duration");
        song.addProperty("interval", formatDuration(duration));
        QualityBuilder quality = new QualityBuilder(song);
        String qualityText = first(raw, "N_MINFO", "n_minfo", "MINFO");
        Matcher matcher = KUWO_QUALITY.matcher(qualityText);
        while (matcher.find()) {
            String bitrate = matcher.group(1);
            String type = "4000".equals(bitrate) ? "flac24bit"
                    : "2000".equals(bitrate) ? "flac"
                    : "320".equals(bitrate) ? "320k"
                    : "128".equals(bitrate) ? "128k" : null;
            if (type != null) quality.add(type, matcher.group(2), null);
        }
        if (quality.isEmpty()) {
            quality.add("128k", "", null);
            quality.add("320k", "", null);
            if (first(raw, "formats", "FORMATS").contains("FLAC")) quality.add("flac", "", null);
        }
        quality.finish();
        return song;
    }

    private JsonObject mapKugouSong(JsonObject raw) {
        String id = first(raw, "Audioid", "audio_id", "songmid", "songid");
        JsonObject song = baseSong("kg", id,
                decode(first(raw, "SongName", "songname")),
                decode(first(raw, "SingerName", "singername")));
        song.addProperty("albumName", decode(first(raw, "AlbumName", "album_name", "remark")));
        song.addProperty("albumId", first(raw, "AlbumID", "album_id"));
        String hash = first(raw, "FileHash", "hash");
        song.addProperty("hash", hash);
        String cover = first(raw, "Image", "album_sizable_cover");
        if (cover.isEmpty()) cover = string(object(raw, "trans_param"), "union_cover", "");
        song.addProperty("img", cover.replace("{size}", "400"));
        long duration = longFirst(raw, "Duration", "duration");
        if (duration > 10000) duration /= 1000;
        song.addProperty("_interval", duration);
        song.addProperty("interval", formatDuration(duration));
        QualityBuilder quality = new QualityBuilder(song);
        quality.addIfPositive("128k", longFirst(raw, "FileSize", "filesize"), hash);
        quality.addIfPositive("320k", longFirst(raw, "HQFileSize", "320filesize", "filesize_320"),
                first(raw, "HQFileHash", "320hash", "hash_320"));
        quality.addIfPositive("flac", longFirst(raw, "SQFileSize", "sqfilesize", "filesize_flac"),
                first(raw, "SQFileHash", "sqhash", "hash_flac"));
        quality.addIfPositive("flac24bit", longFirst(raw, "ResFileSize", "filesize_high"),
                first(raw, "ResFileHash", "hash_high"));
        if (quality.isEmpty()) quality.add("128k", "", hash);
        quality.finish();
        return song;
    }

    private JsonObject mapTencentSong(JsonObject raw) {
        JsonObject file = object(raw, "file");
        JsonObject album = object(raw, "album");
        String songMid = first(raw, "mid", "songmid");
        String albumMid = first(album, "mid", "albummid");
        if (albumMid.isEmpty()) albumMid = first(raw, "albummid", "albumMid");
        String mediaMid = first(file, "media_mid");
        if (mediaMid.isEmpty()) mediaMid = first(raw, "strMediaMid", "media_mid");
        JsonObject song = baseSong("tx", songMid,
                first(raw, "name", "title", "songname"), joinNames(array(raw, "singer"), "name"));
        String songId = first(raw, "id", "songid");
        song.addProperty("id", songId.isEmpty() ? songMid : songId);
        song.addProperty("songId", songId);
        song.addProperty("albumName", first(album, "name"));
        if (string(song, "albumName", "").isEmpty()) song.addProperty("albumName", first(raw, "albumname"));
        song.addProperty("albumId", first(album, "id"));
        song.addProperty("albumMid", albumMid);
        song.addProperty("strMediaMid", mediaMid);
        song.addProperty("img", albumMid.isEmpty() ? ""
                : "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + albumMid + ".jpg");
        long duration = longFirst(raw, "interval");
        song.addProperty("interval", formatDuration(duration));
        QualityBuilder quality = new QualityBuilder(song);
        quality.addIfPositive("128k", longFirst(file, "size_128mp3", "size_128"), null);
        quality.addIfPositive("320k", longFirst(file, "size_320mp3", "size_320"), null);
        quality.addIfPositive("flac", longFirst(file, "size_flac"), null);
        quality.addIfPositive("flac24bit", longFirst(file, "size_hires"), null);
        if (file.entrySet().isEmpty()) {
            quality.addIfPositive("128k", longFirst(raw, "size128"), null);
            quality.addIfPositive("320k", longFirst(raw, "size320"), null);
            quality.addIfPositive("flac", longFirst(raw, "sizeflac"), null);
        }
        if (quality.isEmpty()) quality.add("128k", "", null);
        quality.finish();
        return song;
    }

    private JsonObject mapMiguSong(JsonObject raw) {
        String id = first(raw, "songId", "id");
        String singer = first(raw, "singer");
        if (singer.isEmpty()) singer = joinNames(array(raw, "singerList"), "name");
        if (singer.isEmpty()) singer = joinNames(array(raw, "singers"), "name");
        if (singer.isEmpty()) singer = joinNames(array(raw, "artists"), "name");
        JsonObject song = baseSong("mg", id, first(raw, "songName", "name"), singer);
        JsonArray albums = array(raw, "albums");
        JsonObject album = albums.size() > 0 && albums.get(0).isJsonObject()
                ? albums.get(0).getAsJsonObject() : new JsonObject();
        song.addProperty("albumName", first(raw, "album"));
        if (string(song, "albumName", "").isEmpty()) song.addProperty("albumName", first(album, "name"));
        song.addProperty("albumId", first(raw, "albumId"));
        if (string(song, "albumId", "").isEmpty()) song.addProperty("albumId", first(album, "id"));
        song.addProperty("copyrightId", first(raw, "copyrightId"));
        song.addProperty("lrcUrl", first(raw, "lrcUrl", "lyricUrl"));
        song.addProperty("mrcUrl", first(raw, "mrcUrl", "mrcurl"));
        song.addProperty("trcUrl", first(raw, "trcUrl"));
        String image = first(raw, "img3", "img2", "img1");
        JsonArray imageItems = array(raw, "imgItems");
        if (image.isEmpty() && imageItems.size() > 0 && imageItems.get(0).isJsonObject()) {
            image = first(imageItems.get(0).getAsJsonObject(), "img");
        }
        JsonArray albumImgs = array(raw, "albumImgs");
        if (image.isEmpty() && albumImgs.size() > 0 && albumImgs.get(0).isJsonObject()) {
            image = first(albumImgs.get(0).getAsJsonObject(), "img");
        }
        if (!image.isEmpty() && !image.startsWith("http")) image = "https://d.musicapp.migu.cn" + image;
        song.addProperty("img", image);
        long duration = longFirst(raw, "duration");
        if (duration == 0) duration = intervalSeconds(first(raw, "length"));
        song.addProperty("interval", formatDuration(duration));
        JsonArray formats = array(raw, "audioFormats");
        if (formats.size() == 0) formats = array(raw, "newRateFormats");
        if (formats.size() == 0) formats = array(raw, "rateFormats");
        QualityBuilder quality = new QualityBuilder(song);
        for (JsonElement element : formats) {
            if (!element.isJsonObject()) continue;
            JsonObject format = element.getAsJsonObject();
            String formatType = first(format, "formatType");
            String type = "PQ".equals(formatType) ? "128k" : "HQ".equals(formatType) ? "320k"
                    : "SQ".equals(formatType) ? "flac"
                    : ("ZQ".equals(formatType) || "ZQ24".equals(formatType)) ? "flac24bit" : null;
            if (type == null) continue;
            long size = longFirst(format, "asize", "androidSize", "size", "isize");
            quality.add(type, formatSize(size), null);
        }
        if (quality.isEmpty()) quality.add("128k", "", null);
        quality.finish();
        return song;
    }

    private static JsonArray mapPlaylists(JsonArray raw, String source) {
        JsonArray result = new JsonArray();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            JsonObject mapped = new JsonObject();
            mapped.addProperty("source", source);
            switch (source) {
                case "kw":
                    mapped.addProperty("id", first(item, "id"));
                    mapped.addProperty("name", decode(first(item, "name")));
                    mapped.addProperty("img", normalizeKuwoPic(first(item, "img")));
                    mapped.addProperty("desc", decode(first(item, "desc")));
                    mapped.addProperty("author", first(item, "uname"));
                    mapped.addProperty("total", integer(item, "total", 0));
                    mapped.addProperty("playCount", longFirst(item, "listencnt"));
                    break;
                case "kg":
                    mapped.addProperty("id", first(item, "specialid"));
                    mapped.addProperty("name", decode(first(item, "specialname")));
                    mapped.addProperty("img", first(item, "img", "imgurl"));
                    mapped.addProperty("desc", decode(first(item, "intro")));
                    mapped.addProperty("author", first(item, "nickname", "author"));
                    mapped.addProperty("total", integer(item, "songcount", 0));
                    mapped.addProperty("play_count", first(item, "total_play_count", "play_count"));
                    break;
                case "tx":
                    mapped.addProperty("id", first(item, "tid"));
                    mapped.addProperty("name", first(item, "title"));
                    mapped.addProperty("img", first(item, "cover_url_medium", "cover_url_big"));
                    mapped.addProperty("desc", decode(first(item, "desc")));
                    mapped.addProperty("author", first(object(item, "creator_info"), "nick"));
                    mapped.addProperty("total", array(item, "song_ids").size());
                    mapped.addProperty("playCount", longFirst(item, "access_num"));
                    break;
                default:
                    continue;
            }
            if (!string(mapped, "id", "").isEmpty()) result.add(mapped);
        }
        return result;
    }

    private static void collectMiguPlaylists(JsonElement element, JsonArray result, Set<String> ids) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectMiguPlaylists(child, result, ids);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject item = element.getAsJsonObject();
        String type = first(item, "resType");
        String id = first(item, "resId");
        if (("2021".equals(type) || first(item, "action").contains("song-list-info"))
                && !id.isEmpty() && ids.add(id)) {
            JsonObject mapped = new JsonObject();
            mapped.addProperty("id", id);
            mapped.addProperty("source", "mg");
            mapped.addProperty("name", first(item, "txt", "title"));
            mapped.addProperty("img", first(item, "img", "imageUrl"));
            mapped.addProperty("desc", first(item, "txt2"));
            mapped.addProperty("author", "");
            result.add(mapped);
        }
        for (Map.Entry<String, JsonElement> entry : item.entrySet()) {
            if (entry.getValue().isJsonArray() || entry.getValue().isJsonObject()) {
                collectMiguPlaylists(entry.getValue(), result, ids);
            }
        }
    }

    private JsonObject getJson(String url) throws IOException {
        return getJson(url, Collections.emptyMap());
    }

    private JsonObject getJson(String url, Map<String, String> headers) throws IOException {
        String text = getText(url, headers);
        try {
            JsonElement parsed = new JsonParser().parse(text);
            if (!parsed.isJsonObject()) throw new IOException("接口返回的不是 JSON 对象");
            return parsed.getAsJsonObject();
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("音乐目录响应解析失败", error);
        }
    }

    private JsonObject postJson(String url, JsonObject body, Map<String, String> headers)
            throws IOException {
        Request.Builder builder = requestBuilder(url, headers)
                .post(RequestBody.create(JSON, GSON.toJson(body)));
        String text = execute(builder.build());
        try {
            JsonElement parsed = new JsonParser().parse(text);
            if (!parsed.isJsonObject()) throw new IOException("接口返回的不是 JSON 对象");
            return parsed.getAsJsonObject();
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("音乐目录响应解析失败", error);
        }
    }

    private String getText(String url, Map<String, String> headers) throws IOException {
        return execute(requestBuilder(url, headers).get().build());
    }

    private Request.Builder requestBuilder(String url, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                        + " AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/120.0.0.0 Safari/537.36");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder;
    }

    private String execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("目录接口返回 HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("目录接口返回空内容");
            return body.string();
        }
    }

    private static JsonObject playlistResult(JsonArray list, int total, int limit, int page) {
        JsonObject result = new JsonObject();
        result.add("list", list);
        result.addProperty("total", total);
        result.addProperty("limit", limit);
        result.addProperty("page", page);
        return result;
    }

    private static JsonObject emptyListResult(int page, int limit) {
        return playlistResult(new JsonArray(), 0, limit, page);
    }

    private static Playlist playlistInfo(String id, String source, String name, String image,
                                         String description, String creator, int total) {
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setSource(source);
        playlist.setName(name);
        playlist.setPicUrl(image);
        playlist.setDesc(description);
        playlist.setCreator(creator);
        playlist.setSongCount(total);
        return playlist;
    }

    private static JsonObject baseSong(String source, String id, String name, String singer) {
        JsonObject song = new JsonObject();
        song.addProperty("source", source);
        song.addProperty("songmid", id);
        song.addProperty("id", id);
        song.addProperty("name", name == null || name.isEmpty() ? "未知歌曲" : name);
        song.addProperty("singer", singer == null ? "" : singer);
        return song;
    }

    private static String normalizeNumericId(String raw) {
        if (raw == null) return "";
        String value = raw.replaceFirst("^[a-z]{2}__", "").replaceFirst("^id_", "");
        int marker = value.lastIndexOf("__");
        return marker >= 0 ? value.substring(marker + 2) : value;
    }

    private static Map<String, String> miguHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X)"
                + " AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1");
        headers.put("Referer", "https://m.music.migu.cn/");
        return headers;
    }

    private static String first(JsonObject object, String... names) {
        if (object == null) return "";
        for (String name : names) {
            if (!object.has(name) || object.get(name).isJsonNull()) continue;
            try {
                String value = object.get(name).getAsString();
                if (!value.isEmpty()) return value;
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static long longFirst(JsonObject object, String... names) {
        if (object == null) return 0;
        for (String name : names) {
            if (!object.has(name) || object.get(name).isJsonNull()) continue;
            try {
                return object.get(name).getAsLong();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static JsonObject object(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonObject()
                ? parent.getAsJsonObject(name) : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonArray()
                ? parent.getAsJsonArray(name) : new JsonArray();
    }

    private static String string(JsonObject object, String name, String fallback) {
        String value = first(object, name);
        return value.isEmpty() ? fallback : value;
    }

    private static int integer(JsonObject object, String name, int fallback) {
        try {
            return object != null && object.has(name) ? object.get(name).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double doubleValue(JsonObject object, String name, double fallback) {
        try {
            return object != null && object.has(name) ? object.get(name).getAsDouble() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String encode(String value) throws IOException {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception error) {
            throw new IOException("无法编码目录请求", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static String decode(String value) {
        if (value == null || value.isEmpty()) return "";
        return Html.fromHtml(value).toString();
    }

    private static String normalizeKuwoPic(String value) {
        if (value == null) return "";
        return value.replaceFirst("(/star/albumcover/)\\d+", "$11000")
                .replaceFirst("(pictype=)\\d+", "$11000")
                .replaceFirst("(size=)\\d+", "$11000");
    }

    private static String joinNames(JsonArray values, String key) {
        StringBuilder result = new StringBuilder();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            String name = first(element.getAsJsonObject(), key, "author_name", "singername");
            if (name.isEmpty()) continue;
            if (result.length() > 0) result.append('、');
            result.append(name);
        }
        return decode(result.toString());
    }

    private static String formatDuration(long seconds) {
        long safe = Math.max(0, seconds);
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "";
        return String.format(Locale.US, "%.2f MB", bytes / 1048576d);
    }

    private static long intervalSeconds(String value) {
        if (value == null || value.isEmpty()) return 0;
        String[] parts = value.split(":");
        long seconds = 0;
        for (String part : parts) seconds = seconds * 60 + parseInt(part, 0);
        return seconds;
    }

    private static int intervalMillis(String value) {
        return (int) Math.min(Integer.MAX_VALUE, intervalSeconds(value) * 1000);
    }

    private static String timeTag(double seconds) {
        int minutes = (int) (seconds / 60);
        double remainder = seconds - minutes * 60;
        return String.format(Locale.US, "[%02d:%05.2f]", minutes, remainder);
    }

    private static String decodeBase64(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            return new String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class QualityBuilder {
        private final JsonObject song;
        private final JsonArray list = new JsonArray();
        private final JsonObject details = new JsonObject();

        QualityBuilder(JsonObject song) {
            this.song = song;
        }

        void addIfPositive(String type, long bytes, String hash) {
            if (bytes > 0) add(type, formatSize(bytes), hash);
        }

        void add(String type, String size, String hash) {
            if (details.has(type)) return;
            JsonObject item = new JsonObject();
            item.addProperty("type", type);
            item.addProperty("size", size == null ? "" : size);
            if (hash != null && !hash.isEmpty()) item.addProperty("hash", hash);
            list.add(item);
            JsonObject detail = new JsonObject();
            detail.addProperty("size", size == null ? "" : size);
            if (hash != null && !hash.isEmpty()) detail.addProperty("hash", hash);
            details.add(type, detail);
        }

        boolean isEmpty() {
            return list.size() == 0;
        }

        void finish() {
            song.add("types", list);
            song.add("_types", details);
        }
    }
}
