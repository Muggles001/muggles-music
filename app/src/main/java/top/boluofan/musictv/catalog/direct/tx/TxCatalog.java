package top.boluofan.musictv.catalog.direct.tx;

import android.text.Html;
import android.util.Base64;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import top.boluofan.musictv.catalog.model.LyricInfo;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;
import top.boluofan.musictv.catalog.direct.DirectHttpTransport;
import top.boluofan.musictv.catalog.direct.DirectPlatformCatalog;

public final class TxCatalog implements DirectPlatformCatalog {
    private static final Gson GSON = new Gson();

    private final DirectHttpTransport transport;

    public TxCatalog(DirectHttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public List<MusicInfo> search(String keyword, int page, int limit) throws IOException {
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
                "aid", "oaid", "taid", "tid", "wid", "uid", "sid"}) {
            comm.addProperty(key, "0");
        }
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
        JsonObject root = transport.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body,
                Collections.singletonMap("User-Agent", "QQMusic 14090508(android 12)"));
        JsonObject data = object(object(root, "req"), "data");
        return mapSongs(array(object(data, "body"), "item_song"));
    }

    @Override
    public JsonObject playlistList(String tagId, String sortId, int page) throws IOException {
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
        JsonObject result = object(object(transport.getJson(url), "playlist"), "data");
        return playlistResult(mapPlaylists(array(result, "v_playlist")),
                integer(result, "total", 0), 36, page);
    }

    @Override
    public Playlist playlistDetail(String rawId, int page) throws IOException {
        String id = normalizeNumericId(rawId);
        String url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg"
                + "?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=" + encode(id)
                + "&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8"
                + "&notice=0&platform=yqq.json&needNewCode=0";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Origin", "https://y.qq.com");
        headers.put("Referer", "https://y.qq.com/n/yqq/playsquare/" + id + ".html");
        JsonArray cdlist = array(transport.getJson(url, headers), "cdlist");
        JsonObject detail = cdlist.size() > 0 && cdlist.get(0).isJsonObject()
                ? cdlist.get(0).getAsJsonObject() : new JsonObject();
        JsonArray songs = array(detail, "songlist");
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setSource("tx");
        playlist.setName(string(detail, "dissname", "QQ音乐歌单"));
        playlist.setPicUrl(string(detail, "logo", ""));
        playlist.setDesc(decode(string(detail, "desc", "")));
        playlist.setCreator(string(detail, "nickname", ""));
        playlist.setSongCount(songs.size());
        playlist.setSongs(mapSongs(songs));
        return playlist;
    }

    @Override
    public List<MusicInfo> boardSongs(String id, int page) throws IOException {
        String url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?topid=" + encode(id)
                + "&page=detail&type=top&song_begin=0&song_num=300&g_tk=5381&uin=0"
                + "&format=json&inCharset=utf-8&outCharset=utf-8&notice=0"
                + "&platform=h5&needNewCode=1";
        Map<String, String> headers = Collections.singletonMap("Referer", "https://y.qq.com/");
        JsonArray wrapped = array(transport.getJson(url, headers), "songlist");
        JsonArray songs = new JsonArray();
        for (JsonElement item : wrapped) {
            if (!item.isJsonObject()) continue;
            JsonObject data = object(item.getAsJsonObject(), "data");
            if (!data.entrySet().isEmpty()) songs.add(data);
        }
        return mapSongs(songs);
    }

    @Override
    public LyricInfo lyric(Map<String, String> params) throws IOException {
        String songId = params.get("songmid");
        String url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                + encode(songId) + "&g_tk=5381&loginUin=0&hostUin=0&format=json"
                + "&inCharset=utf8&outCharset=utf-8&platform=yqq";
        JsonObject root = transport.getJson(url, Collections.singletonMap(
                "Referer", "https://y.qq.com/portal/player.html"));
        LyricInfo result = new LyricInfo();
        result.setLyric(decodeBase64(string(root, "lyric", "")));
        result.setTlyric(decodeBase64(string(root, "trans", "")));
        result.setRlyric(decodeBase64(string(root, "roma", "")));
        return result;
    }

    private List<MusicInfo> mapSongs(JsonArray raw) {
        List<MusicInfo> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject mapped = mapSong(element.getAsJsonObject());
            String id = string(mapped, "songmid", "");
            if (id.isEmpty() || !ids.add("tx:" + id)) continue;
            result.add(GSON.fromJson(mapped, MusicInfo.class));
        }
        return result;
    }

    private static JsonObject mapSong(JsonObject raw) {
        JsonObject file = object(raw, "file");
        JsonObject album = object(raw, "album");
        String songMid = first(raw, "mid", "songmid");
        String albumMid = first(album, "mid", "albummid");
        if (albumMid.isEmpty()) albumMid = first(raw, "albummid", "albumMid");
        String mediaMid = first(file, "media_mid");
        if (mediaMid.isEmpty()) mediaMid = first(raw, "strMediaMid", "media_mid");
        JsonObject song = baseSong(songMid,
                first(raw, "name", "title", "songname"), joinNames(array(raw, "singer"), "name"));
        String songId = first(raw, "id", "songid");
        song.addProperty("id", songId.isEmpty() ? songMid : songId);
        song.addProperty("songId", songId);
        song.addProperty("albumName", first(album, "name"));
        if (string(song, "albumName", "").isEmpty()) {
            song.addProperty("albumName", first(raw, "albumname"));
        }
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

    private static JsonArray mapPlaylists(JsonArray raw) {
        JsonArray result = new JsonArray();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            JsonObject mapped = new JsonObject();
            mapped.addProperty("source", "tx");
            mapped.addProperty("id", first(item, "tid"));
            mapped.addProperty("name", first(item, "title"));
            mapped.addProperty("img", first(item, "cover_url_medium", "cover_url_big"));
            mapped.addProperty("desc", decode(first(item, "desc")));
            mapped.addProperty("author", first(object(item, "creator_info"), "nick"));
            mapped.addProperty("total", array(item, "song_ids").size());
            mapped.addProperty("playCount", longFirst(item, "access_num"));
            if (!string(mapped, "id", "").isEmpty()) result.add(mapped);
        }
        return result;
    }

    private static JsonObject baseSong(String id, String name, String singer) {
        JsonObject song = new JsonObject();
        song.addProperty("source", "tx");
        song.addProperty("songmid", id);
        song.addProperty("id", id);
        song.addProperty("name", name == null || name.isEmpty() ? "未知歌曲" : name);
        song.addProperty("singer", singer == null ? "" : singer);
        return song;
    }

    private static JsonObject playlistResult(JsonArray list, int total, int limit, int page) {
        JsonObject result = new JsonObject();
        result.add("list", list);
        result.addProperty("total", total);
        result.addProperty("limit", limit);
        result.addProperty("page", page);
        return result;
    }

    private static String normalizeNumericId(String raw) {
        if (raw == null) return "";
        String value = raw.replaceFirst("^[a-z]{2}__", "").replaceFirst("^id_", "");
        int marker = value.lastIndexOf("__");
        return marker >= 0 ? value.substring(marker + 2) : value;
    }

    private static JsonObject object(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonObject()
                ? parent.getAsJsonObject(name) : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonArray()
                ? parent.getAsJsonArray(name) : new JsonArray();
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

    private static int integer(JsonObject object, String name, int fallback) {
        try {
            return object != null && object.has(name) ? object.get(name).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String string(JsonObject object, String name, String fallback) {
        String value = first(object, name);
        return value.isEmpty() ? fallback : value;
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
