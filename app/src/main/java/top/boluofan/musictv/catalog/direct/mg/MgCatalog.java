package top.boluofan.musictv.catalog.direct.mg;

import android.text.Html;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
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

public final class MgCatalog implements DirectPlatformCatalog {
    private static final Gson GSON = new Gson();

    private final DirectHttpTransport transport;

    public MgCatalog(DirectHttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public List<MusicInfo> search(String keyword, int page, int limit) throws IOException {
        String searchSwitch = "{\"song\":1,\"album\":0,\"singer\":0,\"tagSong\":0,"
                + "\"mvSong\":0,\"songlist\":0,\"bestShow\":0}";
        String url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do"
                + "?isCopyright=1&isCorrect=1&pageNo=" + page + "&pageSize=" + limit
                + "&searchSwitch=" + encode(searchSwitch) + "&sort=0&text=" + encode(keyword);
        JsonArray nested = array(object(transport.getJson(url), "songResultData"), "resultList");
        JsonArray flattened = new JsonArray();
        for (JsonElement group : nested) {
            if (!group.isJsonArray()) continue;
            for (JsonElement song : group.getAsJsonArray()) flattened.add(song);
        }
        return mapSongs(flattened);
    }

    @Override
    public JsonObject playlistList(String tagId, String sortId, int page) throws IOException {
        String url = tagId == null || tagId.isEmpty()
                ? "https://app.c.nf.migu.cn/pc/bmw/page-data/playlist-square-recommend/v1.0"
                    + "?templateVersion=2&pageNo=" + page
                : "https://app.c.nf.migu.cn/pc/v1.0/template/musiclistplaza-listbytag/release"
                    + "?pageNumber=" + page + "&templateVersion=2&tagId=" + encode(tagId);
        JsonArray found = new JsonArray();
        collectPlaylists(object(transport.getJson(url), "data"), found, new LinkedHashSet<>());
        return playlistResult(found, page * 30 + (found.size() >= 30 ? 30 : 0), 30, page);
    }

    @Override
    public Playlist playlistDetail(String rawId, int page) throws IOException {
        String id = normalizeNumericId(rawId);
        Map<String, String> headers = headers();
        String songsUrl = "https://app.c.nf.migu.cn/MIGUM3.0/resource/playlist/song/v2.0"
                + "?pageNo=" + page + "&pageSize=1000&playlistId=" + encode(id);
        JsonObject songData = object(transport.getJson(songsUrl, headers), "data");
        String infoUrl = "https://c.musicapp.migu.cn/MIGUM3.0/resource/playlist/v2.0"
                + "?playlistId=" + encode(id);
        JsonObject info = object(transport.getJson(infoUrl, headers), "data");
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setSource("mg");
        playlist.setName(string(info, "title", "咪咕歌单"));
        playlist.setPicUrl(string(object(info, "imgItem"), "img", ""));
        playlist.setDesc(string(info, "summary", ""));
        playlist.setCreator(string(info, "ownerName", ""));
        playlist.setSongCount(integer(songData, "totalCount", 0));
        playlist.setSongs(mapSongs(array(songData, "songList")));
        return playlist;
    }

    @Override
    public List<MusicInfo> boardSongs(String id, int page) throws IOException {
        String url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/querycontentbyId.do"
                + "?columnId=" + encode(id) + "&needAll=0";
        JsonArray contents = array(object(transport.getJson(url), "columnInfo"), "contents");
        JsonArray songs = new JsonArray();
        for (JsonElement item : contents) {
            if (!item.isJsonObject()) continue;
            JsonObject raw = item.getAsJsonObject();
            JsonObject info = raw.has("objectInfo") && raw.get("objectInfo").isJsonObject()
                    ? raw.getAsJsonObject("objectInfo") : raw;
            songs.add(info);
        }
        return mapSongs(songs);
    }

    @Override
    public LyricInfo lyric(Map<String, String> params) throws IOException {
        LyricInfo result = new LyricInfo();
        String lrcUrl = params.get("lrcUrl");
        if (lrcUrl != null && !lrcUrl.isEmpty()) {
            result.setLyric(transport.getText(lrcUrl, headers()));
        }
        String trcUrl = params.get("trcUrl");
        if (trcUrl != null && !trcUrl.isEmpty()) {
            result.setTlyric(transport.getText(trcUrl, headers()));
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
            if (id.isEmpty() || !ids.add("mg:" + id)) continue;
            result.add(GSON.fromJson(mapped, MusicInfo.class));
        }
        return result;
    }

    private static JsonObject mapSong(JsonObject raw) {
        String id = first(raw, "songId", "id");
        String singer = first(raw, "singer");
        if (singer.isEmpty()) singer = joinNames(array(raw, "singerList"), "name");
        if (singer.isEmpty()) singer = joinNames(array(raw, "singers"), "name");
        if (singer.isEmpty()) singer = joinNames(array(raw, "artists"), "name");
        JsonObject song = baseSong(id, first(raw, "songName", "name"), singer);
        JsonArray albums = array(raw, "albums");
        JsonObject album = albums.size() > 0 && albums.get(0).isJsonObject()
                ? albums.get(0).getAsJsonObject() : new JsonObject();
        song.addProperty("albumName", first(raw, "album"));
        if (string(song, "albumName", "").isEmpty()) {
            song.addProperty("albumName", first(album, "name"));
        }
        song.addProperty("albumId", first(raw, "albumId"));
        if (string(song, "albumId", "").isEmpty()) {
            song.addProperty("albumId", first(album, "id"));
        }
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
        if (!image.isEmpty() && !image.startsWith("http")) {
            image = "https://d.musicapp.migu.cn" + image;
        }
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
            String type = "PQ".equals(formatType) ? "128k"
                    : "HQ".equals(formatType) ? "320k"
                    : "SQ".equals(formatType) ? "flac"
                    : ("ZQ".equals(formatType) || "ZQ24".equals(formatType))
                            ? "flac24bit" : null;
            if (type == null) continue;
            long size = longFirst(format, "asize", "androidSize", "size", "isize");
            quality.add(type, formatSize(size));
        }
        if (quality.isEmpty()) quality.add("128k", "");
        quality.finish();
        return song;
    }

    private static void collectPlaylists(JsonElement element, JsonArray result, Set<String> ids) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectPlaylists(child, result, ids);
            }
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
                collectPlaylists(entry.getValue(), result, ids);
            }
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

    private static JsonObject baseSong(String id, String name, String singer) {
        JsonObject song = new JsonObject();
        song.addProperty("source", "mg");
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

    private static Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X)"
                + " AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1");
        headers.put("Referer", "https://m.music.migu.cn/");
        return headers;
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

    private static String encode(String value) throws IOException {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception error) {
            throw new IOException("无法编码目录请求", error);
        }
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

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
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
