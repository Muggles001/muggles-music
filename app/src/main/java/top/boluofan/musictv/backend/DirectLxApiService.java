package top.boluofan.musictv.backend;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.Response;
import retrofit2.converter.gson.GsonConverterFactory;
import top.boluofan.musictv.ExternalApiService;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.model.ListData;
import top.boluofan.musictv.api.model.LoginResponse;
import top.boluofan.musictv.api.model.LyricInfo;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.MusicUrlResponse;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.local.LocalLibraryStore;

final class DirectLxApiService implements LxApiService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final String[][] WY_BOARDS = {
            {"wy__19723756", "飙升榜"},
            {"wy__3778678", "热歌榜"},
            {"wy__3779629", "新歌榜"},
            {"wy__2884035", "原创榜"},
            {"wy__71384707", "古典榜"},
            {"wy__991319590", "说唱榜"},
            {"wy__1978921795", "电音榜"},
            {"wy__71385702", "ACG榜"},
            {"wy__745956260", "韩语榜"},
            {"wy__5059633707", "摇滚榜"}
    };

    private final ExternalApiService netease;
    private final DirectCatalogProvider catalog;
    private final LocalLibraryStore library;

    DirectLxApiService(Context context) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        netease = new Retrofit.Builder()
                .baseUrl("https://music.163.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ExternalApiService.class);
        catalog = new DirectCatalogProvider();
        library = new LocalLibraryStore(context);
    }

    @Override
    public Call<LoginResponse> verifyUser(Map<String, String> body) {
        return value(() -> guestLogin());
    }

    @Override
    public Call<LoginResponse> loginUser(Map<String, String> body) {
        return value(() -> guestLogin());
    }

    @Override
    public Call<ListData> getUserList(String username, String password, String token) {
        return value(library::get);
    }

    @Override
    public Call<ResponseBody> updateUserList(String username, String password, String token,
                                             ListData listData) {
        return value(() -> {
            library.save(listData);
            return jsonBody("{\"success\":true}");
        });
    }

    @Override
    public Call<List<MusicInfo>> searchMusic(String keyword, String source, int page, int limit) {
        return value(() -> {
            if (!"wy".equals(source)) return catalog.search(source, keyword, page, limit);
            Response<JsonObject> response = netease.cloudSearch(
                    keyword, 1, limit, Math.max(0, page - 1) * limit).execute();
            JsonObject root = successful(response, "搜索失败");
            JsonObject result = object(root, "result");
            return parseTracks(array(result, "songs"));
        });
    }

    @Override
    public Call<MusicUrlResponse> getMusicUrl(String username, String password, String token,
                                               Map<String, Object> body) {
        return value(() -> { throw new IOException("直连模式由本地音源解析播放地址"); });
    }

    @Override
    public Call<LyricInfo> getLyric(String username, String password, String token,
                                    Map<String, String> params) {
        return value(() -> {
            if (!"wy".equals(params.get("source"))) {
                return catalog.lyric(params.get("source"), params);
            }
            long id;
            try {
                id = Long.parseLong(params.get("songmid"));
            } catch (Exception e) {
                return new LyricInfo();
            }
            Response<JsonObject> response = netease.getLyrics(id, 1, 1, -1).execute();
            JsonObject root = successful(response, "歌词获取失败");
            LyricInfo info = new LyricInfo();
            info.setLyric(lyric(root, "lrc"));
            info.setTlyric(lyric(root, "tlyric"));
            info.setRlyric(lyric(root, "romalrc"));
            info.setLxlyric(lyric(root, "klyric"));
            return info;
        });
    }

    @Override
    public Call<ResponseBody> getHotSearch(String source) {
        return value(() -> jsonBody("{\"list\":[\"周杰伦\",\"林俊杰\",\"陈奕迅\","
                + "\"五月天\",\"邓紫棋\",\"孙燕姿\",\"薛之谦\",\"Taylor Swift\"]}"));
    }

    @Override
    public Call<ResponseBody> tipSearch(String keyword, String source) {
        return value(() -> jsonBody("{\"list\":[]}"));
    }

    @Override
    public Call<ResponseBody> getSongListTags(String source) {
        return value(() -> jsonBody("{\"tags\":[],\"hotTag\":[]}"));
    }

    @Override
    public Call<ResponseBody> getSongListList(String source, String tagId, String sortId, int page) {
        return value(() -> {
            if (!"wy".equals(source)) {
                return jsonBody(GSON.toJson(catalog.playlistList(source, tagId, sortId, page)));
            }
            int limit = 30;
            Response<JsonObject> response = netease.getPublicPlaylists(
                    tagId == null || tagId.isEmpty() ? "全部" : tagId,
                    sortId == null || sortId.isEmpty() ? "hot" : sortId,
                    Math.max(0, page - 1) * limit, true, limit).execute();
            JsonObject root = successful(response, "歌单广场加载失败");
            JsonObject output = new JsonObject();
            output.add("list", mapPlaylists(array(root, "playlists")));
            output.addProperty("total", integer(root, "total", 0));
            output.addProperty("limit", limit);
            output.addProperty("page", page);
            return jsonBody(GSON.toJson(output));
        });
    }

    @Override
    public Call<Playlist> getPlaylistDetail(String source, String id, int page) {
        return value(() -> {
            if (!"wy".equals(source)) return catalog.playlistDetail(source, id, page);
            JsonObject detail = playlistDetail(id);
            Playlist playlist = playlistFromDetail(detail);
            playlist.setSongs(parseTracks(array(detail, "tracks")));
            return playlist;
        });
    }

    @Override
    public Call<ResponseBody> searchSongList(String source, String keyword, int page) {
        return value(() -> jsonBody("{\"list\":[],\"total\":0}"));
    }

    @Override
    public Call<ResponseBody> getPlayerConfig() {
        return value(() -> jsonBody("{}"));
    }

    @Override
    public Call<ResponseBody> cacheLyric(Map<String, Object> body) {
        return value(() -> jsonBody("{\"success\":true}"));
    }

    @Override
    public Call<ResponseBody> getCachedLyric(String source, String songmid, String songId) {
        return value(() -> jsonBody("{}"));
    }

    @Override
    public Call<ResponseBody> deletePlaylist(String auth, Map<String, Object> body) {
        return value(() -> jsonBody("{\"success\":true}"));
    }

    @Override
    public Call<ResponseBody> renamePlaylist(String auth, Map<String, Object> body) {
        return value(() -> jsonBody("{\"success\":true}"));
    }

    @Override
    public Call<ResponseBody> deleteSong(String auth, Map<String, Object> body) {
        return value(() -> jsonBody("{\"success\":true}"));
    }

    @Override
    public Call<ResponseBody> batchDeleteSongs(String auth, Map<String, Object> body) {
        return value(() -> jsonBody("{\"success\":true}"));
    }

    @Override
    public Call<ResponseBody> getComment(String source, String songmid) {
        return value(() -> jsonBody("{\"list\":[]}"));
    }

    @Override
    public Call<ResponseBody> getLeaderboardBoards(String source) {
        return value(() -> {
            JsonArray list = new JsonArray();
            if ("wy".equals(source)) {
                for (String[] board : WY_BOARDS) {
                    JsonObject item = new JsonObject();
                    item.addProperty("id", board[0]);
                    item.addProperty("name", board[1]);
                    list.add(item);
                }
            } else {
                list = catalog.boards(source);
            }
            JsonObject root = new JsonObject();
            root.add("list", list);
            return jsonBody(GSON.toJson(root));
        });
    }

    @Override
    public Call<ResponseBody> getLeaderboardList(String source, String bangId, int page) {
        return value(() -> {
            JsonObject root = new JsonObject();
            JsonArray list = new JsonArray();
            if ("wy".equals(source)) {
                String id = bangId == null ? "" : bangId.replace("wy__", "");
                for (MusicInfo music : parseTracks(array(playlistDetail(id), "tracks"))) {
                    list.add(GSON.toJsonTree(music));
                }
            } else {
                for (MusicInfo music : catalog.boardSongs(source, bangId, page)) {
                    list.add(GSON.toJsonTree(music));
                }
            }
            root.add("list", list);
            root.addProperty("total", list.size());
            return jsonBody(GSON.toJson(root));
        });
    }

    @Override
    public Call<ResponseBody> removeSongsFromPlaylist(String username, String password, String token,
                                                       Map<String, Object> body) {
        return value(() -> jsonBody("{\"success\":true}"));
    }

    private JsonObject playlistDetail(String id) throws IOException {
        Response<JsonObject> response = netease.getPublicPlaylistDetail(id, 1000, 8).execute();
        JsonObject root = successful(response, "歌单详情加载失败");
        return root.has("result") && root.get("result").isJsonObject()
                ? root.getAsJsonObject("result") : object(root, "playlist");
    }

    private static Playlist playlistFromDetail(JsonObject detail) {
        Playlist playlist = new Playlist();
        playlist.setId(string(detail, "id", ""));
        playlist.setSource("wy");
        playlist.setName(string(detail, "name", "歌单"));
        playlist.setPicUrl(string(detail, "coverImgUrl", ""));
        playlist.setDesc(string(detail, "description", ""));
        playlist.setSongCount(integer(detail, "trackCount", 0));
        playlist.setPlayCount(longValue(detail, "playCount", 0));
        JsonObject creator = object(detail, "creator");
        playlist.setCreator(string(creator, "nickname", ""));
        return playlist;
    }

    private static JsonArray mapPlaylists(JsonArray raw) {
        JsonArray result = new JsonArray();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            JsonObject mapped = new JsonObject();
            mapped.addProperty("id", string(item, "id", ""));
            mapped.addProperty("name", string(item, "name", "歌单"));
            mapped.addProperty("source", "wy");
            mapped.addProperty("img", string(item, "coverImgUrl", ""));
            mapped.addProperty("desc", string(item, "description", ""));
            mapped.addProperty("total", integer(item, "trackCount", 0));
            mapped.addProperty("playCount", longValue(item, "playCount", 0));
            mapped.addProperty("author", string(object(item, "creator"), "nickname", ""));
            result.add(mapped);
        }
        return result;
    }

    private static List<MusicInfo> parseTracks(JsonArray songs) {
        List<MusicInfo> result = new ArrayList<>();
        for (JsonElement element : songs) {
            if (!element.isJsonObject()) continue;
            JsonObject raw = element.getAsJsonObject();
            JsonObject mapped = new JsonObject();
            mapped.addProperty("source", "wy");
            mapped.addProperty("songmid", string(raw, "id", ""));
            mapped.addProperty("id", string(raw, "id", ""));
            mapped.addProperty("name", string(raw, "name", "未知歌曲"));
            JsonArray artists = raw.has("ar") && raw.get("ar").isJsonArray()
                    ? raw.getAsJsonArray("ar") : array(raw, "artists");
            mapped.addProperty("singer", joinArtists(artists));
            JsonObject album = raw.has("al") && raw.get("al").isJsonObject()
                    ? raw.getAsJsonObject("al") : object(raw, "album");
            mapped.addProperty("albumName", string(album, "name", ""));
            mapped.addProperty("albumId", string(album, "id", ""));
            mapped.addProperty("img", string(album, "picUrl", ""));
            long duration = longValue(raw, "dt", longValue(raw, "duration", 0));
            mapped.addProperty("interval", formatDuration(duration));
            JsonArray types = new JsonArray();
            types.add(quality("128k"));
            types.add(quality("320k"));
            types.add(quality("flac"));
            mapped.add("types", types);
            result.add(GSON.fromJson(mapped, MusicInfo.class));
        }
        return result;
    }

    private static JsonObject quality(String type) {
        JsonObject value = new JsonObject();
        value.addProperty("type", type);
        value.addProperty("size", "");
        return value;
    }

    private static String joinArtists(JsonArray artists) {
        StringBuilder result = new StringBuilder();
        for (JsonElement element : artists) {
            if (!element.isJsonObject()) continue;
            String name = string(element.getAsJsonObject(), "name", "");
            if (name.isEmpty()) continue;
            if (result.length() > 0) result.append('、');
            result.append(name);
        }
        return result.toString();
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        return String.format(java.util.Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static String lyric(JsonObject root, String name) {
        JsonObject value = object(root, name);
        return string(value, "lyric", "");
    }

    private static LoginResponse guestLogin() {
        LoginResponse response = new LoginResponse();
        response.setSuccess(false);
        response.setMessage("直连模式不使用账号");
        return response;
    }

    private static <T> Call<T> value(java.util.concurrent.Callable<T> task) {
        return new ValueCall<>(task);
    }

    private static ResponseBody jsonBody(String value) {
        return ResponseBody.create(JSON, value);
    }

    private static JsonObject successful(Response<JsonObject> response, String message) throws IOException {
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException(message + "：HTTP " + response.code());
        }
        return response.body();
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
        try {
            return object.has(name) && !object.get(name).isJsonNull()
                    ? object.get(name).getAsString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject object, String name, int fallback) {
        try {
            return object.has(name) ? object.get(name).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String name, long fallback) {
        try {
            return object.has(name) ? object.get(name).getAsLong() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
