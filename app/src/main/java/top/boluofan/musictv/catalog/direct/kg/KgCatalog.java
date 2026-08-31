package top.boluofan.musictv.catalog.direct.kg;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import top.boluofan.musictv.catalog.model.LyricInfo;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;
import top.boluofan.musictv.catalog.direct.DirectHttpTransport;
import top.boluofan.musictv.catalog.direct.DirectPlatformCatalog;

public final class KgCatalog implements DirectPlatformCatalog {
    private static final Gson GSON = new Gson();
    private static final Pattern EMBEDDED_PLAYLIST = Pattern.compile(
            "global\\.data\\s*=\\s*(\\[[\\s\\S]*?]);");

    private final DirectHttpTransport transport;

    public KgCatalog(DirectHttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public List<MusicInfo> search(String keyword, int page, int limit) throws IOException {
        String url = "https://songsearch.kugou.com/song_search_v2?keyword=" + encode(keyword)
                + "&page=" + page + "&pagesize=" + limit
                + "&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1"
                + "&privilege_filter=0&area_code=1";
        return mapSongs(array(object(transport.getJson(url), "data"), "lists"));
    }

    @Override
    public JsonObject playlistList(String tagId, String sortId, int page) throws IOException {
        String order = sortId == null || sortId.isEmpty() || "hot".equals(sortId) ? "5" : sortId;
        String url = "http://www2.kugou.kugou.com/yueku/v9/special/getSpecial"
                + "?is_ajax=1&cdn=cdn&t=" + encode(order) + "&c="
                + encode(tagId == null ? "" : tagId) + "&p=" + page;
        JsonObject root = transport.getJson(url);
        return playlistResult(mapPlaylists(array(root, "special_db")),
                integer(object(root, "data"), "total", page * 20 + 20), 20, page);
    }

    @Override
    public Playlist playlistDetail(String rawId, int page) throws IOException {
        String id = normalizeNumericId(rawId);
        String html = transport.getText(
                "http://www2.kugou.kugou.com/yueku/v9/special/single/"
                        + encode(id) + "-5-9999.html",
                Collections.emptyMap());
        Matcher matcher = EMBEDDED_PLAYLIST.matcher(html);
        if (!matcher.find()) throw new IOException("酷狗歌单详情解析失败");
        JsonElement parsed;
        try {
            parsed = new JsonParser().parse(matcher.group(1));
        } catch (Exception error) {
            throw new IOException("酷狗歌单歌曲数据无效", error);
        }
        JsonArray songs = parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setSource("kg");
        playlist.setName("酷狗歌单");
        playlist.setPicUrl("");
        playlist.setDesc("");
        playlist.setCreator("");
        playlist.setSongCount(songs.size());
        playlist.setSongs(mapSongs(songs));
        return playlist;
    }

    @Override
    public List<MusicInfo> boardSongs(String id, int page) throws IOException {
        String url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108"
                + "&ranktype=1&plat=0&pagesize=100&area_code=1&page=" + page
                + "&rankid=" + encode(id) + "&with_res_tag=0&show_portrait_mv=1";
        return mapSongs(array(object(transport.getJson(url), "data"), "info"));
    }

    @Override
    public LyricInfo lyric(Map<String, String> params) throws IOException {
        int millis = intervalMillis(params.get("interval"));
        String url = "http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword="
                + encode(params.get("name")) + "&hash=" + encode(params.get("hash"))
                + "&timelength=" + millis + "&lrctxt=1";
        JsonArray candidates = array(transport.getJson(url), "candidates");
        if (candidates.size() == 0 || !candidates.get(0).isJsonObject()) {
            return new LyricInfo();
        }
        JsonObject candidate = candidates.get(0).getAsJsonObject();
        String download = "http://lyrics.kugou.com/download?ver=1&client=pc&id="
                + encode(string(candidate, "id", "")) + "&accesskey="
                + encode(string(candidate, "accesskey", "")) + "&fmt=lrc&charset=utf8";
        String content = string(transport.getJson(download), "content", "");
        LyricInfo result = new LyricInfo();
        if (!content.isEmpty()) {
            result.setLyric(new String(
                    Base64.decode(content, Base64.DEFAULT), StandardCharsets.UTF_8));
        }
        return result;
    }

    private List<MusicInfo> mapSongs(JsonArray raw) {
        List<MusicInfo> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject mapped = mapSong(element.getAsJsonObject());
            String id = string(mapped, "songmid", "");
            if (id.isEmpty() || !ids.add("kg:" + id)) continue;
            result.add(GSON.fromJson(mapped, MusicInfo.class));
        }
        return result;
    }

    private static JsonObject mapSong(JsonObject raw) {
        String id = first(raw, "Audioid", "audio_id", "songmid", "songid");
        JsonObject song = baseSong(id,
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

    private static JsonArray mapPlaylists(JsonArray raw) {
        JsonArray result = new JsonArray();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            JsonObject mapped = new JsonObject();
            mapped.addProperty("source", "kg");
            mapped.addProperty("id", first(item, "specialid"));
            mapped.addProperty("name", decode(first(item, "specialname")));
            mapped.addProperty("img", first(item, "img", "imgurl"));
            mapped.addProperty("desc", decode(first(item, "intro")));
            mapped.addProperty("author", first(item, "nickname", "author"));
            mapped.addProperty("total", integer(item, "songcount", 0));
            mapped.addProperty("play_count", first(item, "total_play_count", "play_count"));
            if (!string(mapped, "id", "").isEmpty()) result.add(mapped);
        }
        return result;
    }

    private static JsonObject baseSong(String id, String name, String singer) {
        JsonObject song = new JsonObject();
        song.addProperty("source", "kg");
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

    private static JsonArray array(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonArray()
                ? parent.getAsJsonArray(name) : new JsonArray();
    }

    private static JsonObject object(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonObject()
                ? parent.getAsJsonObject(name) : new JsonObject();
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

    private static String normalizeNumericId(String raw) {
        if (raw == null) return "";
        String value = raw.replaceFirst("^[a-z]{2}__", "").replaceFirst("^id_", "");
        int marker = value.lastIndexOf("__");
        return marker >= 0 ? value.substring(marker + 2) : value;
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
