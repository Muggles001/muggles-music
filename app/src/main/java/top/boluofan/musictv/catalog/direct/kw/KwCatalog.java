package top.boluofan.musictv.catalog.direct.kw;

import android.text.Html;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
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

public final class KwCatalog implements DirectPlatformCatalog {
    private static final Gson GSON = new Gson();
    private static final Pattern QUALITY = Pattern.compile(
            "bitrate:(\\d+),format:[^,;]+,size:([^;]+)");

    private final DirectHttpTransport transport;

    public KwCatalog(DirectHttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public List<MusicInfo> search(String keyword, int page, int limit) throws IOException {
        String url = "https://search.kuwo.cn/r.s?client=kt&all=" + encode(keyword)
                + "&pn=" + Math.max(0, page - 1) + "&rn=" + limit
                + "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1"
                + "&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012"
                + "&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1";
        return mapSongs(array(transport.getJson(url), "abslist"));
    }

    @Override
    public JsonObject playlistList(String tagId, String sortId, int page) throws IOException {
        String url;
        if (tagId == null || tagId.isEmpty()) {
            url = "https://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList"
                    + "?loginUid=0&loginSid=0&appUid=76039576&pn=" + page
                    + "&rn=36&order="
                    + (sortId == null || sortId.isEmpty() ? "hot" : sortId);
        } else {
            String id = tagId.contains("-") ? tagId.substring(0, tagId.indexOf('-')) : tagId;
            url = "https://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList"
                    + "?loginUid=0&loginSid=0&appUid=76039576&pn=" + page
                    + "&id=" + encode(id) + "&rn=36";
        }
        JsonObject data = object(transport.getJson(url), "data");
        return playlistResult(mapPlaylists(array(data, "data")),
                integer(data, "total", 0), 36, page);
    }

    @Override
    public Playlist playlistDetail(String rawId, int page) throws IOException {
        String id = normalizeNumericId(rawId);
        String url = "https://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=" + encode(id)
                + "&pn=" + Math.max(0, page - 1)
                + "&rn=1000&encode=utf8&keyset=pl2012&identity=kuwo"
                + "&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1";
        JsonObject root = transport.getJson(url);
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setSource("kw");
        playlist.setName(string(root, "title", "酷我歌单"));
        playlist.setPicUrl(string(root, "pic", ""));
        playlist.setDesc(string(root, "info", ""));
        playlist.setCreator(string(root, "uname", ""));
        playlist.setSongCount(integer(root, "total", 0));
        playlist.setSongs(mapSongs(array(root, "musiclist")));
        return playlist;
    }

    @Override
    public List<MusicInfo> boardSongs(String id, int page) throws IOException {
        String url = "https://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn="
                + Math.max(0, page - 1) + "&rn=100&type=bang&data=content&id=" + encode(id)
                + "&show_copyright_off=0&pcmp4=1&isbang=1";
        return mapSongs(array(transport.getJson(url), "musiclist"));
    }

    @Override
    public LyricInfo lyric(Map<String, String> params) throws IOException {
        String songId = params == null ? "" : params.get("songmid");
        Map<String, String> headers = Collections.singletonMap("User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
                        + " Chrome/120.0.0.0 Mobile Safari/537.36");
        JsonObject data = object(transport.getJson(
                "https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=" + encode(songId),
                headers), "data");
        StringBuilder lyric = new StringBuilder();
        for (JsonElement element : array(data, "lrclist")) {
            if (!element.isJsonObject()) continue;
            JsonObject line = element.getAsJsonObject();
            lyric.append(timeTag(doubleValue(line, "time", 0)))
                    .append(string(line, "lineLyric", ""))
                    .append('\n');
        }
        LyricInfo result = new LyricInfo();
        result.setLyric(lyric.toString());
        return result;
    }

    private List<MusicInfo> mapSongs(JsonArray raw) {
        List<MusicInfo> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject mapped = mapSong(element.getAsJsonObject());
            String id = string(mapped, "songmid", "");
            if (id.isEmpty() || !ids.add("kw:" + id)) continue;
            result.add(GSON.fromJson(mapped, MusicInfo.class));
        }
        return result;
    }

    private static JsonObject mapSong(JsonObject raw) {
        String id = first(raw, "MUSICRID", "id", "songmid").replace("MUSIC_", "");
        JsonObject song = new JsonObject();
        song.addProperty("source", "kw");
        song.addProperty("songmid", id);
        song.addProperty("id", id);
        song.addProperty("name", decode(first(raw, "SONGNAME", "name")));
        song.addProperty("singer",
                decode(first(raw, "ARTIST", "artist")).replace("&", "、"));
        song.addProperty("albumName", decode(first(raw, "ALBUM", "album")));
        song.addProperty("albumId", first(raw, "ALBUMID", "albumid", "albumId"));
        song.addProperty("img",
                normalizePic(first(raw, "prob_albumpic", "albumpic", "pic")));
        song.addProperty("interval",
                formatDuration(longFirst(raw, "DURATION", "duration", "song_duration")));

        QualityBuilder quality = new QualityBuilder(song);
        Matcher matcher = QUALITY.matcher(first(raw, "N_MINFO", "n_minfo", "MINFO"));
        while (matcher.find()) {
            String bitrate = matcher.group(1);
            String type = "4000".equals(bitrate) ? "flac24bit"
                    : "2000".equals(bitrate) ? "flac"
                    : "320".equals(bitrate) ? "320k"
                    : "128".equals(bitrate) ? "128k" : null;
            if (type != null) quality.add(type, matcher.group(2));
        }
        if (quality.isEmpty()) {
            quality.add("128k", "");
            quality.add("320k", "");
            if (first(raw, "formats", "FORMATS").contains("FLAC")) {
                quality.add("flac", "");
            }
        }
        quality.finish();
        return song;
    }

    private static JsonArray mapPlaylists(JsonArray raw) {
        JsonArray result = new JsonArray();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            JsonObject mapped = new JsonObject();
            mapped.addProperty("source", "kw");
            mapped.addProperty("id", first(item, "id"));
            mapped.addProperty("name", decode(first(item, "name")));
            mapped.addProperty("img", normalizePic(first(item, "img")));
            mapped.addProperty("desc", decode(first(item, "desc")));
            mapped.addProperty("author", first(item, "uname"));
            mapped.addProperty("total", integer(item, "total", 0));
            mapped.addProperty("playCount", longFirst(item, "listencnt"));
            if (!string(mapped, "id", "").isEmpty()) result.add(mapped);
        }
        return result;
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

    private static double doubleValue(JsonObject object, String name, double fallback) {
        try {
            return object != null && object.has(name) ? object.get(name).getAsDouble() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String string(JsonObject object, String name, String fallback) {
        String value = first(object, name);
        return value.isEmpty() ? fallback : value;
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

    private static String normalizePic(String value) {
        if (value == null) return "";
        return value.replaceFirst("(/star/albumcover/)\\d+", "$11000")
                .replaceFirst("(pictype=)\\d+", "$11000")
                .replaceFirst("(size=)\\d+", "$11000");
    }

    private static String normalizeNumericId(String value) {
        if (value == null) return "";
        String normalized = value.replaceFirst("^[a-z]{2}__", "").replaceFirst("^id_", "");
        int separator = normalized.lastIndexOf("__");
        return separator >= 0 ? normalized.substring(separator + 2) : normalized;
    }

    private static String formatDuration(long seconds) {
        long safe = Math.max(0, seconds);
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60);
    }

    private static String timeTag(double seconds) {
        int minutes = (int) (seconds / 60);
        return String.format(Locale.US, "[%02d:%05.2f]", minutes, seconds - minutes * 60);
    }

    private static final class QualityBuilder {
        private final JsonObject song;
        private final JsonArray list = new JsonArray();
        private final JsonObject details = new JsonObject();

        QualityBuilder(JsonObject song) {
            this.song = song;
        }

        void add(String type, String size) {
            if (details.has(type)) return;
            JsonObject item = new JsonObject();
            item.addProperty("type", type);
            item.addProperty("size", size == null ? "" : size);
            list.add(item);
            JsonObject detail = new JsonObject();
            detail.addProperty("size", size == null ? "" : size);
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
